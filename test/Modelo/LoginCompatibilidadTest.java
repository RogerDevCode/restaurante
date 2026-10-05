package Modelo;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LoginCompatibilidadTest {
    @Test
    @SuppressWarnings("deprecation")
    public void tipoLegacyUsaUsuarioYConservaAliasPass() {
        login legado = new login(4, "Ana", "ana@example.test", "clave", "Administrador");

        assertEquals("clave", legado.getPassword());
        assertEquals("clave", legado.getPass());
        legado.setPass("otra-clave");
        assertEquals("otra-clave", legado.getPassword());
    }
}
