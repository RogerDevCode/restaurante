package Servicio;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Suite exhaustiva de pruebas para las nuevas características:
 * - Cierre de Caja Parcial (Corte X) y Cierre Total (Corte Z).
 * - Geometría, layout y contenido del PDF térmico de 80 mm.
 * - Desglose impositivo, financiero, operativo y platos estrella.
 * - Previsualización vs Impresión Directa en CierreCajaServicio.
 */
public class CierreCajaExhaustivoTest {

    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void cierreSinVentasManejaDivisionPorCeroYGeneraTotalesEnCero() {
        Config config = new Config(1, "J-00000000-0", "Restaurante Vacío", "0000", "Caracas", "Gracias");
        config.setTasaDolar(new BigDecimal("36.5000"));

        CierreCaja cierre = Modelo.CierreCajaDao.calcularDesdeMemoria(
                "2026-10-07",
                CierreCaja.TipoCierre.TOTAL,
                "Cajero Turno Noche",
                config,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertNotNull(cierre);
        assertEquals(CierreCaja.TipoCierre.TOTAL, cierre.getTipo());
        assertEquals(0, cierre.getPedidosFinalizados());
        assertEquals(0, cierre.getPedidosPendientes());
        assertEquals(new BigDecimal("0.00"), cierre.getTotalVentasUsd());
        assertEquals(new BigDecimal("0.00"), cierre.getTotalVentasBs());
        assertEquals(new BigDecimal("0.00"), cierre.getTicketPromedioUsd());
        assertEquals(new BigDecimal("0.00"), cierre.getTicketPromedioBs());
        assertEquals(new BigDecimal("0.00"), cierre.getIvaUsd());
        assertEquals(new BigDecimal("0.00"), cierre.getIvaBs());
        assertTrue(cierre.getDesgloseMetodos().isEmpty());
        assertTrue(cierre.getTopPlatos().isEmpty());
    }

    @Test
    public void cierreConVentasMultiplesYMetodosDePagoConsolidaCorrectamente() {
        Config config = new Config(1, "J-98765432-1", "Gourmet Plaza", "0212-5554433", "Las Mercedes", "Vuelva pronto");
        config.setTasaDolar(new BigDecimal("40.0000"));

        List<Pedidos> pedidos = new ArrayList<>();

        // Pedido 1: Efectivo
        Pedidos p1 = new Pedidos();
        p1.setId(101);
        p1.setEstado("FINALIZADO");
        p1.setTotalDecimal(new BigDecimal("100.00"));
        p1.setSubtotal(new BigDecimal("86.21"));
        p1.setIvaMonto(new BigDecimal("13.79"));
        p1.setTotalBs(new BigDecimal("4000.00"));
        p1.setSubtotalBs(new BigDecimal("3448.40"));
        p1.setIvaBs(new BigDecimal("551.60"));
        p1.setMetodoPago("EFECTIVO");
        p1.setSala("Salón Principal");
        p1.setUsuario("Carlos Mesero");
        pedidos.add(p1);

        // Pedido 2: Tarjeta
        Pedidos p2 = new Pedidos();
        p2.setId(102);
        p2.setEstado("FINALIZADO");
        p2.setTotalDecimal(new BigDecimal("50.00"));
        p2.setSubtotal(new BigDecimal("43.10"));
        p2.setIvaMonto(new BigDecimal("6.90"));
        p2.setTotalBs(new BigDecimal("2000.00"));
        p2.setSubtotalBs(new BigDecimal("1724.00"));
        p2.setIvaBs(new BigDecimal("276.00"));
        p2.setMetodoPago("TARJETA");
        p2.setSala("Terraza");
        p2.setUsuario("Ana Mesera");
        pedidos.add(p2);

        // Pedido 3: Pago Móvil
        Pedidos p3 = new Pedidos();
        p3.setId(103);
        p3.setEstado("FINALIZADO");
        p3.setTotalDecimal(new BigDecimal("25.00"));
        p3.setSubtotal(new BigDecimal("21.55"));
        p3.setIvaMonto(new BigDecimal("3.45"));
        p3.setTotalBs(new BigDecimal("1000.00"));
        p3.setSubtotalBs(new BigDecimal("862.00"));
        p3.setIvaBs(new BigDecimal("138.00"));
        p3.setMetodoPago("PAGO_MOVIL");
        p3.setSala("Terraza");
        p3.setUsuario("Carlos Mesero");
        pedidos.add(p3);

        // Pedido 4: Pendiente (no cobrado)
        Pedidos p4 = new Pedidos();
        p4.setId(104);
        p4.setEstado("PENDIENTE");
        p4.setTotalDecimal(new BigDecimal("40.00"));
        p4.setSala("Salón Principal");
        p4.setUsuario("Ana Mesera");
        pedidos.add(p4);

        // Detalles acumulativos
        List<DetallePedido> detalles = new ArrayList<>();
        detalles.add(new DetallePedido(1, "Hamburguesa Especial", new BigDecimal("15.00"), 4, "", 101)); // cant: 4
        detalles.add(new DetallePedido(2, "Refresco", new BigDecimal("2.50"), 6, "", 101));           // cant: 6
        detalles.add(new DetallePedido(3, "Hamburguesa Especial", new BigDecimal("15.00"), 2, "", 102)); // cant: +2 = 6
        detalles.add(new DetallePedido(4, "Café Espresso", new BigDecimal("3.00"), 5, "", 103));       // cant: 5

        CierreCaja cierre = Modelo.CierreCajaDao.calcularDesdeMemoria(
                "2026-10-07",
                CierreCaja.TipoCierre.PARCIAL,
                "Supervisor José",
                config,
                pedidos,
                detalles
        );

        assertEquals(3, cierre.getPedidosFinalizados());
        assertEquals(1, cierre.getPedidosPendientes());
        assertEquals(new BigDecimal("175.00"), cierre.getTotalVentasUsd());
        assertEquals(new BigDecimal("7000.00"), cierre.getTotalVentasBs());
        assertEquals(new BigDecimal("58.33"), cierre.getTicketPromedioUsd());
        assertEquals(new BigDecimal("2333.33"), cierre.getTicketPromedioBs());

        // Validar métodos de pago
        BigDecimal efectivoUsd = cierre.getDesgloseMetodos().stream()
                .filter(m -> "EFECTIVO".equals(m.getMetodo()))
                .map(CierreCaja.ResumenMetodoPago::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal tarjetaUsd = cierre.getDesgloseMetodos().stream()
                .filter(m -> "TARJETA".equals(m.getMetodo()))
                .map(CierreCaja.ResumenMetodoPago::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal pagoMovilUsd = cierre.getDesgloseMetodos().stream()
                .filter(m -> "PAGO_MOVIL".equals(m.getMetodo()))
                .map(CierreCaja.ResumenMetodoPago::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);

        assertEquals(new BigDecimal("100.00"), efectivoUsd);
        assertEquals(new BigDecimal("50.00"), tarjetaUsd);
        assertEquals(new BigDecimal("25.00"), pagoMovilUsd);

        // Validar salas
        BigDecimal salonUsd = cierre.getDesgloseSalas().stream()
                .filter(s -> "Salón Principal".equals(s.getSala()))
                .map(CierreCaja.ResumenSala::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal terrazaUsd = cierre.getDesgloseSalas().stream()
                .filter(s -> "Terraza".equals(s.getSala()))
                .map(CierreCaja.ResumenSala::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        assertEquals(new BigDecimal("100.00"), salonUsd);
        assertEquals(new BigDecimal("75.00"), terrazaUsd);

        // Validar mozos
        BigDecimal carlosUsd = cierre.getDesgloseUsuarios().stream()
                .filter(u -> "Carlos Mesero".equals(u.getUsuario()))
                .map(CierreCaja.ResumenUsuario::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal anaUsd = cierre.getDesgloseUsuarios().stream()
                .filter(u -> "Ana Mesera".equals(u.getUsuario()))
                .map(CierreCaja.ResumenUsuario::getMontoUsd)
                .findFirst().orElse(BigDecimal.ZERO);
        assertEquals(new BigDecimal("125.00"), carlosUsd);
        assertEquals(new BigDecimal("50.00"), anaUsd);

        // Validar Top Platos (Hamburguesa y Refresco consolidados)
        assertTrue(cierre.getTopPlatos().size() >= 3);
        CierreCaja.PlatoVendido top1 = cierre.getTopPlatos().stream()
                .filter(p -> "Hamburguesa Especial".equals(p.getNombre()))
                .findFirst().orElse(null);
        assertNotNull(top1);
        assertEquals(6, top1.getCantidad()); // 4 + 2
        assertEquals(new BigDecimal("90.00"), top1.getTotalUsd()); // 6 * 15.00
    }

    @Test
    public void generadorPdfGeneraFormato80mmConCorteXYCorteZ() throws Exception {
        Path carpetaSalida = temporal.newFolder("cierres-80mm").toPath();
        GeneradorPdfCierre generador = new GeneradorPdfCierre(carpetaSalida);

        Config config = new Config(1, "J-11223344-5", "Ristorante Bella Italia", "0212-1112233", "Altamira Sur", "Gracias por su visita");
        config.setTasaDolar(new BigDecimal("36.5000"));

        // 1. Probar CORTE X (Parcial)
        CierreCaja cierreX = new CierreCaja();
        cierreX.setTipo(CierreCaja.TipoCierre.PARCIAL);
        cierreX.setFecha("2026-10-07");
        cierreX.setFechaHoraEmision("2026-10-07 14:30:00");
        cierreX.setUsuarioEmisor("Cajera Laura");
        cierreX.setTasaCambioReferencia(new BigDecimal("36.5000"));
        cierreX.setPedidosFinalizados(5);
        cierreX.setPedidosPendientes(2);
        cierreX.setTotalVentasUsd(new BigDecimal("150.00"));
        cierreX.setTotalVentasBs(new BigDecimal("5475.00"));
        cierreX.setSubtotalUsd(new BigDecimal("129.31"));
        cierreX.setSubtotalBs(new BigDecimal("4719.82"));
        cierreX.setIvaUsd(new BigDecimal("20.69"));
        cierreX.setIvaBs(new BigDecimal("755.18"));
        cierreX.setTicketPromedioUsd(new BigDecimal("30.00"));
        cierreX.setTicketPromedioBs(new BigDecimal("1095.00"));
        cierreX.setDesgloseMetodos(Arrays.asList(
                new CierreCaja.ResumenMetodoPago("EFECTIVO", 3, new BigDecimal("100.00"), new BigDecimal("3650.00")),
                new CierreCaja.ResumenMetodoPago("PAGO_MOVIL", 2, new BigDecimal("50.00"), new BigDecimal("1825.00"))
        ));
        cierreX.setDesgloseSalas(Collections.singletonList(
                new CierreCaja.ResumenSala("Principal", 5, new BigDecimal("150.00"), new BigDecimal("5475.00"))
        ));
        cierreX.setDesgloseUsuarios(Collections.singletonList(
                new CierreCaja.ResumenUsuario("Carlos", 5, new BigDecimal("150.00"))
        ));
        cierreX.setTopPlatos(Collections.singletonList(
                new CierreCaja.PlatoVendido("Pizza Napolitana", 5, new BigDecimal("75.00"))
        ));

        Path archivoX = generador.generar(cierreX, config);
        assertTrue(Files.exists(archivoX));
        assertTrue(archivoX.getFileName().toString().startsWith("cierre-X_"));

        // Inspeccionar PDF X
        PdfReader readerX = new PdfReader(archivoX.toAbsolutePath().toString());
        assertEquals("Debe ser rollo térmico continuo en 1 sola página", 1, readerX.getNumberOfPages());
        assertEquals("Ancho térmico 80 mm (227 pt)", 227.0f, readerX.getPageSize(1).getWidth(), 1.0f);
        String textoX = PdfTextExtractor.getTextFromPage(readerX, 1);
        readerX.close();

        assertTrue(textoX.contains("CORTE X - CIERRE PARCIAL"));
        assertTrue(textoX.contains("Ristorante Bella Italia"));
        assertTrue(textoX.contains("J-11223344-5"));
        assertTrue(textoX.contains("Cajera Laura"));
        assertTrue(textoX.contains("TOTAL GENERAL:"));
        assertTrue(textoX.contains("$ 150.00"));
        assertTrue(textoX.contains("Bs. 5475.00"));
        assertTrue(textoX.contains("Firma Responsable de Caja"));
        assertTrue(textoX.contains("*** CORTE PARCIAL - TURNO EN CURSO ***"));

        // 2. Probar CORTE Z (Total)
        CierreCaja cierreZ = new CierreCaja();
        cierreZ.setTipo(CierreCaja.TipoCierre.TOTAL);
        cierreZ.setFecha("2026-10-07");
        cierreZ.setFechaHoraEmision("2026-10-07 23:00:00");
        cierreZ.setUsuarioEmisor("Gerente Roberto");
        cierreZ.setTasaCambioReferencia(new BigDecimal("36.5000"));
        cierreZ.setPedidosFinalizados(12);
        cierreZ.setPedidosPendientes(0);
        cierreZ.setTotalVentasUsd(new BigDecimal("500.00"));
        cierreZ.setTotalVentasBs(new BigDecimal("18250.00"));
        cierreZ.setSubtotalUsd(new BigDecimal("431.03"));
        cierreZ.setSubtotalBs(new BigDecimal("15732.60"));
        cierreZ.setIvaUsd(new BigDecimal("68.97"));
        cierreZ.setIvaBs(new BigDecimal("2517.40"));
        cierreZ.setTicketPromedioUsd(new BigDecimal("41.67"));
        cierreZ.setTicketPromedioBs(new BigDecimal("1520.83"));

        Path archivoZ = generador.generar(cierreZ, config);
        assertTrue(Files.exists(archivoZ));
        assertTrue(archivoZ.getFileName().toString().startsWith("cierre-Z_"));

        // Inspeccionar PDF Z
        PdfReader readerZ = new PdfReader(archivoZ.toAbsolutePath().toString());
        assertEquals(1, readerZ.getNumberOfPages());
        assertEquals("Ancho térmico 80 mm (227 pt)", 227.0f, readerZ.getPageSize(1).getWidth(), 1.0f);
        String textoZ = PdfTextExtractor.getTextFromPage(readerZ, 1);
        readerZ.close();

        assertTrue(textoZ.contains("CORTE Z - CIERRE TOTAL"));
        assertTrue(textoZ.contains("Gerente Roberto"));
        assertTrue(textoZ.contains("TOTAL GENERAL:"));
        assertTrue(textoZ.contains("$ 500.00"));
        assertTrue(textoZ.contains("Bs. 18250.00"));
    }

    @Test
    public void cierreCajaServicioManejaPrevisualizacionEImpresionSinLanzarErrores() throws IOException {
        Path dirPdf = temporal.newFolder("pdf-cierre").toPath();
        GeneradorPdfCierre generador = new GeneradorPdfCierre(dirPdf);

        Config config = new Config(1, "J-123", "Mi Restaurante", "555", "Direccion", "Gracias");
        config.setTasaDolar(new BigDecimal("36.5000"));

        AtomicReference<Path> visorAbierto = new AtomicReference<>();
        AtomicReference<Path> impresoDirecto = new AtomicReference<>();

        Modelo.CierreCajaDao daoFalso = new Modelo.CierreCajaDao() {
            @Override
            public CierreCaja consultarCierre(String fecha, CierreCaja.TipoCierre tipo, String usuarioEmisor, Config cfg) {
                CierreCaja c = new CierreCaja();
                c.setTipo(tipo);
                c.setFecha(fecha != null ? fecha : "2026-10-07");
                c.setFechaHoraEmision("2026-10-07 18:00:00");
                c.setUsuarioEmisor(usuarioEmisor);
                c.setTotalVentasUsd(new BigDecimal("100.00"));
                c.setTotalVentasBs(new BigDecimal("3650.00"));
                return c;
            }

            @Override
            public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
                return true;
            }
        };

        CierreCajaServicio servicio = new CierreCajaServicio(
                daoFalso,
                () -> config,
                generador,
                p -> { impresoDirecto.set(p); return true; },
                p -> { visorAbierto.set(p); return true; }
        );

        // 1. Probar previsualizar
        var resultadoPrev = servicio.previsualizarCierre("2026-10-07", CierreCaja.TipoCierre.PARCIAL, "Admin");
        assertNotNull(resultadoPrev);
        assertEquals(resultadoPrev.archivo(), visorAbierto.get());
        assertNull("La previsualización no debe enviar a la impresora directa", impresoDirecto.get());

        // 2. Probar imprimir directo
        visorAbierto.set(null);
        var resultadoImp = servicio.imprimirCierre("2026-10-07", CierreCaja.TipoCierre.TOTAL, "Supervisor");
        assertNotNull(resultadoImp);
        assertEquals(resultadoImp.archivo(), impresoDirecto.get());
        assertNull("La impresión directa no debe abrir el visor", visorAbierto.get());
    }

    @Test
    public void cierreCajaConConfiguracionIncompletaNoFallaYAsignaValoresSeguros() throws Exception {
        Path dir = temporal.newFolder("fallback-config").toPath();
        GeneradorPdfCierre generador = new GeneradorPdfCierre(dir);

        // Configuración con campos nulos
        Config configVacia = new Config();
        configVacia.setNombre(null);
        configVacia.setRif(null);
        configVacia.setTelefono(null);
        configVacia.setDireccion(null);
        configVacia.setMensaje(null);
        configVacia.setTasaDolar(null);

        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(CierreCaja.TipoCierre.PARCIAL);
        cierre.setFecha("2026-10-07");
        cierre.setFechaHoraEmision("2026-10-07 12:00:00");
        cierre.setUsuarioEmisor(null);
        cierre.setTasaCambioReferencia(null);
        cierre.setTotalVentasUsd(null);
        cierre.setTotalVentasBs(null);
        cierre.setSubtotalUsd(null);
        cierre.setSubtotalBs(null);
        cierre.setIvaUsd(null);
        cierre.setIvaBs(null);
        cierre.setTicketPromedioUsd(null);
        cierre.setTicketPromedioBs(null);

        Path archivo = generador.generar(cierre, configVacia);
        assertTrue(Files.exists(archivo));

        PdfReader reader = new PdfReader(archivo.toAbsolutePath().toString());
        String texto = PdfTextExtractor.getTextFromPage(reader, 1);
        reader.close();

        assertTrue("Debe mostrar nombre de fallback 'RESTAURANTE'", texto.contains("RESTAURANTE"));
        assertTrue("Debe mostrar 'Sistema'", texto.contains("Sistema"));
    }
}
