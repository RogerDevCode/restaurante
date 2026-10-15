package Vista;

import Modelo.Platos;
import Modelo.ResultadoActualizacionPrecios;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefrescoPreciosUiTest {

    @Before
    @After
    public void reset() {
        RefrescoVistas.limpiar();
    }

    @Test
    public void notificacionConCargaUtilEntregaEnEdt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ejecutadoEnEdt = new AtomicBoolean(false);
        AtomicReference<Object> cargaRecibida = new AtomicReference<>();

        Platos plato = new Platos();
        plato.setId(10);
        plato.setNombre("Pasticho");
        ResultadoActualizacionPrecios evento = new ResultadoActualizacionPrecios(
                plato, 2, new BigDecimal("100.00"), new BigDecimal("120.00"), List.of(5, 8), 0);

        RefrescoVistas.suscribir(RefrescoVistas.PLATOS, carga -> {
            ejecutadoEnEdt.set(SwingUtilities.isEventDispatchThread());
            cargaRecibida.set(carga);
            latch.countDown();
        });

        // Publicar desde un hilo de fondo
        new Thread(() -> RefrescoVistas.notificar(RefrescoVistas.PLATOS, evento)).start();

        assertTrue("El evento debió entregarse", latch.await(2, TimeUnit.SECONDS));
        assertTrue("Debe despacharse estrictamente en el EDT de Swing", ejecutadoEnEdt.get());
        assertSame("La carga recibida debe ser el objeto original", evento, cargaRecibida.get());
    }

    @Test
    public void notificacionPedidosFiltraPorPedidoActivo() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> cargaRecibida1 = new AtomicReference<>();
        AtomicReference<Object> cargaRecibida2 = new AtomicReference<>();

        ResultadoActualizacionPrecios evento = new ResultadoActualizacionPrecios(
                null, 1, new BigDecimal("50.00"), new BigDecimal("60.00"), List.of(42), 0);

        RefrescoVistas.suscribir(RefrescoVistas.PEDIDOS, carga -> {
            cargaRecibida1.set(carga);
            latch.countDown();
        });

        RefrescoVistas.notificar(RefrescoVistas.PEDIDOS, evento);

        assertTrue("El evento debió entregarse", latch.await(2, TimeUnit.SECONDS));
        assertTrue(cargaRecibida1.get() instanceof ResultadoActualizacionPrecios);
        ResultadoActualizacionPrecios res = (ResultadoActualizacionPrecios) cargaRecibida1.get();
        assertTrue(res.pedidosAfectados().contains(42));
        assertFalse(res.pedidosAfectados().contains(999));
    }
}
