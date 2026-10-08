package Vista;

import Modelo.Cliente;
import Modelo.ClienteRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.EstadisticasDashboard;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Consulta estadísticas del dashboard y lista de clientes fuera del EDT para evitar congelar la interfaz.
 */
public final class DashboardSwingWorker extends SwingWorker<DashboardSwingWorker.DatosDashboard, Void> {

    public static final class DatosDashboard {
        private final EstadisticasDashboard stats;
        private final List<Cliente> clientes;

        public DatosDashboard(EstadisticasDashboard stats, List<Cliente> clientes) {
            this.stats = stats;
            this.clientes = clientes;
        }

        public EstadisticasDashboard getStats() {
            return stats;
        }

        public List<Cliente> getClientes() {
            return clientes;
        }
    }

    private final ClienteRepositorio repositorio;
    private final String criterioClientes;
    private final Consumer<DatosDashboard> alCompletar;
    private final Consumer<Throwable> alFallar;

    public DashboardSwingWorker(ClienteRepositorio repositorio, String criterioClientes,
            Consumer<DatosDashboard> alCompletar, Consumer<Throwable> alFallar) {
        if (repositorio == null || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El repositorio y callbacks de dashboard son obligatorios.");
        }
        this.repositorio = repositorio;
        this.criterioClientes = criterioClientes != null ? criterioClientes.trim() : "";
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected DatosDashboard doInBackground() {
        EstadisticasDashboard stats = repositorio.obtenerEstadisticasDashboard();
        List<Cliente> clientes = repositorio.buscarClientes(criterioClientes);
        return new DatosDashboard(stats, clientes);
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            alFallar.accept(new CancellationException("La tarea de dashboard fue cancelada."));
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
