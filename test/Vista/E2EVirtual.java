package Vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E Canónico en pantalla virtual (Xvfb :99) operado 100% mediante UI con Robot.
 *
 * Flujo:
 * 1. Login interactivo en FrmLogin ("admin" / "admin").
 * 2. Navegación a Atención Directa (mapa de salas y mesas).
 * 3. Selección de Mesa 1 (libre) -> Apertura de Toma de Pedidos.
 * 4. Búsqueda de "pollo" y adición al carrito (1 unidad).
 * 5. Envío a cocina (btnGenerarPedido) -> Mesa 1 pasa a OCUPADA en el mapa.
 * 6. Reabrir Mesa 1 haciendo clic en el botón de mesa ocupada -> Abre Caja/Cuenta.
 * 7. Clic en "Modificar Pedido" -> Abre Toma de Pedidos con carrito precargado.
 * 8. Adición de 2 pollos más -> Carrito suma 3 pollos en total.
 * 9. Actualizar comanda (btnGenerarPedido) -> Comanda sincronizada.
 * 10. Reabrir Mesa 1 en Caja/Cuenta -> Verifica visualmente 3 pollos en la cuenta.
 * 11. Liberación de la mesa mediante la interfaz (btnAnularFuga: cancelación de cuenta).
 * 12. Verificación de que Mesa 1 regresa al estado LIBRE en el mapa.
 *
 * Cumple estrictamente con las reglas de AGENTS.md:
 * - Cero llamadas directas a DAOs, repositorios, servicios o controladores.
 * - Cero sentencias SQL directas (ni SELECT ni DELETE).
 * - Cero invocación de métodos privados por reflexión.
 * - Toda la interacción y verificación ocurre a través de componentes observables de Swing.
 */
