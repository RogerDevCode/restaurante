package Vista;

import Modelo.PreferenciasTablaDao;
import infraestructura.ProveedorConexionJdbc;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 *   <li>Que el sistema guarde los nuevos anchos/pesos automáticamente en la BD ({@code preferencias_tabla}).</li>
 *   <li>Cierre y reapertura de la aplicación para recuperar los anchos de columnas de la BD y aplicarlos a la vista de cada JTable.</li>
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

    private static void limpiarPreferenciasUsuario(int usuarioId) {
        try (Connection con = new ProveedorConexionJdbc().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM preferencias_tabla WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
            System.out.println("OK   Preferencias de tabla limpiadas para usuario #" + usuarioId + " en BD.");
        } catch (Exception ex) {
            System.err.println("Aviso al limpiar preferencias previas: " + ex.getMessage());
        }
    }

    private static Optional<double[]> esperarPesosGuardados(
            PreferenciasTablaDao dao, int uid, String clave, int expectedCols,
            int colVista, double propEsperada, long maxEsperaMs) throws Exception {
        long fin = System.currentTimeMillis() + maxEsperaMs;
        while (System.currentTimeMillis() < fin) {
            Optional<double[]> guardado = dao.cargar(uid, clave);
            if (guardado.isPresent() && guardado.get().length == expectedCols) {
                double[] w = guardado.get();
                if (Math.abs(w[colVista] - propEsperada) < 0.06) {
                    return guardado;
                }
            }
            Thread.sleep(150);
        }
        return dao.cargar(uid, clave);
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

    public static void main(String[] args) throws Exception {
        DiaE2EFixture.exigirBdAislada();
        System.setProperty("modo.automatizado", "true");

        final int usuarioAdminId = 1;
        limpiarPreferenciasUsuario(usuarioAdminId);

        final PreferenciasTablaDao prefDao = new PreferenciasTablaDao();
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

        Map<String, double[]> pesosGuardadosEnFase1 = new LinkedHashMap<>();

        try {
            System.out.println("======================================================================");
            System.out.println("  E2E VIRTUAL: REDIMENSIONAR COLUMNAS EN JTABLES Y PERSISTENCIA EN BD ");
            System.out.println("======================================================================");

            // ====================================================================
            // FASE 1: Redimensionar con mouse y validar persistencia en BD
            // ====================================================================
            System.out.println("\n--- FASE 1: Redimensionar columnas con mouse y verificar guardado en BD ---");

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

                double propCalculada = (double) anchoDespues / totalDespues;

                // Esperar a que el debounce (600ms) y el SwingWorker persistan en MySQL
                Thread.sleep(700);
                Optional<double[]> guardado = esperarPesosGuardados(prefDao, usuarioAdminId, clave, colCount, def.colVista(), propCalculada, 4000);
                check(guardado.isPresent(),
                        clave + ": anchos/pesos guardados en la BD (tabla preferencias_tabla)");

                if (guardado.isPresent()) {
                    double[] w = guardado.get();
                    check(w.length == colCount,
                            clave + ": la BD contiene pesos para las " + colCount + " columnas de la JTable");

                    check(Math.abs(w[def.colVista()] - propCalculada) < 0.08,
                            clave + ": la proporción en BD (" + String.format("%.4f", w[def.colVista()])
                                    + ") coincide con la vista (" + String.format("%.4f", propCalculada) + ")");

                    pesosGuardadosEnFase1.put(clave, w);
                }
            }

            // ====================================================================
            // FASE 2: Cerrar y reabrir aplicación para verificar recuperación de BD a la vista
            // ====================================================================
            System.out.println("\n--- FASE 2: Cerrar y reabrir aplicación; verificar recuperación desde BD a JTable ---");

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

                if (!pesosGuardadosEnFase1.containsKey(clave)) {
                    continue;
                }
                double[] pesosEsperados = pesosGuardadosEnFase1.get(clave);

                def.navegador().navegar(sistema2, robot);
                JTable tabla2 = def.extractor().extraer(sistema2, robot);
                robot.esperar(tabla2::isShowing, "Tabla " + clave + " visible en nueva sesión");
                Thread.sleep(200);
                robot.waitForIdle();

                int nCols = robot.edt(() -> tabla2.getColumnModel().getColumnCount());
                int prefW = robot.edt(() -> tabla2.getColumnModel().getColumn(def.colVista()).getPreferredWidth());

                int sumaPref = robot.edt(() -> {
                    int s = 0;
                    for (int i = 0; i < nCols; i++) {
                        s += tabla2.getColumnModel().getColumn(i).getPreferredWidth();
                    }
                    return Math.max(s, 1);
                });

                double propRecuperada = (double) prefW / sumaPref;
                double propEsperada = pesosEsperados[def.colVista()];
                double diff = Math.abs(propRecuperada - propEsperada);

                check(diff < 0.06,
                        clave + ": JTable recuperó anchos de BD a la vista (esperado="
                                + String.format("%.4f", propEsperada) + ", obtenido="
                                + String.format("%.4f", propRecuperada) + ", diff=" + String.format("%.4f", diff) + ")");
            }

        } finally {
            limpiarPreferenciasUsuario(usuarioAdminId);
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
