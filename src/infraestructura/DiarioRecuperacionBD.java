package infraestructura;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/** Diario externo a MySQL para reanudar/restaurar operaciones interrumpidas por caída del proceso. */
public final class DiarioRecuperacionBD {
    private final Path archivo;

    public record Operacion(String id, String fase, String respaldoPrevio, String checksumPrevio,
                            String destino, String checksumDestino, String creadaEn) {}

    public DiarioRecuperacionBD(Path archivo) {
        this.archivo = archivo.toAbsolutePath().normalize();
    }

    public static DiarioRecuperacionBD porDefecto() {
        String configurado = System.getProperty("restaurante.recovery.journal");
        Path path = configurado == null || configurado.isBlank()
                ? Path.of(System.getProperty("user.dir"), "respaldos", ".restauracion_en_curso.properties")
                : Path.of(configurado);
        return new DiarioRecuperacionBD(path);
    }

    public Path getArchivo() { return archivo; }

    public Operacion nueva(String destino, String checksumDestino) {
        return new Operacion(UUID.randomUUID().toString(), "INICIANDO", null, null,
                destino, checksumDestino, Instant.now().toString());
    }

    public void guardar(Operacion operacion) throws IOException {
        Files.createDirectories(archivo.getParent());
        Properties p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("id", operacion.id());
        p.setProperty("fase", operacion.fase());
        p.setProperty("destino", valor(operacion.destino()));
        p.setProperty("checksumDestino", valor(operacion.checksumDestino()));
        p.setProperty("respaldoPrevio", valor(operacion.respaldoPrevio()));
        p.setProperty("checksumPrevio", valor(operacion.checksumPrevio()));
        p.setProperty("creadaEn", valor(operacion.creadaEn()));
        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (var salida = Files.newOutputStream(temporal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                 var writer = new java.io.OutputStreamWriter(salida, StandardCharsets.UTF_8)) {
                p.store(writer, "Diario de recuperación Restaurante");
                writer.flush();
                salida.flush();
            }
            try (FileChannel canal = FileChannel.open(temporal, StandardOpenOption.WRITE)) { canal.force(true); }
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
            try (FileChannel canal = FileChannel.open(archivo.getParent(), StandardOpenOption.READ)) {
                canal.force(true);
            } catch (IOException ignored) {
                // Algunos sistemas de archivos no permiten fsync de directorios.
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    public Operacion leer() throws IOException {
        if (!Files.exists(archivo)) return null;
        Properties p = new Properties();
        try (var entrada = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) { p.load(entrada); }
        if (!"1".equals(p.getProperty("version")) || p.getProperty("id") == null || p.getProperty("fase") == null) {
            throw new IOException("El diario de recuperación tiene un formato desconocido: " + archivo);
        }
        return new Operacion(p.getProperty("id"), p.getProperty("fase"), opcional(p.getProperty("respaldoPrevio")),
                opcional(p.getProperty("checksumPrevio")), opcional(p.getProperty("destino")),
                opcional(p.getProperty("checksumDestino")), opcional(p.getProperty("creadaEn")));
    }

    public void eliminar() throws IOException { Files.deleteIfExists(archivo); }

    private static String valor(String s) { return s == null ? "" : s; }
    private static String opcional(String s) { return s == null || s.isBlank() ? null : s; }
}
