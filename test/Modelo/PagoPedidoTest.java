package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PagoPedidoTest {
    @Test
    public void convierteMonedasConRedondeoDeDosDecimales() {
        PagoPedido usd = new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("1.25"));
        PagoPedido bs = new PagoPedido("TRANSFERENCIA", "VES", new BigDecimal("45.63"));

        assertEquals(new BigDecimal("45.63"), usd.equivalenteBs(new BigDecimal("36.5000")));
        assertEquals(new BigDecimal("1.25"), bs.equivalenteUsd(new BigDecimal("36.5000")));
    }

    @Test
    public void efectivoDebeUsarLaMonedaCorrespondiente() {
        assertThrows(ErrorAplicacionException.class,
                () -> new PagoPedido("EFECTIVO_BS", "USD", new BigDecimal("10.00")));
        assertThrows(ErrorAplicacionException.class,
                () -> new PagoPedido("EFECTIVO_USD", "VES", new BigDecimal("10.00")));
    }

    @Test
    public void rechazaMontosNoPositivosYMasDeDosDecimales() {
        assertThrows(ErrorAplicacionException.class,
                () -> new PagoPedido("TRANSFERENCIA", "VES", BigDecimal.ZERO));
        assertThrows(ErrorAplicacionException.class,
                () -> new PagoPedido("TRANSFERENCIA", "VES", new BigDecimal("1.001")));
    }

    @Test
    public void conversionRequiereTasaValidaYNoUsaFallbacks() {
        PagoPedido pago = new PagoPedido("TRANSFERENCIA", "USD", new BigDecimal("1.00"));
        assertThrows(ErrorAplicacionException.class, () -> pago.equivalenteBs(null));
        assertThrows(ErrorAplicacionException.class, () -> pago.equivalenteBs(BigDecimal.ZERO));
    }
}
