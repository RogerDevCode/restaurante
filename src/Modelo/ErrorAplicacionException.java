package Modelo;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorAplicacionException extends RuntimeException {
    private static final Logger LOGGER = Logger.getLogger(ErrorAplicacionException.class.getName());

    public ErrorAplicacionException(String mensaje, Throwable causa) {
        this(mensaje, causa, Level.SEVERE);
    }

    protected ErrorAplicacionException(String mensaje, Throwable causa, Level nivel) {
        super(mensaje, causa);
        LOGGER.log(nivel, mensaje, causa);
    }

    public static ErrorAplicacionException validacion(String mensaje) {
        return new ErrorAplicacionException(mensaje,
                new IllegalArgumentException(mensaje), Level.WARNING);
    }

    public static boolean resultadoUnaFila(int filasAfectadas, String operacion) {
        if (filasAfectadas == 1) {
            return true;
        }
        LOGGER.log(Level.WARNING,
                "La operación no afectó exactamente una fila: {0}. Filas afectadas: {1}.",
                new Object[]{operacion, filasAfectadas});
        return false;
    }

    public static void registrarAdvertencia(String mensaje) {
        LOGGER.log(Level.WARNING, mensaje);
    }
}
