package infraestructura;

import Modelo.Conexion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConexionTest {
    @Rule
    public final TemporaryFolder directorioTemporal = new TemporaryFolder();

    private String archivoConfiguracionOriginal;
    private String urlOriginal;

    @Before
    public void guardarConfiguracion() {
        archivoConfiguracionOriginal = System.getProperty("restaurante.env");
        urlOriginal = System.getProperty("DB_URL");
    }

    @After
    public void restaurarConfiguracion() {
        if (archivoConfiguracionOriginal == null) {
            System.clearProperty("restaurante.env");
        } else {
            System.setProperty("restaurante.env", archivoConfiguracionOriginal);
        }
        if (urlOriginal == null) {
            System.clearProperty("DB_URL");
        } else {
            System.setProperty("DB_URL", urlOriginal);
        }
    }

    @Test
    public void archivoConEscapeInvalidoSePropagaComoErrorDeConfiguracion() throws IOException {
        Path archivo = directorioTemporal.newFile(".env").toPath();
        Files.write(archivo, "MYSQL_DATABASE=restaurante\nINVALIDO=\\u12ZZ\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("restaurante.env", archivo.toString());

        try {
            new Conexion().getConnection();
        } catch (SQLException error) {
            assertEquals("No se pudo leer o interpretar el archivo de configuración local", error.getMessage());
            assertNotNull(error.getCause());
            assertTrue(error.getCause() instanceof IllegalArgumentException);
            return;
        }
        throw new AssertionError("Se esperaba que la configuración mal formada fallara explícitamente.");
    }

    @Test
    public void propiedadJVMDeBaseDeDatosPrevaleceSobreArchivoLocal() {
        Properties archivo = new Properties();
        archivo.setProperty("DB_URL", "jdbc:mysql://localhost:3306/restaurante");
        System.setProperty("DB_URL", "jdbc:mysql://127.0.0.1:3307/restaurante_test");

        assertEquals("jdbc:mysql://127.0.0.1:3307/restaurante_test",
                new ProveedorConexionJdbc().setting("DB_URL", archivo));
    }
}
