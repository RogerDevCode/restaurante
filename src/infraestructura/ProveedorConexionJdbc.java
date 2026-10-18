package infraestructura;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Provee conexiones JDBC con pool ligero, timeouts de red y TLS configurable. */
public class ProveedorConexionJdbc {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ProveedorConexionJdbc.class.getName());
    private static final ConcurrentHashMap<String, BlockingQueue<Connection>> POOL_DISPONIBLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> CONEXIONES_ACTIVAS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> NIVELES_AISLAMIENTO_DEFAULT = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ProveedorConexionJdbc::limpiarPool, "conexion-pool-shutdown"));
    }

    public Connection getConnection() throws SQLException {
        Properties localConfig = cargarConfiguracionLocal();

        String database = settingConAlias("MYSQL_DATABASE", "DB_NAME", localConfig);
        if (database == null || database.trim().isEmpty()) {
            database = setting("DB_DATABASE", localConfig);
        }
        String user = settingConAlias("DB_USER", "MYSQL_USER", localConfig);
        String password = settingConAlias("DB_PASSWORD", "MYSQL_PASSWORD", localConfig);
        if (password == null) {
            password = setting("DB_PASS", localConfig);
        }
        String host = settingConAlias("MYSQL_HOST", "DB_HOST", localConfig);
        if (host == null || host.trim().isEmpty()) {
            host = "127.0.0.1";
        }
        String port = settingConAlias("MYSQL_PORT", "DB_PORT", localConfig);
        if (port == null || port.trim().isEmpty()) {
            port = "3306";
        }
        String url = setting("DB_URL", localConfig);
        if ((database == null || database.trim().isEmpty()) && url != null && !url.trim().isEmpty()) {
            java.util.regex.Matcher mDb = java.util.regex.Pattern.compile("(?i)jdbc:mysql://[^/]+/([^?\\s]+)").matcher(url);
            if (mDb.find()) {
                database = mDb.group(1);
            }
        }

        if (database == null || database.trim().isEmpty()
                || user == null || user.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new SQLException(
                    "Falta configurar MYSQL_DATABASE (o DB_NAME), MYSQL_USER (o DB_USER) y MYSQL_PASSWORD (o DB_PASSWORD/DB_PASS) en .env o en el entorno");
        }

        String hostEfectivo = host;
        if (url != null && !url.trim().isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)jdbc:mysql://([^:/\\?]+)").matcher(url);
            if (m.find()) {
                hostEfectivo = m.group(1);
            }
        }

        boolean esRemoto = !esHostLocal(hostEfectivo);
        String sslConfigurado = settingConAlias("DB_SSL", "MYSQL_SSL", localConfig);
        String sslModeConfigurado = settingConAlias("DB_SSL_MODE", "MYSQL_SSL_MODE", localConfig);
        String truststore = settingConAlias("DB_SSL_TRUSTSTORE", "MYSQL_SSL_TRUSTSTORE", localConfig);
        String truststorePassword = settingConAlias("DB_SSL_TRUSTSTORE_PASSWORD", "MYSQL_SSL_TRUSTSTORE_PASSWORD", localConfig);
        String allowInsecureRemote = settingConAlias("DB_ALLOW_INSECURE_REMOTE", "DB_ALLOW_INSECURE_REMOTE_SSL", localConfig);
        boolean permitirInseguroRemoto = "true".equalsIgnoreCase(allowInsecureRemote);

        String ssl;
        String sslMode;

        if (esRemoto) {
            if (sslConfigurado != null && "false".equalsIgnoreCase(sslConfigurado.trim())) {
                if (!permitirInseguroRemoto) {
                    throw new IllegalStateException(
                        "[SEGURIDAD] Conexión remota a MySQL ('" + hostEfectivo + "') configurada sin cifrado (DB_SSL=false). "
                        + "Para autorizar explícitamente una conexión insegura en redes internas de confianza, "
                        + "configure DB_ALLOW_INSECURE_REMOTE=true en el archivo .env.");
                } else {
                    LOGGER.warning("[SEGURIDAD] Conexión insegura a host remoto autorizada explícitamente (DB_ALLOW_INSECURE_REMOTE=true) para: " + hostEfectivo);
                }
                ssl = "false";
                sslMode = (sslModeConfigurado != null && !sslModeConfigurado.isBlank())
                        ? sslModeConfigurado.trim().toUpperCase(java.util.Locale.ROOT) : "DISABLED";
            } else {
                ssl = "true";
                if (sslModeConfigurado != null && !sslModeConfigurado.isBlank()) {
                    sslMode = sslModeConfigurado.trim().toUpperCase(java.util.Locale.ROOT);
                } else if (truststore != null && !truststore.isBlank()) {
                    sslMode = "VERIFY_IDENTITY";
                } else {
                    sslMode = "REQUIRED";
                    LOGGER.warning("Conexión remota a MySQL (" + hostEfectivo
                            + ") configurada con TLS pero sin verificación de certificado (sslMode=REQUIRED). "
                            + "Para autenticar la identidad del servidor y mitigar ataques Man-in-the-Middle (MITM), "
                            + "configure DB_SSL_MODE=VERIFY_IDENTITY y DB_SSL_TRUSTSTORE.");
                }
            }
        } else {
            // Host local
            if (sslConfigurado != null && "true".equalsIgnoreCase(sslConfigurado.trim())) {
                ssl = "true";
                sslMode = (sslModeConfigurado != null && !sslModeConfigurado.isBlank())
                        ? sslModeConfigurado.trim().toUpperCase(java.util.Locale.ROOT) : "REQUIRED";
            } else {
                ssl = "false";
                sslMode = (sslModeConfigurado != null && !sslModeConfigurado.isBlank())
                        ? sslModeConfigurado.trim().toUpperCase(java.util.Locale.ROOT) : "DISABLED";
            }
        }

        String allowPublicKeyRetrieval = ("VERIFY_IDENTITY".equals(sslMode) || "VERIFY_CA".equals(sslMode))
                ? "false" : "true";

        String connectTimeout = setting("DB_CONNECT_TIMEOUT_MS", localConfig);
        if (connectTimeout == null || connectTimeout.trim().isEmpty()) {
            connectTimeout = "5000";
        }
        String socketTimeout = setting("DB_SOCKET_TIMEOUT_MS", localConfig);
        if (socketTimeout == null || socketTimeout.trim().isEmpty()) {
            socketTimeout = "30000";
        }

        if (url == null || url.trim().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database)
              .append("?useSSL=").append(ssl)
              .append("&sslMode=").append(sslMode)
              .append("&allowPublicKeyRetrieval=").append(allowPublicKeyRetrieval);
            if (truststore != null && !truststore.isBlank()) {
                String rutaTrust = truststore.trim().startsWith("file:") ? truststore.trim() : "file:" + truststore.trim();
                sb.append("&trustCertificateKeyStoreUrl=").append(rutaTrust);
                if (truststorePassword != null && !truststorePassword.isBlank()) {
                    sb.append("&trustCertificateKeyStorePassword=").append(truststorePassword.trim());
                }
            }
            sb.append("&serverTimezone=UTC")
              .append("&connectTimeout=").append(connectTimeout.trim())
              .append("&socketTimeout=").append(socketTimeout.trim());
            url = sb.toString();
        } else {
            // URL explícita (DB_URL)
            if (esRemoto) {
                if (url.matches("(?i).*[?&]useSSL=false.*") || url.matches("(?i).*[?&]sslMode=DISABLED.*")) {
                    if ("true".equalsIgnoreCase(sslConfigurado)) {
                        url = url.replaceAll("(?i)(useSSL=)false", "$1true");
                        url = url.replaceAll("(?i)(sslMode=)DISABLED", "$1" + sslMode);
                        LOGGER.warning("La URL remota configuraba useSSL=false / sslMode=DISABLED, pero DB_SSL=true está activo. Se forzó cifrado TLS.");
                    } else if (!permitirInseguroRemoto) {
                        throw new IllegalStateException(
                            "[SEGURIDAD] La URL remota hacia '" + hostEfectivo + "' contiene useSSL=false o sslMode=DISABLED "
                            + "sin autorización explícita. Configure DB_ALLOW_INSECURE_REMOTE=true en el archivo .env para permitirlo.");
                    } else {
                        LOGGER.warning("[SEGURIDAD] URL remota insegura autorizada explícitamente (DB_ALLOW_INSECURE_REMOTE=true) para: " + hostEfectivo);
                    }
                } else if (!url.matches("(?i).*[?&]useSSL=.*")) {
                    url = url + (url.contains("?") ? "&" : "?") + "useSSL=" + ssl;
                }
                if (!url.matches("(?i).*[?&]sslMode=.*")) {
                    url = url + (url.contains("?") ? "&" : "?") + "sslMode=" + sslMode;
                }
                if (truststore != null && !truststore.isBlank() && !url.contains("trustCertificateKeyStoreUrl")) {
                    String rutaTrust = truststore.trim().startsWith("file:") ? truststore.trim() : "file:" + truststore.trim();
                    url = url + "&trustCertificateKeyStoreUrl=" + rutaTrust;
                    if (truststorePassword != null && !truststorePassword.isBlank() && !url.contains("trustCertificateKeyStorePassword")) {
                        url = url + "&trustCertificateKeyStorePassword=" + truststorePassword.trim();
                    }
                }
            } else {
                // Host local
                if (!url.matches("(?i).*[?&]useSSL=.*")) {
                    url = url + (url.contains("?") ? "&" : "?") + "useSSL=" + ssl;
                }
            }
        }

        boolean poolHabilitado = !"false".equalsIgnoreCase(setting("DB_POOL_ENABLED", localConfig));
        if (!poolHabilitado) {
            return ControlMantenimientoBD.proteger(crearConexionFisica(url, user, password, localConfig));
        }

        return ControlMantenimientoBD.proteger(obtenerConexionPool(url, user, password, localConfig));
    }

    private Connection obtenerConexionPool(String url, String user, String password, Properties localConfig) throws SQLException {
        String clave = user + "@" + url + "#" + huellaCredencial(password);
        BlockingQueue<Connection> pool = POOL_DISPONIBLES.computeIfAbsent(clave, k -> new LinkedBlockingQueue<>());
        AtomicInteger activas = CONEXIONES_ACTIVAS.computeIfAbsent(clave, k -> new AtomicInteger(0));

        int maxPool = parseEntero(setting("DB_POOL_MAX_SIZE", localConfig), 10);
        int timeoutVal = parseEntero(setting("DB_POOL_VALIDATION_TIMEOUT_S", localConfig), 1);
        long connectTimeoutMs = parseLong(setting("DB_CONNECT_TIMEOUT_MS", localConfig), 5000L);

        int nivelAislamientoDefault = NIVELES_AISLAMIENTO_DEFAULT.computeIfAbsent(clave, k -> Connection.TRANSACTION_READ_COMMITTED);

        while (true) {
            Connection existente = pool.poll();
            if (existente != null) {
                if (esConexionValida(existente, timeoutVal)) {
                    return envolverConexion(existente, pool, activas, nivelAislamientoDefault);
                } else {
                    cerrarSilenciosamente(existente);
                    activas.decrementAndGet();
                    continue;
                }
            }

            while (true) {
                int actuales = activas.get();
                if (actuales < maxPool) {
                    if (activas.compareAndSet(actuales, actuales + 1)) {
                        try {
                            Connection nueva = crearConexionFisica(url, user, password, localConfig);
                            try {
                                nivelAislamientoDefault = nueva.getTransactionIsolation();
                                NIVELES_AISLAMIENTO_DEFAULT.put(clave, nivelAislamientoDefault);
                            } catch (Exception ignored) {
                            }
                            return envolverConexion(nueva, pool, activas, nivelAislamientoDefault);
                        } catch (SQLException | RuntimeException e) {
                            activas.decrementAndGet();
                            throw e;
                        }
                    }
                } else {
                    try {
                        Connection espera = pool.poll(connectTimeoutMs, TimeUnit.MILLISECONDS);
                        if (espera == null) {
                            throw new SQLException("Se agotó el tiempo de espera (" + connectTimeoutMs
                                    + " ms) para obtener una conexión del pool (máx: " + maxPool + ")");
                        }
                        if (esConexionValida(espera, timeoutVal)) {
                            return envolverConexion(espera, pool, activas, nivelAislamientoDefault);
                        } else {
                            cerrarSilenciosamente(espera);
                            activas.decrementAndGet();
                            break;
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Espera interrumpida al obtener conexión del pool", ie);
                    }
                }
            }
        }
    }

    private String huellaCredencial(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("La plataforma no dispone de SHA-256 para identificar el pool.", ex);
        }
    }

    private boolean esConexionValida(Connection conn, int timeoutSeconds) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(Math.max(1, timeoutSeconds));
        } catch (Exception e) {
            return false;
        }
    }

    private Connection envolverConexion(Connection fisica, BlockingQueue<Connection> pool, AtomicInteger activas, int nivelAislamientoDefault) {
        InvocationHandler handler = new InvocationHandler() {
            private volatile boolean cerrado = false;
            private final boolean[] transaccionConEscritura = {false};

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                if ("close".equals(methodName)) {
                    if (!cerrado) {
                        cerrado = true;
                        devolverAlPool(fisica, pool, activas, nivelAislamientoDefault,
                                transaccionConEscritura[0]);
                    }
                    return null;
                }
                if ("isClosed".equals(methodName)) {
                    return cerrado || fisica.isClosed();
                }
                if (cerrado) {
                    throw new SQLException("La conexión ya ha sido cerrada.");
                }
                if ("equals".equals(methodName) && args != null && args.length == 1) {
                    return proxy == args[0] || fisica.equals(args[0]);
                }
                if ("hashCode".equals(methodName)) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(methodName)) {
                    return "PooledConnection[" + fisica + ", cerrado=" + cerrado + "]";
                }
                if ("unwrap".equals(methodName) && args != null && args.length == 1) {
                    Class<?> iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        return proxy;
                    }
                    return fisica.unwrap(iface);
                }
                if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
                    Class<?> iface = (Class<?>) args[0];
                    return iface.isInstance(proxy) || fisica.isWrapperFor(iface);
                }
                try {
                    Object resultado = method.invoke(fisica, args);
                    if ("commit".equals(methodName) || "rollback".equals(methodName)
                            || "setAutoCommit".equals(methodName)) {
                        transaccionConEscritura[0] = false;
                    }
                    if (resultado instanceof Statement sentencia
                            && (methodName.startsWith("createStatement")
                            || methodName.startsWith("prepareStatement")
                            || methodName.startsWith("prepareCall"))) {
                        String sqlPreparado = methodName.startsWith("createStatement")
                                ? null : (args != null && args.length > 0 && args[0] instanceof String sql ? sql : null);
                        return envolverSentencia(sentencia, fisica, sqlPreparado, transaccionConEscritura);
                    }
                    return resultado;
                } catch (InvocationTargetException ite) {
                    throw ite.getCause() != null ? ite.getCause() : ite;
                }
            }
        };

        return (Connection) Proxy.newProxyInstance(
                ProveedorConexionJdbc.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                handler
        );
    }

    private Statement envolverSentencia(Statement sentencia, Connection conexion, String sqlPreparado,
            boolean[] transaccionConEscritura) {
        boolean[] loteTieneEscritura = {false};
        InvocationHandler handler = (proxy, method, args) -> {
            String nombre = method.getName();
            boolean ejecucion = nombre.startsWith("execute");
            boolean loteEscritura = "executeBatch".equals(nombre) || "executeLargeBatch".equals(nombre);
            if ("addBatch".equals(nombre)) {
                String sqlLote = sqlPreparado != null ? sqlPreparado
                        : args != null && args.length > 0 && args[0] instanceof String sql ? sql : null;
                loteTieneEscritura[0] |= ControlMantenimientoBD.esEscritura(sqlLote);
            }
            String sqlEjecutado = sqlPreparado != null ? sqlPreparado
                    : args != null && args.length > 0 && args[0] instanceof String sql ? sql : null;
            boolean escritura = loteEscritura && loteTieneEscritura[0]
                    || !loteEscritura && ControlMantenimientoBD.esEscritura(sqlEjecutado)
                    || sentencia instanceof CallableStatement && ejecucion;
            if (ejecucion && escritura && !conexion.getAutoCommit()) {
                transaccionConEscritura[0] = true;
            }
            try {
                Object resultado = method.invoke(sentencia, args);
                if ("clearBatch".equals(nombre) || loteEscritura) loteTieneEscritura[0] = false;
                return resultado;
            } catch (InvocationTargetException ite) {
                throw ite.getCause() != null ? ite.getCause() : ite;
            }
        };
        Class<?> api = sentencia instanceof CallableStatement ? CallableStatement.class
                : sentencia instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(ProveedorConexionJdbc.class.getClassLoader(),
                new Class<?>[]{api}, handler);
    }

    private void devolverAlPool(Connection fisica, BlockingQueue<Connection> pool, AtomicInteger activas,
            int nivelAislamientoDefault, boolean transaccionConEscritura) throws SQLException {
        try {
            if (fisica.isClosed()) {
                activas.decrementAndGet();
                return;
            }
            SQLException transaccionRevertida = null;
            if (!fisica.getAutoCommit()) {
                if (transaccionConEscritura) {
                    LOGGER.severe("La conexión volvió al pool con autocommit=false y escrituras sin confirmar; "
                            + "el pool las revirtió. Revise la salida transaccional que omitió commit().");
                    transaccionRevertida = new SQLException(
                            "La conexión se cerró con escrituras sin confirmar; el pool revirtió la transacción.");
                }
                fisica.rollback();
                fisica.setAutoCommit(true);
            }
            try {
                if (fisica.getTransactionIsolation() != nivelAislamientoDefault) {
                    fisica.setTransactionIsolation(nivelAislamientoDefault);
                }
            } catch (Exception ignored) {
            }
            try {
                if (fisica.isReadOnly()) {
                    fisica.setReadOnly(false);
                }
            } catch (Exception ignored) {
            }
            fisica.clearWarnings();
            if (transaccionRevertida != null) {
                throw transaccionRevertida;
            }
            if (!pool.offer(fisica)) {
                cerrarSilenciosamente(fisica);
                activas.decrementAndGet();
            }
        } catch (SQLException error) {
            cerrarSilenciosamente(fisica);
            activas.decrementAndGet();
            throw error;
        }
    }

    protected Connection crearConexionFisica(String url, String user, String password, Properties localConfig) throws SQLException {
        Properties info = new Properties();
        if (user != null) {
            info.put("user", user);
        }
        if (password != null) {
            info.put("password", password);
        }
        String connectTimeout = setting("DB_CONNECT_TIMEOUT_MS", localConfig);
        if (connectTimeout == null || connectTimeout.trim().isEmpty()) {
            connectTimeout = "5000";
        }
        String socketTimeout = setting("DB_SOCKET_TIMEOUT_MS", localConfig);
        if (socketTimeout == null || socketTimeout.trim().isEmpty()) {
            socketTimeout = "30000";
        }
        if (!info.containsKey("connectTimeout")) {
            info.put("connectTimeout", connectTimeout.trim());
        }
        if (!info.containsKey("socketTimeout")) {
            info.put("socketTimeout", socketTimeout.trim());
        }
        return DriverManager.getConnection(url, info);
    }

    protected Connection crearConexionFisica(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static void limpiarPool() {
        for (BlockingQueue<Connection> pool : POOL_DISPONIBLES.values()) {
            Connection conn;
            while ((conn = pool.poll()) != null) {
                cerrarSilenciosamente(conn);
            }
        }
        POOL_DISPONIBLES.clear();
        CONEXIONES_ACTIVAS.clear();
    }

    public static int getConexionesDisponiblesEnPool(String clave) {
        BlockingQueue<Connection> q = POOL_DISPONIBLES.get(clave);
        return q == null ? 0 : q.size();
    }

    public static int getConexionesActivas(String clave) {
        AtomicInteger ai = CONEXIONES_ACTIVAS.get(clave);
        return ai == null ? 0 : ai.get();
    }

    private static void cerrarSilenciosamente(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private static int parseEntero(String valor, int porDefecto) {
        if (valor == null || valor.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private static long parseLong(String valor, long porDefecto) {
        if (valor == null || valor.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public String setting(String name, Properties localConfig) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(name);
        }
        if ((value == null || value.trim().isEmpty()) && localConfig != null) {
            value = localConfig.getProperty(name);
        }
        return value;
    }

    /** Lee una clave con la misma precedencia de entorno que usa la conexión, incluido .env. */
    public String settingConfigurado(String name) throws SQLException {
        return setting(name, cargarConfiguracionLocal());
    }

    private String settingConAlias(String alias1, String alias2, Properties localConfig) {
        String val = System.getProperty(alias1);
        if (val == null || val.trim().isEmpty()) val = System.getProperty(alias2);
        if (val == null || val.trim().isEmpty()) val = System.getenv(alias1);
        if (val == null || val.trim().isEmpty()) val = System.getenv(alias2);
        if (val == null || val.trim().isEmpty()) val = localConfig.getProperty(alias1);
        if (val == null || val.trim().isEmpty()) val = localConfig.getProperty(alias2);
        return val;
    }

    private Properties cargarConfiguracionLocal() throws SQLException {
        Properties localConfig = new Properties();
        try {
            String configuredEnvFile = System.getProperty("restaurante.env");
            Path envFile = Paths.get(configuredEnvFile == null ? ".env" : configuredEnvFile);
            if (configuredEnvFile == null && !Files.isRegularFile(envFile)) {
                envFile = Paths.get("..", ".env");
            }
            if (Files.isRegularFile(envFile)) {
                try (java.io.Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
                    Properties rawProps = new Properties();
                    rawProps.load(reader);
                    for (String key : rawProps.stringPropertyNames()) {
                        String cleanKey = key.replace("\uFEFF", "").trim();
                        localConfig.setProperty(cleanKey, rawProps.getProperty(key));
                    }
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            throw new SQLException("No se pudo leer o interpretar el archivo de configuración local", error);
        }
        return localConfig;
    }

    public static boolean esHostLocal(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h) || "[::1]".equals(h);
    }
}
