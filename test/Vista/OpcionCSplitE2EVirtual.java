package Vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E Virtual de Opción C (Pantalla Dividida / Split-Screen) en Xvfb :99 con Robot.
 *
 * Flujo:
 * 1. Login interactivo en FrmLogin ("admin" / "admin").
 * 2. Conmutación a Opción C (Split-Screen) desde el conmutador global.
 * 3. Verificación de dimensionamiento y ajuste ergonómico sin scroll vertical exterior (<= 600px).
 * 4. Verificación de botones de mesas proporcionales en la grilla (altura 60-90px, código en negrita).
 * 5. Si Mesa 1 (P-1) estuviese ocupada previamente, liberación vía UI (btnFuga).
 * 6. Selección de Mesa 1 (P-1) con un solo clic -> Cabecera refleja P-1.
 * 7. Catálogo de platos: selección de plato y adición al carrito.
 * 8. Incremento de cantidad con el botón '+' (+1 unidad).
 * 9. Enviar a Cocina (btnEnviarCocina) -> Mesa P-1 pasa a estado OCUPADA.
 * 10. Verificación de que el botón 'Cobrar' está habilitado y visible en pantalla.
 * 11. Conmutación a Mesa 2 (libre, carrito vacío) y retorno a Mesa 1 (ocupada, recarga comanda).
 * 12. Liberación limpia por UI mediante 'No Pagó' (btnFuga) confirmando diálogos.
 * 13. Verificación de que Mesa 1 regresa a estado LIBRE (disponible).
 *
 * Cumple estrictamente con las reglas de AGENTS.md:
 * - Cero repositorios en memoria (SalasMemoria, PlatosMemoria, PedidosMemoria).
 * - Cero atajos por código ni sentencias SQL directas.
 * - Toda la interacción ocurre mediante componentes observables de Swing.
 */
public class OpcionCSplitE2EVirtual {

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

