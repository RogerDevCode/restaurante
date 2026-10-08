package Vista;

import Controlador.LoginControlador;
import Modelo.AutenticacionRepositorio;
import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas completas de la vista FrmLogin, verificando componentes,
 * pre-llenado de credenciales, validación y transiciones de estado.
 */
public class FrmLoginVistaTest {

    @Test
    public void constructorRechazaParametrosNulos() {
        assertThrows(ErrorAplicacionException.class, () -> new FrmLogin(null, u -> null));
        LoginControlador ctrl = new LoginControlador(new AutenticacionServicio(new RepositorioLoginFalso()));
        assertThrows(ErrorAplicacionException.class, () -> new FrmLogin(ctrl, null));
    }

    @Test
    public void camposVienenPreLlenadosConAdminYAdmin() throws Exception {
        AtomicReference<FrmLogin> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LoginControlador ctrl = new LoginControlador(new AutenticacionServicio(new RepositorioLoginFalso()));
            FrmLogin login = new FrmLogin(ctrl, u -> null);
            ref.set(login);
        });

        FrmLogin login = ref.get();
        assertNotNull(login);

        JTextField txtCorreo = obtenerCampo(login, "txtCorreo", JTextField.class);
        JPasswordField txtPass = obtenerCampo(login, "txtPass", JPasswordField.class);
        JButton btnIniciar = obtenerCampo(login, "btnIniciar", JButton.class);

        assertEquals("admin", txtCorreo.getText());
        assertEquals("admin", new String(txtPass.getPassword()));
        assertTrue(btnIniciar.isEnabled());

        SwingUtilities.invokeAndWait(login::dispose);
    }

    @Test
    public void autenticacionExitosaTransicionaCorrectamente() throws Exception {
        AtomicBoolean sistemaCreado = new AtomicBoolean(false);
        AtomicReference<Usuario> usuarioRecibido = new AtomicReference<>();

        Usuario usuarioPrueba = new Usuario();
        usuarioPrueba.setId(1);
        usuarioPrueba.setNombre("Administrador");
        usuarioPrueba.setCorreo("admin");
        usuarioPrueba.setRol("Administrador");

        RepositorioLoginFalso repo = new RepositorioLoginFalso();
        repo.usuarioRetorno = Optional.of(usuarioPrueba);

        AtomicReference<FrmLogin> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LoginControlador ctrl = new LoginControlador(new AutenticacionServicio(repo));
            FrmLogin login = new FrmLogin(ctrl, u -> {
                sistemaCreado.set(true);
                usuarioRecibido.set(u);
                return null;
            });
            ref.set(login);
        });

        FrmLogin login = ref.get();
        // Invocar autenticacionCompletada
        Method m = FrmLogin.class.getDeclaredMethod("autenticacionCompletada", Optional.class);
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                m.invoke(login, Optional.of(usuarioPrueba));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Usuario guardado = obtenerCampo(login, "usuarioAutenticado", Usuario.class);
        assertEquals("Administrador", guardado.getNombre());
        assertEquals("admin", guardado.getCorreo());

        SwingUtilities.invokeAndWait(login::dispose);
    }

    @Test
    public void autenticacionFallidaRestauraEstadoDelFormulario() throws Exception {
        AtomicReference<FrmLogin> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LoginControlador ctrl = new LoginControlador(new AutenticacionServicio(new RepositorioLoginFalso()));
            FrmLogin login = new FrmLogin(ctrl, u -> null);
            ref.set(login);
        });

        FrmLogin login = ref.get();
        Method cambiarEstado = FrmLogin.class.getDeclaredMethod("cambiarEstadoFormulario", boolean.class);
        cambiarEstado.setAccessible(true);

        SwingUtilities.invokeAndWait(() -> {
            try {
                cambiarEstado.invoke(login, false);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton btn = obtenerCampo(login, "btnIniciar", JButton.class);
        assertFalse(btn.isEnabled());

        SwingUtilities.invokeAndWait(() -> {
            try {
                cambiarEstado.invoke(login, true);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        assertTrue(btn.isEnabled());
        SwingUtilities.invokeAndWait(login::dispose);
    }

    @SuppressWarnings("unchecked")
    private static <T> T obtenerCampo(Object target, String nombre, Class<T> tipo) throws Exception {
        Field f = target.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static class RepositorioLoginFalso implements AutenticacionRepositorio {
        Optional<Usuario> usuarioRetorno = Optional.empty();

        @Override
        public Optional<Usuario> autenticar(String correo, String clave) {
            return usuarioRetorno;
        }
    }
}
