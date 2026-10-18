package Vista;

import Controlador.CategoriaControlador;
import Controlador.FavoritoControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.CategoriaDao;
import Modelo.CategoriaRepositorio;
import Modelo.ConfiguracionSistemaDao;
import Modelo.FavoritoDao;
import Modelo.LoginDao;
import Modelo.PedidosDao;
import Modelo.PlatosDao;
import Modelo.SalasDao;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import Servicio.CategoriaServicio;
import Servicio.CierreCajaServicio;
import Servicio.ConsultaPedidosServicio;
import Servicio.FavoritoServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Comprueba en Xvfb que Forma de Pago está visible y se puede cambiar con mouse/teclado. */
public final class MetodoPagoVisibleTest {

    @org.junit.Test
    public void ejecutarE2E() throws Exception {
        if (!"SI".equalsIgnoreCase(System.getProperty("e2e.permitir"))) {
            org.junit.Assume.assumeTrue("Requiere entorno Xvfb virtual (-De2e.permitir=SI). Se omite en unitarias estándar.", false);
            return;
        }
        main(new String[0]);
    }

    private static int fallos;

    private static void check(boolean condicion, String mensaje) {
        System.out.println((condicion ? "OK   " : "FALLO ") + mensaje);
        if (!condicion) fallos++;
    }

    private static Object campo(Sistema sistema, String nombre) throws Exception {
        var field = Sistema.class.getDeclaredField(nombre);
        field.setAccessible(true);
        return field.get(sistema);
    }

