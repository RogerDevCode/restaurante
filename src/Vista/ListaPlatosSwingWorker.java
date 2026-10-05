package Vista;

import Controlador.PlatosControlador;
import Modelo.ErrorAplicacionException;
import Modelo.Platos;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Ejecuta una consulta de menú fuera del EDT y publica resultado o causa en el EDT. */
final class ListaPlatosSwingWorker extends SwingWorker<List<Platos>, Void> {
    private final PlatosControlador controlador;
    private final String filtro;
    private final String fecha;
    private final Consumer<List<Platos>> alCompletar;
    private final Consumer<Throwable> alFallar;

    ListaPlatosSwingWorker(PlatosControlador controlador, String filtro, String fecha,
            Consumer<List<Platos>> alCompletar, Consumer<Throwable> alFallar) {
        if (controlador == null || filtro == null || fecha == null
                || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("Los datos y callbacks para consultar platos son obligatorios.");
        }
        this.controlador = controlador;
        this.filtro = filtro;
        this.fecha = fecha;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected List<Platos> doInBackground() {
        return controlador.listarPorFecha(filtro, fecha);
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
