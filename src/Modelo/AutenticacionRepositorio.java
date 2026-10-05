package Modelo;

import java.util.Optional;

/** Puerto de acceso para autenticar usuarios, sustituible en pruebas. */
public interface AutenticacionRepositorio {
    Optional<Usuario> autenticar(String correo, String clave);
}
