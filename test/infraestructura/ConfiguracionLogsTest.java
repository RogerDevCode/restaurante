package infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfiguracionLogsTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private String directorioAnterior;

    @Before
    public void guardarConfiguracionAnterior() {
        directorioAnterior = System.getProperty("restaurante.logs.dir");
    }

    @After
    public void restaurarConfiguracion() {
        if (directorioAnterior == null) {
            System.clearProperty("restaurante.logs.dir");
        } else {
            System.setProperty("restaurante.logs.dir", directorioAnterior);
        }
    }

    @Test
    public void siNoPuedeCrearElLogDiarioFallaLaConfiguracionAntesDelArranque() throws IOException {
        Path destinoQueEsArchivo = temporal.newFile("destino-no-directorio").toPath();
        System.setProperty("restaurante.logs.dir", destinoQueEsArchivo.toString());

        try {
            ConfiguracionLogs.configurar();
            fail("La aplicación no debe aceptar operaciones sin poder iniciar el logger diario.");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage().contains("No se pudo inicializar"));
            assertNotNull(error.getCause());
            assertTrue(Files.isRegularFile(destinoQueEsArchivo));
        }
    }
}