public class E2EVirtual {

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.err.println("E2E requiere autorización: relanza con -De2e.permitir=SI");
            System.exit(2);
        }

        Path dirE2E = Path.of(System.getenv().getOrDefault("E2E_WORKDIR", System.getProperty("java.io.tmpdir")));
        System.setProperty("user.home", dirE2E.toString());
        System.setProperty("modo.automatizado", "true");
        System.setProperty("PDF_FACTURAS_DIR", dirE2E.resolve("facturas").toString());

        Throwable fallo = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            DiaE2ERobot robot = new DiaE2ERobot();

            System.out.println("== [E2E] Paso 1: Lanzamiento de la aplicación y Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            robot.edt(() -> {
                login.setLocationRelativeTo(null);
                return null;
            });
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"),
                    System.getenv().getOrDefault("E2E_LOGIN_USERNAME", "admin"));
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"),
                    System.getenv().getOrDefault("E2E_LOGIN_PASSWORD", "admin"));
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            System.out.println("== [E2E] Paso 2: Espera de la ventana principal de Sistema ==");
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                return null;
            });
            robot.esperar(() -> sistema.isShowing() && sistema.getBtnAtencionDirecta().isShowing(),
                    "Ventana Sistema autenticada y disponible");

            System.out.println("== [E2E] Paso 3: Navegación al mapa de mesas (Atención Directa) ==");
            javax.swing.JComboBox<?> modoGlobal = sistema.getCbModoAtencionGlobal();
            if (modoGlobal != null) {
                robot.seleccionarComboPorIndice(modoGlobal, 0);
                robot.esperar(() -> "OPCION_B".equals(sistema.getModoAtencionActual()),
                        "modo de atención Opción B activo");
            }
            robot.clic(sistema.getBtnAtencionDirecta());
            JTabbedPane tabs = sistema.getJTabbedPane1();
            robot.esperar(() -> tabs.indexOfTab("Mesas") >= 0 && tabs.getSelectedIndex() == tabs.indexOfTab("Mesas"),
                    "Pestaña Mesas seleccionada en atención directa");

            JPanel panelSalas = (JPanel) robot.campo(sistema, "panelTabsSalasDirecto");
            JButton btnSala = robot.edt(() -> buscarBoton(panelSalas,
                    b -> b.getText() != null && (b.getText().toUpperCase().contains("SALA PRINCIPAL") || b.getText().startsWith("Sala "))));
            if (btnSala == null) {
                btnSala = robot.edt(() -> buscarBoton(panelSalas, b -> b.getText() != null && !b.getText().isBlank()));
            }
            if (btnSala != null) {
                robot.clic(btnSala);
            }

            JPanel panelMesas = (JPanel) robot.campo(sistema, "PanelMesas");
            Predicate<JButton> esMesa1 = b -> {
                String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                String txt = b.getText() == null ? "" : b.getText().toUpperCase();
                return tip.startsWith("Mesa 1 (") || txt.contains("MESA 1");
            };
            robot.esperar(() -> buscarBoton(panelMesas, esMesa1) != null, "Botón de Mesa 1 visible en el mapa");
            JButton botonMesa1 = robot.edt(() -> buscarBoton(panelMesas, esMesa1));

            // Si Mesa 1 estaba ocupada por alguna prueba abortada previamente, la liberamos vía UI
            boolean estabaOcupada = robot.edt(() -> {
                String tip = botonMesa1.getToolTipText() != null ? botonMesa1.getToolTipText() : "";
                return tip.contains("Ocupada");
            });
            if (estabaOcupada) {
                System.out.println("[E2E Info] Mesa 1 estaba ocupada: liberando vía UI...");
                robot.clic(botonMesa1);
                robot.esperar(() -> tabs.getSelectedIndex() == 4, "Abre Caja/Cuenta para limpiar mesa previa");
                robot.clic((Component) robot.campo(sistema, "btnAnularFuga"));
                robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Limpieza inicial E2E");
                robot.confirmarDialogo("Confirmar No-Pago (Fuga)");
                robot.clic(sistema.getBtnAtencionDirecta());
                robot.esperar(() -> {
                    JButton b = buscarBoton(panelMesas, esMesa1);
                    String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                    return tip.contains("Disponible");
                }, "Mesa 1 quedó libre tras limpieza inicial");
            }
            System.out.println("OK   Mesa 1 libre y lista");

            System.out.println("== [E2E] Paso 4: Abrir Mesa 1 y tomar comanda (Ronda 1: 1 Pollo) ==");
            robot.clic(robot.edt(() -> buscarBoton(panelMesas, esMesa1)));
            robot.esperar(() -> tabs.getSelectedIndex() == 3, "Mesa 1 abre la pestaña Toma de Pedidos");

            JTextField buscar = (JTextField) robot.campo(sistema, "txtBuscarPlato");
            JTable tblMenu = (JTable) robot.campo(sistema, "tblTemPlatos");
            JTable tblCarro = (JTable) robot.campo(sistema, "tableMenu");

            robot.reemplazarTexto(buscar, "pollo");
            robot.esperar(() -> {
                for (int i = 0; i < tblMenu.getRowCount(); i++) {
                    Object v = tblMenu.getModel().getValueAt(tblMenu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return true;
                }
                return false;
            }, "Catálogo muestra platos con POLLO");

            int filaPollo = robot.edt(() -> {
                for (int i = 0; i < tblMenu.getRowCount(); i++) {
                    Object v = tblMenu.getModel().getValueAt(tblMenu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return i;
                }
                return -1;
            });
            // Clic en la celda del plato para agregarlo al carrito
            robot.clicCelda(tblMenu, filaPollo, 0);
            robot.esperar(() -> tblCarro.getRowCount() >= 1, "Carrito tiene al menos 1 fila");

            int cant1 = robot.edt(() -> Integer.parseInt(tblCarro.getModel().getValueAt(0, 2).toString()));
            if (cant1 != 1) {
                throw new AssertionError("Se esperaba cantidad 1 en el carrito pero fue " + cant1);
            }
            System.out.println("OK   Carrito con 1 pollo en ronda 1");

            System.out.println("== [E2E] Paso 5: Enviar a cocina -> Mesa pasa a OCUPADA ==");
            JButton btnGenerar = (JButton) robot.campo(sistema, "btnGenerarPedido");
            robot.clic(btnGenerar);

            robot.esperar(() -> {
                int tabActual = tabs.getSelectedIndex();
                return tabActual == tabs.indexOfTab("Mesas") || tabActual == 0;
            }, "Tras enviar comanda regresa a la vista de Mesas");

            robot.esperar(() -> {
                JButton b = buscarBoton(panelMesas, esMesa1);
                if (b == null) return false;
                String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                String txt = b.getText() == null ? "" : b.getText().toUpperCase();
                return tip.contains("Ocupada") || txt.contains("OCUPADA");
            }, "Mesa 1 se muestra OCUPADA en el mapa");
            System.out.println("OK   Mesa 1 marcada como OCUPADA");

            System.out.println("== [E2E] Paso 6: Reabrir Mesa 1 -> Cuenta en Caja muestra 1 pollo ==");
            JButton mesaOcupada = robot.edt(() -> buscarBoton(panelMesas, esMesa1));
            robot.clic(mesaOcupada);
            try {
                robot.esperar(() -> tabs.getSelectedIndex() == 4,
                        "Clic en mesa ocupada abre Caja/Cuenta (tab=4)");
            } catch (AssertionError error) {
                throw new AssertionError(error.getMessage() + "; estado al fallar: "
                        + diagnosticoNavegacion(robot, sistema, tabs, mesaOcupada), error);
            }

            JTable tblFinalizar = (JTable) robot.campo(sistema, "tableFinalizar");
            robot.esperar(() -> tblFinalizar.getRowCount() >= 1, "Detalle de cuenta visible");
            int cantCuenta1 = robot.edt(() -> Integer.parseInt(tblFinalizar.getModel().getValueAt(0, 2).toString()));
            if (cantCuenta1 != 1) {
                throw new AssertionError("La cuenta muestra " + cantCuenta1 + " pollos, esperado 1");
            }
            System.out.println("OK   Cuenta en pantalla confirma 1 pollo");

            System.out.println("== [E2E] Paso 7: Ronda 2 -> Modificar pedido y agregar 2 pollos más ==");
            JButton btnModificar = (JButton) robot.campo(sistema, "btnModificarPedido");
            robot.clic(btnModificar);
            robot.esperar(() -> tabs.getSelectedIndex() == 3, "Modificar pedido lleva a Toma de Pedidos");

            robot.esperar(() -> tblCarro.getRowCount() >= 1, "Carrito precargado con ronda previa");
            robot.reemplazarTexto(buscar, "pollo");

            // Agregar 2 pollos más haciendo clics reales
            for (int k = 0; k < 2; k++) {
                int fp = robot.edt(() -> {
                    for (int i = 0; i < tblMenu.getRowCount(); i++) {
                        Object v = tblMenu.getModel().getValueAt(tblMenu.convertRowIndexToModel(i), 1);
                        if (v != null && v.toString().toUpperCase().contains("POLLO")) return i;
                    }
                    return -1;
                });
                robot.clicCelda(tblMenu, fp, 0);
                Thread.sleep(600);
            }

            robot.esperar(() -> {
                int total = 0;
                for (int i = 0; i < tblCarro.getRowCount(); i++) {
                    total += Integer.parseInt(tblCarro.getModel().getValueAt(tblCarro.convertRowIndexToModel(i), 2).toString());
                }
                return total == 3;
            }, "Carrito suma 3 pollos en total");
            System.out.println("OK   Carrito suma 3 pollos (1 inicial + 2 nueva ronda)");

            System.out.println("== [E2E] Paso 8: Actualizar comanda en cocina ==");
            robot.clic(btnGenerar);
            robot.esperar(() -> tabs.getSelectedIndex() == tabs.indexOfTab("Mesas"), "Actualización regresa al mapa de mesas");

            System.out.println("== [E2E] Paso 9: Reabrir Mesa 1 -> Verificar 3 pollos en la cuenta ==");
            robot.clic(robot.edt(() -> buscarBoton(panelMesas, esMesa1)));
            robot.esperar(() -> tabs.getSelectedIndex() == 4, "Abre Caja/Cuenta de la comanda actualizada");

            robot.esperar(() -> {
                int total = 0;
                for (int i = 0; i < tblFinalizar.getRowCount(); i++) {
                    total += Integer.parseInt(tblFinalizar.getModel().getValueAt(tblFinalizar.convertRowIndexToModel(i), 2).toString());
                }
                return total == 3;
            }, "Cuenta en pantalla muestra 3 pollos");
            System.out.println("OK   Cuenta en pantalla confirma 3 pollos actualizados");

            System.out.println("== [E2E] Paso 10: Liberar mesa por UI (Anular Cuenta / Fuga) ==");
            JButton btnAnularFuga = (JButton) robot.campo(sistema, "btnAnularFuga");
            robot.clic(btnAnularFuga);
            robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Prueba E2E 1+2 pollos completada");
            robot.confirmarDialogo("Confirmar No-Pago (Fuga)");

            robot.clic(sistema.getBtnAtencionDirecta());
            robot.esperar(() -> {
                JButton b = buscarBoton(panelMesas, esMesa1);
                if (b == null) return false;
                String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
                String txt = b.getText() == null ? "" : b.getText().toUpperCase();
                return tip.contains("Disponible") || txt.contains("LIBRE");
            }, "Mesa 1 regresa a estado LIBRE en el mapa tras la anulación");
            System.out.println("OK   Mesa 1 liberada limpiamente a través de la interfaz");

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
            System.err.println("E2E_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("E2E_OK");
        System.exit(0);
    }

    private static String diagnosticoNavegacion(DiaE2ERobot robot, Sistema sistema,
            JTabbedPane tabs, JButton mesa) throws Exception {
        return robot.edt(() -> {
            StringBuilder estado = new StringBuilder()
                    .append("tab=").append(tabs.getSelectedIndex())
                    .append("/").append(tabs.getTitleAt(tabs.getSelectedIndex()))
                    .append(", mesa.enabled=").append(mesa.isEnabled())
                    .append(", mesa.text=").append(mesa.getText())
                    .append(", mesa.tooltip=").append(mesa.getToolTipText());
            for (Window ventana : Window.getWindows()) {
                if (!ventana.isShowing() || ventana == sistema) continue;
                estado.append(", ventana=");
                if (ventana instanceof JDialog dialogo) estado.append(dialogo.getTitle());
                else estado.append(ventana.getClass().getSimpleName());
                java.util.ArrayDeque<Component> pendientes = new java.util.ArrayDeque<>();
                if (ventana instanceof Container contenedor) pendientes.add(contenedor);
                while (!pendientes.isEmpty()) {
                    Component actual = pendientes.removeFirst();
                    if (actual instanceof JLabel etiqueta && etiqueta.getText() != null) {
                        estado.append("[").append(etiqueta.getText()).append("]");
                    }
                    if (actual instanceof Container contenedor) {
                        java.util.Collections.addAll(pendientes, contenedor.getComponents());
                    }
                }
            }
            return estado.toString();
        });
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
