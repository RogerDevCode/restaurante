package Modelo;

import Servicio.CalculoMonetarioPedido;
import Servicio.CalculoMonetarioPedido.CalculoPedido;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Aplica atómicamente la actualización de la tasa de cambio global a la configuración,
 * al catálogo maestro de platos y a todas las comandas de mesas abiertas (cerrada_en IS NULL),
 * registrando auditoría y evitando carreras con cajas o cierres de mesa.
 */
public class ActualizacionTasaDao {
    private static final Logger LOGGER = Logger.getLogger(ActualizacionTasaDao.class.getName());
    private static final int MAX_INTENTOS_DEADLOCK = 2;

    private final ProveedorConexionJdbc conexiones;
    private final AuditoriaPreciosDao auditoriaDao;

    public ActualizacionTasaDao() {
        this(new ProveedorConexionJdbc(), new AuditoriaPreciosDao());
    }

    public ActualizacionTasaDao(ProveedorConexionJdbc conexiones) {
        this(conexiones, new AuditoriaPreciosDao());
    }

    public ActualizacionTasaDao(ProveedorConexionJdbc conexiones, AuditoriaPreciosDao auditoriaDao) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        this.auditoriaDao = auditoriaDao != null ? auditoriaDao : new AuditoriaPreciosDao();
    }

    public int contarPedidosAbiertos() {
        return contarPedidosAbiertos(null);
    }

    public int contarPedidosAbiertos(Integer idSalaFiltro) {
        String sql = (idSalaFiltro != null && idSalaFiltro > 0)
                ? "SELECT COUNT(*) FROM pedidos WHERE estado = 'PENDIENTE' AND cerrada_en IS NULL AND id_sala = ?"
                : "SELECT COUNT(*) FROM pedidos WHERE estado = 'PENDIENTE' AND cerrada_en IS NULL";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idSalaFiltro != null && idSalaFiltro > 0) {
                ps.setInt(1, idSalaFiltro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return 0;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error al contar pedidos abiertos: " + ex.getMessage(), ex);
        }
    }

    public ResultadoActualizacionPrecios actualizarTasaYRecalcularPendientes(BigDecimal tasaNueva, String usuario) {
        return actualizarTasaYRecalcularPendientes(tasaNueva, usuario, null, null);
    }

    public ResultadoActualizacionPrecios actualizarTasaYRecalcularPendientes(BigDecimal tasaNueva, String usuario, String idempotencyKey) {
        return actualizarTasaYRecalcularPendientes(tasaNueva, usuario, idempotencyKey, null);
    }

    public ResultadoActualizacionPrecios actualizarTasaYRecalcularPendientes(BigDecimal tasaNueva, String usuario, String idempotencyKey, Integer idSalaFiltro) {
        if (tasaNueva == null || tasaNueva.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio debe ser positiva.");
        }
        BigDecimal tasaNormalizada = tasaNueva.setScale(4, RoundingMode.HALF_UP);

        int intentos = 0;
        while (true) {
            intentos++;
            try {
                return ejecutarTransaccion(tasaNormalizada, usuario, idempotencyKey, idSalaFiltro);
            } catch (SQLException ex) {
                if (esDeadlock(ex) && intentos < MAX_INTENTOS_DEADLOCK) {
                    LOGGER.log(Level.WARNING, "Deadlock detectado al actualizar tasa; reintentando intento {0}", intentos);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DataAccessException("Operación interrumpida durante reintento.", ie);
                    }
                    continue;
                }
                throw new DataAccessException("Error al actualizar la tasa de cambio y recalcular pedidos: " + ex.getMessage(), ex);
            }
        }
    }

    private ResultadoActualizacionPrecios ejecutarTransaccion(BigDecimal tasaNueva, String usuario, String idempotencyKey, Integer idSalaFiltro) throws SQLException {
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
                                return new ResultadoActualizacionPrecios(null, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), 0);
                            }
                        }
                    }
                }

                // 1. Bloquear y leer tasa anterior en config y configuracion_sistema
                BigDecimal tasaAnterior = null;
                String sqlLockConfig = "SELECT tasa_dolar FROM config WHERE id = 1 FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(sqlLockConfig);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tasaAnterior = rs.getBigDecimal("tasa_dolar");
                    }
                }
                if (tasaAnterior == null) {
                    tasaAnterior = BigDecimal.ZERO;
                }

                // Si no hay cambio en la tasa, no se requiere recálculo de pedidos
                if (tasaAnterior.compareTo(tasaNueva) == 0) {
                    con.commit();
                    return new ResultadoActualizacionPrecios(null, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), 0);
                }

                // 2. Bloquear platos del catálogo en orden anti-deadlock (ORDER BY id ASC) y recalcular referencias
                String sqlLockPlatos = """
                    SELECT id, nombre, moneda_base, precio_usd, precio_ves, aplica_iva, precio_incluye_iva
                    FROM platos
                    ORDER BY id ASC
                    FOR UPDATE
                    """;
                String sqlUpdatePlatoRef = """
                    UPDATE platos
                    SET precio_usd = ?, precio_ves = ?, precio = ?
                    WHERE id = ?
                    """;
                try (PreparedStatement psLock = con.prepareStatement(sqlLockPlatos);
                     PreparedStatement psUpdate = con.prepareStatement(sqlUpdatePlatoRef);
                     ResultSet rs = psLock.executeQuery()) {
                    while (rs.next()) {
                        int idPlato = rs.getInt("id");
                        String mb = rs.getString("moneda_base");
                        BigDecimal usd = rs.getBigDecimal("precio_usd");
                        BigDecimal ves = rs.getBigDecimal("precio_ves");

                        if ("VES".equalsIgnoreCase(mb) && ves != null) {
                            usd = ves.divide(tasaNueva, 2, RoundingMode.HALF_UP);
                        } else if (usd != null) {
                            ves = usd.multiply(tasaNueva).setScale(2, RoundingMode.HALF_UP);
                        }

                        psUpdate.setBigDecimal(1, usd);
                        psUpdate.setBigDecimal(2, ves);
                        psUpdate.setBigDecimal(3, "VES".equalsIgnoreCase(mb) ? ves : usd);
                        psUpdate.setInt(4, idPlato);
                        psUpdate.executeUpdate();
                    }
                }

                // 3. Bloquear pedidos pendientes y ABIERTOS (cerrada_en IS NULL) en orden anti-deadlock
                String sqlPedidosAbiertos = (idSalaFiltro != null && idSalaFiltro > 0)
                    ? """
                    SELECT id, tasa_cambio, iva_porcentaje, total_bs, version_precio
                    FROM pedidos
                    WHERE estado = 'PENDIENTE' AND cerrada_en IS NULL AND id_sala = ?
                    ORDER BY id ASC
                    FOR UPDATE
                    """
                    : """
                    SELECT id, tasa_cambio, iva_porcentaje, total_bs, version_precio
                    FROM pedidos
                    WHERE estado = 'PENDIENTE' AND cerrada_en IS NULL
                    ORDER BY id ASC
                    FOR UPDATE
                    """;
                List<Integer> pedidosAfectados = new ArrayList<>();
                List<BigDecimal> totalesAnteriores = new ArrayList<>();
                List<BigDecimal> ivasPorcentaje = new ArrayList<>();

                try (PreparedStatement ps = con.prepareStatement(sqlPedidosAbiertos)) {
                    if (idSalaFiltro != null && idSalaFiltro > 0) {
                        ps.setInt(1, idSalaFiltro);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pedidosAfectados.add(rs.getInt("id"));
                            totalesAnteriores.add(rs.getBigDecimal("total_bs"));
                            ivasPorcentaje.add(rs.getBigDecimal("iva_porcentaje"));
                        }
                    }
                }

                BigDecimal totalAnteriorGlobal = BigDecimal.ZERO.setScale(2);
                BigDecimal totalNuevoGlobal = BigDecimal.ZERO.setScale(2);

                String sqlDetalles = """
                    SELECT id, id_plato, nombre, precio, cantidad, comentario, aplica_iva,
                           moneda_base, precio_incluye_iva, iva_porcentaje, precio_congelado
                    FROM detalle_pedidos
                    WHERE id_pedido = ?
                    FOR UPDATE
                    """;

                String sqlUpdateCabecera = """
                    UPDATE pedidos
                    SET tasa_cambio = ?, subtotal = ?, iva_porcentaje = ?, iva_monto = ?, total = ?,
                        subtotal_bs = ?, iva_bs = ?, total_bs = ?,
                        version_precio = version_precio + 1
                    WHERE id = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL
                    """;

                for (int i = 0; i < pedidosAfectados.size(); i++) {
                    int idPed = pedidosAfectados.get(i);
                    BigDecimal totalBsAnt = totalesAnteriores.get(i);
                    BigDecimal ivaPorc = ivasPorcentaje.get(i);
                    if (totalBsAnt != null) {
                        totalAnteriorGlobal = totalAnteriorGlobal.add(totalBsAnt);
                    }

                    List<DetallePedido> detalles = new ArrayList<>();
                    try (PreparedStatement psDet = con.prepareStatement(sqlDetalles)) {
                        psDet.setInt(1, idPed);
                        try (ResultSet rs = psDet.executeQuery()) {
                            while (rs.next()) {
                                DetallePedido det = new DetallePedido();
                                det.setId(rs.getInt("id"));
                                int idPl = rs.getInt("id_plato");
                                det.setIdPlato(rs.wasNull() ? null : idPl);
                                det.setNombre(rs.getString("nombre"));
                                det.setPrecioDecimal(rs.getBigDecimal("precio"));
                                det.setCantidad(rs.getInt("cantidad"));
                                det.setComentario(rs.getString("comentario"));
                                boolean aplIva = rs.getBoolean("aplica_iva");
                                det.setAplicaIva(rs.wasNull() ? null : aplIva);
                                det.setMonedaBase(rs.getString("moneda_base"));
                                int incIva = rs.getInt("precio_incluye_iva");
                                det.setPrecioIncluyeIva(rs.wasNull() ? null : (incIva != 0));
                                det.setIvaPorcentaje(rs.getBigDecimal("iva_porcentaje"));
                                det.setPrecioCongelado(rs.getInt("precio_congelado") != 0);

                                if (!det.isPrecioCongelado()) {
                                    if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()
                                            || det.getAplicaIva() == null || det.getPrecioIncluyeIva() == null) {
                                        throw ErrorAplicacionException.validacion("No se puede actualizar la tasa: el pedido #" + idPed
                                                + " contiene productos heredados sin clasificar (detalle #" + det.getId() + ": '"
                                                + det.getNombre() + "'). Clasifíquelos en el catálogo o congélelos antes de continuar.");
                                    }
                                }
                                if (det.getIvaPorcentaje() == null) {
                                    det.setIvaPorcentaje(Boolean.TRUE.equals(det.getAplicaIva())
                                            ? (ivaPorc != null ? ivaPorc : new BigDecimal("16.00"))
                                            : BigDecimal.ZERO.setScale(2));
                                }
                                detalles.add(det);
                            }
                        }
                    }

                    // Recalcular el pedido completo usando la nueva tasa
                    CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasaNueva);
                    totalNuevoGlobal = totalNuevoGlobal.add(calc.totalVes());

                    try (PreparedStatement psUp = con.prepareStatement(sqlUpdateCabecera)) {
                        psUp.setBigDecimal(1, tasaNueva);
                        psUp.setBigDecimal(2, calc.subtotalUsd());
                        psUp.setBigDecimal(3, ivaPorc != null ? ivaPorc : new BigDecimal("16.00"));
                        psUp.setBigDecimal(4, calc.ivaUsd());
                        psUp.setBigDecimal(5, calc.totalUsd());
                        psUp.setBigDecimal(6, calc.subtotalVes());
                        psUp.setBigDecimal(7, calc.ivaVes());
                        psUp.setBigDecimal(8, calc.totalVes());
                        psUp.setInt(9, idPed);
                        psUp.executeUpdate();
                    }

                    // Registrar auditoría de tasa para este pedido abierto
                    String keyDet = idempotencyKey != null
                            ? (pedidosAfectados.size() == 1 ? idempotencyKey : idempotencyKey + "-ped-" + idPed)
                            : null;
                    AuditoriaPreciosDao.CambioPrecio cambio = new AuditoriaPreciosDao.CambioPrecio(
                            "CAMBIO_TASA",
                            null,
                            idPed,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            tasaAnterior,
                            tasaNueva,
                            totalBsAnt,
                            calc.totalVes(),
                            usuario != null && !usuario.isBlank() ? usuario : "Sistema",
                            "Recálculo de mesa abierta #" + idPed + " por cambio de tasa oficial",
                            keyDet
                    );
                    auditoriaDao.insertar(con, cambio);
                }

                if (pedidosAfectados.isEmpty()) {
                    AuditoriaPreciosDao.CambioPrecio cambio = new AuditoriaPreciosDao.CambioPrecio(
                            "CAMBIO_TASA",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            tasaAnterior,
                            tasaNueva,
                            BigDecimal.ZERO.setScale(2),
                            BigDecimal.ZERO.setScale(2),
                            usuario != null && !usuario.isBlank() ? usuario : "Sistema",
                            "Actualización de tasa oficial sin pedidos abiertos pendientes",
                            idempotencyKey
                    );
                    auditoriaDao.insertar(con, cambio);
                }

                // 4. Actualizar tasa en config y configuracion_sistema
                String sqlUpConfig = "UPDATE config SET tasa_dolar = ? WHERE id = 1";
                try (PreparedStatement ps = con.prepareStatement(sqlUpConfig)) {
                    ps.setBigDecimal(1, tasaNueva);
                    ps.executeUpdate();
                }

                String sqlUpKv = """
                    INSERT INTO configuracion_sistema (clave, valor, actualizado_en)
                    VALUES ('tasa_dolar', ?, NOW())
                    ON DUPLICATE KEY UPDATE valor = VALUES(valor), actualizado_en = NOW()
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlUpKv)) {
                    ps.setString(1, tasaNueva.toPlainString());
                    ps.executeUpdate();
                }

                con.commit();
                return new ResultadoActualizacionPrecios(
                        null,
                        pedidosAfectados.size(),
                        totalAnteriorGlobal,
                        totalNuevoGlobal,
                        pedidosAfectados,
                        0
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
