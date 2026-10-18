package aceptacion;

import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.DetallePedido;
import Servicio.CalculoMonetarioPedido;
import infraestructura.MigradorEsquemaJdbc;
import org.junit.Test;

import javax.swing.table.DefaultTableModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Suite de aceptación para verificar la solución definitiva de los fallos F-29 a F-36.
 */
public class AceptacionFallosF29aF36Test {

    @Test
    public void testF31_PlatosDaoNoFiltraPorFecha() throws Exception {
        // Verificar que en PlatosDao.java ya no existe el filtro restrictivo de fecha "WHERE p.fecha = ?"
        Path path = Path.of("src/Modelo/PlatosDao.java");
        String contenido = Files.readString(path);
        assertFalse("PlatosDao no debe contener 'p.fecha = ?'", contenido.contains("p.fecha = ?"));
        assertFalse("PlatosDao no debe invocar asegurarCatalogoPlatosVigente en el constructor",
                contenido.contains("public PlatosDao() {\n        asegurarCatalogoPlatosVigente();"));
    }

    @Test
    public void testF33_MigradorEsquemaEsVersion13() {
        assertTrue("La versión de esquema debe ser al menos 13 tras incorporar unicidad de Cierre Z",
                MigradorEsquemaJdbc.versionEsquemaActual() >= 13);
    }

    @Test
    public void testF34_PanelSplitMantieneFallbackVESExentoEnLineasHuerfanas() throws Exception {
        Path path = Path.of("src/Vista/PanelAtencionMesasSplit.java");
        String contenido = Files.readString(path);
        // Debe usar fallback a VES y aplicaIva false ante línea no clasificada
        assertTrue("PanelAtencionMesasSplit debe asignar VES a líneas huérfanas",
                contenido.contains("platoDummy.setMonedaBase(d.getMonedaBase() != null && !d.getMonedaBase().isBlank() ? d.getMonedaBase() : \"VES\");"));
        assertTrue("PanelAtencionMesasSplit debe asignar false a aplicaIva en líneas huérfanas",
                contenido.contains("platoDummy.setAplicaIva(d.getAplicaIva() != null ? d.getAplicaIva() : false);"));
    }

    @Test
    public void testF35_CalculoSubtotalSinIvaEnTableFinalizar() {
        // Simular 2 unidades de plato $10.00 con 16% IVA no incluido
        DetallePedido det = new DetallePedido();
        det.setCantidad(2);
        det.setPrecioDecimal(new BigDecimal("10.00"));
        det.setMonedaBase("USD");
        det.setAplicaIva(true);
        det.setPrecioIncluyeIva(false);
        det.setIvaPorcentaje(new BigDecimal("16.00"));

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(
                List.of(det), new BigDecimal("36.5000"));
        CalculoMonetarioPedido.CalculoLinea lin = calc.lineas().get(0);

        // Subtotal neto debe ser 20.00, mientras que el total con IVA es 23.20
        BigDecimal subNetoFuente = lin.baseImponibleFuente().add(lin.exentoFuente());
        assertEquals("El subtotal neto debe ser 20.00 USD (sin IVA)",
                new BigDecimal("20.00"), subNetoFuente);
        assertEquals("El total con IVA de la línea es 23.20 USD",
                new BigDecimal("23.20"), lin.totalFuente());
    }

    @Test
    public void testF36_CierreCajaDaoSqlPendientesUsaCoalesceCerradaEn() throws Exception {
        Path path = Path.of("src/Modelo/CierreCajaDao.java");
        String contenido = Files.readString(path);
        assertTrue("CierreCajaDao debe usar COALESCE(cerrada_en, fecha) en sqlPendientes",
                contenido.contains("WHERE estado = 'PENDIENTE'\n                      AND (COALESCE(cerrada_en, fecha) LIKE ? OR DATE(COALESCE(cerrada_en, fecha)) = ?)"));
    }

    @Test
    public void testF29yF30_BdRestauranteSanitizada() throws Exception {
        Path path = Path.of("BD RESTAURANTE 2026.sql");
        String contenido = Files.readString(path);
        // Debe contener las categorías 1 a 15
        assertTrue("Debe incluir categoría 10", contenido.contains("(10,"));
        assertTrue("Debe incluir categoría 15", contenido.contains("(15,"));
        // No debe contener AUTO_INCREMENT estático en pedidos ni cierres_caja
        assertFalse("No debe tener AUTO_INCREMENT estático en pedidos",
                contenido.contains("CREATE TABLE `pedidos` (\n  `id` int(11) NOT NULL AUTO_INCREMENT,\n  `id_sala`")
                && contenido.contains("AUTO_INCREMENT=2"));
        // Cierres de caja debe tener fecha_jornada_z y restricción UNIQUE
        assertTrue("Debe contener fecha_jornada_z en cierres_caja",
                contenido.contains("`fecha_jornada_z` date GENERATED ALWAYS AS (CASE WHEN `tipo` = 'TOTAL' THEN `fecha_jornada` ELSE NULL END) STORED"));
        assertTrue("Debe contener UNIQUE KEY uq_cierres_caja_z_fecha",
                contenido.contains("UNIQUE KEY `uq_cierres_caja_z_fecha` (`fecha_jornada_z`)"));
    }
}
