package Vista;

import Controlador.PlatosControlador;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Usuario;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ListaPlatosSwingWorkerTest {
    @Test
    public void consultaYCallbackSeEjecutanEnHilosCorrectos() throws Exception {
        AtomicBoolean consultoEnEdt = new AtomicBoolean(true);
        AtomicReference<String> filtroRecibido = new AtomicReference<>();
        AtomicReference<String> fechaRecibida = new AtomicReference<>();
        AtomicReference<List<Platos>> resultado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PlatosControlador controlador = controlador(new RepositorioFalso() {
            @Override
            public List<Platos> listarPorFecha(String nombre, String fecha) {
                consultoEnEdt.set(SwingUtilities.isEventDispatchThread());
                filtroRecibido.set(nombre);
                fechaRecibida.set(fecha);
                return Collections.singletonList(new Platos(4, "Sopa", new java.math.BigDecimal("4.50"), fecha));
            }
        });

        SwingUtilities.invokeAndWait(() -> new ListaPlatosSwingWorker(controlador, "sopa", "2026-10-05",
                lista -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    resultado.set(lista);
                    completado.countDown();
                }, error -> { throw new AssertionError(error); }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertFalse(consultoEnEdt.get());
        assertTrue("sopa".equals(filtroRecibido.get()));
        assertTrue("2026-10-05".equals(fechaRecibida.get()));
        assertTrue(resultado.get().get(0).getId() == 4);
    }

    @Test
    public void publicaLaCausaOriginalDelFalloEnElEdt() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo simulado");
        AtomicReference<Throwable> resultado = new AtomicReference<>();
        CountDownLatch completado = new CountDownLatch(1);
        PlatosControlador controlador = controlador(new RepositorioFalso() {
            @Override public List<Platos> listarPorFecha(String nombre, String fecha) { throw fallo; }
        });

        SwingUtilities.invokeAndWait(() -> new ListaPlatosSwingWorker(controlador, "", "2026-10-05",
                lista -> { throw new AssertionError("No se esperaba resultado"); }, error -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    resultado.set(error);
                    completado.countDown();
                }).execute());

        assertTrue(completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, resultado.get());
    }

    private PlatosControlador controlador(PlatosRepositorio repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        return new PlatosControlador(new PlatosServicio(repositorio, new PoliticaAcceso(usuario)));
    }

    private static class RepositorioFalso implements PlatosRepositorio {
        @Override public boolean registrar(Platos plato) { throw new AssertionError("No utilizado"); }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return Collections.emptyList(); }
        @Override public boolean eliminar(int id) { throw new AssertionError("No utilizado"); }
        @Override public boolean modificar(Platos plato) { throw new AssertionError("No utilizado"); }
    }
}
