package aceptacion;

import Vista.DiaE2ERobot;
import Vista.Sistema;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E VIRTUAL GUARDADO PLATOS (Xvfb :99) — 100% UI con Robot
 *
 * Valida de forma real por interfaz el comportamiento inteligente de formularios:
 * 1. Formulario de Platos inicia en modo 'Guardar'.
 * 2. En creación de plato nuevo, pulsar Enter en el precio NO guarda automáticamente.
 * 3. Al pulsar 'Guardar', se crea el plato y aparece en la tabla del catálogo.
 * 4. Al seleccionar el plato de la tabla, los botones cambian dinámicamente a 'Guardar Cambios'.
 * 5. Modificar el precio a 29.50 y pulsar 'Guardar Cambios' actualiza el precio en pantalla.
 * 6. Modificar el precio a 35.00 y presionar Enter en plato existente SÍ guarda.
 * 7. El botón 'Limpiar Campos' resetea el formulario y regresa a modo 'Guardar'.
 * 8. Botón 'Limpiar Campos' en Salas y Usuarios funciona reactivamente.
 */
public class E2EGuardadoPlatosVirtual {

    private static int fallos = 0;

    private static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.err.println("E2E requiere -De2e.permitir=SI");
            System.exit(2);
        }

        Path dirE2E = Path.of(System.getenv().getOrDefault("E2E_WORKDIR", System.getProperty("java.io.tmpdir")));
        System.setProperty("user.home", dirE2E.toString());
        System.setProperty("modo.automatizado", "true");

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        DiaE2ERobot robot = new DiaE2ERobot();

        Throwable fallo = null;
        String platoNombre = "Plato E2E Test " + System.currentTimeMillis();

        try {
            System.out.println("== [Guardado Platos E2E] Paso 1: Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            robot.edt(() -> {
                login.setLocationRelativeTo(null);
                return null;
            });
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                return null;
            });
            Component bPlatos = (Component) robot.campo(sistema, "btnPlatos");
            robot.esperar(() -> sistema.isShowing() && bPlatos.isShowing(),
                    "Ventana Sistema autenticada");

            System.out.println("== [Guardado Platos E2E] Paso 2: Navegar a pestaña Platos ==");
            robot.clic((Component) robot.campo(sistema, "btnPlatos"));
            JTabbedPane tabs = sistema.getJTabbedPane1();
            robot.esperar(() -> tabs.getSelectedIndex() == 8,
                    "Pestaña Platos seleccionada");

            JTextField txtId = (JTextField) robot.campo(sistema, "txtIdPlato");
            JTextField txtNombre = (JTextField) robot.campo(sistema, "txtNombrePlato");
            JTextField txtPrecio = (JTextField) robot.campo(sistema, "txtPrecioPlato");
            JButton btnGuardar = (JButton) robot.campo(sistema, "btnGuardarPlato");
            JButton btnEditar = (JButton) robot.campo(sistema, "btnEditarPlato");
            JButton btnNuevo = (JButton) robot.campo(sistema, "btnNuevoPlato");
            JTextField txtBuscarAdmin = (JTextField) robot.campo(sistema, "txtBuscarAdminPlatos");
            JTable tablePlatos = (JTable) robot.campo(sistema, "TablePlatos");

            // Limpiar si hubiese residuos
            robot.clic(btnNuevo);
            check("".equals(txtId.getText().trim()), "Formulario inicia con txtIdPlato vacío");
            check("Guardar".equals(btnGuardar.getText()), "Botón izquierdo inicia como 'Guardar'");
            check("Editar".equals(btnEditar.getText()), "Botón derecho inicia como 'Editar'");
            check("Limpiar Campos".equals(btnNuevo.getText()), "Botón de limpieza dice 'Limpiar Campos'");

            System.out.println("== [Guardado Platos E2E] Paso 3: Enter en modo creación NO registra ==");
            robot.reemplazarTexto(txtNombre, platoNombre);
            robot.reemplazarTexto(txtPrecio, "25.00");
            robot.tecla(KeyEvent.VK_ENTER);
            Thread.sleep(300);

            // Verificar que no se guardó prematuramente
            check("".equals(txtId.getText().trim()), "Plato NO se registró prematuramente con Enter en creación");

            System.out.println("== [Guardado Platos E2E] Paso 4: Clic en Guardar registra el plato ==");
            robot.clic(btnGuardar);
            Thread.sleep(600);

            // Buscar en la tabla por la UI
            robot.reemplazarTexto(txtBuscarAdmin, platoNombre);
            robot.esperar(() -> {
                for (int i = 0; i < tablePlatos.getRowCount(); i++) {
                    Object val = tablePlatos.getModel().getValueAt(tablePlatos.convertRowIndexToModel(i), 1);
                    if (val != null && val.toString().equals(platoNombre)) return true;
                }
                return false;
            }, "El nuevo plato aparece en la tabla TablePlatos");
            check(true, "Plato registrado exitosamente mediante botón 'Guardar'");

            check("".equals(txtId.getText().trim()), "Tras registrar, el formulario se autolimpia");
            check("Guardar".equals(btnGuardar.getText()), "Botón regresa a 'Guardar'");

            System.out.println("== [Guardado Platos E2E] Paso 5: Clic en la fila del plato activa 'Guardar Cambios' ==");
            int filaCreada = robot.edt(() -> {
                for (int i = 0; i < tablePlatos.getRowCount(); i++) {
                    Object val = tablePlatos.getModel().getValueAt(tablePlatos.convertRowIndexToModel(i), 1);
                    if (val != null && val.toString().equals(platoNombre)) return i;
                }
                return -1;
            });
            robot.clicCelda(tablePlatos, filaCreada, 1);
            robot.esperar(() -> !txtId.getText().trim().isEmpty(), "Ficha carga los datos del plato seleccionado");
            check("Guardar Cambios".equals(btnGuardar.getText()), "Botón izquierdo cambia a 'Guardar Cambios'");
            check("Guardar Cambios".equals(btnEditar.getText()), "Botón derecho cambia a 'Guardar Cambios'");

            System.out.println("== [Guardado Platos E2E] Paso 6: Modificar precio a 29.50 y pulsar botón izquierdo ==");
            robot.reemplazarTexto(txtPrecio, "29.50");
            robot.clic(btnGuardar);
            Thread.sleep(600);

            robot.reemplazarTexto(txtBuscarAdmin, platoNombre);
            robot.esperar(() -> {
                for (int i = 0; i < tablePlatos.getRowCount(); i++) {
                    Object p = tablePlatos.getModel().getValueAt(tablePlatos.convertRowIndexToModel(i), 2);
                    if (p != null && p.toString().contains("29.50")) return true;
                }
                return false;
            }, "Tabla de platos refleja el precio actualizado a 29.50");
            check(true, "Precio actualizado a 29.50 con botón 'Guardar Cambios'");

            System.out.println("== [Guardado Platos E2E] Paso 7: Modificar precio a 35.00 con Enter en plato existente ==");
            int fila2 = robot.edt(() -> {
                for (int i = 0; i < tablePlatos.getRowCount(); i++) {
                    Object val = tablePlatos.getModel().getValueAt(tablePlatos.convertRowIndexToModel(i), 1);
                    if (val != null && val.toString().equals(platoNombre)) return i;
                }
                return -1;
            });
            robot.clicCelda(tablePlatos, fila2, 1);
            robot.esperar(() -> !txtId.getText().trim().isEmpty(), "Plato cargado");
            robot.reemplazarTexto(txtPrecio, "35.00");
            robot.tecla(KeyEvent.VK_ENTER);
            Thread.sleep(600);

            robot.reemplazarTexto(txtBuscarAdmin, platoNombre);
            robot.esperar(() -> {
                for (int i = 0; i < tablePlatos.getRowCount(); i++) {
                    Object p = tablePlatos.getModel().getValueAt(tablePlatos.convertRowIndexToModel(i), 2);
                    if (p != null && p.toString().contains("35.00")) return true;
                }
                return false;
            }, "Tabla de platos refleja el precio actualizado a 35.00 con Enter");
            check(true, "Precio actualizado a 35.00 con Enter en plato existente");

            System.out.println("== [Guardado Platos E2E] Paso 8: Probar Limpiar Campos en Platos, Salas y Usuarios ==");
            robot.reemplazarTexto(txtNombre, "Borrador Incompleto");
            robot.reemplazarTexto(txtPrecio, "99.99");
            robot.clic(btnNuevo);
            check("".equals(txtNombre.getText().trim()), "txtNombrePlato vacío tras limpiar");
            check("".equals(txtPrecio.getText().trim()), "txtPrecioPlato vacío tras limpiar");
            check("Guardar".equals(btnGuardar.getText()), "Botón reseteado a 'Guardar'");

            // Limpiar en Salas
            robot.clic((Component) robot.campo(sistema, "btnSala"));
            robot.esperar(() -> tabs.getSelectedIndex() == 1, "Pestaña Salas");
            JTextField txtNombreSala = (JTextField) robot.campo(sistema, "txtNombreSala");
            JTextField txtMesas = (JTextField) robot.campo(sistema, "txtMesas");
            JButton btnNuevoSala = (JButton) robot.campo(sistema, "btnNuevoSala");
            robot.reemplazarTexto(txtNombreSala, "Sala Test Limpieza");
            robot.reemplazarTexto(txtMesas, "12");
            robot.clic(btnNuevoSala);
            check("".equals(txtNombreSala.getText().trim()), "txtNombreSala limpio tras pulsar Limpiar");
            check("".equals(txtMesas.getText().trim()), "txtMesas limpio tras pulsar Limpiar");

            // Limpiar en Usuarios
            robot.clic((Component) robot.campo(sistema, "btnUsuarios"));
            robot.esperar(() -> tabs.getSelectedIndex() == 7, "Pestaña Usuarios");
            JTextField txtNombreU = (JTextField) robot.campo(sistema, "txtNombre");
            JTextField txtCorreoU = (JTextField) robot.campo(sistema, "txtCorreo");
            JPasswordField txtPassU = (JPasswordField) robot.campo(sistema, "txtPass");
            JButton btnLimpiarU = (JButton) robot.campo(sistema, "btnLimpiarUsuario");
            robot.reemplazarTexto(txtNombreU, "Usuario Temp");
            robot.reemplazarTexto(txtCorreoU, "temp@correo.com");
            robot.reemplazarTexto(txtPassU, "clave123");
            robot.clic(btnLimpiarU);
            check("".equals(txtNombreU.getText().trim()), "txtNombre usuario limpio tras pulsar Limpiar");
            check("".equals(txtCorreoU.getText().trim()), "txtCorreo usuario limpio tras pulsar Limpiar");
            check(txtPassU.getPassword().length == 0, "txtPass usuario limpio tras pulsar Limpiar");

        } catch (Throwable err) {
            fallo = err;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        w.dispose();
                    }
                });
            } catch (Throwable e) {
                if (fallo == null) fallo = e;
            }
        }

        if (fallo != null) {
            System.err.println("E2E_GUARDADO_PLATOS_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        if (fallos == 0) {
            System.out.println("E2E_GUARDADO_PLATOS_OK");
            System.exit(0);
        } else {
            System.err.println("E2E_GUARDADO_PLATOS_FALLO con " + fallos + " errores");
            System.exit(1);
        }
    }
}
