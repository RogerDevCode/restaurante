package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import Servicio.CalculoMonetarioPedido;
import Servicio.CalculoMonetarioPedido.CalculoPedido;

public class PedidosDao implements PedidosRepositorio {
    private static final Logger LOGGER = Logger.getLogger(PedidosDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public PedidosDao() {
        this(new ProveedorConexionJdbc());
    }

    public PedidosDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }

    public int verificarStado(int mesa, int id_sala) {
        if (mesa <= 0 || id_sala <= 0) {
            throw ErrorAplicacionException.validacion("La sala y el número de mesa deben ser válidos.");
        }
        int id_pedido = 0;
        String sql = """
            SELECT id
            FROM pedidos
            WHERE num_mesa = ? AND id_sala = ? AND estado = ? AND cerrada_en IS NULL
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, mesa);
            sentencia.setInt(2, id_sala);
            sentencia.setString(3, "PENDIENTE");
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    id_pedido = resultados.getInt("id");
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar el estado de la mesa.", ex);
        }
        return id_pedido;
    }

    @Override
    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        validarEstructuraPedido(pedido, detalles);

        String sqlPedido = """
            INSERT INTO pedidos (id_sala, num_mesa, tipo_pedido, subtotal, iva_porcentaje, iva_monto, total, usuario, tasa_cambio, subtotal_bs, iva_bs, total_bs, id_mesonero, mesonero_nombre, version_precio)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """;
        String sqlDetalle = """
            INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido, aplica_iva, id_plato, moneda_base, precio_incluye_iva, iva_porcentaje)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conexion = conexiones.getConnection()) {
            try {
                conexion.setAutoCommit(false);
                calcularYConsolidarSnapshot(conexion, pedido, detalles);

                int idPedido;
                try (PreparedStatement sentenciaPedido = conexion.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                    boolean pedidoRapido = pedido.esPedidoRapido();
                    if (pedidoRapido) {
                        sentenciaPedido.setNull(1, java.sql.Types.INTEGER);
                        sentenciaPedido.setNull(2, java.sql.Types.INTEGER);
                    } else {
                        sentenciaPedido.setInt(1, pedido.getId_sala());
                        sentenciaPedido.setInt(2, pedido.getNum_mesa());
                    }
                    sentenciaPedido.setString(3, pedido.getTipoPedido());
                    sentenciaPedido.setBigDecimal(4, importePersistible(pedido.getSubtotal(), "El subtotal del pedido"));
                    sentenciaPedido.setBigDecimal(5, pedido.getIvaPorcentaje() != null ? pedido.getIvaPorcentaje() : BigDecimal.ZERO);
                    sentenciaPedido.setBigDecimal(6, pedido.getIvaMonto() != null ? pedido.getIvaMonto() : BigDecimal.ZERO);
                    sentenciaPedido.setBigDecimal(7, importePersistible(pedido.getTotalDecimal(), "El total del pedido"));
                    sentenciaPedido.setString(8, pedido.getUsuario());
                    sentenciaPedido.setBigDecimal(9, pedido.getTasaCambio());
                    sentenciaPedido.setBigDecimal(10, pedido.getSubtotalBs());
                    sentenciaPedido.setBigDecimal(11, pedido.getIvaBs());
                    sentenciaPedido.setBigDecimal(12, pedido.getTotalBs());
                    if (pedido.getIdMesonero() != null && pedido.getIdMesonero() > 0) {
                        sentenciaPedido.setInt(13, pedido.getIdMesonero());
                    } else {
                        sentenciaPedido.setNull(13, java.sql.Types.INTEGER);
                    }
                    sentenciaPedido.setString(14, pedido.getMesoneroNombre());
                    if (sentenciaPedido.executeUpdate() != 1) {
                        throw new SQLException("No se pudo insertar el encabezado del pedido.");
                    }
                    try (ResultSet claves = sentenciaPedido.getGeneratedKeys()) {
                        if (!claves.next()) {
                            throw new SQLException("La base de datos no devolvió el ID del pedido.");
                        }
                        idPedido = claves.getInt(1);
                        if (idPedido <= 0) {
                            throw new SQLException("La base de datos devolvió un ID de pedido no válido.");
                        }
                    }
                }

                try (PreparedStatement sentenciaDetalle = conexion.prepareStatement(sqlDetalle)) {
                    for (DetallePedido detalle : detalles) {
                        sentenciaDetalle.setString(1, detalle.getNombre());
                        sentenciaDetalle.setBigDecimal(2, importePersistible(
                                detalle.getPrecioDecimal(), "El precio de cada plato"));
                        sentenciaDetalle.setInt(3, detalle.getCantidad());
                        sentenciaDetalle.setString(4, detalle.getComentario() != null ? detalle.getComentario() : "");
                        sentenciaDetalle.setInt(5, idPedido);
                        sentenciaDetalle.setBoolean(6, detalle.getAplicaIva() == null || detalle.getAplicaIva());
                        if (detalle.getIdPlato() != null && detalle.getIdPlato() > 0) {
                            sentenciaDetalle.setInt(7, detalle.getIdPlato());
                        } else {
                            sentenciaDetalle.setNull(7, java.sql.Types.INTEGER);
                        }
                        sentenciaDetalle.setString(8, detalle.getMonedaBase() != null ? detalle.getMonedaBase() : "USD");
                        if (detalle.getPrecioIncluyeIva() != null) {
                            sentenciaDetalle.setInt(9, detalle.getPrecioIncluyeIva() ? 1 : 0);
                        } else {
                            sentenciaDetalle.setNull(9, java.sql.Types.TINYINT);
                        }
                        if (detalle.getIvaPorcentaje() != null) {
                            sentenciaDetalle.setBigDecimal(10, detalle.getIvaPorcentaje());
                        } else {
                            sentenciaDetalle.setNull(10, java.sql.Types.DECIMAL);
                        }
                        if (sentenciaDetalle.executeUpdate() != 1) {
                            throw new SQLException("No se pudo insertar uno de los detalles del pedido.");
                        }
                    }
                }

                conexion.commit();
                return idPedido;
            } catch (SQLException | RuntimeException error) {
                try {
                    conexion.rollback();
                } catch (SQLException errorRollback) {
                    error.addSuppressed(errorRollback);
                }
                if (error instanceof SQLException errorSql) {
                    String mensaje = errorSql.getMessage();
                    if (errorSql.getErrorCode() == 1062
                            && mensaje != null
                            && mensaje.contains("uq_pedidos_mesa_pendiente")) {
                        throw new PedidoPendienteExistenteException(pedido.getNum_mesa(), errorSql);
                    }
                    if (errorSql.getErrorCode() == 1054 && mensaje != null && mensaje.contains("aplica_iva")) {
                        throw new DataAccessException("La base de datos requiere la migración fiscal de detalles antes de guardar pedidos.", errorSql);
                    }
                    if (errorSql.getErrorCode() == 1054) {
                        return registrarPedidoCompletoLegacy(conexion, pedido, detalles);
                    }
                    throw new DataAccessException("No se pudo guardar el pedido completo.", errorSql);
                }
                throw new ErrorAplicacionException("Falló el guardado completo del pedido.", error);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo abrir la conexión para guardar el pedido.", ex);
        }
    }

    @Override
    public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos) {
        validarEstructuraPedido(pedidoModificado, detallesNuevos);
        
        String sqlUpdatePedido = """
            UPDATE pedidos SET subtotal = ?, iva_porcentaje = ?, iva_monto = ?, total = ?, 
            tasa_cambio = ?, subtotal_bs = ?, iva_bs = ?, total_bs = ?, id_mesonero = ?, mesonero_nombre = ?,
            version_precio = version_precio + 1
            WHERE id = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL
            """;
        String sqlDeleteDetalles = "DELETE FROM detalle_pedidos WHERE id_pedido = ?";
        String sqlInsertDetalle = """
            INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido, aplica_iva, id_plato, moneda_base, precio_incluye_iva, iva_porcentaje)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conexion = conexiones.getConnection()) {
            conexion.setAutoCommit(false);
            calcularYConsolidarSnapshot(conexion, pedidoModificado, detallesNuevos);

            try (PreparedStatement updatePed = conexion.prepareStatement(sqlUpdatePedido);
                 PreparedStatement delDetalles = conexion.prepareStatement(sqlDeleteDetalles);
                 PreparedStatement insDetalle = conexion.prepareStatement(sqlInsertDetalle)) {
                
                updatePed.setBigDecimal(1, pedidoModificado.getSubtotal());
                updatePed.setBigDecimal(2, pedidoModificado.getIvaPorcentaje());
                updatePed.setBigDecimal(3, pedidoModificado.getIvaMonto());
                updatePed.setBigDecimal(4, pedidoModificado.getTotalDecimal());
                updatePed.setBigDecimal(5, pedidoModificado.getTasaCambio());
                updatePed.setBigDecimal(6, pedidoModificado.getSubtotalBs());
                updatePed.setBigDecimal(7, pedidoModificado.getIvaBs());
                updatePed.setBigDecimal(8, pedidoModificado.getTotalBs());
                if (pedidoModificado.getIdMesonero() != null && pedidoModificado.getIdMesonero() > 0) {
                    updatePed.setInt(9, pedidoModificado.getIdMesonero());
                    updatePed.setString(10, pedidoModificado.getMesoneroNombre());
                } else {
                    updatePed.setNull(9, java.sql.Types.INTEGER);
                    updatePed.setNull(10, java.sql.Types.VARCHAR);
                }
                updatePed.setInt(11, idPedido);
                
                int filasAfectadas = updatePed.executeUpdate();
                if (filasAfectadas == 0) {
                    throw new SQLException("El pedido ya no existe, no está PENDIENTE o ya ha sido cerrado.");
                }
                
                delDetalles.setInt(1, idPedido);
                delDetalles.executeUpdate();
                
                for (DetallePedido det : detallesNuevos) {
                    insDetalle.setString(1, det.getNombre());
                    insDetalle.setBigDecimal(2, det.getPrecioDecimal());
                    insDetalle.setInt(3, det.getCantidad());
                    insDetalle.setString(4, det.getComentario() == null ? "" : det.getComentario());
                    insDetalle.setInt(5, idPedido);
                    insDetalle.setBoolean(6, det.getAplicaIva() == null || det.getAplicaIva());
                    if (det.getIdPlato() != null && det.getIdPlato() > 0) {
                        insDetalle.setInt(7, det.getIdPlato());
                    } else {
                        insDetalle.setNull(7, java.sql.Types.INTEGER);
                    }
                    insDetalle.setString(8, det.getMonedaBase() != null ? det.getMonedaBase() : "USD");
                    if (det.getPrecioIncluyeIva() != null) {
                        insDetalle.setInt(9, det.getPrecioIncluyeIva() ? 1 : 0);
                    } else {
                        insDetalle.setNull(9, java.sql.Types.TINYINT);
                    }
                    if (det.getIvaPorcentaje() != null) {
                        insDetalle.setBigDecimal(10, det.getIvaPorcentaje());
                    } else {
                        insDetalle.setNull(10, java.sql.Types.DECIMAL);
                    }
                    insDetalle.addBatch();
                }
                insDetalle.executeBatch();
                conexion.commit();
                return true;
            } catch (SQLException error) {
                conexion.rollback();
                if (error.getErrorCode() == 1054 && error.getMessage() != null && error.getMessage().contains("aplica_iva")) {
                    throw new DataAccessException("La base de datos requiere la migración fiscal de detalles antes de actualizar pedidos.", error);
                }
                if (error.getErrorCode() == 1054) {
                    return actualizarPedidoCompletoLegacy(conexion, idPedido, pedidoModificado, detallesNuevos);
                }
                throw error;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar el pedido completo.", ex);
        }
    }

    private boolean actualizarPedidoCompletoLegacy(Connection conexion, int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos) throws SQLException {
        String sqlUpdatePedido = """
            UPDATE pedidos SET subtotal = ?, iva_porcentaje = ?, iva_monto = ?, total = ?, 
            tasa_cambio = ?, subtotal_bs = ?, iva_bs = ?, total_bs = ?
            WHERE id = ? AND estado = 'PENDIENTE'
            """;
        String sqlDeleteDetalles = "DELETE FROM detalle_pedidos WHERE id_pedido = ?";
        String sqlInsertDetalle = "INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido, aplica_iva) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement updatePed = conexion.prepareStatement(sqlUpdatePedido);
             PreparedStatement delDetalles = conexion.prepareStatement(sqlDeleteDetalles);
             PreparedStatement insDetalle = conexion.prepareStatement(sqlInsertDetalle)) {
             
             updatePed.setBigDecimal(1, pedidoModificado.getSubtotal());
             updatePed.setBigDecimal(2, pedidoModificado.getIvaPorcentaje());
             updatePed.setBigDecimal(3, pedidoModificado.getIvaMonto());
             updatePed.setBigDecimal(4, pedidoModificado.getTotalDecimal());
             updatePed.setBigDecimal(5, pedidoModificado.getTasaCambio());
             updatePed.setBigDecimal(6, pedidoModificado.getSubtotalBs());
             updatePed.setBigDecimal(7, pedidoModificado.getIvaBs());
             updatePed.setBigDecimal(8, pedidoModificado.getTotalBs());
             updatePed.setInt(9, idPedido);
             
             int filasAfectadas = updatePed.executeUpdate();
             if (filasAfectadas == 0) {
                 throw new SQLException("El pedido ya no existe o no está PENDIENTE.");
             }
             
             delDetalles.setInt(1, idPedido);
             delDetalles.executeUpdate();
             
             for (DetallePedido det : detallesNuevos) {
                 insDetalle.setString(1, det.getNombre());
                 insDetalle.setBigDecimal(2, det.getPrecioDecimal());
                 insDetalle.setInt(3, det.getCantidad());
                 insDetalle.setString(4, det.getComentario() == null ? "" : det.getComentario());
                 insDetalle.setInt(5, idPedido);
                 insDetalle.setBoolean(6, det.getAplicaIva());
                 insDetalle.addBatch();
             }
             insDetalle.executeBatch();
             conexion.commit();
             return true;
        } catch (SQLException error) {
            conexion.rollback();
            throw error;
        }
    }

    private int registrarPedidoCompletoLegacy(Connection conexion, Pedidos pedido, List<DetallePedido> detalles) throws SQLException {
        String sqlPedido = """
            INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario, tasa_cambio, subtotal_bs, iva_bs, total_bs)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String sqlDetalle = """
            INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido, aplica_iva)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try {
            int idPedido;
            try (PreparedStatement sentenciaPedido = conexion.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                sentenciaPedido.setInt(1, pedido.getId_sala());
                sentenciaPedido.setInt(2, pedido.getNum_mesa());
                sentenciaPedido.setBigDecimal(3, importePersistible(pedido.getSubtotal(), "El subtotal del pedido"));
                sentenciaPedido.setBigDecimal(4, pedido.getIvaPorcentaje() != null ? pedido.getIvaPorcentaje() : BigDecimal.ZERO);
                sentenciaPedido.setBigDecimal(5, pedido.getIvaMonto() != null ? pedido.getIvaMonto() : BigDecimal.ZERO);
                sentenciaPedido.setBigDecimal(6, importePersistible(pedido.getTotalDecimal(), "El total del pedido"));
                sentenciaPedido.setString(7, pedido.getUsuario());
                sentenciaPedido.setBigDecimal(8, pedido.getTasaCambio());
                sentenciaPedido.setBigDecimal(9, pedido.getSubtotalBs());
                sentenciaPedido.setBigDecimal(10, pedido.getIvaBs());
                sentenciaPedido.setBigDecimal(11, pedido.getTotalBs());
                if (sentenciaPedido.executeUpdate() != 1) {
                    throw new SQLException("No se pudo insertar el encabezado del pedido legacy.");
                }
                try (ResultSet claves = sentenciaPedido.getGeneratedKeys()) {
                    if (!claves.next()) {
                        throw new SQLException("La base de datos no devolvió el ID del pedido.");
                    }
                    idPedido = claves.getInt(1);
                }
            }

            try (PreparedStatement sentenciaDetalle = conexion.prepareStatement(sqlDetalle)) {
                for (DetallePedido detalle : detalles) {
                    sentenciaDetalle.setString(1, detalle.getNombre());
                    sentenciaDetalle.setBigDecimal(2, importePersistible(
                            detalle.getPrecioDecimal(), "El precio de cada plato"));
                    sentenciaDetalle.setInt(3, detalle.getCantidad());
                    sentenciaDetalle.setString(4, detalle.getComentario());
                    sentenciaDetalle.setInt(5, idPedido);
                    sentenciaDetalle.setBoolean(6, detalle.getAplicaIva());
                    if (sentenciaDetalle.executeUpdate() != 1) {
                        throw new SQLException("No se pudo insertar uno de los detalles del pedido.");
                    }
                }
            }
            conexion.commit();
            return idPedido;
        } catch (SQLException ex) {
            try {
                conexion.rollback();
            } catch (SQLException rb) {
                ex.addSuppressed(rb);
            }
            String mensaje = ex.getMessage();
            if (ex.getErrorCode() == 1062
                    && mensaje != null
                    && mensaje.contains("uq_pedidos_mesa_pendiente")) {
                throw new PedidoPendienteExistenteException(pedido.getNum_mesa(), ex);
            }
            throw new DataAccessException("No se pudo guardar el pedido completo (modo compatibilidad).", ex);
        }
    }

    /**
     * Regla de negocio: la mesa debe existir en la sala (1..salas.mesas).
     * Si la sala no se puede leer (mocks/tests), no bloquea: el INSERT o la FK decidirán.
     */
    private void validarMesaEnSala(int idSala, int numMesa) {
        try (Connection conexion = conexiones.getConnection();
             PreparedStatement st = conexion.prepareStatement("SELECT mesas FROM salas WHERE id = ?")) {
            st.setInt(1, idSala);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    int max = rs.getInt(1);
                    if (!rs.wasNull() && (numMesa < 1 || numMesa > max)) {
                        throw ErrorAplicacionException.validacion(
                                "La mesa " + numMesa + " no existe en la sala seleccionada (1.." + max + ").");
                    }
                }
            }
        } catch (ErrorAplicacionException ex) {
            throw ex;
        } catch (Exception ex) {
            Logger.getLogger(PedidosDao.class.getName())
                    .log(java.util.logging.Level.FINE, "Sin validar rango de mesa: " + ex.getMessage());
        }
    }

    private void validarEstructuraPedido(Pedidos pedido, List<DetallePedido> detalles) {
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("El pedido es obligatorio.");
        }
        boolean rapido = pedido.esPedidoRapido();
        if ((!rapido && (pedido.getId_sala() <= 0 || pedido.getNum_mesa() <= 0))
                || (rapido && (pedido.getId_sala() != 0 || pedido.getNum_mesa() != 0))
                || pedido.getUsuario() == null || pedido.getUsuario().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La sala, mesa y usuario del pedido deben ser válidos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()
                    || detalle.getCantidad() <= 0
                    || detalle.getCantidad() > DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
                throw ErrorAplicacionException.validacion("Cada detalle debe tener nombre y una cantidad entre 1 y "
                        + DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
            }
            importePersistible(detalle.getPrecioDecimal(), "El precio de cada plato");
        }

        BigDecimal tasa = (pedido.getTasaCambio() != null && pedido.getTasaCambio().signum() > 0)
                ? pedido.getTasaCambio() : new BigDecimal("36.5000");

        List<DetallePedido> detallesNormalizados = new ArrayList<>(detalles.size());
        for (DetallePedido d : detalles) {
            DetallePedido clon = new DetallePedido();
            clon.setPrecioDecimal(d.getPrecioDecimal());
            clon.setCantidad(d.getCantidad());
            clon.setMonedaBase(d.getMonedaBase() != null && !d.getMonedaBase().isBlank() ? d.getMonedaBase() : "USD");
            clon.setAplicaIva(d.getAplicaIva() != null ? d.getAplicaIva() : Boolean.TRUE);
            clon.setPrecioIncluyeIva(d.getPrecioIncluyeIva() != null ? d.getPrecioIncluyeIva() : Boolean.FALSE);
            clon.setIvaPorcentaje(d.getIvaPorcentaje() != null ? d.getIvaPorcentaje()
                    : (Boolean.TRUE.equals(clon.getAplicaIva())
                            ? (pedido.getIvaPorcentaje() != null ? pedido.getIvaPorcentaje() : new BigDecimal("16.00"))
                            : BigDecimal.ZERO.setScale(2)));
            detallesNormalizados.add(clon);
        }

        CalculoPedido preliminar = CalculoMonetarioPedido.calcularPedido(detallesNormalizados, tasa);
        BigDecimal subtotalEsperado = preliminar.subtotalUsd();
        if (pedido.getSubtotal() != null) {
            BigDecimal subtotalEnviado = pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP);
            if (subtotalEnviado.compareTo(subtotalEsperado) != 0) {
                throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
            }
        }

        BigDecimal totalEnviado = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEsperado = preliminar.totalUsd();
        if (totalEnviado.compareTo(totalEsperado) != 0 && totalEnviado.compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con el subtotal más IVA ni con sus detalles.");
        }

        validarMesaEnSala(pedido.getId_sala(), pedido.getNum_mesa());
    }

    private CalculoPedido calcularYConsolidarSnapshot(
            Connection conexion, Pedidos pedido, List<DetallePedido> detalles) throws SQLException {
        validarEstructuraPedido(pedido, detalles);

        // Si la conexión está disponible y hay id_plato, fijar datos oficiales desde el catálogo
        if (conexion != null) {
            String sqlPlato = """
                SELECT nombre, moneda_base, precio_usd, precio_ves, aplica_iva, precio_incluye_iva, activo, precio_clasificado
                FROM platos WHERE id = ?
                """;
            try (PreparedStatement psPlato = conexion.prepareStatement(sqlPlato)) {
                for (DetallePedido det : detalles) {
                    if (det.getIdPlato() != null && det.getIdPlato() > 0) {
                        psPlato.setInt(1, det.getIdPlato());
                        try (ResultSet rs = psPlato.executeQuery()) {
                            if (!rs.next() || rs.getInt("activo") == 0) {
                                throw ErrorAplicacionException.validacion(
                                        "El producto '" + det.getNombre() + "' ya no está disponible en el catálogo.");
                            }
                            det.setNombre(rs.getString("nombre"));
                            String mb = rs.getString("moneda_base");
                            if (mb == null || mb.isBlank()) mb = "USD";
                            det.setMonedaBase(mb);
                            det.setAplicaIva(rs.getInt("aplica_iva") != 0);
                            int incIva = rs.getInt("precio_incluye_iva");
                            det.setPrecioIncluyeIva(!rs.wasNull() && incIva != 0);
                            BigDecimal precioCat = "VES".equalsIgnoreCase(mb) ? rs.getBigDecimal("precio_ves") : rs.getBigDecimal("precio_usd");
                            if (precioCat != null && precioCat.signum() > 0) {
                                det.setPrecioDecimal(precioCat);
                            }
                        }
                    } else {
                        if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) {
                            det.setMonedaBase("USD");
                        }
                        if (det.getAplicaIva() == null) {
                            det.setAplicaIva(Boolean.TRUE);
                        }
                        if (det.getPrecioIncluyeIva() == null) {
                            det.setPrecioIncluyeIva(Boolean.FALSE);
                        }
                    }
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1054) {
                    throw ex;
                }
            }
        }

        for (DetallePedido det : detalles) {
            if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) {
                det.setMonedaBase("USD");
            }
            if (det.getAplicaIva() == null) {
                det.setAplicaIva(Boolean.TRUE);
            }
            if (det.getPrecioIncluyeIva() == null) {
                det.setPrecioIncluyeIva(Boolean.FALSE);
            }
            if (det.getIvaPorcentaje() == null) {
                det.setIvaPorcentaje(det.getAplicaIva()
                        ? (pedido.getIvaPorcentaje() != null ? pedido.getIvaPorcentaje() : new BigDecimal("16.00"))
                        : BigDecimal.ZERO.setScale(2));
            }
        }

        BigDecimal tasa = (pedido.getTasaCambio() != null && pedido.getTasaCambio().signum() > 0)
                ? pedido.getTasaCambio() : new BigDecimal("36.5000");

        CalculoPedido resultado = CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        if (pedido.getTotalDecimal() != null && pedido.getTotalDecimal().signum() > 0) {
            BigDecimal totalEnviado = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalEsperado = resultado.totalUsd();
            BigDecimal subtotalEsperado = resultado.subtotalUsd();
            if (totalEnviado.compareTo(totalEsperado) != 0 && totalEnviado.compareTo(subtotalEsperado) != 0) {
                throw ErrorAplicacionException.validacion("El total del pedido no coincide con el subtotal más IVA ni con sus detalles.");
            }
        }

        pedido.setSubtotal(resultado.subtotalUsd());
        pedido.setIvaMonto(resultado.ivaUsd());
        pedido.setTotalDecimal(resultado.totalUsd());
        pedido.setTasaCambio(tasa);
        pedido.setSubtotalBs(resultado.subtotalVes());
        pedido.setIvaBs(resultado.ivaVes());
        pedido.setTotalBs(resultado.totalVes());

        return resultado;
    }

    private BigDecimal importePersistible(BigDecimal importe, String campo) {
        if (importe == null || importe.signum() < 0 || importe.scale() > 2
                || importe.precision() - importe.scale() > 8) {
            throw ErrorAplicacionException.validacion(campo + " no debe ser negativo y caber en DECIMAL(10,2).");
        }
        try {
            return importe.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ErrorAplicacionException.validacion(campo + " debe poder representarse con dos decimales.");
        }
    }

    public List<DetallePedido> verPedidoDetalle(int id_pedido) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
        List<DetallePedido> lista = new ArrayList<>();
        String sql = """
            SELECT d.id, d.nombre, d.precio, d.cantidad, d.comentario, d.id_pedido, d.aplica_iva,
                   d.id_plato, d.moneda_base, d.precio_incluye_iva, d.iva_porcentaje,
                   d.precio_congelado, d.congelado_motivo, d.congelado_por, d.congelado_en
            FROM pedidos p
            INNER JOIN detalle_pedidos d ON p.id = d.id_pedido
            WHERE p.id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id_pedido);
            try (ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    DetallePedido det = new DetallePedido();
                    det.setId(resultados.getInt("id"));
                    det.setNombre(resultados.getString("nombre"));
                    det.setPrecioDecimal(resultados.getBigDecimal("precio"));
                    det.setCantidad(resultados.getInt("cantidad"));
                    det.setComentario(resultados.getString("comentario"));
                    boolean aplicaIva = resultados.getBoolean("aplica_iva");
                    det.setAplicaIva(resultados.wasNull() ? null : aplicaIva);
                    
                    int idPlato = resultados.getInt("id_plato");
                    det.setIdPlato(resultados.wasNull() ? null : idPlato);
                    det.setMonedaBase(resultados.getString("moneda_base"));
                    int incIva = resultados.getInt("precio_incluye_iva");
                    det.setPrecioIncluyeIva(resultados.wasNull() ? null : (incIva != 0));
                    det.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                    det.setPrecioCongelado(resultados.getInt("precio_congelado") != 0);
                    det.setCongeladoMotivo(resultados.getString("congelado_motivo"));
                    det.setCongeladoPor(resultados.getString("congelado_por"));
                    java.sql.Timestamp tsCong = resultados.getTimestamp("congelado_en");
                    if (tsCong != null) {
                        det.setCongeladoEn(tsCong.toLocalDateTime());
                    }
                    lista.add(det);
                }
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return verPedidoDetalleLegacy(id_pedido);
            }
            throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
        }
        if (lista.isEmpty()) {
            throw new ErrorAplicacionException(
                    "El pedido " + id_pedido + " no tiene detalles asociados.",
                    new IllegalStateException("Un pedido debe contener al menos un detalle."));
        }
        return lista;
    }

    private List<DetallePedido> verPedidoDetalleLegacy(int id_pedido) {
        List<DetallePedido> lista = new ArrayList<>();
        String sql = """
            SELECT d.id, d.nombre, d.precio, d.cantidad, d.comentario, d.id_pedido, d.aplica_iva
            FROM pedidos p
            INNER JOIN detalle_pedidos d ON p.id = d.id_pedido
            WHERE p.id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id_pedido);
            try (ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    DetallePedido det = new DetallePedido();
                    det.setId(resultados.getInt("id"));
                    det.setNombre(resultados.getString("nombre"));
                    det.setPrecioDecimal(resultados.getBigDecimal("precio"));
                    det.setCantidad(resultados.getInt("cantidad"));
                    det.setComentario(resultados.getString("comentario"));
                    boolean aplicaIva = resultados.getBoolean("aplica_iva");
                    det.setAplicaIva(resultados.wasNull() ? null : aplicaIva);
                    lista.add(det);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron consultar los detalles legacy del pedido.", ex);
        }
        if (lista.isEmpty()) {
            throw new ErrorAplicacionException(
                    "El pedido " + id_pedido + " no tiene detalles asociados.",
                    new IllegalStateException("Un pedido debe contener al menos un detalle."));
        }
        return lista;
    }

    public Pedidos verPedido(int id_pedido) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
        Pedidos ped = null;
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.tipo_pedido, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.tasa_es_estimada, p.usuario, p.estado, COALESCE(s.nombre, 'Pedido rápido') AS nombre_sala,
                   p.cliente_nombre, p.cliente_documento, p.metodo_pago, p.efectivo_bs, p.efectivo_usd, p.id_mesonero, p.mesonero_nombre, p.cerrada_en, p.version_precio
            FROM pedidos p
            LEFT JOIN salas s ON p.id_sala = s.id
            WHERE p.id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id_pedido);
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    ped = new Pedidos();
                    ped.setId(resultados.getInt("id"));
                    ped.setId_sala(resultados.getInt("id_sala"));
                    ped.setTipoPedido(resultados.getString("tipo_pedido"));
                    ped.setFecha(resultados.getString("fecha"));
                    ped.setSala(resultados.getString("nombre_sala"));
                    ped.setNum_mesa(resultados.getInt("num_mesa"));
                    ped.setSubtotal(resultados.getBigDecimal("subtotal"));
                    ped.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                    ped.setIvaMonto(resultados.getBigDecimal("iva_monto"));
                    ped.setTotalDecimal(resultados.getBigDecimal("total"));
                    ped.setSubtotalBs(resultados.getBigDecimal("subtotal_bs"));
                    ped.setIvaBs(resultados.getBigDecimal("iva_bs"));
                    ped.setTotalBs(resultados.getBigDecimal("total_bs"));
                    ped.setUsuario(resultados.getString("usuario"));
                    ped.setEstado(resultados.getString("estado"));
                    ped.setTasaCambio(resultados.getBigDecimal("tasa_cambio"));
                    try {
                        ped.setTasaEsEstimada(resultados.getInt("tasa_es_estimada") == 1);
                    } catch (SQLException ignored) {}
                    try {
                        ped.setClienteNombre(resultados.getString("cliente_nombre"));
                        ped.setClienteDocumento(resultados.getString("cliente_documento"));
                        ped.setMetodoPago(resultados.getString("metodo_pago"));
                        ped.setEfectivoBs(resultados.getBigDecimal("efectivo_bs"));
                        ped.setEfectivoUsd(resultados.getBigDecimal("efectivo_usd"));
                        int idM = resultados.getInt("id_mesonero");
                        if (!resultados.wasNull()) {
                            ped.setIdMesonero(idM);
                        }
                        ped.setMesoneroNombre(resultados.getString("mesonero_nombre"));
                        java.sql.Timestamp tsCierre = resultados.getTimestamp("cerrada_en");
                        if (tsCierre != null) {
                            ped.setCerradaEn(tsCierre.toLocalDateTime());
                        }
                        ped.setVersionPrecio(resultados.getInt("version_precio"));
                    } catch (SQLException ignoreCol) {}
                }
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return verPedidoLegacy(id_pedido);
            }
            throw new DataAccessException("No se pudo consultar el pedido.", ex);
        }
        if (ped == null) {
            throw new ErrorAplicacionException(
                    "No existe el pedido " + id_pedido + ".",
                    new IllegalStateException("La consulta no encontró el pedido solicitado."));
        }
        ped.setPagos(obtenerPagosPedido(id_pedido));
        return ped;
    }

    private List<PagoPedido> obtenerPagosPedido(int idPedido) {
        List<PagoPedido> pagos = new ArrayList<>();
        try (Connection conexion = conexiones.getConnection();
             PreparedStatement ps = conexion.prepareStatement(
                     "SELECT metodo_pago, moneda, monto FROM pedido_pagos WHERE id_pedido = ? ORDER BY id")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pagos.add(new PagoPedido(rs.getString("metodo_pago"), rs.getString("moneda"), rs.getBigDecimal("monto")));
                }
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1146 || "42S02".equals(ex.getSQLState())) {
                LOGGER.fine("La BD legacy aún no tiene desglose pedido_pagos.");
                return pagos;
            }
            throw new DataAccessException("No se pudo consultar el desglose del pago.", ex);
        }
        return pagos;
    }

    private Pedidos verPedidoLegacy(int id_pedido) {
        Pedidos ped = null;
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, COALESCE(s.nombre, 'Sala no disponible') AS nombre_sala
            FROM pedidos p
            LEFT JOIN salas s ON p.id_sala = s.id
            WHERE p.id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id_pedido);
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    ped = new Pedidos();
                    ped.setId(resultados.getInt("id"));
                    ped.setId_sala(resultados.getInt("id_sala"));
                    ped.setFecha(resultados.getString("fecha"));
                    ped.setSala(resultados.getString("nombre_sala"));
                    ped.setNum_mesa(resultados.getInt("num_mesa"));
                    ped.setSubtotal(resultados.getBigDecimal("subtotal"));
                    ped.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                    ped.setIvaMonto(resultados.getBigDecimal("iva_monto"));
                    ped.setTotalDecimal(resultados.getBigDecimal("total"));
                    ped.setSubtotalBs(resultados.getBigDecimal("subtotal_bs"));
                    ped.setIvaBs(resultados.getBigDecimal("iva_bs"));
                    ped.setTotalBs(resultados.getBigDecimal("total_bs"));
                    ped.setUsuario(resultados.getString("usuario"));
                    ped.setEstado(resultados.getString("estado"));
                    ped.setTasaCambio(resultados.getBigDecimal("tasa_cambio"));
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar el pedido legacy.", ex);
        }
        if (ped == null) {
            throw new ErrorAplicacionException(
                    "No existe el pedido " + id_pedido + ".",
                    new IllegalStateException("La consulta no encontró el pedido solicitado."));
        }
        return ped;
    }

    public List<DetallePedido> finalizarPedido(int id_pedido) {
        return verPedidoDetalle(id_pedido);
    }

    @Override
    public boolean actualizarEstado(int id_pedido) {
        return actualizarEstadoConCliente(id_pedido, "Consumidor Final", "V-00000000");
    }

    @Override
    public boolean actualizarEstadoConCliente(int id_pedido, String clienteNombre, String clienteDocumento) {
        return actualizarEstadoConCliente(id_pedido, clienteNombre, clienteDocumento, "EFECTIVO");
    }

    @Override
    public boolean actualizarEstadoConCliente(int id_pedido, String clienteNombre, String clienteDocumento, String metodoPago) {
        return actualizarEstadoConCliente(id_pedido, clienteNombre, clienteDocumento, metodoPago, null, null);
    }

    @Override
    public boolean actualizarEstadoConCliente(int id_pedido, String clienteNombre, String clienteDocumento,
            String metodoPago, List<PagoPedido> pagos) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para finalizarlo.");
        }
        if (pagos == null || pagos.isEmpty()) {
            throw ErrorAplicacionException.validacion("Registre al menos un medio de pago para finalizar.");
        }
        String doc = clienteDocumento == null || clienteDocumento.trim().isEmpty() ? "V-00000000" : clienteDocumento.trim();
        String nom = clienteNombre == null || clienteNombre.trim().isEmpty() ? "Consumidor Final" : clienteNombre.trim();
        try (Connection conexion = conexiones.getConnection()) {
            boolean autoCommitOriginal = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try {
                BigDecimal totalBs;
                BigDecimal tasa;
                int versionPrecio;
                try (PreparedStatement ps = conexion.prepareStatement(
                        "SELECT total, total_bs, tasa_cambio, cerrada_en, version_precio FROM pedidos WHERE id = ? AND estado = 'PENDIENTE' FOR UPDATE")) {
                    ps.setInt(1, id_pedido);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            boolean yaFinalizado = validarMotivoNoFinalizado(conexion, id_pedido);
                            conexion.rollback();
                            return yaFinalizado;
                        }
                        tasa = rs.getBigDecimal("tasa_cambio");
                        if (tasa == null || tasa.signum() <= 0) {
                            throw ErrorAplicacionException.validacion(
                                    "No se puede cobrar: el pedido no tiene una tasa de cambio válida guardada en la BD.");
                        }
                        totalBs = rs.getBigDecimal("total_bs");
                        if (totalBs == null) {
                            totalBs = rs.getBigDecimal("total").multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                        }
                        versionPrecio = rs.getInt("version_precio");
                    }
                }

                BigDecimal recibidoBs = BigDecimal.ZERO.setScale(2);
                BigDecimal efectivoBs = BigDecimal.ZERO.setScale(2);
                BigDecimal efectivoUsd = BigDecimal.ZERO.setScale(2);
                BigDecimal aplicadoVes = BigDecimal.ZERO.setScale(2);
                BigDecimal aplicadoUsd = BigDecimal.ZERO.setScale(2);

                for (PagoPedido pago : pagos) {
                    if (pago == null) {
                        throw ErrorAplicacionException.validacion("Complete cada línea del desglose de pago.");
                    }
                    BigDecimal aplicado = pago.montoAplicado() != null ? pago.montoAplicado() : pago.monto();
                    recibidoBs = recibidoBs.add(pago.equivalenteBs(tasa));
                    if ("VES".equals(pago.moneda())) {
                        aplicadoVes = aplicadoVes.add(aplicado);
                        if ("EFECTIVO_BS".equals(pago.metodo())) efectivoBs = efectivoBs.add(aplicado);
                    } else {
                        aplicadoUsd = aplicadoUsd.add(aplicado);
                        if ("EFECTIVO_USD".equals(pago.metodo())) efectivoUsd = efectivoUsd.add(aplicado);
                    }
                }
                String metodoResumen = pagos.size() == 1 ? pagos.getFirst().metodo() : "MIXTO";
                if (metodoPago != null && !metodoPago.isBlank()
                        && !"MIXTO".equalsIgnoreCase(metodoPago)
                        && !metodoResumen.equalsIgnoreCase(metodoPago)) {
                    throw ErrorAplicacionException.validacion("El método seleccionado no coincide con el detalle del pago.");
                }
                recibidoBs = recibidoBs.setScale(2, RoundingMode.HALF_UP);
                BigDecimal ajusteCambioBs = totalBs.setScale(2, RoundingMode.HALF_UP).subtract(recibidoBs);

                if (aplicadoUsd.signum() == 0) {
                    if (ajusteCambioBs.signum() != 0) {
                        throw ErrorAplicacionException.validacion("El desglose debe completar exactamente Bs. "
                                + totalBs.setScale(2, RoundingMode.HALF_UP).toPlainString()
                                + "; se registraron Bs. " + recibidoBs.toPlainString() + ".");
                    }
                } else {
                    // Tolerancia de ajuste cambiario por redondeo FX: medio centavo USD (máx ~ 0.005 * tasa) o 0.05 Bs
                    BigDecimal maxTolBs = new BigDecimal("0.005").multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    if (maxTolBs.compareTo(new BigDecimal("0.05")) < 0) {
                        maxTolBs = new BigDecimal("0.05");
                    }
                    if (ajusteCambioBs.abs().compareTo(maxTolBs) > 0) {
                        throw ErrorAplicacionException.validacion("El desglose debe completar exactamente Bs. "
                                + totalBs.setScale(2, RoundingMode.HALF_UP).toPlainString()
                                + "; se registraron Bs. " + recibidoBs.toPlainString() + ".");
                    }
                }

                String sqlActualizar = """
                    UPDATE pedidos
                    SET estado = 'FINALIZADO', fecha = CURRENT_TIMESTAMP, cliente_nombre = ?, cliente_documento = ?, metodo_pago = ?,
                        efectivo_bs = ?, efectivo_usd = ?, cerrada_en = COALESCE(cerrada_en, CURRENT_TIMESTAMP)
                    WHERE id = ? AND estado = 'PENDIENTE'
                    """;
                try (PreparedStatement ps = conexion.prepareStatement(sqlActualizar)) {
                    ps.setString(1, nom);
                    ps.setString(2, doc);
                    ps.setString(3, metodoResumen);
                    ps.setBigDecimal(4, efectivoBs);
                    ps.setBigDecimal(5, efectivoUsd);
                    ps.setInt(6, id_pedido);
                    int filas = ps.executeUpdate();
                    if (filas == 0) {
                        boolean yaFinalizado = validarMotivoNoFinalizado(conexion, id_pedido);
                        conexion.rollback();
                        return yaFinalizado;
                    }
                    ErrorAplicacionException.resultadoUnaFila(filas, "finalizar pedido " + id_pedido);
                }

                long idLiquidacion = 0;
                try {
                    String sqlLiq = """
                        INSERT INTO pedido_liquidaciones
                            (id_pedido, version_precio, saldo_restante_ves, aplicado_ves, aplicado_usd, tasa_cambio,
                             ajuste_cambio_bs, diferencia_usd, version_regla, actor, idempotency_key)
                        VALUES (?, ?, 0.00, ?, ?, ?, ?, 0.00, 'V5_PAGO_CONGELADO', ?, ?)
                        """;
                    String idempLiq = "LIQ-" + id_pedido + "-V" + versionPrecio + "-" + System.currentTimeMillis();
                    try (PreparedStatement psLiq = conexion.prepareStatement(sqlLiq, Statement.RETURN_GENERATED_KEYS)) {
                        psLiq.setInt(1, id_pedido);
                        psLiq.setInt(2, versionPrecio);
                        psLiq.setBigDecimal(3, aplicadoVes);
                        psLiq.setBigDecimal(4, aplicadoUsd);
                        psLiq.setBigDecimal(5, tasa);
                        psLiq.setBigDecimal(6, ajusteCambioBs);
                        psLiq.setString(7, nom);
                        psLiq.setString(8, idempLiq);
                        psLiq.executeUpdate();
                        try (ResultSet rsLiq = psLiq.getGeneratedKeys()) {
                            if (rsLiq.next()) {
                                idLiquidacion = rsLiq.getLong(1);
                            }
                        }
                    }
                } catch (SQLException exLiq) {
                    LOGGER.fine("Tabla pedido_liquidaciones no disponible o ya registrada: " + exLiq.getMessage());
                }

                String sqlPago = """
                    INSERT INTO pedido_pagos
                        (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd,
                         monto_recibido, monto_aplicado, cambio_devuelto, es_legado, id_liquidacion, idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """;
                try (PreparedStatement ps = conexion.prepareStatement(sqlPago)) {
                    int idx = 0;
                    for (PagoPedido pago : pagos) {
                        idx++;
                        BigDecimal montoRec = pago.montoRecibido() != null ? pago.montoRecibido() : pago.monto();
                        BigDecimal montoApl = pago.montoAplicado() != null ? pago.montoAplicado() : pago.monto();
                        BigDecimal cambio = pago.cambioDevuelto() != null ? pago.cambioDevuelto() : BigDecimal.ZERO;
                        String idempPago = "PAGO-" + id_pedido + "-" + idx + "-V" + versionPrecio + "-" + System.currentTimeMillis();

                        ps.setInt(1, id_pedido);
                        ps.setString(2, pago.metodo());
                        ps.setString(3, pago.moneda());
                        ps.setBigDecimal(4, montoApl);
                        ps.setBigDecimal(5, tasa);
                        ps.setBigDecimal(6, pago.equivalenteBs(tasa));
                        ps.setBigDecimal(7, pago.equivalenteUsd(tasa));
                        ps.setBigDecimal(8, montoRec);
                        ps.setBigDecimal(9, montoApl);
                        ps.setBigDecimal(10, cambio);
                        if (idLiquidacion > 0) {
                            ps.setLong(11, idLiquidacion);
                        } else {
                            ps.setNull(11, Types.BIGINT);
                        }
                        ps.setString(12, idempPago);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conexion.prepareStatement("""
                        INSERT INTO clientes (documento, nombre) VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE nombre = VALUES(nombre)
                        """)) {
                    ps.setString(1, doc);
                    ps.setString(2, nom);
                    ps.executeUpdate();
                } catch (SQLException ign) {
                    // La factura no se bloquea si la tabla de clientes no existe en una BD legacy.
                }
                conexion.commit();
                return true;
            } catch (RuntimeException | SQLException ex) {
                try {
                    conexion.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new DataAccessException("No se pudo guardar el detalle del pago.", ex);
            } finally {
                try {
                    conexion.setAutoCommit(autoCommitOriginal);
                } catch (SQLException ex) {
                    LOGGER.warning("No se pudo restaurar autoCommit luego de finalizar pago mixto: " + ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo finalizar el pedido con el desglose de pago.", ex);
        }
    }

    @Override
    public boolean actualizarEstadoConCliente(int id_pedido, String clienteNombre, String clienteDocumento,
            String metodoPago, BigDecimal efectivoBs, BigDecimal efectivoUsd) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para finalizarlo.");
        }
        String doc = clienteDocumento == null || clienteDocumento.trim().isEmpty() ? "V-00000000" : clienteDocumento.trim();
        String nom = clienteNombre == null || clienteNombre.trim().isEmpty() ? "Consumidor Final" : clienteNombre.trim();
        String pago = (metodoPago == null || metodoPago.trim().isEmpty()) ? "EFECTIVO" : metodoPago.trim().toUpperCase();
        Pedidos validacionPago = new Pedidos();
        validacionPago.setMetodoPago(pago);

        BigDecimal efectivoFinalBs = efectivoBs;
        BigDecimal efectivoFinalUsd = efectivoUsd;
        if ("MIXTO".equals(pago)) {
            if (efectivoBs == null || efectivoUsd == null) {
                throw ErrorAplicacionException.validacion("Para un pago mixto debe indicar el efectivo recibido en Bs. y USD (use 0 si no aplica).");
            }
            validacionPago.setEfectivoBs(efectivoBs);
            validacionPago.setEfectivoUsd(efectivoUsd);
            efectivoFinalBs = validacionPago.getEfectivoBs();
            efectivoFinalUsd = validacionPago.getEfectivoUsd();
        } else if (efectivoBs != null || efectivoUsd != null) {
            throw ErrorAplicacionException.validacion("El desglose de efectivo solo se admite para pagos MIXTO.");
        }

        String sql = """
            UPDATE pedidos
            SET estado = ?, fecha = CURRENT_TIMESTAMP, cliente_nombre = ?, cliente_documento = ?, metodo_pago = ?,
                efectivo_bs = CASE
                    WHEN ? = 'MIXTO' THEN ?
                    WHEN ? IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(total_bs, total * COALESCE(NULLIF(tasa_cambio, 0), 36.5000))
                    ELSE 0 END,
                efectivo_usd = CASE
                    WHEN ? = 'MIXTO' THEN ?
                    WHEN ? = 'EFECTIVO_USD' THEN total
                    ELSE 0 END,
                cerrada_en = COALESCE(cerrada_en, CURRENT_TIMESTAMP)
            WHERE id = ? AND estado = 'PENDIENTE'
            """;
        try (Connection conexion = conexiones.getConnection()) {
            boolean exito;
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setString(1, "FINALIZADO");
                sentencia.setString(2, nom);
                sentencia.setString(3, doc);
                sentencia.setString(4, pago);
                if ("MIXTO".equals(pago)) {
                    validarEfectivoMixto(conexion, id_pedido, efectivoFinalBs, efectivoFinalUsd);
                }
                sentencia.setString(5, pago);
                sentencia.setBigDecimal(6, efectivoFinalBs);
                sentencia.setString(7, pago);
                sentencia.setString(8, pago);
                sentencia.setBigDecimal(9, efectivoFinalUsd);
                sentencia.setString(10, pago);
                sentencia.setInt(11, id_pedido);
                int filas = sentencia.executeUpdate();
                if (filas == 0) {
                    return validarMotivoNoFinalizado(conexion, id_pedido);
                }
                exito = ErrorAplicacionException.resultadoUnaFila(
                        filas, "finalizar pedido " + id_pedido);
            }

            if (exito) {
                try {
                    String sqlCliente = """
                        INSERT INTO clientes (documento, nombre)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE nombre = VALUES(nombre)
                        """;
                    try (PreparedStatement sCli = conexion.prepareStatement(sqlCliente)) {
                        sCli.setString(1, doc);
                        sCli.setString(2, nom);
                        sCli.executeUpdate();
                    }
                } catch (SQLException ign) {
                    // No bloquear si la tabla clientes no está presente
                }
            }
            return exito;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo finalizar el pedido.", ex);
        }
    }

    private boolean validarMotivoNoFinalizado(Connection conexion, int id_pedido) throws SQLException {
        String sqlCheck = "SELECT estado FROM pedidos WHERE id = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sqlCheck)) {
            ps.setInt(1, id_pedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String est = rs.getString("estado");
                    if ("FINALIZADO".equalsIgnoreCase(est)) {
                        return true;
                    }
                } else {
                    throw ErrorAplicacionException.validacion(
                            "No existe el pedido " + id_pedido + " para finalizar.");
                }
            }
        }
        return false;
    }

    private void validarEfectivoMixto(Connection conexion, int idPedido,
            BigDecimal efectivoBs, BigDecimal efectivoUsd) throws SQLException {
        String sql = "SELECT total, total_bs, tasa_cambio FROM pedidos WHERE id = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ErrorAplicacionException.validacion("No existe el pedido " + idPedido + " para finalizar.");
                }
                BigDecimal totalUsd = rs.getBigDecimal("total");
                BigDecimal tasa = rs.getBigDecimal("tasa_cambio");
                if (tasa == null || tasa.signum() <= 0) {
                    tasa = new BigDecimal("36.5000");
                }
                BigDecimal totalBs = rs.getBigDecimal("total_bs");
                if (totalBs == null) {
                    totalBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal equivalenteBs = efectivoBs.add(efectivoUsd.multiply(tasa))
                        .setScale(2, RoundingMode.HALF_UP);
                if (equivalenteBs.compareTo(totalBs) != 0) {
                    throw ErrorAplicacionException.validacion("El desglose del pago mixto debe completar exactamente Bs. "
                            + totalBs.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
                }
            }
        }
    }

    @Override
    public boolean anularPedido(int id_pedido) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para anularlo.");
        }
        String sql = "UPDATE pedidos SET estado = 'ANULADO' WHERE id = ? AND estado != 'ANULADO'";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id_pedido);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                // Verificar si no existe o ya estaba anulado
                String sqlVerif = "SELECT estado FROM pedidos WHERE id = ?";
                try (PreparedStatement psVerif = con.prepareStatement(sqlVerif)) {
                    psVerif.setInt(1, id_pedido);
                    try (ResultSet rs = psVerif.executeQuery()) {
                        if (rs.next()) {
                            if ("ANULADO".equalsIgnoreCase(rs.getString("estado"))) {
                                throw ErrorAplicacionException.validacion("El pedido #" + id_pedido + " ya se encuentra anulado.");
                            }
                        } else {
                            throw ErrorAplicacionException.validacion("No existe el pedido #" + id_pedido + " para anular.");
                        }
                    }
                }
            }
            return filas > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo anular el pedido #" + id_pedido + ".", ex);
        }
    }

    @Override
    public boolean anularPedidoConAuditoria(int id_pedido, String motivo, String usuario) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para anularlo.");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la anulación del pedido.");
        }
        String usr = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "Sistema";

        String sqlUpdate = "UPDATE pedidos SET estado = 'ANULADO' WHERE id = ? AND estado != 'ANULADO'";
        String sqlAuditoria = """
            INSERT INTO auditoria_pedidos (id_pedido, accion, motivo, usuario, fecha_hora)
            VALUES (?, 'ANULACION', ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection con = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                // Si tiene pagos registrados, impedir anular a menos que tenga movimientos de devolución/crédito registrados
                try (PreparedStatement psPagos = con.prepareStatement(
                        "SELECT COUNT(*) FROM pedido_pagos WHERE id_pedido = ?")) {
                    psPagos.setInt(1, id_pedido);
                    try (ResultSet rs = psPagos.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            try (PreparedStatement psMov = con.prepareStatement(
                                    "SELECT COUNT(*) FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
                                psMov.setInt(1, id_pedido);
                                try (ResultSet rsMov = psMov.executeQuery()) {
                                    int cantMov = rsMov.next() ? rsMov.getInt(1) : 0;
                                    if (cantMov == 0) {
                                        throw ErrorAplicacionException.validacion(
                                                "El pedido #" + id_pedido + " tiene pagos registrados; se requiere conciliación o devolución trazable antes de anular.");
                                    }
                                }
                            }
                        }
                    }
                }

                int filas;
                try (PreparedStatement psUpdate = con.prepareStatement(sqlUpdate)) {
                    psUpdate.setInt(1, id_pedido);
                    filas = psUpdate.executeUpdate();
                }
                if (filas == 0) {
                    String sqlVerif = "SELECT estado FROM pedidos WHERE id = ?";
                    try (PreparedStatement psVerif = con.prepareStatement(sqlVerif)) {
                        psVerif.setInt(1, id_pedido);
                        try (ResultSet rs = psVerif.executeQuery()) {
                            if (rs.next()) {
                                if ("ANULADO".equalsIgnoreCase(rs.getString("estado"))) {
                                    throw ErrorAplicacionException.validacion("El pedido #" + id_pedido + " ya se encuentra anulado.");
                                }
                            } else {
                                throw ErrorAplicacionException.validacion("No existe el pedido #" + id_pedido + " para anular.");
                            }
                        }
                    }
                }

                try (PreparedStatement psAud = con.prepareStatement(sqlAuditoria)) {
                    psAud.setInt(1, id_pedido);
                    psAud.setString(2, motivo.trim());
                    psAud.setString(3, usr);
                    psAud.executeUpdate();
                }

                con.commit();
                return true;
            } catch (Exception ex) {
                con.rollback();
                if (ex instanceof ErrorAplicacionException eae) {
                    throw eae;
                }
                throw new DataAccessException("No se pudo anular el pedido de forma atómica: " + ex.getMessage(), ex);
            } finally {
                con.setAutoCommit(autoCommitPrevio);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error de conexión al anular el pedido: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean cerrarMesa(int idPedido, int versionEsperada) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para cerrar la mesa.");
        }
        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitOriginal = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                int versionActual;
                java.sql.Timestamp cerradaEn;
                String estado;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT id, estado, cerrada_en, version_precio FROM pedidos WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, idPedido);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ErrorAplicacionException.validacion("No existe el pedido #" + idPedido + ".");
                        }
                        estado = rs.getString("estado");
                        cerradaEn = rs.getTimestamp("cerrada_en");
                        versionActual = rs.getInt("version_precio");
                    }
                }

                if (!"PENDIENTE".equalsIgnoreCase(estado)) {
                    throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no está pendiente (estado actual: " + estado + ").");
                }

                if (cerradaEn != null) {
                    if (versionEsperada > 0 && versionActual != versionEsperada) {
                        throw ErrorAplicacionException.validacion("La mesa ya fue cerrada con una cotización distinta (versión " + versionActual + ").");
                    }
                    con.rollback();
                    return true;
                }

                if (versionEsperada > 0 && versionActual != versionEsperada) {
                    throw ErrorAplicacionException.validacion("La comanda fue modificada recientemente; recargue antes de cerrar (versión actual: "
                            + versionActual + ", esperada: " + versionEsperada + ").");
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM detalle_pedidos WHERE id_pedido = ? AND moneda_base IS NULL")) {
                    ps.setInt(1, idPedido);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw ErrorAplicacionException.validacion("La comanda #" + idPedido
                                    + " contiene productos heredados sin clasificar; asígneles moneda base antes de cerrar.");
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE pedidos SET cerrada_en = CURRENT_TIMESTAMP WHERE id = ? AND cerrada_en IS NULL")) {
                    ps.setInt(1, idPedido);
                    int filas = ps.executeUpdate();
                    if (filas == 0) {
                        con.rollback();
                        return true;
                    }
                }

                con.commit();
                return true;
            } catch (Exception ex) {
                try { con.rollback(); } catch (SQLException rEx) { ex.addSuppressed(rEx); }
                if (ex instanceof RuntimeException re) throw re;
                throw new DataAccessException("Error al cerrar la mesa del pedido #" + idPedido, ex);
            } finally {
                try { con.setAutoCommit(autoCommitOriginal); } catch (SQLException ignored) {}
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo conectar a la base de datos para cerrar la mesa.", ex);
        }
    }

    @Override
    public ResumenConciliacionPedido resumenConciliacion(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
        try (Connection con = conexiones.getConnection()) {
            boolean cerrado = false;
            int versionPrecio = 1;
            BigDecimal tasa = BigDecimal.ZERO;
            BigDecimal totalUsd = BigDecimal.ZERO;
            BigDecimal totalBs = BigDecimal.ZERO;

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT total, total_bs, tasa_cambio, cerrada_en, version_precio FROM pedidos WHERE id = ?")) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw ErrorAplicacionException.validacion("No existe el pedido #" + idPedido + ".");
                    }
                    totalUsd = rs.getBigDecimal("total");
                    tasa = rs.getBigDecimal("tasa_cambio");
                    totalBs = rs.getBigDecimal("total_bs");
                    if (tasa == null || tasa.signum() <= 0) {
                        tasa = new BigDecimal("36.5000");
                    }
                    if (totalBs == null) {
                        totalBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    }
                    cerrado = rs.getTimestamp("cerrada_en") != null;
                    versionPrecio = rs.getInt("version_precio");
                }
            }

            // P2-D: pagado_bs_aplicado usa exclusivamente monto_bs guardado en cada fila histórica
            BigDecimal pagadoBsAplicado = BigDecimal.ZERO.setScale(2);
            BigDecimal pagadoUsdAplicado = BigDecimal.ZERO.setScale(2);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COALESCE(SUM(COALESCE(monto_aplicado, monto_bs)), 0) AS total_bs, " +
                    "       COALESCE(SUM(COALESCE(monto_usd, 0)), 0) AS total_usd " +
                    "FROM pedido_pagos WHERE id_pedido = ?")) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        pagadoBsAplicado = rs.getBigDecimal("total_bs").setScale(2, RoundingMode.HALF_UP);
                        pagadoUsdAplicado = rs.getBigDecimal("total_usd").setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }

            BigDecimal saldoBs = totalBs.subtract(pagadoBsAplicado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal excedenteBs = pagadoBsAplicado.subtract(totalBs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            BigDecimal saldoUsd = tasa.signum() > 0 ? saldoBs.divide(tasa, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal excedenteUsd = tasa.signum() > 0 ? excedenteBs.divide(tasa, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            return new ResumenConciliacionPedido(
                    idPedido, cerrado, versionPrecio, tasa, totalUsd, totalBs,
                    pagadoBsAplicado, pagadoUsdAplicado, saldoBs, saldoUsd, excedenteBs, excedenteUsd);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo obtener el resumen de conciliación del pedido #" + idPedido, ex);
        }
    }

    public List<Pedidos> listarPedidos() {
        List<Pedidos> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.tipo_pedido, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, COALESCE(s.nombre, 'Pedido rápido') AS nombre_sala,
                   p.cliente_nombre, p.cliente_documento, p.metodo_pago, p.efectivo_bs, p.efectivo_usd, p.id_mesonero, p.mesonero_nombre
            FROM pedidos p
            LEFT JOIN salas s ON p.id_sala = s.id
            ORDER BY p.fecha DESC
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Pedidos ped = new Pedidos();
                ped.setId(resultados.getInt("id"));
                ped.setId_sala(resultados.getInt("id_sala"));
                ped.setTipoPedido(resultados.getString("tipo_pedido"));
                ped.setSala(resultados.getString("nombre_sala"));
                ped.setNum_mesa(resultados.getInt("num_mesa"));
                ped.setFecha(resultados.getString("fecha"));
                ped.setSubtotal(resultados.getBigDecimal("subtotal"));
                ped.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                ped.setIvaMonto(resultados.getBigDecimal("iva_monto"));
                ped.setTotalDecimal(resultados.getBigDecimal("total"));
                ped.setSubtotalBs(resultados.getBigDecimal("subtotal_bs"));
                ped.setIvaBs(resultados.getBigDecimal("iva_bs"));
                ped.setTotalBs(resultados.getBigDecimal("total_bs"));
                ped.setTasaCambio(resultados.getBigDecimal("tasa_cambio"));
                ped.setUsuario(resultados.getString("usuario"));
                ped.setEstado(resultados.getString("estado"));
                try {
                    ped.setClienteNombre(resultados.getString("cliente_nombre"));
                    ped.setClienteDocumento(resultados.getString("cliente_documento"));
                    ped.setMetodoPago(resultados.getString("metodo_pago"));
                    ped.setEfectivoBs(resultados.getBigDecimal("efectivo_bs"));
                    ped.setEfectivoUsd(resultados.getBigDecimal("efectivo_usd"));
                    int idM = resultados.getInt("id_mesonero");
                    if (!resultados.wasNull()) {
                        ped.setIdMesonero(idM);
                    }
                    ped.setMesoneroNombre(resultados.getString("mesonero_nombre"));
                } catch (SQLException ignoreCol) {}
                lista.add(ped);
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return listarPedidosLegacy();
            }
            throw new DataAccessException("No se pudieron listar los pedidos.", ex);
        }
        return lista;
    }

    private List<Pedidos> listarPedidosLegacy() {
        List<Pedidos> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, COALESCE(s.nombre, 'Pedido rápido') AS nombre_sala
            FROM pedidos p
            LEFT JOIN salas s ON p.id_sala = s.id
            ORDER BY p.fecha DESC
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Pedidos ped = new Pedidos();
                ped.setId(resultados.getInt("id"));
                ped.setSala(resultados.getString("nombre_sala"));
                ped.setNum_mesa(resultados.getInt("num_mesa"));
                ped.setFecha(resultados.getString("fecha"));
                ped.setSubtotal(resultados.getBigDecimal("subtotal"));
                ped.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                ped.setIvaMonto(resultados.getBigDecimal("iva_monto"));
                ped.setTotalDecimal(resultados.getBigDecimal("total"));
                ped.setSubtotalBs(resultados.getBigDecimal("subtotal_bs"));
                ped.setIvaBs(resultados.getBigDecimal("iva_bs"));
                ped.setTotalBs(resultados.getBigDecimal("total_bs"));
                ped.setTasaCambio(resultados.getBigDecimal("tasa_cambio"));
                ped.setUsuario(resultados.getString("usuario"));
                ped.setEstado(resultados.getString("estado"));
                lista.add(ped);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los pedidos legacy.", ex);
        }
        return lista;
    }


    @Override
    public Map<Integer, Integer> contarMesasOcupadasPorSala() {
        Map<Integer, Integer> ocupadasPorSala = new HashMap<>();
        String sql = """
            SELECT id_sala, COUNT(DISTINCT num_mesa) AS ocupadas
            FROM pedidos
            WHERE estado = 'PENDIENTE' AND cerrada_en IS NULL
            GROUP BY id_sala
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                ocupadasPorSala.put(resultados.getInt("id_sala"), resultados.getInt("ocupadas"));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron contar las mesas ocupadas por sala.", ex);
        }
        return Collections.unmodifiableMap(ocupadasPorSala);
    }

    @Override
    public Map<Integer, String> consultarMesonerosMesasPendientes(int idSala) {
        if (idSala <= 0) return Collections.emptyMap();
        Map<Integer, String> resultado = new HashMap<>();
        String sql = """
            SELECT num_mesa, mesonero_nombre
            FROM pedidos
            WHERE id_sala = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, idSala);
            try (ResultSet rs = sentencia.executeQuery()) {
                while (rs.next()) {
                    int numMesa = rs.getInt("num_mesa");
                    String nombre = rs.getString("mesonero_nombre");
                    resultado.put(numMesa, nombre);
                }
            }
        } catch (SQLException ex) {
            // Si la columna aún no existiera por migración pendiente, retornar mapa vacío de forma segura
            if (ex.getErrorCode() != 1054) {
                LOGGER.warning("Aviso consultando mesoneros de mesas pendientes: " + ex.getMessage());
            }
        }
        return Collections.unmodifiableMap(resultado);
    }

    @Override
    public int purgarPedidosFinalizados(int mesesAnteriores) {
        if (mesesAnteriores < 1) {
            throw ErrorAplicacionException.validacion("El período de retención debe ser al menos 1 mes.");
        }
        String sqlDetalles = """
            DELETE d FROM detalle_pedidos d
            INNER JOIN pedidos p ON d.id_pedido = p.id
            WHERE p.estado = 'FINALIZADO'
              AND p.fecha < DATE_SUB(NOW(), INTERVAL ? MONTH)
            """;
        String sqlPedidos = """
            DELETE FROM pedidos
            WHERE estado = 'FINALIZADO'
              AND fecha < DATE_SUB(NOW(), INTERVAL ? MONTH)
            """;
        try (Connection conexion = conexiones.getConnection()) {
            boolean autoCommitOriginal = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try {
                try (PreparedStatement psDetalles = conexion.prepareStatement(sqlDetalles)) {
                    psDetalles.setInt(1, mesesAnteriores);
                    psDetalles.executeUpdate();
                }
                int eliminados;
                try (PreparedStatement psPedidos = conexion.prepareStatement(sqlPedidos)) {
                    psPedidos.setInt(1, mesesAnteriores);
                    eliminados = psPedidos.executeUpdate();
                }
                conexion.commit();
                return eliminados;
            } catch (SQLException ex) {
                conexion.rollback();
                throw ex;
            } finally {
                conexion.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron purgar los pedidos finalizados.", ex);
        }
    }

    @Override
    public boolean congelarLinea(int idDetalle, String motivo, String actor, String idempotencyKey) {
        if (idDetalle <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del detalle de pedido es inválido.");
        }
        if (actor == null || actor.isBlank()) {
            actor = "Administrador";
        }
        if (motivo == null || motivo.isBlank()) {
            motivo = "Línea excluida de recálculo por producto descontinuado";
        }

        String sqlCheckIdempotencia = "SELECT id FROM auditoria_cambios_precio WHERE idempotency_key = ?";
        String sqlSelectDetalle = """
            SELECT d.id, d.id_pedido, d.id_plato, d.nombre, d.precio, d.moneda_base, d.precio_congelado, p.estado, p.cerrada_en
            FROM detalle_pedidos d
            INNER JOIN pedidos p ON p.id = d.id_pedido
            WHERE d.id = ?
            FOR UPDATE
            """;
        String sqlUpdateDetalle = """
            UPDATE detalle_pedidos
            SET precio_congelado = 1, congelado_motivo = ?, congelado_por = ?, congelado_en = NOW()
            WHERE id = ?
            """;

        try (Connection con = conexiones.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    try (PreparedStatement ps = con.prepareStatement(sqlCheckIdempotencia)) {
                        ps.setString(1, idempotencyKey);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                con.commit();
                                return true;
                            }
                        }
                    }
                }

                int idPedido;
                Integer idPlato;
                String monedaBase;
                BigDecimal precio;
                boolean yaCongelado;

                try (PreparedStatement ps = con.prepareStatement(sqlSelectDetalle)) {
                    ps.setInt(1, idDetalle);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ErrorAplicacionException.validacion("La línea del pedido no existe.");
                        }
                        String estado = rs.getString("estado");
                        Timestamp cerradaEn = rs.getTimestamp("cerrada_en");
                        if (!"PENDIENTE".equalsIgnoreCase(estado) || cerradaEn != null) {
                            throw ErrorAplicacionException.validacion("Solo se pueden congelar líneas de pedidos pendientes abiertos.");
                        }
                        idPedido = rs.getInt("id_pedido");
                        int pl = rs.getInt("id_plato");
                        idPlato = rs.wasNull() ? null : pl;
                        monedaBase = rs.getString("moneda_base");
                        precio = rs.getBigDecimal("precio");
                        yaCongelado = rs.getInt("precio_congelado") != 0;
                    }
                }

                if (!yaCongelado) {
                    try (PreparedStatement ps = con.prepareStatement(sqlUpdateDetalle)) {
                        ps.setString(1, motivo);
                        ps.setString(2, actor);
                        ps.setInt(3, idDetalle);
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("No se pudo marcar la línea como congelada.");
                        }
                    }
                }

                AuditoriaPreciosDao.CambioPrecio cambio = new AuditoriaPreciosDao.CambioPrecio(
                        "CONGELAR_LINEA",
                        idPlato,
                        idPedido,
                        idDetalle,
                        monedaBase != null ? monedaBase : "USD",
                        precio,
                        precio,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        actor,
                        motivo,
                        idempotencyKey
                );
                new AuditoriaPreciosDao().insertar(con, cambio);

                con.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error al congelar la línea del pedido: " + ex.getMessage(), ex);
        }
    }
}

