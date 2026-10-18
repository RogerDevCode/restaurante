package Vista;

import java.awt.Component;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E Virtual de CRUD de Usuarios (Xvfb :99) operado 100% mediante UI con Robot.
 *
 * Flujo:
 * 1. Login interactivo en FrmLogin ("admin" / "admin").
 * 2. Clic en botón lateral "Usuarios".
 * 3. Verificación de carga de la pestaña Usuarios y su tabla.
 * 4. Validación de campos obligatorios en formulario vacío.
 * 5. Creación de un usuario por la UI (nombre, correo único, contraseña, rol Asistente).
 * 6. Verificación de que el nuevo usuario aparece en la tabla de usuarios.
 * 7. Selección del usuario en la tabla mediante clic real -> Botones de acción se activan.
 * 8. Cambio de contraseña interactivo con diálogo modal.
 * 9. Desactivación con confirmación modal -> Estado pasa a "Inactivo".
 * 10. Reactivación con confirmación modal -> Estado regresa a "Activo".
 * 11. Eliminación definitiva por la interfaz con confirmación modal -> Fila desaparece de la tabla.
 *
 * Cumple estrictamente con las reglas de AGENTS.md:
 * - Cero llamadas JDBC directas ni DELETE FROM usuarios.
 * - Cero repositorios en memoria (SalasMemoria, PedidosMemoria, etc.).
 * - Cero reflexión para invocar métodos de negocio.
 * - Toda la interacción y verificación ocurre a través de Swing con Robot.
 */
