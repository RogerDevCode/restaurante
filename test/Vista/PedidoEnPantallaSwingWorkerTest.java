package Vista;

import Controlador.PedidosControlador;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.math.BigDecimal;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PedidoEnPantallaSwingWorkerTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void cargaEncabezadoYDetallesFueraDelEdtYEntregaAmbosJuntos() throws Exception {
        AtomicBoolean consultaEnEdt = new AtomicBoolean();
        AtomicReference<PedidoEnPantallaSwingWorker.Resultado> resultado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public Pedidos verPedido(int id) {
                consultaEnEdt.set(SwingUtilities.isEventDispatchThread());
                return pedido(id);
            }
            @Override public List<DetallePedido> verPedidoDetalle(int id) {
                consultaEnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread());
                return Collections.singletonList(detalle());
            }
        });

        SwingUtilities.invokeAndWait(() -> new PedidoEnPantallaSwingWorker(controlador, 13, cargado -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            resultado.set(cargado);
            completado.countDown();
        }, error -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(consultaEnEdt.get());
        assertEquals(13, resultado.get().getPedido().getId());
        assertEquals(1, resultado.get().getDetalles().size());
    }

    @Test
    public void falloDeDetallesNoPublicaPedidoParcialYConservaCausa() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo en detalles");
        AtomicReference<Throwable> errorRecibido = new AtomicReference<>();
        AtomicBoolean huboResultado = new AtomicBoolean();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public Pedidos verPedido(int id) { return pedido(id); }
            @Override public List<DetallePedido> verPedidoDetalle(int id) { throw fallo; }
        });

        SwingUtilities.invokeAndWait(() -> new PedidoEnPantallaSwingWorker(controlador, 13,
                cargado -> { huboResultado.set(true); completado.countDown(); }, error -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    errorRecibido.set(error);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, errorRecibido.get());
        assertFalse(huboResultado.get());
    }

    private PedidosControlador controlador(PedidosRepositorioFalso repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(usuario);
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                id -> pedido(id), id -> Collections.singletonList(detalle()),
                () -> { throw new AssertionError("No utilizado"); },
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> true);
        return new PedidosControlador(new PedidoServicio(repositorio), pdf, politica,
                new ConsultaPedidosServicio(repositorio, politica));
    }

    private static Pedidos pedido(int id) {
        Pedidos pedido = new Pedidos();
        pedido.setId(id);
        pedido.setTotalDecimal(new BigDecimal("5.00"));
        return pedido;
    }

    private static DetallePedido detalle() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("5.00"));
        return detalle;
    }
}
