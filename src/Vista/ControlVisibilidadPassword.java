package Vista;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * Control visual reutilizable para alternar la visibilidad de contraseñas (mostrar/ocultar)
 * con icono vectorial Java2D de ojo / ojo tachado y soporte para cualquier JPasswordField.
 */
public final class ControlVisibilidadPassword {

    private ControlVisibilidadPassword() {}

    /**
     * Envuelve un JPasswordField en un JPanel con un botón de conmutación de visibilidad ("ojo").
     * @param campo Campo de contraseña a controlar
     * @param fondo Color de fondo opcional (o null si es transparente/heredado)
     * @return JPanel contenedor con el campo y el botón de visibilidad
     */
    public static JPanel crearPanelPassword(JPasswordField campo, Color fondo) {
        if (campo == null) {
            throw new IllegalArgumentException("El campo de contraseña es obligatorio.");
        }
        char echoOriginal = campo.getEchoChar() != 0 ? campo.getEchoChar() : '•';

        JButton btnOjo = new JButton();
        btnOjo.setName("btnOjoPassword");
        btnOjo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnOjo.setFocusable(false);
        btnOjo.setBorderPainted(false);
        btnOjo.setContentAreaFilled(false);
        btnOjo.setOpaque(false);
        btnOjo.setPreferredSize(new Dimension(36, 28));

        boolean[] visible = new boolean[]{false};

        Runnable actualizarEstado = () -> {
            if (visible[0]) {
                campo.setEchoChar((char) 0);
                btnOjo.setIcon(new IconoOjo(true, 18, 14, new Color(40, 110, 95)));
                btnOjo.setToolTipText("Ocultar contraseña");
            } else {
                campo.setEchoChar(echoOriginal);
                btnOjo.setIcon(new IconoOjo(false, 18, 14, new Color(120, 130, 125)));
                btnOjo.setToolTipText("Mostrar contraseña");
            }
        };

        btnOjo.addActionListener(e -> {
            visible[0] = !visible[0];
            actualizarEstado.run();
            campo.requestFocusInWindow();
        });

        actualizarEstado.run();

        JPanel panel = new JPanel(new BorderLayout(0, 0));
        if (fondo != null) {
            panel.setOpaque(true);
            panel.setBackground(fondo);
        } else {
            panel.setOpaque(false);
        }
        panel.add(campo, BorderLayout.CENTER);
        panel.add(btnOjo, BorderLayout.EAST);
        return panel;
    }

    /**
     * Icono vectorial que dibuja un ojo abierto (cuando visible=false, acción de mostrar)
     * o un ojo tachado con barra diagonal (cuando visible=true, acción de ocultar).
     */
    public static final class IconoOjo implements Icon {
        private final boolean visible;
        private final int ancho;
        private final int alto;
        private final Color color;

        public IconoOjo(boolean visible, int ancho, int alto, Color color) {
            this.visible = visible;
            this.ancho = ancho;
            this.alto = alto;
            this.color = color != null ? color : Color.DARK_GRAY;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);

            int w = ancho;
            int h = alto;
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // Silueta almendrada del ojo
            Path2D.Double contorno = new Path2D.Double();
            double yMid = y + h / 2.0;
            contorno.moveTo(x + 1.5, yMid);
            contorno.quadTo(x + w / 2.0, y + 1.0, x + w - 1.5, yMid);
            contorno.quadTo(x + w / 2.0, y + h - 1.0, x + 1.5, yMid);
            g2.draw(contorno);

            // Pupila central
            double r = Math.min(w, h) * 0.23;
            Ellipse2D.Double pupila = new Ellipse2D.Double(x + w / 2.0 - r, yMid - r, r * 2, r * 2);
            g2.fill(pupila);

            // Barra diagonal cuando la contraseña está visible (tachado para ocultar)
            if (visible) {
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(x + 2, y + h - 1, x + w - 2, y + 1));
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return ancho;
        }

        @Override
        public int getIconHeight() {
            return alto;
        }

        public boolean isVisible() {
            return visible;
        }
    }
}
