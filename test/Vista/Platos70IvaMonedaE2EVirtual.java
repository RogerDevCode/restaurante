package Vista;

import java.awt.Component;
import java.awt.Window;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** E2E UI-only de catálogo: 70 productos, IVA, moneda y asociación de categoría. */
public final class Platos70IvaMonedaE2EVirtual {
    private static int objetivo = 70;
    private static final String PREFIJO = "E2E70-";
    private static final int[][] PATRONES = {
        {1, 0, 0}, {1, 1, 0}, {0, 0, 0}, {0, 1, 0},
        {1, 0, 1}, {1, 1, 1}, {0, 0, 1}, {0, 1, 1},
    };

    private record Spec(int numero, String nombre, String moneda, boolean aplicaIva,
            boolean incluyeIva, BigDecimal precio, String categoria) {
        boolean incluyeEsperado() {
            return aplicaIva && incluyeIva;
        }
    }

    private Platos70IvaMonedaE2EVirtual() {}

    public static void main(String[] args) throws Exception {
        DiaE2EFixture.exigirBdAislada();
        if (args.length > 0) objetivo = Integer.parseInt(args[0]);
        exigir(objetivo > 0 && objetivo <= 70, "el E2E admite entre 1 y 70 productos");
        Path workdir = Path.of(System.getenv("E2E_WORKDIR"));
        System.setProperty("modo.automatizado", "true");
        System.setProperty("user.home", workdir.toString());
        System.setProperty("PDF_FACTURAS_DIR", workdir.resolve("facturas").toString());
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        DiaE2ERobot robot = new DiaE2ERobot();
        Sistema sistema = null;
        Throwable fallo = null;
        try {
            // El arranque, el login y toda la preparación ocurren como en la operación real.
            restaurante.Restaurante.lanzarLogin();
            FrmLogin login = robot.esperarVentana(FrmLogin.class);
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtIdentificador"), "admin");
            robot.reemplazarTexto((JTextField) robot.campo(login, "txtPass"), "admin");
            robot.clic((Component) robot.campo(login, "btnIniciar"));
            sistema = robot.esperarVentana(Sistema.class);
            Sistema ventana = sistema;
            robot.edt(() -> {
                ventana.setBounds(0, 0, 1366, 711);
                ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
                ventana.setVisible(true);
                ventana.toFront();
                return null;
            });
            robot.esperar(() -> ventana.isShowing() && ventana.getBtnPlatos().isShowing(),
                    "sesión de Administrador iniciada por la interfaz");

            BigDecimal tasa = leerTasaDesdeConfiguracion(ventana, robot);
            String sufijo = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(Locale.ROOT);
            List<String> categorias = crearCategoriasPorInterfaz(ventana, robot, sufijo);
            List<Spec> specs = construirSpecs(sufijo, categorias);

            robot.clic(ventana.getBtnPlatos());
            JTable tabla = ventana.getTablePlatos();
            JTextField nombre = ventana.getTxtNombrePlato();
            JTextField precio = ventana.getTxtPrecioPlato();
            JComboBox<String> moneda = ventana.getCbMonedaBasePlato();
            JComboBox<?> categoria = (JComboBox<?>) robot.campo(ventana, "cbCategoriaFicha");
            var chkAplica = ventana.getChkAplicaIvaPlato();
            var chkIncluye = ventana.getChkPrecioIncluyeIvaPlato();
            var buscar = (JTextField) robot.campo(ventana, "txtBuscarAdminPlatos");
            var guardar = ventana.getBtnGuardarPlato();
            var nuevo = ventana.getBtnNuevoPlato();

            robot.esperar(() -> nombre.isShowing() && tabla.isShowing() && categoria.getItemCount() > 1,
                    "ficha de platos y categorías creadas visibles");
            int creados = 0;
            for (Spec spec : specs) {
                robot.clic(nuevo);
                robot.reemplazarTexto(nombre, spec.nombre());
                robot.seleccionarCombo(moneda, spec.moneda());
                robot.reemplazarTexto(precio, spec.precio().toPlainString());
                robot.seleccionarCombo(categoria, spec.categoria());

                if (robot.edt(chkAplica::isSelected) != spec.aplicaIva()) {
                    robot.clic(chkAplica);
                }
                if (spec.aplicaIva() && robot.edt(chkIncluye::isSelected) != spec.incluyeIva()) {
                    robot.clic(chkIncluye);
                }
                if (!spec.aplicaIva()) {
                    exigir(!robot.edt(chkIncluye::isEnabled) || !robot.edt(chkIncluye::isSelected),
                            "la UI bloquea Incluye IVA en producto exento " + spec.numero());
                }

                robot.clic(guardar);
                robot.esperar(() -> filaPorNombre(tabla, spec.nombre()) >= 0,
                        "producto " + spec.numero() + " guardado y visible en la tabla");
                creados++;
                if (creados % 10 == 0) System.out.println("creados por UI: " + creados + "/" + objetivo);
            }
            exigir(creados == objetivo, "se guardaron " + objetivo + " productos mediante la ficha de la aplicación");

            // Filtrar y comprobar únicamente el modelo visual de la tabla, no consultar la BD.
            robot.reemplazarTexto(buscar, PREFIJO + sufijo);
            robot.esperar(() -> tabla.getRowCount() == objetivo,
                    "la tabla de Platos muestra exactamente los productos de esta ejecución");
            for (Spec spec : specs) {
                int fila = filaPorNombre(tabla, spec.nombre());
                exigir(fila >= 0, "producto visible: " + spec.nombre());
                comprobarFilaCatalogo(tabla, fila, spec, tasa);

                int columnaNombre = columna(tabla, "nombre");
                robot.edt(() -> {
                    tabla.scrollRectToVisible(tabla.getCellRect(fila, columnaNombre, true));
                    return null;
                });
                robot.clicCelda(tabla, fila, columnaNombre);
                robot.esperar(() -> spec.nombre().equals(robotTexto(robot, nombre)),
                        "ficha seleccionada para " + spec.nombre());
                comprobarFicha(robot, ventana, spec, tasa, moneda, precio, categoria, chkAplica, chkIncluye);
            }
            System.out.println("E2E70_OK: creación y verificación exclusivamente por UI");
        } catch (Throwable error) {
            fallo = error;
        } finally {
            try {
                Sistema ventana = sistema;
                SwingUtilities.invokeAndWait(() -> {
                    for (Window abierta : Window.getWindows()) abierta.dispose();
                    if (ventana != null) ventana.dispose();
                });
            } catch (Throwable cierre) {
                if (fallo == null) fallo = cierre;
                else fallo.addSuppressed(cierre);
            }
        }
        if (fallo != null) {
            System.err.println("E2E70_FALLO: " + fallo.getMessage());
            fallo.printStackTrace(System.err);
            throw new AssertionError("Falló el E2E UI-only de platos", fallo);
        }
    }

