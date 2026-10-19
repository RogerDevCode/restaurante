package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import Servicio.ResultadoSalidaTicket;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/** Finaliza en background y diferencia el commit de BD de un fallo posterior del PDF. */
final class FinalizarPedidoSwingWorker extends SwingWorker<FinalizarPedidoSwingWorker.ResultadoFinalizacion, Void> {
    private static final Logger LOGGER = Logger.getLogger(FinalizarPedidoSwingWorker.class.getName());

    private final PedidosControlador controlador;
    private final int idPedido;
    private final String clienteNombre;
    private final String clienteDocumento;
    private final String metodoPago;
    private final java.math.BigDecimal efectivoBs;
    private final java.math.BigDecimal efectivoUsd;
    private final java.util.List<Modelo.PagoPedido> pagosMixtos;
    private final Consumer<ResultadoFinalizacion> alCompletar;
    private final BiConsumer<Throwable, Boolean> alFallar;
    private volatile boolean finalizacionConfirmada;

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            Consumer<ResultadoFinalizacion> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        this(controlador, idPedido, "Consumidor Final", "V-00000000", "EFECTIVO", alCompletar, alFallar);
    }

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            String clienteNombre, String clienteDocumento,
            Consumer<ResultadoFinalizacion> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        this(controlador, idPedido, clienteNombre, clienteDocumento, "EFECTIVO", alCompletar, alFallar);
    }

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            String clienteNombre, String clienteDocumento, String metodoPago,
            Consumer<ResultadoFinalizacion> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        this(controlador, idPedido, clienteNombre, clienteDocumento, metodoPago,
                null, null, alCompletar, alFallar);
    }

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            String clienteNombre, String clienteDocumento, String metodoPago,
            java.math.BigDecimal efectivoBs, java.math.BigDecimal efectivoUsd,
            Consumer<ResultadoFinalizacion> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        if (controlador == null || idPedido <= 0 || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El pedido y callbacks de finalización son obligatorios.");
        }
        this.controlador = controlador;
        this.idPedido = idPedido;
        this.clienteNombre = clienteNombre != null && !clienteNombre.trim().isEmpty() ? clienteNombre.trim() : "Consumidor Final";
        this.clienteDocumento = clienteDocumento != null && !clienteDocumento.trim().isEmpty() ? clienteDocumento.trim() : "V-00000000";
        this.metodoPago = metodoPago != null && !metodoPago.trim().isEmpty() ? metodoPago.trim().toUpperCase() : "EFECTIVO";
        this.efectivoBs = efectivoBs;
        this.efectivoUsd = efectivoUsd;
        this.pagosMixtos = null;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            String clienteNombre, String clienteDocumento, String metodoPago,
            java.util.List<Modelo.PagoPedido> pagosMixtos,
            Consumer<ResultadoFinalizacion> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        if (controlador == null || idPedido <= 0 || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El pedido y callbacks de finalización son obligatorios.");
        }
        this.controlador = controlador;
        this.idPedido = idPedido;
        this.clienteNombre = clienteNombre != null && !clienteNombre.trim().isEmpty() ? clienteNombre.trim() : "Consumidor Final";
        this.clienteDocumento = clienteDocumento != null && !clienteDocumento.trim().isEmpty() ? clienteDocumento.trim() : "V-00000000";
        this.metodoPago = metodoPago != null && !metodoPago.trim().isEmpty() ? metodoPago.trim().toUpperCase() : "EFECTIVO";
        this.efectivoBs = null;
        this.efectivoUsd = null;
        this.pagosMixtos = pagosMixtos != null ? java.util.List.copyOf(pagosMixtos) : null;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected ResultadoFinalizacion doInBackground() {
        boolean finalizado = pagosMixtos != null
                ? controlador.finalizarPedidoConCliente(idPedido, clienteNombre, clienteDocumento, metodoPago, pagosMixtos)
                : controlador.finalizarPedidoConCliente(idPedido, clienteNombre, clienteDocumento,
                        metodoPago, efectivoBs, efectivoUsd);
        if (!finalizado) {
            return new ResultadoFinalizacion(false, ResultadoSalidaTicket.fallo("N/A", "Pedido no finalizado en BD"));
        }
        finalizacionConfirmada = true;

        // La factura ya está confirmada en BD. Los fallos de PDF/impresión NO deben
        // propagarse como excepción fatal: la venta es real independientemente del ticket físico.
        ResultadoSalidaTicket salidaTicket;
        try {
            salidaTicket = controlador.generarPdfPedidoConResultado(idPedido);
            if (salidaTicket == null) {
                salidaTicket = ResultadoSalidaTicket.fallo("PDF", "El servicio no devolvió resultado de salida.");
            }
        } catch (RuntimeException errorSalida) {
            LOGGER.log(Level.WARNING, "No se pudo procesar el ticket del pedido #" + idPedido, errorSalida);
            salidaTicket = ResultadoSalidaTicket.fallo("PDF", errorSalida.getMessage());
        }
        return new ResultadoFinalizacion(true, salidaTicket);
    }

    /**
     * Resultado de la operación de finalización de pedido.
     *
     * @param finalizado   true si el cobro se registró correctamente en BD.
     * @param salidaTicket resultado tipado de la impresión/visualización del ticket.
     */
    public record ResultadoFinalizacion(boolean finalizado, ResultadoSalidaTicket salidaTicket) {
        /** Compatibilidad: true si el ticket fue enviado al spooler físico. */
        public boolean impresoDirecto() {
            return salidaTicket != null
                    && salidaTicket.estado() == ResultadoSalidaTicket.EstadoSalida.ENVIADO_A_SPOOLER;
        }
    }

    @Override
    protected void done() {
        try {
            alCompletar.accept(get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            alFallar.accept(ex, finalizacionConfirmada);
        } catch (ExecutionException ex) {
            Throwable causa = ex.getCause() == null ? ex : ex.getCause();
            if (causa instanceof Error) {
                throw (Error) causa;
            }
            alFallar.accept(causa, finalizacionConfirmada);
        } catch (CancellationException ex) {
            alFallar.accept(ex, finalizacionConfirmada);
        }
    }
}
