package Vista;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TablesTest {
    @Test
    public void coloreaPedidoPendienteYFinalizado() {
        Tables renderer = new Tables();
        JTable pendiente = tabla("PENDIENTE");
        Component rojo = renderer.getTableCellRendererComponent(
                pendiente, "pedido", false, false, 0, 0);
        assertEquals(new Color(255, 51, 51), rojo.getBackground());
        assertEquals(Color.WHITE, rojo.getForeground());

        JTable finalizado = tabla("FINALIZADO");
        Component verde = renderer.getTableCellRendererComponent(
                finalizado, "pedido", false, false, 0, 0);
        assertEquals(new Color(0, 102, 102), verde.getBackground());
        assertEquals(Color.WHITE, verde.getForeground());
    }

    private JTable tabla(String estado) {
        Object[][] filas = {{1, 1, 1, "fecha", 10.0, "sala", estado}};
        return new JTable(filas, new Object[]{"id", "sala", "mesa", "fecha", "total", "usuario", "estado"});
    }
}
