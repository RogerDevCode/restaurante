package Vista;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Diálogo de broma inofensivo entre programadores (Easter Egg).
 * Presenta una advertencia ficticia de licencia con barra dinámica,
 * sin ejecutar NINGUNA acción destructiva en la base de datos.
 */
public class DialogoAlertaLicenciaBroma extends JDialog {

    private static final Color COLOR_ROJO_ALERTA = new Color(192, 57, 43);
    private static final Color COLOR_FONDO_CABECERA = new Color(120, 40, 31);
    private static final Color COLOR_FONDO_VENTANA = new Color(254, 249, 231);

    private JProgressBar barraProgreso;
    private JLabel lblFaseActual;
    private Timer temporizador;
    private int progreso = 0;

    public DialogoAlertaLicenciaBroma(Frame parent) {
        super(parent, "🚨 ALERTA CRÍTICA DE SEGURIDAD · SISTEMA NO LICENCIADO", true);
        inicializarUi();
    }

    public static void mostrar() {
        if (SwingUtilities.isEventDispatchThread()) {
            crearYMostrar();
        } else {
            try {
                SwingUtilities.invokeAndWait(DialogoAlertaLicenciaBroma::crearYMostrar);
            } catch (Exception ignored) {}
        }
    }

    private static void crearYMostrar() {
        DialogoAlertaLicenciaBroma dialogo = new DialogoAlertaLicenciaBroma(null);
        dialogo.setVisible(true);
    }

    private void inicializarUi() {
        // Tamaño incrementado un 30% (740 x 490) con tipografía grande y legible
        setSize(740, 490);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel panelPrincipal = new JPanel(new BorderLayout(0, 0));
        panelPrincipal.setBackground(COLOR_FONDO_VENTANA);
        panelPrincipal.setBorder(BorderFactory.createLineBorder(COLOR_ROJO_ALERTA, 5));

        panelPrincipal.add(crearCabecera(), BorderLayout.NORTH);
        panelPrincipal.add(crearCuerpo(), BorderLayout.CENTER);
        panelPrincipal.add(crearBotonera(), BorderLayout.SOUTH);

        setContentPane(panelPrincipal);
        configurarAtajoEsc();
        iniciarAnimacionBroma();
    }

