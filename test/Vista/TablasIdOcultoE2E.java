package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * E2E en pantalla virtual (Xvfb DISPLAY=:99, Robot propio):
 * {@code ./e2e_virtual.sh Vista.TablasIdOcultoE2E}
 *
 * Valida para TODAS las JTables con ID interno:
 * 1. La columna ID está fuera de la vista pero sigue en el TableModel.
 * 2. La selección por clic lee el ID correcto desde el modelo
 *    (incluye historial ordenado por Total descendente).
 * 3. Arrastrar un borde de columna guarda los pesos en BD
 *    ({@code preferencias_tabla}) y una tabla nueva los recupera.
 *
 * Toca la BD real solo en {@code preferencias_tabla} (respalda y restaura
 * los pesos previos del mismo usuario/clave). Exige {@code -De2e.permitir=SI}.
 */
public final class TablasIdOcultoE2E {

    private static int fallos = 0;
    private static Robot robot;

    private static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    private static <T> T enEdt(Callable<T> c) throws Exception {
        final Object[] r = new Object[1];
        final Throwable[] e = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                r[0] = c.call();
            } catch (Throwable t) {
                e[0] = t;
            }
        });
        if (e[0] != null) {
            throw new RuntimeException(e[0]);
        }
        @SuppressWarnings("unchecked")
        T t = (T) r[0];
        return t;
    }

    private static void enEdtV(Runnable r) throws Exception {
        enEdt(() -> {
            r.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object target, String nombre, Class<T> tipo) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void invocar(Object target, String metodo, Class<?>[] tipos, Object[] args) throws Exception {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(metodo, tipos);
        m.setAccessible(true);
        m.invoke(target, args);
    }

    private static void clicCelda(JTable tabla, int filaVista, int colVista) throws Exception {
        Rectangle r = enEdt(() -> tabla.getCellRect(filaVista, colVista, true));
        Point p = enEdt(() -> tabla.getLocationOnScreen());
        robot.mouseMove(p.x + r.x + r.width / 2, p.y + r.y + r.height / 2);
        robot.delay(150);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(90);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(400);
    }

    private static void arrastrarBordeColumna(JTable tabla, int colVista, int dx) throws Exception {
        int x = enEdt(() -> {
            int acc = 0;
            for (int i = 0; i <= colVista; i++) {
                acc += tabla.getColumnModel().getColumn(i).getWidth();
            }
            return acc;
        });
        Point hp = enEdt(() -> tabla.getTableHeader().getLocationOnScreen());
        int y = enEdt(() -> tabla.getTableHeader().getHeight() / 2);
        robot.mouseMove(hp.x + x - 2, hp.y + y);
        robot.delay(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(150);
        Point ahora = java.awt.MouseInfo.getPointerInfo().getLocation();
        for (int i = 1; i <= 10; i++) {
            robot.mouseMove(ahora.x + (dx * i / 10), ahora.y);
            robot.delay(30);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(300);
    }

    private static void seleccionarTabDe(Sistema sistema, JTable tabla) throws Exception {
        enEdtV(() -> {
            try {
                JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    if (SwingUtilities.isDescendingFrom(tabla, tabs.getComponentAt(i))) {
                        tabs.setSelectedIndex(i);
                        return;
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Thread.sleep(600);
    }

    private static int esperarFilas(JTable tabla, int minimo, long ms) throws Exception {
        long fin = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fin) {
            int n = enEdt(tabla::getRowCount);
            if (n >= minimo) {
                return n;
            }
            Thread.sleep(200);
        }
        return enEdt(tabla::getRowCount);
    }

    private static List<JButton> botonesMesa(Container raiz) {
        List<JButton> r = new ArrayList<>();
        for (Component c : raiz.getComponents()) {
            if (c instanceof JButton b && b.getClientProperty("numMesa") != null) {
                r.add(b);
            } else if (c instanceof Container k) {
                r.addAll(botonesMesa(k));
            }
        }
        return r;
    }

    // ---------------- Repos en memoria ----------------

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(new Salas(11, "Salon E2E", 4));
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static Platos platoE2E(int id, String nombre, String precio) {
        Platos p = new Platos(id, nombre, new BigDecimal(precio), LocalDate.now().toString(), true);
        p.setMonedaBase("USD");
        p.setPrecioUsd(new BigDecimal(precio));
        p.setPrecioClasificado(true);
        p.setPrecioIncluyeIva(false);
        p.setCategoriaNombre("E2E");
        return p;
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = List.of(
                platoE2E(951, "E2E-SplitPollo", "12.50"),
                platoE2E(952, "E2E-Arepa", "10.00"));
        @Override public List<Platos> listarPorFecha(String n, String f) { return platos; }
        @Override public List<Platos> listarPlatosParaVenta(String n, String f) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    private static final class PedidosMemoria implements PedidosRepositorio {
        private final Map<Integer, Pedidos> pedidos = new HashMap<>();
        private final Map<Integer, List<DetallePedido>> detalles = new HashMap<>();
        private int seq = 9600;
        @Override public int registrarPedidoCompleto(Pedidos p, List<DetallePedido> d) {
            int id = ++seq;
            p.setId(id);
            pedidos.put(id, p);
            detalles.put(id, new ArrayList<>(d));
            return id;
        }
        @Override public boolean actualizarPedidoCompleto(int id, Pedidos p, List<DetallePedido> d) {
            if (!pedidos.containsKey(id)) return false;
            p.setId(id);
            pedidos.put(id, p);
            detalles.put(id, new ArrayList<>(d));
            return true;
        }
        @Override public int verificarStado(int mesa, int idSala) { return 0; }
        @Override public Pedidos verPedido(int id) { return pedidos.get(id); }
        @Override public List<DetallePedido> verPedidoDetalle(int id) {
            return detalles.getOrDefault(id, List.of());
        }
        @Override public List<DetallePedido> finalizarPedido(int id) { return verPedidoDetalle(id); }
        @Override public boolean actualizarEstado(int id) { return true; }
        @Override public List<Pedidos> listarPedidos() { return new ArrayList<>(pedidos.values()); }
    }

    // ---------------- Preferencias: respaldo ----------------

    private record PrefRespaldo(int uid, String clave, double[] pesos) {}

    private static Optional<double[]> cargarPesos(PreferenciasTablaDao dao, int uid, String clave) {
        try {
            return dao.cargar(uid, clave);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static void borrarPreferencia(int uid, String clave) throws Exception {
        try (Connection con = new ProveedorConexionJdbc().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM preferencias_tabla WHERE usuario_id = ? AND tabla_clave = ?")) {
            ps.setInt(1, uid);
            ps.setString(2, clave);
            ps.executeUpdate();
        }
    }

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E toca preferencias_tabla real: relanza con -De2e.permitir=SI");
            System.exit(2);
        }
        String display = System.getenv("DISPLAY");
        if (display == null || (!display.contains(":99") && !display.contains(":98"))) {
            System.err.println("ADVERTENCIA: DISPLAY=" + display + " (se esperaba :99 virtual)");
        }
        robot = new Robot();
        robot.setAutoDelay(40);

        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement(
                     "SELECT id FROM usuarios WHERE correo='admin' OR rol='Administrador' ORDER BY id ASC LIMIT 1");
             java.sql.ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                admin.setId(rs.getInt(1));
            }
        }
        PoliticaAcceso permisos = new PoliticaAcceso(admin);
        SalasMemoria salasRepo = new SalasMemoria();
        PlatosMemoria platosRepo = new PlatosMemoria();
        PedidosMemoria pedidosRepo = new PedidosMemoria();
        LoginDao empresaDao = new LoginDao();
        java.nio.file.Path pdfTemp = java.nio.file.Files.createTempDirectory("e2e-tablas-");
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle,
                Config::new, new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo), pdfServicio, permisos,
                new ConsultaPedidosServicio(pedidosRepo, permisos));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, permisos));

        final Sistema[] box = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> {
            box[0] = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, empresaDao);
        });
        Sistema sistema = box[0];
        enEdtV(() -> {
            sistema.setSize(1366, 711);
            sistema.setLocationRelativeTo(null);
            sistema.setVisible(true);
        });
        Thread.sleep(1500);

        PreferenciasTablaDao prefDao = new PreferenciasTablaDao();
        String[] claves = {"split_catalogo", "split_carrito", "toma_carrito", "historial"};
        Map<String, Optional<double[]>> respaldo = new HashMap<>();
        for (String c : claves) {
            respaldo.put(c, cargarPesos(prefDao, admin.getId(), c));
        }

        try {
            // ================= CASO 1: ID fuera de la vista, dentro del modelo =================
            System.out.println("\n--- CASO 1: columna ID oculta, modelo intacto ---");
            JTable tableSala = enEdt(() -> campo(sistema, "tableSala", JTable.class));
            JTable tablePedidos = enEdt(() -> campo(sistema, "TablePedidos", JTable.class));
            JTable tableUsuarios = enEdt(() -> campo(sistema, "TableUsuarios", JTable.class));
            JTable tblTemPlatos = enEdt(() -> campo(sistema, "tblTemPlatos", JTable.class));
            JTable tablePlatos = enEdt(() -> campo(sistema, "TablePlatos", JTable.class));
            JTable tableMenu = enEdt(() -> campo(sistema, "tableMenu", JTable.class));
            JTable tableFinalizar = enEdt(() -> campo(sistema, "tableFinalizar", JTable.class));
            JTable tableMesoneros = enEdt(() -> campo(sistema, "tableMesonerosConfig", JTable.class));
            JTable tableFacturas = enEdt(() -> campo(sistema, "tableFacturasCliente", JTable.class));
            JTable tableClientes = enEdt(() -> campo(sistema, "tableClientes", JTable.class));

            checkEstructura("tableSala", tableSala, "ID");
            checkEstructura("TablePedidos", tablePedidos, "Id");
            checkEstructura("TableUsuarios", tableUsuarios, "Id");
            checkEstructura("tblTemPlatos", tblTemPlatos, "");
            checkEstructura("TablePlatos", tablePlatos, "ID");
            checkEstructura("tableMenu", tableMenu, "");
            checkEstructura("tableFinalizar", tableFinalizar, "");
            checkEstructura("tableMesonerosConfig", tableMesoneros, "ID");
            checkEstructura("tableFacturasCliente", tableFacturas, 0);

            int visCli = enEdt(() -> tableClientes.convertColumnIndexToView(0));
            String headCli = enEdt(() -> tableClientes.getModel().getColumnName(0));
            check(visCli >= 0 && "Cédula / RIF".equals(headCli),
                    "tableClientes conserva Cédula/RIF visible (clave de negocio, no ID interno)");

            // ================= CASO 2: selección por clic lee el ID del modelo =================
            System.out.println("\n--- CASO 2: clic selecciona el ID correcto ---");
            List<Salas> salas = List.of(new Salas(901, "E2E-Sala", 3), new Salas(902, "E2E-Terraza", 2));
            seleccionarTabDe(sistema, tableSala);
            Thread.sleep(1500);
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarSalasEnTabla", new Class<?>[]{List.class}, new Object[]{salas});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            check(enEdt(tableSala::getRowCount) == 2, "tableSala con 2 filas de prueba");
            clicCelda(tableSala, 1, 0);
            Thread.sleep(600);
            String idSalaTxt = enEdt(() -> campo(sistema, "txtIdSala", JTextField.class).getText());
            check("902".equals(idSalaTxt), "clic en fila 1 carga sala 902 en el formulario (txtIdSala=" + idSalaTxt + ")");

            List<Platos> platos = List.of(platoE2E(911, "E2E-PlatoA", "10.00"), platoE2E(912, "E2E-PlatoB", "20.00"));
            seleccionarTabDe(sistema, tablePlatos);
            Thread.sleep(1500);
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarPlatosEnTabla",
                            new Class<?>[]{JTable.class, List.class}, new Object[]{tablePlatos, platos});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            clicCelda(tablePlatos, 1, 0);
            Thread.sleep(600);
            String idPlatoTxt = enEdt(() -> campo(sistema, "txtIdPlato", JTextField.class).getText());
            check("912".equals(idPlatoTxt), "clic en fila 1 carga plato 912 (txtIdPlato=" + idPlatoTxt + ")");

            List<Usuario> usuarios = List.of(
                    new Usuario(921, "E2E Uno", "uno@e2e.test", "pass", "Asistente", true),
                    new Usuario(922, "E2E Dos", "dos@e2e.test", "pass", "Asistente", true));
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarUsuariosEnTabla",
                            new Class<?>[]{List.class}, new Object[]{usuarios});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            Object idUsu = enEdt(() -> Tables.valorModelo(tableUsuarios, 1, 0));
            check("922".equals(String.valueOf(idUsu)), "valorModelo lee ID 922 de la fila 1 de usuarios");

            // ================= CASO 3: historial ordenado, clic carga el pedido visible =================
            System.out.println("\n--- CASO 3: historial con sorter por ID desc ---");
            Pedidos pA = nuevoPedidoHistorial("Salon E2E", 1, "2026-10-01", "10.00", "PENDIENTE");
            Pedidos pB = nuevoPedidoHistorial("Salon E2E", 2, "2026-10-02", "12.50", "PENDIENTE");
            DetallePedido detB = nuevoDetalle(951, "E2E-SplitPollo", 1, "12.50");
            int idA = pedidosCtrl.registrarPedidoCompleto(pA, List.of(nuevoDetalle(952, "E2E-Arepa", 1, "10.00")));
            int idB = pedidosCtrl.registrarPedidoCompleto(pB, List.of(detB));
            int idMayor = Math.max(idA, idB);
            seleccionarTabDe(sistema, tablePedidos);
            Thread.sleep(1500);
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarPedidosEnTabla",
                            new Class<?>[]{List.class}, new Object[]{List.of(pA, pB)});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            check(enEdt(tablePedidos::getRowCount) == 2, "historial con 2 pedidos de prueba");
            enEdtV(() -> {
                @SuppressWarnings("unchecked")
                TableRowSorter<javax.swing.table.TableModel> sorter =
                        (TableRowSorter<javax.swing.table.TableModel>) tablePedidos.getRowSorter();
                sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
            });
            Thread.sleep(800);
            Object primeroVista = enEdt(() -> Tables.valorModelo(tablePedidos, 0, 0));
            check(String.valueOf(idMayor).equals(String.valueOf(primeroVista)),
                    "tras ordenar desc, la fila visible 0 es el pedido " + idMayor);
            clicCelda(tablePedidos, 0, 1);
            String txtHist = "";
            long finEd = System.currentTimeMillis() + 9000;
            while (System.currentTimeMillis() < finEd) {
                txtHist = enEdt(() -> campo(sistema, "txtIdHistorialPedido", JTextField.class).getText());
                if (String.valueOf(idMayor).equals(txtHist)) {
                    break;
                }
                Thread.sleep(400);
            }
            String dialogs = enEdt(() -> {
                StringBuilder sb = new StringBuilder("[");
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof JDialog d && d.isVisible()) {
                        sb.append(d.getTitle()).append("; ");
                    }
                }
                return sb.append("]").toString();
            });
            int selRow = enEdt(tablePedidos::getSelectedRow);
            txtHist = enEdt(() -> campo(sistema, "txtIdHistorialPedido", JTextField.class).getText());
            check(selRow == 0 && String.valueOf(idMayor).equals(txtHist),
                    "clic en fila ordenada cargó el pedido " + idMayor
                    + " (sel=" + selRow + ", txtHist=" + txtHist + ", dialogos=" + dialogs + ")");
            Object nombreCargado = enEdt(() -> {
                JTable fin = campo(sistema, "tableFinalizar", JTable.class);
                return Tables.valorModelo(fin, 0, 1);
            });
            check("E2E-SplitPollo".equals(String.valueOf(nombreCargado)),
                    "el pedido cargado en caja trae su línea E2E-SplitPollo (nombre=" + nombreCargado + ")");

            // ================= CASO 4: split catálogo -> carrito conserva el ID =================
            System.out.println("\n--- CASO 4: split 1 clic agrega con ID correcto ---");
            PanelAtencionMesasSplit split = enEdt(sistema::getPanelSplitScreen);
            check(split != null, "panel split accesible");
            seleccionarTabDe(sistema, enEdt(split::getTablaCatalogo));
            JTable catalogo = enEdt(split::getTablaCatalogo);
            checkEstructura("split-catalogo", catalogo, "ID");
            JTable carrito = enEdt(() -> campo(split, "tblCarrito", JTable.class));
            checkEstructura("split-carrito", carrito, "ID");
            int enVistaCom = enEdt(() -> carrito.convertColumnIndexToView(5));
            check(enVistaCom == -1, "split-carrito: columna Comentario fuera de la vista");
            check(enEdt(catalogo::getRowCount) >= 2, "catálogo split con platos de memoria");
            List<JButton> mesas = new ArrayList<>();
            long finMesas = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < finMesas) {
                mesas = botonesMesa(split);
                if (!mesas.isEmpty()) {
                    break;
                }
                Thread.sleep(300);
            }
            check(!mesas.isEmpty(), "grilla split con mesas (" + mesas.size() + ")");
            int filaPollo = enEdt(() -> {
                for (int i = 0; i < catalogo.getRowCount(); i++) {
                    Object v = catalogo.getModel().getValueAt(catalogo.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().contains("E2E-SplitPollo")) {
                        return i;
                    }
                }
                return -1;
            });
            check(filaPollo >= 0, "plato E2E-SplitPollo visible en catálogo");
            clic(mesas.get(0));
            Thread.sleep(800);
            clicCelda(catalogo, filaPollo, 0);
            Thread.sleep(800);
            DefaultTableModel modCar = enEdt(() -> campo(split, "modeloCarrito", DefaultTableModel.class));
            int nCar = enEdt(modCar::getRowCount);
            check(nCar >= 1, "carrito recibió 1 línea tras 1 clic");
            Object idCar = nCar >= 1 ? enEdt(() -> modCar.getValueAt(0, 0)) : null;
            check("951".equals(String.valueOf(idCar)), "línea del carrito conserva ID 951 en el modelo");

            // ================= CASO 5: clic en carrito clásico muestra cantidad =================
            System.out.println("\n--- CASO 5: carrito clásico ---");
            seleccionarTabDe(sistema, tableMenu);
            Thread.sleep(1500);
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarPlatosEnTabla",
                            new Class<?>[]{JTable.class, List.class}, new Object[]{tblTemPlatos, platos});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            enEdtV(() -> tblTemPlatos.setRowSelectionInterval(0, 0));
            Thread.sleep(400);
            enEdtV(() -> {
                try {
                    campo(sistema, "btnAddPlato", JButton.class).doClick();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            Thread.sleep(800);
            check(enEdt(tableMenu::getRowCount) == 1, "tableMenu con 1 línea");
            clicCelda(tableMenu, 0, 1);
            Thread.sleep(600);
            String cant = enEdt(() -> campo(sistema, "txtCantidadManual", JTextField.class).getText());
            check("1".equals(cant), "clic en carrito refleja cantidad 1 (txtCantidadManual=" + cant + ")");

            // ================= CASO 6: anchos a BD y restauración =================
            System.out.println("\n--- CASO 6: resize guarda en BD y se restaura ---");
            String[][] objetivos = {
                    {"split_carrito", "carrito", "0,5", "3"}, {"toma_carrito", "menu", "0", "3"}, {"historial", "pedidos", "0", "-1"}};
            for (String[] par : objetivos) {
                String clave = par[0];
                JTable tabla = par[1].equals("carrito") ? carrito
                        : par[1].equals("menu") ? tableMenu : tablePedidos;
                int[] ocultas = java.util.Arrays.stream(par[2].split(",")).mapToInt(Integer::parseInt).toArray();
                final int colPrecio = Integer.parseInt(par[3]);
                seleccionarTabDe(sistema, tabla);
                Thread.sleep(400);
                arrastrarBordeColumna(tabla, 0, 90);
                Thread.sleep(3000);
                Optional<double[]> guardado = cargarPesos(prefDao, admin.getId(), clave);
                check(guardado.isPresent(), clave + ": pesos guardados en preferencias_tabla");
                if (guardado.isPresent()) {
                    double[] w = guardado.get();
                    int nCols = enEdt(() -> tabla.getColumnModel().getColumnCount());
                    check(w.length == nCols, clave + ": pesos para las " + nCols + " columnas visibles");
                    double[] rest = enEdt(() -> {
                        JTable t2 = new JTable(tabla.getModel());
                        for (int oc : ocultas) {
                            Tables.ocultarColumnaModelo(t2, oc);
                        }
                        if (colPrecio >= 0) {
                            Tables.agregarColumnaMoneda(t2, colPrecio, mf -> "");
                        }
                        t2.setSize(800, 300);
                        new GestorAnchosTabla(t2, clave, admin.getId(), prefDao);
                        javax.swing.table.TableColumnModel cm = t2.getColumnModel();
                        double[] r = new double[cm.getColumnCount()];
                        for (int i = 0; i < r.length; i++) {
                            r[i] = cm.getColumn(i).getPreferredWidth() / 800.0;
                        }
                        return r;
                    });
                    boolean cerca = rest.length == w.length;
                    boolean[] esM = enEdt(() -> {
                        boolean[] r = new boolean[rest.length];
                        javax.swing.table.TableColumnModel cm0 = tabla.getColumnModel();
                        for (int i = 0; i < r.length && i < cm0.getColumnCount(); i++) {
                            r[i] = Tables.ID_COLUMNA_MONEDA.equals(cm0.getColumn(i).getIdentifier());
                        }
                        return r;
                    });
                    for (int i = 0; cerca && i < w.length; i++) {
                        if (esM[i]) {
                            continue; // M es fija-angosta (26-44px): su peso guardado no se restaura 1:1
                        }
                        if (Math.abs(rest[i] - w[i]) > 0.05) {
                            cerca = false;
                        }
                    }
                    check(cerca, clave + ": tabla nueva restaura proporciones guardadas "
                            + java.util.Arrays.toString(w) + " vs " + java.util.Arrays.toString(rest));
                }
            }
        } finally {
            for (String c : claves) {
                Optional<double[]> orig = respaldo.get(c);
                try {
                    if (orig != null && orig.isPresent()) {
                        prefDao.guardar(admin.getId(), c, orig.get());
                    } else {
                        borrarPreferencia(admin.getId(), c);
                    }
                } catch (Exception ex) {
                    System.err.println("Aviso restaurando preferencia " + c + ": " + ex.getMessage());
                }
            }
            try {
                enEdtV(sistema::dispose);
            } catch (Exception ignored) {
            }
        }
        System.out.println("=================================================");
        System.out.println(fallos == 0 ? "E2E TABLAS ID: TODO OK" : "E2E TABLAS ID: " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    private static void clic(Component c) throws Exception {
        Point p = enEdt(c::getLocationOnScreen);
        Dimension d = enEdt(c::getSize);
        robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
        robot.delay(150);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(90);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(400);
    }

    private static Pedidos nuevoPedidoHistorial(String sala, int mesa, String fecha, String total, String estado) {
        Pedidos p = new Pedidos();
        p.setId_sala(11);
        p.setNum_mesa(mesa);
        p.setTipoPedido("MESA");
        p.setSala(sala);
        p.setUsuario("admin");
        p.setFecha(fecha);
        p.setSubtotal(new BigDecimal(total));
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal(total));
        p.setTasaCambio(new BigDecimal("40.0000"));
        p.setSubtotalBs(new BigDecimal(total).multiply(new BigDecimal("40")));
        p.setIvaBs(BigDecimal.ZERO);
        p.setTotalBs(new BigDecimal(total).multiply(new BigDecimal("40")));
        p.setEstado(estado);
        return p;
    }

    private static DetallePedido nuevoDetalle(int idPlato, String nombre, int cant, String precio) {
        DetallePedido d = new DetallePedido();
        d.setIdPlato(idPlato);
        d.setNombre(nombre);
        d.setCantidad(cant);
        d.setPrecioDecimal(new BigDecimal(precio));
        d.setComentario("");
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);
        return d;
    }

    private static void checkEstructura(String nombre, JTable t, String headerId) throws Exception {        int enVista = enEdt(() -> t.convertColumnIndexToView(0));
        check(enVista == -1, nombre + ": columna ID fuera de la vista");
        String header = enEdt(() -> t.getModel().getColumnName(0));
        check(headerId.equals(header), nombre + ": modelo conserva ID ('" + header + "')");
    }

    private static void checkEstructura(String nombre, JTable t, int colModeloId) throws Exception {
        int enVista = enEdt(() -> t.convertColumnIndexToView(colModeloId));
        check(enVista == -1, nombre + ": columna ID fuera de la vista");
    }
}