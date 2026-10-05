package Vista;

import Controlador.PedidosControlador;
import Modelo.ErrorAplicacionException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Consulta en background los pedidos pendientes de todas las mesas de una sala. */
final class PanelMesasSwingWorker extends SwingWorker<Map<Integer, Integer>, Void> {
    private final PedidosControlador controlador;
    private final int idSala;
    private final int cantidadMesas;
    private final Consumer<Map<Integer, Integer>> alCompletar;
    private final Consumer<Throwable> alFallar;

    PanelMesasSwingWorker(PedidosControlador controlador, int idSala, int cantidadMesas,
            Consumer<Map<Integer, Integer>> alCompletar, Consumer<Throwable> alFallar) {
        if (controlador == null || idSala <= 0 || cantidadMesas <= 0
                || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El controlador, sala, mesas y callbacks son obligatorios.");
        }
        this.controlador = controlador;
        this.idSala = idSala;
        this.cantidadMesas = cantidadMesas;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected Map<Integer, Integer> doInBackground() {
        Map<Integer, Integer> pedidosPendientes = new LinkedHashMap<>();
        for (int numeroMesa = 1; numeroMesa <= cantidadMesas; numeroMesa++) {
            pedidosPendientes.put(numeroMesa,
                    controlador.buscarPedidoPendiente(numeroMesa, idSala));
        }
        return Collections.unmodifiableMap(pedidosPendientes);
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
