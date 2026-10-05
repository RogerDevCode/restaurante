package Servicio;

import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Usuario;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConsultaPedidosServicioTest {
    @Test
    public void asistentePuedeComprobarMesaPeroNoLeerHistorialNiFinalizar() {
        RepositorioFalso repositorio = new RepositorioFalso();
        repositorio.pendiente = 44;
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(repositorio, politica("Asistente"));

        assertEquals(44, servicio.buscarPedidoPendiente(3, 2));
        assertThrows(ErrorAplicacionException.class, servicio::listarHistorial);
        assertThrows(ErrorAplicacionException.class, () -> servicio.obtenerPedido(44));
        assertThrows(ErrorAplicacionException.class, () -> servicio.obtenerDetalles(44));
        assertThrows(ErrorAplicacionException.class, () -> servicio.finalizar(44));

        assertEquals(1, repositorio.busquedasPendientes.get());
        assertEquals(0, repositorio.lecturasAdmin.get());
        assertEquals(0, repositorio.finalizaciones.get());
    }

    @Test
    public void administradorConsultaHistorialDetallesYFinaliza() {
        RepositorioFalso repositorio = new RepositorioFalso();
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(repositorio, politica("Administrador"));

        assertEquals(1, servicio.listarHistorial().size());
        assertEquals(5, servicio.obtenerPedido(5).getId());
        assertEquals(1, servicio.obtenerDetalles(5).size());
        assertTrue(servicio.finalizar(5));

        assertEquals(3, repositorio.lecturasAdmin.get());
        assertEquals(1, repositorio.finalizaciones.get());
    }

    @Test
    public void rechazaIdsOMesasInvalidosAntesDeConsultarRepositorio() {
        RepositorioFalso repositorio = new RepositorioFalso();
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(repositorio, politica("Administrador"));

        assertThrows(ErrorAplicacionException.class, () -> servicio.buscarPedidoPendiente(0, 1));
        assertThrows(ErrorAplicacionException.class, () -> servicio.buscarPedidoPendiente(1, 0));
        assertThrows(ErrorAplicacionException.class, () -> servicio.obtenerPedido(0));
        assertThrows(ErrorAplicacionException.class, () -> servicio.obtenerDetalles(-1));
        assertThrows(ErrorAplicacionException.class, () -> servicio.finalizar(0));

        assertEquals(0, repositorio.busquedasPendientes.get());
        assertEquals(0, repositorio.lecturasAdmin.get());
        assertEquals(0, repositorio.finalizaciones.get());
    }

    @Test
    public void propagaSinSustituirExcepcionDePersistencia() {
        RepositorioFalso repositorio = new RepositorioFalso();
        IllegalStateException fallo = new IllegalStateException("fallo simulado");
        repositorio.fallo = fallo;
        ConsultaPedidosServicio servicio = new ConsultaPedidosServicio(repositorio, politica("Administrador"));

        IllegalStateException propagada = assertThrows(IllegalStateException.class,
                () -> servicio.finalizar(5));

        assertSame(fallo, propagada);
    }

    private PoliticaAcceso politica(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return new PoliticaAcceso(usuario);
    }

    private static final class RepositorioFalso extends PedidosRepositorioFalso {
        private final AtomicInteger busquedasPendientes = new AtomicInteger();
        private final AtomicInteger lecturasAdmin = new AtomicInteger();
        private final AtomicInteger finalizaciones = new AtomicInteger();
        private int pendiente;
        private RuntimeException fallo;

        @Override public int verificarStado(int mesa, int sala) { busquedasPendientes.incrementAndGet(); verificar(); return pendiente; }
        @Override public List<Pedidos> listarPedidos() { lecturasAdmin.incrementAndGet(); verificar(); return Collections.singletonList(pedido()); }
        @Override public Pedidos verPedido(int id) { lecturasAdmin.incrementAndGet(); verificar(); return pedido(); }
        @Override public List<DetallePedido> verPedidoDetalle(int id) { lecturasAdmin.incrementAndGet(); verificar(); return Collections.singletonList(detalle()); }
        @Override public boolean actualizarEstado(int id) { finalizaciones.incrementAndGet(); verificar(); return true; }
        private void verificar() { if (fallo != null) throw fallo; }
        private Pedidos pedido() { Pedidos p = new Pedidos(); p.setId(5); p.setTotalDecimal(new BigDecimal("1.00")); return p; }
        private DetallePedido detalle() { DetallePedido d = new DetallePedido(); d.setNombre("Plato"); d.setPrecioDecimal(new BigDecimal("1.00")); d.setCantidad(1); return d; }
    }
}
