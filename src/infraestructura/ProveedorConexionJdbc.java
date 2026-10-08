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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Provee conexiones JDBC con pool ligero, timeouts de red y TLS configurable. */
public class ProveedorConexionJdbc {

    private static final ConcurrentHashMap<String, BlockingQueue<Connection>> POOL_DISPONIBLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> CONEXIONES_ACTIVAS = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ProveedorConexionJdbc::limpiarPool, "conexion-pool-shutdown"));
    }

    public Connection getConnection() throws SQLException {
        Properties localConfig = cargarConfiguracionLocal();

        String database = setting("MYSQL_DATABASE", localConfig);
        if (database == null || database.trim().isEmpty()) {
            database = setting("DB_NAME", localConfig);
        }
        if (database == null || database.trim().isEmpty()) {
            database = setting("DB_DATABASE", localConfig);
        }
        String user = setting("DB_USER", localConfig);
        if (user == null || user.trim().isEmpty()) {
            user = setting("MYSQL_USER", localConfig);
        }
        String password = setting("DB_PASSWORD", localConfig);
        if (password == null) {
            password = setting("MYSQL_PASSWORD", localConfig);
        }
        if (password == null) {
            password = setting("DB_PASS", localConfig);
        }
        String host = setting("MYSQL_HOST", localConfig);
        if (host == null || host.trim().isEmpty()) {
            host = setting("DB_HOST", localConfig);
        }
        if (host == null || host.trim().isEmpty()) {
            host = "127.0.0.1";
        }
        String port = setting("MYSQL_PORT", localConfig);
        if (port == null || port.trim().isEmpty()) {
            port = "3306";
        }
        String url = setting("DB_URL", localConfig);

        if (database == null || database.trim().isEmpty()
                || user == null || user.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new SQLException(
                    "Falta configurar MYSQL_DATABASE (o DB_NAME), MYSQL_USER (o DB_USER) y MYSQL_PASSWORD (o DB_PASSWORD/DB_PASS) en .env o en el entorno");
        }

        String ssl = setting("DB_SSL", localConfig);
        if (ssl == null || ssl.trim().isEmpty()) {
            ssl = setting("MYSQL_SSL", localConfig);
        }
        if (ssl == null || ssl.trim().isEmpty()) {
            ssl = "false";
        }

        String connectTimeout = setting("DB_CONNECT_TIMEOUT_MS", localConfig);
        if (connectTimeout == null || connectTimeout.trim().isEmpty()) {
            connectTimeout = "5000";
        }
        String socketTimeout = setting("DB_SOCKET_TIMEOUT_MS", localConfig);
        if (socketTimeout == null || socketTimeout.trim().isEmpty()) {
            socketTimeout = "30000";
        }

        if (url == null || url.trim().isEmpty()) {
            url = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl.trim()
                    + "&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC"
                    + "&connectTimeout=" + connectTimeout.trim()
                    + "&socketTimeout=" + socketTimeout.trim();
        }

        boolean poolHabilitado = !"false".equalsIgnoreCase(setting("DB_POOL_ENABLED", localConfig));
        if (!poolHabilitado) {
            return crearConexionFisica(url, user, password, localConfig);
        }

        return obtenerConexionPool(url, user, password, localConfig);
    }

    private Connection obtenerConexionPool(String url, String user, String password, Properties localConfig) throws SQLException {
        String clave = user + "@" + url;
        BlockingQueue<Connection> pool = POOL_DISPONIBLES.computeIfAbsent(clave, k -> new LinkedBlockingQueue<>());
        AtomicInteger activas = CONEXIONES_ACTIVAS.computeIfAbsent(clave, k -> new AtomicInteger(0));

        int maxPool = parseEntero(setting("DB_POOL_MAX_SIZE", localConfig), 10);
        int timeoutVal = parseEntero(setting("DB_POOL_VALIDATION_TIMEOUT_S", localConfig), 1);
        long connectTimeoutMs = parseLong(setting("DB_CONNECT_TIMEOUT_MS", localConfig), 5000L);

        while (true) {
            Connection existente = pool.poll();
            if (existente != null) {
                if (esConexionValida(existente, timeoutVal)) {
                    return envolverConexion(existente, pool, activas);
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
                            return envolverConexion(nueva, pool, activas);
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
                            return envolverConexion(espera, pool, activas);
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

    private boolean esConexionValida(Connection conn, int timeoutSeconds) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(Math.max(1, timeoutSeconds));
        } catch (Exception e) {
            return false;
        }
    }

    private Connection envolverConexion(Connection fisica, BlockingQueue<Connection> pool, AtomicInteger activas) {
        InvocationHandler handler = new InvocationHandler() {
            private volatile boolean cerrado = false;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                if ("close".equals(methodName)) {
                    if (!cerrado) {
                        cerrado = true;
                        devolverAlPool(fisica, pool, activas);
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
                    return method.invoke(fisica, args);
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

    private void devolverAlPool(Connection fisica, BlockingQueue<Connection> pool, AtomicInteger activas) {
        try {
            if (fisica.isClosed()) {
                activas.decrementAndGet();
                return;
            }
            if (!fisica.getAutoCommit()) {
                fisica.rollback();
                fisica.setAutoCommit(true);
            }
            fisica.clearWarnings();
            pool.offer(fisica);
        } catch (SQLException error) {
            cerrarSilenciosamente(fisica);
            activas.decrementAndGet();
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
        if (value == null || value.trim().isEmpty()) {
            value = localConfig.getProperty(name);
        }
        return value;
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
}
