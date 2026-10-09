package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Suite de pruebas para la generación de tickets de 80 mm y el servicio de impresión.
 */
public class ServicioImpresionTicketTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void generadorPdfGeneraDimensionesExactasParaTickera80mm() throws Exception {
        Path destino = temporal.newFolder("tickets-80mm").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(destino);

        assertEquals(GeneradorPdfPedido.FormatoPapel.TICKET_80MM, generador.getFormatoPapel());

        Config config = new Config(1, "J-30123456-7", "Pizzería Nápoles 2026", "0212-9998877", "Chacao - Caracas", "¡Gracias por su preferencia!");
        config.setTasaDolar(new BigDecimal("36.5000"));
        config.setIvaPorcentaje(new BigDecimal("16.00"));

        Pedidos pedido = new Pedidos();
        pedido.setId(501);
        pedido.setFecha("2026-10-07 16:15");
        pedido.setSala("Terraza");
        pedido.setNum_mesa(3);
        pedido.setUsuario("Mesero Carlos");
        pedido.setClienteNombre("Alejandro Gómez");
        pedido.setClienteDocumento("V-19876543");
        pedido.setMetodoPago("PAGO_MOVIL");
        pedido.setSubtotal(new BigDecimal("10.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("1.60"));
        pedido.setTotalDecimal(new BigDecimal("11.60"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotalBs(new BigDecimal("365.00"));
        pedido.setIvaBs(new BigDecimal("58.40"));
        pedido.setTotalBs(new BigDecimal("423.40"));

        List<DetallePedido> detalles = Collections.singletonList(
                new DetallePedido(1, "Pizza Margarita Mediana", new BigDecimal("10.00"), 1, "Sin cebolla", 501)
        );

        Path archivoTicket = generador.generar(pedido, config, detalles);

        assertTrue("El archivo debe existir", Files.exists(archivoTicket));
        assertTrue("El archivo debe tener contenido", Files.size(archivoTicket) > 500);

        // Validar geometría exacta de 80 mm (227 puntos tipográficos)
        PdfReader reader = new PdfReader(archivoTicket.toAbsolutePath().toString());
        assertEquals(1, reader.getNumberOfPages());
        com.itextpdf.text.Rectangle pageSize = reader.getPageSize(1);
        assertEquals("El ancho del PDF debe ser 80mm (227pt)", 227.0f, pageSize.getWidth(), 1.0f);
        assertTrue("La altura debe ser continua y suficiente para el contenido", pageSize.getHeight() >= 360f);

        // Validar contenido del ticket
        String texto = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue("Debe contener nombre de la empresa", texto.contains("Pizzería Nápoles 2026"));
        assertTrue("Debe contener RIF", texto.contains("J-30123456-7"));
        assertTrue("Debe contener N° Pedido", texto.contains("N° Pedido: 501"));
        assertTrue("Debe contener Sala y Mesa", texto.contains("Terraza"));
        assertTrue("Debe contener Cliente", texto.contains("Alejandro Gómez"));
        assertTrue("Debe contener Método de pago", texto.contains("PAGO_MOVIL"));
        assertTrue("Debe contener Producto", texto.contains("Pizza Margarita Mediana"));
        assertTrue("Debe contener desglose bimonetario", texto.contains("TOTAL A PAGAR (Bs.): Bs. 423.40"));
        assertTrue("Debe contener Total USD", texto.contains("Total USD: $11.60"));
        assertTrue("Debe contener Mensaje de cortesía", texto.contains("¡Gracias por su preferencia!"));
    }

    @Test
    public void servicioImpresionListaImpresorasYObtienePredeterminadaSinExcepcion() {
        List<String> impresoras = ServicioImpresionTicket.listarImpresorasDisponibles();
        assertNotNull("La lista de impresoras no debe ser nula", impresoras);

        String predeterminada = ServicioImpresionTicket.obtenerImpresoraPredeterminada();
        // Puede ser null si no hay impresoras en el entorno de pruebas CI/Linux sin CUPS, pero no debe fallar
        System.out.println("Impresora predeterminada detectada: " + predeterminada);
        System.out.println("Total impresoras disponibles: " + impresoras.size());
    }

    @Test
    public void servicioImpresionRespetaConfiguracionDeEntorno() {
        System.setProperty("IMPRESORA_TICKETS", "Tickera-Caja-80mm");
        System.setProperty("ACCION_IMPRESION", "IMPRIMIR");
        try {
            assertEquals("Tickera-Caja-80mm", ServicioImpresionTicket.obtenerImpresoraConfigurada());
            assertEquals("IMPRIMIR", ServicioImpresionTicket.obtenerAccionConfigurada());
        } finally {
            System.clearProperty("IMPRESORA_TICKETS");
            System.clearProperty("ACCION_IMPRESION");
        }

        // Valores cuando no hay System.properties: lee .env (XP-80T) o DEFAULT
        String conf = ServicioImpresionTicket.obtenerImpresoraConfigurada();
        assertTrue("Debe leer la configuración de .env (XP-80T) o DEFAULT", "XP-80T".equals(conf) || "DEFAULT".equals(conf));
        assertEquals("IMPRIMIR", ServicioImpresionTicket.obtenerAccionConfigurada());
    }

    @Test
    public void servicioImpresionToleraRutaInexistenteSinLanzarError() throws Exception {
        // No debe lanzar RuntimeException ante archivo inexistente
        ServicioImpresionTicket.procesarSalida(Path.of("archivo-inexistente.pdf"));
        assertFalse(ServicioImpresionTicket.imprimirEnWindows(Path.of("archivo-inexistente.pdf"), "POS-80"));
    }

    @Test
    public void resolverNombreRealToleraNullVacioYDefault() {
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora(null));
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora("   "));
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora("DEFAULT"));
        assertNull(ServicioImpresionTicket.resolverNombreRealImpresora("XP-80T"));
    }

