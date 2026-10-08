package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import infraestructura.ProveedorConexionJdbc;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas adversariales de Fase 3:
 * - RT-06: Estructura de carpetas mensual para almacenamiento de PDFs y resolución de directorios.
 * - RT-05: Timeouts de red y TLS configurable en ProveedorConexionJdbc.
 * - Pool ligero de conexiones JDBC con proxy, reutilización, reset de auto-commit y descarte por invalidez.
 */
public class AdversarialFase3InfraestructuraYStorageTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private String originalPdfDir;
    private String originalDbSsl;
    private String originalMysqlSsl;
    private String originalConnectTimeout;
    private String originalSocketTimeout;
    private String originalPoolSize;
    private String originalPoolEnabled;

    @Before
    public void setUp() {
        originalPdfDir = System.getProperty("PDF_FACTURAS_DIR");
        originalDbSsl = System.getProperty("DB_SSL");
        originalMysqlSsl = System.getProperty("MYSQL_SSL");
        originalConnectTimeout = System.getProperty("DB_CONNECT_TIMEOUT_MS");
        originalSocketTimeout = System.getProperty("DB_SOCKET_TIMEOUT_MS");
        originalPoolSize = System.getProperty("DB_POOL_MAX_SIZE");
        originalPoolEnabled = System.getProperty("DB_POOL_ENABLED");
        ProveedorConexionJdbc.limpiarPool();
    }

    @After
    public void tearDown() {
        ProveedorConexionJdbc.limpiarPool();
        restaurar("PDF_FACTURAS_DIR", originalPdfDir);
        restaurar("DB_SSL", originalDbSsl);
        restaurar("MYSQL_SSL", originalMysqlSsl);
        restaurar("DB_CONNECT_TIMEOUT_MS", originalConnectTimeout);
        restaurar("DB_SOCKET_TIMEOUT_MS", originalSocketTimeout);
        restaurar("DB_POOL_MAX_SIZE", originalPoolSize);
        restaurar("DB_POOL_ENABLED", originalPoolEnabled);
    }

    private static void restaurar(String clave, String valor) {
        if (valor == null) {
            System.clearProperty(clave);
        } else {
            System.setProperty(clave, valor);
        }
    }

    private static Pedidos pedidoPrueba(int id) {
        Pedidos p = new Pedidos();
        p.setId(id);
        p.setSala("Principal");
        p.setNum_mesa(4);
        p.setTotalDecimal(new BigDecimal("150.00"));
        p.setFecha("2026-10-07");
        p.setUsuario("cajero");
        return p;
    }

    private static Config configPrueba() {
        return new Config(1, "J-12345678-0", "Restaurante RedTeam", "0212-0000000", "Caracas, Venezuela", "Gracias por su visita");
    }

    private static DetallePedido detallePrueba() {
        DetallePedido d = new DetallePedido();
        d.setId(1);
        d.setNombre("Hamburguesa Especial");
        d.setCantidad(2);
        d.setPrecioDecimal(new BigDecimal("75.00"));
        return d;
    }

    // =========================================================================
    // RT-06: Directorio Estructurado de Facturas
    // =========================================================================

    @Test
    public void resolucionDirectorioFacturasPorDefectoRespetaPropiedadYFallback() {
        System.clearProperty("PDF_FACTURAS_DIR");
        Path defecto = GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto();
        assertTrue("Debe terminar en Restaurante/facturas",
                defecto.endsWith(Path.of("Restaurante", "facturas")));

        Path customDir = tempFolder.getRoot().toPath().resolve("custom_facturas");
        System.setProperty("PDF_FACTURAS_DIR", customDir.toString());
        Path resuelto = GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto();
        assertEquals(customDir, resuelto);
    }

    @Test
    public void generadorPdfConEstructuraMensualCreaSubcarpetaPeriodoYArchivo() throws IOException {
        Path baseDir = tempFolder.newFolder("almacenamiento").toPath();
        GeneradorPdfPedido generador = GeneradorPdfPedido.conEstructuraMensual(baseDir);

        assertTrue(generador.isEstructurarPorPeriodo());
        assertEquals(baseDir, generador.getDirectorioSalida());

        String periodoActual = YearMonth.now().toString();
        Path esperado = baseDir.resolve(periodoActual).resolve("pedido-101.pdf");

        Path generado = generador.generar(pedidoPrueba(101), configPrueba(), Collections.singletonList(detallePrueba()));
        assertEquals(esperado, generado);
        assertTrue("El archivo debe existir en la subcarpeta del mes", Files.exists(generado));
        assertTrue(Files.size(generado) > 0);
    }

    @Test
    public void generadorPdfSinEstructuracionMantieneCompatibilidadPlana() throws IOException {
        Path planoDir = tempFolder.newFolder("plano").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(planoDir);

        assertFalse(generador.isEstructurarPorPeriodo());
        Path esperado = planoDir.resolve("pedido-202.pdf");

        Path generado = generador.generar(pedidoPrueba(202), configPrueba(), Collections.singletonList(detallePrueba()));
        assertEquals(esperado, generado);
        assertTrue(Files.exists(generado));
    }

    @Test
    public void generadorConEstructuraMensualManejaColisionConRespaldoEnMismaSubcarpeta() throws IOException {
        Path baseDir = tempFolder.newFolder("colisiones").toPath();
        GeneradorPdfPedido generador = GeneradorPdfPedido.conEstructuraMensual(baseDir);

        Path gen1 = generador.generar(pedidoPrueba(303), configPrueba(), Collections.singletonList(detallePrueba()));
        assertTrue(Files.exists(gen1));

        Path gen2 = generador.generar(pedidoPrueba(303), configPrueba(), Collections.singletonList(detallePrueba()));
        assertEquals(gen1, gen2);

        String periodo = YearMonth.now().toString();
        Path carpetaPeriodo = baseDir.resolve(periodo);
        long respaldos = Files.list(carpetaPeriodo)
                .filter(p -> p.getFileName().toString().startsWith("pedido-303_"))
                .count();
        assertTrue("Debe existir al menos un archivo de respaldo en la subcarpeta mensual", respaldos >= 1);
    }

    // =========================================================================
    // RT-05: Seguridad de Red, Timeouts y Configuración TLS/SSL
    // =========================================================================

    @Test
    public void proveedorConexionAplicaSslYTimeoutsPorDefectoYConfigurables() {
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        Properties cfg = new Properties();

        // Valores por defecto
        assertEquals("false", proveedor.setting("DB_SSL", cfg) != null ? proveedor.setting("DB_SSL", cfg) : "false");

        // Configuración explícita DB_SSL
        System.setProperty("DB_SSL", "true");
        assertEquals("true", proveedor.setting("DB_SSL", cfg));

        System.clearProperty("DB_SSL");
        System.setProperty("MYSQL_SSL", "required");
        assertEquals("required", proveedor.setting("MYSQL_SSL", cfg));

        // Timeouts
        System.setProperty("DB_CONNECT_TIMEOUT_MS", "4000");
        System.setProperty("DB_SOCKET_TIMEOUT_MS", "12000");
        assertEquals("4000", proveedor.setting("DB_CONNECT_TIMEOUT_MS", cfg));
        assertEquals("12000", proveedor.setting("DB_SOCKET_TIMEOUT_MS", cfg));
    }

    // =========================================================================
    // Pool Ligero de Conexiones JDBC: Reutilización, Validación y Reset
    // =========================================================================

    @Test
    public void poolReutilizaConexionCerradaSinCrearNuevaFisica() throws SQLException {
        AtomicInteger creacionesFisicas = new AtomicInteger(0);
        Connection fisicaMock = crearMockConnection(true);

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            protected Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                creacionesFisicas.incrementAndGet();
                return fisicaMock;
            }
        };

        System.setProperty("MYSQL_DATABASE", "db_pool_test");
        System.setProperty("MYSQL_USER", "user_pool");
        System.setProperty("MYSQL_PASSWORD", "secret");

        Connection proxy1 = proveedor.getConnection();
        assertEquals(1, creacionesFisicas.get());
        assertFalse(proxy1.isClosed());

        // Cerrar proxy1: debe devolver la conexión al pool
        proxy1.close();
        assertTrue(proxy1.isClosed());
        assertThrows(SQLException.class, proxy1::getAutoCommit);

        // Obtener conexión nuevamente: debe reutilizar la misma del pool
        Connection proxy2 = proveedor.getConnection();
        assertEquals("No debió crear una nueva conexión física", 1, creacionesFisicas.get());
        assertFalse(proxy2.isClosed());

        proxy2.close();
        assertTrue(proxy2.isClosed());
    }

    @Test
    public void poolRestauraAutoCommitSiTransaccionQuedoAbierta() throws SQLException {
        AtomicBoolean autoCommitEstado = new AtomicBoolean(false);
        AtomicBoolean rollbackLlamado = new AtomicBoolean(false);

        Connection fisicaMock = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isClosed".equals(name)) return false;
                    if ("isValid".equals(name)) return true;
                    if ("getAutoCommit".equals(name)) return autoCommitEstado.get();
                    if ("setAutoCommit".equals(name)) {
                        autoCommitEstado.set((Boolean) args[0]);
                        return null;
                    }
                    if ("rollback".equals(name)) {
                        rollbackLlamado.set(true);
                        return null;
                    }
                    if ("clearWarnings".equals(name)) return null;
                    if ("close".equals(name)) return null;
                    return null;
                });

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            protected Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                return fisicaMock;
            }
        };

        System.setProperty("MYSQL_DATABASE", "db_pool_test_tx");
        System.setProperty("MYSQL_USER", "user_tx");
        System.setProperty("MYSQL_PASSWORD", "secret");

        Connection conn = proveedor.getConnection();
        conn.setAutoCommit(false);
        assertFalse(conn.getAutoCommit());

        // Al cerrar, debe hacer rollback y resetear autoCommit a true
        conn.close();
        assertTrue("Debe invocar rollback al devolver conexión con transacción abierta", rollbackLlamado.get());
        assertTrue("Debe restaurar autoCommit a true", autoCommitEstado.get());
    }

    @Test
    public void poolDescartaConexionInvalidaYCreaNueva() throws SQLException {
        AtomicInteger creacionesFisicas = new AtomicInteger(0);
        AtomicBoolean primeraEsValida = new AtomicBoolean(true);

        Connection fisica1 = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isClosed".equals(name)) return false;
                    if ("isValid".equals(name)) return primeraEsValida.get();
                    if ("getAutoCommit".equals(name)) return true;
                    if ("clearWarnings".equals(name)) return null;
                    if ("close".equals(name)) return null;
                    return null;
                });

        Connection fisica2 = crearMockConnection(true);

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            protected Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                int count = creacionesFisicas.incrementAndGet();
                return count == 1 ? fisica1 : fisica2;
            }
        };

        System.setProperty("MYSQL_DATABASE", "db_pool_invalida");
        System.setProperty("MYSQL_USER", "user_inv");
        System.setProperty("MYSQL_PASSWORD", "secret");

        Connection conn1 = proveedor.getConnection();
        assertEquals(1, creacionesFisicas.get());
        conn1.close(); // Devuelta al pool

        // Ahora simulamos que la conexión física se corrompió/desconectó
        primeraEsValida.set(false);

        // La siguiente petición debe detectar isValid=false, descartarla y crear una nueva
        Connection conn2 = proveedor.getConnection();
        assertEquals("Debe descartar la rota y crear la segunda conexión física", 2, creacionesFisicas.get());
        conn2.close();
    }

    @Test
    public void poolAgotadoConcurrenteLanzaSqlExceptionPorTimeout() {
        Connection fisicaMock = crearMockConnection(true);

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            protected Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                return fisicaMock;
            }
        };

        System.setProperty("MYSQL_DATABASE", "db_pool_exhaust");
        System.setProperty("MYSQL_USER", "user_ex");
        System.setProperty("MYSQL_PASSWORD", "secret");
        System.setProperty("DB_POOL_MAX_SIZE", "1");
        System.setProperty("DB_CONNECT_TIMEOUT_MS", "150"); // 150ms timeout

        try {
            Connection c1 = proveedor.getConnection();
            assertNotNull(c1);

            // Segunda solicitud debe exceder maxPool=1 y fallar por timeout
            SQLException err = assertThrows(SQLException.class, proveedor::getConnection);
            assertTrue("Debe informar que se agotó el tiempo de espera del pool",
                    err.getMessage().contains("Se agotó el tiempo de espera"));

            c1.close();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static Connection crearMockConnection(boolean valido) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isClosed".equals(name)) return false;
                    if ("isValid".equals(name)) return valido;
                    if ("getAutoCommit".equals(name)) return true;
                    if ("setAutoCommit".equals(name)) return null;
                    if ("clearWarnings".equals(name)) return null;
                    if ("rollback".equals(name)) return null;
                    if ("close".equals(name)) return null;
                    if ("unwrap".equals(name)) return proxy;
                    return null;
                });
    }
}
