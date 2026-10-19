package Modelo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;

public class ComandaMarchaTest {

    @Test
    public void mismoPlatoEnOtraRondaSoloEmiteIncremento() {
        List<MovimientoComanda> cambios = ComandaMarcha.calcularDiferencias(
                List.of(detalle(41, "Pollo", 2, "sin sal")),
                List.of(detalle(41, "Pollo", 5, "sin sal")));

        assertEquals(1, cambios.size());
        assertEquals(MovimientoComanda.Accion.PREPARAR, cambios.get(0).accion());
        assertEquals(3, cambios.get(0).cantidad());
    }

    @Test
    public void comentarioDistintoGeneraPreparacionNuevaYRetiroDeLaAnterior() {
        List<MovimientoComanda> cambios = ComandaMarcha.calcularDiferencias(
                List.of(detalle(41, "Pollo", 1, "sin sal")),
                List.of(detalle(41, "Pollo", 1, "sin cebolla")));

        assertEquals(2, cambios.size());
        assertEquals(MovimientoComanda.Accion.PREPARAR, cambios.get(0).accion());
        assertEquals("sin cebolla", cambios.get(0).comentario());
        assertEquals(MovimientoComanda.Accion.RETIRAR, cambios.get(1).accion());
        assertEquals("sin sal", cambios.get(1).comentario());
    }

    @Test
    public void reducirCantidadGeneraRetiroYPrecioNoCambiaComanda() {
        DetallePedido previo = detalle(41, "Pollo", 4, "");
        DetallePedido actual = detalle(41, "Pollo", 2, "");
        actual.setPrecioDecimal(new BigDecimal("99.99"));

        List<MovimientoComanda> cambios = ComandaMarcha.calcularDiferencias(List.of(previo), List.of(actual));

        assertEquals(1, cambios.size());
        assertEquals(MovimientoComanda.Accion.RETIRAR, cambios.get(0).accion());
        assertEquals(2, cambios.get(0).cantidad());
    }

    @Test
    public void pedidoSinCambiosNoCreaMovimientos() {
        assertTrue(ComandaMarcha.calcularDiferencias(
                List.of(detalle(41, "Pollo", 2, "")),
                List.of(detalle(41, "Pollo", 2, ""))).isEmpty());
    }

    private static DetallePedido detalle(int idPlato, String nombre, int cantidad, String comentario) {
        DetallePedido detalle = new DetallePedido();
        detalle.setIdPlato(idPlato);
        detalle.setNombre(nombre);
        detalle.setCantidad(cantidad);
        detalle.setComentario(comentario);
        detalle.setPrecioDecimal(new BigDecimal("12.50"));
        detalle.setMonedaBase("USD");
        detalle.setAplicaIva(true);
        detalle.setPrecioIncluyeIva(false);
        detalle.setIvaPorcentaje(new BigDecimal("16.00"));
        return detalle;
    }
}
