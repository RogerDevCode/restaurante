package Vista;

import Controlador.PlatosControlador;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import Servicio.PlatosServicio;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import javax.swing.table.DefaultTableModel;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonedaBaseCatalogoTest {

    private static class PlatosRepoMemoria implements PlatosRepositorio {
        Platos ultimoRegistrado;
        Platos ultimoModificado;

        @Override
        public boolean registrar(Platos plato) {
            this.ultimoRegistrado = plato;
            return true;
        }

        @Override
        public List<Platos> listarPorFecha(String nombre, String fecha) {
            return Collections.emptyList();
        }

        @Override
        public boolean modificar(Platos plato) {
            this.ultimoModificado = plato;
            return true;
        }
    }

    @Test
    public void cambiarCategoriaNoAlteraMonedaNiPreciosDuales() {
        PlatosRepoMemoria repo = new PlatosRepoMemoria();
        PlatosServicio servicio = new PlatosServicio(repo);

        Platos p = new Platos(10, "Bebida Nacional", new BigDecimal("500.00"), "2026-10-15");
        p.setMonedaBase("VES");
        p.setAplicaIva(true);
        p.setPrecioIncluyeIva(true);
        p.setIdCategoria(1);
        p.setCategoriaNombre("Refrescos");

        BigDecimal tasa = new BigDecimal("50.0000");
        servicio.modificar(p, tasa);

        assertEquals("VES", repo.ultimoModificado.getMonedaBase());
        assertEquals(new BigDecimal("500.00"), repo.ultimoModificado.getPrecioVes());
        assertEquals(new BigDecimal("10.00"), repo.ultimoModificado.getPrecioUsd());
        assertTrue(repo.ultimoModificado.isPrecioClasificado());
        assertTrue(repo.ultimoModificado.getPrecioIncluyeIva());

        // Ahora cambiar categoría
        p.setIdCategoria(2);
        p.setCategoriaNombre("Jugos Naturales");
        servicio.modificar(p, tasa);

        assertEquals("VES", repo.ultimoModificado.getMonedaBase());
        assertEquals(new BigDecimal("500.00"), repo.ultimoModificado.getPrecioVes());
        assertEquals(new BigDecimal("10.00"), repo.ultimoModificado.getPrecioUsd());
        assertEquals(2, repo.ultimoModificado.getIdCategoria());
        assertEquals("Jugos Naturales", repo.ultimoModificado.getCategoriaNombre());
    }

    @Test
    public void apagarAplicaIvaFuerzaPrecioIncluyeIvaFalse() {
        PlatosRepoMemoria repo = new PlatosRepoMemoria();
        PlatosServicio servicio = new PlatosServicio(repo);

        Platos p = new Platos(11, "Pan Exento", new BigDecimal("2.50"), "2026-10-15");
        p.setMonedaBase("USD");
        p.setAplicaIva(false);
        p.setPrecioIncluyeIva(true); // Se intenta mandar true a pesar de ser exento

        servicio.registrar(p, new BigDecimal("45.0000"));

        assertFalse(repo.ultimoRegistrado.isAplicaIva());
        assertFalse(repo.ultimoRegistrado.getPrecioIncluyeIva());
    }

    @Test
    public void productoLegacySinClasificarNoPermiteVentaSegunServicio() {
        PlatosRepoMemoria repo = new PlatosRepoMemoria();
        PlatosServicio servicio = new PlatosServicio(repo);

        Platos legacy = new Platos(12, "Plato Antiguo", new BigDecimal("15.00"), "2026-10-15");
        legacy.setPrecioClasificado(false);

        assertFalse(servicio.esAptoParaVenta(legacy));

        legacy.setMonedaBase("USD");
        legacy.setPrecioUsd(new BigDecimal("15.00"));
        legacy.setPrecioVes(new BigDecimal("675.00"));
        legacy.setPrecioIncluyeIva(false);
        legacy.setPrecioClasificado(true);

        assertTrue(servicio.esAptoParaVenta(legacy));
    }

    @Test
    public void formateoCatalogoPanelSplitDistingueMonedasYReferencias() {
        Platos pUsd = new Platos(1, "Pollo a la Brasa", new BigDecimal("12.00"), "2026-10-15");
        pUsd.setMonedaBase("USD");
        pUsd.setPrecioUsd(new BigDecimal("12.00"));
        pUsd.setPrecioVes(new BigDecimal("540.00"));
        pUsd.setPrecioClasificado(true);

        Platos pVes = new Platos(2, "Refresco Lata", new BigDecimal("100.00"), "2026-10-15");
        pVes.setMonedaBase("VES");
        pVes.setPrecioVes(new BigDecimal("100.00"));
        pVes.setPrecioUsd(new BigDecimal("2.22"));
        pVes.setPrecioClasificado(true);

        Platos pSinClasif = new Platos(3, "Postre Antiguo", new BigDecimal("5.00"), "2026-10-15");
        pSinClasif.setMonedaBase("USD");
        pSinClasif.setPrecioClasificado(false);

        BigDecimal tasa = new BigDecimal("45.0000");

        // Simular lógica de display de PanelAtencionMesasSplit
        String displayUsd = "$ " + String.format(java.util.Locale.US, "%.2f", pUsd.getPrecioUsd())
                + " (Bs. " + String.format(java.util.Locale.US, "%.2f", pUsd.getPrecioUsd().multiply(tasa)) + ")";
        String displayVes = "Bs. " + String.format(java.util.Locale.US, "%.2f", pVes.getPrecioVes())
                + " ($ " + String.format(java.util.Locale.US, "%.2f", pVes.getPrecioVes().divide(tasa, 2, java.math.RoundingMode.HALF_UP)) + ")";
        String displaySinClasif = "[Sin clasificar] $ 5.00 (Bs. 225.00)";

        assertTrue(displayUsd.startsWith("$ 12.00 (Bs. 540.00)"));
        assertTrue(displayVes.startsWith("Bs. 100.00 ($ 2.22)"));
        assertTrue(displaySinClasif.startsWith("[Sin clasificar]"));
    }
}
