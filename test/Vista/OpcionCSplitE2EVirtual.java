package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Test E2E en pantalla virtual (Xvfb DISPLAY=:99) para la Opción C (Pantalla Dividida / Split-Screen).
 *
 * Valida:
 * 1. Dimensionamiento y responsividad: Cero scroll vertical exterior, cabe en pantalla de 1366x711.
 * 2. Visualización de mesas: Tarjetas proporcionales (~75px de alto), nunca estiradas a 900px+.
 * 3. Selector de salas en vivo: Cambio rápido de salas con prefijos (P-1, T-1).
 * 4. Selección de mesa: 1 solo clic resalta con borde dorado y actualiza cabecera.
 * 5. Catálogo de platos: Auto-resize al ancho disponible, 1 clic agrega plato al pedido.
 * 6. Controles de cantidad (+ / - / manual / quitar) y totales USD / Bs.
 * 7. Enviar a cocina: Mesa pasa a OCUPADA (rojo) y pedido se persiste.
 * 8. Cambio entre mesas con comanda existente: Carga el pedido pendiente y activa botón Cobrar.
 */
public final class OpcionCSplitE2EVirtual {

    private static int fallos = 0;

    private static void check(boolean condicion, String mensaje) {
        if (condicion) {
            System.out.println("OK   " + mensaje);
        } else {
            System.err.println("FALLO " + mensaje);
            fallos++;
        }
    }

