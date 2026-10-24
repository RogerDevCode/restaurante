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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
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
        return registrarPedidoCompletoConMarcha(pedido, detalles, true).idPedido();
    }

    @Override
    public ResultadoPedidoComanda registrarPedidoCompletoConMarcha(Pedidos pedido, List<DetallePedido> detalles) {
        return registrarPedidoCompletoConMarcha(pedido, detalles, true);
    }

    @Override
    public ResultadoPedidoComanda registrarPedidoCompletoConMarcha(
            Pedidos pedido, List<DetallePedido> detalles, boolean imprimirComanda) {
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
            boolean autoCommitPrevio = conexion.getAutoCommit();
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
                        sentenciaDetalle.setBoolean(6, detalle.getAplicaIva());
                        if (detalle.getIdPlato() != null && detalle.getIdPlato() > 0) {
                            sentenciaDetalle.setInt(7, detalle.getIdPlato());
                        } else {
                            sentenciaDetalle.setNull(7, java.sql.Types.INTEGER);
                        }
                        sentenciaDetalle.setString(8, detalle.getMonedaBase());
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

                ComandaMarcha marcha = null;
                if (imprimirComanda) {
                    List<MovimientoComanda> movimientosIniciales = detalles.stream()
                            .map(det -> MovimientoComanda.desde(MovimientoComanda.Accion.PREPARAR, det, det.getCantidad()))
                            .toList();
                    marcha = insertarComandaMarcha(
                            conexion, idPedido, ComandaMarcha.Tipo.INICIAL, pedido.getUsuario(), movimientosIniciales);
                }
                conexion.commit();
                return new ResultadoPedidoComanda(idPedido, true, marcha);
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
                    if (errorSql.getErrorCode() == 1054) throw new DataAccessException(
                            "La base de datos requiere aplicar la migración antes de guardar comandas.", errorSql);
                    throw new DataAccessException("No se pudo guardar el pedido completo.", errorSql);
                }
                throw new ErrorAplicacionException("Falló el guardado completo del pedido.", error);
            } finally {
                try {
                    conexion.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo abrir la conexión para guardar el pedido.", ex);
        }
    }

    @Override
    public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos) {
        return actualizarPedidoCompletoConMarcha(idPedido, pedidoModificado, detallesNuevos, true).guardado();
    }

    @Override
    public ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos) {
        return actualizarPedidoCompletoConMarcha(idPedido, pedidoModificado, detallesNuevos, true);
    }

    @Override
    public ResultadoPedidoComanda actualizarPedidoCompletoConMarcha(
            int idPedido, Pedidos pedidoModificado, List<DetallePedido> detallesNuevos, boolean imprimirComanda) {
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
            boolean autoCommitPrevio = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            List<MovimientoComanda> movimientosComanda;
            boolean primeraMarcha;
            try {
                bloquearPedidoPendiente(conexion, idPedido);
                calcularYConsolidarSnapshot(conexion, pedidoModificado, detallesNuevos);
                List<MovimientoComanda> historial = leerMovimientosComanda(conexion, idPedido);
                primeraMarcha = historial.isEmpty();
                List<DetallePedido> enviados = ComandaMarcha.componerEstadoEnviado(historial);
                movimientosComanda = ComandaMarcha.calcularDiferencias(enviados, detallesNuevos);
            } catch (SQLException | RuntimeException ex) {
                try {
                    conexion.rollback();
                } catch (SQLException falloRollback) {
                    ex.addSuppressed(falloRollback);
                }
                try {
                    conexion.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
                throw ex;
            }

            ComandaMarcha marchaGenerada = null;
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
                    insDetalle.setBoolean(6, det.getAplicaIva());
                    if (det.getIdPlato() != null && det.getIdPlato() > 0) {
                        insDetalle.setInt(7, det.getIdPlato());
                    } else {
                        insDetalle.setNull(7, java.sql.Types.INTEGER);
                    }
                    insDetalle.setString(8, det.getMonedaBase());
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
                if (imprimirComanda && !movimientosComanda.isEmpty()) {
                    boolean tieneRetiro = movimientosComanda.stream()
                            .anyMatch(m -> m.accion() == MovimientoComanda.Accion.RETIRAR);
                    marchaGenerada = insertarComandaMarcha(conexion, idPedido,
                            primeraMarcha ? ComandaMarcha.Tipo.INICIAL
                                    : (tieneRetiro ? ComandaMarcha.Tipo.AJUSTE : ComandaMarcha.Tipo.INCREMENTO),
                            pedidoModificado.getUsuario(), movimientosComanda);
                }
                conexion.commit();
                return new ResultadoPedidoComanda(idPedido, true, marchaGenerada);
            } catch (SQLException error) {
                conexion.rollback();
                if (error.getErrorCode() == 1054 && error.getMessage() != null && error.getMessage().contains("aplica_iva")) {
                    throw new DataAccessException("La base de datos requiere la migración fiscal de detalles antes de actualizar pedidos.", error);
                }
                if (error.getErrorCode() == 1054) throw new DataAccessException(
                        "La base de datos requiere aplicar la migración antes de actualizar comandas.", error);
                throw error;
            } finally {
                try {
                    conexion.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
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
    private void validarMesaEnSala(Connection conexion, int idSala, int numMesa) {
        if (conexion != null) {
            try (PreparedStatement st = conexion.prepareStatement("SELECT mesas FROM salas WHERE id = ?")) {
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
            return;
        }
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement("SELECT mesas FROM salas WHERE id = ?")) {
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

    private void validarMesaEnSala(int idSala, int numMesa) {
        validarMesaEnSala(null, idSala, numMesa);
    }

    private void validarEstructuraPedido(Connection conexion, Pedidos pedido, List<DetallePedido> detalles) {
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

        if (pedido.getTasaCambio() == null || pedido.getTasaCambio().signum() <= 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener una tasa de cambio válida mayor a cero.");
        }
        BigDecimal tasa = pedido.getTasaCambio().setScale(4, RoundingMode.HALF_UP);

        if (pedido.getIvaPorcentaje() == null || pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener un porcentaje de IVA válido mayor o igual a cero.");
        }
        BigDecimal ivaPedido = pedido.getIvaPorcentaje().setScale(2, RoundingMode.HALF_UP);
        List<DetallePedido> detallesNormalizados = new ArrayList<>(detalles.size());
        for (DetallePedido d : detalles) {
            if (d == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle nulo.");
            }
            if (d.getMonedaBase() == null || d.getMonedaBase().isBlank()) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' debe tener asignada su moneda base (USD o VES).");
            }
            if (d.getPrecioIncluyeIva() == null) {
                throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' debe indicar si el precio incluye IVA.");
            }
            DetallePedido clon = new DetallePedido();
            clon.setPrecioDecimal(d.getPrecioDecimal());
            clon.setCantidad(d.getCantidad());
            clon.setMonedaBase(d.getMonedaBase());
            clon.setAplicaIva(d.getAplicaIva());
            clon.setPrecioIncluyeIva(d.getPrecioIncluyeIva());
            clon.setIvaPorcentaje(d.getIvaPorcentaje() != null ? d.getIvaPorcentaje()
                    : (Boolean.TRUE.equals(clon.getAplicaIva()) ? ivaPedido : BigDecimal.ZERO.setScale(2)));
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
        if (totalEnviado.compareTo(totalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con el subtotal más IVA ni con sus detalles.");
        }

        validarMesaEnSala(conexion, pedido.getId_sala(), pedido.getNum_mesa());
    }

    private void validarEstructuraPedido(Pedidos pedido, List<DetallePedido> detalles) {
        validarEstructuraPedido(null, pedido, detalles);
    }

    private CalculoPedido calcularYConsolidarSnapshot(
            Connection conexion, Pedidos pedido, List<DetallePedido> detalles) throws SQLException {
        validarEstructuraPedido(conexion, pedido, detalles);

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
                            if (rs.getInt("precio_clasificado") == 0) {
                                throw ErrorAplicacionException.validacion(
                                        "El producto '" + det.getNombre() + "' no tiene sus precios clasificados en el catálogo.");
                            }
                            det.setNombre(rs.getString("nombre"));
                            String mb = rs.getString("moneda_base");
                            if (mb == null || mb.isBlank()) {
                                throw ErrorAplicacionException.validacion(
                                        "El producto '" + det.getNombre() + "' no tiene moneda base asignada.");
                            }
                            det.setMonedaBase(mb);
                            det.setAplicaIva(rs.getInt("aplica_iva") != 0);
                            int incIva = rs.getInt("precio_incluye_iva");
                            if (rs.wasNull()) {
                                throw ErrorAplicacionException.validacion(
                                        "El producto '" + det.getNombre() + "' no tiene clasificada la condición de IVA incluido.");
                            }
                            det.setPrecioIncluyeIva(incIva != 0);
                            BigDecimal precioCat = "VES".equalsIgnoreCase(mb) ? rs.getBigDecimal("precio_ves") : rs.getBigDecimal("precio_usd");
                            if (precioCat != null && precioCat.signum() > 0) {
                                det.setPrecioDecimal(precioCat);
                            }
                        }
                    } else {
                        if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) {
                            throw ErrorAplicacionException.validacion(
                                    "El producto '" + det.getNombre() + "' no tiene moneda base asignada (requiere clasificación).");
                        }
                        if (det.getPrecioIncluyeIva() == null) {
                            throw ErrorAplicacionException.validacion(
                                    "El producto '" + det.getNombre() + "' no tiene clasificada la condición de IVA incluido.");
                        }
                    }
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 1054) {
                    throw ex;
                }
            }
        }

        if (pedido.getIvaPorcentaje() == null || pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener un porcentaje de IVA válido mayor o igual a cero.");
        }
        BigDecimal ivaPedido = pedido.getIvaPorcentaje().setScale(2, RoundingMode.HALF_UP);
        for (DetallePedido det : detalles) {
            if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) {
                throw ErrorAplicacionException.validacion(
                        "El producto '" + det.getNombre() + "' no tiene moneda base asignada.");
            }
            if (det.getPrecioIncluyeIva() == null) {
                throw ErrorAplicacionException.validacion(
                        "El producto '" + det.getNombre() + "' no tiene clasificada la condición de IVA incluido.");
            }
            if (det.getIvaPorcentaje() == null) {
                det.setIvaPorcentaje(det.getAplicaIva() ? ivaPedido : BigDecimal.ZERO.setScale(2));
            }
        }

        if (pedido.getTasaCambio() == null || pedido.getTasaCambio().signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio del pedido es obligatoria y debe ser positiva.");
        }
        BigDecimal tasa = pedido.getTasaCambio().setScale(4, RoundingMode.HALF_UP);

        CalculoPedido resultado = CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        if (pedido.getTotalDecimal() != null && pedido.getTotalDecimal().signum() > 0) {
            BigDecimal totalEnviado = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalEsperado = resultado.totalUsd();
            if (totalEnviado.compareTo(totalEsperado) != 0) {
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

        // Verificación vinculante Red Team: Aditividad estricta con tolerancia CERO (0.00)
        if (pedido.getTotalDecimal().compareTo(pedido.getSubtotal().add(pedido.getIvaMonto())) != 0) {
            throw ErrorAplicacionException.validacion("Discrepancia aditiva en cabecera USD: total != subtotal + iva_monto");
        }
        if (pedido.getTotalBs().compareTo(pedido.getSubtotalBs().add(pedido.getIvaBs())) != 0) {
            throw ErrorAplicacionException.validacion("Discrepancia aditiva en cabecera Bs: total_bs != subtotal_bs + iva_bs");
        }

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
            LOGGER.log(Level.INFO, "[CARGA_DATOS] Carga de detalles para el pedido ID {0} retornó 0 items.", id_pedido);
            throw new ErrorAplicacionException(
                    "El pedido " + id_pedido + " no tiene detalles asociados.",
                    new IllegalStateException("Un pedido debe contener al menos un detalle."));
        }
        return lista;
    }

    private static void bloquearPedidoPendiente(Connection conexion, int idPedido) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT id FROM pedidos WHERE id=? AND estado='PENDIENTE' AND cerrada_en IS NULL FOR UPDATE")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("El pedido ya no existe, no está PENDIENTE o ya fue cerrado.");
            }
        }
    }

    private static List<MovimientoComanda> leerMovimientosComanda(Connection conexion, int idPedido)
            throws SQLException {
        String sql = """
            SELECT md.accion,md.nombre,md.cantidad,md.comentario,md.id_plato,md.moneda_base,md.precio,
                   md.aplica_iva,md.precio_incluye_iva,md.iva_porcentaje
            FROM comandas_marchas m
            JOIN comandas_marchas_detalle md ON md.id_marcha=m.id
            WHERE m.id_pedido=? ORDER BY m.secuencia,md.id
            """;
        List<MovimientoComanda> movimientos = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idPlato = rs.getInt("id_plato");
                    Integer plato = rs.wasNull() ? null : idPlato;
                    boolean aplica = rs.getBoolean("aplica_iva");
                    Boolean aplicaIva = rs.wasNull() ? null : aplica;
                    int incluye = rs.getInt("precio_incluye_iva");
                    Boolean precioIncluye = rs.wasNull() ? null : incluye != 0;
                    movimientos.add(new MovimientoComanda(
                            MovimientoComanda.Accion.valueOf(rs.getString("accion")), rs.getString("nombre"),
                            rs.getInt("cantidad"), rs.getString("comentario"), plato,
                            rs.getString("moneda_base"), rs.getBigDecimal("precio"),
                            aplicaIva, precioIncluye, rs.getBigDecimal("iva_porcentaje")));
                }
            }
        }
        return movimientos;
    }

    private static ComandaMarcha insertarComandaMarcha(Connection conexion, int idPedido,
            ComandaMarcha.Tipo tipo, String actor, List<MovimientoComanda> movimientos) throws SQLException {
        int secuencia;
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT COALESCE(MAX(secuencia),0)+1 FROM comandas_marchas WHERE id_pedido=?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se pudo determinar la secuencia de comanda.");
                secuencia = rs.getInt(1);
            }
        }
        long idMarcha;
        try (PreparedStatement ps = conexion.prepareStatement(
                "INSERT INTO comandas_marchas (id_pedido,secuencia,tipo,actor) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idPedido);
            ps.setInt(2, secuencia);
            ps.setString(3, tipo.name());
            ps.setString(4, actor == null || actor.isBlank() ? "Sistema" : actor.trim());
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo registrar la marcha de cocina.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("MySQL no devolvió el identificador de la marcha.");
                idMarcha = rs.getLong(1);
            }
        }
        String sql = """
            INSERT INTO comandas_marchas_detalle
                (id_marcha,accion,nombre,cantidad,comentario,id_plato,moneda_base,precio,aplica_iva,precio_incluye_iva,iva_porcentaje)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            for (MovimientoComanda movimiento : movimientos) {
                ps.setLong(1, idMarcha);
                ps.setString(2, movimiento.accion().name());
                ps.setString(3, movimiento.nombre());
                ps.setInt(4, movimiento.cantidad());
                ps.setString(5, movimiento.comentario());
                if (movimiento.idPlato() == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, movimiento.idPlato());
                ps.setString(7, movimiento.monedaBase());
                ps.setBigDecimal(8, movimiento.precio());
                if (movimiento.aplicaIva() == null) ps.setNull(9, Types.TINYINT); else ps.setBoolean(9, movimiento.aplicaIva());
                if (movimiento.precioIncluyeIva() == null) ps.setNull(10, Types.TINYINT); else ps.setBoolean(10, movimiento.precioIncluyeIva());
                if (movimiento.ivaPorcentaje() == null) ps.setNull(11, Types.DECIMAL); else ps.setBigDecimal(11, movimiento.ivaPorcentaje());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return new ComandaMarcha(idMarcha, idPedido, secuencia, tipo, movimientos);
    }

    @Override
    public ComandaMarcha obtenerComandaMarcha(long idMarcha) {
        if (idMarcha <= 0) throw ErrorAplicacionException.validacion("El identificador de marcha debe ser válido.");
        String sqlCabecera = "SELECT id,id_pedido,secuencia,tipo FROM comandas_marchas WHERE id=?";
        String sqlDetalle = """
            SELECT accion,nombre,cantidad,comentario,id_plato,moneda_base,precio,aplica_iva,precio_incluye_iva,iva_porcentaje
            FROM comandas_marchas_detalle WHERE id_marcha=? ORDER BY id
            """;
        try (Connection conexion = conexiones.getConnection();
             PreparedStatement cabecera = conexion.prepareStatement(sqlCabecera);
             PreparedStatement detalles = conexion.prepareStatement(sqlDetalle)) {
            cabecera.setLong(1, idMarcha);
            try (ResultSet rs = cabecera.executeQuery()) {
                if (!rs.next()) return null;
                int idPedido = rs.getInt("id_pedido");
                int secuencia = rs.getInt("secuencia");
                ComandaMarcha.Tipo tipo = ComandaMarcha.Tipo.valueOf(rs.getString("tipo"));
                detalles.setLong(1, idMarcha);
                List<MovimientoComanda> movimientos = new ArrayList<>();
                try (ResultSet dr = detalles.executeQuery()) {
                    while (dr.next()) {
                        int idPlato = dr.getInt("id_plato");
                        Integer plato = dr.wasNull() ? null : idPlato;
                        boolean aplica = dr.getBoolean("aplica_iva");
                        Boolean aplicaIva = dr.wasNull() ? null : aplica;
                        boolean incluye = dr.getBoolean("precio_incluye_iva");
                        Boolean precioIncluyeIva = dr.wasNull() ? null : incluye;
                        movimientos.add(new MovimientoComanda(
                                MovimientoComanda.Accion.valueOf(dr.getString("accion")), dr.getString("nombre"),
                                dr.getInt("cantidad"), dr.getString("comentario"), plato,
                                dr.getString("moneda_base"), dr.getBigDecimal("precio"), aplicaIva,
                                precioIncluyeIva, dr.getBigDecimal("iva_porcentaje")));
                    }
                }
                return new ComandaMarcha(idMarcha, idPedido, secuencia, tipo, movimientos);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar la marcha de cocina.", ex);
        }
    }

    @Override
    public List<ComandaMarcha> listarComandasMarchas(int idPedido) {
        if (idPedido <= 0) throw ErrorAplicacionException.validacion("El pedido debe ser válido para consultar sus marchas.");
        List<Long> ids = new ArrayList<>();
        try (Connection conexion = conexiones.getConnection();
             PreparedStatement ps = conexion.prepareStatement(
                     "SELECT id FROM comandas_marchas WHERE id_pedido=? AND tipo<>'BASE_LEGADA' ORDER BY secuencia")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron consultar las marchas de cocina del pedido.", ex);
        }
        List<ComandaMarcha> marchas = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ComandaMarcha marcha = obtenerComandaMarcha(id);
            if (marcha != null && !marcha.movimientos().isEmpty()) marchas.add(marcha);
        }
        return List.copyOf(marchas);
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
                    } catch (Exception ignoreCol) {
                        // Absorbe tanto SQLException (columna ausente en BD legacy) como
                        // ErrorAplicacionException si el metodo_pago guardado no es reconocido.
                        LOGGER.fine(() -> "verPedido #" + id_pedido + ": columna opcional no disponible o valor no reconocido: " + ignoreCol.getMessage());
                    }
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
                            throw ErrorAplicacionException.validacion(
                                    "No se puede cobrar: el pedido #" + id_pedido + " no tiene total en bolívares (total_bs) guardado en la base de datos.");
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
                        if ("EFECTIVO".equals(pago.metodo()) || "EFECTIVO_BS".equals(pago.metodo())) {
                            efectivoBs = efectivoBs.add(aplicado);
                        }
                    } else {
                        aplicadoUsd = aplicadoUsd.add(aplicado);
                        if ("EFECTIVO".equals(pago.metodo()) || "EFECTIVO_USD".equals(pago.metodo())) {
                            efectivoUsd = efectivoUsd.add(aplicado);
                        }
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
                    SET estado = 'FINALIZADO', cliente_nombre = ?, cliente_documento = ?, metodo_pago = ?,
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
                String idempLiq = "LIQ-" + id_pedido + "-V" + versionPrecio;
                String sqlLiq = """
                    INSERT INTO pedido_liquidaciones
                        (id_pedido, version_precio, saldo_restante_ves, aplicado_ves, aplicado_usd, tasa_cambio,
                         ajuste_cambio_bs, diferencia_usd, version_regla, actor, idempotency_key)
                    VALUES (?, ?, 0.00, ?, ?, ?, ?, 0.00, 'V5_PAGO_CONGELADO', ?, ?)
                    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                    """;
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

                Map<String, Integer> contadorOcurrencias = new HashMap<>();

                String sqlPago = """
                    INSERT INTO pedido_pagos
                        (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd,
                         monto_recibido, monto_aplicado, cambio_devuelto, es_legado, id_liquidacion, idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    ON DUPLICATE KEY UPDATE id = id
                    """;
                try (PreparedStatement ps = conexion.prepareStatement(sqlPago)) {
                    for (PagoPedido pago : pagos) {
                        BigDecimal montoRec = pago.montoRecibido() != null ? pago.montoRecibido() : pago.monto();
                        BigDecimal montoApl = pago.montoAplicado() != null ? pago.montoAplicado() : pago.monto();
                        BigDecimal cambio = pago.cambioDevuelto() != null ? pago.cambioDevuelto() : BigDecimal.ZERO;
                        String clavePago = pago.metodo() + "-" + pago.moneda() + "-" + montoApl.stripTrailingZeros().toPlainString();
                        int ocurrencia = contadorOcurrencias.merge(clavePago, 1, Integer::sum);
                        String idempPago = "PAGO-" + id_pedido + "-V" + versionPrecio + "-" + clavePago + "#" + ocurrencia;

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
                    int[] resultadosBatch = ps.executeBatch();
                    for (int r : resultadosBatch) {
                        if (r == 0) {
                            LOGGER.log(Level.INFO, "Reintento de pago absorbido por idempotencia para el pedido #{0}", id_pedido);
                        }
                    }
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
            SET estado = ?, cliente_nombre = ?, cliente_documento = ?, metodo_pago = ?,
                efectivo_bs = CASE
                    WHEN ? = 'MIXTO' THEN ?
                    WHEN ? IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(total_bs, total * NULLIF(tasa_cambio, 0), 0)
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
                    throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene tasa de cambio registrada.");
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
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                // Si tiene pagos registrados, impedir anular a menos que tenga movimientos de devolución/crédito/anulación equivalentes registrados (Invariante F-07)
                BigDecimal pagadoUsd = BigDecimal.ZERO;
                try (PreparedStatement psPagos = con.prepareStatement(
                        "SELECT COALESCE(SUM(COALESCE(monto_usd, CASE WHEN moneda = 'USD' THEN monto_aplicado ELSE ROUND(monto_bs / NULLIF(tasa_cambio, 0), 2) END)), 0) FROM pedido_pagos WHERE id_pedido = ?")) {
                    psPagos.setInt(1, id_pedido);
                    try (ResultSet rs = psPagos.executeQuery()) {
                        if (rs.next()) {
                            pagadoUsd = rs.getBigDecimal(1);
                        }
                    }
                }

                if (pagadoUsd != null && pagadoUsd.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal compensadoUsd = BigDecimal.ZERO;
                    int cantMovimientos = 0;
                    try (PreparedStatement psMov = con.prepareStatement(
                            "SELECT COUNT(*), COALESCE(SUM(CASE WHEN moneda = 'USD' THEN monto_origen ELSE ROUND(equivalente_ves / NULLIF(tasa_cambio, 0), 2) END), 0) FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
                        psMov.setInt(1, id_pedido);
                        try (ResultSet rsMov = psMov.executeQuery()) {
                            if (rsMov.next()) {
                                cantMovimientos = rsMov.getInt(1);
                                compensadoUsd = rsMov.getBigDecimal(2);
                            }
                        }
                    }

                    // Consultar estado actual del pedido para distinguir anulación de mesa abierta / abono parcial (PENDIENTE) vs anulación de factura cobrada (FINALIZADO)
                    String estadoActual = "PENDIENTE";
                    try (PreparedStatement psEst = con.prepareStatement("SELECT estado FROM pedidos WHERE id = ?")) {
                        psEst.setInt(1, id_pedido);
                        try (ResultSet rsEst = psEst.executeQuery()) {
                            if (rsEst.next()) {
                                estadoActual = rsEst.getString("estado");
                            }
                        }
                    }

                    if ("FINALIZADO".equalsIgnoreCase(estadoActual) && cantMovimientos == 0) {
                        // Venta cobrada sin devolución manual previa (F-57 / AceptacionOperacionesPosYReversionTest):
                        // Generar atómicamente los contra-asientos formales de anulación en pedido_saldos_movimientos
                        List<Object[]> pagosRegistrados = new ArrayList<>();
                        try (PreparedStatement psPagosDet = con.prepareStatement(
                                "SELECT id, id_liquidacion, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, monto_aplicado " +
                                "FROM pedido_pagos WHERE id_pedido = ?")) {
                            psPagosDet.setInt(1, id_pedido);
                            try (ResultSet rsPagosDet = psPagosDet.executeQuery()) {
                                while (rsPagosDet.next()) {
                                    pagosRegistrados.add(new Object[]{
                                        rsPagosDet.getLong("id"),
                                        rsPagosDet.getObject("id_liquidacion"),
                                        rsPagosDet.getString("metodo_pago"),
                                        rsPagosDet.getString("moneda"),
                                        rsPagosDet.getBigDecimal("monto"),
                                        rsPagosDet.getBigDecimal("tasa_cambio"),
                                        rsPagosDet.getBigDecimal("monto_bs"),
                                        rsPagosDet.getBigDecimal("monto_usd"),
                                        rsPagosDet.getBigDecimal("monto_aplicado")
                                    });
                                }
                            }
                        }
                        if (!pagosRegistrados.isEmpty()) {
                            String sqlContra = """
                                INSERT INTO pedido_saldos_movimientos (
                                    id_pedido, id_liquidacion, tipo, moneda, monto_origen, equivalente_ves, tasa_cambio, actor, motivo, idempotency_key
                                ) VALUES (?, ?, 'ANULACION', ?, ?, ?, ?, ?, ?, ?)
                                ON DUPLICATE KEY UPDATE motivo = VALUES(motivo)
                                """;
                            try (PreparedStatement psContra = con.prepareStatement(sqlContra)) {
                                for (Object[] pago : pagosRegistrados) {
                                    long idPago = (Long) pago[0];
                                    Long idLiq = pago[1] != null ? ((Number) pago[1]).longValue() : null;
                                    String metodoPago = (String) pago[2];
                                    String moneda = (String) pago[3];
                                    BigDecimal monto = pago[8] != null ? (BigDecimal) pago[8] : (BigDecimal) pago[4];
                                    BigDecimal tasa = (BigDecimal) pago[5];
                                    BigDecimal eqVes = pago[6] != null ? (BigDecimal) pago[6] : (BigDecimal) pago[4];

                                    psContra.setInt(1, id_pedido);
                                    if (idLiq != null) {
                                        psContra.setLong(2, idLiq);
                                    } else {
                                        psContra.setNull(2, java.sql.Types.BIGINT);
                                    }
                                    psContra.setString(3, moneda != null ? moneda : "USD");
                                    psContra.setBigDecimal(4, monto != null ? monto : BigDecimal.ZERO);
                                    psContra.setBigDecimal(5, eqVes != null ? eqVes : BigDecimal.ZERO);
                                    psContra.setBigDecimal(6, tasa != null ? tasa : BigDecimal.ONE);
                                    psContra.setString(7, usr);
                                    psContra.setString(8, "Anulación factura #" + id_pedido + " (" + metodoPago + "): " + motivo.trim());
                                    psContra.setString(9, "ANUL-" + id_pedido + "-PAGO-" + idPago);
                                    psContra.executeUpdate();
                                }
                            }
                        }
                    } else if (compensadoUsd == null || compensadoUsd.compareTo(pagadoUsd.subtract(new BigDecimal("0.01"))) < 0) {
                        // Invariante F-07 y D3: Pedido PENDIENTE con abonos o pedido con compensación incompleta
                        throw ErrorAplicacionException.validacion(
                                "El pedido #" + id_pedido + " tiene pagos registrados por $" + pagadoUsd.setScale(2, RoundingMode.HALF_UP)
                                + " y solo se ha compensado/devuelto $" + (compensadoUsd != null ? compensadoUsd.setScale(2, RoundingMode.HALF_UP) : "0.00")
                                + "; se requiere contra-asiento o devolución equivalente antes de anular.");
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
    public boolean anularFacturaCobradaConDevolucion(int id_pedido, String motivo, String usr) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para anular.");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la anulación de la factura.");
        }

        String sqlUpdate = "UPDATE pedidos SET estado = 'ANULADO', cerrada_en = CURRENT_TIMESTAMP WHERE id = ?";
        String sqlAuditoria = """
            INSERT INTO auditoria_pedidos (id_pedido, accion, motivo, usuario, fecha_hora)
            VALUES (?, 'ANULACION', ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                // 1. Si tiene pagos registrados, generar atómicamente los contra-asientos contables de anulación/reversión
                List<Object[]> pagosRegistrados = new ArrayList<>();
                try (PreparedStatement psPagos = con.prepareStatement(
                        "SELECT id, id_liquidacion, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, monto_aplicado " +
                        "FROM pedido_pagos WHERE id_pedido = ?")) {
                    psPagos.setInt(1, id_pedido);
                    try (ResultSet rs = psPagos.executeQuery()) {
                        while (rs.next()) {
                            pagosRegistrados.add(new Object[]{
                                rs.getLong("id"),
                                rs.getObject("id_liquidacion"),
                                rs.getString("metodo_pago"),
                                rs.getString("moneda"),
                                rs.getBigDecimal("monto"),
                                rs.getBigDecimal("tasa_cambio"),
                                rs.getBigDecimal("monto_bs"),
                                rs.getBigDecimal("monto_usd"),
                                rs.getBigDecimal("monto_aplicado")
                            });
                        }
                    }
                }

                if (!pagosRegistrados.isEmpty()) {
                    String sqlContraAsiento = """
                        INSERT INTO pedido_saldos_movimientos (
                            id_pedido, id_liquidacion, tipo, moneda, monto_origen, equivalente_ves, tasa_cambio, actor, motivo, idempotency_key
                        ) VALUES (?, ?, 'ANULACION', ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE motivo = VALUES(motivo)
                        """;
                    try (PreparedStatement psContra = con.prepareStatement(sqlContraAsiento)) {
                        for (Object[] pago : pagosRegistrados) {
                            long idPago = (Long) pago[0];
                            Long idLiq = pago[1] != null ? ((Number) pago[1]).longValue() : null;
                            String metodoPago = (String) pago[2];
                            String moneda = (String) pago[3];
                            BigDecimal monto = pago[8] != null ? (BigDecimal) pago[8] : (BigDecimal) pago[4];
                            BigDecimal tasa = (BigDecimal) pago[5];
                            BigDecimal eqVes = pago[6] != null ? (BigDecimal) pago[6] : (BigDecimal) pago[4];

                            psContra.setInt(1, id_pedido);
                            if (idLiq != null) {
                                psContra.setLong(2, idLiq);
                            } else {
                                psContra.setNull(2, java.sql.Types.BIGINT);
                            }
                            psContra.setString(3, moneda != null ? moneda : "USD");
                            psContra.setBigDecimal(4, monto != null ? monto : BigDecimal.ZERO);
                            psContra.setBigDecimal(5, eqVes != null ? eqVes : BigDecimal.ZERO);
                            psContra.setBigDecimal(6, tasa != null ? tasa : BigDecimal.ONE);
                            psContra.setString(7, usr);
                            psContra.setString(8, "Anulación factura #" + id_pedido + " (" + metodoPago + "): " + motivo.trim());
                            psContra.setString(9, "ANUL-" + id_pedido + "-PAGO-" + idPago);
                            psContra.executeUpdate();
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
                throw new DataAccessException("No se pudo anular la factura con devolución de forma atómica: " + ex.getMessage(), ex);
            } finally {
                con.setAutoCommit(autoCommitPrevio);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error de conexión al anular la factura con devolución: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean cerrarMesa(int idPedido, int versionEsperada) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para cerrar la mesa.");
        }
        int maxReintentos = 2;
        for (int intento = 1; intento <= maxReintentos; intento++) {
            try (Connection con = conexiones.getConnection()) {
                boolean autoCommitOriginal = con.getAutoCommit();
                con.setAutoCommit(false);
                try {
                    // 1. Bloquear configuración (tasa e IVA) en orden global config -> pedidos -> detalle -> platos
                    BigDecimal tasaActual = null;
                    BigDecimal ivaPorcentaje = null;
                    try (PreparedStatement psConf = con.prepareStatement(
                            "SELECT tasa_dolar, iva_porcentaje FROM config WHERE id = 1 FOR UPDATE")) {
                        try (ResultSet rsConf = psConf.executeQuery()) {
                            if (rsConf.next()) {
                                tasaActual = rsConf.getBigDecimal("tasa_dolar");
                                ivaPorcentaje = rsConf.getBigDecimal("iva_porcentaje");
                            }
                        }
                    }
                    if (tasaActual == null || tasaActual.signum() <= 0) {
                        throw ErrorAplicacionException.validacion("La tasa de cambio configurada en el sistema es inválida o no ha sido establecida.");
                    }
                    BigDecimal tasaNormalizada = tasaActual.setScale(4, RoundingMode.HALF_UP);
                    if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
                        throw ErrorAplicacionException.validacion("El porcentaje de IVA configurado en el sistema es inválido o no ha sido establecido.");
                    }
                    BigDecimal ivaNormalizado = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);

                    // 2. Bloquear pedido
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
                        if (versionEsperada > 0 && versionActual != versionEsperada && versionActual != versionEsperada + 1) {
                            throw ErrorAplicacionException.validacion("La mesa ya fue cerrada con una cotización distinta (versión " + versionActual + ").");
                        }
                        con.rollback();
                        return true;
                    }

                    if (versionEsperada > 0 && versionActual != versionEsperada) {
                        throw ErrorAplicacionException.validacion("La comanda fue modificada recientemente; recargue antes de cerrar (versión actual: "
                                + versionActual + ", esperada: " + versionEsperada + ").");
                    }

                    // 3. Bloquear líneas del pedido
                    List<DetallePedido> detalles = new ArrayList<>();
                    try (PreparedStatement psDet = con.prepareStatement(
                            "SELECT id, id_plato, nombre, cantidad, precio, moneda_base, aplica_iva, precio_incluye_iva, " +
                            "iva_porcentaje, precio_congelado FROM detalle_pedidos WHERE id_pedido = ? ORDER BY id ASC FOR UPDATE")) {
                        psDet.setInt(1, idPedido);
                        try (ResultSet rsDet = psDet.executeQuery()) {
                            while (rsDet.next()) {
                                DetallePedido d = new DetallePedido();
                                d.setId(rsDet.getInt("id"));
                                int idP = rsDet.getInt("id_plato");
                                d.setIdPlato(rsDet.wasNull() ? null : idP);
                                d.setNombre(rsDet.getString("nombre"));
                                d.setCantidad(rsDet.getInt("cantidad"));
                                d.setPrecioDecimal(rsDet.getBigDecimal("precio"));
                                d.setMonedaBase(rsDet.getString("moneda_base"));
                                boolean aplica = rsDet.getBoolean("aplica_iva");
                                d.setAplicaIva(rsDet.wasNull() ? null : aplica);
                                int inc = rsDet.getInt("precio_incluye_iva");
                                d.setPrecioIncluyeIva(rsDet.wasNull() ? null : (inc != 0));
                                d.setIvaPorcentaje(rsDet.getBigDecimal("iva_porcentaje"));
                                d.setPrecioCongelado(rsDet.getInt("precio_congelado") != 0);
                                detalles.add(d);
                            }
                        }
                    }

                    if (detalles.isEmpty()) {
                        throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene detalles asociados.");
                    }

                    // 4. Bloquear y releer platos del catálogo para las líneas no congeladas
                    for (DetallePedido d : detalles) {
                        if (d.getMonedaBase() == null || d.getMonedaBase().isBlank()) {
                            throw ErrorAplicacionException.validacion("La comanda #" + idPedido
                                    + " contiene productos heredados sin clasificar; asígneles moneda base antes de cerrar.");
                        }
                        if (d.getPrecioIncluyeIva() == null) {
                            throw ErrorAplicacionException.validacion("La comanda #" + idPedido
                                    + " contiene productos sin condición de precio con IVA incluido definida antes de cerrar.");
                        }

                        if (!d.isPrecioCongelado() && d.getIdPlato() != null && d.getIdPlato() > 0) {
                            try (PreparedStatement psPla = con.prepareStatement(
                                    "SELECT nombre, moneda_base, precio_usd, precio_ves, aplica_iva, precio_incluye_iva, activo, precio_clasificado " +
                                    "FROM platos WHERE id = ? FOR UPDATE")) {
                                psPla.setInt(1, d.getIdPlato());
                                try (ResultSet rsPla = psPla.executeQuery()) {
                                    if (!rsPla.next() || rsPla.getInt("activo") == 0) {
                                        throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' ya no está activo en el catálogo.");
                                    }
                                    if (rsPla.getInt("precio_clasificado") == 0) {
                                        throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene sus precios clasificados en el catálogo.");
                                    }
                                    String mb = rsPla.getString("moneda_base");
                                    d.setMonedaBase(mb);
                                    boolean gravado = rsPla.getInt("aplica_iva") != 0;
                                    d.setAplicaIva(gravado);
                                    int incIva = rsPla.getInt("precio_incluye_iva");
                                    boolean wasNull = rsPla.wasNull();
                                    if (gravado && wasNull) {
                                        throw ErrorAplicacionException.validacion("El producto '" + d.getNombre() + "' no tiene clasificada la condición de IVA incluido en el catálogo.");
                                    }
                                    d.setPrecioIncluyeIva(!wasNull && incIva != 0);
                                    BigDecimal precioCat = "VES".equalsIgnoreCase(mb) ? rsPla.getBigDecimal("precio_ves") : rsPla.getBigDecimal("precio_usd");
                                    if (precioCat != null && precioCat.signum() > 0) {
                                        d.setPrecioDecimal(precioCat);
                                    }
                                }
                            }
                        }
                        d.setIvaPorcentaje(Boolean.TRUE.equals(d.getAplicaIva()) ? ivaNormalizado : BigDecimal.ZERO.setScale(2));
                    }

                    // 5. Cálculo exacto sin inferencias mediante CalculoMonetarioPedido
                    CalculoPedido calculo = CalculoMonetarioPedido.calcularPedido(detalles, tasaNormalizada);

                    // 6. Actualizar y congelar líneas de detalle
                    String sqlUpdDetalle = """
                        UPDATE detalle_pedidos
                        SET precio = ?, moneda_base = ?, aplica_iva = ?, precio_incluye_iva = ?, iva_porcentaje = ?,
                            precio_congelado = 1, congelado_en = CURRENT_TIMESTAMP, congelado_motivo = 'CIERRE_MESA'
                        WHERE id = ?
                        """;
                    try (PreparedStatement psUpdDet = con.prepareStatement(sqlUpdDetalle)) {
                        for (DetallePedido d : detalles) {
                            psUpdDet.setBigDecimal(1, d.getPrecioDecimal());
                            psUpdDet.setString(2, d.getMonedaBase());
                            psUpdDet.setBoolean(3, Boolean.TRUE.equals(d.getAplicaIva()));
                            psUpdDet.setBoolean(4, Boolean.TRUE.equals(d.getPrecioIncluyeIva()));
                            psUpdDet.setBigDecimal(5, d.getIvaPorcentaje());
                            psUpdDet.setInt(6, d.getId());
                            psUpdDet.addBatch();
                        }
                        psUpdDet.executeBatch();
                    }

                    // 7. Actualizar cabecera del pedido e incrementar version_precio + 1 y marcar cerrada_en
                    String sqlUpdPedido = """
                        UPDATE pedidos
                        SET subtotal = ?, iva_porcentaje = ?, iva_monto = ?, total = ?,
                            tasa_cambio = ?, subtotal_bs = ?, iva_bs = ?, total_bs = ?,
                            version_precio = version_precio + 1, cerrada_en = CURRENT_TIMESTAMP
                        WHERE id = ? AND cerrada_en IS NULL
                        """;
                    try (PreparedStatement psUpdPed = con.prepareStatement(sqlUpdPedido)) {
                        psUpdPed.setBigDecimal(1, calculo.subtotalUsd());
                        psUpdPed.setBigDecimal(2, ivaNormalizado);
                        psUpdPed.setBigDecimal(3, calculo.ivaUsd());
                        psUpdPed.setBigDecimal(4, calculo.totalUsd());
                        psUpdPed.setBigDecimal(5, tasaNormalizada);
                        psUpdPed.setBigDecimal(6, calculo.subtotalVes());
                        psUpdPed.setBigDecimal(7, calculo.ivaVes());
                        psUpdPed.setBigDecimal(8, calculo.totalVes());
                        psUpdPed.setInt(9, idPedido);
                        int filas = psUpdPed.executeUpdate();
                        if (filas == 0) {
                            con.rollback();
                            return true;
                        }
                    }

                    con.commit();
                    return true;
                } catch (SQLException exSql) {
                    try { con.rollback(); } catch (SQLException rEx) { exSql.addSuppressed(rEx); }
                    if (esDeadlock(exSql) && intento < maxReintentos) {
                        try { Thread.sleep(50L * intento); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                    throw exSql;
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
        return false;
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

            String estado = "PENDIENTE";
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT estado, total, total_bs, tasa_cambio, cerrada_en, version_precio FROM pedidos WHERE id = ?")) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw ErrorAplicacionException.validacion("No existe el pedido #" + idPedido + ".");
                    }
                    estado = rs.getString("estado");
                    totalUsd = rs.getBigDecimal("total");
                    tasa = rs.getBigDecimal("tasa_cambio");
                    totalBs = rs.getBigDecimal("total_bs");
                    if (tasa == null || tasa.signum() <= 0) {
                        throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene tasa de cambio registrada.");
                    }
                    if (totalBs == null) {
                        throw ErrorAplicacionException.validacion("El pedido #" + idPedido + " no tiene total en bolívares registrado.");
                    }
                    cerrado = rs.getTimestamp("cerrada_en") != null;
                    versionPrecio = rs.getInt("version_precio");
                }
            }

            // Si el pedido está anulado, no hay cobranza activa exigible ni deuda pendiente
            if ("ANULADO".equalsIgnoreCase(estado)) {
                return new ResumenConciliacionPedido(
                        idPedido, cerrado, versionPrecio, tasa,
                        totalUsd != null ? totalUsd.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
                        totalBs != null ? totalBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
            }

            // P2-D: montos aplicados por moneda de origen con redondeo exacto individual por abono.
            // monto_bs / monto_usd guardan el equivalente aplicado a tasa del pago.
            // Legado: sin monto_aplicado se usa monto_bs (importe aplicado histórico).
            BigDecimal pagadoBsAplicado = BigDecimal.ZERO.setScale(2);
            BigDecimal pagadoUsdAplicado = BigDecimal.ZERO.setScale(2);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT moneda, monto_aplicado, monto_bs, monto_usd, tasa_cambio FROM pedido_pagos WHERE id_pedido = ?")) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String mon = rs.getString("moneda");
                        BigDecimal mAplicado = rs.getBigDecimal("monto_aplicado");
                        BigDecimal mBs = rs.getBigDecimal("monto_bs");
                        BigDecimal mUsd = rs.getBigDecimal("monto_usd");
                        BigDecimal tCam = rs.getBigDecimal("tasa_cambio");

                        // Bolívares aplicados
                        BigDecimal bsFila = mBs != null ? mBs : (mAplicado != null ? mAplicado : BigDecimal.ZERO);
                        pagadoBsAplicado = pagadoBsAplicado.add(bsFila.setScale(2, RoundingMode.HALF_UP));

                        // USD aplicados con redondeo individual por abono (idéntico a PagoPedido.equivalenteUsd)
                        BigDecimal usdFila;
                        if (mUsd != null) {
                            usdFila = mUsd.setScale(2, RoundingMode.HALF_UP);
                        } else if ("USD".equalsIgnoreCase(mon)) {
                            usdFila = (mAplicado != null ? mAplicado : bsFila).setScale(2, RoundingMode.HALF_UP);
                        } else if (tCam != null && tCam.signum() > 0) {
                            usdFila = bsFila.divide(tCam, 2, RoundingMode.HALF_UP);
                        } else {
                            usdFila = BigDecimal.ZERO.setScale(2);
                        }
                        pagadoUsdAplicado = pagadoUsdAplicado.add(usdFila);
                    }
                }
            }

            // Deducir movimientos de compensación / devolución registrados en pedido_saldos_movimientos
            try (PreparedStatement psMov = con.prepareStatement(
                    "SELECT tipo, moneda, monto_origen, equivalente_ves, tasa_cambio FROM pedido_saldos_movimientos WHERE id_pedido = ? AND tipo IN ('DEVOLUCION','CREDITO','ANULACION')")) {
                psMov.setInt(1, idPedido);
                try (ResultSet rsMov = psMov.executeQuery()) {
                    while (rsMov.next()) {
                        String mon = rsMov.getString("moneda");
                        BigDecimal eqVes = rsMov.getBigDecimal("equivalente_ves");
                        BigDecimal mOrigen = rsMov.getBigDecimal("monto_origen");
                        BigDecimal tCam = rsMov.getBigDecimal("tasa_cambio");

                        BigDecimal devBs = eqVes != null ? eqVes : BigDecimal.ZERO;
                        BigDecimal devUsd;
                        if ("USD".equalsIgnoreCase(mon)) {
                            devUsd = mOrigen != null ? mOrigen : BigDecimal.ZERO;
                        } else if (tCam != null && tCam.signum() > 0) {
                            devUsd = devBs.divide(tCam, 2, RoundingMode.HALF_UP);
                        } else {
                            devUsd = BigDecimal.ZERO;
                        }

                        pagadoBsAplicado = pagadoBsAplicado.subtract(devBs.setScale(2, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
                        pagadoUsdAplicado = pagadoUsdAplicado.subtract(devUsd.setScale(2, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
                    }
                }
            } catch (SQLException exMov) {
                LOGGER.log(Level.FINE, "No se pudieron deducir movimientos de compensación para pedido #" + idPedido, exMov);
            }

            try (PreparedStatement psCheck = con.prepareStatement(
                    "SELECT COUNT(*) FROM pedido_pagos WHERE id_pedido = ? AND monto_aplicado IS NULL")) {
                psCheck.setInt(1, idPedido);
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                        LOGGER.log(Level.WARNING, "resumenConciliacion: El pedido #{0} contiene {1} pagos legados con monto_aplicado nulo; se utilizó monto_bs como fallback.", new Object[]{idPedido, rsCheck.getInt(1)});
                    }
                }
            } catch (SQLException exCheck) {
                LOGGER.log(Level.FINE, "No se pudo auditar pagos legados: " + exCheck.getMessage());
            }

            boolean esFinalizado = "FINALIZADO".equalsIgnoreCase(estado);
            if (esFinalizado) {
                // Consultar ajuste cambiario de liquidación para que los pagos aplicados cuadren exactamente al total
                try (PreparedStatement psLiq = con.prepareStatement(
                        "SELECT ajuste_cambio_bs FROM pedido_liquidaciones WHERE id_pedido = ? ORDER BY id DESC LIMIT 1")) {
                    psLiq.setInt(1, idPedido);
                    try (ResultSet rsLiq = psLiq.executeQuery()) {
                        if (rsLiq.next()) {
                            BigDecimal aj = rsLiq.getBigDecimal("ajuste_cambio_bs");
                            if (aj != null && aj.signum() != 0) {
                                pagadoBsAplicado = pagadoBsAplicado.add(aj);
                            }
                        }
                    }
                } catch (SQLException ign) {
                }
            }

            BigDecimal saldoBs = totalBs.subtract(pagadoBsAplicado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal excedenteBs = pagadoBsAplicado.subtract(totalBs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            BigDecimal saldoUsd = totalUsd.subtract(pagadoUsdAplicado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal excedenteUsd = pagadoUsdAplicado.subtract(totalUsd).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            if (esFinalizado) {
                BigDecimal tolBs = new BigDecimal("0.05");
                if (tasa != null && tasa.signum() > 0) {
                    BigDecimal tolCalculada = new BigDecimal("0.005").multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    if (tolCalculada.compareTo(tolBs) > 0) {
                        tolBs = tolCalculada;
                    }
                }
                if (saldoBs.compareTo(tolBs) <= 0) {
                    saldoBs = BigDecimal.ZERO.setScale(2);
                    saldoUsd = BigDecimal.ZERO.setScale(2);
                }
                if (excedenteBs.compareTo(tolBs) <= 0) {
                    excedenteBs = BigDecimal.ZERO.setScale(2);
                    excedenteUsd = BigDecimal.ZERO.setScale(2);
                }
            }

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
                } catch (Exception ignoreCol) {
                    // Absorbe tanto SQLException (columna ausente en BD legacy) como
                    // ErrorAplicacionException si el metodo_pago guardado no es reconocido.
                    LOGGER.fine(() -> "listarPedidos: columna opcional no disponible o valor no reconocido: " + ignoreCol.getMessage());
                }
                lista.add(ped);
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return listarPedidosLegacy();
            }
            throw new DataAccessException("No se pudieron listar los pedidos.", ex);
        }
        if (lista.isEmpty()) {
            LOGGER.log(Level.INFO, "[CARGA_DATOS] Carga general de pedidos retornó 0 items.");
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
        String sqlDetallesMarcha = """
            DELETE md FROM comandas_marchas_detalle md
            INNER JOIN comandas_marchas m ON md.id_marcha = m.id
            INNER JOIN pedidos p ON m.id_pedido = p.id
            WHERE p.estado = 'FINALIZADO'
              AND p.fecha < DATE_SUB(NOW(), INTERVAL ? MONTH)
            """;
        String sqlMarchas = """
            DELETE m FROM comandas_marchas m
            INNER JOIN pedidos p ON m.id_pedido = p.id
            WHERE p.estado = 'FINALIZADO'
              AND p.fecha < DATE_SUB(NOW(), INTERVAL ? MONTH)
            """;
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
                // La purga explícita cumple la política de retención también para el diario de cocina.
                try (PreparedStatement ps = conexion.prepareStatement(sqlDetallesMarcha)) {
                    ps.setInt(1, mesesAnteriores);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conexion.prepareStatement(sqlMarchas)) {
                    ps.setInt(1, mesesAnteriores);
                    ps.executeUpdate();
                }
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

