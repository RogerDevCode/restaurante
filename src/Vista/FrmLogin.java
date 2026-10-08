
package Vista;

import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import Controlador.LoginControlador;
import java.awt.Image;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import java.util.Optional;
import java.util.function.Function;


public class FrmLogin extends javax.swing.JFrame {
    private static final Color VERDE = new Color(27, 62, 55);
    private static final Color VERDE_BOTON = new Color(35, 91, 76);
    private static final Color DORADO = new Color(218, 166, 91);
    private static final Color FONDO = new Color(250, 248, 243);
    private static final Color TEXTO = new Color(37, 46, 43);
    private static final Color SECUNDARIO = new Color(112, 120, 115);
    private Usuario usuarioAutenticado;
    private final LoginControlador controlador;
    private final Function<Usuario, Sistema> crearSistema;
    private Timer tiempo;
    int contador;
    int segundos = 30;
    public FrmLogin(LoginControlador controlador, Function<Usuario, Sistema> crearSistema) {
        if (controlador == null) {
            throw ErrorAplicacionException.validacion("El controlador de inicio de sesión es obligatorio.");
        }
        if (crearSistema == null) {
            throw ErrorAplicacionException.validacion("La fábrica del sistema es obligatoria.");
        }
        this.controlador = controlador;
        this.crearSistema = crearSistema;
        initComponents();
        construirInterfazLogin();
        this.setLocationRelativeTo(null);
        barra.setVisible(false);
        ImageIcon img = new ImageIcon(getClass().getResource("/Img/logo.png"));
        this.setIconImage(img.getImage());
    }

