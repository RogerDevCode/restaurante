package Servicio;

import Modelo.AuditoriaPedido;
import Modelo.AuditoriaPedidosDao;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.Usuario;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Pruebas unitarias para el registro de auditoría de anulaciones y reimpresiones de pedidos.
 * Garantiza trazabilidad sobre quién realizó la acción, por qué motivo y cuándo.
 */
public class AuditoriaAnulacionesYReimpresionesTest {

    private RepositorioPedidosEnMemoria repositorioPedidos;
    private AuditoriaDaoEnMemoria auditoriaDao;
    private ConsultaPedidosServicio servicioAdmin;
    private ConsultaPedidosServicio servicioAsistente;

    @Before
    public void setUp() {
        auditoriaDao = new AuditoriaDaoEnMemoria();
        repositorioPedidos = new RepositorioPedidosEnMemoria(auditoriaDao);

        PoliticaAcceso politicaAdmin = new PoliticaAcceso(new Usuario(1, "Administrador", "admin", "pass", "Administrador"));
        PoliticaAcceso politicaAsistente = new PoliticaAcceso(new Usuario(2, "Cajero", "caja", "pass", "Asistente"));

        servicioAdmin = new ConsultaPedidosServicio(repositorioPedidos, politicaAdmin, auditoriaDao);
        servicioAsistente = new ConsultaPedidosServicio(repositorioPedidos, politicaAsistente, auditoriaDao);
    }

    @Test
    public void modeloAuditoriaPedidoNormalizaYAsignaValoresCorrectamente() {
        AuditoriaPedido aud = new AuditoriaPedido(101, "anulacion", "Error de mesa", "Mozo Juan");
        assertEquals(101, aud.getIdPedido());
        assertEquals("ANULACION", aud.getAccion());
        assertEquals("Error de mesa", aud.getMotivo());
        assertEquals("Mozo Juan", aud.getUsuario());
        assertNotNull(aud.getFechaHora());
        assertTrue(aud.getFechaHoraFormateada().length() > 0);

        // Fallback para usuario nulo o en blanco
        AuditoriaPedido audDefecto = new AuditoriaPedido(102, null, null, "   ");
        assertEquals("ACCION", audDefecto.getAccion());
        assertEquals("", audDefecto.getMotivo());
        assertEquals("Sistema", audDefecto.getUsuario());
    }

