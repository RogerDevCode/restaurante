package integracion;

import Modelo.ActualizacionTasaDao;
import Modelo.CierreCaja;
import Modelo.CierreCajaDao;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.MovimientoSaldoPedido;
import Modelo.MovimientoSaldoPedidoDao;
import Modelo.PagoPedido;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.ResumenConciliacionPedido;
import Modelo.Salas;
import Modelo.SalasDao;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite de integración para la Task 7:
 * - Cierre de mesa y cotización congelada.
 * - Inmunidad de pedidos cerrados ante cambios posteriores de tasa y catálogo.
 * - Liquidación mixta con ajuste cambiario FX exacto (SUM(monto_bs_aplicado) + ajuste_cambio_bs = total_bs_congelado).
 * - Manejo de efectivo excedente y cambio devuelto (recibido = aplicado + vuelto).
 * - Conciliación histórica P2-D (pagado_bs_aplicado no se revaloriza con tasa actual).
 * - Bloqueo de anulación física/lógica si hay pagos no conciliados.
 */
public class CierreMesaYPagosCongeladosIT {
    private static final String USUARIO_PRUEBA = "__IT_CIERRE_TASK7__";
    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
    private final PlatosDao platosDao = new PlatosDao(proveedor);
    private final PedidosDao pedidosDao = new PedidosDao(proveedor);
    private final ActualizacionTasaDao tasaDao = new ActualizacionTasaDao(proveedor);
    private final MovimientoSaldoPedidoDao movDao = new MovimientoSaldoPedidoDao(proveedor);

    @BeforeClass
    public static void comprobarBaseAislada() {
        String url = System.getProperty("DB_URL", "");
        assertTrue("La URL debe apuntar exclusivamente a restaurante_test en 127.0.0.1:3307",
                url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/restaurante_test(?:\\?.*)?"));
    }

    private static Connection conexion() throws SQLException {
        return DriverManager.getConnection(
                System.getProperty("DB_URL"),
                System.getProperty("DB_USER"),
                System.getProperty("DB_PASSWORD"));
    }

    @Before
    public void limpiarEstado() throws SQLException {
        limpiarBase();
    }

    @After
    public void limpiarDatos() throws SQLException {
        limpiarBase();
    }

    private void limpiarBase() throws SQLException {
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM pedido_saldos_movimientos WHERE actor='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM pedido_pagos WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM pedido_liquidaciones WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM detalle_pedidos WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'IT-T7-%'");
            st.executeUpdate("DELETE FROM salas WHERE nombre LIKE 'IT-T7-%'");
            st.executeUpdate("DELETE FROM auditoria_cambios_precio WHERE actor='" + USUARIO_PRUEBA + "'");
        }
    }

    private int crearSalaPrueba(int mesas) {
        SalasDao salasDao = new SalasDao(proveedor);
        Salas sala = new Salas();
        sala.setNombre("IT-T7-SALA-" + UUID.randomUUID());
        sala.setMesas(mesas);
        assertTrue(salasDao.registrar(sala));
        return salasDao.listar().stream()
                .filter(s -> s.getNombre().equals(sala.getNombre()))
                .mapToInt(Salas::getId)
                .findFirst()
                .orElseThrow();
    }

