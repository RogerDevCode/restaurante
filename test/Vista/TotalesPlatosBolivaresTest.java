package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * E2E en pantalla virtual (Xvfb DISPLAY=:99): {@code ./e2e_virtual.sh Vista.TotalesPlatosBolivaresE2E}
 *
 * Prueba de verificación integral para la visualización dual de totales
 * (Bolívares y Dólares) cuando intervienen platos con moneda principal en Bolívares (VES).
 */
public class TotalesPlatosBolivaresTest {

    public static void main(String[] args) {
        if (!"SI".equalsIgnoreCase(System.getProperty("e2e.permitir"))) {
            System.err.println("Este test requiere -De2e.permitir=SI");
            System.exit(2);
        }

        System.out.println("=== INICIO PRUEBA: TOTALES PLATOS BOLIVARES Y DUALIDAD MONETARIA ===");
        try {
            testTotalPagarPlatosVESYDolar();
            testTableFinalizarDualTotals();
            testDashboardTopPlatosCuatroColumnas();
            testPanelSplitTotalesSimetricos();

            System.out.println("\n=================================================");
            System.out.println("PRUEBA TOTALES PLATOS BOLIVARES: TODO OK");
            System.out.println("=================================================");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("\nFALLO EN PRUEBA TOTALES PLATOS BOLIVARES:");
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void ok(String mensaje) {
        System.out.println("OK   " + mensaje);
    }

    private static void asegurar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError("FALLO: " + mensaje);
        }
    }

    private static Sistema crearSistema(Usuario u, BigDecimal tasa, BigDecimal iva) throws Exception {
        PoliticaAcceso rbac = new PoliticaAcceso(u);
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasMemoria(), rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(new PlatosMemoria(), rbac));
        Path tempDir = Files.createTempDirectory("e2e-totales-");
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(id -> null, id -> null, Config::new,
                new GeneradorPdfPedido(tempDir), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(new PedidosMemoria()), pdfServicio, rbac,
                new ConsultaPedidosServicio(new PedidosMemoria(), rbac));
        LoginDao empresaDao = new LoginDao();

