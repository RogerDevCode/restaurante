package Vista;

import Modelo.CierreCaja;
import Modelo.ErrorAplicacionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Consulta el Resumen de Ventas (totales USD/Bs y desglose por método de pago) fuera del EDT
 * para evitar congelar la interfaz del Dashboard.
 */
public final class ResumenVentasSwingWorker extends SwingWorker<CierreCaja, Void> {

    public interface ConsultaResumen {
        CierreCaja consultar() throws Exception;
    }

    private final ConsultaResumen consulta;
    private final Consumer<CierreCaja> alCompletar;
    private final Consumer<Throwable> alFallar;

    public ResumenVentasSwingWorker(ConsultaResumen consulta, Consumer<CierreCaja> alCompletar, Consumer<Throwable> alFallar) {
        if (consulta == null || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("La consulta y los callbacks del resumen de ventas son obligatorios.");
        }
        this.consulta = consulta;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected CierreCaja doInBackground() throws Exception {
        return consulta.consultar();
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            alFallar.accept(new CancellationException("La tarea del resumen de ventas fue cancelada."));
            return;
        }
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
