package Vista;

import Controlador.*;
import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * E2E VIRTUAL (Xvfb :99) — Creación de 70 platos por la INTERFAZ de Platos.
 *
 * Flujo (todo por la UI con mouse/teclado reales e independientes de :0):
 *   1. Verifica que la conexión apunte a la BD desechable restaurante_test; luego limpia
 *      restos de ejecuciones anteriores (prefijo E2E70-).
 *   2. Crea 70 platos por el formulario del panel "Platos" cubriendo el espectro
 *      aplica_iva (IVA/exento) x precio_incluye_iva (con/sin) x moneda_base (USD/VES),
 *      repartidos round-robin entre las categorías EXISTENTES (no se crea ninguna).
 *   3. Verifica por la tabla de la UI que aparecen los 70.
 *   4. Verifica por BD que los 70 platos persistidos corresponden EXACTAMENTE a lo
 *      especificado (moneda, IVA, incluye IVA, precios, clasificación y categoría),
 *      incluyendo la normalización del caso incoherente (exento + "incluye IVA" -> 0).
 *
 * Ejecutable vía:  ./e2e_virtual.sh Vista.Platos70IvaMonedaE2EVirtual
 * Requiere una BD local desechable restaurante_test en el puerto 3307 y E2E_BD_DESECHABLE=SI.
 */
public class Platos70IvaMonedaE2EVirtual {

    private static final String PREFIJO = "E2E70-";
    private static final String PREFIJO_CLAVE = "e2e70-";
    /** Cuántos platos crear (por defecto 70; ajustable con -De2e70.crear=N para depurar). */
    private static int OBJETIVO = 70;

    /** aplica_iva, precio_incluye_iva, moneda(0=USD,1=VES). El caso (0,1) es incoherente y la app lo normaliza. */
    private static final int[][] PATRONES = {
        {1, 0, 0}, {1, 1, 0}, {0, 0, 0}, {0, 1, 0},
        {1, 0, 1}, {1, 1, 1}, {0, 0, 1}, {0, 1, 1},
    };

    private static int fallos = 0;
    private static DiaE2ERobot robot;

    private static final class Spec {
        int indice;
        String nombre;
        String moneda;
        boolean aplica;
        boolean incluye;
        BigDecimal precio;
        int catId;
        String catNombre;

        boolean incluyeEsperado() {
            return aplica && incluye;
        }

