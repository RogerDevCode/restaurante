package Modelo;

import Servicio.PedidoServicio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Matriz completa del dominio mesa/pedido rápido y de las combinaciones de entrada básicas. */
public class PedidoRapidoCombinatoriaTest {
    private static final class RepositorioMemoria implements PedidosRepositorio {
        int registros;
        Pedidos ultimo;
        @Override public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
            registros++;
            ultimo = pedido;
            return registros;
        }
        @Override public boolean actualizarPedidoCompleto(int id, Pedidos pedido, List<DetallePedido> detalles) { return false; }
        @Override public int verificarStado(int mesa, int sala) { return 0; }
        @Override public Pedidos verPedido(int id) { return ultimo; }
        @Override public List<DetallePedido> verPedidoDetalle(int id) { return List.of(); }
        @Override public List<DetallePedido> finalizarPedido(int id) { return List.of(); }
        @Override public boolean actualizarEstado(int id) { return false; }
        @Override public List<Pedidos> listarPedidos() { return ultimo == null ? List.of() : List.of(ultimo); }
    }

    private Pedidos pedido(String tipo, int sala, int mesa) {
        return pedido(tipo, sala, mesa, 1);
    }

    private Pedidos pedido(String tipo, int sala, int mesa, int cantidad) {
        Pedidos p = new Pedidos();
        p.setTipoPedido(tipo);
        p.setId_sala(sala);
        p.setNum_mesa(mesa);
        p.setUsuario("Operador de pruebas");
        BigDecimal subtotal = new BigDecimal("10.00").multiply(BigDecimal.valueOf(cantidad));
        p.setSubtotal(subtotal);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(subtotal);
        return p;
    }

    private List<DetallePedido> detalles(int cantidad) {
        DetallePedido d = new DetallePedido();
        d.setNombre("Plato de prueba");
        d.setCantidad(cantidad);
        d.setPrecioDecimal(new BigDecimal("10.00"));
        return List.of(d);
    }

    @Test
    public void combinatoriaCompletaTipoSalaMesa() {
        RepositorioMemoria repo = new RepositorioMemoria();
        PedidoServicio servicio = new PedidoServicio(repo);
        int combinaciones = 0;
        for (String tipo : List.of("MESA", "RAPIDO")) {
            for (int sala : new int[]{0, 1}) {
                for (int mesa : new int[]{0, 1}) {
                    combinaciones++;
                    boolean esperadoValido = "MESA".equals(tipo)
                            ? sala > 0 && mesa > 0
                            : sala == 0 && mesa == 0;
                    if (esperadoValido) {
                        assertTrue("Debe persistir " + tipo + " sala=" + sala + " mesa=" + mesa,
                                servicio.registrarPedidoCompleto(pedido(tipo, sala, mesa), detalles(1)) > 0);
                        assertEquals(tipo, repo.ultimo.getTipoPedido());
                    } else {
                        assertThrows("Debe rechazar " + tipo + " sala=" + sala + " mesa=" + mesa,
                                ErrorAplicacionException.class,
                                () -> servicio.registrarPedidoCompleto(pedido(tipo, sala, mesa), detalles(1)));
                    }
                }
            }
        }
        assertEquals(8, combinaciones);
        assertEquals(2, repo.registros);
    }

    @Test
    public void matricesDeTipoYCantidadRechazanEntradasFueraDeRango() {
        RepositorioMemoria repo = new RepositorioMemoria();
        PedidoServicio servicio = new PedidoServicio(repo);
        assertEquals("MESA", new Pedidos().getTipoPedido());
        assertThrows(ErrorAplicacionException.class, () -> new Pedidos().setTipoPedido("DOMICILIO_DESCONOCIDO"));
        assertThrows(ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(pedido("RAPIDO", 0, 0), List.of()));

        for (int cantidad : new int[]{0, DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + 1}) {
            assertThrows("Cantidad inválida: " + cantidad, ErrorAplicacionException.class,
                    () -> servicio.registrarPedidoCompleto(pedido("RAPIDO", 0, 0), detalles(cantidad)));
        }
        for (int cantidad : new int[]{1, DetallePedido.CANTIDAD_MAXIMA_POR_PLATO}) {
            assertTrue("Cantidad válida: " + cantidad,
                    servicio.registrarPedidoCompleto(pedido("RAPIDO", 0, 0, cantidad), detalles(cantidad)) > 0);
        }
        assertEquals(2, repo.registros);
    }
}
