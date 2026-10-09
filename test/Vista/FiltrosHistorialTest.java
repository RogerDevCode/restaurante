package Vista;

import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.PedidosRepositorioFalso;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FiltrosHistorialTest {

    private final Usuario usuarioAdmin = new Usuario(1, "Carlos Admin", "admin@rest.com", "pass", "Administrador");
    private Sistema sistema;
    private JTable tablePedidos;
    private String hoy;
    private String ayer;
    private String haceCincoDias;
    private String manana;

    @Before
    public void setUp() throws Exception {
        LocalDate now = LocalDate.now();
        hoy = now.toString();
        ayer = now.minusDays(1).toString();
        haceCincoDias = now.minusDays(5).toString();
        manana = now.plusDays(1).toString();

        sistema = crearSistemaEnEdt(usuarioAdmin);
        tablePedidos = campo(sistema, "TablePedidos", JTable.class);

        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel m = (DefaultTableModel) tablePedidos.getModel();
            m.setRowCount(0);
            // Col 0: Id, 1: Sala, 2: Atendido, 3: Mesa, 4: Fecha, 5: Total, 6: Estado
            m.addRow(new Object[]{1, "Terraza", "Carlos Admin", 2, hoy + " 10:00:00", "$ 15.00", "PENDIENTE"});
            m.addRow(new Object[]{2, "Salón VIP", "Mesero Juan", 4, hoy + " 12:30:00", "$ 50.00", "FINALIZADO"});
            m.addRow(new Object[]{3, "Terraza", "Carlos Admin", 5, ayer + " 14:00:00", "$ 30.00", "FINALIZADO"});
            m.addRow(new Object[]{4, "Patio", "Mesero Juan", 1, haceCincoDias + " 20:15:00", "$ 22.00", "PENDIENTE"});
            m.addRow(new Object[]{5, "Salón VIP", "Carlos Admin", 3, haceCincoDias + " 21:00:00", "$ 40.00", "FINALIZADO"});
            tablePedidos.setAutoCreateRowSorter(true);
            sistema.limpiarFiltrosHistorial();
        });
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testFiltroHoyMuestraSoloPedidosDeLaFechaActual() throws Exception {
        SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialHoy());

        SwingUtilities.invokeAndWait(() -> {
            // Filas 1 y 2 son de hoy
            assertEquals("Deben verse exactamente 2 pedidos de hoy", 2, tablePedidos.getRowCount());
            for (int i = 0; i < tablePedidos.getRowCount(); i++) {
                String f = tablePedidos.getValueAt(i, 4).toString();
                assertEquals(true, f.startsWith(hoy));
            }
        });
    }

    @Test
    public void testFiltroPendientesMuestraSoloPedidosPendientes() throws Exception {
        SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialPendientes());

        SwingUtilities.invokeAndWait(() -> {
            // Filas 1 y 4 son PENDIENTES
            assertEquals("Deben verse 2 pedidos pendientes", 2, tablePedidos.getRowCount());
            for (int i = 0; i < tablePedidos.getRowCount(); i++) {
                assertEquals("PENDIENTE", tablePedidos.getValueAt(i, 6));
            }
        });
    }

    @Test
    public void testFiltroFinalizadosMuestraSoloPedidosFinalizados() throws Exception {
        SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialFinalizados());

        SwingUtilities.invokeAndWait(() -> {
            // Filas 2, 3 y 5 son FINALIZADOS
            assertEquals("Deben verse 3 pedidos finalizados", 3, tablePedidos.getRowCount());
            for (int i = 0; i < tablePedidos.getRowCount(); i++) {
                assertEquals("FINALIZADO", tablePedidos.getValueAt(i, 6));
            }
        });
    }

    @Test
    public void testFiltroRangoFechas() throws Exception {
        // Rango desde ayer hasta hoy
        SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialRangoFechas(ayer, hoy));

        SwingUtilities.invokeAndWait(() -> {
            // Filas de hoy (2) + fila de ayer (1) = 3
            assertEquals("Deben verse 3 pedidos en el rango ayer - hoy", 3, tablePedidos.getRowCount());
        });

        // Rango solo de hace 5 días
        SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialRangoFechas(haceCincoDias, haceCincoDias));

        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Deben verse 2 pedidos de hace 5 días", 2, tablePedidos.getRowCount());
        });
    }

    @Test
    public void testFiltroCombinadoTextoYEstado() throws Exception {
        JTextField txtBuscar = campo(sistema, "txtBuscarHistorial", JTextField.class);

        SwingUtilities.invokeAndWait(() -> {
            txtBuscar.setText("Terraza");
            sistema.filtrarHistorialFinalizados();
        });

        SwingUtilities.invokeAndWait(() -> {
            // Terraza tiene id 1 (PENDIENTE) y id 3 (FINALIZADO). Con filtro FINALIZADO solo queda id 3.
            assertEquals("Solo debe coincidir 1 fila", 1, tablePedidos.getRowCount());
            assertEquals(3, tablePedidos.getValueAt(0, 0));
            assertEquals("Terraza", tablePedidos.getValueAt(0, 1));
            assertEquals("FINALIZADO", tablePedidos.getValueAt(0, 6));
        });
    }

    @Test
    public void testLimpiarFiltrosRestauraTodasLasFilas() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sistema.filtrarHistorialPendientes();
        });
        assertEquals(2, tablePedidos.getRowCount());

        SwingUtilities.invokeAndWait(() -> {
            sistema.limpiarFiltrosHistorial();
        });

        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Debe restaurar las 5 filas originales", 5, tablePedidos.getRowCount());
            assertEquals("", sistema.getTxtFiltroFechaDesde().getText());
            assertEquals("", sistema.getTxtFiltroFechaHasta().getText());
        });
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
            @Override public Map<Integer, Integer> contarMesasOcupadasPorSala() { return Collections.emptyMap(); }
        };

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
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl);
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
