package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DetallePedidoTest {
    @Test
    public void conservaLosCentavosSinConvertirlosABinario() {
        DetallePedido detalle = new DetallePedido(1, "Agua", new BigDecimal("0.10"), 3, "", 9);

        assertEquals(new BigDecimal("0.10"), detalle.getPrecioDecimal());
        assertEquals(new BigDecimal("0.30"), detalle.getPrecioDecimal().multiply(BigDecimal.valueOf(3)));
    }
}
