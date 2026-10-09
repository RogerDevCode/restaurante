package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Finaliza en background y diferencia el commit de BD de un fallo posterior del PDF. */
final class FinalizarPedidoSwingWorker extends SwingWorker<FinalizarPedidoSwingWorker.ResultadoFinalizacion, Void> {
    private final PedidosControlador controlador;
    private final int idPedido;
    private final String clienteNombre;
    private final String clienteDocumento;
    private final String metodoPago;
    private final java.math.BigDecimal efectivoBs;
    private final java.math.BigDecimal efectivoUsd;
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
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected ResultadoFinalizacion doInBackground() {
        boolean finalizado = controlador.finalizarPedidoConCliente(idPedido, clienteNombre, clienteDocumento,
                metodoPago, efectivoBs, efectivoUsd);
        if (!finalizado) {
            return new ResultadoFinalizacion(false, false);
        }
        finalizacionConfirmada = true;
        boolean impresoDirecto = controlador.generarPdfPedido(idPedido);
        return new ResultadoFinalizacion(true, impresoDirecto);
    }

    public record ResultadoFinalizacion(boolean finalizado, boolean impresoDirecto) {}

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
