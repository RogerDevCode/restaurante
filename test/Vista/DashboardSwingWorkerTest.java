package Vista;

import Modelo.Cliente;
import Modelo.ClienteRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.EstadisticasDashboard;
import Modelo.Pedidos;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas unitarias y de concurrencia para DashboardSwingWorker.
 */
public class DashboardSwingWorkerTest {

    @Test
    public void ejecutaEnSegundoPlanoYPublicaResultadosExitosos() throws Exception {
        EstadisticasDashboard statsEsperadas = new EstadisticasDashboard();
        statsEsperadas.setVentasHoyDolares(new BigDecimal("150.00"));
        statsEsperadas.setPedidosHoy(5);

        Cliente cli = new Cliente();
        cli.setDocumento("V-99887766");
        cli.setNombre("Pedro Pérez");
        List<Cliente> clientesEsperados = List.of(cli);

        ClienteRepositorio repo = new ClienteRepositorio() {
            @Override
            public boolean guardarOActualizar(Cliente cliente) {
                return true;
            }

            @Override
            public List<Cliente> buscarClientes(String criterio) {
                return clientesEsperados;
            }

            @Override
            public Cliente buscarPorDocumento(String documento) {
                return null;
            }

            @Override
            public List<Pedidos> listarFacturasCliente(String documentoOCriterio) {
                return Collections.emptyList();
            }

            @Override
            public EstadisticasDashboard obtenerEstadisticasDashboard() {
                return statsEsperadas;
            }
        };

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DashboardSwingWorker.DatosDashboard> resultado = new AtomicReference<>();
        AtomicReference<Throwable> errorCapturado = new AtomicReference<>();

        DashboardSwingWorker worker = new DashboardSwingWorker(repo, "Pedro",
                datos -> {
                    resultado.set(datos);
                    latch.countDown();
                },
                err -> {
                    errorCapturado.set(err);
                    latch.countDown();
                });

        worker.execute();
        boolean completado = latch.await(5, TimeUnit.SECONDS);

        assertTrue("El worker debe completar en tiempo", completado);
        assertNull("No debe haber error", errorCapturado.get());
        assertNotNull("Debe entregar datos", resultado.get());
        assertEquals(statsEsperadas, resultado.get().getStats());
        assertEquals(1, resultado.get().getClientes().size());
        assertEquals("Pedro Pérez", resultado.get().getClientes().get(0).getNombre());
    }

    @Test
    public void capturaFalloDeRepositorioYNotificaCallbackDeError() throws Exception {
        ClienteRepositorio repoFalla = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente cliente) { return false; }
            @Override public List<Cliente> buscarClientes(String criterio) { return Collections.emptyList(); }
            @Override public Cliente buscarPorDocumento(String documento) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String documentoOCriterio) { return Collections.emptyList(); }
            @Override
            public EstadisticasDashboard obtenerEstadisticasDashboard() {
                throw new IllegalStateException("Simulando falla de conexión en BD");
            }
        };

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DashboardSwingWorker.DatosDashboard> resultado = new AtomicReference<>();
        AtomicReference<Throwable> errorCapturado = new AtomicReference<>();

        DashboardSwingWorker worker = new DashboardSwingWorker(repoFalla, "",
                resultado::set,
                err -> {
                    errorCapturado.set(err);
                    latch.countDown();
                });

        worker.execute();
        boolean completado = latch.await(5, TimeUnit.SECONDS);

        assertTrue(completado);
        assertNull(resultado.get());
        assertNotNull(errorCapturado.get());
        assertTrue(errorCapturado.get() instanceof IllegalStateException);
        assertEquals("Simulando falla de conexión en BD", errorCapturado.get().getMessage());
    }

    @Test(expected = ErrorAplicacionException.class)
    public void rechazaConstruccionConRepositorioNulo() {
        new DashboardSwingWorker(null, "crit", d -> {}, e -> {});
    }

    @Test(expected = ErrorAplicacionException.class)
    public void rechazaConstruccionConCallbackCompletarNulo() {
        ClienteRepositorio repoVacio = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String c) { return Collections.emptyList(); }
            @Override public Cliente buscarPorDocumento(String d) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String d) { return Collections.emptyList(); }
            @Override public EstadisticasDashboard obtenerEstadisticasDashboard() { return new EstadisticasDashboard(); }
        };
        new DashboardSwingWorker(repoVacio, "crit", null, e -> {});
    }

    @Test
    public void manejaCancelacionCorrectamente() throws Exception {
        CountDownLatch empezo = new CountDownLatch(1);
        CountDownLatch termino = new CountDownLatch(1);
        AtomicBoolean falloInvocado = new AtomicBoolean(false);

        ClienteRepositorio repoLento = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String c) { return Collections.emptyList(); }
            @Override public Cliente buscarPorDocumento(String d) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String d) { return Collections.emptyList(); }
            @Override
            public EstadisticasDashboard obtenerEstadisticasDashboard() {
                empezo.countDown();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new EstadisticasDashboard();
            }
        };

        DashboardSwingWorker worker = new DashboardSwingWorker(repoLento, "",
                d -> termino.countDown(),
                e -> {
                    falloInvocado.set(true);
                    termino.countDown();
                });

        worker.execute();
        assertTrue("El worker debe comenzar a ejecutarse", empezo.await(5, TimeUnit.SECONDS));
        worker.cancel(true);
        assertTrue("done() debe invocarse tras la cancelación", termino.await(5, TimeUnit.SECONDS));

        assertTrue("El worker debe reportar cancelación", worker.isCancelled());
        assertTrue("Callback de fallo/cancelación debe ser invocado", falloInvocado.get());
    }
}
