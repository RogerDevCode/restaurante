package Vista;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlVisibilidadPasswordTest {

    @Test(expected = IllegalArgumentException.class)
    public void crearPanelPasswordRechazaCampoNulo() {
        ControlVisibilidadPassword.crearPanelPassword(null, Color.WHITE);
    }

    @Test
    public void conmutaVisibilidadDePasswordYActualizaTooltipsEIconos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPasswordField campo = new JPasswordField("secreto123");
            char echoPorDefecto = campo.getEchoChar();
            assertTrue("Debe tener echo char por defecto", echoPorDefecto != 0);

            JPanel panel = ControlVisibilidadPassword.crearPanelPassword(campo, Color.WHITE);
            assertNotNull(panel);

            // Buscar el botón del ojo en el panel
            JButton btnOjo = null;
            for (Component c : panel.getComponents()) {
                if (c instanceof JButton b && "btnOjoPassword".equals(b.getName())) {
                    btnOjo = b;
                    break;
                }
            }
            assertNotNull("El botón de ojo debe estar presente en el panel", btnOjo);
            assertEquals("Mostrar contraseña", btnOjo.getToolTipText());
            assertEquals(echoPorDefecto, campo.getEchoChar());

            // 1er Clic: Mostrar contraseña
            btnOjo.doClick();
            assertEquals((char) 0, campo.getEchoChar());
            assertEquals("Ocultar contraseña", btnOjo.getToolTipText());

            // 2do Clic: Ocultar contraseña nuevamente
            btnOjo.doClick();
            assertEquals(echoPorDefecto, campo.getEchoChar());
            assertEquals("Mostrar contraseña", btnOjo.getToolTipText());
        });
    }

    @Test
    public void iconoOjoRenderizaCorrectamenteEnAmbosEstados() {
        ControlVisibilidadPassword.IconoOjo ojoMostrar = new ControlVisibilidadPassword.IconoOjo(false, 18, 14, Color.BLACK);
        ControlVisibilidadPassword.IconoOjo ojoOcultar = new ControlVisibilidadPassword.IconoOjo(true, 18, 14, Color.BLACK);

        assertEquals(18, ojoMostrar.getIconWidth());
        assertEquals(14, ojoMostrar.getIconHeight());
        assertFalse(ojoMostrar.isVisible());
        assertTrue(ojoOcultar.isVisible());

        BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        try {
            ojoMostrar.paintIcon(null, g2, 5, 5);
            ojoOcultar.paintIcon(null, g2, 5, 5);
        } finally {
            g2.dispose();
        }
    }
}
