package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.*;

/** Prueba visual real de Pedido rápido sobre Xvfb con Robot (mouse y teclado propios). */
public final class PedidoRapidoE2EVirtual {
    private static final class SalasMemoria implements SalasRepositorio {
        @Override public List<Salas> listar() { return List.of(new Salas(1, "Sala demo", 4)); }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = List.of(
                new Platos(11, "Arepa de prueba", new BigDecimal("10.00"), LocalDate.now().toString(), true),
                new Platos(12, "Jugo exento", new BigDecimal("2.50"), LocalDate.now().toString(), false));
        @Override public List<Platos> listarPorFecha(String desde, String hasta) {
            String criterio = desde == null ? "" : desde.trim().toLowerCase(java.util.Locale.ROOT);
            if (criterio.isEmpty()) return platos;
            return platos.stream().filter(p -> p.getNombre().toLowerCase(java.util.Locale.ROOT).contains(criterio)).toList();
        }
        @Override public boolean registrar(Platos plato) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos plato) { return false; }
    }

    private static final class PedidosMemoria implements PedidosRepositorio {
        private int siguienteId = 70;
        private Pedidos pedido;
        private List<DetallePedido> detalles = List.of();
        @Override public int registrarPedidoCompleto(Pedidos p, List<DetallePedido> ds) {
            pedido = p;
            detalles = new ArrayList<>(ds);
            pedido.setId(++siguienteId);
            pedido.setSala("Pedido rápido");
            pedido.setEstado("PENDIENTE");
            return pedido.getId();
        }
        @Override public boolean actualizarPedidoCompleto(int id, Pedidos p, List<DetallePedido> ds) { return false; }
        @Override public int verificarStado(int mesa, int sala) { return 0; }
        @Override public Pedidos verPedido(int id) {
            if (pedido == null || pedido.getId() != id) throw new IllegalArgumentException("Pedido no encontrado");
            return pedido;
        }
        @Override public List<DetallePedido> verPedidoDetalle(int id) { return List.copyOf(detalles); }
        @Override public List<DetallePedido> finalizarPedido(int id) { return verPedidoDetalle(id); }
        @Override public boolean actualizarEstado(int id) { return false; }
        @Override public List<Pedidos> listarPedidos() { return pedido == null ? List.of() : List.of(pedido); }
        @Override public java.util.Map<Integer, Integer> contarMesasOcupadasPorSala() { return Collections.emptyMap(); }
    }

    private static <T> T edt(Callable<T> tarea) throws Exception {
        Object[] resultado = new Object[1];
        Throwable[] error = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { resultado[0] = tarea.call(); } catch (Throwable ex) { error[0] = ex; }
        });
        if (error[0] != null) throw new RuntimeException(error[0]);
        @SuppressWarnings("unchecked") T valor = (T) resultado[0];
        return valor;
    }

    private static Object campo(Sistema sistema, String nombre) throws Exception {
        var field = Sistema.class.getDeclaredField(nombre);
        field.setAccessible(true);
        return field.get(sistema);
    }

    private static void clic(Robot robot, Component componente) throws Exception {
        Point p = edt(componente::getLocationOnScreen);
        java.awt.Dimension d = edt(componente::getSize);
        robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
        robot.delay(100);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(70);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(250);
    }

    private static void escribir(Robot robot, String valor) {
        for (char c : valor.toCharArray()) {
            int key = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(c));
            robot.keyPress(key);
            robot.keyRelease(key);
            robot.delay(45);
        }
    }

    private static void esperar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
        System.out.println("OK   " + mensaje);
    }

    private static void esperarFilas(Robot robot, JTable tabla, int filas) throws Exception {
        long hasta = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < hasta) {
            if (edt(tabla::getRowCount) == filas) return;
            robot.delay(100);
        }
        throw new AssertionError("La tabla quedó con " + edt(tabla::getRowCount) + " filas; se esperaban " + filas);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path pdfTemp = Files.createTempDirectory("pedido-rapido-e2e-");
        Usuario admin = new Usuario(1, "Admin E2E", "admin-e2e", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);
        SalasControlador salas = new SalasControlador(new SalasServicio(new SalasMemoria(), permisos));
        PlatosControlador platos = new PlatosControlador(new PlatosServicio(new PlatosMemoria(), permisos));
        PedidosMemoria repo = new PedidosMemoria();
        PedidoPdfServicio pdf = new PedidoPdfServicio(repo::verPedido, repo::verPedidoDetalle,
                Config::new, new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidos = new PedidosControlador(new PedidoServicio(repo), pdf, permisos,
                new ConsultaPedidosServicio(repo, permisos));
        LoginDao empresa = new LoginDao() {
            private final Config config = configDemo();
            @Override public Config datosEmpresa() { return config; }
            @Override public List<Usuario> ListarUsuarios() { return List.of(); }
        };
        Sistema sistema = edt(() -> new Sistema(admin, salas, platos, pedidos, empresa));
        Robot robot = new Robot();
        robot.setAutoDelay(35);
        try {
            edt(() -> { sistema.setExtendedState(JFrame.MAXIMIZED_BOTH); sistema.setVisible(true); return null; });
            robot.delay(1800);

            JButton acceso = (JButton) campo(sistema, "btnPedidoRapido");
            clic(robot, acceso);
            JTabbedPane tabs = (JTabbedPane) campo(sistema, "jTabbedPane1");
            JTable menu = (JTable) campo(sistema, "tblTemPlatos");
            JTable carrito = (JTable) campo(sistema, "tableMenu");
            JTextField buscar = (JTextField) campo(sistema, "txtBuscarPlato");
            JButton registrar = (JButton) campo(sistema, "btnGenerarPedido");
            long hastaInicio = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < hastaInicio && edt(tabs::getSelectedIndex) != 3) {
                robot.delay(100);
            }
            esperar(edt(tabs::getSelectedIndex) == 3, "acceso lateral abre el formulario de Pedido rápido (actual=" + edt(tabs::getSelectedIndex) + ")");
            esperar(edt(() -> registrar.getText().equals("Cobrar y finalizar")), "acción principal indica el paso de cobro");
            esperarFilas(robot, menu, 2);

            clic(robot, registrar);
            esperar(repo.pedido == null, "carrito vacío no registra ni cobra pedidos");

            clic(robot, buscar);
            escribir(robot, "xyz");
            esperarFilas(robot, menu, 0);
            JButton limpiarBusqueda = (JButton) campo(sistema, "btnLimpiarBuscar");
            clic(robot, limpiarBusqueda);
            esperarFilas(robot, menu, 2);

            clic(robot, buscar);
            robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL);
            escribir(robot, "arepa");
            esperarFilas(robot, menu, 1);
            Rectangle celda = edt(() -> menu.getCellRect(0, 0, true));
            Point origen = edt(menu::getLocationOnScreen);
            robot.mouseMove(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.delay(200);
            clic(robot, (JButton) campo(sistema, "btnAddPlato"));
            esperar(edt(carrito::getRowCount) == 1, "producto gravado se añade al carrito");
            JButton mas = (JButton) campo(sistema, "btnMasCantidad");
            clic(robot, mas);
            esperar("2".equals(String.valueOf(edt(() -> carrito.getModel().getValueAt(carrito.convertRowIndexToModel(0), 2)))), "cantidad se incrementa desde el control rápido");
            celda = edt(() -> menu.getCellRect(0, 0, true));
            origen = edt(menu::getLocationOnScreen);
            robot.mouseMove(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.delay(250);
            esperar("3".equals(String.valueOf(edt(() -> carrito.getModel().getValueAt(carrito.convertRowIndexToModel(0), 2)))), "repetir un producto suma cantidad en la misma línea");

            clic(robot, buscar);
            robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL);
            escribir(robot, "jugo");
            esperarFilas(robot, menu, 1);
            celda = edt(() -> menu.getCellRect(0, 0, true));
            origen = edt(menu::getLocationOnScreen);
            robot.mouseMove(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.delay(200);
            clic(robot, (JButton) campo(sistema, "btnAddPlato"));
            esperar(edt(carrito::getRowCount) == 2, "segundo producto exento se combina con el gravado");
            celda = edt(() -> carrito.getCellRect(1, 1, true));
            origen = edt(carrito::getLocationOnScreen);
            robot.mouseMove(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.delay(150);
            clic(robot, (JButton) campo(sistema, "btnEliminarTempPlato"));
            esperar(edt(carrito::getRowCount) == 1, "eliminar línea actualiza carrito");

            clic(robot, buscar);
            robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL);
            escribir(robot, "jugo");
            esperarFilas(robot, menu, 1);
            celda = edt(() -> menu.getCellRect(0, 0, true));
            origen = edt(menu::getLocationOnScreen);
            robot.mouseMove(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.delay(200);
            clic(robot, (JButton) campo(sistema, "btnAddPlato"));
            clic(robot, registrar);
            long hasta = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < hasta && repo.pedido == null) robot.delay(100);
            esperar(repo.pedido != null && repo.pedido.esPedidoRapido(), "guardar crea pedido RAPIDO sin sala ni mesa");
            esperar(repo.detalles.size() == 2, "pedido combinado conserva dos productos");
            esperar(new BigDecimal("37.30").compareTo(repo.pedido.getTotalDecimal()) == 0,
                    "IVA se calcula solo sobre el producto gravado");
            hasta = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < hasta && edt(tabs::getSelectedIndex) != 4) robot.delay(100);
            esperar(edt(tabs::getSelectedIndex) == 4, "pedido rápido pasa directamente a la pantalla de pago");
            JTextField salaFinalizar = (JTextField) campo(sistema, "txtSalaFinalizar");
            JTextField mesaFinalizar = (JTextField) campo(sistema, "txtNumMesaFinalizar");
            esperar("Para llevar".equals(edt(salaFinalizar::getText)), "pantalla de pago identifica modalidad para llevar");
            esperar("—".equals(edt(mesaFinalizar::getText)), "pantalla de pago no presenta mesa ficticia");
            JTable tablaPagos = (JTable) campo(sistema, "tablaPagosFac");
            JButton btnAgregarPago = (JButton) campo(sistema, "btnAgregarPagoFac");
            JButton btnQuitarPago = (JButton) campo(sistema, "btnQuitarPagoFac");
            esperar(edt(tablaPagos::isShowing), "tabla de pagos se muestra para pedido rápido");
            esperar(edt(tablaPagos::getRowCount) >= 1, "tabla de pagos inicia con al menos 1 fila");
            esperar("EFECTIVO_BS".equals(edt(() -> String.valueOf(tablaPagos.getValueAt(0, 0)))),
                    "medio de pago por defecto es EFECTIVO_BS");
            esperar("VES".equals(edt(() -> String.valueOf(tablaPagos.getValueAt(0, 1)))),
                    "moneda por defecto es VES");
            clic(robot, btnAgregarPago);
            esperar(edt(tablaPagos::getRowCount) == 2, "botón + Medio agrega fila de pago");
            clic(robot, btnQuitarPago);
            esperar(edt(tablaPagos::getRowCount) == 1, "botón − Medio quita fila de pago");
        } finally {
            edt(() -> { sistema.dispose(); return null; });
            try (var files = Files.list(pdfTemp)) { files.forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} }); }
            Files.deleteIfExists(pdfTemp);
        }
    }

    private static Config configDemo() {
        Config config = new Config();
        config.setNombre("Restaurante E2E");
        config.setRuc("J-12345678-9");
        config.setTasaDolar(new BigDecimal("36.5000"));
        config.setIvaPorcentaje(new BigDecimal("16.00"));
        return config;
    }
}
