package Vista;

import Controlador.LoginControlador;
import Modelo.AutenticacionRepositorio;
import Modelo.Usuario;
import Modelo.login;
import Servicio.AutenticacionServicio;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AutenticacionSwingWorkerTest {
    @Test
    public void autenticaFueraDelEdtYEntregaElResultadoEnElEdt() throws Exception {
        AtomicBoolean repositorioEnEdt = new AtomicBoolean(true);
        AtomicBoolean callbackEnEdt = new AtomicBoolean(false);
        AtomicReference<Usuario> recibido = new AtomicReference<>();
        CountDownLatch callbackCompleto = new CountDownLatch(1);
        Usuario esperado = new Usuario();
        esperado.setCorreo("ana@restaurante.test");
        LoginControlador controlador = controladorFalso(repositorioEnEdt, esperado, null);
        AutenticacionSwingWorker worker = new AutenticacionSwingWorker(
                controlador, esperado.getCorreo(), "clave",
                resultado -> {
                    callbackEnEdt.set(SwingUtilities.isEventDispatchThread());
                    recibido.set(resultado.orElse(null));
                    callbackCompleto.countDown();
                }, error -> { throw new AssertionError(error); });

        worker.execute();
        worker.get();
        assertTrue(callbackCompleto.await(5, TimeUnit.SECONDS));

        assertFalse(repositorioEnEdt.get());
        assertTrue(callbackEnEdt.get());
        assertSame(esperado, recibido.get());
    }

    @Test
    public void entregaCausaDelFalloAlCallbackEnElEdt() throws Exception {
        AtomicBoolean repositorioEnEdt = new AtomicBoolean(true);
        AtomicBoolean callbackEnEdt = new AtomicBoolean(false);
        RuntimeException causa = new IllegalStateException("fallo JDBC inducido");
        AtomicReference<Throwable> recibido = new AtomicReference<>();
        CountDownLatch callbackCompleto = new CountDownLatch(1);
        AutenticacionSwingWorker worker = new AutenticacionSwingWorker(
                controladorFalso(repositorioEnEdt, null, causa), "ana@restaurante.test", "clave",
                resultado -> { throw new AssertionError("No se esperaba autenticación exitosa"); },
                error -> {
                    callbackEnEdt.set(SwingUtilities.isEventDispatchThread());
                    recibido.set(error);
                    callbackCompleto.countDown();
                });

        worker.execute();
        org.junit.Assert.assertThrows(ExecutionException.class, worker::get);
        assertTrue(callbackCompleto.await(5, TimeUnit.SECONDS));

        assertFalse(repositorioEnEdt.get());
        assertTrue(callbackEnEdt.get());
        assertSame(causa, recibido.get());
    }

    private LoginControlador controladorFalso(AtomicBoolean repositorioEnEdt,
            Usuario usuario, RuntimeException fallo) {
        AutenticacionRepositorio repositorio = new AutenticacionRepositorio() {
            @Override
            public Optional<login> autenticar(String correo, String clave) {
                repositorioEnEdt.set(SwingUtilities.isEventDispatchThread());
                if (fallo != null) throw fallo;
                return Optional.empty();
            }

            @Override
            public Optional<Usuario> autenticarUsuario(String correo, String clave) {
                repositorioEnEdt.set(SwingUtilities.isEventDispatchThread());
                if (fallo != null) throw fallo;
                return Optional.of(usuario);
            }
        };
        return new LoginControlador(new AutenticacionServicio(repositorio));
    }
}
