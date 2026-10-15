package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Mesonero;
import Modelo.MesoneroDao;
import Modelo.Pedidos;
import Modelo.Platos;
import Servicio.PoliticaAcceso;
import Modelo.Salas;
import Servicio.ServicioMesa;
import Servicio.ServicioMesoneroNombre;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Opción C: Pantalla dividida de alta productividad (Floreant POS style).
 *
 * Panel panorámico donde no se alternan pestañas:
 * - Izquierda (42%): Selector de salas y grilla interactiva de mesas con códigos (P-1, T-2) y estado en vivo.
 * - Derecha (58%): Comanda activa, catálogo de platos rápido, ajuste de cantidades, total y acciones (cocina/cobro).
 *
 * 100% compatible con los controladores existentes y la persistencia en MySQL.
 */
public class PanelAtencionMesasSplit extends JPanel {

    private final Sistema sistema;
    private final SalasControlador salasControlador;
    private final PlatosControlador platosControlador;
    private final PedidosControlador pedidosControlador;
    private final PoliticaAcceso politicaAcceso;

    // Cache de salas y prefijos
    private List<Salas> salasCache = new ArrayList<>();
    private Map<Integer, String> prefijosPorSala = new HashMap<>();

    // Estado de la mesa actualmente seleccionada en el split
    private int idSalaSeleccionada = 0;
    private String nombreSalaSeleccionada = "";
    private int cantMesasSalaSeleccionada = 0;
    private boolean salaSeleccionadaEsBarra = false;
    private int numMesaSeleccionada = 0;
    private int idPedidoActivo = -1; // -1 si es nueva comanda, >0 si es pedido pendiente existente
    private String codigoMesaSeleccionada = "";

    // Componentes lado izquierdo (Salas y Mesas)
    private JPanel panelTabsSalas;
    private JPanel panelGrillaMesas;
    private JScrollPane scrollMesas;
    private JLabel lblEstadoSalas;
    private JButton btnRefrescarMesas;

    // Componentes lado derecho (Catálogo y Comanda)
    private JLabel lblCabeceraMesa;
    private JTextField txtBuscarPlato;
    private JTable tblCatalogoPlatos;
    private DefaultTableModel modeloCatalogoPlatos;
    private final Map<Integer, Platos> platosPorId = new HashMap<>();
    private JTable tblCarrito;
    private DefaultTableModel modeloCarrito;
    private JLabel lblTotalUsd;
    private JLabel lblTotalBs;
    private JComboBox<Object> cbMesonero;
    private JButton btnMenosCant;
    private JButton btnMasCant;
    private JTextField txtCantManual;
    private JButton btnEnviarCocina;
    private JButton btnCobrar;
    private JButton btnLimpiar;

