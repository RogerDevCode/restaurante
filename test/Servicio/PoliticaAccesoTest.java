package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PoliticaAccesoTest {
    @Test
    public void administradorPuedeGestionarTodasLasAreas() {
        PoliticaAcceso politica = new PoliticaAcceso(usuario("Administrador"));
        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            assertTrue("Debe permitir " + accion, politica.permite(accion));
        }
        assertTrue(politica.esAdministrador());
    }

    @Test
    public void asistenteSoloPuedeConsultarSalasYRegistrarPedidos() {
        PoliticaAcceso politica = new PoliticaAcceso(usuario("Asistente"));
        assertTrue(politica.permite(PoliticaAcceso.Accion.CONSULTAR_SALAS));
        assertTrue(politica.permite(PoliticaAcceso.Accion.CONSULTAR_PLATOS));
        assertTrue(politica.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
        assertFalse(politica.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS));
        assertFalse(politica.permite(PoliticaAcceso.Accion.GESTIONAR_SALAS));
        assertFalse(politica.permite(PoliticaAcceso.Accion.GESTIONAR_PLATOS));
        assertFalse(politica.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
        assertFalse(politica.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION));
        assertFalse(politica.esAdministrador());
    }

    @Test
    public void rechazaSesionSinRolORolNoReconocido() {
        assertThrows(ErrorAplicacionException.class, () -> new PoliticaAcceso(null));
        assertThrows(ErrorAplicacionException.class, () -> new PoliticaAcceso(usuario("Otro")));
    }

    private Usuario usuario(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
