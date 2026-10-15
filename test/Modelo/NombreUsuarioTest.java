package Modelo;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NombreUsuarioTest {
    @Test
    public void normalizaEspaciosYConservaAcentosUnicode() {
        assertEquals("José Pérez_2", NombreUsuario.normalizar("  José Pérez_2  "));
    }

    @Test
    public void rechazaNombresCortosYLargos() {
        assertThrows(ErrorAplicacionException.class, () -> NombreUsuario.normalizar("ab"));
        assertThrows(ErrorAplicacionException.class, () -> NombreUsuario.normalizar("x".repeat(61)));
    }

    @Test
    public void normalizaEspaciosEnLosExtremosYRechazaCaracteresNoPermitidos() {
        assertEquals("Ana", NombreUsuario.normalizar(" Ana "));
        assertThrows(ErrorAplicacionException.class, () -> NombreUsuario.normalizar("Ana@local"));
    }

}
