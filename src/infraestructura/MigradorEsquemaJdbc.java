package infraestructura;

import Modelo.DataAccessException;
import Servicio.ServicioRespaldoBaseDatos;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.HashSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifica y sincroniza automáticamente las tablas, columnas e índices necesarios
 * en la base de datos MySQL sin pérdida de datos.
 * Esto asegura que el Dashboard y los módulos de Clientes, Divisas e IVA funcionen
 * correctamente incluso si el usuario o socio no ejecutó manualmente actualizar_bd.bat.
 */
public final class MigradorEsquemaJdbc {

    private static final Logger LOGGER = Logger.getLogger(MigradorEsquemaJdbc.class.getName());
    private static final int VERSION_ESQUEMA = 3;
    private static final String DEFINICION_MIGRACION_V1 = "v1:clients,pedidos,config,platos,indices,config-kv,auditoria,cierres,generated-pending,unique-email,config-ruc30-phone30-userpass255";
    private static final String CHECKSUM_MIGRACION_V1 = calcularChecksum(DEFINICION_MIGRACION_V1);
    private static final String DEFINICION_MIGRACION_V2 = "v2:mesoneros,pedidos_mesonero";
    private static final String CHECKSUM_MIGRACION_V2 = calcularChecksum(DEFINICION_MIGRACION_V2);
    private static final String DEFINICION_MIGRACION = "v3:platos_aplica_iva,salas_tipo";
    private static final String CHECKSUM_MIGRACION = calcularChecksum(DEFINICION_MIGRACION);
    private static final String LOCK_MIGRACION = "restaurante_schema_migration";

    @FunctionalInterface
    public interface RespaldoPrevio {
        Path crear(ProveedorConexionJdbc proveedor) throws Exception;
    }

    private MigradorEsquemaJdbc() {
    }

    public static synchronized void migrarSiEsNecesario(ProveedorConexionJdbc proveedor) {
        migrarSiEsNecesario(proveedor, proveedorActual ->
                new ServicioRespaldoBaseDatos(proveedorActual).crearRespaldoPreMigracion());
    }

