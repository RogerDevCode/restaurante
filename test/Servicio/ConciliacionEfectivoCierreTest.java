package Servicio;

import Modelo.CierreCaja;
import Modelo.CierreCajaDao;
import Modelo.Config;
import Modelo.ErrorAplicacionException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class ConciliacionEfectivoCierreTest {

    private CierreCaja cierre;
    private Config config;

    @Before
    public void setUp() {
        cierre = new CierreCaja();
        cierre.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierre.setFecha("2026-10-08");
        cierre.setFechaHoraEmision("2026-10-08 18:30:00");
        cierre.setUsuarioEmisor("Cajero Principal");
        cierre.setTasaCambioReferencia(new BigDecimal("36.5000"));

        // Simular desglose de formas de pago: Efectivo Bs 200.00, Efectivo USD $5.48, Punto Bs 300.00
        cierre.setDesgloseMetodos(List.of(
                new CierreCaja.ResumenMetodoPago("EFECTIVO_BS", 2, new BigDecimal("5.48"), new BigDecimal("200.00")),
                new CierreCaja.ResumenMetodoPago("EFECTIVO_USD", 1, new BigDecimal("5.48"), new BigDecimal("200.00")),
                new CierreCaja.ResumenMetodoPago("PUNTO_VENTA", 3, new BigDecimal("8.22"), new BigDecimal("300.00"))
        ));

        config = new Config();
        config.setNombre("Restaurante El Paraíso");
        config.setRif("J-12345678-9");
        config.setTasaDolar(new BigDecimal("36.5000"));
    }

    @Test
    public void calculoTotalEfectivoEsperadoEsCorrecto() {
        assertEquals("El efectivo esperado en Bs debe ser exactamente 200.00",
                new BigDecimal("200.00"), cierre.getTotalEfectivoBs());
        assertEquals("El efectivo esperado en USD debe ser exactamente 5.48",
                new BigDecimal("5.48"), cierre.getTotalEfectivoUsd());
    }

    @Test
    public void conciliacionExactaCuandoEfectivoCoincide() {
        cierre.setEfectivoDeclaradoBs(new BigDecimal("200.00"));

        assertTrue(cierre.tieneConciliacionEfectivo());
        assertTrue(cierre.tieneConciliacionBs());
        assertEquals("La diferencia debe ser 0.00",
                new BigDecimal("0.00"), cierre.getDiferenciaEfectivoBs());
        assertEquals("El estado debe ser EXACTO", "EXACTO", cierre.getEstadoConciliacionBs());
    }

    @Test
    public void conciliacionSobranteCuandoHayMasEfectivoFisico() {
        cierre.setEfectivoDeclaradoBs(new BigDecimal("215.50"));

        assertTrue(cierre.tieneConciliacionEfectivo());
        assertEquals("La diferencia debe ser +15.50",
                new BigDecimal("15.50"), cierre.getDiferenciaEfectivoBs());
        assertEquals("El estado debe ser SOBRANTE", "SOBRANTE", cierre.getEstadoConciliacionBs());
    }

    @Test
    public void conciliacionFaltanteCuandoHayMenosEfectivoFisico() {
        cierre.setEfectivoDeclaradoBs(new BigDecimal("185.00"));

        assertTrue(cierre.tieneConciliacionEfectivo());
        assertEquals("La diferencia debe ser -15.00",
                new BigDecimal("-15.00"), cierre.getDiferenciaEfectivoBs());
        assertEquals("El estado debe ser FALTANTE", "FALTANTE", cierre.getEstadoConciliacionBs());
    }

    @Test
    public void conciliacionEnDolaresUsd() {
        cierre.setEfectivoDeclaradoUsd(new BigDecimal("10.00")); // Esperado 5.48 -> Sobrante 4.52

        assertTrue(cierre.tieneConciliacionEfectivo());
        assertTrue(cierre.tieneConciliacionUsd());
        assertEquals(new BigDecimal("4.52"), cierre.getDiferenciaEfectivoUsd());
        assertEquals("SOBRANTE", cierre.getEstadoConciliacionUsd());
    }

    @Test
    public void rechazaMontosDeEfectivoNegativos() {
        assertThrows("No se deben permitir montos de efectivo negativos en Bs",
                ErrorAplicacionException.class, () -> cierre.setEfectivoDeclaradoBs(new BigDecimal("-1.00")));

        assertThrows("No se deben permitir montos de efectivo negativos en USD",
                ErrorAplicacionException.class, () -> cierre.setEfectivoDeclaradoUsd(new BigDecimal("-0.01")));
    }

    @Test
    public void cierreCajaDaoRechazaFechasFuturasOInvalidas() {
        CierreCajaDao dao = new CierreCajaDao();

        assertThrows("Debe rechazar formatos inválidos de fecha",
                ErrorAplicacionException.class,
                () -> dao.consultarCierre("ayer", CierreCaja.TipoCierre.TOTAL, "Admin", config));

        String fechaFutura = LocalDate.now().plusDays(2).toString();
        assertThrows("Debe rechazar fechas futuras",
                ErrorAplicacionException.class,
                () -> dao.consultarCierre(fechaFutura, CierreCaja.TipoCierre.TOTAL, "Admin", config));
    }

    @Test
    public void generadorPdfIncluyeSeccionDeArqueoCuandoSeDeclaraEfectivo() throws IOException {
        cierre.setEfectivoDeclaradoBs(new BigDecimal("190.00")); // Faltante de 10.00 Bs
        cierre.setEfectivoDeclaradoUsd(new BigDecimal("5.48"));   // Exacto en USD

        GeneradorPdfCierre generador = new GeneradorPdfCierre();
        Path archivoPdf = generador.generar(cierre, config);

        assertNotNull("El PDF generado no debe ser nulo", archivoPdf);
        assertTrue("El archivo debe existir en disco", Files.exists(archivoPdf));
        assertTrue("El tamaño debe ser mayor a 1KB", Files.size(archivoPdf) > 1024);

        // Limpiar archivo temporal
        Files.deleteIfExists(archivoPdf);
    }
}
