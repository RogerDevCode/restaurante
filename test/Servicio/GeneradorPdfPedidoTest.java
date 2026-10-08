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
