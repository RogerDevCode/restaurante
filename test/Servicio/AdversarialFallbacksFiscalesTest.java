package Servicio;

import Modelo.CierreCaja;
import Modelo.CierreCajaDao;
import Modelo.CalculoFiscalRecord;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Modelo.Usuario;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite adversarial para verificar que ningún cálculo o persistencia fiscal
 * aplique fallbacks silenciosos, y que todos los fallbacks legítimos registren logs.
 */
public class AdversarialFallbacksFiscalesTest {

    private final PoliticaAcceso admin = new PoliticaAcceso(new Usuario(1, "Admin", "a@a.com", "p", "Administrador"));
    private final CapturadorLogs capturador = new CapturadorLogs();
    private final Logger loggerCfg = Logger.getLogger(ConfiguracionServicio.class.getName());
    private final Logger loggerCierre = Logger.getLogger(CierreCajaDao.class.getName());

    @Before
    public void setUp() {
        loggerCfg.addHandler(capturador);
        loggerCierre.addHandler(capturador);
    }

    @After
    public void tearDown() {
        loggerCfg.removeHandler(capturador);
        loggerCierre.removeHandler(capturador);
    }

    // 1. PlatosServicio no tolera monedaBase ausente ni la defaultea a USD
    @Test
    public void platosServicioRechazaMonedaBaseAusenteOInvalida() {
        PlatosServicio servicio = new PlatosServicio(new FakeRepo(), admin);

        Platos sinMoneda = new Platos(0, "Pollo", new BigDecimal("10.00"), "2026-10-15");
        sinMoneda.setPrecioIncluyeIva(false);
        assertThrows("Moneda nula debe ser rechazada", ErrorAplicacionException.class,
                () -> servicio.registrar(sinMoneda));

        sinMoneda.setMonedaBase("   ");
        assertThrows("Moneda vacía debe ser rechazada", ErrorAplicacionException.class,
                () -> servicio.registrar(sinMoneda));

        sinMoneda.setMonedaBase("EUR");
        assertThrows("Moneda no soportada debe ser rechazada", ErrorAplicacionException.class,
                () -> servicio.registrar(sinMoneda));
    }

    // 2. PedidoServicio exige ivaPorcentaje y tasa en pedido antes de calcular
    @Test
    public void pedidoServicioRechazaIvaYTasaAusentes() {
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso());

        Pedidos p = new Pedidos();
        p.setId_sala(1);
        p.setNum_mesa(1);
        p.setUsuario("Mesonero");
        p.setTotalDecimal(new BigDecimal("10.00"));

        DetallePedido d = new DetallePedido();
        d.setNombre("Sopa");
        d.setCantidad(1);
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setMonedaBase("USD");
        d.setAplicaIva(false);
        d.setPrecioIncluyeIva(false);

        // Sin IVA
        assertThrows("Pedido sin IVA debe ser rechazado", ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(p, List.of(d)));

