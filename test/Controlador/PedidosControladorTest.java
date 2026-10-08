package Controlador;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import Servicio.GeneradorPdfPedido;
import Servicio.ConsultaPedidosServicio;
import Servicio.PoliticaAcceso;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PedidosControladorTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void delegaGeneracionPdfAlServicioConIdYDestinoDelUsuario() {
        AtomicInteger idConsultado = new AtomicInteger();
        AtomicReference<Path> archivoAbierto = new AtomicReference<>();
        PedidoPdfServicio servicioPdf = new PedidoPdfServicio(
                id -> {
                    idConsultado.set(id);
                    return pedido(id);
                },
                id -> Collections.singletonList(detalle()),
                this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                archivoAbierto::set);
        PedidoServicio servicioRegistro = new PedidoServicio(new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, java.util.List<DetallePedido> detalles) {
                return 1;
            }
        });
        PoliticaAcceso administrador = politica("Administrador");
        PedidosControlador controlador = new PedidosControlador(
                servicioRegistro, servicioPdf, administrador,
                new ConsultaPedidosServicio(new PedidosRepositorioFalso(), administrador));

        controlador.generarPdfPedido(42);

        assertEquals(42, idConsultado.get());
        assertNotNull(archivoAbierto.get());
        assertEquals(temporal.getRoot().toPath().resolve("pedido-42.pdf"), archivoAbierto.get());
    }

    @Test
    public void asistentePuedeRegistrarPeroNoGenerarPdfDeHistorial() {
        AtomicInteger registros = new AtomicInteger();
        AtomicInteger consultasPdf = new AtomicInteger();
        PedidosRepositorioFalso repositorio = new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, java.util.List<DetallePedido> detalles) {
                registros.incrementAndGet();
                return 22;
            }
        };
        PedidoServicio servicio = new PedidoServicio(repositorio);
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                id -> { consultasPdf.incrementAndGet(); throw new AssertionError("No debe consultar PDF"); },
                id -> { consultasPdf.incrementAndGet(); return Collections.emptyList(); },
                this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                archivo -> { throw new AssertionError("No debe abrir PDF"); });
        Usuario usuario = new Usuario();
        usuario.setRol("Asistente");
        PoliticaAcceso politicaAsistente = new PoliticaAcceso(usuario);
        PedidosControlador controlador = new PedidosControlador(
                servicio, pdf, politicaAsistente,
                new ConsultaPedidosServicio(new PedidosRepositorioFalso(), politicaAsistente));
        Pedidos pedido = pedido(22);
        pedido.setId_sala(2);
        pedido.setNum_mesa(3);
        pedido.setUsuario("Asistente");
        pedido.setTotalDecimal(new BigDecimal("8.00"));

        assertEquals(22, controlador.registrarPedidoCompleto(
                pedido, Collections.singletonList(detalle())));
        assertTrue(registros.get() == 1);
        assertThrows(ErrorAplicacionException.class, () -> controlador.generarPdfPedido(22));
        assertEquals(0, consultasPdf.get());
    }

    @Test
    public void delegaConsultasYFinalizacionAlServicioAutorizado() {
        AtomicInteger operaciones = new AtomicInteger();
        PedidosRepositorioFalso repositorio = new PedidosRepositorioFalso() {
            @Override public int verificarStado(int mesa, int sala) { operaciones.incrementAndGet(); return 22; }
            @Override public java.util.List<Pedidos> listarPedidos() { operaciones.incrementAndGet(); return Collections.singletonList(pedido(22)); }
            @Override public Pedidos verPedido(int id) { operaciones.incrementAndGet(); return pedido(id); }
            @Override public java.util.List<DetallePedido> verPedidoDetalle(int id) { operaciones.incrementAndGet(); return Collections.singletonList(detalle()); }
            @Override public boolean actualizarEstado(int id) { operaciones.incrementAndGet(); return true; }
            @Override public int registrarPedidoCompleto(Pedidos pedido, java.util.List<DetallePedido> detalles) { return 22; }
        };
        PedidoServicio servicio = new PedidoServicio(repositorio);
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                this::pedido, id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> { });
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(
                repositorio, politica("Administrador"));
        PedidosControlador controlador = new PedidosControlador(
                servicio, pdf, politica("Administrador"), consultas);

        assertEquals(22, controlador.buscarPedidoPendiente(3, 2));
        assertEquals(1, controlador.listarPedidos().size());
        assertEquals(22, controlador.verPedido(22).getId());
        assertEquals(1, controlador.verPedidoDetalle(22).size());
        assertEquals(true, controlador.finalizarPedido(22));
        assertEquals(5, operaciones.get());
    }

    @Test
    public void previsualizarPdfPedidoDelegaAlServicioPdfSinImpresionDirecta() {
        AtomicInteger idPrevisualizado = new AtomicInteger();
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                this::pedido, id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                path -> { throw new AssertionError("No debe imprimir directo"); },
                path -> idPrevisualizado.set(42));
        PedidosControlador controlador = new PedidosControlador(
                new PedidoServicio(new PedidosRepositorioFalso()),
                pdf, politica("Administrador"),
                new ConsultaPedidosServicio(new PedidosRepositorioFalso(), politica("Administrador")));

        controlador.previsualizarPdfPedido(42);

        assertEquals(42, idPrevisualizado.get());
    }

    @Test
    public void operacionesCierreCajaDeleganAlServicioConfigurado() throws Exception {
        AtomicReference<String> accionCierre = new AtomicReference<>();
        Servicio.GeneradorPdfCierre generador = new Servicio.GeneradorPdfCierre(temporal.getRoot().toPath());
        Modelo.CierreCajaDao daoFalso = new Modelo.CierreCajaDao() {
            @Override
            public Modelo.CierreCaja consultarCierre(String fecha, Modelo.CierreCaja.TipoCierre tipo, String usuarioEmisor, Config cfg) {
                Modelo.CierreCaja c = new Modelo.CierreCaja();
                c.setTipo(tipo);
                c.setFecha(fecha != null ? fecha : "2026-10-07");
                c.setFechaHoraEmision("2026-10-07 18:00:00");
                c.setUsuarioEmisor(usuarioEmisor);
                return c;
            }
        };
        Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                daoFalso,
                this::configuracion,
                generador,
                path -> accionCierre.set("IMPRIMIR"),
                path -> accionCierre.set("PREVISUALIZAR")
        );

        PedidoPdfServicio pdf = new PedidoPdfServicio(
                this::pedido, id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> { });
        PedidosControlador controlador = new PedidosControlador(
                new PedidoServicio(new PedidosRepositorioFalso()),
                pdf, politica("Administrador"),
                new ConsultaPedidosServicio(new PedidosRepositorioFalso(), politica("Administrador")),
                cierreServicio);

        // 1. Previsualizar Corte X
        Path resPrev = controlador.previsualizarCierreCaja("2026-10-07", Modelo.CierreCaja.TipoCierre.PARCIAL, "Admin");
        assertNotNull(resPrev);
        assertEquals("PREVISUALIZAR", accionCierre.get());

        // 2. Imprimir Corte Z
        Path resImp = controlador.imprimirCierreCaja("2026-10-07", Modelo.CierreCaja.TipoCierre.TOTAL, "Admin");
        assertNotNull(resImp);
        assertEquals("IMPRIMIR", accionCierre.get());
    }

    @Test
    public void operacionesCierreCajaLanzanErrorSiServicioNoEstaConfigurado() {
        PedidosControlador controladorSinCierre = new PedidosControlador(
                new PedidoServicio(new PedidosRepositorioFalso()),
                new PedidoPdfServicio(this::pedido, id -> Collections.singletonList(detalle()), this::configuracion,
                        new GeneradorPdfPedido(temporal.getRoot().toPath()), a -> {}),
                politica("Administrador"),
                new ConsultaPedidosServicio(new PedidosRepositorioFalso(), politica("Administrador")),
                null);

        assertThrows(IllegalStateException.class, () ->
                controladorSinCierre.imprimirCierreCaja("2026-10-07", Modelo.CierreCaja.TipoCierre.TOTAL, "Admin"));

        assertThrows(IllegalStateException.class, () ->
                controladorSinCierre.previsualizarCierreCaja("2026-10-07", Modelo.CierreCaja.TipoCierre.PARCIAL, "Admin"));
    }

    @Test
    public void reimprimirPdfPedidoRegistraAuditoriaExito() {
        AtomicReference<String> auditoriaAccion = new AtomicReference<>();
        Modelo.AuditoriaPedidosDao daoAuditoria = new Modelo.AuditoriaPedidosDao() {
            @Override
            public boolean registrar(Modelo.AuditoriaPedido aud) {
                auditoriaAccion.set(aud.getAccion());
                return true;
            }
        };

        PedidoPdfServicio pdf = new PedidoPdfServicio(
                this::pedido, id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> { });
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(
                new PedidosRepositorioFalso(), politica("Administrador"), daoAuditoria);
        PedidosControlador controlador = new PedidosControlador(
                new PedidoServicio(new PedidosRepositorioFalso()),
                pdf, politica("Administrador"), consultas);

        controlador.reimprimirPdfPedido(42, "Copia solicitada", "Admin");
        assertEquals("REIMPRESION", auditoriaAccion.get());
    }

    @Test
    public void reimprimirPdfPedidoRegistraAuditoriaFalloAnteError() {
        AtomicReference<String> auditoriaAccion = new AtomicReference<>();
        Modelo.AuditoriaPedidosDao daoAuditoria = new Modelo.AuditoriaPedidosDao() {
            @Override
            public boolean registrar(Modelo.AuditoriaPedido aud) {
                auditoriaAccion.set(aud.getAccion());
                return true;
            }
        };

        PedidoPdfServicio pdfConError = new PedidoPdfServicio(
                id -> { throw new RuntimeException("Error impresora"); },
                id -> Collections.singletonList(detalle()), this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()), archivo -> { });
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(
                new PedidosRepositorioFalso(), politica("Administrador"), daoAuditoria);
        PedidosControlador controlador = new PedidosControlador(
                new PedidoServicio(new PedidosRepositorioFalso()),
                pdfConError, politica("Administrador"), consultas);

        assertThrows(RuntimeException.class, () ->
                controlador.reimprimirPdfPedido(42, "Copia solicitada", "Admin"));
        assertEquals("REIMPRESION_FALLIDA", auditoriaAccion.get());
    }

    private Pedidos pedido(int id) {
        Pedidos pedido = new Pedidos();
        pedido.setId(id);
        pedido.setNum_mesa(3);
        pedido.setSala("Principal");
        pedido.setUsuario("Operador");
        pedido.setFecha("2026-10-05");
        pedido.setTotalDecimal(new BigDecimal("8.00"));
        return pedido;
    }

    private Config configuracion() {
        return new Config(1, "123", "Restaurante", "9", "Santiago", "Gracias");
    }

    private PoliticaAcceso politica(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return new PoliticaAcceso(usuario);
    }

    private DetallePedido detalle() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("8.00"));
        return detalle;
    }
}

