package integracion;

import Modelo.ActualizacionTasaDao;
import Modelo.DetallePedido;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.Platos;
import Modelo.PlatosDao;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import Modelo.ErrorAplicacionException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Prueba de integración transaccional para la actualización global de tasa y
 * recálculo atómico de pedidos abiertos sin carreras (Task 6).
 */
public class ActualizacionTasaPedidosPendientesIT {
    private static final String USUARIO_PRUEBA = "__IT_TASA_GLOBAL__";
    private final ProveedorConexionJdbc proveedorConexiones = new ProveedorConexionJdbc();
    private final PlatosDao platosDao = new PlatosDao(proveedorConexiones);
    private final PedidosDao pedidosDao = new PedidosDao(proveedorConexiones);
    private final ActualizacionTasaDao tasaDao = new ActualizacionTasaDao(proveedorConexiones);

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

    private BigDecimal tasaOriginalGlobal;

    @Before
    public void limpiarEstado() throws SQLException {
        try (Connection con = conexion(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT tasa_dolar FROM config WHERE id = 1")) {
            if (rs.next()) {
                tasaOriginalGlobal = rs.getBigDecimal(1);
            }
        }
        limpiarBase();
    }

    @After
    public void limpiarDatos() throws SQLException {
        try {
            if (tasaOriginalGlobal != null) {
                try (Connection con = conexion();
                     PreparedStatement ps = con.prepareStatement("UPDATE config SET tasa_dolar = ? WHERE id = 1");
                     PreparedStatement psCs = con.prepareStatement("UPDATE configuracion_sistema SET valor = ? WHERE clave = 'tasa_cambio'")) {
                    ps.setBigDecimal(1, tasaOriginalGlobal);
                    ps.executeUpdate();
                    psCs.setString(1, tasaOriginalGlobal.stripTrailingZeros().toPlainString());
                    psCs.executeUpdate();
                }
            }
        } finally {
            limpiarBase();
        }
    }

    private void limpiarBase() throws SQLException {
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM detalle_pedidos WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'IT-TASA-%'");
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
    public void cambioDeTasaRecalculaSoloMesasAbiertasYConservaCerradasYFinalizadas() throws Exception {
        int idSala = crearSalaPrueba(4);
        int pedidosAbiertosPrevios = tasaDao.contarPedidosAbiertosParaTest(idSala);
        BigDecimal tasaInicial = new BigDecimal("40.0000");

        // 1. Catálogo inicial:
        // Plato USD: $10.00 USD (equivale a Bs. 400.00 a tasa 40.00), exento
        Platos platoUsd = crearPlato("IT-TASA-USD", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);
        // Plato VES: Bs. 200.00 VES (equivale a $5.00 USD a tasa 40.00), exento
        Platos platoVes = crearPlato("IT-TASA-VES", new BigDecimal("5.00"), new BigDecimal("200.00"), "VES", false, false);

        // Mesa 1 (abierta, base USD): 1 x Plato USD -> $10.00 USD / Bs. 400.00
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
        int idPedido1 = pedidosDao.registrarPedidoCompleto(p1, List.of(d1));

        // Mesa 2 (abierta, base VES): 1 x Plato VES -> Bs. 200.00 / $5.00 USD
        Pedidos p2 = new Pedidos();
        p2.setId_sala(idSala);
        p2.setNum_mesa(2);
        p2.setUsuario(USUARIO_PRUEBA);
        p2.setTasaCambio(tasaInicial);
        p2.setIvaPorcentaje(BigDecimal.ZERO);
        p2.setSubtotal(new BigDecimal("5.00"));
        p2.setIvaMonto(BigDecimal.ZERO);
        p2.setTotalDecimal(new BigDecimal("5.00"));
        DetallePedido d2 = new DetallePedido();
        d2.setIdPlato(platoVes.getId());
        d2.setNombre(platoVes.getNombre());
        d2.setPrecioDecimal(new BigDecimal("200.00"));
        d2.setCantidad(1);
        d2.setMonedaBase("VES");
        d2.setAplicaIva(false);
        d2.setPrecioIncluyeIva(false);
        d2.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido2 = pedidosDao.registrarPedidoCompleto(p2, List.of(d2));

        // Mesa 3 (cerrada pendiente de pago, base USD): cerrada_en != NULL
        Pedidos p3 = new Pedidos();
        p3.setId_sala(idSala);
        p3.setNum_mesa(3);
        p3.setUsuario(USUARIO_PRUEBA);
        p3.setTasaCambio(tasaInicial);
        p3.setIvaPorcentaje(BigDecimal.ZERO);
        p3.setSubtotal(new BigDecimal("10.00"));
        p3.setIvaMonto(BigDecimal.ZERO);
        p3.setTotalDecimal(new BigDecimal("10.00"));
        DetallePedido d3 = new DetallePedido();
        d3.setIdPlato(platoUsd.getId());
        d3.setNombre(platoUsd.getNombre());
        d3.setPrecioDecimal(new BigDecimal("10.00"));
        d3.setCantidad(1);
        d3.setMonedaBase("USD");
        d3.setAplicaIva(false);
        d3.setPrecioIncluyeIva(false);
        d3.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido3 = pedidosDao.registrarPedidoCompleto(p3, List.of(d3));
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET cerrada_en = NOW() WHERE id = ?")) {
            ps.setInt(1, idPedido3);
            assertEquals(1, ps.executeUpdate());
        }

        // Mesa 4 (pedido finalizado): estado = 'FINALIZADO'
        Pedidos p4 = new Pedidos();
        p4.setId_sala(idSala);
        p4.setNum_mesa(4);
        p4.setUsuario(USUARIO_PRUEBA);
        p4.setTasaCambio(tasaInicial);
        p4.setIvaPorcentaje(BigDecimal.ZERO);
        p4.setSubtotal(new BigDecimal("10.00"));
        p4.setIvaMonto(BigDecimal.ZERO);
        p4.setTotalDecimal(new BigDecimal("10.00"));
        DetallePedido d4 = new DetallePedido();
        d4.setIdPlato(platoUsd.getId());
        d4.setNombre(platoUsd.getNombre());
        d4.setPrecioDecimal(new BigDecimal("10.00"));
        d4.setCantidad(1);
        d4.setMonedaBase("USD");
        d4.setAplicaIva(false);
        d4.setPrecioIncluyeIva(false);
        d4.setIvaPorcentaje(BigDecimal.ZERO);
        int idPedido4 = pedidosDao.registrarPedidoCompleto(p4, List.of(d4));
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET estado = 'FINALIZADO', cerrada_en = NOW() WHERE id = ?")) {
            ps.setInt(1, idPedido4);
            assertEquals(1, ps.executeUpdate());
        }

        // 2. Aplicar cambio de tasa global: sube de 40.0000 a 50.0000
        BigDecimal tasaNueva = new BigDecimal("50.0000");
        ResultadoActualizacionPrecios res = tasaDao.actualizarTasaYRecalcularPendientesParaTest(tasaNueva, USUARIO_PRUEBA, null, idSala);

        assertEquals(pedidosAbiertosPrevios + 2, res.pedidosRecalculados());
        assertTrue(res.pedidosAfectados().contains(idPedido1));
        assertTrue(res.pedidosAfectados().contains(idPedido2));
        assertFalse(res.pedidosAfectados().contains(idPedido3));
        assertFalse(res.pedidosAfectados().contains(idPedido4));

        // 3. Verificaciones de importes
        // Mesa 1 (abierta USD): $10.00 se conserva intacto, total_bs sube de Bs. 400.00 a Bs. 500.00
        Pedidos ped1Post = pedidosDao.verPedido(idPedido1);
        assertEquals(new BigDecimal("10.00"), ped1Post.getTotalDecimal());
        assertEquals(new BigDecimal("500.00"), ped1Post.getTotalBs());
        assertEquals(new BigDecimal("50.0000"), ped1Post.getTasaCambio());
        assertEquals(2, ped1Post.getVersionPrecio());

        // Mesa 2 (abierta VES): Bs. 200.00 se conserva intacto, total_usd de referencia baja de $5.00 a $4.00 (200 / 50)
        Pedidos ped2Post = pedidosDao.verPedido(idPedido2);
        assertEquals(new BigDecimal("200.00"), ped2Post.getTotalBs());
        assertEquals(new BigDecimal("4.00"), ped2Post.getTotalDecimal());
        assertEquals(new BigDecimal("50.0000"), ped2Post.getTasaCambio());
        assertEquals(2, ped2Post.getVersionPrecio());

        // Mesa 3 (cerrada): conservó tasa 40.0000 y total Bs. 400.00 intactos
        Pedidos ped3Post = pedidosDao.verPedido(idPedido3);
        assertEquals(new BigDecimal("10.00"), ped3Post.getTotalDecimal());
        assertEquals(new BigDecimal("400.00"), ped3Post.getTotalBs());
        assertEquals(new BigDecimal("40.0000"), ped3Post.getTasaCambio());
        assertEquals(1, ped3Post.getVersionPrecio());

        // Mesa 4 (finalizado): conservó tasa 40.0000 y total Bs. 400.00 intactos
        Pedidos ped4Post = pedidosDao.verPedido(idPedido4);
        assertEquals(new BigDecimal("10.00"), ped4Post.getTotalDecimal());
        assertEquals(new BigDecimal("400.00"), ped4Post.getTotalBs());
        assertEquals(new BigDecimal("40.0000"), ped4Post.getTasaCambio());

        // 4. Verificación de auditoría
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT pedido_id FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CAMBIO_TASA'")) {
            ps.setString(1, USUARIO_PRUEBA);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> auditados = new ArrayList<>();
                while (rs.next()) {
                    auditados.add(rs.getInt(1));
                }
                assertEquals(pedidosAbiertosPrevios + 2, auditados.size());
                assertTrue(auditados.contains(idPedido1));
                assertTrue(auditados.contains(idPedido2));
                assertFalse(auditados.contains(idPedido3));
                assertFalse(auditados.contains(idPedido4));
            }
        }
    }

    @Test
    public void testActualizarTasaFallaYRevierteSiMesaAbiertaTieneLineaSinClasificar() throws Exception {
        int idSala = crearSalaPrueba(1);
        Platos plato = crearPlato("USD-TEST-ABORT", new BigDecimal("10.00"), new BigDecimal("400.00"), "USD", false, false);

        BigDecimal tasaInicial = new BigDecimal("40.0000");
        tasaDao.actualizarTasaYRecalcularPendientesParaTest(tasaInicial, USUARIO_PRUEBA, null, idSala);

        // Crear comanda abierta
        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(tasaInicial);
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
        assertTrue(idPedido > 0);

        // Simular que una línea de esta mesa abierta quedó sin clasificar en BD
        try (Connection con = conexion();
             PreparedStatement ps = con.prepareStatement("UPDATE detalle_pedidos SET moneda_base = NULL WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }

        List<DetallePedido> detallesGuardados = pedidosDao.verPedidoDetalle(idPedido);
        int idDetalle = detallesGuardados.getFirst().getId();

        // Intentar actualizar la tasa global a 50.00
        BigDecimal tasaNueva = new BigDecimal("50.0000");
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class,
                () -> tasaDao.actualizarTasaYRecalcularPendientesParaTest(tasaNueva, USUARIO_PRUEBA, null, idSala));
        assertTrue("Debe mencionar sin clasificar", ex.getMessage().contains("sin clasificar"));
        assertTrue("Debe incluir id_pedido", ex.getMessage().contains("pedido #" + idPedido));
        assertTrue("Debe incluir id_detalle", ex.getMessage().contains("detalle #" + idDetalle));

        // Comprobar que en config la tasa NO cambió (rollback exitoso)
        try (Connection con = conexion();
             PreparedStatement ps = con.prepareStatement("SELECT tasa_dolar FROM config WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("La tasa en config debe conservar 40.0000", 0, tasaInicial.compareTo(rs.getBigDecimal(1)));
            }
        }
    }
}
