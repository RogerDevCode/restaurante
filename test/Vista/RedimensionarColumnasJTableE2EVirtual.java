package Vista;

import java.awt.Component;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.Test;

/**
 * Test E2E en pantalla virtual (Xvfb DISPLAY=:99) con teclado y mouse simulados por Robot.
 *
 * <p>Prueba en TODAS las JTables de la aplicación:
 * <ol>
 *   <li>La capacidad de redimensionar el ancho de las columnas arrastrando los bordes con el mouse virtual.</li>
 *   <li>Que el sistema conserve los nuevos anchos automáticamente al cerrar y volver a abrir la sesión.</li>
 *   <li>Que la proporción visible de cada columna se recupere en la nueva ventana.</li>
 * </ol>
 *
 * <p>Ejecutable mediante: {@code ./e2e_dia_completo_virtual.sh Vista.RedimensionarColumnasJTableE2EVirtual}</p>
 */
public final class RedimensionarColumnasJTableE2EVirtual {

    private static int fallos = 0;

    @Test
    public void ejecutarE2E() throws Exception {
        if (!"SI".equalsIgnoreCase(System.getProperty("e2e.permitir"))) {
            org.junit.Assume.assumeTrue(
                    "Requiere entorno Xvfb virtual (-De2e.permitir=SI). Se omite en unitarias estándar.",
                    false);
            return;
        }
        main(new String[0]);
    }