    private JPanel crearCabecera() {
        JPanel cab = new JPanel(new BorderLayout(14, 6));
        cab.setBackground(COLOR_FONDO_CABECERA);
        cab.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel lblIcono = new JLabel("⚠️");
        lblIcono.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 46));
        cab.add(lblIcono, BorderLayout.WEST);

        JPanel pnlTexto = new JPanel();
        pnlTexto.setLayout(new BoxLayout(pnlTexto, BoxLayout.Y_AXIS));
        pnlTexto.setOpaque(false);

        JLabel lblTit = new JLabel("ADVERTENCIA CRÍTICA: COPIA NO AUTORIZADA");
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 19));
        lblTit.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Este programa no cuenta con una licencia oficial válida registrada.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblSub.setForeground(new Color(250, 219, 216));

        pnlTexto.add(lblTit);
        pnlTexto.add(Box.createVerticalStrut(4));
        pnlTexto.add(lblSub);

        cab.add(pnlTexto, BorderLayout.CENTER);
        return cab;
    }

    private JPanel crearCuerpo() {
        JPanel cuerpo = new JPanel();
        cuerpo.setLayout(new BoxLayout(cuerpo, BoxLayout.Y_AXIS));
        cuerpo.setOpaque(false);
        cuerpo.setBorder(new EmptyBorder(22, 28, 16, 28));

        JLabel lblAmenaza1 = new JLabel("Protocolo de seguridad activado por el fabricante:");
        lblAmenaza1.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblAmenaza1.setForeground(COLOR_ROJO_ALERTA);
        lblAmenaza1.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(lblAmenaza1);

        cuerpo.add(Box.createVerticalStrut(10));

        JLabel lblAmenaza2 = new JLabel("<html><span style='font-size:13pt; line-height: 1.4; color: #2c3e50;'>"
                + "Se procederá al <b>borrado total e irreversible de la Base de Datos</b> y archivos de respaldo.</span></html>");
        lblAmenaza2.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lblAmenaza2.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(lblAmenaza2);

        cuerpo.add(Box.createVerticalStrut(20));

        barraProgreso = new JProgressBar(0, 100);
        barraProgreso.setStringPainted(true);
        barraProgreso.setFont(new Font("Segoe UI", Font.BOLD, 15));
        barraProgreso.setForeground(COLOR_ROJO_ALERTA);
        barraProgreso.setPreferredSize(new Dimension(680, 36));
        barraProgreso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        barraProgreso.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(barraProgreso);

        cuerpo.add(Box.createVerticalStrut(14));

        lblFaseActual = new JLabel("Iniciando conexión para purga...");
        lblFaseActual.setFont(new Font("Monospaced", Font.BOLD, 14));
        lblFaseActual.setForeground(new Color(120, 40, 31));
        lblFaseActual.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(lblFaseActual);

        cuerpo.add(Box.createVerticalGlue());
        return cuerpo;
    }

    private JPanel crearBotonera() {
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 16));
        sur.setOpaque(false);
        sur.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(245, 183, 177)));

        JButton btnCancelar = new JButton("🛑 CANCELAR BORRADO (ESC)");
        btnCancelar.setName("btnCancelarBroma");
        btnCancelar.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setBackground(new Color(192, 57, 43));
        btnCancelar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelar.setFocusPainted(false);
        btnCancelar.setPreferredSize(new Dimension(340, 46));
        btnCancelar.addActionListener(e -> revelarBromaYFinalizar("¡Cancelación de emergencia activada!"));

        sur.add(btnCancelar);
        return sur;
    }

    private void configurarAtajoEsc() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelarBroma");
        root.getActionMap().put("cancelarBroma", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                revelarBromaYFinalizar("¡Cancelación por teclado recibida!");
            }
        });
    }

    private void iniciarAnimacionBroma() {
        // Ticks cada 80ms para un recorrido total de ~8 segundos
        temporizador = new Timer(80, e -> {
            progreso++;
            barraProgreso.setValue(progreso);

            if (progreso <= 15) {
                lblFaseActual.setText("Conectando al motor MySQL (DROP DATABASE restaurante)...");
            } else if (progreso <= 35) {
                lblFaseActual.setText("Eliminando tablas: pedidos, salas, platos, clientes...");
            } else if (progreso <= 60) {
                lblFaseActual.setText("Sobrescribiendo sectores de disco y datos fiscales...");
            } else if (progreso <= 80) {
                lblFaseActual.setText("Purgando respaldos locales y volcados .sql...");
            } else if (progreso <= 99) {
                lblFaseActual.setText("Destruyendo claves y credenciales de acceso...");
            } else {
                temporizador.stop();
                lblFaseActual.setText("¡Borrado completado con éxito!");
                revelarBromaYFinalizar("¡El contador llegó al 100%!");
            }
        });
        temporizador.start();
    }

    private void revelarBromaYFinalizar(String motivo) {
        if (temporizador != null && temporizador.isRunning()) {
            temporizador.stop();
        }

        String mensajeHtml = "<html><body style='font-family: Segoe UI, Arial, sans-serif; padding: 8px;'>"
                + "<h2 style='color: #196f3d; margin-top: 0; font-size: 16pt;'>😅 ¡TRANQUILO, ES UNA BROMA ENTRE PROGRAMADORES! 😉</h2>"
                + "<p style='font-size: 12pt; color: #2c3e50; line-height: 1.5;'>"
                + "• Ningún dato ni tabla fue tocado.<br>"
                + "• Tu Base de Datos y tus respaldos están <b>100% intactos y seguros</b>."
                + "</p>"
                + "<p style='font-size: 11pt; color: #7f8c8d; font-style: italic; margin-top: 10px;'>"
                + "Iniciando sistema normalmente..."
                + "</p>"
                + "</body></html>";

        JOptionPane.showMessageDialog(this, mensajeHtml, "🎉 Broma de Desarrolladores", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