    @Test
    public void asistenteNoPuedeAnularPedidosPorPoliticaDeSeguridad() {
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAsistente.anular(10, "Mesa cancelada", "Cajero"));
        assertEquals(0, auditoriaDao.registros.size());
        assertFalse(repositorioPedidos.pedidoFueAnulado(10));
    }

    @Test
    public void anularPedidoRequiereMotivoObligatorio() {
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAdmin.anular(10, "", "Admin"));
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAdmin.anular(10, "   ", "Admin"));
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAdmin.anular(10, null, "Admin"));

        assertEquals(0, auditoriaDao.registros.size());
    }

    @Test
    public void anularPedidoRechazaIdInvalido() {
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAdmin.anular(0, "Motivo válido", "Admin"));
        assertThrows(ErrorAplicacionException.class, () ->
                servicioAdmin.anular(-5, "Motivo válido", "Admin"));

        assertEquals(0, auditoriaDao.registros.size());
    }

    @Test
    public void anularPedidoConExitoRegistraAuditoriaYActualizaEstado() {
        int idPedido = 42;
        String motivo = "Cliente se retiró del local sin consumir";
        String usuario = "Administrador Principal";

        boolean resultado = servicioAdmin.anular(idPedido, motivo, usuario);

        assertTrue("La anulación debe ser exitosa", resultado);
        assertTrue("El pedido debe quedar marcado como anulado en el repositorio", repositorioPedidos.pedidoFueAnulado(idPedido));
        assertEquals("Debe haberse registrado 1 evento de auditoría", 1, auditoriaDao.registros.size());

        AuditoriaPedido evento = auditoriaDao.registros.get(0);
        assertEquals(idPedido, evento.getIdPedido());
        assertEquals("ANULACION", evento.getAccion());
        assertEquals(motivo, evento.getMotivo());
        assertEquals(usuario, evento.getUsuario());
    }

    @Test
    public void reimpresionDeTicketRegistraEventoDeAuditoria() {
        int idPedido = 55;
        String motivo = "Ticket fiscal extraviado por el cliente";
        String usuario = "Cajero Turno Noche";

        servicioAdmin.registrarAuditoria(idPedido, "REIMPRESION", motivo, usuario);

        assertEquals(1, auditoriaDao.registros.size());
        AuditoriaPedido evento = auditoriaDao.registros.get(0);
        assertEquals(idPedido, evento.getIdPedido());
        assertEquals("REIMPRESION", evento.getAccion());
        assertEquals(motivo, evento.getMotivo());
        assertEquals(usuario, evento.getUsuario());
    }

    @Test
    public void listarAuditoriaPorPedidoDevuelveHistorialOrdenado() {
        int idPedido = 77;
        servicioAdmin.registrarAuditoria(idPedido, "REIMPRESION", "Copia 1", "Mozo");
        servicioAdmin.registrarAuditoria(idPedido, "REIMPRESION", "Copia 2", "Mozo");
        servicioAdmin.anular(idPedido, "Anulado por error en cobranza", "Admin");

        List<AuditoriaPedido> historial = servicioAdmin.obtenerAuditoriaPedido(idPedido);
        assertEquals(3, historial.size());
        assertEquals("REIMPRESION", historial.get(0).getAccion());
        assertEquals("REIMPRESION", historial.get(1).getAccion());
        assertEquals("ANULACION", historial.get(2).getAccion());
    }

    @Test
    public void falloEnRegistroDeAuditoriaInterrumpeOperacion() {
        AuditoriaDaoEnMemoria daoFallo = new AuditoriaDaoEnMemoria() {
            @Override
            public boolean registrar(AuditoriaPedido a) {
                return false; // Simula falla de auditoría
            }
        };
        PoliticaAcceso politicaAdmin = new PoliticaAcceso(new Usuario(1, "Administrador", "admin", "pass", "Administrador"));
        ConsultaPedidosServicio servicioConFallo = new ConsultaPedidosServicio(repositorioPedidos, politicaAdmin, daoFallo);

        assertThrows("Si el registro de auditoría falla, la operación debe ser abortada",
                ErrorAplicacionException.class,
                () -> servicioConFallo.registrarAuditoria(88, "REIMPRESION", "Copia", "Admin"));
    }

    // Repositorio y DAO en memoria para pruebas aisladas
    private static class RepositorioPedidosEnMemoria implements PedidosRepositorio {
        private final List<Integer> pedidosAnulados = new ArrayList<>();
        private final AuditoriaDaoEnMemoria auditoriaDao;

        public RepositorioPedidosEnMemoria() {
            this(null);
        }

        public RepositorioPedidosEnMemoria(AuditoriaDaoEnMemoria auditoriaDao) {
            this.auditoriaDao = auditoriaDao;
        }

        public boolean pedidoFueAnulado(int id) {
            return pedidosAnulados.contains(id);
        }

        @Override
        public boolean anularPedido(int idPedido) {
            pedidosAnulados.add(idPedido);
            return true;
        }

        @Override
        public boolean anularPedidoConAuditoria(int idPedido, String motivo, String usuario) {
            pedidosAnulados.add(idPedido);
            if (auditoriaDao != null) {
                auditoriaDao.registrar(new AuditoriaPedido(idPedido, "ANULACION", motivo, usuario));
            }
            return true;
        }

        @Override
        public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) { return 1; }
        @Override
        public int verificarStado(int mesa, int idSala) { return 0; }
        @Override
        public Pedidos verPedido(int idPedido) { return new Pedidos(idPedido, 1, 1, "2026-10-08", new java.math.BigDecimal("10.00"), "Sala 1", "Admin", "FINALIZADO"); }
        @Override
        public List<DetallePedido> verPedidoDetalle(int idPedido) { return Collections.emptyList(); }
        @Override
        public List<DetallePedido> finalizarPedido(int idPedido) { return Collections.emptyList(); }
        @Override
        public boolean actualizarEstado(int idPedido) { return true; }
        @Override
        public List<Pedidos> listarPedidos() { return Collections.emptyList(); }
    }

    private static class AuditoriaDaoEnMemoria extends AuditoriaPedidosDao {
        public final List<AuditoriaPedido> registros = new ArrayList<>();

        @Override
        public boolean registrar(AuditoriaPedido auditoria) {
            if (auditoria == null) {
                throw ErrorAplicacionException.validacion("El registro no puede ser nulo");
            }
            if (auditoria.getIdPedido() <= 0) {
                throw ErrorAplicacionException.validacion("El id de pedido debe ser válido");
            }
            registros.add(auditoria);
            return true;
        }

        @Override
        public List<AuditoriaPedido> listarPorPedido(int idPedido) {
            if (idPedido <= 0) return Collections.emptyList();
            List<AuditoriaPedido> filtrados = new ArrayList<>();
            for (AuditoriaPedido a : registros) {
                if (a.getIdPedido() == idPedido) {
                    filtrados.add(a);
                }
            }
            return filtrados;
        }
    }
}
