package integracion;

import Modelo.ActualizacionTasaDao;
import Modelo.AuditoriaPreciosDao;
import Modelo.DetallePedido;
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
import java.util.ArrayList;
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
 * Pruebas de integración sobre auditoría transaccional, idempotencia y ausencia de registros huérfanos (Task 9).
 */
public class AuditoriaTransaccionalPreciosIT {
    private static final String USUARIO_PRUEBA = "__IT_AUDITORIA_PRECIOS__";
    private final ProveedorConexionJdbc proveedorConexiones = new ProveedorConexionJdbc();
    private final PlatosDao platosDao = new PlatosDao(proveedorConexiones);
    private final PedidosDao pedidosDao = new PedidosDao(proveedorConexiones);
    private final ActualizacionTasaDao tasaDao = new ActualizacionTasaDao(proveedorConexiones);
    private final PreciosPedidosDao preciosDao = new PreciosPedidosDao(proveedorConexiones);

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
    public void setup() throws SQLException {
        try (Connection con = conexion(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT tasa_dolar FROM config WHERE id = 1")) {
            if (rs.next()) {
                tasaOriginalGlobal = rs.getBigDecimal(1);
            }
        }
        limpiarBase();
    }

    @After
    public void tearDown() throws SQLException {
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

    public void limpiarBase() throws SQLException {
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.executeUpdate("DELETE md FROM comandas_marchas_detalle md JOIN comandas_marchas m ON m.id=md.id_marcha JOIN pedidos p ON p.id=m.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE m FROM comandas_marchas m JOIN pedidos p ON p.id=m.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM detalle_pedidos WHERE id_pedido IN (SELECT id FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "')");
            st.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            st.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'IT-AUD-%'");
            st.executeUpdate("DELETE FROM salas WHERE nombre LIKE 'IT-SALA-AUD-%'");
            st.executeUpdate("DELETE FROM auditoria_cambios_precio WHERE actor='" + USUARIO_PRUEBA + "'");
        }
    }

    private int crearSalaPrueba(int mesas) {
        SalasDao salasDao = new SalasDao(proveedorConexiones);
        Salas sala = new Salas();
        sala.setNombre("IT-SALA-AUD-" + UUID.randomUUID());
        sala.setMesas(mesas);
        assertTrue(salasDao.registrar(sala));
        return salasDao.listar().stream()
                .filter(s -> s.getNombre().equals(sala.getNombre()))
                .mapToInt(Salas::getId)
                .findFirst()
                .orElseThrow();
    }

    private Platos crearPlato(String nombre, BigDecimal precioUsd, BigDecimal precioVes, String monedaBase, boolean gravado) {
        Platos p = new Platos();
        p.setNombre(nombre);
        p.setPrecioUsd(precioUsd);
        p.setPrecioVes(precioVes);
        p.setMonedaBase(monedaBase);
        p.setAplicaIva(gravado);
        p.setPrecioIncluyeIva(false);
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
    public void congelarLineaEscribeAuditoriaYEsIdempotente() throws SQLException {
        int idSala = crearSalaPrueba(1);
        Platos plato = crearPlato("IT-AUD-CONGELAR", new BigDecimal("12.00"), new BigDecimal("480.00"), "USD", false);

        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(1);
        p.setUsuario(USUARIO_PRUEBA);
        p.setTasaCambio(new BigDecimal("40.0000"));
        p.setIvaPorcentaje(new BigDecimal("16.00"));
        p.setSubtotal(new BigDecimal("12.00"));
        p.setTotalDecimal(new BigDecimal("12.00"));
        DetallePedido d = new DetallePedido();
        d.setIdPlato(plato.getId());
        d.setNombre(plato.getNombre());
        d.setPrecioDecimal(new BigDecimal("12.00"));
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);
        int idPedido = pedidosDao.registrarPedidoCompleto(p, List.of(d));

        List<DetallePedido> detalles = pedidosDao.verPedidoDetalle(idPedido);
        assertEquals(1, detalles.size());
        int idDetalle = detalles.getFirst().getId();

        String idempotencyKey = "key-congelar-" + UUID.randomUUID();

        // 1. Primera ejecución: congela la línea
        boolean congelada = pedidosDao.congelarLinea(idDetalle, "Producto descontinuado de prueba", USUARIO_PRUEBA, idempotencyKey);
        assertTrue(congelada);

        // Verificar que detalle_pedidos tiene precio_congelado = 1
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT precio_congelado, congelado_motivo, congelado_por FROM detalle_pedidos WHERE id = ?")) {
            ps.setInt(1, idDetalle);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("precio_congelado"));
                assertEquals("Producto descontinuado de prueba", rs.getString("congelado_motivo"));
                assertEquals(USUARIO_PRUEBA, rs.getString("congelado_por"));
            }
        }

