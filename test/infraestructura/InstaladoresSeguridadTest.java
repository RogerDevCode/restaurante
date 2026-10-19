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

    @Test
    public void empaquetadoresExcluyenSecretosYUsanPlantillaEnvExample() throws Exception {
        String shEjecutable = Files.readString(Path.of("generar_restaurante_ejecutable.sh"));
        assertFalse("El empaquetador Linux no debe copiar .env con credenciales reales",
                shEjecutable.contains("cp -f \"$DIR/.env\""));
        assertTrue("El empaquetador Linux debe copiar .env.example",
                shEjecutable.contains(".env.example"));

        String ps1Ejecutable = Files.readString(Path.of("generar_restaurante_ejecutable.ps1"));
        assertFalse("El empaquetador Windows no debe incluir credenciales admin/admin por defecto en .env",
                ps1Ejecutable.contains("MYSQL_PASSWORD=admin"));
        assertTrue("El empaquetador Windows debe empaquetar .env.example",
                ps1Ejecutable.contains(".env.example"));

        String shProd = Files.readString(Path.of("empaquetar_produccion.sh"));
        assertTrue("El empaquetador debe construir el paquete desde staging", shProd.contains("STAGING=\"$(mktemp -d)\""));
        assertFalse("El empaquetador no debe archivar todo el árbol del repositorio",
                shProd.contains("zip -r \"$ARCHIVO_ZIP\" ."));
        assertTrue("El empaquetador debe incluir .env.example", shProd.contains(".env.example"));
        assertTrue("El empaquetador debe inspeccionar el ZIP resultante", shProd.contains("unzip -Z1"));

        for (String script : new String[]{"generar_restaurante_ejecutable.ps1", "instalar_mysql_y_bd.ps1", "instalar_acceso_directo.ps1"}) {
            byte[] bytes = Files.readAllBytes(Path.of(script));
            assertTrue(script + " debe tener BOM UTF-8", bytes.length >= 3
                    && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF);
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n') {
                    assertTrue(script + " debe usar CRLF", i > 0 && bytes[i - 1] == '\r');
                }
            }
        }
    }

    @Test
    public void instaladoresAplicanMinimosPrivilegiosYAclRestringida() throws Exception {
        String ps1Instalar = Files.readString(Path.of("instalar_mysql_y_bd.ps1"));
        assertTrue("El instalador nuevo debe conceder mínimos privilegios sobre la base",
                ps1Instalar.contains("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON restaurante.*"));
        assertFalse("El instalador no debe conceder ALL PRIVILEGES sin restricciones",
                ps1Instalar.contains("GRANT ALL PRIVILEGES"));
        assertTrue("El instalador nuevo debe proteger ACL del .env",
                ps1Instalar.contains("Protect-EnvFileAcl"));
        assertTrue("El instalador nuevo debe generar clave HMAC aleatoria",
                ps1Instalar.contains("BACKUP_HMAC_KEY") && ps1Instalar.contains("New-RandomDatabasePassword"));
        assertFalse("No se deben ignorar fallos al asegurar ACL del .env",
                ps1Instalar.contains("Write-Host \"Aviso: No se pudo restringir ACL"));

        String ps1Directo = Files.readString(Path.of("instalar_acceso_directo.ps1"));
        assertTrue("El instalador maestro debe proteger ACL del .env",
                ps1Directo.contains("Protect-EnvFileAcl"));
        assertFalse("El instalador maestro no debe quemar credenciales admin/admin en .env",
                ps1Directo.contains("MYSQL_PASSWORD=admin"));
        assertTrue("El instalador maestro debe guardar una clave HMAC local",
                ps1Directo.contains("BACKUP_HMAC_KEY=$hmacGenerada"));
    }
}
