package Vista;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Bus simple de notificaciones (patrón Observer) para mantener sincronizadas
 * las vistas con la base de datos. Una acción que escribe datos publica un
 * tópico ({@link #notificar(String)}) y todas las vistas suscritas a ese tópico
 * se refrescan automáticamente desde la base de datos.
 *
 * <p>C4: las notificaciones se despachan siempre en el EDT
 * ({@link SwingUtilities#invokeLater}) y se coalescen por tópico, de modo que un
 * escritor que corra en un hilo de fondo nunca toque Swing directamente y varias
 * escrituras seguidas provoquen una sola recarga. Los observadores pueden
 * declarar una carga útil opcional (por ejemplo el id afectado) mediante
 * {@link #suscribir(String, Consumer)} y {@link #notificar(String, Object)}.</p>
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

    /** Marcador para una notificación sin carga útil (ConcurrentHashMap no admite null). */
    private static final Object SIN_CARGA = new Object();

    private static final Map<String, List<Consumer<Object>>> OBSERVADORES = new ConcurrentHashMap<>();
    private static final Map<String, Object> PENDIENTES = new ConcurrentHashMap<>();
    private static final AtomicBoolean FLUSH_PROGRAMADO = new AtomicBoolean(false);

    private RefrescoVistas() {
    }

    /** Registra una vista (observador) interesada en refrescarse ante un tópico. */
    public static void suscribir(String topico, Runnable accion) {
        if (accion == null) {
            return;
        }
        suscribir(topico, carga -> accion.run());
    }

    /** Registra un observador que además recibe la carga útil publicada con el tópico. */
    public static void suscribir(String topico, Consumer<Object> observador) {
        if (topico == null || observador == null) {
            return;
        }
        OBSERVADORES.computeIfAbsent(topico, k -> new ArrayList<>()).add(observador);
    }

    /** Elimina todos los observadores y notificaciones pendientes (evita acumular sesiones). */
    public static void limpiar() {
        OBSERVADORES.clear();
        PENDIENTES.clear();
        FLUSH_PROGRAMADO.set(false);
    }

    /** Publica un cambio sin carga útil. */
    public static void notificar(String topico) {
        notificar(topico, null);
    }

    /** Publica un cambio con una carga útil opcional (por ejemplo el id afectado). */
    public static void notificar(String topico, Object cargaUtil) {
        if (topico == null) {
            return;
        }
        PENDIENTES.put(topico, cargaUtil == null ? SIN_CARGA : cargaUtil);
        programarFlush();
    }

    private static void programarFlush() {
        if (FLUSH_PROGRAMADO.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(RefrescoVistas::flush);
        }
    }

    private static void flush() {
        try {
            Map<String, Object> lote = new HashMap<>();
            for (Map.Entry<String, Object> entrada : PENDIENTES.entrySet()) {
                if (PENDIENTES.remove(entrada.getKey(), entrada.getValue())) {
                    lote.put(entrada.getKey(), entrada.getValue());
                }
            }
            for (Map.Entry<String, Object> entrada : lote.entrySet()) {
                List<Consumer<Object>> lista = OBSERVADORES.get(entrada.getKey());
                if (lista == null) {
                    continue;
                }
                Object cargaUtil = entrada.getValue() == SIN_CARGA ? null : entrada.getValue();
                for (Consumer<Object> observador : lista) {
                    try {
                        observador.accept(cargaUtil);
                    } catch (RuntimeException ex) {
                        // Una vista no debe romper el resto de notificaciones.
                    }
                }
            }
        } finally {
            FLUSH_PROGRAMADO.set(false);
            // Si algo se publicó durante el flush, reprogramar (si no, se perdería).
            if (!PENDIENTES.isEmpty()) {
                programarFlush();
            }
        }
    }
}
