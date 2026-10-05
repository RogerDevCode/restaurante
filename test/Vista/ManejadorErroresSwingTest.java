package Vista;

import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import java.lang.reflect.InvocationTargetException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ManejadorErroresSwingTest {
    private final Logger loggerManejador = Logger.getLogger(ManejadorErroresSwing.class.getName());
    private final Logger loggerAplicacion = Logger.getLogger(ErrorAplicacionException.class.getName());
    private final Capturador capturadorManejador = new Capturador();
    private final Capturador capturadorAplicacion = new Capturador();
    private Level nivelManejador;
    private Level nivelAplicacion;
    private boolean padresManejador;
    private boolean padresAplicacion;

    @Before
    public void capturarLogs() {
        nivelManejador = loggerManejador.getLevel();
        nivelAplicacion = loggerAplicacion.getLevel();
        padresManejador = loggerManejador.getUseParentHandlers();
        padresAplicacion = loggerAplicacion.getUseParentHandlers();
        loggerManejador.setLevel(Level.ALL);
        loggerAplicacion.setLevel(Level.ALL);
        loggerManejador.setUseParentHandlers(false);
        loggerAplicacion.setUseParentHandlers(false);
        loggerManejador.addHandler(capturadorManejador);
        loggerAplicacion.addHandler(capturadorAplicacion);
    }

    @After
    public void restaurarLogs() {
        loggerManejador.removeHandler(capturadorManejador);
        loggerAplicacion.removeHandler(capturadorAplicacion);
        loggerManejador.setLevel(nivelManejador);
        loggerAplicacion.setLevel(nivelAplicacion);
        loggerManejador.setUseParentHandlers(padresManejador);
        loggerAplicacion.setUseParentHandlers(padresAplicacion);
    }

    @Test
    public void runtimeDeEventoSeRegistraUnaVezYSePresentaSinFiltrarMensajeInterno() throws Exception {
        RuntimeException original = new IllegalStateException("detalle interno con información sensible");
        AtomicInteger presentaciones = new AtomicInteger();
        ManejadorErroresSwing manejador = new ManejadorErroresSwing((error, mensaje) -> {
            assertSame(original, error);
            assertEquals("Ocurrió un error inesperado. El detalle quedó registrado en el log.", mensaje);
            presentaciones.incrementAndGet();
        });

        SwingUtilities.invokeAndWait(() -> manejador.ejecutarConManejo(() -> {
            throw original;
        }));

        assertEquals(1, capturadorManejador.registros.size());
        assertSame(original, capturadorManejador.registros.get(0).getThrown());
        assertEquals(1, presentaciones.get());
    }

    @Test
    public void errorDeEventoSePropagaSinLogDuplicadoEnLaCola() {
        AssertionError original = new AssertionError("error fatal");
        ManejadorErroresSwing manejador = new ManejadorErroresSwing((error, mensaje) -> fail("No debe consumir Error"));

        InvocationTargetException wrapper = org.junit.Assert.assertThrows(InvocationTargetException.class,
                () -> SwingUtilities.invokeAndWait(() -> manejador.ejecutarConManejo(() -> {
                    throw original;
                })));

        assertSame(original, wrapper.getCause());
        assertTrue(capturadorManejador.registros.isEmpty());
    }

    @Test
    public void excepcionDeAplicacionPreviamenteRegistradaSoloSePresentaSinSegundoLog() throws Exception {
        DataAccessException original = new DataAccessException("Mensaje seguro", new IllegalStateException("causa"));
        AtomicInteger presentaciones = new AtomicInteger();
        ManejadorErroresSwing manejador = new ManejadorErroresSwing((error, mensaje) -> {
            assertSame(original, error);
            assertEquals("Mensaje seguro", mensaje);
            presentaciones.incrementAndGet();
        });

        SwingUtilities.invokeAndWait(() -> manejador.ejecutarConManejo(() -> {
            throw original;
        }));

        assertEquals(1, capturadorAplicacion.registros.size());
        assertTrue(capturadorManejador.registros.isEmpty());
        assertEquals(1, presentaciones.get());
    }

    @Test
    public void falloAlMostrarConservaElErrorOriginalComoCausaSuprimida() throws Exception {
        RuntimeException original = new IllegalArgumentException("falló la acción");
        IllegalStateException falloDialogo = new IllegalStateException("falló el diálogo");
        ManejadorErroresSwing manejador = new ManejadorErroresSwing((error, mensaje) -> {
            throw falloDialogo;
        });

        SwingUtilities.invokeAndWait(() -> manejador.ejecutarConManejo(() -> {
            throw original;
        }));

        assertEquals(2, capturadorManejador.registros.size());
        assertSame(original, capturadorManejador.registros.get(0).getThrown());
        assertSame(falloDialogo, capturadorManejador.registros.get(1).getThrown());
        assertEquals(1, falloDialogo.getSuppressed().length);
        assertSame(original, falloDialogo.getSuppressed()[0]);
    }

    @Test
    public void handlerDeHiloNoAtendidoRegistraUnaVezYPresentaEnElEdt() throws Exception {
        RuntimeException original = new IllegalStateException("fallo técnico del hilo");
        AtomicInteger presentaciones = new AtomicInteger();
        AtomicReference<Throwable> presentado = new AtomicReference<>();
        Thread.UncaughtExceptionHandler handler = ManejadorErroresSwing.crearManejadorDeHilos((error, mensaje) -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            assertEquals("Ocurrió un error inesperado. El detalle quedó registrado en el log.", mensaje);
            presentado.set(error);
            presentaciones.incrementAndGet();
        });
        Thread hilo = new Thread(() -> handler.uncaughtException(Thread.currentThread(), original), "hilo-prueba");

        hilo.start();
        hilo.join();
        SwingUtilities.invokeAndWait(() -> { });

        assertSame(original, presentado.get());
        assertEquals(1, presentaciones.get());
        assertEquals(1, capturadorManejador.registros.size());
        assertSame(original, capturadorManejador.registros.get(0).getThrown());
    }

    @Test
    public void instalarRegistraLaColaSwingYElHandlerGlobalEnUnProcesoAislado() throws Exception {
        Path directorioTemporal = Files.createTempDirectory("prueba-handler-global-");
        Process proceso = null;
        try {
            String classpath = absolutizarClasspath(System.getProperty("java.class.path"));
            proceso = new ProcessBuilder(
                    Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djava.awt.headless=true",
                    "-cp", classpath,
                    ManejadorErroresSwingTest.Probe.class.getName())
                    .directory(directorioTemporal.toFile())
                    .redirectErrorStream(true)
                    .start();
            assertTrue("La instalación aislada debe terminar en 10 segundos.", proceso.waitFor(10,
                    java.util.concurrent.TimeUnit.SECONDS));
            String salida = new String(proceso.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("Instalación global incorrecta: " + salida, 0, proceso.exitValue());
            assertTrue(salida, salida.contains("GLOBAL_HANDLER_INSTALLED"));
        } finally {
            if (proceso != null && proceso.isAlive()) {
                proceso.destroyForcibly();
            }
            try (java.util.stream.Stream<Path> rutas = Files.walk(directorioTemporal)) {
                rutas.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                    try {
                        Files.deleteIfExists(ruta);
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
            }
        }
    }

    private static String absolutizarClasspath(String classpath) {
        String separador = System.getProperty("path.separator");
        StringBuilder resultado = new StringBuilder();
        for (String entrada : classpath.split(java.util.regex.Pattern.quote(separador))) {
            if (resultado.length() > 0) {
                resultado.append(separador);
            }
            resultado.append(Paths.get(entrada).toAbsolutePath().normalize());
        }
        return resultado.toString();
    }

    public static final class Probe {
        public static void main(String[] args) {
            Thread.UncaughtExceptionHandler anterior = Thread.getDefaultUncaughtExceptionHandler();
            ManejadorErroresSwing.instalar();
            Thread.UncaughtExceptionHandler actual = Thread.getDefaultUncaughtExceptionHandler();
            if (actual == null || actual == anterior
                    || !(java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue() instanceof ManejadorErroresSwing)) {
                System.exit(2);
            }
            System.out.println("GLOBAL_HANDLER_INSTALLED");
        }
    }

    private static final class Capturador extends Handler {
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
