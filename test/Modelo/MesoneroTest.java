package Modelo;

import org.junit.Test;
import static org.junit.Assert.*;

public class MesoneroTest {

    @Test
    public void constructorPorDefectoInicializaCamposActivoYNoEliminado() {
        Mesonero m = new Mesonero();
        assertTrue(m.isActivo());
        assertFalse(m.isEliminado());
        assertEquals(0, m.getId());
    }

    @Test
    public void constructorConvenienteAsignaValoresCorrectos() {
        Mesonero m = new Mesonero("  Carlos Pérez  ", "  V-12345678  ", "  04141234567  ", true);
        assertEquals(0, m.getId());
        assertEquals("Carlos Pérez", m.getNombreCompleto());
        assertEquals("V-12345678", m.getCedula());
        assertEquals("04141234567", m.getTelefono());
        assertTrue(m.isActivo());
        assertFalse(m.isEliminado());
    }

    @Test
    public void constructorCompletoAsignaTodosLosCampos() {
        Mesonero m = new Mesonero(5, "Ana Gomez", "V-87654321", "04249876543", false, true);
        assertEquals(5, m.getId());
        assertEquals("Ana Gomez", m.getNombreCompleto());
        assertEquals("V-87654321", m.getCedula());
        assertEquals("04249876543", m.getTelefono());
        assertFalse(m.isActivo());
        assertTrue(m.isEliminado());
    }

    @Test
    public void gettersYSettersModificanEstado() {
        Mesonero m = new Mesonero();
        m.setId(10);
        m.setNombreCompleto("Pedro Ramirez");
        m.setCedula("V-11223344");
        m.setTelefono("04120000000");
        m.setActivo(false);
        m.setEliminado(true);

        assertEquals(10, m.getId());
        assertEquals("Pedro Ramirez", m.getNombreCompleto());
        assertEquals("V-11223344", m.getCedula());
        assertEquals("04120000000", m.getTelefono());
        assertFalse(m.isActivo());
        assertTrue(m.isEliminado());
    }

    @Test
    public void equalsYHashCodeBasadosEnIdYCedula() {
        Mesonero m1 = new Mesonero(1, "Carlos Gomez", "V-11111111", "", true, false);
        Mesonero m2 = new Mesonero(1, "Carlos Gómez", "V-11111111", "", true, false);
        Mesonero m3 = new Mesonero(2, "Carlos Gomez", "V-22222222", "", true, false);

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3);
    }
}
