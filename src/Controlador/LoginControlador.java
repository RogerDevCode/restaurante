package Controlador;

import Modelo.LoginDao;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import java.util.Optional;

/** Coordina el caso de uso de inicio de sesión entre la vista y el servicio. */
public final class LoginControlador {
    private final AutenticacionServicio servicio;

    public LoginControlador() {
        this(new AutenticacionServicio(new LoginDao()));
    }

    public LoginControlador(AutenticacionServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de autenticación es obligatorio.");
        }
        this.servicio = servicio;
    }

    public Optional<Usuario> autenticar(String correo, String clave) {
        return servicio.autenticar(correo, clave);
    }

    /** Coordina el caso de uso para consumidores migrados al modelo Usuario. */
    public Optional<Usuario> autenticarUsuario(String correo, String clave) {
        return servicio.autenticar(correo, clave);
    }
}