    public static synchronized void migrarSiEsNecesario(ProveedorConexionJdbc proveedor, RespaldoPrevio respaldoPrevio) {
        if (proveedor == null) {
            return;
        }
        if (respaldoPrevio == null) {
            throw new IllegalArgumentException("La operación de respaldo previo es obligatoria.");
        }

        try (Connection con = proveedor.getConnection()) {
            adquirirBloqueo(con);
            try {
                validarEsquemaExistente(con);
                if (!migracionAplicada(con)) {
                    Path respaldo = respaldoPrevio.crear(proveedor);
                    if (respaldo == null) {
                        throw new SQLException("No se confirmó un respaldo previo válido; se cancela la migración.");
                    }
                    LOGGER.info("Respaldo previo a migración confirmado: " + respaldo.toAbsolutePath());
                    registrarMigracionEnCurso(con);
                    asegurarEsquema(con);
                    validarPostcondiciones(con);
                    registrarMigracionAplicada(con);
                } else {
                    validarPostcondiciones(con);
                }
            } finally {
                liberarBloqueo(con);
            }
            LOGGER.info("Esquema de base de datos verificado y sincronizado correctamente.");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "No se pudo verificar o sincronizar el esquema de la BD: " + ex.getMessage(), ex);
            throw new DataAccessException("No se pudo verificar o sincronizar el esquema de la base de datos.", ex);
        }
    }

    private static String calcularChecksum(String definicion) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(definicion.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void adquirirBloqueo(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT GET_LOCK('" + LOCK_MIGRACION + "', 30)")) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new SQLException("No se pudo adquirir el bloqueo exclusivo para migrar la base de datos.");
            }
        }
    }

    private static void liberarBloqueo(Connection con) {
        try (Statement st = con.createStatement()) {
            st.executeQuery("SELECT RELEASE_LOCK('" + LOCK_MIGRACION + "')").close();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo liberar explícitamente el bloqueo SQL de migración.", ex);
        }
    }

    private static boolean tablaExiste(Connection con, String tabla) throws SQLException {
        try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), null, tabla, new String[] {"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                if (tabla.equalsIgnoreCase(rs.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean migracionAplicada(Connection con) throws SQLException {
        if (!tablaExiste(con, "schema_migrations")) return false;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_migrations")) {
            if (rs.next() && rs.getInt(1) > VERSION_ESQUEMA) {
                throw new SQLException("La base tiene una versión de esquema más nueva que esta aplicación.");
            }
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT checksum, estado FROM schema_migrations WHERE version = " + VERSION_ESQUEMA)) {
            if (!rs.next()) return false;
            String checksum = rs.getString("checksum");
            String estado = rs.getString("estado");
            if (!CHECKSUM_MIGRACION.equals(checksum)) {
                throw new SQLException("El checksum de la migración aplicada no coincide. No se modificará el historial; se requiere una nueva migración.");
            }
            return "APLICADA".equalsIgnoreCase(estado);
        }
    }

    private static void validarEsquemaExistente(Connection con) throws SQLException {
        Set<String> tablas = new HashSet<>();
        try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rs.next()) tablas.add(rs.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
        }
        if (tablas.isEmpty()) {
            throw new SQLException("La base de datos no tiene esquema. La aplicación no inicializa una base vacía; use el instalador de base nueva.");
        }
        Set<String> esenciales = Set.of("config", "usuarios", "platos", "salas", "pedidos", "detalle_pedidos");
        Set<String> faltantes = new HashSet<>(esenciales);
        faltantes.removeAll(tablas);
        if (!faltantes.isEmpty()) {
            throw new SQLException("Esquema existente incompleto o no reconocido; faltan tablas esenciales: " + faltantes + ". No se ejecutará auto-reparación.");
        }
    }

    private static void registrarMigracionEnCurso(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INT NOT NULL PRIMARY KEY,
                    nombre VARCHAR(160) NOT NULL,
                    checksum VARCHAR(80) NOT NULL,
                    estado VARCHAR(20) NOT NULL,
                    paso_actual VARCHAR(80) NOT NULL,
                    iniciada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    aplicada_en TIMESTAMP NULL DEFAULT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8
                """);
            st.executeUpdate("INSERT INTO schema_migrations (version,nombre,checksum,estado,paso_actual) VALUES ("
                    + VERSION_ESQUEMA + ",'sincronizacion_esquema_v3','" + CHECKSUM_MIGRACION + "','EN_CURSO','asegurar_esquema') "
                    + "ON DUPLICATE KEY UPDATE checksum=VALUES(checksum), estado='EN_CURSO', paso_actual='asegurar_esquema', iniciada_en=CURRENT_TIMESTAMP");
        }
    }

    private static void registrarMigracionAplicada(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE schema_migrations SET estado='APLICADA', paso_actual='validada', aplicada_en=CURRENT_TIMESTAMP WHERE version=" + VERSION_ESQUEMA);
        }
    }

    private static void validarPostcondiciones(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        String catalogo = con.getCatalog();
        for (String tabla : Set.of("clientes", "configuracion_sistema", "auditoria_pedidos", "cierres_caja", "mesoneros")) {
            if (!tablaExiste(con, tabla)) throw new SQLException("Postcondición no cumplida: falta tabla " + tabla);
        }
        for (String[] par : new String[][] {
                {"pedidos", "total_bs"},
                {"pedidos", "metodo_pago"},
                {"platos", "activo"},
                {"pedidos", "id_mesonero"},
                {"platos", "aplica_iva"},
                {"salas", "tipo"}
        }) {
            if (!columnaExiste(meta, catalogo, par[0], par[1])) {
                throw new SQLException("Postcondición no cumplida: falta columna " + par[0] + "." + par[1]);
            }
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(DISTINCT clave) FROM configuracion_sistema WHERE clave IN "
                     + "('tasa_dolar','iva_porcentaje','impresora_tickets','modo_salida_tickets','imprimir_logo_ticket',"
                     + "'cliente_predeterminado_nombre','cliente_predeterminado_documento','meses_retencion_pedidos')")) {
            if (!rs.next() || rs.getInt(1) != 8) {
                throw new SQLException("Postcondición no cumplida: faltan claves iniciales de configuración.");
            }
        }
    }

    public static void asegurarEsquema(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        String catalogo = con.getCatalog();

        // 1. Asegurar tabla clientes
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clientes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    documento VARCHAR(30) NOT NULL UNIQUE,
                    nombre VARCHAR(150) NOT NULL,
                    telefono VARCHAR(30) NULL,
                    direccion TEXT NULL,
                    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
            st.executeUpdate("INSERT IGNORE INTO clientes (documento, nombre) VALUES ('V-00000000', 'Consumidor Final')");
        }

        // 2. Columnas en pedidos
        asegurarColumna(con, meta, catalogo, "pedidos", "total_bs", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "subtotal_bs", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_bs", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "subtotal", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_porcentaje", "DECIMAL(5,2) NOT NULL DEFAULT 16.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_monto", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "tasa_cambio", "DECIMAL(12,4) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "cliente_nombre", "VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final'");
        asegurarColumna(con, meta, catalogo, "pedidos", "cliente_documento", "VARCHAR(30) NOT NULL DEFAULT 'V-00000000'");
        asegurarColumna(con, meta, catalogo, "pedidos", "metodo_pago", "VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'");
        asegurarColumna(con, meta, catalogo, "pedidos", "efectivo_bs", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "efectivo_usd", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "id_sala_pendiente",
                "INT GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN id_sala ELSE NULL END) STORED");
        asegurarColumna(con, meta, catalogo, "pedidos", "num_mesa_pendiente",
                "INT GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN num_mesa ELSE NULL END) STORED");
        asegurarColumna(con, meta, catalogo, "pedidos", "id_mesonero", "INT NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "mesonero_nombre", "VARCHAR(150) NULL DEFAULT NULL");

        // 3. Columnas en config
        asegurarColumna(con, meta, catalogo, "config", "logo_path", "VARCHAR(255) NULL DEFAULT NULL");
        asegurarLongitudColumna(con, meta, catalogo, "config", "ruc", 30);
        asegurarLongitudColumna(con, meta, catalogo, "config", "telefono", 30);
        asegurarColumna(con, meta, catalogo, "config", "tasa_dolar", "DECIMAL(12,4) NOT NULL DEFAULT 36.5000");
        asegurarColumna(con, meta, catalogo, "config", "iva_porcentaje", "DECIMAL(5,2) NOT NULL DEFAULT 16.00");
        asegurarColumna(con, meta, catalogo, "config", "cliente_predeterminado_nombre", "VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final'");
        asegurarColumna(con, meta, catalogo, "config", "cliente_predeterminado_documento", "VARCHAR(30) NOT NULL DEFAULT 'V-00000000'");
        asegurarColumna(con, meta, catalogo, "config", "meses_retencion_pedidos", "INT NOT NULL DEFAULT 24");
        asegurarColumna(con, meta, catalogo, "config", "imprimir_logo_ticket", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "config", "impresora_tickets", "VARCHAR(150) NOT NULL DEFAULT 'DEFAULT'");
        asegurarColumna(con, meta, catalogo, "config", "modo_salida_tickets", "VARCHAR(50) NOT NULL DEFAULT 'TERMICA_DIRECTA'");
        asegurarLongitudColumna(con, meta, catalogo, "usuarios", "pass", 255);

        // 4. Columnas en platos
        asegurarColumna(con, meta, catalogo, "platos", "activo", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "platos", "desactivado_en", "DATETIME NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "platos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");

        // 5. Columnas en salas (soporte para Salón vs Barra)
        asegurarColumna(con, meta, catalogo, "salas", "tipo", "VARCHAR(20) NOT NULL DEFAULT 'SALON'");

        // 5. Índices de rendimiento
        asegurarIndice(con, meta, catalogo, "pedidos", "idx_pedidos_estado_fecha", "(estado, fecha)");
        asegurarIndice(con, meta, catalogo, "pedidos", "idx_pedidos_cliente_doc", "(cliente_documento)");
        asegurarIndice(con, meta, catalogo, "pedidos", "idx_pedidos_mesonero", "(id_mesonero)");
        asegurarIndice(con, meta, catalogo, "detalle_pedidos", "idx_detalle_pedidos_nombre", "(nombre)");
        asegurarIndiceUnico(con, meta, catalogo, "usuarios", "uq_usuarios_correo", "(correo)");
        asegurarIndiceUnico(con, meta, catalogo, "pedidos", "uq_pedidos_mesa_pendiente", "(id_sala_pendiente, num_mesa_pendiente)");

        // 6. Asegurar tabla clave-valor de configuración del sistema
        asegurarTablaConfiguracionSistema(con, meta, catalogo);

        // 7. Asegurar tabla de auditoría de pedidos (anulaciones y reimpresiones)
        asegurarTablaAuditoriaPedidos(con, meta, catalogo);

        // 8. Asegurar tabla de persistencia de cierres de caja y arqueos
        asegurarTablaCierresCaja(con, meta, catalogo);

        // 9. Asegurar tabla de mesoneros (CRUD, soft-delete, toggle activo)
        asegurarTablaMesoneros(con, meta, catalogo);
    }

    private static void asegurarColumna(Connection con, DatabaseMetaData meta, String catalogo,
            String tabla, String columna, String definicion) throws SQLException {
        if (!columnaExiste(meta, catalogo, tabla, columna)) {
            try (Statement st = con.createStatement()) {
                try {
                    st.executeUpdate("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                } catch (SQLException ex) {
                    if (ex.getErrorCode() != 1060 || !columnaExiste(meta, catalogo, tabla, columna)) {
                        throw ex;
                    }
                }
                LOGGER.info("Columna sincronizada automáticamente: " + tabla + "." + columna);
            }
        }
    }

    private static void asegurarLongitudColumna(Connection con, DatabaseMetaData meta, String catalogo,
            String tabla, String columna, int longitudMinima) throws SQLException {
        int longitud = -1;
        String tipo = null;
        boolean permiteNulos = true;
        try (ResultSet rs = meta.getColumns(catalogo, null, tabla, columna)) {
            if (!rs.next()) throw new SQLException("Falta columna requerida para migrar: " + tabla + "." + columna);
            longitud = rs.getInt("COLUMN_SIZE");
            tipo = rs.getString("TYPE_NAME");
            permiteNulos = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
        }
        if (tipo == null || !(tipo.toUpperCase(java.util.Locale.ROOT).contains("CHAR"))) {
            throw new SQLException("Tipo no reconocido para ampliar " + tabla + "." + columna + ": " + tipo);
        }
        if (longitud >= longitudMinima && !permiteNulos) return;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM `" + tabla + "` WHERE `" + columna + "` IS NULL LIMIT 1")) {
            if (rs.next()) {
                throw new SQLException("No se puede convertir a NOT NULL: hay valores nulos en " + tabla + "." + columna);
            }
        }
        int longitudFinal = Math.max(longitud, longitudMinima);
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE `" + tabla + "` MODIFY COLUMN `" + columna + "` VARCHAR("
                    + longitudFinal + ") COLLATE utf8_spanish_ci NOT NULL");
        }
    }

    private static boolean columnaExiste(DatabaseMetaData meta, String catalogo, String tabla, String columna) throws SQLException {
        try (ResultSet rs = meta.getColumns(catalogo, null, tabla, columna)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getColumns(catalogo, null, tabla, null)) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void asegurarIndice(Connection con, DatabaseMetaData meta, String catalogo,
            String tabla, String nombreIndice, String columnas) throws SQLException {
        if (!indiceExiste(meta, catalogo, tabla, nombreIndice)) {
            try (Statement st = con.createStatement()) {
                try {
                    st.executeUpdate("ALTER TABLE " + tabla + " ADD INDEX " + nombreIndice + " " + columnas);
                } catch (SQLException ex) {
                    if (ex.getErrorCode() != 1061 || !indiceExiste(meta, catalogo, tabla, nombreIndice)) {
                        throw ex;
                    }
                }
                LOGGER.info("Índice sincronizado automáticamente: " + tabla + "." + nombreIndice);
            }
        }
    }

    private static boolean indiceExiste(DatabaseMetaData meta, String catalogo, String tabla, String nombreIndice) throws SQLException {
        try (ResultSet rs = meta.getIndexInfo(catalogo, null, tabla, false, false)) {
            while (rs.next()) {
                if (nombreIndice.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void asegurarIndiceUnico(Connection con, DatabaseMetaData meta, String catalogo,
            String tabla, String nombreIndice, String columnas) throws SQLException {
        if (indiceExiste(meta, catalogo, tabla, nombreIndice)) return;
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE " + tabla + " ADD UNIQUE INDEX " + nombreIndice + " " + columnas);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1061 || !indiceExiste(meta, catalogo, tabla, nombreIndice)) throw ex;
        }
    }

    private static void asegurarTablaConfiguracionSistema(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS configuracion_sistema (
                    clave VARCHAR(80) NOT NULL PRIMARY KEY,
                    valor TEXT NOT NULL,
                    descripcion VARCHAR(255) NULL,
                    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
            LOGGER.info("Tabla configuracion_sistema verificada.");
        }
        migrarParametrosDesdeConfig(con, meta, catalogo);
    }

    private static void migrarParametrosDesdeConfig(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        String sqlSelect = "SELECT * FROM config LIMIT 1";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sqlSelect)) {
            if (rs.next()) {
                String insertSql = "INSERT INTO configuracion_sistema (clave, valor) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE valor=IF(valor=?, VALUES(valor), valor)";
                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "tasa_dolar", "tasa_dolar", "36.5000");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "iva_porcentaje", "iva_porcentaje", "16.00");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "impresora_tickets", "impresora_tickets", "DEFAULT");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "modo_salida_tickets", "modo_salida_tickets", "TERMICA_DIRECTA");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "imprimir_logo_ticket", "imprimir_logo_ticket", "true");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "cliente_predeterminado_nombre", "cliente_predeterminado_nombre", "Consumidor Final");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "cliente_predeterminado_documento", "cliente_predeterminado_documento", "V-00000000");
                    insertarClaveDesdeResultSetSiExiste(meta, catalogo, rs, ps, "meses_retencion_pedidos", "meses_retencion_pedidos", "24");
                }
            } else {
                String insertDefaults = """
                    INSERT IGNORE INTO configuracion_sistema (clave, valor) VALUES
                    ('tasa_dolar', '36.5000'),
                    ('iva_porcentaje', '16.00'),
                    ('impresora_tickets', 'DEFAULT'),
                    ('modo_salida_tickets', 'TERMICA_DIRECTA'),
                    ('imprimir_logo_ticket', 'true'),
                    ('cliente_predeterminado_nombre', 'Consumidor Final'),
                    ('cliente_predeterminado_documento', 'V-00000000'),
                    ('meses_retencion_pedidos', '24')
                    """;
                try (Statement stDef = con.createStatement()) {
                    stDef.executeUpdate(insertDefaults);
                }
            }
        }
    }

    private static void insertarClaveDesdeResultSetSiExiste(DatabaseMetaData meta, String catalogo,
            ResultSet rs, PreparedStatement ps, String columnaBd, String clave, String porDefecto) throws SQLException {
        String valor = columnaExiste(meta, catalogo, "config", columnaBd) ? rs.getString(columnaBd) : null;
        ps.setString(1, clave);
        String valorInicial = valor != null && !valor.isBlank() ? valor : porDefecto;
        ps.setString(2, valorInicial);
        ps.setString(3, porDefecto);
        ps.executeUpdate();
    }

    private static void asegurarTablaAuditoriaPedidos(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS auditoria_pedidos (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_pedido INT NOT NULL,
                    accion VARCHAR(50) NOT NULL,
                    motivo VARCHAR(255) NOT NULL,
                    usuario VARCHAR(100) NOT NULL,
                    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_auditoria_pedidos_pedido (id_pedido),
                    INDEX idx_auditoria_pedidos_fecha (fecha_hora)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
    }

    private static void asegurarTablaCierresCaja(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cierres_caja (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    tipo VARCHAR(20) NOT NULL,
                    fecha_jornada DATE NOT NULL,
                    fecha_hora_emision DATETIME NOT NULL,
                    usuario_emisor VARCHAR(100) NOT NULL,
                    total_ventas_usd DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    total_ventas_bs DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    efectivo_esperado_bs DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    efectivo_declarado_bs DECIMAL(12,2) NULL DEFAULT NULL,
                    diferencia_bs DECIMAL(12,2) NULL DEFAULT NULL,
                    estado_conciliacion_bs VARCHAR(20) NULL DEFAULT NULL,
                    efectivo_esperado_usd DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    efectivo_declarado_usd DECIMAL(12,2) NULL DEFAULT NULL,
                    diferencia_usd DECIMAL(12,2) NULL DEFAULT NULL,
                    estado_conciliacion_usd VARCHAR(20) NULL DEFAULT NULL,
                    tasa_cambio DECIMAL(12,4) NOT NULL DEFAULT 36.5000,
                    ruta_pdf VARCHAR(255) NULL,
                    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_cierres_fecha (fecha_jornada),
                    INDEX idx_cierres_tipo (tipo)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
            LOGGER.info("Tabla cierres_caja verificada.");
        }
    }

    private static void asegurarTablaMesoneros(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mesoneros (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nombre_completo VARCHAR(150) NOT NULL,
                    cedula VARCHAR(30) NOT NULL,
                    telefono VARCHAR(30) NULL,
                    activo TINYINT(1) NOT NULL DEFAULT 1,
                    eliminado TINYINT(1) NOT NULL DEFAULT 0,
                    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_mesoneros_activo (activo),
                    INDEX idx_mesoneros_eliminado (eliminado)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
            LOGGER.info("Tabla mesoneros verificada.");
        }
    }
}
