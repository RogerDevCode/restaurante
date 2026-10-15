package infraestructura;

import Modelo.Usuario;
import Servicio.PoliticaAcceso;
import org.junit.Assert;
import org.junit.Test;

/** Pruebas aisladas de política: no conectan a la BD ni exportan datos locales. */
public class MitigacionHallazgosV2Test {

    @Test
    public void usuariosAdministradorYAsistenteSeDenieganCuandoNoSePuedeValidarSesion() {
        Usuario administrador = new Usuario();
        administrador.setId(1);
        administrador.setRol("Administrador");
        administrador.setActivo(true);

        Usuario asistente = new Usuario();
        asistente.setId(2);
        asistente.setRol("Asistente");
        asistente.setActivo(true);

        PoliticaAcceso politicaAdmin = new PoliticaAcceso(administrador, () -> false);
        PoliticaAcceso politicaAsistente = new PoliticaAcceso(asistente, () -> false);

        Assert.assertFalse(politicaAdmin.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
        Assert.assertFalse(politicaAsistente.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
    }
}