        BigDecimal usdEsperado(BigDecimal tasa) {
            if ("VES".equals(moneda)) {
                return precio.divide(tasa, 2, RoundingMode.HALF_UP);
            }
            return precio.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal vesEsperado(BigDecimal tasa) {
            if ("VES".equals(moneda)) {
                return precio.setScale(2, RoundingMode.HALF_UP);
            }
            return precio.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    private static void info(String msg) {
        System.out.println("     " + msg);
    }

    private static Object campo(Sistema s, String nombre) throws Exception {
        java.lang.reflect.Field f = Sistema.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(s);
    }

    private static <T> T enEdt(java.util.concurrent.Callable<T> c) throws Exception {
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

    // ------------------------- Acceso a datos (solo lectura/carga/limpieza) -------------------------

    private static BigDecimal leerTasa() throws Exception {
        try (Connection c = new ProveedorConexionJdbc().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT tasa_dolar FROM config LIMIT 1")) {
            if (rs.next()) {
                BigDecimal t = rs.getBigDecimal(1);
                if (t != null && t.signum() > 0) {
                    return t;
                }
            }
        }
        throw new IllegalStateException("No hay tasa_dolar activa en config; no se puede calcular la equivalencia.");
    }

    /** Falla antes de cualquier escritura si el test no apunta al MySQL desechable de integración. */
    private static void verificarBDDescartable() throws Exception {
        if (!":99".equals(System.getenv("DISPLAY"))) {
            throw new IllegalStateException("E2E rechazado: debe ejecutarse en Xvfb DISPLAY=:99.");
        }
        if (!"SI".equals(System.getenv("E2E_BD_DESECHABLE"))) {
            throw new IllegalStateException("E2E rechazado: exige E2E_BD_DESECHABLE=SI.");
        }
        try (Connection c = new ProveedorConexionJdbc().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE(), @@port, CURRENT_USER()")) {
            if (!rs.next()) throw new IllegalStateException("No se pudo verificar el destino MySQL del E2E.");
            String bd = rs.getString(1);
            int puerto = rs.getInt(2);
            String usuario = rs.getString(3);
            String url = c.getMetaData().getURL();
            boolean urlLocalDesechable = url != null && url.matches(
                    "(?i)^jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):3307/restaurante_test(?:\\?.*)?$");
            if (!"restaurante_test".equals(bd) || puerto != 3307
                    || usuario == null || !usuario.startsWith("restaurante_test_app@")
                    || !urlLocalDesechable) {
                throw new IllegalStateException("E2E rechazado por destino no desechable: BD=" + bd
                        + ", puerto=" + puerto + ", usuario=" + usuario + ", URL=" + url);
            }
        }
    }

    /** DELETE duro de todo lo que este test pudo dejar antes (platos + sus vínculos por nombre_clave). */
    private static int limpiarTest() throws Exception {
        try (Connection c = new ProveedorConexionJdbc().getConnection()) {
            int borrados;
            try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM plato_categoria WHERE nombre_clave LIKE ?")) {
                p.setString(1, PREFIJO_CLAVE + "%");
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM plato_favorito WHERE nombre_clave LIKE ?")) {
                p.setString(1, PREFIJO_CLAVE + "%");
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "DELETE FROM platos WHERE LOWER(TRIM(nombre)) LIKE ?")) {
                p.setString(1, PREFIJO_CLAVE + "%");
                borrados = p.executeUpdate();
            }
            return borrados;
        }
    }

    /** true si la tabla admin (sin tocar la BD) ya muestra una fila con ese nombre. */
    private static boolean nombreEnTabla(JTable tabla, String nombre) {
        for (int i = 0; i < tabla.getRowCount(); i++) {
            Object v = tabla.getModel().getValueAt(tabla.convertRowIndexToModel(i), 1);
            if (v != null && v.toString().equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    private static final class FilaBd {
        String nombre;
        String moneda;
        int aplicaIva;
        int incluyeIva;
        BigDecimal usd;
        BigDecimal ves;
        int clasificado;
    }

    private static Map<String, FilaBd> leerPlatosBd() throws Exception {
        Map<String, FilaBd> mapa = new LinkedHashMap<>();
        try (Connection c = new ProveedorConexionJdbc().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT nombre, moneda_base, aplica_iva, IFNULL(precio_incluye_iva,-1), "
                     + "precio_usd, precio_ves, precio_clasificado FROM platos "
                     + "WHERE LOWER(TRIM(nombre)) LIKE '" + PREFIJO_CLAVE + "%' ORDER BY nombre")) {
            while (rs.next()) {
                FilaBd f = new FilaBd();
                f.nombre = rs.getString(1);
                f.moneda = rs.getString(2);
                f.aplicaIva = rs.getInt(3);
                f.incluyeIva = rs.getInt(4);
                f.usd = rs.getBigDecimal(5);
                f.ves = rs.getBigDecimal(6);
                f.clasificado = rs.getInt(7);
                mapa.put(f.nombre.toUpperCase(java.util.Locale.ROOT), f);
            }
        }
        return mapa;
    }

    private static Map<String, Integer> leerCategoriasBd() throws Exception {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        try (Connection c = new ProveedorConexionJdbc().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT pc.nombre_clave, pc.id_categoria FROM plato_categoria pc "
                     + "WHERE pc.nombre_clave LIKE '" + PREFIJO_CLAVE + "%'")) {
            while (rs.next()) {
                mapa.put(rs.getString(1).toUpperCase(java.util.Locale.ROOT), rs.getInt(2));
            }
        }
        return mapa;
    }

    // ------------------------- Preparación de los 70 specs -------------------------

    private static List<Spec> construirSpecs(List<Categoria> categorias) {
        List<Spec> specs = new ArrayList<>();
        for (int i = 0; i < OBJETIVO; i++) {
            int[] pat = PATRONES[i % PATRONES.length];
            Spec s = new Spec();
            s.indice = i + 1;
            s.aplica = pat[0] == 1;
            s.incluye = pat[1] == 1;
            s.moneda = pat[2] == 0 ? "USD" : "VES";
            String etiqueta = (s.moneda.equals("USD") ? "U" : "B")
                    + (s.aplica ? "-IVA" : "-EXE") + (s.incluye ? "-INC" : "-SIN");
            s.nombre = String.format("%s%02d %s", PREFIJO, i + 1, etiqueta);
            if (s.moneda.equals("USD")) {
                s.precio = new BigDecimal("5.00").add(new BigDecimal(i).multiply(new BigDecimal("0.50")))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                s.precio = new BigDecimal("250.00").add(new BigDecimal(i).multiply(new BigDecimal("25")))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            Categoria cat = categorias.get(i % categorias.size());
            s.catId = cat.getId();
            s.catNombre = cat.getNombre();
            specs.add(s);
        }
        return specs;
    }

    private static void foto(JFrame f, String nombre) {
        try {
            java.awt.Point p = f.getLocationOnScreen();
            BufferedImage cap = robot.edt(() -> new java.awt.Robot().createScreenCapture(
                    new Rectangle(p.x, p.y, f.getWidth(), f.getHeight())));
            ImageIO.write(cap, "png", new java.io.File("/tmp/opencode/" + nombre + ".png"));
            info("foto: /tmp/opencode/" + nombre + ".png");
        } catch (Exception e) {
            info("foto fallo: " + e.getMessage());
        }
    }

    private static List<String> dialogosVisibles() throws Exception {
        return enEdt(() -> {
            List<String> out = new ArrayList<>();
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    out.add(d.getTitle() == null ? "(sin título)" : d.getTitle());
                }
            }
            return out;
        });
    }

    private static String mensajeDialogo(String titulo) throws Exception {
        return enEdt(() -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()
                        && d.getTitle() != null && d.getTitle().contains(titulo)) {
                    for (Component comp : d.getContentPane().getComponents()) {
                        if (comp instanceof javax.swing.JOptionPane op) {
                            Object m = op.getMessage();
                            if (m != null) {
                                return String.valueOf(m);
                            }
                        }
                    }
                }
            }
            return "";
        });
    }

    private static void cerrarDialogos() throws Exception {
        for (String t : dialogosVisibles()) {
            info("cerrando diálogo inesperado: " + t);
            try {
                robot.clicarBotonEnDialogo(t, "Aceptar");
            } catch (Exception ex) {
                try {
                    robot.clicarBotonEnDialogo(t, "OK");
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ------------------------- Programa principal -------------------------

    public static void main(String[] args) throws Exception {
        if (!"SI".equals(System.getProperty("e2e.permitir"))) {
            System.out.println("E2E toca la BD real (crea 70 platos): relanza con -De2e.permitir=SI");
            System.exit(2);
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("/tmp/opencode"));
        OBJETIVO = Integer.getInteger("e2e70.crear", 70);

        System.out.println("================================================================================");
        System.out.println("E2E VIRTUAL: 70 PLATOS POR LA INTERFAZ (IVA/EXENTO x INCLUYE/SIN x USD/VES)");
        System.out.println("================================================================================");

        verificarBDDescartable();
        BigDecimal tasa = leerTasa();
        info("tasa_dolar activa = " + tasa.toPlainString());

        int previos = limpiarTest();
        info("limpieza previa: " + previos + " plato(s) E2E70- eliminados");

        Usuario admin = new Usuario(2, "Administrador", "admin", "admin", "Administrador");
        try (Connection c = new ProveedorConexionJdbc().getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT id FROM usuarios WHERE correo='admin' OR rol='Administrador' ORDER BY id ASC LIMIT 1");
             ResultSet rs = st.executeQuery()) {
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
                GeneradorPdfPedido.conEstructuraMensual(
                        GeneradorPdfPedido.resolverDirectorioFacturasPorDefecto()),
                PedidoPdfServicio.AbridorPdf.porDefecto());
        Servicio.CierreCajaServicio cierreServicio = new Servicio.CierreCajaServicio(
                new Modelo.CierreCajaDao(), empresaDao::datosEmpresa, new Servicio.GeneradorPdfCierre());
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosDao), pdfServicio, politica,
                new ConsultaPedidosServicio(pedidosDao, politica), cierreServicio);

        final Sistema[] box = new Sistema[1];
        SwingUtilities.invokeAndWait(() -> box[0] = new Sistema(admin,
                new SalasControlador(new Servicio.SalasServicio(new Modelo.SalasDao(), politica)),
                new Controlador.PlatosControlador(new Servicio.PlatosServicio(new Modelo.PlatosDao(), politica)),
                pedidosCtrl,
                new CategoriaControlador(new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politica)),
                new FavoritoControlador(new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politica)),
                empresaDao));
        Sistema s = box[0];
        robot = new DiaE2ERobot();

        Map<String, FilaBd> bd = new LinkedHashMap<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                s.setExtendedState(JFrame.MAXIMIZED_BOTH);
                s.setVisible(true);
            });
            robot.esperarVentana(Sistema.class);
            Thread.sleep(2500);

            // --- Navegar al panel de Platos pulsando el botón de la barra lateral ---
            JButton btnPlatos = (JButton) campo(s, "btnPlatos");
            robot.clic(btnPlatos);
            JTabbedPane tabs = (JTabbedPane) campo(s, "jTabbedPane1");
            robot.esperar(() -> {
                try {
                    return ((JTextField) campo(s, "txtNombrePlato")).isShowing()
                            && ((JTable) campo(s, "TablePlatos")).isShowing();
                } catch (Exception e) {
                    return false;
                }
            }, "el formulario de Platos queda visible");
            info("pestaña activa: " + enEdt(() -> tabs.getTitleAt(tabs.getSelectedIndex())));

            JTextField txtNombre = (JTextField) campo(s, "txtNombrePlato");
            JTextField txtPrecio = (JTextField) campo(s, "txtPrecioPlato");
            @SuppressWarnings("unchecked")
            JComboBox<String> cbMoneda = (JComboBox<String>) campo(s, "cbMonedaBasePlato");
            JCheckBox chkAplica = (JCheckBox) campo(s, "chkAplicaIvaPlato");
            JCheckBox chkIncluye = (JCheckBox) campo(s, "chkPrecioIncluyeIvaPlato");
            @SuppressWarnings("unchecked")
            JComboBox<Categoria> cbCategoria = (JComboBox<Categoria>) campo(s, "cbCategoriaFicha");
            JButton btnGuardar = (JButton) campo(s, "btnGuardarPlato");
            JButton btnLimpiar = (JButton) campo(s, "btnNuevoPlato");
            JTextField txtBuscarAdmin = (JTextField) campo(s, "txtBuscarAdminPlatos");
            JTable tablaAdmin = (JTable) campo(s, "TablePlatos");

            // --- Categorías existentes ofrecidas por el propio combo (no se crea ninguna) ---
            List<Categoria> categorias = new ArrayList<>();
            enEdt(() -> {
                for (int i = 0; i < cbCategoria.getItemCount(); i++) {
                    Object o = cbCategoria.getItemAt(i);
                    if (o instanceof Categoria c && c.getId() > 0) {
                        categorias.add(c);
                    }
                }
                return null;
            });
            check(!categorias.isEmpty(), "el combo de categorías ofrece categorías EXISTENTES (n=" + categorias.size() + ")");
            if (categorias.isEmpty()) {
                throw new IllegalStateException("Sin categorías existentes; el panel no está listo.");
            }

            List<Spec> specs = construirSpecs(categorias);
            info("platos en categorías existentes: " + categorias.size()
                    + " | patrones probados: " + PATRONES.length + " | platos: " + specs.size());

            // --- Creación de los 70 platos por la INTERFAZ ---
            long t0 = System.currentTimeMillis();
            int creados = 0;
            int indexCategoria = 0;
            for (Spec sp : specs) {
                robot.clic(btnLimpiar);
                robot.reemplazarTexto(txtNombre, sp.nombre);
                robot.seleccionarCombo(cbMoneda, sp.moneda);
                robot.reemplazarTexto(txtPrecio, sp.precio.toPlainString());
                // idx 0 del combo = "Sin categoría"; las categorías reales empiezan en 1.
                indexCategoria = 1 + ((sp.indice - 1) % categorias.size());
                robot.seleccionarComboPorIndice(cbCategoria, indexCategoria);

                boolean aplicaAntes = enEdt(chkAplica::isSelected);
                if (sp.aplica != aplicaAntes) {
                    robot.clic(chkAplica);
                }
                boolean incluyeHabilitado = enEdt(chkIncluye::isEnabled);
                if (sp.aplica && sp.incluye) {
                    if (enEdt(chkIncluye::isSelected) == false) {
                        robot.clic(chkIncluye);
                    }
                } else if (!sp.aplica) {
                    // Caso incoherente: la UI debe mantener el checkbox deshabilitado y SIN marcar.
                    if (sp.incluye) {
                        if (incluyeHabilitado) {
                            robot.clic(chkIncluye);
                        }
                        check(!enEdt(chkIncluye::isEnabled) || !enEdt(chkIncluye::isSelected),
                                "exento " + sp.nombre + ": 'Incluye IVA' bloqueado/desmarcado por la UI");
                    }
                }

                robot.clic(btnGuardar);
                // Sondeo SOLO de interfaz (sin abrir conexiones a BD en bucle): la fila nueva
                // debe aparecer en la tabla admin. La persistencia real se verifica al final.
                long fin = System.currentTimeMillis() + 15_000;
                boolean creado = false;
                while (System.currentTimeMillis() < fin) {
                    if (enEdt(() -> nombreEnTabla(tablaAdmin, sp.nombre))) {
                        creado = true;
                        break;
                    }
                    List<String> ds = dialogosVisibles();
                    if (!ds.isEmpty()) {
                        String msg = mensajeDialogo(ds.get(0));
                        foto(s, "e2e70_fallo_" + sp.indice);
                        cerrarDialogos();
                        check(false, "plato #" + sp.indice + " '" + sp.nombre
                                + "': diálogo inesperado → " + ds + " msg='" + msg + "'");
                        break;
                    }
                    Thread.sleep(200);
                }
                if (!creado) {
                    if (dialogosVisibles().isEmpty()) {
                        foto(s, "e2e70_fallo_" + sp.indice);
                        check(false, "plato #" + sp.indice + " '" + sp.nombre + "' no apareció en la tabla");
                    }
                    if (fallos > 6) {
                        throw new IllegalStateException("Demasiados fallos de creación; abortando.");
                    }
                    continue;
                }
                creados++;
                if (creados % 10 == 0) {
                    info("creados " + creados + "/" + OBJETIVO + " (" + ((System.currentTimeMillis() - t0) / 1000) + "s)");
                }
            }
            check(creados == OBJETIVO, "se crearon los 70 platos por la interfaz (creados=" + creados + ")");
            info("tiempo de creación: " + ((System.currentTimeMillis() - t0) / 1000) + "s");

            // --- Verificación por la INTERFAZ: filtrar por prefijo en la tabla admin ---
            robot.reemplazarTexto(txtBuscarAdmin, PREFIJO);
            robot.esperar(() -> {
                try {
                    return ((JTable) campo(s, "TablePlatos")).getRowCount() >= OBJETIVO;
                } catch (Exception e) {
                    return false;
                }
            }, "la tabla muestra los platos E2E70-");
            Thread.sleep(600);
            int filasUi = enEdt(tablaAdmin::getRowCount);
            int nombresUiOk = enEdt(() -> {
                int ok = 0;
                for (int i = 0; i < tablaAdmin.getRowCount(); i++) {
                    Object v = tablaAdmin.getModel().getValueAt(tablaAdmin.convertRowIndexToModel(i), 1);
                    if (v != null && v.toString().toUpperCase(java.util.Locale.ROOT).startsWith(PREFIJO)) {
                        ok++;
                    }
                }
                return ok;
            });
            foto(s, "e2e70_tabla");
            check(filasUi == OBJETIVO, "la tabla admin muestra exactamente 70 platos E2E70- (filas=" + filasUi + ")");
            check(nombresUiOk == OBJETIVO, "las 70 filas de la tabla llevan el prefijo " + PREFIJO + " (n=" + nombresUiOk + ")");

            // --- Verificación por BD: correspondencia EXACTA ---
            bd = leerPlatosBd();
            info("filas E2E70- en BD = " + bd.size());
            check(bd.size() == OBJETIVO, "la BD contiene exactamente 70 platos E2E70- (n=" + bd.size() + ")");

            Map<String, Integer> catsBd = leerCategoriasBd();
            int okCampos = 0;
            int okPrecios = 0;
            int okCategorias = 0;
            int normalizados = 0;
            int esperadosNormalizados = 0;
            for (Spec sp : specs) {
                String clave = sp.nombre.toUpperCase(java.util.Locale.ROOT);
                FilaBd f = bd.get(clave);
                if (f == null) {
                    check(false, "BD sin plato '" + sp.nombre + "'");
                    continue;
                }
                boolean campos = sp.moneda.equals(f.moneda)
                        && (sp.aplica ? 1 : 0) == f.aplicaIva
                        && (sp.incluyeEsperado() ? 1 : 0) == f.incluyeIva
                        && f.clasificado == 1;
                boolean precios = f.usd != null && f.usd.setScale(2, RoundingMode.HALF_UP)
                        .compareTo(sp.usdEsperado(tasa)) == 0
                        && f.ves != null && f.ves.setScale(2, RoundingMode.HALF_UP)
                        .compareTo(sp.vesEsperado(tasa)) == 0;
                boolean categoria = catsBd.getOrDefault(clave, -1) == sp.catId;
                if (campos) {
                    okCampos++;
                } else {
                    check(false, "campos BD ≠ esperado para '" + sp.nombre + "': moneda=" + f.moneda
                            + " aplica=" + f.aplicaIva + " incluye=" + f.incluyeIva + " clasif=" + f.clasificado);
                }
                if (precios) {
                    okPrecios++;
                } else {
                    check(false, "precios BD ≠ esperado para '" + sp.nombre + "': usd=" + f.usd
                            + " esperado=" + sp.usdEsperado(tasa) + " ves=" + f.ves + " esperado=" + sp.vesEsperado(tasa));
                }
                if (categoria) {
                    okCategorias++;
                } else {
                    check(false, "categoría BD ≠ esperado para '" + sp.nombre + "': id="
                            + catsBd.getOrDefault(clave, -1) + " esperado=" + sp.catId);
                }
                if (!sp.aplica && sp.incluye) {
                    esperadosNormalizados++;
                    if (f.incluyeIva == 0) {
                        normalizados++;
                    }
                }
            }
            check(okCampos == OBJETIVO, "IVA/exento/incluye/moneda/clasificación coinciden en los 70 (n=" + okCampos + ")");
            check(okPrecios == OBJETIVO, "precio_usd/precio_ves coinciden en los 70 (n=" + okPrecios + ")");
            check(okCategorias == OBJETIVO, "categoría asignada coincide en los 70 (n=" + okCategorias + ")");
            check(esperadosNormalizados > 0, "hubo casos incoherentes exento+incluye para probar (n="
                    + esperadosNormalizados + ")");
            check(normalizados == esperadosNormalizados,
                    "los " + esperadosNormalizados + " casos exento+incluye se normalizaron a 0 (n=" + normalizados + ")");
        } finally {
            try {
                int eliminados = limpiarTest();
                info("limpieza final: " + eliminados + " plato(s) E2E70- retirados de la BD desechable");
            } catch (Exception errorLimpieza) {
                fallos++;
                info("FALLO limpieza final de platos E2E70-: " + errorLimpieza.getMessage());
            }
            try {
                Sistema ss = box[0];
                if (ss != null) {
                    SwingUtilities.invokeAndWait(ss::dispose);
                }
            } catch (Exception ignored) {
            }
        }
        System.out.println("================================================================================");
        System.out.println(fallos == 0 ? "E2E70_OK" : "E2E70_FALLO(" + fallos + ")");
        System.exit(fallos == 0 ? 0 : 1);
    }
}
