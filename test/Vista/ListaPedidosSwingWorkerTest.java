package Vista;

import Controlador.PedidosControlador;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ListaPedidosSwingWorkerTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void consultaCorreEnBackgroundYCallbackActualizaEnEdt() throws Exception {
        AtomicBoolean consultaEnEdt = new AtomicBoolean(true);
        AtomicReference<List<Pedidos>> pedidosRecibidos = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override
            public List<Pedidos> listarPedidos() {
                consultaEnEdt.set(SwingUtilities.isEventDispatchThread());
                Pedidos pedido = new Pedidos();
                pedido.setId(17);
                return Collections.singletonList(pedido);
            }
        });

        SwingUtilities.invokeAndWait(() -> new ListaPedidosSwingWorker(controlador, lista -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            pedidosRecibidos.set(lista);
            completado.countDown();
        }, error -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(consultaEnEdt.get());
        assertTrue(pedidosRecibidos.get().get(0).getId() == 17);
    }

    @Test
    public void falloDeConsultaSeEntregaEnEdtConCausaOriginal() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo de historial");
        AtomicReference<Throwable> recibido = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public List<Pedidos> listarPedidos() { throw fallo; }
        });

        SwingUtilities.invokeAndWait(() -> new ListaPedidosSwingWorker(controlador,
                lista -> { throw new AssertionError("No se esperaba una lista"); }, error -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    recibido.set(error);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, recibido.get());
    }

    private PedidosControlador controlador(PedidosRepositorioFalso repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(usuario);
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                id -> { throw new AssertionError("No utilizado"); },
                id -> Collections.emptyList(),
                () -> { throw new AssertionError("No utilizado"); },
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                archivo -> { });
        return new PedidosControlador(new PedidoServicio(repositorio), pdf, politica,
                new ConsultaPedidosServicio(repositorio, politica));
    }
}
