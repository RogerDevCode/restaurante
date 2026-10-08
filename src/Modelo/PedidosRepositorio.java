package Modelo;

import java.util.List;

/** Puerto de persistencia de pedidos, inyectable para probar el servicio sin MySQL. */
public interface PedidosRepositorio {
    int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles);

    /** Devuelve el identificador pendiente de una mesa o cero cuando no tiene pedido abierto. */
    int verificarStado(int mesa, int idSala);

    /** Consulta un pedido existente; falla si el identificador no existe. */
    Pedidos verPedido(int idPedido);

    /** Devuelve los detalles guardados de un pedido. */
    List<DetallePedido> verPedidoDetalle(int idPedido);

    /** Consulta los detalles que se muestran al finalizar un pedido. */
    List<DetallePedido> finalizarPedido(int idPedido);

    /** Marca como finalizado un pedido existente. */
    boolean actualizarEstado(int idPedido);

    /** Marca como finalizado un pedido asociándolo a los datos del cliente. */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento) {
        return actualizarEstadoConCliente(idPedido, clienteNombre, clienteDocumento, "EFECTIVO");
    }

    /** Marca como finalizado un pedido asociándolo a los datos del cliente y método de pago. */
    default boolean actualizarEstadoConCliente(int idPedido, String clienteNombre, String clienteDocumento, String metodoPago) {
        return actualizarEstado(idPedido);
    }

    /** Devuelve el historial de pedidos ordenado por fecha descendente. */
    List<Pedidos> listarPedidos();

    /** Devuelve la cantidad de mesas ocupadas (con pedidos pendientes) agrupadas por id de sala. */
    default java.util.Map<Integer, Integer> contarMesasOcupadasPorSala() {
        return java.util.Collections.emptyMap();
    }

    /** Purga pedidos finalizados anteriores a la cantidad de meses especificada. Retorna la cantidad eliminada. */
    default int purgarPedidosFinalizados(int mesesAnteriores) {
        return 0;
    }

    /** Marca como anulado un pedido existente. */
    default boolean anularPedido(int idPedido) {
        return false;
    }

    /** Marca como anulado un pedido existente y registra su auditoría de forma atómica. */
    default boolean anularPedidoConAuditoria(int idPedido, String motivo, String usuario) {
        return anularPedido(idPedido);
    }
}
