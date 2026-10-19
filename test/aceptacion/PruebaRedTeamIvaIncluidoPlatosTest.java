package aceptacion;

import Modelo.*;
import Servicio.CalculoMonetarioPedido;
import Servicio.GeneradorPdfPedido;
import Servicio.PlatosServicio;
import infraestructura.ProveedorConexionJdbc;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Prueba Red Team: Creación de platos con diversas categorías, combinatoria
 * de checkboxes (Aplica IVA vs Precio Incluye IVA, USD vs VES), y auditoría
 * de persistencia en tabla platos, discriminación en pedidos y pantalla/PDF.
 */
public class PruebaRedTeamIvaIncluidoPlatosTest {

    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
    private PlatosDao platosDao;
    private PlatosServicio platosServicio;
    private PedidosDao pedidosDao;
    private final List<Integer> platosCreados = new ArrayList<>();
    private final List<Integer> categoriasCreadas = new ArrayList<>();
    private final List<Integer> pedidosCreados = new ArrayList<>();

    private static final BigDecimal TASA = new BigDecimal("44.8000");
    private static final BigDecimal IVA_16 = new BigDecimal("16.00");

    @Before
    public void setUp() {
        platosDao = new PlatosDao(proveedor);
        platosServicio = new PlatosServicio(platosDao);
        pedidosDao = new PedidosDao(proveedor);
    }

    @After
    public void tearDown() {
        try (Connection con = proveedor.getConnection()) {
            for (Integer idPed : pedidosCreados) {
                try (PreparedStatement ps = con.prepareStatement("""
                        DELETE md FROM comandas_marchas_detalle md
                        JOIN comandas_marchas m ON md.id_marcha=m.id WHERE m.id_pedido=?
                        """)) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM comandas_marchas WHERE id_pedido=?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM detalle_pedidos WHERE id_pedido = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                    ps.setInt(1, idPed);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (Integer idPla : platosCreados) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM plato_categorias WHERE id_plato = ?")) {
                    ps.setInt(1, idPla);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM platos WHERE id = ?")) {
                    ps.setInt(1, idPla);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (Integer idCat : categoriasCreadas) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM categorias WHERE id = ?")) {
                    ps.setInt(1, idCat);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private int crearCategoriaTest(String nombre, String color) throws Exception {
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO categorias (nombre, color) VALUES (?, ?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, color);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    categoriasCreadas.add(id);
                    return id;
                }
            }
        }
        throw new RuntimeException("No se pudo crear categoría");
    }

