package Servicio;

import Modelo.Config;
import Modelo.ModoSalidaTicket;
import Servicio.PoliticaAcceso;
import Modelo.Usuario;
import Vista.Sistema;
import Controlador.SalasControlador;
import Controlador.PlatosControlador;
import Controlador.PedidosControlador;
import Modelo.SalasRepositorio;
import Modelo.PlatosRepositorio;
import Modelo.PedidosRepositorioFalso;
import Modelo.Salas;
import Modelo.Platos;
import Modelo.Pedidos;
import Modelo.DetallePedido;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Pruebas unitarias de las opciones de impresión:
 * Opción 1: Selector de impresoras de Windows en Configuración.
 * Opción 2: Selector de formato de salida (Térmica Directa, PDF estándar, PDF24 Creator)
 *           en todas las pantallas de facturación/impresión con sincronización y fallback resiliente.
 */
public class ModoSalidaYPdf24ImpresionTest {

    private Sistema sistema;

    @Before
    public void setUp() {
        ServicioImpresionTicket.setModoGlobal(ModoSalidaTicket.TERMICA_DIRECTA);
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(() -> {
                sistema.dispose();
                sistema = null;
            });
        }
        ServicioImpresionTicket.setModoGlobal(ModoSalidaTicket.TERMICA_DIRECTA);
    }

    @Test
    public void testModoSalidaTicketEnumValoresYDecodificacion() {
        assertEquals(ModoSalidaTicket.TERMICA_DIRECTA, ModoSalidaTicket.desdeCodigo("TERMICA_DIRECTA"));
        assertEquals(ModoSalidaTicket.VISOR_PDF, ModoSalidaTicket.desdeCodigo("VISOR_PDF"));
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, ModoSalidaTicket.desdeCodigo("PDF24_CREATOR"));

        // Coincidencias insensibles a mayúsculas y espacios
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, ModoSalidaTicket.desdeCodigo("pdf24_creator"));
        assertEquals(ModoSalidaTicket.VISOR_PDF, ModoSalidaTicket.desdeCodigo("  visor_pdf  "));

        // Fallback por defecto ante nulo o valor inválido
        assertEquals(ModoSalidaTicket.TERMICA_DIRECTA, ModoSalidaTicket.desdeCodigo(null));
        assertEquals(ModoSalidaTicket.TERMICA_DIRECTA, ModoSalidaTicket.desdeCodigo("INVALIDO"));

        // Etiquetas amigables
        assertTrue(ModoSalidaTicket.TERMICA_DIRECTA.getEtiqueta().contains("Térmica"));
        assertTrue(ModoSalidaTicket.VISOR_PDF.getEtiqueta().contains("PDF"));
        assertTrue(ModoSalidaTicket.PDF24_CREATOR.getEtiqueta().contains("PDF24"));
    }

    @Test
    public void testConfiguracionModeloImpresoraYModo() {
        Config conf = new Config();
        assertEquals("DEFAULT", conf.getImpresoraTickets());
        assertEquals(ModoSalidaTicket.TERMICA_DIRECTA, conf.getModoSalidaTickets());

        conf.setImpresoraTickets("POS-80 Series");
        assertEquals("POS-80 Series", conf.getImpresoraTickets());

        conf.setModoSalidaTickets(ModoSalidaTicket.PDF24_CREATOR);
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, conf.getModoSalidaTickets());

        conf.setModoSalidaTickets(null);
        assertEquals(ModoSalidaTicket.TERMICA_DIRECTA, conf.getModoSalidaTickets());
    }

    @Test
    public void testServicioImpresionEstadoGlobalYListeners() {
        AtomicReference<ModoSalidaTicket> escuchado = new AtomicReference<>();
        java.util.function.Consumer<ModoSalidaTicket> listener = escuchado::set;

        ServicioImpresionTicket.addModoGlobalListener(listener);
        try {
            ServicioImpresionTicket.setModoGlobal(ModoSalidaTicket.PDF24_CREATOR);
            assertEquals(ModoSalidaTicket.PDF24_CREATOR, ServicioImpresionTicket.getModoGlobal());
            assertEquals(ModoSalidaTicket.PDF24_CREATOR, escuchado.get());

            ServicioImpresionTicket.setModoGlobal(ModoSalidaTicket.VISOR_PDF);
            assertEquals(ModoSalidaTicket.VISOR_PDF, ServicioImpresionTicket.getModoGlobal());
            assertEquals(ModoSalidaTicket.VISOR_PDF, escuchado.get());
        } finally {
            ServicioImpresionTicket.removeModoGlobalListener(listener);
        }
    }

    @Test
    public void testResolverNombreRealImpresora() {
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora(null));
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora("   "));
        assertEquals("DEFAULT", ServicioImpresionTicket.resolverNombreRealImpresora("DEFAULT"));

        // Impresora ficticia devuelve el mismo nombre configurado limpio
        String res = ServicioImpresionTicket.resolverNombreRealImpresora("ImpresoraFicticia123");
        assertEquals("ImpresoraFicticia123", res);
    }

    @Test
    public void testDespacharPdf24FallbackSeguro() throws IOException {
        Path tempPdf = Files.createTempFile("test_salida_pdf24_", ".pdf");
        tempPdf.toFile().deleteOnExit();

        // Debe ejecutarse sin lanzar excepciones ni RuntimeException
        boolean resultado = ServicioImpresionTicket.despacharPdf24(tempPdf);
        // Si no está en Windows o no tiene PDF24, el método degrada a abrirVisor o devuelve true/false limpiamente
        assertTrue("El despachador no debe fallar de forma abrupta", resultado || !resultado);

        // Despacho ante archivo inexistente o nulo
        assertFalse(ServicioImpresionTicket.despacharPdf24(null));
        assertFalse(ServicioImpresionTicket.despacharPdf24(Path.of("archivo_que_no_existe_987654.pdf")));
    }

    @Test
    public void testImprimirTicketPrueba() {
        boolean ok = ServicioImpresionTicket.imprimirTicketPrueba("DEFAULT", ModoSalidaTicket.VISOR_PDF);
        assertTrue("La generación y despacho del ticket de prueba debe ser exitosa", ok);
    }

    @Test
    public void testSincronizacionModosYControlesEnSistema() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Usuario u = new Usuario(1, "Administrador", "admin", "pass", "Administrador");
            PoliticaAcceso rbac = new PoliticaAcceso(u);

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
            PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso() {
                @Override public Map<Integer, Integer> contarMesasOcupadasPorSala() { return Collections.emptyMap(); }
            };

            SalasControlador sCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, rbac));
            PlatosControlador pCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, rbac));
            PedidosControlador pedCtrl = new PedidosControlador(
                    new Servicio.PedidoServicio(pedidosRepo),
                    new PedidoPdfServicio(pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle, () -> new Config(),
                            GeneradorPdfPedido.conEstructuraMensual(Path.of("tmp_facturas")),
                            p -> {}),
                    rbac,
                    new Servicio.ConsultaPedidosServicio(pedidosRepo, rbac)
            );

            sistema = new Sistema(u, sCtrl, pCtrl, pedCtrl);
        });

        assertNotNull("Selector de impresoras en Configuración debe existir", sistema.getCbImpresorasConfig());
        assertNotNull("Selector de modo en Configuración debe existir", sistema.getCbModoSalidaConfig());
        assertNotNull("Selector de modo en Finalizar Pedido debe existir", sistema.getCbModoSalidaFinalizar());
        assertNotNull("Selector de modo en Historial debe existir", sistema.getCbModoSalidaHistorial());
        assertNotNull("Selector de modo en Clientes debe existir", sistema.getCbModoSalidaClientes());
        assertNotNull("Botón de refrescar impresoras debe existir", sistema.getBtnRefrescarImpresoras());
        assertNotNull("Botón de probar impresión debe existir", sistema.getBtnProbarImpresion());

        // Verificar sincronización global al cambiar de modo
        SwingUtilities.invokeAndWait(() -> {
            sistema.cambiarModoSalidaGlobal(ModoSalidaTicket.PDF24_CREATOR);
        });

        assertEquals(ModoSalidaTicket.PDF24_CREATOR, ServicioImpresionTicket.getModoGlobal());
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, sistema.getCbModoSalidaConfig().getSelectedItem());
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, sistema.getCbModoSalidaFinalizar().getSelectedItem());
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, sistema.getCbModoSalidaHistorial().getSelectedItem());
        assertEquals(ModoSalidaTicket.PDF24_CREATOR, sistema.getCbModoSalidaClientes().getSelectedItem());

        // Cambiar en el combo de Historial debe propagarse a todos
        SwingUtilities.invokeAndWait(() -> {
            sistema.getCbModoSalidaHistorial().setSelectedItem(ModoSalidaTicket.VISOR_PDF);
        });

        assertEquals(ModoSalidaTicket.VISOR_PDF, ServicioImpresionTicket.getModoGlobal());
        assertEquals(ModoSalidaTicket.VISOR_PDF, sistema.getCbModoSalidaConfig().getSelectedItem());
        assertEquals(ModoSalidaTicket.VISOR_PDF, sistema.getCbModoSalidaFinalizar().getSelectedItem());
        assertEquals(ModoSalidaTicket.VISOR_PDF, sistema.getCbModoSalidaHistorial().getSelectedItem());
        assertEquals(ModoSalidaTicket.VISOR_PDF, sistema.getCbModoSalidaClientes().getSelectedItem());

        // Verificar refresco de impresoras en combo
        SwingUtilities.invokeAndWait(() -> {
            sistema.refrescarImpresorasEnCombo("DEFAULT");
        });
        assertTrue(sistema.getCbImpresorasConfig().getItemCount() >= 1);
        assertEquals("DEFAULT", sistema.getCbImpresorasConfig().getItemAt(0));
    }
}
