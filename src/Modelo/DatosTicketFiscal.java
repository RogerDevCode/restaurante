package Modelo;

import java.util.Objects;

/**
 * Registro inmutable (Java 21 Record) para los datos de encabezado y pie de ticket fiscal.
 */
public record DatosTicketFiscal(
        String ruc,
        String nombre,
        String telefono,
        String direccion,
        String mensaje) {

    public DatosTicketFiscal {
        ruc = Objects.requireNonNullElse(ruc, "").trim();
        nombre = Objects.requireNonNullElse(nombre, "").trim();
        telefono = Objects.requireNonNullElse(telefono, "").trim();
        direccion = Objects.requireNonNullElse(direccion, "").trim();
        mensaje = Objects.requireNonNullElse(mensaje, "").trim();
    }
}
