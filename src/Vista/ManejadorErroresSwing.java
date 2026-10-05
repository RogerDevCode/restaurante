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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Centraliza los errores no atendidos que alcanzan los límites de hilos de Swing. */
public final class ManejadorErroresSwing extends EventQueue {
    private static final Logger LOGGER = Logger.getLogger(ManejadorErroresSwing.class.getName());
    private static final AtomicBoolean INSTALADO = new AtomicBoolean();
    private static final AtomicBoolean DIALOGO_ABIERTO = new AtomicBoolean();

    private ManejadorErroresSwing() {
    }

    public static synchronized void instalar() {
        if (INSTALADO.get()) {
            return;
        }
        try {
            ConfiguracionLogs.configurar();
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(new ManejadorErroresSwing());
            Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> reportar(error, hilo.getName()));
            INSTALADO.set(true);
        } catch (RuntimeException | Error error) {
            INSTALADO.set(false);
            throw error;
        }
    }

    @Override
    protected void dispatchEvent(AWTEvent evento) {
        try {
            super.dispatchEvent(evento);
        } catch (Throwable error) {
            if (error instanceof Error) {
                // El uncaught handler es el único responsable de registrar Errors.
                throw (Error) error;
            }
            // Las excepciones de una acción quedan resueltas en esta frontera: se
            // registran y muestran, y el EDT continúa atendiendo eventos.
            reportar(error, "Event Dispatch Thread");
        }
    }

    private static void reportar(Throwable error, String hilo) {
        if (!(error instanceof ErrorAplicacionException)) {
            LOGGER.log(Level.SEVERE, "Error no controlado en el hilo " + hilo, error);
        }
        Runnable mostrar = () -> {
            if (!DIALOGO_ABIERTO.compareAndSet(false, true)) {
                return;
            }
            try {
                Window ventana = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                String mensaje = error.getMessage();
                if (mensaje == null || mensaje.trim().isEmpty()) {
                    mensaje = "Ocurrió un error inesperado. El detalle quedó registrado en el log.";
                }
                JOptionPane.showMessageDialog(ventana, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
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
}
