package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Carga el historial de pedidos fuera del EDT y entrega el estado por callback Swing. */
final class ListaPedidosSwingWorker extends SwingWorker<List<Pedidos>, Void> {
    private final PedidosControlador controlador;
    private final Consumer<List<Pedidos>> alCompletar;
    private final Consumer<Throwable> alFallar;

    ListaPedidosSwingWorker(PedidosControlador controlador, Consumer<List<Pedidos>> alCompletar,
            Consumer<Throwable> alFallar) {
        if (controlador == null || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El controlador y callbacks del historial son obligatorios.");
        }
        this.controlador = controlador;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected List<Pedidos> doInBackground() {
        return controlador.listarPedidos();
    }

    @Override
    protected void done() {
        try {
            alCompletar.accept(get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            alFallar.accept(ex);
        } catch (ExecutionException ex) {
            Throwable causa = ex.getCause() == null ? ex : ex.getCause();
            if (causa instanceof Error) {
                throw (Error) causa;
            }
            alFallar.accept(causa);
        } catch (CancellationException ex) {
            alFallar.accept(ex);
        }
    }
}
