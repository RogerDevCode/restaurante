package aceptacion;

import Modelo.Categoria;
import Modelo.CategoriaDao;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import Servicio.CategoriaServicio;
import Servicio.PoliticaAcceso;
import Servicio.RolesSistema;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Suite de aceptación y verificación estricta para el ciclo de vida de Categorías:
 * - Soft-delete (activo = 0)
 * - Imborrabilidad / Inmutabilidad de la categoría "General"
 * - Reasignación atómica de platos asociados hacia "General"
 * - Reactivación (undelete / cambiarEstadoActivo)
 * - Autorización RBAC mediante PoliticaAcceso
 */
public class AceptacionCategoriasSoftDeleteTest {

    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
    private CategoriaDao categoriaDao;
    private CategoriaServicio categoriaServicioAdmin;
    private CategoriaServicio categoriaServicioCocinero;

    private int idCategoriaPrueba = -1;
    private String nombrePlatoPrueba = null;

    @Before
    public void setUp() throws Exception {
        // Asegurar esquema v14 sincronizado (agrega columna activo e inicializa General)
        infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(proveedor);

        categoriaDao = new CategoriaDao(proveedor);

        Usuario admin = new Usuario(1, "Admin Test", "admin@test.com", "pass", RolesSistema.ADMINISTRADOR);
        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        categoriaServicioAdmin = new CategoriaServicio(categoriaDao, rbacAdmin);

        Usuario cocinero = new Usuario(2, "Cocinero Test", "cocinero@test.com", "pass", RolesSistema.COCINERO);
        PoliticaAcceso rbacCocinero = new PoliticaAcceso(cocinero);
        categoriaServicioCocinero = new CategoriaServicio(categoriaDao, rbacCocinero);

        // Asegurar categoría de prueba limpia
        limpiarRegistrosPrueba();
    }

    @After
    public void tearDown() throws Exception {
        limpiarRegistrosPrueba();
    }

    private void limpiarRegistrosPrueba() {
        try (Connection con = proveedor.getConnection();
             Statement stmt = con.createStatement()) {
            // Eliminar plato de prueba si existe
            stmt.executeUpdate("DELETE FROM plato_categoria WHERE nombre_clave IN (SELECT LOWER(TRIM(nombre)) FROM platos WHERE nombre LIKE 'Plato Test Cat %')");
            stmt.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'Plato Test Cat %'");
            // Eliminar categorías de prueba
            stmt.executeUpdate("DELETE FROM categorias WHERE nombre LIKE 'CatPruebaSoftDelete%'");
        } catch (Exception e) {
            // Ignorar en teardown
        }
    }

    @Test
    public void testCategoriaGeneral_EsInmutableEImborrable() {
        Categoria general = categoriaDao.obtenerGeneral();
        assertNotNull("La categoría General debe existir por defecto en el sistema", general);
        assertTrue("isGeneral() debe ser true para la categoría General", general.isGeneral());
        assertTrue("La categoría General debe estar activa", general.isActivo());

        // Intentar eliminar la categoría General directamente por DAO debe ser rechazado
        try {
            categoriaDao.eliminar(general.getId());
            fail("Debe lanzar ErrorAplicacionException al intentar eliminar General");
        } catch (ErrorAplicacionException e) {
            assertTrue("El mensaje debe indicar que General no puede ser eliminada",
                    e.getMessage().toLowerCase().contains("general"));
        }

        // Intentar desactivar General debe ser rechazado
        try {
            categoriaDao.cambiarEstadoActivo(general.getId(), false);
            fail("Debe lanzar ErrorAplicacionException al intentar desactivar General");
        } catch (ErrorAplicacionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("general"));
        }

