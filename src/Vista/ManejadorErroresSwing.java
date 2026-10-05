package Vista;

import Modelo.ErrorAplicacionException;
import infraestructura.ConfiguracionLogs;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.BiConsumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Centraliza los errores no atendidos que alcanzan los límites de hilos de Swing. */
public final class ManejadorErroresSwing extends EventQueue {
    private static final Logger LOGGER = Logger.getLogger(ManejadorErroresSwing.class.getName());
    private static final AtomicBoolean INSTALADO = new AtomicBoolean();
    private static final AtomicBoolean DIALOGO_ABIERTO = new AtomicBoolean();
    private final BiConsumer<Throwable, String> presentador;

    private ManejadorErroresSwing() {
        this(ManejadorErroresSwing::mostrarDialogo);
    }

    ManejadorErroresSwing(BiConsumer<Throwable, String> presentador) {
        if (presentador == null) {
            throw ErrorAplicacionException.validacion("El presentador de errores es obligatorio.");
        }
        this.presentador = presentador;
    }

    public static synchronized void instalar() {
        if (INSTALADO.get()) {
            return;
        }
        try {
            ConfiguracionLogs.configurar();
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(new ManejadorErroresSwing());
            Thread.setDefaultUncaughtExceptionHandler(
                    crearManejadorDeHilos(ManejadorErroresSwing::mostrarDialogo));
            INSTALADO.set(true);
        } catch (RuntimeException | Error error) {
            INSTALADO.set(false);
            throw error;
        }
    }

    static Thread.UncaughtExceptionHandler crearManejadorDeHilos(
            BiConsumer<Throwable, String> presentador) {
        if (presentador == null) {
            throw ErrorAplicacionException.validacion("El presentador de errores es obligatorio.");
        }
        return (hilo, error) -> reportar(error, hilo.getName(), presentador);
    }

    @Override
    protected void dispatchEvent(AWTEvent evento) {
        ejecutarConManejo(() -> super.dispatchEvent(evento));
    }

    void ejecutarConManejo(Runnable despacho) {
        try {
            despacho.run();
        } catch (Throwable error) {
            if (error instanceof Error) {
                // El uncaught handler es el único responsable de registrar Errors.
                throw (Error) error;
            }
            // Las excepciones de una acción quedan resueltas en esta frontera: se
            // registran y muestran, y el EDT continúa atendiendo eventos.
            reportar(error, "Event Dispatch Thread", presentador);
        }
    }

    private static void reportar(Throwable error, String hilo, BiConsumer<Throwable, String> presentador) {
        if (!(error instanceof ErrorAplicacionException)) {
            LOGGER.log(Level.SEVERE, "Error no controlado en el hilo " + hilo, error);
        }
        String mensaje = error instanceof ErrorAplicacionException ? error.getMessage() : null;
        if (mensaje == null || mensaje.trim().isEmpty()) {
            mensaje = "Ocurrió un error inesperado. El detalle quedó registrado en el log.";
        }
        final String mensajeFinal = mensaje;
        Runnable mostrar = () -> {
            if (!DIALOGO_ABIERTO.compareAndSet(false, true)) {
                return;
            }
            try {
                presentador.accept(error, mensajeFinal);
            } catch (RuntimeException | Error falloPresentacion) {
                if (falloPresentacion != error) {
                    falloPresentacion.addSuppressed(error);
                }
                LOGGER.log(Level.SEVERE,
                        "No se pudo presentar el error original al usuario; se conserva como causa suprimida.",
                        falloPresentacion);
            } finally {
                DIALOGO_ABIERTO.set(false);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            mostrar.run();
        } else {
            SwingUtilities.invokeLater(mostrar);
        }
    }

    private static void mostrarDialogo(Throwable error, String mensaje) {
        Window ventana = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JOptionPane.showMessageDialog(ventana, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
