
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
import java.util.List;

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

    public int verificarStado(int mesa, int id_sala){
        if (mesa <= 0 || id_sala <= 0) {
            throw ErrorAplicacionException.validacion("La sala y el número de mesa deben ser válidos.");
        }
        int id_pedido = 0;
        String sql = "SELECT id FROM pedidos WHERE num_mesa=? AND id_sala=? AND estado = ?";
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

        String sqlPedido = "INSERT INTO pedidos (id_sala, num_mesa, total, usuario) VALUES (?,?,?,?)";
        String sqlDetalle = "INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido) VALUES (?,?,?,?,?)";

        try (Connection conexion = conexiones.getConnection()) {
            try {
                conexion.setAutoCommit(false);
                int idPedido;
                try (PreparedStatement sentenciaPedido = conexion.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                    sentenciaPedido.setInt(1, pedido.getId_sala());
                    sentenciaPedido.setInt(2, pedido.getNum_mesa());
                    sentenciaPedido.setBigDecimal(3, importePersistible(pedido.getTotalDecimal(), "El total del pedido"));
                    sentenciaPedido.setString(4, pedido.getUsuario());
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
                if (error instanceof SQLException) {
                    SQLException errorSql = (SQLException) error;
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
        BigDecimal totalCalculado = BigDecimal.ZERO.setScale(2);
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()
                    || detalle.getCantidad() <= 0) {
                throw ErrorAplicacionException.validacion("Cada detalle debe tener nombre y cantidad positiva.");
            }
            BigDecimal precio = importePersistible(detalle.getPrecioDecimal(), "El precio de cada plato");
            totalCalculado = totalCalculado.add(precio.multiply(BigDecimal.valueOf(detalle.getCantidad())));
        }
        BigDecimal total = importePersistible(pedido.getTotalDecimal(), "El total del pedido");
        if (total.compareTo(totalCalculado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles.");
        }
    }

    private BigDecimal importePersistible(BigDecimal importe, String campo) {
        if (importe == null || importe.signum() <= 0 || importe.scale() > 2
                || importe.precision() - importe.scale() > 8) {
            throw ErrorAplicacionException.validacion(campo + " debe ser positivo y caber en DECIMAL(10,2).");
        }
        try {
            return importe.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ErrorAplicacionException.validacion(campo + " debe poder representarse con dos decimales.");
        }
    }

    public List<DetallePedido> verPedidoDetalle(int id_pedido){
       if (id_pedido <= 0) {
           throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
       }
       List<DetallePedido> Lista = new ArrayList<>();
       String sql = "SELECT d.* FROM pedidos p INNER JOIN detalle_pedidos d ON p.id = d.id_pedido WHERE p.id = ?";
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
               Lista.add(det);
           }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
       }
       if (Lista.isEmpty()) {
           throw new ErrorAplicacionException(
                   "El pedido " + id_pedido + " no tiene detalles asociados.",
                   new IllegalStateException("Un pedido debe contener al menos un detalle."));
       }
       return Lista;
   }

    public Pedidos verPedido(int id_pedido){
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
        Pedidos ped = null;
       String sql = "SELECT p.*, s.nombre FROM pedidos p INNER JOIN salas s ON p.id_sala = s.id WHERE p.id = ?";
       try (Connection conexion = conexiones.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql)) {
           sentencia.setInt(1, id_pedido);
           try (ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
               ped = new Pedidos();

               ped.setId(resultados.getInt("id"));
               ped.setFecha(resultados.getString("fecha"));
               ped.setSala(resultados.getString("nombre"));
               ped.setNum_mesa(resultados.getInt("num_mesa"));
               ped.setTotalDecimal(resultados.getBigDecimal("total"));
            }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudo consultar el pedido.", ex);
       }
       if (ped == null) {
           throw new ErrorAplicacionException(
                   "No existe el pedido " + id_pedido + ".",
                   new IllegalStateException("La consulta no encontró el pedido solicitado."));
       }
       return ped;
   }

    public List<DetallePedido> finalizarPedido(int id_pedido){
       if (id_pedido <= 0) {
           throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
       }
       List<DetallePedido> Lista = new ArrayList<>();
       String sql = "SELECT d.* FROM pedidos p INNER JOIN detalle_pedidos d ON p.id = d.id_pedido WHERE p.id = ?";
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
               Lista.add(det);
           }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
       }
       if (Lista.isEmpty()) {
           throw new ErrorAplicacionException(
                   "El pedido " + id_pedido + " no tiene detalles asociados.",
                   new IllegalStateException("Un pedido debe contener al menos un detalle."));
       }
       return Lista;
   }

    public boolean actualizarEstado (int id_pedido){
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para finalizarlo.");
        }
        String sql = "UPDATE pedidos SET estado = ? WHERE id = ?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, "FINALIZADO");
            sentencia.setInt(2, id_pedido);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "finalizar pedido " + id_pedido);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo finalizar el pedido.", ex);
        }
    }

    public List<Pedidos> listarPedidos(){
       List<Pedidos> Lista = new ArrayList<>();
       String sql = "SELECT p.*, s.nombre FROM pedidos p INNER JOIN salas s ON p.id_sala = s.id ORDER BY p.fecha DESC";
       try (Connection conexion = conexiones.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql);
               ResultSet resultados = sentencia.executeQuery()) {
           while (resultados.next()) {
               Pedidos ped = new Pedidos();
               ped.setId(resultados.getInt("id"));
               ped.setSala(resultados.getString("nombre"));
               ped.setNum_mesa(resultados.getInt("num_mesa"));
               ped.setFecha(resultados.getString("fecha"));
               ped.setTotalDecimal(resultados.getBigDecimal("total"));
               ped.setUsuario(resultados.getString("usuario"));
               ped.setEstado(resultados.getString("estado"));
               Lista.add(ped);
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron listar los pedidos.", ex);
       }
       return Lista;
   }

}
