package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Usuario;

/** Política central para separar las acciones administrativas de las de atención. */
public final class PoliticaAcceso {
    public enum Accion {
        CONSULTAR_SALAS,
        CONSULTAR_PLATOS,
        REGISTRAR_PEDIDOS,
        GESTIONAR_PEDIDOS,
        GESTIONAR_SALAS,
        GESTIONAR_PLATOS,
        GESTIONAR_USUARIOS,
        EDITAR_CONFIGURACION
    }

    private final String rol;

    public PoliticaAcceso(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null || usuario.getRol().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Se requiere una sesión con rol válido.");
        }
        rol = usuario.getRol().trim();
        if (!"Administrador".equals(rol) && !"Asistente".equals(rol)) {
            throw ErrorAplicacionException.validacion("El rol de la sesión no está reconocido.");
        }
    }

    public boolean permite(Accion accion) {
        if (accion == null) {
            throw ErrorAplicacionException.validacion("La acción solicitada es obligatoria.");
        }
        if ("Administrador".equals(rol)) {
            return true;
        }
        return accion == Accion.CONSULTAR_SALAS
                || accion == Accion.CONSULTAR_PLATOS
                || accion == Accion.REGISTRAR_PEDIDOS;
    }

    public boolean esAdministrador() {
        return "Administrador".equals(rol);
    }
}
