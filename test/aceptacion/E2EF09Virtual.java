package aceptacion;

import Vista.DiaE2ERobot;
import Vista.Sistema;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E VIRTUAL F-09 (Xvfb :99) — 100% UI con Robot
 *
 * Verifica el comportamiento de txtCantidadManual en la comanda:
 * 1. Ingresar 0 con teclado virtual -> rechazo y texto revertido al valor previo (1).
 * 2. Tipear 4 en fila 0 y hacer clic en fila 1 -> fila 0 se actualiza a 4 y fila 1 se mantiene intacta en 1.
 *
 * Cumple estrictamente con AGENTS.md:
 * - Cero manipulación de modelos por código (no addRow).
 * - Cero invocación por reflexión de aplicarCantidadManual.
 * - Toda la selección de platos y tipeo ocurre mediante Robot e interfaz Swing observable.
 */
public class E2EF09Virtual {

    static int fallos = 0;

    static void check(boolean cond, String msg) {
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
        try {
            System.out.println("== [F-09 E2E] Paso 1: Inicio e inicio de sesión interactivo ==");
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
            robot.esperar(() -> sistema.isShowing() && sistema.getBtnAtencionDirecta().isShowing(),
                    "Ventana Sistema disponible");

            System.out.println("== [F-09 E2E] Paso 2: Navegar a mesas y seleccionar Mesa 1 ==");
            javax.swing.JComboBox<?> modoGlobal = sistema.getCbModoAtencionGlobal();
            if (modoGlobal != null) {
                robot.seleccionarComboPorIndice(modoGlobal, 0);
                robot.esperar(() -> "OPCION_B".equals(sistema.getModoAtencionActual()),
                        "modo de atención Opción B activo");
            }
            robot.clic(sistema.getBtnAtencionDirecta());
            JTabbedPane tabs = sistema.getJTabbedPane1();
            robot.esperar(() -> tabs.indexOfTab("Mesas") >= 0 && tabs.getSelectedIndex() == tabs.indexOfTab("Mesas"),
                    "Pestaña Mesas seleccionada");

            JPanel panelMesas = (JPanel) robot.campo(sistema, "PanelMesas");
            Predicate<JButton> esMesa1 = b -> {
                String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                String txt = b.getText() == null ? "" : b.getText().toUpperCase();
                return tip.startsWith("Mesa 1 (") || txt.contains("MESA 1");
            };
            robot.esperar(() -> buscarBoton(panelMesas, esMesa1) != null, "Botón de Mesa 1 cargado");
            JButton botonMesa1 = robot.edt(() -> buscarBoton(panelMesas, esMesa1));

            // Si estuviera ocupada, liberamos por UI
            boolean ocupada = robot.edt(() -> {
                String tip = botonMesa1.getToolTipText() != null ? botonMesa1.getToolTipText() : "";
                return tip.contains("Ocupada");
            });
            if (ocupada) {
                robot.clic(botonMesa1);
                robot.esperar(() -> tabs.getSelectedIndex() == 4, "Abre Caja/Cuenta");
                robot.clic((Component) robot.campo(sistema, "btnAnularFuga"));
                robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Limpieza previa F-09");
                robot.confirmarDialogo("Confirmar No-Pago (Fuga)");
                robot.clic(sistema.getBtnAtencionDirecta());
                robot.esperar(() -> {
                    JButton b = buscarBoton(panelMesas, esMesa1);
                    String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                    return tip.contains("Disponible");
                }, "Mesa 1 liberada");
            }

            // Abrir Mesa 1
            robot.clic(robot.edt(() -> buscarBoton(panelMesas, esMesa1)));
            robot.esperar(() -> tabs.getSelectedIndex() == 3, "Toma de pedidos abierta");

            JTextField buscar = (JTextField) robot.campo(sistema, "txtBuscarPlato");
            JTable menu = (JTable) robot.campo(sistema, "tblTemPlatos");
            JTable carro = (JTable) robot.campo(sistema, "tableMenu");
            JTextField txtCant = (JTextField) robot.campo(sistema, "txtCantidadManual");

            System.out.println("== [F-09 E2E] Paso 3: Agregar 2 platos reales al carrito por la UI ==");
            // Plato 1: Pollo
            robot.reemplazarTexto(buscar, "pollo");
            robot.esperar(() -> {
                for (int i = 0; i < menu.getRowCount(); i++) {
                    Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return true;
                }
                return false;
            }, "Platos con POLLO cargados en el menú");
            int f1 = robot.edt(() -> {
                for (int i = 0; i < menu.getRowCount(); i++) {
                    Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return i;
                }
                return -1;
            });
            robot.clicCelda(menu, f1, 0);
            robot.esperar(() -> carro.getRowCount() >= 1, "Carrito tiene al menos 1 fila");

            // Plato 2: Sopa
            robot.reemplazarTexto(buscar, "sopa");
            robot.esperar(() -> {
                for (int i = 0; i < menu.getRowCount(); i++) {
                    Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("SOPA")) return true;
                }
                return false;
            }, "Platos con SOPA cargados en el menú");
            int f2 = robot.edt(() -> {
                for (int i = 0; i < menu.getRowCount(); i++) {
                    Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("SOPA")) return i;
                }
                return -1;
            });
            robot.clicCelda(menu, f2, 0);
            robot.esperar(() -> carro.getRowCount() >= 2, "Carrito contiene 2 filas reales");
            check(carro.getRowCount() >= 2, "Carrito contiene 2 platos seleccionados desde el catálogo");

