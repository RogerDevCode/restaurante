package Modelo;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PedidosTest {
    @Test
    public void conservaPrecisionDelTotal() {
        Pedidos pedido = new Pedidos(1, 2, 3, "2026-10-05", new BigDecimal("12.30"),
                "sala", "usuario", "PENDIENTE");

        assertEquals(new BigDecimal("12.30"), pedido.getTotalDecimal());
    }
}
