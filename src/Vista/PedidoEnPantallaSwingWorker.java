package Vista;

import Controlador.PedidosControlador;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Carga encabezado y detalles del pedido fuera del EDT y los publica como una sola unidad. */
final class PedidoEnPantallaSwingWorker extends SwingWorker<PedidoEnPantallaSwingWorker.Resultado, Void> {
    private final PedidosControlador controlador;
    private final int idPedido;
    private final Consumer<Resultado> alCompletar;
    private final Consumer<Throwable> alFallar;

    PedidoEnPantallaSwingWorker(PedidosControlador controlador, int idPedido,
            Consumer<Resultado> alCompletar, Consumer<Throwable> alFallar) {
        if (controlador == null || idPedido <= 0 || alCompletar == null || alFallar == null) {
            throw ErrorAplicacionException.validacion("El pedido y callbacks de carga son obligatorios.");
        }
        this.controlador = controlador;
        this.idPedido = idPedido;
        this.alCompletar = alCompletar;
        this.alFallar = alFallar;
    }

    @Override
    protected Resultado doInBackground() {
        Pedidos pedido = controlador.verPedido(idPedido);
        List<DetallePedido> detalles = controlador.verPedidoDetalle(idPedido);
        return new Resultado(pedido, detalles);
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

    static final class Resultado {
        private final Pedidos pedido;
        private final List<DetallePedido> detalles;

        private Resultado(Pedidos pedido, List<DetallePedido> detalles) {
            this.pedido = pedido;
            this.detalles = detalles;
        }

        Pedidos getPedido() {
            return pedido;
        }

        List<DetallePedido> getDetalles() {
            return detalles;
        }
    }
}
