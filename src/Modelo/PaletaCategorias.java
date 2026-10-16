package Modelo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Paleta de colores para categorías. Cada color se usa una sola vez para que
 * el menú sea legible. Incluye el color de la categoría semilla "General".
 */
public final class PaletaCategorias {

    public static final String COLOR_CATEGORIA_GENERAL = "#6B7280";

    private static final List<String> PALETA = List.of(
            "#E45A4E", "#E56F1A", "#C78605", "#E9B10C", "#71B314", "#2E9E4F",
            "#0F9D9D", "#558EDD", "#2563EB", "#4F46E5", "#A270DB", "#E1489A",
            COLOR_CATEGORIA_GENERAL);

    private PaletaCategorias() {
    }

    /**
     * Atenúa un color mezclándolo con blanco: 1.0 lo deja intacto, 0.0 da blanco.
     * Usado para fondos tenues (p. ej. 0.30 = 30 % del valor actual).
     */
    public static java.awt.Color atenuar(java.awt.Color color, double proporcion) {
        if (color == null) {
            return java.awt.Color.WHITE;
        }
        double f = Math.min(1.0, Math.max(0.0, proporcion));
        int r = (int) Math.round(255 - (255 - color.getRed()) * f);
        int g = (int) Math.round(255 - (255 - color.getGreen()) * f);
        int b = (int) Math.round(255 - (255 - color.getBlue()) * f);
        return new java.awt.Color(r, g, b);
    }

    /** Atenúa un color en hexadecimal ("#RRGGBB"); ante formato inválido, gris tenue. */
    public static java.awt.Color atenuar(String hex, double proporcion) {
        if (hex == null) {
            return atenuar(java.awt.Color.GRAY, proporcion);
        }
        try {
            return atenuar(java.awt.Color.decode(hex), proporcion);
        } catch (NumberFormatException ex) {
            return atenuar(java.awt.Color.GRAY, proporcion);
        }
    }

    public static List<String> paleta() {
        return PALETA;
    }

    public static Set<String> coloresDisponibles(Set<String> usados) {
        Set<String> resultado = new LinkedHashSet<>(PALETA);
        if (usados != null) {
            resultado.removeAll(usados);
        }
        return resultado;
    }
}