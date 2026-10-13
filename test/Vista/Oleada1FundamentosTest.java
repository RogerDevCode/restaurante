package Vista;

import Controlador.CategoriaControlador;
import Controlador.FavoritoControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Oleada 1: C3 (cargas sin bloquear el EDT) y A1 (Enter usa la fila seleccionada).
 */
public class Oleada1FundamentosTest {

    private static final long RETARDO_MS = 2000;
    private static final long UMBRAL_RAPIDO_MS = 1200;

    private Sistema sistema;
    private Path tempDir;

    private static class SalasRepositorioFalso implements Modelo.SalasRepositorio {
        public List<Salas> salas = new ArrayList<>();
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sl) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sl) { return false; }
    }

    private static class PlatosRepositorioFalso implements Modelo.PlatosRepositorio {
        public List<Platos> platos = new ArrayList<>();
        @Override public List<Platos> listarPorFecha(String f1, String f2) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    private static class CategoriaRepositorioLento implements Modelo.CategoriaRepositorio {
        final AtomicReference<String> hilo = new AtomicReference<>();
        @Override public List<Categoria> listar() {
            hilo.set(Thread.currentThread().getName());
            dormir();
            return new ArrayList<>();
        }
        @Override public int registrar(Categoria c) { return 0; }
        @Override public boolean modificar(Categoria c) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public Categoria buscar(int id) { return null; }
        @Override public boolean asignarPlato(String n, int id) { return false; }
        @Override public boolean quitarAsignacionPlato(String n) { return false; }
        @Override public boolean categoriaEnUso(int id) { return false; }
    }

    private static void dormir() {
        try {
            Thread.sleep(RETARDO_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Sistema construir(LoginDao loginDao, CategoriaRepositorioLento catRepo) throws Exception {
        tempDir = Files.createTempDirectory("oleada1-");
        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");

        SalasRepositorioFalso salasRepo = new SalasRepositorioFalso();
        salasRepo.salas.add(new Salas(1, "Principal", 1));
        PlatosRepositorioFalso platosRepo = new PlatosRepositorioFalso();
        platosRepo.platos.add(new Platos(1, "Plato Uno", new BigDecimal("10.00"), LocalDate.now().toString()));

        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso() {
            @Override public Map<Integer, Integer> contarMesasOcupadasPorSala() { return new HashMap<>(); }
        };

        PoliticaAcceso rbac = new PoliticaAcceso(admin);
        CategoriaControlador catCtrl = new CategoriaControlador(new CategoriaServicio(catRepo, rbac));
        PedidoPdfServicio pdf = new PedidoPdfServicio(pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle,
                () -> new Config(), new GeneradorPdfPedido(tempDir), f -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(new PedidoServicio(pedidosRepo), pdf, rbac,
                new ConsultaPedidosServicio(pedidosRepo, rbac));

        final Sistema[] caja = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> caja[0] = new Sistema(admin,
                new SalasControlador(new SalasServicio(salasRepo, rbac)),
                new PlatosControlador(new PlatosServicio(platosRepo, rbac)),
                pedidosCtrl, catCtrl, null, loginDao));
        return caja[0];
    }

    private static Object invocarEnEdt(Sistema s, String metodo) throws Exception {
        Method m = Sistema.class.getDeclaredMethod(metodo);
        m.setAccessible(true);
        final Object[] r = new Object[1];
        final Throwable[] err = new Throwable[1];
        long ini = System.currentTimeMillis();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r[0] = m.invoke(s);
            } catch (Throwable t) {
                err[0] = t;
            }
        });
        long ms = System.currentTimeMillis() - ini;
        if (err[0] != null) {
            throw new AssertionError("Fallo invocando " + metodo, err[0]);
        }
        assertTrue(metodo + " debe retornar sin bloquear al EDT (tardo " + ms + " ms)",
                ms < UMBRAL_RAPIDO_MS);
        return r[0];
    }

    @After
    public void tearDown() throws Exception {
        if (sistema != null) {
            final Sistema s = sistema;
            sistema = null;
            SwingUtilities.invokeAndWait(s::dispose);
        }
    }

    @Test
    public void testC3ListarUsuariosNoBloqueaEdt() throws Exception {
        final AtomicReference<String> hilo = new AtomicReference<>();
        LoginDao lento = new LoginDao() {
            @Override public List<Usuario> ListarUsuarios() {
                hilo.set(Thread.currentThread().getName());
                dormir();
                List<Usuario> r = new ArrayList<>();
                r.add(new Usuario(2, "Cajero IT", "caja@it.test", "x", "Cajero"));
                return r;
            }
        };
        sistema = construir(lento, new CategoriaRepositorioLento());
        invocarEnEdt(sistema, "ListarUsuarios");
        long fin = System.currentTimeMillis() + 8000;
        while (hilo.get() == null && System.currentTimeMillis() < fin) {
            Thread.sleep(100);
        }
        assertNotNull("El worker debio ejecutar la consulta", hilo.get());
        assertFalse("La consulta debe correr fuera del EDT (hilo=" + hilo.get() + ")",
                hilo.get().startsWith("AWT-EventQueue"));
        // Finalizacion en EDT: la fila debe aparecer en la tabla.
        final int[] filas = {-1};
        fin = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < fin) {
            final int[] f = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    java.lang.reflect.Field ft = Sistema.class.getDeclaredField("TableUsuarios");
                    ft.setAccessible(true);
                    f[0] = ((JTable) ft.get(sistema)).getRowCount();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            filas[0] = f[0];
            if (f[0] == 1) break;
            Thread.sleep(100);
        }
        assertEquals("La tabla debe mostrar la fila cargada en fondo", 1, filas[0]);
    }

    @Test
    public void testC3ListarConfigNoBloqueaEdt() throws Exception {
        final AtomicReference<String> hilo = new AtomicReference<>();
        LoginDao lento = new LoginDao() {
            @Override public Config datosEmpresa() {
                hilo.set(Thread.currentThread().getName());
                dormir();
                return null;
            }
        };
        sistema = construir(lento, new CategoriaRepositorioLento());
        invocarEnEdt(sistema, "ListarConfig");
        long fin = System.currentTimeMillis() + 8000;
        while (hilo.get() == null && System.currentTimeMillis() < fin) {
            Thread.sleep(100);
        }
        assertNotNull("El worker debio ejecutar la consulta", hilo.get());
        assertFalse("La consulta debe correr fuera del EDT (hilo=" + hilo.get() + ")",
                hilo.get().startsWith("AWT-EventQueue"));
        // Finalizacion en EDT: con Config nulo se aplican los valores por defecto.
        final String[] idTxt = {null};
        fin = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < fin) {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    java.lang.reflect.Field fi = Sistema.class.getDeclaredField("txtIdConfig");
                    fi.setAccessible(true);
                    idTxt[0] = ((JTextField) fi.get(sistema)).getText();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            if ("1".equals(idTxt[0])) break;
            Thread.sleep(100);
        }
        assertEquals("Debe aplicarse la configuracion por defecto", "1", idTxt[0]);
    }

    @Test
    public void testC3CargarCategoriasNoBloqueaEdt() throws Exception {
        CategoriaRepositorioLento catRepo = new CategoriaRepositorioLento();
        sistema = construir(new LoginDao(), catRepo);
        invocarEnEdt(sistema, "cargarCategorias");
        long fin = System.currentTimeMillis() + 8000;
        while (catRepo.hilo.get() == null && System.currentTimeMillis() < fin) {
            Thread.sleep(100);
        }
        assertNotNull("El worker debio ejecutar la consulta", catRepo.hilo.get());
        assertFalse("La consulta debe correr fuera del EDT (hilo=" + catRepo.hilo.get() + ")",
                catRepo.hilo.get().startsWith("AWT-EventQueue"));
    }

    @Test
    public void testPreseleccionCarritoAlAgregar() throws Exception {
        sistema = construir(new LoginDao(), new CategoriaRepositorioLento());
        List<Platos> lista = new ArrayList<>();
        lista.add(new Platos(1, "Arepa", new BigDecimal("5.00"), LocalDate.now().toString()));
        lista.add(new Platos(2, "Cachapa", new BigDecimal("7.00"), LocalDate.now().toString()));
        Method mostrar = Sistema.class.getDeclaredMethod("mostrarPlatosEnTabla", JTable.class, List.class);
        mostrar.setAccessible(true);
        java.lang.reflect.Field fTbl = Sistema.class.getDeclaredField("tblTemPlatos");
        fTbl.setAccessible(true);
        java.lang.reflect.Field fMenu = Sistema.class.getDeclaredField("tableMenu");
        fMenu.setAccessible(true);
        java.lang.reflect.Field fAdd = Sistema.class.getDeclaredField("btnAddPlato");
        fAdd.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable tbl = (JTable) fTbl.get(sistema);
                JTable menu = (JTable) fMenu.get(sistema);
                JButton add = (JButton) fAdd.get(sistema);
                mostrar.invoke(sistema, tbl, lista);
                tbl.setRowSelectionInterval(1, 1);
                add.doClick();
                assertEquals("El carrito debe tener la fila agregada", 1, menu.getRowCount());
                assertEquals("La fila agregada debe quedar preseleccionada", 0, menu.getSelectedRow());
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        });
    }

    @Test
    public void testA1SeleccionSobreviveAlRefresco() throws Exception {
        sistema = construir(new LoginDao(), new CategoriaRepositorioLento());
        List<Platos> lista = new ArrayList<>();
        lista.add(new Platos(1, "Arepa", new BigDecimal("5.00"), LocalDate.now().toString()));
        lista.add(new Platos(2, "Cachapa", new BigDecimal("7.00"), LocalDate.now().toString()));
        Method mostrar = Sistema.class.getDeclaredMethod("mostrarPlatosEnTabla", JTable.class, List.class);
        mostrar.setAccessible(true);
        java.lang.reflect.Field fTbl = Sistema.class.getDeclaredField("tblTemPlatos");
        fTbl.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            try {
                JTable tbl = (JTable) fTbl.get(sistema);
                mostrar.invoke(sistema, tbl, lista);
                tbl.setRowSelectionInterval(1, 1);
                // Simula el refresco del filtrado con la misma lista.
                mostrar.invoke(sistema, tbl, new ArrayList<>(lista));
                int sel = tbl.getSelectedRow();
                assertTrue("La seleccion debe conservarse tras el refresco", sel >= 0);
                assertEquals("Debe seguir seleccionado el mismo plato",
                        2, ((Number) tbl.getValueAt(sel, 0)).intValue());
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        });
    }
}
