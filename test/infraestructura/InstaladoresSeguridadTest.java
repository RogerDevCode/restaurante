package infraestructura;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** Contratos estáticos para ramas de instalador que solo se pueden ejecutar en Windows. */
public class InstaladoresSeguridadTest {

    @Test
    public void actualizadorNoEjecutaSqlBaseNiCredencialesRootAlternativas() throws Exception {
        String script = Files.readString(Path.of("actualizar_bd.bat"));

        assertTrue(script.contains("restaurante.Restaurante --migrate-db"));
        assertFalse(script.contains("actualizar_bd.sql"));
        assertFalse(script.contains("mysql.exe"));
        assertFalse(script.contains("-u root"));
        assertTrue(script.contains("pushd \"%APP_DIR%\""));
        assertTrue(script.contains("popd"));
        assertTrue(script.contains("if not \"%JAVA_EXIT%\"==\"0\""));
    }

    @Test
    public void instaladorNuevoRechazaBaseExistenteAntesDeImportar() throws Exception {
        String script = Files.readString(Path.of("instalar_mysql_y_bd.ps1"));
        int comprobacion = script.indexOf("SELECT COUNT(*) FROM information_schema.schemata");
        int usuarioExistente = script.indexOf("SELECT COUNT(*) FROM mysql.user");
        int creacion = script.indexOf("CREATE DATABASE restaurante");
        int importacion = script.indexOf("Get-Content -LiteralPath $sqlFile");

        assertTrue(comprobacion >= 0);
        assertTrue(usuarioExistente > comprobacion);
        assertTrue(creacion > comprobacion);
        assertTrue(creacion > usuarioExistente);
        assertTrue(importacion > creacion);
        assertTrue(script.contains("Este instalador es solo para una base nueva"));
        assertFalse(script.contains("CREATE DATABASE IF NOT EXISTS restaurante"));
        assertFalse(script.contains("CREATE USER IF NOT EXISTS"));
        assertFalse(script.contains("ALTER USER"));
        assertTrue(script.contains("New-RandomDatabasePassword"));
        assertTrue(script.contains("CAMBIAR_POR_CLAVE_LOCAL_APP"));
        assertTrue(script.contains("Set-EnvPassword $envFile $dbPassword"));
        assertFalse(script.contains("else { \"admin\" }"));
    }

    @Test
    public void instaladorMaestroNoUsaSqlLegacyComoFallback() throws Exception {
        String script = Files.readString(Path.of("instalar_acceso_directo.ps1"));

        assertTrue(script.contains("(N)ueva instalación o (A)ctualizar"));
        assertTrue(script.contains("Invoke-MigracionJDBC"));
        assertFalse(script.contains("$contentAct"));
        assertFalse(script.contains("SOURCE $sqlFile"));
        assertFalse(script.contains("ALTER USER 'admin'"));
    }

    @Test
    public void empaquetadorIncluyeElHelperDeInstalacionNueva() throws Exception {
        String script = Files.readString(Path.of("generar_restaurante_ejecutable.ps1"));
        assertTrue(script.contains("\"instalar_mysql_y_bd.ps1\""));
    }

    @Test
    public void scriptsDeActualizacionSqlDuplicadosSeMantienenSincronizados() throws Exception {
        assertArrayEquals(Files.readAllBytes(Path.of("actualizar_bd.sql")),
                Files.readAllBytes(Path.of("db/migrations/actualizar_bd.sql")));
    }
}
