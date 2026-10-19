package infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProveedorConexionJdbcTest {

    @Test
    public void clasificacionLanSoloAceptaLiteralesPrivadosValidos() {
        assertTrue(ProveedorConexionJdbc.esRedPrivadaOLan("10.0.0.8"));
        assertTrue(ProveedorConexionJdbc.esRedPrivadaOLan("172.16.0.1"));
        assertTrue(ProveedorConexionJdbc.esRedPrivadaOLan("172.31.255.254"));
        assertTrue(ProveedorConexionJdbc.esRedPrivadaOLan("192.168.1.10"));
        assertTrue(ProveedorConexionJdbc.esRedPrivadaOLan("fd12::10"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan("172.32.0.1"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan("192.168.999.1"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan("2001:4860:4860::8888"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan("db.local"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan("db"));
        assertFalse(ProveedorConexionJdbc.esRedPrivadaOLan(null));
    }
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private String archivoAnterior;
    private String urlAnterior;

    @Before
    public void guardarPropiedades() {
        archivoAnterior = System.getProperty("restaurante.env");
        urlAnterior = System.getProperty("DB_URL");
    }

    @After
    public void restaurarPropiedades() {
        restaurar("restaurante.env", archivoAnterior);
        restaurar("DB_URL", urlAnterior);
    }

    @Test
    public void propiedadDelProcesoPrevaleceSobreArchivoLocal() {
        Properties archivo = new Properties();
        archivo.setProperty("DB_URL", "jdbc:mysql://localhost:3306/restaurante");
        System.setProperty("DB_URL", "jdbc:mysql://127.0.0.1:3307/restaurante_test");

        assertEquals("jdbc:mysql://127.0.0.1:3307/restaurante_test",
                new ProveedorConexionJdbc().setting("DB_URL", archivo));
    }

    @Test
    public void errorDeSintaxisLocalPropagaSqlExceptionConCausa() throws IOException {
        Path archivo = temporal.newFile(".env").toPath();
        Files.write(archivo, "INVALIDO=\\u12ZZ\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("restaurante.env", archivo.toString());

        try {
            new ProveedorConexionJdbc().getConnection();
        } catch (SQLException error) {
            assertEquals("No se pudo leer o interpretar el archivo de configuración local", error.getMessage());
            assertNotNull(error.getCause());
            return;
        }
        throw new AssertionError("La configuración local inválida debe abortar la conexión.");
    }

    @Test
    public void devolucionAlPoolRestauraAislamientoYReadOnly() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorUrl = System.getProperty("DB_URL");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        try {
            System.setProperty("DB_POOL_ENABLED", "true");
            System.setProperty("DB_URL", "jdbc:mysql://mock:3306/test_isolation");
            System.setProperty("DB_USER", "test_user_isolation");
            System.setProperty("DB_PASSWORD", "secret");

            java.util.concurrent.atomic.AtomicBoolean readOnly = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicInteger isolation = new java.util.concurrent.atomic.AtomicInteger(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            java.util.concurrent.atomic.AtomicBoolean autoCommit = new java.util.concurrent.atomic.AtomicBoolean(true);
            java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

            java.sql.Connection mockFisica = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("isClosed".equals(name)) return closed.get();
                        if ("close".equals(name)) { closed.set(true); return null; }
                        if ("isValid".equals(name)) return true;
                        if ("isReadOnly".equals(name)) return readOnly.get();
                        if ("setReadOnly".equals(name)) { readOnly.set((Boolean) args[0]); return null; }
                        if ("getTransactionIsolation".equals(name)) return isolation.get();
                        if ("setTransactionIsolation".equals(name)) { isolation.set((Integer) args[0]); return null; }
                        if ("getAutoCommit".equals(name)) return autoCommit.get();
                        if ("setAutoCommit".equals(name)) { autoCommit.set((Boolean) args[0]); return null; }
                        if ("clearWarnings".equals(name)) return null;
                        return null;
                    }
            );

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    return mockFisica;
                }
            };

            java.sql.Connection conn = proveedor.getConnection();
            conn.setReadOnly(true);
            conn.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
            org.junit.Assert.assertTrue(readOnly.get());
            assertEquals(java.sql.Connection.TRANSACTION_SERIALIZABLE, isolation.get());

            conn.close(); // devuelve al pool

            org.junit.Assert.assertFalse("El pool debe restaurar readOnly a false", readOnly.get());
            assertEquals("El pool debe restaurar el nivel de aislamiento default",
                    java.sql.Connection.TRANSACTION_READ_COMMITTED, isolation.get());
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_URL", anteriorUrl);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
        }
    }

    @Test
    public void hostLocalUsaSslFalsePorDefecto() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorSsl = System.getProperty("DB_SSL");
        String anteriorMySsl = System.getProperty("MYSQL_SSL");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "127.0.0.1");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.clearProperty("DB_SSL");
            System.clearProperty("MYSQL_SSL");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            org.junit.Assert.assertTrue("Host local debe usar useSSL=false por defecto",
                    urlCapturada.get().contains("useSSL=false"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL", anteriorSsl);
            restaurar("MYSQL_SSL", anteriorMySsl);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostRemotoExigeSslTruePorDefecto() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorSsl = System.getProperty("DB_SSL");
        String anteriorMySsl = System.getProperty("MYSQL_SSL");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "192.168.1.50");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.clearProperty("DB_SSL");
            System.clearProperty("MYSQL_SSL");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            org.junit.Assert.assertTrue("Host remoto debe exigir useSSL=true por defecto",
                    urlCapturada.get().contains("useSSL=true"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL", anteriorSsl);
            restaurar("MYSQL_SSL", anteriorMySsl);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void hostRemotoRechazaSslFalseSinAutorizacionExplicita() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorSsl = System.getProperty("DB_SSL");
        String anteriorAllow = System.getProperty("DB_ALLOW_INSECURE_REMOTE");
        String anteriorUrl = System.getProperty("DB_URL");

        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "192.168.1.50");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.setProperty("DB_SSL", "false");
            System.clearProperty("DB_ALLOW_INSECURE_REMOTE");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL", anteriorSsl);
            restaurar("DB_ALLOW_INSECURE_REMOTE", anteriorAllow);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostRemotoRespetaSslFalseConAutorizacionExplicita() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorSsl = System.getProperty("DB_SSL");
        String anteriorAllow = System.getProperty("DB_ALLOW_INSECURE_REMOTE");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "192.168.1.50");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.setProperty("DB_SSL", "false");
            System.setProperty("DB_ALLOW_INSECURE_REMOTE", "true");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            org.junit.Assert.assertTrue("Host remoto debe permitir useSSL=false con DB_ALLOW_INSECURE_REMOTE=true",
                    urlCapturada.get().contains("useSSL=false"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL", anteriorSsl);
            restaurar("DB_ALLOW_INSECURE_REMOTE", anteriorAllow);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostRemotoConTruststoreActivaVerifyIdentityYDesactivaPublicKeyRetrieval() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorTrust = System.getProperty("DB_SSL_TRUSTSTORE");
        String anteriorTrustPass = System.getProperty("DB_SSL_TRUSTSTORE_PASSWORD");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "mysql.produccion.com");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.setProperty("DB_SSL_TRUSTSTORE", "/etc/ssl/certs/mysql-truststore.jks");
            System.setProperty("DB_SSL_TRUSTSTORE_PASSWORD", "secreto123");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            String u = urlCapturada.get();
            org.junit.Assert.assertTrue("Debe exigir useSSL=true", u.contains("useSSL=true"));
            org.junit.Assert.assertTrue("Debe elevar a sslMode=VERIFY_IDENTITY", u.contains("sslMode=VERIFY_IDENTITY"));
            org.junit.Assert.assertTrue("Debe desactivar allowPublicKeyRetrieval", u.contains("allowPublicKeyRetrieval=false"));
            org.junit.Assert.assertTrue("Debe incluir URL del truststore", u.contains("trustCertificateKeyStoreUrl=file:/etc/ssl/certs/mysql-truststore.jks"));
            org.junit.Assert.assertTrue("Debe incluir contraseña del truststore", u.contains("trustCertificateKeyStorePassword=secreto123"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL_TRUSTSTORE", anteriorTrust);
            restaurar("DB_SSL_TRUSTSTORE_PASSWORD", anteriorTrustPass);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostRemotoRespetaSslModeConfiguradoExplicito() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorMode = System.getProperty("DB_SSL_MODE");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "mysql.corporativo.net");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.setProperty("DB_SSL_MODE", "VERIFY_CA");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            String u = urlCapturada.get();
            org.junit.Assert.assertTrue("Debe contener sslMode=VERIFY_CA", u.contains("sslMode=VERIFY_CA"));
            org.junit.Assert.assertTrue("allowPublicKeyRetrieval debe ser false para VERIFY_CA", u.contains("allowPublicKeyRetrieval=false"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL_MODE", anteriorMode);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void dbUrlRemotaConUseSslFalseSeCorrigeSiDbSslEsTrue() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorSsl = System.getProperty("DB_SSL");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.setProperty("DB_SSL", "true");
            System.setProperty("DB_URL", "jdbc:mysql://192.168.1.100:3306/restaurante?useSSL=false&serverTimezone=UTC");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            String u = urlCapturada.get();
            org.junit.Assert.assertTrue("DB_SSL=true debe corregir useSSL=false a useSSL=true en host remoto", u.contains("useSSL=true"));
            org.junit.Assert.assertFalse("No debe quedar useSSL=false", u.contains("useSSL=false"));
            org.junit.Assert.assertTrue("Debe inyectar sslMode=REQUIRED", u.contains("sslMode=REQUIRED"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_SSL", anteriorSsl);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostLanPrivadaUsaSslModeRequiredPorDefecto() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "192.168.1.100");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.clearProperty("DB_SSL");
            System.clearProperty("MYSQL_SSL");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            String u = urlCapturada.get();
            org.junit.Assert.assertTrue("LAN privada debe usar sslMode=REQUIRED", u.contains("sslMode=REQUIRED"));
            org.junit.Assert.assertTrue("LAN privada debe permitir public key retrieval", u.contains("allowPublicKeyRetrieval=true"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    @Test
    public void hostWanPublicoExigeSslModeVerifyIdentityPorDefecto() throws Exception {
        String anteriorPool = System.getProperty("DB_POOL_ENABLED");
        String anteriorHost = System.getProperty("DB_HOST");
        String anteriorUser = System.getProperty("DB_USER");
        String anteriorPass = System.getProperty("DB_PASSWORD");
        String anteriorName = System.getProperty("DB_NAME");
        String anteriorUrl = System.getProperty("DB_URL");

        AtomicReference<String> urlCapturada = new AtomicReference<>();
        try {
            System.setProperty("DB_POOL_ENABLED", "false");
            System.setProperty("DB_HOST", "db.externa.com");
            System.setProperty("DB_USER", "usuario");
            System.setProperty("DB_PASSWORD", "clave");
            System.setProperty("DB_NAME", "restaurante");
            System.clearProperty("DB_SSL");
            System.clearProperty("MYSQL_SSL");
            System.clearProperty("DB_URL");

            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
                @Override
                protected java.sql.Connection crearConexionFisica(String url, String user, String password, Properties localConfig) {
                    urlCapturada.set(url);
                    return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{java.sql.Connection.class},
                            (p, m, a) -> null
                    );
                }
            };

            proveedor.getConnection();
            assertNotNull(urlCapturada.get());
            String u = urlCapturada.get();
            org.junit.Assert.assertTrue("WAN pública debe exigir sslMode=VERIFY_IDENTITY", u.contains("sslMode=VERIFY_IDENTITY"));
            org.junit.Assert.assertTrue("WAN pública debe prohibir allowPublicKeyRetrieval", u.contains("allowPublicKeyRetrieval=false"));
        } finally {
            restaurar("DB_POOL_ENABLED", anteriorPool);
            restaurar("DB_HOST", anteriorHost);
            restaurar("DB_USER", anteriorUser);
            restaurar("DB_PASSWORD", anteriorPass);
            restaurar("DB_NAME", anteriorName);
            restaurar("DB_URL", anteriorUrl);
        }
    }

    private static void restaurar(String nombre, String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(nombre);
        } else {
            System.setProperty(nombre, valorAnterior);
        }
    }
}
