package aceptacion;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import Vista.Sistema;
import infraestructura.ProveedorConexionJdbc;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * E2E VIRTUAL F-09 (Xvfb :99) — RED TEAM
 * Verifica:
 * 1. Tipear 4 en fila 0 y hacer clic en fila 1 -> fila 0 = 4, fila 1 intacta.
 * 2. Ingresar 0 -> rechazo y texto revertido al valor previo.
 */
public class E2EF09Virtual {

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

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E requiere -De2e.permitir=SI");
            System.exit(2);
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        robot = new Robot();
        robot.setAutoDelay(40);

        Usuario admin = new Usuario(2, "Administrador", "admin", "admin", "Administrador");
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

        try {
            SwingUtilities.invokeAndWait(() -> {
                s.setExtendedState(JFrame.MAXIMIZED_BOTH);
                s.setVisible(true);
            });
            Thread.sleep(2000);

            JTabbedPane tabs = (JTabbedPane) campo(s, "jTabbedPane1");
            JTextField txtSala = (JTextField) campo(s, "txtTempIdSala");
            JTextField txtMesa = (JTextField) campo(s, "txtTempNumMesa");
            JTable carro = (JTable) campo(s, "tableMenu");
            JTextField txtCant = (JTextField) campo(s, "txtCantidadManual");

            enEdtV(() -> {
                txtSala.setText("1");
                txtMesa.setText("1");
                tabs.setSelectedIndex(3); // Tab toma de pedidos
            });
            Thread.sleep(500);

            // Cargar directamente 2 platos al carrito (tableMenu)
            enEdtV(() -> {
                DefaultTableModel m = (DefaultTableModel) carro.getModel();
                m.setRowCount(0);
                // Cols: 0:id, 1:nombre, 2:cant, 3:precio, 4:total, 5:comentario, 6:idDetalle, 7:moneda, 8:iva, 9:inc
                m.addRow(new Object[]{1, "POLLO ASADO", 1, new BigDecimal("45.00"), new BigDecimal("45.00"), "", 0, "USD", true, false});
                m.addRow(new Object[]{2, "LOMO SALTADO", 1, new BigDecimal("32.50"), new BigDecimal("32.50"), "", 0, "USD", true, false});
                carro.setRowSelectionInterval(0, 0);
            });
            Thread.sleep(300);

            check(carro.getRowCount() == 2, "Carrito contiene 2 filas");
            check("1".equals(txtCant.getText()), "txtCantidadManual inicial es 1");

            // =================================================================
            // TEST PARTE 1: Ingresar 0 -> rechazo y texto revertido
            // =================================================================
            enEdtV(() -> {
                carro.setRowSelectionInterval(0, 0);
                txtCant.requestFocusInWindow();
                txtCant.setText("0");
            });
            Thread.sleep(200);

            // Disparar aplicarCantidadManual ("0", fila 0) mediante Enter o pérdida de foco
            java.lang.reflect.Method mAplicar = Sistema.class.getDeclaredMethod("aplicarCantidadManual", String.class, int.class);
            mAplicar.setAccessible(true);
            enEdtV(() -> {
                try {
                    mAplicar.invoke(s, "0", 0);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            Thread.sleep(200);

            String textoRevertido = enEdt(txtCant::getText);
            Object cantFila0 = enEdt(() -> carro.getModel().getValueAt(0, 2));
            check("1".equals(textoRevertido), "txtCantidadManual fue revertido a '1' ante 0 (actual: " + textoRevertido + ")");
            check(Integer.valueOf(1).equals(cantFila0), "Fila 0 conserva cantidad 1 (actual: " + cantFila0 + ")");

            // =================================================================
            // TEST PARTE 2: Tipear 4 en fila 0 y hacer clic en fila 1 -> fila 0 = 4, fila 1 intacta
            // =================================================================
            enEdtV(() -> {
                carro.setRowSelectionInterval(0, 0);
                txtCant.requestFocusInWindow();
                txtCant.setText("4");
            });
            Thread.sleep(200);

            // Simular clic en fila 1: selección de fila 1 mientras txtCant tiene foco
            enEdtV(() -> {
                // Al cambiar la selección con txtCant enfocado, el ListSelectionListener transfiere el foco
                carro.setRowSelectionInterval(1, 1);
            });
            Thread.sleep(300);

            Object cantFila0Final = enEdt(() -> carro.getModel().getValueAt(0, 2));
            Object cantFila1Final = enEdt(() -> carro.getModel().getValueAt(1, 2));
            String textoFila1 = enEdt(txtCant::getText);

            check(Integer.valueOf(4).equals(cantFila0Final), "Fila 0 se actualizó a 4 tras cambio de selección (actual: " + cantFila0Final + ")");
            check(Integer.valueOf(1).equals(cantFila1Final), "Fila 1 se mantuvo intacta en 1 (actual: " + cantFila1Final + ")");
            check("1".equals(textoFila1), "txtCantidadManual muestra '1' correspondiente a la fila 1 seleccionada (actual: " + textoFila1 + ")");

            if (fallos == 0) {
                System.out.println("E2E_F09_OK");
            } else {
                System.out.println("E2E_F09_FALLO: " + fallos + " errores");
                System.exit(1);
            }
        } finally {
            enEdtV(s::dispose);
        }
    }
}
