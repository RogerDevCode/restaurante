package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.awt.Container;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * E2E en pantalla virtual (Xvfb DISPLAY=:99): {@code ./e2e_virtual.sh Vista.PreciosVisiblesE2E}
 *
 * Verifica en todas las JTables con precio unitario que:
 * 1. La columna muestra el texto completo del precio más largo (nada cortado).
 * 2. La columna se puede redimensionar (header + columna habilitados).
 *
 * Usa los renderers reales y mide el ancho preferido de cada celda visible.
 * No toca la BD (repos en memoria). Exige {@code -De2e.permitir=SI} por convención E2E.
 */
public final class PreciosVisiblesE2E {

    private static int fallos = 0;

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

    /** Ancho que necesita la celda (renderer real + 8px de aire). */
    private static int anchoRequerido(JTable tabla, int filaVista, int colVista) {
        TableCellRenderer cr = tabla.getCellRenderer(filaVista, colVista);
        Object v = tabla.getValueAt(filaVista, colVista);
        Component c = cr.getTableCellRendererComponent(tabla, v, false, false, filaVista, colVista);
        return c.getPreferredSize().width + 8;
    }

    private static void checkColumnaPrecio(String nombre, JTable tabla, int colModeloPrecio) throws Exception {
        int colVista = enEdt(() -> tabla.convertColumnIndexToView(colModeloPrecio));
        check(colVista >= 0, nombre + ": columna de precio presente en la vista");
        if (colVista < 0) {
            return;
        }
        final int vista = colVista;
        boolean redim = enEdt(() -> tabla.getColumnModel().getColumn(vista).getResizable()
                && tabla.getTableHeader().getResizingAllowed());
        check(redim, nombre + ": columna de precio redimensionable");
        int filas = enEdt(tabla::getRowCount);
        check(filas > 0, nombre + ": tiene filas para medir (" + filas + ")");
        int anchoCol = enEdt(() -> tabla.getColumnModel().getColumn(vista).getWidth());
        int maxReq = 0;
        for (int f = 0; f < filas; f++) {
            final int fila = f;
            maxReq = Math.max(maxReq, enEdt(() -> anchoRequerido(tabla, fila, vista)));
        }
        check(anchoCol >= maxReq,
                nombre + ": ancho " + anchoCol + "px cubre el precio más largo (" + maxReq + "px)");
    }

    // ---------------- Repos en memoria ----------------

