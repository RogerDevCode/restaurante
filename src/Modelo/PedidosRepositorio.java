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

    /** Devuelve el historial de pedidos ordenado por fecha descendente. */
    List<Pedidos> listarPedidos();
}
