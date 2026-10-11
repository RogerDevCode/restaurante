package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Regresión del bug "Agregar Productos" sobre un pedido pendiente:
 * btnModificarPedidoActionPerformed debe dejar idPedidoEdicion apuntando al
 * pedido seleccionado (y el botón en modo "Actualizar Pedido"). Antes del fix,
 * LimpiarTableMenu() se ejecutaba después de asignar idPedidoEdicion y lo
 * reseteaba a -1, provocando PedidoPendienteExistenteException al reintentar.
 */
public class EdicionPedidoPendienteTest {

    private Sistema sistema;
    private Path tempDir;
    private final List<DetallePedido> detallesFake = new ArrayList<>();

    private static class SalasRepositorioFalso implements SalasRepositorio {
        @Override public List<Salas> listar() { return new ArrayList<>(); }
        @Override public boolean registrar(Salas sl) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sl) { return false; }
    }

    private static class PlatosRepositorioFalso implements PlatosRepositorio {
        @Override public List<Platos> listarPorFecha(String f1, String f2) { return new ArrayList<>(); }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    private static Object leerCampo(Object obj, String nombre) throws Exception {
        Field f = Sistema.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(obj);
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("edicion-pedido-pendiente-test-");
        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");

        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasRepositorioFalso(), new PoliticaAcceso(admin)));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(new PlatosRepositorioFalso(), new PoliticaAcceso(admin)));

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso() {
            @Override public List<DetallePedido> verPedidoDetalle(int idPedido) { return detallesFake; }
        };
        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle, () -> new Config(),
                new GeneradorPdfPedido(tempDir), f -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo), pdfServicio, rbacAdmin,
                new ConsultaPedidosServicio(pedidosRepo, rbacAdmin));

        SwingUtilities.invokeAndWait(() -> {
            try {
                Config cfg = new Config();
                cfg.setIvaPorcentaje(new java.math.BigDecimal("16.00"));
                sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl);

                Field confField = Sistema.class.getDeclaredField("conf");
                confField.setAccessible(true);
                confField.set(sistema, cfg);

                Field ivaMapField = Sistema.class.getDeclaredField("platoAplicaIvaMap");
                ivaMapField.setAccessible(true);
                ivaMapField.set(sistema, new HashMap<Integer, Boolean>());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @After
    public void tearDown() {
        if (sistema != null) {
            sistema.dispose();
        }
    }

    @Test
    public void modificarPedidoPendienteMantieneIdDeEdicion() throws Exception {
        Pedidos pedidoPendiente = new Pedidos();
        pedidoPendiente.setId(7);
        pedidoPendiente.setId_sala(1);
        pedidoPendiente.setNum_mesa(4);
        pedidoPendiente.setSala("Principal");
        pedidoPendiente.setUsuario("Administrador");

        DetallePedido det = new DetallePedido();
        det.setNombre("Plato IVA");
        det.setCantidad(2);
        det.setPrecioDecimal(new java.math.BigDecimal("10.00"));
        det.setComentario("sin sal");
        detallesFake.add(det);

        SwingUtilities.invokeAndWait(() -> {
            try {
                Field pedField = Sistema.class.getDeclaredField("ped");
                pedField.setAccessible(true);
                pedField.set(sistema, pedidoPendiente);

                Method m = Sistema.class.getDeclaredMethod("btnModificarPedidoActionPerformed", java.awt.event.ActionEvent.class);
                m.setAccessible(true);
                m.invoke(sistema, new java.awt.event.ActionEvent(sistema, java.awt.event.ActionEvent.ACTION_PERFORMED, "modificar"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        int idEdicion = (int) leerCampo(sistema, "idPedidoEdicion");
        JButton btnGenerar = (JButton) leerCampo(sistema, "btnGenerarPedido");
        JTable tableMenu = (JTable) leerCampo(sistema, "tableMenu");

        assertEquals("idPedidoEdicion debe apuntar al pedido seleccionado", 7, idEdicion);
        assertEquals("Actualizar Pedido", btnGenerar.getText());
        assertEquals("El detalle del pedido debe cargarse en el carrito", 1, tableMenu.getRowCount());
    }
}
