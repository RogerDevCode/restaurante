package aceptacion;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import Vista.Sistema;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * Suite de aceptación exhaustiva para la unificación del botón guardar/editar inteligente (Opción 2),
 * atajo Enter exclusivo en actualización de precio de platos existentes (Opción 3) y botón Limpiar Campos por grupo de inputs.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AceptacionGuardadoPlatosInteligenteTest {

    private static Sistema sistema;
    private static PlatosDao platosDao;

    @BeforeClass
    public static void setUp() throws Exception {
        Usuario admin = new Usuario(1, "xavier", "admin", "admin", "Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(admin);
        PedidosDao pedidosDao = new PedidosDao();
        LoginDao empresaDao = new LoginDao();
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

        platosDao = new PlatosDao();
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosDao, politica));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasDao(), politica));
        CategoriaControlador catCtrl = new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica));
        FavoritoControlador favCtrl = new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica));

        SwingUtilities.invokeAndWait(() -> {
            sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, catCtrl, favCtrl, empresaDao);
        });
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (sistema != null) {
            SwingUtilities.invokeAndWait(sistema::dispose);
        }
    }

    private static <T> T enEdt(Callable<T> callable) throws Exception {
        final Object[] res = new Object[1];
        final Throwable[] err = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                res[0] = callable.call();
            } catch (Throwable t) {
                err[0] = t;
            }
        });
        if (err[0] != null) {
            throw new RuntimeException(err[0]);
        }
        @SuppressWarnings("unchecked")
        T t = (T) res[0];
        return t;
    }

    private static void enEdtV(Runnable runnable) throws Exception {
        enEdt(() -> {
            runnable.run();
            return null;
        });
    }

    private int asegurarPlatoPrueba(String nombre, String precio) throws Exception {
        Optional<Platos> pOpt = platosDao.buscarActivoPorNombre(nombre);
        if (pOpt.isPresent()) {
            return pOpt.get().getId();
        }
        Platos p = new Platos();
        p.setNombre(nombre);
        p.setPrecioDecimal(new BigDecimal(precio));
        p.setFecha("2026-10-17");
        p.setMonedaBase("USD");
        p.setAplicaIva(true);
        p.setPrecioIncluyeIva(false);
        platosDao.registrar(p);
        Optional<Platos> nuevo = platosDao.buscarActivoPorNombre(nombre);
        assertTrue(nuevo.isPresent());
        return nuevo.get().getId();
    }

    @Test
    public void test01_FormularioLimpioModoGuardar() throws Exception {
        enEdtV(() -> {
            sistema.getBtnNuevoPlato().doClick();
            assertEquals("Formulario limpio debe tener txtIdPlato vacío", "", sistema.getTxtIdPlato().getText().trim());
            assertEquals("Botón debe decir 'Guardar' en estado limpio", "Guardar", sistema.getBtnGuardarPlato().getText());
            assertEquals("Botón de la derecha debe decir 'Editar' en estado limpio", "Editar", sistema.getBtnEditarPlato().getText());
            assertEquals("Botón de limpiar debe decir 'Limpiar Campos'", "Limpiar Campos", sistema.getBtnNuevoPlato().getText());
        });
    }

    @Test
    public void test02_EnterEnCreacionNoGuardaParaEvitarAccidentes() throws Exception {
        String nombreUnico = "Plato No Enter Test " + System.currentTimeMillis();
        enEdtV(() -> {
            sistema.getBtnNuevoPlato().doClick();
            sistema.getTxtNombrePlato().setText(nombreUnico);
            sistema.getTxtPrecioPlato().setText("15.00");

            // Presionar Enter en el precio cuando el plato NO existe NO debe guardar
            for (var listener : sistema.getTxtPrecioPlato().getActionListeners()) {
                listener.actionPerformed(new ActionEvent(sistema.getTxtPrecioPlato(), ActionEvent.ACTION_PERFORMED, null));
            }
        });

        // Verificar que NO fue registrado en BD
        Optional<Platos> pOpt = platosDao.buscarActivoPorNombre(nombreUnico);
        assertFalse("El plato NO debe registrarse con Enter en modo creación", pOpt.isPresent());

        // Ahora hacer clic explícito en Guardar: aquí sí debe guardarse
        enEdtV(() -> {
            sistema.getBtnGuardarPlato().doClick();
        });

        pOpt = platosDao.buscarActivoPorNombre(nombreUnico);
        assertTrue("El plato debe registrarse al hacer clic en Guardar", pOpt.isPresent());
        platosDao.desactivar(pOpt.get().getId());
    }

    @Test
    public void test03_CargaDePlatoCambiaBotonAGuardarCambios() throws Exception {
        int id = asegurarPlatoPrueba("Plato Carga Test " + System.currentTimeMillis(), "15.00");
        try {
            enEdtV(() -> {
                sistema.getTxtIdPlato().setText(String.valueOf(id));
                sistema.getTxtNombrePlato().setText("Plato Carga Test");
                sistema.getTxtPrecioPlato().setText("15.00");
                sistema.actualizarEstadoBotonesPlatos();

                assertEquals("Al tener plato cargado, el botón izquierdo debe decir 'Guardar Cambios'",
                        "Guardar Cambios", sistema.getBtnGuardarPlato().getText());
                assertEquals("Al tener plato cargado, el botón derecho también debe decir 'Guardar Cambios'",
                        "Guardar Cambios", sistema.getBtnEditarPlato().getText());
            });
        } finally {
            platosDao.desactivar(id);
        }
    }

    @Test
    public void test04_ModificarPrecioConBotonIzquierdoSinBloqueo() throws Exception {
        String nombre = "Plato Modif Izq " + System.currentTimeMillis();
        int id = asegurarPlatoPrueba(nombre, "10.00");
        try {
            enEdtV(() -> {
                // El operador cambia de precio a 18.00 y pulsa el botón de la IZQUIERDA (btnGuardarPlato)
                sistema.getTxtIdPlato().setText(String.valueOf(id));
                sistema.getTxtNombrePlato().setText(nombre);
                sistema.getTxtPrecioPlato().setText("18.00");
                sistema.actualizarEstadoBotonesPlatos();

                // Ejecuta el botón de la izquierda sin advertencia de bloqueo
                sistema.guardarOActualizarPlato();
            });

            // Verificar en BD que el precio cambió a 18.00
            Optional<Platos> modOpt = platosDao.buscarActivoPorNombre(nombre);
            assertTrue(modOpt.isPresent());
            Platos mod = modOpt.get();
            assertEquals("El precio modificado por botón izquierdo debe ser 18.00",
                    0, new BigDecimal("18.00").compareTo(mod.getPrecioDecimal()));
        } finally {
            platosDao.desactivar(id);
        }
    }

    @Test
    public void test05_ModificarPrecioConAtajoEnterEnPlatoExistente() throws Exception {
        String nombre = "Plato Modif Enter " + System.currentTimeMillis();
        int id = asegurarPlatoPrueba(nombre, "20.00");
        try {
            enEdtV(() -> {
                // Plato cargado (existe txtIdPlato)
                sistema.getTxtIdPlato().setText(String.valueOf(id));
                sistema.getTxtNombrePlato().setText(nombre);
                sistema.getTxtPrecioPlato().setText("22.75");
                sistema.actualizarEstadoBotonesPlatos();

                // Aquí SÍ se activa el ENTER porque el plato ya existe y estamos actualizando el precio
                for (var listener : sistema.getTxtPrecioPlato().getActionListeners()) {
                    listener.actionPerformed(new ActionEvent(sistema.getTxtPrecioPlato(), ActionEvent.ACTION_PERFORMED, null));
                }
            });

            // Verificar en BD que el precio cambió a 22.75
            Optional<Platos> modOpt = platosDao.buscarActivoPorNombre(nombre);
            assertTrue(modOpt.isPresent());
            Platos mod = modOpt.get();
            assertEquals("El precio modificado por Enter debe ser 22.75",
                    0, new BigDecimal("22.75").compareTo(mod.getPrecioDecimal()));
        } finally {
            platosDao.desactivar(id);
        }
    }

    @Test
    public void test06_OperadorCancelaEdicionConBotonLimpiarCampos() throws Exception {
        enEdtV(() -> {
            // Cargar datos en el formulario
            sistema.getTxtIdPlato().setText("9999");
            sistema.getTxtNombrePlato().setText("Plato Cancelar");
            sistema.getTxtPrecioPlato().setText("50.00");
            sistema.actualizarEstadoBotonesPlatos();

            assertEquals("Guardar Cambios", sistema.getBtnGuardarPlato().getText());

            // Operador se arrepiente y hace clic en 'Limpiar Campos'
            sistema.getBtnNuevoPlato().doClick();

            // Debe vaciar todos los campos y volver a 'Guardar'
            assertEquals("ID debe estar vacío", "", sistema.getTxtIdPlato().getText().trim());
            assertEquals("Nombre debe estar vacío", "", sistema.getTxtNombrePlato().getText().trim());
            assertEquals("Precio debe estar vacío", "", sistema.getTxtPrecioPlato().getText().trim());
            assertEquals("Botón debe volver a 'Guardar'", "Guardar", sistema.getBtnGuardarPlato().getText());
            assertEquals("Botón derecho debe volver a 'Editar'", "Editar", sistema.getBtnEditarPlato().getText());
        });
    }

    @Test
    public void test07_ValidacionesCamposVaciosEInvalidos() throws Exception {
        enEdtV(() -> {
            sistema.getBtnNuevoPlato().doClick();
            // Ambos vacíos -> no debe registrar nada
            sistema.guardarOActualizarPlato();
            assertEquals("No debe registrar si está vacío", "", sistema.getTxtIdPlato().getText().trim());

            // Nombre lleno pero precio vacío
            sistema.getTxtNombrePlato().setText("Plato Sin Precio");
            sistema.getTxtPrecioPlato().setText("");
            sistema.guardarOActualizarPlato();
            assertEquals("No debe registrar si precio está vacío", "", sistema.getTxtIdPlato().getText().trim());

            // Precio no numérico
            sistema.getTxtPrecioPlato().setText("abc");
            sistema.guardarOActualizarPlato();
            assertEquals("No debe registrar con precio inválido", "", sistema.getTxtIdPlato().getText().trim());
        });
    }

    @Test
    public void test08_GrupoInputsSalas_LimpiarCamposYGestionUnificada() throws Exception {
        enEdtV(() -> {
            // 1. Limpiar campos de sala
            sistema.getBtnNuevoSala().doClick();
            assertEquals("Limpiar Campos", sistema.getBtnNuevoSala().getText());
            assertEquals("Guardar", sistema.getBtnRegistrarSala().getText());

            // 2. Cargar una sala simulada
            sistema.getTxtIdSala().setText("1");
            sistema.getTxtNombreSala().setText("Terraza VIP");
            sistema.getTxtMesas().setText("10");
            sistema.actualizarEstadoBotonesSalas();

            assertEquals("Al cargar sala, botón registrar pasa a 'Guardar Cambios'",
                    "Guardar Cambios", sistema.getBtnRegistrarSala().getText());
            assertEquals("Al cargar sala, botón actualizar pasa a 'Guardar Cambios'",
                    "Guardar Cambios", sistema.getBtnActualizarSala().getText());

            // 3. Operador limpia el grupo de inputs de sala
            sistema.getBtnNuevoSala().doClick();
            assertEquals("ID sala debe estar vacío", "", sistema.getTxtIdSala().getText().trim());
            assertEquals("Nombre sala debe estar vacío", "", sistema.getTxtNombreSala().getText().trim());
            assertEquals("Mesas sala debe estar vacío", "", sistema.getTxtMesas().getText().trim());
            assertEquals("Botón sala vuelve a 'Guardar'", "Guardar", sistema.getBtnRegistrarSala().getText());
        });
    }

    @Test
    public void test09_GrupoInputsUsuarios_LimpiarCampos() throws Exception {
        enEdtV(() -> {
            assertNotNull("Botón limpiar usuario debe existir", sistema.getBtnLimpiarUsuario());
            assertEquals("Limpiar Campos", sistema.getBtnLimpiarUsuario().getText());

            // Llenar campos de usuario
            sistema.getTxtNombre().setText("Usuario Temporal");
            sistema.getTxtCorreo().setText("temp@restaurante.com");
            sistema.getTxtPass().setText("password123");

            // Hacer clic en Limpiar Campos
            sistema.getBtnLimpiarUsuario().doClick();

            // Verificar que se limpió el grupo de inputs
            assertEquals("Nombre de usuario debe estar vacío", "", sistema.getTxtNombre().getText().trim());
            assertEquals("Correo de usuario debe estar vacío", "", sistema.getTxtCorreo().getText().trim());
            assertEquals("Password debe estar vacía", 0, sistema.getTxtPass().getPassword().length);
        });
    }
}
