package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.CierreCaja;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Suite de pruebas para los componentes visuales de previsualización de facturas
 * y emisión de Cierres de Caja (Corte X y Z).
 */
public class CierreCajaYPrevisualizacionVistaTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    private final Usuario usuarioAdmin = new Usuario(1, "Administrador Caja", "admin@rest.com", "pass", "Administrador");
    private Sistema sistema;

    private final AtomicInteger previsualizacionesFactura = new AtomicInteger();
    private final AtomicInteger reimpresionesFactura = new AtomicInteger();
    private final AtomicInteger impresionesCierre = new AtomicInteger();
    private final AtomicInteger previsualizacionesCierre = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        previsualizacionesFactura.set(0);
        reimpresionesFactura.set(0);
        impresionesCierre.set(0);
        previsualizacionesCierre.set(0);
        sistema = crearSistemaEnEdt(usuarioAdmin);
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void botonesDePrevisualizacionYDeCierreExistenYPresentanTextosAdecuados() {
        JButton btnPrevHist = sistema.getBtnPrevisualizarHistorial();
        JButton btnPrevPed = sistema.getBtnPrevisualizarPedido();
        JButton btnCierreX = sistema.getBtnCierreParcial();
        JButton btnCierreZ = sistema.getBtnCierreTotal();

        assertNotNull("Botón previsualizar en historial debe existir", btnPrevHist);
        assertNotNull("Botón previsualizar en finalizar pedido debe existir", btnPrevPed);
        assertNotNull("Botón Cierre X en historial debe existir", btnCierreX);
        assertNotNull("Botón Cierre Z en historial debe existir", btnCierreZ);

        assertTrue("Botón Cierre X en historial debe estar habilitado", btnCierreX.isEnabled());
        assertTrue("Botón Cierre Z en historial debe estar habilitado", btnCierreZ.isEnabled());

        assertEquals("Cierre Parcial (X)", btnCierreX.getText());
        assertEquals("Cierre Total (Z)", btnCierreZ.getText());
        assertEquals("Previsualizar", btnPrevHist.getText());
        assertEquals("Previsualizar", btnPrevPed.getText());

        assertNotNull("Checkbox imprimir logo debe existir", sistema.getChkImprimirLogoTicket());
        assertTrue("Checkbox imprimir logo debe estar habilitado por defecto", sistema.getChkImprimirLogoTicket().isSelected());
    }

    @Test
    public void previsualizarFacturaClienteSeleccionadaInvocaServicioCuandoHayFila() throws Exception {
        JTable tableFacturas = campo(sistema, "tableFacturasCliente", JTable.class);
        assertNotNull(tableFacturas);

        // Con fila seleccionada
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tableFacturas.getModel();
            model.setRowCount(0);
            model.addRow(new Object[]{501, "2026-10-07 14:00", "Terraza", 2, "$ 30.00", "Bs. 1095.00", "Admin", "FINALIZADO"});
            tableFacturas.setRowSelectionInterval(0, 0);
        });

        SwingUtilities.invokeAndWait(() -> sistema.previsualizarFacturaClienteSeleccionada());
        assertEquals("Debe haberse solicitado la previsualización de la factura 501", 1, previsualizacionesFactura.get());
    }

    @Test
    public void reimprimirFacturaClienteSeleccionadaInvocaServicioCuandoHayFila() throws Exception {
        JTable tableFacturas = campo(sistema, "tableFacturasCliente", JTable.class);
        assertNotNull(tableFacturas);

        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tableFacturas.getModel();
            model.setRowCount(0);
            model.addRow(new Object[]{501, "2026-10-07 14:00", "Terraza", 2, "$ 30.00", "Bs. 1095.00", "Admin", "FINALIZADO"});
            tableFacturas.setRowSelectionInterval(0, 0);
        });

        SwingUtilities.invokeAndWait(() -> sistema.reimprimirFacturaClienteSeleccionada());
        assertEquals("Debe haberse solicitado la reimpresión de la factura 501", 1, reimpresionesFactura.get());
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
        pedidoDummy.setId(501);
        pedidoDummy.setNum_mesa(2);
        pedidoDummy.setSala("Terraza");
        pedidoDummy.setUsuario("Admin");
        pedidoDummy.setFecha("2026-10-07");
        pedidoDummy.setTotalDecimal(new BigDecimal("30.00"));

        Config configDummy = new Config(1, "J-00001", "Restaurante", "123", "Caracas", "Gracias");
        DetallePedido detalleDummy = new DetallePedido();
        detalleDummy.setNombre("Plato");
        detalleDummy.setCantidad(1);
        detalleDummy.setPrecioDecimal(new BigDecimal("30.00"));

        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                id -> pedidoDummy,
                id -> Collections.singletonList(detalleDummy),
                () -> configDummy,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                path -> reimpresionesFactura.incrementAndGet(),
                path -> previsualizacionesFactura.incrementAndGet()
        );

        Servicio.GeneradorPdfCierre generadorCierre = new Servicio.GeneradorPdfCierre(temporal.getRoot().toPath());
        Modelo.CierreCajaDao daoFalso = new Modelo.CierreCajaDao() {
            @Override
            public CierreCaja consultarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, Config cfg) {
                CierreCaja c = new CierreCaja();
                c.setTipo(tipo);
                c.setFecha("2026-10-07");
                c.setFechaHoraEmision("2026-10-07 18:00:00");
                c.setUsuarioEmisor(usuarioEmisor);
                return c;
            }
        };

        Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                daoFalso,
                () -> configDummy,
                generadorCierre,
                path -> impresionesCierre.incrementAndGet(),
                path -> previsualizacionesCierre.incrementAndGet()
        );

        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicio,
                rbac,
                consultas,
                cierreServicio
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