    @Test
    public void testAuditoriaPlatos_IvaIncluidoVsExcluido_PersistenciaYCalculo() throws Exception {
        // 1. Crear 3 Categorías
        int catEntradas = crearCategoriaTest("Test Entradas RT", "#FFAA00");
        int catFuertes = crearCategoriaTest("Test Fuertes RT", "#00AAFF");
        int catBebidas = crearCategoriaTest("Test Bebidas RT", "#00FFAA");

        // 2. Crear Platos con combinatoria de Checkboxes
        // Plato 1: Fuertes - USD - $10.00 - Aplica IVA: SI - Incluye IVA: NO (IVA Excluido)
        // Se espera que al venderse se le sume el IVA (10.00 + 1.60 = 11.60)
        Platos p1 = new Platos();
        p1.setNombre("Hamburguesa RT (USD + IVA)");
        p1.setPrecioDecimal(new BigDecimal("10.00"));
        p1.setFecha("2026-10-17");
        p1.setMonedaBase("USD");
        p1.setAplicaIva(true);
        p1.setPrecioIncluyeIva(false);
        p1.setIdCategoria(catFuertes);
        assertTrue("Debe registrar plato 1", platosServicio.registrar(p1, TASA));
        Platos p1Db = platosDao.buscarActivoPorNombre(p1.getNombre()).orElseThrow();
        platosCreados.add(p1Db.getId());

        // Plato 2: Fuertes - USD - $11.60 - Aplica IVA: SI - Incluye IVA: SI (IVA Incluido)
        // El precio al público final es $11.60. La base debe ser $10.00 y el IVA $1.60. ¡NO debe dar $13.46!
        Platos p2 = new Platos();
        p2.setNombre("Pizza Gourmet RT (USD IVA Inc)");
        p2.setPrecioDecimal(new BigDecimal("11.60"));
        p2.setFecha("2026-10-17");
        p2.setMonedaBase("USD");
        p2.setAplicaIva(true);
        p2.setPrecioIncluyeIva(true);
        p2.setIdCategoria(catFuertes);
        assertTrue("Debe registrar plato 2", platosServicio.registrar(p2, TASA));
        Platos p2Db = platosDao.buscarActivoPorNombre(p2.getNombre()).orElseThrow();
        platosCreados.add(p2Db.getId());

        // Plato 3: Bebidas - USD - $5.00 - Exento (Aplica IVA: NO, Incluye IVA: NO)
        Platos p3 = new Platos();
        p3.setNombre("Jugo Natural RT (Exento)");
        p3.setPrecioDecimal(new BigDecimal("5.00"));
        p3.setFecha("2026-10-17");
        p3.setMonedaBase("USD");
        p3.setAplicaIva(false);
        p3.setPrecioIncluyeIva(false);
        p3.setIdCategoria(catBebidas);
        assertTrue("Debe registrar plato 3", platosServicio.registrar(p3, TASA));
        Platos p3Db = platosDao.buscarActivoPorNombre(p3.getNombre()).orElseThrow();
        platosCreados.add(p3Db.getId());

        // Plato 4: Entradas - VES - Bs. 519.68 - Aplica IVA: SI - Incluye IVA: SI (VES IVA Incluido)
        // A tasa 44.80: Bs. 519.68 equivale a $11.60 total. Base en Bs = Bs. 448.00 ($10.00). IVA Bs = Bs. 71.68 ($1.60).
        Platos p4 = new Platos();
        p4.setNombre("Tequeños RT (VES IVA Inc)");
        p4.setPrecioDecimal(new BigDecimal("519.68"));
        p4.setFecha("2026-10-17");
        p4.setMonedaBase("VES");
        p4.setAplicaIva(true);
        p4.setPrecioIncluyeIva(true);
        p4.setIdCategoria(catEntradas);
        assertTrue("Debe registrar plato 4", platosServicio.registrar(p4, TASA));
        Platos p4Db = platosDao.buscarActivoPorNombre(p4.getNombre()).orElseThrow();
        platosCreados.add(p4Db.getId());

        // =====================================================================
        // AUDITORÍA 1: ¿CÓMO SE GUARDÓ EN LA TABLA PLATOS DE LA BD MYSQL?
        // =====================================================================
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, nombre, precio, aplica_iva, precio_incluye_iva, moneda_base, precio_usd, precio_ves FROM platos WHERE id = ?")) {

            // Verificar Plato 1 (Excluido)
            ps.setInt(1, p1Db.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("P1 precio legacy en BD debe ser 10.00", new BigDecimal("10.00"), rs.getBigDecimal("precio"));
                assertEquals("P1 precio_usd en BD debe ser 10.00", new BigDecimal("10.00"), rs.getBigDecimal("precio_usd"));
                assertEquals("P1 aplica_iva en BD debe ser 1", 1, rs.getInt("aplica_iva"));
                assertEquals("P1 precio_incluye_iva en BD debe ser 0", 0, rs.getInt("precio_incluye_iva"));
            }

            // Verificar Plato 2 (Incluido)
            ps.setInt(1, p2Db.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("P2 precio legacy en BD debe ser 11.60", new BigDecimal("11.60"), rs.getBigDecimal("precio"));
                assertEquals("P2 precio_usd en BD debe ser 11.60", new BigDecimal("11.60"), rs.getBigDecimal("precio_usd"));
                assertEquals("P2 aplica_iva en BD debe ser 1", 1, rs.getInt("aplica_iva"));
                assertEquals("P2 precio_incluye_iva en BD debe ser 1", 1, rs.getInt("precio_incluye_iva"));
            }

            // Verificar Plato 4 (VES Incluido)
            ps.setInt(1, p4Db.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("P4 precio_ves en BD debe ser 519.68", new BigDecimal("519.68"), rs.getBigDecimal("precio_ves"));
                assertEquals("P4 precio_usd en BD debe ser 11.60", new BigDecimal("11.60"), rs.getBigDecimal("precio_usd"));
                assertEquals("P4 precio_incluye_iva en BD debe ser 1", 1, rs.getInt("precio_incluye_iva"));
            }
        }

