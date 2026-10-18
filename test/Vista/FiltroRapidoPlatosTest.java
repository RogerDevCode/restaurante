package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.*;

/**
 * E2E en pantalla virtual (Xvfb DISPLAY=:99): {@code ./e2e_virtual.sh Vista.FiltroRapidoPlatosE2E}
 *
 * Verifica:
 * 1. Catálogo de Platos (Admin): con 'Auto-limpiar' activo, al hacer clic en un plato
 *    filtrado, el filtro se vacía a "", la tabla muestra todos los platos y el plato
 *    elegido queda seleccionado y visible.
 * 2. Toma de pedidos Split: con 'Auto-limpiar' activo, al agregar un plato filtrado,
 *    el filtro se vacía a "", el catálogo muestra todos los platos y el plato queda seleccionado.
 * 3. Con 'Auto-limpiar' desactivado, el texto del filtro se conserva tras seleccionar/agregar.
 * 4. Sincronización bidireccional entre los selectores de Auto-limpiar.
 */
public final class FiltroRapidoPlatosTest {

    @org.junit.Test
    public void ejecutarE2E() throws Exception {
        if (!"SI".equalsIgnoreCase(System.getProperty("e2e.permitir"))) {
            org.junit.Assume.assumeTrue("Requiere entorno Xvfb virtual (-De2e.permitir=SI). Se omite en unitarias estándar.", false);
            return;
        }
        main(new String[0]);
    }

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

    @FunctionalInterface
    private interface AccionEdt {
        void ejecutar() throws Exception;
    }

    private static void enEdtV(AccionEdt r) throws Exception {
        enEdt(() -> {
            r.ejecutar();
            return null;
        });
    }

