package Vista;

import Modelo.Platos;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RendererPlatoTest {

    @Test
    public void aplicaColorDeCategoriaYContraste() {
        Map<Integer, Platos> mapa = new HashMap<>();
        Platos pOscuro = new Platos(1, "Asado Negro", new BigDecimal("10.00"), "2026-10-13");
        pOscuro.setCategoriaNombre("Carnes");
        pOscuro.setCategoriaColor("#1B2631"); // Color muy oscuro
        mapa.put(1, pOscuro);

        Platos pClaro = new Platos(2, "Jugo de Naranja", new BigDecimal("3.00"), "2026-10-13");
        pClaro.setCategoriaNombre("Bebidas");
        pClaro.setCategoriaColor("#F9E79F"); // Color muy claro (amarillo pastel)
        mapa.put(2, pClaro);

        DefaultTableModel modelo = new DefaultTableModel(new Object[]{"ID", "Nombre", "Precio"}, 0);
        modelo.addRow(new Object[]{1, "Asado Negro", "$ 10.00"});
        modelo.addRow(new Object[]{2, "Jugo de Naranja", "$ 3.00"});
        JTable tabla = new JTable(modelo);

        RendererPlato renderer = new RendererPlato(mapa);

        // Fila 0: fondo oscuro -> texto blanco
        JLabel c0 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Asado Negro", false, false, 0, 1);
        assertEquals(Color.decode("#1B2631"), c0.getBackground());
        assertEquals(Color.WHITE, c0.getForeground());

        // Fila 1: fondo claro -> texto negro
        JLabel c1 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Jugo de Naranja", false, false, 1, 1);
        assertEquals(Color.decode("#F9E79F"), c1.getBackground());
        assertEquals(Color.BLACK, c1.getForeground());
    }

    @Test
    public void distintivosVisualesYTooltipsSegunFavoritoYRanking() {
        Map<Integer, Platos> mapa = new HashMap<>();
        
        // 1. Favorito y con ranking alto
        Platos pTopYFav = new Platos(1, "Pollo a la Brasa", new BigDecimal("12.00"), "2026-10-13");
        pTopYFav.setFavorito(true);
        pTopYFav.setRankingTotal(new BigDecimal("45"));
        pTopYFav.setCategoriaNombre("Pollos");
        pTopYFav.setCategoriaColor("#E67E22");
        mapa.put(1, pTopYFav);

        // 2. Solo favorito
        Platos pSoloFav = new Platos(2, "Pabellón Criollo", new BigDecimal("9.00"), "2026-10-13");
        pSoloFav.setFavorito(true);
        pSoloFav.setRankingTotal(BigDecimal.ZERO);
        pSoloFav.setCategoriaNombre("Especiales");
        mapa.put(2, pSoloFav);

        // 3. Solo ranking alto
        Platos pSoloRank = new Platos(3, "Empanada Gallega", new BigDecimal("4.00"), "2026-10-13");
        pSoloRank.setFavorito(false);
        pSoloRank.setRankingTotal(new BigDecimal("30"));
        pSoloRank.setCategoriaNombre("Entradas");
        mapa.put(3, pSoloRank);

        // 4. Plato estándar
        Platos pNormal = new Platos(4, "Agua Mineral", new BigDecimal("1.50"), "2026-10-13");
        pNormal.setFavorito(false);
        pNormal.setRankingTotal(BigDecimal.ZERO);
        mapa.put(4, pNormal);

        DefaultTableModel modelo = new DefaultTableModel(new Object[]{"ID", "Nombre", "Precio"}, 0);
        modelo.addRow(new Object[]{1, "Pollo a la Brasa", "$ 12.00"});
        modelo.addRow(new Object[]{2, "Pabellón Criollo", "$ 9.00"});
        modelo.addRow(new Object[]{3, "Empanada Gallega", "$ 4.00"});
        modelo.addRow(new Object[]{4, "Agua Mineral", "$ 1.50"});
        JTable tabla = new JTable(modelo);

        RendererPlato renderer = new RendererPlato(mapa);

        // Caso 1: Favorito y Top
        JLabel l1 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Pollo a la Brasa", false, false, 0, 1);
        assertEquals("★ 🔥 Pollo a la Brasa", l1.getText());
        assertNotNull(l1.getToolTipText());
        assertTrue(l1.getToolTipText().contains("★ Favorito"));
        assertTrue(l1.getToolTipText().contains("🔥 45 pedidos recientes"));
        assertTrue(l1.getToolTipText().contains("Categoría: Pollos"));

        // Caso 2: Solo Favorito
        JLabel l2 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Pabellón Criollo", false, false, 1, 1);
        assertEquals("★ Pabellón Criollo", l2.getText());
        assertTrue(l2.getToolTipText().contains("★ Favorito"));
        assertFalse(l2.getToolTipText().contains("🔥"));

        // Caso 3: Solo Ranking
        JLabel l3 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Empanada Gallega", false, false, 2, 1);
        assertEquals("🔥 Empanada Gallega", l3.getText());
        assertFalse(l3.getToolTipText().contains("★"));
        assertTrue(l3.getToolTipText().contains("🔥 30 pedidos recientes"));

        // Caso 4: Normal
        JLabel l4 = (JLabel) renderer.getTableCellRendererComponent(tabla, "Agua Mineral", false, false, 3, 1);
        assertEquals("Agua Mineral", l4.getText());

        // Columna 0 y 2 no deben tener prefijos
        JLabel cId = (JLabel) renderer.getTableCellRendererComponent(tabla, 1, false, false, 0, 0);
        assertEquals("1", cId.getText());

        JLabel cPrecio = (JLabel) renderer.getTableCellRendererComponent(tabla, "$ 12.00", false, false, 0, 2);
        assertEquals("$ 12.00", cPrecio.getText());
    }

    @Test
    public void respetaSeleccionDeFila() {
        Map<Integer, Platos> mapa = new HashMap<>();
        Platos p = new Platos(1, "Sopa", new BigDecimal("5.00"), "2026-10-13");
        p.setCategoriaColor("#112233");
        mapa.put(1, p);

        DefaultTableModel modelo = new DefaultTableModel(new Object[]{"ID", "Nombre", "Precio"}, 0);
        modelo.addRow(new Object[]{1, "Sopa", "$ 5.00"});
        JTable tabla = new JTable(modelo);

        RendererPlato renderer = new RendererPlato(mapa);
        JLabel c = (JLabel) renderer.getTableCellRendererComponent(tabla, "Sopa", true, false, 0, 1);
        assertEquals(tabla.getSelectionBackground(), c.getBackground());
        assertEquals(tabla.getSelectionForeground(), c.getForeground());
    }
}
