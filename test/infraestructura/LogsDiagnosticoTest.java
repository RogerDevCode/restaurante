package infraestructura;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests unitarios para el sistema de diagnóstico de logs:
 * - Registro centralizado de [SQL_ERROR] en proxies JDBC de ControlMantenimientoBD.
 * - Lectura y persistencia de logs diarios vía ArchivoLogDiario.
 */
public class LogsDiagnosticoTest {

    @Rule
    public final TemporaryFolder carpetaTemporal = new TemporaryFolder();

    private String dirLogsOriginal;
    private Path dirLogs;

    @Before
    public void configurarEntorno() throws Exception {
        dirLogsOriginal = System.getProperty("restaurante.logs.dir");
        dirLogs = carpetaTemporal.newFolder("logs_diag").toPath();
        System.setProperty("restaurante.logs.dir", dirLogs.toString());
    }

    @After
    public void restaurarEntorno() {
        if (dirLogsOriginal == null) {
            System.clearProperty("restaurante.logs.dir");
        } else {
            System.setProperty("restaurante.logs.dir", dirLogsOriginal);
        }
    }

    @Test
    public void leerLineasLogRetornaContenidoDelArchivoDiario() throws Exception {
        LocalDate hoy = LocalDate.now();
        Path archivoHoy = dirLogs.resolve("restaurante-" + hoy + ".log");
        List<String> lineasEsperadas = List.of(
                "2026-10-16 10:00:00 INFO [SISTEMA] Arranque exitoso",
                "2026-10-16 10:01:00 WARNING [CARGA_DATOS] PlatosDao.listarPorFecha: 0 platos encontrados",
                "2026-10-16 10:02:00 SEVERE [SQL_ERROR] Fallo al ejecutar consulta (código 1146, SQLState '42S02'): Table 'restaurante.tabla_fantasma' doesn't exist | SQL: SELECT * FROM tabla_fantasma"
        );
        Files.write(archivoHoy, lineasEsperadas);

        List<String> lineasLeidas = ArchivoLogDiario.leerLineasLog(hoy);
        assertEquals(3, lineasLeidas.size());
        assertTrue(lineasLeidas.get(0).contains("[SISTEMA]"));
        assertTrue(lineasLeidas.get(1).contains("[CARGA_DATOS]"));
        assertTrue(lineasLeidas.get(2).contains("[SQL_ERROR]"));
    }

    @Test
    public void leerLineasLogDeFechaSinArchivoRetornaMensajeInformativo() {
        LocalDate fechaInexistente = LocalDate.of(1999, 1, 1);
        List<String> resultado = ArchivoLogDiario.leerLineasLog(fechaInexistente);
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).contains("No se encontró el archivo de log para la fecha 1999-01-01"));
    }

    @Test
    public void controlMantenimientoRegistraSqlErrorAlFallarConsulta() throws Exception {
        Logger loggerControl = Logger.getLogger(ControlMantenimientoBD.class.getName());
        List<LogRecord> logsCapturados = new ArrayList<>();
        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logsCapturados.add(record);
            }
            @Override
            public void flush() {}
            @Override
            public void close() throws SecurityException {}
        };
        loggerControl.addHandler(testHandler);

        try {
            // Mock de Statement que lanza SQLException con error code 1054
            Statement mockStatement = (Statement) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, args) -> {
                        if (method.getName().startsWith("execute")) {
                            throw new SQLException("Unknown column 'campo_inexistente' in 'field list'", "42S22", 1054);
                        }
                        if ("close".equals(method.getName())) return null;
                        return null;
                    }
            );

            // Mock de Connection
            Connection mockConnection = (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("createStatement".equals(method.getName())) {
                            return mockStatement;
                        }
                        if ("getAutoCommit".equals(method.getName())) return true;
                        if ("close".equals(method.getName())) return null;
                        return null;
                    }
            );

            Connection conexionProtegida = ControlMantenimientoBD.proteger(mockConnection);
            Statement stProtegido = conexionProtegida.createStatement();

            boolean falloEsperado = false;
            try {
                stProtegido.executeQuery("SELECT campo_inexistente FROM platos WHERE id=99");
            } catch (SQLException ex) {
                falloEsperado = true;
                assertEquals(1054, ex.getErrorCode());
                assertEquals("42S22", ex.getSQLState());
            }

            assertTrue("La consulta protegida debía propagar la SQLException", falloEsperado);

            // Verificar que se emitió el log [SQL_ERROR]
            boolean encontrado = logsCapturados.stream().anyMatch(r ->
                    r.getMessage().contains("[SQL_ERROR]")
                    && r.getMessage().contains("código 1054")
                    && r.getMessage().contains("SQLState '42S22'")
                    && r.getMessage().contains("campo_inexistente"));

            assertTrue("Debe registrarse un evento [SQL_ERROR] con código, SQLState y SQL", encontrado);
        } finally {
            loggerControl.removeHandler(testHandler);
        }
    }
}
