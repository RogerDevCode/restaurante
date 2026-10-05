package Controlador;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import Servicio.ConsultaPedidosServicio;
import java.util.List;

/** Coordina desde la vista el caso de uso de registro completo de pedidos. */
public final class PedidosControlador {
    private final PedidoServicio servicio;
    private final PedidoPdfServicio servicioPdf;
    private final PoliticaAcceso politicaAcceso;
    private final ConsultaPedidosServicio consultas;

    public PedidosControlador(PedidoServicio servicio, PedidoPdfServicio servicioPdf,
            PoliticaAcceso politicaAcceso, ConsultaPedidosServicio consultas) {
        if (servicio == null || servicioPdf == null || politicaAcceso == null || consultas == null) {
            throw ErrorAplicacionException.validacion(
                    "Los servicios, consultas y política de acceso de pedidos son obligatorios.");
        }
        this.servicio = servicio;
        this.servicioPdf = servicioPdf;
        this.politicaAcceso = politicaAcceso;
        this.consultas = consultas;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        exigirPermiso(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        return servicio.registrarPedidoCompleto(pedido, detalles);
    }

    public void generarPdfPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        servicioPdf.generar(idPedido);
    }

    public int buscarPedidoPendiente(int mesa, int idSala) {
        return consultas.buscarPedidoPendiente(mesa, idSala);
    }

    public List<Pedidos> listarPedidos() {
        return consultas.listarHistorial();
    }

    public Pedidos verPedido(int idPedido) {
        return consultas.obtenerPedido(idPedido);
    }

    public List<DetallePedido> verPedidoDetalle(int idPedido) {
        return consultas.obtenerDetalles(idPedido);
    }

    public boolean finalizarPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return consultas.finalizar(idPedido);
    }

    private void exigirPermiso(PoliticaAcceso.Accion accion) {
        if (!politicaAcceso.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }
}
