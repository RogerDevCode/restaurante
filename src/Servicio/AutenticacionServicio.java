package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.util.Optional;

/** Reglas de aplicación para la autenticación. No depende de Swing ni de JDBC. */
public final class AutenticacionServicio {
    private final AutenticacionRepositorio repositorio;

    public AutenticacionServicio(AutenticacionRepositorio repositorio) {
        if (repositorio == null) {
            throw ErrorAplicacionException.validacion("El repositorio de autenticación es obligatorio.");
        }
        this.repositorio = repositorio;
    }

    public Optional<Usuario> autenticar(String correo, String clave) {
        validarCredenciales(correo, clave);
        return repositorio.autenticar(correo.trim(), clave);
    }

    private void validarCredenciales(String correo, String clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
    }
}
