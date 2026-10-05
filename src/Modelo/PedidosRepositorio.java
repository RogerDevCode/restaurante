package Modelo;

import java.util.List;

/** Puerto de persistencia de pedidos, inyectable para probar el servicio sin MySQL. */
public interface PedidosRepositorio {
    int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles);
}