        p.setIvaPorcentaje(new BigDecimal("16.00"));
        // Sin Tasa
        assertThrows("Pedido sin tasa debe ser rechazado", ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(p, List.of(d)));
    }

    // 3. CalculoFiscalRecord exige ivaPorcentaje explícito
    @Test
    public void calculoFiscalRecordRechazaIvaNulo() {
        DetallePedido d = new DetallePedido();
        d.setNombre("Plato");
        d.setMonedaBase("USD");
        d.setAplicaIva(true);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(new BigDecimal("16.00"));
        d.setPrecioDecimal(new BigDecimal("10.00"));
        d.setCantidad(1);

        assertThrows("List<Detalle> con ivaPorcentaje null debe lanzar validacion",
                ErrorAplicacionException.class,
                () -> CalculoFiscalRecord.calcular(List.of(d), null, new BigDecimal("36.5000")));

        assertThrows("Base imponible con ivaPorcentaje null debe lanzar validacion",
                ErrorAplicacionException.class,
                () -> CalculoFiscalRecord.calcular(new BigDecimal("10.00"), BigDecimal.ZERO, null, new BigDecimal("36.5000")));
    }

    // 4. Config y CierreCaja no inyectan defaults mágicos en setters
    @Test
    public void entidadesNoInyectanDefaultsMagicosEnSetters() {
        Config cfg = new Config();
        assertNull("tasaDolar por defecto en Config debe ser null", cfg.getTasaDolar());
        assertNull("ivaPorcentaje por defecto en Config debe ser null", cfg.getIvaPorcentaje());

        cfg.setTasaDolar(null);
        assertNull("setTasaDolar(null) no debe forzar 36.5000", cfg.getTasaDolar());

        cfg.setTasaDolar(BigDecimal.ZERO);
        assertNull("setTasaDolar(0) no debe forzar 36.5000", cfg.getTasaDolar());

        cfg.setIvaPorcentaje(null);
        assertNull("setIvaPorcentaje(null) no debe forzar 16.00", cfg.getIvaPorcentaje());

        CierreCaja cierre = new CierreCaja();
        assertNull("tasaCambioReferencia por defecto en CierreCaja debe ser null", cierre.getTasaCambioReferencia());

        cierre.setTasaCambioReferencia(null);
        assertNull("setTasaCambioReferencia(null) no debe forzar 36.5000", cierre.getTasaCambioReferencia());
    }

    // 5. CierreCajaDao exige tasa configurada para consultar y guardar
    @Test
    public void cierreCajaDaoExigeTasaParaConsultarYGuardar() {
        CierreCajaDao dao = new CierreCajaDao();

        Config cfgSinTasa = new Config();
        assertThrows("consultarCierre sin tasa debe fallar", ErrorAplicacionException.class,
                () -> dao.consultarCierre("2026-10-15", CierreCaja.TipoCierre.TOTAL, "Admin", cfgSinTasa));

        CierreCaja cierreSinTasa = new CierreCaja();
        assertThrows("guardarCierre sin tasa debe fallar", ErrorAplicacionException.class,
                () -> dao.guardarCierre(cierreSinTasa, "cierre.pdf"));
    }

    // 6. Legitimate fallbacks generan log
    @Test
    public void fallbacksLegitimosGeneranLogs() {
        capturador.registros.clear();

        ConfiguracionServicio configServicio = new ConfiguracionServicio(new FakeConfigRepo());
        String val = configServicio.getString("CLAVE_INEXISTENTE", "VALOR_DEFECTO");
        assertEquals("VALOR_DEFECTO", val);

        boolean huboLog = capturador.registros.stream()
                .anyMatch(r -> r.getMessage().contains("Fallback aplicado") || r.getMessage().contains("referencial"));
        assertTrue("ConfiguracionServicio debe haber emitido un log de fallback", huboLog);

        capturador.registros.clear();
        Config cfgVacio = new Config();
        CierreCaja cierreMem = CierreCajaDao.calcularDesdeMemoria("2026-10-15", CierreCaja.TipoCierre.TOTAL, "Admin", cfgVacio, List.of(), List.of());
        assertEquals(new BigDecimal("36.5000"), cierreMem.getTasaCambioReferencia());

        boolean huboLogCierre = capturador.registros.stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING && r.getMessage().contains("referencial"));
        assertTrue("CierreCajaDao.calcularDesdeMemoria debe haber emitido un log de advertencia", huboLogCierre);
    }

    private static final class FakeRepo implements PlatosRepositorio {
        @Override public boolean registrar(Platos p) { return true; }
        @Override public List<Platos> listarPorFecha(String n, String f) { return Collections.emptyList(); }
        @Override public boolean desactivar(int id) { return true; }
        @Override public boolean reactivar(int id) { return true; }
        @Override public List<Platos> listarInactivos() { return Collections.emptyList(); }
        @Override public boolean modificar(Platos p) { return true; }
    }

    private static final class FakeConfigRepo implements Modelo.ConfiguracionRepositorio {
        @Override public String obtener(String clave, String porDefecto) { return porDefecto; }
        @Override public void guardar(String clave, String valor) {}
        @Override public void eliminar(String clave) {}
        @Override public java.util.Map<String, String> obtenerTodos() { return Collections.emptyMap(); }
        @Override public void guardarVarios(java.util.Map<String, String> configuraciones) {}
        @Override public boolean existe(String clave) { return false; }
        @Override public java.sql.Timestamp obtenerFechaActualizacion(String clave) { return null; }
    }

    private static final class CapturadorLogs extends Handler {
        final List<LogRecord> registros = new ArrayList<>();
        @Override public void publish(LogRecord record) { registros.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