    public PanelAtencionMesasSplit(
            Sistema sistema,
            SalasControlador salasControlador,
            PlatosControlador platosControlador,
            PedidosControlador pedidosControlador,
            PoliticaAcceso politicaAcceso
    ) {
        this.sistema = sistema;
        this.salasControlador = salasControlador;
        this.platosControlador = platosControlador;
        this.pedidosControlador = pedidosControlador;
        this.politicaAcceso = politicaAcceso;

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(460);
        splitPane.setResizeWeight(0.42);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        // --- Panel Izquierdo: Salas y Mesas ---
        JPanel pnlIzquierdo = new JPanel(new BorderLayout(0, 6));
        pnlIzquierdo.setBackground(new Color(245, 247, 250));
        pnlIzquierdo.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 4));

        // Barra superior de salas (Pestañas de sala rápidas)
        JPanel pnlNorteSalas = new JPanel(new BorderLayout());
        pnlNorteSalas.setOpaque(false);

        JLabel lblTituloSeccion = new JLabel(" MAPA DE MESAS (1 Clic)");
        lblTituloSeccion.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTituloSeccion.setForeground(new Color(0, 102, 102));
        pnlNorteSalas.add(lblTituloSeccion, BorderLayout.NORTH);

        panelTabsSalas = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        panelTabsSalas.setOpaque(false);
        JScrollPane scrollTabs = new JScrollPane(panelTabsSalas,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollTabs.setBorder(null);
        scrollTabs.setOpaque(false);
        scrollTabs.getViewport().setOpaque(false);
        scrollTabs.setPreferredSize(new Dimension(440, 44));
        pnlNorteSalas.add(scrollTabs, BorderLayout.CENTER);

        pnlIzquierdo.add(pnlNorteSalas, BorderLayout.NORTH);

        // Grilla de mesas top-aligned (evita que 1 sola fila se estire verticalmente)
        panelGrillaMesas = new JPanel(new GridLayout(0, 4, 6, 6));
        panelGrillaMesas.setBackground(Color.WHITE);
        panelGrillaMesas.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel pnlContenedorMesas = new JPanel(new BorderLayout());
        pnlContenedorMesas.setBackground(Color.WHITE);
        pnlContenedorMesas.add(panelGrillaMesas, BorderLayout.NORTH);

        scrollMesas = new JScrollPane(pnlContenedorMesas,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollMesas.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230), 1));
        scrollMesas.getVerticalScrollBar().setUnitIncrement(16);
        pnlIzquierdo.add(scrollMesas, BorderLayout.CENTER);

        // Footer izquierdo: leyenda y actualización
        JPanel pnlSurSalas = new JPanel(new BorderLayout());
        pnlSurSalas.setOpaque(false);
        lblEstadoSalas = new JLabel("Seleccione una mesa para atender");
        lblEstadoSalas.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblEstadoSalas.setForeground(new Color(100, 100, 100));
        pnlSurSalas.add(lblEstadoSalas, BorderLayout.WEST);

        btnRefrescarMesas = new JButton("Actualizar");
        btnRefrescarMesas.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnRefrescarMesas.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRefrescarMesas.addActionListener(e -> refrescarMesasSalaActual());
        pnlSurSalas.add(btnRefrescarMesas, BorderLayout.EAST);

        pnlIzquierdo.add(pnlSurSalas, BorderLayout.SOUTH);

        // --- Panel Derecho: Comanda de la Mesa y Catálogo de Platos ---
        JPanel pnlDerecho = new JPanel(new BorderLayout(0, 6));
        pnlDerecho.setBackground(Color.WHITE);
        pnlDerecho.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 6));

        // Cabecera de la mesa seleccionada
        JPanel pnlCabecera = new JPanel(new BorderLayout(8, 0));
        pnlCabecera.setBackground(new Color(230, 243, 241));
        pnlCabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 102), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        lblCabeceraMesa = new JLabel("MESA: Ninguna seleccionada (Haga clic en una mesa a la izquierda)");
        lblCabeceraMesa.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCabeceraMesa.setForeground(new Color(0, 102, 102));
        pnlCabecera.add(lblCabeceraMesa, BorderLayout.CENTER);

        pnlDerecho.add(pnlCabecera, BorderLayout.NORTH);

        // Centro derecho: Dividido en catálogo (arriba) y comanda/carrito (abajo)
        JSplitPane splitDerecho = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitDerecho.setDividerLocation(180);
        splitDerecho.setResizeWeight(0.35);
        splitDerecho.setContinuousLayout(true);
        splitDerecho.setBorder(null);

        // Parte superior: Buscador y catálogo de platos
        JPanel pnlCatalogo = new JPanel(new BorderLayout(0, 4));
        pnlCatalogo.setOpaque(false);
        pnlCatalogo.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 210), 1),
                "CATÁLOGO DE PLATOS (Clic para agregar)",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 11), new Color(80, 80, 80)
        ));

        JPanel pnlBuscar = new JPanel(new BorderLayout(4, 0));
        pnlBuscar.setOpaque(false);
        pnlBuscar.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        txtBuscarPlato = new JTextField();
        txtBuscarPlato.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtBuscarPlato.putClientProperty("JTextField.placeholderText", "Buscar plato o categoría...");
        txtBuscarPlato.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (tblCatalogoPlatos.getSelectedRow() < 0 && tblCatalogoPlatos.getRowCount() > 0) {
                        tblCatalogoPlatos.setRowSelectionInterval(0, 0);
                    }
                    agregarPlatoSeleccionadoAlCarrito();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (tblCatalogoPlatos.getRowCount() > 0) {
                        tblCatalogoPlatos.requestFocusInWindow();
                        if (tblCatalogoPlatos.getSelectedRow() < 0) {
                            tblCatalogoPlatos.setRowSelectionInterval(0, 0);
                        }
                    }
                    return;
                }
                filtrarCatalogoPlatos(txtBuscarPlato.getText().trim());
            }
        });
        pnlBuscar.add(new JLabel(" Buscar: "), BorderLayout.WEST);
        pnlBuscar.add(txtBuscarPlato, BorderLayout.CENTER);

        JPanel pnlAccionesBuscar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pnlAccionesBuscar.setOpaque(false);

        JButton btnLimpiarBuscar = new JButton("x");
        btnLimpiarBuscar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnLimpiarBuscar.setFocusable(false);
        btnLimpiarBuscar.setToolTipText("Limpiar filtro de búsqueda");
        btnLimpiarBuscar.addActionListener(e -> {
            txtBuscarPlato.setText("");
            filtrarCatalogoPlatos("");
            txtBuscarPlato.requestFocusInWindow();
        });

        JButton btnAddPlato = new JButton("+");
        btnAddPlato.setFont(new Font("Arial Black", Font.BOLD, 14));
        btnAddPlato.setBackground(new Color(0, 102, 102));
        btnAddPlato.setForeground(Color.WHITE);
        btnAddPlato.setFocusable(false);
        btnAddPlato.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAddPlato.setToolTipText("Agregar plato seleccionado al pedido (Enter)");
        btnAddPlato.addActionListener(e -> agregarPlatoSeleccionadoAlCarrito());

        pnlAccionesBuscar.add(btnLimpiarBuscar);
        pnlAccionesBuscar.add(btnAddPlato);
        pnlBuscar.add(pnlAccionesBuscar, BorderLayout.EAST);

        pnlCatalogo.add(pnlBuscar, BorderLayout.NORTH);

        modeloCatalogoPlatos = new DefaultTableModel(new Object[]{"ID", "Plato", "Precio"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblCatalogoPlatos = new JTable(modeloCatalogoPlatos);
        tblCatalogoPlatos.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tblCatalogoPlatos.setRowHeight(25);
        tblCatalogoPlatos.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblCatalogoPlatos.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tblCatalogoPlatos.getColumnModel().getColumn(0).setPreferredWidth(45);
        tblCatalogoPlatos.getColumnModel().getColumn(0).setMaxWidth(55);
        tblCatalogoPlatos.getColumnModel().getColumn(1).setPreferredWidth(350);
        tblCatalogoPlatos.getColumnModel().getColumn(2).setPreferredWidth(85);
        tblCatalogoPlatos.getColumnModel().getColumn(2).setMaxWidth(110);

        tblCatalogoPlatos.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1 && tblCatalogoPlatos.getSelectedRow() >= 0) {
                    agregarPlatoSeleccionadoAlCarrito();
                }
            }
        });
        tblCatalogoPlatos.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    agregarPlatoSeleccionadoAlCarrito();
                }
            }
        });

        JScrollPane scrollCatalogo = new JScrollPane(tblCatalogoPlatos);
        scrollCatalogo.setBorder(BorderFactory.createEmptyBorder());
        pnlCatalogo.add(scrollCatalogo, BorderLayout.CENTER);

        splitDerecho.setTopComponent(pnlCatalogo);

        // Parte inferior: Carrito y Comanda
        JPanel pnlComanda = new JPanel(new BorderLayout(0, 4));
        pnlComanda.setOpaque(false);
        pnlComanda.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 210), 1),
                "ITEMS DEL PEDIDO",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 11), new Color(80, 80, 80)
        ));

        modeloCarrito = new DefaultTableModel(new Object[]{"ID", "Plato", "Cant", "Precio", "SubTotal", "Comentario"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 5; // Solo comentario editable directo
            }
        };
        tblCarrito = new JTable(modeloCarrito);
        tblCarrito.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tblCarrito.setRowHeight(24);
        tblCarrito.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblCarrito.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tblCarrito.getColumnModel().getColumn(0).setPreferredWidth(40);
        tblCarrito.getColumnModel().getColumn(0).setMaxWidth(50);
        tblCarrito.getColumnModel().getColumn(1).setPreferredWidth(220);
        tblCarrito.getColumnModel().getColumn(2).setPreferredWidth(48);
        tblCarrito.getColumnModel().getColumn(2).setMaxWidth(55);
        tblCarrito.getColumnModel().getColumn(3).setPreferredWidth(70);
        tblCarrito.getColumnModel().getColumn(3).setMaxWidth(85);
        tblCarrito.getColumnModel().getColumn(4).setPreferredWidth(80);
        tblCarrito.getColumnModel().getColumn(4).setMaxWidth(95);
        tblCarrito.getColumnModel().getColumn(5).setPreferredWidth(120);

        tblCarrito.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tblCarrito.getSelectedRow() >= 0) {
                int fila = tblCarrito.getSelectedRow();
                txtCantManual.setText(String.valueOf(tblCarrito.getValueAt(fila, 2)));
            }
        });

        JScrollPane scrollCarrito = new JScrollPane(tblCarrito);
        scrollCarrito.setBorder(BorderFactory.createEmptyBorder());
        pnlComanda.add(scrollCarrito, BorderLayout.CENTER);

        // Barra de ajuste de cantidades rápida
        JPanel pnlAjusteCant = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        pnlAjusteCant.setOpaque(false);

        btnMenosCant = new JButton("−");
        btnMenosCant.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnMenosCant.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMenosCant.addActionListener(e -> ajustarCantidadFilaSeleccionada(-1));
        pnlAjusteCant.add(btnMenosCant);

        txtCantManual = new JTextField("1", 3);
        txtCantManual.setFont(new Font("Segoe UI", Font.BOLD, 12));
        txtCantManual.setHorizontalAlignment(JTextField.CENTER);
        txtCantManual.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    aplicarCantidadManual();
                }
            }
        });
        pnlAjusteCant.add(txtCantManual);

        btnMasCant = new JButton("+");
        btnMasCant.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnMasCant.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMasCant.addActionListener(e -> ajustarCantidadFilaSeleccionada(1));
        pnlAjusteCant.add(btnMasCant);

        JButton btnEliminarFila = new JButton("Quitar");
        btnEliminarFila.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnEliminarFila.setForeground(new Color(180, 40, 40));
        btnEliminarFila.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEliminarFila.addActionListener(e -> eliminarFilaSeleccionada());
        pnlAjusteCant.add(btnEliminarFila);

        pnlComanda.add(pnlAjusteCant, BorderLayout.SOUTH);

        splitDerecho.setBottomComponent(pnlComanda);

        pnlDerecho.add(splitDerecho, BorderLayout.CENTER);

        // Sur derecho: Totales, Mesonero y Acciones de Comanda
        JPanel pnlAcciones = new JPanel();
        pnlAcciones.setLayout(new BoxLayout(pnlAcciones, BoxLayout.Y_AXIS));
        pnlAcciones.setOpaque(false);
        pnlAcciones.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 215, 220)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        // Fila 1: Mesonero y Totales
        JPanel pnlFilaTotales = new JPanel(new BorderLayout(8, 0));
        pnlFilaTotales.setOpaque(false);

        JPanel pnlMesonero = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        pnlMesonero.setOpaque(false);
        JLabel lblMesoneroPrompt = new JLabel("Mesonero:");
        lblMesoneroPrompt.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pnlMesonero.add(lblMesoneroPrompt);
        cbMesonero = new JComboBox<>();
        cbMesonero.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cbMesonero.setPreferredSize(new Dimension(180, 26));
        cbMesonero.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Mesonero m) {
                    setText(m.getNombreCompleto());
                }
                return this;
            }
        });
        pnlMesonero.add(cbMesonero);
        pnlFilaTotales.add(pnlMesonero, BorderLayout.WEST);

        JPanel pnlTotales = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlTotales.setOpaque(false);
        lblTotalUsd = new JLabel("Total: $ 0.00");
        lblTotalUsd.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalUsd.setForeground(new Color(0, 102, 102));
        pnlTotales.add(lblTotalUsd);

        lblTotalBs = new JLabel("(Bs. 0.00)");
        lblTotalBs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTotalBs.setForeground(new Color(80, 80, 80));
        pnlTotales.add(lblTotalBs);
        pnlFilaTotales.add(pnlTotales, BorderLayout.EAST);

        pnlAcciones.add(pnlFilaTotales);

        // Fila 2: Botones de Acción
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        pnlBotones.setOpaque(false);

        btnLimpiar = new JButton("Limpiar Comanda");
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLimpiar.setPreferredSize(new Dimension(130, 28));
        btnLimpiar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLimpiar.addActionListener(e -> limpiarComanda());
        pnlBotones.add(btnLimpiar);

        btnEnviarCocina = new JButton("Enviar a Cocina");
        btnEnviarCocina.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnEnviarCocina.setBackground(new Color(0, 102, 102));
        btnEnviarCocina.setForeground(Color.WHITE);
        btnEnviarCocina.setPreferredSize(new Dimension(140, 28));
        btnEnviarCocina.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEnviarCocina.addActionListener(e -> enviarPedidoCocina());
        pnlBotones.add(btnEnviarCocina);

        btnCobrar = new JButton("Cobrar / Facturar");
        btnCobrar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCobrar.setBackground(new Color(40, 116, 166));
        btnCobrar.setForeground(Color.WHITE);
        btnCobrar.setPreferredSize(new Dimension(145, 28));
        btnCobrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCobrar.addActionListener(e -> cobrarPedidoDirecto());
        pnlBotones.add(btnCobrar);

        pnlAcciones.add(pnlBotones);
        pnlDerecho.add(pnlAcciones, BorderLayout.SOUTH);

        splitPane.setLeftComponent(pnlIzquierdo);
        splitPane.setRightComponent(pnlDerecho);
        add(splitPane, BorderLayout.CENTER);

        cargarMesoneros();
        cargarCatalogoPlatosInicial();
    }

    /**
     * Actualiza la lista de salas disponibles y refresca las pestañas de sala.
     */
    public void actualizarSalas(List<Salas> salas, Map<Integer, String> prefijos) {
        if (salas == null) return;
        this.salasCache = new ArrayList<>(salas);
        this.prefijosPorSala = prefijos != null ? new HashMap<>(prefijos) : ServicioMesa.generarPrefijosSalas(salas);

        panelTabsSalas.removeAll();
        Map<Integer, Integer> ocupadas = Collections.emptyMap();
        try {
            ocupadas = pedidosControlador.contarMesasOcupadasPorSala();
        } catch (Exception ignored) {}

        Salas primeraSala = null;
        for (Salas s : salasCache) {
            if (s == null) continue;
            if (primeraSala == null) primeraSala = s;

            int id = s.getId();
            String prefijo = prefijosPorSala.getOrDefault(id, ServicioMesa.sugerirPrefijoBase(s.getNombre(), s.esBarra()));
            int cantMesas = s.getMesas();
            int cantOcupadas = ocupadas.getOrDefault(id, 0);

            JButton btnTab = new JButton(s.getNombre() + " (" + prefijo + ") [" + cantOcupadas + "/" + cantMesas + "]");
            btnTab.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnTab.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnTab.setFocusable(false);

            boolean esActiva = (id == idSalaSeleccionada);
            if (esActiva) {
                btnTab.setBackground(new Color(0, 102, 102));
                btnTab.setForeground(Color.WHITE);
            } else {
                btnTab.setBackground(new Color(235, 238, 242));
                btnTab.setForeground(new Color(50, 50, 50));
            }

            btnTab.addActionListener(e -> seleccionarSala(s));
            panelTabsSalas.add(btnTab);
        }

        panelTabsSalas.revalidate();
        panelTabsSalas.repaint();

        // Si aún no hay sala seleccionada, seleccionar la primera
        if (idSalaSeleccionada == 0 && primeraSala != null) {
            seleccionarSala(primeraSala);
        } else if (idSalaSeleccionada > 0) {
            refrescarMesasSalaActual();
        }
    }

    /**
     * Selecciona una sala y carga su grilla de mesas con los prefijos correspondientes.
     */
    public void seleccionarSala(Salas sala) {
        if (sala == null) return;
        this.idSalaSeleccionada = sala.getId();
        this.nombreSalaSeleccionada = sala.getNombre();
        this.cantMesasSalaSeleccionada = sala.getMesas();
        this.salaSeleccionadaEsBarra = sala.esBarra();

        // Actualizar visual de los botones de pestañas
        for (java.awt.Component c : panelTabsSalas.getComponents()) {
            if (c instanceof JButton b) {
                String prefijo = prefijosPorSala.getOrDefault(sala.getId(), "");
                boolean coincide = b.getText().contains(sala.getNombre());
                if (coincide) {
                    b.setBackground(new Color(0, 102, 102));
                    b.setForeground(Color.WHITE);
                } else {
                    b.setBackground(new Color(235, 238, 242));
                    b.setForeground(new Color(50, 50, 50));
                }
            }
        }

        refrescarMesasSalaActual();
    }

    private void refrescarMesasSalaActual() {
        if (idSalaSeleccionada <= 0 || cantMesasSalaSeleccionada <= 0) return;

        panelGrillaMesas.removeAll();
        lblEstadoSalas.setText("Cargando mesas de " + nombreSalaSeleccionada + "...");

        new javax.swing.SwingWorker<PanelMesasDatos, Void>() {
            @Override
            protected PanelMesasDatos doInBackground() {
                Map<Integer, Integer> estados = new java.util.LinkedHashMap<>();
                for (int m = 1; m <= cantMesasSalaSeleccionada; m++) {
                    estados.put(m, pedidosControlador.buscarPedidoPendiente(m, idSalaSeleccionada));
                }
                Map<Integer, String> mesoneros = pedidosControlador.consultarMesonerosMesasPendientes(idSalaSeleccionada);
                return new PanelMesasDatos(estados, mesoneros);
            }

            @Override
            protected void done() {
                try {
                    PanelMesasDatos datos = get();
                    mostrarMesasEnGrilla(datos.estados, datos.mesoneros);
                } catch (Exception ex) {
                    lblEstadoSalas.setText("Error al cargar mesas: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private record PanelMesasDatos(Map<Integer, Integer> estados, Map<Integer, String> mesoneros) {}

    private void mostrarMesasEnGrilla(Map<Integer, Integer> estados, Map<Integer, String> mesoneros) {
        panelGrillaMesas.removeAll();
        String prefijoSala = prefijosPorSala.getOrDefault(idSalaSeleccionada,
                ServicioMesa.sugerirPrefijoBase(nombreSalaSeleccionada, salaSeleccionadaEsBarra));

        Map<String, String> nombresVisuales = ServicioMesoneroNombre.resolverNombresVisualesDesdeStrings(mesoneros.values());

        for (int i = 1; i <= cantMesasSalaSeleccionada; i++) {
            int numMesa = i;
            String codigoMesa = ServicioMesa.formatearCodigo(prefijoSala, numMesa);
            int idPed = estados.getOrDefault(numMesa, 0);
            boolean ocupada = (idPed > 0);
            String nombreMesonero = mesoneros.get(numMesa);
            String visualMesonero = (nombreMesonero != null && !nombreMesonero.isBlank())
                    ? nombresVisuales.getOrDefault(nombreMesonero.trim(), ServicioMesoneroNombre.extraerPrimerNombre(nombreMesonero))
                    : null;

            JButton btnMesa = new JButton();
            btnMesa.setPreferredSize(new Dimension(100, 75));
            btnMesa.setMinimumSize(new Dimension(80, 65));
            btnMesa.putClientProperty("numMesa", numMesa);
            btnMesa.setFocusable(false);
            btnMesa.setCursor(new Cursor(Cursor.HAND_CURSOR));

            if (ocupada) {
                btnMesa.setBackground(new Color(192, 57, 43));
                btnMesa.setForeground(Color.WHITE);
                btnMesa.setText("<html><center><font size='+1'><b>" + codigoMesa + "</b></font><br>"
                        + "<span style='font-size:10px;'>" + (salaSeleccionadaEsBarra ? "Puesto " : "Mesa ") + numMesa + "</span>"
                        + (visualMesonero != null ? "<br><font color='#FFFF99' size='2'><b>" + visualMesonero + "</b></font>" : "")
                        + "</center></html>");
                btnMesa.setToolTipText("Ocupada (" + codigoMesa + ") - Pedido #" + idPed + (nombreMesonero != null ? " (" + nombreMesonero + ")" : ""));
            } else {
                btnMesa.setBackground(new Color(0, 102, 102));
                btnMesa.setForeground(Color.WHITE);
                btnMesa.setText("<html><center><font size='+1'><b>" + codigoMesa + "</b></font><br>"
                        + "<span style='font-size:10px;'>" + (salaSeleccionadaEsBarra ? "Puesto " : "Mesa ") + numMesa + "</span><br>"
                        + "<span style='color:#A2D9CE;font-size:10px;'>Libre</span></center></html>");
                btnMesa.setToolTipText("Disponible (" + codigoMesa + ")");
            }

            // Resaltar si esta mesa es la actualmente abierta en el panel derecho
            if (numMesa == numMesaSeleccionada) {
                btnMesa.setBorder(BorderFactory.createLineBorder(new Color(241, 196, 15), 3));
            } else {
                btnMesa.setBorder(BorderFactory.createLineBorder(btnMesa.getBackground().darker(), 1));
            }

            btnMesa.addActionListener(e -> seleccionarMesaParaComanda(numMesa, codigoMesa, idPed));
            panelGrillaMesas.add(btnMesa);
        }

        panelGrillaMesas.revalidate();
        panelGrillaMesas.repaint();
        lblEstadoSalas.setText(nombreSalaSeleccionada + ": " + cantMesasSalaSeleccionada + (salaSeleccionadaEsBarra ? " puestos" : " mesas"));
    }

    /**
     * Al hacer 1 solo clic en una mesa, la carga instantáneamente en el lado derecho.
     */
    public void seleccionarMesaParaComanda(int numMesa, String codigoMesa, int idPedidoExistente) {
        this.numMesaSeleccionada = numMesa;
        this.codigoMesaSeleccionada = codigoMesa;
        this.idPedidoActivo = idPedidoExistente;

        // Actualizar bordes de selección en la grilla
        for (java.awt.Component c : panelGrillaMesas.getComponents()) {
            if (c instanceof JButton b) {
                Object val = b.getClientProperty("numMesa");
                if (val instanceof Integer nm && nm == numMesa) {
                    b.setBorder(BorderFactory.createLineBorder(new Color(241, 196, 15), 3));
                } else {
                    b.setBorder(BorderFactory.createLineBorder(b.getBackground().darker(), 1));
                }
            }
        }

        if (idPedidoExistente > 0) {
            if (politicaAcceso != null && !politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                JOptionPane.showMessageDialog(this,
                        "Este espacio tiene un pedido pendiente (#" + idPedidoExistente + "). Solicita a un administrador que lo gestione.",
                        "Pedido pendiente", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Mesa ocupada: Cargar su pedido pendiente en el carrito
            lblCabeceraMesa.setText("MESA: " + codigoMesa + " (" + nombreSalaSeleccionada + ") — OCUPADA [Pedido #" + idPedidoExistente + "]");
            lblCabeceraMesa.setForeground(new Color(180, 40, 40));
            btnEnviarCocina.setText("Actualizar Pedido");
            btnCobrar.setEnabled(true);
            cargarDetallesPedidoExistente(idPedidoExistente);
        } else {
            // Mesa disponible: Comanda en blanco lista para agregar platos
            lblCabeceraMesa.setText("MESA: " + codigoMesa + " (" + nombreSalaSeleccionada + ") — DISPONIBLE (Nuevo Pedido)");
            lblCabeceraMesa.setForeground(new Color(0, 102, 102));
            btnEnviarCocina.setText("Enviar a Cocina");
            btnCobrar.setEnabled(false);
            modeloCarrito.setRowCount(0);
            recalcularTotales();
            txtBuscarPlato.requestFocusInWindow();
        }
    }

    private void cargarDetallesPedidoExistente(int idPedido) {
        modeloCarrito.setRowCount(0);
        new javax.swing.SwingWorker<List<DetallePedido>, Void>() {
            @Override
            protected List<DetallePedido> doInBackground() {
                return pedidosControlador.verPedidoDetalle(idPedido);
            }

            @Override
            protected void done() {
                try {
                    List<DetallePedido> detalles = get();
                    for (DetallePedido d : detalles) {
                        BigDecimal precio = d.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal sub = precio.multiply(BigDecimal.valueOf(d.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                        modeloCarrito.addRow(new Object[]{
                                d.getId(),
                                d.getNombre(),
                                d.getCantidad(),
                                String.format(java.util.Locale.US, "%.2f", precio),
                                String.format(java.util.Locale.US, "%.2f", sub),
                                d.getComentario() != null ? d.getComentario() : ""
                        });
                    }
                    recalcularTotales();
                } catch (Exception ex) {
                    sistema.mostrarAvisoEnBanda("Error al cargar comanda existente: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void cargarCatalogoPlatosInicial() {
        filtrarCatalogoPlatos("");
    }

    private void filtrarCatalogoPlatos(String filtro) {
        new javax.swing.SwingWorker<List<Platos>, Void>() {
            @Override
            protected List<Platos> doInBackground() {
                String hoy = java.time.LocalDate.now().toString();
                List<Platos> platos = platosControlador.listarPorFecha(filtro, hoy);
                return platos != null ? platos : java.util.Collections.emptyList();
            }

            @Override
            protected void done() {
                try {
                    List<Platos> platos = get();
                    int filaSel = tblCatalogoPlatos.getSelectedRow();
                    Integer idSel = (filaSel >= 0 && filaSel < tblCatalogoPlatos.getRowCount())
                            ? (Integer) tblCatalogoPlatos.getValueAt(filaSel, 0) : null;
                    platosPorId.clear();
                    modeloCatalogoPlatos.setRowCount(0);
                    int filaRestaurar = -1;
                    if (platos != null) {
                        for (Platos p : platos) {
                            platosPorId.put(p.getId(), p);
                            BigDecimal precio = p.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                            modeloCatalogoPlatos.addRow(new Object[]{
                                    p.getId(),
                                    p.getNombre(),
                                    "$ " + String.format(java.util.Locale.US, "%.2f", precio)
                            });
                            if (idSel != null && idSel.equals(p.getId())) {
                                filaRestaurar = modeloCatalogoPlatos.getRowCount() - 1;
                            }
                        }
                    }
                    if (filaRestaurar >= 0 && filaRestaurar < tblCatalogoPlatos.getRowCount()) {
                        tblCatalogoPlatos.setRowSelectionInterval(filaRestaurar, filaRestaurar);
                    } else if (tblCatalogoPlatos.getRowCount() > 0) {
                        tblCatalogoPlatos.setRowSelectionInterval(0, 0);
                    }
                    RendererPlato renderer = new RendererPlato(platosPorId);
                    for (int i = 0; i < tblCatalogoPlatos.getColumnCount(); i++) {
                        tblCatalogoPlatos.getColumnModel().getColumn(i).setCellRenderer(renderer);
                    }
                    tblCatalogoPlatos.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void agregarPlatoSeleccionadoAlCarrito() {
        if (numMesaSeleccionada <= 0) {
            sistema.mostrarAvisoEnBanda("Primero selecciona una mesa a la izquierda");
            return;
        }

        int fila = tblCatalogoPlatos.getSelectedRow();
        if (fila < 0) return;

        // Feedback visual verde de confirmacion al agregar
        tblCatalogoPlatos.setSelectionBackground(new Color(144, 238, 144));
        new javax.swing.Timer(300, ev -> tblCatalogoPlatos.setSelectionBackground(javax.swing.UIManager.getColor("Table.selectionBackground"))).start();

        int idPlato = (int) tblCatalogoPlatos.getValueAt(fila, 0);
        String nombre = (String) tblCatalogoPlatos.getValueAt(fila, 1);
        String precioStr = tblCatalogoPlatos.getValueAt(fila, 2).toString().replace("$", "").trim();
        BigDecimal precio = new BigDecimal(precioStr).setScale(2, RoundingMode.HALF_UP);

        // Si ya existe en el carrito, sumar 1
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idFila = (int) modeloCarrito.getValueAt(i, 0);
            if (idFila == idPlato) {
                int cantActual = (int) modeloCarrito.getValueAt(i, 2);
                int nuevaCant = cantActual + 1;
                modeloCarrito.setValueAt(nuevaCant, i, 2);
                modeloCarrito.setValueAt(String.format(java.util.Locale.US, "%.2f", precio.multiply(BigDecimal.valueOf(nuevaCant))), i, 4);
                tblCarrito.setRowSelectionInterval(i, i);
                txtCantManual.setText(String.valueOf(nuevaCant));
                recalcularTotales();
                return;
            }
        }

        // Si no existe, agregar nueva fila con cantidad 1
        modeloCarrito.addRow(new Object[]{
                idPlato,
                nombre,
                1,
                String.format(java.util.Locale.US, "%.2f", precio),
                String.format(java.util.Locale.US, "%.2f", precio),
                ""
        });
        int indexNuevo = modeloCarrito.getRowCount() - 1;
        tblCarrito.setRowSelectionInterval(indexNuevo, indexNuevo);
        txtCantManual.setText("1");
        recalcularTotales();
    }

    private void ajustarCantidadFilaSeleccionada(int delta) {
        int fila = tblCarrito.getSelectedRow();
        if (fila < 0) return;

        int cantActual = (int) modeloCarrito.getValueAt(fila, 2);
        int nuevaCant = cantActual + delta;
        if (nuevaCant <= 0) {
            modeloCarrito.removeRow(fila);
            recalcularTotales();
            return;
        }
        if (nuevaCant > DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
            sistema.mostrarAvisoEnBanda("Cantidad máxima permitida: " + DetallePedido.CANTIDAD_MAXIMA_POR_PLATO);
            return;
        }

        BigDecimal precio = new BigDecimal(modeloCarrito.getValueAt(fila, 3).toString());
        modeloCarrito.setValueAt(nuevaCant, fila, 2);
        modeloCarrito.setValueAt(String.format(java.util.Locale.US, "%.2f", precio.multiply(BigDecimal.valueOf(nuevaCant))), fila, 4);
        txtCantManual.setText(String.valueOf(nuevaCant));
        recalcularTotales();
    }

    private void aplicarCantidadManual() {
        int fila = tblCarrito.getSelectedRow();
        if (fila < 0) return;

        try {
            int cant = Integer.parseInt(txtCantManual.getText().trim());
            if (cant <= 0) {
                modeloCarrito.removeRow(fila);
            } else if (cant > DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
                sistema.mostrarAvisoEnBanda("Cantidad máxima permitida: " + DetallePedido.CANTIDAD_MAXIMA_POR_PLATO);
                txtCantManual.setText(String.valueOf(modeloCarrito.getValueAt(fila, 2)));
            } else {
                BigDecimal precio = new BigDecimal(modeloCarrito.getValueAt(fila, 3).toString());
                modeloCarrito.setValueAt(cant, fila, 2);
                modeloCarrito.setValueAt(String.format(java.util.Locale.US, "%.2f", precio.multiply(BigDecimal.valueOf(cant))), fila, 4);
            }
            recalcularTotales();
        } catch (NumberFormatException ex) {
            txtCantManual.setText(String.valueOf(modeloCarrito.getValueAt(fila, 2)));
        }
    }

    private void eliminarFilaSeleccionada() {
        int fila = tblCarrito.getSelectedRow();
        if (fila >= 0) {
            modeloCarrito.removeRow(fila);
            recalcularTotales();
        }
    }

    private void limpiarComanda() {
        if (modeloCarrito.getRowCount() > 0) {
            int resp = JOptionPane.showConfirmDialog(this,
                    "¿Desea limpiar los items de la comanda actual?",
                    "Limpiar Comanda", JOptionPane.YES_NO_OPTION);
            if (resp == JOptionPane.YES_OPTION) {
                modeloCarrito.setRowCount(0);
                recalcularTotales();
            }
        }
    }

    private void recalcularTotales() {
        BigDecimal totalUsd = BigDecimal.ZERO;
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            BigDecimal sub = new BigDecimal(modeloCarrito.getValueAt(i, 4).toString());
            totalUsd = totalUsd.add(sub);
        }

        lblTotalUsd.setText("Total: $ " + String.format(java.util.Locale.US, "%.2f", totalUsd));
        BigDecimal tasa = sistema.getTasaActual();
        if (tasa != null && tasa.signum() > 0) {
            BigDecimal totalBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            lblTotalBs.setText("(Bs. " + String.format(java.util.Locale.US, "%.2f", totalBs) + ")");
        } else {
            lblTotalBs.setText("(Bs. N/D)");
        }

        btnCobrar.setEnabled(idPedidoActivo > 0 || modeloCarrito.getRowCount() > 0);
    }

    private void enviarPedidoCocina() {
        if (numMesaSeleccionada <= 0 || idSalaSeleccionada <= 0) {
            sistema.mostrarAvisoEnBanda("Seleccione una mesa primero");
            return;
        }
        if (modeloCarrito.getRowCount() == 0) {
            sistema.mostrarAvisoEnBanda("Agregue al menos un plato a la comanda");
            return;
        }

        BigDecimal tasa = sistema.getTasaActual();
        if (tasa == null || tasa.signum() <= 0) tasa = new BigDecimal("36.5000");
        BigDecimal ivaPorcentaje = new BigDecimal("16.00");

        BigDecimal baseImponible = BigDecimal.ZERO;
        BigDecimal exento = BigDecimal.ZERO;
        List<DetallePedido> detalles = new ArrayList<>();

        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idPlato = (int) modeloCarrito.getValueAt(i, 0);
            String nombre = modeloCarrito.getValueAt(i, 1).toString();
            int cant = (int) modeloCarrito.getValueAt(i, 2);
            BigDecimal precio = new BigDecimal(modeloCarrito.getValueAt(i, 3).toString());
            BigDecimal sub = new BigDecimal(modeloCarrito.getValueAt(i, 4).toString());
            String comentario = modeloCarrito.getValueAt(i, 5) != null ? modeloCarrito.getValueAt(i, 5).toString() : "";

            DetallePedido det = new DetallePedido();
            det.setNombre(nombre);
            det.setCantidad(cant);
            det.setPrecioDecimal(precio);
            det.setComentario(comentario);
            detalles.add(det);

            baseImponible = baseImponible.add(sub); // Simplificado por defecto base imponible
        }

        Pedidos p = new Pedidos();
        p.setId_sala(idSalaSeleccionada);
        p.setNum_mesa(numMesaSeleccionada);
        p.setTipoPedido("MESA");
        p.setSubtotal(baseImponible);
        p.setIvaPorcentaje(ivaPorcentaje);
        BigDecimal ivaMonto = baseImponible.multiply(ivaPorcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        p.setIvaMonto(ivaMonto);
        BigDecimal totalUsd = baseImponible.add(ivaMonto);
        p.setTotalDecimal(totalUsd);
        p.setTasaCambio(tasa);
        p.setSubtotalBs(baseImponible.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
        p.setIvaBs(ivaMonto.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
        p.setTotalBs(totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
        p.setUsuario(sistema.getNombreUsuarioActual());

        if (cbMesonero.getSelectedItem() instanceof Mesonero m) {
            p.setIdMesonero(m.getId());
            p.setMesoneroNombre(m.getNombreCompleto());
        }

        try {
            if (idPedidoActivo > 0) {
                if (politicaAcceso != null && !politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                    JOptionPane.showMessageDialog(this,
                            "Tu rol no permite modificar pedidos existentes.",
                            "Acceso Denegado", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                pedidosControlador.actualizarPedidoCompleto(idPedidoActivo, p, detalles);
                sistema.mostrarAvisoEnBanda("Pedido actualizado para " + codigoMesaSeleccionada);
            } else {
                int idNuevo = pedidosControlador.registrarPedidoCompleto(p, detalles);
                this.idPedidoActivo = idNuevo;
                sistema.mostrarAvisoEnBanda("Pedido enviado a cocina para " + codigoMesaSeleccionada + " [#" + idNuevo + "]");
            }
            refrescarMesasSalaActual();
        } catch (Exception ex) {
            sistema.mostrarAvisoEnBanda("Error al procesar: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Error al procesar el pedido: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cobrarPedidoDirecto() {
        if (idPedidoActivo > 0) {
            sistema.cargarPedidoEnPantallaDesdeSplit(idPedidoActivo);
        } else if (modeloCarrito.getRowCount() > 0) {
            // Guardar primero y luego cobrar
            enviarPedidoCocina();
            if (idPedidoActivo > 0) {
                sistema.cargarPedidoEnPantallaDesdeSplit(idPedidoActivo);
            }
        } else {
            sistema.mostrarAvisoEnBanda("No hay pedido para cobrar en esta mesa");
        }
    }

    private void cargarMesoneros() {
        cbMesonero.removeAllItems();
        cbMesonero.addItem("- Sin mesonero -");
        try {
            MesoneroDao dao = new MesoneroDao();
            List<Mesonero> activos = dao.listarActivos();
            for (Mesonero m : activos) {
                cbMesonero.addItem(m);
            }
        } catch (Exception ignored) {}
    }

    public void seleccionarSalaPorId(int idSala) {
        for (Salas s : salasCache) {
            if (s.getId() == idSala) {
                seleccionarSala(s);
                return;
            }
        }
    }

    public void seleccionarMesa(int numMesa, String codigoMesa) {
        seleccionarMesaParaComanda(numMesa, codigoMesa, -1);
    }

    public int getCantidadItemsEnCarrito() {
        return modeloCarrito != null ? modeloCarrito.getRowCount() : 0;
    }

    public JTable getTablaCatalogo() {
        return tblCatalogoPlatos;
    }

    public void agregarPlatoSeleccionadoACarrito() {
        agregarPlatoSeleccionadoAlCarrito();
    }
}