        // =====================================================================
        // AUDITORÍA 2: CÁLCULO MONETARIO DE CADA LÍNEA INDIVIDUAL
        // =====================================================================
        // Línea 1 (P1: $10.00 + IVA)
        CalculoMonetarioPedido.CalculoLinea lin1 = CalculoMonetarioPedido.calcularLinea(
                p1Db.getPrecioUsd(), 1, "USD", true, false, IVA_16, TASA);
        assertEquals("Lin1 Base Fuente debe ser 10.00", new BigDecimal("10.00"), lin1.baseImponibleFuente());
        assertEquals("Lin1 IVA Fuente debe ser 1.60", new BigDecimal("1.60"), lin1.ivaFuente());
        assertEquals("Lin1 Total Fuente debe ser 11.60", new BigDecimal("11.60"), lin1.totalFuente());

        // Línea 2 (P2: $11.60 IVA Incluido)
        CalculoMonetarioPedido.CalculoLinea lin2 = CalculoMonetarioPedido.calcularLinea(
                p2Db.getPrecioUsd(), 1, "USD", true, true, IVA_16, TASA);
        assertEquals("Lin2 Base Fuente debe ser 10.00", new BigDecimal("10.00"), lin2.baseImponibleFuente());
        assertEquals("Lin2 IVA Fuente debe ser 1.60", new BigDecimal("1.60"), lin2.ivaFuente());
        assertEquals("Lin2 Total Fuente debe ser 11.60 (NO 13.46)", new BigDecimal("11.60"), lin2.totalFuente());

        // Línea 4 (P4: Bs. 519.68 IVA Incluido)
        CalculoMonetarioPedido.CalculoLinea lin4 = CalculoMonetarioPedido.calcularLinea(
                p4Db.getPrecioVes(), 1, "VES", true, true, IVA_16, TASA);
        assertEquals("Lin4 Base VES debe ser 448.00", new BigDecimal("448.00"), lin4.baseImponibleVes());
        assertEquals("Lin4 IVA VES debe ser 71.68", new BigDecimal("71.68"), lin4.ivaVes());
        assertEquals("Lin4 Total VES debe ser 519.68", new BigDecimal("519.68"), lin4.totalVes());

        // =====================================================================
        // AUDITORÍA 3: PEDIDO CONJUNTO (CON TODAS LAS COMBINACIONES)
        // =====================================================================
        List<DetallePedido> detallesPedido = new ArrayList<>();

        DetallePedido d1 = new DetallePedido();
        d1.setIdPlato(p1Db.getId());
        d1.setNombre(p1Db.getNombre());
        d1.setCantidad(1);
        d1.setPrecioDecimal(p1Db.getPrecioUsd());
        d1.setMonedaBase("USD");
        d1.setAplicaIva(true);
        d1.setPrecioIncluyeIva(false);
        d1.setIvaPorcentaje(IVA_16);
        detallesPedido.add(d1);

        DetallePedido d2 = new DetallePedido();
        d2.setIdPlato(p2Db.getId());
        d2.setNombre(p2Db.getNombre());
        d2.setCantidad(1);
        d2.setPrecioDecimal(p2Db.getPrecioUsd());
        d2.setMonedaBase("USD");
        d2.setAplicaIva(true);
        d2.setPrecioIncluyeIva(true);
        d2.setIvaPorcentaje(IVA_16);
        detallesPedido.add(d2);

        DetallePedido d3 = new DetallePedido();
        d3.setIdPlato(p3Db.getId());
        d3.setNombre(p3Db.getNombre());
        d3.setCantidad(1);
        d3.setPrecioDecimal(p3Db.getPrecioUsd());
        d3.setMonedaBase("USD");
        d3.setAplicaIva(false);
        d3.setPrecioIncluyeIva(false);
        d3.setIvaPorcentaje(BigDecimal.ZERO);
        detallesPedido.add(d3);