    private static void check(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("OK   " + mensaje);
        } else {
            fallos++;
            System.err.println("FALLO " + mensaje);
        }
    }

    private static void maximizar(Window ventana) {
        if (ventana instanceof JFrame frame) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    @FunctionalInterface
    private interface NavegadorTabla {
        void navegar(Sistema sistema, DiaE2ERobot robot) throws Exception;
    }

    @FunctionalInterface
    private interface ExtractorTabla {
        JTable extraer(Sistema sistema, DiaE2ERobot robot) throws Exception;
    }

    private record DefinicionTablaE2E(
            String clave,
            String descripcion,
            int colVista,
            int dx,
            NavegadorTabla navegador,
            ExtractorTabla extractor
    ) {}

    private record AnchoEsperado(int cantidadColumnas, double proporcionColumna) {}

    public static void main(String[] args) throws Exception {
        DiaE2EFixture.exigirBdAislada();
        System.setProperty("modo.automatizado", "true");

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        DiaE2ERobot robot = new DiaE2ERobot();

        // Definición de las 14 tablas auditadas
        Map<String, DefinicionTablaE2E> tablas = new LinkedHashMap<>();

        tablas.put("usuarios", new DefinicionTablaE2E(
                "usuarios", "CRUD Usuarios (TableUsuarios)", 0, 45,
                (s, r) -> r.clic(s.getBtnUsuarios()),
                (s, r) -> s.getTableUsuarios()
        ));

        tablas.put("salas_crud", new DefinicionTablaE2E(
                "salas_crud", "CRUD Salas (tableSala)", 0, 45,
                (s, r) -> r.clic(s.getBtnSala()),
                (s, r) -> s.getTableSala()
        ));

        tablas.put("platos_crud", new DefinicionTablaE2E(
                "platos_crud", "CRUD Platos (TablePlatos)", 0, 45,
                (s, r) -> r.clic(s.getBtnPlatos()),
                (s, r) -> s.getTablePlatos()
        ));

        tablas.put("historial", new DefinicionTablaE2E(
                "historial", "Historial Pedidos (TablePedidos)", 0, 45,
                (s, r) -> r.clic(s.getBtnVentas()),
                (s, r) -> s.getTablePedidos()
        ));

        tablas.put("mesoneros_config", new DefinicionTablaE2E(
                "mesoneros_config", "Personal Mesoneros (tableMesonerosConfig)", 0, 45,
                (s, r) -> {
                    r.clic(s.getBtnConfig());
                    JTabbedPane tabCfg = s.getTabConfigDerecha();
                    r.seleccionarPestaña(tabCfg, "Mesoneros");
                },
                (s, r) -> s.getTableMesonerosConfig()
        ));

        tablas.put("dash_top", new DefinicionTablaE2E(
                "dash_top", "Dashboard Top Platos (tableTopPlatos)", 0, 35,
                (s, r) -> {
                    r.clic(s.getBtnDashboard());
                    r.seleccionarPestaña(s.getTabLeftDashboard(), "Top Platos");
                },
                (s, r) -> s.getTableTopPlatos()
        ));

        tablas.put("dash_mesoneros", new DefinicionTablaE2E(
                "dash_mesoneros", "Dashboard Mesoneros (tableMesonerosDashboard)", 0, 35,
                (s, r) -> {
                    r.clic(s.getBtnDashboard());
                    r.seleccionarPestaña(s.getTabLeftDashboard(), "Mesoneros");
                },
                (s, r) -> s.getTableMesonerosDashboard()
        ));

        tablas.put("dash_clientes", new DefinicionTablaE2E(
                "dash_clientes", "Dashboard Clientes (tableClientes)", 0, 35,
                (s, r) -> r.clic(s.getBtnDashboard()),
                (s, r) -> s.getTableClientes()
        ));

        tablas.put("dash_facturas", new DefinicionTablaE2E(
                "dash_facturas", "Dashboard Facturas Cliente (tableFacturasCliente)", 0, 35,
                (s, r) -> r.clic(s.getBtnDashboard()),
                (s, r) -> s.getTableFacturasCliente()
        ));

        tablas.put("toma_menu", new DefinicionTablaE2E(
                "toma_menu", "Toma Pedido Catálogo (tblTemPlatos)", 0, 40,
                (s, r) -> r.seleccionarPestaña(s.getJTabbedPane1(), 3),
                (s, r) -> s.getTblTemPlatos()
        ));

        tablas.put("toma_carrito", new DefinicionTablaE2E(
                "toma_carrito", "Toma Pedido Carrito (tableMenu)", 0, 40,
                (s, r) -> {
                    JTabbedPane tabs = s.getJTabbedPane1();
                    if (tabs.getSelectedIndex() != 3) {
                        r.seleccionarPestaña(tabs, 3);
                    }
                },
                (s, r) -> s.getTableMenu()
        ));

        tablas.put("caja", new DefinicionTablaE2E(
                "caja", "Caja / Finalizar Pedido (tableFinalizar)", 0, 40,
                (s, r) -> r.seleccionarPestaña(s.getJTabbedPane1(), 4),
                (s, r) -> s.getTableFinalizar()
        ));

        tablas.put("split_catalogo", new DefinicionTablaE2E(
                "split_catalogo", "Split POS Catálogo (tblCatalogoPlatos)", 0, 40,
                (s, r) -> r.seleccionarPestaña(s.getJTabbedPane1(), "Atención Directa (Split)"),
                (s, r) -> s.getPanelSplitScreen().getTablaCatalogo()
        ));

        tablas.put("split_carrito", new DefinicionTablaE2E(
                "split_carrito", "Split POS Carrito (tblCarrito)", 0, 40,
                (s, r) -> {
                    JTabbedPane tabs = s.getJTabbedPane1();
                    int idx = tabs.indexOfTab("Atención Directa (Split)");
                    if (idx >= 0 && tabs.getSelectedIndex() != idx) {
                        r.seleccionarPestaña(tabs, idx);
                    }
                },
                (s, r) -> s.getPanelSplitScreen().getTablaCarrito()
        ));

        Map<String, AnchoEsperado> anchosEsperados = new LinkedHashMap<>();

        try {
            System.out.println("======================================================================");
            System.out.println("  E2E VIRTUAL: REDIMENSIONAR Y RECUPERAR COLUMNAS EN JTABLES ");
            System.out.println("======================================================================");

            // ====================================================================
            // FASE 1: Redimensionar con mouse y registrar la proporción visible
            // ====================================================================
            System.out.println("\n--- FASE 1: Redimensionar columnas con mouse ---");

            restaurante.Restaurante.lanzarLogin();
            FrmLogin login = robot.esperarVentana(FrmLogin.class);
            maximizar(login);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            Sistema sistema = robot.esperarVentana(Sistema.class);
            maximizar(sistema);
            robot.esperar(() -> sistema.isShowing() && sistema.getBtnAtencionDirecta().isShowing(),
                    "Sistema autenticado disponible en Xvfb");

            for (Map.Entry<String, DefinicionTablaE2E> entry : tablas.entrySet()) {
                String clave = entry.getKey();
                DefinicionTablaE2E def = entry.getValue();

                System.out.println("\n[Probando " + def.descripcion() + " (" + clave + ")]");
                def.navegador().navegar(sistema, robot);

                JTable tabla = def.extractor().extraer(sistema, robot);
                robot.esperar(tabla::isShowing, "Tabla " + clave + " visible en pantalla");

                int colCount = robot.edt(() -> tabla.getColumnModel().getColumnCount());
                int anchoAntes = robot.edt(() -> tabla.getColumnModel().getColumn(def.colVista()).getWidth());
                int totalAntes = robot.edt(tabla::getWidth);

                System.out.println("  Ancho inicial de columna " + def.colVista() + ": " + anchoAntes + " px (total=" + totalAntes + ")");

                // Redimensionar arrastrando el borde de la columna con el mouse virtual
                robot.arrastrarBordeColumna(tabla, def.colVista(), def.dx());

                int anchoDespues = robot.edt(() -> tabla.getColumnModel().getColumn(def.colVista()).getWidth());
                int totalDespues = robot.edt(tabla::getWidth);

                System.out.println("  Ancho tras arrastrar con mouse: " + anchoDespues + " px (total=" + totalDespues + ")");
                check(anchoDespues != anchoAntes,
                        clave + ": el ancho de columna en la vista cambió tras arrastrar con el mouse");

                if (anchoDespues != anchoAntes && totalDespues > 0) {
                    double proporcion = (double) anchoDespues / totalDespues;
                    anchosEsperados.put(clave, new AnchoEsperado(colCount, proporcion));
                }
            }

            // ====================================================================
            // FASE 2: Cerrar y reabrir aplicación para verificar la proporción visible
            // ====================================================================
            System.out.println("\n--- FASE 2: Cerrar y reabrir sesión; verificar recuperación visual ---");

            // Deja terminar el debounce de 600 ms y la escritura asíncrona de preferencias.
            Thread.sleep(1000);
            robot.waitForIdle();

            robot.edt(() -> {
                sistema.dispose();
                return null;
            });
            Thread.sleep(800);

            restaurante.Restaurante.lanzarLogin();
            FrmLogin login2 = robot.esperarVentana(FrmLogin.class);
            maximizar(login2);
            robot.reemplazarTexto((JTextField) robot.campo(login2, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login2, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login2, "btnIniciar"));

            Sistema sistema2 = robot.esperarVentana(Sistema.class);
            maximizar(sistema2);
            robot.esperar(() -> sistema2.isShowing() && sistema2.getBtnAtencionDirecta().isShowing(),
                    "Sistema reabierto disponible en Xvfb");

            for (Map.Entry<String, DefinicionTablaE2E> entry : tablas.entrySet()) {
                String clave = entry.getKey();
                DefinicionTablaE2E def = entry.getValue();

                if (!anchosEsperados.containsKey(clave)) {
                    continue;
                }
                AnchoEsperado anchoEsperado = anchosEsperados.get(clave);

                def.navegador().navegar(sistema2, robot);
                JTable tabla2 = def.extractor().extraer(sistema2, robot);
                robot.esperar(tabla2::isShowing, "Tabla " + clave + " visible en nueva sesión");
                Thread.sleep(200);
                robot.waitForIdle();

                int nCols = robot.edt(() -> tabla2.getColumnModel().getColumnCount());
                int anchoColumna = robot.edt(() -> tabla2.getColumnModel().getColumn(def.colVista()).getWidth());
                int anchoTabla = robot.edt(tabla2::getWidth);

                check(nCols == anchoEsperado.cantidadColumnas(),
                        clave + ": la JTable conserva sus " + anchoEsperado.cantidadColumnas() + " columnas");
                if (nCols != anchoEsperado.cantidadColumnas() || anchoTabla <= 0) {
                    continue;
                }

                double propRecuperada = (double) anchoColumna / anchoTabla;
                double propEsperada = anchoEsperado.proporcionColumna();
                double diff = Math.abs(propRecuperada - propEsperada);

                check(diff < 0.06,
                        clave + ": JTable recuperó la proporción visible de ancho (esperado="
                                + String.format("%.4f", propEsperada) + ", obtenido="
                                + String.format("%.4f", propRecuperada) + ", diff=" + String.format("%.4f", diff) + ")");
            }

        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        w.dispose();
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        System.out.println("======================================================================");
        if (fallos == 0) {
            System.out.println("E2E_OK");
        } else {
            System.err.println("E2E_FALLO: Se encontraron " + fallos + " fallos durante la prueba.");
            throw new AssertionError("Falló la suite E2E de Redimensionar Columnas JTable (" + fallos + " fallos).");
        }
    }
}
