package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CalculoMonetarioPedidoTest {
    private static final BigDecimal TASA_40 = new BigDecimal("40.0000");
    private static final BigDecimal IVA_16 = new BigDecimal("16.00");

    @Test
    public void desgloseHaciaAtrasDePrecioVesConIvaIncluido() {
        var calculo = calcular("60.00", 1, "VES", true, true, IVA_16, TASA_40);

        assertEquals(new BigDecimal("51.72"), calculo.baseImponibleFuente());
        assertEquals(new BigDecimal("8.28"), calculo.ivaFuente());
        assertEquals(new BigDecimal("60.00"), calculo.totalFuente());
        assertEquals(new BigDecimal("60.00"), calculo.totalVes());
        assertEquals(new BigDecimal("1.50"), calculo.totalUsdReferencia());
        assertEquals(new BigDecimal("0.00"), calculo.ajusteCambioBs());
    }

    @Test
    public void agregaIvaAlPrecioUsdExcluidoYConvierteTotalAVes() {
        var calculo = calcular("10.00", 1, "USD", true, false, IVA_16, TASA_40);

        assertEquals(new BigDecimal("10.00"), calculo.baseImponibleFuente());
        assertEquals(new BigDecimal("1.60"), calculo.ivaFuente());
        assertEquals(new BigDecimal("11.60"), calculo.totalFuente());
        assertEquals(new BigDecimal("400.00"), calculo.baseImponibleVes());
        assertEquals(new BigDecimal("64.00"), calculo.ivaVes());
        assertEquals(new BigDecimal("464.00"), calculo.totalVes());
        assertEquals(new BigDecimal("11.60"), calculo.totalUsdReferencia());
    }

    @Test
    public void exentoNoGeneraBaseGravableNiIva() {
        var calculo = calcular("10.00", 1, "VES", false, false, IVA_16, TASA_40);

        assertEquals(new BigDecimal("0.00"), calculo.baseImponibleVes());
        assertEquals(new BigDecimal("10.00"), calculo.exentoVes());
        assertEquals(new BigDecimal("0.00"), calculo.ivaVes());
        assertEquals(new BigDecimal("10.00"), calculo.totalVes());
    }

    @Test
    public void tresUnidadesVesConservanTotalPactadoExacto() {
        var calculo = calcular("60.00", 3, "VES", true, true, IVA_16, TASA_40);

        assertEquals(new BigDecimal("180.00"), calculo.totalVes());
        assertEquals(new BigDecimal("155.17"), calculo.baseImponibleVes());
        assertEquals(new BigDecimal("24.83"), calculo.ivaVes());
        assertEquals(new BigDecimal("4.50"), calculo.totalUsdReferencia());
    }

    @Test
    public void importeVesImparConservaResiduoAlConvertirReferenciaUsd() {
        var calculo = calcular("60.03", 1, "VES", true, true, IVA_16, TASA_40);

        assertEquals(new BigDecimal("60.03"), calculo.totalVes());
        assertEquals(new BigDecimal("1.50"), calculo.totalUsdReferencia());
        assertEquals(new BigDecimal("0.03"), calculo.ajusteCambioBs());
    }

    @Test
    public void pedidoMixtoSumaImportesVesSinReconstruirLosPreciosFuente() {
        DetallePedido bebida = detalle("60.03", 1, "VES", true, true, IVA_16);
        DetallePedido comida = detalle("10.00", 1, "USD", true, false, IVA_16);

        var pedido = CalculoMonetarioPedido.calcularPedido(List.of(bebida, comida), TASA_40);

        assertEquals(new BigDecimal("524.03"), pedido.totalVes());
        assertEquals(new BigDecimal("13.10"), pedido.totalUsdReferencia());
        assertEquals(new BigDecimal("0.03"), pedido.ajusteCambioBs());
        assertEquals(2, pedido.lineas().size());
        assertThrows(UnsupportedOperationException.class, () -> pedido.lineas().clear());
    }

    @Test
    public void normalizaCodigoMonedaSinInferirloDesdeOtrosDatos() {
        var calculo = calcular("2.00", 1, " usd ", false, false, IVA_16, TASA_40);

        assertEquals("USD", calculo.monedaBase());
        assertEquals(new BigDecimal("80.00"), calculo.totalVes());
    }

    @Test
    public void rechazaPrecioNuloNoPositivoConMasDeDosDecimalesYFueraDeRango() {
        assertThrows(ErrorAplicacionException.class,
                () -> calcular(null, 1, "VES", true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("0.00", 1, "VES", true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("-1.00", 1, "VES", true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularLinea(new BigDecimal("1.001"), 1, "VES", true,
                        false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1000000000000.00", 1, "VES", true, false, IVA_16, TASA_40));
    }

    @Test
    public void rechazaMonedaDesconocida() {
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, null, true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "EUR", true, false, IVA_16, TASA_40));
    }

    @Test
    public void rechazaCantidadNulaLogicaNegativaOCuantiaFueraDeLimite() {
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 0, "VES", true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", -1, "VES", true, false, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + 1,
                        "VES", true, false, IVA_16, TASA_40));
    }

    @Test
    public void rechazaTasaNulaNoPositivaFueraDeEscalaYFueraDeRango() {
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, IVA_16, null));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, IVA_16, BigDecimal.ZERO));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, IVA_16, new BigDecimal("-1.0000")));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, IVA_16, new BigDecimal("1.00001")));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, IVA_16, new BigDecimal("100000000.0000")));
    }

    @Test
    public void rechazaCondicionFiscalDesconocidaIncoherenteONoRepresentable() {
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, null, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, null, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", false, true, IVA_16, TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, new BigDecimal("16.001"), TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> calcular("1.00", 1, "VES", true, false, new BigDecimal("100.01"), TASA_40));
    }

    @Test
    public void elPedidoRechazaLineasHeredadasSinMonedaOFiscalidadYListaVacia() {
        DetallePedido sinMoneda = new DetallePedido();
        sinMoneda.setPrecioDecimal(new BigDecimal("1.00"));
        sinMoneda.setCantidad(1);
        sinMoneda.setAplicaIva(Boolean.TRUE);
        sinMoneda.setPrecioIncluyeIva(Boolean.FALSE);
        sinMoneda.setIvaPorcentaje(IVA_16);

        assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularPedido(List.of(sinMoneda), TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularPedido(List.of(), TASA_40));
        assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularPedido(null, TASA_40));
    }

    @Test
    public void cadaLineaHistoricaDebeTenerCondicionFiscalClasificada() {
        DetallePedido detalle = detalle("1.00", 1, "VES", true, false, IVA_16);
        detalle.setAplicaIva(null);
        assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularPedido(List.of(detalle), TASA_40));

        DetallePedido ivaSinClasificar = detalle("1.00", 1, "VES", true, false, null);
        ErrorAplicacionException error = assertThrows(ErrorAplicacionException.class,
                () -> CalculoMonetarioPedido.calcularPedido(List.of(ivaSinClasificar), TASA_40));
        assertTrue(error.getMessage().contains("no está clasificado"));
    }

    private static CalculoMonetarioPedido.CalculoLinea calcular(
            String precio, int cantidad, String moneda, boolean aplicaIva,
            Boolean incluyeIva, BigDecimal iva, BigDecimal tasa) {
        BigDecimal monto = precio == null ? null : new BigDecimal(precio);
        return CalculoMonetarioPedido.calcularLinea(monto, cantidad, moneda, aplicaIva, incluyeIva, iva, tasa);
    }

    private static DetallePedido detalle(
            String precio, int cantidad, String moneda, boolean aplicaIva,
            Boolean incluyeIva, BigDecimal iva) {
        DetallePedido detalle = new DetallePedido(1, "Producto", new BigDecimal(precio), cantidad, "", 1);
        detalle.setMonedaBase(moneda);
        detalle.setAplicaIva(aplicaIva);
        detalle.setPrecioIncluyeIva(incluyeIva);
        detalle.setIvaPorcentaje(iva);
        return detalle;
    }
}
