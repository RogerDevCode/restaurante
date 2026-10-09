package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.CalculoFiscalRecord;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.LoginDao;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas exhaustiva para cada vista/pantalla y pestaña de Sistema.java.
 *
 * Cubre:
 * - Roles y RBAC: Administrador vs Asistente en todos los componentes y acciones.
 * - Tab 0: Panel de Salas (generación de botones y navegación).
 * - Tab 1: Salas CRUD (validación, registro, listado y limpieza).
 * - Tab 2: Panel de Mesas (estados libres vs ocupadas, navegación condicionada por rol).
 * - Tab 3: Pedidos / Carrito (búsqueda, catálogo con precios enteros en USD y Bs,
 *          adición acumulativa, cálculo sin decimales y sin IVA preliminar, persistencia fiscal).
 * - Tab 4: Finalizar Pedido / Facturación (desglose fiscal con IVA, totales enteros en USD y Bs,
 *          finalización y emisión de factura PDF).
 * - Tab 5: Historial de Ventas (listado con montos bimonetarios y estados).
 * - Tab 6: Configuración de la Empresa (validaciones de tasa y rango de IVA [0.00-100.00%]).
 * - Tab 7: Usuarios (listado, validación de roles y alta).
 * - Tab 8: Catálogo de Platos CRUD (precios enteros, altas, bajas y modificaciones).
 */
public class SistemaTabsYVistasTest {

    private Usuario admin;
    private Usuario asistente;
    private SalasRepositorioFalso salasRepo;
    private PlatosRepositorioFalso platosRepo;
    private PedidosRepositorioTestDouble pedidosRepo;
    private LoginDaoFalso loginDaoFalso;

    private SalasControlador salasCtrlAdmin;
    private PlatosControlador platosCtrlAdmin;
    private PedidosControlador pedidosCtrlAdmin;

    private SalasControlador salasCtrlAsistente;
    private PlatosControlador platosCtrlAsistente;
    private PedidosControlador pedidosCtrlAsistente;

    private Path tempDir;

    private static java.util.Timer autoDismissTimer;

