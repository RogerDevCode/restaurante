package aceptacion;

import Modelo.*;
import Servicio.*;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Suite de Certificación de Sincronización Cuádruple Definitiva:
 * Pantalla (Memoria) ≡ Base de Datos MySQL (Persistencia) ≡ Factura/Ticket (PDF) ≡ Cierre de Caja (Z / X).
 *
 * Prueba comanda multimoneda extrema (USD gravado, USD exento, VES gravado incluido,
 * VES gravado excluido, VES exento) a tasa no trivial, persistiendo físicamente en MySQL,
 * auditando aditividad con tolerancia CERO (0.00) y verificando los textos emitidos en PDF.
 */
public class SincronizacionPantallaBdCierreTest {

    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
    private PedidosDao pedidosDao;
    private CierreCajaDao cierreDao;
    private Config config;
    private Path tempDir;
    private final List<Path> archivosGenerados = new ArrayList<>();
    private final List<Integer> pedidosCreados = new ArrayList<>();
    private String fechaJornadaTest;

    private static final BigDecimal IVA_16 = new BigDecimal("16.00");
    private static final BigDecimal TASA_CAMBIO = new BigDecimal("44.8000");

    @Before
    public void setUp() throws Exception {
        pedidosDao = new PedidosDao(proveedor);
        cierreDao = new CierreCajaDao(proveedor);
        tempDir = Files.createTempDirectory("test_sincronizacion_cuadruple_");
        fechaJornadaTest = LocalDate.now().toString();

        config = new Config();
        config.setNombre("Restaurante Sincronía 2026");
        config.setRuc("J-50123456-7");
        config.setTelefono("0212-7778899");
        config.setDireccion("Altamira, Caracas, Venezuela");
        config.setMensaje("¡Sincronía fiscal garantizada!");
        config.setTasaDolar(TASA_CAMBIO);
    }

    @After
    public void tearDown() throws Exception {
        // Limpiar registros de prueba en la BD
        try (Connection con = proveedor.getConnection()) {
            for (Integer idPed : pedidosCreados) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_saldos_movimientos WHERE id_pedido = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_pagos WHERE id_pedido = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM detalle_pedidos WHERE id_pedido = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM cierres_caja WHERE usuario_emisor = 'AuditorSincronia'")) {
                ps.executeUpdate();
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        // Limpiar archivos generados
        for (Path p : archivosGenerados) {
            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
        }
        try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
    }

    @Test
    public void testSincronizacionCuadruple_Pantalla_BD_PDF_CierreZ() throws Exception {
        // -------------------------------------------------------------
        // PREPARACIÓN DE COMANDA MIXTA EXTREMA (5 ÍTEMS FISCALES)
        // -------------------------------------------------------------
        List<DetallePedido> detalles = new ArrayList<>();

        // 1. USD Gravado 16% (Excluido): 2 x $12.50 = $25.00 Base
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Parrilla Premium (USD + IVA)");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("12.50"));
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(IVA_16);
        detalles.add(d1);

        // 2. USD Exento: 3 x $2.50 = $7.50 Exento
        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Agua Mineral (USD Exento)");
        d2.setCantidad(3);
        d2.setPrecioDecimal(new BigDecimal("2.50"));
        d2.setMonedaBase("USD");
        d2.setAplicaIva(false);
        d2.setPrecioIncluyeIva(false);
        d2.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d2);

        // 3. VES Gravado 16% (IVA Incluido): 1 x Bs. 1160.00 -> Base Bs. 1000.00, IVA Bs. 160.00
        DetallePedido d3 = new DetallePedido();
        d3.setNombre("Pizza Familiar (VES IVA inc)");
        d3.setCantidad(1);
        d3.setPrecioDecimal(new BigDecimal("1160.00"));
        d3.setMonedaBase("VES");
        d3.setAplicaIva(true);
        d3.setPrecioIncluyeIva(true);
        d3.setIvaPorcentaje(IVA_16);
        detalles.add(d3);

        // 4. VES Gravado 16% (IVA Excluido): 2 x Bs. 250.00 = Bs. 500.00 Base, IVA Bs. 80.00
        DetallePedido d4 = new DetallePedido();
        d4.setNombre("Arepa Mixta (VES + IVA)");
        d4.setCantidad(2);
        d4.setPrecioDecimal(new BigDecimal("250.00"));
        d4.setMonedaBase("VES");
        d4.setAplicaIva(true);
        d4.setPrecioIncluyeIva(false);
        d4.setIvaPorcentaje(IVA_16);
        detalles.add(d4);

        // 5. VES Exento: 2 x Bs. 150.00 = Bs. 300.00 Exento
        DetallePedido d5 = new DetallePedido();
        d5.setNombre("Jugo Natural (VES Exento)");
        d5.setCantidad(2);
        d5.setPrecioDecimal(new BigDecimal("150.00"));
        d5.setMonedaBase("VES");
        d5.setAplicaIva(false);
        d5.setPrecioIncluyeIva(false);
        d5.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d5);

