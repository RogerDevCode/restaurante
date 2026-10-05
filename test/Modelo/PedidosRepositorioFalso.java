package Modelo;

import java.util.List;

/** Adaptador de prueba: cada operación no configurada falla explícitamente. */
public class PedidosRepositorioFalso implements PedidosRepositorio {
    @Override
    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        throw new AssertionError("La prueba debe configurar registrarPedidoCompleto.");
    }

    @Override
    public int verificarStado(int mesa, int idSala) {
        throw new AssertionError("La prueba no configuró verificarStado.");
    }

    @Override
    public Pedidos verPedido(int idPedido) {
        throw new AssertionError("La prueba no configuró verPedido.");
    }

    @Override
    public List<DetallePedido> verPedidoDetalle(int idPedido) {
        throw new AssertionError("La prueba no configuró verPedidoDetalle.");
    }

    @Override
    public List<DetallePedido> finalizarPedido(int idPedido) {
        throw new AssertionError("La prueba no configuró finalizarPedido.");
    }

    @Override
    public boolean actualizarEstado(int idPedido) {
        throw new AssertionError("La prueba no configuró actualizarEstado.");
    }

    @Override
    public List<Pedidos> listarPedidos() {
        throw new AssertionError("La prueba no configuró listarPedidos.");
    }
}
