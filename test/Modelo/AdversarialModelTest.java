package Modelo;

import org.junit.Test;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class AdversarialModelTest {

    @Test
    public void configTasaDolarExtremaONegativa() {
        Config config = new Config();
        
        config.setTasaDolar(new BigDecimal("-36.50"));
        org.junit.Assert.assertNull("Debe rechazar negativos dejando null sin fallback silencioso", config.getTasaDolar());
        
        config.setTasaDolar(BigDecimal.ZERO);
        org.junit.Assert.assertNull("Debe rechazar cero dejando null sin fallback silencioso", config.getTasaDolar());

        config.setTasaDolar(new BigDecimal("999999999.9999"));
        assertEquals("Acepta valores positivos extremos (aunque el DAO tal vez no)", new BigDecimal("999999999.9999"), config.getTasaDolar());
    }

    @Test
    public void pedidoTotalBsCalculo() {
        Pedidos pedido = new Pedidos();
        pedido.setTasaCambio(new BigDecimal("36.50"));
        pedido.setTotalDecimal(new BigDecimal("10.00"));
        pedido.setTotalBs(pedido.getTotalDecimal().multiply(pedido.getTasaCambio()));
        
        assertEquals(new BigDecimal("365.00"), pedido.getTotalBs());
    }

    @Test
    public void StringsExtremosEnEntidadesNoLanzanErrorEnMemoria() {
        Config config = new Config();
        String extremo = "A".repeat(10000);
        config.setNombre(extremo);
        config.setRuc(extremo);
        
        assertEquals(extremo, config.getNombre());
        assertEquals(extremo, config.getRuc());

        Usuario user = new Usuario();
        user.setNombre(extremo);
        user.setCorreo(extremo);
        user.setPassword(extremo);
        user.setRol(extremo);

        assertEquals(extremo, user.getNombre());
    }
}
