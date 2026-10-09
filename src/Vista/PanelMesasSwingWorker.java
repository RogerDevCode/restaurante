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

/** Consulta en background los pedidos pendientes y mesoneros de todas las mesas de una sala. */
final class PanelMesasSwingWorker extends SwingWorker<PanelMesasSwingWorker.DatosMesasSala, Void> {

    public static final class DatosMesasSala {
        private final Map<Integer, Integer> pedidosPendientes;
        private final Map<Integer, String> mesonerosMesas;

        public DatosMesasSala(Map<Integer, Integer> pedidosPendientes, Map<Integer, String> mesonerosMesas) {
            this.pedidosPendientes = pedidosPendientes != null ? pedidosPendientes : Collections.emptyMap();
            this.mesonerosMesas = mesonerosMesas != null ? mesonerosMesas : Collections.emptyMap();
        }

        public Map<Integer, Integer> getPedidosPendientes() {
            return pedidosPendientes;
        }

        public Map<Integer, String> getMesonerosMesas() {
            return mesonerosMesas;
        }
    }

    private final PedidosControlador controlador;
    private final int idSala;
    private final int cantidadMesas;
    private final Consumer<DatosMesasSala> alCompletar;
    private final Consumer<Throwable> alFallar;

    PanelMesasSwingWorker(PedidosControlador controlador, int idSala, int cantidadMesas,
            Consumer<DatosMesasSala> alCompletar, Consumer<Throwable> alFallar) {
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
    protected DatosMesasSala doInBackground() {
        Map<Integer, Integer> pedidosPendientes = new LinkedHashMap<>();
        for (int numeroMesa = 1; numeroMesa <= cantidadMesas; numeroMesa++) {
            pedidosPendientes.put(numeroMesa,
                    controlador.buscarPedidoPendiente(numeroMesa, idSala));
        }
        Map<Integer, String> mesoneros = controlador.consultarMesonerosMesasPendientes(idSala);
        return new DatosMesasSala(Collections.unmodifiableMap(pedidosPendientes), mesoneros);
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
