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
import java.util.ArrayList;
import java.util.List;
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
    private static final int VERSION_ESQUEMA = 12;
    private static final String DEFINICION_MIGRACION_V1 = "v1:clients,pedidos,config,platos,indices,config-kv,auditoria,cierres,generated-pending,unique-email,config-ruc30-phone30-userpass255";
    private static final String CHECKSUM_MIGRACION_V1 = calcularChecksum(DEFINICION_MIGRACION_V1);
    private static final String DEFINICION_MIGRACION_V2 = "v2:mesoneros,pedidos_mesonero";
    private static final String CHECKSUM_MIGRACION_V2 = calcularChecksum(DEFINICION_MIGRACION_V2);
    private static final String DEFINICION_MIGRACION_V3 = "v3:platos_aplica_iva,salas_tipo";
    private static final String DEFINICION_MIGRACION_V4 = "v4:categorias,plato_categoria,plato_favorito,platos_nombre_clave,detalle_pedidos_nombre_clave,pedidos_estado_anulado";
    private static final String DEFINICION_MIGRACION_V5 = "v5:preferencias_tabla";
    private static final String CHECKSUM_MIGRACION_V5 = calcularChecksum(DEFINICION_MIGRACION_V5);
    private static final String DEFINICION_MIGRACION_V6 = "v6:pedido_pagos_mixtos";
    private static final String CHECKSUM_MIGRACION_V6 = calcularChecksum(DEFINICION_MIGRACION_V6);
    private static final String DEFINICION_MIGRACION_V7 = "v7:pedidos_rapidos_sin_mesa";
    private static final String CHECKSUM_MIGRACION_V7 = calcularChecksum(DEFINICION_MIGRACION_V7);
    private static final String DEFINICION_MIGRACION_V9 = "v9:login_intentos_por_usuario_con_bloqueo_dao";
    private static final String CHECKSUM_MIGRACION_V9 = calcularChecksum(DEFINICION_MIGRACION_V9);
    private static final String DEFINICION_MIGRACION_V10 = "v10:reserva_alias_admin_repara_fk_login_cascade";
    private static final String CHECKSUM_MIGRACION_V10 = calcularChecksum(DEFINICION_MIGRACION_V10);
    private static final String DEFINICION_MIGRACION_V11 = "v11:precios_duales,snapshot_fiscal,cierre_mesa,liquidaciones,auditoria_precios,pagos_inmutables";
    private static final String CHECKSUM_MIGRACION_V11 = calcularChecksum(DEFINICION_MIGRACION_V11);
    private static final String DEFINICION_MIGRACION_V12 = "v12:detalle_pedidos_aplica_iva_not_null";
    private static final String CHECKSUM_MIGRACION_V12 = calcularChecksum(DEFINICION_MIGRACION_V12);
    private static final String NOMBRE_MIGRACION = "sincronizacion_esquema_v12";
    private static final String LOCK_MIGRACION = "restaurante_schema_migration";

    public static int versionEsquemaActual() { return VERSION_ESQUEMA; }

    @FunctionalInterface
    public interface RespaldoPrevio {
        Path crear(ProveedorConexionJdbc proveedor) throws Exception;
    }

    private static volatile boolean esquemaSincronizado = false;

    public static void invalidarCacheEsquema() {
        esquemaSincronizado = false;
    }

    public static boolean esEsquemaSincronizado() {
        return esquemaSincronizado;
    }

    private MigradorEsquemaJdbc() {
    }

    public static synchronized void migrarSiEsNecesario(ProveedorConexionJdbc proveedor) {
        if (esquemaSincronizado) {
            return;
        }
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

        try (AutoCloseable modoInterno = ControlMantenimientoBD.permitirOperacionInterna();
             Connection con = proveedor.getConnection()) {
            adquirirBloqueo(con);
            try {
                validarEsquemaExistente(con);
                ControlMantenimientoBD.asegurarTablaControl(con);
                String estadoMantenimiento = ControlMantenimientoBD.estado(con);
                String operacionMantenimiento = ControlMantenimientoBD.operacionActual(con);
                if ("MANTENIMIENTO".equalsIgnoreCase(estadoMantenimiento)
                        && (operacionMantenimiento == null || !operacionMantenimiento.startsWith("MIGRACION-"))) {
                    throw new SQLException("La BD tiene una operación de mantenimiento/restauración pendiente: "
                            + operacionMantenimiento + ". No se abrirá la aplicación.");
                }

                boolean pendiente = !migracionAplicada(con);
                String operacionId = operacionMantenimiento;
                if (pendiente) {
                    if (!"MANTENIMIENTO".equalsIgnoreCase(estadoMantenimiento)) {
                        operacionId = ControlMantenimientoBD.iniciarMantenimiento(con, "MIGRACION-v" + VERSION_ESQUEMA);
                    }
                    Path respaldo = respaldoPrevio.crear(proveedor);
                    if (respaldo == null) {
                        throw new SQLException("No se confirmó un respaldo previo válido; se cancela la migración.");
                    }
                    new ServicioRespaldoBaseDatos(proveedor).validarRestaurableEnEsquemaTemporal(respaldo);
                    LOGGER.info("Respaldo previo a migración confirmado: " + respaldo.toAbsolutePath());
                    registrarMigracionEnCurso(con);
                    asegurarEsquema(con);
                    validarPostcondiciones(con);
                    registrarMigracionAplicada(con);
                } else {
                    validarPostcondiciones(con);
                }
                if ("MANTENIMIENTO".equalsIgnoreCase(ControlMantenimientoBD.estado(con))
                        && operacionId != null && operacionId.startsWith("MIGRACION-")) {
                    ControlMantenimientoBD.terminarMantenimiento(con, operacionId);
                }
            } finally {
                liberarBloqueo(con);
            }
            LOGGER.info("Esquema de base de datos verificado y sincronizado correctamente.");
            esquemaSincronizado = true;
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
            if (!CHECKSUM_MIGRACION_V12.equals(checksum)) {
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
                    + VERSION_ESQUEMA + ",'" + NOMBRE_MIGRACION + "','" + CHECKSUM_MIGRACION_V12 + "','EN_CURSO','asegurar_esquema') "
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
        for (String tabla : Set.of("clientes", "configuracion_sistema", "auditoria_pedidos", "cierres_caja", "mesoneros",
                "categorias", "plato_categoria", "plato_favorito", "preferencias_tabla", "pedido_pagos", "login_intentos",
                "auditoria_cambios_precio", "pedido_liquidaciones", "pedido_saldos_movimientos")) {
            if (!tablaExiste(con, tabla)) throw new SQLException("Postcondición no cumplida: falta tabla " + tabla);
        }
        for (String[] par : new String[][] {
                {"pedidos", "total_bs"},
                {"pedidos", "metodo_pago"},
                {"pedidos", "tipo_pedido"},
                {"pedidos", "id_sala"},
                {"pedidos", "num_mesa"},
                {"platos", "activo"},
                {"pedidos", "id_mesonero"},
                {"platos", "aplica_iva"},
                {"salas", "tipo"},
                {"platos", "nombre_clave"},
                {"detalle_pedidos", "nombre_clave"},
                {"detalle_pedidos", "aplica_iva"},
                {"platos", "moneda_base"}, {"platos", "precio_usd"}, {"platos", "precio_ves"},
                {"platos", "precio_clasificado"}, {"platos", "precio_incluye_iva"},
                {"detalle_pedidos", "id_plato"}, {"detalle_pedidos", "moneda_base"},
                {"detalle_pedidos", "precio_incluye_iva"}, {"detalle_pedidos", "iva_porcentaje"},
                {"detalle_pedidos", "precio_congelado"}, {"detalle_pedidos", "congelado_motivo"},
                {"detalle_pedidos", "congelado_por"}, {"detalle_pedidos", "congelado_en"},
                {"pedidos", "cerrada_en"}, {"pedidos", "version_precio"},
                {"pedido_pagos", "monto_recibido"}, {"pedido_pagos", "monto_aplicado"},
                {"pedido_pagos", "cambio_devuelto"}, {"pedido_pagos", "es_legado"},
                {"usuarios", "nombre_unico"},
                {"login_intentos", "usuario_id"},
                {"login_intentos", "intentos"},
                {"login_intentos", "bloqueado_hasta_ms"}
        }) {
            if (!columnaExiste(meta, catalogo, par[0], par[1])) {
                throw new SQLException("Postcondición no cumplida: falta columna " + par[0] + "." + par[1]);
            }
        }
        if (!indiceExiste(meta, catalogo, "usuarios", "uq_usuarios_nombre")) {
            throw new SQLException("Postcondición no cumplida: falta el índice único de nombre de usuario.");
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT COUNT(*)
                     FROM information_schema.KEY_COLUMN_USAGE k
                     JOIN information_schema.REFERENTIAL_CONSTRAINTS r
                       ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                      AND r.TABLE_NAME = k.TABLE_NAME
                      AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                     WHERE k.TABLE_SCHEMA = DATABASE()
                       AND k.TABLE_NAME = 'login_intentos'
                       AND k.COLUMN_NAME = 'usuario_id'
                       AND k.REFERENCED_TABLE_NAME = 'usuarios'
                       AND k.REFERENCED_COLUMN_NAME = 'id'
                       AND r.DELETE_RULE = 'CASCADE'
                     """)) {
            if (!rs.next() || rs.getInt(1) != 1) {
                throw new SQLException("Postcondición no cumplida: login_intentos no está ligado de forma única a usuarios.id.");
            }
        }
        if (!checkExiste(con, "detalle_pedidos", "chk_detalle_aplica_iva")
                || !checkExiste(con, "platos", "chk_platos_precio_clasificado")
                || !checkExiste(con, "platos", "chk_platos_precio_iva_coherente")
                || !checkExiste(con, "detalle_pedidos", "chk_detalle_precio_iva_coherente")
                || !checkExiste(con, "pedido_pagos", "chk_pedido_pagos_montos")
                || !checkExiste(con, "usuarios", "chk_usuarios_nombre_formato")) {
            throw new SQLException("Postcondición no cumplida: faltan restricciones de formato fiscal o de usuario.");
        }
        if (!foreignKeyExiste(con, "detalle_pedidos", "id_plato", "platos", "id", "SET NULL")
                || !foreignKeyExiste(con, "pedido_pagos", "id_pedido", "pedidos", "id", "RESTRICT")
                || !foreignKeyExiste(con, "pedido_pagos", "id_liquidacion", "pedido_liquidaciones", "id", "RESTRICT")) {
            throw new SQLException("Postcondición no cumplida: faltan las relaciones históricas restrictivas del nuevo esquema monetario.");
        }
        for (String columna : new String[]{"total", "subtotal", "iva_monto"}) {
            try (ResultSet rs = meta.getColumns(catalogo, null, "pedidos", columna)) {
                if (!rs.next() || rs.getInt("DECIMAL_DIGITS") != 2 || rs.getInt("COLUMN_SIZE") < 14) {
                    throw new SQLException("Postcondición no cumplida: pedidos." + columna + " no es DECIMAL(14,2).");
                }
            }
        }
        if (!pedidosEstadoAdmiteAnulacion(con)) {
            throw new SQLException("Postcondición no cumplida: pedidos.estado no admite 'ANULADO'.");
        }
        for (String columna : new String[]{"id_sala", "num_mesa"}) {
            try (ResultSet rs = meta.getColumns(catalogo, null, "pedidos", columna)) {
                if (!rs.next() || rs.getInt("NULLABLE") != DatabaseMetaData.columnNullable) {
                    throw new SQLException("Postcondición no cumplida: pedidos." + columna + " debe aceptar NULL.");
                }
            }
        }
        for (String[] par : new String[][] { {"platos", "aplica_iva"}, {"detalle_pedidos", "aplica_iva"} }) {
            try (PreparedStatement ps = con.prepareStatement("SELECT IS_NULLABLE FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
                ps.setString(1, par[0]);
                ps.setString(2, par[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !"NO".equalsIgnoreCase(rs.getString(1))) {
                        throw new SQLException("Postcondición no cumplida: " + par[0] + "." + par[1] + " debe ser NOT NULL.");
                    }
                }
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

        // 1b. Preferencias de ancho de columnas por usuario y tabla (v5).
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS preferencias_tabla (
                    usuario_id INT NOT NULL,
                    tabla_clave VARCHAR(64) NOT NULL,
                    layout_json VARCHAR(2000) NOT NULL,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (usuario_id, tabla_clave)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }

        // 2. Columnas en pedidos
        asegurarColumna(con, meta, catalogo, "pedidos", "total_bs", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "subtotal_bs", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_bs", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "subtotal", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_porcentaje", "DECIMAL(5,2) NOT NULL DEFAULT 16.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "iva_monto", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        asegurarColumna(con, meta, catalogo, "pedidos", "tasa_cambio", "DECIMAL(12,4) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "tasa_es_estimada", "TINYINT(1) NOT NULL DEFAULT 0");
        asegurarColumna(con, meta, catalogo, "pedidos", "cliente_nombre", "VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final'");
        asegurarColumna(con, meta, catalogo, "pedidos", "cliente_documento", "VARCHAR(30) NOT NULL DEFAULT 'V-00000000'");
        asegurarColumna(con, meta, catalogo, "pedidos", "metodo_pago", "VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'");
        asegurarColumna(con, meta, catalogo, "pedidos", "efectivo_bs", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "efectivo_usd", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "tipo_pedido", "VARCHAR(16) NOT NULL DEFAULT 'MESA'");
        asegurarPermiteNulos(con, meta, catalogo, "pedidos", "id_sala", "INT NULL DEFAULT NULL");
        asegurarPermiteNulos(con, meta, catalogo, "pedidos", "num_mesa", "INT NULL DEFAULT NULL");
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
        if (columnaExiste(meta, catalogo, "config", "modo_atencion")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE config DROP COLUMN modo_atencion");
                LOGGER.info("Columna config.modo_atencion eliminada; se gestiona en configuracion_sistema.");
            }
        }
        asegurarLongitudColumna(con, meta, catalogo, "usuarios", "pass", 255);
        asegurarColumna(con, meta, catalogo, "usuarios", "activo", "TINYINT(1) NOT NULL DEFAULT 1");

        // 4. Columnas en platos
        asegurarColumna(con, meta, catalogo, "platos", "activo", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "platos", "desactivado_en", "DATETIME NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "platos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumnaNoNula(con, "platos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");

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

        // Los intentos fallidos se guardan por ID de usuario, nunca por un texto arbitrario del formulario.
        asegurarTablaIntentosLogin(con);
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM configuracion_sistema WHERE LEFT(clave, 11) = '_sec_login_'");
        }

        // 7. Asegurar tabla de auditoría de pedidos (anulaciones y reimpresiones)
        asegurarTablaAuditoriaPedidos(con, meta, catalogo);

        // 8. Asegurar tabla de persistencia de cierres de caja y arqueos
        asegurarTablaCierresCaja(con, meta, catalogo);

        // 9. Asegurar tabla de mesoneros (CRUD, soft-delete, toggle activo)
        asegurarTablaMesoneros(con, meta, catalogo);

        // 10. Columna normalizada de identidad de plato (clave estable para categorías/favoritos/ranking)
        asegurarColumna(con, meta, catalogo, "platos", "nombre_clave",
                "VARCHAR(200) GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED");
        asegurarIndice(con, meta, catalogo, "platos", "idx_platos_nombre_clave", "(nombre_clave)");

        // 11. Misma clave normalizada en el detalle de pedidos para agregar el ranking sin desnormalizar
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "nombre_clave",
                "VARCHAR(200) GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED");
        asegurarIndice(con, meta, catalogo, "detalle_pedidos", "idx_detalle_pedidos_nombre_clave", "(nombre_clave)");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumnaNoNula(con, "detalle_pedidos", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
        if (checkExiste(con, "detalle_pedidos", "chk_detalle_aplica_iva")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE detalle_pedidos DROP CHECK chk_detalle_aplica_iva");
            }
        }
        asegurarCheck(con, "detalle_pedidos", "chk_detalle_aplica_iva", "(aplica_iva IN (0,1))");
        asegurarNombreUsuario(con, meta, catalogo);
        // La condición fiscal por línea es obligatoria (0/1) desde v12; ya no se admiten NULL.

        // 12. Asegurar tabla de categorías con paleta de colores (semilla: General)
        asegurarTablaCategorias(con, meta, catalogo);

        // 13. Asegurar tabla de asignación plato -> categoría (identidad por nombre_clave, sin FK a platos)
        asegurarTablaPlatoCategoria(con, meta, catalogo);

        // 14. Asegurar tabla de platos favoritos (identidad por nombre_clave)
        asegurarTablaPlatoFavorito(con, meta, catalogo);

        // 15. Ampliar pedidos.estado con 'ANULADO' para que la anulación de pedidos funcione
        asegurarEstadoPedidosConAnulacion(con);

        // 16. Guardar cada medio y moneda que compone los pagos mixtos.
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pedido_pagos (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    id_pedido INT NOT NULL,
                    metodo_pago VARCHAR(30) NOT NULL,
                    moneda CHAR(3) NOT NULL,
                    monto DECIMAL(14,2) NOT NULL,
                    tasa_cambio DECIMAL(12,4) NOT NULL,
                    monto_bs DECIMAL(14,2) NOT NULL,
                    monto_usd DECIMAL(14,2) NOT NULL,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_pedido_pagos_pedido (id_pedido),
                    INDEX idx_pedido_pagos_metodo_fecha (metodo_pago, creado_en),
                    CONSTRAINT fk_pedido_pagos_pedido FOREIGN KEY (id_pedido)
                        REFERENCES pedidos(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }

        // 17. Precio base explícito, snapshots por línea, cierre independiente del pago y ledger.
        asegurarEsquemaPrecios(con);
    }

    private static void asegurarEsquemaPrecios(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        String catalogo = con.getCatalog();
        asegurarColumna(con, meta, catalogo, "platos", "moneda_base", "CHAR(3) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "platos", "precio_usd", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "platos", "precio_ves", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "platos", "precio_clasificado", "TINYINT(1) NOT NULL DEFAULT 0");
        asegurarColumna(con, meta, catalogo, "platos", "precio_incluye_iva", "TINYINT(1) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "id_plato", "INT NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "moneda_base", "CHAR(3) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "precio_incluye_iva", "TINYINT(1) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "iva_porcentaje", "DECIMAL(5,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "precio_congelado", "TINYINT(1) NOT NULL DEFAULT 0");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "congelado_motivo", "VARCHAR(500) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "congelado_por", "VARCHAR(100) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "detalle_pedidos", "congelado_en", "DATETIME NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "cerrada_en", "DATETIME NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedidos", "version_precio", "INT NOT NULL DEFAULT 1");

        try (Statement st = con.createStatement()) {
            // Aumentar rango sin recalcular ni convertir los valores históricos.
            st.executeUpdate("ALTER TABLE platos MODIFY COLUMN precio DECIMAL(10,2) NULL DEFAULT NULL");
            st.executeUpdate("ALTER TABLE detalle_pedidos MODIFY COLUMN precio DECIMAL(14,2) NOT NULL");
            st.executeUpdate("ALTER TABLE pedidos MODIFY COLUMN subtotal DECIMAL(14,2) NOT NULL DEFAULT 0.00, "
                    + "MODIFY COLUMN iva_monto DECIMAL(14,2) NOT NULL DEFAULT 0.00, "
                    + "MODIFY COLUMN total DECIMAL(14,2) NOT NULL");
        }

        asegurarCheck(con, "platos", "chk_platos_precio_clasificado",
                "(precio_clasificado = 0 OR (moneda_base IS NOT NULL AND moneda_base IN ('USD','VES') "
                        + "AND ((moneda_base = 'USD' AND precio_usd > 0) OR (moneda_base = 'VES' AND precio_ves > 0)) "
                        + "AND precio_incluye_iva IS NOT NULL AND precio_incluye_iva IN (0,1)))");
        asegurarCheck(con, "platos", "chk_platos_precio_iva_coherente",
                "(aplica_iva = 1 OR precio_incluye_iva IS NULL OR precio_incluye_iva = 0)");
        asegurarCheck(con, "detalle_pedidos", "chk_detalle_precio_iva_coherente",
                "(aplica_iva = 1 OR precio_incluye_iva IS NULL OR precio_incluye_iva = 0)");

        asegurarForeignKeyDetallePlato(con);
        asegurarCierreMesaEIndice(con, meta, catalogo);
        asegurarTablaAuditoriaPrecios(con);
        asegurarTablaPedidoLiquidaciones(con);
        asegurarTablaMovimientosSaldo(con);
        asegurarColumnasPagoYRestriccion(con, meta, catalogo);
    }

    private static void asegurarCierreMesaEIndice(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        boolean requiereActualizar = true;
        try (PreparedStatement ps = con.prepareStatement("SELECT GENERATION_EXPRESSION FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'pedidos' AND column_name IN ('id_sala_pendiente','num_mesa_pendiente')");
             ResultSet rs = ps.executeQuery()) {
            int cantidadCorrecta = 0;
            while (rs.next()) {
                String expresion = rs.getString(1);
                if (expresion != null && expresion.toLowerCase(java.util.Locale.ROOT).contains("cerrada_en")) cantidadCorrecta++;
            }
            requiereActualizar = cantidadCorrecta != 2;
        }
        if (requiereActualizar) {
            try (Statement st = con.createStatement()) {
                if (indiceExiste(meta, catalogo, "pedidos", "uq_pedidos_mesa_pendiente")) {
                    st.executeUpdate("ALTER TABLE pedidos DROP INDEX uq_pedidos_mesa_pendiente");
                }
                if (columnaExiste(meta, catalogo, "pedidos", "id_sala_pendiente")) st.executeUpdate("ALTER TABLE pedidos DROP COLUMN id_sala_pendiente");
                if (columnaExiste(meta, catalogo, "pedidos", "num_mesa_pendiente")) st.executeUpdate("ALTER TABLE pedidos DROP COLUMN num_mesa_pendiente");
                st.executeUpdate("ALTER TABLE pedidos ADD COLUMN id_sala_pendiente INT GENERATED ALWAYS AS "
                        + "(CASE WHEN estado='PENDIENTE' AND cerrada_en IS NULL THEN id_sala ELSE NULL END) STORED");
                st.executeUpdate("ALTER TABLE pedidos ADD COLUMN num_mesa_pendiente INT GENERATED ALWAYS AS "
                        + "(CASE WHEN estado='PENDIENTE' AND cerrada_en IS NULL THEN num_mesa ELSE NULL END) STORED");
            }
        }
        asegurarIndiceUnico(con, con.getMetaData(), con.getCatalog(), "pedidos", "uq_pedidos_mesa_pendiente",
                "(id_sala_pendiente, num_mesa_pendiente)");
    }

    private static void asegurarForeignKeyDetallePlato(Connection con) throws SQLException {
        boolean existe = false;
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='detalle_pedidos' AND COLUMN_NAME='id_plato' "
                + "AND REFERENCED_TABLE_NAME='platos' AND REFERENCED_COLUMN_NAME='id'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) existe = rs.getInt(1) > 0;
        }
        if (!existe) {
            try (Statement st = con.createStatement()) {
                asegurarIndice(con, con.getMetaData(), con.getCatalog(), "detalle_pedidos", "idx_detalle_pedidos_plato", "(id_plato)");
                st.executeUpdate("ALTER TABLE detalle_pedidos ADD CONSTRAINT fk_detalle_pedidos_plato "
                        + "FOREIGN KEY (id_plato) REFERENCES platos(id) ON DELETE SET NULL");
            }
        }
    }

    private static void asegurarTablaAuditoriaPrecios(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS auditoria_cambios_precio (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    accion VARCHAR(32) NOT NULL,
                    producto_id INT NULL,
                    pedido_id INT NULL,
                    detalle_pedido_id INT NULL,
                    moneda_base CHAR(3) NULL,
                    precio_usd_anterior DECIMAL(14,2) NULL,
                    precio_usd_nuevo DECIMAL(14,2) NULL,
                    precio_ves_anterior DECIMAL(14,2) NULL,
                    precio_ves_nuevo DECIMAL(14,2) NULL,
                    tasa_anterior DECIMAL(12,4) NULL,
                    tasa_nueva DECIMAL(12,4) NULL,
                    total_anterior_ves DECIMAL(14,2) NULL,
                    total_nuevo_ves DECIMAL(14,2) NULL,
                    actor VARCHAR(100) NOT NULL,
                    motivo VARCHAR(500) NULL,
                    idempotency_key VARCHAR(100) NULL,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_auditoria_precios_idempotencia (idempotency_key),
                    KEY idx_auditoria_precios_producto_fecha (producto_id, creado_en),
                    KEY idx_auditoria_precios_pedido_fecha (pedido_id, creado_en)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
    }

    private static void asegurarTablaPedidoLiquidaciones(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pedido_liquidaciones (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    id_pedido INT NOT NULL,
                    version_precio INT NOT NULL,
                    saldo_restante_ves DECIMAL(14,2) NOT NULL,
                    aplicado_ves DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                    aplicado_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                    tasa_cambio DECIMAL(12,4) NOT NULL,
                    ajuste_cambio_bs DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                    diferencia_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                    version_regla VARCHAR(40) NOT NULL,
                    actor VARCHAR(100) NOT NULL,
                    idempotency_key VARCHAR(100) NOT NULL,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_pedido_liquidacion_version (id_pedido, version_precio),
                    UNIQUE KEY uq_pedido_liquidacion_idempotencia (idempotency_key),
                    CONSTRAINT fk_pedido_liquidaciones_pedido FOREIGN KEY (id_pedido)
                        REFERENCES pedidos(id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
    }

    private static void asegurarTablaMovimientosSaldo(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pedido_saldos_movimientos (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    id_pedido INT NOT NULL,
                    id_liquidacion BIGINT NULL,
                    tipo VARCHAR(20) NOT NULL,
                    moneda CHAR(3) NOT NULL,
                    monto_origen DECIMAL(14,2) NOT NULL,
                    equivalente_ves DECIMAL(14,2) NOT NULL,
                    tasa_cambio DECIMAL(12,4) NOT NULL,
                    actor VARCHAR(100) NOT NULL,
                    motivo VARCHAR(500) NOT NULL,
                    idempotency_key VARCHAR(100) NOT NULL,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_pedido_saldos_idempotencia (idempotency_key),
                    KEY idx_pedido_saldos_pedido_fecha (id_pedido, creado_en),
                    CONSTRAINT chk_pedido_saldos_tipo CHECK (tipo IN ('CREDITO','DEVOLUCION','ANULACION')),
                    CONSTRAINT fk_pedido_saldos_pedido FOREIGN KEY (id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT,
                    CONSTRAINT fk_pedido_saldos_liquidacion FOREIGN KEY (id_liquidacion)
                        REFERENCES pedido_liquidaciones(id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
    }

    private static void asegurarColumnasPagoYRestriccion(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "monto_recibido", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "monto_aplicado", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "cambio_devuelto", "DECIMAL(14,2) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "es_legado", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "id_liquidacion", "BIGINT NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "pedido_pagos", "idempotency_key", "VARCHAR(100) NULL DEFAULT NULL");
        asegurarIndiceUnico(con, con.getMetaData(), con.getCatalog(), "pedido_pagos", "uq_pedido_pagos_idempotencia", "(idempotency_key)");
        asegurarCheck(con, "pedido_pagos", "chk_pedido_pagos_montos",
                "(es_legado = 1 OR (monto_recibido IS NOT NULL AND monto_aplicado IS NOT NULL "
                        + "AND cambio_devuelto IS NOT NULL AND monto_recibido = monto_aplicado + cambio_devuelto))");
        boolean fkCorrecta = false;
        List<String> fks = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT k.CONSTRAINT_NAME, r.DELETE_RULE "
                + "FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                + "ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME "
                + "WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME='pedido_pagos' AND k.COLUMN_NAME='id_pedido' "
                + "AND k.REFERENCED_TABLE_NAME='pedidos'")) {
            while (rs.next()) {
                if ("RESTRICT".equalsIgnoreCase(rs.getString("DELETE_RULE")) || "NO ACTION".equalsIgnoreCase(rs.getString("DELETE_RULE"))) fkCorrecta = true;
                else fks.add(rs.getString("CONSTRAINT_NAME"));
            }
        }
        try (Statement st = con.createStatement()) {
            for (String fk : fks) st.executeUpdate("ALTER TABLE pedido_pagos DROP FOREIGN KEY `" + fk.replace("`", "``") + "`");
            if (!fkCorrecta) st.executeUpdate("ALTER TABLE pedido_pagos ADD CONSTRAINT fk_pedido_pagos_pedido "
                    + "FOREIGN KEY (id_pedido) REFERENCES pedidos(id) ON DELETE RESTRICT");
            if (!foreignKeyExiste(con, "pedido_pagos", "id_liquidacion", "pedido_liquidaciones", "id", "RESTRICT")) {
                st.executeUpdate("ALTER TABLE pedido_pagos ADD CONSTRAINT fk_pedido_pagos_liquidacion FOREIGN KEY (id_liquidacion) "
                        + "REFERENCES pedido_liquidaciones(id) ON DELETE RESTRICT");
            }
        }
    }

    private static boolean foreignKeyExiste(Connection con, String tabla, String columna, String tablaReferenciada,
            String columnaReferenciada, String reglaBorrado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE k "
                + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA "
                + "AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME "
                + "WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME=? AND k.COLUMN_NAME=? "
                + "AND k.REFERENCED_TABLE_NAME=? AND k.REFERENCED_COLUMN_NAME=? AND r.DELETE_RULE=?")) {
            ps.setString(1, tabla); ps.setString(2, columna); ps.setString(3, tablaReferenciada);
            ps.setString(4, columnaReferenciada); ps.setString(5, reglaBorrado);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
        }
    }

    private static void asegurarTablaIntentosLogin(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS login_intentos (
                    usuario_id INT NOT NULL PRIMARY KEY,
                    intentos INT NOT NULL DEFAULT 0,
                    bloqueado_hasta_ms BIGINT NOT NULL DEFAULT 0,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_login_intentos_usuario FOREIGN KEY (usuario_id)
                        REFERENCES usuarios(id) ON DELETE CASCADE,
                    CONSTRAINT chk_login_intentos_no_negativos CHECK (intentos >= 0 AND bloqueado_hasta_ms >= 0)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
        boolean claveForaneaCascadeExiste = false;
        List<String> clavesForaneasAEliminar = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT k.CONSTRAINT_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.DELETE_RULE
                     FROM information_schema.KEY_COLUMN_USAGE k
                     JOIN information_schema.REFERENTIAL_CONSTRAINTS r
                       ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                      AND r.TABLE_NAME = k.TABLE_NAME
                      AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                     WHERE k.TABLE_SCHEMA = DATABASE()
                       AND k.TABLE_NAME = 'login_intentos'
                       AND k.COLUMN_NAME = 'usuario_id'
                     """)) {
            while (rs.next()) {
                boolean correcta = "usuarios".equalsIgnoreCase(rs.getString("REFERENCED_TABLE_NAME"))
                        && "id".equalsIgnoreCase(rs.getString("REFERENCED_COLUMN_NAME"))
                        && "CASCADE".equalsIgnoreCase(rs.getString("DELETE_RULE"));
                if (correcta) claveForaneaCascadeExiste = true;
                else clavesForaneasAEliminar.add(rs.getString("CONSTRAINT_NAME"));
            }
        }
        if (!claveForaneaCascadeExiste || !clavesForaneasAEliminar.isEmpty()) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("DELETE li FROM login_intentos li LEFT JOIN usuarios u ON u.id = li.usuario_id WHERE u.id IS NULL");
                for (String nombre : clavesForaneasAEliminar) {
                    st.executeUpdate("ALTER TABLE login_intentos DROP FOREIGN KEY `"
                            + nombre.replace("`", "``") + "`");
                }
                if (!claveForaneaCascadeExiste) {
                    st.executeUpdate("ALTER TABLE login_intentos ADD CONSTRAINT fk_login_intentos_usuario "
                            + "FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE");
                }
            }
        }
    }

    /** Repara la relación que puede perderse al reemplazar usuarios durante una restauración. */
    public static void asegurarIntegridadIntentosLogin(Connection con) throws SQLException {
        if (con == null) throw new IllegalArgumentException("La conexión es obligatoria.");
        asegurarTablaIntentosLogin(con);
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

    private static void asegurarColumnaNoNula(Connection con, String tabla, String columna,
            String definicionNoNula) throws SQLException {
        String nullable;
        try (PreparedStatement ps = con.prepareStatement("SELECT IS_NULLABLE FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Falta columna requerida para migrar: " + tabla + "." + columna);
                }
                nullable = rs.getString(1);
            }
        }
        if ("NO".equalsIgnoreCase(nullable)) {
            return;
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM `" + tabla + "` WHERE `" + columna + "` IS NULL LIMIT 1")) {
            if (rs.next()) {
                LOGGER.log(Level.WARNING, "No se puede convertir a NOT NULL: {0}.{1} contiene registros con NULL. Se preserva como nullable para no abortar el inicio ni alterar históricos.",
                        new Object[]{tabla, columna});
                return;
            }
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE `" + tabla + "` MODIFY COLUMN `" + columna + "` " + definicionNoNula);
        }
    }

    private static void asegurarPermiteNulos(Connection con, DatabaseMetaData meta, String catalogo,
            String tabla, String columna, String definicionNullable) throws SQLException {
        try (ResultSet rs = meta.getColumns(catalogo, null, tabla, columna)) {
            if (!rs.next()) {
                throw new SQLException("Falta columna requerida para pedidos rápidos: " + tabla + "." + columna);
            }
            if (rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE " + tabla + " MODIFY COLUMN " + columna + " " + definicionNullable);
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

    private static void asegurarNombreUsuario(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        java.util.List<Object[]> nombres = new java.util.ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT id, nombre FROM usuarios"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    nombres.add(new Object[]{rs.getInt("id"), Modelo.NombreUsuario.normalizar(rs.getString("nombre"))});
                } catch (Modelo.ErrorAplicacionException ex) {
                    throw new SQLException("No se puede migrar usuarios: el nombre del usuario con ID "
                            + rs.getInt("id") + " no cumple el formato o usa un nombre reservado. Corríjalo y vuelva a iniciar.", ex);
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE usuarios SET nombre = ? WHERE id = ?")) {
            for (Object[] fila : nombres) {
                ps.setString(1, (String) fila[1]);
                ps.setInt(2, (Integer) fila[0]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        asegurarColumna(con, meta, catalogo, "usuarios", "nombre_unico",
                "VARCHAR(200) GENERATED ALWAYS AS (LOWER(TRIM(nombre))) STORED");
        // Pre-validar duplicados antes de crear índice único para dar mensaje accionable
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT LOWER(TRIM(nombre)) AS nombre_norm, COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id SEPARATOR ', ') AS ids "
                + "FROM usuarios GROUP BY LOWER(TRIM(nombre)) HAVING cnt > 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<String> conflictos = new java.util.ArrayList<>();
                while (rs.next()) {
                    conflictos.add("'" + rs.getString("nombre_norm") + "' (IDs: " + rs.getString("ids") + ")");
                }
                if (!conflictos.isEmpty()) {
                    throw new SQLException(
                        "No se puede crear índice único de nombres en 'usuarios': existen nombres duplicados (insensibles a mayúsculas). "
                        + "Corrija manualmente estos registros antes de iniciar: "
                        + String.join("; ", conflictos));
                }
            }
        }
        asegurarIndiceUnico(con, meta, catalogo, "usuarios", "uq_usuarios_nombre", "(nombre_unico)");
        if (checkExiste(con, "usuarios", "chk_usuarios_nombre_formato")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE usuarios DROP CHECK chk_usuarios_nombre_formato");
            }
        }
        asegurarCheck(con, "usuarios", "chk_usuarios_nombre_formato",
                "(CHAR_LENGTH(nombre) BETWEEN 3 AND 60 AND LOWER(TRIM(nombre)) <> 'admin' AND nombre REGEXP "
                        + "'^[[:alnum:]][[:alnum:] ._-]{1,58}[[:alnum:]]$')");
    }

    private static void asegurarCheck(Connection con, String tabla, String nombre, String expresion) throws SQLException {
        if (!checkExiste(con, tabla, nombre)) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE " + tabla + " ADD CONSTRAINT " + nombre + " CHECK " + expresion);
            }
        }
    }

    private static boolean checkExiste(Connection con, String tabla, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM information_schema.table_constraints "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND constraint_name = ? LIMIT 1")) {
            ps.setString(1, tabla);
            ps.setString(2, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
                    ('meses_retencion_pedidos', '24'),
                    ('modo_atencion', 'OPCION_B')
                    """;
                try (Statement stDef = con.createStatement()) {
                    stDef.executeUpdate(insertDefaults);
                }
            }
            try (Statement stModo = con.createStatement()) {
                stModo.executeUpdate("INSERT IGNORE INTO configuracion_sistema (clave, valor, descripcion) "
                        + "VALUES ('modo_atencion', 'OPCION_B', 'Modo predeterminado de atención de mesas')");
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

    private static boolean pedidosEstadoAdmiteAnulacion(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pedidos' AND COLUMN_NAME = 'estado'")) {
            return rs.next() && rs.getString(1).toUpperCase(java.util.Locale.ROOT).contains("ANULADO");
        }
    }

    private static void asegurarEstadoPedidosConAnulacion(Connection con) throws SQLException {
        if (pedidosEstadoAdmiteAnulacion(con)) {
            return;
        }
        String definicion;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                     + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pedidos' AND COLUMN_NAME = 'estado'")) {
            if (!rs.next()) throw new SQLException("Falta pedidos.estado; se cancela el cambio de tipo.");
            definicion = rs.getString(1);
        }
        if (definicion == null || !definicion.toUpperCase(java.util.Locale.ROOT).startsWith("ENUM(")) {
            throw new SQLException("Tipo de pedidos.estado no reconocido; se requiere migración manual sin conversión automática: " + definicion);
        }
        java.util.regex.Matcher valores = java.util.regex.Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'").matcher(definicion);
        java.util.LinkedHashSet<String> opciones = new java.util.LinkedHashSet<>();
        while (valores.find()) {
            opciones.add(valores.group(1).replace("\\\\'", "'").replace("\\\\\\\\", "\\\\"));
        }
        if (!opciones.containsAll(Set.of("PENDIENTE", "FINALIZADO"))) {
            throw new SQLException("pedidos.estado no contiene los estados estándar; no se reemplazará su definición: " + definicion);
        }
        opciones.add("ANULADO");
        String lista = opciones.stream().map(v -> "'" + v.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'") + "'")
                .collect(java.util.stream.Collectors.joining(","));
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE pedidos MODIFY COLUMN estado ENUM(" + lista
                    + ") COLLATE utf8_spanish_ci NOT NULL DEFAULT 'PENDIENTE'");
        }
        LOGGER.info("pedidos.estado ampliado automáticamente con 'ANULADO'.");
    }

    private static void asegurarTablaCategorias(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS categorias (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nombre VARCHAR(60) NOT NULL UNIQUE,
                    color CHAR(7) NOT NULL DEFAULT '#6B7280',
                    orden INT NOT NULL DEFAULT 0,
                    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
            st.executeUpdate("INSERT IGNORE INTO categorias (nombre, color, orden) VALUES ('General', '#6B7280', 0)");
        }
        LOGGER.info("Tabla categorias verificada.");
    }

    private static void asegurarTablaPlatoCategoria(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS plato_categoria (
                    nombre_clave VARCHAR(200) NOT NULL,
                    id_categoria INT NOT NULL,
                    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (nombre_clave),
                    INDEX idx_plato_categoria_categoria (id_categoria)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
        LOGGER.info("Tabla plato_categoria verificada.");
    }

    private static void asegurarTablaPlatoFavorito(Connection con, DatabaseMetaData meta, String catalogo) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS plato_favorito (
                    nombre_clave VARCHAR(200) NOT NULL,
                    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (nombre_clave)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
        LOGGER.info("Tabla plato_favorito verificada.");
    }
}
