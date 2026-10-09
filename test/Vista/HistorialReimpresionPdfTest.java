package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.PedidosRepositorioFalso;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HistorialReimpresionPdfTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private final Usuario usuarioAdmin = new Usuario(1, "Admin Historial", "admin@rest.com", "pass", "Administrador");
    private Sistema sistema;
    private final AtomicInteger reimpresionesSolicitadas = new AtomicInteger();
    private final AtomicInteger previsualizacionesSolicitadas = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        reimpresionesSolicitadas.set(0);
        previsualizacionesSolicitadas.set(0);
        sistema = crearSistemaEnEdt(usuarioAdmin);
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void tooltipsYEstadoInicialDeBotonesReimpresionYCierre() throws Exception {
        JButton btnPdf = campo(sistema, "btnPdfPedido", JButton.class);
        JButton btnReimprimir = campo(sistema, "btnReimprimirHistorial", JButton.class);
        JButton btnPrevHist = campo(sistema, "btnPrevisualizarHistorial", JButton.class);
        JButton btnCierreX = campo(sistema, "btnCierreParcial", JButton.class);
        JButton btnCierreZ = campo(sistema, "btnCierreTotal", JButton.class);

        assertNotNull(btnPdf);
        assertNotNull(btnReimprimir);
        assertNotNull(btnPrevHist);
        assertNotNull(btnCierreX);
        assertNotNull(btnCierreZ);
        assertEquals("Ver / Reimprimir factura del pedido seleccionado", btnPdf.getToolTipText());
        assertEquals("Ver / Reimprimir factura del pedido seleccionado", btnReimprimir.getToolTipText());
        assertFalse(btnReimprimir.isEnabled());
        assertFalse(btnPrevHist.isEnabled());
        assertTrue(btnCierreX.isEnabled());
        assertTrue(btnCierreZ.isEnabled());
    }

    @Test
    public void seleccionEnTablePedidosHabilitaReimpresionSoloParaFinalizados() throws Exception {
        JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
        JButton btnPdf = campo(sistema, "btnPdfPedido", JButton.class);
        JButton btnReimprimir = campo(sistema, "btnReimprimirHistorial", JButton.class);
        JButton btnPrevHist = campo(sistema, "btnPrevisualizarHistorial", JButton.class);
        JTextField txtIdHistorial = campo(sistema, "txtIdHistorialPedido", JTextField.class);

        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tablePedidos.getModel();
            model.setRowCount(0);
            // Col 0: Id, 1: Sala, 2: Atendido, 3: Mesa, 4: Fecha, 5: Total, 6: Estado
            model.addRow(new Object[]{101, "Terraza", "Admin", 1, "2026-10-07 10:00:00", "$ 25.00", "PENDIENTE"});
            model.addRow(new Object[]{102, "VIP", "Admin", 2, "2026-10-07 11:00:00", "$ 80.00", "FINALIZADO"});
        });

        // 1. Seleccionar fila 0 (PENDIENTE)
        SwingUtilities.invokeAndWait(() -> tablePedidos.setRowSelectionInterval(0, 0));

        assertEquals("101", txtIdHistorial.getText());
        assertFalse("No debe habilitarse botón PDF para pedido PENDIENTE", btnPdf.isEnabled());
        assertFalse("No debe habilitarse botón Reimprimir Historial para pedido PENDIENTE", btnReimprimir.isEnabled());
        assertFalse("No debe habilitarse botón Previsualizar para pedido PENDIENTE", btnPrevHist.isEnabled());

        // 2. Seleccionar fila 1 (FINALIZADO)
        SwingUtilities.invokeAndWait(() -> tablePedidos.setRowSelectionInterval(1, 1));

        assertEquals("102", txtIdHistorial.getText());
        assertTrue("Debe habilitarse botón PDF para pedido FINALIZADO", btnPdf.isEnabled());
        assertTrue("Debe habilitarse botón Reimprimir Historial para pedido FINALIZADO", btnReimprimir.isEnabled());
        assertTrue("Debe habilitarse botón Previsualizar para pedido FINALIZADO", btnPrevHist.isEnabled());
    }

    @Test
    public void clicEnReimprimirDisparaReimpresionConTimestamp() throws Exception {
        JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
        JButton btnReimprimir = campo(sistema, "btnReimprimirHistorial", JButton.class);

        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tablePedidos.getModel();
            model.setRowCount(0);
            model.addRow(new Object[]{205, "Principal", "Admin", 3, "2026-10-07 12:00:00", "$ 40.00", "FINALIZADO"});
            tablePedidos.setRowSelectionInterval(0, 0);
        });

        assertTrue(btnReimprimir.isEnabled());

        SwingUtilities.invokeAndWait(btnReimprimir::doClick);

        assertEquals("Debe haberse solicitado la reimpresión exactamente una vez", 1, reimpresionesSolicitadas.get());
    }

    @Test
    public void clicEnPrevisualizarDisparaAperturaEnVisor() throws Exception {
        JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
        JButton btnPrevHist = campo(sistema, "btnPrevisualizarHistorial", JButton.class);

        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tablePedidos.getModel();
            model.setRowCount(0);
            model.addRow(new Object[]{205, "Principal", "Admin", 3, "2026-10-07 12:00:00", "$ 40.00", "FINALIZADO"});
            tablePedidos.setRowSelectionInterval(0, 0);
        });

        assertTrue(btnPrevHist.isEnabled());

        SwingUtilities.invokeAndWait(btnPrevHist::doClick);

        assertEquals("Debe haberse solicitado la previsualización en visor exactamente una vez", 1, previsualizacionesSolicitadas.get());
    }

    private Sistema crearSistemaEnEdt(Usuario usuario) throws Exception {
        final Sistema[] contenedor = new Sistema[1];
        PoliticaAcceso rbac = new PoliticaAcceso(usuario);

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Salas sl) { return true; }
            @Override public List<Salas> listar() { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Salas sl) { return true; }
        };

        PlatosRepositorio platosRepo = new PlatosRepositorio() {
            @Override public boolean registrar(Platos pla) { return true; }
            @Override public List<Platos> listarPorFecha(String f, String fe) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Platos pla) { return true; }
        };

        PedidosRepositorio pedidosRepo = new PedidosRepositorioFalso() {
            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                return Collections.emptyMap();
            }
        };

        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(pedidosRepo, rbac);

        Pedidos pedidoDummy = new Pedidos();
        pedidoDummy.setId(205);
        pedidoDummy.setNum_mesa(3);
        pedidoDummy.setSala("Principal");
        pedidoDummy.setUsuario("Admin");
        pedidoDummy.setFecha("2026-10-07");
        pedidoDummy.setTotalDecimal(new BigDecimal("40.00"));

        Config configDummy = new Config(1, "123", "Restaurante", "123", "Caracas", "Gracias");
        DetallePedido detalleDummy = new DetallePedido();
        detalleDummy.setNombre("Plato");
        detalleDummy.setCantidad(1);
        detalleDummy.setPrecioDecimal(new BigDecimal("40.00"));

        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                id -> pedidoDummy,
                id -> Collections.singletonList(detalleDummy),
                () -> configDummy,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                path -> reimpresionesSolicitadas.incrementAndGet(),
                path -> previsualizacionesSolicitadas.incrementAndGet()
        );

        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicio,
                rbac,
                consultas
        );

        SwingUtilities.invokeAndWait(() -> {
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl);
            contenedor[0].setProveedorMotivoAccion(titulo -> "Motivo test automatizado");
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
