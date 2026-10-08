package Modelo;

/**
 * Modos de salida disponibles para la emisión de tickets y comprobantes.
 */
public enum ModoSalidaTicket {
    TERMICA_DIRECTA("Tickera Térmica (Directa 80mm)", "DIRECTA"),
    VISOR_PDF("Archivo PDF (Visor Estándar)", "VISOR"),
    PDF24_CREATOR("PDF24 Creator (Asistente / Virtual)", "PDF24");

    private final String etiqueta;
    private final String codigo;

    ModoSalidaTicket(String etiqueta, String codigo) {
        this.etiqueta = etiqueta;
        this.codigo = codigo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCodigo() {
        return codigo;
    }

    @Override
    public String toString() {
        return etiqueta;
    }

    public static ModoSalidaTicket desdeCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return TERMICA_DIRECTA;
        }
        for (ModoSalidaTicket m : values()) {
            if (m.codigo.equalsIgnoreCase(codigo.trim()) || m.name().equalsIgnoreCase(codigo.trim())) {
                return m;
            }
        }
        return TERMICA_DIRECTA;
    }
}