        // Intentar renombrar General debe ser rechazado por Servicio
        try {
            Categoria mod = new Categoria(general.getId(), "General Modificada", "#123456", 1, true);
            categoriaServicioAdmin.modificar(mod);
            fail("Debe lanzar ErrorAplicacionException al intentar renombrar General");
        } catch (ErrorAplicacionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("general"));
        }

        // Intentar eliminar por Servicio
        try {
            categoriaServicioAdmin.eliminar(general.getId());
            fail("Debe rechazar eliminar General por servicio");
        } catch (ErrorAplicacionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("general"));
        }
    }

    @Test
    public void testRechazoSinPermisos_RBAC() {
        try {
            Categoria cat = new Categoria(0, "CatPruebaSoftDelete_NoAuth", "#ABCDEF", 99, true);
            categoriaServicioCocinero.registrar(cat);
            fail("Debe rechazar registrar categoría sin permiso GESTIONAR_PLATOS");
        } catch (ErrorAplicacionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("rol no permite") ||
                    e.getMessage().toLowerCase().contains("permiso"));
        }
    }

    @Test
    public void testCicloDeVida_SoftDelete_ReasignacionYReactivacion() throws Exception {
        // 1. Crear categoría de prueba
        String nombreCat = "CatPruebaSoftDelete_Venta";
        Categoria nueva = new Categoria(0, nombreCat, "#FE0123", 99, true);
        int idCreado = categoriaServicioAdmin.registrar(nueva);
        assertTrue("El ID retornado debe ser válido", idCreado > 0);
        idCategoriaPrueba = idCreado;

        Categoria catRegistrada = categoriaDao.buscar(idCategoriaPrueba);
        assertNotNull(catRegistrada);
        assertTrue("La nueva categoría debe estar activa", catRegistrada.isActivo());

        // Verificar que aparece en listar() (activas)
        List<Categoria> activasInicial = categoriaDao.listar();
        assertTrue("Debe estar en la lista de activas",
                activasInicial.stream().anyMatch(c -> c.getId() == idCategoriaPrueba));

        // 2. Crear y asociar un plato a la categoría
        nombrePlatoPrueba = "Plato Test Cat " + System.currentTimeMillis();
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO platos (nombre, precio, precio_usd, moneda_base, precio_incluye_iva, aplica_iva, precio_clasificado, activo, fecha) "
                            + "VALUES (?, 10.00, 10.00, 'USD', 0, 0, 1, 1, CURRENT_DATE())")) {
                ps.setString(1, nombrePlatoPrueba);
                ps.executeUpdate();
            }
        }
        categoriaDao.asignarPlato(nombrePlatoPrueba, idCategoriaPrueba);

        // Verificar conteo de platos
        int platosAsociados = categoriaDao.contarPlatosEnCategoria(idCategoriaPrueba);
        assertEquals("Debe tener 1 plato asociado", 1, platosAsociados);

        // 3. Reasignar a General y Desactivar (Soft-Delete)
        Categoria general = categoriaDao.obtenerGeneral();
        assertNotNull(general);

        categoriaServicioAdmin.desactivarConReasignacion(idCategoriaPrueba, general.getId());

        // Verificar que la categoría ahora está inactiva
        Categoria catPostSoftDelete = categoriaDao.buscar(idCategoriaPrueba);
        assertNotNull(catPostSoftDelete);
        assertFalse("La categoría debe estar inactiva (activo = 0)", catPostSoftDelete.isActivo());

        // Verificar que NO aparece en listar() (activas)
        List<Categoria> activasPost = categoriaDao.listar();
        assertFalse("No debe aparecer en listar activas",
                activasPost.stream().anyMatch(c -> c.getId() == idCategoriaPrueba));

        // Verificar que SÍ aparece en listarTodas()
        List<Categoria> todasPost = categoriaDao.listarTodas();
        assertTrue("Debe aparecer en listarTodas",
                todasPost.stream().anyMatch(c -> c.getId() == idCategoriaPrueba && !c.isActivo()));

        // Verificar que el plato fue reasignado a General
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id_categoria FROM plato_categoria WHERE nombre_clave = LOWER(TRIM(?))")) {
            ps.setString(1, nombrePlatoPrueba);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                assertTrue("El plato debe existir en plato_categoria", rs.next());
                assertEquals("El plato debe estar ahora asociado a General",
                        general.getId(), rs.getInt("id_categoria"));
            }
        }

        // La categoría de prueba ya no tiene platos asociados
        assertEquals("La categoría origen no debe tener platos tras la reasignación",
                0, categoriaDao.contarPlatosEnCategoria(idCategoriaPrueba));

        // 4. Reactivación (undelete)
        categoriaServicioAdmin.cambiarEstadoActivo(idCategoriaPrueba, true);

        Categoria catReactivada = categoriaDao.buscar(idCategoriaPrueba);
        assertNotNull(catReactivada);
        assertTrue("La categoría debe volver a estar activa", catReactivada.isActivo());

        List<Categoria> activasReactivadas = categoriaDao.listar();
        assertTrue("Debe volver a aparecer en listar activas",
                activasReactivadas.stream().anyMatch(c -> c.getId() == idCategoriaPrueba));
    }
}
