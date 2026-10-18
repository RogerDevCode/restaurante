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

    @Test
    public void registrarEjecutaInsertConColumnasMonetariasDuales() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> sqlCapturado = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.Map<Integer, Object> parametros = new java.util.HashMap<>();

        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if (method.getName().startsWith("set") && args != null && args.length >= 2) {
                parametros.put((Integer) args[0], args[1]);
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

        Platos plato = new Platos();
        plato.setNombre("Pabellon Criollo");
        plato.setFecha("2026-10-05");
        plato.setMonedaBase("VES");
        plato.setPrecioVes(new BigDecimal("120.00"));
        plato.setPrecioUsd(new BigDecimal("3.00"));
        plato.setAplicaIva(true);
        plato.setPrecioIncluyeIva(true);
        plato.setPrecioClasificado(true);

        boolean ok = dao.registrar(plato);
        assertTrue(ok);
        assertNotNull(sqlCapturado.get());
        assertTrue("SQL debe incluir moneda_base", sqlCapturado.get().contains("moneda_base"));
        assertTrue("SQL debe incluir precio_usd", sqlCapturado.get().contains("precio_usd"));
        assertTrue("SQL debe incluir precio_ves", sqlCapturado.get().contains("precio_ves"));
        assertTrue("SQL debe incluir precio_clasificado", sqlCapturado.get().contains("precio_clasificado"));
        assertTrue("SQL debe incluir precio_incluye_iva", sqlCapturado.get().contains("precio_incluye_iva"));

        assertEquals("VES", parametros.get(5));
        assertEquals(new BigDecimal("3.00"), parametros.get(6));
        assertEquals(new BigDecimal("120.00"), parametros.get(7));
        assertEquals(1, parametros.get(8)); // clasificado
        assertEquals(1, parametros.get(9)); // incluye IVA
    }

    @Test
    public void modificarEjecutaUpdateConColumnasMonetariasDuales() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> sqlCapturado = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.Map<Integer, Object> parametros = new java.util.HashMap<>();

        java.sql.ResultSet rsVacio = (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class},
                (proxy, method, args) -> "next".equals(method.getName()) ? Boolean.FALSE : null);
        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if (method.getName().startsWith("set") && args != null && args.length >= 2) {
                parametros.put((Integer) args[0], args[1]);
                return null;
            }
            if ("executeUpdate".equals(method.getName())) {
                return 1;
            }
            if ("executeQuery".equals(method.getName())) {
                return rsVacio;
            }
            return null;
        };
        java.sql.PreparedStatement psMock = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                psHandler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                String sql = (String) args[0];
                if (sql != null && sql.trim().toUpperCase().startsWith("UPDATE")) {
                    sqlCapturado.set(sql);
                }
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

        Platos plato = new Platos();
        plato.setId(15);
        plato.setNombre("Hamburguesa Doble");
        plato.setFecha("2026-10-05");
        plato.setMonedaBase("USD");
        plato.setPrecioUsd(new BigDecimal("8.50"));
        plato.setPrecioVes(new BigDecimal("340.00"));
        plato.setAplicaIva(false);
        plato.setPrecioIncluyeIva(false);
        plato.setPrecioClasificado(true);

        boolean ok = dao.modificar(plato);
        assertTrue(ok);
        assertNotNull(sqlCapturado.get());
        assertTrue("SQL debe incluir moneda_base", sqlCapturado.get().contains("moneda_base = ?"));
        assertTrue("SQL debe incluir precio_usd", sqlCapturado.get().contains("precio_usd = ?"));
        assertTrue("SQL debe incluir precio_ves", sqlCapturado.get().contains("precio_ves = ?"));

        assertEquals("USD", parametros.get(4));
        assertEquals(new BigDecimal("8.50"), parametros.get(5));
        assertEquals(new BigDecimal("340.00"), parametros.get(6));
        assertEquals(1, parametros.get(7)); // clasificado
        assertEquals(0, parametros.get(8)); // incluye IVA false
        assertEquals(15, parametros.get(9)); // id
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