    private static BigDecimal leerTasaDesdeConfiguracion(Sistema sistema, DiaE2ERobot robot) throws Exception {
        robot.clic(sistema.getBtnConfig());
        JTextField tasa = (JTextField) robot.campo(sistema, "txtTasaConfig");
        robot.esperar(() -> tasa.isShowing() && tasa.getText().trim().matches("[0-9]+(?:\\.[0-9]+)?"),
                "tasa visible en la pantalla Configuración");
        BigDecimal valor = new BigDecimal(robot.edt(tasa::getText).trim());
        exigir(valor.signum() > 0, "tasa de cambio positiva leída desde la UI");
        return valor;
    }

    private static List<String> crearCategoriasPorInterfaz(Sistema sistema, DiaE2ERobot robot,
            String sufijo) throws Exception {
        robot.clic((Component) robot.campo(sistema, "btnCategorias"));
        JList<?> lista = (JList<?>) robot.campo(sistema, "listaCategorias");
        JTextField nombre = (JTextField) robot.campo(sistema, "txtNombreCategoria");
        JComboBox<?> color = (JComboBox<?>) robot.campo(sistema, "cbColorCategoria");
        var guardar = (javax.swing.JButton) robot.campo(sistema, "btnGuardarCategoria");
        robot.esperar(() -> color.getItemCount() > 0, "paleta de colores cargada en el combo");
        List<String> categorias = new ArrayList<>();
        // Una categoría propia basta para cubrir la asignación y deja libre la paleta
        // que la semilla de integración ya usa para sus categorías reales.
        String categoria = "E2E70-CAT-" + sufijo;
        robot.reemplazarTexto(nombre, categoria);
        robot.seleccionarComboPorIndice(color, 0);
        robot.clic(guardar);
        robot.esperar(() -> listaContiene(lista, categoria),
                "categoría creada y visible desde la pantalla Categorías: " + categoria);
        categorias.add(categoria);
        return List.copyOf(categorias);
    }

