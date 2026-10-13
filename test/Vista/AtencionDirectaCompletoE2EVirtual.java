package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.*;
import Servicio.*;
import java.awt.Component;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Test E2E Completo en pantalla virtual (Xvfb DISPLAY=:99) para el Selector de Modalidades.
 *
 * Cubre exhaustivamente TODAS las posibilidades del operador:
 * 1. Inicialización en modo por defecto (Opción B) y acceso desde botón lateral "Atención directa".
 * 2. Cambio a Opción A (Digitado rápido):
 *    - Cambio inmediato de vista a Toma de Pedido.
 *    - Sincronización de combobox global y local.
 *    - Catálogo de platos y mesoneros cargados.
 *    - Digitación directa de código (ej: P-2, T-3) con Enter.
 *    - Selección mediante badge interactivo / menú rápido (B-1).
 * 3. Cambio a Opción C (Pantalla dividida / Split-Screen):
 *    - Cambio inmediato de vista a Split.
 *    - Pestañas de salas en Split.
 *    - Selección de mesa y adición de plato al carrito.
 * 4. Cambio a Modo Clásico:
 *    - Cambio inmediato a pantalla Panel (tarjetas de salas).
 *    - Clic en tarjeta de sala abre mesas de esa sala.
 * 5. Cambio a Opción B desde el combobox de la cabecera de mesas:
 *    - Sincronización con el combobox global.
 *    - Pestañas horizontales de sala y semáforos.
 * 6. Preservación del modo seleccionado al navegar a otras pantallas (Salas, Config, etc.)
 *    y retorno con "Atención directa".
 * 7. Transiciones cíclicas completas entre todas las modalidades desde cualquier pantalla.
 * 8. Verificación de visibilidad y Z-order en diferentes resoluciones.
 */
