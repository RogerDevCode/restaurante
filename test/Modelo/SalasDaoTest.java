package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SalasDaoTest {
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
    public void restaurarLogs() {
        logger.removeHandler(capturador);
        logger.setLevel(nivelOriginal);
        logger.setUseParentHandlers(padresOriginales);
    }

    @Test
    public void repositorioPropagaFalloDeConexionConCausaYUnSoloRegistro() {
        SQLException causa = new SQLException("Fallo de conexión de salas inducido", "08001");
        ProveedorConexionJdbc proveedorFalso = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() throws SQLException {
                throw causa;
            }
        };
        SalasRepositorio repositorio = new SalasDao(proveedorFalso);

        DataAccessException error = org.junit.Assert.assertThrows(
                DataAccessException.class, repositorio::listar);

        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
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