    @org.junit.BeforeClass
    public static void setupAutoDismiss() {
        autoDismissTimer = new java.util.Timer("modal-dismisser", true);
        autoDismissTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    for (java.awt.Window window : java.awt.Window.getWindows()) {
                        if (window instanceof javax.swing.JDialog dialog && dialog.isModal()) {
                            dialog.dispose();
                        }
                    }
                });
            }
        }, 50, 50);
    }

    @org.junit.AfterClass
    public static void teardownAutoDismiss() {
        if (autoDismissTimer != null) {
            autoDismissTimer.cancel();
        }
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sistema-vistas-test-");

        admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");
        asistente = new Usuario(2, "Mesero Juan", "mesero", "1234", "Asistente");

        salasRepo = new SalasRepositorioFalso();
        salasRepo.salas.add(new Salas(1, "Terraza", 5));
        salasRepo.salas.add(new Salas(2, "Salón VIP", 8));

        platosRepo = new PlatosRepositorioFalso();
        platosRepo.platos.add(new Platos(1, "Arepa Reina Pepiada", new BigDecimal("10.00"), LocalDate.now().toString()));
        platosRepo.platos.add(new Platos(2, "Jugo Natural", new BigDecimal("2.50"), LocalDate.now().toString()));

        pedidosRepo = new PedidosRepositorioTestDouble();
        loginDaoFalso = new LoginDaoFalso();

        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        PoliticaAcceso rbacAsistente = new PoliticaAcceso(asistente);

        salasCtrlAdmin = new SalasControlador(new SalasServicio(salasRepo, rbacAdmin));
        platosCtrlAdmin = new PlatosControlador(new PlatosServicio(platosRepo, rbacAdmin));

        GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(tempDir);
        PedidoPdfServicio pdfServicioAdmin = new PedidoPdfServicio(
                pedidosRepo::verPedido,
                pedidosRepo::verPedidoDetalle,
                () -> loginDaoFalso.datosEmpresa(),
                generadorPdf,
                f -> true
        );
        ConsultaPedidosServicio consultaServicioAdmin = new ConsultaPedidosServicio(pedidosRepo, rbacAdmin);
        pedidosCtrlAdmin = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicioAdmin,
                rbacAdmin,
                consultaServicioAdmin
        );

        salasCtrlAsistente = new SalasControlador(new SalasServicio(salasRepo, rbacAsistente));
        platosCtrlAsistente = new PlatosControlador(new PlatosServicio(platosRepo, rbacAsistente));
        pedidosCtrlAsistente = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicioAdmin,
                rbacAsistente,
                consultaServicioAdmin
        );
    }

    @After
    public void tearDown() {
        if (tempDir != null) {
            try {
                File[] files = tempDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
        }
    }

    private Sistema crearSistemaEnEdt(Usuario usuario, boolean esAdmin) throws Exception {
        AtomicReference<Sistema> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Sistema s = new Sistema(
                    usuario,
                    esAdmin ? salasCtrlAdmin : salasCtrlAsistente,
                    esAdmin ? platosCtrlAdmin : platosCtrlAsistente,
                    esAdmin ? pedidosCtrlAdmin : pedidosCtrlAsistente,
                    loginDaoFalso
            );
            ref.set(s);
        });
        return ref.get();
    }

    // =========================================================================
    // Helpers de Acceso Reflexivo
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object target, String nombre, Class<T> tipo) {
        try {
            Field f = target.getClass().getDeclaredField(nombre);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception ex) {
            throw new RuntimeException("Error accediendo a campo " + nombre, ex);
        }
    }

    private static void fijarCampo(Object target, String nombre, Object valor) {
        try {
            Field f = target.getClass().getDeclaredField(nombre);
            f.setAccessible(true);
            f.set(target, valor);
        } catch (Exception ex) {
            throw new RuntimeException("Error asignando campo " + nombre, ex);
        }
    }

    private static Object invocar(Object target, String nombreMetodo, Class<?>[] tipos, Object[] args) {
        try {
            Method m = target.getClass().getDeclaredMethod(nombreMetodo, tipos);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception ex) {
            throw new RuntimeException("Error invocando " + nombreMetodo, ex);
        }
    }

    // =========================================================================
    // 1. Roles y RBAC en Vistas y Pantallas
    // =========================================================================

    @Test
    public void testControlesActivosSegunRolAdministrador() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            assertTrue("btnConfig habilitado para Admin", campo(sistema, "btnConfig", JButton.class).isEnabled());
            assertTrue("btnUsuarios habilitado para Admin", campo(sistema, "btnUsuarios", JButton.class).isEnabled());
            assertTrue("btnPlatos habilitado para Admin", campo(sistema, "btnPlatos", JButton.class).isEnabled());
            assertTrue("btnRegistrarSala habilitado para Admin", campo(sistema, "btnRegistrarSala", JButton.class).isEnabled());
            assertTrue("btnActualizarConfig habilitado para Admin", campo(sistema, "btnActualizarConfig", JButton.class).isEnabled());
            assertTrue("btnGuardarPlato habilitado para Admin", campo(sistema, "btnGuardarPlato", JButton.class).isEnabled());
            assertEquals("Administrador", campo(sistema, "LabelVendedor", JLabel.class).getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testControlesDeshabilitadosSegunRolAsistente() throws Exception {
        Sistema sistema = crearSistemaEnEdt(asistente, false);
        try {
            assertFalse("btnConfig deshabilitado para Asistente", campo(sistema, "btnConfig", JButton.class).isEnabled());
            assertFalse("btnUsuarios deshabilitado para Asistente", campo(sistema, "btnUsuarios", JButton.class).isEnabled());
            assertFalse("btnPlatos deshabilitado para Asistente", campo(sistema, "btnPlatos", JButton.class).isEnabled());
            assertFalse("btnRegistrarSala deshabilitado para Asistente", campo(sistema, "btnRegistrarSala", JButton.class).isEnabled());
            assertFalse("btnActualizarConfig deshabilitado para Asistente", campo(sistema, "btnActualizarConfig", JButton.class).isEnabled());
            assertFalse("btnGuardarPlato deshabilitado para Asistente", campo(sistema, "btnGuardarPlato", JButton.class).isEnabled());
            assertFalse("txtNombrePlato deshabilitado para Asistente", campo(sistema, "txtNombrePlato", JTextField.class).isEnabled());
            assertFalse("txtPrecioPlato deshabilitado para Asistente", campo(sistema, "txtPrecioPlato", JTextField.class).isEnabled());
            assertEquals("Mesero Juan", campo(sistema, "LabelVendedor", JLabel.class).getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 2. Tab 0: Panel de Salas
    // =========================================================================

    @Test
    public void testTab0PanelSalasCargaBotonesYNavegaAMesas() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            JPanel panelSalas = campo(sistema, "PanelSalas", JPanel.class);

            // Esperar que el SwingWorker de salas complete en EDT
            for (int i = 0; i < 30 && panelSalas.getComponentCount() < 2; i++) {
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> { });
            }

            assertEquals(2, panelSalas.getComponentCount());
            JButton btn1 = (JButton) panelSalas.getComponent(0);
            JButton btn2 = (JButton) panelSalas.getComponent(1);
            assertTrue(btn1.getText().startsWith("Terraza"));
            assertTrue(btn1.getText().contains("ocupadas"));
            assertTrue(btn2.getText().startsWith("Salón VIP"));
            assertTrue(btn2.getText().contains("ocupadas"));

            // Simular click en la sala 1 para navegar a mesas (Tab 2)
            SwingUtilities.invokeAndWait(() -> btn1.getActionListeners()[0].actionPerformed(
                    new ActionEvent(btn1, ActionEvent.ACTION_PERFORMED, null)));

            JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);
            assertEquals("Debe navegar al Tab 2 (Mesas)", 2, tabs.getSelectedIndex());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 3. Tab 1: Salas CRUD y Limpieza
    // =========================================================================

    @Test
    public void testTab1SalasCrudYLimpiezaFormulario() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            List<Salas> listaSalas = Arrays.asList(
                    new Salas(10, "Patio", 6),
                    new Salas(11, "Barra", 12)
            );

            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarSalasEnTabla", new Class<?>[]{List.class}, new Object[]{listaSalas});
            });

            JTable tableSala = campo(sistema, "tableSala", JTable.class);
            assertEquals(2, tableSala.getRowCount());
            assertEquals("Patio", tableSala.getValueAt(0, 1));
            assertEquals(6, tableSala.getValueAt(0, 2));

            // Probar método de limpieza
            JTextField txtNombreSala = campo(sistema, "txtNombreSala", JTextField.class);
            JTextField txtMesas = campo(sistema, "txtMesas", JTextField.class);
            JTextField txtIdSala = campo(sistema, "txtIdSala", JTextField.class);

            SwingUtilities.invokeAndWait(() -> {
                txtNombreSala.setText("Temporal");
                txtMesas.setText("4");
                txtIdSala.setText("99");
                invocar(sistema, "LimpiarSala", new Class<?>[]{}, new Object[]{});
            });

            assertEquals("", txtNombreSala.getText());
            assertEquals("", txtMesas.getText());
            assertEquals("", txtIdSala.getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 4. Tab 2: Panel de Mesas (Libres y Ocupadas)
    // =========================================================================

    @Test
    public void testTab2PanelMesasLibresYOcupadasColoresYNavegacion() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Map<Integer, Integer> estadosMesas = new HashMap<>();
            estadosMesas.put(1, 0);   // Mesa 1 Libre (idPedido = 0)
            estadosMesas.put(2, 42);  // Mesa 2 Ocupada (idPedido = 42)

            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPanelMesas",
                        new Class<?>[]{int.class, int.class, Map.class},
                        new Object[]{1, 2, estadosMesas});
            });

            JPanel panelMesas = campo(sistema, "PanelMesas", JPanel.class);
            assertEquals(2, panelMesas.getComponentCount());
            JButton btnMesa1 = (JButton) panelMesas.getComponent(0);
            JButton btnMesa2 = (JButton) panelMesas.getComponent(1);

            assertEquals("MESA N°: 1", btnMesa1.getText());
            assertEquals("MESA N°: 2", btnMesa2.getText());

            // Mesa libre color verde (0, 102, 102), ocupada color rojo (255, 51, 51)
            assertEquals(new Color(0, 102, 102), btnMesa1.getBackground());
            assertEquals(new Color(255, 51, 51), btnMesa2.getBackground());

            // Click en mesa libre debe navegar a Tab 3 (Pedidos) y fijar identificadores
            SwingUtilities.invokeAndWait(() -> btnMesa1.getActionListeners()[0].actionPerformed(
                    new ActionEvent(btnMesa1, ActionEvent.ACTION_PERFORMED, null)));

            JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);
            assertEquals("Debe navegar a Tab 3 (Pedidos)", 3, tabs.getSelectedIndex());
            assertEquals("1", campo(sistema, "txtTempIdSala", JTextField.class).getText());
            assertEquals("1", campo(sistema, "txtTempNumMesa", JTextField.class).getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 5. Tab 3: Pedidos / Carrito (Precios Enteros, Acumulación, Sin IVA)
    // =========================================================================

    @Test
    public void testTab3CatalogoMuestraPreciosEnterosEnUsdYBs() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Config conf = campo(sistema, "conf", Config.class);
            conf.setTasaDolar(new BigDecimal("36.5000"));

            List<Platos> catalogo = Arrays.asList(
                    new Platos(1, "Arepa Reina Pepiada", new BigDecimal("10.00"), LocalDate.now().toString()),
                    new Platos(2, "Jugo Natural", new BigDecimal("2.50"), LocalDate.now().toString())
            );

            JTable tblTemPlatos = campo(sistema, "tblTemPlatos", JTable.class);
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPlatosEnTabla",
                        new Class<?>[]{JTable.class, List.class},
                        new Object[]{tblTemPlatos, catalogo});
            });

            assertEquals(2, tblTemPlatos.getRowCount());

            // En el catálogo y toma de pedidos, los precios se muestran en USD con 2 decimales
            assertEquals("Arepa Reina Pepiada", tblTemPlatos.getValueAt(0, 1));
            String precioStr1 = tblTemPlatos.getValueAt(0, 2).toString();
            assertEquals("$ 10.00", precioStr1);

            assertEquals("Jugo Natural", tblTemPlatos.getValueAt(1, 1));
            String precioStr2 = tblTemPlatos.getValueAt(1, 2).toString();
            assertEquals("$ 2.50", precioStr2);
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testTab3AgregarPlatosAlCarritoAcumulaYCalculaTotalSinIva() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Config conf = campo(sistema, "conf", Config.class);
            conf.setTasaDolar(new BigDecimal("36.5000"));
            conf.setIvaPorcentaje(new BigDecimal("16.00"));

            JTable tblTemPlatos = campo(sistema, "tblTemPlatos", JTable.class);
            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);
            JButton btnAddPlato = campo(sistema, "btnAddPlato", JButton.class);
            JLabel totalMenu = campo(sistema, "totalMenu", JLabel.class);

            // Poblar menú temporal con 1 plato de $10 (con 2 decimales)
            DefaultTableModel modeloPlatos = (DefaultTableModel) tblTemPlatos.getModel();
            modeloPlatos.setRowCount(0);
            modeloPlatos.addRow(new Object[]{101, "Pabellón Criollo", "$ 10.00"});

            SwingUtilities.invokeAndWait(() -> {
                tblTemPlatos.setRowSelectionInterval(0, 0);
                btnAddPlato.doClick();
            });

            assertEquals(1, tableMenu.getRowCount());
            assertEquals(101, tableMenu.getValueAt(0, 0));
            assertEquals("Pabellón Criollo", tableMenu.getValueAt(0, 1));
            assertEquals(1, tableMenu.getValueAt(0, 2));
            assertEquals("10.00", tableMenu.getValueAt(0, 4)); // Subtotal con 2 decimales

            // Agregar por segunda vez: debe incrementar cantidad a 2 y subtotal a 20.00
            SwingUtilities.invokeAndWait(() -> {
                tblTemPlatos.setRowSelectionInterval(0, 0);
                btnAddPlato.doClick();
            });

            assertEquals(1, tableMenu.getRowCount());
            assertEquals(2, tableMenu.getValueAt(0, 2)); // Cantidad = 2
            assertEquals("20.00", tableMenu.getValueAt(0, 4)); // Subtotal = 20.00

            // El total preliminar del menú debe mostrarse en USD con 2 decimales:
            assertEquals("$ 20.00", totalMenu.getText());
            assertTrue("Tooltip debe indicar que IVA y Bs. se calculan al facturar",
                    totalMenu.getToolTipText().contains("al facturar"));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testTab2YTab3IndependenciaEntreSalasYMesas() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);
            JLabel totalMenu = campo(sistema, "totalMenu", JLabel.class);
            JTextField txtTempIdSala = campo(sistema, "txtTempIdSala", JTextField.class);
            JTextField txtTempNumMesa = campo(sistema, "txtTempNumMesa", JTextField.class);

            // 1. Simular apertura de Mesa 1 en Sala Principal (id_sala = 1)
            Map<Integer, Integer> estadosSala1 = Collections.singletonMap(1, 0);
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPanelMesas",
                        new Class<?>[]{int.class, String.class, int.class, Map.class},
                        new Object[]{1, "SALA PRINCIPAL", 1, estadosSala1});
            });

            JPanel panelMesas = campo(sistema, "PanelMesas", JPanel.class);
            JButton btnMesaSala1 = (JButton) panelMesas.getComponent(0);
            SwingUtilities.invokeAndWait(() -> btnMesaSala1.doClick());

            // Cargar platos ficticios en el carrito de Sala Principal
            DefaultTableModel modeloMenu = (DefaultTableModel) tableMenu.getModel();
            modeloMenu.addRow(new Object[]{1, "Chaufa", 2, "20", "40", ""});
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "TotalPagar",
                        new Class<?>[]{JTable.class, JLabel.class},
                        new Object[]{tableMenu, totalMenu});
            });
            assertEquals(1, tableMenu.getRowCount());
            assertEquals("$ 40.00", totalMenu.getText());
            assertEquals("1", txtTempIdSala.getText());

            // 2. Simular cambio a Segundo Piso (id_sala = 2) y seleccionar Mesa 1
            Map<Integer, Integer> estadosSala2 = Collections.singletonMap(1, 0);
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPanelMesas",
                        new Class<?>[]{int.class, String.class, int.class, Map.class},
                        new Object[]{2, "SEGUNDO PISO", 1, estadosSala2});
            });

            JButton btnMesaSala2 = (JButton) panelMesas.getComponent(0);
            SwingUtilities.invokeAndWait(() -> btnMesaSala2.doClick());

            // Verificar que el carrito de Segundo Piso está COMPLETAMENTE LIMPIO e independiente
            assertEquals("El carrito debe estar vacío al abrir otra sala/mesa", 0, tableMenu.getRowCount());
            assertEquals("El total debe reiniciarse a $ 0.00", "$ 0.00", totalMenu.getText());
            assertEquals("El id de sala debe ser 2 (Segundo Piso)", "2", txtTempIdSala.getText());
            assertEquals("El num_mesa debe ser 1", "1", txtTempNumMesa.getText());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testTab3GenerarPedidoPersisteCalculoFiscalCompleto() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Config conf = campo(sistema, "conf", Config.class);
            conf.setTasaDolar(new BigDecimal("36.5000"));
            conf.setIvaPorcentaje(new BigDecimal("16.00"));

            campo(sistema, "txtTempIdSala", JTextField.class).setText("1");
            campo(sistema, "txtTempNumMesa", JTextField.class).setText("5");

            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);
            JLabel totalMenu = campo(sistema, "totalMenu", JLabel.class);

            // Configurar tabla menú con 1 ítem de $20
            DefaultTableModel modeloMenu = (DefaultTableModel) tableMenu.getModel();
            modeloMenu.setRowCount(0);
            modeloMenu.addRow(new Object[]{101, "Pabellón", 2, "10.00", "20.00", "Sin cebolla"});

            // Actualizar total preliminar a $20.00
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "TotalPagar",
                        new Class<?>[]{JTable.class, JLabel.class},
                        new Object[]{tableMenu, totalMenu});
            });

            // Configurar repositorio para capturar persistencia
            AtomicReference<Pedidos> pedidoCapturado = new AtomicReference<>();
            pedidosRepo.configurarRegistrarPedidoCompleto((ped, det) -> {
                pedidoCapturado.set(ped);
                return 777; // ID generado
            });

            int idGenerado = (int) invocar(sistema, "registrarPedidoCompleto", new Class<?>[]{}, new Object[]{});

            assertEquals(777, idGenerado);
            Pedidos ped = pedidoCapturado.get();
            assertNotNull(ped);
            assertEquals(1, ped.getId_sala());
            assertEquals(5, ped.getNum_mesa());
            // Subtotal: 20.00 | IVA 16%: 3.20 | Total: 23.20
            assertEquals(new BigDecimal("20.00"), ped.getSubtotal());
            assertEquals(new BigDecimal("16.00"), ped.getIvaPorcentaje());
            assertEquals(new BigDecimal("3.20"), ped.getIvaMonto());
            assertEquals(new BigDecimal("23.20"), ped.getTotalDecimal());
            assertEquals(new BigDecimal("36.5000"), ped.getTasaCambio());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 6. Tab 4: Finalizar Pedido / Facturación (Desglose Fiscal con IVA)
    // =========================================================================

    @Test
    public void testTab4MostrarPedidoEnPantallaDesgloseFiscalCompletoYTotalesEnteros() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Config conf = campo(sistema, "conf", Config.class);
            conf.setTasaDolar(new BigDecimal("36.5000"));

            Pedidos pedido = new Pedidos();
            pedido.setId(55);
            pedido.setId_sala(1);
            pedido.setSala("Salón");
            pedido.setNum_mesa(3);
            pedido.setFecha(LocalDate.now().toString());
            pedido.setSubtotal(new BigDecimal("20.00"));
            pedido.setIvaPorcentaje(new BigDecimal("16.00"));
            pedido.setIvaMonto(new BigDecimal("3.20"));
            pedido.setTotalDecimal(new BigDecimal("23.20"));
            pedido.setTasaCambio(new BigDecimal("36.5000"));
            pedido.setSubtotalBs(new BigDecimal("730.00"));
            pedido.setIvaBs(new BigDecimal("116.80"));
            pedido.setTotalBs(new BigDecimal("846.80"));
            pedido.setEstado("PENDIENTE");

            DetallePedido det = new DetallePedido(1, "Pabellón", new BigDecimal("10.00"), 2, "Sin cebolla", 55);
            List<DetallePedido> detalles = Collections.singletonList(det);

            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPedidoEnPantalla",
                        new Class<?>[]{Pedidos.class, List.class},
                        new Object[]{pedido, detalles});
            });

            JTable tableFinalizar = campo(sistema, "tableFinalizar", JTable.class);
            JLabel totalFinalizar = campo(sistema, "totalFinalizar", JLabel.class);

            assertEquals(1, tableFinalizar.getRowCount());
            assertEquals("Pabellón", tableFinalizar.getValueAt(0, 1));
            assertEquals(2, tableFinalizar.getValueAt(0, 2));

            // totalFinalizar muestra totales con 2 decimales en USD y Bs
            assertEquals("Bs. 846.80 ($ 23.20)", totalFinalizar.getText());

            // Y el desglose fiscal detallado en el tooltip con 2 decimales:
            String tooltip = totalFinalizar.getToolTipText();
            assertNotNull(tooltip);
            assertTrue("Debe contener Subtotal en tooltip", tooltip.contains("Subtotal: $ 20.00"));
            assertTrue("Debe contener IVA % y monto en tooltip", tooltip.contains("IVA (16.00%): $ 3.20") || tooltip.contains("IVA (16%): $ 3.20"));
            assertTrue("Debe contener Total a Pagar en tooltip", tooltip.contains("Total a Pagar: $ 23.20 (Bs. 846.80)"));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 7. Tab 5: Historial de Ventas / Pedidos
    // =========================================================================

    @Test
    public void testTab5HistorialPedidosFormatoYListado() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            Pedidos p1 = new Pedidos(1, 1, 2, "2026-10-06", new BigDecimal("23.20"), "Salón", "admin", "FINALIZADO", new BigDecimal("36.5000"), new BigDecimal("846.80"));
            List<Pedidos> lista = Collections.singletonList(p1);

            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPedidosEnTabla", new Class<?>[]{List.class}, new Object[]{lista});
            });

            JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
            assertEquals(1, tablePedidos.getRowCount());
            assertEquals(1, tablePedidos.getValueAt(0, 0));
            assertEquals("Salón", tablePedidos.getValueAt(0, 1));
            assertEquals("admin", tablePedidos.getValueAt(0, 2));
            assertEquals(2, tablePedidos.getValueAt(0, 3));
            assertEquals("FINALIZADO", tablePedidos.getValueAt(0, 6));
            assertTrue(tablePedidos.getValueAt(0, 5).toString().contains("Bs. 846.80"));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 8. Tab 6: Datos de la Empresa (Validaciones Tasa e IVA)
    // =========================================================================

    @Test
    public void testTab6ListarConfigYValidacionesEmpresa() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            loginDaoFalso.configuracionActual = new Config();
            loginDaoFalso.configuracionActual.setId(1);
            loginDaoFalso.configuracionActual.setRuc("J-12345678-9");
            loginDaoFalso.configuracionActual.setNombre("Restaurante Gourmet");
            loginDaoFalso.configuracionActual.setTelefono("04141234567");
            loginDaoFalso.configuracionActual.setDireccion("Av. Principal");
            loginDaoFalso.configuracionActual.setMensaje("¡Gracias!");
            loginDaoFalso.configuracionActual.setTasaDolar(new BigDecimal("38.5000"));
            loginDaoFalso.configuracionActual.setIvaPorcentaje(new BigDecimal("16.00"));

            SwingUtilities.invokeAndWait(sistema::ListarConfig);

            JTextField txtRuc = campo(sistema, "txtRucConfig", JTextField.class);
            JTextField txtNombre = campo(sistema, "txtNombreConfig", JTextField.class);
            JTextField txtTasa = campo(sistema, "txtTasaConfig", JTextField.class);
            JTextField txtIva = campo(sistema, "txtIvaConfig", JTextField.class);
            JButton btnActualizar = campo(sistema, "btnActualizarConfig", JButton.class);

            assertEquals("J-12345678-9", txtRuc.getText());
            assertEquals("Restaurante Gourmet", txtNombre.getText());
            assertEquals("38.5000", txtTasa.getText());
            assertEquals("16.00", txtIva.getText());

            // Actualización con nueva tasa y nuevo IVA
            txtTasa.setText("40.0000");
            txtIva.setText("8.00");

            SwingUtilities.invokeAndWait(btnActualizar::doClick);

            assertEquals(new BigDecimal("40.0000"), loginDaoFalso.configuracionActual.getTasaDolar());
            assertEquals(new BigDecimal("8.00"), loginDaoFalso.configuracionActual.getIvaPorcentaje());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testTab6RetencionYPurgaPorRol() throws Exception {
        Sistema sistemaAdmin = crearSistemaEnEdt(admin, true);
        try {
            javax.swing.JSpinner spRetencion = sistemaAdmin.getSpMesesRetencion();
            JButton btnPurga = sistemaAdmin.getBtnPurgarHistorial();

            assertNotNull(spRetencion);
            assertNotNull(btnPurga);
            assertTrue(btnPurga.isVisible());
            assertTrue(btnPurga.isEnabled());
            assertTrue(spRetencion.isEnabled());
            assertEquals(24, spRetencion.getValue());

            // Actualizar meses de retención en configuración
            spRetencion.setValue(12);
            JButton btnActualizar = campo(sistemaAdmin, "btnActualizarConfig", JButton.class);
            SwingUtilities.invokeAndWait(btnActualizar::doClick);

            assertEquals(12, loginDaoFalso.configuracionActual.getMesesRetencionPedidos());
        } finally {
            SwingUtilities.invokeAndWait(sistemaAdmin::dispose);
        }

        Sistema sistemaAsistente = crearSistemaEnEdt(asistente, true);
        try {
            JButton btnPurga = sistemaAsistente.getBtnPurgarHistorial();
            javax.swing.JSpinner spRetencion = sistemaAsistente.getSpMesesRetencion();

            if (btnPurga != null) {
                assertFalse(btnPurga.isVisible());
            }
            if (spRetencion != null) {
                assertFalse(spRetencion.isEnabled());
            }
        } finally {
            SwingUtilities.invokeAndWait(sistemaAsistente::dispose);
        }
    }

    // =========================================================================
    // 9. Tab 7: Usuarios (Listado y Validación de Roles)
    // =========================================================================

    @Test
    public void testTab7UsuariosListado() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            loginDaoFalso.usuariosRetorno = Arrays.asList(
                    new Usuario(1, "Administrador", "admin", "pass", "Administrador"),
                    new Usuario(2, "Cajero", "caja", "pass", "Asistente")
            );

            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "ListarUsuarios", new Class<?>[]{}, new Object[]{});
            });

            JTable tableUsuarios = campo(sistema, "TableUsuarios", JTable.class);
            assertEquals(2, tableUsuarios.getRowCount());
            assertEquals("Administrador", tableUsuarios.getValueAt(0, 1));
            assertEquals("Cajero", tableUsuarios.getValueAt(1, 1));
            assertEquals("Asistente", tableUsuarios.getValueAt(1, 3));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 10. Tab 8: Catálogo Platos CRUD
    // =========================================================================

    @Test
    public void testTab8PlatosCrudMuestraPreciosEnterosEnTablaPlatos() throws Exception {
        Sistema sistema = crearSistemaEnEdt(admin, true);
        try {
            List<Platos> lista = Arrays.asList(
                    new Platos(1, "Chivo en Coco", new BigDecimal("18.00"), "2026-10-06"),
                    new Platos(2, "Postre Tres Leches", new BigDecimal("4.50"), "2026-10-06")
            );

            JTable tablePlatos = campo(sistema, "TablePlatos", JTable.class);
            SwingUtilities.invokeAndWait(() -> {
                invocar(sistema, "mostrarPlatosEnTabla",
                        new Class<?>[]{JTable.class, List.class},
                        new Object[]{tablePlatos, lista});
            });

            assertEquals(2, tablePlatos.getRowCount());
            assertEquals("Chivo en Coco", tablePlatos.getValueAt(0, 1));
            assertEquals("18.00", tablePlatos.getValueAt(0, 2)); // Formato estándar con 2 decimales
            assertEquals("Postre Tres Leches", tablePlatos.getValueAt(1, 1));
            assertEquals("4.50", tablePlatos.getValueAt(1, 2));  // 4.50 con 2 decimales exactos
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testTab8PlatosBotonesDesactivarYReactivarPorRol() throws Exception {
        Sistema sistemaAdmin = crearSistemaEnEdt(admin, true);
        try {
            javax.swing.JButton btnEliminar = campo(sistemaAdmin, "btnEliminarPlato", javax.swing.JButton.class);
            javax.swing.JButton btnReactivar = sistemaAdmin.getBtnReactivarPlato();

            assertEquals("Desactivar", btnEliminar.getText());
            assertEquals("Oculta el plato del menú sin eliminarlo", btnEliminar.getToolTipText());
            assertNotNull(btnReactivar);
            assertTrue(btnReactivar.isVisible());
            assertTrue(btnReactivar.isEnabled());
        } finally {
            SwingUtilities.invokeAndWait(sistemaAdmin::dispose);
        }

        Sistema sistemaAsistente = crearSistemaEnEdt(asistente, true);
        try {
            javax.swing.JButton btnEliminar = campo(sistemaAsistente, "btnEliminarPlato", javax.swing.JButton.class);
            javax.swing.JButton btnReactivar = sistemaAsistente.getBtnReactivarPlato();

            assertFalse(btnEliminar.isEnabled());
            if (btnReactivar != null) {
                assertFalse(btnReactivar.isVisible());
            }
        } finally {
            SwingUtilities.invokeAndWait(sistemaAsistente::dispose);
        }
    }

    // =========================================================================
    // Fakes / Stubs para aislamiento completo de Swing
    // =========================================================================

    private static class SalasRepositorioFalso implements SalasRepositorio {
        List<Salas> salas = new ArrayList<>();
        @Override public boolean registrar(Salas sala) { return salas.add(sala); }
        @Override public List<Salas> listar() { return new ArrayList<>(salas); }
        @Override public boolean eliminar(int id) { return salas.removeIf(s -> s.getId() == id); }
        @Override public boolean modificar(Salas sala) { return true; }
    }

    private static class PlatosRepositorioFalso implements PlatosRepositorio {
        List<Platos> platos = new ArrayList<>();
        List<Platos> inactivos = new ArrayList<>();
        @Override public boolean registrar(Platos plato) { return platos.add(plato); }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return new ArrayList<>(platos); }
        @Override public boolean desactivar(int id) {
            platos.stream().filter(p -> p.getId() == id).findFirst().ifPresent(inactivos::add);
            return platos.removeIf(p -> p.getId() == id);
        }
        @Override public boolean reactivar(int id) {
            inactivos.stream().filter(p -> p.getId() == id).findFirst().ifPresent(platos::add);
            return inactivos.removeIf(p -> p.getId() == id);
        }
        @Override public List<Platos> listarInactivos() { return new ArrayList<>(inactivos); }
        @Override public boolean eliminar(int id) { return desactivar(id); }
        @Override public boolean modificar(Platos plato) { return true; }
    }

    private static class PedidosRepositorioTestDouble extends PedidosRepositorioFalso {
        java.util.function.BiFunction<Pedidos, List<DetallePedido>, Integer> funcionRegistro;

        public void configurarRegistrarPedidoCompleto(java.util.function.BiFunction<Pedidos, List<DetallePedido>, Integer> fn) {
            this.funcionRegistro = fn;
        }

        @Override
        public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
            if (funcionRegistro != null) {
                return funcionRegistro.apply(pedido, detalles);
            }
            return 1;
        }

        @Override
        public int verificarStado(int mesa, int idSala) {
            return 0;
        }

        @Override
        public List<Pedidos> listarPedidos() {
            return Collections.emptyList();
        }
    }

    private static class LoginDaoFalso extends LoginDao {
        Config configuracionActual = new Config();
        List<Usuario> usuariosRetorno = new ArrayList<>();

        public LoginDaoFalso() {
            super();
            configuracionActual.setId(1);
            configuracionActual.setRuc("J-12345678-9");
            configuracionActual.setNombre("Restaurante Demo");
            configuracionActual.setTelefono("02120000000");
            configuracionActual.setDireccion("Caracas");
            configuracionActual.setMensaje("Bienvenido");
            configuracionActual.setTasaDolar(new BigDecimal("36.5000"));
            configuracionActual.setIvaPorcentaje(new BigDecimal("16.00"));
        }

        @Override
        public Config datosEmpresa() {
            return configuracionActual;
        }

        @Override
        public boolean ModificarDatos(Config conf) {
            this.configuracionActual = conf;
            return true;
        }

        @Override
        public List<Usuario> ListarUsuarios() {
            return usuariosRetorno;
        }
    }
}
