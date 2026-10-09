package Servicio;

import Modelo.CalculoFiscalRecord;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import infraestructura.ProveedorConexionJdbc;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas adversariales para el cálculo, validación y persistencia de IVA.
 */
public class AdversarialIvaTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    // -------------------------------------------------------------
    // 1. CALCULOFISCALRECORD: PRUEBAS MATEMÁTICAS Y LÍMITES
    // -------------------------------------------------------------

    @Test
    public void calculoFiscalEstandar16Porciento() {
        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(
                new BigDecimal("100.00"),
                new BigDecimal("16.00"),
                new BigDecimal("36.5000")
        );
        assertEquals(new BigDecimal("100.00"), fiscal.subtotalUsd());
        assertEquals(new BigDecimal("16.00"), fiscal.ivaPorcentaje());
        assertEquals(new BigDecimal("16.00"), fiscal.ivaUsd());
        assertEquals(new BigDecimal("116.00"), fiscal.totalUsd());
        assertEquals(new BigDecimal("3650.00"), fiscal.subtotalBs());
        assertEquals(new BigDecimal("584.00"), fiscal.ivaBs());
        assertEquals(new BigDecimal("4234.00"), fiscal.totalBs());
    }

    @Test
    public void calculoFiscalTasaCeroIvaExonerado() {
        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("40.0000")
        );
        assertEquals(new BigDecimal("50.00"), fiscal.subtotalUsd());
        assertEquals(new BigDecimal("0.00"), fiscal.ivaPorcentaje());
        assertEquals(new BigDecimal("0.00"), fiscal.ivaUsd());
        assertEquals(new BigDecimal("50.00"), fiscal.totalUsd());
        assertEquals(new BigDecimal("2000.00"), fiscal.subtotalBs());
        assertEquals(new BigDecimal("0.00"), fiscal.ivaBs());
        assertEquals(new BigDecimal("2000.00"), fiscal.totalBs());
    }

    @Test
    public void calculoFiscalTasaCienPorcientoLimiteSuperior() {
        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(
                new BigDecimal("25.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.0000")
        );
        assertEquals(new BigDecimal("25.00"), fiscal.subtotalUsd());
        assertEquals(new BigDecimal("100.00"), fiscal.ivaPorcentaje());
        assertEquals(new BigDecimal("25.00"), fiscal.ivaUsd());
        assertEquals(new BigDecimal("50.00"), fiscal.totalUsd());
        assertEquals(new BigDecimal("250.00"), fiscal.subtotalBs());
        assertEquals(new BigDecimal("250.00"), fiscal.ivaBs());
        assertEquals(new BigDecimal("500.00"), fiscal.totalBs());
    }

    @Test
    public void redondeoCentavoRedondeoHalfUp() {
        // 0.05 * 16% = 0.0080 -> redondea a 0.01
        CalculoFiscalRecord fiscal1 = CalculoFiscalRecord.calcular(
                new BigDecimal("0.05"),
                new BigDecimal("16.00"),
                new BigDecimal("36.5000")
        );
        assertEquals(new BigDecimal("0.01"), fiscal1.ivaUsd());
        assertEquals(new BigDecimal("0.06"), fiscal1.totalUsd());

        // 0.03 * 16% = 0.0048 -> redondea a 0.00
        CalculoFiscalRecord fiscal2 = CalculoFiscalRecord.calcular(
                new BigDecimal("0.03"),
                new BigDecimal("16.00"),
                new BigDecimal("36.5000")
        );
        assertEquals(new BigDecimal("0.00"), fiscal2.ivaUsd());
        assertEquals(new BigDecimal("0.03"), fiscal2.totalUsd());
    }

    @Test
    public void rechazoSubtotalNegativo() {
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("-10.00"), new BigDecimal("16.00"), new BigDecimal("36.5000"))
        );
        assertTrue(ex.getMessage().contains("no puede ser negativo"));
    }

    @Test
    public void rechazoIvaNegativo() {
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("10.00"), new BigDecimal("-0.01"), new BigDecimal("36.5000"))
        );
        assertTrue(ex.getMessage().contains("entre 0.00% y 100.00%"));
    }

    @Test
    public void rechazoIvaSuperiorACien() {
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("10.00"), new BigDecimal("100.01"), new BigDecimal("36.5000"))
        );
        assertTrue(ex.getMessage().contains("entre 0.00% y 100.00%"));
    }

    @Test
    public void rechazoIvaEscalaExcesiva() {
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("10.00"), new BigDecimal("16.123"), new BigDecimal("36.5000"))
        );
        assertTrue(ex.getMessage().contains("hasta dos decimales"));
    }

    @Test
    public void rechazoTasaCambioInvalida() {
        assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("10.00"), new BigDecimal("16.00"), BigDecimal.ZERO)
        );
        assertThrows(ErrorAplicacionException.class, () ->
                CalculoFiscalRecord.calcular(new BigDecimal("10.00"), new BigDecimal("16.00"), new BigDecimal("-5.0000"))
        );
    }

    // -------------------------------------------------------------
    // 2. MODELO CONFIG: VALIDACIÓN Y VALORES POR DEFECTO
    // -------------------------------------------------------------

    @Test
    public void configValoresPorDefectoIva() {
        Config config = new Config();
        assertEquals(new BigDecimal("16.00"), config.getIvaPorcentaje());

        config.setIvaPorcentaje(null);
        assertEquals(new BigDecimal("16.00"), config.getIvaPorcentaje());
    }

    @Test
    public void configAceptaLimitesValidos() {
        Config config = new Config();
        config.setIvaPorcentaje(BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), config.getIvaPorcentaje());

        config.setIvaPorcentaje(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("100.00"), config.getIvaPorcentaje());

        config.setIvaPorcentaje(new BigDecimal("8.00"));
        assertEquals(new BigDecimal("8.00"), config.getIvaPorcentaje());
    }

    @Test
    public void configRechazaValoresAdversarios() {
        Config config = new Config();
        assertThrows(ErrorAplicacionException.class, () -> config.setIvaPorcentaje(new BigDecimal("-1.00")));
        assertThrows(ErrorAplicacionException.class, () -> config.setIvaPorcentaje(new BigDecimal("100.01")));
        assertThrows(ErrorAplicacionException.class, () -> config.setIvaPorcentaje(new BigDecimal("16.999")));
    }

    // -------------------------------------------------------------
    // 3. PEDIDOS DAO: INTEGRIDAD FISCAL Y RECHAZO DE ALTERACIONES
    // -------------------------------------------------------------

    @Test
    public void pedidosDaoRechazaSubtotalAlteradoNoCoincidenteConDetalles() {
        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override
            public java.sql.Connection getConnection() {
                throw new UnsupportedOperationException("No debe alcanzar BD si falla la validación.");
            }
        });

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(1);
        pedido.setUsuario("Test");
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        // Subtotal manipulado a $15.00 a pesar de que los detalles suman $20.00
        pedido.setSubtotal(new BigDecimal("15.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("2.40"));
        pedido.setTotalDecimal(new BigDecimal("17.40"));

        List<DetallePedido> detalles = Arrays.asList(
                new DetallePedido(1, "Plato A", new BigDecimal("10.00"), 2, "", 1) // 10.00 * 2 = 20.00
        );

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                dao.registrarPedidoCompleto(pedido, detalles)
        );
        assertTrue(ex.getMessage().contains("no coincide con sus detalles"));
    }

    @Test
    public void pedidosDaoRechazaTotalAlteradoNoCoincidenteConSubtotalMasIva() {
        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override
            public java.sql.Connection getConnection() {
                throw new UnsupportedOperationException("No debe alcanzar BD si falla la validación.");
            }
        });

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(1);
        pedido.setUsuario("Test");
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("3.20"));
        // Total manipulado a $30.00 en lugar de $23.20
        pedido.setTotalDecimal(new BigDecimal("30.00"));

        List<DetallePedido> detalles = Arrays.asList(
                new DetallePedido(1, "Plato A", new BigDecimal("10.00"), 2, "", 1)
        );

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () ->
                dao.registrarPedidoCompleto(pedido, detalles)
        );
        assertTrue(ex.getMessage().contains("no coincide con el subtotal más IVA"));
    }

    // -------------------------------------------------------------
    // 4. GENERADOR PDF: DESGLOSE FISCAL EN TICKETS
    // -------------------------------------------------------------

    @Test
    public void generadorPdfEmiteTicketConIvaExitosamente() throws Exception {
        Path destino = temporal.newFolder("pdf-iva").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(destino);

        Config config = new Config(1, "J-12345678", "Restaurante Gourmet", "0212-0000000", "Caracas", "Gracias por su visita");
        config.setIvaPorcentaje(new BigDecimal("16.00"));
        config.setTasaDolar(new BigDecimal("36.5000"));

        Pedidos pedido = new Pedidos();
        pedido.setId(101);
        pedido.setNum_mesa(5);
        pedido.setFecha("2026-10-06");
        pedido.setSala("Comedor Principal");
        pedido.setUsuario("Cajero1");
        pedido.setSubtotal(new BigDecimal("20.00"));
        pedido.setIvaPorcentaje(new BigDecimal("16.00"));
        pedido.setIvaMonto(new BigDecimal("3.20"));
        pedido.setTotalDecimal(new BigDecimal("23.20"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setSubtotalBs(new BigDecimal("730.00"));
        pedido.setIvaBs(new BigDecimal("116.80"));
        pedido.setTotalBs(new BigDecimal("846.80"));

        List<DetallePedido> detalles = Collections.singletonList(
                new DetallePedido(1, "Pabellon Criollo", new BigDecimal("10.00"), 2, "", 101)
        );

        Path archivoPdf = generador.generar(pedido, config, detalles);
        assertNotNull(archivoPdf);
        assertTrue(Files.exists(archivoPdf));
        assertTrue(Files.size(archivoPdf) > 200);

        byte[] encabezadoBytes = new byte[5];
        System.arraycopy(Files.readAllBytes(archivoPdf), 0, encabezadoBytes, 0, 5);
        assertEquals("%PDF-", new String(encabezadoBytes, StandardCharsets.US_ASCII));
    }

    @Test
    public void testCalculoFiscalPlatosExentosYGravados() {
        // 1 plato gravado ($10) + 1 plato exento ($5)
        BigDecimal gravado = new BigDecimal("10.00");
        BigDecimal exento = new BigDecimal("5.00");
        BigDecimal ivaPorcentaje = new BigDecimal("16.00");
        BigDecimal tasa = new BigDecimal("40.0000");

        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(gravado, exento, ivaPorcentaje, tasa);

        assertEquals(new BigDecimal("15.00"), fiscal.subtotalUsd());
        assertEquals(new BigDecimal("10.00"), fiscal.baseImponibleUsd());
        assertEquals(new BigDecimal("5.00"), fiscal.exentoUsd());
        assertEquals(new BigDecimal("1.60"), fiscal.ivaUsd()); // 16% de 10.00
        assertEquals(new BigDecimal("16.60"), fiscal.totalUsd()); // 15.00 + 1.60

        assertEquals(new BigDecimal("600.00"), fiscal.subtotalBs()); // 15.00 * 40
        assertEquals(new BigDecimal("400.00"), fiscal.baseImponibleBs()); // 10.00 * 40
        assertEquals(new BigDecimal("200.00"), fiscal.exentoBs()); // 5.00 * 40
        assertEquals(new BigDecimal("64.00"), fiscal.ivaBs()); // 1.60 * 40
        assertEquals(new BigDecimal("664.00"), fiscal.totalBs()); // 16.60 * 40
    }

    @Test
    public void generadorPdfCompatibleConPedidoSinIvaLegacy() throws Exception {
        Path destino = temporal.newFolder("pdf-legacy").toPath();
        GeneradorPdfPedido generador = new GeneradorPdfPedido(destino);

        Config config = new Config(1, "J-12345678", "Restaurante Legacy", "0212-0000000", "Caracas", "Gracias");
        Pedidos pedido = new Pedidos();
        pedido.setId(102);
        pedido.setNum_mesa(2);
        pedido.setFecha("2026-10-06");
        pedido.setSala("Terraza");
        pedido.setUsuario("Mesero");
        // Campos de IVA intencionalmente nulos (pedido legacy de version anterior)
        pedido.setTotalDecimal(new BigDecimal("15.00"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setTotalBs(new BigDecimal("547.50"));

        List<DetallePedido> detalles = Collections.singletonList(
                new DetallePedido(1, "Cafe", new BigDecimal("15.00"), 1, "", 102)
        );

        Path archivoPdf = generador.generar(pedido, config, detalles);
        assertNotNull(archivoPdf);
        assertTrue(Files.exists(archivoPdf));
        assertTrue(Files.size(archivoPdf) > 200);
    }
}
