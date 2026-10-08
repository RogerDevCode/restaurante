package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.LoginDao;
import Modelo.Pedidos;
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
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de Pruebas Unitarias Adversariales para los controles de cantidad del Carrito (Sprint 1).
 *
 * Misión Adversarial:
 * 1. Llamadas sin fila seleccionada (getSelectedRow == -1, tabla vacía o selección limpia).
 * 2. Valores inválidos o no numéricos en columna Cant ("abc", "", "   ", "1.5", null).
 * 3. Situaciones límite y desbordamiento en columna Cant (Integer.MAX_VALUE, cantidades negativas o cero).
 * 4. Formatos y valores extremos en columna Precio ("$ 0", "$ 9999999.99", "$ 10.50", "invalido", null).
 * 5. Múltiples filas: modificación de fila intermedia, primera o última.
 * 6. Disminución de cantidad hasta eliminación (1 fila vs múltiples filas, reajuste de índice, sin IndexOutOfBoundsException).
 * 7. Incrementos repetidos hasta cantidades grandes.
 * 8. Sincronización con btnEliminarTempPlato y LimpiarTableMenu.
 * 9. Selección múltiple y no-atomicidad en actualizaciones.
 */
public class AdversarialCarritoCantidadTest {

    private Usuario admin;
    private SalasRepositorioFalso salasRepo;
    private PlatosRepositorioFalso platosRepo;
    private PedidosRepositorioTestDouble pedidosRepo;
    private LoginDaoFalso loginDaoFalso;

    private SalasControlador salasCtrl;
    private PlatosControlador platosCtrl;
    private PedidosControlador pedidosCtrl;

    private Path tempDir;
    private Sistema sistema;
    private JTable tableMenu;
    private JLabel totalMenu;
    private JButton btnEliminarTempPlato;

    private static java.util.Timer autoDismissTimer;

