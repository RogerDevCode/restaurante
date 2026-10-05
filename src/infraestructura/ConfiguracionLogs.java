package infraestructura;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfiguracionLogs {
    private static final Logger LOGGER = Logger.getLogger(ConfiguracionLogs.class.getName());
    private static final AtomicBoolean CONFIGURADO = new AtomicBoolean();

    private ConfiguracionLogs() {
    }

    public static synchronized void configurar() {
        if (CONFIGURADO.get()) {
            return;
        }

        Logger raiz = Logger.getLogger("");
        try {
            Handler handlerDiario = new ArchivoLogDiario();
            for (Handler handlerExistente : raiz.getHandlers()) {
                raiz.removeHandler(handlerExistente);
                handlerExistente.close();
            }
            raiz.setLevel(Level.INFO);
            handlerDiario.setLevel(Level.INFO);
            raiz.addHandler(handlerDiario);
            CONFIGURADO.set(true);
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "No se pudo inicializar el registro diario en archivos.", ex);
            throw new IllegalStateException("No se pudo inicializar el registro diario en archivos.", ex);
        }
    }
}