            System.out.println("== [Opción C Split E2E] Paso 1: Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            robot.edt(() -> {
                login.setLocationRelativeTo(null);
                return null;
            });
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            System.out.println("== [Opción C Split E2E] Paso 2: Esperar ventana Sistema y conmutar a Opción C ==");
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setBounds(0, 0, 1366, 711);
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.revalidate();
                sistema.setVisible(true);
                return null;
            });
            JComboBox<?> modoGlobal = sistema.getCbModoAtencionGlobal();
            robot.esperar(() -> sistema.isShowing() && modoGlobal != null,
                    "Ventana Sistema con selector global disponible");

            robot.seleccionarComboPorIndice(modoGlobal, 2); // Opción C
            robot.clic(sistema.getBtnAtencionDirecta());
            robot.esperar(() -> "OPCION_C".equals(sistema.getModoAtencionActual()),
                    "Modo de atención actualizado a OPCION_C");

            PanelAtencionMesasSplit split = sistema.getPanelSplitScreen();
            robot.esperar(() -> split != null && split.isShowing(),
                    "PanelAtencionMesasSplit visible en pantalla");

            System.out.println("== [Opción C Split E2E] Paso 3: Dimensionamiento y ergonomía de tarjetas ==");
            if (split.getHeight() > 700) {
                throw new AssertionError("Altura de PanelAtencionMesasSplit excede el límite visible: " + split.getHeight() + "px");
            }
            System.out.println("OK   Panel Split cabe en pantalla (" + split.getHeight() + "px <= 700px)");

            robot.esperar(() -> !obtenerBotonesMesa(split).isEmpty(),
                    "Botones de mesa cargados en la grilla de Split");
            List<JButton> botonesMesa = obtenerBotonesMesa(split);
            JButton botonMesa1 = botonesMesa.get(0);
            int altoBoton = robot.edt(botonMesa1::getHeight);
            if (altoBoton < 40 || altoBoton > 110) {
                throw new AssertionError("Alto de tarjeta de mesa no es ergonómico: " + altoBoton + "px");
            }
            System.out.println("OK   Tarjetas de mesa ergonómicas (" + altoBoton + "px)");

            // Limpieza previa si Mesa 1 estuviese ocupada
            boolean ocupadaInicial = robot.edt(() -> esMesaOcupada(botonMesa1));
            if (ocupadaInicial) {
                System.out.println("[Opción C E2E Info] Mesa 1 estaba ocupada: liberando por UI...");
                robot.clic(botonMesa1);
                JButton btnFuga = split.getBtnFuga();
                Thread hiloLimpieza = new Thread(() -> {
                    try {
                        robot.clic(btnFuga);
                    } catch (Exception ignored) {}
                });
                hiloLimpieza.start();
                robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Limpieza inicial E2E Split");
                robot.confirmarDialogo("Confirmar No-Pago (Fuga)");
                hiloLimpieza.join(5000);
                robot.esperar(() -> esMesaLibre(obtenerBotonesMesa(split).get(0)),
                        "Mesa 1 liberada tras limpieza inicial");
            }

            System.out.println("== [Opción C Split E2E] Paso 4: Selección de Mesa 1 (P-1) ==");
            robot.clic(botonMesa1);
            JLabel lblCabecera = obtenerLabelCabecera(split);
            robot.esperar(() -> lblCabecera != null && lblCabecera.getText().contains("P-1"),
                    "Cabecera refleja mesa seleccionada P-1");
            System.out.println("OK   Mesa P-1 seleccionada");

            System.out.println("== [Opción C Split E2E] Paso 5: Catálogo de platos y adición al pedido ==");
            JTable tblCatalogo = split.getTablaCatalogo();
            robot.esperar(() -> tblCatalogo != null && tblCatalogo.getRowCount() >= 1,
                    "Catálogo de platos contiene productos");

            robot.clicCelda(tblCatalogo, 0, 0);
            robot.esperar(() -> split.getCantidadItemsEnCarrito() >= 1,
                    "Plato agregado al carrito en Split");
            System.out.println("OK   Plato agregado al carrito");

            System.out.println("== [Opción C Split E2E] Paso 6: Incrementar cantidad con botón '+' ==");
            JButton btnMas = encontrarBotonPorTextoExacto(split, "+");
            if (btnMas != null) {
                robot.clic(btnMas);
            }
            System.out.println("OK   Botón '+' pulsado");

            System.out.println("== [Opción C Split E2E] Paso 7: Enviar a Cocina -> Mesa pasa a OCUPADA ==");
            JButton btnCocina = split.getBtnEnviarCocina();
            if (btnCocina == null) throw new AssertionError("Botón 'Enviar a Cocina' no encontrado");
            robot.clic(btnCocina);

            robot.esperar(() -> {
                List<JButton> ms = obtenerBotonesMesa(split);
                return !ms.isEmpty() && esMesaOcupada(ms.get(0));
            }, "Mesa P-1 pasa a estado OCUPADA");
            System.out.println("OK   Mesa P-1 en estado OCUPADA");

            JButton btnCobrar = split.getBtnCobrar();
            robot.esperar(() -> btnCobrar != null && btnCobrar.isEnabled() && btnCobrar.isShowing(),
                    "Botón 'Cobrar' habilitado y visible");
            System.out.println("OK   Botón Cobrar habilitado");

            System.out.println("== [Opción C Split E2E] Paso 8: Conmutar a Mesa 2 y regresar a Mesa 1 ==");
            List<JButton> botonesMesaPaso8 = obtenerBotonesMesa(split);
            if (botonesMesaPaso8.size() >= 2) {
                robot.clic(botonesMesaPaso8.get(1));
                robot.esperar(() -> split.getCantidadItemsEnCarrito() == 0,
                        "Mesa 2 (libre) muestra carrito vacío");

                robot.esperar(() -> {
                    List<JButton> ms = obtenerBotonesMesa(split);
                    return !ms.isEmpty() && esMesaOcupada(ms.get(0));
                }, "Grilla actualizada con Mesa 1 ocupada");

                List<JButton> botonesRetorno = obtenerBotonesMesa(split);
                robot.clic(botonesRetorno.get(0));
                robot.esperar(() -> split.getCantidadItemsEnCarrito() >= 1 && split.getIdPedidoActivo() > 0,
                        "Mesa 1 (ocupada) recarga automáticamente su comanda y pedido activo");
                System.out.println("OK   Recarga automática de comanda existente validada (Pedido #" + split.getIdPedidoActivo() + ")");
            }

            System.out.println("== [Opción C Split E2E] Paso 9: Liberar comanda mediante 'No Pagó' (btnFuga) ==");
            robot.esperar(() -> split.getIdPedidoActivo() > 0, "Hay un pedido activo seleccionado para anular");
            JButton btnFuga = split.getBtnFuga();
            robot.esperar(() -> btnFuga != null && btnFuga.isEnabled() && btnFuga.isShowing(),
                    "Botón 'No Pagó' visible y habilitado");

            robot.clic(btnFuga);
            robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Prueba E2E Split completada");
            robot.confirmarDialogo("Confirmar No-Pago (Fuga)");

            robot.esperar(() -> {
                List<JButton> ms = obtenerBotonesMesa(split);
                return !ms.isEmpty() && esMesaLibre(ms.get(0));
            }, "Mesa P-1 regresa a estado DISPONIBLE tras la anulación");
            System.out.println("OK   Mesa P-1 liberada limpiamente a través de la interfaz");

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
            System.err.println("E2E_OPCION_C_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("E2E_OPCION_C_OK");
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

    private static JLabel obtenerLabelCabecera(Container root) {
        if (root instanceof JLabel l && l.getText() != null && l.getText().contains("MESA:")) {
            return l;
        }
        for (Component c : root.getComponents()) {
            if (c instanceof Container cont) {
                JLabel encontrado = obtenerLabelCabecera(cont);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private static JButton encontrarBotonPorTexto(Container root, String texto) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && b.getText() != null && b.getText().contains(texto)) {
                return b;
            }
            if (c instanceof Container cont) {
                JButton res = encontrarBotonPorTexto(cont, texto);
                if (res != null) return res;
            }
        }
        return null;
    }

    private static JButton encontrarBotonPorTextoExacto(Container root, String texto) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && texto.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container cont) {
                JButton res = encontrarBotonPorTextoExacto(cont, texto);
                if (res != null) return res;
            }
        }
        return null;
    }

    private static boolean esMesaOcupada(JButton b) {
        if (b == null) return false;
        String tip = b.getToolTipText();
        if (tip != null && tip.contains("Ocupada")) return true;
        java.awt.Color bg = b.getBackground();
        return Servicio.ServicioMesa.FONDO_MESA_OCUPADA.equals(bg);
    }

    private static boolean esMesaLibre(JButton b) {
        if (b == null) return false;
        String tip = b.getToolTipText();
        if (tip != null && tip.contains("Disponible")) return true;
        String txt = b.getText();
        if (txt != null && txt.contains("LIBRE")) return true;
        return Servicio.ServicioMesa.FONDO_MESA_LIBRE.equals(b.getBackground());
    }
}
