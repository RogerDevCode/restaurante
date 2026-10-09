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
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas unitarias para las 7 mejoras de usabilidad, integridad y presentación.
 */
public class MejorasUIYServicioTest {

    @Rule
    public final TemporaryFolder carpetaTemporal = new TemporaryFolder();

    private final Usuario usuarioAdmin = new Usuario(1, "Carlos Admin", "admin@rest.com", "pass", "Administrador");

    // =========================================================================
    // 1. Título de ventana con ortografía correcta y usuario logueado
    // =========================================================================
    @Test
    public void testTituloVentanaCorrigeOrtografiaYMuestraUsuario() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            String titulo = sistema.getTitle();
            assertNotNull(titulo);
            assertTrue("Debe contener 'Panel de Administración' (con tilde y sin typo)",
                    titulo.startsWith("Panel de Administración"));
            assertTrue("Debe mostrar el nombre del usuario logueado",
                    titulo.contains("Carlos Admin"));
            assertFalse("No debe contener el viejo typo 'Adminstración'",
                    titulo.contains("Adminstración"));
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 2. TablePedidos sortable
    // =========================================================================
    @Test
    public void testTablePedidosTieneRowSorterHabilitado() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
            assertNotNull("TablePedidos debe existir", tablePedidos);
            assertNotNull("TablePedidos debe tener RowSorter configurado para permitir ordenar columnas",
                    tablePedidos.getRowSorter());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 3. Auto-refresh de historial al cambiar al Tab de Ventas (Tab 5)
    // =========================================================================
    @Test
    public void testAutoRefreshHistorialAlSeleccionarTabVentas() throws Exception {
        AtomicBoolean consultoHistorial = new AtomicBoolean(false);

        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public List<Pedidos> listarPedidos() {
                consultoHistorial.set(true);
                Pedidos p = new Pedidos();
                p.setId(101);
                p.setSala("Salón Central");
                p.setUsuario("Carlos Admin");
                p.setNum_mesa(3);
                p.setFecha("2026-10-06");
                p.setTotalDecimal(new BigDecimal("25.00"));
                p.setEstado("FINALIZADO");
                return Collections.singletonList(p);
            }

            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                return Collections.emptyMap();
            }
        };

        Sistema sistema = crearSistemaConRepositorio(usuarioAdmin, repositorio);
        try {
            JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);

            // Cambiar a la pestaña 5 (Historial Pedidos)
            SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(5));

            // Esperar propagación del ChangeListener en SwingWorker
            for (int i = 0; i < 20 && !consultoHistorial.get(); i++) {
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> { });
            }

            assertTrue("El cambio a la pestaña 5 debe haber disparado la consulta del historial",
                    consultoHistorial.get());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 4. Búsqueda y filtrado en Historial de Pedidos (txtBuscarHistorial)
    // =========================================================================
    @Test
    public void testFiltroHistorialPedidosPorTexto() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            JTable tablePedidos = campo(sistema, "TablePedidos", JTable.class);
            JTextField txtBuscar = campo(sistema, "txtBuscarHistorial", JTextField.class);
            assertNotNull("El campo de búsqueda del historial debe existir", txtBuscar);

            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel modelo = (DefaultTableModel) tablePedidos.getModel();
                modelo.setRowCount(0);
                modelo.addRow(new Object[]{1, "Terraza", "Carlos Admin", 2, "2026-10-06", "$ 15.00", "PENDIENTE"});
                modelo.addRow(new Object[]{2, "VIP", "Mesero Juan", 4, "2026-10-06", "$ 80.00", "FINALIZADO"});
                modelo.addRow(new Object[]{3, "Terraza", "Carlos Admin", 5, "2026-10-06", "$ 30.00", "FINALIZADO"});
                tablePedidos.setAutoCreateRowSorter(true);
            });

            assertEquals(3, tablePedidos.getRowCount());

            // Simular búsqueda por "VIP"
            SwingUtilities.invokeAndWait(() -> {
                txtBuscar.setText("VIP");
                for (java.awt.event.KeyListener l : txtBuscar.getKeyListeners()) {
                    l.keyReleased(new java.awt.event.KeyEvent(txtBuscar, 0, 0, 0, 0, ' '));
                }
            });

            assertEquals("Solo debe quedar 1 fila visible para 'VIP'", 1, tablePedidos.getRowCount());
            assertEquals("VIP", tablePedidos.getValueAt(0, 1));

            // Limpiar búsqueda
            SwingUtilities.invokeAndWait(() -> {
                txtBuscar.setText("");
                for (java.awt.event.KeyListener l : txtBuscar.getKeyListeners()) {
                    l.keyReleased(new java.awt.event.KeyEvent(txtBuscar, 0, 0, 0, 0, ' '));
                }
            });

            assertEquals("Deben volver a verse las 3 filas", 3, tablePedidos.getRowCount());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 5. PDF con timestamp y respaldo para evitar sobreescritura
    // =========================================================================
    @Test
    public void testGeneradorPdfTimestampYRespaldo() throws Exception {
        Path dirSalida = carpetaTemporal.newFolder("pdf_test").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(dirSalida);

        Pedidos pedido = new Pedidos();
        pedido.setId(55);
        pedido.setFecha("2026-10-06");
        pedido.setSala("Terraza");
        pedido.setNum_mesa(2);
        pedido.setTotalDecimal(new BigDecimal("10.00"));
        pedido.setUsuario("Carlos Admin");

        Config config = new Config(1, "Restaurante Demo", "J-12345678", "04141234567",
                "Calle 1", "Mensaje Demo", new BigDecimal("16.00"), new BigDecimal("36.50"));

        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Hamburguesa");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("10.00"));
        List<DetallePedido> detalles = Collections.singletonList(detalle);

        // 1. Generar con timestamp explícito
        Path pdfTimestamp = generador.generarConTimestamp(pedido, config, detalles);
        assertTrue(Files.exists(pdfTimestamp));
        assertTrue(pdfTimestamp.getFileName().toString().startsWith("pedido-55_"));
        assertTrue(pdfTimestamp.getFileName().toString().endsWith(".pdf"));

        // 2. Generar estándar inicial
        Path pdfOriginal = generador.generar(pedido, config, detalles);
        assertEquals(dirSalida.resolve("pedido-55.pdf"), pdfOriginal);
        assertTrue(Files.exists(pdfOriginal));

        // 3. Regenerar el mismo pedido: debe crear un respaldo con timestamp del previo sin borrar nada
        Path pdfRegenerado = generador.generar(pedido, config, detalles);
        assertEquals(dirSalida.resolve("pedido-55.pdf"), pdfRegenerado);
        assertTrue(Files.exists(pdfRegenerado));

        // Debe haber al menos 3 archivos en la carpeta: el timestamped inicial, el respaldo, y el actual
        File[] archivos = dirSalida.toFile().listFiles();
        assertNotNull(archivos);
        assertTrue("Debe haber creado archivos de respaldo con timestamp", archivos.length >= 3);
    }

    // =========================================================================
    // 6. Conteo de mesas ocupadas por sala (DAO / Servicio / Controlador)
    // =========================================================================
    @Test
    public void testContarMesasOcupadasPorSalaServicioYControlador() {
        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                Map<Integer, Integer> mapa = new HashMap<>();
                mapa.put(1, 3);
                mapa.put(2, 0);
                return mapa;
            }
        };

        PoliticaAcceso rbac = new PoliticaAcceso(usuarioAdmin);
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(repositorio, rbac);
        PedidosControlador ctrl = new PedidosControlador(
                new PedidoServicio(repositorio),
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> true),
                rbac,
                servicio
        );

        Map<Integer, Integer> resultado = ctrl.contarMesasOcupadasPorSala();
        assertNotNull(resultado);
        assertEquals(Integer.valueOf(3), resultado.get(1));
        assertEquals(Integer.valueOf(0), resultado.get(2));
    }

    // =========================================================================
    // 7. Botones de sala muestran badge de ocupación
    // =========================================================================
    @Test
    public void testBotonesSalasMuestranBadgeDeMesasOcupadas() throws Exception {
        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                Map<Integer, Integer> mapa = new HashMap<>();
                mapa.put(1, 2); // Sala 1 tiene 2 mesas ocupadas
                return mapa;
            }
        };

        Sistema sistema = crearSistemaConRepositorio(usuarioAdmin, repositorio);
        try {
            JPanel panelSalas = campo(sistema, "PanelSalas", JPanel.class);

            // Esperar que el SwingWorker de salas complete en EDT
            for (int i = 0; i < 30 && panelSalas.getComponentCount() < 1; i++) {
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(() -> { });
            }

            assertTrue(panelSalas.getComponentCount() >= 1);
            JButton btn1 = (JButton) panelSalas.getComponent(0);
            String texto = btn1.getText();
            assertNotNull(texto);
            assertTrue("Debe indicar las mesas ocupadas en el texto del botón",
                    texto.contains("ocupadas"));
            assertEquals("Terraza (2/10 ocupadas)", texto);
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // 8. Botones + / − para cantidades en el carrito y subtotal por línea
    // =========================================================================
    @Test
    public void testBotonesCantidadInicialmenteDeshabilitadosYSeHabilitanAlSeleccionarFila() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            JButton btnMas = sistema.getBtnMasCantidad();
            JButton btnMenos = sistema.getBtnMenosCantidad();
            JButton btnEliminar = campo(sistema, "btnEliminarTempPlato", JButton.class);
            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);

            assertNotNull("El botón + debe existir", btnMas);
            assertNotNull("El botón - debe existir", btnMenos);
            assertNotNull("El botón eliminar debe existir", btnEliminar);

            // Inicialmente sin filas seleccionadas
            SwingUtilities.invokeAndWait(() -> {
                tableMenu.clearSelection();
                sistema.actualizarEstadoBotonesCarrito();
            });
            assertFalse("El botón + debe estar deshabilitado sin selección", btnMas.isEnabled());
            assertFalse("El botón - debe estar deshabilitado sin selección", btnMenos.isEnabled());
            assertFalse("El botón eliminar debe estar deshabilitado sin selección", btnEliminar.isEnabled());

            // Agregar una fila al carrito
            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel modelo = (DefaultTableModel) tableMenu.getModel();
                modelo.setRowCount(0);
                modelo.addRow(new Object[]{1, "Pabellón Criollo", 1, "$ 10", "$ 10", ""});
                tableMenu.setRowSelectionInterval(0, 0);
            });

            assertTrue("El botón + debe habilitarse al seleccionar fila", btnMas.isEnabled());
            assertTrue("El botón - debe habilitarse al seleccionar fila", btnMenos.isEnabled());
            assertTrue("El botón eliminar debe habilitarse al seleccionar fila", btnEliminar.isEnabled());

            // Al deseleccionar se deben deshabilitar
            SwingUtilities.invokeAndWait(() -> {
                tableMenu.clearSelection();
            });
            assertFalse("El botón + debe deshabilitarse al limpiar selección", btnMas.isEnabled());
            assertFalse("El botón - debe deshabilitarse al limpiar selección", btnMenos.isEnabled());
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testAumentarCantidadActualizaSubtotalYTotalPagar() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);
            javax.swing.JLabel totalMenu = campo(sistema, "totalMenu", javax.swing.JLabel.class);

            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel modelo = (DefaultTableModel) tableMenu.getModel();
                modelo.setRowCount(0);
                modelo.addRow(new Object[]{1, "Arepa Reina Pepiada", 1, "$ 5", "$ 5", ""});
                tableMenu.setRowSelectionInterval(0, 0);
            });

            // Aumentar de 1 a 2
            SwingUtilities.invokeAndWait(sistema::aumentarCantidadSeleccionada);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals("La cantidad debe ser 2", 2, Integer.parseInt(tableMenu.getValueAt(0, 2).toString()));
                assertEquals("El subtotal debe ser 10", "10.00", tableMenu.getValueAt(0, 4).toString());
                assertTrue("El totalMenu debe reflejar 10", totalMenu.getText().contains("10"));
            });

            // Aumentar de 2 a 3
            SwingUtilities.invokeAndWait(sistema::aumentarCantidadSeleccionada);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals("La cantidad debe ser 3", 3, Integer.parseInt(tableMenu.getValueAt(0, 2).toString()));
                assertEquals("El subtotal debe ser 15", "15.00", tableMenu.getValueAt(0, 4).toString());
                assertTrue("El totalMenu debe reflejar 15", totalMenu.getText().contains("15"));
            });
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    @Test
    public void testDisminuirCantidadDecrementaOEliminaFilaYActualizaTotales() throws Exception {
        Sistema sistema = crearSistemaEnEdt(usuarioAdmin);
        try {
            JTable tableMenu = campo(sistema, "tableMenu", JTable.class);
            javax.swing.JLabel totalMenu = campo(sistema, "totalMenu", javax.swing.JLabel.class);

            SwingUtilities.invokeAndWait(() -> {
                DefaultTableModel modelo = (DefaultTableModel) tableMenu.getModel();
                modelo.setRowCount(0);
                modelo.addRow(new Object[]{1, "Cachapa con Queso", 2, "$ 8", "$ 16", ""});
                tableMenu.setRowSelectionInterval(0, 0);
            });

            // Disminuir de 2 a 1
            SwingUtilities.invokeAndWait(sistema::disminuirCantidadSeleccionada);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Debe quedar 1 fila", 1, tableMenu.getRowCount());
                assertEquals("La cantidad debe ser 1", 1, Integer.parseInt(tableMenu.getValueAt(0, 2).toString()));
                assertEquals("El subtotal debe ser 8", "8.00", tableMenu.getValueAt(0, 4).toString());
                assertTrue("El total debe ser 8", totalMenu.getText().contains("8"));
            });

            // Disminuir de 1 a 0 (debe remover la fila)
            SwingUtilities.invokeAndWait(sistema::disminuirCantidadSeleccionada);

            SwingUtilities.invokeAndWait(() -> {
                assertEquals("La tabla debe quedar vacía", 0, tableMenu.getRowCount());
                assertTrue("El total debe ser 0", totalMenu.getText().contains("0"));
                assertFalse("El botón - debe quedar deshabilitado", sistema.getBtnMenosCantidad().isEnabled());
            });
        } finally {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    // =========================================================================
    // Utilidades de apoyo para las pruebas
    // =========================================================================
    private Sistema crearSistemaEnEdt(Usuario usuario) throws Exception {
        return crearSistemaConRepositorio(usuario, new PedidosRepositorioFalso() {
            @Override
            public Map<Integer, Integer> contarMesasOcupadasPorSala() {
                return Collections.emptyMap();
            }
        });
    }

    private Sistema crearSistemaConRepositorio(Usuario usuario, PedidosRepositorio repositorioPedidos) throws Exception {
        final Sistema[] contenedor = new Sistema[1];
        PoliticaAcceso rbac = new PoliticaAcceso(usuario);

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Salas sl) { return true; }
            @Override public List<Salas> listar() {
                Salas s1 = new Salas(1, "Terraza", 10);
                Salas s2 = new Salas(2, "Salón VIP", 5);
                return List.of(s1, s2);
            }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Salas sl) { return true; }
        };

        PlatosRepositorio platosRepo = new PlatosRepositorio() {
            @Override public boolean registrar(Platos pla) { return true; }
            @Override public List<Platos> listarPorFecha(String filtro, String fecha) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Platos pla) { return true; }
        };

        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(repositorioPedidos, rbac);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(repositorioPedidos),
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