        Sistema[] holder = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> {
            Sistema s = new Sistema(u, salasCtrl, platosCtrl, pedidosCtrl, empresaDao);
            s.conf.setTasaDolar(tasa);
            s.conf.setIvaPorcentaje(iva);
            holder[0] = s;
        });
        return holder[0];
    }

    /**
     * CASO 1: En la toma de pedidos clásica (tableMenu / totalMenu),
     * un plato con moneda base VES debe calcular y mostrar ambos montos:
     * USD $ XX.XX  |  Bs. YY.YY, sin mostrar el monto de bolívares como si fueran dólares.
     */
    private static void testTotalPagarPlatosVESYDolar() throws Exception {
        System.out.println("\n--- CASO 1: TotalPagar con platos en VES y USD ---");
        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        BigDecimal tasa = new BigDecimal("36.5000");
        BigDecimal iva = new BigDecimal("16.00");
        Sistema s = crearSistema(admin, tasa, iva);

        try {
            JTable tableMenu = campo(s, "tableMenu", JTable.class);
            JLabel totalMenu = campo(s, "totalMenu", JLabel.class);

            // Plato 1: Sopa Criolla en VES (500 Bs., Exento de IVA)
            Platos pVes = new Platos(201, "Sopa Criolla", new BigDecimal("500.00"), LocalDate.now().toString());
            pVes.setMonedaBase("VES");
            pVes.setPrecioVes(new BigDecimal("500.00"));
            pVes.setAplicaIva(false);
            pVes.setPrecioIncluyeIva(false);
            pVes.setPrecioClasificado(true);

            // Registrar plato en mapa del sistema
            @SuppressWarnings("unchecked")
            Map<Integer, Platos> map = (Map<Integer, Platos>) campo(s, "platosPorId", Map.class);
            map.put(201, pVes);

            // Agregar fila al carrito tableMenu: Cantidad 1, Precio 500.00, Subtotal 500.00
            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel m = (DefaultTableModel) tableMenu.getModel();
                m.setRowCount(0);
                m.addRow(new Object[]{201, "Sopa Criolla", 1, "500.00", "500.00", "", "Exento"});
                invocar(s, "TotalPagar", new Class<?>[]{JTable.class, JLabel.class}, new Object[]{tableMenu, totalMenu});
            });

            String texto1 = totalMenu.getText();
            System.out.println("Total mostrado para plato VES (500 Bs.): " + texto1);
            asegurar(texto1.contains("Bs.") && texto1.contains("500.00"),
                    "El total debe mostrar claramente los 500.00 Bs.");
            asegurar(texto1.contains("USD $") || texto1.contains("$"),
                    "El total debe mostrar también el equivalente en USD.");
            asegurar(!texto1.equals("$ 500.00"),
                    "ERROR CRITICO: 500 Bolívares no deben mostrarse como '$ 500.00'!");
            ok("Plato en VES muestra ambos montos (USD y Bs.) y no inventa $500.00");

            // Verificar formateo dual en celda usando formatearMontoCelda
            String formattedCelda = Sistema.formatearMontoCelda(new BigDecimal("500.00"), "VES", tasa);
            System.out.println("Formato de celda para plato VES en carrito: " + formattedCelda);
            asegurar(formattedCelda.contains("Bs.") && formattedCelda.contains("500.00") && formattedCelda.contains("$"),
                    "La celda de item de mesa debe discriminar Bs. y USD");
            ok("Celdas de items de mesa discriminan ambos montos (VES y USD)");

            // Plato 2: Jugo Natural en USD ($ 10.00, gravado con IVA)
            Platos pUsd = new Platos(202, "Jugo Natural", new BigDecimal("10.00"), LocalDate.now().toString());
            pUsd.setMonedaBase("USD");
            pUsd.setPrecioUsd(new BigDecimal("10.00"));
            pUsd.setAplicaIva(true);
            pUsd.setPrecioIncluyeIva(true);
            pUsd.setPrecioClasificado(true);
            map.put(202, pUsd);

            // Agregar ambos platos al carrito
            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel m = (DefaultTableModel) tableMenu.getModel();
                m.addRow(new Object[]{202, "Jugo Natural", 1, "10.00", "10.00", "", "IVA 16%"});
                invocar(s, "TotalPagar", new Class<?>[]{JTable.class, JLabel.class}, new Object[]{tableMenu, totalMenu});
            });

            String textoMixto = totalMenu.getText();
            System.out.println("Total mostrado para pedido mixto (VES + USD): " + textoMixto);
            asegurar(textoMixto.contains("USD $") && textoMixto.contains("Bs."),
                    "El pedido mixto debe mostrar ambos montos: USD y Bs.");
            ok("Pedido mixto (VES + USD) calcula y muestra ambos totales correctamente");

        } finally {
            SwingUtilities.invokeAndWait(s::dispose);
        }
    }

    /**
     * CASO 2: En la pantalla de Finalizar / Cobro (tableFinalizar),
     * la columna Sub-Total de cada plato debe mostrar ambos montos:
     * $ XX.XX (Bs. YY.YY).
     */
    private static void testTableFinalizarDualTotals() throws Exception {
        System.out.println("\n--- CASO 2: tableFinalizar muestra dualidad en Sub-Total ---");
        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        BigDecimal tasa = new BigDecimal("36.5000");
        BigDecimal iva = new BigDecimal("16.00");
        Sistema s = crearSistema(admin, tasa, iva);

        try {
            JTable tableFinalizar = campo(s, "tableFinalizar", JTable.class);
            JLabel totalFinalizar = campo(s, "totalFinalizar", JLabel.class);

            Pedidos ped = new Pedidos();
            ped.setId(99);
            ped.setSala("Principal");
            ped.setNum_mesa(1);
            ped.setFecha(LocalDate.now().toString());
            ped.setTasaCambio(tasa);
            ped.setTotalDecimal(new BigDecimal("13.70"));
            ped.setTotalBs(new BigDecimal("500.00"));

            DetallePedido detVes = new DetallePedido();
            detVes.setId(1);
            detVes.setNombre("Sopa Criolla");
            detVes.setCantidad(1);
            detVes.setPrecioDecimal(new BigDecimal("500.00"));
            detVes.setMonedaBase("VES");
            detVes.setAplicaIva(false);
            detVes.setPrecioIncluyeIva(false);
            detVes.setIvaPorcentaje(BigDecimal.ZERO);

            SwingUtilities.invokeAndWait(() -> {
                invocar(s, "mostrarPedidoEnPantalla",
                        new Class<?>[]{Pedidos.class, List.class},
                        new Object[]{ped, Collections.singletonList(detVes)});
            });

            asegurar(tableFinalizar.getRowCount() == 1, "tableFinalizar debe tener 1 fila");
            Object subtotalCelda = tableFinalizar.getValueAt(0, 4);
            System.out.println("Sub-total celda en tableFinalizar: " + subtotalCelda);
            asegurar(subtotalCelda != null && subtotalCelda.toString().contains("$") && subtotalCelda.toString().contains("Bs."),
                    "El subtotal del plato en tableFinalizar debe mostrar ambos montos ($ y Bs.)");

            String totFin = totalFinalizar.getText();
            System.out.println("totalFinalizar: " + totFin);
            asegurar(totFin.contains("USD $") && totFin.contains("Bs."),
                    "totalFinalizar debe mostrar ambos totales (USD y Bs.)");
            ok("tableFinalizar muestra montos en USD y Bs. tanto en líneas como en el total general");

        } finally {
            SwingUtilities.invokeAndWait(s::dispose);
        }
    }

    /**
     * CASO 3: En el Dashboard, tableTopPlatos debe tener 4 columnas:
     * Plato, Cant., Total ($), Total (Bs.).
     */
    private static void testDashboardTopPlatosCuatroColumnas() throws Exception {
        System.out.println("\n--- CASO 3: Dashboard tableTopPlatos tiene 4 columnas con USD y Bs. ---");
        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        Sistema s = crearSistema(admin, new BigDecimal("36.5000"), new BigDecimal("16.00"));

        try {
            JTable tableTopPlatos = campo(s, "tableTopPlatos", JTable.class);
            asegurar(tableTopPlatos != null, "tableTopPlatos no puede ser nula");
            asegurar(tableTopPlatos.getColumnCount() == 4,
                    "tableTopPlatos debe tener 4 columnas (Plato, Cant., Total ($), Total (Bs.))");
            asegurar("Total ($)".equals(tableTopPlatos.getColumnName(2)), "Columna 2 debe ser Total ($)");
            asegurar("Total (Bs.)".equals(tableTopPlatos.getColumnName(3)), "Columna 3 debe ser Total (Bs.)");

            // Simular carga de datos
            EstadisticasDashboard stats = new EstadisticasDashboard();
            stats.getTopPlatos().add(new EstadisticasDashboard.ItemEstadistica(
                    "Pabellón Criollo", 10, new BigDecimal("136.99"), new BigDecimal("5000.00")));

            DashboardSwingWorker.DatosDashboard datos = new DashboardSwingWorker.DatosDashboard(stats, Collections.emptyList());
            SwingUtilities.invokeAndWait(() -> {
                invocar(s, "mostrarDashboardEnPantalla",
                        new Class<?>[]{DashboardSwingWorker.DatosDashboard.class},
                        new Object[]{datos});
            });

            asegurar(tableTopPlatos.getRowCount() == 1, "Debe tener 1 fila");
            String colUsd = tableTopPlatos.getValueAt(0, 2).toString();
            String colBs = tableTopPlatos.getValueAt(0, 3).toString();
            System.out.println("Top Plato - Total ($): " + colUsd + ", Total (Bs.): " + colBs);

            asegurar(colUsd.contains("$ 136.99"), "Columna Total ($) debe mostrar el monto en USD");
            asegurar(colBs.contains("Bs. 5000.00"), "Columna Total (Bs.) debe mostrar el monto en Bs.");
            ok("Dashboard tableTopPlatos muestra ambas columnas y montos de USD y Bolívares");

        } finally {
            SwingUtilities.invokeAndWait(s::dispose);
        }
    }

    /**
     * CASO 4: En PanelAtencionMesasSplit, los labels de total deben ser simétricos
     * y claros en ambas monedas sin reclasificar Bolívares como nota al pie secundaria.
     */
    private static void testPanelSplitTotalesSimetricos() throws Exception {
        System.out.println("\n--- CASO 4: PanelAtencionMesasSplit totales simétricos ---");
        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        Sistema s = crearSistema(admin, new BigDecimal("36.5000"), new BigDecimal("16.00"));

        try {
            PanelAtencionMesasSplit split = s.getPanelSplitScreen();
            asegurar(split != null, "panelSplitScreen no puede ser nulo");

            JLabel lblTotalUsd = campo(split, "lblTotalUsd", JLabel.class);
            JLabel lblTotalBs = campo(split, "lblTotalBs", JLabel.class);

            asegurar(lblTotalUsd != null && lblTotalBs != null, "Labels de total deben existir");
            System.out.println("Split vacío: " + lblTotalUsd.getText() + lblTotalBs.getText());
            asegurar(lblTotalUsd.getText().contains("USD $ 0.00"), "Debe mostrar USD $ 0.00");
            asegurar(lblTotalBs.getText().contains("Bs. 0.00"), "Debe mostrar Bs. 0.00");
            asegurar(lblTotalBs.getFont().isBold(), "lblTotalBs debe ser negrita para igual prominencia visual");
            ok("PanelAtencionMesasSplit muestra ambos totales simétricos y en negrita");

        } finally {
            SwingUtilities.invokeAndWait(s::dispose);
        }
    }

    // =========================================================================
    // Helpers de reflexión y dobles de prueba
    // =========================================================================

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

    private static Object invocar(Object obj, String metodo, Class<?>[] tipos, Object[] args) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(metodo, tipos);
                m.setAccessible(true);
                return m.invoke(obj, args);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException(new NoSuchMethodException(metodo));
    }

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(new Salas(1, "Principal", 10));
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = new ArrayList<>();
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
}
