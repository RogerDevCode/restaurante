package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Autorización por permiso explícito, con bypass inmutable para Administrador. */
public final class PoliticaAcceso {
    public enum Accion {
        CONSULTAR_SALAS("Consultar salas y mesas", "Salas y mesas"),
        CONSULTAR_OCUPACION_MESAS("Consultar ocupación de mesas", "Salas y mesas"),
        GESTIONAR_SALAS("Administrar salas y mesas", "Salas y mesas"),
        CONSULTAR_PLATOS("Consultar platos", "Menú"),
        GESTIONAR_PLATOS("Administrar platos y categorías", "Menú"),
        REGISTRAR_PEDIDOS("Registrar pedidos", "Pedidos"),
        CONSULTAR_PEDIDOS_PENDIENTES("Consultar cuentas pendientes", "Pedidos"),
        EDITAR_PEDIDOS_ABIERTOS("Editar pedidos abiertos", "Pedidos"),
        CANCELAR_CUENTA_PENDIENTE("Cancelar cuentas pendientes", "Pedidos"),
        APLICAR_CORTESIA("Aplicar cortesías", "Caja", true),
        COBRAR_PEDIDOS("Cobrar y finalizar pedidos", "Caja"),
        CONSULTAR_HISTORIAL("Consultar historial de ventas", "Caja"),
        ANULAR_FACTURA_COBRADA("Anular facturas cobradas", "Caja", true),
        GESTIONAR_CIERRE_CAJA("Emitir y consultar cierres de caja", "Caja", true),
        CONGELAR_LINEA_PEDIDO("Congelar línea de pedido", "Administración", true),
        GESTIONAR_USUARIOS("Administrar usuarios", "Administración", true),
        GESTIONAR_ROLES_ACCESOS("Administrar roles y accesos", "Administración", true),
        EDITAR_CONFIGURACION("Editar configuración del sistema", "Administración", true),
        PURGAR_HISTORIAL("Purgar historial de ventas", "Administración", true);

        private final String etiqueta;
        private final String grupo;
        private final boolean soloAdministrador;

        Accion(String etiqueta, String grupo) {
            this(etiqueta, grupo, false);
        }

        Accion(String etiqueta, String grupo, boolean soloAdministrador) {
            this.etiqueta = etiqueta;
            this.grupo = grupo;
            this.soloAdministrador = soloAdministrador;
        }

        public String etiqueta() { return etiqueta; }
        public String grupo() { return grupo; }
        public boolean soloAdministrador() { return soloAdministrador; }
    }

    private final String rol;
    private final BooleanSupplier sesionActivaProveedor;
    private final Set<Accion> permisos;

    public PoliticaAcceso(Usuario usuario) {
        this(usuario, usuario != null ? usuario::isActivo : () -> false,
                ConfiguracionAccesosServicio.permisosPredeterminados(usuario != null ? usuario.getRol() : null));
    }

    /** Política segura para inicializar consumidores sin sesión: deniega toda acción. */
    public static PoliticaAcceso denegada() {
        Usuario anonimo = new Usuario(0, "Sin sesión", "", "", RolesSistema.MESONERO);
        return new PoliticaAcceso(anonimo, () -> false, Set.of());
    }

    public PoliticaAcceso(Usuario usuario, BooleanSupplier sesionActivaProveedor) {
        this(usuario, sesionActivaProveedor,
                ConfiguracionAccesosServicio.permisosPredeterminados(usuario != null ? usuario.getRol() : null));
    }

    public PoliticaAcceso(Usuario usuario, BooleanSupplier sesionActivaProveedor, Set<Accion> permisos) {
        if (usuario == null || !RolesSistema.reconocido(usuario.getRol())) {
            throw ErrorAplicacionException.validacion("Se requiere una sesión con rol válido.");
        }
        this.rol = usuario.getRol().trim();
        this.sesionActivaProveedor = sesionActivaProveedor != null
                ? sesionActivaProveedor : usuario::isActivo;
        EnumSet<Accion> copia = EnumSet.noneOf(Accion.class);
        if (!RolesSistema.ADMINISTRADOR.equals(rol) && permisos != null) {
            copia.addAll(permisos);
        }
        this.permisos = Collections.unmodifiableSet(copia);
    }

    public boolean permite(Accion accion) {
        if (accion == null || !sesionActivaProveedor.getAsBoolean()) {
            return false;
        }
        if (RolesSistema.ADMINISTRADOR.equals(rol)) return true;
        return !accion.soloAdministrador() && permisos.contains(accion);
    }

    public boolean esAdministrador() {
        return RolesSistema.ADMINISTRADOR.equals(rol) && sesionActivaProveedor.getAsBoolean();
    }

    public Set<Accion> permisos() {
        if (RolesSistema.ADMINISTRADOR.equals(rol)) {
            return Collections.unmodifiableSet(EnumSet.allOf(Accion.class));
        }
        return permisos;
    }
}
