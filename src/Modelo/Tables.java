
package Modelo;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public class Tables extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
            JTable jtable, Object o, boolean isSelected, boolean hasFocus, int row, int col) {
        
        // Llamada obligatoria al método padre
        super.getTableCellRendererComponent(jtable, o, isSelected, hasFocus, row, col);
        
        // Si la fila está seleccionada, respetamos los colores por defecto del sistema
        if (isSelected) {
            return this;
        }

        // Validación segura para evitar NullPointerException si la columna 6 está vacía
        Object value = jtable.getValueAt(row, 6);
        if (value != null) {
            String estado = value.toString();
            
            // Usamos switch tradicional compatible con cualquier versión de Java
            switch (estado) {
                case "PENDIENTE":
                    setBackground(new Color(255, 51, 51));
                    setForeground(Color.white);
                    break;
                case "FINALIZADO":
                    setBackground(new Color(0, 102, 102));
                    setForeground(Color.white);
                    break;
                default:
                    setBackground(Color.white);
                    setForeground(Color.black);
                    break;
            }
        } else {
            // Valores por defecto si la celda es nula
            setBackground(Color.white);
            setForeground(Color.black);
        }

        return this;
    }
}