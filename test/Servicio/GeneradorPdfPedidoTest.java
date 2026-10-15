package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GeneradorPdfPedidoTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void propagaYRegistraUnaVezElFalloDeEscrituraDelPdf() throws Exception {
        Path directorioBloqueado = temporal.newFile("no-es-directorio").toPath();
        Logger logger = Logger.getLogger(ErrorAplicacionException.class.getName());
        CapturadorLogs capturador = new CapturadorLogs();
        Level nivelAnterior = logger.getLevel();
        boolean padresAnteriores = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(capturador);
        try {
            ErrorAplicacionException error = org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                    () -> new GeneradorPdfPedido(directorioBloqueado)
                            .generar(pedidoValido(), configuracionValida(), detalleValido()));

            assertTrue(error.getCause() instanceof IOException);
            assertEquals(1, capturador.registros.size());
            assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
            assertSame(error.getCause(), capturador.registros.get(0).getThrown());
        } finally {
            logger.removeHandler(capturador);
            logger.setLevel(nivelAnterior);
            logger.setUseParentHandlers(padresAnteriores);
        }
    }

    @Test
    public void generaPdfEnDestinoInyectadoSinAbrirAplicacionExterna() throws Exception {
        Path destino = temporal.newFolder("pdf").toPath();
        Path archivo = new GeneradorPdfPedido(destino).generar(pedidoValido(), configuracionValida(), detalleValido());

        assertEquals(destino.resolve("pedido-42.pdf"), archivo);
        assertTrue(Files.isRegularFile(archivo));
        assertTrue(Files.size(archivo) > 100);
        String encabezado = new String(Files.readAllBytes(archivo), 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", encabezado);
    }

    @Test
    public void rechazaPedidoSinDetallesAntesDeCrearArchivo() throws Exception {
        Path destino = temporal.newFolder("sin-detalles").toPath();

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new GeneradorPdfPedido(destino).generar(pedidoValido(), configuracionValida(), null));

        assertFalse(Files.exists(destino.resolve("pedido-42.pdf")));
    }

    @Test
    public void pdfContieneMetodoPagoEnEncabezadoYCierre() throws Exception {
        Path destino = temporal.newFolder("pdf-metodo-pago").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setMetodoPago("TRANSFERENCIA");
        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), detalleValido());

        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("El PDF debe contener 'TRANSFERENCIA'", textoPdf.contains("TRANSFERENCIA"));
    }

    @Test
    public void pdfUsaEfectivoComoMetodoPagoPorDefecto() throws Exception {
        Path destino = temporal.newFolder("pdf-efectivo").toPath();
        // El pedidoValido() no setea metodoPago, por lo tanto usa el default EFECTIVO
        Path archivo = new GeneradorPdfPedido(destino).generar(pedidoValido(), configuracionValida(), detalleValido());

        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("El PDF debe contener 'EFECTIVO' como método de pago por defecto", textoPdf.contains("EFECTIVO"));
    }

    @Test
    public void pdfInfiereTasaDeTotalBsYTotalUsdCuandoNoHayTasaEnPedido() throws Exception {
        Path destino = temporal.newFolder("pdf-tasa-inferida").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setTasaCambio(null);
        pedido.setTotalDecimal(new BigDecimal("10.00"));
        pedido.setTotalBs(new BigDecimal("370.00"));

        Config config = configuracionValida();
        config.setTasaDolar(null); // Sin tasa configurada

        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Hamburguesa Simple");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("10.00"));
        detalle.setAplicaIva(false);

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, config, java.util.List.of(detalle));
        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("Debe inferir la tasa 37.0000", textoPdf.contains("37.0000"));
    }

    @Test
    public void pdfMuestraMontoExentoInclusoConIvaCero() throws Exception {
        Path destino = temporal.newFolder("pdf-exento-iva-cero").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setIvaPorcentaje(BigDecimal.ZERO);
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setSubtotal(new BigDecimal("12.50"));
        pedido.setTotalDecimal(new BigDecimal("12.50"));

        DetallePedido detalle = detalleValido().getFirst();
        detalle.setAplicaIva(false);

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), java.util.List.of(detalle));
        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("Debe contener Monto Exento", textoPdf.contains("Monto Exento"));
    }

    @Test
    public void pdfFallaSiNoHayTasaEnPedidoNiTotalesNiConfiguracion() throws Exception {
        Path destino = temporal.newFolder("pdf-sin-tasa").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setTasaCambio(null);
        pedido.setTotalBs(null);

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new GeneradorPdfPedido(destino).generar(pedido, null, detalleValido()));
    }

    @Test
    public void pdfInfiereTasaDeHistoricosDeIva() throws Exception {
        Path destino = temporal.newFolder("pdf-tasa-iva").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setTasaCambio(null);
        pedido.setTotalBs(null);
        pedido.setSubtotalBs(null);
        pedido.setSubtotal(new BigDecimal("12.50"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("2.00"));
        pedido.setTotalDecimal(new BigDecimal("14.50"));
        pedido.setIvaBs(new BigDecimal("74.00")); // 74 / 2 = 37.0000

        Config config = configuracionValida();
        config.setTasaDolar(null);

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, config, detalleValido());
        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("Debe inferir tasa 37.0000 a partir del desglose de IVA", textoPdf.contains("37.0000"));
    }

    @Test
    public void pdfInfiereTasaDeSubtotal() throws Exception {
        Path destino = temporal.newFolder("pdf-tasa-subtotal").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setTasaCambio(null);
        pedido.setTotalBs(null);
        pedido.setIvaBs(null);
        pedido.setIvaPorcentaje(BigDecimal.ZERO);
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setSubtotal(new BigDecimal("12.50"));
        pedido.setSubtotalBs(new BigDecimal("462.50")); // 462.50 / 12.50 = 37.0000
        pedido.setTotalDecimal(new BigDecimal("12.50"));

        Config config = configuracionValida();
        config.setTasaDolar(null);

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, config, detalleValido());
        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("Debe inferir tasa 37.0000 a partir del subtotal", textoPdf.contains("37.0000"));
    }

    @Test
    public void pdfInfiereTasaDeMismaFechaConResolvedor() throws Exception {
        Path destino = temporal.newFolder("pdf-tasa-fecha").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setEstado("FINALIZADO");
        pedido.setFecha("2026-05-18 12:00:00");
        pedido.setTasaCambio(null);
        pedido.setTotalBs(null);
        pedido.setIvaBs(null);
        pedido.setSubtotalBs(null);

        GeneradorPdfPedido generador = new GeneradorPdfPedido(
                destino, false, GeneradorPdfPedido.FormatoPapel.TICKET_80MM,
                (fecha, id) -> "2026-05-18 12:00:00".equals(fecha) ? new BigDecimal("38.5000") : null);

        Path archivo = generador.generar(pedido, configuracionValida(), detalleValido());
        assertTrue(Files.isRegularFile(archivo));
        String textoPdf = extraerTextoPdf(archivo);
        assertTrue("Debe inferir la tasa 38.5000 a partir de registros de su misma fecha", textoPdf.contains("38.5000"));
    }

    @Test
    public void pdfRechazaAplicarTasaActualAPedidoHistoricoSinRegistro() throws Exception {
        Path destino = temporal.newFolder("pdf-rechaza-historico").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setEstado("FINALIZADO");
        pedido.setFecha("2022-05-18");
        pedido.setTasaCambio(null);
        pedido.setTotalBs(null);
        pedido.setIvaBs(null);
        pedido.setSubtotalBs(null);

        // Sin tasa histórica para esa fecha
        GeneradorPdfPedido generador = new GeneradorPdfPedido(
                destino, false, GeneradorPdfPedido.FormatoPapel.TICKET_80MM,
                (fecha, id) -> null);

        Config config = configuracionValida();
        config.setTasaDolar(new BigDecimal("50.0000")); // Tasa de HOY no debe ser usada en histórico

        ErrorAplicacionException error = org.junit.Assert.assertThrows(
                ErrorAplicacionException.class,
                () -> generador.generar(pedido, config, detalleValido()));

        assertTrue(error.getMessage().contains("pedido histórico"));
        assertTrue(error.getMessage().contains("integridad contable y fiscal"));
    }

    @Test
    public void pdfTicketUsdExcluido() throws Exception {
        Path destino = temporal.newFolder("pdf-usd-excluido").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setId(101);
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("3.20"));
        pedido.setTotalDecimal(new BigDecimal("23.20"));
        pedido.setSubtotalBs(new BigDecimal("730.00"));
        pedido.setIvaBs(new BigDecimal("116.80"));
        pedido.setTotalBs(new BigDecimal("846.80"));

        DetallePedido d = new DetallePedido();
        d.setNombre("Hamburguesa Doble");
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(2);
        d.setMonedaBase("USD");
        d.setAplicaIva(true);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(new BigDecimal("16.00"));

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), List.of(d));
        assertTrue(Files.isRegularFile(archivo));
        String texto = extraerTextoPdf(archivo);

        assertTrue("Debe mostrar precio unitario USD", texto.contains("$10.00"));
        assertTrue("Debe mostrar precio unitario Bs", texto.contains("365.00"));
        assertTrue("Debe mostrar subtotal", texto.contains("Subtotal: $20.00 (Bs. 730.00)"));
        assertTrue("Debe mostrar IVA", texto.contains("IVA (16.00%): $3.20 (Bs. 116.80)"));
        assertTrue("Debe mostrar Total USD", texto.contains("Total USD: $23.20"));
        assertTrue("Debe mostrar TOTAL A PAGAR Bs", texto.contains("TOTAL A PAGAR (Bs.): Bs. 846.80"));
    }

    @Test
    public void pdfTicketVesIncluido60ConTotal60() throws Exception {
        Path destino = temporal.newFolder("pdf-ves-incluido-60").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setId(102);
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotal(new BigDecimal("1.42"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("0.23"));
        pedido.setTotalDecimal(new BigDecimal("1.64"));
        pedido.setSubtotalBs(new BigDecimal("51.72"));
        pedido.setIvaBs(new BigDecimal("8.28"));
        pedido.setTotalBs(new BigDecimal("60.00"));

        DetallePedido d = new DetallePedido();
        d.setNombre("Almuerzo Ejecutivo");
        d.setPrecioDecimal(new BigDecimal("60.00"));
        d.setCantidad(1);
        d.setMonedaBase("VES");
        d.setAplicaIva(true);
        d.setPrecioIncluyeIva(true);
        d.setIvaPorcentaje(new BigDecimal("16.00"));

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), List.of(d));
        assertTrue(Files.isRegularFile(archivo));
        String texto = extraerTextoPdf(archivo);

        assertTrue("Debe mostrar precio unitario en Bs", texto.contains("60.00"));
        assertTrue("Debe desglosar base imponible calculada hacia atras", texto.contains("Subtotal: $1.42 (Bs. 51.72)"));
        assertTrue("Debe desglosar IVA calculado hacia atras", texto.contains("IVA (16.00%): $0.23 (Bs. 8.28)"));
        assertTrue("Debe mostrar total en USD referencial", texto.contains("Total USD: $1.64"));
        assertTrue("El total acordado en VES no se altera por impuestos", texto.contains("TOTAL A PAGAR (Bs.): Bs. 60.00"));
    }

    @Test
    public void pdfTicketExento() throws Exception {
        Path destino = temporal.newFolder("pdf-exento").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setId(103);
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotal(new BigDecimal("15.00"));
        pedido.setIvaPorcentaje(BigDecimal.ZERO);
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setTotalDecimal(new BigDecimal("15.00"));
        pedido.setSubtotalBs(new BigDecimal("547.50"));
        pedido.setIvaBs(BigDecimal.ZERO);
        pedido.setTotalBs(new BigDecimal("547.50"));

        DetallePedido d = new DetallePedido();
        d.setNombre("Arepa Reina Pepiada");
        d.setPrecioDecimal(new BigDecimal("15.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), List.of(d));
        assertTrue(Files.isRegularFile(archivo));
        String texto = extraerTextoPdf(archivo);

        assertTrue("Debe contener Monto Exento", texto.contains("Monto Exento: $15.00 (Bs. 547.50)"));
        assertFalse("No debe contener IVA", texto.contains("IVA ("));
        assertTrue("Debe mostrar Total USD", texto.contains("Total USD: $15.00"));
        assertTrue("Debe mostrar TOTAL A PAGAR Bs", texto.contains("TOTAL A PAGAR (Bs.): Bs. 547.50"));
    }

    @Test
    public void pdfTicketMixtoUsdYVes() throws Exception {
        Path destino = temporal.newFolder("pdf-mixto").toPath();
        Pedidos pedido = pedidoValido();
        pedido.setId(104);
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotal(new BigDecimal("11.42"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("1.83"));
        pedido.setTotalDecimal(new BigDecimal("13.24"));
        pedido.setSubtotalBs(new BigDecimal("416.72"));
        pedido.setIvaBs(new BigDecimal("66.68"));
        pedido.setTotalBs(new BigDecimal("483.40"));

        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Pizza Pepperoni");
        d1.setPrecioDecimal(new BigDecimal("10.00"));
        d1.setCantidad(1);
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Jugo Natural");
        d2.setPrecioDecimal(new BigDecimal("60.00"));
        d2.setCantidad(1);
        d2.setMonedaBase("VES");
        d2.setAplicaIva(true);
        d2.setPrecioIncluyeIva(true);
        d2.setIvaPorcentaje(new BigDecimal("16.00"));

        Path archivo = new GeneradorPdfPedido(destino).generar(pedido, configuracionValida(), List.of(d1, d2));
        assertTrue(Files.isRegularFile(archivo));
        String texto = extraerTextoPdf(archivo);

        assertTrue("Debe mostrar subtotal combinado", texto.contains("Subtotal: $11.42 (Bs. 416.72)"));
        assertTrue("Debe mostrar IVA combinado", texto.contains("IVA (16.00%): $1.83 (Bs. 66.68)"));
        assertTrue("Debe mostrar Total USD combinado", texto.contains("Total USD: $13.24"));
        assertTrue("Debe mostrar Total Bs combinado", texto.contains("TOTAL A PAGAR (Bs.): Bs. 483.40"));
    }

    private String extraerTextoPdf(Path archivo) throws IOException {
        PdfReader reader = new PdfReader(archivo.toAbsolutePath().toString());
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(PdfTextExtractor.getTextFromPage(reader, i));
        }
        reader.close();
        return sb.toString();
    }

    private Pedidos pedidoValido() {
        Pedidos pedido = new Pedidos();
        pedido.setId(42);
        pedido.setNum_mesa(4);
        pedido.setFecha("2026-10-05");
        pedido.setTotalDecimal(new BigDecimal("12.50"));
        pedido.setSala("Principal");
        pedido.setUsuario("Operador");
        return pedido;
    }

    private Config configuracionValida() {
        return new Config(1, "123", "Restaurante de prueba", "+56 9", "Santiago", "Gracias por su compra");
    }

    private java.util.List<DetallePedido> detalleValido() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato de prueba");
        detalle.setPrecioDecimal(new BigDecimal("12.50"));
        detalle.setCantidad(1);
        return java.util.Collections.singletonList(detalle);
    }

    private static final class CapturadorLogs extends Handler {
        private final List<LogRecord> registros = new ArrayList<>();

        @Override
        public void publish(LogRecord registro) {
            if (registro != null && isLoggable(registro)) {
                registros.add(registro);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
