package Modelo;

import Servicio.PoliticaAcceso;
import Servicio.ResultadoOperacion;
import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Test;

public class Java21RecordPatternMatchingTest {

    @Test
    public void testCotizacionMonedaRecordValida() {
        CotizacionMonedaRecord cotizacion = new CotizacionMonedaRecord(
                new BigDecimal("15.50"),
                new BigDecimal("565.75"),
                new BigDecimal("36.5000")
        );

        Assert.assertEquals(new BigDecimal("15.50"), cotizacion.totalUsd());
        Assert.assertEquals(new BigDecimal("565.75"), cotizacion.totalBs());
        Assert.assertEquals(new BigDecimal("36.5000"), cotizacion.tasaCambio());
        Assert.assertTrue(cotizacion.textoBimonetario().contains("Bs. 565.75 ($ 15.50)"));
    }

    @Test(expected = ErrorAplicacionException.class)
    public void testCotizacionMonedaRecordRechazaNegativos() {
        new CotizacionMonedaRecord(
                new BigDecimal("-5.00"),
                new BigDecimal("100.00"),
                new BigDecimal("36.5000")
        );
    }

    @Test(expected = ErrorAplicacionException.class)
    public void testCotizacionMonedaRecordRechazaTasaCero() {
        new CotizacionMonedaRecord(
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                BigDecimal.ZERO
        );
    }


    @Test
    public void testResultadoOperacionSealedInterfacePatternMatching() {
        ResultadoOperacion<String> exito = ResultadoOperacion.exito("ID_PEDIDO_99", "Pedido guardado");
        ResultadoOperacion<String> fallo = ResultadoOperacion.fallo("Error de conexión", new RuntimeException("DB down"));

        Assert.assertTrue(exito.esExitoso());
        Assert.assertFalse(fallo.esExitoso());

        String msgExito = switch (exito) {
            case ResultadoOperacion.Exito(var datos, var mensaje) -> "Exito: " + datos + " - " + mensaje;
            case ResultadoOperacion.Fallo(var error, var causa) -> "Fallo: " + error;
        };
        Assert.assertEquals("Exito: ID_PEDIDO_99 - Pedido guardado", msgExito);

        String msgFallo = switch (fallo) {
            case ResultadoOperacion.Exito(var datos, var mensaje) -> "Exito: " + datos;
            case ResultadoOperacion.Fallo(var error, var causa) -> "Fallo: " + error;
        };
        Assert.assertEquals("Fallo: Error de conexión", msgFallo);
    }

    @Test
    public void testPoliticaAccesoSwitchExpressions() {
        Usuario admin = new Usuario();
        admin.setRol("Administrador");
        PoliticaAcceso politicaAdmin = new PoliticaAcceso(admin);

        Usuario mesero = new Usuario();
        mesero.setRol("Asistente");
        PoliticaAcceso politicaMesero = new PoliticaAcceso(mesero);

        // Administrador tiene acceso a todas las acciones
        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            Assert.assertTrue(politicaAdmin.permite(accion));
        }

        // Asistente solo a consulta y registro de pedidos
        Assert.assertTrue(politicaMesero.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS));
        Assert.assertTrue(politicaMesero.permite(PoliticaAcceso.Accion.CONSULTAR_PLATOS));
        Assert.assertTrue(politicaMesero.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));

        Assert.assertFalse(politicaMesero.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS));
        Assert.assertFalse(politicaMesero.permite(PoliticaAcceso.Accion.GESTIONAR_PLATOS));
        Assert.assertFalse(politicaMesero.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
        Assert.assertFalse(politicaMesero.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION));
    }
}