        // =============================================================
        // PUNTA 1: PANTALLA (Cálculo autoritativo en memoria)
        // =============================================================
        CalculoMonetarioPedido.CalculoPedido calcPantalla = CalculoMonetarioPedido.calcularPedido(detalles, TASA_CAMBIO);

        BigDecimal panSubUsd = calcPantalla.subtotalUsd();
        BigDecimal panSubBs = calcPantalla.subtotalVes();
        BigDecimal panBaseUsd = calcPantalla.baseImponibleUsd();
        BigDecimal panBaseBs = calcPantalla.baseImponibleVes();
        BigDecimal panExUsd = calcPantalla.exentoUsd();
        BigDecimal panExBs = calcPantalla.exentoVes();
        BigDecimal panIvaUsd = calcPantalla.ivaUsd();
        BigDecimal panIvaBs = calcPantalla.ivaVes();
        BigDecimal panTotUsd = calcPantalla.totalUsd();
        BigDecimal panTotBs = calcPantalla.totalVes();

        // Validar aditividad matemática estricta en pantalla (tolerancia cero)
        assertEquals("Pantalla: Subtotal USD == Base + Exento", 0, panSubUsd.compareTo(panBaseUsd.add(panExUsd)));
        assertEquals("Pantalla: Subtotal Bs == Base + Exento", 0, panSubBs.compareTo(panBaseBs.add(panExBs)));
        assertEquals("Pantalla: Total USD == Subtotal + IVA", 0, panTotUsd.compareTo(panSubUsd.add(panIvaUsd)));
        assertEquals("Pantalla: Total Bs == Subtotal + IVA", 0, panTotBs.compareTo(panSubBs.add(panIvaBs)));

        // =============================================================
        // PUNTA 2: BASE DE DATOS FÍSICA (Persistencia real en MySQL)
        // =============================================================
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(1);
        pedido.setFecha(fechaJornadaTest + " 12:30:00");
        pedido.setSala("Salón Principal");
        pedido.setUsuario("AuditorSincronia");
        pedido.setClienteNombre("Cliente Auditoria");
        pedido.setClienteDocumento("V-99887766");
        pedido.setEstado("PENDIENTE");
        pedido.setTasaCambio(TASA_CAMBIO);
        pedido.setIvaPorcentaje(IVA_16);

        // Asignar los montos de cabecera calculados por el motor
        pedido.setSubtotal(panSubUsd);
        pedido.setSubtotalBs(panSubBs);
        pedido.setIvaMonto(panIvaUsd);
        pedido.setIvaBs(panIvaBs);
        pedido.setTotalDecimal(panTotUsd);
        pedido.setTotalBs(panTotBs);

        int idPedido = pedidosDao.registrarPedidoCompleto(pedido, detalles);
        assertTrue("El pedido debe ser insertado exitosamente en BD", idPedido > 0);
        pedidosCreados.add(idPedido);
        pedido.setId(idPedido);

