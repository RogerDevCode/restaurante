package infraestructura;

import infraestructura.seguridad.VerificadorIntegridadLogs;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handler JUL asíncrono e inmutable con hash-chaining criptográfico (HMAC-SHA256).
 * Diseñado conforme a NIST SP 800-92 y optimizado para Java 21 Project Loom:
 * - Desacopla la publicación de registros a una cola en memoria no bloqueante para evitar Carrier Thread Pinning.
 * - Un worker daemon dedicado calcula el HMAC encadenado y escribe en disco con formato estructurado monolínea.
 * - Protege los archivos físicos con permisos restringidos de sistema operativo (0600 en Linux).
 */
public final class ArchivoLogDiario extends Handler {
    private static final Pattern ARCHIVO_DIARIO = Pattern.compile("restaurante-(?:emergencia-)?(\\d{4}-\\d{2}-\\d{2})\\.log");
    private static final String HASH_GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final int CAPACIDAD_COLA = 10_000;

    private static final LogRecord VENENO_CIERRE = new LogRecord(java.util.logging.Level.OFF, "__SHUTDOWN__");

    private final Path directorio;
    private final BlockingQueue<LogRecord> cola;
    private final Thread hiloEscritor;
    private final AtomicBoolean activo;
    private final byte[] claveAuditoria;

    // Estado interno del hilo escritor
    private BufferedWriter escritor;
    private LocalDate fechaAbierta;
    private long secuenciaActual = 0;
    private String ultimoHash = HASH_GENESIS.substring(0, 16);

    public ArchivoLogDiario() throws IOException {
        String configurado = System.getProperty("restaurante.logs.dir", "logs");
        directorio = Paths.get(configurado).toAbsolutePath().normalize();
        Files.createDirectories(directorio);

        this.claveAuditoria = obtenerOCrearClaveAuditoria(directorio);
        this.cola = new LinkedBlockingQueue<>(CAPACIDAD_COLA);
        this.activo = new AtomicBoolean(true);

        setFormatter(new FormateadorLogSellado());
        setErrorManager(new CanalEmergencia(directorio));

        // Inicializar worker thread dedicado
        this.hiloEscritor = new Thread(this::bucleEscritura, "log-audit-writer");
        this.hiloEscritor.setDaemon(true);
        this.hiloEscritor.start();

        purgarAntiguos(LocalDate.now());
    }

    @Override
    public void publish(LogRecord registro) {
        if (registro == null || !isLoggable(registro) || !activo.get()) {
            return;
        }
        // Ofrecer a la cola sin bloquear hilos portadores (Virtual Threads de Loom)
        if (!cola.offer(registro)) {
            reportError("Cola de auditoría de logs saturada. Se descartó un registro.",
                    null, ErrorManager.WRITE_FAILURE);
        }
    }

    @Override
    public void flush() {
        // Enviar un marcador y esperar a que la cola se vacíe
        long tiempoLimite = System.currentTimeMillis() + 2000L;
        while (!cola.isEmpty() && System.currentTimeMillis() < tiempoLimite) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        synchronized (this) {
            if (escritor != null) {
                try {
                    escritor.flush();
                } catch (IOException | SecurityException ex) {
                    reportError("No se pudo vaciar el archivo diario de log", ex, ErrorManager.FLUSH_FAILURE);
                }
            }
        }
    }

