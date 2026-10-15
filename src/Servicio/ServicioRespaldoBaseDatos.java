package Servicio;

import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import infraestructura.ProveedorConexionJdbc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio integral para el respaldo automático y manual de la base de datos MySQL
 * y su correspondiente restauración.
 * Opera 100% mediante JDBC puro, sin depender de binarios externos como mysqldump.exe o mysql.exe,
 * garantizando compatibilidad total en Windows 11 y Linux.
 */
public class ServicioRespaldoBaseDatos {

    private static final Logger LOGGER = Logger.getLogger(ServicioRespaldoBaseDatos.class.getName());
    public static final Path DIRECTORIO_RESPALDOS_DEFAULT = Paths.get("respaldos");
    public static final String MAGIC_HEADER = "-- RESTAURANTE_2026_BACKUP_SQL";
    public static final String MAGIC_FOOTER = "-- FIN DEL RESPALDO RESTAURANTE 2026";
    private static final DateTimeFormatter FORMATO_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter FORMATO_FECHA_DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern SQL_INSERT_RESPALDO_HEAD = Pattern.compile(
            "(?is)^INSERT\\s+INTO\\s+`[^`]+`(?:\\s*\\(([^)]*)\\))?\\s+VALUES\\s*(.*)$");
    private static final Pattern SQL_INSERT_COLUMNS = Pattern.compile(
            "(?is)^\\s*`[^`]+`(?:\\s*,\\s*`[^`]+`)*\\s*$");
    private static final Pattern SQL_LITERAL_RESPALDO = Pattern.compile(
            "(?is)^(?:NULL|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|'(?:\\\\.|[^'\\\\])*')$");
    private static final Pattern SQL_DROP_TABLA_RESPALDO = Pattern.compile("(?is)^DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`([^`]+)`$");
    private static final Pattern SQL_CREATE_TABLA_RESPALDO = Pattern.compile(
            "(?is)^CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`([^`]+)`\\s*\\(.*\\)\\s+ENGINE\\s*=.*$");

    public static final Set<String> TABLAS_PERMITIDAS_RESPALDO = Set.of(
            "config",
            "usuarios",
            "platos",
            "salas",
            "pedidos",
            "detalle_pedidos",
            "pedido_pagos",
            "clientes",
            "configuracion_sistema",
            "auditoria_pedidos",
            "cierres_caja",
            "preferencias_tabla",
            "mesoneros",
            "categorias",
            "plato_categoria",
            "plato_favorito",
            "test_respaldo_tmp"
    );

    public static final Set<String> TABLAS_PROTEGIDAS_INFRAESTRUCTURA = Set.of(
            "restaurante_control",
            "schema_migrations",
            "login_intentos"
    );

    private final ProveedorConexionJdbc conexiones;
    private final Path directorioRespaldos;
    private final infraestructura.DiarioRecuperacionBD diarioRecuperacion;

    private void validarTablaPermitida(String tabla) {
        if (tabla == null || tabla.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El respaldo contiene una referencia a tabla vacía o nula.");
        }
        String t = tabla.trim().toLowerCase(java.util.Locale.ROOT);
        if (TABLAS_PROTEGIDAS_INFRAESTRUCTURA.contains(t)) {
            throw ErrorAplicacionException.validacion(
                    "El respaldo intenta reemplazar una tabla de infraestructura protegida ('" + tabla + "') y fue rechazado.");
        }
        if (!TABLAS_PERMITIDAS_RESPALDO.contains(t)) {
            throw ErrorAplicacionException.validacion(
                    "El respaldo contiene una tabla no reconocida o fuera de la lista blanca ('" + tabla + "') y fue rechazado.");
        }
    }

    private record TablaExportada(String nombre, List<String> columnas, long filas) {}
    private static final class ResumenSql {
        final Set<String> eliminadas = new HashSet<>();
        final Set<String> creadas = new HashSet<>();
        final Map<String, Long> filas = new HashMap<>();
        final Map<String, List<String>> columnas = new HashMap<>();
    }

    public ServicioRespaldoBaseDatos() {
        this(new ProveedorConexionJdbc(), DIRECTORIO_RESPALDOS_DEFAULT);
    }

    public ServicioRespaldoBaseDatos(ProveedorConexionJdbc conexiones) {
        this(conexiones, DIRECTORIO_RESPALDOS_DEFAULT);
    }

    public ServicioRespaldoBaseDatos(ProveedorConexionJdbc conexiones, Path directorioRespaldos) {
        this(conexiones, directorioRespaldos, infraestructura.DiarioRecuperacionBD.porDefecto());
    }

