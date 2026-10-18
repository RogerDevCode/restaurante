package Servicio;

import Modelo.ConfigClaves;
import Modelo.ConfiguracionSistemaDao;
import Modelo.DetallePedido;
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
    public void testClaveConfiguracionImprimirComanda() {
        assertEquals("imprimir_comanda_cocina", ConfigClaves.IMPRIMIR_COMANDA_COCINA);
    }
}
