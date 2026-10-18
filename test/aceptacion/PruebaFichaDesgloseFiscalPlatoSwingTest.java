package aceptacion;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import Vista.Sistema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.math.BigDecimal;

import static org.junit.Assert.*;

/**
 * Prueba del Panel de Desglose Fiscal en Vivo en el TAB de Platos (Ficha de Producto).
 * Valida que mientras el usuario escribe en txtPrecioPlato y conmuta los checkboxes
 * de IVA, el panel refleje en vivo la Base Imponible, IVA y Precio Final que pagará el cliente.
 */
public class PruebaFichaDesgloseFiscalPlatoSwingTest {

    private Sistema sistema;

    @Before
    public void setUp() throws Exception {
        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(admin);
        LoginDao empresaDao = new LoginDao();

        // Asegurar que la tasa de cambio sea 44.80 y el IVA 16.00%
        Config cfg = empresaDao.datosEmpresa();
        if (cfg == null) {
            cfg = new Config();
        }
        cfg.setTasaDolar(new BigDecimal("44.8000"));
        cfg.setIvaPorcentaje(new BigDecimal("16.00"));
        empresaDao.ModificarDatos(cfg);

        SalasDao salasDao = new SalasDao();
        PlatosDao platosDao = new PlatosDao();
        PedidosDao pedidosDao = new PedidosDao();

        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosDao::verPedido,
                id -> pedidosDao.verPedidoDetalle(id),
                empresaDao::datosEmpresa,
                GeneradorPdfPedido.conEstructuraMensual(GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto()),
                PedidoPdfServicio.AbridorPdf.porDefecto());
        Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                new Modelo.CierreCajaDao(), empresaDao::datosEmpresa, new Servicio.GeneradorPdfCierre());
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosDao), pdfServicio, politica,
                new ConsultaPedidosServicio(pedidosDao, politica), cierreServicio);

        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politica));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasDao, politica));
        CategoriaControlador catCtrl = new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica));
        FavoritoControlador favCtrl = new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica));

        SwingUtilities.invokeAndWait(() -> {
            sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, catCtrl, favCtrl, empresaDao);
            sistema.setVisible(true);
        });
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(() -> sistema.dispose());
        }
    }

    @Test
    public void testDesgloseFiscalEnVivo_PlatoConIvaIncluido() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Seleccionar USD
            sistema.getCbMonedaBasePlato().setSelectedItem("USD");
            // Marcar Genera IVA y Marcar Incluye IVA
            sistema.getChkAplicaIvaPlato().setSelected(true);
            sistema.getChkPrecioIncluyeIvaPlato().setSelected(true);

            // Escribir precio final $11.60
            sistema.getTxtPrecioPlato().setText("11.60");
            sistema.actualizarDesgloseFiscalFichaPlato();

            // Verificar etiquetas del nuevo panel
            JLabel lblPrecioFinal = sistema.getLblFichaPrecioFinal();
            JLabel lblBase = sistema.getLblFichaBaseImponible();
            JLabel lblIva = sistema.getLblFichaIvaMonto();

            assertNotNull("Label precio final no debe ser nulo", lblPrecioFinal);
            assertNotNull("Label base no debe ser nulo", lblBase);
            assertNotNull("Label IVA no debe ser nulo", lblIva);

            // Precio final debe ser 11.60
            assertTrue("Debe mostrar precio al cliente $ 11.60", lblPrecioFinal.getText().contains("11.60"));
            // Base Imponible debe desglosarse a 10.00
            assertTrue("Debe mostrar base neta $ 10.00", lblBase.getText().contains("10.00"));
            // IVA debe desglosarse a 1.60
            assertTrue("Debe mostrar IVA $ 1.60", lblIva.getText().contains("1.60"));
        });
    }

    @Test
    public void testDesgloseFiscalEnVivo_PlatoConIvaExcluido() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Seleccionar USD
            sistema.getCbMonedaBasePlato().setSelectedItem("USD");
            // Marcar Genera IVA pero DESMARCAR Incluye IVA
            sistema.getChkAplicaIvaPlato().setSelected(true);
            sistema.getChkPrecioIncluyeIvaPlato().setSelected(false);

            // Escribir precio base $10.00
            sistema.getTxtPrecioPlato().setText("10.00");
            sistema.actualizarDesgloseFiscalFichaPlato();

            JLabel lblPrecioFinal = sistema.getLblFichaPrecioFinal();
            JLabel lblBase = sistema.getLblFichaBaseImponible();
            JLabel lblIva = sistema.getLblFichaIvaMonto();

            // Precio final que pagará el cliente debe ser 11.60
            assertTrue("Debe mostrar que el cliente pagará $ 11.60", lblPrecioFinal.getText().contains("11.60"));
            // Base Imponible es el monto ingresado $10.00
            assertTrue("Base neta debe ser $ 10.00", lblBase.getText().contains("10.00"));
            // IVA que se sumará es 1.60
            assertTrue("IVA a agregar debe ser $ 1.60", lblIva.getText().contains("1.60"));
        });
    }

    @Test
    public void testDesgloseFiscalEnVivo_PlatoExento() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Desmarcar Genera IVA (Exento)
            sistema.getChkAplicaIvaPlato().setSelected(false);

            // Escribir precio $10.00
            sistema.getTxtPrecioPlato().setText("10.00");
            sistema.actualizarDesgloseFiscalFichaPlato();

            JLabel lblPrecioFinal = sistema.getLblFichaPrecioFinal();
            JLabel lblBase = sistema.getLblFichaBaseImponible();
            JLabel lblIva = sistema.getLblFichaIvaMonto();

            assertTrue("Precio final exento debe ser $ 10.00", lblPrecioFinal.getText().contains("10.00"));
            assertTrue("Condición fiscal debe decir EXENTO", lblBase.getText().contains("EXENTO"));
            assertTrue("IVA discriminado debe ser 0.00", lblIva.getText().contains("0.00"));
        });
    }

    @Test
    public void testDesgloseFiscalEnVivo_MonedaBolivares() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Seleccionar VES
            sistema.getCbMonedaBasePlato().setSelectedItem("VES");
            sistema.getChkAplicaIvaPlato().setSelected(true);
            sistema.getChkPrecioIncluyeIvaPlato().setSelected(true);

            // Escribir Bs. 519.68 (equivale a $11.60 a tasa 44.80)
            sistema.getTxtPrecioPlato().setText("519.68");
            sistema.actualizarDesgloseFiscalFichaPlato();

            JLabel lblPrecioFinal = sistema.getLblFichaPrecioFinal();
            JLabel lblBase = sistema.getLblFichaBaseImponible();
            JLabel lblIva = sistema.getLblFichaIvaMonto();

            assertTrue("Precio final debe mostrar Bs. 519.68", lblPrecioFinal.getText().contains("519.68"));
            assertTrue("Base neta en Bs debe ser Bs. 448.00", lblBase.getText().contains("448.00"));
            assertTrue("IVA en Bs debe ser Bs. 71.68", lblIva.getText().contains("71.68"));
        });
    }
}
