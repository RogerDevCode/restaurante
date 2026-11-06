package Vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Notificación flotante (Toast) no bloqueante para Java Swing.
 *
 * Muestra alertas y confirmaciones efímeras en la esquina superior o central de la ventana
 * sin interrumpir el flujo operativo con diálogos modales (evita JOptionPane innecesarios).
 */
public final class ToastNotificacion extends JPanel {

    public enum Tipo {
        EXITO(new Color(16, 185, 129), "✓"),
        INFO(new Color(56, 189, 248), "ℹ"),
        ADVERTENCIA(new Color(245, 158, 11), "⚠"),
        ERROR(new Color(239, 68, 68), "✕");

        private final Color colorAcento;
        private final String icono;

        Tipo(Color colorAcento, String icono) {
            this.colorAcento = colorAcento;
            this.icono = icono;
        }

        public Color colorAcento() {
            return colorAcento;
        }

        public String icono() {
            return icono;
        }
    }

    private static final int DURACION_DEFECTO_MS = 2500;
    private static final Color FONDO_TOAST = new Color(30, 41, 59, 240); // Slate-800 con ligera transparencia
    private static final Color BORDE_TOAST = new Color(51, 65, 85);
    private static final Color TEXTO_BLANCO = new Color(248, 250, 252);

    private final Timer temporizadorAutoCierre;
    private final JLayeredPane capaDestino;

    private ToastNotificacion(JLayeredPane capaDestino, String mensaje, Tipo tipo, int duracionMs) {
        this.capaDestino = capaDestino;
        setLayout(new BorderLayout(10, 0));
        setOpaque(false);
        setFocusable(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

        // Icono de estado
        JLabel lblIcono = new JLabel(tipo.icono());
        lblIcono.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblIcono.setForeground(tipo.colorAcento());

        // Mensaje de texto
        JLabel lblTexto = new JLabel(mensaje);
        lblTexto.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTexto.setForeground(TEXTO_BLANCO);

        // Botón cerrar discreto
        JLabel lblCerrar = new JLabel("×");
        lblCerrar.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblCerrar.setForeground(new Color(148, 163, 184));

        JPanel pnlCentro = new JPanel();
        pnlCentro.setOpaque(false);
        pnlCentro.setLayout(new BoxLayout(pnlCentro, BoxLayout.X_AXIS));
        pnlCentro.add(lblIcono);
        pnlCentro.add(Box.createRigidArea(new Dimension(8, 0)));
        pnlCentro.add(lblTexto);
        pnlCentro.add(Box.createRigidArea(new Dimension(12, 0)));
        pnlCentro.add(lblCerrar);

        add(pnlCentro, BorderLayout.CENTER);

        // Descartar al hacer clic
        MouseAdapter listenerCierre = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                descartar();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (temporizadorAutoCierre.isRunning()) {
                    temporizadorAutoCierre.stop();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!temporizadorAutoCierre.isRunning()) {
                    temporizadorAutoCierre.start();
                }
            }
        };
        addMouseListener(listenerCierre);
        lblTexto.addMouseListener(listenerCierre);
        lblCerrar.addMouseListener(listenerCierre);

        // Timer para cierre automático
        this.temporizadorAutoCierre = new Timer(duracionMs > 0 ? duracionMs : DURACION_DEFECTO_MS, e -> descartar());
        this.temporizadorAutoCierre.setRepeats(false);

        // Limpieza defensiva si el contenedor se destruye o deja de ser visible
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                descartar();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(FONDO_TOAST);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.setColor(BORDE_TOAST);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.dispose();
        super.paintComponent(g);
    }

    public void descartar() {
        if (temporizadorAutoCierre != null && temporizadorAutoCierre.isRunning()) {
            temporizadorAutoCierre.stop();
        }
        if (capaDestino != null) {
            capaDestino.remove(this);
            capaDestino.revalidate();
            capaDestino.repaint();
        }
    }

    /**
     * Muestra una notificación flotante sobre la ventana contenedora.
     */
    public static void mostrar(Component componentePadre, String mensaje, Tipo tipo) {
        mostrar(componentePadre, mensaje, tipo, DURACION_DEFECTO_MS);
    }

    /**
     * Muestra una notificación flotante con duración personalizada.
     */
    public static void mostrar(Component componentePadre, String mensaje, Tipo tipo, int duracionMs) {
        if (mensaje == null || mensaje.isBlank()) {
            return;
        }

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> mostrar(componentePadre, mensaje, tipo, duracionMs));
            return;
        }

        JLayeredPane layeredPane = resolverLayeredPane(componentePadre);
        if (layeredPane == null) {
            return;
        }

        // Remover toasts previos para no solapar mensajes repetitivos
        for (Component c : layeredPane.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof ToastNotificacion toastViejo) {
                toastViejo.descartar();
            }
        }

        ToastNotificacion toast = new ToastNotificacion(layeredPane, mensaje.trim(), tipo != null ? tipo : Tipo.INFO, duracionMs);
        Dimension tamPref = toast.getPreferredSize();
        int ancho = Math.min(tamPref.width, Math.max(260, layeredPane.getWidth() - 40));
        int alto = Math.max(38, tamPref.height);
        int x = (layeredPane.getWidth() - ancho) / 2;
        int y = 20; // Margen superior

        toast.setBounds(x, y, ancho, alto);
        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.moveToFront(toast);
        layeredPane.revalidate();
        layeredPane.repaint();

        toast.temporizadorAutoCierre.start();
    }

    public static void mostrarExito(Component padre, String mensaje) {
        mostrar(padre, mensaje, Tipo.EXITO);
    }

    public static void mostrarInfo(Component padre, String mensaje) {
        mostrar(padre, mensaje, Tipo.INFO);
    }

    public static void mostrarAdvertencia(Component padre, String mensaje) {
        mostrar(padre, mensaje, Tipo.ADVERTENCIA);
    }

    public static void mostrarError(Component padre, String mensaje) {
        mostrar(padre, mensaje, Tipo.ERROR);
    }

    private static JLayeredPane resolverLayeredPane(Component componente) {
        if (componente == null) {
            return null;
        }
        if (componente instanceof RootPaneContainer rpc && rpc.getRootPane() != null) {
            return rpc.getRootPane().getLayeredPane();
        }
        JRootPane root = SwingUtilities.getRootPane(componente);
        if (root != null) {
            return root.getLayeredPane();
        }
        Window win = SwingUtilities.getWindowAncestor(componente);
        if (win instanceof RootPaneContainer rpc && rpc.getRootPane() != null) {
            return rpc.getRootPane().getLayeredPane();
        }
        return null;
    }
}