    @Override
    public void close() throws SecurityException {
        if (activo.compareAndSet(true, false)) {
            cola.offer(VENENO_CIERRE);
            try {
                hiloEscritor.join(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                if (escritor != null) {
                    try {
                        escritor.flush();
                        escritor.close();
                    } catch (IOException | SecurityException ex) {
                        reportError("No se pudo cerrar el archivo diario de log", ex, ErrorManager.CLOSE_FAILURE);
                    } finally {
                        escritor = null;
                    }
                }
            }
        }
    }

    private void bucleEscritura() {
        while (activo.get() || !cola.isEmpty()) {
            try {
                LogRecord registro = cola.poll(200, TimeUnit.MILLISECONDS);
                if (registro == null || registro == VENENO_CIERRE) {
                    continue;
                }
                procesarYGrabarRegistro(registro);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                reportError("Error procesando registro en hilo escritor de auditoría", ex, ErrorManager.WRITE_FAILURE);
            }
        }
    }

    private synchronized void procesarYGrabarRegistro(LogRecord registro) {
        try {
            LocalDate fechaRegistro = registro.getInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate hoy = LocalDate.now();
            LocalDate fechaArchivo = fechaRegistro.isBefore(fechaLimite(hoy)) || fechaRegistro.isAfter(hoy)
                    ? hoy : fechaRegistro;

            if (!fechaArchivo.equals(fechaAbierta) || escritor == null) {
                abrirArchivo(fechaArchivo);
            }

            secuenciaActual++;
            Instant instante = registro.getInstant();
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(instante);
            String nivel = registro.getLevel().getName();

            // Extraer y formatear mensaje atómico (monolínea)
            String cuerpoMensaje = getFormatter().format(registro);
            String mensajeSanitizado = cuerpoMensaje.replace("\r", "").replace("\n", "\\n").trim();

            String hashPrevio = ultimoHash;
            String payload = secuenciaActual + "|" + timestamp + "|" + nivel + "|" + hashPrevio + "|" + mensajeSanitizado;
            String hmac = VerificadorIntegridadLogs.computarHmac16(claveAuditoria, payload);

            String lineaSellada = String.format("[SEQ:%d | %s | %s | PREV:%s | HMAC:%s] %s%n",
                    secuenciaActual, timestamp, nivel, hashPrevio, hmac, mensajeSanitizado);

            escritor.write(lineaSellada);
            escritor.flush();
            ultimoHash = hmac;

        } catch (IOException ex) {
            reportError("No se pudo escribir o rotar el archivo diario de log", ex, ErrorManager.WRITE_FAILURE);
        } catch (RuntimeException ex) {
            reportError("No se pudo escribir o rotar el archivo diario de log", ex, ErrorManager.WRITE_FAILURE);
        }
    }

    private void abrirArchivo(LocalDate fecha) throws IOException {
        if (escritor != null) {
            escritor.flush();
            escritor.close();
        }
        Files.createDirectories(directorio);
        purgarAntiguos(LocalDate.now());

        Path archivo = directorio.resolve("restaurante-" + fecha + ".log");
        boolean esNuevo = !Files.exists(archivo) || Files.size(archivo) == 0;

        if (esNuevo) {
            secuenciaActual = 0;
            ultimoHash = HASH_GENESIS.substring(0, 16);
        } else {
            recuperarUltimoEstado(archivo);
        }

        escritor = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        fechaAbierta = fecha;
        aplicarPermisosRestringidos(archivo);
    }

    private void recuperarUltimoEstado(Path archivo) {
        secuenciaActual = 0;
        ultimoHash = HASH_GENESIS.substring(0, 16);
        try (var lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            Pattern p = VerificadorIntegridadLogs.getPatronLogSellado();
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) continue;
                Matcher m = p.matcher(linea);
                if (m.matches()) {
                    secuenciaActual = Long.parseLong(m.group(1));
                    ultimoHash = m.group(5);
                }
            }
        } catch (Exception ex) {
            reportError("No se pudo leer el estado previo del log " + archivo + ": " + ex.getMessage(),
                    ex, ErrorManager.GENERIC_FAILURE);
        }
    }

    private static void aplicarPermisosRestringidos(Path archivo) {
        try {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> permisos = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(archivo, permisos);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] obtenerOCrearClaveAuditoria(Path dir) throws IOException {
        Path keyFile = dir.resolve(".audit.key");
        if (Files.exists(keyFile) && Files.size(keyFile) >= 32) {
            String hex = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            try {
                return HexFormat.of().parseHex(hex);
            } catch (Exception ignored) {
            }
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String hex = HexFormat.of().formatHex(key);
        Files.writeString(keyFile, hex, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        aplicarPermisosRestringidos(keyFile);
        return key;
    }

    public byte[] getClaveAuditoria() {
        return claveAuditoria.clone();
    }

    static LocalDate fechaLimite(LocalDate hoy) {
        return hoy.minusMonths(1);
    }

    private void purgarAntiguos(LocalDate hoy) throws IOException {
        LocalDate limite = fechaLimite(hoy);
        try (Stream<Path> archivos = Files.list(directorio)) {
            for (Path archivo : (Iterable<Path>) archivos::iterator) {
                Matcher coincidencia = ARCHIVO_DIARIO.matcher(archivo.getFileName().toString());
                if (!coincidencia.matches()) {
                    continue;
                }
                try {
                    LocalDate fechaArchivo = LocalDate.parse(coincidencia.group(1));
                    if (fechaArchivo.isBefore(limite)) {
                        Files.deleteIfExists(archivo);
                    }
                } catch (DateTimeParseException fechaInvalida) {
                    reportError("Se conservó un archivo de log con fecha inválida: " + archivo,
                            fechaInvalida, ErrorManager.GENERIC_FAILURE);
                }
            }
        }
    }

    /** Formateador interno para serializar un LogRecord a cuerpo atómico. */
    private static final class FormateadorLogSellado extends Formatter {
        @Override
        public String format(LogRecord r) {
            if (r == null) return "";
            StringBuilder sb = new StringBuilder();
            sb.append(formatMessage(r));
            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(" | EXCEPCION: ").append(sw.toString());
            }
            return sb.toString();
        }
    }

    private static final class CanalEmergencia extends ErrorManager {
        private final Path directorio;

        private CanalEmergencia(Path directorio) {
            this.directorio = directorio;
        }

        @Override
        public synchronized void error(String mensaje, Exception error, int codigo) {
            StringBuilder entrada = new StringBuilder()
                    .append(java.time.Instant.now())
                    .append(" [ErrorManager ").append(codigo).append("] ")
                    .append(mensaje == null ? "Fallo del sistema de logs" : mensaje)
                    .append(System.lineSeparator());
            if (error != null) {
                StringWriter traza = new StringWriter();
                error.printStackTrace(new PrintWriter(traza));
                entrada.append(traza);
            }
            Path archivoEmergencia = directorio.resolve(
                    "restaurante-emergencia-" + LocalDate.now() + ".log");
            try {
                Files.createDirectories(directorio);
                Files.write(archivoEmergencia, entrada.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                aplicarPermisosRestringidos(archivoEmergencia);
            } catch (IOException | SecurityException falloCanalEmergencia) {
                if (error != null) {
                    falloCanalEmergencia.addSuppressed(error);
                }
                throw new IllegalStateException(
                        "No se pudieron escribir los logs diario ni de emergencia. " + mensaje,
                        falloCanalEmergencia);
            }
        }
    }

    /** Obtiene la ruta física del archivo de log para la fecha indicada. */
    public static Path obtenerRutaLog(LocalDate fecha) {
        String configurado = System.getProperty("restaurante.logs.dir", "logs");
        Path dir = Paths.get(configurado).toAbsolutePath().normalize();
        return dir.resolve("restaurante-" + fecha + ".log");
    }

    /** Fuerza el vaciado de los buffers de los handlers activos en el logger raíz. */
    public static void vaciarBuffers() {
        java.util.logging.Logger raiz = java.util.logging.Logger.getLogger("");
        for (Handler h : raiz.getHandlers()) {
            try {
                h.flush();
            } catch (Exception ignored) {
            }
        }
    }

    /** Lee todas las líneas registradas en el archivo de log de la fecha solicitada. */
    public static java.util.List<String> leerLineasLog(LocalDate fecha) {
        vaciarBuffers();
        Path archivo = obtenerRutaLog(fecha);
        if (!Files.exists(archivo)) {
            return java.util.Collections.singletonList("No se encontró el archivo de log para la fecha " + fecha + " (" + archivo + ").");
        }
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return java.util.Collections.singletonList("Error al leer el archivo de log: " + ex.getMessage());
        }
    }
}
