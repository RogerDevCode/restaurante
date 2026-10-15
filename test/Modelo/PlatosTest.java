package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlatosTest {
    @Test
    public void conservaPrecisionDecimalEnElDominio() {
        Platos plato = new Platos(1, "Té", new BigDecimal("0.10"), "2026-10-05");

        assertEquals(new BigDecimal("0.10"), plato.getPrecioDecimal());
    }

    @Test
    public void propiedadesMonetariasNuevasPermitenClasificacionPendiente() {
        Platos plato = new Platos();

        assertNull(plato.getMonedaBase());
        assertNull(plato.getPrecioUsd());
        assertNull(plato.getPrecioVes());
        assertNull(plato.getPrecioIncluyeIva());

        plato.setMonedaBase("VES");
        plato.setPrecioVes(new BigDecimal("60.00"));
        plato.setPrecioUsd(new BigDecimal("1.50"));
        plato.setPrecioIncluyeIva(Boolean.TRUE);

        assertEquals("VES", plato.getMonedaBase());
        assertEquals(new BigDecimal("60.00"), plato.getPrecioVes());
        assertEquals(new BigDecimal("1.50"), plato.getPrecioUsd());
        assertEquals(Boolean.TRUE, plato.getPrecioIncluyeIva());
    }
}
