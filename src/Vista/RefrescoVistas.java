package Vista;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bus simple de notificaciones (patrón Observer) para mantener sincronizadas
 * las vistas con la base de datos. Una acción que escribe datos publica un
 * tópico ({@link #notificar(String)}) y todas las vistas suscritas a ese tópico
 * se refrescan automáticamente desde la BD.
 */
public final class RefrescoVistas {

    public static final String PLATOS = "platos";
    public static final String CATEGORIAS = "categorias";
    public static final String SALAS = "salas";
    public static final String MESAS = "mesas";
    public static final String PEDIDOS = "pedidos";
    public static final String HISTORIAL = "historial";
    public static final String USUARIOS = "usuarios";
    public static final String CONFIG = "configuracion";
    public static final String DASHBOARD = "dashboard";

    private static final Map<String, List<Runnable>> OBSERVADORES = new ConcurrentHashMap<>();

    private RefrescoVistas() {
    }

    /** Registra una vista (observador) interesada en refrescarse ante un tópico. */
    public static void suscribir(String topico, Runnable accion) {
        if (topico == null || accion == null) {
            return;
        }
        OBSERVADORES.computeIfAbsent(topico, k -> new ArrayList<>()).add(accion);
    }

    /** Elimina todos los observadores (evita acumular vistas de sesiones anteriores). */
    public static void limpiar() {
        OBSERVADORES.clear();
    }

    /** Publica un cambio; todas las vistas suscritas al tópico se refrescan. */
    public static void notificar(String topico) {
        if (topico == null) {
            return;
        }
        List<Runnable> lista = OBSERVADORES.get(topico);
        if (lista == null) {
            return;
        }
        for (Runnable accion : lista) {
            try {
                accion.run();
            } catch (RuntimeException ex) {
                // Una vista no debe romper el resto de notificaciones.
            }
        }
    }
}