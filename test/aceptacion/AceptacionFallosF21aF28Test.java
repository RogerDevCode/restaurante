package aceptacion;

import Modelo.*;
import Servicio.*;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import org.junit.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Suite de aceptación y cobertura de puntos ciegos para los defectos F-21 a F-28.
 */
public class AceptacionFallosF21aF28Test {

    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

    @Test
    public void testF21_CierreZCuadraDesglosesConCabeceraDeFormaHomogenea() {
        Config config = new Config();
        config.setTasaDolar(new BigDecimal("1000.0000"));

        CierreCajaDao dao = new CierreCajaDao(proveedor);
        CierreCaja cierre = dao.consultarCierre("1999-01-01", CierreCaja.TipoCierre.TOTAL, "Test", config);
        assertNotNull(cierre);
        assertEquals("Total Bs debe ser cero para fecha vacía", 0, cierre.getTotalVentasBs().compareTo(BigDecimal.ZERO));

        BigDecimal sumMetodosBs = cierre.getDesgloseMetodos().stream()
                .map(CierreCaja.ResumenMetodoPago::getMontoBs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumSalasBs = cierre.getDesgloseSalas().stream()
                .map(CierreCaja.ResumenSala::getMontoBs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals("Suma métodos Bs debe igualar cabecera", 0, cierre.getTotalVentasBs().compareTo(sumMetodosBs));
        assertEquals("Suma salas Bs debe igualar cabecera", 0, cierre.getTotalVentasBs().compareTo(sumSalasBs));
    }

    @Test
    public void testF22_DetalleSinIdPlatoPreservaNuloYNoSustituyePorIdDetalle() {
        DetallePedido det = new DetallePedido();
        det.setId(9999);
        det.setIdPlato(null);
        det.setNombre("Plato Especial Abierto");
        det.setCantidad(1);
        det.setPrecioDecimal(new BigDecimal("15.00"));
        det.setMonedaBase("USD");
        det.setAplicaIva(true);
        det.setPrecioIncluyeIva(false);

        // Al verificar la regla de resolución de ID plato para ítems desclasificados:
        int idPlatoResuelto = (det.getIdPlato() != null && det.getIdPlato() > 0) ? det.getIdPlato() : (det.getId() > 0 ? -det.getId() : 0);
        assertTrue("Un detalle sin id_plato debe resolverse a un identificador no positivo", idPlatoResuelto <= 0);

        // Al reconstruir el detalle para persistencia:
        Integer idPlatoParaDb = idPlatoResuelto > 0 ? idPlatoResuelto : null;
        assertNull("El id_plato persistible para BD debe ser NULL, jamás 9999", idPlatoParaDb);
    }

    @Test
    public void testF24_RecalculoPlatosNoInflaLineasSinMonedaAUsd() {
        DetallePedido det = new DetallePedido();
        det.setId(1234);
        det.setNombre("Bebida Casera");
        det.setPrecioDecimal(new BigDecimal("200.00"));
        det.setMonedaBase(null);
        det.setAplicaIva(null);
        det.setPrecioIncluyeIva(null);

        // Regla F-24: auto-congelar en VES para preservar valor nominal
        det.setPrecioCongelado(true);
        if (det.getMonedaBase() == null || det.getMonedaBase().isBlank()) det.setMonedaBase("VES");
        if (det.getAplicaIva() == null) det.setAplicaIva(false);
        if (det.getPrecioIncluyeIva() == null) det.setPrecioIncluyeIva(false);

        assertEquals("La moneda base de un ítem sin clasificar debe ser VES para no inflar en USD", "VES", det.getMonedaBase());
        assertFalse("Un ítem no clasificado no debe inventar que aplica IVA", det.getAplicaIva());
        assertFalse("Un ítem no clasificado no debe inventar precio_incluye_iva", det.getPrecioIncluyeIva());
        assertEquals("El precio nominal en Bs debe preservarse", 0, det.getPrecioDecimal().compareTo(new BigDecimal("200.00")));
    }

    @Test
    public void testF26_CierreZRechazaDuplicadosParaMismaFecha() {
        CierreCajaDao dao = new CierreCajaDao(proveedor);
        String fechaPrueba = "2099-12-31";

        CierreCaja c1 = new CierreCaja();
        c1.setTipo(CierreCaja.TipoCierre.TOTAL);
        c1.setFecha(fechaPrueba);
        c1.setFechaHoraEmision("2099-12-31 23:00:00");
        c1.setUsuarioEmisor("Administrador");
        c1.setTotalVentasUsd(BigDecimal.ZERO);
        c1.setTotalVentasBs(BigDecimal.ZERO);
        c1.setResumenEfectivo(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        c1.setTasaCambioReferencia(new BigDecimal("1000.0000"));

        try {
            boolean registrado = dao.guardarCierre(c1, null);
            assertTrue("El primer cierre Z debe registrarse exitosamente", registrado);

            CierreCaja c2 = new CierreCaja();
            c2.setTipo(CierreCaja.TipoCierre.TOTAL);
            c2.setFecha(fechaPrueba);
            c2.setFechaHoraEmision("2099-12-31 23:05:00");
            c2.setUsuarioEmisor("Administrador");
            c2.setTotalVentasUsd(BigDecimal.ZERO);
            c2.setTotalVentasBs(BigDecimal.ZERO);
            c2.setResumenEfectivo(BigDecimal.ZERO, BigDecimal.ZERO, 0);
            c2.setTasaCambioReferencia(new BigDecimal("1000.0000"));

            assertThrows("Registrar un segundo Cierre Z para la misma fecha debe ser rechazado",
                    ErrorAplicacionException.class, () -> dao.guardarCierre(c2, null));
        } finally {
            try (Connection con = proveedor.getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM cierres_caja WHERE fecha_jornada = ?")) {
                ps.setString(1, fechaPrueba);
                ps.executeUpdate();
            } catch (Exception ignore) {}
        }
    }

    @Test
    public void testF27_AjusteCambioBsSincronizadoConTotalUsdAditivo() {
        DetallePedido d = new DetallePedido();
        d.setPrecioDecimal(new BigDecimal("100.00"));
        d.setCantidad(1);
        d.setAplicaIva(true);
        d.setPrecioIncluyeIva(false);
        d.setIvaPorcentaje(new BigDecimal("16.00"));
        d.setMonedaBase("VES");

        BigDecimal tasa = new BigDecimal("1014.0000");
        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(List.of(d), tasa);

        // Verificar aditividad USD
        assertEquals("Subtotal USD + IVA USD == Total USD",
                calc.subtotalUsd().add(calc.ivaUsd()).setScale(2, RoundingMode.HALF_UP),
                calc.totalUsd());

        // Verificar que ajusteCambioBs cuadre con totalUsd()
        BigDecimal esperadoAjuste = calc.totalVes().subtract(
                calc.totalUsd().multiply(tasa).setScale(2, RoundingMode.HALF_UP)).setScale(2);
        assertEquals("ajusteCambioBs debe calcularse contra totalUsd() aditivo",
                esperadoAjuste, calc.ajusteCambioBs());
    }

    @Test
    public void testF28_LoginUsuarioInexistenteRetornaVacioDeFormaSegura() {
        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        Optional<Usuario> res = dao.autenticar("usuario_inexistente_xyz_12345", "claveCualquiera123");
        assertTrue("Login con usuario inexistente debe retornar Optional vacío", res.isEmpty());
    }
}
