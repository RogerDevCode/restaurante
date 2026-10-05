package Controlador;

import Modelo.AutenticacionRepositorio;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoginControladorTest {
    @Test
    public void coordinaAutenticacionUsuarioConRepositorioFalso() {
        Usuario usuarioEsperado = new Usuario(3, "Eva", "eva@example.test", "clave", "Asistente");
        AutenticacionRepositorio repositorio = (correo, clave) -> Optional.of(usuarioEsperado);
        LoginControlador controlador = new LoginControlador(new AutenticacionServicio(repositorio));

        Optional<Usuario> usuario = controlador.autenticar("eva@example.test", "clave");

        assertTrue(usuario.isPresent());
        assertEquals("Eva", usuario.get().getNombre());
        assertEquals("Asistente", usuario.get().getRol());
    }
}
