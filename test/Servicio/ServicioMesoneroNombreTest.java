package Servicio;

import Modelo.Mesonero;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ServicioMesoneroNombreTest {

    @Test
    public void sinColisionesMuestraSoloPrimerNombre() {
        Mesonero m1 = new Mesonero(1, "Carlos Pérez", "V-11111111", "04141234567", true, false);
        Mesonero m2 = new Mesonero(2, "María Rodríguez", "V-22222222", "04147654321", true, false);
        Mesonero m3 = new Mesonero(3, "Juan Blanco", "V-33333333", "", true, false);

        Map<Integer, String> nombres = ServicioMesoneroNombre.generarNombresVisuales(Arrays.asList(m1, m2, m3));
        assertEquals("Carlos", nombres.get(1));
        assertEquals("María", nombres.get(2));
        assertEquals("Juan", nombres.get(3));
    }

    @Test
    public void colisionPrimerNombreUsaPrimerNombreYTresLetrasApellido() {
        Mesonero m1 = new Mesonero(1, "Carlos Pérez", "V-11111111", "", true, false);
        Mesonero m2 = new Mesonero(2, "Carlos Gómez", "V-22222222", "", true, false);
        Mesonero m3 = new Mesonero(3, "Ana Silva", "V-33333333", "", true, false);

        Map<Integer, String> nombres = ServicioMesoneroNombre.generarNombresVisuales(Arrays.asList(m1, m2, m3));
        assertEquals("Carlos Pér", nombres.get(1));
        assertEquals("Carlos Góm", nombres.get(2));
        assertEquals("Ana", nombres.get(3));
    }

    @Test
    public void colisionMismoPrimerNombreYMismasTresLetrasApellidoDesempataExtendiendo() {
        Mesonero m1 = new Mesonero(1, "Carlos Romero", "V-11111111", "", true, false);
        Mesonero m2 = new Mesonero(2, "Carlos Rodríguez", "V-22222222", "", true, false);

        Map<Integer, String> nombres = ServicioMesoneroNombre.generarNombresVisuales(Arrays.asList(m1, m2));
        assertNotEquals(nombres.get(1), nombres.get(2));
        assertTrue(nombres.get(1).startsWith("Carlos Rom"));
        assertTrue(nombres.get(2).startsWith("Carlos Rod"));
    }

    @Test
    public void colisionExactaMismoNombreYApellidoDesempataConCedula() {
        Mesonero m1 = new Mesonero(1, "Carlos Pérez", "V-12345678", "", true, false);
        Mesonero m2 = new Mesonero(2, "Carlos Pérez", "V-87654321", "", true, false);

        Map<Integer, String> nombres = ServicioMesoneroNombre.generarNombresVisuales(Arrays.asList(m1, m2));
        assertNotEquals(nombres.get(1), nombres.get(2));
        assertTrue(nombres.get(1).contains("5678") || nombres.get(1).contains("#1"));
        assertTrue(nombres.get(2).contains("4321") || nombres.get(2).contains("#2"));
    }

    @Test
    public void resolverNombresVisualesDesdeStringsMapeaCorrectamente() {
        List<String> nombres = Arrays.asList("Carlos Pérez", "Carlos Gómez", "Ana Silva", "Pedro");
        Map<String, String> res = ServicioMesoneroNombre.resolverNombresVisualesDesdeStrings(nombres);

        assertEquals("Carlos Pér", res.get("Carlos Pérez"));
        assertEquals("Carlos Góm", res.get("Carlos Gómez"));
        assertEquals("Ana", res.get("Ana Silva"));
        assertEquals("Pedro", res.get("Pedro"));
    }
}
