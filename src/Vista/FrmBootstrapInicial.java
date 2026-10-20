package Vista;

import Modelo.ErrorAplicacionException;
import Servicio.ServicioBootstrapInicial;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Diálogo modal de bienvenida y bootstrap inicial para crear el Administrador
 * principal de Restaurante 2026 en instalaciones nuevas.
 */
public class FrmBootstrapInicial extends JDialog {

    private final ServicioBootstrapInicial servicio;
    private final JTextField txtUsuario;
    private final JPasswordField txtPass;
    private final JPasswordField txtPassConfirm;
    private boolean completado = false;

    public FrmBootstrapInicial(Frame parent, ServicioBootstrapInicial servicio) {
        super(parent, "Configuración Inicial del Administrador — Restaurante 2026", true);
        this.servicio = servicio;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmarSalida();
            }
        });

        JPanel panelPrincipal = new JPanel(new BorderLayout(15, 15));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        panelPrincipal.setBackground(new Color(245, 247, 250));

        // Encabezado
        JPanel panelEncabezado = new JPanel(new BorderLayout(5, 5));
        panelEncabezado.setOpaque(false);
        JLabel lblTitulo = new JLabel("Bienvenido a Restaurante 2026");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitulo.setForeground(new Color(0, 51, 102));

        JLabel lblSubtitulo = new JLabel(
                "<html>Para inicializar el sistema de forma segura, configure la cuenta del <b>Administrador principal</b>.<br>Esta cuenta tendrá acceso total para configurar salas, platos, mesoneros y caja.</html>");
        lblSubtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitulo.setForeground(new Color(70, 70, 70));

        panelEncabezado.add(lblTitulo, BorderLayout.NORTH);
        panelEncabezado.add(lblSubtitulo, BorderLayout.CENTER);
        panelPrincipal.add(panelEncabezado, BorderLayout.NORTH);

        // Formulario
        JPanel panelForm = new JPanel(new GridBagLayout());
        panelForm.setOpaque(false);
        panelForm.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Fila 0: Usuario
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblUsr = new JLabel("Usuario (mín. 4 alfanuméricos):");
        lblUsr.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panelForm.add(lblUsr, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        txtUsuario = new JTextField();
        txtUsuario.setPreferredSize(new Dimension(240, 28));
        panelForm.add(txtUsuario, gbc);

        // Fila 1: Contraseña
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        JLabel lblPass = new JLabel("Contraseña (mín. 8 car.):");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panelForm.add(lblPass, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        txtPass = new JPasswordField();
        txtPass.setPreferredSize(new Dimension(240, 28));
        panelForm.add(txtPass, gbc);

        // Fila 2: Confirmación
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        JLabel lblConf = new JLabel("Confirmar Contraseña:");
        lblConf.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panelForm.add(lblConf, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7;
        txtPassConfirm = new JPasswordField();
        txtPassConfirm.setPreferredSize(new Dimension(240, 28));
        txtPassConfirm.addActionListener(e -> guardar());
        panelForm.add(txtPassConfirm, gbc);

        panelPrincipal.add(panelForm, BorderLayout.CENTER);

        // Botonera
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setOpaque(false);

        JButton btnSalir = new JButton("Salir");
        btnSalir.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnSalir.addActionListener(e -> confirmarSalida());

        JButton btnGuardar = new JButton("Crear Administrador e Iniciar");
        btnGuardar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnGuardar.setBackground(new Color(0, 102, 102));
        btnGuardar.setForeground(Color.WHITE);
        btnGuardar.addActionListener(e -> guardar());

        panelBotones.add(btnSalir);
        panelBotones.add(btnGuardar);
        panelPrincipal.add(panelBotones, BorderLayout.SOUTH);

        setContentPane(panelPrincipal);
        pack();
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(parent);
    }

    private void guardar() {
        String usuario = txtUsuario.getText() != null ? txtUsuario.getText().trim() : "";
        char[] pass = txtPass.getPassword();
        char[] passConf = txtPassConfirm.getPassword();

        if (usuario.isEmpty() || pass == null || pass.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Todos los campos son obligatorios para inicializar el Administrador.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (usuario.length() < 4 || usuario.length() > 60 || !usuario.matches("^[A-Za-z0-9]+$")) {
            JOptionPane.showMessageDialog(this,
                    "El usuario debe tener entre 4 y 60 caracteres y contener solo letras y números (sin espacios ni símbolos).",
                    "Usuario inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (pass.length < 8) {
            JOptionPane.showMessageDialog(this,
                    "La contraseña del Administrador debe tener al menos 8 caracteres.",
                    "Contraseña muy corta", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!Arrays.equals(pass, passConf)) {
            JOptionPane.showMessageDialog(this,
                    "La confirmación de la contraseña no coincide. Verifíquela e intente de nuevo.",
                    "Error de coincidencia", JOptionPane.WARNING_MESSAGE);
            Arrays.fill(passConf, '\0');
            return;
        }
        Arrays.fill(passConf, '\0');

        try {
            servicio.completarBootstrap(usuario, pass);
            JOptionPane.showMessageDialog(this,
                    "¡Cuenta de Administrador configurada exitosamente!\nAhora puede iniciar sesión con su usuario y contraseña.",
                    "Configuración Completada", JOptionPane.INFORMATION_MESSAGE);
            completado = true;
            dispose();
        } catch (ErrorAplicacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación de Bootstrap", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Ocurrió un error al guardar el Administrador inicial: " + ex.getMessage(),
                    "Error de Inicialización", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmarSalida() {
        int resp = JOptionPane.showConfirmDialog(this,
                "Si cancela la configuración inicial, no podrá acceder al sistema.\n¿Está seguro de que desea salir?",
                "Cancelar Configuración Inicial", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (resp == JOptionPane.YES_OPTION) {
            completado = false;
            dispose();
        }
    }

    public static boolean mostrarDialogo(Frame parent, ServicioBootstrapInicial servicio) {
        FrmBootstrapInicial dlg = new FrmBootstrapInicial(parent, servicio);
        dlg.setVisible(true);
        return dlg.completado;
    }
}