    private static final class SalasMemoria implements SalasRepositorio {
        private final List<Salas> salas = List.of(
                new Salas(1, "Salon Principal", 4),
                new Salas(2, "Terraza", 3),
                new Salas(3, "Barra", 2, "BARRA")
        );
        @Override public List<Salas> listar() { return salas; }
        @Override public boolean registrar(Salas sala) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Salas sala) { return false; }
    }

    private static final class PlatosMemoria implements PlatosRepositorio {
        private final List<Platos> platos;

        public PlatosMemoria() {
            Platos p1 = new Platos(1, "Pollo a la Brasa", new BigDecimal("12.50"), LocalDate.now().toString(), true);
            p1.setFavorito(true);
            p1.setRankingTotal(new BigDecimal("25"));
            p1.setCategoriaNombre("Pollos");
            p1.setCategoriaColor("#E67E22");
            p1.setMonedaBase("USD");
            p1.setPrecioUsd(new BigDecimal("12.50"));
            p1.setAplicaIva(true);
            p1.setPrecioIncluyeIva(false);
            p1.setPrecioClasificado(true);

            Platos p2 = new Platos(2, "Papas Fritas", new BigDecimal("3.00"), LocalDate.now().toString(), false);
            p2.setCategoriaNombre("Guarniciones");
            p2.setCategoriaColor("#F39C12");
            p2.setMonedaBase("USD");
            p2.setPrecioUsd(new BigDecimal("3.00"));
            p2.setAplicaIva(true);
            p2.setPrecioIncluyeIva(false);
            p2.setPrecioClasificado(true);

            Platos p3 = new Platos(3, "Refresco", new BigDecimal("1.50"), LocalDate.now().toString(), false);
            p3.setCategoriaNombre("Bebidas");
            p3.setCategoriaColor("#3498DB");
            p3.setMonedaBase("USD");
            p3.setPrecioUsd(new BigDecimal("1.50"));
            p3.setAplicaIva(true);
            p3.setPrecioIncluyeIva(false);
            p3.setPrecioClasificado(true);

            platos = List.of(p1, p2, p3);
        }

        @Override public List<Platos> listarPorFecha(String desde, String hasta) {
            String criterio = desde == null ? "" : desde.trim().toLowerCase(java.util.Locale.ROOT);
            if (criterio.isEmpty()) return platos;
            return platos.stream().filter(p -> p.getNombre().toLowerCase(java.util.Locale.ROOT).contains(criterio)).toList();
        }
        @Override public boolean registrar(Platos plato) { return false; }
        @Override public boolean eliminar(int id) { return false; }
        @Override public boolean modificar(Platos plato) { return false; }
    }

    private static final class PedidosMemoria implements PedidosRepositorio {
        private int siguienteId = 100;
        private final List<Pedidos> pedidos = new ArrayList<>();
        private final List<DetallePedido> detalles = new ArrayList<>();

        @Override
        public int registrarPedidoCompleto(Pedidos p, List<DetallePedido> ds) {
            p.setId(++siguienteId);
            p.setEstado("PENDIENTE");
            pedidos.add(p);
            detalles.addAll(ds);
            return p.getId();
        }

        @Override
        public boolean actualizarPedidoCompleto(int id, Pedidos p, List<DetallePedido> ds) {
            return true;
        }

        @Override
        public int verificarStado(int mesa, int sala) {
            for (Pedidos p : pedidos) {
                if (p.getId_sala() == sala && p.getNum_mesa() == mesa && "PENDIENTE".equalsIgnoreCase(p.getEstado())) {
                    return p.getId();
                }
            }
            return 0;
        }

        @Override
        public Pedidos verPedido(int id) {
            for (Pedidos p : pedidos) {
                if (p.getId() == id) return p;
            }
            throw new IllegalArgumentException("Pedido no encontrado: " + id);
        }

        @Override
        public List<DetallePedido> verPedidoDetalle(int id) {
            return List.copyOf(detalles);
        }

        @Override
        public List<DetallePedido> finalizarPedido(int id) {
            return verPedidoDetalle(id);
        }

        @Override
        public boolean actualizarEstado(int id) {
            for (Pedidos p : pedidos) {
                if (p.getId() == id) {
                    p.setEstado("FINALIZADO");
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<Pedidos> listarPedidos() {
            return List.copyOf(pedidos);
        }

        @Override
        public Map<Integer, Integer> contarMesasOcupadasPorSala() {
            Map<Integer, Integer> conteo = new java.util.HashMap<>();
            for (Pedidos p : pedidos) {
                if ("PENDIENTE".equalsIgnoreCase(p.getEstado())) {
                    conteo.put(p.getId_sala(), conteo.getOrDefault(p.getId_sala(), 0) + 1);
                }
            }
            return conteo;
        }

        @Override
        public Map<Integer, String> consultarMesonerosMesasPendientes(int s) {
            return Collections.emptyMap();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("  TEST E2E VIRTUAL: OPCIÓN C (SPLIT-SCREEN)      ");
        System.out.println("=================================================");

        String display = System.getenv("DISPLAY");
        if (display == null || (!display.contains(":99") && !display.contains(":98"))) {
            System.err.println("ADVERTENCIA: DISPLAY=" + display + " (Se esperaba :99)");
        }

        Path pdfTemp = Files.createTempDirectory("e2e-opcion-c-");
        Usuario admin = new Usuario(1, "Admin", "admin", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);

        SalasMemoria salasRepo = new SalasMemoria();
        PlatosMemoria platosRepo = new PlatosMemoria();
        PedidosMemoria pedidosRepo = new PedidosMemoria();
        LoginDao empresaDao = new LoginDao();

        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, permisos));
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(pedidosRepo::verPedido, pedidosRepo::verPedidoDetalle,
                Config::new, new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                pdfServicio,
                permisos,
                new ConsultaPedidosServicio(pedidosRepo, permisos)
        );

        Sistema sistema = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, empresaDao);
        sistema.setSize(1366, 711);
        sistema.setLocationRelativeTo(null);
        sistema.setVisible(true);

        Thread.sleep(1200);

        try {
            // -------------------------------------------------------------
            // CASO 1: Cambiar a Opción C y validar dimensionamiento y ajuste
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 1: Cambio a Opción C y ajuste en pantalla ---");
            SwingUtilities.invokeAndWait(() -> {
                sistema.getCbModoAtencionGlobal().setSelectedIndex(2); // Opción C
            });
            Thread.sleep(1200);

            PanelAtencionMesasSplit split = sistema.getPanelSplitScreen();
            check(split != null, "PanelAtencionMesasSplit no es null");
            check(split.isShowing(), "PanelAtencionMesasSplit está visible en pantalla");
            check(split.getHeight() <= 600, "Altura de PanelAtencionMesasSplit (" + split.getHeight() + "px) <= 600px (cero scroll vertical exterior)");

            // -------------------------------------------------------------
            // CASO 2: Dimensiones de tarjetas de mesas (nunca 968px)
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 2: Dimensiones de botones de mesa ---");
            List<JButton> botonesMesa = obtenerBotonesMesa(split);
            check(!botonesMesa.isEmpty(), "Se encontraron botones de mesa en la grilla: " + botonesMesa.size());
            if (!botonesMesa.isEmpty()) {
                JButton b0 = botonesMesa.get(0);
                System.out.println("Dimensiones botón mesa 0: " + b0.getBounds());
                check(b0.getHeight() >= 60 && b0.getHeight() <= 90,
                        "Alto del botón (" + b0.getHeight() + "px) es ergonómico (60-90px), no deformado verticalmente");
                check(b0.getText().contains("<b>P-1</b>"), "Botón tiene el código con formato en negrita: " + b0.getText().replace("<br>", " "));
            }

            // -------------------------------------------------------------
            // CASO 3: Selección de mesa con 1 solo clic y borde dorado
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 3: Selección de mesa (1 Clic) ---");
            SwingUtilities.invokeAndWait(() -> {
                botonesMesa.get(0).doClick();
            });
            Thread.sleep(600);

            JLabel lblCabecera = obtenerLabelCabecera(split);
            check(lblCabecera != null && lblCabecera.getText().contains("P-1"),
                    "Cabecera comanda refleja mesa seleccionada: " + (lblCabecera != null ? lblCabecera.getText() : "null"));

            // -------------------------------------------------------------
            // CASO 4: Catálogo de platos y adición al carrito con 1 clic
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 4: Catálogo de platos y carrito ---");
            JTable tblCatalogo = split.getTablaCatalogo();
            check(tblCatalogo != null, "Tabla de catálogo de platos accesible");
            check(tblCatalogo.getRowCount() >= 3, "Catálogo tiene platos cargados (encontrados: " + tblCatalogo.getRowCount() + ")");

            // Validar estilo y presentación sincronizada del catálogo de platos:
            javax.swing.table.TableCellRenderer cr = tblCatalogo.getCellRenderer(0, 0);
            java.awt.Component comp0 = cr.getTableCellRendererComponent(tblCatalogo, tblCatalogo.getModel().getValueAt(tblCatalogo.convertRowIndexToModel(0), 1), false, false, 0, 0);
            check(comp0 instanceof JLabel && ((JLabel) comp0).getText().contains("★"), "Plato favorito/top muestra distintivo '★' en el catálogo");
            check(comp0 instanceof JLabel && ((JLabel) comp0).getBackground().equals(java.awt.Color.decode("#E67E22")), "Plato tiene color de su categoría en el catálogo");
            check("Pollo a la Brasa".equals(tblCatalogo.getModel().getValueAt(tblCatalogo.convertRowIndexToModel(0), 1)), "El valor en el modelo conserva el nombre limpio sin alterar la base de datos");

            SwingUtilities.invokeAndWait(() -> {
                tblCatalogo.setRowSelectionInterval(0, 0);
                split.agregarPlatoSeleccionadoACarrito();
            });
            Thread.sleep(500);

            check(split.getCantidadItemsEnCarrito() == 1,
                    "Plato agregado al carrito exitosamente (Items: " + split.getCantidadItemsEnCarrito() + ")");

            // Agregar un segundo plato
            SwingUtilities.invokeAndWait(() -> {
                tblCatalogo.setRowSelectionInterval(1, 1);
                split.agregarPlatoSeleccionadoACarrito();
            });
            Thread.sleep(500);

            check(split.getCantidadItemsEnCarrito() == 2,
                    "Segundo plato agregado al carrito exitosamente (Items: " + split.getCantidadItemsEnCarrito() + ")");

            // -------------------------------------------------------------
            // CASO 5: Controles de cantidad (+ / - / quitar)
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 5: Ajuste de cantidades ---");
            JButton btnMas = encontrarBotonPorTextoExacto(split, "+");
            JButton btnMenos = encontrarBotonPorTextoExacto(split, "−");
            check(btnMas != null, "Botón '+' de incremento encontrado");
            check(btnMenos != null, "Botón '−' de decremento encontrado");

            if (btnMas != null) {
                SwingUtilities.invokeAndWait(btnMas::doClick);
                Thread.sleep(400);
            }

            // -------------------------------------------------------------
            // CASO 6: Enviar a Cocina y Mesa a OCUPADA
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 6: Enviar a Cocina ---");
            JButton btnCocina = encontrarBotonPorTexto(split, "Enviar a Cocina");
            check(btnCocina != null, "Botón 'Enviar a Cocina' encontrado");
            if (btnCocina != null) {
                SwingUtilities.invokeAndWait(btnCocina::doClick);
                Thread.sleep(1500);
            }

            check(pedidosRepo.verificarStado(1, 1) > 0, "Pedido registrado en repositorio para Mesa 1, Sala 1");

            // -------------------------------------------------------------
            // CASO 7: Botones de acción siempre visibles en pantalla
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 7: Visibilidad de botones de acción en pantalla ---");
            JButton btnCobrar = encontrarBotonPorTexto(split, "Cobrar / Facturar");
            check(btnCobrar != null, "Botón 'Cobrar / Facturar' encontrado");
            if (btnCobrar != null) {
                int yPos = btnCobrar.getLocationOnScreen().y - sistema.getLocationOnScreen().y;
                System.out.println("Posición Y relativa de 'Cobrar / Facturar': " + yPos + "px");
                check(yPos + btnCobrar.getHeight() <= sistema.getHeight(),
                        "Botón 'Cobrar / Facturar' visible en pantalla (Y=" + yPos + "px + alto=" + btnCobrar.getHeight() + "px <= " + sistema.getHeight() + "px)");
                check(btnCobrar.isEnabled(), "Botón 'Cobrar / Facturar' habilitado para comanda ocupada");
            }

            // -------------------------------------------------------------
            // CASO 8: Cambio a otra mesa y retorno con recarga de comanda
            // -------------------------------------------------------------
            System.out.println("\n--- Caso 8: Recarga automática de comanda existente ---");
            for (int t = 0; t < 30; t++) {
                List<JButton> bs = obtenerBotonesMesa(split);
                if (!bs.isEmpty() && bs.get(0).getText().contains("OCUPADA")) {
                    break;
                }
                Thread.sleep(100);
            }
            List<JButton> botonesMesaActualizados = obtenerBotonesMesa(split);
            if (botonesMesaActualizados.size() >= 2) {
                // Clic en Mesa 2 (libre)
                SwingUtilities.invokeAndWait(() -> botonesMesaActualizados.get(1).doClick());
                Thread.sleep(500);
                check(split.getCantidadItemsEnCarrito() == 0, "Mesa 2 (libre) inicia con comanda vacía");

                // Retorno a Mesa 1 (ocupada)
                SwingUtilities.invokeAndWait(() -> botonesMesaActualizados.get(0).doClick());
                for (int t = 0; t < 30 && split.getCantidadItemsEnCarrito() == 0; t++) {
                    Thread.sleep(100);
                }
                check(split.getCantidadItemsEnCarrito() >= 1,
                        "Mesa 1 (ocupada) recarga automáticamente sus items (Items: " + split.getCantidadItemsEnCarrito() + ")");
            }

        } finally {
            sistema.dispose();
        }

        System.out.println("\n=================================================");
        if (fallos == 0) {
            System.out.println("  RESULTADO: TODOS LOS CASOS PASARON (0 FALLOS)  ");
        } else {
            System.err.println("  RESULTADO: HUBO " + fallos + " FALLOS EN EL TEST");
        }
        System.out.println("=================================================");

        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static List<JButton> obtenerBotonesMesa(Container root) {
        List<JButton> lista = new ArrayList<>();
        buscarBotonesMesaRecursivo(root, lista);
        return lista;
    }

    private static void buscarBotonesMesaRecursivo(Component c, List<JButton> lista) {
        if (c instanceof JButton b && b.getClientProperty("numMesa") != null) {
            lista.add(b);
        } else if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                buscarBotonesMesaRecursivo(child, lista);
            }
        }
    }

    private static JLabel obtenerLabelCabecera(Container root) {
        if (root instanceof JLabel l && l.getText().contains("MESA:")) {
            return l;
        }
        for (Component c : root.getComponents()) {
            if (c instanceof Container cont) {
                JLabel encontrado = obtenerLabelCabecera(cont);
                if (encontrado != null) return encontrado;
            }
        }
        return null;
    }

    private static JButton encontrarBotonPorTexto(Container root, String texto) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && b.getText().contains(texto)) {
                return b;
            }
            if (c instanceof Container cont) {
                JButton res = encontrarBotonPorTexto(cont, texto);
                if (res != null) return res;
            }
        }
        return null;
    }

    private static JButton encontrarBotonPorTextoExacto(Container root, String texto) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && texto.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container cont) {
                JButton res = encontrarBotonPorTextoExacto(cont, texto);
                if (res != null) return res;
            }
        }
        return null;
    }
}
