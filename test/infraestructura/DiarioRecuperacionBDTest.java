package infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class DiarioRecuperacionBDTest {
    @Rule public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void diarioSeEscribeYLeeConTodosLosCampos() throws Exception {
        Path archivo = temporal.getRoot().toPath().resolve("recovery/journal.properties");
        DiarioRecuperacionBD diario = new DiarioRecuperacionBD(archivo);
        DiarioRecuperacionBD.Operacion operacion = diario.nueva("/tmp/destino.sql", "abc123");
        diario.guardar(new DiarioRecuperacionBD.Operacion(operacion.id(), "APLICANDO", "/tmp/previo.sql",
                "def456", operacion.destino(), operacion.checksumDestino(), operacion.creadaEn()));

        DiarioRecuperacionBD.Operacion leida = diario.leer();
        assertEquals(operacion.id(), leida.id());
        assertEquals("APLICANDO", leida.fase());
        assertEquals("/tmp/previo.sql", leida.respaldoPrevio());
        assertEquals("def456", leida.checksumPrevio());
        assertEquals("/tmp/destino.sql", leida.destino());
        assertEquals("abc123", leida.checksumDestino());
    }

    @Test
    public void diarioAusenteNoCreaOperacion() throws Exception {
        DiarioRecuperacionBD diario = new DiarioRecuperacionBD(temporal.getRoot().toPath().resolve("no-existe.properties"));
        assertNull(diario.leer());
    }

    @Test
    public void diarioCorruptoSeRechazaYNoSeInterpretaComoOperacionVacia() throws Exception {
        Path archivo = temporal.getRoot().toPath().resolve("journal.properties");
        Files.writeString(archivo, "version=99\nid=sin-fase\n");
        DiarioRecuperacionBD diario = new DiarioRecuperacionBD(archivo);
        assertThrows(IOException.class, diario::leer);
    }

    @Test
    public void eliminarQuitaElDiarioDurable() throws Exception {
        DiarioRecuperacionBD diario = new DiarioRecuperacionBD(temporal.getRoot().toPath().resolve("journal.properties"));
        diario.guardar(diario.nueva("destino.sql", "hash"));
        diario.eliminar();
        assertNull(diario.leer());
    }
}