    public ServicioRespaldoBaseDatos(ProveedorConexionJdbc conexiones, Path directorioRespaldos,
            infraestructura.DiarioRecuperacionBD diarioRecuperacion) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones no puede ser nulo.");
        this.directorioRespaldos = directorioRespaldos != null ? directorioRespaldos : DIRECTORIO_RESPALDOS_DEFAULT;
        this.diarioRecuperacion = Objects.requireNonNull(diarioRecuperacion, "El diario de recuperación no puede ser nulo.");
    }

    public Path getDirectorioRespaldos() {
        return directorioRespaldos;
    }

    /**
     * Genera un volcado SQL completo de la base de datos (DDL + DML) en el directorio configurado.
     * Utiliza una transacción REPEATABLE READ para consistencia de lectura snapshot.
     *
     * @return Ruta del archivo .sql generado.
     */
    public Path crearRespaldo() {
        return crearRespaldo(this.directorioRespaldos, "respaldo_restaurante_");
    }

    /**
     * Genera un volcado SQL completo en un directorio destino específico.
     *
     * @param destino Directorio donde se escribirá el archivo.
     * @return Ruta del archivo .sql generado.
     */
    public Path crearRespaldo(Path destino) {
        return crearRespaldo(destino, "respaldo_restaurante_");
    }

    /**
     * Genera un volcado SQL de seguridad previo a una restauración.
     */
    public Path crearRespaldoPreRestauracion() {
        return crearRespaldo(this.directorioRespaldos, "pre_restauracion_seguridad_");
    }

    /** Respaldo obligatorio que precede cambios DDL de una migración. */
    public Path crearRespaldoPreMigracion() {
        return crearRespaldo(this.directorioRespaldos, "pre_migracion_restaurante_");
    }

    private Path crearRespaldo(Path destino, String prefijoNombre) {
        if (destino == null) {
            destino = this.directorioRespaldos;
        }
        final byte[] claveHmac;
        try {
            claveHmac = obtenerClaveHmac();
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se puede crear un respaldo autenticado: " + ex.getMessage(), ex);
        }
        try {
            if (!Files.exists(destino)) {
                Files.createDirectories(destino);
            }
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio de respaldos: " + destino, ex);
        }

        String timestamp = LocalDateTime.now().format(FORMATO_ARCHIVO);
        String identificador = timestamp + "_" + UUID.randomUUID();
        Path temporal = destino.resolve("." + prefijoNombre + identificador + ".tmp");
        Path archivoSql = destino.resolve(prefijoNombre + identificador + ".sql");
        Path manifiestoTemporal = manifiestoPath(temporal);
        Path checksumTemporal = checksumPath(temporal);
        List<TablaExportada> resumenTablas = new ArrayList<>();

        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            int aislamientoPrevio = con.getTransactionIsolation();

            try {
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.setAutoCommit(false);

                infraestructura.ControlMantenimientoBD.bloquearSnapshot(con);
                validarCoberturaRespaldable(con);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(temporal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        StandardCharsets.UTF_8))) {
                    escribirEncabezado(writer, timestamp);

                    List<String> tablas = listarTablas(con);
                    for (String tabla : tablas) {
                        resumenTablas.add(exportarTabla(con, writer, tabla));
                    }

                    escribirPie(writer);
                    writer.flush();
                }

                con.commit();
                crearManifiesto(temporal, con.getCatalog(), resumenTablas, claveHmac);
                crearChecksum(temporal, temporal.getFileName().toString());
                validarArchivoRespaldo(temporal);
                Files.move(temporal, archivoSql);
                Files.move(manifiestoTemporal, manifiestoPath(archivoSql));
                Files.move(checksumTemporal, checksumPath(archivoSql));
                Files.deleteIfExists(checksumPath(archivoSql));
                crearChecksum(archivoSql, archivoSql.getFileName().toString());
                validarArchivoRespaldo(archivoSql);
                LOGGER.info("Respaldo de base de datos generado exitosamente: " + archivoSql.toAbsolutePath());
                return archivoSql;

            } catch (SQLException | IOException | RuntimeException ex) {
                try { con.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                try {
                    Files.deleteIfExists(temporal);
                    Files.deleteIfExists(archivoSql);
                    Files.deleteIfExists(checksumPath(archivoSql));
                    Files.deleteIfExists(manifiestoTemporal);
                    Files.deleteIfExists(checksumTemporal);
                    Files.deleteIfExists(manifiestoPath(archivoSql));
                } catch (IOException ignored) {
                }
                throw new DataAccessException("Falló la creación del respaldo consistente de la base de datos: " + ex.getMessage(), ex);
            } finally {
                try {
                    con.setTransactionIsolation(aislamientoPrevio);
                    con.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
            }

        } catch (SQLException ex) {
            throw new DataAccessException("Error de conexión durante el respaldo: " + ex.getMessage(), ex);
        }
    }

    private void validarCoberturaRespaldable(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        String catalogo = con.getCatalog();
        try (ResultSet rs = meta.getTables(catalogo, null, "%", null)) {
            while (rs.next()) {
                String tipo = rs.getString("TABLE_TYPE");
                String nombre = rs.getString("TABLE_NAME");
                if (tipo != null && !"TABLE".equalsIgnoreCase(tipo) && !"BASE TABLE".equalsIgnoreCase(tipo)) {
                    throw new SQLException("No se puede garantizar respaldo íntegro: existe un objeto no soportado (" + tipo + "): " + nombre);
                }
            }
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_NAME, ENGINE FROM information_schema.tables WHERE table_schema = DATABASE() AND TABLE_TYPE='BASE TABLE'")) {
            while (rs.next()) {
                String engine = rs.getString("ENGINE");
                if (engine == null || !"InnoDB".equalsIgnoreCase(engine)) {
                    throw new SQLException("No se puede garantizar snapshot consistente: tabla " + rs.getString("TABLE_NAME") + " usa motor " + engine);
                }
            }
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT TRIGGER_NAME FROM information_schema.triggers WHERE trigger_schema = DATABASE() LIMIT 1")) {
            if (rs.next()) throw new SQLException("No se puede respaldar: hay triggers que el volcado JDBC no incluye.");
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT ROUTINE_NAME FROM information_schema.routines WHERE routine_schema = DATABASE() LIMIT 1")) {
            if (rs.next()) throw new SQLException("No se puede respaldar: hay rutinas que el volcado JDBC no incluye.");
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT EVENT_NAME FROM information_schema.events WHERE event_schema = DATABASE() LIMIT 1")) {
            if (rs.next()) throw new SQLException("No se puede respaldar: hay eventos que el volcado JDBC no incluye.");
        }
    }

    private Path checksumPath(Path archivo) {
        return archivo.resolveSibling(archivo.getFileName() + ".sha256");
    }

    private Path manifiestoPath(Path archivo) {
        return archivo.resolveSibling(archivo.getFileName() + ".manifest");
    }

    private void crearChecksum(Path archivo, String nombrePublicado) throws IOException {
        try {
            String checksumSql = java.util.HexFormat.of().formatHex(calcularSha256(archivo));
            String checksumManifest = java.util.HexFormat.of().formatHex(calcularSha256(manifiestoPath(archivo)));
            Files.writeString(checksumPath(archivo), "archivo=" + nombrePublicado + System.lineSeparator()
                            + "sql=" + checksumSql + System.lineSeparator()
                            + "manifest=" + checksumManifest + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 no está disponible para verificar el respaldo.", ex);
        }
    }

    private void crearManifiesto(Path archivo, String catalogo, List<TablaExportada> tablas, byte[] claveHmac) throws IOException {
        Properties p = new Properties();
        p.setProperty("formato", "1");
        try {
            p.setProperty("sha256", java.util.HexFormat.of().formatHex(calcularSha256(archivo)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 no está disponible para el manifiesto.", ex);
        }
        p.setProperty("catalogo", Base64.getEncoder().encodeToString(Objects.toString(catalogo, "").getBytes(StandardCharsets.UTF_8)));
        p.setProperty("tablas.cantidad", Integer.toString(tablas.size()));
        for (int i = 0; i < tablas.size(); i++) {
            TablaExportada t = tablas.get(i);
            p.setProperty("tabla." + i + ".nombre", t.nombre());
            p.setProperty("tabla." + i + ".filas", Long.toString(t.filas()));
            p.setProperty("tabla." + i + ".columnas", Base64.getEncoder().encodeToString(
                    String.join("\u001f", t.columnas()).getBytes(StandardCharsets.UTF_8)));
        }
        byte[] hmac = calcularHmac(contenidoCanonicoManifiesto(p), claveHmac);
        p.setProperty("hmac_sha256", java.util.HexFormat.of().formatHex(hmac));
        try (var writer = Files.newBufferedWriter(manifiestoPath(archivo), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            p.store(writer, "Manifiesto de respaldo Restaurante");
        }
    }

    private static final String HMAC_ALGO = "HmacSHA256";

    private byte[] obtenerClaveHmac() throws IOException {
        String clave = null;
        try {
            if (conexiones != null) {
                clave = conexiones.settingConfigurado("BACKUP_HMAC_KEY");
            }
        } catch (Exception ex) {
            throw new IOException("No se pudo leer de forma segura BACKUP_HMAC_KEY desde la configuración local.", ex);
        }
        if (clave == null || clave.isBlank()) {
            clave = System.getProperty("BACKUP_HMAC_KEY");
        }
        if (clave == null || clave.isBlank()) {
            clave = System.getenv("BACKUP_HMAC_KEY");
        }
        if (clave == null || clave.isBlank()) {
            throw new IOException("BACKUP_HMAC_KEY es obligatoria para crear o restaurar respaldos. Genere una con `openssl rand -hex 32` y configúrela en .env.");
        }
        clave = clave.trim();
        byte[] bytes = clave.length() >= 64 && clave.length() % 2 == 0 && clave.matches("[0-9a-fA-F]+")
                ? java.util.HexFormat.of().parseHex(clave)
                : clave.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IOException("BACKUP_HMAC_KEY debe contener al menos 32 bytes (o 64 caracteres hexadecimales).");
        }
        return bytes;
    }

    private static byte[] contenidoCanonicoManifiesto(Properties manifiesto) {
        StringBuilder canonical = new StringBuilder();
        manifiesto.stringPropertyNames().stream()
                .filter(nombre -> !"hmac_sha256".equals(nombre))
                .sorted()
                .forEach(nombre -> {
                    String valor = manifiesto.getProperty(nombre, "");
                    canonical.append(nombre.length()).append(':').append(nombre)
                            .append(valor.length()).append(':').append(valor).append('\n');
                });
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] calcularHmac(byte[] datos, byte[] clave) throws IOException {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_ALGO);
            mac.init(new javax.crypto.spec.SecretKeySpec(clave, HMAC_ALGO));
            return mac.doFinal(datos);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IOException("No se pudo calcular HMAC-SHA256: " + ex.getMessage(), ex);
        }
    }

    private void validarChecksumSiExiste(Path archivo) {
        Path checksum = checksumPath(archivo);
        if (!Files.exists(checksum)) {
            throw ErrorAplicacionException.validacion("El respaldo no tiene checksum; no se puede verificar su integridad.");
        }
        try {
            Properties sumas = new Properties();
            for (String linea : Files.readAllLines(checksum, StandardCharsets.UTF_8)) {
                int separador = linea.indexOf('=');
                if (separador > 0) {
                    String clave = linea.substring(0, separador).trim();
                    if (sumas.containsKey(clave)) {
                        throw ErrorAplicacionException.validacion("El checksum del respaldo contiene campos duplicados.");
                    }
                    sumas.setProperty(clave, linea.substring(separador + 1).trim());
                }
            }
            if (!sumas.stringPropertyNames().equals(Set.of("archivo", "sql", "manifest"))
                    || !archivo.getFileName().toString().equals(sumas.getProperty("archivo"))) {
                throw ErrorAplicacionException.validacion("El checksum no corresponde al nombre de este archivo de respaldo.");
            }
            String actual = java.util.HexFormat.of().formatHex(calcularSha256(archivo));
            String actualManifiesto = java.util.HexFormat.of().formatHex(calcularSha256(manifiestoPath(archivo)));
            if (!MessageDigest.isEqual(sumas.getProperty("sql", "").getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII))
                    || !MessageDigest.isEqual(sumas.getProperty("manifest", "").getBytes(StandardCharsets.US_ASCII),
                            actualManifiesto.getBytes(StandardCharsets.US_ASCII))) {
                throw ErrorAplicacionException.validacion("El checksum SHA-256 del respaldo no coincide.");
            }
            Properties manifiesto = leerManifiesto(archivo);
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    manifiesto.getProperty("sha256", "").getBytes(StandardCharsets.US_ASCII))) {
                throw ErrorAplicacionException.validacion("El checksum del manifiesto no coincide con el respaldo.");
            }
            String hmacManifest = manifiesto.getProperty("hmac_sha256");
            byte[] claveHmac = obtenerClaveHmac();
            if (hmacManifest == null || hmacManifest.isBlank()) {
                throw ErrorAplicacionException.validacion(
                        "El respaldo no tiene firma HMAC y no se puede autenticar; se rechaza la restauración.");
            }
            if (!hmacManifest.matches("(?i)[0-9a-f]{64}")) {
                throw ErrorAplicacionException.validacion("La firma HMAC del manifiesto tiene un formato inválido.");
            }
            byte[] hmacCalculado = calcularHmac(contenidoCanonicoManifiesto(manifiesto), claveHmac);
            String hmacCalculadoHex = java.util.HexFormat.of().formatHex(hmacCalculado);
            if (!MessageDigest.isEqual(hmacManifest.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                    hmacCalculadoHex.getBytes(StandardCharsets.US_ASCII))) {
                throw ErrorAplicacionException.validacion(
                        "La firma HMAC del manifiesto de respaldo no coincide. El archivo fue alterado o no proviene de una fuente autorizada.");
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new DataAccessException("No se pudo verificar el checksum del respaldo.", ex);
        }
    }

    private Properties leerManifiesto(Path archivo) throws IOException {
        Path manifestPath = manifiestoPath(archivo);
        if (!Files.isRegularFile(manifestPath)) {
            throw ErrorAplicacionException.validacion("El respaldo no tiene manifiesto de cobertura.");
        }
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) { p.load(reader); }
        if (!"1".equals(p.getProperty("formato"))) {
            throw ErrorAplicacionException.validacion("La versión del manifiesto de respaldo no es compatible.");
        }
        return p;
    }

    private byte[] calcularSha256(Path archivo) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(archivo)) {
            byte[] buffer = new byte[64 * 1024];
            int leidos;
            while ((leidos = input.read(buffer)) >= 0) {
                if (leidos > 0) digest.update(buffer, 0, leidos);
            }
        }
        return digest.digest();
    }

    /**
     * Crea un respaldo automático si aún no existe uno para el día actual.
     *
     * @return Ruta del archivo generado o null si ya existía hoy.
     */
    public Path crearRespaldoAutomaticoSiEsNecesario() {
        try {
            if (!Files.exists(directorioRespaldos)) {
                Files.createDirectories(directorioRespaldos);
            }
            String prefijoHoy = "respaldo_restaurante_" + LocalDate.now().format(FORMATO_FECHA_DIA);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directorioRespaldos, prefijoHoy + "*.sql")) {
                for (Path existente : stream) {
                    try {
                        validarArchivoRespaldo(existente);
                        LOGGER.fine("Ya existe un respaldo verificado para la fecha de hoy. Se omite respaldo automático.");
                        return null;
                    } catch (RuntimeException invalido) {
                        String sufijo = ".invalido-" + System.currentTimeMillis();
                        Files.move(existente, existente.resolveSibling(existente.getFileName() + sufijo));
                        Path checksum = checksumPath(existente);
                        Path manifest = manifiestoPath(existente);
                        if (Files.exists(checksum)) Files.move(checksum, checksum.resolveSibling(checksum.getFileName() + sufijo));
                        if (Files.exists(manifest)) Files.move(manifest, manifest.resolveSibling(manifest.getFileName() + sufijo));
                        LOGGER.warning("Se apartó un respaldo diario inválido: " + existente.getFileName());
                    }
                }
            }
            Path nuevoRespaldo = crearRespaldo();
            limpiarRespaldosAntiguos(30);
            return nuevoRespaldo;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso durante respaldo automático de la BD: " + ex.getMessage());
            return null;
        }
    }

    /** Valida las marcas, el cierre y las únicas sentencias admitidas en el formato de respaldo del sistema. */
    public void validarArchivoRespaldo(Path archivoSql) {
        if (archivoSql == null || !Files.exists(archivoSql) || !Files.isRegularFile(archivoSql)) {
            throw ErrorAplicacionException.validacion("El archivo de respaldo especificado no existe o es inválido.");
        }
        try {
            validarChecksumSiExiste(archivoSql);
            if (Files.size(archivoSql) == 0) {
                throw ErrorAplicacionException.validacion("El archivo de respaldo seleccionado está vacío.");
            }
            try (BufferedReader reader = Files.newBufferedReader(archivoSql, StandardCharsets.UTF_8)) {
                boolean tieneFirma = false;
                boolean tienePie = false;
                boolean despuesDelPie = false;
                ResumenSql resumen = new ResumenSql();
                String linea;
                int lineasLeidas = 0;
                StringBuilder sentencia = new StringBuilder();
                while ((linea = reader.readLine()) != null) {
                    lineasLeidas++;
                    if (lineasLeidas <= 25) {
                        if (linea.trim().equals(MAGIC_HEADER)
                                || linea.trim().equals("-- RESPALDO DE BASE DE DATOS - RESTAURANTE 2026")) {
                            tieneFirma = true;
                        }
                    }
                    if (linea.trim().equals(MAGIC_FOOTER) || linea.trim().equals("-- FIN DEL RESPALDO")) {
                        tienePie = true;
                        despuesDelPie = true;
                        continue;
                    }
                    String recortada = linea.trim();
                    if (despuesDelPie && !recortada.isEmpty()) {
                        throw ErrorAplicacionException.validacion("El respaldo contiene contenido después de la marca final y fue rechazado.");
                    }
                    if (recortada.isEmpty() || recortada.startsWith("--") || recortada.startsWith("/*")) {
                        continue;
                    }
                    if (!tieneFirma) {
                        throw ErrorAplicacionException.validacion("El respaldo contiene SQL antes de su encabezado oficial.");
                    }
                    sentencia.append(linea).append('\n');
                    if (recortada.endsWith(";")) {
                        validarSentenciaRespaldo(sentencia.toString(), resumen);
                        sentencia.setLength(0);
                    }
                }
                if (!sentencia.toString().isBlank()) {
                    throw ErrorAplicacionException.validacion("El respaldo termina con una sentencia SQL incompleta.");
                }
                if (!tieneFirma) {
                    throw ErrorAplicacionException.validacion("El archivo seleccionado no tiene la firma oficial de respaldo de Restaurante 2026.");
                }
                if (!tienePie) {
                    throw ErrorAplicacionException.validacion("El archivo de respaldo está incompleto o truncado: no contiene la marca de finalización oficial (" + MAGIC_FOOTER + ").");
                }
                validarCoberturaManifiesto(archivoSql, resumen);
            }
        } catch (IOException ex) {
            throw new DataAccessException("No se pudo leer el archivo de respaldo para su validación: " + ex.getMessage(), ex);
        }
    }

    /** Verifica un respaldo importándolo en un schema aleatorio y desechable del mismo servidor. */
    public void validarRestaurableEnEsquemaTemporal(Path archivoSql) {
        validarArchivoRespaldo(archivoSql);
        String schemaTemporal = "restaurante_restore_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
             Connection con = conexiones.getConnection(); Statement st = con.createStatement()) {
            String schemaOriginal = con.getCatalog();
            if (schemaOriginal == null || schemaOriginal.isBlank()) {
                throw new SQLException("La conexión no informa el schema original para verificación temporal.");
            }
            boolean creado = false;
            try {
                st.executeUpdate("CREATE DATABASE `" + schemaTemporal + "` CHARACTER SET utf8mb4");
                creado = true;
                st.execute("USE `" + schemaTemporal + "`");
                ejecutarScriptSql(con, archivoSql);
                validarContenidoVivo(con, archivoSql);
                validarVersionEsquemaRespaldada(con);
            } catch (SQLException ex) {
                if (!creado && esFaltaDePrivilegiosCreateDatabase(ex)) {
                    LOGGER.warning("El usuario de BD carece de privilegios CREATE DATABASE (error " + ex.getErrorCode()
                            + ", SQLState " + ex.getSQLState() + "); se omite la validación en schema temporal. "
                            + "Se confía en la validación estructural estricta de hash y sentencias DDL.");
                    return;
                }
                throw ex;
            } finally {
                if (creado) {
                    try { st.execute("USE `" + schemaOriginal.replace("`", "``") + "`"); }
                    finally {
                        try { st.executeUpdate("DROP DATABASE IF EXISTS `" + schemaTemporal + "`"); }
                        catch (SQLException e) { LOGGER.warning("No se pudo eliminar schema temporal " + schemaTemporal + ": " + e.getMessage()); }
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("El respaldo no pasó la restauración aislada en schema temporal; no se reemplazará la BD viva: "
                    + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new DataAccessException("No se pudo leer el respaldo durante la validación temporal.", ex);
        } catch (Exception ex) {
            throw new DataAccessException("No se pudo completar la validación aislada del respaldo.", ex);
        }
    }

    private boolean esFaltaDePrivilegiosCreateDatabase(SQLException ex) {
        int code = ex.getErrorCode();
        if (code == 1044 || code == 1045 || code == 1227) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg != null && "42000".equals(ex.getSQLState())) {
            String msgLower = msg.toLowerCase(java.util.Locale.ROOT);
            return msgLower.contains("access denied") || msgLower.contains("denied to user") || msgLower.contains("command denied");
        }
        return false;
    }

    private void validarVersionEsquemaRespaldada(Connection con) throws SQLException {
        if (!tablaExiste(con, "schema_migrations")) return;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_migrations")) {
            if (rs.next() && rs.getInt(1) > infraestructura.MigradorEsquemaJdbc.versionEsquemaActual()) {
                throw new SQLException("El respaldo requiere una versión de aplicación más nueva que la instalada.");
            }
        }
    }

    private boolean tablaExiste(Connection con, String tabla) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void validarSentenciaRespaldo(String sentenciaConPuntoYComa, ResumenSql resumen) {
        String sql = sentenciaConPuntoYComa.trim();
        if (!sql.endsWith(";")) {
            throw ErrorAplicacionException.validacion("Sentencia inválida en el respaldo.");
        }
        sql = sql.substring(0, sql.length() - 1).trim();
        if (contieneSeparadorFueraDeCadena(sql)) {
            throw ErrorAplicacionException.validacion("El respaldo contiene más de una sentencia SQL por bloque y fue rechazado.");
        }
        if (sql.matches("(?is)^ALTER\\s+TABLE.*")) {
            throw ErrorAplicacionException.validacion("El respaldo contiene una sentencia ALTER TABLE no permitida y fue rechazado.");
        }
        boolean permitida = sql.matches("(?is)^SET\\s+FOREIGN_KEY_CHECKS\\s*=\\s*[01]$")
                || sql.matches("(?is)^SET\\s+SQL_MODE\\s*=\\s*'NO_AUTO_VALUE_ON_ZERO'$");
        var drop = SQL_DROP_TABLA_RESPALDO.matcher(sql);
        var create = SQL_CREATE_TABLA_RESPALDO.matcher(sql);
        if (drop.matches()) {
            String tabla = drop.group(1);
            validarTablaPermitida(tabla);
            resumen.eliminadas.add(tabla);
            permitida = true;
        } else if (create.matches()) {
            String tabla = create.group(1);
            validarTablaPermitida(tabla);
            resumen.creadas.add(tabla);
            permitida = true;
        } else {
            var insert = SQL_INSERT_RESPALDO_HEAD.matcher(sql);
            if (insert.matches()) {
                int filas = contarFilasInsert(sql);
                if (filas >= 0) {
                    String tabla = insert.group(0).replaceFirst("(?is)^INSERT\\s+INTO\\s+`([^`]+)`.*$", "$1");
                    validarTablaPermitida(tabla);
                    resumen.filas.merge(tabla, (long) filas, Long::sum);
                    if (insert.group(1) != null) {
                        List<String> columnas = java.util.Arrays.stream(insert.group(1).split(","))
                                .map(String::trim).map(s -> s.replaceAll("^`|`$", "")).toList();
                        List<String> previas = resumen.columnas.putIfAbsent(tabla, columnas);
                        if (previas != null && !previas.equals(columnas)) {
                            throw ErrorAplicacionException.validacion("El respaldo cambia columnas entre lotes de la tabla " + tabla + ".");
                        }
                    }
                    permitida = true;
                }
            }
        }
        if (!permitida) {
            throw ErrorAplicacionException.validacion("El respaldo contiene una sentencia SQL fuera del formato permitido y fue rechazado.");
        }
    }

    private int contarFilasInsert(String sql) {
        var matcher = SQL_INSERT_RESPALDO_HEAD.matcher(sql);
        if (!matcher.matches()) return -1;
        String columnas = matcher.group(1);
        if (columnas != null && !SQL_INSERT_COLUMNS.matcher(columnas).matches()) return -1;
        int cantidadColumnas = columnas == null ? -1 : (int) columnas.chars().filter(c -> c == ',').count() + 1;
        String valores = matcher.group(2);
        int cursor = 0;
        int filas = 0;
        while (cursor < valores.length()) {
            cursor = saltarEspacios(valores, cursor);
            if (cursor >= valores.length() || valores.charAt(cursor++) != '(') return -1;
            boolean dentroCadena = false;
            boolean escapado = false;
            StringBuilder valor = new StringBuilder();
            List<String> valoresTupla = new ArrayList<>();
            boolean cerroTupla = false;
            for (; cursor < valores.length(); cursor++) {
                char c = valores.charAt(cursor);
                if (dentroCadena && escapado) {
                    valor.append(c);
                    escapado = false;
                } else if (dentroCadena && c == '\\') {
                    valor.append(c);
                    escapado = true;
                } else if (c == '\'') {
                    valor.append(c);
                    dentroCadena = !dentroCadena;
                } else if (!dentroCadena && c == ',') {
                    valoresTupla.add(valor.toString().trim());
                    valor.setLength(0);
                } else if (!dentroCadena && c == ')') {
                    valoresTupla.add(valor.toString().trim());
                    cursor++;
                    cerroTupla = true;
                    break;
                } else {
                    valor.append(c);
                }
            }
            if (!cerroTupla || dentroCadena || valoresTupla.isEmpty()
                    || (cantidadColumnas >= 0 && valoresTupla.size() != cantidadColumnas)
                    || valoresTupla.stream().anyMatch(v -> !SQL_LITERAL_RESPALDO.matcher(v).matches())) return -1;
            filas++;
            cursor = saltarEspacios(valores, cursor);
            if (cursor == valores.length()) return filas;
            if (valores.charAt(cursor++) != ',') return -1;
        }
        return -1;
    }

    private void validarCoberturaManifiesto(Path archivo, ResumenSql resumen) throws IOException {
        Properties manifiesto = leerManifiesto(archivo);
        int cantidad;
        try { cantidad = Integer.parseInt(manifiesto.getProperty("tablas.cantidad", "-1")); }
        catch (NumberFormatException ex) { throw ErrorAplicacionException.validacion("La cantidad de tablas del manifiesto no es válida."); }
        if (cantidad <= 0) throw ErrorAplicacionException.validacion("El manifiesto no declara tablas de respaldo.");

        Map<String, TablaExportada> esperadas = new LinkedHashMap<>();
        try {
            for (int i = 0; i < cantidad; i++) {
                String prefijo = "tabla." + i + ".";
                String nombre = manifiesto.getProperty(prefijo + "nombre");
                long filas = Long.parseLong(manifiesto.getProperty(prefijo + "filas", "-1"));
                String columnas64 = manifiesto.getProperty(prefijo + "columnas");
                if (nombre == null || filas < 0 || columnas64 == null) throw new IllegalArgumentException();
                String columnasTexto = new String(Base64.getDecoder().decode(columnas64), StandardCharsets.UTF_8);
                List<String> columnas = columnasTexto.isEmpty() ? List.of() : List.of(columnasTexto.split("\\u001f", -1));
                if (esperadas.put(nombre, new TablaExportada(nombre, columnas, filas)) != null) throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw ErrorAplicacionException.validacion("El manifiesto contiene tablas, columnas o conteos inválidos.");
        }
        Set<String> base = Set.of("config", "usuarios", "platos", "salas", "pedidos", "detalle_pedidos");
        for (String t : esperadas.keySet()) {
            validarTablaPermitida(t);
        }
        for (String t : resumen.eliminadas) {
            validarTablaPermitida(t);
        }
        for (String t : resumen.creadas) {
            validarTablaPermitida(t);
        }
        if (!esperadas.keySet().containsAll(base)
                || !resumen.eliminadas.equals(esperadas.keySet())
                || !resumen.creadas.equals(esperadas.keySet())) {
            throw ErrorAplicacionException.validacion("El respaldo está incompleto: el catálogo de tablas no coincide con su SQL.");
        }
        if (!esperadas.keySet().containsAll(resumen.filas.keySet())) {
            throw ErrorAplicacionException.validacion("El respaldo contiene inserciones para tablas no declaradas en el manifiesto.");
        }
        if (!esperadas.keySet().containsAll(resumen.columnas.keySet())) {
            throw ErrorAplicacionException.validacion("El respaldo contiene columnas para tablas no declaradas en el manifiesto.");
        }
        if (!resumen.creadas.containsAll(resumen.filas.keySet())) {
            throw ErrorAplicacionException.validacion("El respaldo contiene inserciones para tablas no creadas en el archivo.");
        }
        for (TablaExportada tabla : esperadas.values()) {
            if (resumen.filas.getOrDefault(tabla.nombre(), 0L) != tabla.filas()) {
                throw ErrorAplicacionException.validacion("El número de filas SQL no coincide con el manifiesto para " + tabla.nombre() + ".");
            }
            List<String> columnasSql = resumen.columnas.get(tabla.nombre());
            if (tabla.filas() > 0 && !tabla.columnas().equals(columnasSql)) {
                throw ErrorAplicacionException.validacion("Las columnas insertadas no coinciden con el manifiesto para " + tabla.nombre() + ".");
            }
        }
    }

    private int saltarEspacios(String texto, int indice) {
        while (indice < texto.length() && Character.isWhitespace(texto.charAt(indice))) indice++;
        return indice;
    }

    private boolean contieneSeparadorFueraDeCadena(String sql) {
        boolean dentroDeCadena = false;
        boolean escapado = false;
        for (int i = 0; i < sql.length(); i++) {
            char caracter = sql.charAt(i);
            if (dentroDeCadena && escapado) {
                escapado = false;
                continue;
            }
            if (dentroDeCadena && caracter == '\\') {
                escapado = true;
                continue;
            }
            if (caracter == '\'') {
                dentroDeCadena = !dentroDeCadena;
            } else if (!dentroDeCadena && caracter == ';') {
                return true;
            }
        }
        return false;
    }

    /**
     * Restaura una base de datos a partir de un archivo .sql generado previamente.
     * Ejecuta una copia de seguridad previa de emergencia y aplica política Fail-Fast con Auto-Recuperación.
     * Si la restauración falla (dado que DDL en MySQL ejecuta commits implícitos), se auto-recupera desde la copia previa.
     * Garantiza el restablecimiento de FOREIGN_KEY_CHECKS y SQL_MODE en un bloque finally.
     *
     * @param archivoSql Ruta al archivo SQL.
     * @return Ruta de la copia de seguridad previa de emergencia creada antes de restaurar.
     */
    public Path restaurarRespaldo(Path archivoSql) {
        validarArchivoRespaldo(archivoSql);
        validarRestaurableEnEsquemaTemporal(archivoSql);
        Path destino = archivoSql.toAbsolutePath().normalize();
        String checksumDestino = checksumHex(destino);
        infraestructura.DiarioRecuperacionBD.Operacion operacion;
        try {
            if (diarioRecuperacion.leer() != null) {
                throw new IOException("Hay una operación de restauración pendiente; debe recuperarse al iniciar.");
            }
            operacion = diarioRecuperacion.nueva(destino.toString(), checksumDestino);
            diarioRecuperacion.guardar(operacion);
        } catch (Exception ex) {
            throw new DataAccessException("No se pudo registrar de forma durable la restauración; la BD no fue modificada.", ex);
        }

        String idControl = "RESTAURACION:" + operacion.id();
        Path respaldoPrevio = null;
        boolean mantenimientoIniciado = false;
        try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
             Connection control = conexiones.getConnection()) {
            infraestructura.ControlMantenimientoBD.asegurarTablaControl(control);
            if (!"OPERATIVO".equalsIgnoreCase(infraestructura.ControlMantenimientoBD.estado(control))) {
                diarioRecuperacion.eliminar();
                throw new SQLException("La BD ya está en mantenimiento; la restauración no puede reemplazar otra operación.");
            }
            infraestructura.ControlMantenimientoBD.iniciarMantenimiento(control, "RESTAURACION", operacion.id());
            mantenimientoIniciado = true;
            operacion = actualizarDiario(operacion, "MANTENIMIENTO_SIN_RESPALDO", null, null);

            respaldoPrevio = crearRespaldoPreRestauracion();
            if (respaldoPrevio == null || !Files.isRegularFile(respaldoPrevio)) {
                throw new IOException("No se pudo crear el respaldo previo obligatorio.");
            }
            validarArchivoRespaldo(respaldoPrevio);
            validarRestaurableEnEsquemaTemporal(respaldoPrevio);
            operacion = actualizarDiario(operacion, "RESPALDO_COMPLETO",
                    respaldoPrevio.toAbsolutePath().normalize().toString(), checksumHex(respaldoPrevio));

            operacion = actualizarDiario(operacion, "APLICANDO", operacion.respaldoPrevio(), operacion.checksumPrevio());
            importarYValidar(destino);
            operacion = actualizarDiario(operacion, "DESTINO_VALIDADO",
                    operacion.respaldoPrevio(), operacion.checksumPrevio());
            infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
            diarioRecuperacion.eliminar();
            LOGGER.info("Restauración verificada y completada: " + destino);
        } catch (Exception fallo) {
            if (!mantenimientoIniciado) {
                try { diarioRecuperacion.eliminar(); } catch (IOException limpiar) { fallo.addSuppressed(limpiar); }
                throw new DataAccessException("Restauración abortada antes de modificar la BD: " + mensaje(fallo), fallo);
            }
            if ("DESTINO_VALIDADO".equals(operacion.fase())) {
                throw new DataAccessException("El respaldo destino fue validado, pero no se pudo cerrar la operación. La BD queda bloqueada para completar la recuperación al iniciar.", fallo);
            }
            if (respaldoPrevio == null || !Files.isRegularFile(respaldoPrevio)) {
                try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
                     Connection control = conexiones.getConnection()) {
                    infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
                    diarioRecuperacion.eliminar();
                } catch (Exception liberar) {
                    fallo.addSuppressed(liberar);
                }
                throw new DataAccessException("Restauración cancelada antes del reemplazo. La BD permaneció intacta; causa: " + mensaje(fallo), fallo);
            }
            try {
                operacion = actualizarDiario(operacion, "RECUPERANDO",
                        respaldoPrevio.toAbsolutePath().normalize().toString(), checksumHex(respaldoPrevio));
                validarArchivoRespaldo(respaldoPrevio);
                importarYValidar(respaldoPrevio);
                try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
                     Connection control = conexiones.getConnection()) {
                    infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
                }
                diarioRecuperacion.eliminar();
                infraestructura.MigradorEsquemaJdbc.invalidarCacheEsquema();
                try {
                    infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
                } catch (Exception ex) {
                    LOGGER.warning("Fallo al sincronizar esquema tras auto-recuperar respaldo previo: " + mensaje(ex));
                }
                throw new DataAccessException("La restauración falló; la BD fue recuperada desde el respaldo previo "
                        + respaldoPrevio.getFileName() + ". Causa original: " + mensaje(fallo), fallo);
            } catch (DataAccessException recuperacionExitosa) {
                if (recuperacionExitosa.getMessage() != null
                        && recuperacionExitosa.getMessage().startsWith("La restauración falló; la BD fue recuperada")) {
                    throw recuperacionExitosa;
                }
                fallo.addSuppressed(recuperacionExitosa);
            } catch (Exception recuperacionFallida) {
                fallo.addSuppressed(recuperacionFallida);
            }
            throw new DataAccessException("Falló la restauración y no se pudo confirmar la recuperación. La BD permanece en MANTENIMIENTO y el diario "
                    + diarioRecuperacion.getArchivo() + " conserva los pasos para reintentar al iniciar. Causa: " + mensaje(fallo), fallo);
        }

        infraestructura.MigradorEsquemaJdbc.invalidarCacheEsquema();
        try {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        } catch (Exception ex) {
            throw new DataAccessException("El respaldo fue restaurado, pero falló la sincronización del esquema: " + mensaje(ex), ex);
        }
        return respaldoPrevio;
    }

    /** Recupera una operación interrumpida antes de abrir login o ejecutar migraciones. */
    public void recuperarOperacionInterrumpida() {
        final infraestructura.DiarioRecuperacionBD.Operacion inicial;
        try {
            inicial = diarioRecuperacion.leer();
        } catch (IOException ex) {
            throw new DataAccessException("El diario de restauración está dañado; la aplicación no abrirá la BD.", ex);
        }
        if (inicial == null) return;

        boolean sincronizarEsquema = false;
        String idControl = "RESTAURACION:" + inicial.id();
        try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
             Connection control = conexiones.getConnection()) {
            infraestructura.ControlMantenimientoBD.asegurarTablaControl(control);
            String estado = infraestructura.ControlMantenimientoBD.estado(control);
            String operacionControl = infraestructura.ControlMantenimientoBD.operacionActual(control);
            if ("OPERATIVO".equalsIgnoreCase(estado)
                    && ("INICIANDO".equals(inicial.fase()) || "DESTINO_VALIDADO".equals(inicial.fase()))) {
                if ("DESTINO_VALIDADO".equals(inicial.fase())) {
                    validarDestinoVivo(inicial.destino(), inicial.checksumDestino());
                    sincronizarEsquema = true;
                }
                diarioRecuperacion.eliminar();
            } else if ("MANTENIMIENTO".equalsIgnoreCase(estado) && !idControl.equals(operacionControl)) {
                throw new SQLException("El diario no coincide con la operación de mantenimiento activa (" + operacionControl + ").");
            } else {
                switch (inicial.fase()) {
                    case "INICIANDO", "MANTENIMIENTO_SIN_RESPALDO", "RESPALDO_COMPLETO" -> {
                        if ("MANTENIMIENTO".equalsIgnoreCase(estado)) {
                            infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
                        }
                        diarioRecuperacion.eliminar();
                        LOGGER.info("Se cerró de forma segura una restauración interrumpida antes de ejecutar DDL.");
                    }
                    case "DESTINO_VALIDADO" -> {
                        validarDestinoVivo(inicial.destino(), inicial.checksumDestino());
                        if ("MANTENIMIENTO".equalsIgnoreCase(estado)) {
                            infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
                        }
                        diarioRecuperacion.eliminar();
                        sincronizarEsquema = true;
                    }
                    case "APLICANDO", "RECUPERANDO" -> {
                        Path previo = rutaDiario(inicial.respaldoPrevio());
                        validarArchivoRespaldo(previo);
                        validarChecksumEsperado(previo, inicial.checksumPrevio());
                        if ("OPERATIVO".equalsIgnoreCase(estado)) {
                            infraestructura.ControlMantenimientoBD.iniciarMantenimiento(control, "RESTAURACION", inicial.id());
                        }
                        actualizarDiario(inicial, "RECUPERANDO", previo.toString(), inicial.checksumPrevio());
                        importarYValidar(previo);
                        infraestructura.ControlMantenimientoBD.terminarMantenimiento(control, idControl);
                        diarioRecuperacion.eliminar();
                        LOGGER.warning("Se restauró automáticamente el respaldo previo de una operación de restauración interrumpida.");
                        sincronizarEsquema = true;
                    }
                    default -> throw new SQLException("Fase de restauración desconocida en el diario: " + inicial.fase());
                }
            }
        } catch (Exception ex) {
            throw new DataAccessException("No se pudo recuperar la operación de restauración pendiente. La aplicación queda bloqueada; revise "
                    + diarioRecuperacion.getArchivo() + ". " + mensaje(ex), ex);
        }

        if (sincronizarEsquema) {
            infraestructura.MigradorEsquemaJdbc.invalidarCacheEsquema();
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        }
    }

    private infraestructura.DiarioRecuperacionBD.Operacion actualizarDiario(
            infraestructura.DiarioRecuperacionBD.Operacion actual, String fase, String respaldo, String checksum) throws IOException {
        var siguiente = new infraestructura.DiarioRecuperacionBD.Operacion(actual.id(), fase,
                respaldo, checksum, actual.destino(), actual.checksumDestino(), actual.creadaEn());
        diarioRecuperacion.guardar(siguiente);
        return siguiente;
    }

    private void importarYValidar(Path archivo) throws SQLException, IOException {
        validarArchivoRespaldo(archivo);
        try (AutoCloseable interno = infraestructura.ControlMantenimientoBD.permitirOperacionInterna();
             Connection con = conexiones.getConnection()) {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            String sqlMode = null;
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT @@SESSION.sql_mode")) {
                if (rs.next()) sqlMode = rs.getString(1);
            }
            try {
                ejecutarScriptSql(con, archivo);
                try (Statement limpiar = con.createStatement()) {
                    limpiar.executeUpdate("DELETE FROM login_intentos");
                }
                infraestructura.MigradorEsquemaJdbc.asegurarIntegridadIntentosLogin(con);
                validarContenidoVivo(con, archivo);
            } finally {
                try (Statement reset = con.createStatement()) {
                    reset.execute("SET FOREIGN_KEY_CHECKS = 1");
                    if (sqlMode != null) reset.execute("SET SESSION sql_mode = '" + sqlMode.replace("'", "\\'") + "'");
                } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "No se pudieron restaurar variables de sesión tras importar respaldo.", ex);
                }
                try { con.setAutoCommit(autoCommit); } catch (SQLException ex) {
                    LOGGER.log(Level.WARNING, "No se pudo restaurar autoCommit tras importar respaldo.", ex);
                }
            }
        } catch (SQLException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SQLException("No se pudo mantener el modo interno de restauración.", ex);
        }
    }

    private void validarDestinoVivo(String destino, String checksum) throws Exception {
        Path archivo = rutaDiario(destino);
        validarArchivoRespaldo(archivo);
        validarChecksumEsperado(archivo, checksum);
        try (Connection con = conexiones.getConnection()) { validarContenidoVivo(con, archivo); }
    }

    private void validarContenidoVivo(Connection con, Path archivo) throws SQLException, IOException {
        Properties manifiesto = leerManifiesto(archivo);
        int cantidad = Integer.parseInt(manifiesto.getProperty("tablas.cantidad", "-1"));
        DatabaseMetaData meta = con.getMetaData();
        String catalogo = con.getCatalog();
        for (int i = 0; i < cantidad; i++) {
            String prefijo = "tabla." + i + ".";
            String tabla = manifiesto.getProperty(prefijo + "nombre");
            long filasEsperadas = Long.parseLong(manifiesto.getProperty(prefijo + "filas", "-1"));
            String textoColumnas = new String(Base64.getDecoder().decode(manifiesto.getProperty(prefijo + "columnas", "")), StandardCharsets.UTF_8);
            List<String> columnasEsperadas = textoColumnas.isEmpty() ? List.of() : List.of(textoColumnas.split("\u001f", -1));
            Set<String> columnasVivas = new HashSet<>();
            try (ResultSet rs = meta.getColumns(catalogo, null, tabla, "%")) {
                while (rs.next()) columnasVivas.add(rs.getString("COLUMN_NAME"));
            }
            if (!columnasVivas.containsAll(columnasEsperadas)) {
                throw new SQLException("La tabla restaurada " + tabla + " no contiene las columnas registradas en el manifiesto.");
            }
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + tabla.replace("`", "``") + "`")) {
                if (!rs.next() || rs.getLong(1) != filasEsperadas) {
                    throw new SQLException("El conteo de filas de " + tabla + " no coincide con el manifiesto.");
                }
            }
        }
    }

    private Path rutaDiario(String texto) throws IOException {
        if (texto == null || texto.isBlank()) throw new IOException("El diario no contiene una ruta de respaldo.");
        return Path.of(texto).toAbsolutePath().normalize();
    }

    private void validarChecksumEsperado(Path archivo, String esperado) throws IOException, NoSuchAlgorithmException {
        if (esperado == null || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                checksumHex(archivo).getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("El checksum del archivo indicado por el diario no coincide.");
        }
    }

    private String checksumHex(Path archivo) {
        try { return java.util.HexFormat.of().formatHex(calcularSha256(archivo)); }
        catch (IOException | NoSuchAlgorithmException ex) { throw new DataAccessException("No se pudo calcular SHA-256 para " + archivo, ex); }
    }

    private String mensaje(Throwable ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void ejecutarScriptSql(Connection con, Path script) throws SQLException, IOException {
        try (Statement st = con.createStatement();
             BufferedReader reader = Files.newBufferedReader(script, StandardCharsets.UTF_8)) {

            st.execute("SET FOREIGN_KEY_CHECKS = 0;");
            st.execute("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';");

            StringBuilder sb = new StringBuilder();
            String linea;
            int numeroSentencia = 0;
            boolean encontroPie = false;

            while ((linea = reader.readLine()) != null) {
                if (linea.contains(MAGIC_FOOTER) || linea.contains("-- FIN DEL RESPALDO")) {
                    encontroPie = true;
                }
                String recortada = linea.trim();
                if (recortada.isEmpty() || recortada.startsWith("--") || recortada.startsWith("/*")) {
                    continue;
                }
                sb.append(linea).append("\n");
                if (recortada.endsWith(";")) {
                    String sql = sb.toString().trim();
                    if (sql.endsWith(";")) {
                        sql = sql.substring(0, sql.length() - 1);
                    }
                    if (!sql.isBlank()) {
                        numeroSentencia++;
                        try {
                            st.execute(sql);
                        } catch (SQLException ex) {
                            String resumen = sql.length() > 80 ? sql.substring(0, 80) + "..." : sql;
                            throw new SQLException("Fallo en sentencia #" + numeroSentencia + " [" + resumen + "]: " + ex.getMessage(), ex);
                        }
                    }
                    sb.setLength(0);
                }
            }

            if (!sb.toString().trim().isEmpty()) {
                throw new SQLException("El archivo de respaldo está truncado o incompleto: quedó una sentencia SQL sin finalizar al final del archivo.");
            }
            if (!encontroPie) {
                throw new SQLException("El archivo de respaldo está truncado: no contiene la marca de fin de archivo esperada.");
            }

            st.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }

    private void escribirEncabezado(BufferedWriter writer, String timestamp) throws IOException {
        writer.write("-- ========================================================\n");
        writer.write("-- RESPALDO DE BASE DE DATOS - RESTAURANTE 2026\n");
        writer.write(MAGIC_HEADER + "\n");
        writer.write("-- Fecha de generación: " + timestamp + "\n");
        writer.write("-- Generado automáticamente mediante JDBC Pure Engine\n");
        writer.write("-- ========================================================\n\n");
        writer.write("SET FOREIGN_KEY_CHECKS = 0;\n");
        writer.write("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';\n\n");
    }

    private void escribirPie(BufferedWriter writer) throws IOException {
        writer.write("\nSET FOREIGN_KEY_CHECKS = 1;\n");
        writer.write(MAGIC_FOOTER + "\n");
    }

    private List<String> listarTablas(Connection con) throws SQLException {
        List<String> tablas = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")) {
            while (rs.next()) {
                String nombreTabla = rs.getString("TABLE_NAME");
                if (nombreTabla == null) continue;
                String t = nombreTabla.trim().toLowerCase(java.util.Locale.ROOT);
                if (TABLAS_PROTEGIDAS_INFRAESTRUCTURA.contains(t) || !TABLAS_PERMITIDAS_RESPALDO.contains(t)) {
                    continue;
                }
                tablas.add(nombreTabla);
            }
        }
        return tablas;
    }

    private TablaExportada exportarTabla(Connection con, BufferedWriter writer, String tabla) throws SQLException, IOException {
        writer.write("\n--\n-- Estructura y datos para la tabla `" + tabla + "`\n--\n\n");
        writer.write("DROP TABLE IF EXISTS `" + tabla + "`;\n");

        // Obtener DDL (SHOW CREATE TABLE)
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + tabla + "`")) {
            if (rs.next()) {
                String ddl = rs.getString(2);
                writer.write(ddl + ";\n\n");
            }
        }

        List<Integer> indicesInsertables = new ArrayList<>();
        List<String> nombresInsertables = new ArrayList<>();
        try (ResultSet columnas = con.getMetaData().getColumns(con.getCatalog(), null, tabla, "%")) {
            while (columnas.next()) {
                String generado = columnas.getString("IS_GENERATEDCOLUMN");
                if (!"YES".equalsIgnoreCase(generado)) {
                    indicesInsertables.add(columnas.getInt("ORDINAL_POSITION"));
                    nombresInsertables.add(columnas.getString("COLUMN_NAME"));
                }
            }
        }
        if (nombresInsertables.isEmpty()) {
            throw new SQLException("La tabla " + tabla + " no tiene columnas respaldables.");
        }
        String listaColumnas = nombresInsertables.stream().map(n -> "`" + n.replace("`", "``") + "`")
                .collect(java.util.stream.Collectors.joining(", "));

        // Los campos generados no se insertan: MySQL los recalcula al restaurar.
        String sqlSelect = "SELECT * FROM `" + tabla + "`";
        long cantidadFilas = 0;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sqlSelect)) {

            ResultSetMetaData rsMeta = rs.getMetaData();
            int columnas = rsMeta.getColumnCount();

            List<String> filas = new ArrayList<>();
            while (rs.next()) {
                StringBuilder fila = new StringBuilder("(");
                for (int j = 0; j < indicesInsertables.size(); j++) {
                    if (j > 0) fila.append(", ");
                    int i = indicesInsertables.get(j);
                    Object obj = rs.getObject(i);
                    if (obj == null) {
                        fila.append("NULL");
                    } else if (obj instanceof Number) {
                        fila.append(obj);
                    } else if (obj instanceof Boolean) {
                        fila.append((Boolean) obj ? "1" : "0");
                    } else {
                        String str = rs.getString(i);
                        fila.append("'").append(escaparSql(str)).append("'");
                    }
                }
                fila.append(")");
                filas.add(fila.toString());
                cantidadFilas++;

                if (filas.size() >= 100) {
                    escribirLoteInsert(writer, tabla, listaColumnas, filas);
                    filas.clear();
                }
            }

            if (!filas.isEmpty()) {
                escribirLoteInsert(writer, tabla, listaColumnas, filas);
            }
        }
        return new TablaExportada(tabla, List.copyOf(nombresInsertables), cantidadFilas);
    }

    private void escribirLoteInsert(BufferedWriter writer, String tabla, String listaColumnas, List<String> filas) throws IOException {
        writer.write("INSERT INTO `" + tabla + "` (" + listaColumnas + ") VALUES\n");
        for (int i = 0; i < filas.size(); i++) {
            writer.write(filas.get(i));
            if (i < filas.size() - 1) {
                writer.write(",\n");
            } else {
                writer.write(";\n");
            }
        }
    }

    private String escaparSql(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\r", "\\r")
                  .replace("\n", "\\n");
    }

    private void limpiarRespaldosAntiguos(int diasRetencion) {
        try {
            LocalDate fechaLimite = LocalDate.now().minusDays(diasRetencion);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directorioRespaldos, "respaldo_restaurante_*.sql")) {
                for (Path archivo : stream) {
                    String nombre = archivo.getFileName().toString();
                    if (nombre.length() >= 28) {
                        try {
                            String fechaParte = nombre.substring(20, 28);
                            LocalDate fechaArchivo = LocalDate.parse(fechaParte, FORMATO_FECHA_DIA);
                            if (fechaArchivo.isBefore(fechaLimite)) {
                                Files.deleteIfExists(archivo);
                                Files.deleteIfExists(checksumPath(archivo));
                                LOGGER.info("Respaldo antiguo depurado por retención: " + nombre);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error al depurar respaldos antiguos: " + ex.getMessage());
        }
    }
}
