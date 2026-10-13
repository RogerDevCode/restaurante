package Vista;

import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.*;

/** Validacion y normalizacion de pesos (sin Swing ni BD). */
public class GestorAnchosTablaTest {

    @Test
    public void testValidoPasaYNormailza() {
        Optional<double[]> r = GestorAnchosTabla.normalizar(new double[]{0.2, 0.3, 0.5}, 3);
        assertTrue(r.isPresent());
        assertArrayEquals(new double[]{0.2, 0.3, 0.5}, r.get(), 1e-9);
    }

    @Test
    public void testDerivaAceptableSeNormalizaASumaUno() {
        Optional<double[]> r = GestorAnchosTabla.normalizar(new double[]{0.2, 0.3, 0.55}, 3);
        assertTrue(r.isPresent());
        double suma = 0;
        for (double v : r.get()) {
            suma += v;
        }
        assertEquals(1.0, suma, 1e-9);
    }

    @Test
    public void testRechaza() {
        assertFalse(GestorAnchosTabla.normalizar(null, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{0.5, 0.5}, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{0.0, 0.5, 0.5}, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{1.5, -0.5, 1.0}, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{0.1, 0.1, 0.1}, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{0.7, 0.7, 0.7}, 3).isPresent());
        assertFalse(GestorAnchosTabla.normalizar(new double[]{0.5, 0.5}, 0).isPresent());
    }
}
