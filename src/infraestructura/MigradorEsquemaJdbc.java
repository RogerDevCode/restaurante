package infraestructura;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final AtomicBoolean MIGRADO = new AtomicBoolean(false);

    private MigradorEsquemaJdbc() {
    }

    public static synchronized void migrarSiEsNecesario(ProveedorConexionJdbc proveedor) {
        if (MIGRADO.get()) {
            return;
        }
        if (proveedor == null) {
            return;
        }

        try (Connection con = proveedor.getConnection()) {
            asegurarEsquema(con);
            MIGRADO.set(true);
            LOGGER.info("Esquema de base de datos verificado y sincronizado correctamente.");
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Aviso al sincronizar automáticamente el esquema de la BD: " + ex.getMessage(), ex);
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
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Aviso creando tabla clientes: " + e.getMessage());
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

        // 3. Columnas en config
        asegurarColumna(con, meta, catalogo, "config", "logo_path", "VARCHAR(255) NULL DEFAULT NULL");
        asegurarColumna(con, meta, catalogo, "config", "tasa_dolar", "DECIMAL(12,4) NOT NULL DEFAULT 36.5000");
        asegurarColumna(con, meta, catalogo, "config", "iva_porcentaje", "DECIMAL(5,2) NOT NULL DEFAULT 16.00");
        asegurarColumna(con, meta, catalogo, "config", "cliente_predeterminado_nombre", "VARCHAR(150) NOT NULL DEFAULT 'Consumidor Final'");
        asegurarColumna(con, meta, catalogo, "config", "cliente_predeterminado_documento", "VARCHAR(30) NOT NULL DEFAULT 'V-00000000'");
        asegurarColumna(con, meta, catalogo, "config", "meses_retencion_pedidos", "INT NOT NULL DEFAULT 24");
        asegurarColumna(con, meta, catalogo, "config", "imprimir_logo_ticket", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "config", "impresora_tickets", "VARCHAR(150) NOT NULL DEFAULT 'DEFAULT'");
        asegurarColumna(con, meta, catalogo, "config", "modo_salida_tickets", "VARCHAR(50) NOT NULL DEFAULT 'TERMICA_DIRECTA'");

        // 4. Columnas en platos
        asegurarColumna(con, meta, catalogo, "platos", "activo", "TINYINT(1) NOT NULL DEFAULT 1");
        asegurarColumna(con, meta, catalogo, "platos", "desactivado_en", "DATETIME NULL DEFAULT NULL");

        // 5. Índices de rendimiento
        asegurarIndice(con, meta, catalogo, "pedidos", "idx_pedidos_estado_fecha", "(estado, fecha)");
        asegurarIndice(con, meta, catalogo, "pedidos", "idx_pedidos_cliente_doc", "(cliente_documento)");
        asegurarIndice(con, meta, catalogo, "detalle_pedidos", "idx_detalle_pedidos_nombre", "(nombre)");

        // 6. Asegurar tabla clave-valor de configuración del sistema
        asegurarTablaConfiguracionSistema(con, meta, catalogo);

        // 7. Asegurar tabla de auditoría de pedidos (anulaciones y reimpresiones)
        asegurarTablaAuditoriaPedidos(con, meta, catalogo);

        // 8. Asegurar tabla de persistencia de cierres de caja y arqueos
        asegurarTablaCierresCaja(con, meta, catalogo);
    }

    private static void asegurarColumna(Connection con, DatabaseMetaData meta, String catalogo, String tabla, String columna, String definicion) {
        try {
            if (!columnaExiste(meta, catalogo, tabla, columna)) {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                    LOGGER.info("Columna sincronizada automáticamente: " + tabla + "." + columna);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso asegurando columna " + tabla + "." + columna + ": " + ex.getMessage());
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

    private static void asegurarIndice(Connection con, DatabaseMetaData meta, String catalogo, String tabla, String nombreIndice, String columnas) {
        try {
            if (!indiceExiste(meta, catalogo, tabla, nombreIndice)) {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + tabla + " ADD INDEX " + nombreIndice + " " + columnas);
                    LOGGER.info("Índice sincronizado automáticamente: " + tabla + "." + nombreIndice);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso asegurando índice " + tabla + "." + nombreIndice + ": " + ex.getMessage());
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

    private static void asegurarTablaConfiguracionSistema(Connection con, DatabaseMetaData meta, String catalogo) {
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
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso creando tabla configuracion_sistema: " + ex.getMessage());
        }

        try {
            migrarParametrosDesdeConfig(con, meta, catalogo);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso sincronizando parámetros clave-valor: " + ex.getMessage());
        }
    }

    private static void migrarParametrosDesdeConfig(Connection con, DatabaseMetaData meta, String catalogo) {
        String sqlSelect = "SELECT * FROM config LIMIT 1";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sqlSelect)) {
            if (rs.next()) {
                String insertSql = "INSERT IGNORE INTO configuracion_sistema (clave, valor) VALUES (?, ?)";
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
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso migrando config a clave-valor: " + ex.getMessage());
        }
    }

    private static void insertarClaveDesdeResultSetSiExiste(DatabaseMetaData meta, String catalogo, ResultSet rs, PreparedStatement ps, String columnaBd, String clave, String porDefecto) {
        try {
            if (columnaExiste(meta, catalogo, "config", columnaBd)) {
                String valor = rs.getString(columnaBd);
                ps.setString(1, clave);
                ps.setString(2, valor != null && !valor.isBlank() ? valor : porDefecto);
                ps.executeUpdate();
            } else {
                ps.setString(1, clave);
                ps.setString(2, porDefecto);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    private static void asegurarTablaAuditoriaPedidos(Connection con, DatabaseMetaData meta, String catalogo) {
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
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso creando tabla auditoria_pedidos: " + ex.getMessage());
        }
    }

    private static void asegurarTablaCierresCaja(Connection con, DatabaseMetaData meta, String catalogo) {
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
        } catch (SQLException ex) {
            LOGGER.log(Level.FINE, "Aviso creando tabla cierres_caja: " + ex.getMessage());
        }
    }
}
