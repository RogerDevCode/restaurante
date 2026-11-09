package Vista;

import Modelo.Platos;
import java.awt.Color;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

/**
 * Renderer de platos unificado para todo el sistema (toma de pedidos, split-screen y catálogo):
 * - Coloreado de celda/fila según la categoría del plato.
 * - Contraste cromático dinámico para el texto (blanco o negro según la luminancia del fondo).
 * - Distintivo visual '★ ' para platos marcados como favoritos.
 * - Distintivo visual '🔥 ' para los platos con mayor ranking de pedidos recientes.
 * - Tooltip detallado con categoría, favorito y cantidad de pedidos recientes.
 */
public class RendererPlato implements TableCellRenderer {

    /** Proporción de color de categoría para filas pares (más suave, mayor luminosidad). */
    public static final double ATENUACION_ZEBRA_PAR = 0.22;
    /** Proporción de color de categoría para filas impares (~20-30% más saturado/contrastado). */
    public static final double ATENUACION_ZEBRA_IMPAR = 0.32;

    /** Fondo neutro para filas pares sin categoría (blanco). */
    public static final Color COLOR_ZEBRA_NEUTRO_PAR = Color.WHITE;
    /** Fondo neutro para filas impares sin categoría (Slate-50 / gris tenue). */
    public static final Color COLOR_ZEBRA_NEUTRO_IMPAR = new Color(248, 250, 252);

    private final Map<Integer, Platos> porId;
    private final JLabel etiqueta;

    public RendererPlato(Map<Integer, Platos> porId) {
        this.porId = porId;
        this.etiqueta = new JLabel();
        this.etiqueta.setOpaque(true);
        this.etiqueta.setHorizontalAlignment(SwingConstants.LEFT);
        this.etiqueta.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    /**
     * Calcula el fondo cromático de una fila respetando la categoría y aplicando
     * el paso de cebra (~20% de contraste cromático entre filas de la misma categoría).
     */
    public static Color calcularFondoFila(Platos p, int fila) {
        if (p != null && p.getCategoriaColor() != null && !p.getCategoriaColor().isBlank()) {
            try {
                double prop = (fila % 2 == 0) ? ATENUACION_ZEBRA_PAR : ATENUACION_ZEBRA_IMPAR;
                return Modelo.PaletaCategorias.atenuar(p.getCategoriaColor(), prop);
            } catch (Exception ignored) {}
        }
        return (fila % 2 == 0) ? COLOR_ZEBRA_NEUTRO_PAR : COLOR_ZEBRA_NEUTRO_IMPAR;
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
            boolean enfocado, int fila, int columna) {
        etiqueta.setEnabled(tabla == null || tabla.isEnabled());
        etiqueta.setFont(tabla != null ? tabla.getFont() : etiqueta.getFont());
        String valorStr = valor == null ? "" : valor.toString();
        etiqueta.setText(valorStr);
        etiqueta.setToolTipText(null);

        Platos p = null;
        if (tabla != null && porId != null) {
            Object idObj = null;
            try {
                int modelRow = tabla.convertRowIndexToModel(fila);
                idObj = tabla.getModel().getValueAt(modelRow, 0);
            } catch (Exception e) {
                try {
                    idObj = tabla.getValueAt(fila, 0);
                } catch (Exception ignored) {}
            }
            if (idObj != null) {
                try {
                    p = porId.get(Integer.parseInt(idObj.toString()));
                } catch (NumberFormatException ignored) {}
            }
        }

        if (seleccionado && tabla != null) {
            etiqueta.setBackground(tabla.getSelectionBackground());
            etiqueta.setForeground(tabla.getSelectionForeground());
        } else {
            Color fondo = calcularFondoFila(p, fila);
            etiqueta.setBackground(fondo);
            etiqueta.setForeground(etiqueta.isEnabled() ? colorContraste(fondo) : javax.swing.UIManager.getColor("Label.disabledForeground"));
        }

        int colModelo = (tabla != null) ? tabla.convertColumnIndexToModel(columna) : columna;
        etiqueta.setHorizontalAlignment(colModelo == 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
        if (colModelo == 1 && p != null) {
            String texto = (p.getNombre() != null && !p.getNombre().isBlank()) ? p.getNombre() : valorStr;
            while (true) {
                if (texto.startsWith("★ ")) {
                    texto = texto.substring("★ ".length());
                } else if (texto.startsWith("🔥 ")) {
                    texto = texto.substring("🔥 ".length());
                } else {
                    break;
                }
            }
            StringBuilder prefijo = new StringBuilder();
            if (p.isFavorito()) {
                prefijo.append("★ ");
            }
            if (p.getRankingTotal() != null && p.getRankingTotal().compareTo(BigDecimal.ZERO) > 0) {
                prefijo.append("🔥 ");
            }
            etiqueta.setText(prefijo.toString() + texto);
        }

        if (p != null) {
            StringBuilder tip = new StringBuilder();
            if (p.isFavorito()) {
                tip.append("★ Favorito. ");
            }
            if (p.getRankingTotal() != null && p.getRankingTotal().compareTo(BigDecimal.ZERO) > 0) {
                tip.append("🔥 ").append(p.getRankingTotal().intValue()).append(" pedidos recientes. ");
            }
            if (p.getCategoriaNombre() != null && !p.getCategoriaNombre().isBlank()) {
                tip.append("Categoría: ").append(p.getCategoriaNombre());
            }
            if (tip.length() > 0) {
                etiqueta.setToolTipText(tip.toString().trim());
            }
        }
        return etiqueta;
    }

    public static Color colorContraste(Color fondo) {
        if (fondo == null) return Color.BLACK;
        double luminancia = (0.299 * fondo.getRed() + 0.587 * fondo.getGreen() + 0.114 * fondo.getBlue()) / 255.0;
        return luminancia > 0.5 ? Color.BLACK : Color.WHITE;
    }
}
