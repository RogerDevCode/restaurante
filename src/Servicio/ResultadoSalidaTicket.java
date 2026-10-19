package Servicio;

/**
 * Contrato tipado del resultado de salida de tickets y comprobantes.
 * ENVIADO_A_SPOOLER confirma que el spooler del sistema operativo
 * (spoolsv.exe / CUPS) aceptó el trabajo para entrega a la impresora.
 */
public record ResultadoSalidaTicket(
    EstadoSalida estado,
    String canal,
    String detalleError
) {
    public enum EstadoSalida {
        ENVIADO_A_SPOOLER,
        ABIERTO_EN_VISOR,
        FALLO
    }

    public boolean esExito() {
        return estado == EstadoSalida.ENVIADO_A_SPOOLER || estado == EstadoSalida.ABIERTO_EN_VISOR;
    }

    public static ResultadoSalidaTicket spooler(String canal) {
        return new ResultadoSalidaTicket(EstadoSalida.ENVIADO_A_SPOOLER, canal != null ? canal : "Spooler", null);
    }

    public static ResultadoSalidaTicket visor(String canal, String detalle) {
        return new ResultadoSalidaTicket(EstadoSalida.ABIERTO_EN_VISOR, canal != null ? canal : "Visor", detalle);
    }

    public static ResultadoSalidaTicket fallo(String canal, String detalleError) {
        return new ResultadoSalidaTicket(EstadoSalida.FALLO, canal != null ? canal : "Desconocido", detalleError);
    }
}