        DetallePedido d4 = new DetallePedido();
        d4.setIdPlato(p4Db.getId());
        d4.setNombre(p4Db.getNombre());
        d4.setCantidad(1);
        d4.setPrecioDecimal(p4Db.getPrecioVes());
        d4.setMonedaBase("VES");
        d4.setAplicaIva(true);
        d4.setPrecioIncluyeIva(true);
        d4.setIvaPorcentaje(IVA_16);
        detallesPedido.add(d4);

        CalculoMonetarioPedido.CalculoPedido calcPedido = CalculoMonetarioPedido.calcularPedido(detallesPedido, TASA);

        // Subtotal = Base d1 (10) + Base d2 (10) + Exento d3 (5) + Base d4 convertida (10) = 35.00 USD
        assertEquals("Subtotal USD debe ser 35.00", new BigDecimal("35.00"), calcPedido.subtotalUsd());
        // Base Imponible USD = 10 (d1) + 10 (d2) + 10 (d4) = 30.00 USD
        assertEquals("Base Imponible USD debe ser 30.00", new BigDecimal("30.00"), calcPedido.baseImponibleUsd());
        // Exento USD = 5.00 (d3)
        assertEquals("Exento USD debe ser 5.00", new BigDecimal("5.00"), calcPedido.exentoUsd());
        // IVA USD = 1.60 (d1) + 1.60 (d2) + 1.60 (d4) = 4.80 USD
        assertEquals("IVA USD debe ser 4.80", new BigDecimal("4.80"), calcPedido.ivaUsd());
        // Total USD = 35.00 + 4.80 = 39.80 USD
        assertEquals("Total USD debe ser 39.80", new BigDecimal("39.80"), calcPedido.totalUsd());

        // En Bolívares (Tasa 44.80):
        // Total VES = 39.80 * 44.80 = 1783.04 Bs.
        assertEquals("Total VES debe ser 1783.04", new BigDecimal("1783.04"), calcPedido.totalVes());

        // =====================================================================
        // AUDITORÍA 4: REGISTRO DEL PEDIDO EN BD Y COMPROBACIÓN FÍSICA
        // =====================================================================
        Pedidos ped = new Pedidos();
        ped.setId_sala(1);
        ped.setNum_mesa(1);
        ped.setFecha("2026-10-17 14:00:00");
        ped.setSala("Salón Principal");
        ped.setUsuario("AuditorRT");
        ped.setTasaCambio(TASA);
        ped.setIvaPorcentaje(IVA_16);
        ped.setSubtotal(calcPedido.subtotalUsd());
        ped.setSubtotalBs(calcPedido.subtotalVes());
        ped.setIvaMonto(calcPedido.ivaUsd());
        ped.setIvaBs(calcPedido.ivaVes());
        ped.setTotalDecimal(calcPedido.totalUsd());
        ped.setTotalBs(calcPedido.totalVes());

        int idPedido = pedidosDao.registrarPedidoCompleto(ped, detallesPedido);
        assertTrue("Pedido debe insertarse en BD", idPedido > 0);
        pedidosCreados.add(idPedido);

        // Verificar datos físicos en tabla pedidos y detalle_pedidos
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT total, subtotal, iva_monto, total_bs, subtotal_bs, iva_bs FROM pedidos WHERE id = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("BD Pedido Total USD debe ser 39.80", new BigDecimal("39.80"), rs.getBigDecimal("total"));
                assertEquals("BD Pedido Subtotal USD debe ser 35.00", new BigDecimal("35.00"), rs.getBigDecimal("subtotal"));
                assertEquals("BD Pedido IVA USD debe ser 4.80", new BigDecimal("4.80"), rs.getBigDecimal("iva_monto"));
                assertEquals("BD Pedido Total Bs debe ser 1783.04", new BigDecimal("1783.04"), rs.getBigDecimal("total_bs"));
            }
        }

        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, nombre, precio, precio_incluye_iva, aplica_iva FROM detalle_pedidos WHERE id_pedido = ? AND id_plato = ?")) {
            ps.setInt(1, idPedido);
            ps.setInt(2, p2Db.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("Detalle de plato 2 debe existir", rs.next());
                assertEquals("Detalle P2 precio debe ser 11.60", new BigDecimal("11.60"), rs.getBigDecimal("precio"));
                assertEquals("Detalle P2 precio_incluye_iva debe ser 1", 1, rs.getInt("precio_incluye_iva"));
            }
        }
    }
}
