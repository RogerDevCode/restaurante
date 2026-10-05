package Modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ErrorAplicacionExceptionTest {
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
    public void validacionPropagaCausaYRegistraAdvertencia() {
        ErrorAplicacionException error = ErrorAplicacionException.validacion("Dato requerido");

        assertEquals("Dato requerido", error.getMessage());
        assertNotNull(error.getCause());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
        assertSame(error.getCause(), capturador.registros.get(0).getThrown());
    }

    @Test
    public void falloTecnicoRegistraUnaVezComoSevereYConservaLaCausa() {
        IllegalStateException causa = new IllegalStateException("Error JDBC subyacente");

        ErrorAplicacionException error = new ErrorAplicacionException("No se pudo completar la operación.", causa);

        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
        assertSame(causa, capturador.registros.get(0).getThrown());
    }

    @Test
    public void falloDeAccesoADatosConservaLaSqlExceptionYRegistraUnaVez() {
        SQLException causa = new SQLException("Conexión MySQL no disponible", "08001");

        DataAccessException error = new DataAccessException("No se pudo consultar la base.", causa);

        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
        assertSame(causa, capturador.registros.get(0).getThrown());
    }

    @Test
    public void resultadoDeUnaFilaRegistraLosResultadosInesperados() {
        assertTrue(ErrorAplicacionException.resultadoUnaFila(1, "actualizar sala"));
        assertTrue(capturador.registros.isEmpty());

        assertFalse(ErrorAplicacionException.resultadoUnaFila(0, "actualizar sala"));
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
        assertEquals("actualizar sala", capturador.registros.get(0).getParameters()[0]);
    }

    @Test
    public void daoRechazaArgumentosInvalidosAntesDeAbrirLaBase() {
        ErrorAplicacionException error = org.junit.Assert.assertThrows(
                ErrorAplicacionException.class,
                () -> new LoginDao().log(null, ""));

        assertEquals("El correo y la contraseña son obligatorios.", error.getMessage());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
    }

    @Test
    public void conflictoDeMesaSeRegistraComoAdvertenciaYConservaCausaSql() {
        SQLException causa = new SQLException("índice único de mesa pendiente", "23000", 1062);

        PedidoPendienteExistenteException error = new PedidoPendienteExistenteException(4, causa);

        assertEquals("La mesa 4 ya tiene un pedido pendiente.", error.getMessage());
        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
        assertSame(causa, capturador.registros.get(0).getThrown());
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