    @BeforeClass
    public static void setupAutoDismiss() {
        autoDismissTimer = new java.util.Timer("adversarial-modal-dismisser", true);
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
        }, 30, 30);
    }

    @AfterClass
    public static void teardownAutoDismiss() {
        if (autoDismissTimer != null) {
            autoDismissTimer.cancel();
        }
    }

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("adversarial-carrito-test-");

        admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");

        salasRepo = new SalasRepositorioFalso();
        salasRepo.salas.add(new Salas(1, "Principal", 10));

        platosRepo = new PlatosRepositorioFalso();
        platosRepo.platos.add(new Platos(1, "Hamburguesa Clásica", new BigDecimal("12.00"), LocalDate.now().toString()));
        platosRepo.platos.add(new Platos(2, "Papas Fritas", new BigDecimal("4.50"), LocalDate.now().toString()));
        platosRepo.platos.add(new Platos(3, "Refresco", new BigDecimal("2.00"), LocalDate.now().toString()));

        pedidosRepo = new PedidosRepositorioTestDouble();
        loginDaoFalso = new LoginDaoFalso();

        PoliticaAcceso rbac = new PoliticaAcceso(admin);
        salasCtrl = new SalasControlador(new SalasServicio(salasRepo, rbac));
        platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, rbac));

        GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(tempDir);
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosRepo::verPedido,
                pedidosRepo::verPedidoDetalle,
                () -> loginDaoFalso.datosEmpresa(),
                generadorPdf,
                f -> {}
        );
        ConsultaPedidosServicio consultaServicio = new ConsultaPedidosServicio(pedidosRepo, rbac);
        pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicio,
                rbac,
                consultaServicio
        );

        AtomicReference<Sistema> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Sistema s = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, loginDaoFalso);
            ref.set(s);
        });
        sistema = ref.get();

        tableMenu = campo(sistema, "tableMenu", JTable.class);
        totalMenu = campo(sistema, "totalMenu", JLabel.class);
        btnEliminarTempPlato = campo(sistema, "btnEliminarTempPlato", JButton.class);

        // Aseguramos que el carrito inicie completamente vacío y con botones deshabilitados
        SwingUtilities.invokeAndWait(() -> {
            invocar(sistema, "LimpiarTableMenu", new Class<?>[]{}, new Object[]{});
        });
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
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

    // =========================================================================
    // Helpers para manipulación del carrito
    // =========================================================================

    private void agregarFilaAlCarrito(Object id, Object nombre, Object cant, Object precio, Object subtotal) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tableMenu.getModel();
            model.addRow(new Object[]{id, nombre, cant, precio, subtotal, ""});
            invocar(sistema, "TotalPagar", new Class<?>[]{JTable.class, JLabel.class}, new Object[]{tableMenu, totalMenu});
            sistema.actualizarEstadoBotonesCarrito();
        });
    }

    private void seleccionarFila(int fila) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (fila >= 0 && fila < tableMenu.getRowCount()) {
                tableMenu.setRowSelectionInterval(fila, fila);
            } else {
                tableMenu.clearSelection();
            }
            sistema.actualizarEstadoBotonesCarrito();
        });
    }

    // =========================================================================
    // GRUPO 1: Ataques sin fila seleccionada / Tabla vacía
    // =========================================================================

    @Test
    public void testSinSeleccion_TablaVacia_NoLanzaExcepcionYBotonesDeshabilitados() throws Exception {
        assertEquals(0, tableMenu.getRowCount());
        assertEquals(-1, tableMenu.getSelectedRow());

        // Verificar que los 3 botones inicien deshabilitados
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());
        assertFalse(btnEliminarTempPlato.isEnabled());

        // Ataque: invocar aumentar sin fila seleccionada
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals(0, tableMenu.getRowCount());
        assertFalse(sistema.getBtnMasCantidad().isEnabled());

        // Ataque: invocar disminuir sin fila seleccionada
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals(0, tableMenu.getRowCount());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());

        // Ataque: invocar eliminar sin fila seleccionada
        SwingUtilities.invokeAndWait(() -> btnEliminarTempPlato.doClick());
        assertEquals(0, tableMenu.getRowCount());
    }

    @Test
    public void testSinSeleccion_ConFilasPeroSeleccionLimpia_NoModificaFilasYBotonesDeshabilitados() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", 2, "12", "24");
        agregarFilaAlCarrito(2, "Papas Fritas", 1, "4.50", "4.50");

        seleccionarFila(-1); // Limpiar selección explícitamente

        assertEquals(2, tableMenu.getRowCount());
        assertEquals(-1, tableMenu.getSelectedRow());
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());
        assertFalse(btnEliminarTempPlato.isEnabled());

        // Invocar aumentar sin selección
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        // Verificar que las cantidades no cambiaron
        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals(1, tableMenu.getValueAt(1, 2));

        // Invocar disminuir sin selección
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals(2, tableMenu.getRowCount());
        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals(1, tableMenu.getValueAt(1, 2));
    }

    // =========================================================================
    // GRUPO 2: Valores no enteros, corruptos o malformados en 'Cant'
    // =========================================================================

    @Test
    public void testCantidadInvalida_TextoArbitrario_ManejoExcepcionSinCorrupcion() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", "invalido", "12", "12");
        seleccionarFila(0);

        // Aumentar con texto no numérico
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        // El valor no debe corromperse a número aleatorio ni borrar la fila
        assertEquals("invalido", tableMenu.getValueAt(0, 2));

        // Disminuir con texto no numérico
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals("invalido", tableMenu.getValueAt(0, 2));
        assertEquals(1, tableMenu.getRowCount());
    }

    @Test
    public void testCantidadInvalida_TextoVacioOEspacios_ManejoSeguro() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", "   ", "12", "12");
        seleccionarFila(0);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals("   ", tableMenu.getValueAt(0, 2));

        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals("   ", tableMenu.getValueAt(0, 2));
    }

    @Test
    public void testCantidadInvalida_DecimalEnCantidad_ManejoSeguro() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", "2.5", "12", "24");
        seleccionarFila(0);

        // Debe atrapar NumberFormatException porque cantidad debe ser un int
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals("2.5", tableMenu.getValueAt(0, 2));
    }

    @Test
    public void testCantidadInvalida_NullEnCantidad_ManejoSeguro() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", null, "12", "12");
        seleccionarFila(0);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        // Debe ser atrapado por el bloque try-catch sin crash
        assertEquals(null, tableMenu.getValueAt(0, 2));
    }

    // =========================================================================
    // GRUPO 3: Situaciones Límite y Desbordamiento en 'Cant'
    // =========================================================================

    @Test
    public void testDesbordamiento_IntegerMaxValue_ComportamientoAdversarial() throws Exception {
        // Inyectamos Integer.MAX_VALUE en la columna de cantidad
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", Integer.MAX_VALUE, "10", "10");
        seleccionarFila(0);

        // Al ejecutar aumentar: cantActual + 1 desborda en aritmética primitiva de Java
        // y se convierte en Integer.MIN_VALUE (-2147483648).
        // Al calcular nuevoSub = precio * nuevaCantidad, da -21474836480.
        // Al intentar TotalPagar con -21474836480, excede DECIMAL(10,2) y lanza ArithmeticException,
        // la cual es capturada en catch (Exception ex).
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        // Verificamos que la tabla no haya crasheado la JVM y documentamos el valor asignado
        assertNotNull(tableMenu.getValueAt(0, 2));
    }

    @Test
    public void testCantidadCero_DisminuirEliminaFila_AumentarSumaUno() throws Exception {
        // Escenario con cantidad inicial en 0 (estado anómalo)
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", 0, "10", "0");
        seleccionarFila(0);

        // Aumentar pasa de 0 a 1
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals(1, tableMenu.getValueAt(0, 2));
        assertEquals("10.00", tableMenu.getValueAt(0, 4));

        // Forzamos cantidad a 0 de nuevo
        SwingUtilities.invokeAndWait(() -> tableMenu.setValueAt(0, 0, 2));
        seleccionarFila(0);

        // Al llamar a disminuir con cantActual = 0: (cantActual > 1) es falso, por lo que elimina la fila
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals(0, tableMenu.getRowCount());
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
    }

    @Test
    public void testCantidadNegativa_Adversarial_ComportamientoObservado() throws Exception {
        // Escenario con cantidad negativa preexistente
        agregarFilaAlCarrito(1, "Hamburguesa Clásica", -2, "10", "0");
        seleccionarFila(0);

        // Aumentar normaliza de forma segura cualquier valor anómalo negativo a 1
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals(1, tableMenu.getValueAt(0, 2));

        // Disminuir desde 1: entra al bloque else y remueve la fila
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        assertEquals(0, tableMenu.getRowCount());
    }

    // =========================================================================
    // GRUPO 4: Formatos extremos y anómalos en columna 'Precio'
    // =========================================================================

    @Test
    public void testPrecioFormato_ConSignoDolarYEspacios_CalculaSubtotalYTotalCorrectamente() throws Exception {
        agregarFilaAlCarrito(1, "Hamburguesa Especial", 1, "$ 25", "25");
        seleccionarFila(0);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals("50.00", tableMenu.getValueAt(0, 4));
        assertTrue(totalMenu.getText().contains("50.00"));
    }

    @Test
    public void testPrecioFormato_ConDecimales_CalculaSubtotalConDecimalesYTotalRedondeado() throws Exception {
        agregarFilaAlCarrito(1, "Papas Fritas", 1, "$ 4.50", "4.50");
        seleccionarFila(0);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals("9.00", tableMenu.getValueAt(0, 4));
        assertTrue(totalMenu.getText().contains("9.00"));
    }

    @Test
    public void testPrecioFormato_CeroDolares_CalculaSubtotalCero() throws Exception {
        agregarFilaAlCarrito(1, "Plato Promo Gratis", 1, "$ 0", "0");
        seleccionarFila(0);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals("0.00", tableMenu.getValueAt(0, 4));
        assertEquals("$ 0.00", totalMenu.getText());
    }

    @Test
    public void testPrecioInvalido_TextoInvalidoONull_ManejoSeguro() throws Exception {
        agregarFilaAlCarrito(1, "Plato Malo", 1, "no_es_precio", "10");
        seleccionarFila(0);

        // importeMonetario lanzará validacion exception, atrapada por try-catch
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        // Cantidad no debió alterarse
        assertEquals(1, tableMenu.getValueAt(0, 2));

        // Ahora probamos con null en precio
        agregarFilaAlCarrito(2, "Plato Null", 1, null, "10");
        seleccionarFila(1);
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        assertEquals(1, tableMenu.getValueAt(1, 2));
    }

    // =========================================================================
    // GRUPO 5: Múltiples Filas (Intermedia, Primera, Última) y Reajuste de Selección
    // =========================================================================

    @Test
    public void testMultiplesFilas_ModificarFilaIntermedia_AfectaSoloFilaSeleccionada() throws Exception {
        agregarFilaAlCarrito(1, "Plato A", 1, "10", "10");
        agregarFilaAlCarrito(2, "Plato B", 2, "20", "40");
        agregarFilaAlCarrito(3, "Plato C", 3, "5", "15");

        // Seleccionar fila intermedia (índice 1)
        seleccionarFila(1);

        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        // Fila 0 intacta
        assertEquals(1, tableMenu.getValueAt(0, 2));
        assertEquals("10", tableMenu.getValueAt(0, 4));

        // Fila 1 incrementada
        assertEquals(3, tableMenu.getValueAt(1, 2));
        assertEquals("60.00", tableMenu.getValueAt(1, 4));

        // Fila 2 intacta
        assertEquals(3, tableMenu.getValueAt(2, 2));
        assertEquals("15", tableMenu.getValueAt(2, 4));

        // Total: 10 + 60 + 15 = 85
        assertEquals("$ 85.00", totalMenu.getText());

        // La selección debe seguir en la fila 1
        assertEquals(1, tableMenu.getSelectedRow());
    }

    @Test
    public void testDisminucion_TresFilas_EliminarFilaIntermedia_ReajusteSeleccion() throws Exception {
        agregarFilaAlCarrito(1, "Plato A", 1, "10", "10");
        agregarFilaAlCarrito(2, "Plato B", 1, "20", "20");
        agregarFilaAlCarrito(3, "Plato C", 1, "30", "30");

        // Seleccionar fila intermedia (1) y disminuir cuando cantidad es 1 -> debe eliminarse
        seleccionarFila(1);
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());

        assertEquals(2, tableMenu.getRowCount());
        assertEquals("Plato A", tableMenu.getValueAt(0, 1));
        assertEquals("Plato C", tableMenu.getValueAt(1, 1));

        // La selección debe reajustarse al índice 1 (que ahora es Plato C) sin desbordar
        assertEquals(1, tableMenu.getSelectedRow());
        assertTrue(sistema.getBtnMasCantidad().isEnabled());
        assertTrue(sistema.getBtnMenosCantidad().isEnabled());
        assertEquals("$ 40.00", totalMenu.getText());
    }

    @Test
    public void testDisminucion_DosFilas_EliminarUltimaFila_ReajusteAIndiceAnterior() throws Exception {
        agregarFilaAlCarrito(1, "Plato A", 1, "10", "10");
        agregarFilaAlCarrito(2, "Plato B", 1, "20", "20");

        // Seleccionar última fila (índice 1) y disminuir -> se elimina
        seleccionarFila(1);
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());

        assertEquals(1, tableMenu.getRowCount());
        assertEquals("Plato A", tableMenu.getValueAt(0, 1));

        // Reajuste al nuevo límite superior: índice 0
        assertEquals(0, tableMenu.getSelectedRow());
        assertTrue(sistema.getBtnMasCantidad().isEnabled());
        assertTrue(sistema.getBtnMenosCantidad().isEnabled());
        assertEquals("$ 10.00", totalMenu.getText());
    }

    @Test
    public void testDisminucion_UnaFila_EliminarUnicaFila_LimpiaSeleccionYDeshabilitaBotones() throws Exception {
        agregarFilaAlCarrito(1, "Plato Único", 1, "15", "15");

        seleccionarFila(0);
        assertTrue(sistema.getBtnMasCantidad().isEnabled());
        assertTrue(sistema.getBtnMenosCantidad().isEnabled());
        assertTrue(btnEliminarTempPlato.isEnabled());

        // Disminuir cuando solo queda 1 fila y cant es 1 -> tabla queda vacía
        SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());

        assertEquals(0, tableMenu.getRowCount());
        assertEquals(-1, tableMenu.getSelectedRow());

        // Botones deben quedar todos deshabilitados
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());
        assertFalse(btnEliminarTempPlato.isEnabled());
        assertEquals("$ 0.00", totalMenu.getText());
    }

    // =========================================================================
    // GRUPO 6: Operaciones Repetitivas y Grandes Volúmenes
    // =========================================================================

    @Test
    public void testAumentoRepetido_CantidadesGrandes_ConsistenciaTotal() throws Exception {
        agregarFilaAlCarrito(1, "Combo Familiar", 1, "15", "15");
        seleccionarFila(0);

        // Aumentar 20 veces
        for (int i = 0; i < 20; i++) {
            SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());
        }

        assertEquals(21, tableMenu.getValueAt(0, 2));
        assertEquals("315.00", tableMenu.getValueAt(0, 4)); // 21 * 15 = 315
        assertEquals("$ 315.00", totalMenu.getText());
        assertEquals(0, tableMenu.getSelectedRow());

        // Disminuir 10 veces
        for (int i = 0; i < 10; i++) {
            SwingUtilities.invokeAndWait(() -> sistema.disminuirCantidadSeleccionada());
        }

        assertEquals(11, tableMenu.getValueAt(0, 2));
        assertEquals("165.00", tableMenu.getValueAt(0, 4)); // 11 * 15 = 165
        assertEquals("$ 165.00", totalMenu.getText());
        assertEquals(0, tableMenu.getSelectedRow());
    }

    // =========================================================================
    // GRUPO 7: Sincronización con btnEliminarTempPlato y LimpiarTableMenu
    // =========================================================================

    @Test
    public void testSincronizacion_BtnEliminarTempPlato_ComportamientoConsistente() throws Exception {
        agregarFilaAlCarrito(1, "Plato 1", 3, "10", "30");
        agregarFilaAlCarrito(2, "Plato 2", 1, "5", "5");

        seleccionarFila(1);
        assertTrue(btnEliminarTempPlato.isEnabled());

        // Clic en botón eliminar
        SwingUtilities.invokeAndWait(() -> btnEliminarTempPlato.doClick());

        assertEquals(1, tableMenu.getRowCount());
        assertEquals(0, tableMenu.getSelectedRow());
        assertEquals("$ 30.00", totalMenu.getText());
        assertTrue(sistema.getBtnMasCantidad().isEnabled());

        // Eliminar la última fila restante
        SwingUtilities.invokeAndWait(() -> btnEliminarTempPlato.doClick());

        assertEquals(0, tableMenu.getRowCount());
        assertEquals(-1, tableMenu.getSelectedRow());
        assertEquals("$ 0.00", totalMenu.getText());
        assertFalse(btnEliminarTempPlato.isEnabled());
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());
    }

    @Test
    public void testSincronizacion_LimpiarTableMenu_ReseteaTodo() throws Exception {
        agregarFilaAlCarrito(1, "Plato 1", 5, "10", "50");
        agregarFilaAlCarrito(2, "Plato 2", 2, "20", "40");
        seleccionarFila(0);

        assertTrue(sistema.getBtnMasCantidad().isEnabled());

        // Limpiar tabla por completo
        SwingUtilities.invokeAndWait(() -> {
            invocar(sistema, "LimpiarTableMenu", new Class<?>[]{}, new Object[]{});
        });

        assertEquals(0, tableMenu.getRowCount());
        assertEquals(-1, tableMenu.getSelectedRow());
        assertEquals("$ 0.00", totalMenu.getText());
        assertFalse(sistema.getBtnMasCantidad().isEnabled());
        assertFalse(sistema.getBtnMenosCantidad().isEnabled());
        assertFalse(btnEliminarTempPlato.isEnabled());
    }

    // =========================================================================
    // GRUPO 8: Comportamiento ante Selección Múltiple
    // =========================================================================

    @Test
    public void testSeleccionMultiple_Adversarial_ColapsaSeleccionAFilaUnica() throws Exception {
        agregarFilaAlCarrito(1, "Plato A", 1, "10", "10");
        agregarFilaAlCarrito(2, "Plato B", 1, "20", "20");
        agregarFilaAlCarrito(3, "Plato C", 1, "30", "30");

        // Simular que el usuario seleccionó múltiples filas simultáneamente (filas 0 y 1)
        SwingUtilities.invokeAndWait(() -> {
            tableMenu.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            tableMenu.addRowSelectionInterval(0, 1);
        });

        assertEquals(2, tableMenu.getSelectedRowCount());
        assertEquals(0, tableMenu.getSelectedRow());

        // Al presionar aumentar, debe afectar solo a la primera fila seleccionada
        // y colapsar la selección a esa sola fila (evitando inconsistencias visuales)
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        assertEquals(2, tableMenu.getValueAt(0, 2));
        assertEquals("20.00", tableMenu.getValueAt(0, 4));
        assertEquals(1, tableMenu.getValueAt(1, 2)); // Fila 1 no se tocó
        assertEquals(1, tableMenu.getSelectedRowCount());
        assertEquals(0, tableMenu.getSelectedRow());
    }

    // =========================================================================
    // GRUPO 9: Subtotal Corrupto en otra Fila (Atomicidad)
    // =========================================================================

    @Test
    public void testSubtotalCorruptoEnOtraFila_AlAumentarFila0_ManejoSeguro() throws Exception {
        agregarFilaAlCarrito(1, "Plato A", 1, "10", "10");
        // Inyectamos fila con subtotal corrupto
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = (DefaultTableModel) tableMenu.getModel();
            model.addRow(new Object[]{2, "Plato B Corrupto", 1, "10", "no_monetario", ""});
        });

        seleccionarFila(0);

        // Al aumentar fila 0: fila 0 se incrementa a 2, pero TotalPagar falla al leer "no_monetario" en fila 1.
        // La excepción es capturada en el try-catch de aumentarCantidadSeleccionada().
        SwingUtilities.invokeAndWait(() -> sistema.aumentarCantidadSeleccionada());

        // Verificamos que el sistema no se derrumbó con excepción no controlada
        assertNotNull(tableMenu.getValueAt(0, 2));
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
    // Clases Fake / Stubs para aislamiento de prueba
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
        @Override public boolean registrar(Platos plato) { return platos.add(plato); }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return new ArrayList<>(platos); }
        @Override public boolean eliminar(int id) { return platos.removeIf(p -> p.getId() == id); }
        @Override public boolean modificar(Platos plato) { return true; }
    }

    private static class PedidosRepositorioTestDouble extends PedidosRepositorioFalso {
        @Override
        public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
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
