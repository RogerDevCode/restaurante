package Servicio;

import Modelo.ConfiguracionSistemaDao;
import Modelo.DataAccessException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Carga y persiste permisos de rol; cada sesión recibe una copia inmutable. */
public final class ConfiguracionAccesosServicio {
    private static final String PREFIJO = "seguridad.rol.";
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(ConfiguracionAccesosServicio.class.getName());
    private final ConfiguracionSistemaDao configuracion;

    public ConfiguracionAccesosServicio() {
        this(new ConfiguracionSistemaDao());
    }

    public ConfiguracionAccesosServicio(ConfiguracionSistemaDao configuracion) {
        if (configuracion == null) throw new IllegalArgumentException("El DAO de configuración es obligatorio.");
        this.configuracion = configuracion;
    }

    public Set<PoliticaAcceso.Accion> cargarPermisos(String rol) {
        if (!RolesSistema.reconocido(rol)) return Set.of();
        if (RolesSistema.ADMINISTRADOR.equals(rol)) {
            return Collections.unmodifiableSet(EnumSet.allOf(PoliticaAcceso.Accion.class));
        }
        Map<String, String> valores = configuracion.obtenerTodosEstrictamente();
        EnumSet<PoliticaAcceso.Accion> permisos = EnumSet.noneOf(PoliticaAcceso.Accion.class);
        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            if (accion.soloAdministrador()) continue;
            String valor = valores.get(clave(rol, accion));
            boolean otorgado = valor == null
                    ? permisosPredeterminados(rol).contains(accion)
                    : "1".equals(valor.trim());
            if (otorgado) permisos.add(accion);
        }
        return Collections.unmodifiableSet(permisos);
    }

    public void guardarPermisos(String rol, Map<PoliticaAcceso.Accion, Boolean> cambios,
            PoliticaAcceso solicitante, int actorId) {
        if (solicitante == null || !solicitante.esAdministrador()
                || !solicitante.permite(PoliticaAcceso.Accion.GESTIONAR_ROLES_ACCESOS) || actorId <= 0) {
            throw new SecurityException("Se requiere una sesión de Administrador activa para modificar permisos.");
        }
        if (!RolesSistema.reconocido(rol) || RolesSistema.ADMINISTRADOR.equals(rol)) {
            throw new IllegalArgumentException("El rol seleccionado no puede modificarse.");
        }
        if (cambios == null || cambios.size() != PoliticaAcceso.Accion.values().length
                || !cambios.keySet().containsAll(EnumSet.allOf(PoliticaAcceso.Accion.class))) {
            throw new IllegalArgumentException("La matriz debe contener todos los permisos conocidos.");
        }
        Map<String, String> valores = new HashMap<>();
        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            Boolean permitido = cambios.get(accion);
            if (permitido == null) throw new IllegalArgumentException("El valor de un permiso no es válido.");
            if (accion.soloAdministrador() && permitido) {
                throw new IllegalArgumentException("Este permiso está reservado al Administrador.");
            }
            valores.put(clave(rol, accion), permitido ? "1" : "0");
        }
        configuracion.guardarVarios(valores);
        LOGGER.info(() -> "Permisos actualizados por el usuario #" + actorId + " para el rol " + rol
                + " (" + valores.size() + " permisos).");
    }

    public static Set<PoliticaAcceso.Accion> permisosPredeterminados(String rol) {
        if (!RolesSistema.reconocido(rol)) return Set.of();
        if (RolesSistema.ADMINISTRADOR.equals(rol)) {
            return Collections.unmodifiableSet(EnumSet.allOf(PoliticaAcceso.Accion.class));
        }
        EnumSet<PoliticaAcceso.Accion> permisos = EnumSet.noneOf(PoliticaAcceso.Accion.class);
        switch (rol) {
            case RolesSistema.ASISTENTE -> permisos.addAll(EnumSet.of(
                    PoliticaAcceso.Accion.CONSULTAR_SALAS,
                    PoliticaAcceso.Accion.CONSULTAR_OCUPACION_MESAS,
                    PoliticaAcceso.Accion.CONSULTAR_PLATOS,
                    PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
            case RolesSistema.MESONERO -> permisos.addAll(EnumSet.of(
                    PoliticaAcceso.Accion.CONSULTAR_SALAS,
                    PoliticaAcceso.Accion.CONSULTAR_OCUPACION_MESAS,
                    PoliticaAcceso.Accion.CONSULTAR_PLATOS,
                    PoliticaAcceso.Accion.REGISTRAR_PEDIDOS,
                    PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES,
                    PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS));
            case RolesSistema.COCINERO -> { }
            case RolesSistema.CAJERO -> permisos.addAll(EnumSet.of(
                    PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES,
                    PoliticaAcceso.Accion.COBRAR_PEDIDOS,
                    PoliticaAcceso.Accion.CONSULTAR_HISTORIAL));
            case RolesSistema.MAITRE -> permisos.addAll(EnumSet.of(
                    PoliticaAcceso.Accion.CONSULTAR_SALAS,
                    PoliticaAcceso.Accion.CONSULTAR_OCUPACION_MESAS,
                    PoliticaAcceso.Accion.CONSULTAR_PLATOS,
                    PoliticaAcceso.Accion.REGISTRAR_PEDIDOS,
                    PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES,
                    PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS,
                    PoliticaAcceso.Accion.CANCELAR_CUENTA_PENDIENTE));
            case RolesSistema.HOST -> permisos.addAll(EnumSet.of(
                    PoliticaAcceso.Accion.CONSULTAR_SALAS,
                    PoliticaAcceso.Accion.CONSULTAR_OCUPACION_MESAS));
            default -> { }
        }
        return Collections.unmodifiableSet(permisos);
    }

    private static String clave(String rol, PoliticaAcceso.Accion accion) {
        return PREFIJO + RolesSistema.clave(rol) + "." + accion.name().toLowerCase(java.util.Locale.ROOT);
    }
}
