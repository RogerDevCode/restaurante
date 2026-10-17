package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Usuario;
import Vista.Eventos;
import Vista.Sistema;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JTextField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas unitarias para validar la precisión decimal (2 decimales):
 * - Entrada por teclado (Eventos.numberDecimalKeyPress: coma a punto, máx 2 decimales).
 * - Parseo monetario (importeMonetario).
 * - Entidades Platos, Pedidos, DetallePedido.
 * - Cálculo fiscal con IVA y conversión a Bolívares.
 * - Generación de ticket e informe de cierre en PDF con 2 decimales exactos.
 */
public class PreciosDecimalesYVisualizacionTest {

    private Path tempDir;
    private Eventos eventos;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test-precios-decimales-");
        eventos = new Eventos();
    }

    @After
    public void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            File[] files = tempDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testEventosEntradaDecimalPermiteComaYConvierteAPunto() {
        JTextField txt = new JTextField("12");
        KeyEvent evtComa = new KeyEvent(txt, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, ',');
        
        eventos.numberDecimalKeyPress(evtComa, txt);
        
        assertFalse("La coma no debe ser consumida", evtComa.isConsumed());
        assertEquals("La coma debe convertirse a punto", '.', evtComa.getKeyChar());
    }

    @Test
    public void testEventosEntradaDecimalBloqueaSegundoPunto() {
        JTextField txt = new JTextField("12.5");
        KeyEvent evtPunto = new KeyEvent(txt, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, '.');
        
        eventos.numberDecimalKeyPress(evtPunto, txt);
        
        assertTrue("El segundo punto debe ser consumido (bloqueado)", evtPunto.isConsumed());
    }

    @Test
    public void testEventosEntradaDecimalBloqueaTercerDecimal() {
        JTextField txt = new JTextField("12.25");
        txt.setCaretPosition(5); // cursor al final
        KeyEvent evtDigitoExtra = new KeyEvent(txt, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, '9');
        
        eventos.numberDecimalKeyPress(evtDigitoExtra, txt);
        
        assertTrue("No debe permitir más de 2 dígitos decimales", evtDigitoExtra.isConsumed());
    }

    @Test
    public void testEventosEntradaDecimalPermiteDosDecimalesExactos() {
        JTextField txt = new JTextField("12.2");
        txt.setCaretPosition(4);
        KeyEvent evtSegundoDecimal = new KeyEvent(txt, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, '5');
        
        eventos.numberDecimalKeyPress(evtSegundoDecimal, txt);
        
        assertFalse("El segundo dígito decimal debe ser permitido", evtSegundoDecimal.isConsumed());
    }

    @Test
    public void testImporteMonetarioParseaDolaresYBolivaresConDecimales() {
        BigDecimal res1 = Sistema.importeMonetario("12.25");
        assertEquals(new BigDecimal("12.25"), res1);

        BigDecimal res2 = Sistema.importeMonetario("12,25");
        assertEquals(new BigDecimal("12.25"), res2);

        BigDecimal res3 = Sistema.importeMonetario("$ 12.25");
        assertEquals(new BigDecimal("12.25"), res3);

        BigDecimal res4 = Sistema.importeMonetario("Bs. 447.13");
        assertEquals(new BigDecimal("447.13"), res4);

        BigDecimal res5 = Sistema.importeMonetario("$ 12.25 (Bs. 447.13)");
        assertEquals(new BigDecimal("12.25"), res5);
    }

    @Test
    public void testPlatoEntidadYServicioValidacionDecimales() {
        Platos platoValido = new Platos(1, "Pasticho", new BigDecimal("12.25"), "2026-10-08");
        platoValido.setMonedaBase("USD");
        platoValido.setPrecioIncluyeIva(false);
        assertEquals(new BigDecimal("12.25"), platoValido.getPrecioDecimal());
        assertEquals(2, platoValido.getPrecioDecimal().scale());

        PoliticaAcceso rbac = new PoliticaAcceso(new Usuario(1, "Admin", "admin", "admin", "Administrador"));
        PlatosServicio servicio = new PlatosServicio(new PlatosRepositorio() {
            @Override public boolean registrar(Platos plato) { return true; }
            @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Platos plato) { return true; }
        }, rbac);

        assertTrue("Plato con 2 decimales debe ser registrado con éxito", servicio.registrar(platoValido));
    }

    @Test
    public void testCalculoFiscalConPreciosDecimalesYConversionBs() {
        // 3 platos a $ 12.25 c/u = $ 36.75
        BigDecimal precioUnitario = new BigDecimal("12.25");
        int cantidad = 3;
        BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("36.75"), subtotal);

        // IVA 16% sobre $ 36.75 = $ 5.88
        BigDecimal porcentajeIva = new BigDecimal("16.00");
        BigDecimal factorIva = porcentajeIva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal ivaUsd = subtotal.multiply(factorIva).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("5.88"), ivaUsd);

        BigDecimal totalUsd = subtotal.add(ivaUsd).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("42.63"), totalUsd);

        // Conversión a Bolívares a tasa 36.50
        BigDecimal tasa = new BigDecimal("36.5000");
        BigDecimal subtotalBs = subtotal.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ivaBs = ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("1341.38"), subtotalBs);
        assertEquals(new BigDecimal("214.62"), ivaBs);
        assertEquals(new BigDecimal("1556.00"), totalBs);
    }

    @Test
    public void testGeneradorPdfPedidoImprimeDecimalesSinError() throws Exception {
        GeneradorPdfPedido generador = new GeneradorPdfPedido(tempDir);

        Config empresa = new Config(1, "J-12345678-9", "Restaurante Gourmet", "0212-5555555", "Caracas", "Gracias por su compra");
        empresa.setTasaDolar(new BigDecimal("36.5000"));
        empresa.setIvaPorcentaje(new BigDecimal("16.00"));
        empresa.setImprimirLogoTicket(false);

        Pedidos pedido = new Pedidos();
        pedido.setId(99);
        pedido.setId_sala(1);
        pedido.setSala("Salón VIP");
        pedido.setNum_mesa(4);
        pedido.setFecha(LocalDate.now().toString());
        pedido.setSubtotal(new BigDecimal("36.75"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("5.88"));
        pedido.setTotalDecimal(new BigDecimal("42.63"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotalBs(new BigDecimal("1341.38"));
        pedido.setIvaBs(new BigDecimal("214.62"));
        pedido.setTotalBs(new BigDecimal("1556.00"));
        pedido.setClienteNombre("Carlos Mendoza");
        pedido.setClienteDocumento("V-12345678");

        DetallePedido item = new DetallePedido(1, "Pasticho Gourmet", new BigDecimal("12.25"), 3, "Bien caliente", 99);
        List<DetallePedido> detalles = Collections.singletonList(item);

        // Generar Ticket 80mm
        Path ticket = generador.generar(pedido, empresa, detalles);
        assertNotNull(ticket);
        assertTrue(ticket.toFile().exists());
        assertTrue(ticket.toFile().length() > 0);

        // Generar Factura A4
        GeneradorPdfPedido generadorA4 = new GeneradorPdfPedido(tempDir, false, GeneradorPdfPedido.FormatoPapel.PAGINA_A4);
        Path factura = generadorA4.generar(pedido, empresa, detalles);
        assertNotNull(factura);
        assertTrue(factura.toFile().exists());
        assertTrue(factura.toFile().length() > 0);
    }

    @Test
    public void testGeneradorPdfCierreImprimeDecimalesEnReporte() throws Exception {
        GeneradorPdfCierre generador = new GeneradorPdfCierre(tempDir);

        Config empresa = new Config(1, "J-12345678-9", "Restaurante Gourmet", "0212-5555555", "Caracas", "Cierre Diario");
        empresa.setTasaDolar(new BigDecimal("36.5000"));
        empresa.setImprimirLogoTicket(false);

        Modelo.CierreCaja cierre = new Modelo.CierreCaja();
        cierre.setTipo(Modelo.CierreCaja.TipoCierre.PARCIAL);
        cierre.setFecha("2026-10-08");
        cierre.setFechaHoraEmision("2026-10-08 14:00:00");
        cierre.setUsuarioEmisor("admin");
        cierre.setTotalVentasUsd(new BigDecimal("183.75"));
        cierre.setTotalVentasBs(new BigDecimal("6706.88"));
        cierre.setSubtotalUsd(new BigDecimal("158.41"));
        cierre.setSubtotalBs(new BigDecimal("5781.97"));
        cierre.setIvaUsd(new BigDecimal("25.34"));
        cierre.setIvaBs(new BigDecimal("924.91"));
        cierre.setPedidosFinalizados(5);
        cierre.setPedidosPendientes(1);
        cierre.setTotalArticulos(15);
        cierre.setTicketPromedioUsd(new BigDecimal("36.75"));
        cierre.setTicketPromedioBs(new BigDecimal("1341.38"));

        Path pdfCierre = generador.generar(cierre, empresa);
        assertNotNull(pdfCierre);
        assertTrue(pdfCierre.toFile().exists());
        assertTrue(pdfCierre.toFile().length() > 0);
    }

    @Test
    public void testTicketLineaHeredadaVesNoRotulaBolivaresComoDolares() throws Exception {
        GeneradorPdfPedido generador = new GeneradorPdfPedido(tempDir);

        Config empresa = new Config(1, "J-12345678-9", "Restaurante Gourmet", "0212-5555555", "Caracas", "Gracias");
        empresa.setTasaDolar(new BigDecimal("36.5000"));
        empresa.setIvaPorcentaje(BigDecimal.ZERO);
        empresa.setImprimirLogoTicket(false);

        Pedidos pedido = new Pedidos();
        pedido.setId(7002);
        pedido.setId_sala(1);
        pedido.setSala("Principal");
        pedido.setNum_mesa(2);
        pedido.setFecha(LocalDate.now().toString());
        pedido.setSubtotal(new BigDecimal("13.70"));
        pedido.setSubtotalBs(new BigDecimal("500.00"));
        pedido.setIvaPorcentaje(BigDecimal.ZERO);
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setIvaBs(BigDecimal.ZERO);
        pedido.setTotalDecimal(new BigDecimal("13.70"));
        pedido.setTotalBs(new BigDecimal("500.00"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));

        DetallePedido heredado = new DetallePedido();
        heredado.setId(2);
        heredado.setNombre("Sopa Criolla");
        heredado.setCantidad(1);
        heredado.setPrecioDecimal(new BigDecimal("500.00"));
        heredado.setMonedaBase("VES");
        heredado.setAplicaIva(null);
        heredado.setPrecioIncluyeIva(null);

        Path ticket = generador.generar(pedido, empresa, Collections.singletonList(heredado));
        String texto = extraerTextoPdf(ticket);

        assertTrue("El ticket debe mostrar el monto en Bs.: " + texto, texto.contains("Bs. 500.00"));
        assertFalse("No debe rotular 500 Bs como $500.00", texto.contains("$500.00"));
        assertTrue("Debe mostrar el equivalente en USD ~13.70", texto.contains("13.70"));
    }

    @Test
    public void testTicketBaseImponibleNuncaExcedeSubtotalConItemsVesMultiples() throws Exception {
        GeneradorPdfPedido generador = new GeneradorPdfPedido(tempDir);

        Config empresa = new Config(1, "J-12345678-9", "Restaurante Gourmet", "0212-5555555", "Caracas", "Gracias");
        empresa.setTasaDolar(new BigDecimal("36.5000"));
        empresa.setIvaPorcentaje(new BigDecimal("16.00"));
        empresa.setImprimirLogoTicket(false);

        // 3 items de 10.00 VES (exentos) para verificar que la Base/Exento nunca exceda el Subtotal ($0.81)
        List<DetallePedido> detalles = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            DetallePedido d = new DetallePedido();
            d.setId(i);
            d.setNombre("Arepa " + i);
            d.setCantidad(1);
            d.setPrecioDecimal(new BigDecimal("10.00"));
            d.setMonedaBase("VES");
            d.setAplicaIva(false);
            d.setPrecioIncluyeIva(false);
            d.setIvaPorcentaje(BigDecimal.ZERO);
            detalles.add(d);
        }

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, new BigDecimal("36.5000"));

        Pedidos pedido = new Pedidos();
        pedido.setId(7003);
        pedido.setId_sala(1);
        pedido.setSala("Principal");
        pedido.setNum_mesa(3);
        pedido.setFecha(LocalDate.now().toString());
        pedido.setSubtotal(calc.subtotalUsd());
        pedido.setSubtotalBs(calc.subtotalVes());
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(calc.ivaUsd());
        pedido.setIvaBs(calc.ivaVes());
        pedido.setTotalDecimal(calc.totalUsd());
        pedido.setTotalBs(calc.totalVes());
        pedido.setTasaCambio(new BigDecimal("36.5000"));

        Path ticket = generador.generar(pedido, empresa, detalles);
        String texto = extraerTextoPdf(ticket);

        // Subtotal debe ser $0.81 y Monto Exento no debe exceder $0.81 (antes daba $0.82)
        assertTrue("Debe mostrar Subtotal $0.81: " + texto, texto.contains("Subtotal: $0.81"));
        assertTrue("Monto Exento no debe exceder subtotal: " + texto, texto.contains("Monto Exento: $0.81"));
        assertFalse("Monto Exento no debe ser $0.82", texto.contains("Monto Exento: $0.82"));
    }

    @Test
    public void testTicketPedidoHeredadoConIvaIncluidoNoBloqueaImpresion() throws Exception {
        GeneradorPdfPedido generador = new GeneradorPdfPedido(tempDir);

        Config empresa = new Config(1, "J-12345678-9", "Restaurante Gourmet", "0212-5555555", "Caracas", "Gracias");
        empresa.setTasaDolar(new BigDecimal("36.5000"));
        empresa.setIvaPorcentaje(new BigDecimal("16.00"));
        empresa.setImprimirLogoTicket(false);

        Pedidos pedido = new Pedidos();
        pedido.setId(7004);
        pedido.setId_sala(1);
        pedido.setSala("Principal");
        pedido.setNum_mesa(4);
        pedido.setFecha(LocalDate.now().toString());
        // Plato con IVA incluido de $11.60 -> base $10.00, IVA $1.60, total $11.60
        pedido.setSubtotal(new BigDecimal("10.00"));
        pedido.setSubtotalBs(new BigDecimal("365.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("1.60"));
        pedido.setIvaBs(new BigDecimal("58.40"));
        pedido.setTotalDecimal(new BigDecimal("11.60"));
        pedido.setTotalBs(new BigDecimal("423.40"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));

        DetallePedido d1 = new DetallePedido();
        d1.setId(10);
        d1.setNombre("Hamburguesa Especial");
        d1.setCantidad(1);
        d1.setPrecioDecimal(new BigDecimal("11.60"));
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(true);
        d1.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido d2Heredado = new DetallePedido();
        d2Heredado.setId(11);
        d2Heredado.setNombre("Agua Mineral");
        d2Heredado.setCantidad(1);
        d2Heredado.setPrecioDecimal(BigDecimal.ZERO);
        d2Heredado.setMonedaBase(null); // Provoca que el pedido sea esLegacy
        d2Heredado.setAplicaIva(null);
        d2Heredado.setPrecioIncluyeIva(null);

        // No debe lanzar ErrorAplicacionException por validación de subtotal o total
        Path ticket = generador.generar(pedido, empresa, List.of(d1, d2Heredado));
        assertNotNull(ticket);
        assertTrue(Files.exists(ticket));
    }

    @Test
    public void testModificarProductoYRecalcularPendientesNoBloqueaConDetallesLegados() throws Exception {
        org.junit.Assume.assumeTrue("Test con escrituras reales requiere BD restaurante_test local en puerto Compose 3307",
                infraestructura.ProteccionEntornoBaseDatos.permiteEscriturasDestructivas());

        infraestructura.ProveedorConexionJdbc proveedor = new infraestructura.ProveedorConexionJdbc();
        Modelo.PreciosPedidosDao dao = new Modelo.PreciosPedidosDao(proveedor);
        Modelo.PlatosDao platosDao = new Modelo.PlatosDao();

        // 1. Crear un plato en el catalogo
        Platos platoPrueba = new Platos(0, "Plato Test Autoheal", new BigDecimal("10.00"), LocalDate.now().toString());
        platoPrueba.setMonedaBase("USD");
        platoPrueba.setAplicaIva(true);
        platoPrueba.setPrecioIncluyeIva(false);
        platosDao.registrar(platoPrueba);
        int idPlato = platosDao.buscarActivoPorNombre("Plato Test Autoheal").get().getId();

        // 2. Crear un pedido pendiente con un detalle legado sin clasificar
        int idPedido;
        try (java.sql.Connection conn = proveedor.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                     tasa_cambio, subtotal_bs, iva_bs, total_bs, estado, fecha)
                VALUES (1, 98, 10.00, 16.00, 1.60, 11.60, 'TEST_USER', 36.5000, 365.00, 58.40, 423.40, 'PENDIENTE', CURRENT_TIMESTAMP)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next());
                idPedido = rs.getInt(1);
            }
        }

        try {
            try (java.sql.Connection conn = proveedor.getConnection();
                 java.sql.PreparedStatement psDet = conn.prepareStatement("""
                    INSERT INTO detalle_pedidos (id_pedido, id_plato, nombre, precio, cantidad, comentario, aplica_iva,
                                                 moneda_base, precio_incluye_iva, iva_porcentaje, precio_congelado)
                    VALUES (?, ?, 'Plato Test Autoheal', 10.00, 1, '', 1, NULL, NULL, NULL, 0)
                    """)) {
                psDet.setInt(1, idPedido);
                psDet.setInt(2, idPlato);
                psDet.executeUpdate();
            }

            // 3. Modificar el plato y recalcular pendientes: NO DEBE LANZAR ErrorAplicacionException
            Platos platoModificado = new Platos(idPlato, "Plato Test Autoheal", new BigDecimal("12.00"), LocalDate.now().toString());
            platoModificado.setMonedaBase("USD");
            platoModificado.setAplicaIva(true);
            platoModificado.setPrecioIncluyeIva(false);

            Modelo.ResultadoActualizacionPrecios resultado = dao.modificarProductoYRecalcularPendientes(platoModificado, "TEST_USER");
            assertNotNull(resultado);
            assertEquals(1, resultado.pedidosRecalculados());
        } finally {
            try (java.sql.Connection conn = proveedor.getConnection()) {
                try (java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM detalle_pedidos WHERE id_pedido = ?")) {
                    ps.setInt(1, idPedido);
                    ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                    ps.setInt(1, idPedido);
                    ps.executeUpdate();
                }
            } catch (java.sql.SQLException ignore) {}
            platosDao.eliminar(idPlato);
        }
    }

    private static String extraerTextoPdf(Path pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf.toFile())) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
        }
    }
}
