package Vista;

import Servicio.ServicioMesa;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Renderer Swing que colorea pedidos según su estado (paleta central ServicioMesa). */
public class Tables extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
            JTable tabla, Object valor, boolean seleccionado, boolean enfocado, int fila, int columna) {
        super.getTableCellRendererComponent(tabla, valor, seleccionado, enfocado, fila, columna);
        if (seleccionado) {
            return this;
        }

        Object estadoCelda = valorModelo(tabla, fila, 6);
        String estado = estadoCelda == null ? "" : estadoCelda.toString();
        switch (estado) {
            case "PENDIENTE":
                setBackground(ServicioMesa.FONDO_MESA_OCUPADA);
                setForeground(Color.WHITE);
                break;
            case "FINALIZADO":
                setBackground(ServicioMesa.FONDO_MESA_LIBRE);
                setForeground(Color.WHITE);
                break;
            default:
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
                break;
        }
        setHorizontalAlignment(esNumerico(valor) ? javax.swing.SwingConstants.RIGHT : javax.swing.SwingConstants.LEFT);
        return this;
    }

    /**
     * Quita una columna del modelo de la vista (p. ej. el ID interno) sin quitarla
     * del {@code TableModel}: los datos siguen ahí para selecciones, pero el usuario
     * no la ve. Después de esto, leer siempre desde el modelo convirtiendo la fila:
     * <pre>
     * int filaModelo = tabla.convertRowIndexToModel(tabla.getSelectedRow());
     * Object id = tabla.getModel().getValueAt(filaModelo, INDICE_ID_MODELO);
     * </pre>
     * o usar {@link #valorModelo}. Llamar después de fijar el modelo
     * (y tras cada {@code setModel} que lo reemplace).
     */
    public static void ocultarColumnaModelo(JTable tabla, int indiceModelo) {
        if (tabla == null) {
            return;
        }
        int vista;
        try {
            vista = tabla.convertColumnIndexToView(indiceModelo);
        } catch (Exception e) {
            return;
        }
        if (vista < 0 || vista >= tabla.getColumnModel().getColumnCount()) {
            return;
        }
        tabla.getColumnModel().removeColumn(tabla.getColumnModel().getColumn(vista));
    }

    /**
     * Lee una celda por índice de modelo, convirtiendo la fila de vista (ordenamiento/filtros).
     */
    public static Object valorModelo(JTable tabla, int filaVista, int colModelo) {
        int filaModelo = tabla.convertRowIndexToModel(filaVista);
        return tabla.getModel().getValueAt(filaModelo, colModelo);
    }

    /** Indica si el valor es numérico (tolera símbolos de moneda, [!], % y referencias entre paréntesis). */
    public static boolean esNumerico(Object valor) {
        if (valor instanceof Number) {
            return true;
        }
        if (valor == null) {
            return false;
        }
        String s = valor.toString().trim();
        if (s.isEmpty()) {
            return false;
        }
        if (s.contains("(")) {
            s = s.substring(0, s.indexOf('(')).trim();
        }
        s = s.replace("Bs.", "").replace("Bs", "").replace("$", "")
                .replace("[!]", "").replace("★", "").replace("🔥", "").trim();
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return false;
        }
        try {
            new java.math.BigDecimal(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Renderer genérico: numéricos a la derecha, texto a la izquierda. */
    private static final class RendererNumerico extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable tabla, Object valor, boolean seleccionado, boolean enfocado, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, seleccionado, enfocado, fila, columna);
            setHorizontalAlignment(esNumerico(valor)
                    ? javax.swing.SwingConstants.RIGHT : javax.swing.SwingConstants.LEFT);
            return this;
        }
    }

    /** Aplica alineación numérica-derecha a las celdas sin renderer propio. */
    public static void aplicarAlineacionNumerica(JTable tabla) {
        if (tabla == null) {
            return;
        }
        tabla.setDefaultRenderer(Object.class, new RendererNumerico());
    }

    private static javax.swing.ImageIcon iconoMesa48;

    /**
     * Tarjeta de mesa estándar para todos los modos de atención directa (B clásico
     * y C split): icono 48px, código, unidad, estado/mesonero y borde de selección.
     * El tooltip y la acción los define cada modo.
     */
    public static javax.swing.JButton crearBotonMesa(
            String codigoMesa, String lineaUnidad, String lineaEstado, boolean ocupada,
            boolean seleccionada, String toolTip, java.awt.event.ActionListener accion) {
        javax.swing.JButton b = new javax.swing.JButton();
        b.setPreferredSize(new java.awt.Dimension(120, 110));
        b.setMinimumSize(new java.awt.Dimension(100, 95));
        b.setFocusable(false);
        b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        if (iconoMesa48 == null) {
            java.net.URL url = Tables.class.getResource("/Img/mesa.png");
            if (url != null) {
                java.awt.Image img = new javax.swing.ImageIcon(url).getImage()
                        .getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
                iconoMesa48 = new javax.swing.ImageIcon(img);
            }
        }
        if (iconoMesa48 != null) {
            b.setIcon(iconoMesa48);
            b.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            b.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        }
        b.setBackground(ocupada ? ServicioMesa.FONDO_MESA_OCUPADA : ServicioMesa.FONDO_MESA_LIBRE);
        b.setForeground(java.awt.Color.WHITE);
        StringBuilder html = new StringBuilder("<html><center><font size='+2'><b>")
                .append(codigoMesa).append("</b></font><br>")
                .append("<span style='font-size:12px;'><b>").append(lineaUnidad).append("</b></span>");
        if (lineaEstado != null && !lineaEstado.isBlank()) {
            html.append("<br><span style='font-size:12px;'><b>").append(lineaEstado).append("</b></span>");
        }
        b.setText(html.append("</center></html>").toString());
        if (toolTip != null) {
            b.setToolTipText(toolTip);
        }
        b.setBorder(javax.swing.BorderFactory.createLineBorder(
                seleccionada ? ServicioMesa.BORDE_MESA_SELECCIONADA : b.getBackground().darker(),
                seleccionada ? 4 : 2));
        if (accion != null) {
            b.addActionListener(accion);
        }
        return b;
    }

    /** Identificador de la columna vista de moneda (U/B), sin columna en el modelo. */
    public static final String ID_COLUMNA_MONEDA = "MONEDA_U_B";

    /**
     * Agrega junto al precio una columna estrecha solo-vista con "$" (USD) o "Bs." (VES)
     * según la moneda de cada fila. No altera el modelo ni sus índices.
     *
     * @param tabla tabla destino
     * @param colModeloPrecio índice de modelo de la columna de precio unitario
     * @param monedaPorFilaModelo función fila-modelo -&gt; "U"/"$", "B"/"Bs." o "" (desconocida)
     */
    public static void agregarColumnaMoneda(JTable tabla, int colModeloPrecio,
            java.util.function.Function<Integer, String> monedaPorFilaModelo) {
        if (tabla == null || monedaPorFilaModelo == null) {
            return;
        }
        javax.swing.table.TableColumnModel cm = tabla.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            if (ID_COLUMNA_MONEDA.equals(cm.getColumn(i).getIdentifier())) {
                return;
            }
        }
        int vistaPrecio;
        try {
            vistaPrecio = tabla.convertColumnIndexToView(colModeloPrecio);
        } catch (Exception e) {
            return;
        }
        if (vistaPrecio < 0) {
            return;
        }
        javax.swing.table.TableColumn col = new javax.swing.table.TableColumn(colModeloPrecio);
        col.setIdentifier(ID_COLUMNA_MONEDA);
        col.setHeaderValue("M");
        col.setMinWidth(26);
        col.setMaxWidth(44);
        col.setPreferredWidth(30);
        col.setResizable(true);
        col.setCellRenderer(new RendererMoneda(monedaPorFilaModelo));
        cm.addColumn(col);
        try {
            cm.moveColumn(cm.getColumnCount() - 1, vistaPrecio + 1);
        } catch (Exception ignored) {
        }
    }

    /** Renderer de la columna M: $/Bs. centrado en negrita con tooltip de moneda. */
    private static final class RendererMoneda extends DefaultTableCellRenderer {
        private final java.util.function.Function<Integer, String> monedaPorFilaModelo;

        RendererMoneda(java.util.function.Function<Integer, String> monedaPorFilaModelo) {
            this.monedaPorFilaModelo = monedaPorFilaModelo;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable tabla, Object valor, boolean seleccionado, boolean enfocado, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, "", seleccionado, enfocado, fila, columna);
            String m = "";
            try {
                int mf = tabla.convertRowIndexToModel(fila);
                String r = monedaPorFilaModelo.apply(mf);
                if (r != null) {
                    String n = r.trim().toUpperCase(java.util.Locale.ROOT);
                    if ("U".equals(n) || "$".equals(n)) {
                        m = "$";
                    } else if ("B".equals(n) || n.startsWith("BS")) {
                        m = "Bs.";
                    }
                }
            } catch (Exception ignored) {
            }
            setText(m);
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            setFont(getFont().deriveFont(java.awt.Font.BOLD));
            setToolTipText("$".equals(m) ? "Dólares (USD)" : "Bs.".equals(m) ? "Bolívares (VES)" : null);
            return this;
        }
    }

    /**
     * Fija la cabecera de una columna de precio a la moneda mostrada: "USD" o "VES"
     * si todas las filas comparten moneda, "USD/VES" si hay mezcla. Sin filas con
     * moneda conocida conserva la cabecera actual. Solo cambia el título visible.
     */
    public static void fijarCabeceraMoneda(JTable tabla, int colModeloPrecio,
            java.util.Collection<String> monedas) {
        if (tabla == null || monedas == null || monedas.isEmpty()) {
            return;
        }
        java.util.Set<String> distintas = new java.util.TreeSet<>();
        for (String m : monedas) {
            if (m == null) {
                continue;
            }
            String n = m.trim().toUpperCase(java.util.Locale.ROOT);
            if ("USD".equals(n) || "VES".equals(n)) {
                distintas.add(n);
            }
        }
        if (distintas.isEmpty()) {
            return;
        }
        String titulo = distintas.size() == 1 ? distintas.iterator().next() : "USD/VES";
        int vista;
        try {
            vista = tabla.convertColumnIndexToView(colModeloPrecio);
        } catch (Exception e) {
            return;
        }
        if (vista < 0 || vista >= tabla.getColumnModel().getColumnCount()) {
            return;
        }
        tabla.getColumnModel().getColumn(vista).setHeaderValue(titulo);
        if (tabla.getTableHeader() != null) {
            tabla.getTableHeader().repaint();
        }
    }
}
