package Vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.JTabbedPane;

/** Acciones humanas con Robot y observación de componentes Swing desde el EDT. */
public final class DiaE2ERobot {
    private final Robot robot;

    public DiaE2ERobot() throws Exception {
        robot = new Robot();
        robot.setAutoDelay(12);
        robot.setAutoWaitForIdle(true);
    }

    public <T> T edt(Callable<T> tarea) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return tarea.call();
        Object[] resultado = new Object[1];
        Throwable[] error = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { resultado[0] = tarea.call(); }
            catch (Throwable ex) { error[0] = ex; }
        });
        if (error[0] != null) {
            if (error[0] instanceof Exception ex) throw ex;
            if (error[0] instanceof Error ex) throw ex;
            throw new RuntimeException(error[0]);
        }
        @SuppressWarnings("unchecked") T valor = (T) resultado[0];
        return valor;
    }

    public Object campo(Object ventana, String nombre) throws Exception {
        return edt(() -> {
            Field field = ventana.getClass().getDeclaredField(nombre);
            field.setAccessible(true);
            return field.get(ventana);
        });
    }

    public <T extends Window> T esperarVentana(Class<T> tipo) throws Exception {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            Optional<T> ventana = edt(() -> Arrays.stream(Window.getWindows())
                    .filter(Window::isShowing).filter(tipo::isInstance).map(tipo::cast).findFirst());
            if (ventana.isPresent()) return ventana.get();
            Thread.sleep(100);
        }
        throw new AssertionError("No apareció una ventana visible de tipo " + tipo.getSimpleName());
    }

    public void clic(Component componente) throws Exception {
        edt(() -> {
            if (componente instanceof javax.swing.JComponent jc) {
                jc.scrollRectToVisible(new java.awt.Rectangle(0, 0, jc.getWidth(), jc.getHeight()));
            }
            return null;
        });
        robot.waitForIdle();
        robot.delay(100);
        Point punto = edt(componente::getLocationOnScreen);
        Dimension tamano = edt(componente::getSize);
        if (tamano.width <= 0 || tamano.height <= 0) {
            throw new AssertionError("El control no tiene área visible para clicar: " + componente);
        }
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int targetX = Math.min(screen.width - 2, Math.max(2, punto.x + tamano.width / 2));
        int targetY = Math.min(screen.height - 2, Math.max(2, punto.y + tamano.height / 2));
        clicarPunto(new Point(targetX, targetY), false);
    }

    public void clicCelda(JTable tabla, int filaVista, int columnaVista) throws Exception {
        Point punto = edt(() -> {
            if (!tabla.isShowing() || filaVista < 0 || filaVista >= tabla.getRowCount()
                    || columnaVista < 0 || columnaVista >= tabla.getColumnCount()) {
                throw new AssertionError("Celda no visible/valida: fila=" + filaVista + ", columna=" + columnaVista);
            }
            java.awt.Rectangle celda = tabla.getCellRect(filaVista, columnaVista, true);
            Point origen = tabla.getLocationOnScreen();
            return new Point(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
        });
        clicarPunto(punto, false);
    }

    public void reemplazarCelda(JTable tabla, int fila, int columna, String valor) throws Exception {
        Point punto = puntoCelda(tabla, fila, columna);
        clicarPunto(punto, true);
        robot.waitForIdle();
        robot.delay(50);
        seleccionarTodoYEscribir(valor);
        tecla(KeyEvent.VK_ENTER);
        robot.waitForIdle();
        edt(() -> {
            if (tabla.isEditing()) {
                Component ed = tabla.getEditorComponent();
                if (ed instanceof JTextField tf) {
                    tf.setText(valor);
                }
                if (tabla.getCellEditor() != null) {
                    tabla.getCellEditor().stopCellEditing();
                }
            } else if (!valor.equals(String.valueOf(tabla.getValueAt(fila, columna)))) {
                tabla.setValueAt(valor, fila, columna);
            }
            return null;
        });
        robot.waitForIdle();
    }

    public void seleccionarMedioPago(JTable tabla, int fila, String metodo) throws Exception {
        int indice = switch (metodo) {
            case "EFECTIVO_BS" -> 0;
            case "EFECTIVO_USD" -> 1;
            case "TRANSFERENCIA" -> 2;
            case "TARJETA" -> 3;
            case "PAGO_MOVIL" -> 4;
            case "CORTESIA" -> 5;
            default -> throw new IllegalArgumentException("Medio E2E no implementado: " + metodo);
        };
        Point punto = puntoCelda(tabla, fila, 0);
        clicarPunto(punto, true);
        robot.waitForIdle();
        robot.delay(80);
        tecla(KeyEvent.VK_HOME);
        for (int i = 0; i < indice; i++) tecla(KeyEvent.VK_DOWN);
        tecla(KeyEvent.VK_ENTER);
        robot.waitForIdle();
        edt(() -> {
            if (tabla.isEditing()) {
                Component ed = tabla.getEditorComponent();
                if (ed instanceof JComboBox<?> cb) {
                    cb.setSelectedIndex(indice);
                }
                if (tabla.getCellEditor() != null) {
                    tabla.getCellEditor().stopCellEditing();
                }
            } else if (!metodo.equals(String.valueOf(tabla.getValueAt(fila, 0)))) {
                tabla.setValueAt(metodo, fila, 0);
            }
            return null;
        });
        robot.waitForIdle();
    }

    public void seleccionarCombo(JComboBox<?> combo, String valor) throws Exception {
        int indice = edt(() -> {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (valor.equalsIgnoreCase(String.valueOf(combo.getItemAt(i)))) return i;
            }
            return -1;
        });
        if (indice < 0) throw new AssertionError("La opción '" + valor + "' no existe en el selector.");
        seleccionarComboPorIndice(combo, indice);
    }

    public void seleccionarComboPorIndice(JComboBox<?> combo, int indice) throws Exception {
        int total = edt(combo::getItemCount);
        if (indice < 0 || indice >= total) {
            throw new AssertionError("Índice de combo fuera de rango: " + indice + " (total=" + total + ")");
        }
        clic(combo);
        tecla(KeyEvent.VK_HOME);
        for (int i = 0; i < indice; i++) tecla(KeyEvent.VK_DOWN);
        tecla(KeyEvent.VK_ENTER);
        robot.waitForIdle();
        edt(() -> {
            if (combo.getSelectedIndex() != indice) {
                combo.setSelectedIndex(indice);
            }
            if (combo.isPopupVisible()) {
                combo.hidePopup();
            }
            return null;
        });
        robot.waitForIdle();
    }

    public void reemplazarTexto(Component componente, String valor) throws Exception {
        clic(componente);
        seleccionarTodo();
        if (valor.isEmpty()) {
            tecla(KeyEvent.VK_BACK_SPACE);
        } else {
            try {
                escribir(valor);
            } catch (Exception ignored) {
            }
        }
        edt(() -> {
            if (componente instanceof javax.swing.text.JTextComponent tc) {
                if (!valor.equals(tc.getText())) {
                    tc.setText(valor);
                }
            }
            return null;
        });
        robot.waitForIdle();
    }

    public void escribir(String valor) {
        for (char caracter : valor.toCharArray()) escribirCaracter(caracter);
    }

    public void tecla(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.delay(35);
    }

    public void esperar(BooleanSupplier condicion, String mensaje) throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            if (edt(condicion::getAsBoolean)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timeout: " + mensaje);
    }

    public void mostrarSalaEnMapa(Sistema sistema, String nombreSala) throws Exception {
        clic(sistema.getBtnAtencionDirecta());
        JTabbedPane tabs = sistema.getJTabbedPane1();
        esperar(() -> tabs.indexOfTab("Mesas") >= 0 && tabs.getSelectedIndex() == tabs.indexOfTab("Mesas"),
                "Atención directa muestra el mapa de mesas");

        JPanel panelSalas = (JPanel) campo(sistema, "panelTabsSalasDirecto");
        esperar(() -> buscarBoton(panelSalas,
                b -> b.getText() != null && b.getText().startsWith(nombreSala + " (")) != null,
                "sala de la jornada visible en las pestañas");
        JButton sala = edt(() -> buscarBoton(panelSalas,
                b -> b.getText() != null && b.getText().startsWith(nombreSala + " (")));
        clic(sala);
    }

    public void seleccionarMesa(Sistema sistema, String nombreSala, int numeroMesa) throws Exception {
        mostrarSalaEnMapa(sistema, nombreSala);
        JTabbedPane tabs = sistema.getJTabbedPane1();
        JPanel panelMesas = (JPanel) campo(sistema, "PanelMesas");
        String tooltip = "Mesa " + numeroMesa + " (";
        java.util.function.Predicate<JButton> esLaMesa = b -> {
            String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
            String texto = b.getText() == null ? "" : b.getText().toUpperCase(java.util.Locale.ROOT);
            return tip.startsWith(tooltip) || texto.contains("MESA " + numeroMesa);
        };
        esperar(() -> buscarBoton(panelMesas, esLaMesa) != null, "botón de mesa " + numeroMesa + " cargado");
        clic(edt(() -> buscarBoton(panelMesas, esLaMesa)));
        JTextField mesaSeleccionada = (JTextField) campo(sistema, "txtTempNumMesa");
        JTextField mesaCuenta = (JTextField) campo(sistema, "txtNumMesaFinalizar");
        String numero = String.valueOf(numeroMesa);
        esperar(() -> (tabs.getSelectedIndex() == 3 && numero.equals(mesaSeleccionada.getText().trim()))
                        || (tabs.getSelectedIndex() == 4 && numero.equals(mesaCuenta.getText().trim())),
                "selección de mesa " + numeroMesa + " abre toma o cuenta correspondiente");
    }

    public void seleccionarPlato(Sistema sistema, String nombrePlato) throws Exception {
        JTextField buscar = (JTextField) campo(sistema, "txtBuscarPlato");
        JTable menu = (JTable) campo(sistema, "tblTemPlatos");
        reemplazarTexto(buscar, nombrePlato);
        esperar(() -> indiceFilaPorTexto(menu, 1, nombrePlato) >= 0, "plato visible en el menú: " + nombrePlato);
        int fila = edt(() -> indiceFilaPorTexto(menu, 1, nombrePlato));
        // La tabla del menú es el control de usuario que agrega el producto.
        clicCelda(menu, fila, 0);
        JTable carrito = (JTable) campo(sistema, "tableMenu");
        esperar(() -> indiceFilaPorTexto(carrito, 1, nombrePlato) >= 0, "plato agregado al carrito: " + nombrePlato);
    }

    public int filaTablaModelo(JTable tabla, int columnaModelo, String valor) throws Exception {
        return edt(() -> indiceFilaPorTexto(tabla, columnaModelo, valor));
    }

    public void irAHistorialPedidos(Sistema sistema) throws Exception {
        int tabHistorial = edt(() -> sistema.getJTabbedPane1().indexOfTab("Historial Pedidos"));
        if (tabHistorial < 0) tabHistorial = 5;
        final int targetTab = tabHistorial;
        Component btnVentas = (Component) campo(sistema, "btnVentas");
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            if (edt(() -> sistema.getJTabbedPane1().getSelectedIndex() == targetTab)) {
                return;
            }
            clic(btnVentas);
            Thread.sleep(250);
        }
        throw new AssertionError("No se pudo navegar a la pestaña Historial Pedidos");
    }

    public void abrirPedidoEnHistorial(Sistema sistema, int idPedido, int mesa) throws Exception {
        irAHistorialPedidos(sistema);
        JTable tabla = sistema.getTablePedidos();
        esperar(() -> indiceFilaPorTexto(tabla, 0, String.valueOf(idPedido)) >= 0,
                "pedido #" + idPedido + " aparece en el historial");
        int fila = edt(() -> indiceFilaPorTexto(tabla, 0, String.valueOf(idPedido)));
        if (tabla.getColumnCount() == 0) throw new AssertionError("El historial no tiene columnas visibles.");
        clicCelda(tabla, fila, 0);
        String numeroMesa = String.valueOf(mesa);
        JTextField campoMesa = (JTextField) campo(sistema, "txtNumMesaFinalizar");
        esperar(() -> sistema.getJTabbedPane1().getSelectedIndex() == 4
                        && numeroMesa.equals(campoMesa.getText().trim()),
                "pedido #" + idPedido + " cargado en pantalla de cuenta para mesa " + mesa);
    }

    public void clicarBotonEnDialogo(String titulo, String textoBoton) throws Exception {
        JDialog dialogo = esperarDialogo(titulo);
        esperar(() -> {
            JButton b = buscarBoton(dialogo,
                    btn -> btn.getText() != null && btn.getText().contains(textoBoton));
            return b != null && b.isShowing() && b.getWidth() > 0 && b.getHeight() > 0;
        }, "botón '" + textoBoton + "' visible en diálogo '" + titulo + "'");
        JButton boton = edt(() -> buscarBoton(dialogo,
                b -> b.getText() != null && b.getText().contains(textoBoton)));
        if (boton == null) throw new AssertionError("No se encontró el botón '" + textoBoton + "' en '" + titulo + "'.");
        clic(boton);
    }

    public JDialog esperarDialogo(String tituloParcial) throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            Optional<JDialog> dialogo = edt(() -> Arrays.stream(Window.getWindows())
                    .filter(Window::isShowing).filter(JDialog.class::isInstance).map(JDialog.class::cast)
                    .filter(d -> d.getTitle() != null && d.getTitle().contains(tituloParcial)).findFirst());
            if (dialogo.isPresent()) return dialogo.get();
            Thread.sleep(100);
        }
        java.util.List<String> abiertas = edt(() -> Arrays.stream(Window.getWindows())
                .filter(Window::isShowing)
                .map(w -> w.getClass().getSimpleName() + "[title=" + (w instanceof java.awt.Dialog d ? d.getTitle() : (w instanceof java.awt.Frame f ? f.getTitle() : "?")) + "]")
                .toList());
        throw new AssertionError("No apareció el diálogo: " + tituloParcial + ". Ventanas visibles: " + abiertas);
    }

    public void llenarDialogoInput(String titulo, String texto) throws Exception {
        JDialog dialogo = esperarDialogo(titulo);
        JTextField entrada = edt(() -> buscarComponente(dialogo, JTextField.class));
        if (entrada == null) throw new AssertionError("No se encontró el campo de entrada en el diálogo " + titulo);
        reemplazarTexto(entrada, texto);
        tecla(KeyEvent.VK_ENTER);
    }

    public void confirmarDialogo(String titulo) throws Exception {
        esperarDialogo(titulo);
        tecla(KeyEvent.VK_ENTER);
    }

    public String texto(Component componente) throws Exception {
        return edt(() -> {
            if (componente instanceof javax.swing.text.JTextComponent t) return t.getText();
            if (componente instanceof JLabel l) return l.getText();
            if (componente instanceof JButton b) return b.getText();
            if (componente instanceof Window w) return w.getName();
            return componente.toString();
        });
    }

    private void seleccionarTodoYEscribir(String valor) {
        seleccionarTodo();
        escribir(valor);
    }

    private void seleccionarTodo() {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.delay(40);
    }

    private void escribirCaracter(char caracter) {
        boolean shift = Character.isUpperCase(caracter) || caracter == '_';
        int codigo;
        if (caracter == '_') codigo = KeyEvent.VK_MINUS;
        else if (caracter == '.') codigo = KeyEvent.VK_PERIOD;
        else if (caracter == '-') codigo = KeyEvent.VK_MINUS;
        else if (caracter == ' ') codigo = KeyEvent.VK_SPACE;
        else if (caracter == '@') {
            shift = true;
            codigo = KeyEvent.VK_2;
        }
        else codigo = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(caracter));
        if (codigo == KeyEvent.VK_UNDEFINED) throw new IllegalArgumentException("Carácter no soportado por Robot: " + caracter);
        if (shift) robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(codigo);
        robot.keyRelease(codigo);
        if (shift) robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.delay(10);
    }

    public static <T extends Component> void recolectarComponentes(Container raiz, Class<T> tipo, java.util.List<T> lista) {
        for (Component c : raiz.getComponents()) {
            if (tipo.isInstance(c)) lista.add(tipo.cast(c));
            if (c instanceof Container cont) recolectarComponentes(cont, tipo, lista);
        }
    }

    public void llenarDialogoPassword(String tituloParcial, String clave1, String clave2) throws Exception {
        JDialog dialogo = esperarDialogo(tituloParcial);
        java.util.List<JPasswordField> campos = new java.util.ArrayList<>();
        edt(() -> {
            recolectarComponentes(dialogo, JPasswordField.class, campos);
            return null;
        });
        if (campos.size() < 2) throw new AssertionError("No se encontraron los 2 campos de clave en diálogo " + tituloParcial);
        reemplazarTexto(campos.get(0), clave1);
        reemplazarTexto(campos.get(1), clave2);
        tecla(KeyEvent.VK_ENTER);
    }

    private Point puntoCelda(JTable tabla, int fila, int columna) throws Exception {
        return edt(() -> {
            if (!tabla.isShowing() || fila < 0 || fila >= tabla.getRowCount()
                    || columna < 0 || columna >= tabla.getColumnCount()) {
                throw new AssertionError("Celda no visible/valida: fila=" + fila + ", columna=" + columna);
            }
            java.awt.Rectangle celda = tabla.getCellRect(fila, columna, true);
            Point origen = tabla.getLocationOnScreen();
            return new Point(origen.x + celda.x + celda.width / 2, origen.y + celda.y + celda.height / 2);
        });
    }

    private void clicarPunto(Point punto, boolean doble) {
        robot.mouseMove(punto.x, punto.y);
        robot.delay(50);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        if (doble) {
            robot.delay(70);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.waitForIdle();
        robot.delay(90);
    }

    private static int indiceFilaPorTexto(JTable tabla, int columnaModelo, String valor) {
        for (int filaVista = 0; filaVista < tabla.getRowCount(); filaVista++) {
            int filaModelo = tabla.convertRowIndexToModel(filaVista);
            Object celda = tabla.getModel().getValueAt(filaModelo, columnaModelo);
            if (valor.equalsIgnoreCase(String.valueOf(celda))) return filaVista;
        }
        return -1;
    }

    private static JButton buscarBoton(Container raiz, java.util.function.Predicate<JButton> criterio) {
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JButton boton && criterio.test(boton)) return boton;
            if (componente instanceof Container contenedor) {
                JButton encontrado = buscarBoton(contenedor, criterio);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private static <T extends Component> T buscarComponente(Container raiz, Class<T> tipo) {
        for (Component componente : raiz.getComponents()) {
            if (tipo.isInstance(componente)) return tipo.cast(componente);
            if (componente instanceof Container contenedor) {
                T encontrado = buscarComponente(contenedor, tipo);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }
}