            // Seleccionar fila 0 del carrito
            robot.clicCelda(carro, 0, 1);
            robot.esperar(() -> "1".equals(txtCant.getText()), "txtCantidadManual inicial es 1");

            System.out.println("== [F-09 E2E] Paso 4: Tipear 0 -> rechazo y reversión a 1 ==");
            robot.reemplazarTexto(txtCant, "0");
            robot.tecla(KeyEvent.VK_ENTER);
            robot.esperar(() -> "1".equals(txtCant.getText()), "txtCantidadManual se revierte a '1' ante '0'");

            int cantFila0 = robot.edt(() -> Integer.parseInt(carro.getModel().getValueAt(0, 2).toString()));
            check(cantFila0 == 1, "Fila 0 conserva cantidad 1");

            System.out.println("== [F-09 E2E] Paso 5: Tipear 4 en fila 0 y hacer clic en fila 1 ==");
            robot.clicCelda(carro, 0, 1);
            robot.reemplazarTexto(txtCant, "4");

            // Clic real con mouse virtual en fila 1
            robot.clicCelda(carro, 1, 1);

            robot.esperar(() -> {
                try {
                    int c0 = Integer.parseInt(carro.getModel().getValueAt(0, 2).toString());
                    int c1 = Integer.parseInt(carro.getModel().getValueAt(1, 2).toString());
                    return c0 == 4 && c1 == 1;
                } catch (Exception ex) {
                    return false;
                }
            }, "Fila 0 actualizada a 4 y fila 1 se mantiene en 1 tras cambiar foco");

            int c0Final = robot.edt(() -> Integer.parseInt(carro.getModel().getValueAt(0, 2).toString()));
            int c1Final = robot.edt(() -> Integer.parseInt(carro.getModel().getValueAt(1, 2).toString()));
            String cantFila1Text = txtCant.getText();

            check(c0Final == 4, "Fila 0 actualizada a 4");
            check(c1Final == 1, "Fila 1 se mantiene en 1");
            check("1".equals(cantFila1Text), "txtCantidadManual refleja '1' de la fila 1 seleccionada");

            System.out.println("== [F-09 E2E] Paso 6: Cancelar comanda sin guardar para dejar mesa limpia ==");
            robot.clic(sistema.getBtnAtencionDirecta());
            robot.esperar(() -> tabs.getSelectedIndex() == tabs.indexOfTab("Mesas"), "Regresa a Mesas sin alterar la BD");

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
            System.err.println("E2E_F09_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        if (fallos == 0) {
            System.out.println("E2E_F09_OK");
            System.exit(0);
        } else {
            System.err.println("E2E_F09_FALLO con " + fallos + " errores");
            System.exit(1);
        }
    }

    private static JButton buscarBoton(Container raiz, Predicate<JButton> criterio) {
        for (Component c : raiz.getComponents()) {
            if (c instanceof JButton b && criterio.test(b)) {
                return b;
            }
            if (c instanceof Container cont) {
                JButton encontrado = buscarBoton(cont, criterio);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
}
