package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import java.util.List;

/** Reglas de aplicación para registrar un pedido completo. */
public final class PedidoServicio {
    private final PedidosRepositorio repositorio;

    public PedidoServicio(PedidosRepositorio repositorio) {
        if (repositorio == null) {
            throw ErrorAplicacionException.validacion("El repositorio de pedidos es obligatorio.");
        }
        this.repositorio = repositorio;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        validar(pedido, detalles);
        return repositorio.registrarPedidoCompleto(pedido, detalles);
    }

    private void validar(Pedidos pedido, List<DetallePedido> detalles) {
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("El pedido es obligatorio.");
        }
        if (pedido.getId_sala() <= 0 || pedido.getNum_mesa() <= 0) {
            throw ErrorAplicacionException.validacion("La sala y el número de mesa deben ser válidos.");
        }
        if (pedido.getUsuario() == null || pedido.getUsuario().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El usuario que registra el pedido es obligatorio.");
        }
        if (!Double.isFinite(pedido.getTotal()) || pedido.getTotal() <= 0) {
            throw ErrorAplicacionException.validacion("El total del pedido debe ser mayor que cero.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        for (DetallePedido detalle : detalles) {
            if (detalle == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle vacío.");
            }
            if (detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()) {
                throw ErrorAplicacionException.validacion("Cada detalle debe incluir el nombre del plato.");
            }
            if (detalle.getCantidad() <= 0) {
                throw ErrorAplicacionException.validacion("La cantidad de cada plato debe ser mayor que cero.");
            }
            if (!Double.isFinite(detalle.getPrecio()) || detalle.getPrecio() <= 0) {
                throw ErrorAplicacionException.validacion("El precio de cada plato debe ser mayor que cero.");
            }
        }
    }
}
