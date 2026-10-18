package Servicio;

import java.util.List;
import java.util.Locale;

/** Roles canónicos de acceso; los identificadores se guardan en usuarios.rol. */
public final class RolesSistema {
    public static final String ADMINISTRADOR = "Administrador";
    public static final String ASISTENTE = "Asistente";
    public static final String MESONERO = "Mesonero";
    public static final String COCINERO = "Cocinero";
    public static final String CAJERO = "Cajero";
    public static final String MAITRE = "Maitre";
    public static final String HOST = "Host";

    private static final List<String> ROLES = List.of(
            ADMINISTRADOR, ASISTENTE, MESONERO, COCINERO, CAJERO, MAITRE, HOST);

    private RolesSistema() { }

    public static List<String> todos() {
        return ROLES;
    }

    public static boolean reconocido(String rol) {
        return rol != null && ROLES.contains(rol.trim());
    }

    public static String clave(String rol) {
        if (!reconocido(rol)) {
            throw new IllegalArgumentException("Rol no reconocido.");
        }
        return rol.trim().toLowerCase(Locale.ROOT);
    }
}
