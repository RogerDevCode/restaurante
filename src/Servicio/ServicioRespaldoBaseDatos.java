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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private static final Pattern SQL_DROP_TABLA_RESPALDO = Pattern.compile("(?is)^DROP\\s+TABLE\\s+IF\\s+EXISTS\\s+`[^`]+`$");
    private static final Pattern SQL_CREATE_TABLA_RESPALDO = Pattern.compile(
            "(?is)^CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`[^`]+`\\s*\\(.*\\)\\s+ENGINE\\s*=.*$");

    private final ProveedorConexionJdbc conexiones;
    private final Path directorioRespaldos;

    public ServicioRespaldoBaseDatos() {
        this(new ProveedorConexionJdbc(), DIRECTORIO_RESPALDOS_DEFAULT);
    }

    public ServicioRespaldoBaseDatos(ProveedorConexionJdbc conexiones) {
        this(conexiones, DIRECTORIO_RESPALDOS_DEFAULT);
    }

    public ServicioRespaldoBaseDatos(ProveedorConexionJdbc conexiones, Path directorioRespaldos) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones no puede ser nulo.");
        this.directorioRespaldos = directorioRespaldos != null ? directorioRespaldos : DIRECTORIO_RESPALDOS_DEFAULT;
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

        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            int aislamientoPrevio = con.getTransactionIsolation();

            try {
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.setAutoCommit(false);

                validarCoberturaRespaldable(con);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(temporal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        StandardCharsets.UTF_8))) {
                    escribirEncabezado(writer, timestamp);

                    List<String> tablas = listarTablas(con);
                    for (String tabla : tablas) {
                        exportarTabla(con, writer, tabla);
                    }

                    escribirPie(writer);
                    writer.flush();
                }

                con.commit();
                validarArchivoRespaldo(temporal);
                Files.move(temporal, archivoSql);
                crearChecksum(archivoSql);
                LOGGER.info("Respaldo de base de datos generado exitosamente: " + archivoSql.toAbsolutePath());
                return archivoSql;

            } catch (SQLException | IOException ex) {
                con.rollback();
                try {
                    Files.deleteIfExists(temporal);
                    Files.deleteIfExists(archivoSql);
                    Files.deleteIfExists(checksumPath(archivoSql));
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

    private void crearChecksum(Path archivo) throws IOException {
        try {
            String checksum = java.util.HexFormat.of().formatHex(calcularSha256(archivo));
            Files.writeString(checksumPath(archivo), checksum + "  " + archivo.getFileName() + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 no está disponible para verificar el respaldo.", ex);
        }
    }

    private void validarChecksumSiExiste(Path archivo) {
        Path checksum = checksumPath(archivo);
        if (!Files.exists(checksum)) return;
        try {
            String esperado = Files.readString(checksum, StandardCharsets.UTF_8).trim().split("\\s+", 2)[0];
            String actual = java.util.HexFormat.of().formatHex(calcularSha256(archivo));
            if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII))) {
                throw ErrorAplicacionException.validacion("El checksum SHA-256 del respaldo no coincide.");
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new DataAccessException("No se pudo verificar el checksum del respaldo.", ex);
        }
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
                if (stream.iterator().hasNext()) {
                    LOGGER.fine("Ya existe un respaldo para la fecha de hoy. Se omite respaldo automático.");
                    return null;
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
                        validarSentenciaRespaldo(sentencia.toString());
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
            }
        } catch (IOException ex) {
            throw new DataAccessException("No se pudo leer el archivo de respaldo para su validación: " + ex.getMessage(), ex);
        }
    }

    private void validarSentenciaRespaldo(String sentenciaConPuntoYComa) {
        String sql = sentenciaConPuntoYComa.trim();
        if (!sql.endsWith(";")) {
            throw ErrorAplicacionException.validacion("Sentencia inválida en el respaldo.");
        }
        sql = sql.substring(0, sql.length() - 1).trim();
        if (contieneSeparadorFueraDeCadena(sql)) {
            throw ErrorAplicacionException.validacion("El respaldo contiene más de una sentencia SQL por bloque y fue rechazado.");
        }
        boolean permitida = sql.matches("(?is)^SET\\s+FOREIGN_KEY_CHECKS\\s*=\\s*[01]$")
                || sql.matches("(?is)^SET\\s+SQL_MODE\\s*=\\s*'NO_AUTO_VALUE_ON_ZERO'$")
                || SQL_DROP_TABLA_RESPALDO.matcher(sql).matches()
                || SQL_CREATE_TABLA_RESPALDO.matcher(sql).matches()
                || esInsertRespaldo(sql);
        if (!permitida) {
            throw ErrorAplicacionException.validacion("El respaldo contiene una sentencia SQL fuera del formato permitido y fue rechazado.");
        }
    }

    private boolean esInsertRespaldo(String sql) {
        var matcher = SQL_INSERT_RESPALDO_HEAD.matcher(sql);
        if (!matcher.matches()) return false;
        String columnas = matcher.group(1);
        if (columnas != null && !SQL_INSERT_COLUMNS.matcher(columnas).matches()) return false;
        int cantidadColumnas = columnas == null ? -1 : (int) columnas.chars().filter(c -> c == ',').count() + 1;
        String valores = matcher.group(2);
        int cursor = 0;
        while (cursor < valores.length()) {
            cursor = saltarEspacios(valores, cursor);
            if (cursor >= valores.length() || valores.charAt(cursor++) != '(') return false;
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
                    || valoresTupla.stream().anyMatch(v -> !SQL_LITERAL_RESPALDO.matcher(v).matches())) return false;
            cursor = saltarEspacios(valores, cursor);
            if (cursor == valores.length()) return true;
            if (valores.charAt(cursor++) != ',') return false;
        }
        return false;
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

        Path preRespaldo;
        try {
            preRespaldo = crearRespaldoPreRestauracion();
            if (preRespaldo == null || !Files.exists(preRespaldo)) {
                throw new IOException("El archivo de respaldo previo de emergencia no pudo ser creado en disco.");
            }
            LOGGER.info("Copia de seguridad previa creada con éxito en: " + preRespaldo.toAbsolutePath());
        } catch (Exception ex) {
            throw new DataAccessException("Restauración abortada por seguridad: Es obligatorio contar con un respaldo previo de la base viva antes de restaurar. Detalle: " + ex.getMessage(), ex);
        }

        LOGGER.info("Iniciando restauración fail-fast de base de datos desde: " + archivoSql.toAbsolutePath());

        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);

            String sqlModePrevio = null;
            try (Statement stMode = con.createStatement();
                 ResultSet rsMode = stMode.executeQuery("SELECT @@SESSION.sql_mode")) {
                if (rsMode.next()) {
                    sqlModePrevio = rsMode.getString(1);
                }
            } catch (SQLException ignored) {
            }

            try {
                ejecutarScriptSql(con, archivoSql);
                con.commit();
                LOGGER.info("Restauración de base de datos completada exitosamente.");
                return preRespaldo;

            } catch (Exception ex) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                LOGGER.log(Level.SEVERE, "Fallo durante la restauración. Iniciando auto-recuperación desde la copia de seguridad previa: " + preRespaldo.getFileName(), ex);
                Exception falloRecuperacion = null;
                try {
                    ejecutarScriptSql(con, preRespaldo);
                    con.commit();
                    LOGGER.info("Auto-recuperación completada: La base de datos viva fue restaurada a su estado previo.");
                } catch (Exception recEx) {
                    falloRecuperacion = recEx;
                    LOGGER.log(Level.SEVERE, "ERROR CRÍTICO: Falló la auto-recuperación desde la copia previa: " + recEx.getMessage(), recEx);
                }
                if (falloRecuperacion == null) {
                    throw new DataAccessException("La restauración falló; la base de datos fue recuperada desde la copia previa (" + preRespaldo.getFileName() + "). Causa: " + ex.getMessage(), ex);
                }
                DataAccessException error = new DataAccessException(
                        "Falló la restauración y también la recuperación automática desde " + preRespaldo.getFileName()
                        + ". La base puede haber quedado parcialmente restaurada; se requiere recuperación manual.", ex);
                error.addSuppressed(falloRecuperacion);
                throw error;
            } finally {
                try (Statement stReset = con.createStatement()) {
                    stReset.execute("SET FOREIGN_KEY_CHECKS = 1;");
                    if (sqlModePrevio != null) {
                        stReset.execute("SET SESSION sql_mode = '" + sqlModePrevio.replace("'", "\\'") + "';");
                    }
                } catch (Exception ignored) {
                }
                try {
                    con.setAutoCommit(autoCommitPrevio);
                } catch (Exception ignored) {
                }
            }

        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo restaurar el respaldo de la base de datos: " + ex.getMessage(), ex);
        }
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
                tablas.add(nombreTabla);
            }
        }
        return tablas;
    }

    private void exportarTabla(Connection con, BufferedWriter writer, String tabla) throws SQLException, IOException {
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

                if (filas.size() >= 100) {
                    escribirLoteInsert(writer, tabla, listaColumnas, filas);
                    filas.clear();
                }
            }

            if (!filas.isEmpty()) {
                escribirLoteInsert(writer, tabla, listaColumnas, filas);
            }
        }
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
