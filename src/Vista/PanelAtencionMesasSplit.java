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
import Servicio.CalculoMonetarioPedido;

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
import javax.swing.JCheckBox;
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
    private Integer idMesoneroOriginal = null;
    private String nombreMesoneroOriginal = null;
    private boolean mesoneroQuitadoExplicito = false;
    private boolean sincronizandoComandaProgramaticamente = false;
    private final java.util.concurrent.atomic.AtomicLong tokenRevisionMesas = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong tokenRevisionPedido = new java.util.concurrent.atomic.AtomicLong(0);

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
    private JButton btnPreFactura;
    private JButton btnCobrar;
    private JButton btnCortesia;
    private JButton btnFuga;
    private JButton btnLimpiar;
    private JCheckBox chkImprimirComanda;
    private ImageIcon iconoMesaSplit;
    private JCheckBox chkFiltroRapidoSplit;
    private Integer desplazarAPlatoIdSplit = null;
    private final Map<Integer, DetallePedido> detallesExistentesCarrito = new HashMap<>();
    /** Ancho objetivo por tarjeta (botón 120 + gaps): la grilla ajusta columnas al viewport. */
    private static final int ANCHO_TARJETA_MESA_OBJETIVO = 135;

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
        scrollMesas.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarColumnasGrillaMesas();
            }
        });
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
        btnRefrescarMesas.setToolTipText("Recargar el estado de las mesas de la sala");
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
        chkFiltroRapidoSplit = new JCheckBox("Auto-limpiar", true);
        chkFiltroRapidoSplit.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chkFiltroRapidoSplit.setToolTipText("Al elegir un plato se limpia el filtro y se muestra en la lista completa");
        chkFiltroRapidoSplit.addItemListener(e -> {
            boolean sel = chkFiltroRapidoSplit.isSelected();
            sistema.guardarPreferenciaFiltroRapido(sel);
        });
        pnlAccionesBuscar.add(chkFiltroRapidoSplit, 0);
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
        for (int i = 0; i < tblCatalogoPlatos.getColumnModel().getColumnCount(); i++) {
            tblCatalogoPlatos.getColumnModel().getColumn(i).setPreferredWidth(100);
            tblCatalogoPlatos.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
            tblCatalogoPlatos.getColumnModel().getColumn(i).setMinWidth(30);
        }
        tblCatalogoPlatos.getColumnModel().getColumn(1).setPreferredWidth(300);
        tblCatalogoPlatos.getColumnModel().getColumn(2).setPreferredWidth(160);
        Tables.ocultarColumnaModelo(tblCatalogoPlatos, 0); // ID interno: oculto, sigue en el modelo
        Tables.agregarColumnaMoneda(tblCatalogoPlatos, 2, this::monedaUbFilaCatalogo);
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

        modeloCarrito = new DefaultTableModel(new Object[]{"ID", "Plato", "Cant.", "Precio", "Sub-Total", "Comentario"}, 0) {
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
        // Ancho proporcional igualitario: sin topes, AUTO_RESIZE reparte según
        // preferredWidth, así cada columna ocupa 1/6 del viewport y crece pareja.
        for (int i = 0; i < tblCarrito.getColumnModel().getColumnCount(); i++) {
            tblCarrito.getColumnModel().getColumn(i).setPreferredWidth(100);
            tblCarrito.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
            tblCarrito.getColumnModel().getColumn(i).setMinWidth(30);
        }
        // Precio/SubTotal con mínimo suficiente para el importe más largo.
        for (int mc : new int[]{3, 4}) {
            int v = tblCarrito.convertColumnIndexToView(mc);
            if (v >= 0) {
                tblCarrito.getColumnModel().getColumn(v).setMinWidth(110);
                tblCarrito.getColumnModel().getColumn(v).setPreferredWidth(110);
            }
        }
        // ID y Comentario fuera de la vista (siguen en el modelo para guardar correctamente).
        Tables.ocultarColumnaModelo(tblCarrito, 0);
        Tables.ocultarColumnaModelo(tblCarrito, 5);
        Tables.agregarColumnaMoneda(tblCarrito, 3, this::monedaUbFilaCarrito);

        tblCarrito.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tblCarrito.getSelectedRow() >= 0) {
                int fila = tblCarrito.getSelectedRow();
                txtCantManual.setText(String.valueOf(Tables.valorModelo(tblCarrito, fila, 2)));
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
        btnMenosCant.setToolTipText("Disminuir cantidad de la línea seleccionada");
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
        btnMasCant.setToolTipText("Aumentar cantidad de la línea seleccionada");
        btnMasCant.addActionListener(e -> ajustarCantidadFilaSeleccionada(1));
        pnlAjusteCant.add(btnMasCant);

        JButton btnEliminarFila = new JButton("Quitar");
        btnEliminarFila.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnEliminarFila.setForeground(new Color(180, 40, 40));
        btnEliminarFila.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEliminarFila.setToolTipText("Quitar la línea seleccionada de la comanda");
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
        cbMesonero.setPreferredSize(new Dimension(140, 26));
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
        JButton btnQuitarMesonero = new JButton("Quitar");
        btnQuitarMesonero.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnQuitarMesonero.setToolTipText("Quitar la asignación del mesonero (se guarda sin mesonero)");
        btnQuitarMesonero.addActionListener(e -> quitarMesoneroAsignado());
        pnlMesonero.add(btnQuitarMesonero);

        chkImprimirComanda = new JCheckBox("Imprimir Comanda", true);
        chkImprimirComanda.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chkImprimirComanda.setForeground(new Color(0, 102, 102));
        chkImprimirComanda.setOpaque(false);
        chkImprimirComanda.setFocusable(false);
        chkImprimirComanda.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chkImprimirComanda.setToolTipText("Habilita o deshabilita la emisión automática de la comanda de cocina al enviar el pedido");
        String confComanda = new Modelo.ConfiguracionSistemaDao().obtener(Modelo.ConfigClaves.IMPRIMIR_COMANDA_COCINA, "1");
        chkImprimirComanda.setSelected(!"0".equals(confComanda) && !"false".equalsIgnoreCase(confComanda));
        chkImprimirComanda.addActionListener(e -> {
            if (sincronizandoComandaProgramaticamente) return;
            if (politicaAcceso == null || !politicaAcceso.esAdministrador()) {
                sincronizandoComandaProgramaticamente = true;
                try {
                    chkImprimirComanda.setSelected(!chkImprimirComanda.isSelected());
                } finally {
                    sincronizandoComandaProgramaticamente = false;
                }
                JOptionPane.showMessageDialog(this,
                        "Solo un Administrador puede cambiar la emisión automática de comandas.",
                        "Acceso restringido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String val = chkImprimirComanda.isSelected() ? "1" : "0";
            new Thread(() -> {
                try {
                    new Modelo.ConfiguracionSistemaDao().guardar(Modelo.ConfigClaves.IMPRIMIR_COMANDA_COCINA, val);
                } catch (Exception ignored) {}
            }).start();
        });
        pnlMesonero.add(chkImprimirComanda);

        cbMesonero.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED && e.getItem() instanceof Mesonero) {
                mesoneroQuitadoExplicito = false;
            }
        });
        pnlFilaTotales.add(pnlMesonero, BorderLayout.WEST);

        JPanel pnlTotales = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlTotales.setOpaque(false);
        lblTotalUsd = new JLabel("USD $ 0.00");
        lblTotalUsd.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalUsd.setForeground(new Color(0, 102, 102));
        pnlTotales.add(lblTotalUsd);

        lblTotalBs = new JLabel("  |  Bs. 0.00");
        lblTotalBs.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalBs.setForeground(new Color(0, 102, 102));
        pnlTotales.add(lblTotalBs);
        pnlFilaTotales.add(pnlTotales, BorderLayout.EAST);

        pnlAcciones.add(pnlFilaTotales);

        // Fila 2: Botones de Acción
        JPanel pnlBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        pnlBotones.setOpaque(false);

        btnLimpiar = new JButton("Limpiar");
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnLimpiar.setPreferredSize(new Dimension(80, 28));
        btnLimpiar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLimpiar.setToolTipText("Vaciar la comanda actual sin guardar");
        btnLimpiar.addActionListener(e -> limpiarComanda());
        pnlBotones.add(btnLimpiar);

        btnEnviarCocina = new JButton("Enviar a Cocina");
        btnEnviarCocina.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Sistema.botonSolido(btnEnviarCocina, new Color(0, 102, 102),
                Sistema.colorTextoContraste(new Color(0, 102, 102)));
        btnEnviarCocina.setPreferredSize(new Dimension(130, 28));
        btnEnviarCocina.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEnviarCocina.setToolTipText("Guardar la comanda y enviarla a cocina");
        btnEnviarCocina.addActionListener(e -> enviarPedidoCocina());
        pnlBotones.add(btnEnviarCocina);

        btnPreFactura = new JButton("Pre-Factura");
        btnPreFactura.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Sistema.botonSolido(btnPreFactura, new Color(39, 174, 96), new Color(0, 0, 0));
        btnPreFactura.setPreferredSize(new Dimension(95, 28));
        btnPreFactura.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPreFactura.setToolTipText("Emitir la cuenta / pre-factura para el cliente sin cerrar la mesa");
        btnPreFactura.addActionListener(e -> emitirPreFacturaDirecta());
        pnlBotones.add(btnPreFactura);

        btnCortesia = new JButton("Cortesía");
        btnCortesia.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Sistema.botonSolido(btnCortesia, new Color(142, 68, 173), new Color(0, 0, 0));
        btnCortesia.setPreferredSize(new Dimension(80, 28));
        btnCortesia.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCortesia.setToolTipText("Registrar como consumo de la casa / cortesía autorizada");
        btnCortesia.addActionListener(e -> aplicarCortesiaDirecto());
        pnlBotones.add(btnCortesia);

        btnFuga = new JButton("No Pagó");
        btnFuga.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Sistema.botonSolido(btnFuga, new Color(192, 57, 43), new Color(0, 0, 0));
        btnFuga.setPreferredSize(new Dimension(80, 28));
        btnFuga.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnFuga.setToolTipText("Liberar la mesa anulando el pedido por retiro sin pago (Fuga / Incobrable)");
        btnFuga.addActionListener(e -> anularPedidoPorFugaDirecto());
        pnlBotones.add(btnFuga);

        btnCobrar = new JButton("Cobrar");
        btnCobrar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Sistema.botonSolido(btnCobrar, new Color(40, 116, 166),
                Sistema.colorTextoContraste(new Color(40, 116, 166)));
        btnCobrar.setPreferredSize(new Dimension(80, 28));
        btnCobrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCobrar.setToolTipText("Cobrar y facturar la comanda de la mesa");
        btnCobrar.addActionListener(e -> cobrarPedidoDirecto());
        pnlBotones.add(btnCobrar);

        pnlAcciones.add(pnlBotones);
        pnlDerecho.add(pnlAcciones, BorderLayout.SOUTH);

        splitPane.setLeftComponent(pnlIzquierdo);
        splitPane.setRightComponent(pnlDerecho);
        add(splitPane, BorderLayout.CENTER);

        cargarMesoneros();
        cargarCatalogoPlatosInicial();
        actualizarPermisosControles();

        RefrescoVistas.suscribir(RefrescoVistas.PLATOS, this::refrescarPlatos);
        RefrescoVistas.suscribir(RefrescoVistas.PEDIDOS, this::refrescarPedidos);
        registrarPersistenciaAnchos();
    }

    /** Persiste los anchos del catálogo y carrito por usuario (claves split_*). */
    private void registrarPersistenciaAnchos() {
        try {
            int uid = (sistema != null) ? sistema.getIdUsuarioActual() : 0;
            if (uid <= 0) {
                return;
            }
            Modelo.PreferenciasTablaDao dao = new Modelo.PreferenciasTablaDao();
            new GestorAnchosTabla(tblCatalogoPlatos, "split_catalogo", uid, dao);
            new GestorAnchosTabla(tblCarrito, "split_carrito", uid, dao);
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(PanelAtencionMesasSplit.class.getName())
                    .log(java.util.logging.Level.FINE, "Sin persistencia de anchos split: " + ex.getMessage());
        }
    }

    public void refrescarPlatos(Object carga) {
        platosPorId.clear();
        filtrarCatalogoPlatos(txtBuscarPlato != null ? txtBuscarPlato.getText().trim() : "");
        if (idPedidoActivo > 0) {
            cargarDetallesPedidoExistente(idPedidoActivo);
        }
    }

    public void refrescarPedidos(Object carga) {
        // La ocupación pudo cambiar en cualquier mesa (incluso desde otro terminal
        // o con el flujo de caja): repintar siempre la grilla, además de la comanda activa.
        refrescarMesasSalaActual();
        if (idPedidoActivo > 0) {
            if (carga instanceof Modelo.ResultadoActualizacionPrecios res) {
                if (res.pedidosAfectados() == null || res.pedidosAfectados().isEmpty() || res.pedidosAfectados().contains(idPedidoActivo)) {
                    cargarDetallesPedidoExistente(idPedidoActivo);
                }
            } else if (carga instanceof List<?> lista) {
                if (lista.contains(idPedidoActivo)) {
                    cargarDetallesPedidoExistente(idPedidoActivo);
                }
            } else {
                // Evento sin detalle (p. ej. cobro/facturación): el pedido activo pudo
                // finalizarse; verificar en BD y liberar la mesa si ya no está pendiente.
                verificarPedidoActivoTrasEvento();
            }
        }
    }

    /**
     * Repinta el mapa de mesas (para el refresco periódico del modo visible).
     */
    public void refrescarMapa() {
        refrescarMesasSalaActual();
    }

    /**
     * Tras un evento de pedidos sin carga, comprueba si la comanda activa sigue
     * pendiente; si se cobró/facturó (o desapareció), deja la mesa como
     * disponible y repinta la grilla en verde en vez de mostrarla ocupada.
     */
    private void verificarPedidoActivoTrasEvento() {
        final int idVerificar = idPedidoActivo;
        new javax.swing.SwingWorker<Pedidos, Void>() {
            @Override
            protected Pedidos doInBackground() {
                try {
                    return pedidosControlador.verPedido(idVerificar);
                } catch (Exception ex) {
                    return null;
                }
            }

            @Override
            protected void done() {
                if (idPedidoActivo != idVerificar) {
                    return; // El operador ya cambió de mesa; respuesta obsoleta.
                }
                Pedidos ped = null;
                try {
                    ped = get();
                } catch (Exception ignored) {
                }
                if (ped != null && "PENDIENTE".equalsIgnoreCase(ped.getEstado())) {
                    cargarDetallesPedidoExistente(idPedidoActivo);
                    return;
                }
                idPedidoActivo = -1;
                idMesoneroOriginal = null;
                nombreMesoneroOriginal = null;
                mesoneroQuitadoExplicito = false;
                modeloCarrito.setRowCount(0);
                lblCabeceraMesa.setText("MESA: " + codigoMesaSeleccionada + " (" + nombreSalaSeleccionada + ") — DISPONIBLE (Nuevo Pedido)");
                lblCabeceraMesa.setForeground(new Color(0, 102, 102));
                btnEnviarCocina.setText("Enviar a Cocina");
                btnCobrar.setEnabled(false);
                if (btnPreFactura != null) btnPreFactura.setEnabled(false);
                if (btnCortesia != null) btnCortesia.setEnabled(false);
                if (btnFuga != null) btnFuga.setEnabled(false);
                if (cbMesonero != null && cbMesonero.getItemCount() > 0) {
                    cbMesonero.setSelectedIndex(0);
                }
                recalcularTotales();
                sistema.mostrarAvisoEnBanda("Mesa " + codigoMesaSeleccionada + " liberada tras el cobro");
                refrescarMesasSalaActual();
            }
        }.execute();
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
            btnTab.setToolTipText("Ver mesas de " + s.getNombre());

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

        final long tokenRevision = tokenRevisionMesas.incrementAndGet();
        final int idSalaPeticion = idSalaSeleccionada;
        final int cantMesasPeticion = cantMesasSalaSeleccionada;

        new javax.swing.SwingWorker<PanelMesasDatos, Void>() {
            @Override
            protected PanelMesasDatos doInBackground() {
                Map<Integer, Integer> estados = new java.util.LinkedHashMap<>();
                for (int m = 1; m <= cantMesasPeticion; m++) {
                    estados.put(m, pedidosControlador.buscarPedidoPendiente(m, idSalaPeticion));
                }
                Map<Integer, String> mesoneros = pedidosControlador.consultarMesonerosMesasPendientes(idSalaPeticion);
                return new PanelMesasDatos(estados, mesoneros);
            }

            @Override
            protected void done() {
                if (tokenRevision != tokenRevisionMesas.get() || idSalaPeticion != idSalaSeleccionada) {
                    return; // Callback obsoleto, descartar
                }
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
            btnMesa.setPreferredSize(new Dimension(120, 110));
            btnMesa.setMinimumSize(new Dimension(100, 95));
            btnMesa.putClientProperty("numMesa", numMesa);
            btnMesa.setFocusable(false);
            btnMesa.setCursor(new Cursor(Cursor.HAND_CURSOR));
            ImageIcon iconoMesa = iconoMesaSplit();
            if (iconoMesa != null) {
                btnMesa.setIcon(iconoMesa);
                btnMesa.setHorizontalTextPosition(SwingConstants.CENTER);
                btnMesa.setVerticalTextPosition(SwingConstants.BOTTOM);
            }

            if (ocupada) {
                btnMesa.setBackground(ServicioMesa.FONDO_MESA_OCUPADA);
                btnMesa.setForeground(Color.WHITE);
                btnMesa.setText("<html><center><font size='+2'><b>" + codigoMesa + "</b></font><br>"
                        + "<span style='font-size:12px;'><b>" + (salaSeleccionadaEsBarra ? "Puesto " : "Mesa ") + numMesa + "</b></span>"
                        + (visualMesonero != null ? "<br><span style='font-size:12px;'><b>" + visualMesonero + "</b></span>" : "")
                        + "</center></html>");
                btnMesa.setToolTipText("Ocupada (" + codigoMesa + ") - Pedido #" + idPed + (nombreMesonero != null ? " (" + nombreMesonero + ")" : ""));
            } else {
                btnMesa.setBackground(ServicioMesa.FONDO_MESA_LIBRE);
                btnMesa.setForeground(Color.WHITE);
                btnMesa.setText("<html><center><font size='+2'><b>" + codigoMesa + "</b></font><br>"
                        + "<span style='font-size:12px;'><b>" + (salaSeleccionadaEsBarra ? "Puesto " : "Mesa ") + numMesa + "</b></span><br>"
                        + "<span style='font-size:12px;'><b>LIBRE</b></span></center></html>");
                btnMesa.setToolTipText("Disponible (" + codigoMesa + ")");
            }

            // Resaltar si esta mesa es la actualmente abierta en el panel derecho
            if (numMesa == numMesaSeleccionada) {
                btnMesa.setBorder(BorderFactory.createLineBorder(ServicioMesa.BORDE_MESA_SELECCIONADA, 4));
            } else {
                btnMesa.setBorder(BorderFactory.createLineBorder(btnMesa.getBackground().darker(), 2));
            }

            btnMesa.addActionListener(e -> seleccionarMesaParaComanda(numMesa, codigoMesa, idPed));
            panelGrillaMesas.add(btnMesa);
        }

        ajustarColumnasGrillaMesas();
        panelGrillaMesas.revalidate();
        panelGrillaMesas.repaint();
        lblEstadoSalas.setText(nombreSalaSeleccionada + ": " + cantMesasSalaSeleccionada + (salaSeleccionadaEsBarra ? " puestos" : " mesas"));
    }

    /**
     * Rejilla responsiva: calcula columnas según el ancho visible para que cada
     * tarjeta conserve su tamaño y las mesas fluyan hacia abajo con scroll vertical.
     */
    private void ajustarColumnasGrillaMesas() {
        if (panelGrillaMesas == null || scrollMesas == null || scrollMesas.getViewport() == null) {
            return;
        }
        int ancho = scrollMesas.getViewport().getExtentSize().width;
        if (ancho <= 0) {
            ancho = scrollMesas.getWidth();
        }
        if (ancho <= 0) {
            return;
        }
        int n = panelGrillaMesas.getComponentCount();
        if (n == 0) {
            return;
        }
        int cols = Math.max(1, (ancho - 16) / ANCHO_TARJETA_MESA_OBJETIVO);
        cols = Math.min(cols, n);
        if (panelGrillaMesas.getLayout() instanceof java.awt.GridLayout rejilla && rejilla.getColumns() != cols) {
            rejilla.setColumns(cols);
            panelGrillaMesas.revalidate();
        }
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
                    b.setBorder(BorderFactory.createLineBorder(ServicioMesa.BORDE_MESA_SELECCIONADA, 4));
                } else {
                    b.setBorder(BorderFactory.createLineBorder(b.getBackground().darker(), 2));
                }
            }
        }

        if (idPedidoExistente > 0) {
            if (politicaAcceso != null && !politicaAcceso.permite(PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES)) {
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
            if (btnPreFactura != null) btnPreFactura.setEnabled(true);
            if (btnCortesia != null) btnCortesia.setEnabled(true);
            if (btnFuga != null) btnFuga.setEnabled(true);
            cargarMesoneros();
            cbMesonero.setSelectedIndex(0);
            preseleccionarMesoneroDelPedido(idPedidoExistente);
            cargarDetallesPedidoExistente(idPedidoExistente);
            actualizarPermisosControles();
        } else {
            // Mesa disponible: Comanda en blanco lista para agregar platos
            lblCabeceraMesa.setText("MESA: " + codigoMesa + " (" + nombreSalaSeleccionada + ") — DISPONIBLE (Nuevo Pedido)");
            lblCabeceraMesa.setForeground(new Color(0, 102, 102));
            btnEnviarCocina.setText("Enviar a Cocina");
            btnCobrar.setEnabled(false);
            if (btnPreFactura != null) btnPreFactura.setEnabled(false);
            if (btnCortesia != null) btnCortesia.setEnabled(false);
            if (btnFuga != null) btnFuga.setEnabled(false);
            idMesoneroOriginal = null;
            nombreMesoneroOriginal = null;
            mesoneroQuitadoExplicito = false;
            detallesExistentesCarrito.clear();
            modeloCarrito.setRowCount(0);
            recalcularTotales();
            txtBuscarPlato.requestFocusInWindow();
            actualizarPermisosControles();
        }
    }

    private void actualizarPermisosControles() {
        if (politicaAcceso == null) return;
        boolean puedeRegistrar = politicaAcceso.permite(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS);
        boolean puedeEditar = politicaAcceso.permite(PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS);
        boolean puedeCobrar = politicaAcceso.permite(PoliticaAcceso.Accion.COBRAR_PEDIDOS);
        boolean puedeConsultar = politicaAcceso.permite(PoliticaAcceso.Accion.CONSULTAR_PEDIDOS_PENDIENTES);
        boolean puedeMutar = idPedidoActivo > 0 ? puedeEditar : puedeRegistrar;
        if (tblCatalogoPlatos != null) tblCatalogoPlatos.setEnabled(puedeMutar);
        if (tblCarrito != null) tblCarrito.setEnabled(puedeMutar);
        if (txtBuscarPlato != null) txtBuscarPlato.setEnabled(puedeMutar);
        if (txtCantManual != null) txtCantManual.setEnabled(puedeMutar);
        if (btnMenosCant != null) btnMenosCant.setEnabled(puedeMutar);
        if (btnMasCant != null) btnMasCant.setEnabled(puedeMutar);
        if (cbMesonero != null) cbMesonero.setEnabled(puedeMutar);
        if (chkImprimirComanda != null) chkImprimirComanda.setEnabled(puedeMutar && politicaAcceso.esAdministrador());
        if (btnLimpiar != null) btnLimpiar.setEnabled(puedeMutar);
        if (btnEnviarCocina != null) btnEnviarCocina.setEnabled(puedeMutar);
        if (btnPreFactura != null) btnPreFactura.setEnabled(idPedidoActivo > 0 && puedeConsultar);
        if (btnCobrar != null) btnCobrar.setEnabled(idPedidoActivo > 0 && puedeCobrar);
        if (btnCortesia != null) btnCortesia.setEnabled(idPedidoActivo > 0
                && politicaAcceso.permite(PoliticaAcceso.Accion.APLICAR_CORTESIA));
        if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0
                && politicaAcceso.permite(PoliticaAcceso.Accion.CANCELAR_CUENTA_PENDIENTE));
    }

    /** Acción explícita para dejar el pedido sin mesonero (con confirmación). */
    private void quitarMesoneroAsignado() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Quitar al mesonero asignado? Al guardar, el pedido quedará sin mesonero.",
                "Quitar mesonero", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        idMesoneroOriginal = null;
        nombreMesoneroOriginal = null;
        cbMesonero.setSelectedIndex(0);
        mesoneroQuitadoExplicito = true;
        sistema.mostrarAvisoEnBanda("Asignación de mesonero retirada; se guardará sin mesonero.");
    }

    /** Al abrir una mesa ocupada, deja seleccionado el mesonero del pedido para no sobrescribirlo.
     * El combo solo lista mesoneros 100% activos; si el asignado ya no lo está, se avisa y
     * se conserva la asignación original salvo que se elija explícitamente otro activo. */
    private void preseleccionarMesoneroDelPedido(int idPedido) {
        idMesoneroOriginal = null;
        nombreMesoneroOriginal = null;
        mesoneroQuitadoExplicito = false;
        new javax.swing.SwingWorker<Pedidos, Void>() {
            @Override
            protected Pedidos doInBackground() {
                return pedidosControlador.verPedido(idPedido);
            }

            @Override
            protected void done() {
                try {
                    if (idPedidoActivo != idPedido) {
                        return; // El operador ya cambió de mesa; respuesta obsoleta.
                    }
                    Pedidos ped = get();
                    if (ped == null || ped.getIdMesonero() == null || ped.getIdMesonero() <= 0) {
                        cbMesonero.setSelectedIndex(0);
                        return;
                    }
                    for (int i = 0; i < cbMesonero.getItemCount(); i++) {
                        Object item = cbMesonero.getItemAt(i);
                        if (item instanceof Mesonero m && m.getId() == ped.getIdMesonero()) {
                            idMesoneroOriginal = ped.getIdMesonero();
                            nombreMesoneroOriginal = ped.getMesoneroNombre();
                            cbMesonero.setSelectedIndex(i);
                            return;
                        }
                    }
                    idMesoneroOriginal = ped.getIdMesonero();
                    nombreMesoneroOriginal = ped.getMesoneroNombre();
                    cbMesonero.setSelectedIndex(0);
                    sistema.mostrarAvisoEnBanda("El mesonero asignado ('"
                            + (nombreMesoneroOriginal != null ? nombreMesoneroOriginal : ("#" + idMesoneroOriginal))
                            + "') ya no está activo; seleccione uno activo, use Quitar para liberarlo o se conservará la asignación.");
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(PanelAtencionMesasSplit.class.getName())
                            .log(java.util.logging.Level.WARNING, "No se pudo preseleccionar el mesonero del pedido #" + idPedido + ": " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void cargarDetallesPedidoExistente(int idPedido) {
        final long tokenRevision = tokenRevisionPedido.incrementAndGet();
        modeloCarrito.setRowCount(0);
        new javax.swing.SwingWorker<List<DetallePedido>, Void>() {
            @Override
            protected List<DetallePedido> doInBackground() {
                return pedidosControlador.verPedidoDetalle(idPedido);
            }

            @Override
            protected void done() {
                if (tokenRevision != tokenRevisionPedido.get() || idPedido != idPedidoActivo) {
                    return; // Callback obsoleto, descartar
                }
                try {
                    List<DetallePedido> detalles = get();
                    detallesExistentesCarrito.clear();
                    for (int fila = 0; fila < detalles.size(); fila++) {
                        DetallePedido d = detalles.get(fila);
                        detallesExistentesCarrito.put(fila, d);
                        int idPlato = (d.getIdPlato() != null && d.getIdPlato() > 0) ? d.getIdPlato() : (d.getId() > 0 ? -d.getId() : 0);
                        if (!platosPorId.containsKey(idPlato)) {
                            Platos platoDummy = new Platos();
                            platoDummy.setId(idPlato);
                            platoDummy.setNombre(d.getNombre());
                            platoDummy.setMonedaBase(d.getMonedaBase() != null && !d.getMonedaBase().isBlank() ? d.getMonedaBase() : "VES");
                            platoDummy.setAplicaIva(d.getAplicaIva() != null ? d.getAplicaIva() : false);
                            platoDummy.setPrecioIncluyeIva(Boolean.TRUE.equals(d.getPrecioIncluyeIva()) && platoDummy.isAplicaIva());
                            platosPorId.put(idPlato, platoDummy);
                        }
                        BigDecimal precio = d.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal sub = precio.multiply(BigDecimal.valueOf(d.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal tasa = sistema.getTasaActual();
                        String mBase = d.getMonedaBase() != null && !d.getMonedaBase().isBlank() ? d.getMonedaBase() : "VES";
                        modeloCarrito.addRow(new Object[]{
                                idPlato,
                                d.getNombre(),
                                d.getCantidad(),
                                Sistema.formatearMontoCelda(precio, mBase, tasa),
                                Sistema.formatearMontoCelda(sub, mBase, tasa),
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
                List<Platos> platos = platosControlador.listarPlatosParaVenta(filtro, hoy);
                return platos != null ? platos : java.util.Collections.emptyList();
            }

            @Override
            protected void done() {
                try {
                    List<Platos> platos = get();
                    int filaSel = tblCatalogoPlatos.getSelectedRow();
                    Integer idSel = (filaSel >= 0 && filaSel < tblCatalogoPlatos.getRowCount())
                            ? (Integer) Tables.valorModelo(tblCatalogoPlatos, filaSel, 0) : null;
                    platosPorId.clear();
                    modeloCatalogoPlatos.setRowCount(0);
                    int filaRestaurar = -1;
                    if (platos != null) {
                        BigDecimal tasa = sistema.getTasaActual();
                        for (Platos p : platos) {
                            platosPorId.put(p.getId(), p);
                            String moneda = p.getMonedaBase() != null ? p.getMonedaBase().toUpperCase() : "USD";
                            BigDecimal precioPrimario = "VES".equals(moneda) && p.getPrecioVes() != null
                                    ? p.getPrecioVes()
                                    : (p.getPrecioUsd() != null ? p.getPrecioUsd() : p.getPrecioDecimal());
                            if (precioPrimario == null) precioPrimario = BigDecimal.ZERO;
                            precioPrimario = precioPrimario.setScale(2, RoundingMode.HALF_UP);

                            String displayPrecio;
                            if ("VES".equals(moneda)) {
                                BigDecimal refUsd = (tasa != null && tasa.signum() > 0)
                                        ? precioPrimario.divide(tasa, 2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                displayPrecio = "Bs. " + String.format(java.util.Locale.US, "%.2f", precioPrimario)
                                        + " ($ " + String.format(java.util.Locale.US, "%.2f", refUsd) + ")";
                            } else {
                                BigDecimal refVes = (tasa != null && tasa.signum() > 0)
                                        ? precioPrimario.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                displayPrecio = "$ " + String.format(java.util.Locale.US, "%.2f", precioPrimario)
                                        + " (Bs. " + String.format(java.util.Locale.US, "%.2f", refVes) + ")";
                            }
                            if (!p.isPrecioClasificado()) {
                                displayPrecio = "[Sin clasificar] " + displayPrecio;
                            }

                            modeloCatalogoPlatos.addRow(new Object[]{
                                    p.getId(),
                                    p.getNombre(),
                                    displayPrecio
                            });
                            if (idSel != null && idSel.equals(p.getId())) {
                                filaRestaurar = modeloCatalogoPlatos.getRowCount() - 1;
                            }
                        }
                    }
                    java.util.List<String> monedasCat = new java.util.ArrayList<>();
                    if (platos != null) {
                        for (Platos p : platos) {
                            monedasCat.add(p.getMonedaBase());
                        }
                    }
                    Tables.fijarCabeceraMoneda(tblCatalogoPlatos, 2, monedasCat);
                    if (filaRestaurar >= 0 && filaRestaurar < tblCatalogoPlatos.getRowCount()) {
                        tblCatalogoPlatos.setRowSelectionInterval(filaRestaurar, filaRestaurar);
                    } else if (tblCatalogoPlatos.getRowCount() > 0) {
                        tblCatalogoPlatos.setRowSelectionInterval(0, 0);
                    }
                    if (desplazarAPlatoIdSplit != null) {
                        int objetivo = desplazarAPlatoIdSplit;
                        desplazarAPlatoIdSplit = null;
                        for (int i = 0; i < modeloCatalogoPlatos.getRowCount(); i++) {
                            Object v = modeloCatalogoPlatos.getValueAt(i, 0);
                            if (v != null && String.valueOf(objetivo).equals(v.toString())) {
                                int filaV = tblCatalogoPlatos.convertRowIndexToView(i);
                                if (filaV >= 0) {
                                    tblCatalogoPlatos.setRowSelectionInterval(filaV, filaV);
                                    tblCatalogoPlatos.scrollRectToVisible(tblCatalogoPlatos.getCellRect(filaV, 0, true));
                                }
                                break;
                            }
                        }
                    }
                    RendererPlato renderer = new RendererPlato(platosPorId);
                    for (int i = 0; i < tblCatalogoPlatos.getColumnCount(); i++) {
                        if (Tables.ID_COLUMNA_MONEDA.equals(tblCatalogoPlatos.getColumnModel().getColumn(i).getIdentifier())) {
                            continue;
                        }
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

        int idPlato = (int) Tables.valorModelo(tblCatalogoPlatos, fila, 0);
        Platos platoObj = platosPorId.get(idPlato);
        if (platoObj != null && !platoObj.isPrecioClasificado()) {
            sistema.mostrarAvisoEnBanda("El producto '" + platoObj.getNombre() + "' no está clasificado con moneda/IVA.");
            JOptionPane.showMessageDialog(this,
                    "El producto '" + platoObj.getNombre() + "' no tiene moneda base ni condición de IVA clasificadas.\n" +
                    "Debe editarse y guardarse en el catálogo de platos antes de poder agregarse a pedidos.",
                    "Producto sin clasificar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Feedback visual verde de confirmacion al agregar
        tblCatalogoPlatos.setSelectionBackground(new Color(144, 238, 144));
        new javax.swing.Timer(300, ev -> tblCatalogoPlatos.setSelectionBackground(javax.swing.UIManager.getColor("Table.selectionBackground"))).start();

        String nombre = (String) Tables.valorModelo(tblCatalogoPlatos, fila, 1);
        String moneda = (platoObj != null && platoObj.getMonedaBase() != null) ? platoObj.getMonedaBase().toUpperCase() : "USD";
        BigDecimal precio;
        if (platoObj != null) {
            precio = "VES".equals(moneda) && platoObj.getPrecioVes() != null
                    ? platoObj.getPrecioVes()
                    : (platoObj.getPrecioUsd() != null ? platoObj.getPrecioUsd() : platoObj.getPrecioDecimal());
        } else {
            precio = BigDecimal.ZERO;
        }
        if (precio == null) precio = BigDecimal.ZERO;
        precio = precio.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tasa = sistema.getTasaActual();
        // Si ya existe en el carrito, sumar 1
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idFila = (int) modeloCarrito.getValueAt(i, 0);
            if (idFila == idPlato) {
                int cantActual = (int) modeloCarrito.getValueAt(i, 2);
                int nuevaCant = cantActual + 1;
                modeloCarrito.setValueAt(nuevaCant, i, 2);
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(nuevaCant)).setScale(2, RoundingMode.HALF_UP);
                modeloCarrito.setValueAt(Sistema.formatearMontoCelda(sub, moneda, tasa), i, 4);
                tblCarrito.setRowSelectionInterval(i, i);
                txtCantManual.setText(String.valueOf(nuevaCant));
                recalcularTotales();
                autoLimpiarFiltroSplitTrasAgregar(idPlato);
                return;
            }
        }

        // Si no existe, agregar nueva fila con cantidad 1
        modeloCarrito.addRow(new Object[]{
                idPlato,
                nombre,
                1,
                Sistema.formatearMontoCelda(precio, moneda, tasa),
                Sistema.formatearMontoCelda(precio, moneda, tasa),
                ""
        });
        int indexNuevo = modeloCarrito.getRowCount() - 1;
        tblCarrito.setRowSelectionInterval(indexNuevo, indexNuevo);
        txtCantManual.setText("1");
        recalcularTotales();
        autoLimpiarFiltroSplitTrasAgregar(idPlato);
    }

    /**
     * Filtro rápido: tras agregar un plato, limpia el filtro y muestra la lista
     * completa dejando seleccionado y visible el plato elegido.
     */
    private void autoLimpiarFiltroSplitTrasAgregar(int idPlato) {
        if (chkFiltroRapidoSplit != null && chkFiltroRapidoSplit.isSelected()
                && txtBuscarPlato != null && !txtBuscarPlato.getText().trim().isEmpty()) {
            desplazarAPlatoIdSplit = idPlato;
            txtBuscarPlato.setText("");
            filtrarCatalogoPlatos("");
        }
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

        BigDecimal precio = Sistema.importeMonetario(modeloCarrito.getValueAt(fila, 3));
        String moneda = resolverMonedaFilaCarrito(fila);
        BigDecimal tasa = sistema.getTasaActual();
        BigDecimal sub = precio.multiply(BigDecimal.valueOf(nuevaCant)).setScale(2, RoundingMode.HALF_UP);
        modeloCarrito.setValueAt(nuevaCant, fila, 2);
        modeloCarrito.setValueAt(Sistema.formatearMontoCelda(sub, moneda, tasa), fila, 4);
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
                BigDecimal precio = Sistema.importeMonetario(modeloCarrito.getValueAt(fila, 3));
                String moneda = resolverMonedaFilaCarrito(fila);
                BigDecimal tasa = sistema.getTasaActual();
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(cant)).setScale(2, RoundingMode.HALF_UP);
                modeloCarrito.setValueAt(cant, fila, 2);
                modeloCarrito.setValueAt(Sistema.formatearMontoCelda(sub, moneda, tasa), fila, 4);
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
            detallesExistentesCarrito.remove(fila);
            Map<Integer, DetallePedido> temp = new HashMap<>();
            for (Map.Entry<Integer, DetallePedido> e : detallesExistentesCarrito.entrySet()) {
                if (e.getKey() > fila) {
                    temp.put(e.getKey() - 1, e.getValue());
                } else if (e.getKey() < fila) {
                    temp.put(e.getKey(), e.getValue());
                }
            }
            detallesExistentesCarrito.clear();
            detallesExistentesCarrito.putAll(temp);
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
                detallesExistentesCarrito.clear();
                recalcularTotales();
            }
        }
    }

    private void recalcularTotales() {
        if (modeloCarrito.getRowCount() == 0) {
            lblTotalUsd.setText("USD $ 0.00");
            lblTotalBs.setText("  |  Bs. 0.00");
            boolean permitir = idPedidoActivo > 0;
            btnCobrar.setEnabled(permitir);
            if (btnPreFactura != null) btnPreFactura.setEnabled(permitir);
            if (btnCortesia != null) btnCortesia.setEnabled(permitir);
            if (btnFuga != null) btnFuga.setEnabled(permitir);
            return;
        }

        BigDecimal tasa = sistema.getTasaActual();
        if (tasa == null || tasa.signum() <= 0) {
            lblTotalUsd.setText("USD $ 0.00");
            lblTotalBs.setText("  |  (Tasa no configurada)");
            btnCobrar.setEnabled(false);
            if (btnPreFactura != null) btnPreFactura.setEnabled(false);
            if (btnCortesia != null) btnCortesia.setEnabled(false);
            if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0);
            return;
        }
        BigDecimal ivaPorcentaje = sistema.getIvaPorcentajeActual();
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
            lblTotalUsd.setText("USD $ 0.00");
            lblTotalBs.setText("  |  (IVA no configurado)");
            btnCobrar.setEnabled(false);
            if (btnPreFactura != null) btnPreFactura.setEnabled(false);
            if (btnCortesia != null) btnCortesia.setEnabled(false);
            if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0);
            return;
        }

        List<DetallePedido> detalles = new ArrayList<>();
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idPlato = (int) modeloCarrito.getValueAt(i, 0);
            int cant = (int) modeloCarrito.getValueAt(i, 2);
            BigDecimal precio = Sistema.importeMonetario(modeloCarrito.getValueAt(i, 3));
            DetallePedido detExistente = detallesExistentesCarrito.get(i);
            String mb;
            boolean gravado;
            boolean incIva;
            BigDecimal ivaLinea;

            if (detExistente != null && detExistente.getMonedaBase() != null && !detExistente.getMonedaBase().isBlank()) {
                mb = detExistente.getMonedaBase();
                gravado = Boolean.TRUE.equals(detExistente.getAplicaIva());
                incIva = Boolean.TRUE.equals(detExistente.getPrecioIncluyeIva());
                ivaLinea = detExistente.getIvaPorcentaje() != null ? detExistente.getIvaPorcentaje() : (gravado ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));
            } else {
                Platos plato = platosPorId.get(idPlato);
                if (plato == null || plato.getMonedaBase() == null || plato.getMonedaBase().isBlank()) {
                    lblTotalUsd.setText("USD $ 0.00");
                    lblTotalBs.setText("  |  (Producto sin clasificar)");
                    btnCobrar.setEnabled(false);
                    if (btnPreFactura != null) btnPreFactura.setEnabled(false);
                    if (btnCortesia != null) btnCortesia.setEnabled(false);
                    if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0);
                    return;
                }
                if (plato.isAplicaIva() && plato.getPrecioIncluyeIva() == null) {
                    lblTotalUsd.setText("USD $ 0.00");
                    lblTotalBs.setText("  |  (IVA inc. no definido)");
                    btnCobrar.setEnabled(false);
                    if (btnPreFactura != null) btnPreFactura.setEnabled(false);
                    if (btnCortesia != null) btnCortesia.setEnabled(false);
                    if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0);
                    return;
                }
                mb = plato.getMonedaBase();
                gravado = plato.isAplicaIva();
                incIva = Boolean.TRUE.equals(plato.getPrecioIncluyeIva());
                ivaLinea = gravado ? ivaPorcentaje : BigDecimal.ZERO.setScale(2);
            }

            DetallePedido det = new DetallePedido();
            det.setPrecioDecimal(precio);
            det.setCantidad(cant);
            det.setMonedaBase(mb);
            det.setAplicaIva(gravado);
            det.setPrecioIncluyeIva(incIva);
            det.setIvaPorcentaje(ivaLinea);
            detalles.add(det);
        }

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasa);
        lblTotalUsd.setText("USD $ " + String.format(java.util.Locale.US, "%.2f", calc.totalUsd()));
        lblTotalBs.setText("  |  Bs. " + String.format(java.util.Locale.US, "%.2f", calc.totalVes()));

        boolean puedeAccion = idPedidoActivo > 0 || modeloCarrito.getRowCount() > 0;
        btnCobrar.setEnabled(puedeAccion);
        if (btnPreFactura != null) btnPreFactura.setEnabled(puedeAccion);
        if (btnCortesia != null) btnCortesia.setEnabled(puedeAccion);
        if (btnFuga != null) btnFuga.setEnabled(idPedidoActivo > 0);
        actualizarCabecerasMonedaCarrito();
    }

    /** Cabeceras Precio/SubTotal según la moneda de las líneas (USD, VES o USD/VES). */
    private void actualizarCabecerasMonedaCarrito() {
        java.util.List<String> monedas = new java.util.ArrayList<>();
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            Object idObj = modeloCarrito.getValueAt(i, 0);
            if (idObj instanceof Number n) {
                Platos p = platosPorId.get(n.intValue());
                if (p != null && p.getMonedaBase() != null) {
                    monedas.add(p.getMonedaBase());
                }
            }
        }
        Tables.fijarCabeceraMoneda(tblCarrito, 3, monedas);
        Tables.fijarCabeceraMoneda(tblCarrito, 4, monedas);
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
        if (tasa == null || tasa.signum() <= 0) {
            sistema.mostrarAvisoEnBanda("Configure una tasa de cambio válida antes de enviar el pedido.");
            return;
        }
        BigDecimal ivaPorcentaje = sistema.getIvaPorcentajeActual();
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
            sistema.mostrarAvisoEnBanda("Configure un porcentaje de IVA válido antes de enviar el pedido.");
            return;
        }

        List<DetallePedido> detalles = new ArrayList<>();

        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idPlato = (int) modeloCarrito.getValueAt(i, 0);
            String nombre = modeloCarrito.getValueAt(i, 1).toString();
            int cant = (int) modeloCarrito.getValueAt(i, 2);
            BigDecimal precio = Sistema.importeMonetario(modeloCarrito.getValueAt(i, 3));
            String comentario = modeloCarrito.getValueAt(i, 5) != null ? modeloCarrito.getValueAt(i, 5).toString() : "";

            Platos plato = platosPorId.get(idPlato);
            if (plato == null || plato.getMonedaBase() == null || plato.getMonedaBase().isBlank()) {
                sistema.mostrarAvisoEnBanda("El producto '" + nombre + "' no tiene moneda base asignada (requiere clasificación).");
                return;
            }
            if (plato.isAplicaIva() && plato.getPrecioIncluyeIva() == null) {
                sistema.mostrarAvisoEnBanda("El producto '" + nombre + "' no tiene clasificada la condición de IVA incluido.");
                return;
            }

            DetallePedido det = new DetallePedido();
            det.setIdPlato(idPlato > 0 ? idPlato : null);
            det.setNombre(nombre);
            det.setCantidad(cant);
            det.setPrecioDecimal(precio);
            det.setComentario(comentario);
            det.setMonedaBase(plato.getMonedaBase());
            det.setAplicaIva(plato.isAplicaIva());
            det.setPrecioIncluyeIva(Boolean.TRUE.equals(plato.getPrecioIncluyeIva()));
            det.setIvaPorcentaje(plato.isAplicaIva() ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));

            detalles.add(det);
        }

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        Pedidos p = new Pedidos();
        p.setId_sala(idSalaSeleccionada);
        p.setNum_mesa(numMesaSeleccionada);
        p.setTipoPedido("MESA");
        p.setSubtotal(calc.subtotalUsd());
        p.setIvaPorcentaje(ivaPorcentaje);
        p.setIvaMonto(calc.ivaUsd());
        p.setTotalDecimal(calc.totalUsd());
        p.setTasaCambio(tasa);
        p.setSubtotalBs(calc.subtotalVes());
        p.setIvaBs(calc.ivaVes());
        p.setTotalBs(calc.totalVes());
        p.setUsuario(sistema.getNombreUsuarioActual());

        if (cbMesonero.getSelectedItem() instanceof Mesonero m) {
            p.setIdMesonero(m.getId());
            p.setMesoneroNombre(m.getNombreCompleto());
        } else if (!mesoneroQuitadoExplicito && idPedidoActivo > 0 && idMesoneroOriginal != null) {
            p.setIdMesonero(idMesoneroOriginal);
            p.setMesoneroNombre(nombreMesoneroOriginal);
        }

        try {
            boolean eraEdicion = idPedidoActivo > 0;
            boolean imprimirComanda = chkImprimirComanda != null && chkImprimirComanda.isSelected();
            Modelo.ResultadoPedidoComanda resultado;
            if (eraEdicion) {
                if (sistema != null) {
                    if (!sistema.autorizar(PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS)) return;
                } else if (politicaAcceso != null
                        && !politicaAcceso.permite(PoliticaAcceso.Accion.EDITAR_PEDIDOS_ABIERTOS)) {
                    JOptionPane.showMessageDialog(this,
                            "Tu rol no permite modificar pedidos existentes.",
                            "Acceso Denegado", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                resultado = pedidosControlador.actualizarPedidoCompletoConMarcha(
                        idPedidoActivo, p, detalles, imprimirComanda);
                sistema.mostrarAvisoEnBanda(resultado.marcha() == null
                        ? "Pedido actualizado; sin salida de cocina nueva para " + codigoMesaSeleccionada
                        : "Pedido actualizado para " + codigoMesaSeleccionada);
            } else {
                resultado = pedidosControlador.registrarPedidoCompletoConMarcha(p, detalles, imprimirComanda);
                int idNuevo = resultado.idPedido();
                this.idPedidoActivo = idNuevo;
                sistema.mostrarAvisoEnBanda(imprimirComanda
                        ? "Pedido enviado a cocina para " + codigoMesaSeleccionada + " [#" + idNuevo + "]"
                        : "Pedido guardado sin emitir comanda para " + codigoMesaSeleccionada + " [#" + idNuevo + "]");
            }
            if (resultado.marcha() != null) {
                String salaTexto = (nombreSalaSeleccionada != null && !nombreSalaSeleccionada.isBlank()) ? nombreSalaSeleccionada : "Sala " + idSalaSeleccionada;
                String mesoneroTexto = (p.getMesoneroNombre() != null && !p.getMesoneroNombre().isBlank()) ? p.getMesoneroNombre() : sistema.getNombreUsuarioActual();
                Servicio.ServicioComandaCocina.imprimirAsync(resultado.marcha(), salaTexto,
                        numMesaSeleccionada, mesoneroTexto);
            }
            refrescarMesasSalaActual();
        } catch (Exception ex) {
            sistema.mostrarAvisoEnBanda("Error al procesar: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Error al procesar el pedido: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cobrarPedidoDirecto() {
        if (modeloCarrito.getRowCount() > 0) {
            // Guardar o actualizar cambios locales primero y luego cobrar
            enviarPedidoCocina();
            if (idPedidoActivo > 0) {
                final int idACobrar = idPedidoActivo;
                sistema.abrirModalCobroPedido(idACobrar, () -> {
                    modeloCarrito.setRowCount(0);
                    detallesExistentesCarrito.clear();
                    idPedidoActivo = 0;
                    recalcularTotales();
                    refrescarMesasSalaActual();
                });
            } else {
                java.util.logging.Logger.getLogger(PanelAtencionMesasSplit.class.getName())
                        .warning("cobrarPedidoDirecto: enviarPedidoCocina no fijó idPedidoActivo válido (" + idPedidoActivo + ")");
            }
        } else if (idPedidoActivo > 0) {
            final int idACobrar = idPedidoActivo;
            sistema.abrirModalCobroPedido(idACobrar, () -> {
                modeloCarrito.setRowCount(0);
                detallesExistentesCarrito.clear();
                idPedidoActivo = 0;
                recalcularTotales();
                refrescarMesasSalaActual();
            });
        } else {
            sistema.mostrarAvisoEnBanda("No hay pedido para cobrar en esta mesa");
        }
    }

    private void emitirPreFacturaDirecta() {
        if (idPedidoActivo <= 0 && modeloCarrito.getRowCount() == 0) {
            sistema.mostrarAvisoEnBanda("No hay pedido activo ni consumos en esta mesa");
            return;
        }

        // Si hay consumos en el carrito pero el pedido aún no se ha guardado, o se han modificado items,
        // guardamos/actualizamos primero para tener el pedido persistido en BD.
        if (modeloCarrito.getRowCount() > 0) {
            enviarPedidoCocina();
        }

        if (idPedidoActivo <= 0) {
            sistema.mostrarAvisoEnBanda("No se pudo emitir la pre-factura: pedido no guardado.");
            return;
        }

        try {
            pedidosControlador.emitirPreFactura(idPedidoActivo);
            sistema.mostrarAvisoEnBanda("Pre-factura emitida exitosamente para pedido #" + idPedidoActivo);
        } catch (Exception ex) {
            sistema.mostrarAvisoEnBanda("Error al emitir pre-factura: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Error al emitir pre-factura: " + ex.getMessage(),
                    "Pre-Factura", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void anularPedidoPorFugaDirecto() {
        if (idPedidoActivo <= 0) {
            sistema.mostrarAvisoEnBanda("No hay pedido activo en esta mesa para anular por fuga");
            return;
        }
        int id = idPedidoActivo;
        String motivo = (String) JOptionPane.showInputDialog(this,
                "Indique el motivo / detalle del no-pago para el pedido #" + id + ":\n"
                        + "La mesa se liberará inmediatamente sin generar faltantes falsos en caja.",
                "Anular por Fuga / No Pagó",
                JOptionPane.WARNING_MESSAGE,
                null,
                null,
                "Cliente se retiró sin pagar");

        if (motivo == null || motivo.trim().isEmpty()) {
            return;
        }

        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Confirma anular el pedido #" + id + " por FUGA / NO-PAGO?\nMotivo: " + motivo.trim(),
                "Confirmar No-Pago (Fuga)",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirmacion != JOptionPane.YES_OPTION) return;

        String usr = (sistema != null) ? sistema.getNombreUsuarioActual() : "Sistema";
        try {
            boolean exito = pedidosControlador.anularPedido(id, motivo.trim(), usr);
            if (exito) {
                sistema.mostrarAvisoEnBanda("Pedido #" + id + " anulado por fuga. Mesa liberada.");
                idPedidoActivo = -1;
                idMesoneroOriginal = null;
                nombreMesoneroOriginal = null;
                mesoneroQuitadoExplicito = false;
                modeloCarrito.setRowCount(0);
                lblCabeceraMesa.setText("MESA: " + codigoMesaSeleccionada + " (" + nombreSalaSeleccionada + ") — DISPONIBLE (Nuevo Pedido)");
                lblCabeceraMesa.setForeground(new Color(0, 102, 102));
                btnEnviarCocina.setText("Enviar a Cocina");
                btnCobrar.setEnabled(false);
                if (btnPreFactura != null) btnPreFactura.setEnabled(false);
                if (btnCortesia != null) btnCortesia.setEnabled(false);
                if (btnFuga != null) btnFuga.setEnabled(false);
                recalcularTotales();
                refrescarMesasSalaActual();
                RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
                RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo anular el pedido #" + id + ".", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al anular pedido por fuga: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicarCortesiaDirecto() {
        if (modeloCarrito.getRowCount() > 0) {
            enviarPedidoCocina();
        }
        if (idPedidoActivo <= 0) {
            sistema.mostrarAvisoEnBanda("No hay pedido activo en esta mesa para aplicar cortesía");
            return;
        }
        sistema.cargarPedidoEnPantallaDesdeSplit(idPedidoActivo);
        sistema.aplicarCortesiaActual();
    }

    /** Icono de mesa compartido con el modo clásico (48px, se carga una vez). */
    private ImageIcon iconoMesaSplit() {
        if (iconoMesaSplit == null) {
            java.net.URL url = getClass().getResource("/Img/mesa.png");
            if (url != null) {
                java.awt.Image img = new ImageIcon(url).getImage()
                        .getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
                iconoMesaSplit = new ImageIcon(img);
            }
        }
        return iconoMesaSplit;
    }
    private String monedaUbFilaCatalogo(int filaModelo) {
        return monedaUbDePlato(modeloCatalogoPlatos, filaModelo);
    }

    private String monedaUbFilaCarrito(int filaModelo) {
        return monedaUbDePlato(modeloCarrito, filaModelo);
    }

    private String resolverMonedaFilaCarrito(int fila) {
        if (fila < 0 || fila >= modeloCarrito.getRowCount()) return "USD";
        Object idObj = modeloCarrito.getValueAt(fila, 0);
        if (idObj instanceof Number n) {
            Platos p = platosPorId.get(n.intValue());
            if (p != null && p.getMonedaBase() != null) {
                return p.getMonedaBase().toUpperCase(java.util.Locale.ROOT);
            }
        }
        Object celda = modeloCarrito.getValueAt(fila, 3);
        if (celda != null) {
            String s = celda.toString().trim().toUpperCase(java.util.Locale.ROOT);
            if (s.startsWith("BS") || s.contains("BS.")) {
                return "VES";
            }
        }
        return "USD";
    }

    private String monedaUbDePlato(javax.swing.table.DefaultTableModel modelo, int filaModelo) {
        if (filaModelo < 0 || filaModelo >= modelo.getRowCount()) {
            return "";
        }
        Object idObj = modelo.getValueAt(filaModelo, 0);
        if (!(idObj instanceof Number n)) {
            return "";
        }
        Platos p = platosPorId.get(n.intValue());
        if (p == null || p.getMonedaBase() == null) {
            return "";
        }
        return "VES".equalsIgnoreCase(p.getMonedaBase().trim()) ? "B" : "U";
    }

    private void cargarMesoneros() {
        Object seleccionado = cbMesonero.getSelectedItem();
        Integer idSeleccionado = (seleccionado instanceof Mesonero m) ? m.getId() : null;
        cbMesonero.removeAllItems();
        cbMesonero.addItem("- Sin mesonero -");
        try {
            MesoneroDao dao = new MesoneroDao();
            List<Mesonero> activos = dao.listarActivos();
            for (Mesonero m : activos) {
                cbMesonero.addItem(m);
            }
            if (idSeleccionado != null) {
                preseleccionarMesoneroPorId(idSeleccionado);
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(PanelAtencionMesasSplit.class.getName())
                    .log(java.util.logging.Level.WARNING, "Error al cargar mesoneros activos: " + ex.getMessage());
        }
    }

    /** Recarga el combo tras altas/bajas en Configuración; conserva la selección si sigue activa. */
    public void recargarMesoneros() {
        cargarMesoneros();
    }

    /** Sincroniza el toggle de filtro rápido con el de la toma clásica. */
    public void setFiltroRapido(boolean sel) {
        if (chkFiltroRapidoSplit != null && chkFiltroRapidoSplit.isSelected() != sel) {
            chkFiltroRapidoSplit.setSelected(sel);
        }
    }

    private void preseleccionarMesoneroPorId(int idMesonero) {
        for (int i = 0; i < cbMesonero.getItemCount(); i++) {
            Object item = cbMesonero.getItemAt(i);
            if (item instanceof Mesonero m && m.getId() == idMesonero) {
                cbMesonero.setSelectedIndex(i);
                return;
            }
        }
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

    public java.util.Collection<Platos> getPlatosCatalogo() {
        return new java.util.ArrayList<>(platosPorId.values());
    }

    public JTable getTablaCarrito() {
        return tblCarrito;
    }

    public void agregarPlatoSeleccionadoACarrito() {
        agregarPlatoSeleccionadoAlCarrito();
    }

    public JButton getBtnLimpiar() {
        return btnLimpiar;
    }

    public JButton getBtnCortesia() {
        return btnCortesia;
    }

    public JButton getBtnFuga() {
        return btnFuga;
    }

    public JButton getBtnEnviarCocina() {
        return btnEnviarCocina;
    }

    public JButton getBtnCobrar() {
        return btnCobrar;
    }

    public int getIdPedidoActivo() {
        return idPedidoActivo;
    }

    public boolean tieneMesaSeleccionada() {
        return numMesaSeleccionada > 0 && idSalaSeleccionada > 0;
    }

    public int getNumMesaSeleccionada() {
        return numMesaSeleccionada;
    }

    public String getCodigoMesaSeleccionada() {
        return codigoMesaSeleccionada != null && !codigoMesaSeleccionada.isBlank()
                ? codigoMesaSeleccionada : ("Mesa " + numMesaSeleccionada);
    }

    public javax.swing.JComponent getComponenteParaAnclarPopup() {
        return txtBuscarPlato != null ? txtBuscarPlato : this;
    }

    public void agregarPlatoDirectoAlCarrito(Platos platoObj) {
        if (numMesaSeleccionada <= 0) {
            sistema.mostrarAvisoEnBanda("Primero selecciona una mesa a la izquierda");
            return;
        }
        if (platoObj == null) return;
        int idPlato = platoObj.getId();

        String nombre = platoObj.getNombre();
        String moneda = (platoObj.getMonedaBase() != null) ? platoObj.getMonedaBase().toUpperCase() : "USD";
        BigDecimal precio = "VES".equals(moneda) && platoObj.getPrecioVes() != null
                ? platoObj.getPrecioVes()
                : (platoObj.getPrecioUsd() != null ? platoObj.getPrecioUsd() : platoObj.getPrecioDecimal());
        if (precio == null) precio = BigDecimal.ZERO;
        precio = precio.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tasa = sistema.getTasaActual();
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            int idFila = (int) modeloCarrito.getValueAt(i, 0);
            if (idFila == idPlato) {
                int cantActual = (int) modeloCarrito.getValueAt(i, 2);
                int nuevaCant = cantActual + 1;
                modeloCarrito.setValueAt(nuevaCant, i, 2);
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(nuevaCant)).setScale(2, RoundingMode.HALF_UP);
                modeloCarrito.setValueAt(Sistema.formatearMontoCelda(sub, moneda, tasa), i, 4);
                tblCarrito.setRowSelectionInterval(i, i);
                txtCantManual.setText(String.valueOf(nuevaCant));
                recalcularTotales();
                autoLimpiarFiltroSplitTrasAgregar(idPlato);
                return;
            }
        }

        modeloCarrito.addRow(new Object[]{
                idPlato,
                nombre,
                1,
                Sistema.formatearMontoCelda(precio, moneda, tasa),
                Sistema.formatearMontoCelda(precio, moneda, tasa),
                ""
        });
        tblCarrito.setRowSelectionInterval(modeloCarrito.getRowCount() - 1, modeloCarrito.getRowCount() - 1);
        txtCantManual.setText("1");
        recalcularTotales();
        autoLimpiarFiltroSplitTrasAgregar(idPlato);
    }
}
