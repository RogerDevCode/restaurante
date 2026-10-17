package Modelo;

import Servicio.CalculoMonetarioPedido;
import Servicio.CalculoMonetarioPedido.CalculoPedido;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestiona la propagación atómica de cambios en catálogo a los pedidos abiertos,
 * asegurando aislamiento transaccional y orden de bloqueos anti-deadlock.
 */
public class PreciosPedidosDao {
    private static final Logger LOGGER = Logger.getLogger(PreciosPedidosDao.class.getName());
    private static final int MAX_INTENTOS_DEADLOCK = 2;

    private final ProveedorConexionJdbc conexiones;
    private final AuditoriaPreciosDao auditoriaDao;

    public PreciosPedidosDao() {
        this(new ProveedorConexionJdbc(), new AuditoriaPreciosDao());
    }

    public PreciosPedidosDao(ProveedorConexionJdbc conexiones) {
        this(conexiones, new AuditoriaPreciosDao());
    }

    public PreciosPedidosDao(ProveedorConexionJdbc conexiones, AuditoriaPreciosDao auditoriaDao) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        this.auditoriaDao = auditoriaDao != null ? auditoriaDao : new AuditoriaPreciosDao();
    }

    public int contarPedidosAbiertosConPlato(int idPlato) {
        if (idPlato <= 0) return 0;
        String sql = """
            SELECT COUNT(DISTINCT p.id)
            FROM pedidos p
            INNER JOIN detalle_pedidos d ON p.id = d.id_pedido
            WHERE p.estado = 'PENDIENTE'
              AND p.cerrada_en IS NULL
              AND d.id_plato = ?
              AND (d.precio_congelado = 0 OR d.precio_congelado IS NULL)
            """;
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPlato);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return 0;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error al contar pedidos abiertos para el plato: " + ex.getMessage(), ex);
        }
    }

    public ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, String usuario) {
        return modificarProductoYRecalcularPendientes(plato, usuario, null);
    }

    public ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, String usuario, String idempotencyKey) {
        if (plato == null || plato.getId() <= 0) {
            throw ErrorAplicacionException.validacion("El plato a modificar debe tener un ID válido.");
        }

        int intentos = 0;
        while (true) {
            intentos++;
            try {
                return ejecutarTransaccion(plato, usuario, idempotencyKey);
            } catch (SQLException ex) {
                if (esDeadlock(ex) && intentos < MAX_INTENTOS_DEADLOCK) {
                    LOGGER.log(Level.WARNING, "Deadlock detectado al actualizar catálogo; reintentando intento {0}", intentos);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DataAccessException("Operación interrumpida durante reintento.", ie);
                    }
                    continue;
                }
                throw new DataAccessException("Error de base de datos al propagar precios del catálogo: " + ex.getMessage(), ex);
            }
        }
    }

    private ResultadoActualizacionPrecios ejecutarTransaccion(Platos plato, String usuario, String idempotencyKey) throws SQLException {
        try (Connection con = conexiones.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    String sqlCheck = "SELECT id FROM auditoria_cambios_precio WHERE idempotency_key = ? OR idempotency_key LIKE ?";
                    try (PreparedStatement ps = con.prepareStatement(sqlCheck)) {
                        ps.setString(1, idempotencyKey);
                        ps.setString(2, idempotencyKey + "-ped-%");
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                con.commit();
                                return new ResultadoActualizacionPrecios(plato, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), 0);
                            }
                        }
                    }
                }

                // 1. ORDEN CANÓNICO DE BLOQUEO: Bloquear primero pedidos pendientes afectados (pedidos -> platos)
                List<Integer> pedidosAfectados = new ArrayList<>();
                String sqlBuscarYBloquearPedidos = """
                    SELECT id FROM pedidos WHERE id IN (
                        SELECT DISTINCT d.id_pedido
                        FROM detalle_pedidos d
                        INNER JOIN pedidos p ON p.id = d.id_pedido
                        WHERE p.estado = 'PENDIENTE'
                          AND p.cerrada_en IS NULL
                          AND d.id_plato = ?
                          AND (d.precio_congelado = 0 OR d.precio_congelado IS NULL)
                    )
                    ORDER BY id ASC
                    FOR UPDATE
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlBuscarYBloquearPedidos)) {
                    ps.setInt(1, plato.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pedidosAfectados.add(rs.getInt(1));
                        }
                    }
                }

                // 2. Bloquear y verificar el plato actual (después de bloquear pedidos)
                BigDecimal precioUsdAnt = null;
                BigDecimal precioVesAnt = null;
                String monedaAnt = null;

                String sqlLockPlato = """
                    SELECT id, nombre, precio, aplica_iva, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva
                    FROM platos
                    WHERE id = ?
                    FOR UPDATE
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlLockPlato)) {
                    ps.setInt(1, plato.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ErrorAplicacionException.validacion("El producto no existe en el catálogo.");
                        }
                        monedaAnt = rs.getString("moneda_base");
                        precioUsdAnt = rs.getBigDecimal("precio_usd");
                        precioVesAnt = rs.getBigDecimal("precio_ves");
                    }
                }

                // 3. Actualizar el plato
                String sqlUpdatePlato = """
                    UPDATE platos
                    SET nombre = ?, precio = ?, aplica_iva = ?, moneda_base = ?,
                        precio_usd = ?, precio_ves = ?, precio_clasificado = ?, precio_incluye_iva = ?
                    WHERE id = ?
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlUpdatePlato)) {
                    ps.setString(1, plato.getNombre());
                    BigDecimal precioLegacy = "VES".equalsIgnoreCase(plato.getMonedaBase())
                            ? (plato.getPrecioVes() != null ? plato.getPrecioVes() : plato.getPrecioDecimal())
                            : (plato.getPrecioUsd() != null ? plato.getPrecioUsd() : plato.getPrecioDecimal());
                    ps.setBigDecimal(2, precioLegacy);
                    ps.setInt(3, plato.isAplicaIva() ? 1 : 0);
                    ps.setString(4, plato.getMonedaBase());
                    ps.setBigDecimal(5, plato.getPrecioUsd());
                    ps.setBigDecimal(6, plato.getPrecioVes());
                    ps.setInt(7, plato.isPrecioClasificado() ? 1 : 0);
                    if (plato.getPrecioIncluyeIva() != null) {
                        ps.setInt(8, plato.getPrecioIncluyeIva() ? 1 : 0);
                    } else {
                        ps.setNull(8, java.sql.Types.TINYINT);
                    }
                    ps.setInt(9, plato.getId());
                    if (ps.executeUpdate() != 1) {
                        throw ErrorAplicacionException.validacion("No se pudo actualizar el plato en el catálogo.");
                    }
                }

                // 4. Contar líneas no enlazadas con el mismo nombre en pedidos abiertos
                int lineasNoEnlazadas = 0;
                String sqlNoEnlazadas = """
                    SELECT COUNT(*)
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON p.id = d.id_pedido
                    WHERE p.estado = 'PENDIENTE'
                      AND p.cerrada_en IS NULL
                      AND d.id_plato IS NULL
                      AND LOWER(TRIM(d.nombre)) = LOWER(TRIM(?))
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlNoEnlazadas)) {
                    ps.setString(1, plato.getNombre());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            lineasNoEnlazadas = rs.getInt(1);
                        }
                    }
                }

                BigDecimal totalAnteriorGlobal = BigDecimal.ZERO.setScale(2);
                BigDecimal totalNuevoGlobal = BigDecimal.ZERO.setScale(2);

                BigDecimal nuevoPrecioLinea = "VES".equalsIgnoreCase(plato.getMonedaBase())
                        ? (plato.getPrecioVes() != null ? plato.getPrecioVes() : plato.getPrecioDecimal())
                        : (plato.getPrecioUsd() != null ? plato.getPrecioUsd() : plato.getPrecioDecimal());

                // 5. Para cada pedido, recalcular detalles y cabecera
                for (int idPedido : pedidosAfectados) {
                    BigDecimal tasaCambio = null;
                    BigDecimal ivaPorcentajeCabecera = null;
                    BigDecimal totalBsAnterior = null;

                    String sqlCabecera = """
                        SELECT tasa_cambio, iva_porcentaje, total_bs, version_precio
                        FROM pedidos
                        WHERE id = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL
                        FOR UPDATE
                        """;
                    try (PreparedStatement ps = con.prepareStatement(sqlCabecera)) {
                        ps.setInt(1, idPedido);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                continue; // Pedido cerró concurrentemente
                            }
                            tasaCambio = rs.getBigDecimal("tasa_cambio");
                            ivaPorcentajeCabecera = rs.getBigDecimal("iva_porcentaje");
                            totalBsAnterior = rs.getBigDecimal("total_bs");
                        }
                    }

                    if (tasaCambio == null || tasaCambio.signum() <= 0) {
                        throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene una tasa de cambio válida registrada.");
                    }
                    if (ivaPorcentajeCabecera == null || ivaPorcentajeCabecera.compareTo(BigDecimal.ZERO) < 0) {
                        throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene un porcentaje de IVA válido registrado.");
                    }
                    if (totalBsAnterior != null) {
                        totalAnteriorGlobal = totalAnteriorGlobal.add(totalBsAnterior);
                    }

                    // Leer detalles del pedido
                    String sqlDetalles = """
                        SELECT id, id_plato, nombre, precio, cantidad, comentario, aplica_iva,
                               moneda_base, precio_incluye_iva, iva_porcentaje, precio_congelado
                        FROM detalle_pedidos
                        WHERE id_pedido = ?
                        FOR UPDATE
                        """;
                    List<DetallePedido> detalles = new ArrayList<>();
                    try (PreparedStatement ps = con.prepareStatement(sqlDetalles)) {
                        ps.setInt(1, idPedido);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                DetallePedido det = new DetallePedido();
                                det.setId(rs.getInt("id"));
                                int idPl = rs.getInt("id_plato");
                                det.setIdPlato(rs.wasNull() ? null : idPl);
                                det.setNombre(rs.getString("nombre"));
                                det.setPrecioDecimal(rs.getBigDecimal("precio"));
                                det.setCantidad(rs.getInt("cantidad"));
                                det.setComentario(rs.getString("comentario"));
                                int aplIva = rs.getInt("aplica_iva");
                                det.setAplicaIva(rs.wasNull() ? null : (aplIva != 0));
                                det.setMonedaBase(rs.getString("moneda_base"));
                                int incIva = rs.getInt("precio_incluye_iva");
                                det.setPrecioIncluyeIva(rs.wasNull() ? null : (incIva != 0));
                                det.setIvaPorcentaje(rs.getBigDecimal("iva_porcentaje"));
                                det.setPrecioCongelado(rs.getInt("precio_congelado") != 0);
                                detalles.add(det);
                            }
                        }
                    }

                    // Actualizar en BD y memoria las líneas que corresponden al plato actualizado
                    String sqlUpdateDetalle = """
                        UPDATE detalle_pedidos
                        SET nombre = ?, precio = ?, moneda_base = ?, aplica_iva = ?, precio_incluye_iva = ?, iva_porcentaje = ?
                        WHERE id = ?
                        """;
                    try (PreparedStatement ps = con.prepareStatement(sqlUpdateDetalle)) {
                        for (DetallePedido det : detalles) {
                            if (det.getIdPlato() != null && det.getIdPlato() == plato.getId() && !det.isPrecioCongelado()) {
                                det.setNombre(plato.getNombre());
                                det.setPrecioDecimal(nuevoPrecioLinea);
                                det.setMonedaBase(plato.getMonedaBase());
                                det.setAplicaIva(plato.isAplicaIva());
                                det.setPrecioIncluyeIva(Boolean.TRUE.equals(plato.getPrecioIncluyeIva()));
                                det.setIvaPorcentaje(Boolean.TRUE.equals(det.getAplicaIva())
                                        ? ivaPorcentajeCabecera
                                        : BigDecimal.ZERO.setScale(2));

                                ps.setString(1, det.getNombre());
                                ps.setBigDecimal(2, det.getPrecioDecimal());
                                ps.setString(3, det.getMonedaBase());
                                ps.setInt(4, Boolean.TRUE.equals(det.getAplicaIva()) ? 1 : 0);
                                ps.setInt(5, Boolean.TRUE.equals(det.getPrecioIncluyeIva()) ? 1 : 0);
                                ps.setBigDecimal(6, det.getIvaPorcentaje());
                                ps.setInt(7, det.getId());
                                ps.executeUpdate();
                            } else {
                                if (!det.isPrecioCongelado()) {
                                    if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()
                                            || det.getPrecioIncluyeIva() == null
                                            || det.getAplicaIva() == null) {
                                        if (det.getIdPlato() != null && det.getIdPlato() > 0) {
                                            throw ErrorAplicacionException.validacion("No se puede actualizar el catálogo: el pedido #" + idPedido
                                                    + " contiene productos heredados sin clasificar (detalle #" + det.getId() + ": '"
                                                    + det.getNombre() + "'). Clasifíquelos en el catálogo o márquelos como congelados antes de continuar.");
                                        }

                                        // Para ítems legados sin idPlato (desvinculados del catálogo), auto-congelar preservando valor nominal en moneda local
                                        det.setPrecioCongelado(true);
                                        if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) det.setMonedaBase("VES");
                                        if (det.getAplicaIva() == null) det.setAplicaIva(false);
                                        if (det.getPrecioIncluyeIva() == null) det.setPrecioIncluyeIva(false);
                                        BigDecimal ivaDet = Boolean.TRUE.equals(det.getAplicaIva()) ? ivaPorcentajeCabecera : BigDecimal.ZERO.setScale(2);
                                        det.setIvaPorcentaje(ivaDet);

                                        String sqlCong = """
                                            UPDATE detalle_pedidos
                                            SET precio_congelado = 1,
                                                congelado_motivo = 'Auto-congelado por ítem legado sin clasificación en catálogo',
                                                congelado_por = ?,
                                                congelado_en = CURRENT_TIMESTAMP,
                                                moneda_base = ?,
                                                precio_incluye_iva = ?,
                                                aplica_iva = ?,
                                                iva_porcentaje = ?
                                            WHERE id = ?
                                            """;
                                        try (PreparedStatement psCong = con.prepareStatement(sqlCong)) {
                                            psCong.setString(1, (usuario != null && !usuario.isBlank()) ? usuario : "SISTEMA");
                                            psCong.setString(2, det.getMonedaBase());
                                            psCong.setInt(3, Boolean.TRUE.equals(det.getPrecioIncluyeIva()) ? 1 : 0);
                                            psCong.setInt(4, Boolean.TRUE.equals(det.getAplicaIva()) ? 1 : 0);
                                            psCong.setBigDecimal(5, det.getIvaPorcentaje());
                                            psCong.setInt(6, det.getId());
                                            psCong.executeUpdate();
                                        }
                                    }
                                }
                                if (det.getIvaPorcentaje() == null) {
                                    det.setIvaPorcentaje(Boolean.TRUE.equals(det.getAplicaIva())
                                            ? ivaPorcentajeCabecera
                                            : BigDecimal.ZERO.setScale(2));
                                }
                            }
                        }
                    }

                    // Recalcular cabecera
                    CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasaCambio);
                    totalNuevoGlobal = totalNuevoGlobal.add(calc.totalVes());

                    String sqlUpdateCabecera = """
                        UPDATE pedidos
                        SET subtotal = ?, iva_porcentaje = ?, iva_monto = ?, total = ?,
                            subtotal_bs = ?, iva_bs = ?, total_bs = ?,
                            version_precio = version_precio + 1
                        WHERE id = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL
                        """;
                    try (PreparedStatement ps = con.prepareStatement(sqlUpdateCabecera)) {
                        ps.setBigDecimal(1, calc.subtotalUsd());
                        ps.setBigDecimal(2, ivaPorcentajeCabecera);
                        ps.setBigDecimal(3, calc.ivaUsd());
                        ps.setBigDecimal(4, calc.totalUsd());
                        ps.setBigDecimal(5, calc.subtotalVes());
                        ps.setBigDecimal(6, calc.ivaVes());
                        ps.setBigDecimal(7, calc.totalVes());
                        ps.setInt(8, idPedido);
                        ps.executeUpdate();
                    }

                    // Insertar auditoría
                    String keyDet = idempotencyKey != null
                            ? (pedidosAfectados.size() == 1 ? idempotencyKey : idempotencyKey + "-ped-" + idPedido)
                            : null;
                    AuditoriaPreciosDao.CambioPrecio cambio = new AuditoriaPreciosDao.CambioPrecio(
                            "CAMBIO_CATALOGO",
                            plato.getId(),
                            idPedido,
                            null,
                            plato.getMonedaBase(),
                            precioUsdAnt,
                            plato.getPrecioUsd(),
                            precioVesAnt,
                            plato.getPrecioVes(),
                            tasaCambio,
                            tasaCambio,
                            totalBsAnterior,
                            calc.totalVes(),
                            usuario != null && !usuario.isBlank() ? usuario : "Sistema",
                            "Propagación de catálogo a pedido #" + idPedido,
                            keyDet
                    );
                    auditoriaDao.insertar(con, cambio);
                }

                if (pedidosAfectados.isEmpty()) {
                    AuditoriaPreciosDao.CambioPrecio cambio = new AuditoriaPreciosDao.CambioPrecio(
                            "CAMBIO_CATALOGO",
                            plato.getId(),
                            null,
                            null,
                            plato.getMonedaBase(),
                            precioUsdAnt,
                            plato.getPrecioUsd(),
                            precioVesAnt,
                            plato.getPrecioVes(),
                            null,
                            null,
                            BigDecimal.ZERO.setScale(2),
                            BigDecimal.ZERO.setScale(2),
                            usuario != null && !usuario.isBlank() ? usuario : "Sistema",
                            "Modificación de producto en catálogo",
                            idempotencyKey
                    );
                    auditoriaDao.insertar(con, cambio);
                }

                con.commit();
                return new ResultadoActualizacionPrecios(
                        plato,
                        pedidosAfectados.size(),
                        totalAnteriorGlobal,
                        totalNuevoGlobal,
                        pedidosAfectados,
                        lineasNoEnlazadas
                );
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    private static boolean esDeadlock(SQLException ex) {
        if ("40001".equals(ex.getSQLState()) || ex.getErrorCode() == 1213) {
            return true;
        }
        Throwable causa = ex.getCause();
        if (causa instanceof SQLException subEx) {
            return esDeadlock(subEx);
        }
        return false;
    }
}
