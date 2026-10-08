package Modelo;

import java.util.Map;

/**
 * Contrato de repositorio para el almacenamiento clave-valor de las configuraciones del sistema.
 */
public interface ConfiguracionRepositorio {

    /**
     * Obtiene el valor textual asociado a la clave, o el valor por defecto si no existe.
     */
    String obtener(String clave, String valorPorDefecto);

    /**
     * Guarda o actualiza un par clave-valor.
     */
    void guardar(String clave, String valor);

    /**
     * Obtiene todos los pares clave-valor almacenados en la base de datos.
     */
    Map<String, String> obtenerTodos();

    /**
     * Guarda o actualiza un conjunto de pares clave-valor en una sola operación.
     */
    void guardarVarios(Map<String, String> configuraciones);

    /**
     * Verifica si una clave existe en el almacenamiento.
     */
    boolean existe(String clave);

    /**
     * Elimina una clave del almacenamiento.
     */
    void eliminar(String clave);

    /**
     * Obtiene la fecha/hora en que fue actualizada la clave, o null si no existe.
     */
    default java.sql.Timestamp obtenerFechaActualizacion(String clave) {
        return null;
    }
}