    private static Platos platoE2E(int id, String nombre, String usd, String ves, String moneda) {
        Platos p = new Platos(id, nombre, new BigDecimal(usd), LocalDate.now().toString(), true);
        p.setMonedaBase(moneda);
        p.setPrecioUsd(new BigDecimal(usd));
        p.setPrecioVes(new BigDecimal(ves));
        p.setPrecioClasificado(true);
        p.setPrecioIncluyeIva(false);
        p.setCategoriaNombre("E2E");
        return p;
    }

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(new Salas(11, "Salon E2E", 4));
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = List.of(
                platoE2E(971, "E2E-GrandeUSD", "1234567.89", "98765432.10", "USD"),
                platoE2E(972, "E2E-GrandeVES", "999.99", "7654321.50", "VES"));
        @Override public List<Platos> listarPorFecha(String n, String f) { return platos; }
        @Override public List<Platos> listarPlatosParaVenta(String n, String f) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    private static final class PedidosMemoria implements PedidosRepositorio {
        private final Map<Integer, Pedidos> pedidos = new HashMap<>();
        private final Map<Integer, List<DetallePedido>> detalles = new HashMap<>();
        private int seq = 9700;
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

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E visual: relanza con -De2e.permitir=SI");
            System.exit(2);
        }
        String display = System.getenv("DISPLAY");
        if (display == null || (!display.contains(":99") && !display.contains(":98"))) {
            System.err.println("ADVERTENCIA: DISPLAY=" + display + " (se esperaba :99 virtual)");
        }

        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);
        LoginDao empresaDao = new LoginDao();
        java.nio.file.Path pdfTemp = java.nio.file.Files.createTempDirectory("e2e-precios-");
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(id -> null, id -> null, Config::new,
                new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(new PedidosMemoria()), pdfServicio, permisos,
                new ConsultaPedidosServicio(new PedidosMemoria(), permisos));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasMemoria(), permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(new PlatosMemoria(), permisos));

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

        try {
            JTable tblTemPlatos = enEdt(() -> campo(sistema, "tblTemPlatos", JTable.class));
            JTable tablePlatos = enEdt(() -> campo(sistema, "TablePlatos", JTable.class));
            JTable tableMenu = enEdt(() -> campo(sistema, "tableMenu", JTable.class));
            JTable tableFinalizar = enEdt(() -> campo(sistema, "tableFinalizar", JTable.class));
            PanelAtencionMesasSplit split = enEdt(sistema::getPanelSplitScreen);
            JTable catalogo = enEdt(split::getTablaCatalogo);
            JTable carrito = enEdt(() -> campo(split, "tblCarrito", JTable.class));

            List<Platos> grandes = List.of(
                    platoE2E(971, "E2E-GrandeUSD", "1234567.89", "98765432.10", "USD"),
                    platoE2E(972, "E2E-GrandeVES", "999.99", "7654321.50", "VES"));
            enEdtV(() -> {
                try {
                    invocar(sistema, "mostrarPlatosEnTabla",
                            new Class<?>[]{JTable.class, List.class}, new Object[]{tblTemPlatos, grandes});
                    invocar(sistema, "mostrarPlatosEnTabla",
                            new Class<?>[]{JTable.class, List.class}, new Object[]{tablePlatos, grandes});
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            enEdtV(() -> {
                DefaultTableModel mMenu = (DefaultTableModel) tableMenu.getModel();
                mMenu.setRowCount(0);
                mMenu.addRow(new Object[]{971, "E2E-GrandeUSD", 1, "$ 1234567.89", "$ 1234567.89", ""});
                mMenu.addRow(new Object[]{972, "E2E-GrandeVES", 2, "Bs. 7654321.50", "Bs. 15308643.00", ""});
                DefaultTableModel mSplit = (DefaultTableModel) carrito.getModel();
                mSplit.setRowCount(0);
                mSplit.addRow(new Object[]{971, "E2E-GrandeUSD", 1, "$ 1234567.89", "$ 1234567.89", ""});
                mSplit.addRow(new Object[]{972, "E2E-GrandeVES", 2, "Bs. 7654321.50", "Bs. 15308643.00", ""});
                DefaultTableModel mFin = (DefaultTableModel) tableFinalizar.getModel();
                mFin.setRowCount(0);
                mFin.addRow(new Object[]{1, "E2E-GrandeUSD", 1, "$ 1234567.89 (Bs. 98765432.10)", "$ 1234567.89", ""});
                mFin.addRow(new Object[]{2, "E2E-GrandeVES", 1, "$ 999.99 (Bs. 7654321.50)", "$ 999.99", ""});
            });

            System.out.println("\n--- Precios visibles y redimensionables ---");
            checkColumnaPrecio("split-catalogo.Precio", catalogo, 2);
            checkColumnaPrecio("split-carrito.Precio", carrito, 3);
            checkColumnaPrecio("split-carrito.SubTotal", carrito, 4);
            checkColumnaPrecio("tableMenu.Precio", tableMenu, 3);
            checkColumnaPrecio("tableMenu.SubTotal", tableMenu, 4);
            checkColumnaPrecio("tableFinalizar.Precio", tableFinalizar, 3);
            checkColumnaPrecio("tableFinalizar.SubTotal", tableFinalizar, 4);
            checkColumnaPrecio("TablePlatos.PRECIO", tablePlatos, 2);
            checkColumnaPrecio("tblTemPlatos.Precio", tblTemPlatos, 2);
        } finally {
            try {
                enEdtV(sistema::dispose);
            } catch (Exception ignored) {
            }
        }
        System.out.println("=================================================");
        System.out.println(fallos == 0 ? "E2E PRECIOS: TODO OK" : "E2E PRECIOS: " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}