package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PlatosTest {
    @Test
    public void conservaPrecisionDecimalEnElDominio() {
        Platos plato = new Platos(1, "Té", new BigDecimal("0.10"), "2026-10-05");

        assertEquals(new BigDecimal("0.10"), plato.getPrecioDecimal());
    }
}