    private static List<Spec> construirSpecs(String sufijo, List<String> categorias) {
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < objetivo; i++) {
            int[] patron = PATRONES[i % PATRONES.length];
            boolean aplica = patron[0] == 1;
            boolean incluye = patron[1] == 1;
            String moneda = patron[2] == 0 ? "USD" : "VES";
            BigDecimal precio = moneda.equals("USD")
                    ? new BigDecimal("5.00").add(new BigDecimal(i).multiply(new BigDecimal("0.50")))
                    : new BigDecimal("250.00").add(new BigDecimal(i).multiply(new BigDecimal("25.00")));
            String nombre = String.format(Locale.ROOT, "%s%s-%02d-%s-%s", PREFIJO, sufijo,
                    i + 1, moneda, aplica ? (incluye ? "INC" : "IVA") : "EXE");
            specs.add(new Spec(i + 1, nombre, moneda, aplica, incluye, precio.setScale(2),
                    categorias.get(i % categorias.size())));
        }
        return List.copyOf(specs);
    }

    private static void comprobarFilaCatalogo(JTable tabla, int fila, Spec spec, BigDecimal tasa) {
        String nombre = String.valueOf(valor(tabla, fila, "nombre"));
        String precio = String.valueOf(valor(tabla, fila, "precio"));
        String iva = String.valueOf(valor(tabla, fila, "iva")).toLowerCase(Locale.ROOT);
        exigir(nombre.equals(spec.nombre()), "nombre de producto visible coincide: " + spec.numero());
        exigir(precio.contains(spec.precio().toPlainString()), "precio principal visible coincide: " + spec.numero());
        BigDecimal referencia = referencia(spec, tasa);
        if (spec.moneda().equals("VES")) {
            exigir(precio.contains(referencia.toPlainString()), "precio equivalente visible coincide: " + spec.numero());
        }
        boolean gravadoVisible = iva.startsWith("sí") || iva.startsWith("si") || iva.contains("iva");
        exigir(spec.aplicaIva() ? gravadoVisible : iva.contains("exent"),
                "clasificación IVA visible coincide: " + spec.numero());
        exigir(!spec.incluyeEsperado() || iva.contains("inc"), "indicador IVA incluido visible: " + spec.numero());
        exigir(!precio.contains("[!]"), "precio aparece clasificado: " + spec.numero());
    }

    private static void comprobarFicha(DiaE2ERobot robot, Sistema sistema, Spec spec, BigDecimal tasa,
            JComboBox<String> moneda, JTextField precio, JComboBox<?> categoria,
            javax.swing.JCheckBox chkAplica, javax.swing.JCheckBox chkIncluye) throws Exception {
        exigir(spec.moneda().equals(robot.edt(moneda::getSelectedItem)), "moneda guardada visible en ficha: " + spec.numero());
        exigir(decimalIgual(robot.edt(precio::getText), spec.precio()), "precio guardado visible en ficha: " + spec.numero());
        exigir(robot.edt(chkAplica::isSelected) == spec.aplicaIva(), "IVA guardado visible en ficha: " + spec.numero());
        exigir(robot.edt(chkIncluye::isSelected) == spec.incluyeEsperado(), "IVA incluido visible en ficha: " + spec.numero());
        Object categoriaSeleccionada = robot.edt(categoria::getSelectedItem);
        exigir(categoriaSeleccionada != null && spec.categoria().equals(categoriaSeleccionada.toString()),
                "categoría guardada visible en ficha: " + spec.numero());

        String ref = robot.edt(sistema.getLblPrecioReferenciaPlato()::getText);
        String esperado = "Ref: " + (spec.moneda().equals("USD") ? "Bs. " : "$ ") + referencia(spec, tasa);
        exigir(esperado.equals(ref), "conversión visible desde la ficha coincide: " + spec.numero());
    }

    private static BigDecimal referencia(Spec spec, BigDecimal tasa) {
        return (spec.moneda().equals("USD")
                ? spec.precio().multiply(tasa)
                : spec.precio().divide(tasa, 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean decimalIgual(String texto, BigDecimal esperado) {
        try {
            return new BigDecimal(texto.trim()).compareTo(esperado) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String robotTexto(DiaE2ERobot robot, JTextField campo) {
        try {
            return robot.edt(campo::getText);
        } catch (Exception ex) {
            return "";
        }
    }

    private static int filaPorNombre(JTable tabla, String nombre) {
        int columna = columna(tabla, "nombre");
        for (int fila = 0; fila < tabla.getRowCount(); fila++) {
            Object valor = valor(tabla, fila, columna);
            if (valor != null && nombre.equalsIgnoreCase(valor.toString())) return fila;
        }
        return -1;
    }

    private static int columna(JTable tabla, String fragmento) {
        for (int columna = 0; columna < tabla.getColumnCount(); columna++) {
            String encabezado = tabla.getColumnName(columna).toLowerCase(Locale.ROOT);
            boolean nombrePlato = "nombre".equalsIgnoreCase(fragmento)
                    && (encabezado.contains("plato") || encabezado.contains("descrip"));
            if (encabezado.contains(fragmento.toLowerCase(Locale.ROOT)) || nombrePlato) return columna;
        }
        throw new AssertionError("No se encontró la columna visible: " + fragmento);
    }

    private static Object valor(JTable tabla, int fila, String encabezado) {
        return valor(tabla, fila, columna(tabla, encabezado));
    }

    private static Object valor(JTable tabla, int fila, int columna) {
        int modeloFila = tabla.convertRowIndexToModel(fila);
        int modeloColumna = tabla.convertColumnIndexToModel(columna);
        return tabla.getModel().getValueAt(modeloFila, modeloColumna);
    }

    private static boolean listaContiene(JList<?> lista, String valor) {
        for (int i = 0; i < lista.getModel().getSize(); i++) {
            Object elemento = lista.getModel().getElementAt(i);
            if (elemento != null && valor.equals(elemento.toString())) return true;
        }
        return false;
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
