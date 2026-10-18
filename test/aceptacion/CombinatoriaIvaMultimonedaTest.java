package aceptacion;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Servicio.CalculoMonetarioPedido;
import Servicio.CalculoMonetarioPedido.CalculoLinea;
import Servicio.CalculoMonetarioPedido.CalculoPedido;
import Servicio.GeneradorPdfCierre;
import Servicio.GeneradorPdfPedido;
import Servicio.GeneradorPdfPedido.FormatoPapel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Suite Incisiva de Pruebas Combinatorias para Cálculos de IVA, Base Imponible,
 * Multimoneda (USD y VES) y Generación de Facturas/Pre-facturas/Cierres.
 */
public class CombinatoriaIvaMultimonedaTest {

    private Config config;
    private Path tempDir;
    private List<Path> archivosGenerados;

    private static final BigDecimal IVA_16 = new BigDecimal("16.00");
    private static final BigDecimal IVA_8 = new BigDecimal("8.00");

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test_combinatoria_iva_");
        archivosGenerados = new ArrayList<>();
        config = new Config();
        config.setNombre("Restaurante Combinatoria 2026");
        config.setRuc("J-99887766-5");
        config.setTelefono("0212-9999999");
        config.setDireccion("Sabana Grande, Caracas");
        config.setMensaje("¡Gracias por su visita!");
    }

    @After
    public void tearDown() throws Exception {
        for (Path p : archivosGenerados) {
            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
        }
        try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
    }

    /**
     * Prueba todas las combinatorias posibles de un producto individual:
     * - Monedas: USD, VES
     * - Condición Fiscal: Gravado (IVA 16%), Gravado Reducido (IVA 8%), Exento (0%)
     * - Modalidad: IVA incluido vs IVA excluido
     * - Tasas cambiarias: 36.5000, 40.0000, 42.1250
     * - Cantidades: 1, 3, 7
     */
    @Test
    public void testMatrizCombinatoriaPlatosIndividuales() {
        String[] monedas = {"USD", "VES"};
        BigDecimal[] porcentajesIva = {IVA_16, IVA_8, BigDecimal.ZERO};
        BigDecimal[] tasas = {new BigDecimal("36.5000"), new BigDecimal("40.0000"), new BigDecimal("42.1250")};
        int[] cantidades = {1, 3, 7};

        int combinacionesProbadas = 0;

        for (String moneda : monedas) {
            for (BigDecimal pctIva : porcentajesIva) {
                boolean esGravado = pctIva.compareTo(BigDecimal.ZERO) > 0;
                boolean[] modalidadesIncluye = esGravado ? new boolean[]{false, true} : new boolean[]{false};

                for (boolean incluyeIva : modalidadesIncluye) {
                    for (BigDecimal tasa : tasas) {
                        for (int cant : cantidades) {
                            BigDecimal precioUnit = "USD".equals(moneda) ? new BigDecimal("12.50") : new BigDecimal("450.00");

                            DetallePedido det = new DetallePedido();
                            det.setNombre("Item Comb " + moneda + " IVA" + pctIva);
                            det.setCantidad(cant);
                            det.setPrecioDecimal(precioUnit);
                            det.setMonedaBase(moneda);
                            det.setAplicaIva(esGravado);
                            det.setPrecioIncluyeIva(incluyeIva);
                            det.setIvaPorcentaje(pctIva.setScale(2, RoundingMode.HALF_UP));

                            CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(List.of(det), tasa);

                            // Invariante 1: Aditividad exacta en USD
                            BigDecimal sumaUsd = calc.baseImponibleUsd().add(calc.exentoUsd()).add(calc.ivaUsd());
                            assertEquals("Aditividad USD falló en comb: " + det.getNombre(),
                                    calc.totalUsd(), sumaUsd);

                            // Invariante 2: Aditividad exacta en VES
                            BigDecimal sumaVes = calc.baseImponibleVes().add(calc.exentoVes()).add(calc.ivaVes());
                            assertEquals("Aditividad VES falló en comb: " + det.getNombre(),
                                    calc.totalVes(), sumaVes);

                            // Invariante 3: Subtotal neto = Base + Exento
                            assertEquals(calc.subtotalUsd(), calc.baseImponibleUsd().add(calc.exentoUsd()));
                            assertEquals(calc.subtotalVes(), calc.baseImponibleVes().add(calc.exentoVes()));

                            // Invariante 4: Si es exento, base e IVA deben ser cero
                            if (!esGravado) {
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.baseImponibleUsd());
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.baseImponibleVes());
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.ivaUsd());
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.ivaVes());
                                assertTrue(calc.exentoUsd().signum() > 0);
                                assertTrue(calc.exentoVes().signum() > 0);
                            } else {
                                assertTrue(calc.baseImponibleUsd().signum() > 0);
                                assertTrue(calc.baseImponibleVes().signum() > 0);
                                assertTrue(calc.ivaUsd().signum() > 0);
                                assertTrue(calc.ivaVes().signum() > 0);
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.exentoUsd());
                                assertEquals(BigDecimal.ZERO.setScale(2), calc.exentoVes());
                            }

                            // Invariante 5: Consistencia de CalculoLinea
                            CalculoLinea lin = calc.lineas().get(0);
                            assertEquals(calc.subtotalUsd(), lin.subtotalUsd(tasa));
                            assertEquals(calc.subtotalVes(), lin.subtotalVes());

                            combinacionesProbadas++;
                        }
                    }
                }
            }
        }

        assertTrue("Debieron evaluarse al menos 80 combinaciones", combinacionesProbadas >= 80);
    }

    /**
     * Prueba comanda heterogénea masiva con mezcla de:
     * - Platos en USD con IVA excluido
     * - Platos en USD con IVA incluido
     * - Platos en USD exentos
     * - Platos en VES con IVA excluido
     * - Platos en VES con IVA incluido
     * - Platos en VES exentos
     */
    @Test
    public void testComandaHeterogeneaMultimonedaYGeneracionDocumentos() throws Exception {
        BigDecimal tasa = new BigDecimal("41.2500");
        List<DetallePedido> detalles = new ArrayList<>();

        // 1. USD gravado excluido
        DetallePedido d1 = new DetallePedido();
        d1.setId(1);
        d1.setNombre("Steak Premium (USD + IVA)");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("20.00")); // base $40.00, iva $6.40
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(IVA_16);
        detalles.add(d1);

        // 2. USD gravado incluido
        DetallePedido d2 = new DetallePedido();
        d2.setId(2);
        d2.setNombre("Combo Burguer (USD IVA inc)");
        d2.setCantidad(3);
        d2.setPrecioDecimal(new BigDecimal("11.60")); // total $34.80, base $30.00, iva $4.80
        d2.setMonedaBase("USD");
        d2.setAplicaIva(true);
        d2.setPrecioIncluyeIva(true);
        d2.setIvaPorcentaje(IVA_16);
        detalles.add(d2);

        // 3. USD exento
        DetallePedido d3 = new DetallePedido();
        d3.setId(3);
        d3.setNombre("Agua Mineral (USD Exento)");
        d3.setCantidad(4);
        d3.setPrecioDecimal(new BigDecimal("2.50")); // exento $10.00
        d3.setMonedaBase("USD");
        d3.setAplicaIva(false);
        d3.setPrecioIncluyeIva(false);
        d3.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d3);

        // 4. VES gravado excluido
        DetallePedido d4 = new DetallePedido();
        d4.setId(4);
        d4.setNombre("Arepa Reina Pepiada (VES + IVA)");
        d4.setCantidad(5);
        d4.setPrecioDecimal(new BigDecimal("200.00")); // base Bs. 1000.00, iva Bs. 160.00
        d4.setMonedaBase("VES");
        d4.setAplicaIva(true);
        d4.setPrecioIncluyeIva(false);
        d4.setIvaPorcentaje(IVA_16);
        detalles.add(d4);

        // 5. VES gravado incluido
        DetallePedido d5 = new DetallePedido();
        d5.setId(5);
        d5.setNombre("Cachapa con Queso (VES IVA inc)");
        d5.setCantidad(2);
        d5.setPrecioDecimal(new BigDecimal("580.00")); // total Bs. 1160.00, base Bs. 1000.00, iva Bs. 160.00
        d5.setMonedaBase("VES");
        d5.setAplicaIva(true);
        d5.setPrecioIncluyeIva(true);
        d5.setIvaPorcentaje(IVA_16);
        detalles.add(d5);

        // 6. VES exento
        DetallePedido d6 = new DetallePedido();
        d6.setId(6);
        d6.setNombre("Jugo Natural (VES Exento)");
        d6.setCantidad(2);
        d6.setPrecioDecimal(new BigDecimal("150.00")); // exento Bs. 300.00
        d6.setMonedaBase("VES");
        d6.setAplicaIva(false);
        d6.setPrecioIncluyeIva(false);
        d6.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d6);

        CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        // Verificar Aditividad Global
        BigDecimal totalCalculadoUsd = calc.baseImponibleUsd().add(calc.exentoUsd()).add(calc.ivaUsd());
        assertEquals("Total USD debe cuadrar aditivamente con base + exento + iva",
                calc.totalUsd(), totalCalculadoUsd);

        BigDecimal totalCalculadoVes = calc.baseImponibleVes().add(calc.exentoVes()).add(calc.ivaVes());
        assertEquals("Total VES debe cuadrar aditivamente con base + exento + iva",
                calc.totalVes(), totalCalculadoVes);

        // Verificar que la suma de subtotales netos de las líneas coincide exactamente con el Subtotal general
        BigDecimal sumaSubtotalesUsd = BigDecimal.ZERO.setScale(2);
        BigDecimal sumaSubtotalesVes = BigDecimal.ZERO.setScale(2);
        for (CalculoLinea lin : calc.lineas()) {
            sumaSubtotalesUsd = sumaSubtotalesUsd.add(lin.subtotalUsd(tasa));
            sumaSubtotalesVes = sumaSubtotalesVes.add(lin.subtotalVes());
        }
        assertEquals("Suma de subtotales de líneas debe coincidir con Subtotal USD",
                calc.subtotalUsd(), sumaSubtotalesUsd);
        assertEquals("Suma de subtotales de líneas debe coincidir con Subtotal VES",
                calc.subtotalVes(), sumaSubtotalesVes);

        // Crear pedido representativo
        Pedidos pedido = new Pedidos();
        pedido.setId(555);
        pedido.setNum_mesa(8);
        pedido.setSala("Salón VIP");
        pedido.setFecha("2026-10-17 19:00:00");
        pedido.setClienteNombre("Empresa Corporativa C.A.");
        pedido.setClienteDocumento("J-30405060-1");
        pedido.setEstado("FINALIZADO");
        pedido.setTasaCambio(tasa);
        pedido.setTotalDecimal(calc.totalUsd());
        pedido.setTotalBs(calc.totalVes());
        pedido.setSubtotal(calc.subtotalUsd());
        pedido.setSubtotalBs(calc.subtotalVes());
        pedido.setIvaMonto(calc.ivaUsd());
        pedido.setIvaBs(calc.ivaVes());
        pedido.setIvaPorcentaje(IVA_16);
        pedido.setMetodoPago("TRANSFERENCIA");

        // 1. Probar generación de Pre-Factura
        GeneradorPdfPedido generadorTicket = new GeneradorPdfPedido(tempDir, false, FormatoPapel.TICKET_80MM);
        Path pdfPreFactura = generadorTicket.generarPreFactura(pedido, config, detalles);
        archivosGenerados.add(pdfPreFactura);
        assertTrue("Pre-factura debe existir", Files.exists(pdfPreFactura));
        assertTrue("Pre-factura debe contener bytes válidos", Files.size(pdfPreFactura) > 500);

        // 2. Probar generación de Ticket Térmico 80mm
        Path pdfTicket80 = generadorTicket.generar(pedido, config, detalles);
        archivosGenerados.add(pdfTicket80);
        assertTrue("Ticket 80mm debe existir", Files.exists(pdfTicket80));
        assertTrue("Ticket 80mm debe contener bytes válidos", Files.size(pdfTicket80) > 500);

        // 3. Probar generación de Factura A4
        GeneradorPdfPedido generadorA4 = new GeneradorPdfPedido(tempDir, false, FormatoPapel.PAGINA_A4);
        Path pdfA4 = generadorA4.generar(pedido, config, detalles);
        archivosGenerados.add(pdfA4);
        assertTrue("Factura A4 debe existir", Files.exists(pdfA4));
        assertTrue("Factura A4 debe contener bytes válidos", Files.size(pdfA4) > 500);

        // 4. Probar generación de Cierre Z con desglose fiscal exacto
        CierreCaja cierreZ = new CierreCaja();
        cierreZ.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierreZ.setFecha("2026-10-17");
        cierreZ.setFechaHoraEmision("2026-10-17 23:00:00");
        cierreZ.setUsuarioEmisor("admin");
        cierreZ.setPedidosFinalizados(1);
        cierreZ.setTotalVentasUsd(calc.totalUsd());
        cierreZ.setTotalVentasBs(calc.totalVes());
        cierreZ.setSubtotalUsd(calc.subtotalUsd());
        cierreZ.setSubtotalBs(calc.subtotalVes());
        cierreZ.setIvaUsd(calc.ivaUsd());
        cierreZ.setIvaBs(calc.ivaVes());
        cierreZ.setTasaCambioReferencia(tasa);
        cierreZ.setBaseImponibleUsd(calc.baseImponibleUsd());
        cierreZ.setBaseImponibleBs(calc.baseImponibleVes());
        cierreZ.setExentoUsd(calc.exentoUsd());
        cierreZ.setExentoBs(calc.exentoVes());

        GeneradorPdfCierre generadorCierre = new GeneradorPdfCierre(tempDir);
        Path pdfCierre = generadorCierre.generar(cierreZ, config);
        archivosGenerados.add(pdfCierre);
        assertTrue("Cierre Z debe existir", Files.exists(pdfCierre));
        assertTrue("Cierre Z debe contener bytes válidos", Files.size(pdfCierre) > 500);
    }
}
