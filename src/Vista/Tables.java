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

        Object estadoCelda = tabla.getValueAt(fila, 6);
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
        return this;
    }
}
