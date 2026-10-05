package Vista;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Renderer Swing que colorea pedidos según su estado. */
public class Tables extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
            JTable tabla, Object valor, boolean seleccionado, boolean enfocado, int fila, int columna) {
        super.getTableCellRendererComponent(tabla, valor, seleccionado, enfocado, fila, columna);
        if (seleccionado) {
            return this;
        }

        Object estadoCelda = tabla.getValueAt(fila, 6);
        String estado = estadoCelda == null ? "" : estadoCelda.toString();
        switch (estado) {
            case "PENDIENTE":
                setBackground(new Color(255, 51, 51));
                setForeground(Color.WHITE);
                break;
            case "FINALIZADO":
                setBackground(new Color(0, 102, 102));
                setForeground(Color.WHITE);
                break;
            default:
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
                break;
        }
        return this;
    }
}
