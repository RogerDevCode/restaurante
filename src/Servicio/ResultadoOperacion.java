package Servicio;

import java.util.Objects;

/**
 * Jerarquía sellada (Java 21 Sealed Interface - JEP 409) para representar de forma cerrada
 * y segura el resultado de una operación de negocio o persistencia.
 *
 * @param <T> Tipo de datos retornado en caso de éxito
 */
public sealed interface ResultadoOperacion<T> permits ResultadoOperacion.Exito, ResultadoOperacion.Fallo {

    /**
     * Resultado exitoso conteniendo los datos y un mensaje opcional.
     */
    record Exito<T>(T datos, String mensaje) implements ResultadoOperacion<T> {
        public Exito(T datos) {
            this(datos, "Operación completada exitosamente.");
        }
    }

    /**
     * Resultado fallido conteniendo la descripción del error y la causa raíz opcional.
     */
    record Fallo<T>(String mensajeError, Throwable causa) implements ResultadoOperacion<T> {
        public Fallo {
            Objects.requireNonNull(mensajeError, "El mensaje de error no puede ser nulo.");
        }

        public Fallo(String mensajeError) {
            this(mensajeError, null);
        }
    }

    /**
     * Método fábrica de conveniencia para éxito.
     */
    static <T> ResultadoOperacion<T> exito(T datos, String mensaje) {
        return new Exito<>(datos, mensaje);
    }

    /**
     * Método fábrica de conveniencia para fallo.
     */
    static <T> ResultadoOperacion<T> fallo(String mensajeError, Throwable causa) {
        return new Fallo<>(mensajeError, causa);
    }

    /**
     * Indica si la operación culminó con éxito.
     */
    default boolean esExitoso() {
        return this instanceof Exito;
    }
}
