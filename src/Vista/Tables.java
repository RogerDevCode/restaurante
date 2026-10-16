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
}
