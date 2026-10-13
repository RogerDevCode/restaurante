package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** A2 (atajos InputMap/ActionMap) y C4 (bus con payload, EDT y coalescing). */
public class AtajosUxTest {

    private Sistema sistema;
    private Path tempDir;

    private static class SalasRepositorioFalso implements Modelo.SalasRepositorio {
        public List<Salas> salas = new ArrayList<>();
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sl) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sl) { return false; }
    }
    private static class PlatosRepositorioFalso implements Modelo.PlatosRepositorio {
        public List<Platos> platos = new ArrayList<>();
        @Override public List<Platos> listarPorFecha(String f1, String f2) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("atajos-ux-test-");
        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");

        SalasRepositorioFalso salasRepo = new SalasRepositorioFalso();
        salasRepo.salas.add(new Salas(1, "Principal", 1));
        PlatosRepositorioFalso platosRepo = new PlatosRepositorioFalso();
        platosRepo.platos.add(new Platos(1, "Plato", new BigDecimal("10.00"), LocalDate.now().toString()));

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso() {
            @Override public java.util.Map<Integer, Integer> contarMesasOcupadasPorSala() { return new HashMap<>(); }
        };

        PoliticaAcceso rbac = new PoliticaAcceso(admin);
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, rbac));
        PedidoPdfServicio pdf = new PedidoPdfServicio(pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle,
                () -> new Config(), new GeneradorPdfPedido(tempDir), f -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(new PedidoServicio(pedidosRepo), pdf, rbac,
                new ConsultaPedidosServicio(pedidosRepo, rbac));

        SwingUtilities.invokeAndWait(() -> sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl));
    }

    @After
    public void tearDown() {
        if (sistema != null) {
            sistema.dispose();
        }
    }

    @Test
    public void testAtajosInstaladosEnRootPane() {
        JRootPane root = sistema.getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        for (int vk : new int[]{KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F5}) {
            Object accion = im.get(KeyStroke.getKeyStroke(vk, 0));
            assertNotNull("atajo F" + (vk - KeyEvent.VK_F1 + 1) + " mapeado en rootPane", accion);
            assertNotNull("accion presente para " + accion, am.get(accion));
        }
    }

    @Test
    public void testEscapeAcotadoAlCarrito() throws Exception {
        java.lang.reflect.Field f = Sistema.class.getDeclaredField("jPanel23");
        f.setAccessible(true);
        JComponent carrito = (JComponent) f.get(sistema);
        Object key = carrito.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        assertNotNull("ESC mapeado en el panel del carrito", key);
        assertNotNull("accion ESC presente", carrito.getActionMap().get(key));

        InputMap rootIm = sistema.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertNull("ESC no debe estar secuestrado globalmente",
                rootIm.get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)));
    }

    @Test
    public void testRefrescoCoalescidoYEnEdt() throws Exception {
        final String topico = "test.atajos.coalesce";
        final AtomicInteger llamadas = new AtomicInteger(0);
        final AtomicReference<Object> carga = new AtomicReference<>();
        final AtomicReference<Boolean> enEdt = new AtomicReference<>(false);

        RefrescoVistas.suscribir(topico, payload -> {
            llamadas.incrementAndGet();
            carga.set(payload);
            enEdt.set(SwingUtilities.isEventDispatchThread());
        });

        SwingUtilities.invokeAndWait(() -> {
            RefrescoVistas.notificar(topico, "uno");
            RefrescoVistas.notificar(topico, "dos");
        });
        SwingUtilities.invokeAndWait(() -> { /* drena el invokeLater del bus */ });

        assertEquals("debe coalescer a una sola notificacion", 1, llamadas.get());
        assertEquals("gana la ultima carga util", "dos", carga.get());
        assertTrue("el observador corre en el EDT", enEdt.get());
    }
}
