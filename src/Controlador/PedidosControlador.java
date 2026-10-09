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

    public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedido, List<DetallePedido> detalles) {
        exigirPermiso(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        return servicio.actualizarPedidoCompleto(idPedido, pedido, detalles);
    }

    public boolean generarPdfPedido(int idPedido) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return servicioPdf.generar(idPedido);
    }

    public boolean reimprimirPdfPedido(int idPedido) {
        return reimprimirPdfPedido(idPedido, "Reimpresión de ticket solicitada", "Sistema");
    }

    public boolean reimprimirPdfPedido(int idPedido, String motivo, String usuario) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar el motivo de la reimpresión.");
        }
        String usr = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "Sistema";
        try {
            boolean ok = servicioPdf.reimprimir(idPedido);
            consultas.registrarAuditoria(idPedido, "REIMPRESION", motivo.trim(), usr);
            return ok;
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

    public Servicio.CierreCajaServicio.ResultadoCierre imprimirCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return imprimirCierreCaja(fecha, tipo, usuarioEmisor, null, null);
    }

    public Servicio.CierreCajaServicio.ResultadoCierre imprimirCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efBs, java.math.BigDecimal efUsd) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        if (cierreServicio == null) {
            throw new IllegalStateException("El servicio de cierre de caja no está configurado.");
        }
        return cierreServicio.imprimirCierre(fecha, tipo, usuarioEmisor, efBs, efUsd);
    }

    public Servicio.CierreCajaServicio.ResultadoCierre previsualizarCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor) {
        return previsualizarCierreCaja(fecha, tipo, usuarioEmisor, null, null);
    }

    public Servicio.CierreCajaServicio.ResultadoCierre previsualizarCierreCaja(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor, java.math.BigDecimal efBs, java.math.BigDecimal efUsd) {
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
        return finalizarPedidoConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago, null, null);
    }

    public boolean finalizarPedidoConCliente(int idPedido, String clienteNombre, String clienteDocumento,
            String metodoPago, java.math.BigDecimal efectivoBs, java.math.BigDecimal efectivoUsd) {
        exigirPermiso(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS);
        return consultas.finalizarConCliente(idPedido, clienteNombre, clienteDocumento,
                metodoPago, efectivoBs, efectivoUsd);
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(PedidosControlador.class.getName());

    public Map<Integer, Integer> contarMesasOcupadasPorSala() {
        exigirPermiso(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return consultas.contarMesasOcupadasPorSala();
    }

    public Map<Integer, String> consultarMesonerosMesasPendientes(int idSala) {
        exigirPermiso(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return consultas.consultarMesonerosMesasPendientes(idSala);
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
