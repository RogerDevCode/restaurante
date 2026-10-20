package infraestructura;

import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigradorEsquemaJdbcTest {

    @Test
    public void versionEsquemaIncluyeLaPersistenciaDeMarchasParciales() {
        assertTrue("El esquema debe incluir la migración v15 de marchas de cocina incrementales.",
                MigradorEsquemaJdbc.versionEsquemaActual() >= 15);
    }

    @Test
    public void yaNoExisteElRecalculoQueAdivinaLaFiscalidadHistorica() {
        for (Method metodo : MigradorEsquemaJdbc.class.getDeclaredMethods()) {
            assertNotEquals("No debe existir backfill que adivine aplica_iva histórico.",
                    "recalcularFiscalidadHistorica", metodo.getName());
        }
    }
}
