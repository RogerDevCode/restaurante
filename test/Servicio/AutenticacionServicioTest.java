package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AutenticacionServicioTest {
    private final Logger logger = Logger.getLogger(ErrorAplicacionException.class.getName());
    private final CapturadorLogs capturador = new CapturadorLogs();
    private Level nivelOriginal;
    private boolean padresOriginales;

    @Before
    public void capturarLogs() {
        nivelOriginal = logger.getLevel();
        padresOriginales = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(capturador);
    }

    @After
    public void restaurarLogger() {
        logger.removeHandler(capturador);
        logger.setLevel(nivelOriginal);
        logger.setUseParentHandlers(padresOriginales);
    }

    @Test
    public void validaAntesDeInvocarElRepositorio() {
        AtomicInteger llamadas = new AtomicInteger();
        AutenticacionServicio servicio = new AutenticacionServicio((correo, clave) -> {
            llamadas.incrementAndGet();
            return Optional.empty();
        });

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> servicio.autenticar("  ", "clave"));

        assertEquals(0, llamadas.get());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
    }

    @Test
    public void entregaCredencialesLimpiasYDevuelveUsuarioAutenticado() {
        Usuario usuario = new Usuario(7, "Ana", "ana@restaurante.cl", "secreto", "Administrador");
        AutenticacionRepositorio repositorio = (correo, clave) -> {
            assertEquals("ana@restaurante.cl", correo);
            assertEquals("secreto", clave);
            return Optional.of(usuario);
        };

        Optional<Usuario> resultado = new AutenticacionServicio(repositorio)
                .autenticar(" ana@restaurante.cl ", "secreto");

        assertTrue(resultado.isPresent());
        assertSame(usuario, resultado.get());
    }

    @Test
    public void validaFormatoYCamposAntesDeConsultarRepositorio() {
        Usuario usuarioEsperado = new Usuario(12, "Leo", "leo@example.test", "clave", "Administrador");
        AtomicInteger llamadas = new AtomicInteger();
        AutenticacionRepositorio repositorio = (correo, clave) -> {
            llamadas.incrementAndGet();
            assertEquals("leo@example.test", correo);
            return Optional.of(usuarioEsperado);
        };

        Optional<Usuario> usuario = new AutenticacionServicio(repositorio)
                .autenticar(" leo@example.test ", "clave");

        assertTrue(usuario.isPresent());
        assertSame(usuarioEsperado, usuario.get());
        assertEquals(1, llamadas.get());
        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new AutenticacionServicio(repositorio).autenticar(" ", "clave"));
        assertEquals(1, llamadas.get());
    }

    @Test
    public void devuelveRechazoComoResultadoEsperadoSinOcultarErroresTecnicos() {
        AutenticacionServicio servicio = new AutenticacionServicio((correo, clave) -> Optional.empty());
        assertFalse(servicio.autenticar("ana@restaurante.cl", "incorrecta").isPresent());

        DataAccessException falloRepositorio = new DataAccessException("Fallo de BD", new IllegalStateException());
        AutenticacionServicio servicioConFallo = new AutenticacionServicio((correo, clave) -> {
            throw falloRepositorio;
        });
        DataAccessException propagado = org.junit.Assert.assertThrows(DataAccessException.class,
                () -> servicioConFallo.autenticar("ana@restaurante.cl", "secreto"));
        assertSame(falloRepositorio, propagado);
        assertEquals("El servicio solo propaga el fallo ya registrado por la capa que lo originó.",
                1, capturador.registros.size());
    }

    private static final class CapturadorLogs extends Handler {
        private final List<LogRecord> registros = new ArrayList<>();

        @Override
        public void publish(LogRecord registro) {
            if (registro != null && isLoggable(registro)) {
                registros.add(registro);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
