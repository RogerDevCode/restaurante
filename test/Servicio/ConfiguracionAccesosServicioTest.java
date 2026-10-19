package Servicio;

import Modelo.ConfiguracionSistemaDao;
import Modelo.DataAccessException;
import Modelo.Usuario;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfiguracionAccesosServicioTest {

    @Test
    public void matrizVaciaDeniegaTodoAccesoNoAdministrativo() {
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(dao(Map.of()));

        assertTrue(servicio.cargarPermisos(RolesSistema.MESONERO).isEmpty());
        assertTrue(servicio.cargarPermisos(RolesSistema.CAJERO).isEmpty());
    }

    @Test
    public void matrizParcialSoloConcedeClavesExplicitasConValorUno() {
        Map<String, String> valores = new HashMap<>();
        valores.put("seguridad.rol.mesonero.registrar_pedidos", "1");
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(dao(valores));

        Set<PoliticaAcceso.Accion> permisos = servicio.cargarPermisos(RolesSistema.MESONERO);

        assertTrue(permisos.contains(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
        assertFalse(permisos.contains(PoliticaAcceso.Accion.COBRAR_PEDIDOS));
        assertFalse(permisos.contains(PoliticaAcceso.Accion.CONSULTAR_SALAS));
    }

    @Test
    public void administradorConservaTodosLosPermisosAunqueMatrizEsteVacia() {
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(dao(Map.of()));

        assertEquals(Set.of(PoliticaAcceso.Accion.values()), servicio.cargarPermisos(RolesSistema.ADMINISTRADOR));
    }

    @Test(expected = DataAccessException.class)
    public void errorDeLecturaNoDegradaAPermisosPredeterminados() {
        ConfiguracionSistemaDao dao = new ConfiguracionSistemaDao() {
            @Override
            public Map<String, String> obtenerTodosEstrictamente() {
                throw new DataAccessException("fallo de lectura", new IllegalStateException("BD inaccesible"));
            }
        };
        new ConfiguracionAccesosServicio(dao).cargarPermisos(RolesSistema.MESONERO);
    }

    private ConfiguracionSistemaDao dao(Map<String, String> valores) {
        return new ConfiguracionSistemaDao() {
            @Override
            public Map<String, String> obtenerTodosEstrictamente() {
                return Map.copyOf(valores);
            }
        };
    }
}