public class UsuariosCrudE2E {

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.err.println("E2E requiere autorización: relanza con -De2e.permitir=SI");
            System.exit(2);
        }

        Path dirE2E = Path.of(System.getenv().getOrDefault("E2E_WORKDIR", System.getProperty("java.io.tmpdir")));
        System.setProperty("user.home", dirE2E.toString());
        System.setProperty("modo.automatizado", "true");

        Throwable fallo = null;
        String correoTest = "e2e_" + System.currentTimeMillis() + "@test.local";
        String nombreTest = "Usuario E2E Test";

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            DiaE2ERobot robot = new DiaE2ERobot();

            System.out.println("== [Usuarios E2E] Paso 1: Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            robot.edt(() -> {
                login.setLocationRelativeTo(null);
                return null;
            });
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            System.out.println("== [Usuarios E2E] Paso 2: Esperar ventana principal Sistema ==");
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                return null;
            });
            Component btnUsuarios = (Component) robot.campo(sistema, "btnUsuarios");
            robot.esperar(() -> sistema.isShowing() && btnUsuarios.isShowing(),
                    "Ventana Sistema autenticada y botón Usuarios visible");

            System.out.println("== [Usuarios E2E] Paso 3: Navegar a pestaña Usuarios ==");
            robot.clic(btnUsuarios);
            JTabbedPane tabs = sistema.getJTabbedPane1();
            robot.esperar(() -> "Usuarios".equals(tabs.getTitleAt(tabs.getSelectedIndex())),
                    "Pestaña Usuarios seleccionada");

            JTable tableUsuarios = (JTable) robot.campo(sistema, "TableUsuarios");
            robot.esperar(() -> tableUsuarios.getRowCount() >= 1,
                    "Tabla de usuarios contiene al menos el usuario administrador");
            int filasIniciales = robot.edt(tableUsuarios::getRowCount);

            JTextField txtNombre = (JTextField) robot.campo(sistema, "txtNombre");
            JTextField txtCorreo = (JTextField) robot.campo(sistema, "txtCorreo");
            JPasswordField txtPass = (JPasswordField) robot.campo(sistema, "txtPass");
            @SuppressWarnings("unchecked")
            JComboBox<String> cbxRol = (JComboBox<String>) robot.campo(sistema, "cbxRol");
            JButton btnGuardar = (JButton) robot.campo(sistema, "btnIniciar");
            JButton btnLimpiar = (JButton) robot.campo(sistema, "btnLimpiarUsuario");

            System.out.println("== [Usuarios E2E] Paso 4: Validaciones de formulario vacío ==");
            robot.clic(btnLimpiar);
            robot.clic(btnGuardar);
            robot.esperar(() -> {
                String tip = txtNombre.getToolTipText();
                return tip != null && tip.toLowerCase().contains("obligatorio");
            }, "Formulario vacío señala campo Nombre como obligatorio");
            if (robot.edt(tableUsuarios::getRowCount) != filasIniciales) {
                throw new AssertionError("Intento de guardar formulario vacío alteró la tabla de usuarios");
            }
            System.out.println("OK   Validación de campos vacíos verificada");

            System.out.println("== [Usuarios E2E] Paso 5: Registrar nuevo usuario '" + correoTest + "' ==");
            robot.reemplazarTexto(txtNombre, nombreTest);
            robot.reemplazarTexto(txtCorreo, correoTest);
            robot.reemplazarTexto(txtPass, "Clave1234");
            robot.seleccionarCombo(cbxRol, "Asistente");
            robot.clic(btnGuardar);

            robot.esperar(() -> {
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) return true;
                }
                return false;
            }, "Usuario creado aparece en TableUsuarios");
            System.out.println("OK   Usuario registrado y visible en la tabla");

            System.out.println("== [Usuarios E2E] Paso 6: Seleccionar usuario en la tabla ==");
            int filaUsuario = robot.edt(() -> {
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) return i;
                }
                return -1;
            });
            robot.clicCelda(tableUsuarios, filaUsuario, 1);

            JButton btnCambiarClave = (JButton) robot.campo(sistema, "btnCambiarClaveUsuario");
            JButton btnEstado = (JButton) robot.campo(sistema, "btnEstadoUsuario");
            JButton btnEliminar = (JButton) robot.campo(sistema, "btnEliminarUsuario");

            robot.esperar(() -> btnCambiarClave.isEnabled() && btnEstado.isEnabled() && btnEliminar.isEnabled(),
                    "Botones de acción se habilitan al seleccionar el usuario");
            System.out.println("OK   Botones de acción habilitados para el usuario");

            System.out.println("== [Usuarios E2E] Paso 7: Cambiar contraseña con diálogo modal ==");
            Thread hiloClave = new Thread(() -> {
                try {
                    robot.clic(btnCambiarClave);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            hiloClave.start();
            robot.llenarDialogoPassword("Cambiar contraseña de usuario", "NuevaClave99", "NuevaClave99");
            hiloClave.join(8000);
            System.out.println("OK   Contraseña cambiada exitosamente");

            System.out.println("== [Usuarios E2E] Paso 8: Desactivar usuario con confirmación ==");
            robot.esperar(() -> "Desactivar".equals(btnEstado.getText()), "Botón de estado dice 'Desactivar'");
            Thread hiloDesactivar = new Thread(() -> {
                try {
                    robot.clic(btnEstado);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            hiloDesactivar.start();
            robot.confirmarDialogo("Confirmar desactivar");
            hiloDesactivar.join(8000);

            robot.esperar(() -> {
                int f = -1;
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) { f = i; break; }
                }
                if (f < 0) return false;
                Object est = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(f), 4);
                return "Inactivo".equalsIgnoreCase(String.valueOf(est));
            }, "Estado de usuario pasa a 'Inactivo'");
            System.out.println("OK   Usuario desactivado con éxito");

            System.out.println("== [Usuarios E2E] Paso 9: Reactivar usuario con confirmación ==");
            // Seleccionar nuevamente la fila para refrescar los botones si fuera necesario
            int filaInactivo = robot.edt(() -> {
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) return i;
                }
                return -1;
            });
            robot.clicCelda(tableUsuarios, filaInactivo, 1);
            robot.esperar(() -> "Reactivar".equals(btnEstado.getText()), "Botón de estado dice 'Reactivar'");

            Thread hiloReactivar = new Thread(() -> {
                try {
                    robot.clic(btnEstado);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            hiloReactivar.start();
            robot.confirmarDialogo("Confirmar reactivar");
            hiloReactivar.join(8000);

            robot.esperar(() -> {
                int f = -1;
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) { f = i; break; }
                }
                if (f < 0) return false;
                Object est = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(f), 4);
                return "Activo".equalsIgnoreCase(String.valueOf(est));
            }, "Estado de usuario regresa a 'Activo'");
            System.out.println("OK   Usuario reactivado con éxito");

            System.out.println("== [Usuarios E2E] Paso 10: Eliminar usuario por UI con confirmación ==");
            int filaActivo = robot.edt(() -> {
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) return i;
                }
                return -1;
            });
            robot.clicCelda(tableUsuarios, filaActivo, 1);

            Thread hiloEliminar = new Thread(() -> {
                try {
                    robot.clic(btnEliminar);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            hiloEliminar.start();
            robot.confirmarDialogo("Confirmar eliminación de usuario");
            hiloEliminar.join(8000);

            robot.esperar(() -> {
                for (int i = 0; i < tableUsuarios.getRowCount(); i++) {
                    Object v = tableUsuarios.getModel().getValueAt(tableUsuarios.convertRowIndexToModel(i), 2);
                    if (correoTest.equals(String.valueOf(v))) return false;
                }
                return true;
            }, "Usuario eliminado desaparece de TableUsuarios");
            System.out.println("OK   Usuario eliminado por la interfaz limpiamente");

        } catch (Throwable error) {
            fallo = error;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        w.dispose();
                    }
                });
            } catch (Throwable errorLimpieza) {
                if (fallo == null) fallo = errorLimpieza;
                else fallo.addSuppressed(errorLimpieza);
            }
        }

        if (fallo != null) {
            System.err.println("E2E_USUARIOS_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("E2E_USUARIOS_OK");
        System.exit(0);
    }
}