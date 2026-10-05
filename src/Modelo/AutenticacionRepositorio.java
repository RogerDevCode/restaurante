package Modelo;

import java.util.Optional;

/** Puerto de acceso para autenticar usuarios, sustituible en pruebas. */
public interface AutenticacionRepositorio {
    Optional<login> autenticar(String correo, String clave);

    /** Adaptador de transición al modelo Usuario sin duplicar ni perder datos. */
    default Optional<Usuario> autenticarUsuario(String correo, String clave) {
        return autenticar(correo, clave).map(usuario -> usuario);
    }
}
