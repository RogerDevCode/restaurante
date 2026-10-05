package infraestructura;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Handler JUL con un archivo por fecha y retención de un mes calendario. */
public final class ArchivoLogDiario extends Handler {
    private static final Pattern ARCHIVO_DIARIO = Pattern.compile("restaurante-(?:emergencia-)?(\\d{4}-\\d{2}-\\d{2})\\.log");

    private final Path directorio;
    private BufferedWriter escritor;
    private LocalDate fechaAbierta;

    public ArchivoLogDiario() throws IOException {
        String configurado = System.getProperty("restaurante.logs.dir", "logs");
        directorio = Paths.get(configurado).toAbsolutePath().normalize();
        setFormatter(new SimpleFormatter());
        setErrorManager(new CanalEmergencia(directorio));
        abrirArchivo(LocalDate.now());
    }

    @Override
    public synchronized void publish(LogRecord registro) {
        if (registro == null || !isLoggable(registro)) {
            return;
        }
        try {
            LocalDate fechaRegistro = registro.getInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate hoy = LocalDate.now();
            LocalDate fechaArchivo = fechaRegistro.isBefore(fechaLimite(hoy)) || fechaRegistro.isAfter(hoy)
                    ? hoy : fechaRegistro;
            if (!fechaArchivo.equals(fechaAbierta)) {
                abrirArchivo(fechaArchivo);
            }
            escritor.write(getFormatter().format(registro));
            escritor.flush();
        } catch (IOException ex) {
            reportError("No se pudo escribir o rotar el archivo diario de log", ex, ErrorManager.WRITE_FAILURE);
        } catch (RuntimeException ex) {
            reportError("No se pudo escribir o rotar el archivo diario de log", ex, ErrorManager.WRITE_FAILURE);
        }
    }

    @Override
    public synchronized void flush() {
        if (escritor == null) {
            return;
        }
        try {
            escritor.flush();
        } catch (IOException | SecurityException ex) {
            reportError("No se pudo vaciar el archivo diario de log", ex, ErrorManager.FLUSH_FAILURE);
        }
    }

    @Override
    public synchronized void close() throws SecurityException {
        if (escritor == null) {
            return;
        }
        try {
            escritor.close();
        } catch (IOException | SecurityException ex) {
            reportError("No se pudo cerrar el archivo diario de log", ex, ErrorManager.CLOSE_FAILURE);
        } finally {
            escritor = null;
        }
    }

    static LocalDate fechaLimite(LocalDate hoy) {
        return hoy.minusMonths(1);
    }

    private void abrirArchivo(LocalDate fecha) throws IOException {
        if (escritor != null) {
            escritor.close();
        }
        Files.createDirectories(directorio);
        purgarAntiguos(LocalDate.now());
        Path archivo = directorio.resolve("restaurante-" + fecha + ".log");
        escritor = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        fechaAbierta = fecha;
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
}
