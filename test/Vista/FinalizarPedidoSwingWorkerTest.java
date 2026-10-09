package Vista;

import Controlador.PedidosControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
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

public class FinalizarPedidoSwingWorkerTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void finalizaYGeneraPdfFueraDelEdtConCallbackEnEdt() throws Exception {
        AtomicBoolean operacionesEnEdt = new AtomicBoolean(true);
        AtomicReference<FinalizarPedidoSwingWorker.ResultadoFinalizacion> finalizado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosRepositorioFalso repositorio = new PedidosRepositorioFalso() {
            @Override public boolean actualizarEstado(int id) {
                operacionesEnEdt.set(SwingUtilities.isEventDispatchThread());
                return true;
            }
        };
        PedidosControlador controlador = controlador(repositorio, archivo -> { 
            operacionesEnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread()); return true;
        });

        SwingUtilities.invokeAndWait(() -> new FinalizarPedidoSwingWorker(controlador, 31, resultado -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            finalizado.set(resultado);
            completado.countDown();
        }, (error, confirmado) -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(operacionesEnEdt.get());
        assertTrue(finalizado.get().finalizado());
    }

    @Test
    public void pedidoSinCambiosNoGeneraPdf() throws Exception {
        AtomicBoolean pdfGenerado = new AtomicBoolean();
        AtomicBoolean pdfConsultado = new AtomicBoolean();
        AtomicReference<FinalizarPedidoSwingWorker.ResultadoFinalizacion> finalizado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public boolean actualizarEstado(int id) { return false; }
        }, archivo -> {  pdfGenerado.set(true); return true; }, pdfConsultado);

        SwingUtilities.invokeAndWait(() -> new FinalizarPedidoSwingWorker(controlador, 31, resultado -> {
            finalizado.set(resultado);
            completado.countDown();
        }, (error, confirmado) -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(finalizado.get().finalizado());
        assertFalse(pdfGenerado.get());
        assertFalse(pdfConsultado.get());
    }

    @Test
    public void fallaDeBaseDeDatosInformaQueNoSeConfirmoLaFinalizacion() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo de base");
        AtomicReference<Throwable> errorRecibido = new AtomicReference<>();
        AtomicReference<Boolean> confirmadoRecibido = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public boolean actualizarEstado(int id) { throw fallo; }
        }, archivo -> { throw new AssertionError("PDF no corresponde"); });

        SwingUtilities.invokeAndWait(() -> new FinalizarPedidoSwingWorker(controlador, 31,
                resultado -> { throw new AssertionError("No se esperaba éxito"); }, (error, confirmado) -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    errorRecibido.set(error);
                    confirmadoRecibido.set(confirmado);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, errorRecibido.get());
        assertFalse(confirmadoRecibido.get());
    }

    @Test
    public void fallaAlAbrirPdfInformaQueLaBaseSiFinalizo() throws Exception {
        IOException fallo = new IOException("visor no disponible");
        AtomicReference<Throwable> errorRecibido = new AtomicReference<>();
        AtomicReference<Boolean> confirmadoRecibido = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosControlador controlador = controlador(new PedidosRepositorioFalso() {
            @Override public boolean actualizarEstado(int id) { return true; }
        }, archivo -> {  throw fallo; });

        SwingUtilities.invokeAndWait(() -> new FinalizarPedidoSwingWorker(controlador, 31,
                resultado -> { throw new AssertionError("No se esperaba éxito"); }, (error, confirmado) -> {
                    errorRecibido.set(error);
                    confirmadoRecibido.set(confirmado);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertTrue(errorRecibido.get() instanceof Modelo.ErrorAplicacionException);
        assertSame(fallo, errorRecibido.get().getCause());
        assertTrue(confirmadoRecibido.get());
    }

    @Test
    public void metodoPagoSePasaCorrectamenteAlRepositorio() throws Exception {
        AtomicReference<String> metodoPagoCapturado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PedidosRepositorioFalso repositorio = new PedidosRepositorioFalso() {
            @Override
            public boolean actualizarEstadoConCliente(int id, String nombre, String doc, String metodoPago) {
                metodoPagoCapturado.set(metodoPago);
                return true;
            }
        };
        PedidosControlador controlador = controlador(repositorio, archivo -> true);

        SwingUtilities.invokeAndWait(() -> new FinalizarPedidoSwingWorker(controlador, 31,
                "Juan", "V-12345678", "TARJETA",
                resultado -> completado.countDown(),
                (error, confirmado) -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertEquals("TARJETA", metodoPagoCapturado.get());
    }

    private PedidosControlador controlador(PedidosRepositorioFalso repositorio,
            PedidoPdfServicio.AbridorPdf abridor) {
        return controlador(repositorio, abridor, new AtomicBoolean());
    }

    private PedidosControlador controlador(PedidosRepositorioFalso repositorio,
            PedidoPdfServicio.AbridorPdf abridor, AtomicBoolean consultaPdf) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(usuario);
        PedidoPdfServicio pdf = new PedidoPdfServicio(id -> {
            consultaPdf.set(true);
            return pedido(id);
        }, id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()), abridor);
        return new PedidosControlador(new PedidoServicio(repositorio), pdf, politica,
                new ConsultaPedidosServicio(repositorio, politica));
    }

    private Pedidos pedido(int id) {
        Pedidos pedido = new Pedidos();
        pedido.setId(id);
        pedido.setTotalDecimal(new BigDecimal("5.00"));
        return pedido;
    }

    private DetallePedido detalle() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("5.00"));
        return detalle;
    }

    private Config configuracion() {
        return new Config(1, "123", "Restaurante", "9", "Santiago", "Gracias");
    }
}
