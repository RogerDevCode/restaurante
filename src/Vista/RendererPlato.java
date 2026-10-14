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

    private final Map<Integer, Platos> porId;
    private final JLabel etiqueta;

    public RendererPlato(Map<Integer, Platos> porId) {
        this.porId = porId;
        this.etiqueta = new JLabel();
        this.etiqueta.setOpaque(true);
        this.etiqueta.setHorizontalAlignment(SwingConstants.LEFT);
        this.etiqueta.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
            boolean enfocado, int fila, int columna) {
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
            Color fondo = null;
            if (p != null && p.getCategoriaColor() != null) {
                try {
                    fondo = Color.decode(p.getCategoriaColor());
                } catch (NumberFormatException ignored) {}
            }
            if (fondo != null) {
                etiqueta.setBackground(fondo);
                etiqueta.setForeground(colorContraste(fondo));
            } else if (tabla != null) {
                etiqueta.setBackground(tabla.getBackground());
                etiqueta.setForeground(tabla.getForeground());
            } else {
                etiqueta.setBackground(Color.WHITE);
                etiqueta.setForeground(Color.BLACK);
            }
        }

        if (columna == 1 && p != null) {
            String texto = valorStr;
            while (texto.startsWith("★ ") || texto.startsWith("🔥 ")) {
                texto = texto.substring(2);
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
