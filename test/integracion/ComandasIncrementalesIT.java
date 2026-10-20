package integracion;

import Modelo.ComandaMarcha;
import Modelo.DetallePedido;
import Modelo.MovimientoComanda;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Servicio.CalculoMonetarioPedido;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Verifica persistencia atómica de diferencias de cocina contra el MySQL desechable de integración. */
public class ComandasIncrementalesIT {
    private final String usuario = "IT_COMANDA_" + UUID.randomUUID();
    private final PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc());
    private int idPedido;

    @Before
    public void verificarDestinoDePrueba() throws Exception {
        try (Connection con = new ProveedorConexionJdbc().getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE(), @@port")) {
            if (!rs.next() || !"restaurante_test".equals(rs.getString(1))
                    || (rs.getInt(2) != 3306 && rs.getInt(2) != 3307)) {
                throw new IllegalStateException("La integración de comandas exige restaurante_test en MySQL desechable.");
            }
        }
    }

    @After
    public void limpiarPedidoDePrueba() throws SQLException {
        if (idPedido <= 0) return;
        try (Connection con = new ProveedorConexionJdbc().getConnection()) {
            con.setAutoCommit(false);
            try {
                for (String tabla : List.of("comandas_marchas_detalle", "comandas_marchas", "detalle_pedidos")) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tabla
                            + ("comandas_marchas_detalle".equals(tabla)
                                    ? " WHERE id_marcha IN (SELECT id FROM comandas_marchas WHERE id_pedido=?)"
                                    : " WHERE id_pedido=?"))) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id=?")) {
                    ps.setInt(1, idPedido);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        }
    }

    @Test
    public void creaComandaInicialYEnLaRondaSiguienteSoloPersisteElIncremento() {
        DetallePedido inicial = detalle(41, 2, "sin sal");
        ComandaMarcha primera = crearPedido(List.of(inicial));

        assertEquals(ComandaMarcha.Tipo.INICIAL, primera.tipo());
        assertEquals(1, primera.secuencia());
        assertEquals(2, primera.movimientos().get(0).cantidad());

        ComandaMarcha segunda = actualizar(List.of(detalle(41, 5, "sin sal")));
        assertNotNull(segunda);
        assertEquals(ComandaMarcha.Tipo.INCREMENTO, segunda.tipo());
        assertEquals(2, segunda.secuencia());
        assertEquals(MovimientoComanda.Accion.PREPARAR, segunda.movimientos().get(0).accion());
        assertEquals(3, segunda.movimientos().get(0).cantidad());
        assertEquals(3, dao.obtenerComandaMarcha(segunda.id()).movimientos().get(0).cantidad());
        List<ComandaMarcha> historial = dao.listarComandasMarchas(idPedido);
        assertEquals(2, historial.size());
        assertEquals(1, historial.get(0).secuencia());
        assertEquals(2, historial.get(0).movimientos().get(0).cantidad());
        assertEquals(2, historial.get(1).secuencia());
        assertEquals(3, historial.get(1).movimientos().get(0).cantidad());
    }

    @Test
    public void reduccionYCambioDeNotaSeGuardanComoAjusteSinReemitirElPedidoCompleto() {
        crearPedido(List.of(detalle(41, 3, "sin sal")));

        ComandaMarcha reduccion = actualizar(List.of(detalle(41, 2, "sin sal")));
        assertEquals(ComandaMarcha.Tipo.AJUSTE, reduccion.tipo());
        assertEquals(MovimientoComanda.Accion.RETIRAR, reduccion.movimientos().get(0).accion());
        assertEquals(1, reduccion.movimientos().get(0).cantidad());

        ComandaMarcha cambioNota = actualizar(List.of(detalle(41, 2, "sin cebolla")));
        assertEquals(ComandaMarcha.Tipo.AJUSTE, cambioNota.tipo());
        assertEquals(2, cambioNota.movimientos().size());
        assertEquals(MovimientoComanda.Accion.PREPARAR, cambioNota.movimientos().get(0).accion());
        assertEquals(2, cambioNota.movimientos().get(0).cantidad());
        assertEquals(MovimientoComanda.Accion.RETIRAR, cambioNota.movimientos().get(1).accion());
        assertEquals(2, cambioNota.movimientos().get(1).cantidad());
    }

    @Test
    public void guardarSinCambiosNoCreaMarchaNiReimpresion() {
        crearPedido(List.of(detalle(41, 1, "")));

        assertNull(actualizar(List.of(detalle(41, 1, ""))));
        assertEquals(1, contarMarchas());
    }

    @Test
    public void cambiosSinImpresionSeAcumulanHastaLaSiguienteComandaEmitida() {
        List<DetallePedido> inicial = List.of(detalle(41, 2, ""));
        Modelo.ResultadoPedidoComanda creado = dao.registrarPedidoCompletoConMarcha(pedido(inicial), inicial, false);
        idPedido = creado.idPedido();
        assertNull(creado.marcha());

        List<DetallePedido> primeraActualizacion = List.of(detalle(41, 4, ""));
        Modelo.ResultadoPedidoComanda guardado = dao.actualizarPedidoCompletoConMarcha(
                idPedido, pedido(primeraActualizacion), primeraActualizacion, false);
        assertNull(guardado.marcha());

        ComandaMarcha emitida = actualizar(List.of(detalle(41, 5, "")));
        assertEquals(ComandaMarcha.Tipo.INICIAL, emitida.tipo());
        assertEquals(5, emitida.movimientos().get(0).cantidad());
    }

    @Test
    public void actualizacionesConcurrentesNoDuplicanElIncrementoEnviado() throws Exception {
        crearPedido(List.of(detalle(41, 2, "")));
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService ejecutores = Executors.newFixedThreadPool(2);
        try {
            Future<Modelo.ResultadoPedidoComanda> una = ejecutores.submit(() -> {
                inicio.await();
                return new PedidosDao(new ProveedorConexionJdbc()).actualizarPedidoCompletoConMarcha(
                        idPedido, pedido(List.of(detalle(41, 5, ""))), List.of(detalle(41, 5, "")));
            });
            Future<Modelo.ResultadoPedidoComanda> dos = ejecutores.submit(() -> {
                inicio.await();
                return new PedidosDao(new ProveedorConexionJdbc()).actualizarPedidoCompletoConMarcha(
                        idPedido, pedido(List.of(detalle(41, 5, ""))), List.of(detalle(41, 5, "")));
            });
            inicio.countDown();
            Modelo.ResultadoPedidoComanda resultadoUno = una.get();
            Modelo.ResultadoPedidoComanda resultadoDos = dos.get();

            int marchasConIncremento = (resultadoUno.marcha() == null ? 0 : 1)
                    + (resultadoDos.marcha() == null ? 0 : 1);
            assertEquals("Solo una actualización puede emitir el incremento de tres unidades", 1, marchasConIncremento);
            ComandaMarcha emitida = resultadoUno.marcha() != null ? resultadoUno.marcha() : resultadoDos.marcha();
            assertEquals(3, emitida.movimientos().get(0).cantidad());
            assertEquals(2, contarMarchas());
        } finally {
            ejecutores.shutdownNow();
        }
    }

    @Test
    public void migracionRepetidaCreaUnSoloBaselineParaPedidoPendienteExistente() throws Exception {
        List<DetallePedido> detalles = List.of(detalle(41, 2, ""));
        Modelo.ResultadoPedidoComanda creado = dao.registrarPedidoCompletoConMarcha(pedido(detalles), detalles, false);
        idPedido = creado.idPedido();

        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new ProveedorConexionJdbc());
        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new ProveedorConexionJdbc());

        try (Connection con = new ProveedorConexionJdbc().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*), MAX(tipo) FROM comandas_marchas WHERE id_pedido=?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals("BASE_LEGADA", rs.getString(2));
            }
        }
        assertEquals(1, contarLineasMarchaLegada());
        assertTrue("El baseline de migración no debe ofrecerse para reimpresión",
                dao.listarComandasMarchas(idPedido).isEmpty());
    }

    private ComandaMarcha crearPedido(List<DetallePedido> detalles) {
        Modelo.ResultadoPedidoComanda resultado = dao.registrarPedidoCompletoConMarcha(pedido(detalles), detalles);
        idPedido = resultado.idPedido();
        return resultado.marcha();
    }

    private ComandaMarcha actualizar(List<DetallePedido> detalles) {
        return dao.actualizarPedidoCompletoConMarcha(idPedido, pedido(detalles), detalles).marcha();
    }

    private int contarMarchas() {
        try (Connection con = new ProveedorConexionJdbc().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM comandas_marchas WHERE id_pedido=?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int contarLineasMarchaLegada() {
        try (Connection con = new ProveedorConexionJdbc().getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT COUNT(*) FROM comandas_marchas_detalle md
                     JOIN comandas_marchas m ON md.id_marcha=m.id
                     WHERE m.id_pedido=? AND m.tipo='BASE_LEGADA'
                     """)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Pedidos pedido(List<DetallePedido> detalles) {
        BigDecimal tasa = new BigDecimal("1.0000");
        CalculoMonetarioPedido.CalculoPedido calculo = CalculoMonetarioPedido.calcularPedido(detalles, tasa);
        Pedidos pedido = new Pedidos();
        pedido.setTipoPedido("RAPIDO");
        pedido.setId_sala(0);
        pedido.setNum_mesa(0);
        pedido.setUsuario(usuario);
        pedido.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));
        pedido.setSubtotal(calculo.subtotalUsd());
        pedido.setIvaMonto(calculo.ivaUsd());
        pedido.setTotalDecimal(calculo.totalUsd());
        pedido.setTasaCambio(tasa);
        pedido.setSubtotalBs(calculo.subtotalVes());
        pedido.setIvaBs(calculo.ivaVes());
        pedido.setTotalBs(calculo.totalVes());
        return pedido;
    }

    private static DetallePedido detalle(int idPlato, int cantidad, String comentario) {
        DetallePedido detalle = new DetallePedido();
        detalle.setIdPlato(idPlato);
        detalle.setNombre("Producto prueba cocina");
        detalle.setPrecioDecimal(new BigDecimal("10.00"));
        detalle.setCantidad(cantidad);
        detalle.setComentario(comentario);
        detalle.setMonedaBase("USD");
        detalle.setAplicaIva(false);
        detalle.setPrecioIncluyeIva(false);
        detalle.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));
        // El ID null evita depender del catálogo de platos en esta integración del diario de comandas.
        detalle.setIdPlato(null);
        return detalle;
    }
}
