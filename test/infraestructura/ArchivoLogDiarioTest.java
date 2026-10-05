package infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArchivoLogDiarioTest {
    @Rule
    public final TemporaryFolder directorioTemporal = new TemporaryFolder();

    private String directorioOriginal;
    private Path directorioLogs;
    private ArchivoLogDiario handler;

    @Before
    public void prepararDirectorio() throws IOException {
        directorioOriginal = System.getProperty("restaurante.logs.dir");
        directorioLogs = directorioTemporal.newFolder("logs").toPath();
        System.setProperty("restaurante.logs.dir", directorioLogs.toString());
    }

    @After
    public void restaurarConfiguracion() {
        if (handler != null) {
            handler.close();
        }
        if (directorioOriginal == null) {
            System.clearProperty("restaurante.logs.dir");
        } else {
            System.setProperty("restaurante.logs.dir", directorioOriginal);
        }
    }

    @Test
    public void retencionCruzaMesYAnioCalendario() {
        assertEquals(LocalDate.of(2025, 12, 1),
                ArchivoLogDiario.fechaLimite(LocalDate.of(2026, 1, 1)));
        assertEquals(LocalDate.of(2024, 2, 29),
                ArchivoLogDiario.fechaLimite(LocalDate.of(2024, 3, 31)));
        assertEquals(LocalDate.of(2026, 2, 28),
                ArchivoLogDiario.fechaLimite(LocalDate.of(2026, 3, 31)));
    }

    @Test
    public void creaArchivoDiarioYRotaSegunFechaDelRegistro() throws IOException {
        handler = new ArchivoLogDiario();
        LocalDate hoy = LocalDate.now();
        publicar("error del día actual", hoy);
        publicar("error del día anterior", hoy.minusDays(1));
        handler.close();
        handler = null;

        String contenidoHoy = new String(Files.readAllBytes(archivo(hoy)), StandardCharsets.UTF_8);
        String contenidoAyer = new String(Files.readAllBytes(archivo(hoy.minusDays(1))), StandardCharsets.UTF_8);
        assertTrue(contenidoHoy.contains("error del día actual"));
        assertTrue(contenidoAyer.contains("error del día anterior"));
    }

    @Test
    public void eventoAtrasadoFueraDeRetencionNoRecreaUnArchivoVencido() throws IOException {
        handler = new ArchivoLogDiario();
        LocalDate fechaVencida = LocalDate.now().minusMonths(2);
        publicar("evento atrasado", fechaVencida);

        assertFalse(Files.exists(archivo(fechaVencida)));
        assertTrue(Files.readAllBytes(archivo(LocalDate.now())).length > 0);
    }

    @Test
    public void purgaSoloArchivosAnterioresAlLimiteMensual() throws IOException {
        LocalDate limite = ArchivoLogDiario.fechaLimite(LocalDate.now());
        Path antiguo = archivo(limite.minusDays(1));
        Path limiteConservado = archivo(limite);
        Files.createFile(antiguo);
        Files.createFile(limiteConservado);

        handler = new ArchivoLogDiario();

        assertFalse(Files.exists(antiguo));
        assertTrue(Files.exists(limiteConservado));
    }

    @Test
    public void canalDeEmergenciaGuardaFalloYTrazaEnArchivoDiarioSeparado() throws IOException {
        handler = new ArchivoLogDiario();
        IllegalStateException causa = new IllegalStateException("disco simulado no disponible");

        handler.getErrorManager().error("No se pudo escribir el log diario", causa, ErrorManager.WRITE_FAILURE);

        Path emergencia = directorioLogs.resolve("restaurante-emergencia-" + LocalDate.now() + ".log");
        assertTrue(Files.exists(emergencia));
        String contenido = new String(Files.readAllBytes(emergencia), StandardCharsets.UTF_8);
        assertTrue(contenido.contains("No se pudo escribir el log diario"));
        assertTrue(contenido.contains("disco simulado no disponible"));
        assertTrue(contenido.contains("IllegalStateException"));
    }

    @Test
    public void fallaVisiblementeCuandoTampocoPuedeEscribirElCanalDeEmergencia() throws IOException {
        handler = new ArchivoLogDiario();
        handler.close();
        Files.deleteIfExists(archivo(LocalDate.now()));
        Files.delete(directorioLogs);
        Files.createFile(directorioLogs);

        IllegalStateException error = org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> handler.getErrorManager().error("Fallaron ambos canales", new IOException("disco lleno"),
                        ErrorManager.WRITE_FAILURE));

        assertTrue(error.getMessage().contains("diario ni de emergencia"));
        assertNotNull(error.getCause());
        assertTrue(error.getCause().getMessage().contains("logs"));
    }

    @Test
    public void excepcionUncheckedDelFormateadorSeRegistraPorCanalDeEmergencia() throws IOException {
        handler = new ArchivoLogDiario();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                throw new IllegalStateException("formateador simulado defectuoso");
            }
        });

        publicar("evento con formato defectuoso", LocalDate.now());

        Path emergencia = directorioLogs.resolve("restaurante-emergencia-" + LocalDate.now() + ".log");
        String contenido = new String(Files.readAllBytes(emergencia), StandardCharsets.UTF_8);
        assertTrue(contenido.contains("No se pudo escribir o rotar el archivo diario de log"));
        assertTrue(contenido.contains("formateador simulado defectuoso"));
        assertTrue(contenido.contains("IllegalStateException"));
    }

    private void publicar(String mensaje, LocalDate fecha) {
        LogRecord registro = new LogRecord(Level.SEVERE, mensaje);
        registro.setInstant(fecha.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
        handler.publish(registro);
        handler.flush();
    }

    private Path archivo(LocalDate fecha) {
        return directorioLogs.resolve("restaurante-" + fecha + ".log");
    }
}
