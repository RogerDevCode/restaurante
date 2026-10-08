package Controlador;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import Servicio.ConsultaPedidosServicio;
import java.util.List;
import java.util.Map;

/** Coordina desde la vista el caso de uso de registro completo de pedidos. */
public final class PedidosControlador {
    private final PedidoServicio servicio;
    private final PedidoPdfServicio servicioPdf;
    private final PoliticaAcceso politicaAcceso;
    private final ConsultaPedidosServicio consultas;
    private final Servicio.CierreCajaServicio cierreServicio;

    public PedidosControlador(PedidoServicio servicio, PedidoPdfServicio servicioPdf,
            PoliticaAcceso politicaAcceso, ConsultaPedidosServicio consultas) {
        this(servicio, servicioPdf, politicaAcceso, consultas, null);
    }

    public PedidosControlador(PedidoServicio servicio, PedidoPdfServicio servicioPdf,
            PoliticaAcceso politicaAcceso, ConsultaPedidosServicio consultas,
            Servicio.CierreCajaServicio cierreServicio) {
        if (servicio == null || servicioPdf == null || politicaAcceso == null || consultas == null) {
            throw ErrorAplicacionException.validacion(
                    "Los servicios, consultas y política de acceso de pedidos son obligatorios.");
        }
        this.servicio = servicio;
        this.servicioPdf = servicioPdf;
        this.politicaAcceso = politicaAcceso;
        this.consultas = consultas;
        this.cierreServicio = cierreServicio;
    }

    public Servicio.CierreCajaServicio getCierreServicio() {
        return cierreServicio;
    }

    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        exigirPermiso(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        return servicio.registrarPedidoCompleto(pedido, detalles);
    }

    public void generarPdfPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        servicioPdf.generar(idPedido);
    }

    public void reimprimirPdfPedido(int idPedido) {
        reimprimirPdfPedido(idPedido, "Reimpresión de ticket solicitada", "Sistema");
    }

    public void reimprimirPdfPedido(int idPedido, String motivo, String usuario) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la reimpresión.");
        }
        String usr = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "Sistema";
        try {
            servicioPdf.reimprimir(idPedido);
            consultas.registrarAuditoria(idPedido, "REIMPRESION", motivo.trim(), usr);
        } catch (RuntimeException ex) {
            try {
                consultas.registrarAuditoria(idPedido, "REIMPRESION_FALLIDA", motivo.trim() + " [FALLO: " + ex.getMessage() + "]", usr);
            } catch (Exception ignored) {
            }
            throw ex;
        }
    }

    public boolean anularPedido(int idPedido, String motivo, String usuario) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return consultas.anular(idPedido, motivo, usuario);
    }

    public List<Modelo.AuditoriaPedido> obtenerAuditoriaPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return consultas.obtenerAuditoriaPedido(idPedido);
    }

    public void previsualizarPdfPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        servicioPdf.previsualizar(idPedido);
    }

    public java.nio.file.Path imprimirCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return imprimirCierreCaja(fecha, tipo, usuarioEmisor, null, null);
    }

    public java.nio.file.Path imprimirCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efBs, java.math.BigDecimal efUsd) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        if (cierreServicio == null) {
            throw new IllegalStateException("El servicio de cierre de caja no está configurado.");
        }
        return cierreServicio.imprimirCierre(fecha, tipo, usuarioEmisor, efBs, efUsd);
    }

    public java.nio.file.Path previsualizarCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return previsualizarCierreCaja(fecha, tipo, usuarioEmisor, null, null);
    }

    public java.nio.file.Path previsualizarCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efBs, java.math.BigDecimal efUsd) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        if (cierreServicio == null) {
            throw new IllegalStateException("El servicio de cierre de caja no está configurado.");
        }
        return cierreServicio.previsualizarCierre(fecha, tipo, usuarioEmisor, efBs, efUsd);
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

    public boolean finalizarPedidoConCliente(int idPedido, String clienteNombre, String clienteDocumento) {
        return finalizarPedidoConCliente(idPedido, clienteNombre, clienteDocumento, "EFECTIVO");
    }

    public boolean finalizarPedidoConCliente(int idPedido, String clienteNombre, String clienteDocumento, String metodoPago) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return consultas.finalizarConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago);
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(PedidosControlador.class.getName());

    public Map<Integer, Integer> contarMesasOcupadasPorSala() {
        exigirPermiso(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return consultas.contarMesasOcupadasPorSala();
    }

    public int purgarPedidosFinalizados(int meses) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        int eliminados = consultas.purgarPedidosFinalizados(meses);
        LOGGER.info("PURGA: " + eliminados + " pedidos finalizados eliminados (retención: " + meses + " meses)");
        return eliminados;
    }

    private void exigirPermiso(PoliticaAcceso.Accion accion) {
        if (!politicaAcceso.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }
}
