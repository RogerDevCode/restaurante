package Vista;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * E2E en pantalla virtual (Xvfb): run con DISPLAY=:99.
 * Mesa 1/Sala 1: buscar pollo -> agregar 1 -> enviar -> verificar 1 ->
 * reabrir -> agregar 2 -> actualizar -> verificar 3. Limpia el pedido al final.
 */
public class E2EVirtual {

    static Robot robot;
    static int fallos = 0;

    static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    static <T> T enEdt(Callable<T> c) throws Exception {
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

    static void enEdtV(Runnable r) throws Exception {
        enEdt(() -> {
            r.run();
            return null;
        });
    }

    static Object campo(Sistema s, String nombre) throws Exception {
        java.lang.reflect.Field f = Sistema.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(s);
    }

    static void clic(Component c) throws Exception {
        Point p = c.getLocationOnScreen();
        Dimension d = c.getSize();
        robot.mouseMove(p.x + d.width / 2, p.y + d.height / 2);
        robot.delay(120);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(80);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(300);
    }

    static void escribir(String s) {
        for (char ch : s.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(ch));
            robot.keyPress(code);
            robot.delay(40);
            robot.keyRelease(code);
            robot.delay(60);
        }
    }

    static int sumaPollosDb(int sala, int mesa) throws Exception {
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement(
                     "SELECT COALESCE(SUM(d.cantidad),0) FROM detalle_pedidos d JOIN pedidos p ON p.id=d.id_pedido "
                     + "WHERE p.estado='PENDIENTE' AND p.id_sala=? AND p.num_mesa=? AND d.nombre LIKE '%POLLO ASADO%'")) {
            st.setInt(1, sala);
            st.setInt(2, mesa);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static int idPendienteDb(int sala, int mesa) throws Exception {
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement(
                     "SELECT id FROM pedidos WHERE estado='PENDIENTE' AND id_sala=? AND num_mesa=? ORDER BY id DESC LIMIT 1")) {
            st.setInt(1, sala);
            st.setInt(2, mesa);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    static void limpiarDb(int id) throws Exception {
        if (id <= 0) {
            return;
        }
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM detalle_pedidos WHERE id_pedido=" + id);
            st.executeUpdate("DELETE FROM pedidos WHERE id=" + id);
        }
    }

    static void foto(JFrame f, String nombre) {
        try {
            Point p = f.getLocationOnScreen();
            BufferedImage cap = robot.createScreenCapture(new Rectangle(p.x, p.y, f.getWidth(), f.getHeight()));
            ImageIO.write(cap, "png", new java.io.File("/tmp/opencode/" + nombre + ".png"));
        } catch (Exception e) {
            System.out.println("foto fallo: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E toca la BD real (crea y borra 1 pedido): relanza con -De2e.permitir=SI");
            System.exit(2);
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        robot = new Robot();
        robot.setAutoDelay(40);
        Usuario admin = new Usuario(2, "Administrador", "admin", "admin", "Administrador");
        try (java.sql.Connection c = new ProveedorConexionJdbc().getConnection();
             java.sql.PreparedStatement st = c.prepareStatement("SELECT id FROM usuarios WHERE correo='admin' OR rol='Administrador' ORDER BY id ASC LIMIT 1");
             java.sql.ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                admin.setId(rs.getInt(1));
            }
        }
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
        final Sistema[] box = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> box[0] = new Sistema(admin,
                new SalasControlador(new SalasServicio(new SalasDao(), politica)),
                new PlatosControlador(new PlatosServicio(new PlatosDao(), politica)),
                pedidosCtrl,
                new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica)),
                new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica)),
                empresaDao));
        Sistema s = box[0];
        int creado = -1;
        try {
            SwingUtilities.invokeAndWait(() -> {
                s.setExtendedState(JFrame.MAXIMIZED_BOTH);
                s.setVisible(true);
            });
            Thread.sleep(2500);
            check(idPendienteDb(1, 1) < 0, "mesa 1 libre al inicio");

            // Contexto mesa 1 / sala 1 + tab Toma.
            JTabbedPane tabs = (JTabbedPane) campo(s, "jTabbedPane1");
            JTextField txtSala = (JTextField) campo(s, "txtTempIdSala");
            JTextField txtMesa = (JTextField) campo(s, "txtTempNumMesa");
            JTable menu = (JTable) campo(s, "tblTemPlatos");
            JTable carro = (JTable) campo(s, "tableMenu");
            JTextField buscar = (JTextField) campo(s, "txtBuscarPlato");
            JButton generar = (JButton) campo(s, "btnGenerarPedido");
            enEdtV(() -> {
                txtSala.setText("1");
                txtMesa.setText("1");
                tabs.setSelectedIndex(3);
            });
            Thread.sleep(800);

            // Buscar pollo con eventos reales.
            clic(buscar);
            escribir("pollo");
            long fin = System.currentTimeMillis() + 15000;
            int filaPollo = -1;
            while (System.currentTimeMillis() < fin) {
                filaPollo = enEdt(() -> {
                    for (int i = 0; i < menu.getRowCount(); i++) {
                        Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                        if (v != null && v.toString().toUpperCase().contains("POLLO ASADO")) {
                            return i;
                        }
                    }
                    return -1;
                });
                if (filaPollo >= 0) {
                    break;
                }
                Thread.sleep(400);
            }
            check(filaPollo >= 0, "filtro muestra POLLO ASADO");
            foto(s, "e2e_menu");
            if (filaPollo < 0) {
                throw new RuntimeException("sin fila de pollo");
            }

            // Agregar 1 con clic real (el handler suma al carrito).
            final int f1 = filaPollo;
            enEdtV(() -> {
                Rectangle r = menu.getCellRect(f1, 0, true);
                Point p = menu.getLocationOnScreen();
                robot.mouseMove(p.x + r.x + r.width / 2, p.y + r.y + r.height / 2);
            });
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(80);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            fin = System.currentTimeMillis() + 10000;
            int filasCarro = 0;
            while (System.currentTimeMillis() < fin) {
                filasCarro = enEdt(carro::getRowCount);
                if (filasCarro >= 1) {
                    break;
                }
                Thread.sleep(300);
            }
            check(filasCarro >= 1, "carrito tiene 1 fila tras clic");
            foto(s, "e2e_carro1");

            // Enviar (sin confirmacion en el jar nuevo).
            clic(generar);
            fin = System.currentTimeMillis() + 15000;
            int q1 = -1;
            while (System.currentTimeMillis() < fin) {
                q1 = sumaPollosDb(1, 1);
                if (q1 == 1) {
                    break;
                }
                Thread.sleep(500);
            }
            check(q1 == 1, "BD muestra 1 pollo (q=" + q1 + ")");
            foto(s, "e2e_enviado");
            creado = idPendienteDb(1, 1);
            check(creado > 0, "pedido pendiente creado id=" + creado);

            // Reabrir: simula clic en mesa ocupada -> Caja.
            java.lang.reflect.Method cargar = Sistema.class.getDeclaredMethod("cargarPedidoEnPantalla", int.class, boolean.class);
            cargar.setAccessible(true);
            final int idReabrir = creado;
            enEdtV(() -> {
                try {
                    cargar.invoke(s, idReabrir, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(1500);
            int tab = enEdt(tabs::getSelectedIndex);
            check(tab == 4, "reabrir lleva a Caja (tab=" + tab + ")");

            // Agregar productos -> Toma con carrito cargado.
            JButton modificar = (JButton) campo(s, "btnModificarPedido");
            clic(modificar);
            Thread.sleep(1500);
            int carro2 = enEdt(carro::getRowCount);
            check(carro2 >= 1, "carrito precargado con lo existente (" + carro2 + ")");
            foto(s, "e2e_precargado");

            // Agregar 2 mas con clics reales.
            for (int k = 0; k < 2; k++) {
                int fr = enEdt(() -> {
                    for (int i = 0; i < menu.getRowCount(); i++) {
                        Object v = menu.getModel().getValueAt(menu.convertRowIndexToModel(i), 1);
                        if (v != null && v.toString().toUpperCase().contains("POLLO ASADO")) {
                            return i;
                        }
                    }
                    return -1;
                });
                check(fr >= 0, "fila pollo visible para agregar #" + (k + 2));
                if (fr < 0) {
                    break;
                }
                final int ff = fr;
                enEdtV(() -> {
                    Rectangle r = menu.getCellRect(ff, 0, true);
                    Point p = menu.getLocationOnScreen();
                    robot.mouseMove(p.x + r.x + r.width / 2, p.y + r.y + r.height / 2);
                });
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(80);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(1200);
            }
            int intcant = enEdt(() -> {
                int t = 0;
                for (int i = 0; i < carro.getRowCount(); i++) {
                    t += Integer.parseInt(carro.getModel().getValueAt(carro.convertRowIndexToModel(i), 2).toString());
                }
                return t;
            });
            check(intcant == 3, "carrito suma 3 pollos (cant=" + intcant + ")");
            foto(s, "e2e_carro3");

            // Actualizar (instrumentado + sonda + fallback doClick).
            final long[] sonda = new long[2];
            enEdtV(() -> {
                try {
                    JButton gg = (JButton) campo(s, "btnGenerarPedido");
                    System.out.println("PRE bounds=" + gg.getBounds() + " visible=" + gg.isVisible()
                        + " showing=" + gg.isShowing() + " enabled=" + gg.isEnabled()
                        + " tab=" + ((JTabbedPane) campo(s, "jTabbedPane1")).getSelectedIndex());
                    gg.addActionListener(ev -> sonda[0]++);
                    gg.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mousePressed(java.awt.event.MouseEvent e) { sonda[1]++; }
                    });
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("PRE idEdicion=" + enEdt(() -> {
                java.lang.reflect.Field f = Sistema.class.getDeclaredField("idPedidoEdicion");
                f.setAccessible(true);
                return f.getInt(s);
            }) + " txtIdPedido='" + enEdt(() -> ((JTextField) campo(s, "txtIdPedido")).getText()) + "'"
                + " btnTxt='" + enEdt(() -> ((JButton) campo(s, "btnGenerarPedido")).getText()) + "'"
                + " btnOn=" + enEdt(() -> ((JButton) campo(s, "btnGenerarPedido")).isEnabled()));
            clic(generar);
            Thread.sleep(1500);
            System.out.println("sonda action=" + sonda[0] + " mouse=" + sonda[1]);
            if (sonda[0] == 0) {
                System.out.println("Robot no llego: fallback doClick()");
                enEdtV(() -> {
                    try {
                        ((JButton) campo(s, "btnGenerarPedido")).doClick();
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            fin = System.currentTimeMillis() + 15000;
            int q2 = -1;
            while (System.currentTimeMillis() < fin) {
                q2 = sumaPollosDb(1, 1);
                if (q2 == 3) {
                    break;
                }
                Thread.sleep(500);
            }
            check(q2 == 3, "BD muestra 3 pollos (q=" + q2 + ")");
            foto(s, "e2e_actualizado");
            System.out.println("dialogos=" + enEdt(() -> {
                java.util.List<String> ds = new java.util.ArrayList<>();
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof javax.swing.JDialog && w.isVisible()) {
                        ds.add(((javax.swing.JDialog) w).getTitle());
                    }
                }
                return ds;
            }));
        } finally {
            try {
                int id = idPendienteDb(1, 1);
                if (id > 0) {
                    limpiarDb(id);
                    System.out.println("limpio pedido " + id);
                }
            } catch (Exception e) {
                System.out.println("limpieza fallo: " + e.getMessage());
            }
            try {
                Sistema ss = box[0];
                if (ss != null) {
                    SwingUtilities.invokeAndWait(ss::dispose);
                }
            } catch (Exception ignored) {
            }
        }
        System.out.println(fallos == 0 ? "E2E_OK" : "E2E_FALLO(" + fallos + ")");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
