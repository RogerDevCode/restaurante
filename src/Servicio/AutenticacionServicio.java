package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.login;
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

    public Optional<login> autenticar(String correo, String clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
        return repositorio.autenticar(correo.trim(), clave);
    }
}
