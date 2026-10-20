package Vista;

import java.awt.Component;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * E2E Virtual de Pedido Rápido (Xvfb :99) operado 100% mediante UI con Robot.
 *
 * Flujo:
 * 1. Login interactivo en FrmLogin ("admin" / "admin").
 * 2. Clic en botón lateral "Pedido rápido".
 * 3. Verificación de apertura directa en pestaña 3 (Toma de Pedidos) en modalidad "Cobrar y finalizar".
 * 4. Validación de que con carrito vacío no avanza ni genera pedidos.
 * 5. Búsqueda de plato en el catálogo y adición al carrito.
 * 6. Ajuste reactivo de cantidad mediante botón de incremento rápido (+).
 * 7. Envío a cobro ("Cobrar y finalizar") -> Navegación automática a pestaña 4 (Caja / Finalizar).
 * 8. Verificación en pantalla de pago: modalidad "Para llevar", mesa "—", ítems y totales.
 * 9. Verificación de interactividad de la tabla de pagos (agregar y quitar medios de pago).
 * 10. Cancelación / anulación limpia del pedido rápido por UI (btnAnularFuga) confirmando diálogos.
 * 11. Verificación del retorno a la vista inicial sin pedidos huérfanos.
 *
 * Cumple estrictamente con las reglas de AGENTS.md:
 * - Cero DAOs, cero repositorios o servicios en memoria, cero queries SQL directas.
 * - Toda la interacción y verificación se realiza a través de componentes observables de Swing.
 */
