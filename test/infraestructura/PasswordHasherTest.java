package infraestructura;

import Modelo.ErrorAplicacionException;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    public void almacenaSalDistintaYVerificaSinRecuperarLaContrasena() {
        char[] contrasena = "clave-de-prueba".toCharArray();
        String primero = hasher.hash(contrasena);
        String segundo = hasher.hash(contrasena);

        assertNotEquals(primero, segundo);
        assertTrue(hasher.esHash(primero));
        assertTrue(hasher.verificar(contrasena, primero));
        assertFalse(hasher.verificar("otra-clave".toCharArray(), primero));
    }

    @Test
    public void rechazaHashDañadoEnLugarDeTratarloComoContraseñaPlana() {
        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> hasher.verificar("clave".toCharArray(), "pbkdf2-sha256$no-valido"));
    }
}
