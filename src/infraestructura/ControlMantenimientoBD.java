package infraestructura;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

/** Barrera SQL compartida para evitar escrituras concurrentes con mantenimiento de esquema/BD. */
public final class ControlMantenimientoBD {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ControlMantenimientoBD.class.getName());
    private static final ThreadLocal<Integer> PROFUNDIDAD_BYPASS = ThreadLocal.withInitial(() -> 0);
    private static volatile boolean activa;

    private ControlMantenimientoBD() {}

    public static AutoCloseable permitirOperacionInterna() {
        int anterior = entrarBypass();
        return () -> restaurarBypass(anterior);
    }

    private static int entrarBypass() {
        int anterior = PROFUNDIDAD_BYPASS.get();
        PROFUNDIDAD_BYPASS.set(anterior + 1);
        return anterior;
    }

    private static void restaurarBypass(int anterior) {
        if (anterior <= 0) PROFUNDIDAD_BYPASS.remove();
        else PROFUNDIDAD_BYPASS.set(anterior);
    }

    static boolean bypassActivo() {
        return PROFUNDIDAD_BYPASS.get() > 0;
    }

    public static void activar() {
        // Habilita la barrera de comprobación para este proceso; no cambia el
        // estado persistido de restaurante_control (que puede seguir OPERATIVO).
        activa = true;
    }

    static boolean estaActiva() {
        return activa;
    }

    static Connection proteger(Connection conexion) {
        InvocationHandler handler = new ConexionHandler(conexion);
        return (Connection) Proxy.newProxyInstance(ControlMantenimientoBD.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    public static void asegurarTablaControl(Connection conexion) throws SQLException {
        int bypass = entrarBypass();
        try {
            try (Statement st = conexion.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS restaurante_control (
                        id TINYINT NOT NULL PRIMARY KEY,
                        estado VARCHAR(20) NOT NULL,
                        operacion_id VARCHAR(100) NULL,
                        actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,
                        CONSTRAINT chk_restaurante_control_estado
                            CHECK (estado IN ('OPERATIVO', 'MANTENIMIENTO'))
                    ) ENGINE=InnoDB
                    """);
                st.executeUpdate("INSERT IGNORE INTO restaurante_control (id, estado) VALUES (1, 'OPERATIVO')");
            }
            long largoActual = 0;
            try (PreparedStatement ps = conexion.prepareStatement("""
                    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'restaurante_control' AND column_name = 'operacion_id'
                    """); var rs = ps.executeQuery()) {
                if (rs.next()) largoActual = rs.getLong(1);
            }
            if (largoActual > 0 && largoActual < 100) {
                try (Statement st = conexion.createStatement()) {
                    st.executeUpdate("ALTER TABLE restaurante_control MODIFY operacion_id VARCHAR(100) NULL");
                }
            }
        } finally {
            restaurarBypass(bypass);
        }
        activar();
    }

    public static String estado(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT estado FROM restaurante_control WHERE id=1");
             var rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No existe la fila de control de mantenimiento.");
            return rs.getString(1);
        }
    }

    public static String operacionActual(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT operacion_id FROM restaurante_control WHERE id=1");
             var rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("No existe la fila de control de mantenimiento.");
            return rs.getString(1);
        }
    }

    public static String iniciarMantenimiento(Connection conexion, String tipo) throws SQLException {
        return iniciarMantenimiento(conexion, tipo, UUID.randomUUID().toString());
    }

    public static String iniciarMantenimiento(Connection conexion, String tipo, String id) throws SQLException {
        int bypass = entrarBypass();
        String operacionId = tipo + ":" + id;
        boolean autoCommit = conexion.getAutoCommit();
        if (autoCommit) conexion.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "SELECT estado, operacion_id FROM restaurante_control WHERE id=1 FOR UPDATE");
                 var rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No existe la fila de control de mantenimiento.");
                if (!"OPERATIVO".equalsIgnoreCase(rs.getString("estado"))) {
                    throw new SQLException("La BD ya está en mantenimiento (" + rs.getString("operacion_id") + ").");
                }
            }
            try (PreparedStatement ps = conexion.prepareStatement(
                    "UPDATE restaurante_control SET estado='MANTENIMIENTO', operacion_id=? WHERE id=1")) {
                ps.setString(1, operacionId);
                ps.executeUpdate();
            }
            conexion.commit();
            return operacionId;
        } catch (SQLException ex) {
            try { conexion.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally {
            if (autoCommit) conexion.setAutoCommit(true);
            restaurarBypass(bypass);
        }
    }

    public static void terminarMantenimiento(Connection conexion, String operacionId) throws SQLException {
        int bypass = entrarBypass();
        boolean autoCommit = conexion.getAutoCommit();
        if (autoCommit) conexion.setAutoCommit(false);
        try (PreparedStatement ps = conexion.prepareStatement(
                "UPDATE restaurante_control SET estado='OPERATIVO', operacion_id=NULL WHERE id=1 AND operacion_id=?")) {
            ps.setString(1, operacionId);
            int actualizadas = ps.executeUpdate();
            if (actualizadas != 1) throw new SQLException("El ID de mantenimiento no coincide; se conserva el bloqueo.");
            conexion.commit();
        } catch (SQLException ex) {
            try { conexion.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally {
            if (autoCommit) conexion.setAutoCommit(true);
            restaurarBypass(bypass);
        }
    }

    /**
     * Mantiene la transacción bajo bloqueo compartido para que el mantenimiento
     * espere a que termine. Los bloqueos compartidos se permiten entre sí; la
     * barrera no serializa por sí sola las escrituras normales.
     */
    public static void bloquearSnapshot(Connection conexion) throws SQLException {
        if (!activa || bypassActivo()) return;
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT estado FROM restaurante_control WHERE id=1 FOR SHARE");
             var rs = ps.executeQuery()) {
            if (!rs.next() || !"OPERATIVO".equalsIgnoreCase(rs.getString(1))) {
                throw new SQLException("La base de datos está en mantenimiento; no se iniciará el respaldo.");
            }
        }
    }

    static boolean esEscritura(String sql) {
        if (sql == null) return false;
        String limpio = sql.stripLeading();
        if (limpio.startsWith("/*!") || limpio.indexOf(';') >= 0) return true;
        while (limpio.startsWith("--") || limpio.startsWith("/*")) {
            int fin = limpio.startsWith("--") ? limpio.indexOf('\n') : limpio.indexOf("*/");
            if (fin < 0) return false;
            limpio = limpio.substring(fin + (limpio.startsWith("--") ? 1 : 2)).stripLeading();
        }
        String primera = limpio.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return switch (primera) {
            case "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "TRUNCATE", "ALTER",
                 "CREATE", "DROP", "RENAME", "GRANT", "REVOKE", "LOAD", "CALL", "DO", "WITH" -> true;
            default -> false;
        };
    }

    private static final class ConexionHandler implements InvocationHandler {
        private final Connection destino;
        private ConexionHandler(Connection destino) { this.destino = destino; }

        @Override public Object invoke(Object proxy, Method metodo, Object[] args) throws Throwable {
            if ("unwrap".equals(metodo.getName()) && args != null && args.length == 1) {
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("No se permite acceder a la conexión física fuera de la barrera de mantenimiento.");
            }
            if ("isWrapperFor".equals(metodo.getName()) && args != null && args.length == 1) {
                return ((Class<?>) args[0]).isInstance(proxy);
            }
            Object resultado = invocar(destino, metodo, args);
            if (resultado instanceof Statement st && (
                    metodo.getName().equals("createStatement")
                    || metodo.getName().equals("prepareStatement")
                    || metodo.getName().equals("prepareCall"))) {
                String sql = metodo.getName().equals("createStatement") ? null
                        : (args != null && args.length > 0 && args[0] instanceof String s ? s : null);
                return envolverStatement(st, destino, (Connection) proxy, sql);
            }
            if (resultado instanceof java.sql.DatabaseMetaData meta && metodo.getName().equals("getMetaData")) {
                return envolverMetadata(meta, (Connection) proxy);
            }
            return resultado;
        }
    }

    private static java.sql.DatabaseMetaData envolverMetadata(java.sql.DatabaseMetaData meta, Connection conexionProxy) {
        return (java.sql.DatabaseMetaData) Proxy.newProxyInstance(ControlMantenimientoBD.class.getClassLoader(),
                new Class<?>[]{java.sql.DatabaseMetaData.class}, (proxy, metodo, args) -> {
                    if ("getConnection".equals(metodo.getName())) return conexionProxy;
                    if ("unwrap".equals(metodo.getName()) && args != null && args.length == 1) {
                        if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                        throw new SQLException("No se permite desenvolver DatabaseMetaData.");
                    }
                    if ("isWrapperFor".equals(metodo.getName()) && args != null && args.length == 1) {
                        return ((Class<?>) args[0]).isInstance(proxy);
                    }
                    return invocar(meta, metodo, args);
                });
    }

    private static Statement envolverStatement(Statement st, Connection conexion, Connection conexionProxy, String sqlPreparado) {
        final boolean[] loteEscritura = {false};
        InvocationHandler handler = (proxy, metodo, args) -> {
            String nombre = metodo.getName();
            if ("unwrap".equals(nombre) && args != null && args.length == 1) {
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("No se permite desenvolver una sentencia fuera de la barrera de mantenimiento.");
            }
            if ("isWrapperFor".equals(nombre) && args != null && args.length == 1) {
                return ((Class<?>) args[0]).isInstance(proxy);
            }
            final String sqlConsulta = (sqlPreparado != null) ? sqlPreparado
                    : (args != null && args.length > 0 && args[0] instanceof String s && nombre.startsWith("execute")) ? s : null;
            if ("addBatch".equals(nombre) && args != null && args.length > 0 && args[0] instanceof String lote) {
                loteEscritura[0] |= esEscritura(lote);
            }
            boolean escritura = esEscritura(sqlConsulta)
                    || ("executeBatch".equals(nombre) || "executeLargeBatch".equals(nombre)) && loteEscritura[0];
            boolean activaGuardia = escritura && activa && !bypassActivo();
            boolean autoCommitPrevio = false;
            boolean transaccionPropia = false;
            if (activaGuardia) {
                autoCommitPrevio = conexion.getAutoCommit();
                if (autoCommitPrevio) {
                    conexion.setAutoCommit(false);
                    transaccionPropia = true;
                }
                try (PreparedStatement gate = conexion.prepareStatement(
                        "SELECT estado FROM restaurante_control WHERE id=1 FOR SHARE");
                     var rs = gate.executeQuery()) {
                    if (!rs.next() || !"OPERATIVO".equalsIgnoreCase(rs.getString(1))) {
                        if (transaccionPropia) {
                            conexion.rollback();
                            conexion.setAutoCommit(true);
                            transaccionPropia = false;
                        }
                        throw new SQLException("La base de datos está en mantenimiento; la escritura fue bloqueada.");
                    }
                } catch (SQLException ex) {
                    if (transaccionPropia) {
                        try { conexion.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                        try { conexion.setAutoCommit(true); } catch (SQLException reset) { ex.addSuppressed(reset); }
                    }
                    throw ex;
                }
            }
            try {
                Object resultado = invocar(st, metodo, args);
                if (("executeBatch".equals(nombre) || "executeLargeBatch".equals(nombre))
                        || "clearBatch".equals(nombre)) loteEscritura[0] = false;
                if ("getConnection".equals(nombre)) return conexionProxy;
                if (resultado instanceof java.sql.ResultSet rs) return envolverResultSet(rs, (Statement) proxy);
                if (transaccionPropia) conexion.commit();
                return resultado;
            } catch (Throwable ex) {
                if (transaccionPropia) {
                    try { conexion.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                }
                if (ex instanceof SQLException sqlEx && nombre.startsWith("execute")) {
                    final String sqlFijo = sqlConsulta;
                    final Statement stFijo = st;
                    LOGGER.log(java.util.logging.Level.WARNING, () -> String.format(
                            "[SQL_ERROR] Fallo al ejecutar consulta/sentencia SQL (código %d, SQLState '%s'): %s | SQL: %s",
                            sqlEx.getErrorCode(),
                            sqlEx.getSQLState() != null ? sqlEx.getSQLState() : "N/A",
                            sqlEx.getMessage(),
                            sqlFijo != null ? sqlFijo.replaceAll("\\s+", " ").trim() : stFijo.toString()));
                }
                throw ex;
            } finally {
                if (transaccionPropia) conexion.setAutoCommit(true);
            }
        };
        Class<?> api = st instanceof java.sql.CallableStatement ? java.sql.CallableStatement.class
                : st instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(ControlMantenimientoBD.class.getClassLoader(),
                new Class<?>[]{api}, handler);
    }

    private static java.sql.ResultSet envolverResultSet(java.sql.ResultSet rs, Statement statementProxy) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(ControlMantenimientoBD.class.getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class}, (proxy, metodo, args) -> {
                    if ("getStatement".equals(metodo.getName())) return statementProxy;
                    if ("unwrap".equals(metodo.getName()) && args != null && args.length == 1) {
                        if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                        throw new SQLException("No se permite desenvolver ResultSet.");
                    }
                    if ("isWrapperFor".equals(metodo.getName()) && args != null && args.length == 1) {
                        return ((Class<?>) args[0]).isInstance(proxy);
                    }
                    return invocar(rs, metodo, args);
                });
    }

    private static Object invocar(Object destino, Method metodo, Object[] args) throws Throwable {
        try { return metodo.invoke(destino, args); }
        catch (InvocationTargetException ex) { throw ex.getCause() == null ? ex : ex.getCause(); }
    }
}
