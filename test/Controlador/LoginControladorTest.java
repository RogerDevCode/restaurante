package Controlador;

import Modelo.AutenticacionRepositorio;
import Modelo.Usuario;
import Modelo.login;
import Servicio.AutenticacionServicio;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoginControladorTest {
    @Test
    @SuppressWarnings("deprecation")
    public void coordinaAutenticacionUsuarioConRepositorioFalso() {
        login legado = new login(3, "Eva", "eva@example.test", "clave", "Asistente");
        AutenticacionRepositorio repositorio = (correo, clave) -> Optional.of(legado);
        LoginControlador controlador = new LoginControlador(new AutenticacionServicio(repositorio));

        Optional<Usuario> usuario = controlador.autenticarUsuario("eva@example.test", "clave");

        assertTrue(usuario.isPresent());
        assertEquals("Eva", usuario.get().getNombre());
        assertEquals("Asistente", usuario.get().getRol());
    }
}
