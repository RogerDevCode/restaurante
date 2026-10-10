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
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class SistemaCantidadesTest {

    private Sistema sistema;
    private Path tempDir;
    
    // Mocks and Doubles
    private class SalasRepositorioFalso implements Modelo.SalasRepositorio {
        public java.util.List<Salas> salas = new java.util.ArrayList<>();
        @Override public java.util.List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sl) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sl) { return false; }
    }
    private class PlatosRepositorioFalso implements Modelo.PlatosRepositorio {
        public java.util.List<Platos> platos = new java.util.ArrayList<>();
        @Override public java.util.List<Platos> listarPorFecha(String f1, String f2) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sistema-cantidades-test-");
        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");

        SalasRepositorioFalso salasRepo = new SalasRepositorioFalso();
        salasRepo.salas.add(new Salas(1, "Terraza", 5));

        PlatosRepositorioFalso platosRepo = new PlatosRepositorioFalso();
        platosRepo.platos.add(new Platos(1, "Arepa", new BigDecimal("10.00"), LocalDate.now().toString()));

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso() {
            @Override public java.util.Map<Integer, Integer> contarMesasOcupadasPorSala() { return new java.util.HashMap<>(); }
        };

        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, rbacAdmin));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, rbacAdmin));
        
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle, () -> new Config(),
                new GeneradorPdfPedido(tempDir), f -> true);
        
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo), pdfServicio, rbacAdmin,
                new ConsultaPedidosServicio(pedidosRepo, rbacAdmin));

        SwingUtilities.invokeAndWait(() -> {
            try {
                sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @After
    public void tearDown() {
        if (sistema != null) sistema.dispose();
    }

    @Test
    public void testSistemaMultiHibrido() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Access private fields using reflection
                java.lang.reflect.Field tblPlatosField = Sistema.class.getDeclaredField("tblTemPlatos");
                tblPlatosField.setAccessible(true);
                JTable tblPlatos = (JTable) tblPlatosField.get(sistema);
                
                java.lang.reflect.Field tableMenuField = Sistema.class.getDeclaredField("tableMenu");
                tableMenuField.setAccessible(true);
                JTable tableMenu = (JTable) tableMenuField.get(sistema);
                
                java.lang.reflect.Field txtCantidadField = Sistema.class.getDeclaredField("txtCantidadManual");
                txtCantidadField.setAccessible(true);
                JTextField txtCantidad = (JTextField) txtCantidadField.get(sistema);
                
                java.lang.reflect.Field btnMasField = Sistema.class.getDeclaredField("btnMasCantidad");
                btnMasField.setAccessible(true);
                JButton btnMas = (JButton) btnMasField.get(sistema);

                // Add plate to menu list
                javax.swing.table.DefaultTableModel tmPlatos = (javax.swing.table.DefaultTableModel) tblPlatos.getModel();
                tmPlatos.addRow(new Object[]{1, "Arepa", "10.00"});
                
                // Add to cart manually by simulating click logic (calling add action)
                // Actually the system has btnAddPlato, let's trigger it
                java.lang.reflect.Field btnAddField = Sistema.class.getDeclaredField("btnAddPlato");
                btnAddField.setAccessible(true);
                JButton btnAdd = (JButton) btnAddField.get(sistema);
                
                tblPlatos.setRowSelectionInterval(0, 0);
                btnAdd.doClick(); // Should add 1 Arepa to cart
                
                assertEquals(1, tableMenu.getRowCount());
                assertEquals("1", tableMenu.getValueAt(0, 2).toString());
                
                // Select in cart
                tableMenu.setRowSelectionInterval(0, 0);
                
                // Simulate + click
                btnMas.doClick();
                assertEquals("2", tableMenu.getValueAt(0, 2).toString());
                assertEquals("2", txtCantidad.getText());
                
                // Set manual quantity via input
                txtCantidad.setText("15");
                java.awt.event.KeyEvent enterEvent = new java.awt.event.KeyEvent(
                        txtCantidad, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n');
                java.awt.event.KeyListener[] listeners = txtCantidad.getKeyListeners();
                for (java.awt.event.KeyListener l : listeners) {
                    l.keyPressed(enterEvent);
                }
                
                // Verify update
                assertEquals("15", tableMenu.getValueAt(0, 2).toString());
                
                // Find grid buttons
                // They are inside the pnlBotonesCantidad hierarchy, let's just trigger applying manually
                java.lang.reflect.Method aplicarMethod = Sistema.class.getDeclaredMethod("aplicarCantidadManual", String.class, int.class);
                aplicarMethod.setAccessible(true);
                aplicarMethod.invoke(sistema, "8", 0);
                
                assertEquals("8", tableMenu.getValueAt(0, 2).toString());
                assertEquals("8", txtCantidad.getText());
                
            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        });
    }
}
