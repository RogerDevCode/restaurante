package Servicio;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CierreCajaTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void calcularCierreDesdeMemoriaTotalizaVentasYMetodosCorrectamente() {
        Config config = new Config(1, "J-12345678-9", "La Delicia Gourmet", "+58 412 1234567", "Caracas", "Gracias por su compra");
        config.setTasaDolar(new BigDecimal("40.0000"));

        List<Pedidos> pedidos = new ArrayList<>();

        // Pedido 1: Finalizado, 50 USD, EFECTIVO
        Pedidos p1 = new Pedidos();
        p1.setId(1);
        p1.setEstado("FINALIZADO");
        p1.setTotalDecimal(new BigDecimal("50.00"));
        p1.setSubtotal(new BigDecimal("43.10"));
        p1.setIvaMonto(new BigDecimal("6.90"));
        p1.setTotalBs(new BigDecimal("2000.00"));
        p1.setSubtotalBs(new BigDecimal("1724.00"));
        p1.setIvaBs(new BigDecimal("276.00"));
        p1.setMetodoPago("EFECTIVO");
        p1.setSala("Terraza");
        p1.setUsuario("Carlos");
        pedidos.add(p1);

        // Pedido 2: Finalizado, 30 USD, PAGO_MOVIL
        Pedidos p2 = new Pedidos();
        p2.setId(2);
        p2.setEstado("FINALIZADO");
        p2.setTotalDecimal(new BigDecimal("30.00"));
        p2.setSubtotal(new BigDecimal("25.86"));
        p2.setIvaMonto(new BigDecimal("4.14"));
        p2.setTotalBs(new BigDecimal("1200.00"));
        p2.setSubtotalBs(new BigDecimal("1034.40"));
        p2.setIvaBs(new BigDecimal("165.60"));
        p2.setMetodoPago("PAGO_MOVIL");
        p2.setSala("Principal");
        p2.setUsuario("Maria");
        pedidos.add(p2);

        // Pedido 3: Pendiente, 20 USD (no debe sumarse a ventas cobradas pero sí contarse en pendientes)
        Pedidos p3 = new Pedidos();
        p3.setId(3);
        p3.setEstado("PENDIENTE");
        p3.setTotalDecimal(new BigDecimal("20.00"));
        pedidos.add(p3);

        List<DetallePedido> detalles = new ArrayList<>();
        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Pabellón Criollo");
        d1.setCantidad(2);
        d1.setPrecioDecimal(new BigDecimal("25.00"));
        detalles.add(d1);

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Jugo Natural");
        d2.setCantidad(3);
        d2.setPrecioDecimal(new BigDecimal("10.00"));
        detalles.add(d2);

        CierreCaja cierre = Modelo.CierreCajaDao.calcularDesdeMemoria(
                "2026-10-07",
                CierreCaja.TipoCierre.PARCIAL,
                "Admin",
                config,
                pedidos,
                detalles
        );

        assertEquals("2026-10-07", cierre.getFecha());
        assertEquals(CierreCaja.TipoCierre.PARCIAL, cierre.getTipo());
        assertEquals("Admin", cierre.getUsuarioEmisor());
        assertEquals(2, cierre.getPedidosFinalizados());
        assertEquals(1, cierre.getPedidosPendientes());
        assertEquals(new BigDecimal("80.00"), cierre.getTotalVentasUsd());
        assertEquals(new BigDecimal("3200.00"), cierre.getTotalVentasBs());
        assertEquals(new BigDecimal("40.00"), cierre.getTicketPromedioUsd());
        assertEquals(new BigDecimal("1600.00"), cierre.getTicketPromedioBs());
        assertEquals(5, cierre.getTotalArticulos());

        // Desglose de métodos
        assertEquals(2, cierre.getDesgloseMetodos().size());
        assertEquals(2, cierre.getDesgloseSalas().size());
        assertEquals(2, cierre.getDesgloseUsuarios().size());
        assertEquals(2, cierre.getTopPlatos().size());
    }

    @Test
    public void generadorPdfCierreGeneraArchivo80mmParaCorteXYCorteZ() {
        GeneradorPdfCierre generador = new GeneradorPdfCierre(temporal.getRoot().toPath());
        Config config = new Config(1, "J-99999999-0", "Restaurante Prueba", "+58 212 9999999", "Las Mercedes", "Gracias");
        config.setTasaDolar(new BigDecimal("42.0000"));

        // Prueba Corte X
        CierreCaja cierreX = new CierreCaja();
        cierreX.setTipo(CierreCaja.TipoCierre.PARCIAL);
        cierreX.setFecha("2026-10-07");
        cierreX.setFechaHoraEmision("2026-10-07 14:30:00");
        cierreX.setUsuarioEmisor("Cajero 1");
        cierreX.setTotalVentasUsd(new BigDecimal("150.00"));
        cierreX.setTotalVentasBs(new BigDecimal("6300.00"));
        cierreX.setSubtotalUsd(new BigDecimal("129.31"));
        cierreX.setSubtotalBs(new BigDecimal("5431.02"));
        cierreX.setIvaUsd(new BigDecimal("20.69"));
        cierreX.setIvaBs(new BigDecimal("868.98"));
        cierreX.setPedidosFinalizados(5);
        cierreX.setPedidosPendientes(1);
        cierreX.setTotalArticulos(12);
        cierreX.setTicketPromedioUsd(new BigDecimal("30.00"));
        cierreX.setTicketPromedioBs(new BigDecimal("1260.00"));

        Path archivoX = generador.generar(cierreX, config);
        assertNotNull(archivoX);
        assertTrue(archivoX.toFile().isFile());
        assertTrue(archivoX.getFileName().toString().startsWith("cierre-X_"));
        assertTrue(archivoX.toFile().length() > 500);

        // Prueba Corte Z
        CierreCaja cierreZ = new CierreCaja();
        cierreZ.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierreZ.setFecha("2026-10-07");
        cierreZ.setFechaHoraEmision("2026-10-07 23:00:00");
        cierreZ.setUsuarioEmisor("Gerente");
        cierreZ.setTotalVentasUsd(new BigDecimal("350.00"));
        cierreZ.setTotalVentasBs(new BigDecimal("14700.00"));
        cierreZ.setSubtotalUsd(new BigDecimal("301.72"));
        cierreZ.setSubtotalBs(new BigDecimal("12672.24"));
        cierreZ.setIvaUsd(new BigDecimal("48.28"));
        cierreZ.setIvaBs(new BigDecimal("2027.76"));
        cierreZ.setPedidosFinalizados(15);
        cierreZ.setPedidosPendientes(0);
        cierreZ.setTotalArticulos(40);
        cierreZ.setTicketPromedioUsd(new BigDecimal("23.33"));
        cierreZ.setTicketPromedioBs(new BigDecimal("980.00"));

        Path archivoZ = generador.generar(cierreZ, config);
        assertNotNull(archivoZ);
        assertTrue(archivoZ.toFile().isFile());
        assertTrue(archivoZ.getFileName().toString().startsWith("cierre-Z_"));
        assertTrue(archivoZ.toFile().length() > 500);
    }

    @Test
    public void servicioCierreCajaDiferenciaImpresionDirectaDePrevisualizacion() {
        AtomicReference<Path> impreso = new AtomicReference<>();
        AtomicReference<Path> previsualizado = new AtomicReference<>();

        Config config = new Config(1, "J-0001", "Restaurante", "123", "Caracas", "Fin");
        GeneradorPdfCierre generador = new GeneradorPdfCierre(temporal.getRoot().toPath());

        AtomicBoolean guardadoLlamado = new AtomicBoolean(false);
        Modelo.CierreCajaDao daoFalso = new Modelo.CierreCajaDao() {
            @Override
            public CierreCaja consultarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, Config cfg) {
                CierreCaja c = new CierreCaja();
                c.setTipo(tipo);
                c.setFecha(fecha != null ? fecha : "2026-10-07");
                c.setFechaHoraEmision("2026-10-07 18:00:00");
                c.setUsuarioEmisor(usuarioEmisor);
                c.setTotalVentasUsd(new BigDecimal("100.00"));
                c.setTotalVentasBs(new BigDecimal("4000.00"));
                return c;
            }

            @Override
            public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
                guardadoLlamado.set(true);
                return true;
            }
        };

        CierreCajaServicio servicio = new CierreCajaServicio(
                daoFalso,
                () -> config,
                generador,
                impreso::set,
                previsualizado::set
        );

        // 1. Imprimir Cierre Parcial -> debe guardar en BD
        guardadoLlamado.set(false);
        Path resImp = servicio.imprimirCierre("2026-10-07", CierreCaja.TipoCierre.PARCIAL, "Mesonero 1");
        assertNotNull(impreso.get());
        assertEquals(resImp, impreso.get());
        assertNull(previsualizado.get());
        assertTrue("La impresión directa debe persistir el cierre en la base de datos", guardadoLlamado.get());

        // 2. Previsualizar Cierre Total -> NO debe guardar en BD
        impreso.set(null);
        guardadoLlamado.set(false);
        Path resPrev = servicio.previsualizarCierre("2026-10-07", CierreCaja.TipoCierre.TOTAL, "Gerente");
        assertNull(impreso.get());
        assertNotNull(previsualizado.get());
        assertEquals(resPrev, previsualizado.get());
        assertFalse("La previsualización NO debe insertar un cierre definitivo en la base de datos", guardadoLlamado.get());
    }

    @Test
    public void imprimirCierreFallaSiNoSePuedePersistirEnBaseDeDatos() {
        Config config = new Config(1, "J-000", "Restaurant", "123", "Calle", "Gracias");
        GeneradorPdfCierre generador = new GeneradorPdfCierre(temporal.getRoot().toPath());

        Modelo.CierreCajaDao daoConFalloGuardado = new Modelo.CierreCajaDao() {
            @Override
            public CierreCaja consultarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, Config cfg) {
                return new CierreCaja();
            }

            @Override
            public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
                return false; // Simula fallo en persistencia
            }
        };

        CierreCajaServicio servicio = new CierreCajaServicio(
                daoConFalloGuardado,
                () -> config,
                generador,
                p -> {},
                p -> {}
        );

        assertThrows(Modelo.DataAccessException.class,
                () -> servicio.imprimirCierre("2026-10-07", CierreCaja.TipoCierre.TOTAL, "Admin"));

        try (var archivos = Files.list(temporal.getRoot().toPath())) {
            assertEquals("El PDF debe eliminarse si falla el INSERT del cierre", 0, archivos.count());
        } catch (IOException ex) {
            throw new AssertionError("No se pudo comprobar la limpieza del PDF", ex);
        }
    }

    @Test
    public void falloSqlLanzadoTambienLimpiaElPdfGenerado() throws IOException {
        Config config = new Config(1, "J-000", "Restaurant", "123", "Calle", "Gracias");
        Modelo.CierreCajaDao daoConExcepcion = new Modelo.CierreCajaDao() {
            @Override
            public CierreCaja consultarCierre(String fecha, CierreCaja.TipoCierre tipo,
                    String usuarioEmisor, Config cfg) {
                return new CierreCaja();
            }

            @Override
            public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
                throw new Modelo.DataAccessException("Fallo SQL inducido");
            }
        };
        CierreCajaServicio servicio = new CierreCajaServicio(
                daoConExcepcion, () -> config,
                new GeneradorPdfCierre(temporal.getRoot().toPath()), p -> { }, p -> { });

        assertThrows(Modelo.DataAccessException.class,
                () -> servicio.imprimirCierre("2026-10-07", CierreCaja.TipoCierre.TOTAL, "Admin"));
        try (var archivos = Files.list(temporal.getRoot().toPath())) {
            assertEquals("El error SQL no debe dejar el PDF sin registro", 0, archivos.count());
        }
    }

    @Test
    public void pdfDeCierresDelMismoSegundoUsaRutasDistintas() {
        GeneradorPdfCierre generador = new GeneradorPdfCierre(temporal.getRoot().toPath());
        Config config = new Config(1, "J-000", "Restaurant", "123", "Calle", "Gracias");
        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierre.setFecha("2026-10-07");
        cierre.setFechaHoraEmision("2026-10-07 12:00:00");
        cierre.setUsuarioEmisor("Admin");

        Path primero = generador.generar(cierre, config);
        Path segundo = generador.generar(cierre, config);

        assertFalse("Una emisión no debe sobrescribir el PDF de otra", primero.equals(segundo));
        assertTrue(Files.exists(primero));
        assertTrue(Files.exists(segundo));
    }
}
