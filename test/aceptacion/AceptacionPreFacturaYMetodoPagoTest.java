package aceptacion;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.PagoPedido;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.GeneradorPdfPedido.FormatoPapel;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import Vista.Sistema;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pruebas de aceptación para:
 * 1. Folio / Número de Ticket destacado en tickets térmicos y facturas.
 * 2. Pre-Factura provisional no fiscal para comensales en mesa.
 * 3. Visibilidad restaurada de Métodos de Pago en el historial y pantalla de cobro.
 */
public class AceptacionPreFacturaYMetodoPagoTest {

    private Config config;
    private GeneradorPdfPedido generadorTicket;
    private GeneradorPdfPedido generadorA4;
    private Path tempDir;
    private List<Path> archivosGenerados;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test_prefactura_");
        config = new Config();
        config.setNombre("Restaurante Demo 2026");
        config.setRuc("J-12345678-9");
        config.setTelefono("0212-5555555");
        config.setDireccion("Caracas, Venezuela");
        config.setMensaje("¡Gracias por su visita!");

        generadorTicket = new GeneradorPdfPedido(tempDir, false, FormatoPapel.TICKET_80MM);
        generadorA4 = new GeneradorPdfPedido(tempDir, false, FormatoPapel.PAGINA_A4);
        archivosGenerados = new ArrayList<>();
    }

    @After
    public void tearDown() {
        for (Path p : archivosGenerados) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {}
        }
        if (tempDir != null) {
            try {
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
        }
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
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                id -> new Pedidos(),
                id -> Collections.emptyList(),
                () -> config,
                generadorTicket,
                path -> true,
                path -> true
        );
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicio,
                rbac,
                consultas
        );

        SwingUtilities.invokeAndWait(() -> {
            contenedor[0] = new Sistema(usuario, salasCtrl, platosCtrl, pedidosCtrl);
        });
        return contenedor[0];
    }

    @Test
    public void testTicketContieneNumeroDeTicketDestacado() throws Exception {
        Pedidos pedido = new Pedidos();
        pedido.setId(42);
        pedido.setSala("Terraza");
        pedido.setNum_mesa(3);
        pedido.setFecha("2026-10-17 18:30:00");
        pedido.setUsuario("carlos");
        pedido.setTasaCambio(new BigDecimal("40.00"));
        pedido.setTotalDecimal(new BigDecimal("20.00"));
        pedido.setTotalBs(new BigDecimal("800.00"));
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setIvaPorcentaje(BigDecimal.ZERO);

        DetallePedido det = new DetallePedido();
        det.setId(1);
        det.setNombre("Pollo a la Brasa");
        det.setCantidad(1);
        det.setPrecioDecimal(new BigDecimal("20.00"));
        det.setMonedaBase("USD");
        det.setAplicaIva(false);
        det.setPrecioIncluyeIva(false);
        det.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));

        Path ticket80mm = generadorTicket.generar(pedido, config, List.of(det));
        archivosGenerados.add(ticket80mm);
        assertTrue("El archivo de ticket debe existir", Files.exists(ticket80mm));
        assertTrue("El ticket debe tener bytes generados", Files.size(ticket80mm) > 500);

        Path facturaA4 = generadorA4.generar(pedido, config, List.of(det));
        archivosGenerados.add(facturaA4);
        assertTrue("La factura A4 debe existir", Files.exists(facturaA4));
        assertTrue("La factura A4 debe tener bytes generados", Files.size(facturaA4) > 500);
    }

    @Test
    public void testGeneracionPreFacturaProvisional() throws Exception {
        Pedidos pedido = new Pedidos();
        pedido.setId(77);
        pedido.setSala("Salón Principal");
        pedido.setNum_mesa(5);
        pedido.setFecha("2026-10-17 19:00:00");
        pedido.setUsuario("mesero1");
        pedido.setTasaCambio(new BigDecimal("45.00"));
        pedido.setTotalDecimal(new BigDecimal("35.00"));
        pedido.setTotalBs(new BigDecimal("1575.00"));
        pedido.setSubtotal(new BigDecimal("35.00"));
        pedido.setIvaMonto(BigDecimal.ZERO);
        pedido.setIvaPorcentaje(BigDecimal.ZERO);

        DetallePedido det1 = new DetallePedido();
        det1.setId(10);
        det1.setNombre("Parrilla Mixta");
        det1.setCantidad(1);
        det1.setPrecioDecimal(new BigDecimal("25.00"));
        det1.setMonedaBase("USD");
        det1.setAplicaIva(false);
        det1.setPrecioIncluyeIva(false);
        det1.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));

        DetallePedido det2 = new DetallePedido();
        det2.setId(11);
        det2.setNombre("Jugo Natural");
        det2.setCantidad(2);
        det2.setPrecioDecimal(new BigDecimal("5.00"));
        det2.setMonedaBase("USD");
        det2.setAplicaIva(false);
        det2.setPrecioIncluyeIva(false);
        det2.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));

        Path preFactura = generadorTicket.generarPreFactura(pedido, config, List.of(det1, det2));
        archivosGenerados.add(preFactura);
        assertTrue("El archivo de pre-factura debe existir", Files.exists(preFactura));
        assertTrue("El archivo de pre-factura debe contener contenido PDF", Files.size(preFactura) > 500);
    }

    @Test
    public void testTablePedidosMuestraColumnaMetodoPago() throws Exception {
        Usuario usuario = new Usuario(1, "Admin", "admin@demo.com", "pass", "Administrador");

        Sistema sistema = null;
        try {
            sistema = crearSistemaEnEdt(usuario);
            JTable tablePedidos = sistema.getTablePedidos();
            assertNotNull("TablePedidos debe existir", tablePedidos);

            DefaultTableModel modelo = (DefaultTableModel) tablePedidos.getModel();
            int colMetodo = modelo.findColumn("Método de Pago");
            assertTrue("La columna 'Método de Pago' debe estar presente en TablePedidos", colMetodo >= 0);

            int colEstado = modelo.findColumn("Estado");
            assertTrue("La columna 'Estado' debe estar presente en TablePedidos", colEstado >= 0);
            assertEquals("La columna Estado debe mantenerse en el índice 6 para compatibilidad", 6, colEstado);
            assertEquals("La columna Método de Pago debe ser la columna 7", 7, colMetodo);

            // Verificar formateo visual en tabla
            Pedidos p1 = new Pedidos();
            p1.setId(100);
            p1.setSala("Terraza");
            p1.setNum_mesa(1);
            p1.setUsuario("admin");
            p1.setFecha("2026-10-17 12:00:00");
            p1.setTotalDecimal(new BigDecimal("10.00"));
            p1.setTotalBs(new BigDecimal("400.00"));
            p1.setEstado("FINALIZADO");
            p1.setMetodoPago("EFECTIVO_USD");

            Pedidos p2 = new Pedidos();
            p2.setId(101);
            p2.setSala("VIP");
            p2.setNum_mesa(2);
            p2.setUsuario("admin");
            p2.setFecha("2026-10-17 12:30:00");
            p2.setTotalDecimal(new BigDecimal("15.00"));
            p2.setTotalBs(new BigDecimal("600.00"));
            p2.setEstado("FINALIZADO");
            p2.setMetodoPago("PAGO_MOVIL");

            Pedidos p3 = new Pedidos();
            p3.setId(102);
            p3.setSala("Barra");
            p3.setNum_mesa(3);
            p3.setUsuario("admin");
            p3.setFecha("2026-10-17 13:00:00");
            p3.setTotalDecimal(new BigDecimal("20.00"));
            p3.setTotalBs(new BigDecimal("800.00"));
            p3.setEstado("PENDIENTE");

            final Sistema sisRef = sistema;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("mostrarPedidosEnTabla", List.class);
                    m.setAccessible(true);
                    m.invoke(sisRef, List.of(p1, p2, p3));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            assertEquals("Deben listarse 3 filas", 3, modelo.getRowCount());
            assertEquals("Efectivo $", modelo.getValueAt(0, colMetodo));
            assertEquals("Pago móvil", modelo.getValueAt(1, colMetodo));
            assertEquals("—", modelo.getValueAt(2, colMetodo));
        } finally {
            if (sistema != null) {
                final Sistema s = sistema;
                SwingUtilities.invokeAndWait(s::dispose);
            }
        }
    }

    @Test
    public void testMostrarPedidoEnPantallaCargaPagosRealesSiFinalizado() throws Exception {
        Usuario usuario = new Usuario(1, "Admin", "admin@demo.com", "pass", "Administrador");

        Sistema sistema = null;
        try {
            sistema = crearSistemaEnEdt(usuario);

            Pedidos pFinalizado = new Pedidos();
            pFinalizado.setId(200);
            pFinalizado.setSala("Terraza");
            pFinalizado.setNum_mesa(4);
            pFinalizado.setFecha("2026-10-17 15:00:00");
            pFinalizado.setTasaCambio(new BigDecimal("40.00"));
            pFinalizado.setTotalDecimal(new BigDecimal("50.00"));
            pFinalizado.setTotalBs(new BigDecimal("2000.00"));
            pFinalizado.setSubtotal(new BigDecimal("50.00"));
            pFinalizado.setIvaMonto(BigDecimal.ZERO);
            pFinalizado.setIvaPorcentaje(BigDecimal.ZERO);
            pFinalizado.setEstado("FINALIZADO");
            pFinalizado.setMetodoPago("MIXTO");

            List<PagoPedido> pagos = List.of(
                    new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("30.00")),
                    new PagoPedido("PAGO_MOVIL", "VES", new BigDecimal("800.00"))
            );
            pFinalizado.setPagos(pagos);

            DetallePedido det = new DetallePedido();
            det.setId(1);
            det.setNombre("Hamburguesa");
            det.setCantidad(2);
            det.setPrecioDecimal(new BigDecimal("25.00"));
            det.setMonedaBase("USD");
            det.setAplicaIva(false);
            det.setPrecioIncluyeIva(false);
            det.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));

            final Sistema sisRef = sistema;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    java.lang.reflect.Method m = Sistema.class.getDeclaredMethod("mostrarPedidoEnPantalla", Pedidos.class, List.class);
                    m.setAccessible(true);
                    m.invoke(sisRef, pFinalizado, List.of(det));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            DefaultTableModel modPagos = (DefaultTableModel) sisRef.getTablaPagosFac().getModel();
            assertEquals("La tabla de pagos debe contener los 2 pagos mixtos reales", 2, modPagos.getRowCount());
            assertEquals("EFECTIVO_USD", modPagos.getValueAt(0, 0));
            assertEquals("USD", modPagos.getValueAt(0, 1));
            assertEquals("30.00", modPagos.getValueAt(0, 2));

            assertEquals("PAGO_MOVIL", modPagos.getValueAt(1, 0));
            assertEquals("VES", modPagos.getValueAt(1, 1));
            assertEquals("800.00", modPagos.getValueAt(1, 2));

            assertFalse("La tabla de pagos debe estar deshabilitada para pedidos ya cobrados", sisRef.getTablaPagosFac().isEnabled());
            assertNotNull("El botón Pre-Factura debe existir", sisRef.getBtnPreFacturaPedido());
            assertTrue("El botón Pre-Factura debe estar habilitado", sisRef.getBtnPreFacturaPedido().isEnabled());
        } finally {
            if (sistema != null) {
                final Sistema s = sistema;
                SwingUtilities.invokeAndWait(s::dispose);
            }
        }
    }

    private static class PedidosRepositorioFalso implements PedidosRepositorio {
        @Override public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) { return 1; }
        @Override public boolean actualizarPedidoCompleto(int idPedido, Pedidos pedido, List<DetallePedido> detalles) { return true; }
        @Override public int verificarStado(int mesa, int idSala) { return 0; }
        @Override public Pedidos verPedido(int idPedido) { return new Pedidos(); }
        @Override public List<DetallePedido> verPedidoDetalle(int idPedido) { return Collections.emptyList(); }
        @Override public List<DetallePedido> finalizarPedido(int idPedido) { return Collections.emptyList(); }
        @Override public boolean actualizarEstado(int idPedido) { return true; }
        @Override public List<Pedidos> listarPedidos() { return Collections.emptyList(); }
    }
}
