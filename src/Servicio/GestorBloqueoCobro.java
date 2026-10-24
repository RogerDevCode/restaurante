package Servicio;

import Modelo.ConfiguracionRepositorio;
import Modelo.ConfiguracionSistemaDao;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centinela efímero anti-colisión para operaciones de cobro en ambientes multiusuario / multicaja.
 * Evita que dos terminales o cajeros abran simultáneamente el modal de cobro sobre el mismo pedido.
 * Utiliza clave-valor en configuracion_sistema (sin DDL) con TTL de expiración automática (3 min)
 * y una guardia dual en memoria para colisiones locales de la misma JVM.
 *
 * <p>Identifica de forma unívoca cada instancia/terminal mediante un ID único para prevenir
 * colisiones incluso cuando varias cajas operan bajo la misma cuenta de usuario (ej. 'admin').</p>
 */
public final class GestorBloqueoCobro {
    private static final Logger LOGGER = Logger.getLogger(GestorBloqueoCobro.class.getName());
    public static final String PREFIJO_CLAVE = "lock.cobro.pedido.";
    public static final long TTL_MS = 180_000L; // 3 minutos

    public record ResultadoBloqueo(boolean adquirido, String mensaje, String usuarioOcupante, long restanteSegundos) {}

    private final ConfiguracionRepositorio repo;
    private final String terminalId;
    private final Map<Integer, BloqueoLocal> bloqueosLocales = new ConcurrentHashMap<>();

    private record BloqueoLocal(int idUsuario, String terminalId, String nombreUsuario, long timestamp) {}

    public GestorBloqueoCobro() {
        this(new ConfiguracionSistemaDao(), UUID.randomUUID().toString().substring(0, 8));
    }

    public GestorBloqueoCobro(ConfiguracionRepositorio repo) {
        this(repo, UUID.randomUUID().toString().substring(0, 8));
    }

    public GestorBloqueoCobro(ConfiguracionRepositorio repo, String terminalId) {
        this.repo = repo;
        this.terminalId = (terminalId != null && !terminalId.isBlank())
                ? terminalId.trim()
                : UUID.randomUUID().toString().substring(0, 8);
    }

    public String getTerminalId() {
        return terminalId;
    }

    /**
     * Intenta adquirir el centinela efímero para el cobro del pedido.
     * Si ya está tomado por otro usuario o por otra terminal dentro de la ventana de TTL, rechaza la apertura.
     */
    public synchronized ResultadoBloqueo adquirir(int idPedido, int idUsuario, String nombreUsuario) {
        if (idPedido <= 0) {
            return new ResultadoBloqueo(true, "ID de pedido inválido", null, 0);
        }
        long ahora = System.currentTimeMillis();
        String usuarioLimpio = (nombreUsuario != null && !nombreUsuario.isBlank())
                ? nombreUsuario.trim().replace("|", " ") : ("Usuario " + idUsuario);

        // 1. Guardia local JVM (misma máquina / terminal)
        BloqueoLocal local = bloqueosLocales.get(idPedido);
        if (local != null && (ahora - local.timestamp()) < TTL_MS) {
            if (!local.terminalId().equals(this.terminalId) || (local.idUsuario() != idUsuario && idUsuario > 0)) {
                long restSeg = Math.max(1, (TTL_MS - (ahora - local.timestamp())) / 1000);
                return new ResultadoBloqueo(false,
                        "El pedido #" + idPedido + " ya está en proceso de cobro por '" + local.nombreUsuario() + "' en esta terminal.",
                        local.nombreUsuario(), restSeg);
            }
        }

        // 2. Guardia repositorio clave-valor (multicaja / diferentes terminales)
        String clave = PREFIJO_CLAVE + idPedido;
        try {
            if (repo != null) {
                String valor = repo.obtener(clave, null);
                if (valor != null && !valor.isBlank()) {
                    DatosLock parsed = parsearPayload(valor);
                    if (parsed != null && (ahora - parsed.timestamp) < TTL_MS) {
                        // Conflicto si pertenece a otra terminal O a otro usuario
                        if (!parsed.terminalId.equals(this.terminalId)) {
                            long restSeg = Math.max(1, (TTL_MS - (ahora - parsed.timestamp)) / 1000);
                            return new ResultadoBloqueo(false,
                                    "El pedido #" + idPedido + " ya está en proceso de cobro en otra terminal por: " + parsed.nombreUsuario,
                                    parsed.nombreUsuario, restSeg);
                        }
                    }
                }
                // Adquirir lock en BD con payload delimitado por pipe: uid|terminalId|nombreUsuario|timestamp
                String nuevoValor = idUsuario + "|" + terminalId + "|" + usuarioLimpio + "|" + ahora;
                repo.guardar(clave, nuevoValor);
            }
            bloqueosLocales.put(idPedido, new BloqueoLocal(idUsuario, terminalId, usuarioLimpio, ahora));
            return new ResultadoBloqueo(true, "Bloqueo adquirido", null, 0);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al gestionar centinela de cobro para pedido #" + idPedido + ": " + ex.getMessage(), ex);
            // Modo defensivo: no interrumpir la caja si falla la consulta de red, permitir con guardia local
            bloqueosLocales.put(idPedido, new BloqueoLocal(idUsuario, terminalId, usuarioLimpio, ahora));
            return new ResultadoBloqueo(true, "Centinela local adquirido (falla no fatal de BD)", null, 0);
        }
    }

    /**
     * Libera el centinela efímero al cerrar el diálogo o finalizar la transacción.
     */
    public synchronized void liberar(int idPedido, int idUsuario) {
        if (idPedido <= 0) return;
        bloqueosLocales.remove(idPedido);
        String clave = PREFIJO_CLAVE + idPedido;
        try {
            if (repo != null) {
                String valor = repo.obtener(clave, null);
                if (valor != null) {
                    DatosLock parsed = parsearPayload(valor);
                    if (parsed == null || parsed.terminalId.equals(this.terminalId) || parsed.idUsuario == idUsuario || idUsuario <= 0) {
                        repo.eliminar(clave);
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error liberando centinela para pedido #" + idPedido + ": " + ex.getMessage());
        }
    }

    private record DatosLock(int idUsuario, String terminalId, String nombreUsuario, long timestamp) {}

    private static DatosLock parsearPayload(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            if (valor.contains("|")) {
                String[] partes = valor.split("\\|", 4);
                if (partes.length >= 4) {
                    return new DatosLock(Integer.parseInt(partes[0].trim()), partes[1].trim(), partes[2].trim(), Long.parseLong(partes[3].trim()));
                }
            } else if (valor.contains(":")) {
                // Retrocompatibilidad con formato antiguo uid:nombre:timestamp
                String[] partes = valor.split(":");
                if (partes.length >= 3) {
                    return new DatosLock(Integer.parseInt(partes[0].trim()), "legacy", partes[1].trim(), Long.parseLong(partes[2].trim()));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "No se pudo interpretar payload de lock: " + valor);
        }
        return null;
    }
}
