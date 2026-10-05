package Vista;

import Controlador.SalasControlador;
import Modelo.ErrorAplicacionException;
import Modelo.Salas;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Ejecuta la consulta de salas fuera del EDT y publica su resultado de forma segura. */
final class ListaSalasSwingWorker extends SwingWorker<List<Salas>, Void> {
    private final SalasControlador controlador;
    private final Consumer<List<Salas>> alCompletar;
    private final Consumer<Throwable> alFallar;

    ListaSalasSwingWorker(SalasControlador controlador, Consumer<List<Salas>> alCompletar,
            Consumer<Throwable> alFallar) {
        if (controlador == null || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El controlador y callbacks de salas son obligatorios.");
        }
        this.controlador = controlador;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected List<Salas> doInBackground() {
        return controlador.listar();
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
