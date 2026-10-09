package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Cliente;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.EstadisticasDashboard;
import Modelo.LoginDao;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.PlatosRepositorio;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas unitarias e integradas para el Dashboard, Clientes y Facturación personalizada.
 */
public class DashboardYClientesTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private static java.util.Timer autoDismissTimer;

    @BeforeClass
    public static void setupAutoDismiss() {
        autoDismissTimer = new java.util.Timer("modal-dismisser-dashboard", true);
        autoDismissTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    for (java.awt.Window window : java.awt.Window.getWindows()) {
                        if (window instanceof javax.swing.JDialog dialog && dialog.isVisible()) {
                            for (java.awt.Component comp : dialog.getContentPane().getComponents()) {
                                if (comp instanceof javax.swing.JOptionPane pane) {
                                    pane.setValue(javax.swing.JOptionPane.YES_OPTION);
                                }
                            }
                            dialog.dispose();
                        }
                    }
                });
            }
        }, 30, 30);
    }

    @AfterClass
    public static void tearDown() {
        if (autoDismissTimer != null) {
            autoDismissTimer.cancel();
        }
    }

    @Test
    public void testClienteModeloPropiedades() {
        Cliente cli = new Cliente();
        cli.setId(10);
        cli.setDocumento("V-28111222");
        cli.setNombre("Carlos Mendoza");
        cli.setDireccion("Av. Las Delicias");
        cli.setTelefono("0414-1234567");
        cli.setCreadoEn("2026-10-06 20:00:00");
        cli.setTotalFacturas(5);
        cli.setTotalGastadoDolares(new BigDecimal("125.50"));
        cli.setTotalGastadoBs(new BigDecimal("4518.00"));

        assertEquals(10, cli.getId());
        assertEquals("V-28111222", cli.getDocumento());
        assertEquals("Carlos Mendoza", cli.getNombre());
        assertEquals("Av. Las Delicias", cli.getDireccion());
        assertEquals("0414-1234567", cli.getTelefono());
        assertEquals("2026-10-06 20:00:00", cli.getCreadoEn());
        assertEquals(5, cli.getTotalFacturas());
        assertEquals(new BigDecimal("125.50"), cli.getTotalGastadoDolares());
        assertEquals(new BigDecimal("4518.00"), cli.getTotalGastadoBs());
    }

    @Test
    public void testEstadisticasDashboardCalculos() {
        EstadisticasDashboard stats = new EstadisticasDashboard();
        assertEquals(BigDecimal.ZERO.setScale(2), stats.getTicketPromedioHistoricoDolares());
        assertEquals(BigDecimal.ZERO.setScale(2), stats.getTicketPromedioHistoricoBs());

        stats.setPedidosHistoricos(4);
        stats.setVentasHistoricasDolares(new BigDecimal("200.00"));
        stats.setVentasHistoricasBs(new BigDecimal("7200.00"));

        assertEquals(new BigDecimal("50.00"), stats.getTicketPromedioHistoricoDolares());
        assertEquals(new BigDecimal("1800.00"), stats.getTicketPromedioHistoricoBs());

        stats.setVentasHoyDolares(new BigDecimal("100.00"));
        stats.setVentasHoyBs(new BigDecimal("3600.00"));
        stats.setPedidosHoy(2);
        stats.setTotalClientes(5);
        stats.setMesasOcupadasActuales(3);

        assertEquals(new BigDecimal("100.00"), stats.getVentasHoyDolares());
        assertEquals(new BigDecimal("3600.00"), stats.getVentasHoyBs());
        assertEquals(2, stats.getPedidosHoy());
        assertEquals(5, stats.getTotalClientes());
        assertEquals(3, stats.getMesasOcupadasActuales());
        assertEquals(new BigDecimal("50.00"), stats.getTicketPromedioHoyDolares());
        assertEquals(new BigDecimal("1800.00"), stats.getTicketPromedioHoyBs());
    }

    @Test
    public void testConfigClientePorDefecto() {
        Config conf = new Config();
        assertEquals("Consumidor Final", conf.getClienteDefaultNombre());
        assertEquals("V-00000000", conf.getClienteDefaultDoc());

        conf.setClienteDefaultNombre("Cliente General");
        conf.setClienteDefaultDoc("J-12345678-0");

        assertEquals("Cliente General", conf.getClienteDefaultNombre());
        assertEquals("J-12345678-0", conf.getClienteDefaultDoc());
        assertEquals("Cliente General", conf.getClientePredeterminadoNombre());
        assertEquals("J-12345678-0", conf.getClientePredeterminadoDocumento());
    }

    @Test
    public void testPedidosDatosCliente() {
        Pedidos pedido = new Pedidos();
        pedido.setId(42);
        pedido.setClienteNombre("Maria Perez");
        pedido.setClienteDocumento("V-19876543");

        assertEquals("Maria Perez", pedido.getClienteNombre());
        assertEquals("V-19876543", pedido.getClienteDocumento());
    }

    @Test
    public void testGeneradorPdfImprimeDatosCliente() throws Exception {
        Path dirSalida = temporal.newFolder("pdf_cliente_test").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(dirSalida);

        Pedidos pedido = new Pedidos();
        pedido.setId(88);
        pedido.setNum_mesa(3);
        pedido.setSala("Terraza");
        pedido.setUsuario("cajero");
        pedido.setFecha("2026-10-06 21:00:00");
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("3.20"));
        pedido.setTotalDecimal(new BigDecimal("23.20"));
        pedido.setSubtotalBs(new BigDecimal("720.00"));
        pedido.setIvaBs(new BigDecimal("115.20"));
        pedido.setTotalBs(new BigDecimal("835.20"));
        pedido.setTasaCambio(new BigDecimal("36.0000"));
        pedido.setClienteNombre("Inversiones El Sol, C.A.");
        pedido.setClienteDocumento("J-98765432-1");

        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Hamburguesa Especial");
        detalle.setCantidad(2);
        detalle.setPrecioDecimal(new BigDecimal("10.00"));

        Config config = new Config();
        config.setNombre("Restaurante Gourmet");
        config.setRuc("J-12345678-0");
        config.setTelefono("0212-9999999");
        config.setDireccion("Caracas");
        config.setMensaje("Gracias por su visita");
        config.setTasaCambio(new BigDecimal("36.0000"));
        config.setIvaPorcentaje(new BigDecimal("16.00"));

        Path pdfPath = generador.generar(pedido, config, List.of(detalle));
        assertTrue("El archivo PDF debe haberse generado", Files.exists(pdfPath) && Files.size(pdfPath) > 500);
    }

    @Test
    public void testSistemaComponentesDashboard() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        usuario.setNombre("Admin Test");

        Config configInicial = new Config(1, "J-12345678-0", "Restaurante Ávila", "0212-9876543",
                "Chacao, Caracas", "Gracias", new BigDecimal("38.5000"), new BigDecimal("16.00"), null);
        configInicial.setClienteDefaultNombre("Consumidor Habitual");
        configInicial.setClienteDefaultDoc("V-11223344");

        LoginDaoFalsoMock daoMock = new LoginDaoFalsoMock(configInicial);
        Sistema sistema = crearSistemaConDao(usuario, daoMock);

        try {
            JTabbedPane tabbedPane = campo(sistema, "jTabbedPane1", JTabbedPane.class);
            assertNotNull(tabbedPane);

            // Botón Dashboard en Sidebar
            JButton btnDashboard = campo(sistema, "btnDashboard", JButton.class);
            assertNotNull(btnDashboard);
            assertEquals("Dashboard", btnDashboard.getText());

            // Navegar a Dashboard
            SwingUtilities.invokeAndWait(btnDashboard::doClick);
            assertEquals("Dashboard", tabbedPane.getTitleAt(tabbedPane.getSelectedIndex()));

            // Campos en jPanel8 (Configuración)
            JTextField txtClienteDefNom = campo(sistema, "txtClienteDefaultNombre", JTextField.class);
            assertNotNull(txtClienteDefNom);
            assertEquals("Consumidor Habitual", txtClienteDefNom.getText());

            JTextField txtClienteDefDoc = campo(sistema, "txtClienteDefaultDoc", JTextField.class);
            assertNotNull(txtClienteDefDoc);
            assertEquals("V-11223344", txtClienteDefDoc.getText());

            // Componentes en Dashboard
            JTable tableClientes = campo(sistema, "tableClientes", JTable.class);
            assertNotNull(tableClientes);

            JTable tableFacturas = campo(sistema, "tableFacturasCliente", JTable.class);
            assertNotNull(tableFacturas);

            JTextField txtBuscar = campo(sistema, "txtBuscarCliente", JTextField.class);
            assertNotNull(txtBuscar);
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testDashboardSeleccionadoInicialmenteParaAdministrador() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setRol("Administrador");
        usuario.setNombre("Admin Inicial");

        Config configInicial = new Config(1, "J-12345678-0", "Restaurante Ávila", "0212-9876543",
                "Chacao, Caracas", "Gracias", new BigDecimal("38.5000"), new BigDecimal("16.00"), null);
        LoginDaoFalsoMock daoMock = new LoginDaoFalsoMock(configInicial);
        Sistema sistema = crearSistemaConDao(usuario, daoMock);

        try {
            JTabbedPane tabbedPane = campo(sistema, "jTabbedPane1", JTabbedPane.class);
            assertNotNull(tabbedPane);
            int selectedIndex = tabbedPane.getSelectedIndex();
            assertEquals("Dashboard", tabbedPane.getTitleAt(selectedIndex));
            assertTrue("El tab de Dashboard debe estar habilitado", tabbedPane.isEnabledAt(selectedIndex));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    private static class LoginDaoFalsoMock extends LoginDao {
        Config configuracionActual;

        LoginDaoFalsoMock(Config configInicial) {
            this.configuracionActual = configInicial;
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
    }

    private Sistema crearSistemaConDao(Usuario usuario, LoginDao loginDao) throws Exception {
        final Sistema[] contenedor = new Sistema[1];
        PoliticaAcceso rbac = new PoliticaAcceso(usuario);

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Modelo.Salas sl) { return true; }
            @Override public List<Modelo.Salas> listar() { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Salas sl) { return true; }
        };

        PlatosRepositorio platosRepo = new PlatosRepositorio() {
            @Override public boolean registrar(Modelo.Platos pla) { return true; }
            @Override public List<Modelo.Platos> listarPorFecha(String f, String fe) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Platos pla) { return true; }
        };

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso();

        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(pedidosRepo, rbac);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> true),
                rbac,
                consultas
        );

        SwingUtilities.invokeAndWait(() -> {
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl, loginDao);
        });
        return contenedor[0];
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object obj, String nombreCampo, Class<T> tipo) throws Exception {
        Field f = obj.getClass().getDeclaredField(nombreCampo);
        f.setAccessible(true);
        return (T) f.get(obj);
    }
}
