package Vista;

import Controlador.SalasControlador;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
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

public class ListaSalasSwingWorkerTest {
    @Test
    public void consultaCorreFueraDelEdtYResultadoVuelveAlEdt() throws Exception {
        AtomicBoolean consultaEnEdt = new AtomicBoolean(true);
        CountDownLatch completado = new CountDownLatch(1);
        AtomicReference<List<Salas>> resultado = new AtomicReference<>();
        SalasControlador controlador = controlador(new RepositorioFalso() {
            @Override
            public List<Salas> listar() {
                consultaEnEdt.set(SwingUtilities.isEventDispatchThread());
                return Collections.singletonList(new Salas(3, "Principal", 4));
            }
        });

        SwingUtilities.invokeAndWait(() -> new ListaSalasSwingWorker(controlador, lista -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            resultado.set(lista);
            completado.countDown();
        }, error -> {
            throw new AssertionError(error);
        }).execute());

        assertTrue("La consulta debe completar", completado.await(5, TimeUnit.SECONDS));
        assertFalse("JDBC/consulta no debe ejecutarse en EDT", consultaEnEdt.get());
        assertTrue(resultado.get().get(0).getId() == 3);
    }

    @Test
    public void falloSeEntregaEnEdtConLaCausaOriginal() throws Exception {
        IllegalStateException fallo = new IllegalStateException("fallo de consulta");
        CountDownLatch completado = new CountDownLatch(1);
        AtomicReference<Throwable> resultado = new AtomicReference<>();
        SalasControlador controlador = controlador(new RepositorioFalso() {
            @Override
            public List<Salas> listar() {
                throw fallo;
            }
        });

        SwingUtilities.invokeAndWait(() -> new ListaSalasSwingWorker(controlador,
                lista -> { throw new AssertionError("No se esperaba una lista"); }, error -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    resultado.set(error);
                    completado.countDown();
                }).execute());

        assertTrue("El error debe publicarse", completado.await(5, TimeUnit.SECONDS));
        assertSame(fallo, resultado.get());
    }

    private SalasControlador controlador(SalasRepositorio repositorio) {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        return new SalasControlador(new SalasServicio(repositorio, new PoliticaAcceso(usuario)));
    }

    private static class RepositorioFalso implements SalasRepositorio {
        @Override public boolean registrar(Salas sala) { throw new AssertionError("No utilizado"); }
        @Override public List<Salas> listar() { return Collections.emptyList(); }
        @Override public boolean eliminar(int id) { throw new AssertionError("No utilizado"); }
        @Override public boolean modificar(Salas sala) { throw new AssertionError("No utilizado"); }
    }
}
