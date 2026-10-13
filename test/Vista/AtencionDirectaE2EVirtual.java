package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.awt.Point;
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
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.*;

/**
 * Test E2E en pantalla virtual (Xvfb DISPLAY=:99) para la Atención Directa de Mesas.
 * Verifica de forma completa y aislada:
 * - Opción B: Navegación horizontal de salas con pestañas y prefijos automáticos (P-1, T-2).
 * - Opción A: Cabecera interactiva y asignación rápida por código en la toma de pedidos.
 * - Opción C: Pantalla dividida (Split-Screen) con mesas, catálogo y comanda simultáneos.
 * - Conmutador de modos y compatibilidad 100% con el menú clásico.
 */
public final class AtencionDirectaE2EVirtual {

    private static int fallos = 0;

    private static void check(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("OK   " + mensaje);
        } else {
            System.out.println("FALLO " + mensaje);
            fallos++;
        }
    }

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(
                new Salas(1, "Salon Principal", 4),
                new Salas(2, "Terraza", 3),
                new Salas(3, "Barra", 2, "BARRA")
        );
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = List.of(
                new Platos(1, "Pollo a la Brasa", new BigDecimal("12.50"), LocalDate.now().toString(), true),
                new Platos(2, "Papas Fritas", new BigDecimal("3.00"), LocalDate.now().toString(), false),
                new Platos(3, "Refresco", new BigDecimal("1.50"), LocalDate.now().toString(), false)
        );
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
        private int siguienteId = 100;
        private final List<Pedidos> pedidos = new ArrayList<>();
        private final List<DetallePedido> detalles = new ArrayList<>();

        @Override
        public int registrarPedidoCompleto(Pedidos p, List<DetallePedido> ds) {
            p.setId(++siguienteId);
            p.setEstado("PENDIENTE");
            pedidos.add(p);
            detalles.addAll(ds);
            return p.getId();
        }

        @Override
        public boolean actualizarPedidoCompleto(int id, Pedidos p, List<DetallePedido> ds) {
            return true;
        }

        @Override
        public int verificarStado(int mesa, int sala) {
            for (Pedidos p : pedidos) {
                if (p.getId_sala() == sala && p.getNum_mesa() == mesa && "PENDIENTE".equalsIgnoreCase(p.getEstado())) {
                    return p.getId();
                }
            }
            return 0;
        }

        @Override
        public Pedidos verPedido(int id) {
            for (Pedidos p : pedidos) {
                if (p.getId() == id) return p;
            }
            throw new IllegalArgumentException("Pedido no encontrado: " + id);
        }

        @Override
        public List<DetallePedido> verPedidoDetalle(int id) {
            return List.copyOf(detalles);
        }

        @Override
        public List<DetallePedido> finalizarPedido(int id) {
            return verPedidoDetalle(id);
        }

        @Override
        public boolean actualizarEstado(int id) {
            for (Pedidos p : pedidos) {
                if (p.getId() == id) {
                    p.setEstado("FINALIZADO");
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<Pedidos> listarPedidos() {
            return List.copyOf(pedidos);
        }

        @Override
        public Map<Integer, Integer> contarMesasOcupadasPorSala() {
            Map<Integer, Integer> conteo = new java.util.HashMap<>();
            for (Pedidos p : pedidos) {
                if ("PENDIENTE".equalsIgnoreCase(p.getEstado())) {
                    conteo.put(p.getId_sala(), conteo.getOrDefault(p.getId_sala(), 0) + 1);
                }
            }
            return conteo;
        }
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

    public static void main(String[] args) throws Exception {
        System.out.println("=== INICIO E2E ATENCIÓN DIRECTA DE MESAS (VIRTUAL) ===");

        Path pdfTemp = Files.createTempDirectory("atencion-directa-e2e-");
        Usuario admin = new Usuario(1, "Admin E2E", "admin-e2e", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);

        SalasMemoria salasRepo = new SalasMemoria();
        PlatosMemoria platosRepo = new PlatosMemoria();
        PedidosMemoria repo = new PedidosMemoria();

        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, permisos));
        PedidoPdfServicio pdf = new PedidoPdfServicio(repo::verPedido, repo::verPedidoDetalle,
                Config::new, new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(new PedidoServicio(repo), pdf, permisos,
                new ConsultaPedidosServicio(repo, permisos));

        LoginDao empresa = new LoginDao() {
            @Override public Config datosEmpresa() { return new Config(); }
            @Override public List<Usuario> ListarUsuarios() { return List.of(); }
        };

        Sistema sistema = edt(() -> {
            Sistema s = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, empresa);
            s.setSize(1366, 711);
            s.setLocationRelativeTo(null);
            s.setVisible(true);
            return s;
        });

        // Esperar renderizado y carga de datos en EDT
        Thread.sleep(1500);

        JTabbedPane tabs = (JTabbedPane) campo(sistema, "jTabbedPane1");
        JPanel panelTabsSalas = (JPanel) campo(sistema, "panelTabsSalasDirecto");
        JButton btnSelectorCabecera = (JButton) campo(sistema, "btnSelectorMesaCabecera");
        JTextField txtCodigoMesa = (JTextField) campo(sistema, "txtCodigoMesaDirecto");
        JTextField txtTempIdSala = (JTextField) campo(sistema, "txtTempIdSala");
        JTextField txtTempNumMesa = (JTextField) campo(sistema, "txtTempNumMesa");
        JComboBox<?> cbModo = (JComboBox<?>) campo(sistema, "cbModoAtencion");
        JButton btnAtencionDirecta = (JButton) campo(sistema, "btnAtencionDirecta");

        check(panelTabsSalas != null, "Barra de pestañas de sala (Opción B) inicializada");
        check(btnSelectorCabecera != null, "Selector rápido en cabecera (Opción A) inicializado");
        check(txtCodigoMesa != null, "Campo de código de mesa en cabecera (Opción A) inicializado");
        check(cbModo != null, "Conmutador de modos disponible");
        check(btnAtencionDirecta != null, "Botón de menú lateral para atención directa disponible");

        // ----------------------------------------------------
        // TEST 1: Opción B - Pestañas de Sala y grilla con prefijos
        // ----------------------------------------------------
        System.out.println("\n--- Verificando Opción B (Mapa + Pestañas de Sala) ---");
        edt(() -> {
            cbModo.setSelectedIndex(0); // Opción B
            btnAtencionDirecta.doClick();
            return null;
        });
        Thread.sleep(800);

        int tabMesasIdx = tabs.indexOfTab("Mesas");
        check(tabs.getSelectedIndex() == tabMesasIdx, "Acceso directo navegó a la vista 'Mesas'");

        int numTabsSalas = edt(panelTabsSalas::getComponentCount);
        check(numTabsSalas >= 3, "Se muestran las pestañas superiores para las salas (encontradas: " + numTabsSalas + ")");

        // Cambiar a Terraza (segunda sala, id 2) haciendo clic en su pestaña
        edt(() -> {
            JButton tabTerraza = (JButton) panelTabsSalas.getComponent(1);
            check(tabTerraza.getText().contains("T"), "Pestaña de Terraza contiene el prefijo 'T': " + tabTerraza.getText());
            tabTerraza.doClick();
            return null;
        });
        Thread.sleep(800);

        JPanel panelMesasGrid = (JPanel) campo(sistema, "PanelMesas");
        int numBotonesMesas = edt(panelMesasGrid::getComponentCount);
        check(numBotonesMesas == 3, "Grilla de Terraza muestra 3 mesas");

        JButton btnMesa1 = (JButton) edt(() -> panelMesasGrid.getComponent(0));
        check(btnMesa1.getText().contains("T-1"), "Botón de la mesa 1 muestra código prefijado 'T-1'");

        // Abrir pedido para T-1
        edt(() -> {
            btnMesa1.doClick();
            return null;
        });
        Thread.sleep(600);

        int tabPlatosIdx = tabs.indexOfTab("Platos");
        check(tabs.getSelectedIndex() == tabPlatosIdx, "Clic en mesa T-1 abre directamente la toma de pedidos (Tab Platos)");
        check("2".equals(txtTempIdSala.getText()), "ID de sala en pedido configurado a 2 (Terraza)");
        check("1".equals(txtTempNumMesa.getText()), "Número de mesa configurado a 1");
        check(btnSelectorCabecera.getText().contains("T-1"), "Cabecera muestra badge 'T-1': " + btnSelectorCabecera.getText());

        // ----------------------------------------------------
        // TEST 2: Opción A - Selector Rápido y Digitación directa
        // ----------------------------------------------------
        System.out.println("\n--- Verificando Opción A (Selector Rápido y Digitación) ---");
        // Digitar P-3 en el campo directo y presionar Enter
        edt(() -> {
            txtCodigoMesa.setText("P3");
            txtCodigoMesa.postActionEvent();
            return null;
        });
        Thread.sleep(600);

        check("1".equals(txtTempIdSala.getText()), "Digitación rápida 'P3' resolvió a Sala 1 (Salon Principal)");
        check("3".equals(txtTempNumMesa.getText()), "Digitación rápida resolvió a Mesa 3");
        check(btnSelectorCabecera.getText().contains("P-3"), "Cabecera actualizada a 'P-3': " + btnSelectorCabecera.getText());

        // Digitar B-1 (Barra)
        edt(() -> {
            txtCodigoMesa.setText("B1");
            txtCodigoMesa.postActionEvent();
            return null;
        });
        Thread.sleep(600);

        check("3".equals(txtTempIdSala.getText()), "Digitación rápida 'B1' resolvió a Sala 3 (Barra)");
        check("1".equals(txtTempNumMesa.getText()), "Digitación rápida resolvió a Puesto 1 de Barra");
        check(btnSelectorCabecera.getText().contains("B-1"), "Cabecera actualizada a 'B-1': " + btnSelectorCabecera.getText());

        // ----------------------------------------------------
        // TEST 3: Opción C - Split-Screen / Pantalla Dividida
        // ----------------------------------------------------
        System.out.println("\n--- Verificando Opción C (Split-Screen) mediante cambio directo de combobox ---");
        edt(() -> {
            cbModo.setSelectedIndex(2); // Al cambiar el combobox a Opción C, debe activar Split-Screen directamente
            return null;
        });
        Thread.sleep(800);

        int tabSplitIdx = tabs.indexOfTab("Atención Directa (Split)");
        check(tabSplitIdx != -1, "Pestaña de Atención Directa (Split) existe");
        check(tabs.getSelectedIndex() == tabSplitIdx, "Cambiar combobox a Opción C activó inmediatamente la pestaña Split-Screen");

        PanelAtencionMesasSplit splitPanel = (PanelAtencionMesasSplit) campo(sistema, "panelSplitScreen");
        check(splitPanel != null, "Instancia de PanelAtencionMesasSplit accesible");

        // Cambiar combobox de vuelta a Opción B
        edt(() -> {
            cbModo.setSelectedIndex(0); // Regresar a Opción B directamente
            return null;
        });
        Thread.sleep(800);
        check(tabs.getSelectedIndex() == tabMesasIdx, "Cambiar combobox a Opción B regresó inmediatamente a la vista 'Mesas'");

        // ----------------------------------------------------
        // TEST 4: Flujo Clásico 100% compatible
        // ----------------------------------------------------
        System.out.println("\n--- Verificando Compatibilidad 100% con Flujo Clásico ---");
        JButton btnSalaMenu = (JButton) campo(sistema, "btnSala");
        edt(() -> {
            btnSalaMenu.doClick();
            return null;
        });
        Thread.sleep(600);

        int tabSalasIdx = tabs.indexOfTab("Salas");
        check(tabs.getSelectedIndex() == tabSalasIdx, "Clic en botón lateral 'Salas' abre la vista clásica de salas");

        // Cerrar ventana de prueba
        edt(() -> {
            sistema.dispose();
            return null;
        });

        System.out.println("\n========================================================");
        if (fallos == 0) {
            System.out.println("RESULTADO: TODOS LOS TESTS E2E VIRTUALES PASARON EXITOSAMENTE (0 fallos).");
            System.exit(0);
        } else {
            System.err.println("RESULTADO: " + fallos + " FALLOS DETECTADOS EN TEST E2E VIRTUAL.");
            System.exit(1);
        }
    }
}
