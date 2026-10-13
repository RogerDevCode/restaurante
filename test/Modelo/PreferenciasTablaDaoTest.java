package Modelo;

import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.*;

/** Codec JSON compacto de pesos (sin BD). */
public class PreferenciasTablaDaoTest {

    @Test
    public void testRoundTrip() {
        double[] pesos = {0.25, 0.5, 0.25};
        Optional<double[]> leido = PreferenciasTablaDao.deJson(PreferenciasTablaDao.aJson(pesos));
        assertTrue(leido.isPresent());
        assertArrayEquals(pesos, leido.get(), 1e-9);
    }

    @Test
    public void testFormatoEsperado() {
        assertEquals("{\"v\":1,\"w\":[0.25,0.75]}", PreferenciasTablaDao.aJson(new double[]{0.25, 0.75}));
    }

    @Test
    public void testInvalidosDanVacio() {
        assertFalse(PreferenciasTablaDao.deJson(null).isPresent());
        assertFalse(PreferenciasTablaDao.deJson("").isPresent());
        assertFalse(PreferenciasTablaDao.deJson("sin-corchetes").isPresent());
        assertFalse(PreferenciasTablaDao.deJson("{\"v\":1,\"w\":[]}").isPresent());
        assertFalse(PreferenciasTablaDao.deJson("{\"v\":1,\"w\":[0.5,abc]}").isPresent());
        assertFalse(PreferenciasTablaDao.deJson("{\"v\":1,\"w\":[0.5,NaN]}").isPresent());
        assertFalse(PreferenciasTablaDao.deJson("{\"v\":1,\"w\":[0.5,Infinity]}").isPresent());
    }
}
