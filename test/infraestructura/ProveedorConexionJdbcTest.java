package infraestructura;

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

public class ProveedorConexionJdbcTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private String archivoAnterior;
    private String urlAnterior;

    @Before
    public void guardarPropiedades() {
        archivoAnterior = System.getProperty("restaurante.env");
        urlAnterior = System.getProperty("DB_URL");
    }

    @After
    public void restaurarPropiedades() {
        restaurar("restaurante.env", archivoAnterior);
        restaurar("DB_URL", urlAnterior);
    }

    @Test
    public void propiedadDelProcesoPrevaleceSobreArchivoLocal() {
        Properties archivo = new Properties();
        archivo.setProperty("DB_URL", "jdbc:mysql://localhost:3306/restaurante");
        System.setProperty("DB_URL", "jdbc:mysql://127.0.0.1:3307/restaurante_test");

        assertEquals("jdbc:mysql://127.0.0.1:3307/restaurante_test",
                new ProveedorConexionJdbc().setting("DB_URL", archivo));
    }

    @Test
    public void errorDeSintaxisLocalPropagaSqlExceptionConCausa() throws IOException {
        Path archivo = temporal.newFile(".env").toPath();
        Files.write(archivo, "INVALIDO=\\u12ZZ\n".getBytes(StandardCharsets.UTF_8));
        System.setProperty("restaurante.env", archivo.toString());

        try {
            new ProveedorConexionJdbc().getConnection();
        } catch (SQLException error) {
            assertEquals("No se pudo leer o interpretar el archivo de configuración local", error.getMessage());
            assertNotNull(error.getCause());
            return;
        }
        throw new AssertionError("La configuración local inválida debe abortar la conexión.");
    }

    private static void restaurar(String nombre, String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(nombre);
        } else {
            System.setProperty(nombre, valorAnterior);
        }
    }
}
