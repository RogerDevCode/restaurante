package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlatosDaoTest {
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
    public void propagaFalloDeConexionConCausaYUnSoloRegistro() {
        SQLException causa = new SQLException("Fallo de consulta de menú inducido", "08001");
        ProveedorConexionJdbc proveedorFalso = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() throws SQLException {
                throw causa;
            }
        };

        PlatosRepositorio repositorio = new PlatosDao(proveedorFalso);
        DataAccessException error = org.junit.Assert.assertThrows(
                DataAccessException.class,
                () -> repositorio.listarPorFecha("", "2026-10-05"));

        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
        assertSame(causa, capturador.registros.get(0).getThrown());
    }

    @Test
    public void rechazaPrecioNoPositivoAntesDeAbrirConexion() {
        ProveedorConexionJdbc proveedorFalso = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                throw new AssertionError("No debe abrir conexión para precio inválido");
            }
        };
        Platos plato = new Platos();
        plato.setNombre("Plato de prueba");
        plato.setFecha("2026-10-05");
        plato.setPrecioDecimal(BigDecimal.ZERO);

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new PlatosDao(proveedorFalso).registrar(plato));
    }

    @Test
    public void desactivarEjecutaUpdateYNoDelete() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> sqlCapturado = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger parametroId = new java.util.concurrent.atomic.AtomicInteger();

        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if ("setInt".equals(method.getName()) && args != null && args.length == 2) {
                if ((int) args[0] == 1) {
                    parametroId.set((int) args[1]);
                }
                return null;
            }
            if ("executeUpdate".equals(method.getName())) {
                return 1;
            }
            return null;
        };
        java.sql.PreparedStatement psMock = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                psHandler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                sqlCapturado.set((String) args[0]);
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                connHandler);

        PlatosDao dao = new PlatosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        boolean resultado = dao.desactivar(42);

        assertTrue(resultado);
        assertEquals(42, parametroId.get());
        assertNotNull(sqlCapturado.get());
        assertTrue("Debe ser UPDATE y no DELETE", sqlCapturado.get().toUpperCase().startsWith("UPDATE PLATOS"));
        assertTrue(sqlCapturado.get().contains("activo = 0"));
        assertFalse(sqlCapturado.get().toUpperCase().contains("DELETE"));
    }

    @Test
    public void reactivarEjecutaUpdateConActivoUno() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> sqlCapturado = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger parametroId = new java.util.concurrent.atomic.AtomicInteger();

        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if ("setInt".equals(method.getName()) && args != null && args.length == 2) {
                parametroId.set((int) args[1]);
                return null;
            }
            if ("executeUpdate".equals(method.getName())) {
                return 1;
            }
            return null;
        };
        java.sql.PreparedStatement psMock = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                psHandler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                sqlCapturado.set((String) args[0]);
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                connHandler);

        PlatosDao dao = new PlatosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        assertTrue(dao.reactivar(99));
        assertEquals(99, parametroId.get());
        assertTrue(sqlCapturado.get().toUpperCase().startsWith("UPDATE PLATOS"));
        assertTrue(sqlCapturado.get().contains("activo = 1"));
    }

    @Test
    public void listarPorFechaExcluyePlatosInactivosEnSql() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> sqlCapturado = new java.util.concurrent.atomic.AtomicReference<>();

        java.lang.reflect.InvocationHandler rsHandler = (proxy, method, args) -> {
            if ("next".equals(method.getName())) return false;
            return null;
        };
        java.sql.ResultSet rsMock = (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class},
                rsHandler);

        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) return rsMock;
            return null;
        };
        java.sql.PreparedStatement psMock = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                psHandler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                sqlCapturado.set((String) args[0]);
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                connHandler);

        PlatosDao dao = new PlatosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        dao.listarPorFecha("", "2026-10-07");
        assertNotNull(sqlCapturado.get());
        assertTrue(sqlCapturado.get().contains("activo = 1"));
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