    @Test
    public void ticketConLogoDeshabilitadoGeneraPdfLimpioSinLogo() throws Exception {
        Path destino = temporal.newFolder("tickets-sin-logo").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(destino);

        Config config = new Config(1, "J-30123456-7", "Restaurante Sin Logo", "0212-0000000", "Caracas", "Gracias");
        config.setImprimirLogoTicket(false);

        Pedidos pedido = new Pedidos();
        pedido.setId(777);
        pedido.setFecha("2026-10-07 17:00");
        pedido.setSala("Bar");
        pedido.setNum_mesa(1);
        pedido.setUsuario("Mesero");
        pedido.setClienteNombre("Pedro Pérez");
        pedido.setClienteDocumento("V-12345678");
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("3.20"));
        pedido.setTotalDecimal(new BigDecimal("23.20"));
        pedido.setTotalBs(new BigDecimal("846.80"));

        List<DetallePedido> detalles = Collections.singletonList(
                new DetallePedido(1, "Hamburguesa Doble", new BigDecimal("20.00"), 1, "", 777)
        );

        Path archivo = generador.generar(pedido, config, detalles);
        assertTrue(Files.exists(archivo));

        PdfReader reader = new PdfReader(archivo.toAbsolutePath().toString());
        assertEquals(1, reader.getNumberOfPages());
        String texto = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue(texto.contains("Restaurante Sin Logo"));
        assertTrue(texto.contains("Pedro Pérez"));
        assertTrue(texto.contains("Hamburguesa Doble"));
    }

    @Test
    public void cierreConLogoDeshabilitadoGeneraPdfLimpioSinLogo() throws Exception {
        Path destino = temporal.newFolder("cierres-sin-logo").toPath();
        GeneradorPdfCierre generador = new GeneradorPdfCierre(destino);

        Config config = new Config(1, "J-30123456-7", "Restaurante Sin Logo", "0212-0000000", "Caracas", "Gracias");
        config.setImprimirLogoTicket(false);

        Modelo.CierreCaja cierre = new Modelo.CierreCaja();
        cierre.setTipo(Modelo.CierreCaja.TipoCierre.PARCIAL);
        cierre.setFecha("2026-10-07");
        cierre.setFechaHoraEmision("2026-10-07 18:00:00");
        cierre.setUsuarioEmisor("Cajero");
        cierre.setTotalVentasUsd(new BigDecimal("100.00"));
        cierre.setTotalVentasBs(new BigDecimal("3650.00"));
        cierre.setSubtotalUsd(new BigDecimal("86.21"));
        cierre.setSubtotalBs(new BigDecimal("3146.67"));
        cierre.setIvaUsd(new BigDecimal("13.79"));
        cierre.setIvaBs(new BigDecimal("503.33"));

        Path archivo = generador.generar(cierre, config);
        assertTrue(Files.exists(archivo));

        PdfReader reader = new PdfReader(archivo.toAbsolutePath().toString());
        assertEquals(1, reader.getNumberOfPages());
        String texto = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue(texto.contains("CORTE X - CIERRE PARCIAL"));
        assertTrue(texto.contains("Restaurante Sin Logo"));
        assertTrue(texto.contains("$ 100.00"));
    }
}
