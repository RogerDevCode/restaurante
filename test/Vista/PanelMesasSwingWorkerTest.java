package Vista;

import Controlador.PedidosControlador;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PanelMesasSwingWorkerTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void buscaTodasLasMesasFueraDelEdtYEntregaElMapaEnEdt() throws Exception {
        AtomicBoolean consultaEnEdt = new AtomicBoolean();
        AtomicInteger consultas = new AtomicInteger();
        AtomicReference<Map<Integer, Integer>> resultado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public int verificarStado(int mesa, int idSala) {
                consultaEnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread());
                consultas.incrementAndGet();
                return mesa == 2 ? 92 : 0;
            }
        });

        SwingUtilities.invokeAndWait(() -> new PanelMesasSwingWorker(controlador, 4, 3, estados -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            resultado.set(estados);
            completado.countDown();
        }, error -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(consultaEnEdt.get());
        assertEquals(3, consultas.get());
        assertEquals(Integer.valueOf(92), resultado.get().get(2));
        assertEquals(Integer.valueOf(0), resultado.get().get(1));
    }

    @Test
    public void falloEnUnaMesaEntregaLaCausaYNoPublicaEstadoParcial() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo de consulta");
        AtomicReference<Throwable> errorRecibido = new AtomicReference<>();
        AtomicBoolean huboLista = new AtomicBoolean();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public int verificarStado(int mesa, int idSala) {
                if (mesa == 2) throw fallo;
                return 0;
            }
        });

        SwingUtilities.invokeAndWait(() -> new PanelMesasSwingWorker(controlador, 4, 3,
                estados -> { huboLista.set(true); completado.countDown(); }, error -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    errorRecibido.set(error);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, errorRecibido.get());
        assertFalse(huboLista.get());
    }

    @Test
    public void callbackAlCompletarRecibeMapaCorrectoParaTodasLasMesas() throws Exception {
        CountDownLatch completado = new CountDownLatch(1);
        AtomicReference<Map<Integer, Integer>> mapaRecibido = new AtomicReference<>();
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override
            public int verificarStado(int mesa, int idSala) {
                if (mesa == 1) return 101;
                if (mesa == 3) return 103;
                if (mesa == 5) return 105;
                return 0; // Mesa 2 y 4 libres
            }
        });

        SwingUtilities.invokeAndWait(() -> new PanelMesasSwingWorker(controlador, 1, 5, estados -> {
            mapaRecibido.set(estados);
            completado.countDown();
        }, error -> {
            throw new AssertionError(error);
        }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        Map<Integer, Integer> estados = mapaRecibido.get();
        assertEquals(5, estados.size());
        assertEquals(Integer.valueOf(101), estados.get(1));
        assertEquals(Integer.valueOf(0), estados.get(2));
        assertEquals(Integer.valueOf(103), estados.get(3));
        assertEquals(Integer.valueOf(0), estados.get(4));
        assertEquals(Integer.valueOf(105), estados.get(5));
    }

    private PedidosControlador controlador(PedidosRepositorioFalso repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(usuario);
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                id -> null, id -> java.util.Collections.emptyList(),
                () -> { throw new AssertionError("No utilizado"); },
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> { });
        return new PedidosControlador(new PedidoServicio(repositorio), pdf, politica,
                new ConsultaPedidosServicio(repositorio, politica));
    }
}
