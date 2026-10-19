package Servicio;

import Modelo.ConfigClaves;
import Modelo.ConfiguracionSistemaDao;
import Modelo.DetallePedido;
import Modelo.ComandaMarcha;
import Modelo.MovimientoComanda;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pruebas unitarias para la generación y configuración de la comanda de cocina.
 */
public class ServicioComandaCocinaTest {

    @Test
    public void testGenerarComandaCocinaGeneraPdfLegibleSinPrecios() throws Exception {
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("POLLO A LA CANASTA");
        d1.setCantidad(2);
        d1.setComentario("Bien dorado, sin ensalada");

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("PAPAS FRITAS");
        d2.setCantidad(1);
        d2.setComentario(null);

        List<DetallePedido> detalles = List.of(d1, d2);

        Path archivo = ServicioComandaCocina.generarDocumentoComanda(
                105,
                "TERRAZA",
                4,
                "Carlos Mesonero",
                detalles,
                false
        );

        assertNotNull("El archivo PDF generado no debe ser nulo", archivo);
        assertTrue("El archivo PDF de comanda debe existir", Files.isRegularFile(archivo));
        assertTrue("El archivo debe tener contenido", Files.size(archivo) > 500);

        // Limpiar archivo temporal de prueba si se desea
        archivo.toFile().deleteOnExit();
    }

    @Test
    public void testGenerarComandaCocinaActualizada() throws Exception {
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("REFRESCO COLA");
        d1.setCantidad(3);
        d1.setComentario("Con hielo");

        Path archivo = ServicioComandaCocina.generarDocumentoComanda(
                106,
                "SALÓN",
                2,
                "Ana Mesonera",
                List.of(d1),
                true
        );

        assertNotNull(archivo);
        assertTrue(Files.isRegularFile(archivo));
        archivo.toFile().deleteOnExit();
    }

    @Test
    public void testTicketIncrementalContieneSoloCantidadNueva() throws Exception {
        ComandaMarcha marcha = new ComandaMarcha(71, 107, 2, ComandaMarcha.Tipo.INCREMENTO,
                List.of(new MovimientoComanda(MovimientoComanda.Accion.PREPARAR,
                        "POLLO", 3, "", 41, "USD", null, false, false, null)));

        Path archivo = ServicioComandaCocina.generarDocumentoComanda(marcha, "SALÓN", 2, "Ana");
        archivo.toFile().deleteOnExit();

        PdfReader reader = new PdfReader(archivo.toString());
        try {
            String texto = PdfTextExtractor.getTextFromPage(reader, 1);
            assertTrue(texto, texto.contains("Marcha: #2"));
            assertTrue(texto, texto.contains("PREPARAR [3]"));
            assertTrue(texto, texto.contains("POLLO"));
            assertFalse("No debe reimprimir la cantidad anterior", texto.contains("PREPARAR [5]"));
        } finally {
            reader.close();
        }
    }

    @Test
    public void testTicketDeAjusteIdentificaProductosQueDebenRetirarse() throws Exception {
        ComandaMarcha marcha = new ComandaMarcha(72, 108, 3, ComandaMarcha.Tipo.AJUSTE,
                List.of(new MovimientoComanda(MovimientoComanda.Accion.RETIRAR,
                        "PAPAS", 1, "", 42, "USD", null, false, false, null)));

        Path archivo = ServicioComandaCocina.generarDocumentoComanda(marcha, "SALÓN", 2, "Ana");
        archivo.toFile().deleteOnExit();

        PdfReader reader = new PdfReader(archivo.toString());
        try {
            String texto = PdfTextExtractor.getTextFromPage(reader, 1);
            assertTrue(texto, texto.contains("AJUSTE DE COMANDA"));
            assertTrue(texto, texto.contains("RETIRAR [1]"));
            assertTrue(texto, texto.contains("PAPAS"));
        } finally {
            reader.close();
        }
    }

    @Test
    public void testClaveConfiguracionImprimirComanda() {
        assertEquals("imprimir_comanda_cocina", ConfigClaves.IMPRIMIR_COMANDA_COCINA);
    }
}
