package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PedidosDao implements PedidosRepositorio {
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
            WHERE num_mesa = ? AND id_sala = ? AND estado = ?
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
        validarPedidoPersistible(pedido, detalles);
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        for (DetallePedido detalle : detalles) {
            if (detalle == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle vacío.");
            }
        }

        String sqlPedido = """
            INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario, tasa_cambio, subtotal_bs, iva_bs, total_bs)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String sqlDetalle = """
            INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conexion = conexiones.getConnection()) {
            try {
                conexion.setAutoCommit(false);
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
                        sentenciaDetalle.setString(4, detalle.getComentario());
                        sentenciaDetalle.setInt(5, idPedido);
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
                    throw new DataAccessException("No se pudo guardar el pedido completo.", errorSql);
                }
                throw new ErrorAplicacionException("Falló el guardado completo del pedido.", error);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo abrir la conexión para guardar el pedido.", ex);
        }
    }

    private void validarPedidoPersistible(Pedidos pedido, List<DetallePedido> detalles) {
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("El pedido es obligatorio.");
        }
        if (pedido.getId_sala() <= 0 || pedido.getNum_mesa() <= 0
                || pedido.getUsuario() == null || pedido.getUsuario().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La sala, mesa y usuario del pedido deben ser válidos.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        BigDecimal sumaDetalles = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()
                    || detalle.getCantidad() <= 0) {
                throw ErrorAplicacionException.validacion("Cada detalle debe tener nombre y cantidad positiva.");
            }
            BigDecimal precio = importePersistible(detalle.getPrecioDecimal(), "El precio de cada plato");
            sumaDetalles = sumaDetalles.add(precio.multiply(BigDecimal.valueOf(detalle.getCantidad())));
        }

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        BigDecimal subtotalEsperado = sumaDetalles;
        BigDecimal ivaEsperado = BigDecimal.ZERO;
        BigDecimal totalEsperado = subtotalEsperado;

        if (ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0) {
            ivaEsperado = subtotalEsperado.multiply(ivaPorcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalEsperado = subtotalEsperado.add(ivaEsperado).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal subtotal = importePersistible(pedido.getSubtotal() != null ? pedido.getSubtotal() : subtotalEsperado, "El subtotal del pedido");
        if (subtotal.compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
        }

        BigDecimal ivaMonto = importePersistible(pedido.getIvaMonto() != null ? pedido.getIvaMonto() : ivaEsperado, "El monto del IVA");
        if (ivaMonto.compareTo(ivaEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El monto del IVA no coincide con el porcentaje aplicado.");
        }

        BigDecimal total = importePersistible(pedido.getTotalDecimal(), "El total del pedido");
        if (total.compareTo(totalEsperado) != 0 && total.compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con el subtotal más IVA.");
        }

        BigDecimal tasa = pedido.getTasaCambio();
        if (tasa == null || tasa.compareTo(BigDecimal.ZERO) <= 0) {
            tasa = new BigDecimal("36.5000");
            pedido.setTasaCambio(tasa);
        }

        BigDecimal subBs = subtotal.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        if (pedido.getSubtotalBs() == null) {
            pedido.setSubtotalBs(subBs);
        } else if (pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP).compareTo(subBs) != 0) {
            throw ErrorAplicacionException.validacion("El subtotal en Bs no coincide con la tasa de cambio.");
        }

        BigDecimal ivaBs = ivaMonto.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        if (pedido.getIvaBs() == null) {
            pedido.setIvaBs(ivaBs);
        } else if (pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP).compareTo(ivaBs) != 0) {
            throw ErrorAplicacionException.validacion("El IVA en Bs no coincide con la tasa de cambio.");
        }

        BigDecimal totalBs = total.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        if (pedido.getTotalBs() == null) {
            pedido.setTotalBs(totalBs);
        } else if (pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP).compareTo(totalBs) != 0) {
            throw ErrorAplicacionException.validacion("El total en Bs no coincide con la tasa de cambio.");
        }
        if (pedido.getSubtotal() == null) {
            pedido.setSubtotal(subtotalEsperado);
        }
        if (pedido.getIvaMonto() == null) {
            pedido.setIvaMonto(ivaEsperado);
        }
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
            SELECT d.id, d.nombre, d.precio, d.cantidad, d.comentario, d.id_pedido
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
                    lista.add(det);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
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
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, s.nombre AS nombre_sala,
                   p.cliente_nombre, p.cliente_documento, p.metodo_pago, p.efectivo_bs, p.efectivo_usd
            FROM pedidos p
            INNER JOIN salas s ON p.id_sala = s.id
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
                    try {
                        ped.setClienteNombre(resultados.getString("cliente_nombre"));
                        ped.setClienteDocumento(resultados.getString("cliente_documento"));
                        ped.setMetodoPago(resultados.getString("metodo_pago"));
                        ped.setEfectivoBs(resultados.getBigDecimal("efectivo_bs"));
                        ped.setEfectivoUsd(resultados.getBigDecimal("efectivo_usd"));
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
        return ped;
    }

    private Pedidos verPedidoLegacy(int id_pedido) {
        Pedidos ped = null;
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, s.nombre AS nombre_sala
            FROM pedidos p
            INNER JOIN salas s ON p.id_sala = s.id
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
                    WHEN ? IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(total_bs, total * COALESCE(NULLIF(tasa_cambio, 0), 36.5000))
                    ELSE 0 END,
                efectivo_usd = CASE
                    WHEN ? = 'MIXTO' THEN ?
                    WHEN ? = 'EFECTIVO_USD' THEN total
                    ELSE 0 END
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
                if (equivalenteBs.compareTo(totalBs) > 0) {
                    throw ErrorAplicacionException.validacion("El efectivo indicado para el pago mixto supera el total del pedido.");
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

    public List<Pedidos> listarPedidos() {
        List<Pedidos> lista = new ArrayList<>();
        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, s.nombre AS nombre_sala,
                   p.cliente_nombre, p.cliente_documento, p.metodo_pago, p.efectivo_bs, p.efectivo_usd
            FROM pedidos p
            INNER JOIN salas s ON p.id_sala = s.id
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
                try {
                    ped.setClienteNombre(resultados.getString("cliente_nombre"));
                    ped.setClienteDocumento(resultados.getString("cliente_documento"));
                    ped.setMetodoPago(resultados.getString("metodo_pago"));
                    ped.setEfectivoBs(resultados.getBigDecimal("efectivo_bs"));
                    ped.setEfectivoUsd(resultados.getBigDecimal("efectivo_usd"));
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
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto, p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado, s.nombre AS nombre_sala
            FROM pedidos p
            INNER JOIN salas s ON p.id_sala = s.id
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
            WHERE estado = 'PENDIENTE'
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
}
