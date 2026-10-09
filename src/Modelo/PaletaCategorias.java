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