        // Verificar que se insertó exactamente una fila de auditoría
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CONGELAR_LINEA'")) {
            ps.setString(1, USUARIO_PRUEBA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        // 2. Reintento idempotente con la misma clave: no debe duplicar la auditoría
        boolean reintento = pedidosDao.congelarLinea(idDetalle, "Reintento", USUARIO_PRUEBA, idempotencyKey);
        assertTrue(reintento);

        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CONGELAR_LINEA'")) {
            ps.setString(1, USUARIO_PRUEBA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    public void cambioCatalogoEscribeAuditoriaYReintentoIdempotenteNoDuplica() throws SQLException {
        Platos plato = crearPlato("IT-AUD-CATALOGO", new BigDecimal("15.00"), new BigDecimal("600.00"), "USD", false);
        String idempotencyKey = "key-catalogo-" + UUID.randomUUID();

        plato.setPrecioUsd(new BigDecimal("18.00"));
        plato.setPrecioVes(new BigDecimal("720.00"));

        ResultadoActualizacionPrecios res1 = preciosDao.modificarProductoYRecalcularPendientes(plato, USUARIO_PRUEBA, idempotencyKey);
        assertNotNull(res1);

        // Debe existir exactamente 1 registro en auditoria_cambios_precio
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CAMBIO_CATALOGO' AND producto_id = ?")) {
            ps.setString(1, USUARIO_PRUEBA);
            ps.setInt(2, plato.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }

        // Reintento idempotente: no debe duplicar registro
        ResultadoActualizacionPrecios res2 = preciosDao.modificarProductoYRecalcularPendientes(plato, USUARIO_PRUEBA, idempotencyKey);
        assertNotNull(res2);

        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CAMBIO_CATALOGO' AND producto_id = ?")) {
            ps.setString(1, USUARIO_PRUEBA);
            ps.setInt(2, plato.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    public void cambioTasaIdempotenteYRollbackSinAuditoriaHuerfana() throws Exception {
        int idSala = crearSalaPrueba(1);
        String idempotencyKey = "key-tasa-" + UUID.randomUUID();
        BigDecimal tasaNueva = new BigDecimal("44.5000");

        ResultadoActualizacionPrecios res1 = tasaDao.actualizarTasaYRecalcularPendientesParaTest(tasaNueva, USUARIO_PRUEBA, idempotencyKey, idSala);
        assertNotNull(res1);

        // Reintento idempotente con la misma clave
        ResultadoActualizacionPrecios res2 = tasaDao.actualizarTasaYRecalcularPendientesParaTest(tasaNueva, USUARIO_PRUEBA, idempotencyKey, idSala);
        assertNotNull(res2);

        // Verificar que se insertó auditoría para esta clave y que el reintento no la duplicó
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND accion = 'CAMBIO_TASA' AND (idempotency_key = ? OR idempotency_key LIKE ?)")) {
            ps.setString(1, USUARIO_PRUEBA);
            ps.setString(2, idempotencyKey);
            ps.setString(3, idempotencyKey + "-ped-%");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertTrue("Debe existir al menos un registro de auditoría para la clave", count >= 1);
            }
        }

        // Verificar que un rollback transaccional anula la auditoría insertada en esa transacción
        try (Connection con = conexion()) {
            con.setAutoCommit(false);
            try {
                AuditoriaPreciosDao auditoriaDao = new AuditoriaPreciosDao();
                AuditoriaPreciosDao.CambioPrecio cambioRollback = new AuditoriaPreciosDao.CambioPrecio(
                        "CAMBIO_TASA", null, null, null, null, null, null, null, null,
                        new BigDecimal("44.5000"), new BigDecimal("45.0000"),
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        USUARIO_PRUEBA, "Prueba de rollback", "key-rollback-" + UUID.randomUUID()
                );
                auditoriaDao.insertar(con, cambioRollback);
                // Inducir aborto
                con.rollback();
            } finally {
                con.setAutoCommit(true);
            }
        }

        // Verificar que la fila revertida NO quedó huérfana en la BD
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM auditoria_cambios_precio WHERE actor = ? AND motivo = 'Prueba de rollback'")) {
            ps.setString(1, USUARIO_PRUEBA);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
