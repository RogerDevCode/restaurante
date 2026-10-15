package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DetallePedidoTest {
    @Test
    public void conservaLosCentavosSinConvertirlosABinario() {
        DetallePedido detalle = new DetallePedido(1, "Agua", new BigDecimal("0.10"), 3, "", 9);

        assertEquals(new BigDecimal("0.10"), detalle.getPrecioDecimal());
        assertEquals(new BigDecimal("0.30"), detalle.getPrecioDecimal().multiply(BigDecimal.valueOf(3)));
    }

    @Test
    public void snapshotsNuevosAceptanNulosParaDatosHistoricosNoClasificados() {
        DetallePedido detalle = new DetallePedido();

        assertNull(detalle.getIdPlato());
        assertNull(detalle.getMonedaBase());
        assertNull(detalle.getPrecioIncluyeIva());
        assertNull(detalle.getIvaPorcentaje());

        detalle.setIdPlato(42);
        detalle.setMonedaBase("USD");
        detalle.setPrecioIncluyeIva(Boolean.FALSE);
        detalle.setIvaPorcentaje(new BigDecimal("16.00"));

        assertEquals(Integer.valueOf(42), detalle.getIdPlato());
        assertEquals("USD", detalle.getMonedaBase());
        assertEquals(Boolean.FALSE, detalle.getPrecioIncluyeIva());
        assertEquals(new BigDecimal("16.00"), detalle.getIvaPorcentaje());
    }
}
