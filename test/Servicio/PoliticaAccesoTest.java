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

    @Test
    public void sesionInactivaODesactivadaDeniegaTodasLasAcciones() {
        java.util.concurrent.atomic.AtomicBoolean activa = new java.util.concurrent.atomic.AtomicBoolean(true);
        PoliticaAcceso politica = new PoliticaAcceso(usuario("Administrador"), activa::get);
        assertTrue(politica.esAdministrador());
        assertTrue(politica.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));

        // Administrador desactiva la cuenta o sesion expira
        activa.set(false);
        assertFalse("Al estar inactiva la sesión debe denegar esAdministrador", politica.esAdministrador());
        for (PoliticaAcceso.Accion accion : PoliticaAcceso.Accion.values()) {
            assertFalse("Al estar inactiva debe denegar " + accion, politica.permite(accion));
        }

        // Si usuario.isActivo() es falso de inicio
        Usuario uInactivo = usuario("Administrador");
        uInactivo.setActivo(false);
        PoliticaAcceso politicaInactiva = new PoliticaAcceso(uInactivo);
        assertFalse(politicaInactiva.esAdministrador());
        assertFalse(politicaInactiva.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
    }

    private Usuario usuario(String rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