    private Platos crearPlato(String nombre, BigDecimal precioUsd, BigDecimal precioVes, String monedaBase, boolean gravado, Boolean incIva) {
        String nombreUnico = "IT-T7-" + nombre + "-" + UUID.randomUUID();
        Platos p = new Platos();
        p.setNombre(nombreUnico);
        p.setPrecioUsd(precioUsd);
        p.setPrecioVes(precioVes);
        p.setPrecioDecimal("USD".equals(monedaBase) ? precioUsd : precioVes);
        p.setMonedaBase(monedaBase);
        p.setAplicaIva(gravado);
        p.setPrecioIncluyeIva(incIva);
        p.setPrecioClasificado(true);
        p.setFecha(LocalDate.now().toString());
        assertTrue(platosDao.registrar(p));
        return platosDao.listarPorFecha("", LocalDate.now().toString()).stream()
                .filter(pl -> pl.getNombre().equals(nombreUnico))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void testSecuenciaCierreCongelaImportesYDespuesCambioTasaNoAfecta() throws Exception {
        int idSala = crearSalaPrueba(2);
        BigDecimal tasaInicial = new BigDecimal("40.0000");

        Platos platoUsd = crearPlato("USD-PLATO", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        // 1. Abrir comanda en mesa 1
        Pedidos p1 = new Pedidos();
        p1.setId_sala(idSala);
        p1.setNum_mesa(1);
        p1.setUsuario(USUARIO_PRUEBA);
        p1.setTasaCambio(tasaInicial);
        p1.setIvaPorcentaje(BigDecimal.ZERO);
        p1.setSubtotal(new BigDecimal("10.00"));
        p1.setIvaMonto(BigDecimal.ZERO);
        p1.setTotalDecimal(new BigDecimal("10.00"));
        DetallePedido d1 = new DetallePedido();
        d1.setIdPlato(platoUsd.getId());
        d1.setNombre(platoUsd.getNombre());
        d1.setPrecioDecimal(new BigDecimal("10.00"));
        d1.setCantidad(1);
        d1.setMonedaBase("USD");
        d1.setAplicaIva(false);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido = pedidosDao.registrarPedidoCompleto(p1, List.of(d1));

        // 2. Tasa sube a 45.00 -> mesa abierta se recalcula a Bs. 450.00, version_precio = 2
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("45.0000"), USUARIO_PRUEBA, null, idSala);
        Pedidos pedPostTasa1 = pedidosDao.verPedido(idPedido);
        assertEquals(new BigDecimal("10.00"), pedPostTasa1.getTotalDecimal());
        assertEquals(new BigDecimal("450.00"), pedPostTasa1.getTotalBs());
        assertEquals(new BigDecimal("45.0000"), pedPostTasa1.getTasaCambio());
        assertEquals(2, pedPostTasa1.getVersionPrecio());

        // 3. Cerrar mesa: congela snapshot en versión 2
        assertTrue(pedidosDao.cerrarMesa(idPedido, 2));

        // Verificar que cerrarMesa es idempotente
        assertTrue(pedidosDao.cerrarMesa(idPedido, 2));

        // Verificar que versión obsoleta es rechazada
        assertThrows(ErrorAplicacionException.class, () -> pedidosDao.cerrarMesa(idPedido, 1));

        // 4. Cambiar tasa nuevamente de 45.00 a 60.00: La mesa cerrada DEBE CONSERVAR Bs. 450.00 y tasa 45.0000
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("60.0000"), USUARIO_PRUEBA, null, idSala);

        Pedidos pedCerrado = pedidosDao.verPedido(idPedido);
        assertEquals("Total USD debe seguir congelado", new BigDecimal("10.00"), pedCerrado.getTotalDecimal());
        assertEquals("Total Bs debe seguir congelado a 450.00", new BigDecimal("450.00"), pedCerrado.getTotalBs());
        assertEquals("Tasa de cambio debe seguir congelada a 45.0000", new BigDecimal("45.0000"), pedCerrado.getTasaCambio());
        assertEquals("Versión debe ser 3 tras el cierre", 3, pedCerrado.getVersionPrecio());
        assertNotNull("cerrada_en no debe ser nulo", pedCerrado.getCerradaEn());

        // 5. Cobrar mesa cerrada: usa exactamente la tasa congelada (45.0000) y Bs. 450.00
        List<PagoPedido> pagos = List.of(new PagoPedido("EFECTIVO_BS", "VES", new BigDecimal("450.00")));
        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Cobro", "V-777", "EFECTIVO_BS", pagos));

        Pedidos pedFinalizado = pedidosDao.verPedido(idPedido);
        assertEquals("FINALIZADO", pedFinalizado.getEstado());
        assertEquals(new BigDecimal("450.00"), pedFinalizado.getTotalBs());
        assertEquals(new BigDecimal("45.0000"), pedFinalizado.getTasaCambio());
    }

