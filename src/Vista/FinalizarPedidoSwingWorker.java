package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Finaliza en background y diferencia el commit de BD de un fallo posterior del PDF. */
final class FinalizarPedidoSwingWorker extends SwingWorker<Boolean, Void> {
    private final PedidosControlador controlador;
    private final int idPedido;
    private final Consumer<Boolean> alCompletar;
    private final BiConsumer<Throwable, Boolean> alFallar;
    private volatile boolean finalizacionConfirmada;

    FinalizarPedidoSwingWorker(PedidosControlador controlador, int idPedido,
            Consumer<Boolean> alCompletar, BiConsumer<Throwable, Boolean> alFallar) {
        if (controlador == null || idPedido <= 0 || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El pedido y callbacks de finalización son obligatorios.");
        }
        this.controlador = controlador;
        this.idPedido = idPedido;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected Boolean doInBackground() {
        boolean finalizado = controlador.finalizarPedido(idPedido);
        if (!finalizado) {
            return false;
        }
        finalizacionConfirmada = true;
        controlador.generarPdfPedido(idPedido);
        return true;
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
