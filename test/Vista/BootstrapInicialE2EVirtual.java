package Vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Arranque de instalación limpia y bootstrap inicial, recorrido solo mediante UI. */
public final class BootstrapInicialE2EVirtual {
    private static final String PASSWORD = "E2EBootstrap2026X";
    private static final String EMAIL = "bootstrap-e2e@example.test";

    private BootstrapInicialE2EVirtual() {}

    public static void main(String[] args) throws Exception {
        DiaE2EFixture.exigirBdAislada();
        Path workdir = Path.of(System.getenv("E2E_WORKDIR"));
        System.setProperty("modo.automatizado", "true");
        System.setProperty("user.home", workdir.toString());
        System.setProperty("PDF_FACTURAS_DIR", workdir.resolve("facturas").toString());
        System.setProperty("restaurante.instancia.puerto", "0");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        DiaE2ERobot robot = new DiaE2ERobot();
        Throwable fallo = null;
        try {
            Thread.ofVirtual().name("e2e-arranque-restaurante").start(
                    () -> restaurante.Restaurante.main(new String[0]));
            FrmBootstrapInicial bootstrap = robot.esperarVentana(FrmBootstrapInicial.class);

            JTextField nombre = (JTextField) robot.campo(bootstrap, "txtNombre");
            JTextField correo = (JTextField) robot.campo(bootstrap, "txtCorreo");
            var password = (javax.swing.JPasswordField) robot.campo(bootstrap, "txtPass");
            var confirmar = (javax.swing.JPasswordField) robot.campo(bootstrap, "txtPassConfirm");
            JButton crear = robot.edt(() -> buscarBoton(bootstrap, "Crear Administrador e Iniciar"));
            exigir(crear != null, "botón visible de creación de Administrador disponible");

            // El primer clic confirma que los campos obligatorios se validan en pantalla.
            robot.clic(crear);
            robot.confirmarDialogo("Datos incompletos");

            String nuevoCorreo = EMAIL;
            robot.reemplazarTexto(nombre, "Administrador E2E");
            robot.reemplazarTexto(correo, nuevoCorreo);

            robot.reemplazarTexto(password, "corta7");
            robot.reemplazarTexto(confirmar, "corta7");
            robot.clic(crear);
            robot.confirmarDialogo("Contraseña muy corta");

            robot.reemplazarTexto(password, PASSWORD);
            robot.reemplazarTexto(confirmar, "DifferentBootstrap2026");
            robot.clic(crear);
            robot.confirmarDialogo("Error de coincidencia");

            robot.reemplazarTexto(confirmar, PASSWORD);
            robot.clic(crear);
            robot.confirmarDialogo("Configuración Completada");

            FrmLogin login = robot.esperarVentana(FrmLogin.class);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));
            robot.confirmarDialogo("Acceso denegado");

            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), nuevoCorreo);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), PASSWORD);
            robot.clic((Component) robot.campo(login, "btnIniciar"));
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setBounds(0, 0, 1366, 711);
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                return null;
            });
            Object etiquetaAdmin = robot.campo(sistema, "LabelVendedor");
            robot.esperar(() -> sistema.isShowing()
                            && "Administrador E2E".equals(texto(robot, etiquetaAdmin)),
                    "la cuenta creada inicia sesión como Administrador en el sistema");
            System.out.println("BOOTSTRAP_E2E_OK: instalación limpia, validaciones, demo bloqueada y login nuevo por UI");
        } catch (Throwable error) {
            fallo = error;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window window : Window.getWindows()) window.dispose();
                });
            } catch (Throwable cierre) {
                if (fallo == null) fallo = cierre;
                else fallo.addSuppressed(cierre);
            }
        }
        if (fallo != null) {
            System.err.println("BOOTSTRAP_E2E_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            throw new AssertionError("Falló el E2E de instalación limpia", fallo);
        }
    }

    private static JButton buscarBoton(Container contenedor, String texto) {
        for (Component componente : contenedor.getComponents()) {
            if (componente instanceof JButton boton && texto.equals(boton.getText())) return boton;
            if (componente instanceof Container hijo) {
                JButton boton = buscarBoton(hijo, texto);
                if (boton != null) return boton;
            }
        }
        return null;
    }

    private static String texto(DiaE2ERobot robot, Object componente) {
        try {
            return robot.texto((Component) componente);
        } catch (Exception error) {
            return "";
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
