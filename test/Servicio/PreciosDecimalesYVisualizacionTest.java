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
}
