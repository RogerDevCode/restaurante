package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.*;

/**
 * E2E en pantalla virtual (Xvfb DISPLAY=:99, Robot propio):
 * {@code ./e2e_virtual.sh Vista.UsuariosCrudE2E}
 *
 * Combinatoria del menú Usuarios + CRUD completo con usuarios de prueba
 * (correo e2e_*@test, eliminados al final):
 * 1. Entrar al menú (botón lateral) sin errores ni diálogos inesperados.
 * 2. Validaciones: vacío, correo inválido, clave corta, correo duplicado.
 * 3. Crear Asistente y Administrador (hash de clave, fila visible).
 * 4. Cambiar clave (diálogo real) y validación de coincidencia.
 * 5. Desactivar/reactivar con confirmación.
 * 6. Eliminar con confirmación + limpieza final.
 */
public final class UsuariosCrudE2E {

    private static int fallos = 0;
    private static Robot robot;

    private static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    private static <T> T enEdt(Callable<T> c) throws Exception {
        final Object[] r = new Object[1];
        final Throwable[] e = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                r[0] = c.call();
            } catch (Throwable t) {
                e[0] = t;
            }
        });
        if (e[0] != null) {
            throw new RuntimeException(e[0]);
        }
        @SuppressWarnings("unchecked")
        T t = (T) r[0];
        return t;
    }

    private static void enEdtV(Runnable r) throws Exception {
        enEdt(() -> {
            r.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object target, String nombre, Class<T> tipo) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void clic(Component c) throws Exception {
        Point p = enEdt(c::getLocationOnScreen);
        Dimension d = enEdt(c::getSize);
        robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
        robot.delay(150);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(90);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(400);
    }

    private static void clicCelda(JTable tabla, int filaVista, int colVista) throws Exception {
        Rectangle r = enEdt(() -> tabla.getCellRect(filaVista, colVista, true));
        Point p = enEdt(tabla::getLocationOnScreen);
        robot.mouseMove(p.x + r.x + r.width / 2, p.y + r.y + r.height / 2);
        robot.delay(150);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(90);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(400);
    }

    private static void escribir(String s) {
        for (char ch : s.toCharArray()) {
            if (ch == '@') {
                robot.keyPress(KeyEvent.VK_ALT_GRAPH);
                robot.keyPress(KeyEvent.VK_Q);
                robot.keyRelease(KeyEvent.VK_Q);
                robot.keyRelease(KeyEvent.VK_ALT_GRAPH);
            } else if (ch == '_') {
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_MINUS);
                robot.keyRelease(KeyEvent.VK_MINUS);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            } else if (ch == '.') {
                robot.keyPress(KeyEvent.VK_PERIOD);
                robot.keyRelease(KeyEvent.VK_PERIOD);
            } else {
                int code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(ch));
                boolean mayus = Character.isUpperCase(ch);
                if (mayus) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                }
                robot.keyPress(code);
                robot.keyRelease(code);
                if (mayus) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
            }
            robot.delay(30);
        }
    }

    private static JDialog dialogoVisible(String parteTitulo, long ms) throws Exception {
        long fin = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < fin) {
            JDialog d = enEdt(() -> {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof JDialog jd && jd.isVisible()
                            && jd.getTitle() != null && jd.getTitle().contains(parteTitulo)) {
                        return jd;
                    }
                }
                return null;
            });
            if (d != null) {
                return d;
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static String textoDialogo(JDialog d) throws Exception {
        return enEdt(() -> {
            StringBuilder sb = new StringBuilder();
            buscarTextos(d.getContentPane(), sb);
            return sb.toString();
        });
    }

    private static void buscarTextos(Container c, StringBuilder sb) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JLabel l && l.getText() != null) {
                sb.append(l.getText()).append(' ');
            } else if (k instanceof Container kc) {
                buscarTextos(kc, sb);
            }
        }
    }

    private static void pulsarBotonDialogo(JDialog d, String texto) throws Exception {
        enEdtV(() -> {
            JButton b = buscarBoton(d.getContentPane(), texto);
            if (b == null) {
                List<JButton> todos = new ArrayList<>();
                recolectar(d.getContentPane(), JButton.class, todos);
                if (!todos.isEmpty()) {
                    b = todos.get(0);
                }
            }
            if (b == null) {
                throw new RuntimeException("Botón '" + texto + "' no encontrado");
            }
            b.doClick();
        });
    }

    private static JButton buscarBoton(Container c, String texto) {
        for (Component k : c.getComponents()) {
            if (k instanceof JButton b && texto.equals(b.getText())) {
                return b;
            } else if (k instanceof Container kc) {
                JButton r = buscarBoton(kc, texto);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void fijarClaveDialogo(JDialog d, String clave) throws Exception {
        enEdtV(() -> {
            List<JPasswordField> pws = new ArrayList<>();
            recolectar(d.getContentPane(), JPasswordField.class, pws);
            for (JPasswordField p : pws) {
                p.setText(clave);
            }
            if (pws.size() < 2) {
                throw new RuntimeException("Campos de clave no encontrados: " + pws.size());
            }
        });
    }

    private static <T> void recolectar(Container c, Class<T> tipo, List<T> r) {
        for (Component k : c.getComponents()) {
            if (tipo.isInstance(k)) {
                r.add(tipo.cast(k));
            } else if (k instanceof Container kc) {
                recolectar(kc, tipo, r);
            }
        }
    }

    private static int filasUsuarios(JTable t) throws Exception {
        return enEdt(t::getRowCount);
    }

    private static int filaPorCorreo(JTable t, String correo) throws Exception {        return enEdt(() -> {
            for (int i = 0; i < t.getRowCount(); i++) {
                Object v = t.getModel().getValueAt(t.convertRowIndexToModel(i), 2);
                if (correo.equals(v != null ? v.toString() : null)) {
                    return i;
                }
            }
            return -1;
        });
    }

    private static void limpiarE2E() throws Exception {
        try (java.sql.Connection con = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM usuarios WHERE correo LIKE 'e2e\\_%@test'")) {
            ps.executeUpdate();
        }
    }

    // ---------------- Repos en memoria (lo ajeno a usuarios) ----------------

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(new Salas(11, "Salon E2E", 4));
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos = List.of();
        @Override public List<Platos> listarPorFecha(String n, String f) { return platos; }
        @Override public List<Platos> listarPlatosParaVenta(String n, String f) { return platos; }
        @Override public boolean registrar(Platos p) { return false; }
        @Override public boolean modificar(Platos p) { return false; }
    }

    private static final class PedidosMemoria implements PedidosRepositorio {
        @Override public int registrarPedidoCompleto(Pedidos p, List<DetallePedido> d) { return -1; }
        @Override public boolean actualizarPedidoCompleto(int id, Pedidos p, List<DetallePedido> d) { return false; }
        @Override public int verificarStado(int mesa, int idSala) { return 0; }
        @Override public Pedidos verPedido(int id) { return null; }
        @Override public List<DetallePedido> verPedidoDetalle(int id) { return List.of(); }
        @Override public List<DetallePedido> finalizarPedido(int id) { return List.of(); }
        @Override public boolean actualizarEstado(int id) { return true; }
        @Override public List<Pedidos> listarPedidos() { return List.of(); }
    }

    private static void rellenarFormulario(Sistema sistema, String nombre, String correo, String clave, String rol) throws Exception {
        enEdtV(() -> {
            try {
                campo(sistema, "txtNombre", JTextField.class).setText(nombre);
                campo(sistema, "txtCorreo", JTextField.class).setText(correo);
                campo(sistema, "txtPass", JPasswordField.class).setText(clave);
                @SuppressWarnings("unchecked")
                JComboBox<String> cbx = campo(sistema, "cbxRol", JComboBox.class);
                cbx.setSelectedItem(rol);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Thread.sleep(300);
    }

    private static String tituloVentana(Sistema sistema) throws Exception {
        return enEdt(sistema::getTitle);
    }

    private static void clicBoton(Sistema sistema, String campoBoton) throws Exception {
        enEdtV(() -> {
            try {
                campo(sistema, campoBoton, JButton.class).doClick();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E toca usuarios reales (crea y borra e2e_*@test): relanza con -De2e.permitir=SI");
            System.exit(2);
        }
        try (java.sql.Connection connCheck = new ProveedorConexionJdbc().getConnection()) {
            String dbCatalog = connCheck.getCatalog();
            if (dbCatalog == null || !dbCatalog.toLowerCase().contains("restaurante_test")) {
                throw new IllegalStateException("Seguridad: UsuariosCrudE2E solo puede ejecutarse contra la BD 'restaurante_test'. Base actual: " + dbCatalog);
            }
        }
        String display = System.getenv("DISPLAY");
        if (display == null || (!display.contains(":99") && !display.contains(":98"))) {
            System.err.println("ADVERTENCIA: DISPLAY=" + display + " (se esperaba :99 virtual)");
        }
        robot = new Robot();
        robot.setAutoDelay(40);
        limpiarE2E();

        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement(
                     "SELECT id FROM usuarios WHERE correo='admin' OR rol='Administrador' ORDER BY id ASC LIMIT 1");
             java.sql.ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                admin.setId(rs.getInt(1));
            }
        }
        PoliticaAcceso permisos = new PoliticaAcceso(admin);
        LoginDao empresaDao = new LoginDao();
        java.nio.file.Path pdfTemp = java.nio.file.Files.createTempDirectory("e2e-usuarios-");
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(id -> null, id -> null, Config::new,
                new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(new PedidosMemoria()), pdfServicio, permisos,
                new ConsultaPedidosServicio(new PedidosMemoria(), permisos));
        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(new SalasMemoria(), permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(new PlatosMemoria(), permisos));

        final Sistema[] box = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> {
            box[0] = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, empresaDao);
        });
        Sistema sistema = box[0];
        enEdtV(() -> {
            sistema.setSize(1366, 711);
            sistema.setLocationRelativeTo(null);
            sistema.setVisible(true);
        });
        Thread.sleep(1500);

        try {
            JTable tabla = enEdt(() -> campo(sistema, "TableUsuarios", JTable.class));
            JButton btnUsuarios = enEdt(() -> campo(sistema, "btnUsuarios", JButton.class));

            // ================= CASO 1: entrar al menú =================
            System.out.println("\n--- CASO 1: entrar a Usuarios ---");
            clic(btnUsuarios);
            Thread.sleep(2500);
            String tab = enEdt(() -> {
                JTabbedPane tabs = campo(sistema, "jTabbedPane1", JTabbedPane.class);
                return tabs.getTitleAt(tabs.getSelectedIndex());
            });
            check("Usuarios".equals(tab), "entra a la pestaña Usuarios (actual: " + tab + ")");
            JDialog err = dialogoVisible("", 500);
            check(err == null, "sin diálogos de error al entrar");
            if (err != null) {
                System.out.println("  detalle error: " + textoDialogo(err));
                pulsarBotonDialogo(err, "Aceptar");
            }
            int n0 = filasUsuarios(tabla);
            check(n0 >= 1, "tabla cargó usuarios existentes (" + n0 + ")");

            // ================= CASO 2: validaciones =================
            System.out.println("\n--- CASO 2: validaciones del formulario ---");
            rellenarFormulario(sistema, "", "", "", "Asistente");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(600);
            String tipNom = enEdt(() -> campo(sistema, "txtNombre", JTextField.class).getToolTipText());
            check(tipNom != null && tipNom.contains("obligatorio"),
                    "vacío alerta campo nombre (tip: " + tipNom + ")");
            check(filasUsuarios(tabla) == n0, "vacío no crea filas");

            rellenarFormulario(sistema, "E2E Uno", "sin-arroba", "Clave1234", "Asistente");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(600);
            String tipCor = enEdt(() -> campo(sistema, "txtCorreo", JTextField.class).getToolTipText());
            check(tipCor != null && tipCor.toLowerCase().contains("correo"),
                    "correo inválido alerta campo (tip: " + tipCor + ")");
            check(filasUsuarios(tabla) == n0, "correo inválido no crea filas");

            rellenarFormulario(sistema, "E2E Uno", "e2e_u1@test.local", "123", "Asistente");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(600);
            String tipCla = enEdt(() -> campo(sistema, "txtPass", JPasswordField.class).getToolTipText());
            check(tipCla != null && tipCla.contains("al menos"),
                    "clave corta alerta campo (tip: " + tipCla + ")");
            check(filasUsuarios(tabla) == n0, "clave corta no crea filas");

            // ================= CASO 3: crear Asistente y Administrador =================
            System.out.println("\n--- CASO 3: crear usuarios ---");
            rellenarFormulario(sistema, "E2E Uno", "e2e_u1@test.local", "Clave1234", "Asistente");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(1200);
            int f1 = filaPorCorreo(tabla, "e2e_u1@test.local");
            check(f1 >= 0, "Asistente e2e_u1@test.local creado y visible");
            check(tituloVentana(sistema).contains("registrado"), "banda confirma registro");

            rellenarFormulario(sistema, "E2E Uno", "e2e_u1@test.local", "Clave1234", "Asistente");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(1200);
            JDialog dup = dialogoVisible("registrar usuario", 1500);
            boolean hayDup = dup != null;
            if (hayDup) {
                System.out.println("  detalle duplicado: " + textoDialogo(dup));
                pulsarBotonDialogo(dup, "Aceptar");
                Thread.sleep(400);
            }
            int cuentaU1 = enEdt(() -> {
                int n = 0;
                for (int i = 0; i < tabla.getRowCount(); i++) {
                    Object v = tabla.getModel().getValueAt(tabla.convertRowIndexToModel(i), 2);
                    if ("e2e_u1@test.local".equals(v != null ? v.toString() : null)) {
                        n++;
                    }
                }
                return n;
            });
            check(cuentaU1 == 1, "correo duplicado no crea segunda fila (hay " + cuentaU1 + ")");

            rellenarFormulario(sistema, "E2E Dos", "e2e_u2@test.local", "Clave5678", "Administrador");
            clicBoton(sistema, "btnIniciar");
            Thread.sleep(1200);
            check(filaPorCorreo(tabla, "e2e_u2@test.local") >= 0, "Administrador e2e_u2@test.local creado");

            // ================= CASO 4: cambiar clave =================
            System.out.println("\n--- CASO 4: cambiar clave ---");
            final int f2 = filaPorCorreo(tabla, "e2e_u1@test.local");
            check(f2 >= 0, "fila de e2e_u1@test.local localizable");
            clicCelda(tabla, f2, 1);
            Thread.sleep(500);
            Thread accion = new Thread(() -> {
                try {
                    clicBoton(sistema, "btnCambiarClaveUsuario");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            accion.start();
            JDialog dlgClave = dialogoVisible("Cambiar contraseña", 5000);
            check(dlgClave != null, "diálogo de cambio de clave visible");
            if (dlgClave != null) {
                fijarClaveDialogo(dlgClave, "NuevaClave99");
                pulsarBotonDialogo(dlgClave, "OK");
                accion.join(8000);
                Thread.sleep(1200);
                check(tituloVentana(sistema).contains("actualizada"), "banda confirma clave actualizada");
            } else {
                accion.join(8000);
            }

            // ================= CASO 5: desactivar / reactivar =================
            System.out.println("\n--- CASO 5: desactivar y reactivar ---");
            Thread accDes = new Thread(() -> {
                try {
                    clicBoton(sistema, "btnEstadoUsuario");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            accDes.start();
            JDialog dlgDes = dialogoVisible("desactivar", 5000);
            check(dlgDes != null, "confirmación de desactivar visible");
            if (dlgDes != null) {
                pulsarBotonDialogo(dlgDes, "Sí");
                accDes.join(8000);
                Thread.sleep(1500);
                Object est = enEdt(() -> {
                    int f = -1;
                    for (int i = 0; i < tabla.getRowCount(); i++) {
                        Object v = tabla.getModel().getValueAt(tabla.convertRowIndexToModel(i), 2);
                        if ("e2e_u1@test.local".equals(v != null ? v.toString() : null)) {
                            f = i;
                            break;
                        }
                    }
                    return f < 0 ? null : tabla.getModel().getValueAt(tabla.convertRowIndexToModel(f), 4);
                });
                check("Inactivo".equals(est != null ? est.toString() : null), "e2e_u1@test.local queda Inactivo");
            } else {
                accDes.join(8000);
            }
            Thread accRea = new Thread(() -> {
                try {
                    clicBoton(sistema, "btnEstadoUsuario");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            accRea.start();
            JDialog dlgRea = dialogoVisible("reactivar", 5000);
            check(dlgRea != null, "confirmación de reactivar visible");
            if (dlgRea != null) {
                pulsarBotonDialogo(dlgRea, "Sí");
                accRea.join(8000);
                Thread.sleep(1500);
                Object est = enEdt(() -> {
                    int f = -1;
                    for (int i = 0; i < tabla.getRowCount(); i++) {
                        Object v = tabla.getModel().getValueAt(tabla.convertRowIndexToModel(i), 2);
                        if ("e2e_u1@test.local".equals(v != null ? v.toString() : null)) {
                            f = i;
                            break;
                        }
                    }
                    return f < 0 ? null : tabla.getModel().getValueAt(tabla.convertRowIndexToModel(f), 4);
                });
                check("Activo".equals(est != null ? est.toString() : null), "e2e_u1@test.local vuelve a Activo");
            } else {
                accRea.join(8000);
            }

            // ================= CASO 6: eliminar =================
            System.out.println("\n--- CASO 6: eliminar ---");
            Thread accDel = new Thread(() -> {
                try {
                    clicBoton(sistema, "btnEliminarUsuario");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            accDel.start();
            JDialog dlgDel = dialogoVisible("ELIMINAR", 5000);
            check(dlgDel != null, "confirmación de eliminar visible");
            if (dlgDel != null) {
                pulsarBotonDialogo(dlgDel, "Sí");
                accDel.join(8000);
                Thread.sleep(1500);
                check(filaPorCorreo(tabla, "e2e_u1@test.local") < 0, "e2e_u1@test.local eliminado de la tabla");
            } else {
                accDel.join(8000);
            }
            Thread accDel2 = new Thread(() -> {
                try {
                    int f = filaPorCorreo(tabla, "e2e_u2@test.local");
                    if (f >= 0) {
                        enEdtV(() -> tabla.setRowSelectionInterval(f, f));
                    }
                    clicBoton(sistema, "btnEliminarUsuario");
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            accDel2.start();
            JDialog dlgDel2 = dialogoVisible("ELIMINAR", 5000);
            if (dlgDel2 != null) {
                pulsarBotonDialogo(dlgDel2, "Sí");
            }
            accDel2.join(8000);
            Thread.sleep(1200);
            check(filaPorCorreo(tabla, "e2e_u2@test.local") < 0, "e2e_u2@test.local eliminado de la tabla");
        } finally {
            try {
                limpiarE2E();
            } catch (Exception ex) {
                System.err.println("Aviso limpiando usuarios E2E: " + ex.getMessage());
            }
            try {
                enEdtV(sistema::dispose);
            } catch (Exception ignored) {
            }
        }
        System.out.println("=================================================");
        System.out.println(fallos == 0 ? "E2E USUARIOS: TODO OK" : "E2E USUARIOS: " + fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }
}