    private void construirInterfazLogin() {
        setTitle("Restaurante | Iniciar sesión");
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(FONDO);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(FONDO);
        raiz.setPreferredSize(new Dimension(980, 620));
        raiz.add(crearPanelBienvenida(), BorderLayout.WEST);
        raiz.add(crearPanelFormulario(), BorderLayout.CENTER);
        getContentPane().add(raiz, BorderLayout.CENTER);

        setMinimumSize(new Dimension(850, 560));
        setPreferredSize(new Dimension(980, 620));
        setResizable(true);
        getRootPane().setDefaultButton(btnIniciar);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel crearPanelBienvenida() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 13));
                g2.fillOval(getWidth() - 150, 34, 260, 260);
                g2.fillOval(getWidth() - 90, getHeight() - 210, 200, 200);
                g2.setColor(new Color(DORADO.getRed(), DORADO.getGreen(), DORADO.getBlue(), 45));
                g2.fillOval(-90, getHeight() - 175, 230, 230);
                g2.dispose();
            }
        };
        panel.setBackground(VERDE);
        panel.setPreferredSize(new Dimension(350, 600));
        panel.setBorder(BorderFactory.createEmptyBorder(54, 42, 42, 36));

        JPanel contenido = new JPanel();
        contenido.setOpaque(false);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));

        JLabel marca = new JLabel("R");
        marca.setFont(new Font("Serif", Font.BOLD, 30));
        marca.setForeground(VERDE);
        marca.setHorizontalAlignment(SwingConstants.CENTER);
        marca.setPreferredSize(new Dimension(58, 58));
        marca.setMinimumSize(new Dimension(58, 58));
        marca.setMaximumSize(new Dimension(58, 58));
        marca.setOpaque(true);
        marca.setBackground(DORADO);
        marca.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 100), 1));
        marca.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel eyebrow = new JLabel("GESTIÓN RESTAURANTE");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 12));
        eyebrow.setForeground(new Color(231, 215, 188));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titulo = new JLabel("<html>Todo listo<br>para servir.</html>");
        titulo.setFont(new Font("Serif", Font.BOLD, 37));
        titulo.setForeground(Color.WHITE);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descripcion = new JLabel("<html>Administra tus mesas, pedidos y<br>platos desde un solo lugar.</html>");
        descripcion.setFont(new Font("SansSerif", Font.PLAIN, 15));
        descripcion.setForeground(new Color(218, 228, 221));
        descripcion.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textoMarca = new JPanel();
        textoMarca.setOpaque(false);
        textoMarca.setLayout(new BoxLayout(textoMarca, BoxLayout.Y_AXIS));
        JLabel nombre = new JLabel("RESTAURANTE");
        nombre.setFont(new Font("SansSerif", Font.BOLD, 15));
        nombre.setForeground(Color.WHITE);
        JLabel sistema = new JLabel("Panel de administración");
        sistema.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sistema.setForeground(new Color(201, 218, 207));
        textoMarca.add(nombre);
        textoMarca.add(Box.createVerticalStrut(5));
        textoMarca.add(sistema);

        JPanel cabeceraMarca = new JPanel(new BorderLayout(14, 0));
        cabeceraMarca.setOpaque(false);
        cabeceraMarca.setAlignmentX(Component.LEFT_ALIGNMENT);
        cabeceraMarca.add(marca, BorderLayout.WEST);
        cabeceraMarca.add(textoMarca, BorderLayout.CENTER);

        contenido.add(cabeceraMarca);
        contenido.add(Box.createVerticalStrut(72));
        contenido.add(eyebrow);
        contenido.add(Box.createVerticalStrut(17));
        contenido.add(titulo);
        contenido.add(Box.createVerticalStrut(18));
        contenido.add(descripcion);
        panel.add(contenido, BorderLayout.NORTH);

        JLabel pie = new JLabel("Una experiencia simple, cada día.");
        pie.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pie.setForeground(new Color(201, 218, 207));
        pie.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(pie, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearPanelFormulario() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(FONDO);
        panel.setBorder(BorderFactory.createEmptyBorder(42, 58, 38, 58));

        JPanel formulario = new JPanel();
        formulario.setOpaque(false);
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.setMaximumSize(new Dimension(420, 470));

        JLabel titulo = new JLabel("Iniciar sesión");
        titulo.setFont(new Font("SansSerif", Font.BOLD, 30));
        titulo.setForeground(TEXTO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel ayuda = new JLabel("Ingresa tus datos para continuar");
        ayuda.setFont(new Font("SansSerif", Font.PLAIN, 14));
        ayuda.setForeground(SECUNDARIO);
        ayuda.setAlignmentX(Component.LEFT_ALIGNMENT);

        configurarEtiqueta(jLabel3, "Usuario / Correo");
        configurarEtiqueta(jLabel4, "Contraseña");
        configurarCampo(txtCorreo);
        configurarCampo(txtPass);
        txtCorreo.setText("admin");
        txtPass.setText("admin");

        btnIniciar.setText("Ingresar");
        btnIniciar.setFont(new Font("SansSerif", Font.BOLD, 15));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.setBackground(VERDE_BOTON);
        btnIniciar.setFocusPainted(false);
        btnIniciar.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        btnIniciar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnIniciar.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnIniciar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btnIniciar.setPreferredSize(new Dimension(390, 48));

        jButton1.setText("Salir");
        jButton1.setFont(new Font("SansSerif", Font.PLAIN, 13));
        jButton1.setForeground(SECUNDARIO);
        jButton1.setBackground(FONDO);
        jButton1.setFocusPainted(false);
        jButton1.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        jButton1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        jButton1.setAlignmentX(Component.LEFT_ALIGNMENT);

        barra.setForeground(VERDE_BOTON);
        barra.setBackground(new Color(231, 233, 227));
        barra.setBorderPainted(false);
        barra.setAlignmentX(Component.LEFT_ALIGNMENT);
        barra.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));

        formulario.add(titulo);
        formulario.add(Box.createVerticalStrut(9));
        formulario.add(ayuda);
        formulario.add(Box.createVerticalStrut(34));
        formulario.add(jLabel3);
        formulario.add(Box.createVerticalStrut(9));
        formulario.add(txtCorreo);
        formulario.add(Box.createVerticalStrut(22));
        formulario.add(jLabel4);
        formulario.add(Box.createVerticalStrut(9));
        formulario.add(txtPass);
        formulario.add(Box.createVerticalStrut(28));
        formulario.add(btnIniciar);
        formulario.add(Box.createVerticalStrut(15));
        formulario.add(barra);
        formulario.add(Box.createVerticalStrut(8));
        formulario.add(jButton1);

        JPanel centrado = new JPanel(new java.awt.GridBagLayout());
        centrado.setOpaque(false);
        centrado.add(formulario);
        panel.add(centrado, BorderLayout.CENTER);
        return panel;
    }

    private void configurarEtiqueta(JLabel etiqueta, String texto) {
        etiqueta.setText(texto);
        etiqueta.setFont(new Font("SansSerif", Font.BOLD, 13));
        etiqueta.setForeground(TEXTO);
        etiqueta.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void configurarCampo(javax.swing.JTextField campo) {
        campo.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        campo.setFont(new Font("SansSerif", Font.PLAIN, 15));
        campo.setForeground(TEXTO);
        campo.setBackground(Color.WHITE);
        campo.setCaretColor(VERDE_BOTON);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 221, 215)),
                BorderFactory.createEmptyBorder(10, 13, 10, 13)));
        campo.setAlignmentX(Component.LEFT_ALIGNMENT);
        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        campo.setPreferredSize(new Dimension(390, 46));
    }
    public class BarraProgreso implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent ae) {
            contador++;
            barra.setValue(contador);
            if (contador == 100) {
                tiempo.stop();
                if (barra.getValue() == 100) {
                    Sistema sis = crearSistema.apply(usuarioAutenticado);
                    sis.setVisible(true);
                    dispose();
                }
            }
        }
    }
    public void validar(){
        String correo = txtCorreo.getText().trim();
        char[] claveCapturada = txtPass.getPassword();
        String pass = new String(claveCapturada);
        java.util.Arrays.fill(claveCapturada, '\0');
        if (correo.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingresa el correo y la contraseña.",
                    "Datos requeridos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        cambiarEstadoFormulario(false);
        new AutenticacionSwingWorker(controlador, correo, pass,
                this::autenticacionCompletada, this::autenticacionFallida).execute();
    }

    private void autenticacionCompletada(Optional<Usuario> resultado) {
        if (!resultado.isPresent()) {
            cambiarEstadoFormulario(true);
            JOptionPane.showMessageDialog(this, "Correo o contraseña incorrectos.",
                    "Acceso denegado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        usuarioAutenticado = resultado.get();
        barra.setVisible(true);
        contador = -1;
        barra.setValue(0);
        barra.setStringPainted(true);
        tiempo = new Timer(segundos, new BarraProgreso());
        tiempo.start();
    }

    private void autenticacionFallida(Throwable error) {
        cambiarEstadoFormulario(true);
        if (error instanceof DataAccessException) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo acceder a la base de datos. Revisa la conexión e inténtalo nuevamente.",
                    "Error de conexión", JOptionPane.ERROR_MESSAGE);
        } else if (error instanceof ErrorAplicacionException appEx) {
            JOptionPane.showMessageDialog(this, appEx.getMessage(),
                    "No se pudo iniciar sesión", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No se pudo iniciar sesión. El detalle quedó registrado.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cambiarEstadoFormulario(boolean habilitado) {
        btnIniciar.setEnabled(habilitado);
        txtCorreo.setEnabled(habilitado);
        txtPass.setEnabled(habilitado);
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        txtCorreo = new javax.swing.JTextField();
        txtPass = new javax.swing.JPasswordField();
        btnIniciar = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        barra = new javax.swing.JProgressBar();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setUndecorated(false);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel2.setBackground(new java.awt.Color(153, 153, 153));
        jPanel2.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel3.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel3.setText("Correo Electrónico");
        jPanel2.add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 190, -1, -1));

        jLabel4.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel4.setText("Password");
        jPanel2.add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 280, -1, -1));

        txtCorreo.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCorreo.setBorder(null);
        txtCorreo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtCorreoActionPerformed(evt);
            }
        });
        jPanel2.add(txtCorreo, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 220, 250, 35));

        txtPass.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtPass.setBorder(null);
        jPanel2.add(txtPass, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 300, 250, 35));

        btnIniciar.setBackground(new java.awt.Color(0, 0, 0));
        btnIniciar.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        btnIniciar.setForeground(new java.awt.Color(255, 255, 255));
        btnIniciar.setText("Login");
        btnIniciar.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnIniciar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIniciarActionPerformed(evt);
            }
        });
        jPanel2.add(btnIniciar, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 360, 93, 35));

        jButton1.setBackground(new java.awt.Color(204, 0, 0));
        jButton1.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jButton1.setForeground(new java.awt.Color(255, 255, 255));
        jButton1.setText("Salir");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel2.add(jButton1, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 360, 93, 35));

        jPanel4.setBackground(new java.awt.Color(0, 0, 0));

        jLabel1.setFont(new java.awt.Font("Tw Cen MT", 3, 36)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/iniciar.png"))); // NOI18N
        jLabel1.setText("Iniciar Sesión");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 310, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
        );

        jPanel2.add(jPanel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 110));

        barra.setBackground(new java.awt.Color(255, 255, 255));
        barra.setForeground(new java.awt.Color(0, 110, 255));
        jPanel2.add(barra, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 130, 250, 30));

        getContentPane().add(jPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 430));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnIniciarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIniciarActionPerformed
      validar();
    }//GEN-LAST:event_btnIniciarActionPerformed

    private void txtCorreoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtCorreoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtCorreoActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // TODO add your handling code here:
        System.exit(0);
    }//GEN-LAST:event_jButton1ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JProgressBar barra;
    private javax.swing.JButton btnIniciar;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JTextField txtCorreo;
    private javax.swing.JPasswordField txtPass;
    // End of variables declaration//GEN-END:variables
}
