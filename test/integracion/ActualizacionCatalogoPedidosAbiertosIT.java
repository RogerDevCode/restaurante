package integracion;

import Modelo.AuditoriaPreciosDao;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.PreciosPedidosDao;
import Modelo.ResultadoActualizacionPrecios;
import Modelo.Salas;
import Modelo.SalasDao;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
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
import static org.junit.Assert.assertTrue;

/**
 * Prueba de integración transaccional para la propagación de cambios de catálogo
 * a pedidos abiertos (Task 5).
 */
public class ActualizacionCatalogoPedidosAbiertosIT {
    private static final String USUARIO_PRUEBA = "__IT_PROPAGACION__";
    private final ProveedorConexionJdbc proveedorConexiones = new ProveedorConexionJdbc();
    private final PlatosDao platosDao = new PlatosDao(proveedorConexiones);
    private final PedidosDao pedidosDao = new PedidosDao(proveedorConexiones);
    private final PreciosPedidosDao preciosPedidosDao = new PreciosPedidosDao(proveedorConexiones);

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
            st.executeUpdate("DELETE FROM detalle_pedidos WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'IT-PROP-%'");
            st.executeUpdate("DELETE FROM salas WHERE nombre LIKE 'IT-SALA-%'");
            st.executeUpdate("DELETE FROM auditoria_cambios_precio WHERE actor='" + USUARIO_PRUEBA + "'");
        }
    }

    private int crearSalaPrueba(int mesas) {
        SalasDao salasDao = new SalasDao(proveedorConexiones);
        Salas sala = new Salas();
        sala.setNombre("IT-SALA-" + UUID.randomUUID());
        sala.setMesas(mesas);
        assertTrue(salasDao.registrar(sala));
        return salasDao.listar().stream()
                .filter(s -> s.getNombre().equals(sala.getNombre()))
                .mapToInt(Salas::getId)
                .findFirst()
                .orElseThrow();
    }

    private Platos crearPlato(String nombre, BigDecimal precioUsd, BigDecimal precioVes, String monedaBase, boolean gravado, Boolean incIva) {
        Platos p = new Platos();
        p.setNombre(nombre);
        p.setPrecioUsd(precioUsd);
        p.setPrecioVes(precioVes);
        p.setMonedaBase(monedaBase);
        p.setAplicaIva(gravado);
        p.setPrecioIncluyeIva(incIva);
        p.setPrecioClasificado(true);
        p.setPrecioDecimal("VES".equalsIgnoreCase(monedaBase) ? precioVes : precioUsd);
        p.setFecha(LocalDate.now().toString());
        assertTrue(platosDao.registrar(p));
        return platosDao.listarPorFecha("", LocalDate.now().toString()).stream()
                .filter(pl -> pl.getNombre().equals(nombre))
                .findFirst()
                .orElseThrow();
    }

    @Test
    public void dosMesasAbiertasSeRecalculanMesaCerradaYPedidoFinalizadoPermanecenIntactos() throws Exception {
        int idSala = crearSalaPrueba(4);
        BigDecimal tasa = new BigDecimal("40.0000");

        // 1. Crear producto en catálogo: $10.00 USD, 16% IVA no incluido
        Platos plato = crearPlato("IT-PROP-BURGER", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", true, false);

        // Mesa 1: Abierta con 2 raciones ($20.00 base, $3.20 IVA -> $23.20 USD / Bs. 928.00)
        Pedidos p1 = new Pedidos();
        p1.setId_sala(idSala);
        p1.setNum_mesa(1);
        p1.setUsuario(USUARIO_PRUEBA);
        p1.setTasaCambio(tasa);
        p1.setIvaPorcentaje(new BigDecimal("16.00"));
        p1.setSubtotal(new BigDecimal("20.00"));
        p1.setIvaMonto(new BigDecimal("3.20"));
        p1.setTotalDecimal(new BigDecimal("23.20"));
        DetallePedido d1 = new DetallePedido();
        d1.setIdPlato(plato.getId());
        d1.setNombre(plato.getNombre());
        d1.setPrecioDecimal(new BigDecimal("10.00"));
        d1.setCantidad(2);
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido1 = pedidosDao.registrarPedidoCompleto(p1, List.of(d1));

        // Mesa 2: Abierta con 1 ración ($10.00 base, $1.60 IVA -> $11.60 USD / Bs. 464.00)
        Pedidos p2 = new Pedidos();
        p2.setId_sala(idSala);
        p2.setNum_mesa(2);
        p2.setUsuario(USUARIO_PRUEBA);
        p2.setTasaCambio(tasa);
        p2.setIvaPorcentaje(new BigDecimal("16.00"));
        p2.setSubtotal(new BigDecimal("10.00"));
        p2.setIvaMonto(new BigDecimal("1.60"));
        p2.setTotalDecimal(new BigDecimal("11.60"));
        DetallePedido d2 = new DetallePedido();
        d2.setIdPlato(plato.getId());
        d2.setNombre(plato.getNombre());
        d2.setPrecioDecimal(new BigDecimal("10.00"));
        d2.setCantidad(1);
        d2.setMonedaBase("USD");
        d2.setAplicaIva(true);
        d2.setPrecioIncluyeIva(false);
        d2.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido2 = pedidosDao.registrarPedidoCompleto(p2, List.of(d2));

        // Mesa 3: Mesa CERRADA pero pendiente de pago (cerrada_en != NULL)
        Pedidos p3 = new Pedidos();
        p3.setId_sala(idSala);
        p3.setNum_mesa(3);
        p3.setUsuario(USUARIO_PRUEBA);
        p3.setTasaCambio(tasa);
        p3.setIvaPorcentaje(new BigDecimal("16.00"));
        p3.setSubtotal(new BigDecimal("10.00"));
        p3.setIvaMonto(new BigDecimal("1.60"));
        p3.setTotalDecimal(new BigDecimal("11.60"));
        DetallePedido d3 = new DetallePedido();
        d3.setIdPlato(plato.getId());
        d3.setNombre(plato.getNombre());
        d3.setPrecioDecimal(new BigDecimal("10.00"));
        d3.setCantidad(1);
        d3.setMonedaBase("USD");
        d3.setAplicaIva(true);
        d3.setPrecioIncluyeIva(false);
        d3.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido3 = pedidosDao.registrarPedidoCompleto(p3, List.of(d3));
        // Marcar cerrada_en en la BD
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET cerrada_en = NOW() WHERE id = ?")) {
            ps.setInt(1, idPedido3);
            assertEquals(1, ps.executeUpdate());
        }

        // Mesa 4: Pedido FINALIZADO (pagado)
        Pedidos p4 = new Pedidos();
        p4.setId_sala(idSala);
        p4.setNum_mesa(4);
        p4.setUsuario(USUARIO_PRUEBA);
        p4.setTasaCambio(tasa);
        p4.setIvaPorcentaje(new BigDecimal("16.00"));
        p4.setSubtotal(new BigDecimal("10.00"));
        p4.setIvaMonto(new BigDecimal("1.60"));
        p4.setTotalDecimal(new BigDecimal("11.60"));
        DetallePedido d4 = new DetallePedido();
        d4.setIdPlato(plato.getId());
        d4.setNombre(plato.getNombre());
        d4.setPrecioDecimal(new BigDecimal("10.00"));
        d4.setCantidad(1);
        d4.setMonedaBase("USD");
        d4.setAplicaIva(true);
        d4.setPrecioIncluyeIva(false);
        d4.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido4 = pedidosDao.registrarPedidoCompleto(p4, List.of(d4));
        // Marcar finalizado en la BD
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET estado = 'FINALIZADO', cerrada_en = NOW() WHERE id = ?")) {
            ps.setInt(1, idPedido4);
            assertEquals(1, ps.executeUpdate());
        }

        // 2. Modificar producto en catálogo: precio sube a $15.00 USD
        plato.setPrecioUsd(new BigDecimal("15.00"));
        plato.setPrecioVes(new BigDecimal("600.00"));
        plato.setPrecioDecimal(new BigDecimal("15.00"));

        ResultadoActualizacionPrecios res = preciosPedidosDao.modificarProductoYRecalcularPendientes(plato, USUARIO_PRUEBA);

        // Validar resultado
        assertEquals(2, res.pedidosRecalculados());
        assertTrue(res.pedidosAfectados().contains(idPedido1));
        assertTrue(res.pedidosAfectados().contains(idPedido2));
        assertFalse("Mesa cerrada no debe estar en pedidos afectados", res.pedidosAfectados().contains(idPedido3));
        assertFalse("Pedido finalizado no debe estar en pedidos afectados", res.pedidosAfectados().contains(idPedido4));

        // 3. Verificar estado de los 4 pedidos
        // Mesa 1: recalculada a 2 * $15.00 = $30.00 base, $4.80 IVA -> $34.80 USD
        Pedidos ped1Post = pedidosDao.verPedido(idPedido1);
        assertEquals(new BigDecimal("34.80"), ped1Post.getTotalDecimal());
        assertEquals(new BigDecimal("1392.00"), ped1Post.getTotalBs());
        assertEquals(2, ped1Post.getVersionPrecio());

        // Mesa 2: recalculada a 1 * $15.00 = $15.00 base, $2.40 IVA -> $17.40 USD
        Pedidos ped2Post = pedidosDao.verPedido(idPedido2);
        assertEquals(new BigDecimal("17.40"), ped2Post.getTotalDecimal());
        assertEquals(new BigDecimal("696.00"), ped2Post.getTotalBs());
        assertEquals(2, ped2Post.getVersionPrecio());

        // Mesa 3 (cerrada): conservó $11.60 exacto
        Pedidos ped3Post = pedidosDao.verPedido(idPedido3);
        assertEquals(new BigDecimal("11.60"), ped3Post.getTotalDecimal());
        assertEquals(new BigDecimal("464.00"), ped3Post.getTotalBs());
        assertEquals(1, ped3Post.getVersionPrecio());

        // Mesa 4 (finalizado): conservó $11.60 exacto
        Pedidos ped4Post = pedidosDao.verPedido(idPedido4);
        assertEquals(new BigDecimal("11.60"), ped4Post.getTotalDecimal());
        assertEquals(new BigDecimal("464.00"), ped4Post.getTotalBs());

        // 4. Verificar auditoría
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CAMBIO_CATALOGO'")) {
            ps.setString(1, USUARIO_PRUEBA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1)); // 2 pedidos recalculados y auditados
            }
        }
    }

    @Test
    public void lineasNoEnlazadasMismoNombreNoSePropaganYSeReportanOmitidas() throws Exception {
        int idSala = crearSalaPrueba(2);
        BigDecimal tasa = new BigDecimal("40.0000");

        Platos plato = crearPlato("IT-PROP-PASTA", new BigDecimal("12.00"), new BigDecimal("480.00"), "USD", true, false);

        // Crear pedido con línea no enlazada (id_plato = NULL) pero mismo nombre
        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(new BigDecimal("16.00"));
        p.setSubtotal(new BigDecimal("12.00"));
        p.setIvaMonto(new BigDecimal("1.92"));
        p.setTotalDecimal(new BigDecimal("13.92"));

        DetallePedido dHistorico = new DetallePedido();
        dHistorico.setIdPlato(null); // No ligado a catálogo
        dHistorico.setNombre(plato.getNombre());
        dHistorico.setPrecioDecimal(new BigDecimal("12.00"));
        dHistorico.setCantidad(1);
        dHistorico.setMonedaBase("USD");
        dHistorico.setAplicaIva(true);
        dHistorico.setPrecioIncluyeIva(false);
        dHistorico.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(dHistorico));

        // Modificar precio del plato en catálogo
        plato.setPrecioUsd(new BigDecimal("20.00"));
        plato.setPrecioVes(new BigDecimal("800.00"));
        plato.setPrecioDecimal(new BigDecimal("20.00"));

        ResultadoActualizacionPrecios res = preciosPedidosDao.modificarProductoYRecalcularPendientes(plato, USUARIO_PRUEBA);

        // La línea no enlazada no se modificó
        assertEquals(0, res.pedidosRecalculados());
        assertEquals(1, res.lineasNoEnlazadasOmitidas());

        // Pedido conserva su importe histórico intacto
        Pedidos pedPost = pedidosDao.verPedido(idPedido);
        assertEquals(new BigDecimal("13.92"), pedPost.getTotalDecimal());
    }

    @Test
    public void actualizacionDirectaRechazaMesaCerradaSinModificarCotizacion() throws Exception {
        int idSala = crearSalaPrueba(1);
        BigDecimal tasa = new BigDecimal("40.0000");

        Platos plato = crearPlato("IT-PROP-SOPA", new BigDecimal("8.00"), new BigDecimal("320.00"), "USD", true, false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(new BigDecimal("16.00"));
        p.setSubtotal(new BigDecimal("8.00"));
        p.setIvaMonto(new BigDecimal("1.28"));
        p.setTotalDecimal(new BigDecimal("9.28"));

        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("8.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(true);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(new BigDecimal("16.00"));
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        // Cerrar mesa
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET cerrada_en = NOW() WHERE id = ?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        // Intentar actualizar el pedido cerrado a través del DAO
        p.setSubtotal(new BigDecimal("16.00"));
        p.setTotalDecimal(new BigDecimal("18.56"));
        d.setCantidad(2);
        
        org.junit.Assert.assertThrows(Modelo.DataAccessException.class, () ->
                pedidosDao.actualizarPedidoCompleto(idPedido, p, List.of(d))
        );

        // Verificar que el total sigue siendo el original
        Pedidos pedCerrado = pedidosDao.verPedido(idPedido);
        assertEquals(new BigDecimal("9.28"), pedCerrado.getTotalDecimal());
    }
}
