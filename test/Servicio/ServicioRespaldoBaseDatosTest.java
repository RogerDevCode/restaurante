package Servicio;

import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import infraestructura.ProveedorConexionJdbc;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class ServicioRespaldoBaseDatosTest {

    private Path tempDir;
    private ServicioRespaldoBaseDatos servicio;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test_respaldos_");
        servicio = new ServicioRespaldoBaseDatos(new ProveedorConexionJdbc(), tempDir);
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                for (Path p : stream) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void crearRespaldoGeneraArchivoSqlValidoConContenido() throws IOException {
        Path archivo = servicio.crearRespaldo(tempDir);

        assertNotNull("La ruta del respaldo no debe ser nula", archivo);
        assertTrue("El archivo de respaldo debe existir físicamente", Files.exists(archivo));
        assertTrue("El nombre debe comenzar con 'respaldo_restaurante_'", archivo.getFileName().toString().startsWith("respaldo_restaurante_"));
        assertTrue("El nombre debe terminar en '.sql'", archivo.getFileName().toString().endsWith(".sql"));

        List<String> lineas = Files.readAllLines(archivo);
        assertFalse("El archivo no debe estar vacío", lineas.isEmpty());

        String contenido = String.join("\n", lineas);
        assertTrue("Debe desactivar foreign key checks", contenido.contains("SET FOREIGN_KEY_CHECKS = 0;"));
        assertTrue("Debe reactivar foreign key checks", contenido.contains("SET FOREIGN_KEY_CHECKS = 1;"));
        assertTrue("Debe contener encabezado identificativo oficial", contenido.contains("RESPALDO DE BASE DE DATOS - RESTAURANTE 2026"));
        assertTrue("Debe contener firma mágica de respaldo", contenido.contains(ServicioRespaldoBaseDatos.MAGIC_HEADER));
    }

    @Test
    public void crearRespaldoAutomaticoSiEsNecesarioEvitaDuplicadosMismoDia() {
        Path primerRespaldo = servicio.crearRespaldoAutomaticoSiEsNecesario();
        assertNotNull("El primer respaldo automático de hoy debe generarse", primerRespaldo);

        Path segundoRespaldo = servicio.crearRespaldoAutomaticoSiEsNecesario();
        assertNull("El segundo llamado el mismo día debe omitirse para no duplicar", segundoRespaldo);
    }

    @Test
    public void restaurarRespaldoFallaSiArchivoNoExiste() {
        Path inexistente = tempDir.resolve("no_existe.sql");
        assertThrows(ErrorAplicacionException.class, () -> servicio.restaurarRespaldo(inexistente));
    }

    @Test
    public void restaurarRespaldoRechazaArchivoSinFirmaOficial() throws IOException {
        Path scriptSinFirma = tempDir.resolve("ajeno.sql");
        Files.writeString(scriptSinFirma, "SELECT 1;");

        assertThrows("Archivos ajenos sin firma deben ser rechazados",
                ErrorAplicacionException.class, () -> servicio.restaurarRespaldo(scriptSinFirma));
    }

    @Test
    public void restaurarRespaldoRechazaComandosPeligrosos() throws IOException {
        Path scriptPeligroso = tempDir.resolve("peligroso.sql");
        String sql = "-- RESTAURANTE_2026_BACKUP_SQL\n"
                + "DROP DATABASE restaurante;\n";
        Files.writeString(scriptPeligroso, sql);

        assertThrows("Archivos con DROP DATABASE deben ser bloqueados",
                ErrorAplicacionException.class, () -> servicio.restaurarRespaldo(scriptPeligroso));
    }

    @Test
    public void restaurarRespaldoEjecutaScriptSqlValidoYCreaCopiaPrevia() throws IOException {
        Path scriptPrueba = tempDir.resolve("prueba_restore.sql");
        String sql = """
            -- ========================================================
            -- RESPALDO DE BASE DE DATOS - RESTAURANTE 2026
            -- RESTAURANTE_2026_BACKUP_SQL
            -- ========================================================
            SET FOREIGN_KEY_CHECKS = 0;
            CREATE TABLE IF NOT EXISTS test_respaldo_tmp (
                id INT PRIMARY KEY,
                valor VARCHAR(50)
            );
            INSERT INTO test_respaldo_tmp VALUES (1, 'Dato Test')
            ON DUPLICATE KEY UPDATE valor = VALUES(valor);
            SET FOREIGN_KEY_CHECKS = 1;
            -- FIN DEL RESPALDO RESTAURANTE 2026
            """;
        Files.writeString(scriptPrueba, sql);

        Path preCopia = servicio.restaurarRespaldo(scriptPrueba);
        assertNotNull("Debe crearse un respaldo de seguridad previo antes de restaurar", preCopia);
        assertTrue("La copia previa debe existir en disco", Files.exists(preCopia));
    }

    @Test
    public void restaurarRespaldoRechazaArchivoTruncadoSinPie() throws IOException {
        Path scriptTruncado = tempDir.resolve("truncado.sql");
        String sql = """
            -- ========================================================
            -- RESPALDO DE BASE DE DATOS - RESTAURANTE 2026
            -- RESTAURANTE_2026_BACKUP_SQL
            -- ========================================================
            SET FOREIGN_KEY_CHECKS = 0;
            CREATE TABLE IF NOT EXISTS test_respaldo_tmp (id INT PRIMARY KEY);
            """;
        Files.writeString(scriptTruncado, sql);

        assertThrows("Archivos truncados sin pie oficial deben ser rechazados",
                ErrorAplicacionException.class, () -> servicio.restaurarRespaldo(scriptTruncado));
    }

    @Test
    public void restaurarRespaldoInterrumpeInmediatamenteAnteErrorSqlFailFast() throws IOException {
        Path scriptConError = tempDir.resolve("error_restore.sql");
        String sql = """
            -- ========================================================
            -- RESPALDO DE BASE DE DATOS - RESTAURANTE 2026
            -- RESTAURANTE_2026_BACKUP_SQL
            -- ========================================================
            SET FOREIGN_KEY_CHECKS = 0;
            SENTENCIA_SQL_TOTALMENTE_INVALIDA_PARA_PROVOCAR_ERROR;
            SET FOREIGN_KEY_CHECKS = 1;
            -- FIN DEL RESPALDO RESTAURANTE 2026
            """;
        Files.writeString(scriptConError, sql);

        assertThrows("Cualquier error de sentencia SQL debe abortar inmediatamente (Fail-Fast)",
                DataAccessException.class, () -> servicio.restaurarRespaldo(scriptConError));
    }
}
