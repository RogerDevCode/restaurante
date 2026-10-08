package Modelo;

import java.util.logging.Level;

public class DataAccessException extends ErrorAplicacionException {
    public DataAccessException(String mensaje) {
        this(mensaje, null);
    }

    public DataAccessException(String mensaje, Throwable causa) {
        this(mensaje, causa, Level.SEVERE);
    }

    protected DataAccessException(String mensaje, Throwable causa, Level nivel) {
        super(mensaje, causa, nivel);
    }
}
