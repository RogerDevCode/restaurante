package Modelo;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class UsuarioTest {
    @Test
    public void constructorYAccesoresRepresentanDatosDeUsuario() {
        Usuario usuario = new Usuario(5, "Ana Pérez", "ana@example.test", "secreto", "Administrador");

        assertEquals(5, usuario.getId());
        assertEquals("Ana Pérez", usuario.getNombre());
        assertEquals("ana@example.test", usuario.getCorreo());
        assertEquals("secreto", usuario.getPassword());
        assertEquals("Administrador", usuario.getRol());
    }
}
