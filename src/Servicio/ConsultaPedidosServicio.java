package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import java.util.List;

/** Consultas y finalización de pedidos con permisos explícitos por caso de uso. */
public final class ConsultaPedidosServicio {
    private final PedidosRepositorio repositorio;
    private final PoliticaAcceso politica;

    public ConsultaPedidosServicio(PedidosRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de pedidos son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public int buscarPedidoPendiente(int mesa, int idSala) {
        exigir(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        if (mesa <= 0 || idSala <= 0) {
            throw ErrorAplicacionException.validacion("La sala y mesa deben ser válidas para buscar pedidos.");
        }
        int idPedido = repositorio.verificarStado(mesa, idSala);
        if (idPedido < 0) {
            throw new ErrorAplicacionException(
                    "La consulta devolvió un identificador de pedido no válido.",
                    new IllegalStateException("verificarStado devolvió un ID negativo."));
        }
        return idPedido;
    }

    public List<Pedidos> listarHistorial() {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return repositorio.listarPedidos();
    }

    public Pedidos obtenerPedido(int idPedido) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        validarId(idPedido);
        Pedidos pedido = repositorio.verPedido(idPedido);
        if (pedido == null) {
            throw new ErrorAplicacionException(
                    "No se encontró el pedido solicitado.",
                    new IllegalStateException("El repositorio devolvió null para el ID " + idPedido + "."));
        }
        return pedido;
    }

    public List<DetallePedido> obtenerDetalles(int idPedido) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        validarId(idPedido);
        List<DetallePedido> detalles = repositorio.verPedidoDetalle(idPedido);
        if (detalles == null) {
            throw new ErrorAplicacionException(
                    "No se pudieron cargar los detalles del pedido.",
                    new IllegalStateException("El repositorio devolvió null para los detalles del pedido "
                            + idPedido + "."));
        }
        return detalles;
    }

    public boolean finalizar(int idPedido) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        validarId(idPedido);
        return repositorio.actualizarEstado(idPedido);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }

    private void validarId(int idPedido) {
        if (idPedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
    }
}