    private static <T> T enEdt(java.util.concurrent.Callable<T> accion) throws Exception {
        AtomicReference<T> valor = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                valor.set(accion.call());
            } catch (Throwable ex) {
                error.set(ex);
            }
        });
        if (error.get() != null) throw new RuntimeException(error.get());
        return valor.get();
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Robot robot = new Robot();
        robot.setAutoDelay(80);

        Usuario admin = new Usuario(1, "Administrador", "admin", "admin", "Administrador");
        PoliticaAcceso politica = new PoliticaAcceso(admin);
        PedidosDao pedidosDao = new PedidosDao();
        LoginDao loginDao = new LoginDao();
        PedidoPdfServicio pdf = new PedidoPdfServicio(
                pedidosDao::verPedido, pedidosDao::verPedidoDetalle, loginDao::datosEmpresa,
                GeneradorPdfPedido.conEstructuraMensual(GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto()),
                archivo -> true);
        CierreCajaServicio cierre = new CierreCajaServicio(
                new Modelo.CierreCajaDao(), loginDao::datosEmpresa, new Servicio.GeneradorPdfCierre());
        PedidosControlador pedidos = new PedidosControlador(
                new PedidoServicio(pedidosDao), pdf, politica,
                new ConsultaPedidosServicio(pedidosDao, politica), cierre);
        Sistema sistema = enEdt(() -> new Sistema(admin,
                new SalasControlador(new SalasServicio(new SalasDao(), politica)),
                new PlatosControlador(new PlatosServicio(new PlatosDao(), politica)),
                pedidos,
                new CategoriaControlador(new CategoriaServicio(new CategoriaDao(), politica)),
                new FavoritoControlador(new FavoritoServicio(new FavoritoDao(), politica)),
                loginDao));

        try {
            enEdt(() -> {
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                JTabbedPane tabs = (JTabbedPane) campo(sistema, "jTabbedPane1");
                tabs.setSelectedIndex(tabs.indexOfTab("Finalizar Pedido"));
                return null;
            });
            robot.delay(1800);

            javax.swing.JTable tablaPagos = (javax.swing.JTable) campo(sistema, "tablaPagosFac");
            JButton btnAgregarPago = (JButton) campo(sistema, "btnAgregarPagoFac");
            JButton btnQuitarPago = (JButton) campo(sistema, "btnQuitarPagoFac");
            JButton finalizar = (JButton) campo(sistema, "btnFinalizar");
            JTabbedPane tabs = (JTabbedPane) campo(sistema, "jTabbedPane1");
            Rectangle visibleTabla = enEdt(tablaPagos::getVisibleRect);
            Point puntoTabla = enEdt(tablaPagos::getLocationOnScreen);
            String pestana = enEdt(() -> tabs.getTitleAt(tabs.getSelectedIndex()));
            int filasIniciales = enEdt(tablaPagos::getRowCount);
            String medioInicial = enEdt(() -> String.valueOf(tablaPagos.getValueAt(0, 0)));
            String monedaInicial = enEdt(() -> String.valueOf(tablaPagos.getValueAt(0, 1)));
            check("Finalizar Pedido".equals(pestana), "pestaña Finalizar Pedido seleccionada");
            check(enEdt(tablaPagos::isShowing), "tabla de pagos inline se muestra en pantalla");
            check(visibleTabla.width == tablaPagos.getWidth() && visibleTabla.height == tablaPagos.getHeight()
                    && visibleTabla.width > 0 && visibleTabla.height > 0,
                    "tabla de pagos completamente visible; visibleRect=" + visibleTabla);
            check(enEdt(btnAgregarPago::isShowing), "botón + Medio se muestra en pantalla");
            check(enEdt(btnQuitarPago::isShowing), "botón − Medio se muestra en pantalla");
            check(puntoTabla.y < enEdt(finalizar::getLocationOnScreen).y,
                    "tabla de pagos está por encima del botón Finalizar; y=" + puntoTabla.y);
            Rectangle visibleFinalizar = enEdt(finalizar::getVisibleRect);
            check(visibleFinalizar.width == finalizar.getWidth()
                    && visibleFinalizar.height == finalizar.getHeight(),
                    "botón Finalizar completamente visible; visibleRect=" + visibleFinalizar);
            check(filasIniciales >= 1, "tabla inicia con al menos una fila");
            check("EFECTIVO_BS".equals(medioInicial) && "VES".equals(monedaInicial),
                    "primera fila inicia con EFECTIVO_BS / VES");
            System.out.println("UBICACION Tabla Pagos: x=" + puntoTabla.x + ", y=" + puntoTabla.y
                    + ", ancho=" + tablaPagos.getWidth() + ", alto=" + tablaPagos.getHeight()
                    + ", filas=" + filasIniciales);

            Path captura = Path.of("/tmp/opencode/e2e_forma_pago.png");
            Files.createDirectories(captura.getParent());
            Rectangle ventana = enEdt(() -> new Rectangle(sistema.getLocationOnScreen(), sistema.getSize()));
            BufferedImage imagen = robot.createScreenCapture(ventana);
            ImageIO.write(imagen, "png", captura.toFile());
            System.out.println("CAPTURA " + captura);

            Point pAgregar = enEdt(btnAgregarPago::getLocationOnScreen);
            robot.mouseMove(pAgregar.x + btnAgregarPago.getWidth() / 2, pAgregar.y + btnAgregarPago.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(350);
            int filasDespuesAgregar = enEdt(tablaPagos::getRowCount);
            check(filasDespuesAgregar == filasIniciales + 1,
                    "botón + Medio agrega una nueva fila (antes=" + filasIniciales + ", despues=" + filasDespuesAgregar + ")");

            Point pQuitar = enEdt(btnQuitarPago::getLocationOnScreen);
            robot.mouseMove(pQuitar.x + btnQuitarPago.getWidth() / 2, pQuitar.y + btnQuitarPago.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(350);
            int filasDespuesQuitar = enEdt(tablaPagos::getRowCount);
            check(filasDespuesQuitar == filasIniciales,
                    "botón − Medio quita la fila seleccionada (actual=" + filasDespuesQuitar + ")");
        } finally {
            enEdt(() -> {
                sistema.dispose();
                return null;
            });
        }
        System.out.println(fallos == 0 ? "E2E_OK" : "E2E_FALLO(" + fallos + ")");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
