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
    public static final String MODO_ATENCION = "modo_atencion";
    public static final String FILTRO_RAPIDO_PLATOS = "filtro_rapido_platos";
    public static final String IMPRIMIR_COMANDA_COCINA = "imprimir_comanda_cocina";
    public static final String IMPRESORA_COCINA_IP = "impresora_cocina_ip";
    public static final String IMPRESORA_COCINA_PUERTO = "impresora_cocina_puerto";
    public static final String IMPRESORA_COCINA_FAILOVER = "impresora_cocina_failover";
    public static final String IMPRESORA_COCINA_BUZZER = "impresora_cocina_buzzer";
    public static final String PANTALLA_COCINA_HABILITADA = "pantalla_cocina_habilitada";
    public static final String TUNEL_SSH_HABILITADO = "tunel_ssh_habilitado";
    public static final String TUNEL_SSH_HOST = "tunel_ssh_host";
    public static final String TUNEL_SSH_PUERTO = "tunel_ssh_puerto";
    public static final String TUNEL_SSH_USUARIO = "tunel_ssh_usuario";
    public static final String TUNEL_SSH_CLAVE_PRIVADA = "tunel_ssh_clave_privada";
    public static final String TUNEL_SSH_PUERTO_REMOTO = "tunel_ssh_puerto_remoto";
    public static final String TUNEL_SSH_TOKEN_ADMIN = "tunel_ssh_token_admin";
    public static final String BROMA_INICIO_CONTEO = "broma_inicio_conteo";
}