        // Consultar directamente vía SQL físico en MySQL para auditar los datos grabados
        BigDecimal bdSubUsd, bdSubBs, bdIvaUsd, bdIvaBs, bdTotUsd, bdTotBs;
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT total, subtotal, iva_monto, total_bs, subtotal_bs, iva_bs FROM pedidos WHERE id = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("El pedido debe existir en la tabla pedidos", rs.next());
                bdTotUsd = rs.getBigDecimal("total");
                bdSubUsd = rs.getBigDecimal("subtotal");
                bdIvaUsd = rs.getBigDecimal("iva_monto");
                bdTotBs = rs.getBigDecimal("total_bs");
                bdSubBs = rs.getBigDecimal("subtotal_bs");
                bdIvaBs = rs.getBigDecimal("iva_bs");
            }
        }

        // Aserciones de coincidencia EXACTA (centavo a centavo) entre Pantalla y BD física
        assertEquals("BD: Total USD debe coincidir exactamente con Pantalla", 0, panTotUsd.compareTo(bdTotUsd));
        assertEquals("BD: Total Bs debe coincidir exactamente con Pantalla", 0, panTotBs.compareTo(bdTotBs));
        assertEquals("BD: Subtotal USD debe coincidir exactamente con Pantalla", 0, panSubUsd.compareTo(bdSubUsd));
        assertEquals("BD: Subtotal Bs debe coincidir exactamente con Pantalla", 0, panSubBs.compareTo(bdSubBs));
        assertEquals("BD: IVA USD debe coincidir exactamente con Pantalla", 0, panIvaUsd.compareTo(bdIvaUsd));
        assertEquals("BD: IVA Bs debe coincidir exactamente con Pantalla (NO inflado)", 0, panIvaBs.compareTo(bdIvaBs));

        // Invariantes fiscales de BD: Tolerancia CERO (Directriz 1 Red Team)
        assertEquals("BD: total == subtotal + iva_monto estricto", 0, bdTotUsd.compareTo(bdSubUsd.add(bdIvaUsd)));
        assertEquals("BD: total_bs == subtotal_bs + iva_bs estricto", 0, bdTotBs.compareTo(bdSubBs.add(bdIvaBs)));

        // =============================================================
        // PUNTA 3: COMPROBANTES FACTURA / TICKET (PDF generado)
        // =============================================================
        // 3.1 Ticket Térmico 80mm
        GeneradorPdfPedido genTicket = new GeneradorPdfPedido(tempDir, false, GeneradorPdfPedido.FormatoPapel.TICKET_80MM);
        Path pdfTicket = genTicket.generar(pedido, config, detalles);
        archivosGenerados.add(pdfTicket);
        assertTrue("El ticket 80mm debe generarse en disco", Files.exists(pdfTicket));

        PdfReader readerTicket = new PdfReader(pdfTicket.toString());
        String textoTicket = PdfTextExtractor.getTextFromPage(readerTicket, 1);
        readerTicket.close();

        // Validar que el Ticket 80mm contenga EXACTAMENTE los mismos montos de Pantalla y BD
        String expSubUsd = String.format(Locale.US, "%.2f", panSubUsd);
        String expSubBs = String.format(Locale.US, "%.2f", panSubBs);
        String expBaseUsd = String.format(Locale.US, "%.2f", panBaseUsd);
        String expBaseBs = String.format(Locale.US, "%.2f", panBaseBs);
        String expExUsd = String.format(Locale.US, "%.2f", panExUsd);
        String expExBs = String.format(Locale.US, "%.2f", panExBs);
        String expIvaUsd = String.format(Locale.US, "%.2f", panIvaUsd);
        String expIvaBs = String.format(Locale.US, "%.2f", panIvaBs);
        String expTotUsd = String.format(Locale.US, "%.2f", panTotUsd);
        String expTotBs = String.format(Locale.US, "%.2f", panTotBs);

        assertTrue("Ticket debe reflejar Subtotal USD: " + expSubUsd, textoTicket.contains(expSubUsd));
        assertTrue("Ticket debe reflejar Subtotal Bs: " + expSubBs, textoTicket.contains(expSubBs));
        assertTrue("Ticket debe reflejar Base Imponible USD: " + expBaseUsd, textoTicket.contains(expBaseUsd));
        assertTrue("Ticket debe reflejar Base Imponible Bs: " + expBaseBs, textoTicket.contains(expBaseBs));
        assertTrue("Ticket debe reflejar Monto Exento USD: " + expExUsd, textoTicket.contains(expExUsd));
        assertTrue("Ticket debe reflejar Monto Exento Bs: " + expExBs, textoTicket.contains(expExBs));
        assertTrue("Ticket debe reflejar IVA USD: " + expIvaUsd, textoTicket.contains(expIvaUsd));
        assertTrue("Ticket debe reflejar IVA Bs: " + expIvaBs, textoTicket.contains(expIvaBs));
        assertTrue("Ticket debe reflejar Total USD: " + expTotUsd, textoTicket.contains(expTotUsd));
        assertTrue("Ticket debe reflejar Total Bs: " + expTotBs, textoTicket.contains(expTotBs));

        // 3.2 Factura A4
        GeneradorPdfPedido genA4 = new GeneradorPdfPedido(tempDir, false, GeneradorPdfPedido.FormatoPapel.PAGINA_A4);
        Path pdfA4 = genA4.generar(pedido, config, detalles);
        archivosGenerados.add(pdfA4);
        assertTrue("La factura A4 debe generarse en disco", Files.exists(pdfA4));

        PdfReader readerA4 = new PdfReader(pdfA4.toString());
        String textoA4 = PdfTextExtractor.getTextFromPage(readerA4, 1);
        readerA4.close();

        assertTrue("Factura A4 debe reflejar Subtotal USD", textoA4.contains(expSubUsd));
        assertTrue("Factura A4 debe reflejar Subtotal Bs", textoA4.contains(expSubBs));
        assertTrue("Factura A4 debe reflejar Base Imponible USD", textoA4.contains(expBaseUsd));
        assertTrue("Factura A4 debe reflejar Base Imponible Bs", textoA4.contains(expBaseBs));
        assertTrue("Factura A4 debe reflejar Monto Exento USD", textoA4.contains(expExUsd));
        assertTrue("Factura A4 debe reflejar Monto Exento Bs", textoA4.contains(expExBs));
        assertTrue("Factura A4 debe reflejar IVA USD", textoA4.contains(expIvaUsd));
        assertTrue("Factura A4 debe reflejar IVA Bs", textoA4.contains(expIvaBs));
        assertTrue("Factura A4 debe reflejar Total USD", textoA4.contains(expTotUsd));
        assertTrue("Factura A4 debe reflejar Total Bs", textoA4.contains(expTotBs));

        // =============================================================
        // PUNTA 4: COBRO Y CONCILIACIÓN (Finalización con pagos mixtos)
        // =============================================================
        // Dividir el pago: $20.00 en Efectivo USD y el resto en Efectivo Bs
        BigDecimal pagoUsd = new BigDecimal("20.00");
        BigDecimal restanteUsd = panTotUsd.subtract(pagoUsd);
        BigDecimal pagoBs = restanteUsd.multiply(TASA_CAMBIO).setScale(2, RoundingMode.HALF_UP);

        // Registrar pago mixto en la BD
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedido_pagos (id_pedido, metodo_pago, monto, moneda, monto_recibido, monto_aplicado, cambio_devuelto, monto_usd, monto_bs, tasa_cambio, es_legado) " +
                    "VALUES (?, 'EFECTIVO_USD', ?, 'USD', ?, ?, 0.00, ?, ?, ?, 0)")) {
                ps.setInt(1, idPedido);
                ps.setBigDecimal(2, pagoUsd);
                ps.setBigDecimal(3, pagoUsd);
                ps.setBigDecimal(4, pagoUsd);
                ps.setBigDecimal(5, pagoUsd);
                ps.setBigDecimal(6, pagoUsd.multiply(TASA_CAMBIO).setScale(2, RoundingMode.HALF_UP));
                ps.setBigDecimal(7, TASA_CAMBIO);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO pedido_pagos (id_pedido, metodo_pago, monto, moneda, monto_recibido, monto_aplicado, cambio_devuelto, monto_usd, monto_bs, tasa_cambio, es_legado) " +
                    "VALUES (?, 'EFECTIVO_BS', ?, 'VES', ?, ?, 0.00, ?, ?, ?, 0)")) {
                ps.setInt(1, idPedido);
                ps.setBigDecimal(2, pagoBs);
                ps.setBigDecimal(3, pagoBs);
                ps.setBigDecimal(4, pagoBs);
                ps.setBigDecimal(5, pagoBs.divide(TASA_CAMBIO, 2, RoundingMode.HALF_UP));
                ps.setBigDecimal(6, pagoBs);
                ps.setBigDecimal(7, TASA_CAMBIO);
                ps.executeUpdate();
            }
            // Finalizar pedido
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE pedidos SET estado = 'FINALIZADO', metodo_pago = 'MIXTO', cerrada_en = ? WHERE id = ?")) {
                ps.setString(1, fechaJornadaTest + " 12:35:00");
                ps.setInt(2, idPedido);
                ps.executeUpdate();
            }
        }

        // =============================================================
        // PUNTA 5: CIERRE DE CAJA Z (Consistencia analítica y persistencia)
        // =============================================================
        CierreCaja cierre = cierreDao.consultarCierre(fechaJornadaTest, CierreCaja.TipoCierre.TOTAL, "AuditorSincronia", config);
        assertNotNull("El cierre de caja consultado no debe ser nulo", cierre);
        assertTrue("Debe registrar al menos 1 pedido finalizado", cierre.getPedidosFinalizados() >= 1);

        // Verificar consistencia del Cierre Z con la cabecera y el cálculo
        assertEquals("Cierre Z: Total USD debe coincidir con BD", 0, bdTotUsd.compareTo(cierre.getTotalVentasUsd()));
        assertEquals("Cierre Z: Total Bs debe coincidir con BD", 0, bdTotBs.compareTo(cierre.getTotalVentasBs()));
        assertEquals("Cierre Z: Subtotal USD debe coincidir con BD", 0, bdSubUsd.compareTo(cierre.getSubtotalUsd()));
        assertEquals("Cierre Z: Subtotal Bs debe coincidir con BD", 0, bdSubBs.compareTo(cierre.getSubtotalBs()));
        assertEquals("Cierre Z: IVA USD debe coincidir con BD", 0, bdIvaUsd.compareTo(cierre.getIvaUsd()));
        assertEquals("Cierre Z: IVA Bs debe coincidir con BD (sin inflar)", 0, bdIvaBs.compareTo(cierre.getIvaBs()));

        // Directriz 4 del Red Team: Sincronía algebraica exacta en Cierre Z
        assertEquals("Cierre Z: subtotal_bs == base_imponible_bs + exento_bs",
                0, cierre.getSubtotalBs().compareTo(cierre.getBaseImponibleBs().add(cierre.getExentoBs())));
        assertEquals("Cierre Z: subtotal_usd == base_imponible_usd + exento_usd",
                0, cierre.getSubtotalUsd().compareTo(cierre.getBaseImponibleUsd().add(cierre.getExentoUsd())));
        assertEquals("Cierre Z: total_bs == subtotal_bs + iva_bs",
                0, cierre.getTotalVentasBs().compareTo(cierre.getSubtotalBs().add(cierre.getIvaBs())));
        assertEquals("Cierre Z: total_usd == subtotal_usd + iva_usd",
                0, cierre.getTotalVentasUsd().compareTo(cierre.getSubtotalUsd().add(cierre.getIvaUsd())));

        // Persistir el Cierre Z físico y verificar que se guarde sin error
        GeneradorPdfCierre genPdfCierre = new GeneradorPdfCierre(tempDir);
        Path pdfCierre = genPdfCierre.generar(cierre, config);
        archivosGenerados.add(pdfCierre);
        assertTrue("El PDF del Cierre Z debe existir", Files.exists(pdfCierre));

        boolean guardadoOk = cierreDao.guardarCierre(cierre, pdfCierre.toString());
        assertTrue("El Cierre Z debe persistirse satisfactoriamente en la tabla cierres_caja", guardadoOk);
    }

    @Test
    public void testSincronizacion_PedidoCienPorCientoExento() throws Exception {
        List<DetallePedido> detalles = new ArrayList<>();

        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Agua Mineral Exenta USD");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("3.00"));
        d1.setMonedaBase("USD");
        d1.setAplicaIva(false);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d1);

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Jugo Natural Exento VES");
        d2.setCantidad(4);
        d2.setPrecioDecimal(new BigDecimal("100.00"));
        d2.setMonedaBase("VES");
        d2.setAplicaIva(false);
        d2.setPrecioIncluyeIva(false);
        d2.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d2);

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, TASA_CAMBIO);

        // En comanda 100% exenta:
        assertEquals("IVA USD debe ser 0.00", 0, calc.ivaUsd().compareTo(BigDecimal.ZERO));
        assertEquals("IVA Bs debe ser 0.00", 0, calc.ivaVes().compareTo(BigDecimal.ZERO));
        assertEquals("Base Imponible USD debe ser 0.00", 0, calc.baseImponibleUsd().compareTo(BigDecimal.ZERO));
        assertEquals("Base Imponible Bs debe ser 0.00", 0, calc.baseImponibleVes().compareTo(BigDecimal.ZERO));
        assertEquals("Exento USD debe ser igual al Total USD", 0, calc.exentoUsd().compareTo(calc.totalUsd()));
        assertEquals("Exento Bs debe ser igual al Total Bs", 0, calc.exentoVes().compareTo(calc.totalVes()));

        Pedidos ped = new Pedidos();
        ped.setId_sala(1);
        ped.setNum_mesa(2);
        ped.setFecha(fechaJornadaTest + " 13:00:00");
        ped.setSala("Salón Principal");
        ped.setUsuario("AuditorSincronia");
        ped.setClienteNombre("Cliente Exento");
        ped.setClienteDocumento("V-11223344");
        ped.setEstado("PENDIENTE");
        ped.setTasaCambio(TASA_CAMBIO);
        ped.setIvaPorcentaje(IVA_16);
        ped.setSubtotal(calc.subtotalUsd());
        ped.setSubtotalBs(calc.subtotalVes());
        ped.setIvaMonto(calc.ivaUsd());
        ped.setIvaBs(calc.ivaVes());
        ped.setTotalDecimal(calc.totalUsd());
        ped.setTotalBs(calc.totalVes());

        int id = pedidosDao.registrarPedidoCompleto(ped, detalles);
        assertTrue(id > 0);
        pedidosCreados.add(id);
        ped.setId(id);

        // Generar Ticket 80mm y verificar que indique "Monto Exento" y no IVA
        GeneradorPdfPedido gen = new GeneradorPdfPedido(tempDir, false, GeneradorPdfPedido.FormatoPapel.TICKET_80MM);
        Path pdf = gen.generar(ped, config, detalles);
        archivosGenerados.add(pdf);

        PdfReader reader = new PdfReader(pdf.toString());
        String txt = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue("Ticket debe mostrar Monto Exento", txt.contains("Monto Exento:"));
        assertFalse("Ticket NO debe mostrar IVA cuando es 100% exento", txt.contains("IVA (16.00%)"));
    }

    @Test
    public void testSincronizacion_PedidoCienPorCientoGravado() throws Exception {
        List<DetallePedido> detalles = new ArrayList<>();

        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Hamburguesa Gravada USD");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("10.00")); // $20.00 base, $3.20 IVA
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(IVA_16);
        detalles.add(d1);

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Pizza Gravada VES inc");
        d2.setCantidad(1);
        d2.setPrecioDecimal(new BigDecimal("1160.00")); // Bs. 1000 base, Bs. 160 IVA
        d2.setMonedaBase("VES");
        d2.setAplicaIva(true);
        d2.setPrecioIncluyeIva(true);
        d2.setIvaPorcentaje(IVA_16);
        detalles.add(d2);

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, TASA_CAMBIO);

        // En comanda 100% gravada:
        assertEquals("Exento USD debe ser 0.00", 0, calc.exentoUsd().compareTo(BigDecimal.ZERO));
        assertEquals("Exento Bs debe ser 0.00", 0, calc.exentoVes().compareTo(BigDecimal.ZERO));
        assertEquals("Base Imponible USD debe ser igual al Subtotal USD", 0, calc.baseImponibleUsd().compareTo(calc.subtotalUsd()));
        assertEquals("Base Imponible Bs debe ser igual al Subtotal Bs", 0, calc.baseImponibleVes().compareTo(calc.subtotalVes()));
        assertEquals("Total USD == Subtotal + IVA", 0, calc.totalUsd().compareTo(calc.subtotalUsd().add(calc.ivaUsd())));
        assertEquals("Total Bs == Subtotal + IVA", 0, calc.totalVes().compareTo(calc.subtotalVes().add(calc.ivaVes())));

        Pedidos ped = new Pedidos();
        ped.setId_sala(1);
        ped.setNum_mesa(3);
        ped.setFecha(fechaJornadaTest + " 13:30:00");
        ped.setSala("Salón Principal");
        ped.setUsuario("AuditorSincronia");
        ped.setClienteNombre("Cliente Gravado");
        ped.setClienteDocumento("V-55667788");
        ped.setEstado("PENDIENTE");
        ped.setTasaCambio(TASA_CAMBIO);
        ped.setIvaPorcentaje(IVA_16);
        ped.setSubtotal(calc.subtotalUsd());
        ped.setSubtotalBs(calc.subtotalVes());
        ped.setIvaMonto(calc.ivaUsd());
        ped.setIvaBs(calc.ivaVes());
        ped.setTotalDecimal(calc.totalUsd());
        ped.setTotalBs(calc.totalVes());

        int id = pedidosDao.registrarPedidoCompleto(ped, detalles);
        assertTrue(id > 0);
        pedidosCreados.add(id);
        ped.setId(id);

        // Generar Ticket 80mm y verificar que muestre Base Imponible e IVA, pero NO Monto Exento
        GeneradorPdfPedido gen = new GeneradorPdfPedido(tempDir, false, GeneradorPdfPedido.FormatoPapel.TICKET_80MM);
        Path pdf = gen.generar(ped, config, detalles);
        archivosGenerados.add(pdf);

        PdfReader reader = new PdfReader(pdf.toString());
        String txt = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue("Ticket debe mostrar Base Imponible", txt.contains("Base Imponible:"));
        assertTrue("Ticket debe mostrar IVA (16.00%)", txt.contains("IVA (16.00%):"));
        assertFalse("Ticket NO debe mostrar Monto Exento en comanda 100% gravada", txt.contains("Monto Exento:"));
    }

    @Test
    public void testRechazoEstricto_PersistenciaConAditividadInvalida() {
        List<DetallePedido> detalles = new ArrayList<>();
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Café");
        d1.setCantidad(1);
        d1.setPrecioDecimal(new BigDecimal("5.00"));
        d1.setMonedaBase("USD");
        d1.setAplicaIva(false);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(BigDecimal.ZERO);
        detalles.add(d1);

        Pedidos pedInvalido = new Pedidos();
        pedInvalido.setId_sala(1);
        pedInvalido.setNum_mesa(4);
        pedInvalido.setFecha(fechaJornadaTest + " 14:00:00");
        pedInvalido.setSala("Salón Principal");
        pedInvalido.setUsuario("AuditorSincronia");
        pedInvalido.setClienteNombre("Test Invalido");
        pedInvalido.setClienteDocumento("V-00000000");
        pedInvalido.setEstado("PENDIENTE");
        pedInvalido.setTasaCambio(TASA_CAMBIO);
        pedInvalido.setIvaPorcentaje(IVA_16);

        // Forzar discrepancia deliberada de 1 centavo en la cabecera (romper aditividad)
        pedInvalido.setSubtotal(new BigDecimal("5.00"));
        pedInvalido.setIvaMonto(BigDecimal.ZERO.setScale(2));
        pedInvalido.setTotalDecimal(new BigDecimal("5.01")); // 5.00 != 5.01
        pedInvalido.setSubtotalBs(new BigDecimal("224.00"));
        pedInvalido.setIvaBs(BigDecimal.ZERO.setScale(2));
        pedInvalido.setTotalBs(new BigDecimal("224.00"));

        try {
            pedidosDao.registrarPedidoCompleto(pedInvalido, detalles);
            fail("Debe rechazar la persistencia cuando total != subtotal + iva");
        } catch (ErrorAplicacionException ex) {
            assertTrue("Debe informar error de cálculo/aditividad",
                    ex.getMessage().contains("inconsistente") || ex.getMessage().contains("aditividad") || ex.getMessage().contains("total"));
        }
    }
}

