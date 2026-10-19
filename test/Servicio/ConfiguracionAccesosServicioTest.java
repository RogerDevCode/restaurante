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

    @Test
    public void matrizPredeterminadaSeSiembraSoloSiNoExisteNingunaClave() {
        Map<String, String> valores = new HashMap<>();
        ConfiguracionSistemaDao dao = daoMutable(valores);
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(dao);

        assertTrue(servicio.asegurarMatrizPredeterminada());
        assertFalse(servicio.asegurarMatrizPredeterminada());
        assertEquals(ConfiguracionAccesosServicio.permisosPredeterminados(RolesSistema.MESONERO),
                servicio.cargarPermisos(RolesSistema.MESONERO));
        assertEquals(ConfiguracionAccesosServicio.permisosPredeterminados(RolesSistema.CAJERO),
                servicio.cargarPermisos(RolesSistema.CAJERO));
        assertEquals(ConfiguracionAccesosServicio.permisosPredeterminados(RolesSistema.HOST),
                servicio.cargarPermisos(RolesSistema.HOST));
        assertTrue(servicio.cargarPermisos(RolesSistema.COCINERO).isEmpty());
    }

    @Test
    public void matrizSembradaNoConcedeAccionesSensiblesNiRegistraClavesDeAdministrador() {
        Map<String, String> valores = new HashMap<>();
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(daoMutable(valores));

        servicio.asegurarMatrizPredeterminada();

        Set<PoliticaAcceso.Accion> mesonero = servicio.cargarPermisos(RolesSistema.MESONERO);
        assertTrue(mesonero.contains(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
        assertFalse(mesonero.contains(PoliticaAcceso.Accion.COBRAR_PEDIDOS));
        assertFalse(mesonero.contains(PoliticaAcceso.Accion.GESTIONAR_USUARIOS));
        assertTrue(servicio.cargarPermisos(RolesSistema.CAJERO).contains(PoliticaAcceso.Accion.COBRAR_PEDIDOS));
        assertFalse(servicio.cargarPermisos(RolesSistema.HOST).contains(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS));
        assertTrue(valores.keySet().stream().noneMatch(k -> k.startsWith("seguridad.rol.administrador.")));
    }

    @Test
    public void matrizExistenteAunqueParcialNoSeSobrescribe() {
        Map<String, String> valores = new HashMap<>();
        valores.put("seguridad.rol.mesonero.registrar_pedidos", "1");
        ConfiguracionAccesosServicio servicio = new ConfiguracionAccesosServicio(daoMutable(valores));

        assertFalse(servicio.asegurarMatrizPredeterminada());
        assertEquals(Set.of("seguridad.rol.mesonero.registrar_pedidos"), valores.keySet());
    }

    private ConfiguracionSistemaDao daoMutable(Map<String, String> valores) {
        return new ConfiguracionSistemaDao() {
            @Override
            public Map<String, String> obtenerTodosEstrictamente() {
                return Map.copyOf(valores);
            }

            @Override
            public void guardarVarios(Map<String, String> configuraciones) {
                valores.putAll(configuraciones);
            }
        };
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
