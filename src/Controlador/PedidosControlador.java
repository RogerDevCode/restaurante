package Controlador;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Servicio.PedidoServicio;
import java.util.List;

/** Coordina desde la vista el caso de uso de registro completo de pedidos. */
public final class PedidosControlador {
    private final PedidoServicio servicio;

    public PedidosControlador() {
        this(new PedidoServicio(new PedidosDao()));
    }

    public PedidosControlador(PedidoServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de pedidos es obligatorio.");
        }
        this.servicio = servicio;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        return servicio.registrarPedidoCompleto(pedido, detalles);
    }
}