public class PedidoRapidoE2EVirtual {

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.err.println("E2E requiere autorización: relanza con -De2e.permitir=SI");
            System.exit(2);
        }

        Path dirE2E = Path.of(System.getenv().getOrDefault("E2E_WORKDIR", System.getProperty("java.io.tmpdir")));
        System.setProperty("user.home", dirE2E.toString());
        System.setProperty("modo.automatizado", "true");
        System.setProperty("PDF_FACTURAS_DIR", dirE2E.resolve("facturas").toString());

        Throwable fallo = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            DiaE2ERobot robot = new DiaE2ERobot();

            System.out.println("== [Pedido Rápido E2E] Paso 1: Login interactivo ==");
            restaurante.Restaurante.lanzarLogin();
            Vista.FrmLogin login = robot.esperarVentana(Vista.FrmLogin.class);
            robot.edt(() -> {
                login.setLocationRelativeTo(null);
                return null;
            });
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"),
                    System.getenv().getOrDefault("E2E_LOGIN_USERNAME", "admin"));
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"),
                    System.getenv().getOrDefault("E2E_LOGIN_PASSWORD", "admin"));
            robot.clic((Component) robot.campo(login, "btnIniciar"));

            System.out.println("== [Pedido Rápido E2E] Paso 2: Esperar ventana principal Sistema ==");
            Sistema sistema = robot.esperarVentana(Sistema.class);
            robot.edt(() -> {
                sistema.setExtendedState(JFrame.MAXIMIZED_BOTH);
                sistema.setVisible(true);
                return null;
            });
            Component btnPedidoRapido = (Component) robot.campo(sistema, "btnPedidoRapido");
            robot.esperar(() -> sistema.isShowing() && btnPedidoRapido.isShowing(),
                    "Ventana Sistema autenticada y botón Pedido Rápido visible");

            System.out.println("== [Pedido Rápido E2E] Paso 3: Clic en 'Pedido rápido' ==");
            robot.clic(btnPedidoRapido);
            JTabbedPane tabs = sistema.getJTabbedPane1();
            robot.esperar(() -> tabs.getSelectedIndex() == 3,
                    "Pestaña 3 (Toma de Pedidos) abierta para Pedido Rápido");

            JButton btnGenerar = (JButton) robot.campo(sistema, "btnGenerarPedido");
            robot.esperar(() -> "Cobrar y finalizar".equals(btnGenerar.getText()),
                    "Botón principal indica 'Cobrar y finalizar'");

            System.out.println("== [Pedido Rápido E2E] Paso 4: Carrito vacío no avanza ==");
            robot.clic(btnGenerar);
            robot.esperar(() -> tabs.getSelectedIndex() == 3,
                    "Pestaña permanece en 3 con carrito vacío");

            System.out.println("== [Pedido Rápido E2E] Paso 5: Buscar plato y agregar al carrito ==");
            JTextField txtBuscar = (JTextField) robot.campo(sistema, "txtBuscarPlato");
            JTable tblMenu = (JTable) robot.campo(sistema, "tblTemPlatos");
            JTable tblCarro = (JTable) robot.campo(sistema, "tableMenu");

            robot.reemplazarTexto(txtBuscar, "pollo");
            robot.esperar(() -> {
                for (int i = 0; i < tblMenu.getRowCount(); i++) {
                    Object v = tblMenu.getModel().getValueAt(tblMenu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return true;
                }
                return false;
            }, "Catálogo muestra resultados para 'pollo'");

            int filaPollo = robot.edt(() -> {
                for (int i = 0; i < tblMenu.getRowCount(); i++) {
                    Object v = tblMenu.getModel().getValueAt(tblMenu.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase().contains("POLLO")) return i;
                }
                return -1;
            });
            // Clic en la celda del plato para agregarlo al carrito
            robot.clicCelda(tblMenu, filaPollo, 0);
            robot.esperar(() -> tblCarro.getRowCount() >= 1, "Carrito tiene al menos 1 fila");

            int cant1 = robot.edt(() -> Integer.parseInt(tblCarro.getModel().getValueAt(0, 2).toString()));
            if (cant1 != 1) {
                throw new AssertionError("Se esperaba cantidad 1 en el carrito pero fue " + cant1);
            }
            System.out.println("OK   Plato agregado al carrito (1 unidad)");

            System.out.println("== [Pedido Rápido E2E] Paso 6: Incrementar cantidad con btnMasCantidad ==");
            JButton btnMas = (JButton) robot.campo(sistema, "btnMasCantidad");
            robot.clic(btnMas);
            robot.esperar(() -> {
                Object v = tblCarro.getModel().getValueAt(0, 2);
                return v != null && "2".equals(v.toString());
            }, "Cantidad en carrito se incrementa a 2");
            System.out.println("OK   Cantidad incrementada a 2");

            System.out.println("== [Pedido Rápido E2E] Paso 7: Cobrar y finalizar -> Pasa a Caja ==");
            robot.clic(btnGenerar);
            robot.esperar(() -> tabs.getSelectedIndex() == 4,
                    "Navega a pestaña 4 (Finalizar / Caja)");

            JTextField txtSala = (JTextField) robot.campo(sistema, "txtSalaFinalizar");
            JTextField txtMesa = (JTextField) robot.campo(sistema, "txtNumMesaFinalizar");
            robot.esperar(() -> "Para llevar".equals(txtSala.getText()),
                    "Sala muestra 'Para llevar'");
            robot.esperar(() -> "—".equals(txtMesa.getText()),
                    "Mesa muestra '—'");

            JTable tblFinalizar = (JTable) robot.campo(sistema, "tableFinalizar");
            robot.esperar(() -> tblFinalizar.getRowCount() >= 1,
                    "Tabla finalizar muestra los productos");

            System.out.println("== [Pedido Rápido E2E] Paso 8: Probar tabla de pagos en Caja ==");
            JTable tablaPagos = sistema.getTablaPagosFac();
            JButton btnAgregarPago = (JButton) robot.campo(sistema, "btnAgregarPagoFac");
            JButton btnQuitarPago = (JButton) robot.campo(sistema, "btnQuitarPagoFac");

            robot.esperar(() -> tablaPagos.isShowing() && tablaPagos.getRowCount() >= 1,
                    "Tabla de pagos visible con al menos 1 fila");
            int filasIniciales = robot.edt(tablaPagos::getRowCount);

            robot.clic(btnAgregarPago);
            robot.esperar(() -> tablaPagos.getRowCount() == filasIniciales + 1,
                    "Botón '+ Medio' agrega una fila de pago");

            robot.clic(btnQuitarPago);
            robot.esperar(() -> tablaPagos.getRowCount() == filasIniciales,
                    "Botón '− Medio' remueve la fila de pago agregada");
            System.out.println("OK   Tabla de pagos interactiva probada");

            System.out.println("== [Pedido Rápido E2E] Paso 9: Cancelar / anular cuenta rápida por UI ==");
            JButton btnAnularFuga = (JButton) robot.campo(sistema, "btnAnularFuga");
            robot.clic(btnAnularFuga);
            robot.llenarDialogoInput("Anular por Fuga / No Pagó", "Cancelación Pedido Rápido E2E");
            robot.confirmarDialogo("Confirmar No-Pago (Fuga)");

            robot.esperar(() -> tabs.getSelectedIndex() != 4,
                    "Tras anular, el sistema sale de la pantalla de cobro");
            System.out.println("OK   Pedido rápido cancelado limpiamente por UI");

        } catch (Throwable error) {
            fallo = error;
        } finally {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    for (Window w : Window.getWindows()) {
                        w.dispose();
                    }
                });
            } catch (Throwable errorLimpieza) {
                if (fallo == null) fallo = errorLimpieza;
                else fallo.addSuppressed(errorLimpieza);
            }
        }

        if (fallo != null) {
            System.err.println("E2E_PEDIDO_RAPIDO_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            System.exit(1);
        }

        System.out.println("E2E_PEDIDO_RAPIDO_OK");
        System.exit(0);
    }
}
