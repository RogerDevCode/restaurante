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
    private JLabel lblAccionActual;
    private JLabel lblEstadoAccion;
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
        // Dimensiones generosas para acomodar fuentes grandes sin esfuerzo de lectura
        setSize(860, 610);
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
        JPanel cab = new JPanel(new BorderLayout(16, 6));
        cab.setBackground(COLOR_FONDO_CABECERA);
        cab.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel lblIcono = new JLabel("⚠️");
        lblIcono.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        cab.add(lblIcono, BorderLayout.WEST);

        JPanel pnlTexto = new JPanel();
        pnlTexto.setLayout(new BoxLayout(pnlTexto, BoxLayout.Y_AXIS));
        pnlTexto.setOpaque(false);

        JLabel lblTit = new JLabel("ADVERTENCIA CRÍTICA: COPIA NO AUTORIZADA");
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 23));
        lblTit.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Este programa no cuenta con una licencia oficial válida registrada.");
        lblSub.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblSub.setForeground(new Color(255, 245, 157)); // Amarillo suave brillante de alto contraste

        pnlTexto.add(lblTit);
        pnlTexto.add(Box.createVerticalStrut(6));
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
        lblAmenaza1.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblAmenaza1.setForeground(COLOR_ROJO_ALERTA);
        lblAmenaza1.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(lblAmenaza1);

        cuerpo.add(Box.createVerticalStrut(10));

        JPanel pnlAlertaTexto = new JPanel();
        pnlAlertaTexto.setLayout(new BoxLayout(pnlAlertaTexto, BoxLayout.Y_AXIS));
        pnlAlertaTexto.setOpaque(false);
        pnlAlertaTexto.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblAmenaza2 = new JLabel("Se procederá al borrado total e irreversible de la Base de Datos");
        lblAmenaza2.setFont(new Font("Segoe UI", Font.BOLD, 21));
        lblAmenaza2.setForeground(new Color(146, 43, 33)); // Rojo oscuro intenso de máxima legibilidad
        lblAmenaza2.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblAmenaza3 = new JLabel("y de todos los archivos de respaldo del sistema.");
        lblAmenaza3.setFont(new Font("Segoe UI", Font.BOLD, 19));
        lblAmenaza3.setForeground(new Color(44, 62, 80)); // Azul oscuro pizarra de alto contraste
        lblAmenaza3.setAlignmentX(LEFT_ALIGNMENT);

        pnlAlertaTexto.add(lblAmenaza2);
        pnlAlertaTexto.add(Box.createVerticalStrut(4));
        pnlAlertaTexto.add(lblAmenaza3);
        cuerpo.add(pnlAlertaTexto);

        cuerpo.add(Box.createVerticalStrut(18));

        barraProgreso = new JProgressBar(0, 100);
        barraProgreso.setStringPainted(true);
        barraProgreso.setFont(new Font("Segoe UI", Font.BOLD, 18));
        barraProgreso.setForeground(COLOR_ROJO_ALERTA);
        barraProgreso.setPreferredSize(new Dimension(780, 42));
        barraProgreso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        barraProgreso.setAlignmentX(LEFT_ALIGNMENT);
        cuerpo.add(barraProgreso);

        cuerpo.add(Box.createVerticalStrut(14));

        // Tarjeta contenedora con fondo blanco y alto contraste para las acciones simuladas
        JPanel pnlFase = new JPanel();
        pnlFase.setLayout(new BoxLayout(pnlFase, BoxLayout.Y_AXIS));
        pnlFase.setBackground(Color.WHITE);
        pnlFase.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(192, 57, 43), 2, true),
                new EmptyBorder(12, 18, 12, 18)
        ));
        pnlFase.setPreferredSize(new Dimension(800, 96));
        pnlFase.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        pnlFase.setAlignmentX(LEFT_ALIGNMENT);

        lblAccionActual = new JLabel("⚡ Conectando al motor MySQL (DROP DATABASE restaurante)...");
        lblAccionActual.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblAccionActual.setForeground(new Color(10, 61, 98)); // Azul marino profundo de alto contraste
        lblAccionActual.setAlignmentX(LEFT_ALIGNMENT);

        lblEstadoAccion = new JLabel("⏳ Conectando con privilegios de administrador...");
        lblEstadoAccion.setFont(new Font("Segoe UI", Font.BOLD, 20)); // Texto grande de estado
        lblEstadoAccion.setForeground(new Color(180, 100, 0)); // Ámbar visible
        lblEstadoAccion.setAlignmentX(LEFT_ALIGNMENT);

        pnlFase.add(lblAccionActual);
        pnlFase.add(Box.createVerticalStrut(6));
        pnlFase.add(lblEstadoAccion);
        cuerpo.add(pnlFase);

        cuerpo.add(Box.createVerticalGlue());
        return cuerpo;
    }

    private JPanel crearBotonera() {
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 16));
        sur.setOpaque(false);
        sur.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(245, 183, 177)));

        JButton btnCancelar = new JButton("🛑 CANCELAR BORRADO (ESC)");
        btnCancelar.setName("btnCancelarBroma");
        btnCancelar.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setBackground(new Color(192, 57, 43));
        btnCancelar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancelar.setFocusPainted(false);
        btnCancelar.setPreferredSize(new Dimension(400, 52));
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
        // Ticks cada 200ms para un recorrido total exacto de 20 segundos (100 pasos * 200ms)
        temporizador = new Timer(200, e -> {
            progreso++;
            barraProgreso.setValue(progreso);

            if (progreso <= 20) {
                lblAccionActual.setText("⚡ Conectando al motor MySQL (DROP DATABASE restaurante)...");
                if (progreso <= 10) {
                    lblEstadoAccion.setText("⏳ Conectando con privilegios de administrador...");
                    lblEstadoAccion.setForeground(new Color(180, 100, 0));
                } else {
                    lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                    lblEstadoAccion.setForeground(new Color(20, 125, 55)); // Verde fuerte vibrante de alto contraste
                }
            } else if (progreso <= 40) {
                lblAccionActual.setText("⚡ Eliminando tablas: pedidos, salas, platos, clientes...");
                if (progreso <= 30) {
                    lblEstadoAccion.setText("⏳ Purgando registros y esquemas de BD...");
                    lblEstadoAccion.setForeground(new Color(180, 100, 0));
                } else {
                    lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                    lblEstadoAccion.setForeground(new Color(20, 125, 55));
                }
            } else if (progreso <= 60) {
                lblAccionActual.setText("⚡ Sobrescribiendo sectores de disco y datos fiscales...");
                if (progreso <= 50) {
                    lblEstadoAccion.setText("⏳ Sobrescribiendo bloques de almacenamiento...");
                    lblEstadoAccion.setForeground(new Color(180, 100, 0));
                } else {
                    lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                    lblEstadoAccion.setForeground(new Color(20, 125, 55));
                }
            } else if (progreso <= 80) {
                lblAccionActual.setText("⚡ Purgando respaldos locales y volcados .sql...");
                if (progreso <= 70) {
                    lblEstadoAccion.setText("⏳ Eliminando copias de seguridad...");
                    lblEstadoAccion.setForeground(new Color(180, 100, 0));
                } else {
                    lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                    lblEstadoAccion.setForeground(new Color(20, 125, 55));
                }
            } else if (progreso <= 99) {
                lblAccionActual.setText("⚡ Destruyendo claves y credenciales de acceso...");
                if (progreso <= 90) {
                    lblEstadoAccion.setText("⏳ Borrando certificados y accesos del sistema...");
                    lblEstadoAccion.setForeground(new Color(180, 100, 0));
                } else {
                    lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                    lblEstadoAccion.setForeground(new Color(20, 125, 55));
                }
            } else {
                temporizador.stop();
                lblAccionActual.setText("⚡ Purga simulada del sistema completada.");
                lblEstadoAccion.setText("¡¡¡ ACCION EJECUTADA CON EXITO !!!");
                lblEstadoAccion.setForeground(new Color(20, 125, 55));
                revelarBromaYFinalizar("¡El contador llegó al 100%!");
            }
        });
        temporizador.start();
    }

    private void revelarBromaYFinalizar(String motivo) {
        if (temporizador != null && temporizador.isRunning()) {
            temporizador.stop();
        }

        String mensajeHtml = "<html><body style='font-family: Segoe UI, Arial, sans-serif; padding: 12px; text-align: center;'>"
                + "<h1 style='color: #c0392b; font-size: 26pt; margin-top: 0; margin-bottom: 8px;'>¡¡¡ Paga tu Licencia !!!</h1>"
                + "<h2 style='color: #196f3d; font-size: 18pt; margin-top: 0; margin-bottom: 12px;'>😅 ¡Tranquilo, es una broma entre programadores! 😉</h2>"
                + "<p style='font-size: 15pt; color: #17202a; line-height: 1.6; margin: 6px 0;'>"
                + "• Ningún dato ni tabla fue tocado.<br>"
                + "• Tu Base de Datos y tus respaldos están <b>100% intactos y seguros</b>."
                + "</p>"
                + "<p style='font-size: 13pt; color: #566573; font-style: italic; margin-top: 14px;'>"
                + "Iniciando sistema normalmente..."
                + "</p>"
                + "</body></html>";

        JOptionPane.showMessageDialog(this, mensajeHtml, "🎉 ¡¡¡ Paga tu Licencia !!!", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
