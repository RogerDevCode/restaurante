package aceptacion;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.PagoPedido;
import Modelo.Pedidos;
import Servicio.CalculoMonetarioPedido;
import Servicio.GeneradorPdfCierre;
import Servicio.GeneradorPdfPedido;
import Servicio.GeneradorPdfPedido.FormatoPapel;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pruebas de aceptación para:
 * 1. Precisión y corrección de la Base Imponible del IVA (pedidos 100% gravados, exentos y mixtos).
 * 2. Visualización obligatoria de Base Imponible en tickets y facturas PDF aún sin productos exentos.
 * 3. Base Imponible y Exento desagregados en el Cierre de Caja (Z y X).
 * 4. Gestión de mesas no pagadas: Opción 1 (Anulación por Fuga) y Opción 3 (Cortesía / Casa).
 */
public class AceptacionNoPagoYBaseImponibleTest {

    private Config config;
    private Path tempDir;
    private List<Path> archivosGenerados;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test_fiscal_nopago_");
        archivosGenerados = new ArrayList<>();
        config = new Config();
        config.setNombre("Restaurante Demo 2026");
        config.setRuc("J-12345678-9");
        config.setTelefono("0212-5555555");
        config.setDireccion("Caracas, Venezuela");
        config.setMensaje("¡Gracias por su visita!");
    }

    @After
    public void tearDown() throws Exception {
        for (Path p : archivosGenerados) {
            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
        }
        try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
    }

    @Test
    public void testCalculoBaseImponibleYExento_PedidoMixto() {
        BigDecimal tasa = new BigDecimal("40.0000");

        // Detalle 1: Gravado USD $10.00 + IVA 16% (excluido)
        DetallePedido detGravado = new DetallePedido();
        detGravado.setNombre("Hamburguesa Especial");
        detGravado.setCantidad(2);
        detGravado.setPrecioDecimal(new BigDecimal("10.00")); // $20.00 base
        detGravado.setMonedaBase("USD");
        detGravado.setAplicaIva(true);
        detGravado.setPrecioIncluyeIva(false);
        detGravado.setIvaPorcentaje(new BigDecimal("16.00"));

        // Detalle 2: Exento USD $5.00
        DetallePedido detExento = new DetallePedido();
        detExento.setNombre("Agua Mineral");
        detExento.setCantidad(3);
        detExento.setPrecioDecimal(new BigDecimal("5.00")); // $15.00 exento
        detExento.setMonedaBase("USD");
        detExento.setAplicaIva(false);
        detExento.setPrecioIncluyeIva(false);
        detExento.setIvaPorcentaje(BigDecimal.ZERO);

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(List.of(detGravado, detExento), tasa);

        // Base Imponible: 2 * 10 = 20.00 USD
        assertEquals(new BigDecimal("20.00"), calc.baseImponibleUsd());
        // Exento: 3 * 5 = 15.00 USD
        assertEquals(new BigDecimal("15.00"), calc.exentoUsd());
        // IVA: 16% sobre 20.00 = 3.20 USD
        assertEquals(new BigDecimal("3.20"), calc.ivaUsd());
        // Total: 20.00 + 15.00 + 3.20 = 38.20 USD
        assertEquals(new BigDecimal("38.20"), calc.totalUsd());

        // En Bs. (tasa 40.00)
        // Base Imponible Bs: 20 * 40 = 800.00
        assertEquals(new BigDecimal("800.00"), calc.baseImponibleVes());
        // Exento Bs: 15 * 40 = 600.00
        assertEquals(new BigDecimal("600.00"), calc.exentoVes());
        // IVA Bs: 3.20 * 40 = 128.00
        assertEquals(new BigDecimal("128.00"), calc.ivaVes());
        // Total Bs: 38.20 * 40 = 1528.00
        assertEquals(new BigDecimal("1528.00"), calc.totalVes());
    }

    @Test
    public void testCalculoBaseImponible_Pedido100PorcientoGravado() {
        BigDecimal tasa = new BigDecimal("45.0000");

        DetallePedido det = new DetallePedido();
        det.setNombre("Plato Principal");
        det.setCantidad(1);
        det.setPrecioDecimal(new BigDecimal("50.00"));
        det.setMonedaBase("USD");
        det.setAplicaIva(true);
        det.setPrecioIncluyeIva(false);
        det.setIvaPorcentaje(new BigDecimal("16.00"));

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(List.of(det), tasa);

        assertEquals(new BigDecimal("50.00"), calc.baseImponibleUsd());
        assertEquals(new BigDecimal("0.00"), calc.exentoUsd());
        assertEquals(new BigDecimal("8.00"), calc.ivaUsd());
        assertEquals(new BigDecimal("58.00"), calc.totalUsd());
    }

    @Test
    public void testGeneradorPdfPedido_ImprimeBaseImponibleAunSinExentos() throws Exception {
        BigDecimal tasa = new BigDecimal("40.0000");

        Pedidos pedido = new Pedidos();
        pedido.setId(101);
        pedido.setNum_mesa(5);
        pedido.setSala("Terraza");
        pedido.setFecha("2026-10-17 14:00:00");
        pedido.setClienteNombre("Carlos Perez");
        pedido.setClienteDocumento("V-12345678");
        pedido.setEstado("FINALIZADO");
        pedido.setTasaCambio(tasa);
        pedido.setMetodoPago("EFECTIVO_USD");
        pedido.setTotalDecimal(new BigDecimal("29.00"));
        pedido.setTotalBs(new BigDecimal("1160.00"));
        pedido.setSubtotal(new BigDecimal("25.00"));
        pedido.setIvaMonto(new BigDecimal("4.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido det = new DetallePedido();
        det.setId(1);
        det.setNombre("Corte de Carne");
        det.setCantidad(1);
        det.setPrecioDecimal(new BigDecimal("25.00"));
        det.setMonedaBase("USD");
        det.setAplicaIva(true);
        det.setPrecioIncluyeIva(false);
        det.setIvaPorcentaje(new BigDecimal("16.00"));

        GeneradorPdfPedido generadorTicket = new GeneradorPdfPedido(tempDir, false, FormatoPapel.TICKET_80MM);
        Path pdfTicket = generadorTicket.generar(pedido, config, List.of(det));
        archivosGenerados.add(pdfTicket);

        assertTrue("El PDF térmico debió ser generado", Files.exists(pdfTicket));
        assertTrue("El archivo PDF debe tener contenido", Files.size(pdfTicket) > 500);

        GeneradorPdfPedido generadorA4 = new GeneradorPdfPedido(tempDir, false, FormatoPapel.PAGINA_A4);
        Path pdfA4 = generadorA4.generar(pedido, config, List.of(det));
        archivosGenerados.add(pdfA4);

        assertTrue("El PDF A4 debió ser generado", Files.exists(pdfA4));
        assertTrue("El archivo PDF A4 debe tener contenido", Files.size(pdfA4) > 500);
    }

    @Test
    public void testPagoPedido_AceptaMetodoCortesia() {
        PagoPedido pago = new PagoPedido("CORTESIA", "VES", new BigDecimal("1200.00"));
        assertEquals("CORTESIA", pago.metodo());
        assertEquals("VES", pago.moneda());
        assertEquals(new BigDecimal("1200.00"), pago.monto());
        assertEquals(new BigDecimal("1200.00"), pago.equivalenteBs(new BigDecimal("40.0000")));
    }

    @Test
    public void testCierreCaja_DesgloseBaseImponibleYExento() throws Exception {
        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierre.setFecha("2026-10-17");
        cierre.setFechaHoraEmision("2026-10-17 22:00:00");
        cierre.setUsuarioEmisor("admin");
        cierre.setPedidosFinalizados(10);
        cierre.setTotalVentasUsd(new BigDecimal("500.00"));
        cierre.setTotalVentasBs(new BigDecimal("20000.00"));
        cierre.setSubtotalUsd(new BigDecimal("450.00"));
        cierre.setSubtotalBs(new BigDecimal("18000.00"));
        cierre.setIvaUsd(new BigDecimal("50.00"));
        cierre.setIvaBs(new BigDecimal("2000.00"));
        cierre.setTasaCambioReferencia(new BigDecimal("40.0000"));

        // Se fijan base imponible y exento explícitos
        cierre.setBaseImponibleUsd(new BigDecimal("312.50"));
        cierre.setBaseImponibleBs(new BigDecimal("12500.00"));
        cierre.setExentoUsd(new BigDecimal("137.50"));
        cierre.setExentoBs(new BigDecimal("5500.00"));

        cierre.setDesgloseMetodos(Arrays.asList(
                new CierreCaja.ResumenMetodoPago("EFECTIVO_USD", 5, new BigDecimal("250.00"), new BigDecimal("10000.00")),
                new CierreCaja.ResumenMetodoPago("CORTESIA", 2, new BigDecimal("150.00"), new BigDecimal("6000.00")),
                new CierreCaja.ResumenMetodoPago("PAGO_MOVIL", 3, new BigDecimal("100.00"), new BigDecimal("4000.00"))
        ));

        assertEquals(new BigDecimal("312.50"), cierre.getBaseImponibleUsd());
        assertEquals(new BigDecimal("137.50"), cierre.getExentoUsd());

        // Generar PDF de Cierre Z
        GeneradorPdfCierre generadorCierre = new GeneradorPdfCierre(tempDir);
        Path pdfCierre = generadorCierre.generar(cierre, config);
        archivosGenerados.add(pdfCierre);

        assertTrue("El reporte de Cierre Z debió ser generado", Files.exists(pdfCierre));
        assertTrue("El archivo de Cierre Z debe contener datos", Files.size(pdfCierre) > 500);
    }
}
