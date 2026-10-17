package infraestructura;

import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigradorEsquemaJdbcTest {

    @Test
    public void versionEsquemaIncluyeElEndurecimientoDeAplicaIva() {
        assertTrue("El esquema debe incluir la migración v12 (aplica_iva NOT NULL).",
                MigradorEsquemaJdbc.versionEsquemaActual() >= 12);
    }

    @Test
    public void yaNoExisteElRecalculoQueAdivinaLaFiscalidadHistorica() {
        for (Method metodo : MigradorEsquemaJdbc.class.getDeclaredMethods()) {
            assertNotEquals("No debe existir backfill que adivine aplica_iva histórico.",
                    "recalcularFiscalidadHistorica", metodo.getName());
        }
    }
}
