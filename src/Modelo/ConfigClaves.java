package Modelo;

/**
 * Constantes con los nombres de claves estándar para la tabla clave-valor `configuracion_sistema`.
 * Desacopla las configuraciones técnicas y de hardware de la entidad fiscal de la empresa.
 */
public final class ConfigClaves {

    private ConfigClaves() {
    }

    public static final String TASA_DOLAR = "tasa_dolar";
    public static final String IVA_PORCENTAJE = "iva_porcentaje";
    public static final String IMPRESORA_TICKETS = "impresora_tickets";
    public static final String MODO_SALIDA_TICKETS = "modo_salida_tickets";
    public static final String IMPRIMIR_LOGO_TICKET = "imprimir_logo_ticket";
    public static final String CLIENTE_DEFAULT_NOMBRE = "cliente_predeterminado_nombre";
    public static final String CLIENTE_DEFAULT_DOCUMENTO = "cliente_predeterminado_documento";
    public static final String MESES_RETENCION_PEDIDOS = "meses_retencion_pedidos";
}
