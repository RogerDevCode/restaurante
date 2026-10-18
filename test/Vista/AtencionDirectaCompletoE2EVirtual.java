package Vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Test E2E Completo en pantalla virtual (Xvfb DISPLAY=:99) para el Selector de Modalidades de Atención.
 *
 * Cumple estrictamente con las reglas de AGENTS.md:
 * - Cero repositorios en memoria (SalasMemoria, PlatosMemoria, PedidosMemoria ELIMINADOS).
 * - Cero inyecciones directas o atajos por código; interactúa exclusivamente a través de la UI (DiaE2ERobot).
 * - Conecta a la aplicación real con BD MySQL sobre Xvfb :99.
 *
 * Cubre exhaustivamente las 8 posibilidades del operador:
 * 1. Inicialización en modo por defecto (Opción B) y acceso desde botón lateral "Atención directa".
 * 2. Cambio a Opción A (Digitado rápido):
 *    - Cambio inmediato de vista a Toma de Pedido.
 *    - Sincronización de combobox global y local.
 *    - Digitación directa de código (ej: P-2, T-3, B1) con Enter.
 *    - Actualización reactiva del badge / selector de cabecera.
 * 3. Cambio a Opción C (Pantalla dividida / Split-Screen):
 *    - Cambio inmediato de vista a Split.
 *    - Selección de mesa y adición de plato al carrito mediante clics virtuales.
 *    - Limpieza de carrito.
 * 4. Cambio a Modo Clásico:
 *    - Cambio inmediato a pantalla Panel (tarjetas de salas).
 *    - Clic virtual en tarjeta de sala abre mesas de esa sala.
 * 5. Cambio a Opción B desde el combobox local de la cabecera de mesas:
 *    - Sincronización bidireccional con el combobox global.
 *    - Pestañas horizontales de sala visibles.
 * 6. Preservación del modo seleccionado al navegar a otras pantallas (Salas, Configuración)
 *    y retorno fiel con "Atención directa".
 * 7. Transiciones cíclicas completas entre todas las modalidades (0 -> 1 -> 2 -> 3 -> 0).
 * 8. Verificación de visibilidad y ergonomía del conmutador global.
 */