    @Test
    public void testPagoMixtoConAjusteCambiarioFxExacto() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasaCierre = new BigDecimal("40.0000");
        tasaDao.actualizarTasaYRecalcularPendientes(tasaCierre, USUARIO_PRUEBA, null, idSala);

        // Creamos un plato VES con importe impar 100.03 VES
        Platos platoVes = crearPlato("VES-IMPAR", new BigDecimal("2.50"), new BigDecimal("100.03"), "VES", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasaCierre);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("2.50"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("2.50"));
        p.setTotalBs(new BigDecimal("100.03"));
        DetallePedido d = new DetallePedido();
        d.setIdPlato(platoVes.getId());
        d.setNombre(platoVes.getNombre());
        d.setPrecioDecimal(new BigDecimal("100.03"));
        d.setCantidad(1);
        d.setMonedaBase("VES");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        // Forzar total_bs = 100.03 en pedidos
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET total_bs = 100.03 WHERE id = ?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        // Pago mixto: 40.00 VES + 1.50 USD (1.50 USD * 40.00 = 60.00 VES).
        // Total aplicado en Bs = 100.00 VES.
        // Diferencia FX = +0.03 VES.
        List<PagoPedido> pagos = List.of(
                new PagoPedido("PAGO_MOVIL", "VES", new BigDecimal("40.00")),
                new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("1.50"))
        );

        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Mixto", "V-888", "MIXTO", pagos));

        // Verificar liquidación guardada con ajuste_cambio_bs
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT aplicado_ves, aplicado_usd, ajuste_cambio_bs FROM pedido_liquidaciones WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("Debe existir fila en pedido_liquidaciones", rs.next());
                assertEquals(new BigDecimal("40.00"), rs.getBigDecimal("aplicado_ves"));
                assertEquals(new BigDecimal("1.50"), rs.getBigDecimal("aplicado_usd"));
                assertEquals(new BigDecimal("0.03"), rs.getBigDecimal("ajuste_cambio_bs"));

                // Verificar la regla fundamental: SUM(monto_bs_aplicado) + ajuste_cambio_bs = total_bs_congelado
                BigDecimal sumBsAplicado = new BigDecimal("40.00").add(new BigDecimal("1.50").multiply(tasaCierre));
                BigDecimal totalBsCongelado = sumBsAplicado.add(rs.getBigDecimal("ajuste_cambio_bs")).setScale(2, RoundingMode.HALF_UP);
                assertEquals(new BigDecimal("100.03"), totalBsCongelado);
            }
        }

        // Verificar pedido_pagos
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT metodo_pago, moneda, monto_aplicado, cambio_devuelto, es_legado FROM pedido_pagos WHERE id_pedido = ? ORDER BY id ASC")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PAGO_MOVIL", rs.getString("metodo_pago"));
                assertEquals("VES", rs.getString("moneda"));
                assertEquals(new BigDecimal("40.00"), rs.getBigDecimal("monto_aplicado"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("cambio_devuelto"));
                assertEquals(0, rs.getInt("es_legado"));

                assertTrue(rs.next());
                assertEquals("EFECTIVO_USD", rs.getString("metodo_pago"));
                assertEquals("USD", rs.getString("moneda"));
                assertEquals(new BigDecimal("1.50"), rs.getBigDecimal("monto_aplicado"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("cambio_devuelto"));
                assertEquals(0, rs.getInt("es_legado"));
            }
        }
    }

    @Test
    public void testEfectivoExcedenteRegistraCambioDevuelto() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasaCierre = new BigDecimal("40.0000");

        Platos plato = crearPlato("USD-EXCEDENTE", new BigDecimal("15.00"), new BigDecimal("600.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasaCierre);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("15.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("15.00"));
        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("15.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        // Cliente entrega un billete de $20 USD para pagar la comanda de $15 USD ($5 de cambio devuelto)
        PagoPedido pagoConVuelto = new PagoPedido(
                "EFECTIVO_USD",
                "USD",
                new BigDecimal("15.00"), // monto
                new BigDecimal("20.00"), // montoRecibido
                new BigDecimal("15.00"), // montoAplicado
                new BigDecimal("5.00")   // cambioDevuelto
        );

        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Vuelto", "V-999", "EFECTIVO_USD", List.of(pagoConVuelto)));

        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT monto_recibido, monto_aplicado, cambio_devuelto, es_legado FROM pedido_pagos WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(new BigDecimal("20.00"), rs.getBigDecimal("monto_recibido"));
                assertEquals(new BigDecimal("15.00"), rs.getBigDecimal("monto_aplicado"));
                assertEquals(new BigDecimal("5.00"), rs.getBigDecimal("cambio_devuelto"));
                assertEquals(0, rs.getInt("es_legado"));
            }
        }
    }

    @Test
    public void testResumenConciliacionReglaP2D() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasaOriginal = new BigDecimal("40.0000");
        tasaDao.actualizarTasaYRecalcularPendientes(tasaOriginal, USUARIO_PRUEBA, null, idSala);

        Platos plato = crearPlato("USD-CONCIL", new BigDecimal("50.00"), new BigDecimal("2000.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasaOriginal);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("50.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("50.00"));
        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("50.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        // Insertar un pago histórico/legado de $20 USD (equivalente guardado: Bs. 800.00 a tasa 40.00)
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("""
            INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, es_legado)
            VALUES (?, 'EFECTIVO_USD', 'USD', 20.00, 40.0000, 800.00, 20.00, 1)
            """)) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        // Resumen antes de cambio de tasa
        ResumenConciliacionPedido r1 = pedidosDao.resumenConciliacion(idPedido);
        assertNotNull(r1);
        assertEquals(new BigDecimal("800.00"), r1.pagadoBsAplicado());
        assertEquals(new BigDecimal("2000.00"), r1.totalBsCongelado());
        assertEquals(new BigDecimal("1200.00"), r1.saldoBsPendiente());

        // Cambiar la tasa global en la base a 60.0000
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("60.0000"), USUARIO_PRUEBA, null, idSala);

        // Resumen después del cambio de tasa: Demostrar que según P2-D el monto pagado en Bs sigue siendo 800.00
        ResumenConciliacionPedido r2 = pedidosDao.resumenConciliacion(idPedido);
        assertNotNull(r2);
        assertEquals("P2-D: El pagado histórico en Bs NO debe revalorizarse con la tasa nueva",
                new BigDecimal("800.00"), r2.pagadoBsAplicado());
        assertEquals(new BigDecimal("2000.00"), r2.totalBsCongelado());
        assertEquals(new BigDecimal("1200.00"), r2.saldoBsPendiente());
    }

    @Test
    public void testAnulacionBloqueadaSiHayPagosSinMovimientoDeCompensacion() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasa = new BigDecimal("40.0000");

        Platos plato = crearPlato("USD-ANULA", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("10.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("10.00"));
        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        // Insertar un pago registrado
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("""
            INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, tasa_cambio, monto_bs, monto_usd, es_legado)
            VALUES (?, 'EFECTIVO_BS', 'VES', 400.00, 40.0000, 400.00, 10.00, 1)
            """)) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        // Intentar anular directamente debe fallar por tener pagos sin movimiento de resolución
        assertThrows(ErrorAplicacionException.class,
                () -> pedidosDao.anularPedidoConAuditoria(idPedido, "Cliente se fue", USUARIO_PRUEBA));

        // Registrar movimiento trazable de compensación/devolución
        MovimientoSaldoPedido mov = new MovimientoSaldoPedido(
                idPedido, null, "DEVOLUCION", "VES",
                new BigDecimal("400.00"), new BigDecimal("400.00"), tasa,
                USUARIO_PRUEBA, "Devolución en efectivo autorizada", "MOV-" + idPedido + "-1"
        );
        assertTrue(movDao.registrarMovimiento(mov));

        // Ahora la anulación debe proceder exitosamente
        assertTrue(pedidosDao.anularPedidoConAuditoria(idPedido, "Anulado tras devolucion", USUARIO_PRUEBA));

        Pedidos pedAnulado = pedidosDao.verPedido(idPedido);
        assertEquals("ANULADO", pedAnulado.getEstado());
    }

    @Test
    public void testReintentoPagoNoDuplicaCobroNiRegistros() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasa = new BigDecimal("40.0000");
        tasaDao.actualizarTasaYRecalcularPendientes(tasa, USUARIO_PRUEBA, null, idSala);

        Platos plato = crearPlato("USD-REINTENTO", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("10.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("10.00"));

        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);

        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));
        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        List<PagoPedido> pagos = List.of(new PagoPedido("TARJETA", "VES", new BigDecimal("400.00")));

        // Primer cobro
        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Fiel", "V-11111111", "TARJETA", pagos));

        // Reintento idéntico (simula doble submit o reintento de red)
        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Fiel", "V-11111111", "TARJETA", pagos));

        // Verificar que en la base de datos existe exactamente 1 liquidación y exactamente 1 fila de pago
        try (Connection con = conexion()) {
            try (PreparedStatement psLiq = con.prepareStatement(
                    "SELECT COUNT(*) FROM pedido_liquidaciones WHERE id_pedido = ?")) {
                psLiq.setInt(1, idPedido);
                try (ResultSet rs = psLiq.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Debe existir exactamente 1 liquidación", 1, rs.getInt(1));
                }
            }
            try (PreparedStatement psPag = con.prepareStatement(
                    "SELECT COUNT(*) FROM pedido_pagos WHERE id_pedido = ?")) {
                psPag.setInt(1, idPedido);
                try (ResultSet rs = psPag.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Debe existir exactamente 1 registro de pago", 1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testCerrarMesaFallaSiTienePlatoSinClasificar() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasa = new BigDecimal("40.0000");

        Platos plato = crearPlato("USD-CLASIF", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("10.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("10.00"));

        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);

        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        // Simular que una línea histórica quedó sin clasificar (moneda_base = NULL)
        try (Connection con = conexion();
             PreparedStatement ps = con.prepareStatement("UPDATE detalle_pedidos SET moneda_base = NULL WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class,
                () -> pedidosDao.cerrarMesa(idPedido, 1));
        assertTrue(ex.getMessage().contains("sin clasificar"));
    }

    @Test
    public void testReintentoPagoConOrdenInvertidoEsIdempotenteYNoDuplica() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasa = new BigDecimal("40.0000");
        tasaDao.actualizarTasaYRecalcularPendientes(tasa, USUARIO_PRUEBA, null, idSala);

        Platos plato = crearPlato("USD-REORDEN", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(BigDecimal.ZERO);
        p.setSubtotal(new BigDecimal("10.00"));
        p.setIvaMonto(BigDecimal.ZERO);
        p.setTotalDecimal(new BigDecimal("10.00"));

        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(BigDecimal.ZERO);

        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));
        assertTrue(pedidosDao.cerrarMesa(idPedido, 1));

        PagoPedido pagoBs = new PagoPedido("EFECTIVO_BS", "VES", new BigDecimal("200.00"));
        PagoPedido pagoUsd = new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("5.00"));

        // Primer cobro: orden [pagoBs, pagoUsd]
        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Idemp", "V-22222222", "MIXTO", List.of(pagoBs, pagoUsd)));

        // Reintento: orden inverso [pagoUsd, pagoBs]
        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente Idemp", "V-22222222", "MIXTO", List.of(pagoUsd, pagoBs)));

        // Comprobar que en BD existen exactamente 1 liquidación y exactamente 2 pagos (no se duplicaron)
        try (Connection con = conexion()) {
            try (PreparedStatement psLiq = con.prepareStatement("SELECT COUNT(*) FROM pedido_liquidaciones WHERE id_pedido = ?")) {
                psLiq.setInt(1, idPedido);
                try (ResultSet rs = psLiq.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Debe existir exactamente 1 liquidación", 1, rs.getInt(1));
                }
            }
            try (PreparedStatement psPag = con.prepareStatement("SELECT COUNT(*) FROM pedido_pagos WHERE id_pedido = ?")) {
                psPag.setInt(1, idPedido);
                try (ResultSet rs = psPag.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Debe existir exactamente 2 registros de pago", 2, rs.getInt(1));
                }
            }
        }
    }
}