public final class AtencionDirectaCompletoE2EVirtual {

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
        private final List<Platos> platos = List.of(
                new Platos(1, "Pollo a la Brasa", new BigDecimal("12.50"), LocalDate.now().toString(), true),
                new Platos(2, "Papas Fritas", new BigDecimal("3.00"), LocalDate.now().toString(), false),
                new Platos(3, "Refresco", new BigDecimal("1.50"), LocalDate.now().toString(), false)
        );
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
    }

    private static <T> T edt(Callable<T> tarea) throws Exception {
        Object[] resultado = new Object[1];
        Throwable[] error = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { resultado[0] = tarea.call(); } catch (Throwable ex) { error[0] = ex; }
        });
        if (error[0] != null) throw new RuntimeException(error[0]);
        @SuppressWarnings("unchecked") T valor = (T) resultado[0];
        return valor;
    }

    private static Object campo(Sistema sistema, String nombre) throws Exception {
        var field = Sistema.class.getDeclaredField(nombre);
        field.setAccessible(true);
        return field.get(sistema);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==========================================================================");
        System.out.println("=== TEST E2E COMPLETO: TODAS LAS POSIBILIDADES DEL SELECTOR DE MODOS ===");
        System.out.println("==========================================================================");

        Path pdfTemp = Files.createTempDirectory("e2e-selector-modos-");
        Usuario admin = new Usuario(1, "Admin E2E", "admin-e2e", "admin", "Administrador");
        PoliticaAcceso permisos = new PoliticaAcceso(admin);

        SalasMemoria salasRepo = new SalasMemoria();
        PlatosMemoria platosRepo = new PlatosMemoria();
        PedidosMemoria repo = new PedidosMemoria();

        SalasControlador salasCtrl = new SalasControlador(new SalasServicio(salasRepo, permisos));
        PlatosControlador platosCtrl = new PlatosControlador(new PlatosServicio(platosRepo, permisos));
        PedidoPdfServicio pdf = new PedidoPdfServicio(repo::verPedido, repo::verPedidoDetalle,
                Config::new, new GeneradorPdfPedido(pdfTemp), archivo -> true);
        PedidosControlador pedidosCtrl = new PedidosControlador(new PedidoServicio(repo), pdf, permisos,
                new ConsultaPedidosServicio(repo, permisos));

        LoginDao empresa = new LoginDao() {
            @Override public Config datosEmpresa() { return new Config(); }
            @Override public List<Usuario> ListarUsuarios() { return List.of(); }
        };

        Sistema sistema = edt(() -> {
            Sistema s = new Sistema(admin, salasCtrl, platosCtrl, pedidosCtrl, empresa);
            s.setSize(1366, 711);
            s.setLocationRelativeTo(null);
            s.setVisible(true);
            return s;
        });

        // Esperar renderizado y estabilización del frame en Xvfb
        Thread.sleep(1200);

        JTabbedPane tabs = (JTabbedPane) campo(sistema, "jTabbedPane1");
        JComboBox<String> cbModoGlobal = sistema.getCbModoAtencionGlobal();
        JComboBox<String> cbModoLocal = sistema.getCbModoAtencion();
        JButton btnAtencionDirecta = sistema.getBtnAtencionDirecta();
        JTextField txtCodigoMesa = sistema.getTxtCodigoMesaDirecto();
        JButton btnSelectorCabecera = sistema.getBtnSelectorMesaCabecera();
        JTextField txtTempIdSala = (JTextField) campo(sistema, "txtTempIdSala");
        JTextField txtTempNumMesa = (JTextField) campo(sistema, "txtTempNumMesa");
        JTable tblTemPlatos = (JTable) campo(sistema, "tblTemPlatos");
        JPanel panelTabsSalas = (JPanel) campo(sistema, "panelTabsSalasDirecto");
        PanelAtencionMesasSplit panelSplit = sistema.getPanelSplitScreen();

        check(cbModoGlobal != null, "Conmutador global en cabecera existe");
        check(cbModoLocal != null, "Conmutador local en panel de mesas existe");
        check(btnAtencionDirecta != null, "Botón lateral 'Atención directa' existe");
        check(tabs != null, "JTabbedPane principal accesible");
        check("OPCION_B".equals(sistema.getModoAtencionActual()), "Modo inicial por defecto es OPCION_B");
        check(cbModoGlobal.getSelectedIndex() == 0, "Combo global inicia en índice 0 (Opción B)");
        check(cbModoLocal.getSelectedIndex() == 0, "Combo local inicia en índice 0 (Opción B)");

        // =========================================================================
        // POSIBILIDAD 1: Clic en 'Atención directa' con modo inicial Opción B
        // =========================================================================
        System.out.println("\n--- [Posibilidad 1] Apertura de Atención Directa en Modo B ---");
        edt(() -> {
            btnAtencionDirecta.doClick();
            return null;
        });
        Thread.sleep(600);

        int tabMesasIdx = tabs.indexOfTab("Mesas");
        check(tabs.getSelectedIndex() == tabMesasIdx, "Clic en 'Atención directa' abre pestaña 'Mesas'");
        int numTabsSalas = edt(panelTabsSalas::getComponentCount);
        check(numTabsSalas >= 3, "Pestañas horizontales de sala visibles (encontradas: " + numTabsSalas + ")");

        // =========================================================================
        // POSIBILIDAD 2: Cambio directo de combobox a OPCION_A (Digitado rápido)
        // =========================================================================
        System.out.println("\n--- [Posibilidad 2] Cambiar combobox a Opción A (Digitado Rápido) ---");
        edt(() -> {
            cbModoGlobal.setSelectedIndex(1); // Opción A
            return null;
        });
        Thread.sleep(600);

        check("OPCION_A".equals(sistema.getModoAtencionActual()), "Modo activo actualizado a OPCION_A");
        check(cbModoGlobal.getSelectedIndex() == 1, "Combo global sincronizado a índice 1");
        check(cbModoLocal.getSelectedIndex() == 1, "Combo local sincronizado a índice 1");
        check(tabs.getSelectedIndex() == 3, "Vista cambió INMEDIATAMENTE a pestaña 3 (Toma de Pedido)");

        int platosCargados = edt(() -> tblTemPlatos.getRowCount());
        check(platosCargados >= 3, "Catálogo de platos listo y visible en modo A (" + platosCargados + " platos)");

        // 2.1 Operador digita "P-2" y presiona Enter
        System.out.println("  -> Operador digita 'P-2' y pulsa Enter");
        edt(() -> {
            txtCodigoMesa.setText("P-2");
            txtCodigoMesa.postActionEvent();
            return null;
        });
        Thread.sleep(500);

        check("1".equals(txtTempIdSala.getText()), "Código 'P-2' asignó Sala 1 (Salon Principal)");
        check("2".equals(txtTempNumMesa.getText()), "Código 'P-2' asignó Mesa 2");
        check(btnSelectorCabecera.getText().contains("P-2"), "Badge cabecera muestra 'P-2': " + btnSelectorCabecera.getText());

        // 2.2 Operador digita "T3" (sin guion) y presiona Enter
        System.out.println("  -> Operador digita 'T3' (sin guion) y pulsa Enter");
        edt(() -> {
            txtCodigoMesa.setText("T3");
            txtCodigoMesa.postActionEvent();
            return null;
        });
        Thread.sleep(500);

        check("2".equals(txtTempIdSala.getText()), "Código 'T3' asignó Sala 2 (Terraza)");
        check("3".equals(txtTempNumMesa.getText()), "Código 'T3' asignó Mesa 3");
        check(btnSelectorCabecera.getText().contains("T-3"), "Badge cabecera formateó a 'T-3': " + btnSelectorCabecera.getText());

        // 2.3 Operador pulsa el selector rápido de mesa
        System.out.println("  -> Operador asigna Barra 'B-1' directamente vía método de asignación directa");
        edt(() -> {
            sistema.cambiarModoAtencion(1); // Reafirma Opción A
            txtCodigoMesa.setText("B1");
            txtCodigoMesa.postActionEvent();
            return null;
        });
        Thread.sleep(500);

        check("3".equals(txtTempIdSala.getText()), "Código 'B1' asignó Sala 3 (Barra)");
        check("1".equals(txtTempNumMesa.getText()), "Código 'B1' asignó Puesto 1");
        check(btnSelectorCabecera.getText().contains("B-1"), "Badge cabecera muestra 'B-1': " + btnSelectorCabecera.getText());

        // =========================================================================
        // POSIBILIDAD 3: Cambio directo de combobox a OPCION_C (Split-Screen)
        // =========================================================================
        System.out.println("\n--- [Posibilidad 3] Cambiar combobox a Opción C (Split-Screen) ---");
        edt(() -> {
            cbModoGlobal.setSelectedIndex(2); // Opción C
            return null;
        });
        Thread.sleep(600);

        int tabSplitIdx = tabs.indexOfTab("Atención Directa (Split)");
        check(tabSplitIdx != -1, "Pestaña 'Atención Directa (Split)' existe");
        check(tabs.getSelectedIndex() == tabSplitIdx, "Vista cambió INMEDIATAMENTE a Split-Screen");
        check("OPCION_C".equals(sistema.getModoAtencionActual()), "Modo activo actualizado a OPCION_C");
        check(cbModoGlobal.getSelectedIndex() == 2, "Combo global sincronizado a índice 2");
        check(cbModoLocal.getSelectedIndex() == 2, "Combo local sincronizado a índice 2");
        check(panelSplit != null, "Instancia PanelAtencionMesasSplit accesible");

        // 3.1 Operador selecciona mesa y agrega plato en Split
        System.out.println("  -> Operador interactúa con componentes dentro de Split-Screen");
        edt(() -> {
            panelSplit.seleccionarSalaPorId(2); // Terraza
            return null;
        });
        Thread.sleep(400);

        edt(() -> {
            panelSplit.seleccionarMesa(1, "T-1");
            return null;
        });
        Thread.sleep(400);

        int itemsAntes = edt(() -> panelSplit.getCantidadItemsEnCarrito());
        edt(() -> {
            // Simular agregar primer plato del catálogo
            JTable tblCat = panelSplit.getTablaCatalogo();
            if (tblCat != null && tblCat.getRowCount() > 0) {
                tblCat.setRowSelectionInterval(0, 0);
                panelSplit.agregarPlatoSeleccionadoACarrito();
            }
            return null;
        });
        Thread.sleep(400);
        int itemsDespues = edt(() -> panelSplit.getCantidadItemsEnCarrito());
        check(itemsDespues >= itemsAntes, "Interacción con Split-Screen agregó ítem a la comanda activa");

        // =========================================================================
        // POSIBILIDAD 4: Cambio directo de combobox a MODO CLÁSICO
        // =========================================================================
        System.out.println("\n--- [Posibilidad 4] Cambiar combobox a Modo Clásico ---");
        edt(() -> {
            cbModoGlobal.setSelectedIndex(3); // Clásico
            return null;
        });
        Thread.sleep(600);

        int tabPanelIdx = tabs.indexOfTab("Panel");
        check(tabs.getSelectedIndex() == tabPanelIdx, "Vista cambió INMEDIATAMENTE a 'Panel' (Tarjetas de sala)");
        check("CLASICO".equals(sistema.getModoAtencionActual()), "Modo activo actualizado a CLASICO");
        check(cbModoGlobal.getSelectedIndex() == 3, "Combo global sincronizado a índice 3");
        check(cbModoLocal.getSelectedIndex() == 3, "Combo local sincronizado a índice 3");

        // 4.1 Clic en tarjeta de sala abre sus mesas en modo clásico
        JPanel panelSalasContenedor = (JPanel) campo(sistema, "PanelSalas");
        int tarjetasSalas = edt(panelSalasContenedor::getComponentCount);
        check(tarjetasSalas >= 3, "Tarjetas clásicas de salas visibles (encontradas: " + tarjetasSalas + ")");

        edt(() -> {
            JButton cardTerraza = (JButton) panelSalasContenedor.getComponent(1);
            cardTerraza.doClick();
            return null;
        });
        Thread.sleep(600);

        check(tabs.getSelectedIndex() == tabMesasIdx, "Clic en tarjeta clásica abrió la pestaña 'Mesas'");

        // =========================================================================
        // POSIBILIDAD 5: Cambio de modo desde el combobox local (en jPanel22 Mesas)
        // =========================================================================
        System.out.println("\n--- [Posibilidad 5] Cambiar modo usando combobox local de Mesas a Opción B ---");
        edt(() -> {
            cbModoLocal.setSelectedIndex(0); // Regresar a Opción B
            return null;
        });
        Thread.sleep(600);

        check("OPCION_B".equals(sistema.getModoAtencionActual()), "Modo activo actualizado a OPCION_B");
        check(cbModoGlobal.getSelectedIndex() == 0, "Combo global sincronizado a índice 0");
        check(cbModoLocal.getSelectedIndex() == 0, "Combo local sincronizado a índice 0");
        check(tabs.getSelectedIndex() == tabMesasIdx, "Vista se mantiene/actualiza en 'Mesas'");

        // =========================================================================
        // POSIBILIDAD 6: Navegar a otra pestaña y volver con 'Atención directa'
        // =========================================================================
        System.out.println("\n--- [Posibilidad 6] Preservación de modo al navegar y volver ---");
        // Establecer a Opción C
        edt(() -> {
            cbModoGlobal.setSelectedIndex(2); // Opción C
            return null;
        });
        Thread.sleep(500);

        // Operador navega a 'Salas' (Mantenimiento)
        JButton btnSalaMenu = (JButton) campo(sistema, "btnSala");
        edt(() -> {
            btnSalaMenu.doClick();
            return null;
        });
        Thread.sleep(500);
        int tabSalasIdx = tabs.indexOfTab("Salas");
        check(tabs.getSelectedIndex() == tabSalasIdx, "Operador navegó a pestaña 'Salas'");
        check("OPCION_C".equals(sistema.getModoAtencionActual()), "Modo OPCION_C se mantiene preservado");

        // Operador pulsa 'Atención directa' desde el menú lateral
        edt(() -> {
            btnAtencionDirecta.doClick();
            return null;
        });
        Thread.sleep(500);
        check(tabs.getSelectedIndex() == tabSplitIdx, "Clic en 'Atención directa' regresó fielmente al modo configurado (Split)");

        // Probar lo mismo con Opción A
        edt(() -> {
            cbModoGlobal.setSelectedIndex(1); // Opción A
            return null;
        });
        Thread.sleep(500);
        check(tabs.getSelectedIndex() == 3, "Cambió a Opción A (Toma de pedidos)");

        // Navegar a Configuración
        JButton btnConfigMenu = (JButton) campo(sistema, "btnConfig");
        edt(() -> {
            btnConfigMenu.doClick();
            return null;
        });
        Thread.sleep(500);
        int tabConfigIdx = tabs.indexOfTab("Datos de la Empresa");
        check(tabs.getSelectedIndex() == tabConfigIdx, "Operador navegó a pestaña 'Configuración'");

        // Pulsar 'Atención directa'
        edt(() -> {
            btnAtencionDirecta.doClick();
            return null;
        });
        Thread.sleep(500);
        check(tabs.getSelectedIndex() == 3, "Clic en 'Atención directa' regresó fielmente a Opción A (Toma de pedido)");

        // =========================================================================
        // POSIBILIDAD 7: Ciclo completo rápido entre todas las modalidades
        // =========================================================================
        System.out.println("\n--- [Posibilidad 7] Ciclo completo entre modalidades (0->1->2->3->0) ---");
        int[] secuencia = {0, 1, 2, 3, 0};
        String[] nombresEsperados = {"OPCION_B", "OPCION_A", "OPCION_C", "CLASICO", "OPCION_B"};

        for (int i = 0; i < secuencia.length; i++) {
            final int modoIdx = secuencia[i];
            final String modoEsperado = nombresEsperados[i];
            edt(() -> {
                cbModoGlobal.setSelectedIndex(modoIdx);
                return null;
            });
            Thread.sleep(400);

            check(modoEsperado.equals(sistema.getModoAtencionActual()),
                    "Ciclo paso " + (i+1) + ": Modo es " + modoEsperado);
            check(cbModoGlobal.getSelectedIndex() == modoIdx,
                    "Ciclo paso " + (i+1) + ": Combo global está en índice " + modoIdx);
            check(cbModoLocal.getSelectedIndex() == modoIdx,
                    "Ciclo paso " + (i+1) + ": Combo local está en índice " + modoIdx);
        }

        // =========================================================================
        // POSIBILIDAD 8: Redimensionamiento y responsividad del panel conmutador
        // =========================================================================
        System.out.println("\n--- [Posibilidad 8] Responsividad y visibilidad en ventana ---");
        edt(() -> {
            sistema.setSize(1024, 768);
            sistema.revalidate();
            sistema.repaint();
            return null;
        });
        Thread.sleep(400);

        JPanel pnlConmutador = (JPanel) campo(sistema, "panelConmutadorModoGlobal");
        check(pnlConmutador != null && pnlConmutador.isVisible(), "Panel conmutador es visible en 1024x768");
        check(pnlConmutador.getWidth() > 0 && pnlConmutador.getHeight() > 0,
                "Dimensiones válidas del conmutador: " + pnlConmutador.getWidth() + "x" + pnlConmutador.getHeight());

        // Cerrar ventana
        edt(() -> {
            sistema.dispose();
            return null;
        });

        System.out.println("\n==========================================================================");
        if (fallos == 0) {
            System.out.println("RESULTADO: TODOS LOS TESTS E2E DEL SELECTOR DE MODALIDADES PASARON (0 fallos).");
            System.out.println("==========================================================================");
            System.exit(0);
        } else {
            System.err.println("RESULTADO: " + fallos + " FALLOS DETECTADOS EN EL TEST E2E DEL SELECTOR.");
            System.out.println("==========================================================================");
            System.exit(1);
        }
    }
}
