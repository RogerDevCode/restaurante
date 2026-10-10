package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import Modelo.Usuario;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AdversarialValidationTest {

    private final PoliticaAcceso politicaAdmin = new PoliticaAcceso(new Usuario(1, "Admin", "admin@a.com", "pass", "Administrador"));
    private final PoliticaAcceso politicaAsistente = new PoliticaAcceso(new Usuario(2, "Asist", "asist@a.com", "pass", "Asistente"));
    
    @Test
    public void rolInvalidoEnPoliticaAcceso() {
        assertThrows(ErrorAplicacionException.class, () -> new PoliticaAcceso(new Usuario(3, "Desc", "desc@a.com", "pass", "Desconocido")));
        assertThrows(ErrorAplicacionException.class, () -> new PoliticaAcceso(new Usuario(3, "Desc", "desc@a.com", "pass", "")));
    }

    @Test
    public void pedidoTotalNegativo() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, 1, 1, "2026-01-01", new BigDecimal("-10.00"), "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = Arrays.asList(new DetallePedido(1, "Plato", new BigDecimal("-10.00"), 1, "", 1));
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("positivo"));
    }

    @Test
    public void pedidoTotalCero() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, 1, 1, "2026-01-01", BigDecimal.ZERO, "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = Arrays.asList(new DetallePedido(1, "Plato", BigDecimal.ZERO, 1, "", 1));
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("positivo"));
    }

    @Test
    public void pedidoSinDetalles() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, 1, 1, "2026-01-01", new BigDecimal("10.00"), "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = new ArrayList<>();
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("al menos un detalle"));
    }

    @Test
    public void pedidoMismatchedTotal() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, 1, 1, "2026-01-01", new BigDecimal("20.00"), "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = Arrays.asList(new DetallePedido(1, "Plato", new BigDecimal("10.00"), 1, "", 1));
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("no coincide con sus detalles"));
    }

    @Test
    public void cantidadNulaONegativa() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, 1, 1, "2026-01-01", new BigDecimal("-10.00"), "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = Arrays.asList(new DetallePedido(1, "Plato", new BigDecimal("10.00"), -1, "", 1));
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("positivo"));
    }

    @Test
    public void salaNumMesaNegativa() {
        PedidoServicio servicio = new PedidoServicio(new DummyPedidosRepositorio());
        Pedidos pedido = new Pedidos(0, -1, -5, "2026-01-01", new BigDecimal("10.00"), "Sala A", "usuario", "PENDIENTE");
        List<DetallePedido> detalles = Arrays.asList(new DetallePedido(1, "Plato", new BigDecimal("10.00"), 1, "", 1));
        
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrarPedidoCompleto(pedido, detalles));
        assertTrue(e.getMessage().contains("válidos"));
    }

    @Test
    public void platoPrecioCeroONegativoOExcesivo() {
        PlatosServicio servicio = new PlatosServicio(new DummyPlatosRepositorio(), politicaAdmin);
        
        Platos platoCero = new Platos(0, "P", BigDecimal.ZERO, "2026-01-01");
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(platoCero));

        Platos platoNeg = new Platos(0, "P", new BigDecimal("-10"), "2026-01-01");
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(platoNeg));

        Platos platoExc = new Platos(0, "P", new BigDecimal("100000000.00"), "2026-01-01");
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(platoExc));
    }

    @Test
    public void platoRestriccionAsistente() {
        PlatosServicio servicio = new PlatosServicio(new DummyPlatosRepositorio(), politicaAsistente);
        Platos plato = new Platos(0, "P", new BigDecimal("10.00"), "2026-01-01");
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(plato));
        assertTrue(e.getMessage().contains("Tu rol no permite"));
    }

    @Test
    public void salaRestriccionAsistente() {
        SalasServicio servicio = new SalasServicio(new DummySalasRepositorio(), politicaAsistente);
        Salas sala = new Salas(0, "Sala", 5);
        ErrorAplicacionException e = assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(sala));
        assertTrue(e.getMessage().contains("Tu rol no permite"));
    }

    @Test
    public void salaMesasNegativasOZero() {
        SalasServicio servicio = new SalasServicio(new DummySalasRepositorio(), politicaAdmin);
        Salas salaCero = new Salas(0, "Sala", 0);
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(salaCero));
        
        Salas salaNeg = new Salas(0, "Sala", -1);
        assertThrows(ErrorAplicacionException.class, () -> servicio.registrar(salaNeg));
    }

    @Test
    public void purgaConMesesInvalidoLanzaExcepcion() {
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(new DummyPedidosRepositorio(), politicaAdmin);
        assertThrows(ErrorAplicacionException.class, () -> servicio.purgarPedidosFinalizados(0));
        assertThrows(ErrorAplicacionException.class, () -> servicio.purgarPedidosFinalizados(-5));
    }

    @Test
    public void purgaPorAsistenteLanzaExcepcion() {
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(new DummyPedidosRepositorio(), politicaAsistente);
        assertThrows(ErrorAplicacionException.class, () -> servicio.purgarPedidosFinalizados(12));
    }

    @Test
    public void configRetencionInvalidaLanzaExcepcion() {
        Modelo.Config config = new Modelo.Config();
        assertThrows(ErrorAplicacionException.class, () -> config.setMesesRetencionPedidos(0));
        assertThrows(ErrorAplicacionException.class, () -> config.setMesesRetencionPedidos(-2));
    }

    static class DummyPedidosRepositorio implements PedidosRepositorio {
    public boolean actualizarPedidoCompleto(int id, Pedidos p, java.util.List<DetallePedido> d) { return true; }
        @Override public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) { return 1; }
        @Override public Pedidos verPedido(int idPedido) { return null; }
        @Override public List<DetallePedido> verPedidoDetalle(int idPedido) { return null; }
        @Override public int verificarStado(int mesa, int idSala) { return 0; }
        @Override public boolean actualizarEstado(int idPedido) { return true; }
        @Override public List<DetallePedido> finalizarPedido(int idPedido) { return null; }
        @Override public List<Pedidos> listarPedidos() { return null; }
    }

    static class DummyPlatosRepositorio implements PlatosRepositorio {
        @Override public boolean registrar(Platos plato) { return true; }
        @Override public List<Platos> listarPorFecha(String nombre, String fecha) { return null; }
        @Override public boolean eliminar(int id) { return true; }
        @Override public boolean modificar(Platos plato) { return true; }
    }

    static class DummySalasRepositorio implements SalasRepositorio {
        @Override public boolean registrar(Salas sala) { return true; }
        @Override public List<Salas> listar() { return null; }
        @Override public boolean eliminar(int id) { return true; }
        @Override public boolean modificar(Salas sala) { return true; }
    }
}
