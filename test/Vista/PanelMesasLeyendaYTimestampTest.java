package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PanelMesasLeyendaYTimestampTest {

    private final Usuario usuarioAdmin = new Usuario(1, "Admin Mesas", "admin@rest.com", "pass", "Administrador");
    private Sistema sistema;
    private final AtomicInteger consultasVerificarEstado = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        consultasVerificarEstado.set(0);
        sistema = crearSistemaEnEdt(usuarioAdmin);
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void componentesDeHeaderYLeyendaEstanInicializadosCorrectamente() throws Exception {
        JLabel lblTitulo = campo(sistema, "lblTituloSalaMesas", JLabel.class);
        JLabel lblUltimaCarga = campo(sistema, "lblUltimaCargaMesas", JLabel.class);
        JButton btnActualizar = campo(sistema, "btnActualizarMesas", JButton.class);
        JPanel panelMesasTab = campo(sistema, "jPanel22", JPanel.class);

        assertNotNull("El label de título de mesas debe existir", lblTitulo);
        assertNotNull("El label de última carga de mesas debe existir", lblUltimaCarga);
        assertNotNull("El botón actualizar mesas debe existir", btnActualizar);
        assertNotNull("El tab jPanel22 debe existir", panelMesasTab);

        assertEquals("Selecciona una sala para ver sus mesas", lblTitulo.getText());
        assertEquals("Última actualización: --:--:--", lblUltimaCarga.getText());
        assertFalse("El botón actualizar debe estar inicialmente deshabilitado", btnActualizar.isEnabled());
        assertNotNull("El botón actualizar debe tener tooltip descriptivo", btnActualizar.getToolTipText());

        // Verificar que en jPanel22 existe la leyenda visual con Libre y Ocupada
        boolean encontroLeyenda = false;
        boolean encontroLibre = false;
        boolean encontroOcupada = false;

        for (Component c : panelMesasTab.getComponents()) {
            if (c instanceof JPanel) {
                JPanel pnl = (JPanel) c;
                for (Component sub : pnl.getComponents()) {
                    if (sub instanceof JLabel) {
                        JLabel lbl = (JLabel) sub;
                        String txt = lbl.getText().trim();
                        if ("Leyenda:".equalsIgnoreCase(txt)) {
                            encontroLeyenda = true;
                        } else if ("Libre".equalsIgnoreCase(txt)) {
                            encontroLibre = true;
                            assertEquals(new Color(0, 102, 102), lbl.getBackground());
                        } else if ("Ocupada".equalsIgnoreCase(txt)) {
                            encontroOcupada = true;
                            assertEquals(new Color(255, 51, 51), lbl.getBackground());
                        }
                    }
                }
            }
        }

        assertTrue("Debe existir el indicador 'Leyenda:' en el panel", encontroLeyenda);
        assertTrue("Debe existir el indicador 'Libre' en la leyenda", encontroLibre);
        assertTrue("Debe existir el indicador 'Ocupada' en la leyenda", encontroOcupada);
    }

    @Test
    public void mostrarPanelMesasActualizaTituloTimestampYHabilitaBotonActualizar() throws Exception {
        Map<Integer, Integer> estados = new HashMap<>();
        estados.put(1, 0);   // Mesa 1 Libre
        estados.put(2, 45);  // Mesa 2 Ocupada

        SwingUtilities.invokeAndWait(() -> {
            try {
                var metodo = sistema.getClass().getDeclaredMethod("mostrarPanelMesas", int.class, String.class, int.class, Map.class);
                metodo.setAccessible(true);
                metodo.invoke(sistema, 3, "TERRAZA VIP", 2, estados);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        JLabel lblTitulo = campo(sistema, "lblTituloSalaMesas", JLabel.class);
        JLabel lblUltimaCarga = campo(sistema, "lblUltimaCargaMesas", JLabel.class);
        JButton btnActualizar = campo(sistema, "btnActualizarMesas", JButton.class);

        assertEquals("Mesas de: TERRAZA VIP (2 mesas)", lblTitulo.getText());
        assertTrue("El botón actualizar debe habilitarse tras cargar una sala", btnActualizar.isEnabled());
        assertTrue("El timestamp debe tener formato 'Última actualización: HH:mm:ss'",
                lblUltimaCarga.getText().matches("Última actualización: \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void botonActualizarMesasRecargaLaSalaActual() throws Exception {
        // Fijar sala actual simulada
        Map<Integer, Integer> estados = Collections.singletonMap(1, 0);

        SwingUtilities.invokeAndWait(() -> {
            try {
                var metodo = sistema.getClass().getDeclaredMethod("mostrarPanelMesas", int.class, String.class, int.class, Map.class);
                metodo.setAccessible(true);
                metodo.invoke(sistema, 5, "SALON PRINCIPAL", 1, estados);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton btnActualizar = campo(sistema, "btnActualizarMesas", JButton.class);
        assertTrue(btnActualizar.isEnabled());

        int consultasAntes = consultasVerificarEstado.get();

        SwingUtilities.invokeAndWait(btnActualizar::doClick);

        // Esperar a que el worker background ejecute
        Thread.sleep(500);

        assertTrue("Al presionar actualizar debe invocar la consulta de estado de las mesas de la sala",
                consultasVerificarEstado.get() > consultasAntes);
    }

    @Test
    public void cuadroColorCreaBadgeConFormatoYColor() throws Exception {
        Color libreColor = new Color(0, 102, 102);
        JLabel badgeLibre = sistema.cuadroColor(libreColor, "Libre");

        assertNotNull(badgeLibre);
        assertEquals("Libre", badgeLibre.getText().trim());
        assertEquals(libreColor, badgeLibre.getBackground());
        assertTrue(badgeLibre.isOpaque());
        assertEquals(Color.WHITE, badgeLibre.getForeground());

        // Color claro
        Color claro = new Color(200, 255, 200);
        JLabel badgeClaro = sistema.cuadroColor(claro, "Claro");
        assertEquals(Color.BLACK, badgeClaro.getForeground());
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
            public int verificarStado(int mesa, int idSala) {
                consultasVerificarEstado.incrementAndGet();
                return 0;
            }

            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                return Collections.emptyMap();
            }
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