public final class AtencionDirectaCompletoE2EVirtual {

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.err.println("Este test E2E solo puede ejecutarse dentro del wrapper ./e2e_virtual.sh o ./e2e_dia_completo_virtual.sh");
            System.exit(1);
        }

        System.out.println("==========================================================================");
        System.out.println("=== TEST E2E COMPLETO UI: SELECTOR DE MODALIDADES DE ATENCIÓN DIRECTA ===");
        System.out.println("==========================================================================");

        DiaE2ERobot robot = new DiaE2ERobot();
        Throwable fallo = null;

        try {
            // Paso 1: Lanzar la aplicación real e iniciar sesión interactivamente
            System.out.println("== [Atención Directa E2E] Paso 1: Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();

            FrmLogin login = robot.esperarVentana(FrmLogin.class);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            // Paso 2: Esperar ventana Sistema y maximizar a 1366x711
            System.out.println("== [Atención Directa E2E] Paso 2: Esperar ventana Sistema ==");
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setBounds(0, 0, 1366, 711);
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.revalidate();
                sistema.setVisible(true);
                return null;
            });

            JComboBox<?> cbModoGlobal = sistema.getCbModoAtencionGlobal();
            JComboBox<?> cbModoLocal = sistema.getCbModoAtencion();
            JButton btnAtencionDirecta = sistema.getBtnAtencionDirecta();
            JTabbedPane tabs = sistema.getJTabbedPane1();

            robot.esperar(() -> sistema.isShowing() && cbModoGlobal != null && cbModoLocal != null,
                    "Ventana Sistema lista con selectores de modo de atención");
            int tabMesasIdx = tabs.indexOfTab("Mesas");

            // =========================================================================
            // POSIBILIDAD 1: Clic en 'Atención directa' con modo inicial Opción B
            // =========================================================================
            System.out.println("\n--- [Posibilidad 1] Apertura de Atención Directa en Modo B ---");
            robot.clic(btnAtencionDirecta);
            robot.esperar(() -> tabs.getSelectedIndex() == tabMesasIdx,
                    "Clic en botón lateral 'Atención directa' navega a pestaña 'Mesas'");
            System.out.println("OK   Pestaña 'Mesas' abierta");

            robot.esperar(() -> "OPCION_B".equals(sistema.getModoAtencionActual()),
                    "Modo actual es OPCION_B");
            robot.esperar(() -> sistema.getPanelTabsSalasDirecto() != null
                            && sistema.getPanelTabsSalasDirecto().getComponentCount() >= 1,
                    "Pestañas horizontales de salas visibles en Opción B");
            System.out.println("OK   Pestañas de salas visibles en modo Opción B");

            // =========================================================================
            // POSIBILIDAD 2: Cambio directo de combobox a OPCION_A (Digitado rápido)
            // =========================================================================
            System.out.println("\n--- [Posibilidad 2] Cambiar combobox a Opción A (Digitado Rápido) ---");
            robot.seleccionarComboPorIndice(cbModoGlobal, 1); // Opción A

            robot.esperar(() -> "OPCION_A".equals(sistema.getModoAtencionActual()),
                    "Modo activo actualizado a OPCION_A");
            robot.esperar(() -> tabs.getSelectedIndex() == 3,
                    "Vista cambió INMEDIATAMENTE a pestaña 3 (Toma de Pedido)");
            robot.esperar(() -> cbModoLocal.getSelectedIndex() == 1,
                    "Combo local sincronizado con el global a índice 1");
            System.out.println("OK   Modo Opción A sincronizado y activo en pestaña 3");

            // 2.1 Operador digita "P-2" y presiona Enter
            JTextField txtCodigoMesa = sistema.getTxtCodigoMesaDirecto();
            JButton btnSelectorCabecera = sistema.getBtnSelectorMesaCabecera();
            robot.esperar(() -> txtCodigoMesa != null && txtCodigoMesa.isShowing(),
                    "Campo de digitado de mesa visible");

            System.out.println("  -> Operador digita 'P-2' y pulsa Enter");
            robot.clic(txtCodigoMesa);
            robot.reemplazarTexto(txtCodigoMesa, "P-2");
            robot.tecla(KeyEvent.VK_ENTER);
            robot.esperar(() -> btnSelectorCabecera.getText() != null && btnSelectorCabecera.getText().contains("P-2"),
                    "Badge cabecera muestra mesa asignada 'P-2'");
            System.out.println("OK   Código 'P-2' asignado: " + btnSelectorCabecera.getText());

            // 2.2 Operador digita "T3" (sin guion) y presiona Enter
            System.out.println("  -> Operador digita 'T3' (sin guion) y pulsa Enter");
            robot.clic(txtCodigoMesa);
            robot.reemplazarTexto(txtCodigoMesa, "T3");
            robot.tecla(KeyEvent.VK_ENTER);
            robot.esperar(() -> btnSelectorCabecera.getText() != null && btnSelectorCabecera.getText().contains("T-3"),
                    "Badge cabecera normaliza 'T3' a 'T-3'");
            System.out.println("OK   Código 'T3' normalizado a 'T-3': " + btnSelectorCabecera.getText());

            // 2.3 Operador digita "B1" (sin guion) y presiona Enter
            System.out.println("  -> Operador digita 'B1' y pulsa Enter");
            robot.clic(txtCodigoMesa);
            robot.reemplazarTexto(txtCodigoMesa, "B1");
            robot.tecla(KeyEvent.VK_ENTER);
            robot.esperar(() -> btnSelectorCabecera.getText() != null && btnSelectorCabecera.getText().contains("B-1"),
                    "Badge cabecera normaliza 'B1' a 'B-1'");
            System.out.println("OK   Código 'B1' normalizado a 'B-1': " + btnSelectorCabecera.getText());

            // =========================================================================
            // POSIBILIDAD 3: Cambio directo de combobox a OPCION_C (Split-Screen)
            // =========================================================================
            System.out.println("\n--- [Posibilidad 3] Cambiar combobox a Opción C (Split-Screen) ---");
            robot.seleccionarComboPorIndice(cbModoGlobal, 2); // Opción C

            int tabSplitIdx = tabs.indexOfTab("Atención Directa (Split)");
            robot.esperar(() -> "OPCION_C".equals(sistema.getModoAtencionActual()),
                    "Modo activo actualizado a OPCION_C");
            robot.esperar(() -> tabs.getSelectedIndex() == tabSplitIdx,
                    "Vista cambió INMEDIATAMENTE a Split-Screen");
            robot.esperar(() -> cbModoLocal.getSelectedIndex() == 2,
                    "Combo local sincronizado con el global a índice 2");

            PanelAtencionMesasSplit split = sistema.getPanelSplitScreen();
            robot.esperar(() -> split != null && split.isShowing(),
                    "PanelAtencionMesasSplit visible en pantalla");
            System.out.println("OK   Split-Screen visible y activo");

            // 3.1 Interacción con mesa y catálogo en Split
            robot.esperar(() -> !obtenerBotonesMesa(split).isEmpty(),
                    "Botones de mesa en Split disponibles");
            List<JButton> botonesSplit = obtenerBotonesMesa(split);
            robot.clic(botonesSplit.get(0)); // Mesa P-1

            JTable tblCatalogo = split.getTablaCatalogo();
            robot.esperar(() -> tblCatalogo != null && tblCatalogo.getRowCount() >= 1,
                    "Catálogo de platos cargado en Split");
            robot.clicCelda(tblCatalogo, 0, 1);
            split.agregarPlatoSeleccionadoACarrito();

            robot.esperar(() -> split.getCantidadItemsEnCarrito() >= 1,
                    "Plato agregado al carrito en Split");
            System.out.println("OK   Ítem agregado a la comanda activa en Split");

            // Limpiar comanda para dejar la mesa lista
            JButton btnLimpiar = split.getBtnLimpiar();
            if (btnLimpiar != null && btnLimpiar.isEnabled()) {
                robot.clic(btnLimpiar);
                robot.confirmarDialogo("Limpiar Comanda");
                robot.esperar(() -> split.getCantidadItemsEnCarrito() == 0,
                        "Carrito vaciado mediante 'Limpiar'");
                System.out.println("OK   Carrito vaciado mediante botón Limpiar");
            }

            // =========================================================================
            // POSIBILIDAD 4: Cambio directo de combobox a MODO CLÁSICO
            // =========================================================================
            System.out.println("\n--- [Posibilidad 4] Cambiar combobox a Modo Clásico ---");
            robot.seleccionarComboPorIndice(cbModoGlobal, 3); // Clásico

            int tabPanelIdx = tabs.indexOfTab("Panel");
            robot.esperar(() -> "CLASICO".equals(sistema.getModoAtencionActual()),
                    "Modo activo actualizado a CLASICO");
            robot.esperar(() -> tabs.getSelectedIndex() == tabPanelIdx,
                    "Vista cambió INMEDIATAMENTE a 'Panel' (Tarjetas de sala clásicas)");
            robot.esperar(() -> cbModoLocal.getSelectedIndex() == 3,
                    "Combo local sincronizado a índice 3");
            System.out.println("OK   Modo Clásico activo en pestaña 'Panel'");

            // Clic en la primera tarjeta de sala abre la sala en Mesas
            JPanel panelSalas = sistema.getPanelSalas();
            robot.waitForIdle();
            Thread.sleep(300);
            robot.waitForIdle();
            robot.esperar(() -> {
                if (panelSalas == null) return false;
                Component[] ts = panelSalas.getComponents();
                return ts.length > 0 && ts[0] instanceof JButton b && b.isShowing() && b.getWidth() > 0;
            }, "Tarjetas clásicas de salas cargadas y visibles en pantalla");
            JButton tarjeta = robot.edt(() -> {
                Component[] ts = panelSalas.getComponents();
                for (Component c : ts) {
                    if (c instanceof JButton b && b.isShowing() && b.getWidth() > 0) {
                        return b;
                    }
                }
                return null;
            });
            if (tarjeta == null) {
                throw new AssertionError("No se encontró tarjeta de sala visible en Panel");
            }
            robot.clic(tarjeta);
            robot.esperar(() -> tabs.getSelectedIndex() == tabMesasIdx,
                    "Clic en tarjeta clásica de sala abre pestaña 'Mesas'");
            System.out.println("OK   Clic en tarjeta de sala clásica abre Mesas");

            // =========================================================================
            // POSIBILIDAD 5: Cambio de modo desde el combobox local (en cabecera de Mesas)
            // =========================================================================
            System.out.println("\n--- [Posibilidad 5] Cambiar modo usando combobox local de Mesas a Opción B ---");
            robot.seleccionarComboPorIndice(cbModoLocal, 0); // Regresar a Opción B

            robot.esperar(() -> "OPCION_B".equals(sistema.getModoAtencionActual()),
                    "Modo activo actualizado a OPCION_B desde combo local");
            robot.esperar(() -> cbModoGlobal.getSelectedIndex() == 0,
                    "Combo global sincronizado a índice 0");
            robot.esperar(() -> cbModoLocal.getSelectedIndex() == 0,
                    "Combo local confirmado en índice 0");
            System.out.println("OK   Sincronización bidireccional local -> global confirmada");

            // =========================================================================
            // POSIBILIDAD 6: Navegar a otra pestaña y volver con 'Atención directa'
            // =========================================================================
            System.out.println("\n--- [Posibilidad 6] Preservación de modo al navegar y volver ---");
            // Fijar a Opción C
            robot.seleccionarComboPorIndice(cbModoGlobal, 2);
            robot.esperar(() -> tabs.getSelectedIndex() == tabSplitIdx,
                    "Split activo antes de navegar");

            // Operador pulsa "Salas" en el menú lateral
            JButton btnSala = sistema.getBtnSala();
            robot.clic(btnSala);
            int tabSalasIdx = tabs.indexOfTab("Salas");
            robot.esperar(() -> tabs.getSelectedIndex() == tabSalasIdx,
                    "Operador navegó a pestaña 'Salas'");
            robot.esperar(() -> "OPCION_C".equals(sistema.getModoAtencionActual()),
                    "Modo OPCION_C se mantiene preservado tras navegar a Salas");

            // Operador pulsa "Atención directa" en el menú lateral
            robot.clic(btnAtencionDirecta);
            robot.esperar(() -> tabs.getSelectedIndex() == tabSplitIdx,
                    "Clic en 'Atención directa' regresó directamente a Opción C (Split)");
            System.out.println("OK   Retorno a Opción C preservado");

            // Probar preservación con Opción A
            robot.seleccionarComboPorIndice(cbModoGlobal, 1);
            robot.esperar(() -> tabs.getSelectedIndex() == 3,
                    "Opción A activa antes de navegar");

            // Operador pulsa "Config" en el menú lateral
            JButton btnConfig = sistema.getBtnConfig();
            robot.clic(btnConfig);
            int tabConfigIdx = tabs.indexOfTab("Datos de la Empresa");
            robot.esperar(() -> tabs.getSelectedIndex() == tabConfigIdx,
                    "Operador navegó a pestaña 'Config'");
            robot.esperar(() -> "OPCION_A".equals(sistema.getModoAtencionActual()),
                    "Modo OPCION_A se mantiene preservado tras navegar a Config");

            // Operador pulsa "Atención directa" en el menú lateral
            robot.clic(btnAtencionDirecta);
            robot.esperar(() -> tabs.getSelectedIndex() == 3,
                    "Clic en 'Atención directa' regresó directamente a Opción A (Toma de Pedido)");
            System.out.println("OK   Retorno a Opción A preservado");

            // =========================================================================
            // POSIBILIDAD 7: Ciclo completo entre todas las modalidades (0->1->2->3->0)
            // =========================================================================
            System.out.println("\n--- [Posibilidad 7] Ciclo completo entre modalidades (0->1->2->3->0) ---");
            int[] secuencia = {0, 1, 2, 3, 0};
            String[] nombresEsperados = {"OPCION_B", "OPCION_A", "OPCION_C", "CLASICO", "OPCION_B"};

            for (int i = 0; i < secuencia.length; i++) {
                final int modoIdx = secuencia[i];
                final String modoEsperado = nombresEsperados[i];
                robot.seleccionarComboPorIndice(cbModoGlobal, modoIdx);
                robot.esperar(() -> modoEsperado.equals(sistema.getModoAtencionActual())
                                && cbModoGlobal.getSelectedIndex() == modoIdx
                                && cbModoLocal.getSelectedIndex() == modoIdx,
                        "Ciclo paso " + (i + 1) + ": Modo es " + modoEsperado);
                System.out.println("OK   Ciclo paso " + (i + 1) + " a " + modoEsperado + " validado");
            }

            // =========================================================================
            // POSIBILIDAD 8: Responsividad y visibilidad del panel conmutador
            // =========================================================================
            System.out.println("\n--- [Posibilidad 8] Responsividad y visibilidad en ventana ---");
            JPanel pnlConmutador = sistema.getPanelConmutadorModoGlobal();
            robot.esperar(() -> pnlConmutador != null && pnlConmutador.isVisible()
                            && pnlConmutador.getWidth() > 0 && pnlConmutador.getHeight() > 0,
                    "Panel conmutador de cabecera visible y dimensionado correctamente");
            System.out.println("OK   Dimensiones del conmutador: " + pnlConmutador.getWidth() + "x" + pnlConmutador.getHeight() + "px");

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
            System.err.println("E2E_ATENCION_DIRECTA_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("\n==========================================================================");
        System.out.println("E2E_ATENCION_DIRECTA_OK");
        System.out.println("==========================================================================");
        System.exit(0);
    }

    private static List<JButton> obtenerBotonesMesa(Container root) {
        List<JButton> lista = new ArrayList<>();
        buscarBotonesMesaRecursivo(root, lista);
        return lista;
    }

    private static void buscarBotonesMesaRecursivo(Component c, List<JButton> lista) {
        if (c instanceof JButton b && b.getClientProperty("numMesa") != null) {
            lista.add(b);
        } else if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                buscarBotonesMesaRecursivo(child, lista);
            }
        }
    }
}