    private static void esperar(Callable<Boolean> cond, long timeoutMs, String msg) throws Exception {
        long fin = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < fin) {
            Boolean ok = enEdt(cond);
            if (Boolean.TRUE.equals(ok)) {
                return;
            }
            Thread.sleep(50);
        }
        check(false, msg);
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object obj, String nombre, Class<T> tipo) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(nombre);
                f.setAccessible(true);
                return (T) f.get(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

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
        private final List<Platos> platos = new ArrayList<>(List.of(
                platoE2E(101, "Pollo Asado", "12.50", "456.25", "USD"),
                platoE2E(102, "Pollo Frito", "10.00", "365.00", "USD"),
                platoE2E(103, "Carne Mechada", "15.00", "547.50", "USD"),
                platoE2E(104, "Ensalada César", "8.00", "292.00", "USD")));

        @Override public List<Platos> listarPorFecha(String n, String f) {
            if (n == null || n.trim().isEmpty()) return platos;
            String q = n.trim().toLowerCase();
            return platos.stream().filter(p -> p.getNombre().toLowerCase().contains(q)).toList();
        }
        @Override public List<Platos> listarPlatosParaVenta(String n, String f) { return listarPorFecha(n, f); }
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
        if (!"SI".equalsIgnoreCase(System.getProperty("e2e.permitir"))) {
            System.err.println("Este test requiere -De2e.permitir=SI");
            System.exit(2);
        }

        System.out.println("--- E2E FILTRO RAPIDO (AUTO-LIMPIAR) ---");

        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);
        LoginDao empresaDao = new LoginDao();
        java.nio.file.Path pdfTemp = java.nio.file.Files.createTempDirectory("e2e-filtro-");
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
            sistema.setSize(1280, 720);
            sistema.setLocationRelativeTo(null);
            sistema.setVisible(true);
        });
        Thread.sleep(1200);

        try {
            // =========================================================================
            // CASO 1: Catálogo Admin - Auto-limpiar activo (defecto)
            // =========================================================================
            System.out.println("\n--- CASO 1: Catálogo Admin con Auto-limpiar activo ---");
            JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);
            enEdtV(() -> {
                int idxPlatos = tabs.indexOfTab("Platos");
                if (idxPlatos >= 0) tabs.setSelectedIndex(idxPlatos);
            });

            JTextField txtBuscarAdmin = campo(sistema, "txtBuscarAdminPlatos", JTextField.class);
            JCheckBox chkAdmin = campo(sistema, "chkFiltroRapidoAdminPlatos", JCheckBox.class);
            JTable tablePlatos = campo(sistema, "TablePlatos", JTable.class);

            check(txtBuscarAdmin != null, "Campo txtBuscarAdminPlatos presente");
            check(chkAdmin != null, "Selector chkFiltroRapidoAdminPlatos presente");
            check(chkAdmin != null && chkAdmin.isSelected(), "chkFiltroRapidoAdminPlatos marcado por defecto");

            // Filtrar por "Pollo"
            enEdtV(() -> {
                txtBuscarAdmin.setText("Pollo");
                java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("ListarPlatos", JTable.class, String.class);
                m.setAccessible(true);
                m.invoke(sistema, tablePlatos, "Pollo");
            });
            esperar(() -> tablePlatos.getRowCount() == 2, 3000, "Filtro 'Pollo' muestra 2 platos");
            check(tablePlatos.getRowCount() == 2, "Filtro 'Pollo' muestra 2 platos (actual: " + tablePlatos.getRowCount() + ")");

            // Clic en la fila 1 ("Pollo Frito", ID 102)
            enEdtV(() -> {
                tablePlatos.setRowSelectionInterval(1, 1);
                java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("cargarPlatoEnFormulario", int.class);
                m.setAccessible(true);
                m.invoke(sistema, 1);
            });

            esperar(() -> tablePlatos.getRowCount() == 4, 3000, "La tabla ahora muestra todos los platos (4 filas)");
            check("".equals(txtBuscarAdmin.getText()), "El filtro de búsqueda se limpió automáticamente a vacío");
            check(tablePlatos.getRowCount() == 4, "La tabla ahora muestra todos los platos (4 filas)");
            int filaSel = enEdt(tablePlatos::getSelectedRow);
            check(filaSel >= 0, "Hay una fila seleccionada tras la recarga (fila " + filaSel + ")");
            Object idSel = enEdt(() -> Tables.valorModelo(tablePlatos, filaSel, 0));
            check("102".equals(String.valueOf(idSel)), "El plato seleccionado sigue siendo Pollo Frito (ID 102)");

            // =========================================================================
            // CASO 2: Catálogo Admin - Auto-limpiar desactivado
            // =========================================================================
            System.out.println("\n--- CASO 2: Catálogo Admin con Auto-limpiar desactivado ---");
            enEdtV(() -> chkAdmin.setSelected(false));

            enEdtV(() -> {
                txtBuscarAdmin.setText("Carne");
                java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("ListarPlatos", JTable.class, String.class);
                m.setAccessible(true);
                m.invoke(sistema, tablePlatos, "Carne");
            });
            esperar(() -> tablePlatos.getRowCount() == 1, 3000, "Filtro 'Carne' muestra 1 plato");
            check(tablePlatos.getRowCount() == 1, "Filtro 'Carne' muestra 1 plato");

            enEdtV(() -> {
                tablePlatos.setRowSelectionInterval(0, 0);
                java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("cargarPlatoEnFormulario", int.class);
                m.setAccessible(true);
                m.invoke(sistema, 0);
            });
            Thread.sleep(300);

            check("Carne".equals(txtBuscarAdmin.getText()), "Con auto-limpiar desactivado, el texto del filtro se conserva");
            check(tablePlatos.getRowCount() == 1, "La tabla conserva el filtro (1 fila)");

            // Reactivar para las siguientes pruebas
            enEdtV(() -> chkAdmin.setSelected(true));

            // =========================================================================
            // CASO 3: Toma Split - Auto-limpiar activo
            // =========================================================================
            System.out.println("\n--- CASO 3: Toma Split con Auto-limpiar activo ---");
            PanelAtencionMesasSplit split = enEdt(sistema::getPanelSplitScreen);
            check(split != null, "Panel Split disponible");

            if (split != null) {
                JTextField txtBuscarSplit = campo(split, "txtBuscarPlato", JTextField.class);
                JCheckBox chkSplit = campo(split, "chkFiltroRapidoSplit", JCheckBox.class);
                JTable tblCatalogoSplit = campo(split, "tblCatalogoPlatos", JTable.class);

                check(txtBuscarSplit != null, "Campo búsqueda de Split presente");
                check(chkSplit != null, "Selector Auto-limpiar en Split presente");
                check(chkSplit != null && chkSplit.isSelected(), "chkSplit sincronizado en true");

                // Seleccionar mesa 1 para poder comandar
                enEdtV(() -> split.seleccionarMesa(1, "M1"));

                // Filtrar en split por "César"
                enEdtV(() -> {
                    txtBuscarSplit.setText("César");
                    java.lang.reflect.Method m = PanelAtencionMesasSplit.class.getDeclaredMethod("filtrarCatalogoPlatos", String.class);
                    m.setAccessible(true);
                    m.invoke(split, "César");
                });
                esperar(() -> tblCatalogoSplit.getRowCount() == 1, 3000, "Catálogo Split filtrado muestra 1 plato");
                check(tblCatalogoSplit.getRowCount() == 1, "Catálogo Split filtrado muestra 1 plato");

                // Agregar el plato seleccionado al carrito
                enEdtV(() -> {
                    tblCatalogoSplit.setRowSelectionInterval(0, 0);
                    java.lang.reflect.Method m = PanelAtencionMesasSplit.class.getDeclaredMethod("agregarPlatoSeleccionadoAlCarrito");
                    m.setAccessible(true);
                    m.invoke(split);
                });
                esperar(() -> tblCatalogoSplit.getRowCount() == 4, 3000, "En Split el catálogo muestra todos los platos (4 filas)");

                check("".equals(txtBuscarSplit.getText()), "En Split el filtro se limpió automáticamente");
                check(tblCatalogoSplit.getRowCount() == 4, "En Split el catálogo muestra todos los platos (4 filas)");
                int selSplit = enEdt(tblCatalogoSplit::getSelectedRow);
                check(selSplit >= 0, "Hay plato seleccionado en catálogo Split");
                Object idSplitSel = enEdt(() -> Tables.valorModelo(tblCatalogoSplit, selSplit, 0));
                check("104".equals(String.valueOf(idSplitSel)), "El plato seleccionado en Split es Ensalada César (ID 104)");
            }

            // =========================================================================
            // CASO 4: Sincronización entre selectores
            // =========================================================================
            System.out.println("\n--- CASO 4: Sincronización entre checkboxes ---");
            JCheckBox chkToma = campo(sistema, "chkFiltroRapido", JCheckBox.class);
            check(chkToma != null, "chkFiltroRapido en toma clásica presente");

            enEdtV(() -> sistema.guardarPreferenciaFiltroRapido(false));
            check(!chkAdmin.isSelected(), "chkAdmin actualizado a false");
            check(chkToma != null && !chkToma.isSelected(), "chkToma actualizado a false");

            enEdtV(() -> sistema.guardarPreferenciaFiltroRapido(true));
            check(chkAdmin.isSelected(), "chkAdmin actualizado a true");
            check(chkToma != null && chkToma.isSelected(), "chkToma actualizado a true");

        } finally {
            enEdtV(sistema::dispose);
        }

        System.out.println("\n=================================================");
        if (fallos == 0) {
            System.out.println("E2E FILTRO RAPIDO: TODO OK");
            System.exit(0);
        } else {
            System.err.println("E2E FILTRO RAPIDO FALLO: " + fallos + " errores.");
            System.exit(1);
        }
    }
}
