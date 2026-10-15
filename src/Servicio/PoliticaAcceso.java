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
    private final java.util.function.BooleanSupplier sesionActivaProveedor;

    public PoliticaAcceso(Usuario usuario) {
        this(usuario, usuario != null ? usuario::isActivo : () -> true);
    }

    public PoliticaAcceso(Usuario usuario, java.util.function.BooleanSupplier sesionActivaProveedor) {
        if (usuario == null || usuario.getRol() == null || usuario.getRol().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Se requiere una sesión con rol válido.");
        }
        rol = usuario.getRol().trim();
        if (!"Administrador".equals(rol) && !"Asistente".equals(rol)) {
            throw ErrorAplicacionException.validacion("El rol de la sesión no está reconocido.");
        }
        this.sesionActivaProveedor = sesionActivaProveedor != null ? sesionActivaProveedor : usuario::isActivo;
    }

    public boolean permite(Accion accion) {
        if (accion == null) {
            throw ErrorAplicacionException.validacion("La acción solicitada es obligatoria.");
        }
        if (sesionActivaProveedor != null && !sesionActivaProveedor.getAsBoolean()) {
            return false;
        }
        return switch (rol) {
            case "Administrador" -> true;
            case "Asistente" -> switch (accion) {
                case CONSULTAR_SALAS, CONSULTAR_PLATOS, REGISTRAR_PEDIDOS -> true;
                case GESTIONAR_PEDIDOS, GESTIONAR_SALAS, GESTIONAR_PLATOS, GESTIONAR_USUARIOS, EDITAR_CONFIGURACION -> false;
            };
            default -> false;
        };
    }

    public boolean esAdministrador() {
        if (sesionActivaProveedor != null && !sesionActivaProveedor.getAsBoolean()) {
            return false;
        }
        return "Administrador".equals(rol);
    }
}
