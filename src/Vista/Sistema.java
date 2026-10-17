/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vista;
import java.awt.GridLayout;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Cliente;
import Modelo.EstadisticasDashboard;
import Modelo.CalculoFiscalRecord;
import Modelo.Config;
import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import Modelo.DetallePedido;
import Modelo.LoginDao;
import Modelo.Pedidos;
import Modelo.PedidoPendienteExistenteException;
import Modelo.Platos;
import Modelo.Salas;
import Modelo.Usuario;
import Modelo.Mesonero;
import Modelo.MesoneroDao;
import Modelo.PaletaCategorias;
import Servicio.AutenticacionServicio;
import Servicio.PoliticaAcceso;
import Servicio.ServicioMesa;
import Servicio.ServicioMesa.MesaIdentificada;
import Servicio.ServicioMesa.MesaVisual;
import Servicio.ServicioMesoneroNombre;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public final class Sistema extends javax.swing.JFrame {

    /** Rojo de peligro con contraste suficiente para texto blanco, incluso en hover. */
    private static final java.awt.Color COLOR_PELIGRO = new java.awt.Color(168, 70, 60);
    /** Pizarra: cabeceras y navegacion lateral (unifica la paleta). */
    private static final java.awt.Color COLOR_CABECERA = new java.awt.Color(44, 62, 80);
    /** Tiffany para el fondo de todos los campos de entrada. */
    private static final java.awt.Color COLOR_TIFFANY = new java.awt.Color(129, 216, 208);
    /** Texto oscuro para garantizar contraste sobre el fondo tiffany. */
    private static final java.awt.Color COLOR_TEXTO_INPUT = new java.awt.Color(51, 51, 51);
    private static final int ANCHO_BOTON_ESTANDAR = 100;
    private static final int ALTO_BOTON_ESTANDAR = 34;
    /** Barra lateral izquierda original 200 px reducida un 25 %. */
    private static final int ANCHO_BARRA_LATERAL = 150;
    private static final int ALTO_CABECERA = 90;

    Salas sl = new Salas();
    Config conf = new Config();
    Eventos event = new Eventos();

    Platos pla = new Platos();

    Pedidos ped = new Pedidos();
    private final PedidosControlador pedidosControlador;
    private final SalasControlador salasControlador;
    private final PlatosControlador platosControlador;

    DefaultTableModel modelo = new DefaultTableModel();
    DefaultTableModel tmp = new DefaultTableModel();

    LoginDao lgDao = new LoginDao();
    private final PoliticaAcceso politicaAcceso;
    int item;
    private long versionMenuPlatos;
    private long versionCatalogoPlatos;
    private long versionHistorialPedidos;
    private long versionPanelMesas;
    private long versionPedidoPantalla;
    private long versionDashboard;
    private long versionCargaUsuarios;
    private long versionCargaConfig;
    private long versionCargaCategorias;
    private long versionBusquedaClientes;
    private long versionFacturasCliente;
    BigDecimal Totalpagar = BigDecimal.ZERO.setScale(2);
    private javax.swing.JTextField txtBuscarHistorial;
    private javax.swing.JButton btnFiltroHoy;
    private javax.swing.JButton btnFiltroPendientes;
    private javax.swing.JButton btnFiltroFinalizados;
    private javax.swing.JTextField txtFiltroFechaDesde;
    private javax.swing.JTextField txtFiltroFechaHasta;
    private javax.swing.JButton btnFiltroRangoFecha;
    private javax.swing.JButton btnLimpiarFiltrosHistorial;
    private String filtroEstadoSeleccionado;
    private boolean filtroSoloHoyActivo;
    private javax.swing.JButton btnMasCantidad;
    private javax.swing.JButton btnMenosCantidad;
    private javax.swing.JButton btnReimprimirHistorial;
    private javax.swing.JButton btnPrevisualizarHistorial;
    private javax.swing.JButton btnPrevisualizarPedido;
    private javax.swing.JButton btnCierreParcial;
    private javax.swing.JButton btnCierreTotal;
    private javax.swing.JTextField txtCantidadManual;
    private int platoSeleccionadoFilaFoco = -1;
    private int idPedidoEdicion = -1;
    private int idSalaEdicion = -1;
    private int numMesaEdicion = -1;
    private boolean sincronizandoPlatoForm = false;
    private javax.swing.JCheckBox chkFiltroRapido;
    private Integer desplazarAPlatoIdToma = null;
    private javax.swing.JCheckBox chkFiltroRapidoAdminPlatos;
    private javax.swing.JTextField txtBuscarAdminPlatos;
    private Integer desplazarAPlatoIdAdmin = null;
    private Integer idMesoneroEdicionOriginal = null;
    private String nombreMesoneroEdicionOriginal = null;
    private boolean quitarMesoneroEdicion = false;
    private javax.swing.JButton btnQuitarMesoneroPedido;
    private String ultimoTextoIngresado = "";
    private boolean isUpdatingUI = false;
    private Servicio.CierreCajaServicio cierreCajaServicio;
    private javax.swing.JLabel lblTituloSalaMesas;
    private javax.swing.JLabel lblUltimaCargaMesas;
    private javax.swing.JButton btnActualizarMesas;
    private int idSalaActualMesas;
    private String nombreSalaActualMesas;
    private int cantMesasActual;
    private javax.swing.Timer temporizadorRefrescoModos;
    private javax.swing.JLabel lblLogoPreview;
    private javax.swing.JTextField txtRutaLogo;
    private javax.swing.JButton btnCambiarLogo;
    private javax.swing.JButton btnRestablecerLogo;
    private javax.swing.JCheckBox chkImprimirLogoTicket;
    private javax.swing.JComboBox<String> cbImpresorasConfig;
    private javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbModoSalidaConfig;
    private javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbModoSalidaFinalizar;
    private javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbModoSalidaHistorial;
    private javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbModoSalidaClientes;
    private javax.swing.JButton btnRefrescarImpresoras;
    private javax.swing.JButton btnProbarImpresion;
    private boolean sincronizandoModoSalida = false;
    private boolean sincronizandoImpresora = false;
    private javax.swing.JButton btnReactivarPlato;
    private javax.swing.JSpinner spMesesRetencion;
    private javax.swing.JButton btnPurgarHistorial;
    private javax.swing.JLabel lblRetencionConfig;
    private Usuario usuarioActual;
    private final Modelo.ClienteDao clienteDao = new Modelo.ClienteDao();
    private javax.swing.JPanel panelDashboard;
    private javax.swing.JButton btnDashboard;
    private javax.swing.JButton btnPedidoRapido;
    private boolean modoPedidoRapido;
    private javax.swing.JTextField txtBuscarCliente;
    private javax.swing.JTable tableClientes;
    private javax.swing.JTable tableFacturasCliente;
    private javax.swing.JTable tableTopPlatos;
    private javax.swing.JLabel lblVentasHoy;
    private javax.swing.JLabel lblVentasHistoricas;
    private javax.swing.JLabel lblTicketPromedio;
    private javax.swing.JLabel lblClientesYMesas;
    private javax.swing.JTextField txtClienteDefaultNombre;
    private javax.swing.JTextField txtClienteDefaultDoc;
    private final Servicio.ServicioRespaldoBaseDatos servicioRespaldo = new Servicio.ServicioRespaldoBaseDatos();
    private final Servicio.ConfiguracionServicio configServicio = new Servicio.ConfiguracionServicio();
    private javax.swing.JButton btnAnularPedido;
    private javax.swing.JButton btnAuditoriaPedido;
    private javax.swing.JLabel lblBannerTasa;
    private javax.swing.JLabel lblFechaTasaConfig;
    private final MesoneroDao mesoneroDao = new MesoneroDao();
    private javax.swing.JComboBox<Object> cbMesoneroPedido;
    private javax.swing.JTextField txtMesoneroId;
    private javax.swing.JTextField txtMesoneroNombre;
    private javax.swing.JTextField txtMesoneroCedula;
    private javax.swing.JTextField txtMesoneroTelefono;
    private javax.swing.JCheckBox chkMesoneroActivo;
    private javax.swing.JTable tableMesonerosConfig;
    private javax.swing.JTable tableMesonerosDashboard;
    private javax.swing.JTextField txtMesoneroFinalizar;
    private final Map<Integer, Boolean> platoAplicaIvaMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, String> salaTipoMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Platos> platosPorId = new java.util.concurrent.ConcurrentHashMap<>();
    /** Moneda U/B por fila-modelo de tableFinalizar (su modelo no trae moneda). */
    private final Map<Integer, String> monedaFilaFinalizar = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean salaActualEsBarra = false;
    private javax.swing.JCheckBox chkAplicaIvaPlato;
    private javax.swing.JComboBox<String> cbMonedaBasePlato;
    private javax.swing.JComboBox<Modelo.Categoria> cbCategoriaFicha;
    private javax.swing.JLabel lblPrecioReferenciaPlato;
    private javax.swing.JCheckBox chkPrecioIncluyeIvaPlato;
    private javax.swing.JComboBox<String> cbTipoSala;

    private Controlador.CategoriaControlador categoriaControlador;
    private Controlador.FavoritoControlador favoritoControlador;
    private javax.swing.JPanel panelCategorias;
    private javax.swing.JList<Modelo.Categoria> listaCategorias;
    private javax.swing.DefaultListModel<Modelo.Categoria> modeloListaCategorias;
    private javax.swing.JTextField txtNombreCategoria;
    private javax.swing.JComboBox<String> cbColorCategoria;
    private javax.swing.JButton btnGuardarCategoria;
    private javax.swing.JButton btnEditarCategoria;
    private javax.swing.JButton btnEliminarCategoria;
    private javax.swing.JComboBox<Platos> cbPlatoAsignacion;
    private javax.swing.JComboBox<Modelo.Categoria> cbCategoriaPlato;
    private javax.swing.JCheckBox chkFavoritoPlato;
    private javax.swing.JButton btnAsignarCategoria;

    // --- Modalidad de Atención Directa de Mesas y Prefijos ---
    private List<Salas> salasCache = new ArrayList<>();
    private final Map<Integer, String> salaIdAPrefijo = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> prefijoASalaId = new java.util.concurrent.ConcurrentHashMap<>();

    // Opción B: Barra de pestañas horizontales de sala en panel de mesas
    private javax.swing.JPanel panelTabsSalasDirecto;

    // Opción A: Cabecera interactiva y selector rápido de mesa en toma de pedidos
    private javax.swing.JPanel panelCabeceraMesaDirecta;
    private javax.swing.JButton btnSelectorMesaCabecera;
    private javax.swing.JTextField txtCodigoMesaDirecto;

    // Opción C: Panel Split-Screen
    private PanelAtencionMesasSplit panelSplitScreen;

    // Conmutador de modos de atención
    private static final String[] NOMBRES_MODOS_ATENCION = new String[]{
        "Opción B (Mapa + Pestañas)",
        "Opción A (Selector Cabecera)",
        "Opción C (Pantalla Dividida)",
        "Clásico (Menú Salas)"
    };
    private javax.swing.JComboBox<String> cbModoAtencion;
    private javax.swing.JComboBox<String> cbModoAtencionGlobal;
    private javax.swing.JPanel panelConmutadorModoGlobal;
    private boolean cambiandoModoProgramaticamente = false;
    private javax.swing.JButton btnAtencionDirecta;
    private String modoAtencionActual = "OPCION_B";

    public Sistema(Usuario priv, SalasControlador salasControlador, PlatosControlador platosControlador,
            PedidosControlador pedidosControlador) {
        this(priv, salasControlador, platosControlador, pedidosControlador, null, null, new LoginDao());
    }

    public Sistema(Usuario priv, SalasControlador salasControlador, PlatosControlador platosControlador,
            PedidosControlador pedidosControlador, LoginDao loginDao) {
        this(priv, salasControlador, platosControlador, pedidosControlador, null, null, loginDao);
    }

    public Sistema(Usuario priv, SalasControlador salasControlador, PlatosControlador platosControlador,
            PedidosControlador pedidosControlador, Controlador.CategoriaControlador categoriaControlador,
            Controlador.FavoritoControlador favoritoControlador, LoginDao loginDao) {
        if (salasControlador == null || platosControlador == null || pedidosControlador == null) {
            throw ErrorAplicacionException.validacion("Los controladores de salas, platos y pedidos son obligatorios.");
        }
        this.salasControlador = salasControlador;
        this.platosControlador = platosControlador;
        this.pedidosControlador = pedidosControlador;
        if (loginDao != null) {
            this.lgDao = loginDao;
        }
        this.usuarioActual = priv;
        unificarFuentesBase();
        unificarFondosBase();
        initComponents();
        
        // Intercept close operation to validate open tables
        this.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmarCierreSistema();
            }
        });
        aplicarMejorasDeUX();
        tableMenu.setRowHeight(25);
        tblTemPlatos.setRowHeight(30);
        tblTemPlatos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        politicaAcceso = new PoliticaAcceso(priv, () -> {
            if (priv == null || priv.getId() <= 0) return true;
            try {
                return lgDao.esUsuarioActivo(priv.getId());
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Sistema.class.getName())
                        .warning("No se pudo verificar el estado activo del usuario #" + priv.getId() + " en la BD; acceso denegado por seguridad: " + ex.getMessage());
                return false;
            }
        });
        this.categoriaControlador = categoriaControlador;
        this.favoritoControlador = favoritoControlador;
        this.cierreCajaServicio = (pedidosControlador != null && pedidosControlador.getCierreServicio() != null)
                ? pedidosControlador.getCierreServicio()
                : new Servicio.CierreCajaServicio(
                        new Modelo.CierreCajaDao(),
                        () -> conf != null ? conf : lgDao.datosEmpresa(),
                        new Servicio.GeneradorPdfCierre());
        ImageIcon img = new ImageIcon(getClass().getResource("/Img/logo.png"));
        Image igmEscalada = img.getImage().getScaledInstance(labelLogo.getWidth(), labelLogo.getHeight(), Image.SCALE_SMOOTH);
        Icon icono = new ImageIcon(igmEscalada);
        labelLogo.setIcon(icono);
        this.setIconImage(img.getImage());
        this.setLocationRelativeTo(null);
        txtIdHistorialPedido.setVisible(false);
        txtIdConfig.setVisible(false);
        if (!politicaAcceso.esAdministrador()) {
            btnConfig.setEnabled(false);
            btnUsuarios.setEnabled(false);
            btnPlatos.setEnabled(false);
            btnRegistrarSala.setEnabled(false);
            btnActualizarSala.setEnabled(false);
            btnNuevoSala.setEnabled(false);
            btnEliminarSala.setEnabled(false);
            btnActualizarConfig.setEnabled(false);
            btnIniciar.setEnabled(false);
            btnGuardarPlato.setEnabled(false);
            btnEditarPlato.setEnabled(false);
            btnEliminarPlato.setEnabled(false);
            btnNuevoPlato.setEnabled(false);
            txtNombreSala.setEnabled(false);
            txtMesas.setEnabled(false);
            txtNombre.setEnabled(false);
            txtCorreo.setEnabled(false);
            txtPass.setEnabled(false);
            cbxRol.setEnabled(false);
            txtNombrePlato.setEnabled(false);
            txtPrecioPlato.setEnabled(false);
            LabelVendedor.setText(priv.getNombre());
        } else {
            LabelVendedor.setText(priv.getNombre());
        }
        txtIdConfig.setVisible(false);
        txtIdHistorialPedido.setVisible(false);
        txtIdPedido.setVisible(false);
        txtIdPlato.setVisible(false);
        txtIdSala.setVisible(false);
        txtTempIdSala.setVisible(false);
        txtTempNumMesa.setVisible(false);
        jTabbedPane1.setEnabled(false);

        String nombreUsuario = (priv != null && priv.getNombre() != null && !priv.getNombre().isBlank())
                ? priv.getNombre() : "Usuario";
        setTitle("Panel de Administración - " + nombreUsuario);

        lblBannerTasa = new javax.swing.JLabel();
        lblBannerTasa.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblBannerTasa.setOpaque(true);
        lblBannerTasa.setBackground(new java.awt.Color(245, 247, 250));
        lblBannerTasa.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 205, 215), 1),
                javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        lblBannerTasa.setToolTipText("Haga clic para ir a Configuración y actualizar la tasa de cambio oficial");
        lblBannerTasa.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (politicaAcceso.permite(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) {
                    ListarConfig();
                    jTabbedPane1.setSelectedIndex(6);
                    if (txtTasaConfig != null) {
                        txtTasaConfig.requestFocus();
                        txtTasaConfig.selectAll();
                    }
                }
            }
        });
        getContentPane().add(lblBannerTasa, new org.netbeans.lib.awtextra.AbsoluteConstraints(205, 15, 305, 65));

        TablePedidos.setAutoCreateRowSorter(true);

        if (tableMenu != null) {
            tableMenu.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    if (txtCantidadManual != null && txtCantidadManual.hasFocus()) {
                        txtCantidadManual.transferFocus();
                    }
                    actualizarEstadoBotonesCarrito();
                    javax.swing.SwingUtilities.invokeLater(this::actualizarEstadoBotonesCarrito);
                }
            });
        }
        actualizarEstadoBotonesCarrito();
        cargarComboMesoneros();

        btnEliminarPlato.setText("Desactivar");
        btnEliminarPlato.setToolTipText("Oculta el plato del menú sin eliminarlo");

        btnReactivarPlato = new javax.swing.JButton("Reactivar Platos");
        btnReactivarPlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnReactivarPlato.setToolTipText("Ver y reactivar platos inactivos");
        btnReactivarPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        boolean esAdminPlatos = politicaAcceso.esAdministrador();
        btnReactivarPlato.setVisible(esAdminPlatos);
        btnReactivarPlato.setEnabled(esAdminPlatos);
        btnReactivarPlato.addActionListener(e -> mostrarDialogoReactivarPlatos());
        jPanel11.add(btnReactivarPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 430, 250, 35));

        if (jLabel16 != null) {
            jLabel16.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15));
            jLabel16.setText("HISTORIAL PEDIDOS");
            jLabel16.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            jPanel6.remove(jLabel16);
            jPanel6.add(jLabel16, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 12, 175, 28));
        }

        btnAnularPedido = new javax.swing.JButton("Anular Pedido");
        btnAnularPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnAnularPedido.setForeground(java.awt.Color.WHITE);
        btnAnularPedido.setBackground(COLOR_PELIGRO);
        btnAnularPedido.setContentAreaFilled(true);
        btnAnularPedido.setOpaque(true);
        btnAnularPedido.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btnAnularPedido.setToolTipText("Anular el pedido seleccionado justificando el motivo en auditoría");
        btnAnularPedido.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnAnularPedido.setEnabled(false);
        btnAnularPedido.addActionListener(e -> anularPedidoSeleccionado());
        jPanel6.add(btnAnularPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(225, 10, 120, 32));

        btnAuditoriaPedido = new javax.swing.JButton("Ver Auditoría");
        btnAuditoriaPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnAuditoriaPedido.setToolTipText("Consultar historial de auditoría del pedido (reimpresiones y anulaciones)");
        btnAuditoriaPedido.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnAuditoriaPedido.setEnabled(false);
        btnAuditoriaPedido.addActionListener(e -> mostrarAuditoriaPedidoSeleccionado());
        jPanel6.add(btnAuditoriaPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(355, 10, 120, 32));

        javax.swing.JLabel lblModoHistorial = new javax.swing.JLabel("Salida:");
        lblModoHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel6.add(lblModoHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(485, 12, 45, 28));

        cbModoSalidaHistorial = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbModoSalidaHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        cbModoSalidaHistorial.setToolTipText("Modo de salida del comprobante para reimpresiones");
        cbModoSalidaHistorial.addActionListener(e -> {
            if (!sincronizandoModoSalida && cbModoSalidaHistorial.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
        });
        jPanel6.add(cbModoSalidaHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(535, 10, 205, 32));

        btnCierreParcial = new javax.swing.JButton("Cierre Parcial (X)");
        btnCierreParcial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnCierreParcial.setToolTipText("Generar e imprimir Cierre Parcial de Caja (Corte X) de la jornada");
        btnCierreParcial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        java.net.URL urlPrintHist = getClass().getResource("/Img/print.png");
        if (urlPrintHist != null) {
            btnCierreParcial.setIcon(new javax.swing.ImageIcon(urlPrintHist));
        }
        btnCierreParcial.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.PARCIAL));
        jPanel6.add(btnCierreParcial, new org.netbeans.lib.awtextra.AbsoluteConstraints(750, 10, 175, 32));

        btnCierreTotal = new javax.swing.JButton("Cierre Total (Z)");
        btnCierreTotal.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnCierreTotal.setToolTipText("Generar e imprimir Cierre Total definitivo de Caja (Corte Z) de la jornada");
        btnCierreTotal.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        if (urlPrintHist != null) {
            btnCierreTotal.setIcon(new javax.swing.ImageIcon(urlPrintHist));
        }
        btnCierreTotal.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.TOTAL));
        jPanel6.add(btnCierreTotal, new org.netbeans.lib.awtextra.AbsoluteConstraints(933, 10, 165, 32));

        javax.swing.JLabel lblBuscarHistorial = new javax.swing.JLabel("Buscar:");
        lblBuscarHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        txtBuscarHistorial = new javax.swing.JTextField();
        txtBuscarHistorial.setToolTipText("Búsqueda libre por texto (ID, sala, atendido, etc.)");
        txtBuscarHistorial.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                aplicarFiltroHistorial();
            }
        });
        jPanel6.add(lblBuscarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 48, 48, 30));
        jPanel6.add(txtBuscarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 48, 120, 30));

        btnFiltroHoy = new javax.swing.JButton("Hoy");
        btnFiltroHoy.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroHoy.setToolTipText("Mostrar sólo pedidos realizados hoy");
        btnFiltroHoy.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroHoy.addActionListener(e -> filtrarHistorialHoy());
        jPanel6.add(btnFiltroHoy, new org.netbeans.lib.awtextra.AbsoluteConstraints(215, 48, 65, 30));

        btnFiltroPendientes = new javax.swing.JButton("Pendientes");
        btnFiltroPendientes.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroPendientes.setToolTipText("Mostrar sólo pedidos en estado PENDIENTE");
        btnFiltroPendientes.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroPendientes.addActionListener(e -> filtrarHistorialPendientes());
        jPanel6.add(btnFiltroPendientes, new org.netbeans.lib.awtextra.AbsoluteConstraints(288, 48, 105, 30));

        btnFiltroFinalizados = new javax.swing.JButton("Finalizados");
        btnFiltroFinalizados.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroFinalizados.setToolTipText("Mostrar sólo pedidos en estado FINALIZADO");
        btnFiltroFinalizados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroFinalizados.addActionListener(e -> filtrarHistorialFinalizados());
        jPanel6.add(btnFiltroFinalizados, new org.netbeans.lib.awtextra.AbsoluteConstraints(401, 48, 105, 30));

        javax.swing.JLabel lblDesde = new javax.swing.JLabel("Desde:");
        lblDesde.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        jPanel6.add(lblDesde, new org.netbeans.lib.awtextra.AbsoluteConstraints(514, 48, 40, 30));

        txtFiltroFechaDesde = new javax.swing.JTextField();
        txtFiltroFechaDesde.setToolTipText("Fecha inicial (AAAA-MM-DD)");
        txtFiltroFechaDesde.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(txtFiltroFechaDesde, new org.netbeans.lib.awtextra.AbsoluteConstraints(557, 48, 75, 30));

        javax.swing.JLabel lblHasta = new javax.swing.JLabel("Hasta:");
        lblHasta.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        jPanel6.add(lblHasta, new org.netbeans.lib.awtextra.AbsoluteConstraints(640, 48, 38, 30));

        txtFiltroFechaHasta = new javax.swing.JTextField();
        txtFiltroFechaHasta.setToolTipText("Fecha final (AAAA-MM-DD)");
        txtFiltroFechaHasta.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(txtFiltroFechaHasta, new org.netbeans.lib.awtextra.AbsoluteConstraints(680, 48, 75, 30));

        btnFiltroRangoFecha = new javax.swing.JButton("Rango");
        btnFiltroRangoFecha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroRangoFecha.setToolTipText("Aplicar filtro de rango de fechas");
        btnFiltroRangoFecha.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroRangoFecha.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(btnFiltroRangoFecha, new org.netbeans.lib.awtextra.AbsoluteConstraints(763, 48, 72, 30));

        btnLimpiarFiltrosHistorial = new javax.swing.JButton("Limpiar");
        btnLimpiarFiltrosHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnLimpiarFiltrosHistorial.setToolTipText("Limpiar todos los filtros y restaurar historial completo");
        btnLimpiarFiltrosHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnLimpiarFiltrosHistorial.addActionListener(e -> limpiarFiltrosHistorial());
        jPanel6.add(btnLimpiarFiltrosHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(843, 48, 78, 30));

        btnPdfPedido.setToolTipText("Ver / Reimprimir factura del pedido seleccionado");

        btnReimprimirHistorial = new javax.swing.JButton("Reimprimir");
        btnReimprimirHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnReimprimirHistorial.setToolTipText("Ver / Reimprimir factura del pedido seleccionado");
        btnReimprimirHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnReimprimirHistorial.setEnabled(false);
        java.net.URL urlPdfHist = getClass().getResource("/Img/pdf.png");
        if (urlPdfHist != null) {
            btnReimprimirHistorial.setIcon(new javax.swing.ImageIcon(urlPdfHist));
        }
        btnReimprimirHistorial.addActionListener(e -> {
            if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
            if (txtIdHistorialPedido.getText().isBlank()) {
                mostrarAvisoEnBanda("Seleccione un pedido finalizado de la tabla para reimprimir");
                return;
            }
            try {
                int id = Integer.parseInt(txtIdHistorialPedido.getText());
                String motivo = solicitarMotivoAccion("Reimpresión de Factura",
                        "Ingrese el motivo de la reimpresión del pedido #" + id + ":", "Copia para el cliente");
                if (motivo == null) return;
                String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
                boolean impreso = pedidosControlador.reimprimirPdfPedido(id, motivo, usr);
                if (!impreso) {
                    mostrarAvisoEnBanda("PDF generado (impresión degradada a visor PDF)");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        jPanel6.add(btnReimprimirHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(929, 48, 120, 30));

        btnPrevisualizarHistorial = new javax.swing.JButton("Previsualizar");
        btnPrevisualizarHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnPrevisualizarHistorial.setToolTipText("Pre-visualizar ticket en pantalla (opcional - puede imprimir desde el visor)");
        btnPrevisualizarHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnPrevisualizarHistorial.setEnabled(false);
        java.net.URL urlLupa = getClass().getResource("/Img/lupa.png");
        if (urlLupa != null) {
            btnPrevisualizarHistorial.setIcon(new javax.swing.ImageIcon(urlLupa));
        }
        btnPrevisualizarHistorial.addActionListener(e -> {
            if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
            if (txtIdHistorialPedido.getText().isBlank()) {
                mostrarAvisoEnBanda("Seleccione un pedido de la tabla para previsualizar");
                return;
            }
            try {
                int id = Integer.parseInt(txtIdHistorialPedido.getText());
                pedidosControlador.previsualizarPdfPedido(id);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo previsualizar el PDF: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        jPanel6.add(btnPrevisualizarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(1057, 48, 130, 30));

        btnPrevisualizarPedido = new javax.swing.JButton("Previsualizar");
        btnPrevisualizarPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnPrevisualizarPedido.setToolTipText("Pre-visualizar ticket en pantalla (opcional - puede imprimir desde el visor)");
        btnPrevisualizarPedido.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnPrevisualizarPedido.setEnabled(false);
        if (urlLupa != null) {
            btnPrevisualizarPedido.setIcon(new javax.swing.ImageIcon(urlLupa));
        }
        btnPrevisualizarPedido.addActionListener(this::btnPrevisualizarPedidoActionPerformed);
        jPanel25.add(btnPrevisualizarPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(821, 440, 145, 40));

        javax.swing.JLabel lblDestinoFinalizar = new javax.swing.JLabel("Salida:");
        lblDestinoFinalizar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblDestinoFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(420, 420, 60, 20));

        cbModoSalidaFinalizar = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbModoSalidaFinalizar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        cbModoSalidaFinalizar.setToolTipText("Modo de salida del comprobante (Tickera térmica, PDF o PDF24 Creator)");
        cbModoSalidaFinalizar.addActionListener(e -> {
            if (!sincronizandoModoSalida && cbModoSalidaFinalizar.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
        });
        jPanel25.add(cbModoSalidaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(420, 442, 205, 36));

        TablePedidos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = TablePedidos.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = TablePedidos.convertRowIndexToModel(viewRow);
                    Object idObj = TablePedidos.getModel().getValueAt(modelRow, 0);
                    txtIdHistorialPedido.setText(idObj != null ? idObj.toString() : "");
                    Object estadoObj = TablePedidos.getModel().getValueAt(modelRow, 6);
                    boolean finalizado = "FINALIZADO".equals(estadoObj);
                    boolean esAnulado = "ANULADO".equals(estadoObj);
                    btnPdfPedido.setEnabled(finalizado);
                    if (btnReimprimirHistorial != null) {
                        btnReimprimirHistorial.setEnabled(finalizado);
                    }
                    if (btnPrevisualizarHistorial != null) {
                        btnPrevisualizarHistorial.setEnabled(finalizado);
                    }
                    if (btnPrevisualizarPedido != null) {
                        btnPrevisualizarPedido.setEnabled(finalizado);
                    }
                    if (btnAnularPedido != null) {
                        btnAnularPedido.setEnabled(!esAnulado);
                    }
                    if (btnAuditoriaPedido != null) {
                        btnAuditoriaPedido.setEnabled(true);
                    }
                } else {
                    btnPdfPedido.setEnabled(false);
                    if (btnReimprimirHistorial != null) {
                        btnReimprimirHistorial.setEnabled(false);
                    }
                    if (btnPrevisualizarHistorial != null) {
                        btnPrevisualizarHistorial.setEnabled(false);
                    }
                    if (btnAnularPedido != null) {
                        btnAnularPedido.setEnabled(false);
                    }
                    if (btnAuditoriaPedido != null) {
                        btnAuditoriaPedido.setEnabled(false);
                    }
                }
            }
        });

        jTabbedPane1.addChangeListener(e -> {
            if (jTabbedPane1.getSelectedIndex() == 5) {
                if (politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                    ListarPedidos();
                }
            }
        });

        // Configuración de RIF venezolano
        jLabel27.setText("RIF");

        // Panel de logo/icono en pestaña 6 (Datos de la Empresa)
        if (jLabel21 != null) {
            jLabel21.setVisible(false);
        }
        javax.swing.JPanel panelLogoConfig = new javax.swing.JPanel();
        panelLogoConfig.setBackground(new java.awt.Color(245, 245, 245));
        panelLogoConfig.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        lblLogoPreview = new javax.swing.JLabel();
        lblLogoPreview.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblLogoPreview.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180), 1));
        panelLogoConfig.add(lblLogoPreview, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 20, 220, 150));

        javax.swing.JLabel lblInfoLogo = new javax.swing.JLabel(
                "<html><center>Personaliza el logo que aparecerá en los tickets PDF, facturas y barra del sistema.</center></html>");
        lblInfoLogo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        panelLogoConfig.add(lblInfoLogo, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 185, 520, 30));

        javax.swing.JLabel lblRutaLogo = new javax.swing.JLabel("Ruta de la imagen:");
        lblRutaLogo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        panelLogoConfig.add(lblRutaLogo, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 225, 200, 25));

        txtRutaLogo = new javax.swing.JTextField();
        txtRutaLogo.setEditable(false);
        txtRutaLogo.setBackground(new java.awt.Color(255, 255, 255));
        panelLogoConfig.add(txtRutaLogo, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 255, 500, 30));

        btnCambiarLogo = new javax.swing.JButton("Cambiar Logo / Ícono...");
        btnCambiarLogo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnCambiarLogo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnCambiarLogo.addActionListener(e -> seleccionarNuevoLogo());
        panelLogoConfig.add(btnCambiarLogo, new org.netbeans.lib.awtextra.AbsoluteConstraints(70, 310, 210, 40));

        btnRestablecerLogo = new javax.swing.JButton("Restablecer Predeterminado");
        btnRestablecerLogo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnRestablecerLogo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnRestablecerLogo.addActionListener(e -> {
            txtRutaLogo.setText("");
            actualizarLogoEIcono(null);
        });
        panelLogoConfig.add(btnRestablecerLogo, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 310, 210, 40));

        // Pestaña de Impresión y Salida de Tickets
        javax.swing.JPanel panelImpresionConfig = new javax.swing.JPanel(null);
        panelImpresionConfig.setBackground(new java.awt.Color(245, 245, 245));

        javax.swing.JLabel lblImpTit = new javax.swing.JLabel("Impresora de Tickets (Windows 11 / Sistema):");
        lblImpTit.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        lblImpTit.setBounds(30, 20, 350, 22);
        panelImpresionConfig.add(lblImpTit);

        cbImpresorasConfig = new javax.swing.JComboBox<>();
        cbImpresorasConfig.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        cbImpresorasConfig.setBounds(30, 45, 360, 32);
        
        panelImpresionConfig.add(cbImpresorasConfig);

        btnRefrescarImpresoras = new javax.swing.JButton("Actualizar");
        btnRefrescarImpresoras.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnRefrescarImpresoras.setBounds(400, 45, 110, 32);
        btnRefrescarImpresoras.setToolTipText("Escanear impresoras físicas y virtuales instaladas en Windows");
        btnRefrescarImpresoras.addActionListener(e -> {
            String sel = (cbImpresorasConfig.getSelectedItem() != null) ? cbImpresorasConfig.getSelectedItem().toString() : "DEFAULT";
            refrescarImpresorasEnCombo(sel);
            mostrarAvisoEnBanda("Impresoras actualizadas (" + (cbImpresorasConfig.getItemCount() - 1) + " detectadas)");
        });
        panelImpresionConfig.add(btnRefrescarImpresoras);

        javax.swing.JLabel lblModoTit = new javax.swing.JLabel("Modo de Salida Predeterminado:");
        lblModoTit.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        lblModoTit.setBounds(30, 95, 350, 22);
        panelImpresionConfig.add(lblModoTit);

        cbModoSalidaConfig = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbModoSalidaConfig.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        cbModoSalidaConfig.setBounds(30, 120, 360, 32);
        
        panelImpresionConfig.add(cbModoSalidaConfig);

        chkImprimirLogoTicket = new javax.swing.JCheckBox("Imprimir Logo en Tickera de 80 mm");
        chkImprimirLogoTicket.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        chkImprimirLogoTicket.setSelected(true);
        chkImprimirLogoTicket.setOpaque(false);
        chkImprimirLogoTicket.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        chkImprimirLogoTicket.setToolTipText("Si se desmarca, los tickets y cierres de caja saldrán solo con texto limpio, ahorrando papel y tiempo");
        chkImprimirLogoTicket.setBounds(30, 168, 440, 28);
        panelImpresionConfig.add(chkImprimirLogoTicket);

        btnProbarImpresion = new javax.swing.JButton("Probar Impresión de Ticket");
        btnProbarImpresion.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnProbarImpresion.setBounds(30, 205, 230, 35);
        btnProbarImpresion.setToolTipText("Enviar un ticket de prueba usando la impresora y modo seleccionados");
        btnProbarImpresion.addActionListener(e -> probarImpresionTicket());
        panelImpresionConfig.add(btnProbarImpresion);

        javax.swing.JTextArea txtInfoModos = new javax.swing.JTextArea();
        txtInfoModos.setEditable(false);
        txtInfoModos.setOpaque(false);
        txtInfoModos.setLineWrap(true);
        txtInfoModos.setWrapStyleWord(true);
        txtInfoModos.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        txtInfoModos.setForeground(new java.awt.Color(70, 70, 70));
        txtInfoModos.setText("• Tickera Térmica (Directa 80mm): Emisión rápida y silenciosa a la impresora física.\n"
                + "• Archivo PDF: Genera el ticket y abre el visor predeterminado para guardar/ver.\n"
                + "• PDF24 Creator: Envía a la impresora virtual PDF24 o abre su asistente para enviar por WhatsApp, correo o guardar con calidad.\n"
                + "Nota: Si 'PDF24' no está instalado en este equipo, el sistema abrirá automáticamente el visor PDF estándar.");
        txtInfoModos.setBounds(30, 255, 480, 120);
        panelImpresionConfig.add(txtInfoModos);

        javax.swing.JTabbedPane tabConfigDerecha = new javax.swing.JTabbedPane();
        tabConfigDerecha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        tabConfigDerecha.addTab("Impresión y Tickets", panelImpresionConfig);
        tabConfigDerecha.addTab("Logo e Ícono", panelLogoConfig);
        tabConfigDerecha.addTab("Base de Datos y Respaldos", crearPanelRespaldoConfig());
        tabConfigDerecha.addTab("Mesoneros", crearPanelMesonerosConfig());

        jPanel7.add(tabConfigDerecha, new org.netbeans.lib.awtextra.AbsoluteConstraints(450, 60, 580, 490));

        lblFechaTasaConfig = new javax.swing.JLabel("");
        lblFechaTasaConfig.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 10));
        lblFechaTasaConfig.setForeground(new java.awt.Color(90, 90, 90));
        jPanel8.add(lblFechaTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 285, 215, 15));

        // Campos para configuración de Cliente Predeterminado en jPanel8
        javax.swing.JLabel lblCliDef = new javax.swing.JLabel("Cliente Predeterminado");
        lblCliDef.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel8.add(lblCliDef, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 295, 200, 20));

        txtClienteDefaultNombre = new javax.swing.JTextField();
        txtClienteDefaultNombre.setBackground(new java.awt.Color(255, 255, 255));
        jPanel8.add(txtClienteDefaultNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 318, 220, 30));

        javax.swing.JLabel lblDocDef = new javax.swing.JLabel("Cédula / RIF Predeterminado");
        lblDocDef.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel8.add(lblDocDef, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 362, 200, 20));

        txtClienteDefaultDoc = new javax.swing.JTextField();
        txtClienteDefaultDoc.setBackground(new java.awt.Color(255, 255, 255));
        jPanel8.add(txtClienteDefaultDoc, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 382, 147, 28));

        // Retención de historial y purga en jPanel8
        lblRetencionConfig = new javax.swing.JLabel("Retención Historial (meses):");
        lblRetencionConfig.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel8.add(lblRetencionConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(215, 362, 195, 20));

        spMesesRetencion = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(24, 1, 120, 1));
        spMesesRetencion.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        spMesesRetencion.setToolTipText("Cantidad de meses que se conservan los pedidos finalizados antes de purgar");
        spMesesRetencion.setEnabled(politicaAcceso.esAdministrador());
        jPanel8.add(spMesesRetencion, new org.netbeans.lib.awtextra.AbsoluteConstraints(215, 382, 120, 28));

        jPanel8.remove(btnActualizarConfig);
        btnActualizarConfig.setBounds(15, 415, 185, 45);
        jPanel8.add(btnActualizarConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(15, 422, 185, 45));

        btnPurgarHistorial = new javax.swing.JButton("Purgar Historial");
        btnPurgarHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnPurgarHistorial.setBackground(COLOR_PELIGRO);
        btnPurgarHistorial.setForeground(java.awt.Color.WHITE);
        btnPurgarHistorial.setToolTipText("Elimina pedidos finalizados anteriores al período de retención");
        btnPurgarHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        boolean esAdminRetencion = politicaAcceso.esAdministrador();
        btnPurgarHistorial.setVisible(esAdminRetencion);
        btnPurgarHistorial.setEnabled(esAdminRetencion);
        btnPurgarHistorial.addActionListener(e -> purgarHistorialPedidos());
        jPanel8.add(btnPurgarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(215, 422, 195, 45));

        initConmutadorModosAtencion();
        initPanelMesasMejorado();
        initCabeceraMesaDirecta();
        initPanelAtencionSplit();
        initDashboardYClientes();
        initIvaPlatosYBarraSalas();
        initGestionUsuarios();

        if (politicaAcceso.esAdministrador() && panelDashboard != null) {
            int idxDashboard = jTabbedPane1.indexOfComponent(panelDashboard);
            if (idxDashboard >= 0) {
                jTabbedPane1.setEnabledAt(idxDashboard, true);
                jTabbedPane1.setSelectedIndex(idxDashboard);
            }
            cargarDashboardYClientes();
        }

        panelSalas();
        observarEdicionConfig();
        ListarConfig();
        cargarPreferenciaFiltroRapido();
        actualizarEstadoTasaUI();
        configurarTabCategorias();

        jTabbedPane1.addChangeListener(e -> {
            int idx = jTabbedPane1.getSelectedIndex();
            if (idx == -1) return;
            refrescarVistaVisible(jTabbedPane1.getTitleAt(idx));
        });
        configurarObservadoresVistas();
        aplicarMedidaEstandarBotones(getContentPane(), new java.util.HashSet<>());
        aplicarEncabezados();
        configurarVentanaResponsiva();
        configurarTodasLasTablas(getContentPane());
        ocultarColumnasId(); // IDs fuera de la vista antes de repartir anchos (siguen en el modelo)
        configurarTablaSalasProporcional();
        configurarTablaUsuariosIgualitaria();
        configurarTablaHistorialProporcional();
        configurarTablaFinalizarProporcional();
        configurarColumnasProporcionales(tableMenu,
                new int[]{120, 50, 110, 100, 100, 60},
                new int[]{220, 50, 110, 100, 160, 60},
                new int[]{-1, 50, -1, -1, -1, 60});
        configurarColumnasProporcionales(tblTemPlatos,
                new int[]{150, 110},
                new int[]{200, 110},
                new int[]{-1, -1});
        configurarColumnasProporcionales(TablePlatos,
                new int[]{150, 110, 70},
                new int[]{220, 120, 80},
                new int[]{-1, -1, 90});
        configurarColumnasProporcionales(tableMesonerosConfig,
                new int[]{150, 100, 100, 90},
                new int[]{200, 110, 110, 100},
                new int[]{-1, 120, 120, 110});
        configurarColumnasProporcionales(tableTopPlatos,
                new int[]{120, 50, 80, 80},
                new int[]{150, 60, 100, 100},
                new int[]{-1, 70, 110, 110});
        configurarColumnasProporcionales(tableMesonerosDashboard,
                new int[]{100, 70, 80, 80},
                new int[]{140, 80, 100, 100},
                new int[]{-1, 80, 100, 100});
        configurarColumnasProporcionales(tableClientes,
                new int[]{100, 140, 100, 70, 90, 90},
                new int[]{110, 180, 110, 80, 100, 100},
                new int[]{120, -1, 120, 80, 110, 110});
        configurarColumnasProporcionales(tableFacturasCliente,
                new int[]{100, 100, 60, 90, 90, 100, 90},
                new int[]{110, 120, 70, 100, 100, 130, 100},
                new int[]{110, 120, 70, 100, 100, -1, 100});
        registrarPersistenciaAnchos();
        // tableMesonerosConfig y tablas del dashboard se configuran en sus constructores
        // (se crean despues de aplicarMejorasDeUX).
        configurarScrollHistorialResponsivo();
        crearCamposFacturacionRapida();
        configurarScrollFinalizarResponsivo();
        aplicarFondoTiffanyInputs(getContentPane());
        centrarTextoBarraLateral();
        estilizarBarraLateral();
        solidificarBotones(getContentPane());
        aplicarTitulosTabsContraste();
        if (jTabbedPane1 != null) {
            jTabbedPane1.addChangeListener(e -> aplicarTitulosTabsContraste());
        }
    }

    /**
     * Títulos de pestaña con color explícito (el L&F GTK ignora los UIManager de
     * TabbedPane y los deja tenues). Usa componentes de pestaña para no alterar el
     * título interno (los indexOfTab siguen funcionando). Seleccionada en verde
     * petróleo, resto en negro suave; se reaplica al cambiar de pestaña.
     */
    private void aplicarTitulosTabsContraste() {
        if (jTabbedPane1 == null) {
            return;
        }
        int sel = jTabbedPane1.getSelectedIndex();
        for (int i = 0; i < jTabbedPane1.getTabCount(); i++) {
            String titulo = jTabbedPane1.getTitleAt(i);
            if (titulo == null) {
                continue;
            }
            java.awt.Component comp = jTabbedPane1.getTabComponentAt(i);
            javax.swing.JLabel lbl;
            if (comp instanceof javax.swing.JLabel l) {
                lbl = l;
            } else {
                lbl = new javax.swing.JLabel();
                lbl.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
                jTabbedPane1.setTabComponentAt(i, lbl);
            }
            lbl.setText(titulo);
            lbl.setForeground(new java.awt.Color(i == sel ? 0x00_66_66 : 0x21_21_21));
        }
    }

    /** Fuente base Segoe UI para los componentes que no traen fuente explicita (tablas, campos, combos). */
    private static void unificarFuentesBase() {
        javax.swing.plaf.FontUIResource base = new javax.swing.plaf.FontUIResource("Segoe UI", java.awt.Font.PLAIN, 12);
        javax.swing.plaf.FontUIResource baseNegrita = new javax.swing.plaf.FontUIResource("Segoe UI", java.awt.Font.BOLD, 12);
        String[] claves = {"Button.font", "Label.font", "Table.font", "TableHeader.font", "TextField.font",
                "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font",
                "ComboBox.font", "List.font", "Spinner.font", "CheckBox.font", "TabbedPane.font"};
        for (String clave : claves) {
            javax.swing.UIManager.put(clave, base);
        }
        javax.swing.UIManager.put("Button.font", baseNegrita);
        javax.swing.UIManager.put("TableHeader.font", baseNegrita);
        // Selección visible en fondo blanco con texto negro (contraste máximo).
        javax.swing.UIManager.put("Table.selectionBackground", new javax.swing.plaf.ColorUIResource(java.awt.Color.WHITE));
        javax.swing.UIManager.put("Table.selectionForeground", new javax.swing.plaf.ColorUIResource(java.awt.Color.BLACK));
        // Pestañas legibles en cualquier tema del SO: texto oscuro sobre fondo claro.
        javax.swing.UIManager.put("TabbedPane.foreground", new javax.swing.plaf.ColorUIResource(new java.awt.Color(33, 33, 33)));
        javax.swing.UIManager.put("TabbedPane.background", new javax.swing.plaf.ColorUIResource(new java.awt.Color(232, 235, 239)));
        javax.swing.UIManager.put("TabbedPane.selected", new javax.swing.plaf.ColorUIResource(java.awt.Color.WHITE));
        javax.swing.UIManager.put("TabbedPane.focus", new javax.swing.plaf.ColorUIResource(new java.awt.Color(0, 102, 102)));
    }

    /** Tabla de salas proporcional: Mesas y Tipo fijas, la columna Nombre absorbe el 100 % del ancho libre. */
    private void configurarTablaSalasProporcional() {
        if (tableSala == null || tableSala.getColumnModel().getColumnCount() < 2) {
            return;
        }
        tableSala.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        tableSala.getColumnModel().getColumn(0).setMinWidth(150);
        tableSala.getColumnModel().getColumn(0).setPreferredWidth(200);
        tableSala.getColumnModel().getColumn(1).setMinWidth(110);
        tableSala.getColumnModel().getColumn(1).setMaxWidth(110);
        if (tableSala.getColumnModel().getColumnCount() > 2) {
            tableSala.getColumnModel().getColumn(2).setMinWidth(110);
            tableSala.getColumnModel().getColumn(2).setMaxWidth(110);
        }
    }

    /** Fondo tiffany por defecto para los componentes de entrada creados despues (incluye dialogos). */
    private static void unificarFondosBase() {
        String[] claves = {"TextField.background", "PasswordField.background", "FormattedTextField.background",
                "TextArea.background", "TextPane.background", "EditorPane.background",
                "ComboBox.background", "Spinner.background", "List.background"};
        for (String clave : claves) {
            javax.swing.UIManager.put(clave, COLOR_TIFFANY);
        }
        String[] clavesTexto = {"TextField.foreground", "PasswordField.foreground", "FormattedTextField.foreground",
                "TextArea.foreground", "TextPane.foreground", "EditorPane.foreground",
                "ComboBox.foreground", "Spinner.foreground", "List.foreground"};
        for (String clave : clavesTexto) {
            javax.swing.UIManager.put(clave, COLOR_TEXTO_INPUT);
        }
    }

    /** Pinta de tiffany los campos de entrada y unifica su texto en un tono oscuro legible. */
    public static void aplicarFondoTiffanyInputs(java.awt.Container contenedor) {
        if (contenedor == null) {
            return;
        }
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.JTextField campo) {
                campo.setBackground(COLOR_TIFFANY);
                normalizarTextoInput(campo);
            } else if (c instanceof javax.swing.JTextArea area) {
                area.setBackground(COLOR_TIFFANY);
                normalizarTextoInput(area);
            } else if (c instanceof javax.swing.JEditorPane panelTexto) {
                panelTexto.setBackground(COLOR_TIFFANY);
                normalizarTextoInput(panelTexto);
            } else if (c instanceof javax.swing.JComboBox<?> combo) {
                combo.setBackground(COLOR_TIFFANY);
                normalizarTextoInput(combo);
                java.awt.Component editor = combo.getEditor() != null ? combo.getEditor().getEditorComponent() : null;
                if (editor instanceof javax.swing.JComponent jc) {
                    jc.setBackground(COLOR_TIFFANY);
                    normalizarTextoInput(jc);
                }
            } else if (c instanceof javax.swing.JSpinner spinner) {
                if (spinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor ed) {
                    ed.getTextField().setBackground(COLOR_TIFFANY);
                    normalizarTextoInput(ed.getTextField());
                }
            }
            if (c instanceof java.awt.Container hijo) {
                aplicarFondoTiffanyInputs(hijo);
            }
        }
    }

    private static void normalizarTextoInput(javax.swing.JComponent comp) {
        comp.setForeground(COLOR_TEXTO_INPUT);
    }

    /** Tabla de usuarios: 4 columnas visibles repartiendose el ancho disponible con proporciones legibles. */
    private void configurarTablaUsuariosIgualitaria() {
        if (TableUsuarios == null || TableUsuarios.getColumnModel().getColumnCount() == 0) {
            return;
        }
        TableUsuarios.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        int cols = TableUsuarios.getColumnModel().getColumnCount();
        if (cols >= 4) {
            TableUsuarios.getColumnModel().getColumn(0).setMaxWidth(Integer.MAX_VALUE);
            TableUsuarios.getColumnModel().getColumn(0).setMinWidth(90);
            TableUsuarios.getColumnModel().getColumn(0).setPreferredWidth(140);

            TableUsuarios.getColumnModel().getColumn(1).setMaxWidth(Integer.MAX_VALUE);
            TableUsuarios.getColumnModel().getColumn(1).setMinWidth(110);
            TableUsuarios.getColumnModel().getColumn(1).setPreferredWidth(160);

            TableUsuarios.getColumnModel().getColumn(2).setMaxWidth(160);
            TableUsuarios.getColumnModel().getColumn(2).setMinWidth(90);
            TableUsuarios.getColumnModel().getColumn(2).setPreferredWidth(110);

            TableUsuarios.getColumnModel().getColumn(3).setMaxWidth(130);
            TableUsuarios.getColumnModel().getColumn(3).setMinWidth(80);
            TableUsuarios.getColumnModel().getColumn(3).setPreferredWidth(100);
        } else {
            for (int i = 0; i < cols; i++) {
                // Orden: maximo primero (setPreferredWidth recorta contra el maximo vigente).
                TableUsuarios.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
                TableUsuarios.getColumnModel().getColumn(i).setMinWidth(70);
                TableUsuarios.getColumnModel().getColumn(i).setPreferredWidth(150);
            }
        }
    }

    /** Tabla de historial: columnas proporcionales que absorben el 100 % del ancho libre. */
    private void configurarTablaHistorialProporcional() {
        if (TablePedidos == null || TablePedidos.getColumnModel().getColumnCount() < 6) {
            return;
        }
        TablePedidos.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        int[] minimos = {110, 120, 80, 130, 100, 100};
        int[] preferidos = {140, 160, 80, 150, 100, 120};
        for (int i = 0; i < 6; i++) {
            TablePedidos.getColumnModel().getColumn(i).setMinWidth(minimos[i]);
            TablePedidos.getColumnModel().getColumn(i).setPreferredWidth(preferidos[i]);
            TablePedidos.getColumnModel().getColumn(i).setMaxWidth(Integer.MAX_VALUE);
        }
        TablePedidos.getColumnModel().getColumn(2).setMaxWidth(80);
        TablePedidos.getColumnModel().getColumn(4).setMaxWidth(100);
    }

    /** El scroll del historial llena el panel con margenes fijos (proporcional al tamano de ventana). */
    private void configurarScrollHistorialResponsivo() {
        if (jPanel6 == null || jScrollPane5 == null) {
            return;
        }
        jPanel6.setLayout(null);
        jPanel6.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarScrollHistorial();
            }
        });
        ajustarScrollHistorial();
    }

    private void ajustarScrollHistorial() {
        int w = jPanel6.getWidth();
        int h = jPanel6.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        jScrollPane5.setBounds(40, 90, Math.max(300, w - 80), Math.max(200, h - 120));
        jScrollPane5.validate();
        jPanel6.setPreferredSize(new java.awt.Dimension(w, h));
        jPanel6.revalidate();
    }

    /** Tabla de caja/facturacion: columnas fijas + Plato y Comentario absorbiendo el 100 % libre. */
    private void configurarTablaFinalizarProporcional() {
        if (tableFinalizar == null || tableFinalizar.getColumnModel().getColumnCount() < 5) {
            return;
        }
        tableFinalizar.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        // Orden maximo-primero: setPreferredWidth recorta contra el maximo vigente.
        // Comentario a la mitad para ceder espacio a Precio/SubTotal (USD/VES), sin topes
        // que bloqueen el redimensionado manual (se persisten por usuario).
        int[] maximos = {Integer.MAX_VALUE, 50, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] minimos = {150, 50, 90, 100, 60};
        int[] preferidos = {220, 50, 130, 140, 80};
        for (int i = 0; i < 5; i++) {
            tableFinalizar.getColumnModel().getColumn(i).setMaxWidth(maximos[i]);
            tableFinalizar.getColumnModel().getColumn(i).setMinWidth(minimos[i]);
            tableFinalizar.getColumnModel().getColumn(i).setPreferredWidth(preferidos[i]);
        }
    }

    /** El scroll de caja llena el ancho del panel con margenes fijos (alto intacto: debajo hay mas seccion). */
    private void configurarScrollFinalizarResponsivo() {
        if (jPanel25 == null || jScrollPane13 == null) {
            return;
        }
        jPanel25.setLayout(null);
        jPanel25.setPreferredSize(new java.awt.Dimension(1194, 620));
        jPanel25.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarScrollFinalizar();
            }
        });
        jTabbedPane1.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarScrollFinalizar();
            }
        });
        ajustarScrollFinalizar();
    }

    private void ajustarScrollFinalizar() {
        int anchoPestana = jTabbedPane1.getWidth();
        int altoPestana = jTabbedPane1.getHeight();
        int w = anchoPestana > 0 ? Math.max(980, anchoPestana - 34) : jPanel25.getWidth();
        int h = altoPestana > 0 ? Math.max(480, altoPestana - 40) : jPanel25.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        java.awt.Dimension preferido = jPanel25.getPreferredSize();
        if (preferido.width != w || preferido.height != h) {
            jPanel25.setPreferredSize(new java.awt.Dimension(w, h));
            jPanel25.revalidate();
        }

        // 1. Tabla de platos del pedido: altura proporcional para dejar espacio a la captura y cobro.
        int hScroll = Math.max(140, Math.min(220, h - 330));
        jScrollPane13.setBounds(30, 10, Math.max(300, w - 60), hScroll);

        // 2. Fila 1 de información básica debajo de la tabla de platos
        int yInfo = 10 + hScroll + 12;
        jLabel7.setBounds(30, yInfo, 95, 26);
        txtFechaHora.setBounds(130, yInfo, 170, 26);
        if (lblMesoneroFin != null) lblMesoneroFin.setBounds(320, yInfo, 75, 26);
        if (txtMesoneroFinalizar != null) txtMesoneroFinalizar.setBounds(400, yInfo, 170, 26);

        int xTotal = Math.max(590, w - 340);
        jLabel17.setBounds(xTotal, yInfo - 6, 330, 20);
        totalFinalizar.setBounds(xTotal, yInfo + 14, 330, 28);

        // 3. Fila 2 de información: Sala, Cliente, Doc
        jLabel8.setBounds(30, yInfo + 32, 95, 26);
        txtSalaFinalizar.setBounds(130, yInfo + 32, 170, 26);
        if (lblClienteFac != null) lblClienteFac.setBounds(320, yInfo + 32, 75, 26);
        if (txtClienteNombreFac != null) txtClienteNombreFac.setBounds(400, yInfo + 32, 170, 26);
        if (lblDocFac != null) lblDocFac.setBounds(585, yInfo + 32, 40, 26);
        if (txtClienteDocFac != null) txtClienteDocFac.setBounds(630, yInfo + 32, 120, 26);

        // 4. Fila 3: Mesa
        jLabel9.setBounds(30, yInfo + 64, 95, 26);
        txtNumMesaFinalizar.setBounds(130, yInfo + 64, 170, 26);

        // 5. Sección de Pagos inline
        int yPagos = yInfo + 96;
        if (lblPagoFac != null) lblPagoFac.setBounds(30, yPagos, 75, 20);

        int wPagos = Math.max(350, Math.min(620, w - 240));
        if (scrollPagosFac != null) scrollPagosFac.setBounds(110, yPagos, wPagos, 72);

        int xBtnPago = 110 + wPagos + 10;
        if (btnAgregarPagoFac != null) btnAgregarPagoFac.setBounds(xBtnPago, yPagos, 95, 32);
        if (btnQuitarPagoFac != null) btnQuitarPagoFac.setBounds(xBtnPago, yPagos + 38, 95, 32);

        int yResumen = yPagos + 76;
        if (lblResumenPagosFac != null) lblResumenPagosFac.setBounds(110, yResumen, Math.max(300, w - 130), 18);
        if (lblAlertaPagosFac != null) lblAlertaPagosFac.setBounds(110, yResumen + 18, Math.max(300, w - 130), 18);

        // 6. Fila inferior de botones de acción
        int yBotones = Math.max(yResumen + 40, Math.min(h - 45, yPagos + 115));
        btnModificarPedido.setBounds(30, yBotones, 180, 36);
        btnPdfPedido.setBounds(Math.max(220, w - 240), yBotones, 110, 36);
        btnFinalizar.setBounds(Math.max(340, w - 120), yBotones, 110, 36);

        jScrollPane13.validate();
    }

    /**
     * Columnas proporcionales genericas: la tabla llena el 100 % del viewport.
     * maximo -1 = sin tope. Orden maximo-primero (setPreferredWidth recorta contra el maximo vigente).
     */
    private static void configurarColumnasProporcionales(javax.swing.JTable tabla, int[] minimos, int[] preferidos, int[] maximos) {
        if (tabla == null || tabla.getColumnModel() == null) {
            return;
        }
        int n = Math.min(tabla.getColumnModel().getColumnCount(),
                Math.min(minimos.length, Math.min(preferidos.length, maximos.length)));
        if (n == 0) {
            return;
        }
        tabla.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        for (int i = 0; i < n; i++) {
            tabla.getColumnModel().getColumn(i).setMaxWidth(maximos[i] < 0 ? Integer.MAX_VALUE : maximos[i]);
            tabla.getColumnModel().getColumn(i).setMinWidth(minimos[i]);
            tabla.getColumnModel().getColumn(i).setPreferredWidth(preferidos[i]);
        }
    }

    /** Persiste los anchos de las 12 tablas por usuario (claves semanticas estables). */
    private void registrarPersistenciaAnchos() {
        int uid = (usuarioActual != null) ? usuarioActual.getId() : 0;
        if (uid <= 0) {
            return;
        }
        final Modelo.PreferenciasTablaDao dao;
        try {
            dao = new Modelo.PreferenciasTablaDao();
        } catch (RuntimeException ex) {
            return;
        }
        registrarAnchos(dao, uid, tableSala, "salas_crud");
        registrarAnchos(dao, uid, tblTemPlatos, "toma_menu");
        registrarAnchos(dao, uid, tableMenu, "toma_carrito");
        registrarAnchos(dao, uid, tableFinalizar, "caja");
        registrarAnchos(dao, uid, TablePedidos, "historial");
        registrarAnchos(dao, uid, TableUsuarios, "usuarios");
        registrarAnchos(dao, uid, TablePlatos, "platos_crud");
        registrarAnchos(dao, uid, tableMesonerosConfig, "mesoneros_config");
        registrarAnchos(dao, uid, tableTopPlatos, "dash_top");
        registrarAnchos(dao, uid, tableMesonerosDashboard, "dash_mesoneros");
        registrarAnchos(dao, uid, tableClientes, "dash_clientes");
        registrarAnchos(dao, uid, tableFacturasCliente, "dash_facturas");
    }

    /**
     * Oculta visualmente las columnas ID internas de todas las grillas (el ID sigue
     * en el modelo para selecciones). Se aplica después de anchos/persistencia para
     * que ninguna restauración de layout vuelva a mostrarlas. tableClientes conserva
     * su primera columna (Cédula/RIF es clave de negocio, no ID interno).
     */
    private void ocultarColumnasId() {
        Tables.ocultarColumnaModelo(tableSala, 0);
        Tables.ocultarColumnaModelo(TablePedidos, 0);
        Tables.ocultarColumnaModelo(TableUsuarios, 0);
        Tables.ocultarColumnaModelo(tblTemPlatos, 0);
        Tables.ocultarColumnaModelo(TablePlatos, 0);
        Tables.ocultarColumnaModelo(tableMenu, 0);
        Tables.ocultarColumnaModelo(tableFinalizar, 0);
        Tables.ocultarColumnaModelo(tableMesonerosConfig, 0);
        Tables.ocultarColumnaModelo(tableFacturasCliente, 0);
        configurarColumnasMoneda();
    }

    /** U/B por fila según el plato (U=USD, B=VES, ""=desconocida). */
    private String monedaUbFila(javax.swing.JTable tabla, int filaModelo) {
        if (tabla == null) {
            return "";
        }
        javax.swing.table.TableModel m = tabla.getModel();
        if (filaModelo < 0 || filaModelo >= m.getRowCount()) {
            return "";
        }
        Object idObj = m.getValueAt(filaModelo, 0);
        if (idObj == null) {
            return "";
        }
        try {
            Platos p = platosPorId.get(Integer.parseInt(idObj.toString().trim()));
            return ub(p == null ? null : p.getMonedaBase());
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static String ub(String moneda) {
        if (moneda == null) {
            return "";
        }
        String n = moneda.trim().toUpperCase(java.util.Locale.ROOT);
        if ("VES".equals(n)) {
            return "B";
        }
        return "USD".equals(n) ? "U" : "";
    }

    /** Columna M (U/B) junto al precio unitario; sin efecto en el modelo. */
    private void configurarColumnasMoneda() {
        Tables.agregarColumnaMoneda(tableMenu, 3, mf -> monedaUbFila(tableMenu, mf));
        Tables.agregarColumnaMoneda(tableFinalizar, 3, mf -> monedaFilaFinalizar.getOrDefault(mf, ""));
        Tables.agregarColumnaMoneda(tblTemPlatos, 2, mf -> monedaUbFila(tblTemPlatos, mf));
        Tables.agregarColumnaMoneda(TablePlatos, 2, mf -> monedaUbFila(TablePlatos, mf));
    }

    private static void registrarAnchos(Modelo.PreferenciasTablaDao dao, int uid,
            javax.swing.JTable tabla, String clave) {
        if (tabla == null) {
            return;
        }
        try {
            new GestorAnchosTabla(tabla, clave, uid, dao);
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(Sistema.class.getName())
                    .log(java.util.logging.Level.FINE, "Sin persistencia para " + clave + ": " + ex.getMessage());
        }
    }

    /**
     * Si el operador edita mientras una carga esta en vuelo, esa carga queda obsoleta:
     * al completarse se descarta para no pisar lo tecleado. Se instala una sola vez.
     */
    private void observarEdicionConfig() {
        javax.swing.event.DocumentListener invalidador = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { versionCargaConfig++; }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { versionCargaConfig++; }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { versionCargaConfig++; }
        };
        for (javax.swing.JTextField campo : new javax.swing.JTextField[]{txtRucConfig, txtNombreConfig,
                txtTelefonoConfig, txtDireccionConfig, txtMensaje, txtTasaConfig, txtIvaConfig,
                txtRutaLogo, txtClienteDefaultNombre, txtClienteDefaultDoc}) {
            if (campo != null) {
                campo.getDocument().addDocumentListener(invalidador);
            }
        }
        if (spMesesRetencion != null) {
            spMesesRetencion.addChangeListener(e -> versionCargaConfig++);
        }
        if (chkImprimirLogoTicket != null) {
            chkImprimirLogoTicket.addActionListener(e -> versionCargaConfig++);
        }
        if (cbImpresorasConfig != null) {
            cbImpresorasConfig.addActionListener(e -> versionCargaConfig++);
        }
        if (cbModoSalidaConfig != null) {
            cbModoSalidaConfig.addActionListener(e -> versionCargaConfig++);
        }
    }

    private javax.swing.Timer temporizadorAviso;
    private javax.swing.JLabel encabezadoAvisoActual;

    /**
     * Mensaje no bloqueante en la banda (cabecera) de la pestana visible.
     * Reemplaza avisos de exito e informacion para no interrumpir al operador;
     * se restaura el titulo original a los pocos segundos. Solo llamar desde el EDT.
     */
    void mostrarAvisoEnBanda(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return;
        }
        if (getRootPane() != null) {
            String origWinTitle = (String) getRootPane().getClientProperty("ux.winTituloOriginal");
            if (origWinTitle == null) {
                origWinTitle = getTitle();
                if (origWinTitle != null) {
                    getRootPane().putClientProperty("ux.winTituloOriginal", origWinTitle);
                }
            }
            if (origWinTitle != null) {
                setTitle("✓ " + mensaje + " — " + origWinTitle);
            }
        }
        if (jTabbedPane1 != null) {
            java.awt.Component vista = jTabbedPane1.getSelectedComponent();
            javax.swing.JLabel tituloAnterior = encabezadoAvisoActual;
            if (tituloAnterior != null && (vista == null || !javax.swing.SwingUtilities.isDescendingFrom(tituloAnterior, vista))) {
                restaurarEncabezadoAviso();
            }
            if (vista instanceof java.awt.Container contenedor) {
                javax.swing.JLabel titulo = buscarTituloEncabezado(contenedor);
                if (titulo != null) {
                    if (titulo.getClientProperty("ux.tituloOriginal") == null) {
                        titulo.putClientProperty("ux.tituloOriginal", titulo.getText());
                    }
                    titulo.setText("✓ " + mensaje);
                    encabezadoAvisoActual = titulo;
                }
            }
        }
        if (temporizadorAviso != null && temporizadorAviso.isRunning()) {
            temporizadorAviso.stop();
        }
        temporizadorAviso = new javax.swing.Timer(4000, e -> {
            if (getRootPane() != null) {
                Object origWin = getRootPane().getClientProperty("ux.winTituloOriginal");
                if (origWin != null) {
                    setTitle(origWin.toString());
                    getRootPane().putClientProperty("ux.winTituloOriginal", null);
                }
            }
            restaurarEncabezadoAviso();
        });
        temporizadorAviso.setRepeats(false);
        temporizadorAviso.start();
    }

    private void restaurarEncabezadoAviso() {
        javax.swing.JLabel titulo = encabezadoAvisoActual;
        encabezadoAvisoActual = null;
        if (titulo == null) return;
        Object original = titulo.getClientProperty("ux.tituloOriginal");
        if (original != null) {
            titulo.setText(original.toString());
            titulo.putClientProperty("ux.tituloOriginal", null);
        }
    }

    /** Muestra una alerta roja junto al campo sin interrumpir el trabajo con un diálogo. */
    private void mostrarAlertaCampo(javax.swing.JComponent campo, String mensaje) {
        if (campo == null || mensaje == null || mensaje.isBlank()) return;
        Object popupAnterior = campo.getClientProperty("ux.alertaPopup");
        if (popupAnterior instanceof javax.swing.Popup popup) popup.hide();
        if (campo.getClientProperty("ux.alertaBordeOriginal") == null) {
            campo.putClientProperty("ux.alertaBordeOriginal", campo.getBorder());
            campo.putClientProperty("ux.alertaTooltipOriginal", campo.getToolTipText());
        }
        campo.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
        campo.setToolTipText(mensaje);

        javax.swing.JLabel aviso = new javax.swing.JLabel("⚠ " + mensaje);
        aviso.setOpaque(true);
        aviso.setForeground(new java.awt.Color(120, 35, 30));
        aviso.setBackground(new java.awt.Color(255, 238, 235));
        aviso.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO),
                javax.swing.BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        aviso.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        aviso.setSize(aviso.getPreferredSize());
        if (campo.isShowing()) {
            java.awt.Point punto = campo.getLocationOnScreen();
            java.awt.Dimension pantalla = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            int x = punto.x + campo.getWidth() + 6;
            if (x + aviso.getWidth() > pantalla.width) x = Math.max(0, punto.x - aviso.getWidth() - 6);
            int y = Math.min(Math.max(0, punto.y), pantalla.height - aviso.getHeight());
            javax.swing.Popup popup = javax.swing.PopupFactory.getSharedInstance().getPopup(campo, aviso, x, y);
            popup.show();
            campo.putClientProperty("ux.alertaPopup", popup);
        }
        if (!Boolean.TRUE.equals(campo.getClientProperty("ux.alertaObservada"))) {
            campo.putClientProperty("ux.alertaObservada", Boolean.TRUE);
            Runnable limpiar = () -> limpiarAlertaCampo(campo);
            if (campo instanceof javax.swing.text.JTextComponent texto) {
                texto.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { limpiar.run(); }
                    @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { limpiar.run(); }
                    @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { limpiar.run(); }
                });
            } else if (campo instanceof javax.swing.JSpinner spinner) {
                spinner.addChangeListener(e -> limpiar.run());
            } else {
                campo.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusGained(java.awt.event.FocusEvent e) { limpiar.run(); }
                });
            }
        }
        campo.requestFocusInWindow();
    }

    private void limpiarAlertaCampo(javax.swing.JComponent campo) {
        Object popupAnterior = campo.getClientProperty("ux.alertaPopup");
        if (popupAnterior instanceof javax.swing.Popup popup) popup.hide();
        campo.putClientProperty("ux.alertaPopup", null);
        Object borde = campo.getClientProperty("ux.alertaBordeOriginal");
        if (borde instanceof javax.swing.border.Border bordeOriginal) campo.setBorder(bordeOriginal);
        campo.putClientProperty("ux.alertaBordeOriginal", null);
        Object tooltip = campo.getClientProperty("ux.alertaTooltipOriginal");
        campo.setToolTipText(tooltip instanceof String texto ? texto : null);
        campo.putClientProperty("ux.alertaTooltipOriginal", null);
    }

    private static javax.swing.JLabel buscarTituloEncabezado(java.awt.Container contenedor) {
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.JLabel lbl && "uxTituloEncabezado".equals(lbl.getName())) {
                return lbl;
            }
            if (c instanceof java.awt.Container hijo) {
                javax.swing.JLabel hallado = buscarTituloEncabezado(hijo);
                if (hallado != null) {
                    return hallado;
                }
            }
        }
        return null;
    }

    /** Captura de cliente y desglose multimoneda integrado en la misma factura. */
    private void crearCamposFacturacionRapida() {
        jPanel25.setLayout(null);
        lblClienteFac = new javax.swing.JLabel("Cliente:");
        lblClienteFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblClienteFac);

        txtClienteNombreFac = new javax.swing.JTextField();
        txtClienteNombreFac.setToolTipText("Nombre del cliente (vacio = consumidor predeterminado)");
        jPanel25.add(txtClienteNombreFac);

        lblDocFac = new javax.swing.JLabel("Doc:");
        lblDocFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblDocFac);

        txtClienteDocFac = new javax.swing.JTextField();
        txtClienteDocFac.setToolTipText("Cedula / RIF (vacio = documento predeterminado)");
        jPanel25.add(txtClienteDocFac);

        lblPagoFac = new javax.swing.JLabel("Pagos:");
        lblPagoFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblPagoFac);

        modeloPagosFac = new javax.swing.table.DefaultTableModel(
                new Object[][]{{"EFECTIVO_BS", "VES", ""}},
                new String[]{"Medio", "Moneda", "Monto"}) {
            @Override public boolean isCellEditable(int row, int column) { return true; }
            @Override public Class<?> getColumnClass(int column) { return String.class; }
            @Override public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 0) {
                    String metodo = String.valueOf(value);
                    if ("EFECTIVO_BS".equals(metodo)) super.setValueAt("VES", row, 1);
                    if ("EFECTIVO_USD".equals(metodo)) super.setValueAt("USD", row, 1);
                }
            }
        };
        tablaPagosFac = new javax.swing.JTable(modeloPagosFac);
        tablaPagosFac.setRowHeight(23);
        tablaPagosFac.setFillsViewportHeight(true);
        tablaPagosFac.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaPagosFac.getColumnModel().getColumn(0).setPreferredWidth(180);
        tablaPagosFac.getColumnModel().getColumn(1).setPreferredWidth(70);
        tablaPagosFac.getColumnModel().getColumn(2).setPreferredWidth(120);
        tablaPagosFac.getColumnModel().getColumn(0).setCellEditor(new javax.swing.DefaultCellEditor(
                new javax.swing.JComboBox<>(new String[]{"EFECTIVO_BS", "EFECTIVO_USD", "TRANSFERENCIA", "TARJETA", "PAGO_MOVIL"})));
        tablaPagosFac.getColumnModel().getColumn(1).setCellEditor(new javax.swing.DefaultCellEditor(
                new javax.swing.JComboBox<>(new String[]{"VES", "USD"})));
        tablaPagosFac.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(new javax.swing.JTextField()));
        scrollPagosFac = new javax.swing.JScrollPane(tablaPagosFac);
        jPanel25.add(scrollPagosFac);

        btnAgregarPagoFac = new javax.swing.JButton("+ Medio");
        btnAgregarPagoFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnAgregarPagoFac.setToolTipText("Agregar otra forma o moneda de pago");
        btnAgregarPagoFac.addActionListener(e -> {
            modeloPagosFac.addRow(new Object[]{"TRANSFERENCIA", "VES", ""});
            int fila = modeloPagosFac.getRowCount() - 1;
            tablaPagosFac.changeSelection(fila, 2, false, false);
            tablaPagosFac.editCellAt(fila, 2);
            tablaPagosFac.requestFocusInWindow();
        });
        jPanel25.add(btnAgregarPagoFac);

        btnQuitarPagoFac = new javax.swing.JButton("− Medio");
        btnQuitarPagoFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnQuitarPagoFac.setToolTipText("Quitar la forma de pago seleccionada");
        btnQuitarPagoFac.addActionListener(e -> {
            int fila = tablaPagosFac.getSelectedRow();
            if (fila >= 0 && modeloPagosFac.getRowCount() > 1) modeloPagosFac.removeRow(fila);
            actualizarResumenPagosFac();
        });
        jPanel25.add(btnQuitarPagoFac);

        lblResumenPagosFac = new javax.swing.JLabel("Tasa del pedido: — | Pagado: Bs. 0.00 | Saldo: —");
        lblResumenPagosFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblResumenPagosFac);

        lblAlertaPagosFac = new javax.swing.JLabel(" ");
        lblAlertaPagosFac.setForeground(COLOR_PELIGRO);
        lblAlertaPagosFac.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        jPanel25.add(lblAlertaPagosFac);

        modeloPagosFac.addTableModelListener(e -> actualizarResumenPagosFac());
        ajustarScrollFinalizar();
        configurarTablaIndividual(tablaPagosFac);
        aplicarTooltipsDefecto(jPanel25);
    }

    /** Unifica la barra lateral: botones pizarra con texto blanco y fuente Segoe UI. */
    public static void botonSolido(javax.swing.AbstractButton boton, java.awt.Color fondo, java.awt.Color texto) {
        // Los LaF (Windows ignora setBackground y pinta el tema del SO; GTK respeta
        // a medias) no garantizan el fondo: este delegado SIEMPRE pinta el color dado.
        // El LaF solo se usa una vez al arranque, asi que no hay updateUI que lo revierta.
        // Orden: setUI primero porque BasicButtonUI.installDefaults resetea opaque.
        boton.setUI(new BotonSolidoUI());
        boton.setBackground(fondo);
        boton.setForeground(texto);
        boton.setOpaque(true);
        boton.setContentAreaFilled(false);
        boton.setBorderPainted(true);
        java.awt.Font fuente = boton.getFont();
        if (fuente != null && !fuente.isBold()) {
            boton.setFont(fuente.deriveFont(java.awt.Font.BOLD));
        }
    }

    /** UI que rellena el fondo del boton (con estados hover/pressed) en cualquier LaF. */
    private static final class BotonSolidoUI extends javax.swing.plaf.basic.BasicButtonUI {
        @Override
        public void paint(java.awt.Graphics g, javax.swing.JComponent c) {
            javax.swing.AbstractButton b = (javax.swing.AbstractButton) c;
            java.awt.Color base = b.getBackground() != null
                    ? b.getBackground() : new java.awt.Color(44, 62, 80);
            javax.swing.ButtonModel m = b.getModel();
            if (m.isPressed() && m.isArmed()) {
                base = base.darker();
            } else if (m.isRollover()) {
                java.awt.Color hover = base.brighter();
                if (contraste(hover, b.getForeground()) < 4.5) {
                    hover = base.darker();
                }
                base = hover;
            }
            g.setColor(base);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            super.paint(g, c);
        }
    }

    /** Aplica botonSolido a todo boton con fondo personalizado (el nativo ignora el color en Windows). */
    private static void solidificarBotones(java.awt.Container contenedor) {
        if (contenedor == null) {
            return;
        }
        javax.swing.JButton defecto = new javax.swing.JButton();
        java.awt.Color fondoDefecto = defecto.getBackground();
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.JButton boton) {
                java.awt.Font fuente = boton.getFont();
                if (fuente != null && !fuente.isBold()) {
                    boton.setFont(fuente.deriveFont(java.awt.Font.BOLD));
                }
                if (!(boton.getUI() instanceof BotonSolidoUI)) {
                    java.awt.Color fondo = boton.getBackground();
                    if (fondo != null && !fondo.equals(fondoDefecto)) {
                        botonSolido(boton, fondo, boton.getForeground() != null
                                ? boton.getForeground() : new java.awt.Color(255, 255, 255));
                    }
                }
            }
            if (c instanceof java.awt.Container hijo) {
                solidificarBotones(hijo);
            }
        }
    }

    private static double contraste(java.awt.Color fondo, java.awt.Color texto) {
        double lFondo = luminancia(fondo);
        double lTexto = luminancia(texto);
        return (Math.max(lFondo, lTexto) + 0.05) / (Math.min(lFondo, lTexto) + 0.05);
    }

    private static double luminancia(java.awt.Color color) {
        double r = canalLineal(color.getRed());
        double g = canalLineal(color.getGreen());
        double b = canalLineal(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double canalLineal(int canal) {
        double valor = canal / 255.0;
        return valor <= 0.04045 ? valor / 12.92 : Math.pow((valor + 0.055) / 1.055, 2.4);
    }

    private void estilizarBarraLateral() {
        if (jPanel1 == null) {
            return;
        }
        java.awt.Font fuenteBarra = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12);
        for (java.awt.Component c : jPanel1.getComponents()) {
            if (c instanceof javax.swing.AbstractButton boton) {
                botonSolido(boton, COLOR_CABECERA, new java.awt.Color(255, 255, 255));
                boton.setFont(fuenteBarra);
            }
        }
    }

    /** Carga un icono de la barra lateral y lo escala a un tamano uniforme de 32x32. */
    private static javax.swing.ImageIcon iconoBarra(String recurso) {
        try {
            java.net.URL url = Sistema.class.getResource(recurso);
            if (url == null) {
                return null;
            }
            java.awt.Image img = new javax.swing.ImageIcon(url).getImage();
            return new javax.swing.ImageIcon(img.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Abre la aplicacion maximizada y hace que la barra lateral y el area de
     * pestañas se redimensionen con la ventana para aprovechar todo el espacio.
     */
    private void configurarVentanaResponsiva() {
        setResizable(true);
        java.awt.Container cp = getContentPane();
        cp.setLayout(null);
        cp.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                reacomodarComponentes();
            }
        });
        setExtendedState(getExtendedState() | javax.swing.JFrame.MAXIMIZED_BOTH);
        javax.swing.SwingUtilities.invokeLater(this::reacomodarComponentes);
    }

    /** Reubica los paneles principales (barra lateral, banner, pestañas) al tamano actual. */
    private void reacomodarComponentes() {
        java.awt.Container cp = getContentPane();
        int ancho = cp.getWidth();
        int alto = cp.getHeight();
        if (ancho <= 0 || alto <= 0) {
            return;
        }
        int anchoBarra = ANCHO_BARRA_LATERAL;
        if (scrollBarraLateral != null) {
            scrollBarraLateral.setBounds(0, 0, anchoBarra, alto);
        } else if (jPanel1 != null) {
            jPanel1.setBounds(0, 0, anchoBarra, alto);
        }
        if (lblBannerTasa != null) {
            lblBannerTasa.setBounds(anchoBarra + 5, 12, 305, 65);
        }
        if (panelConmutadorModoGlobal != null) {
            panelConmutadorModoGlobal.setBounds(anchoBarra + 315, 12, 280, 65);
            try {
                cp.setComponentZOrder(panelConmutadorModoGlobal, 0);
            } catch (Exception ignored) {}
        }
        if (jLabel38 != null) {
            int xTitulo = Math.max(anchoBarra + 600, ancho - 545);
            jLabel38.setBounds(xTitulo, 0, 540, ALTO_CABECERA);
        }
        if (jTabbedPane1 != null) {
            int yTabs = ALTO_CABECERA + 5;
            jTabbedPane1.setBounds(anchoBarra, yTabs,
                    Math.max(200, ancho - anchoBarra), Math.max(150, alto - yTabs));
        }
    }

    /** Refresca desde la BD la vista que acaba de hacerse visible (pestaña activa). */
    private void refrescarVistaVisible(String title) {
        if (title == null) return;
        switch (title) {
            case "Mesas" -> {
                refrescarPanelMesasActual();
            }
            case "Panel" -> {
                panelSalas();
            }
            case "Salas" -> ListarSalas();
            case "Platos" -> {
                int selIdx = jTabbedPane1.getSelectedIndex();
                if (selIdx == 3) {
                    refrescarMenuPlatos();
                } else {
                    ListarPlatos(TablePlatos, "");
                }
            }
            case "Datos de la Empresa" -> ListarConfig();
            case "Usuarios" -> ListarUsuarios();
            case "Dashboard" -> { if (politicaAcceso.esAdministrador()) cargarDashboardYClientes(); }
            case "Atención Directa (Split)" -> {
                if (panelSplitScreen != null && !salasCache.isEmpty()) {
                    panelSplitScreen.actualizarSalas(salasCache, salaIdAPrefijo);
                }
            }
            default -> { }
        }
    }

    /** Patrón Observer: registra qué vistas se refrescan cuando cambian los datos. */
    private void configurarObservadoresVistas() {
        RefrescoVistas.limpiar();
        RefrescoVistas.suscribir(RefrescoVistas.PLATOS, carga -> {
            refrescarMenuPlatos();
            if (panelSplitScreen != null) {
                panelSplitScreen.refrescarPlatos(carga);
            }
        });
        RefrescoVistas.suscribir(RefrescoVistas.CATEGORIAS, this::cargarCategorias);
        RefrescoVistas.suscribir(RefrescoVistas.CATEGORIAS, this::refrescarMenuPlatos);
        RefrescoVistas.suscribir(RefrescoVistas.SALAS, this::panelSalas);
        RefrescoVistas.suscribir(RefrescoVistas.MESAS, this::refrescarPanelMesasActual);
        RefrescoVistas.suscribir(RefrescoVistas.PEDIDOS, carga -> {
            refrescarPanelMesasActual();
            ListarPedidos();
            if (ped != null && ped.getId() > 0 && jTabbedPane1.getSelectedIndex() == 4) {
                if (carga instanceof Modelo.ResultadoActualizacionPrecios res) {
                    if (res.pedidosAfectados() == null || res.pedidosAfectados().isEmpty() || res.pedidosAfectados().contains(ped.getId())) {
                        cargarPedidoEnPantalla(ped.getId(), btnFinalizar.isEnabled());
                    }
                } else if (carga instanceof List<?> lista) {
                    if (lista.contains(ped.getId())) {
                        cargarPedidoEnPantalla(ped.getId(), btnFinalizar.isEnabled());
                    }
                }
            }
            if (panelSplitScreen != null) {
                panelSplitScreen.refrescarPedidos(carga);
            }
        });
        RefrescoVistas.suscribir(RefrescoVistas.HISTORIAL, this::ListarPedidos);
        RefrescoVistas.suscribir(RefrescoVistas.USUARIOS, this::ListarUsuarios);
        RefrescoVistas.suscribir(RefrescoVistas.CONFIG, this::ListarConfig);
        RefrescoVistas.suscribir(RefrescoVistas.DASHBOARD, () -> { if (politicaAcceso.esAdministrador()) cargarDashboardYClientes(); });
        iniciarRefrescoPeriodicoModos();
    }

    /** Vuelve a leer el menú/selector de platos (pestaña de toma de pedido). */
    private void refrescarMenuPlatos() {
        if (tblTemPlatos != null) {
            ListarPlatos(tblTemPlatos, txtBuscarPlato != null ? txtBuscarPlato.getText() : "");
        }
    }

    /**
     * Refresco permanente: cada 30 s relee la vista del modo visible (A/B/C/Clásico)
     * para que ningún mapa quede obsoleto (p. ej. cambios desde otro terminal, que el
     * bus en-proceso no recibe). Solo relee; no mueve selección ni foco. Desactivado
     * en E2E para no interferir con pruebas guiadas.
     */
    private void iniciarRefrescoPeriodicoModos() {
        if (temporizadorRefrescoModos != null) {
            return;
        }
        if ("SI".equals(System.getProperty("e2e.permitir"))) {
            return;
        }
        temporizadorRefrescoModos = new javax.swing.Timer(30000, e -> refrescarVistaModoVisible());
        temporizadorRefrescoModos.setRepeats(true);
        temporizadorRefrescoModos.setCoalesce(true);
        temporizadorRefrescoModos.start();
    }

    private void refrescarVistaModoVisible() {
        if (!isVisible() || jTabbedPane1 == null) {
            return;
        }
        try {
            java.awt.Component sel = jTabbedPane1.getSelectedComponent();
            if (sel == null) {
                return;
            }
            if (panelSplitScreen != null && javax.swing.SwingUtilities.isDescendingFrom(panelSplitScreen, sel)) {
                panelSplitScreen.refrescarMapa();
                return;
            }
            int idxSel = jTabbedPane1.getSelectedIndex();
            if (idxSel == jTabbedPane1.indexOfTab("Mesas")) {
                refrescarPanelMesasActual();
            } else if (idxSel == jTabbedPane1.indexOfTab("Panel")) {
                panelSalas();
            }
        } catch (Exception ignored) {
        }
    }

    /** Reconstruye la cuadrícula de mesas de la sala actualmente abierta, si la hay. */
    private void refrescarPanelMesasActual() {
        if (idSalaActualMesas > 0 && cantMesasActual > 0 && nombreSalaActualMesas != null) {
            panelMesas(idSalaActualMesas, nombreSalaActualMesas, cantMesasActual);
        } else if (!salasCache.isEmpty()) {
            Salas s = salasCache.get(0);
            panelMesas(s.getId(), s.getNombre(), s.getMesas(), s.esBarra());
        }
        refrescarBarraSalasMesas();
    }

    /**
     * Aplica una medida estándar (ancho/alto) a los botones con texto, según la
     * recomendación de ergonomía para interfaces de escritorio. Se omiten los botones
     * de glifos o con icono (numpad, navegación lateral, +/- cantidades, etc.).
     */
    private void aplicarMedidaEstandarBotones(java.awt.Container contenedor, java.util.Set<java.awt.Component> excluidos) {
        if (contenedor == null) return;
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.JButton boton && !excluidos.contains(c)) {
                String texto = boton.getText();
                boolean esGlifo = texto == null || texto.trim().length() <= 2;
                boolean tieneIcono = boton.getIcon() != null;
                if (!esGlifo && !tieneIcono) {
                    int ancho = Math.max(boton.getPreferredSize().width, ANCHO_BOTON_ESTANDAR);
                    boton.setMinimumSize(new java.awt.Dimension(ANCHO_BOTON_ESTANDAR, ALTO_BOTON_ESTANDAR));
                    boton.setPreferredSize(new java.awt.Dimension(ancho, ALTO_BOTON_ESTANDAR));
                }
                aplicarTooltipsDefecto(boton);
            }
            if (c instanceof java.awt.Container sub) {
                aplicarMedidaEstandarBotones(sub, excluidos);
            }
        }
    }

    /**
     * Garantiza tooltip en un botón (usa su texto si no tiene) y, para contenedores,
     * lo aplica recursivamente. Usado en ventanas de diálogo construidas bajo demanda.
     */
    public static void aplicarTooltipsDefecto(java.awt.Component comp) {
        if (comp instanceof javax.swing.JButton boton) {
            String tip = boton.getToolTipText();
            String texto = boton.getText();
            if ((tip == null || tip.isBlank()) && texto != null && !texto.isBlank()) {
                boton.setToolTipText(texto.trim());
            }
            return;
        }
        if (comp instanceof java.awt.Container cont) {
            for (java.awt.Component hijo : cont.getComponents()) {
                aplicarTooltipsDefecto(hijo);
            }
        }
    }

    private Controlador.CategoriaControlador categoriaControlador() {
        if (categoriaControlador == null) {
            categoriaControlador = new Controlador.CategoriaControlador(
                    new Servicio.CategoriaServicio(new Modelo.CategoriaDao(), politicaAcceso));
        }
        return categoriaControlador;
    }

    private Controlador.FavoritoControlador favoritoControlador() {
        if (favoritoControlador == null) {
            favoritoControlador = new Controlador.FavoritoControlador(
                    new Servicio.FavoritoServicio(new Modelo.FavoritoDao(), politicaAcceso));
        }
        return favoritoControlador;
    }

    private boolean autorizar(PoliticaAcceso.Accion accion) {
        if (usuarioActual != null && usuarioActual.getId() > 0) {
            boolean activo;
            try {
                activo = lgDao.esUsuarioActivo(usuarioActual.getId());
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Sistema.class.getName())
                        .warning("No se pudo verificar el estado activo del usuario #" + usuarioActual.getId() + " en la BD; acceso denegado por seguridad: " + ex.getMessage());
                JOptionPane.showMessageDialog(this,
                        "No se pudo verificar el estado de su cuenta con la base de datos por un error de conexión.\nPor seguridad, la acción ha sido denegada.",
                        "Error de verificación", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            if (!activo) {
                usuarioActual.setActivo(false);
                JOptionPane.showMessageDialog(this,
                        "Tu cuenta ha sido desactivada por un administrador. La sesión ha sido revocada.",
                        "Acceso revocado", JOptionPane.ERROR_MESSAGE);
                this.dispose();
                restaurante.Restaurante.lanzarLogin();
                return false;
            }
        }
        if (politicaAcceso.permite(accion)) {
            return true;
        }
        JOptionPane.showMessageDialog(this,
                "Tu rol no permite realizar esta acción.",
                "Acceso restringido", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        labelLogo = new javax.swing.JLabel();
        btnSala = new javax.swing.JButton();
        btnVentas = new javax.swing.JButton();
        btnConfig = new javax.swing.JButton();
        LabelVendedor = new javax.swing.JLabel();
        tipo = new javax.swing.JLabel();
        btnUsuarios = new javax.swing.JButton();
        btnCategorias = new javax.swing.JButton();
        btnInicio = new javax.swing.JButton();
        btnPedidoRapido = new javax.swing.JButton();
        btnAtencionDirecta = new javax.swing.JButton();
        btnPlatos = new javax.swing.JButton();
        jLabel38 = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel9 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        PanelSalas = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tableSala = new javax.swing.JTable();
        jPanel10 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        txtNombreSala = new javax.swing.JTextField();
        btnRegistrarSala = new javax.swing.JButton();
        btnActualizarSala = new javax.swing.JButton();
        btnNuevoSala = new javax.swing.JButton();
        btnEliminarSala = new javax.swing.JButton();
        txtIdSala = new javax.swing.JTextField();
        jPanel35 = new javax.swing.JPanel();
        jPanel38 = new javax.swing.JPanel();
        jLabel33 = new javax.swing.JLabel();
        jPanel36 = new javax.swing.JPanel();
        txtMesas = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        jPanel22 = new javax.swing.JPanel();
        jScrollPane9 = new javax.swing.JScrollPane();
        PanelMesas = new javax.swing.JPanel();
        jPanel23 = new javax.swing.JPanel();
        jPanel24 = new javax.swing.JPanel();
        txtBuscarPlato = new javax.swing.JTextField();
        jScrollPane10 = new javax.swing.JScrollPane();
        tblTemPlatos = new javax.swing.JTable();
        btnAddPlato = new javax.swing.JButton();
        btnLimpiarBuscar = new javax.swing.JButton();
        chkFiltroRapido = new javax.swing.JCheckBox("Auto-limpiar", true);
        chkFiltroRapido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        chkFiltroRapido.setToolTipText("Al elegir un plato se limpia el filtro y se muestra en la lista completa");
        chkFiltroRapido.addItemListener(e -> {
            guardarPreferenciaFiltroRapido(chkFiltroRapido.isSelected());
        });
        jScrollPane11 = new javax.swing.JScrollPane();
        tableMenu = new javax.swing.JTable();
        jLabel6 = new javax.swing.JLabel();
        jScrollPane12 = new javax.swing.JScrollPane();
        txtComentario = new javax.swing.JTextPane();
        jLabel11 = new javax.swing.JLabel();
        totalMenu = new javax.swing.JLabel();
        btnGenerarPedido = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        btnEliminarTempPlato = new javax.swing.JButton();
        txtTempIdSala = new javax.swing.JTextField();
        txtTempNumMesa = new javax.swing.JTextField();
        jPanel25 = new javax.swing.JPanel();
        btnFinalizar = new javax.swing.JButton();
        totalFinalizar = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        jScrollPane13 = new javax.swing.JScrollPane();
        tableFinalizar = new javax.swing.JTable();
        txtIdPedido = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        txtFechaHora = new javax.swing.JTextField();
        txtSalaFinalizar = new javax.swing.JTextField();
        txtNumMesaFinalizar = new javax.swing.JTextField();
        btnPdfPedido = new javax.swing.JButton();
        btnModificarPedido = new javax.swing.JButton();
        txtIdHistorialPedido = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        TablePedidos = new javax.swing.JTable();
        jLabel16 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        jLabel32 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        txtIdConfig = new javax.swing.JTextField();
        jLabel30 = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        txtTelefonoConfig = new javax.swing.JTextField();
        txtDireccionConfig = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
        txtMensaje = new javax.swing.JTextField();
        jLabelTasaConfig = new javax.swing.JLabel();
        txtTasaConfig = new javax.swing.JTextField();
        jLabelIvaConfig = new javax.swing.JLabel();
        txtIvaConfig = new javax.swing.JTextField();
        btnActualizarConfig = new javax.swing.JButton();
        jLabel27 = new javax.swing.JLabel();
        txtRucConfig = new javax.swing.JTextField();
        jLabel28 = new javax.swing.JLabel();
        txtNombreConfig = new javax.swing.JTextField();
        jPanel41 = new javax.swing.JPanel();
        jPanel42 = new javax.swing.JPanel();
        jPanel43 = new javax.swing.JPanel();
        jPanel44 = new javax.swing.JPanel();
        jPanel45 = new javax.swing.JPanel();
        jLabel21 = new javax.swing.JLabel();
        jPanel40 = new javax.swing.JPanel();
        jPanel12 = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        TableUsuarios = new javax.swing.JTable();
        jPanel15 = new javax.swing.JPanel();
        jLabel34 = new javax.swing.JLabel();
        jLabel35 = new javax.swing.JLabel();
        txtCorreo = new javax.swing.JTextField();
        txtPass = new javax.swing.JPasswordField();
        btnIniciar = new javax.swing.JButton();
        jLabel36 = new javax.swing.JLabel();
        txtNombre = new javax.swing.JTextField();
        jLabel37 = new javax.swing.JLabel();
        cbxRol = new javax.swing.JComboBox<>();
        jPanel16 = new javax.swing.JPanel();
        jPanel17 = new javax.swing.JPanel();
        jPanel18 = new javax.swing.JPanel();
        jPanel21 = new javax.swing.JPanel();
        jLabel39 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jPanel11 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        txtNombrePlato = new javax.swing.JTextField();
        jLabel25 = new javax.swing.JLabel();
        txtPrecioPlato = new javax.swing.JTextField();
        btnGuardarPlato = new javax.swing.JButton();
        btnEditarPlato = new javax.swing.JButton();
        btnEliminarPlato = new javax.swing.JButton();
        btnNuevoPlato = new javax.swing.JButton();
        jPanel31 = new javax.swing.JPanel();
        jPanel33 = new javax.swing.JPanel();
        jPanel39 = new javax.swing.JPanel();
        jLabel40 = new javax.swing.JLabel();
        txtIdPlato = new javax.swing.JTextField();
        jScrollPane4 = new javax.swing.JScrollPane();
        TablePlatos = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Panel de Adminstración");
        setResizable(false);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        labelLogo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        labelLogo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        labelLogo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                labelLogoMouseClicked(evt);
            }
        });

        btnSala.setBackground(new java.awt.Color(0, 0, 0));
        btnSala.setForeground(new java.awt.Color(255, 255, 255));
        btnSala.setIcon(iconoBarra("/Img/sala.png"));
        btnSala.setText("Salas");
        btnSala.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnSala.setFocusable(false);
        btnSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSalaActionPerformed(evt);
            }
        });

        btnVentas.setBackground(new java.awt.Color(0, 0, 0));
        btnVentas.setForeground(new java.awt.Color(255, 255, 255));
        btnVentas.setIcon(iconoBarra("/Img/pedidos.png"));
        btnVentas.setText("Pedidos");
        btnVentas.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnVentas.setFocusable(false);
        btnVentas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnVentasActionPerformed(evt);
            }
        });

        btnConfig.setBackground(new java.awt.Color(0, 0, 0));
        btnConfig.setForeground(new java.awt.Color(255, 255, 255));
        btnConfig.setIcon(iconoBarra("/Img/config.png"));
        btnConfig.setText("Config");
        btnConfig.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnConfig.setFocusable(false);
        btnConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConfigActionPerformed(evt);
            }
        });

        LabelVendedor.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        LabelVendedor.setText("Administrador");

        tipo.setForeground(new java.awt.Color(255, 255, 255));

        btnUsuarios.setBackground(new java.awt.Color(0, 0, 0));
        btnUsuarios.setForeground(new java.awt.Color(255, 255, 255));
        btnUsuarios.setIcon(iconoBarra("/Img/usuarios.png"));
        btnUsuarios.setText("Usuarios");
        btnUsuarios.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnUsuarios.setFocusable(false);
        btnUsuarios.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUsuariosActionPerformed(evt);
            }
        });

        btnPlatos.setBackground(new java.awt.Color(0, 0, 0));
        btnPlatos.setForeground(new java.awt.Color(255, 255, 255));
        btnPlatos.setIcon(iconoBarra("/Img/platos.png"));
        btnPlatos.setText("Platos");
        btnPlatos.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnPlatos.setFocusable(false);
        btnPlatos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPlatosActionPerformed(evt);
            }
        });

        btnCategorias.setBackground(new java.awt.Color(0, 0, 0));
        btnCategorias.setForeground(new java.awt.Color(255, 255, 255));
        // Reuse platos icon or config icon
        btnCategorias.setIcon(iconoBarra("/Img/platos.png"));
        btnCategorias.setText("Categorías");
        btnCategorias.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnCategorias.setFocusable(false);
        btnCategorias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
                int idx = jTabbedPane1.indexOfTab("Categorías");
                if(idx != -1) jTabbedPane1.setSelectedIndex(idx);
            }
        });

        btnInicio.setBackground(new java.awt.Color(0, 0, 0));
        btnInicio.setForeground(new java.awt.Color(255, 255, 255));
        btnInicio.setIcon(iconoBarra("/Img/mesa.png"));
        btnInicio.setText("Inicio");
        btnInicio.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        btnInicio.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnInicio.setFocusable(false);
        btnInicio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LimpiarTableMenu();
                modoPedidoRapido = false;
                jTabbedPane1.setSelectedIndex(0);
                panelSalas();
            }
        });

        btnPedidoRapido.setBackground(java.awt.Color.BLACK);
        btnPedidoRapido.setForeground(java.awt.Color.WHITE);
        btnPedidoRapido.setIcon(iconoBarra("/Img/pedidos.png"));
        btnPedidoRapido.setText("Pedido rápido");
        btnPedidoRapido.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        btnPedidoRapido.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnPedidoRapido.setFocusable(false);
        btnPedidoRapido.setToolTipText("Registrar un pedido para llevar sin asignarlo a una mesa");
        btnPedidoRapido.addActionListener(e -> abrirPedidoRapido());

        btnAtencionDirecta.setBackground(java.awt.Color.BLACK);
        btnAtencionDirecta.setForeground(java.awt.Color.WHITE);
        btnAtencionDirecta.setIcon(iconoBarra("/Img/mesa.png"));
        btnAtencionDirecta.setText("Atn. directa");
        btnAtencionDirecta.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        btnAtencionDirecta.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnAtencionDirecta.setFocusable(false);
        btnAtencionDirecta.setToolTipText("Atención directa: Acceso rápido a atención de mesas");
        btnAtencionDirecta.addActionListener(e -> abrirAtencionDirectaSegunModo());

        btnDashboard = new javax.swing.JButton("Dashboard");
        btnDashboard.setBackground(new java.awt.Color(0, 0, 0));
        btnDashboard.setForeground(new java.awt.Color(255, 255, 255));
        btnDashboard.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnDashboard.setIcon(iconoBarra("/Img/pedidos.png"));
        btnDashboard.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        btnDashboard.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnDashboard.setFocusable(false);
        btnDashboard.addActionListener(e -> {
            int idx = jTabbedPane1.indexOfTab("Dashboard");
            if(idx != -1) jTabbedPane1.setSelectedIndex(idx);
            cargarDashboardYClientes();
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnSala, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnVentas, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(LabelVendedor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnUsuarios, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(74, 74, 74)
                .addComponent(tipo)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(btnInicio, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnPedidoRapido, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnAtencionDirecta, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnPlatos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnCategorias, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnDashboard, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(labelLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(labelLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(tipo)
                .addGap(12, 12, 12)
                .addComponent(LabelVendedor)
                .addGap(12, 12, 12)
                .addComponent(btnInicio, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(btnPedidoRapido, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(btnAtencionDirecta, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(btnPlatos, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(btnCategorias, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6)
                .addComponent(btnSala, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(btnVentas, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(btnConfig, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(btnUsuarios, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addComponent(btnDashboard, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        // AVISO DISEÑADOR: bloque manual (no está en Sistema.form). Ver nota en el campo.
        // Barra lateral con scroll: jPanel1 supera los 720 px con 10 botones y el
        // Dashboard quedaba recortado. El viewport muestra scroll vertical solo si hace falta.
        scrollBarraLateral = new javax.swing.JScrollPane(jPanel1,
                javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollBarraLateral.setBorder(null);
        scrollBarraLateral.getVerticalScrollBar().setUnitIncrement(16);
        getContentPane().add(scrollBarraLateral, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 150, 720));

        jLabel38.setFont(new java.awt.Font("Zilla Slab", 3, 48)); // NOI18N
        jLabel38.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/titulo.png"))); // NOI18N
        jLabel38.setText("Restaurante");
        jLabel38.setFocusable(false);
        jLabel38.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        getContentPane().add(jLabel38, new org.netbeans.lib.awtextra.AbsoluteConstraints(520, 0, 540, 90));

        jTabbedPane1.setBackground(new java.awt.Color(255, 255, 255));

        PanelSalas.setBackground(new java.awt.Color(245, 247, 250));
        PanelSalas.setLayout(new java.awt.GridLayout(0, 5, 12, 12));
        PanelSalas.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        jScrollPane8.setViewportView(PanelSalas);
        // Rejilla proporcional: recalcula columnas al cambiar el ancho (ver ajustarColumnasSalas).
        jScrollPane8.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarColumnasSalas();
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(30, 30, 30))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 480, Short.MAX_VALUE)
                .addGap(30, 30, 30))
        );

        jTabbedPane1.addTab("Panel", jPanel9);

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));
        jPanel4.setLayout(new java.awt.BorderLayout(15, 0));
        jPanel4.setBorder(javax.swing.BorderFactory.createEmptyBorder(25, 30, 25, 30));

        tableSala.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "NOMBRE", "Mesas"
            }
        ));
        tableSala.setRowHeight(23);
        tableSala.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tableSalaMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(tableSala);
        if (tableSala.getColumnModel().getColumnCount() > 0) {
            tableSala.getColumnModel().getColumn(0).setMinWidth(80);
            tableSala.getColumnModel().getColumn(0).setPreferredWidth(80);
            tableSala.getColumnModel().getColumn(0).setMaxWidth(130);
            tableSala.getColumnModel().getColumn(1).setPreferredWidth(100);
            tableSala.getColumnModel().getColumn(2).setMinWidth(80);
            tableSala.getColumnModel().getColumn(2).setPreferredWidth(80);
            tableSala.getColumnModel().getColumn(2).setMaxWidth(150);
        }

        jPanel4.add(jScrollPane3, java.awt.BorderLayout.CENTER);

        jPanel10.setBackground(new java.awt.Color(255, 255, 255));
        jPanel10.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_CABECERA, 1));
        jPanel10.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel18.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel18.setText("Nombre:");
        jPanel10.add(jLabel18, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 80, -1, -1));

        txtNombreSala.setBackground(new java.awt.Color(255, 255, 255));
        txtNombreSala.setBorder(null);
        jPanel10.add(txtNombreSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 70, 190, 30));

        btnRegistrarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/GuardarTodo.png"))); // NOI18N
        btnRegistrarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRegistrarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnRegistrarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 230, 129, 40));

        btnActualizarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/Actualizar (2).png"))); // NOI18N
        btnActualizarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnActualizarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnActualizarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(161, 230, 129, 40));

        btnNuevoSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/nuevo.png"))); // NOI18N
        btnNuevoSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNuevoSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnNuevoSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(161, 300, 129, 40));

        btnEliminarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/eliminar.png"))); // NOI18N
        btnEliminarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEliminarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnEliminarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 300, 129, 40));
        jPanel10.add(txtIdSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 50, 24, -1));

        jPanel35.setBackground(COLOR_CABECERA);

        javax.swing.GroupLayout jPanel35Layout = new javax.swing.GroupLayout(jPanel35);
        jPanel35.setLayout(jPanel35Layout);
        jPanel35Layout.setHorizontalGroup(
            jPanel35Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 190, Short.MAX_VALUE)
        );
        jPanel35Layout.setVerticalGroup(
            jPanel35Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 2, Short.MAX_VALUE)
        );

        jPanel10.add(jPanel35, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 100, 190, 2));

        jPanel38.setBackground(COLOR_CABECERA);
        jPanel38.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel33.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel33.setForeground(new java.awt.Color(255, 255, 255));
        jLabel33.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel33.setText("Nueva Sala");
        jPanel38.add(jLabel33, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 30));

        jPanel10.add(jPanel38, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 35));

        jPanel36.setBackground(COLOR_CABECERA);

        javax.swing.GroupLayout jPanel36Layout = new javax.swing.GroupLayout(jPanel36);
        jPanel36.setLayout(jPanel36Layout);
        jPanel36Layout.setHorizontalGroup(
            jPanel36Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 190, Short.MAX_VALUE)
        );
        jPanel36Layout.setVerticalGroup(
            jPanel36Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 2, Short.MAX_VALUE)
        );

        jPanel10.add(jPanel36, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 170, 190, 2));

        txtMesas.setBackground(new java.awt.Color(255, 255, 255));
        txtMesas.setBorder(null);
        jPanel10.add(txtMesas, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 140, 190, 30));

        jLabel19.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel19.setText("Mesas:");
        jPanel10.add(jLabel19, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 150, -1, -1));

        jPanel4.add(jPanel10, java.awt.BorderLayout.WEST);

        jTabbedPane1.addTab("Salas", jPanel4);

        PanelMesas.setLayout(new java.awt.GridLayout(0, 5));
        jScrollPane9.setViewportView(PanelMesas);

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 1051, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 564, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab("Mesas", jPanel22);

        jPanel24.setBorder(javax.swing.BorderFactory.createTitledBorder("Platos del Dia"));

        txtBuscarPlato.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtBuscarPlatoKeyReleased(evt);
            }
        });

        tblTemPlatos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Nombre", "Precio"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tblTemPlatos.setRowHeight(23);
        jScrollPane10.setViewportView(tblTemPlatos);
        if (tblTemPlatos.getColumnModel().getColumnCount() > 0) {
            tblTemPlatos.getColumnModel().getColumn(0).setMinWidth(30);
            tblTemPlatos.getColumnModel().getColumn(0).setPreferredWidth(30);
            tblTemPlatos.getColumnModel().getColumn(0).setMaxWidth(50);
            tblTemPlatos.getColumnModel().getColumn(2).setMinWidth(150);
            tblTemPlatos.getColumnModel().getColumn(2).setPreferredWidth(150);
            tblTemPlatos.getColumnModel().getColumn(2).setMaxWidth(200);
        }
        
        tblTemPlatos.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() >= 1) {
                    // Click en tblTemPlatos: Si el plato ya está en el carrito, sumarle 1 y seleccionarlo
                    int row = tblTemPlatos.rowAtPoint(evt.getPoint());
                    if (row >= 0) {
                        int id = Integer.parseInt(Tables.valorModelo(tblTemPlatos, row, 0).toString());
                        String nombreNuevo = Tables.valorModelo(tblTemPlatos, row, 1).toString();
                        // Revisar si ya está en el tableMenu
                        DefaultTableModel tmp = (DefaultTableModel) tableMenu.getModel();
                        boolean found = false;
                        for (int i = 0; i < tmp.getRowCount(); i++) {
                            String idFila = tmp.getValueAt(i, 0).toString();
                            String nombreFila = tmp.getValueAt(i, 1).toString();
                            boolean coincide = false;
                            if (!idFila.isBlank() && idFila.matches("\\d+")) {
                                coincide = Integer.parseInt(idFila) == id;
                            }
                            if (!coincide && nombreFila.equalsIgnoreCase(nombreNuevo)) {
                                coincide = true;
                            }
                            if (coincide) {
                                found = true;
                                int cantActual = Integer.parseInt(tmp.getValueAt(i, 2).toString());
                                setCantidadPlatoPorFila(i, cantActual + 1);
                                tableMenu.setRowSelectionInterval(i, i);
                                if (txtCantidadManual != null) {
                                    txtCantidadManual.requestFocusInWindow();
                                    txtCantidadManual.selectAll();
                                }
                                break;
                            }
                        }
                        if (!found) {
                            // If not found, we simulate the "Añadir" button action
                            btnAddPlato.doClick();
                        }
                    }
                }
            }
        });

        tblTemPlatos.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    e.consume();
                    if (tblTemPlatos.getSelectedRow() >= 0) {
                        btnAddPlato.doClick();
                        tblTemPlatos.setSelectionBackground(new java.awt.Color(144, 238, 144));
                        new javax.swing.Timer(300, ev -> tblTemPlatos.setSelectionBackground(javax.swing.UIManager.getColor("Table.selectionBackground"))).start();
                    }
                }
            }
        });


        btnAddPlato.setBackground(new java.awt.Color(0, 0, 0));
        btnAddPlato.setFont(new java.awt.Font("Arial Black", 1, 24)); // NOI18N
        btnAddPlato.setForeground(new java.awt.Color(255, 255, 255));
        btnAddPlato.setText("+");
        btnAddPlato.setToolTipText("Agregar plato seleccionado al pedido");
        btnAddPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnAddPlato.setFocusable(false);
        btnAddPlato.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnAddPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddPlatoActionPerformed(evt);
            }
        });

        btnLimpiarBuscar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnLimpiarBuscar.setText("x");
        btnLimpiarBuscar.setToolTipText("Limpiar filtro de búsqueda");
        btnLimpiarBuscar.setFocusable(false);
        btnLimpiarBuscar.addActionListener(e -> {
            txtBuscarPlato.setText("");
            ListarPlatos(tblTemPlatos, "");
        });

        javax.swing.GroupLayout jPanel24Layout = new javax.swing.GroupLayout(jPanel24);
        jPanel24.setLayout(jPanel24Layout);
        jPanel24Layout.setHorizontalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel24Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane10, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel24Layout.createSequentialGroup()
                        .addComponent(txtBuscarPlato, javax.swing.GroupLayout.PREFERRED_SIZE, 230, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnLimpiarBuscar, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chkFiltroRapido, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 2, Short.MAX_VALUE)
                        .addComponent(btnAddPlato)))
                .addContainerGap())
        );
        jPanel24Layout.setVerticalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel24Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(txtBuscarPlato, javax.swing.GroupLayout.DEFAULT_SIZE, 32, Short.MAX_VALUE)
                    .addComponent(btnLimpiarBuscar, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chkFiltroRapido, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnAddPlato, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)
                .addContainerGap())
        );

        tableMenu.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Plato", "Cant.", "Precio", "Sub-Total", "Comentario"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tableMenu.setRowHeight(23);
        jScrollPane11.setViewportView(tableMenu);
        if (tableMenu.getColumnModel().getColumnCount() > 0) {
            tableMenu.getColumnModel().getColumn(0).setMinWidth(30);
            tableMenu.getColumnModel().getColumn(0).setPreferredWidth(30);
            tableMenu.getColumnModel().getColumn(0).setMaxWidth(50);
            tableMenu.getColumnModel().getColumn(1).setPreferredWidth(100);
            tableMenu.getColumnModel().getColumn(2).setMinWidth(40);
            tableMenu.getColumnModel().getColumn(2).setPreferredWidth(40);
            tableMenu.getColumnModel().getColumn(2).setMaxWidth(50);
            tableMenu.getColumnModel().getColumn(3).setPreferredWidth(50);
            tableMenu.getColumnModel().getColumn(4).setPreferredWidth(60);
        }

        jLabel6.setText("Comentario:");

        jScrollPane12.setViewportView(txtComentario);

        jLabel11.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        jLabel11.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/money.png"))); // NOI18N
        jLabel11.setText("Total a Pagar");

        totalMenu.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        totalMenu.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        totalMenu.setText("00.00");

        btnGenerarPedido.setText("Realizar Pedido");
        btnGenerarPedido.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGenerarPedidoActionPerformed(evt);
            }
        });

        jButton2.setText("Agregar");
        jButton2.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        btnEliminarTempPlato.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/eliminar.png"))); // NOI18N
        btnEliminarTempPlato.setToolTipText("Eliminar plato seleccionado del pedido");
        btnEliminarTempPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnEliminarTempPlato.setToolTipText("Eliminar plato seleccionado del pedido");
        btnEliminarTempPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEliminarTempPlatoActionPerformed(evt);
            }
        });

        btnMasCantidad = new javax.swing.JButton("+");
        btnMasCantidad.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        btnMasCantidad.setToolTipText("Aumentar cantidad (+1)");
        btnMasCantidad.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnMasCantidad.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aumentarCantidadSeleccionada();
            }
        });

        btnMenosCantidad = new javax.swing.JButton("−");
        btnMenosCantidad.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        btnMenosCantidad.setToolTipText("Disminuir cantidad (-1)");
        btnMenosCantidad.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnMenosCantidad.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                disminuirCantidadSeleccionada();
            }
        });


        txtCantidadManual = new javax.swing.JTextField("1");
        txtCantidadManual.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCantidadManual.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        txtCantidadManual.addFocusListener(new java.awt.event.FocusAdapter() {
            private Object platoIdFoco = null;

            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                javax.swing.SwingUtilities.invokeLater(() -> txtCantidadManual.selectAll());
                int selRow = tableMenu.getSelectedRow();
                if (selRow >= 0 && selRow < tableMenu.getRowCount()) {
                    platoSeleccionadoFilaFoco = selRow;
                    platoIdFoco = tableMenu.getModel().getValueAt(selRow, 0);
                } else {
                    platoSeleccionadoFilaFoco = -1;
                    platoIdFoco = null;
                }
                ultimoTextoIngresado = txtCantidadManual.getText();
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                int filaObjetivo = -1;
                if (platoIdFoco != null) {
                    for (int i = 0; i < tableMenu.getRowCount(); i++) {
                        if (platoIdFoco.equals(tableMenu.getModel().getValueAt(i, 0))) {
                            filaObjetivo = i;
                            break;
                        }
                    }
                }
                if (filaObjetivo != -1) {
                    String texto = txtCantidadManual.getText();
                    aplicarCantidadManual(texto != null && !texto.trim().isEmpty() ? texto.trim() : ultimoTextoIngresado, filaObjetivo);
                }
                platoIdFoco = null;
                platoSeleccionadoFilaFoco = -1;
                actualizarEstadoBotonesCarrito();
            }
        });
        txtCantidadManual.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateText(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateText(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateText(); }
            private void updateText() {
                if (txtCantidadManual.hasFocus() && !isUpdatingUI) {
                    ultimoTextoIngresado = txtCantidadManual.getText();
                }
            }
        });
        txtCantidadManual.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    if (tableMenu.getSelectedRow() >= 0) {
                        int currentRow = tableMenu.getSelectedRow();
                        aplicarCantidadManual(txtCantidadManual.getText(), currentRow);
                        tableMenu.requestFocusInWindow();
                    }
                }
            }
        });

        javax.swing.JPanel pnlBotonesCantidad = new javax.swing.JPanel(new java.awt.BorderLayout(0, 4));
        pnlBotonesCantidad.setOpaque(false);

        javax.swing.JPanel pnlControl = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
        pnlControl.setOpaque(false);
        pnlControl.add(btnMenosCantidad, java.awt.BorderLayout.WEST);
        pnlControl.add(txtCantidadManual, java.awt.BorderLayout.CENTER);
        pnlControl.add(btnMasCantidad, java.awt.BorderLayout.EAST);
        pnlBotonesCantidad.add(pnlControl, java.awt.BorderLayout.NORTH);

        javax.swing.JPanel pnlGrilla = new javax.swing.JPanel(new java.awt.GridLayout(2, 5, 2, 2));
        pnlGrilla.setOpaque(false);
        for (int i = 1; i <= 10; i++) {
            javax.swing.JButton btnNum = new javax.swing.JButton(String.valueOf(i));
            btnNum.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            btnNum.setMargin(new java.awt.Insets(2, 2, 2, 2));
            int qty = i;
            btnNum.addActionListener(e -> {
                if (tableMenu.getSelectedRow() >= 0) {
                    int currentRow = tableMenu.getSelectedRow();
                    aplicarCantidadManual(String.valueOf(qty), currentRow);
                }
            });
            pnlGrilla.add(btnNum);
        }
        pnlBotonesCantidad.add(pnlGrilla, java.awt.BorderLayout.CENTER);


        javax.swing.JPanel pnlMesoneroPedido = new javax.swing.JPanel(new java.awt.BorderLayout(0, 3));
        pnlMesoneroPedido.setOpaque(false);
        javax.swing.JLabel lblMesoneroPedido = new javax.swing.JLabel("Mesonero asignado (opcional):");
        lblMesoneroPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        cbMesoneroPedido = new javax.swing.JComboBox<>();
        cbMesoneroPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        cbMesoneroPedido.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Mesonero m) {
                    setText(m.getNombreCompleto() + " (" + m.getCedula() + ")");
                }
                return this;
            }
        });
        pnlMesoneroPedido.add(lblMesoneroPedido, java.awt.BorderLayout.NORTH);
        pnlMesoneroPedido.add(cbMesoneroPedido, java.awt.BorderLayout.CENTER);
        btnQuitarMesoneroPedido = new javax.swing.JButton("Quitar");
        btnQuitarMesoneroPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        btnQuitarMesoneroPedido.setToolTipText("Quitar la asignación del mesonero (se guarda sin mesonero)");
        btnQuitarMesoneroPedido.addActionListener(e -> quitarMesoneroAsignadoPedido());
        pnlMesoneroPedido.add(btnQuitarMesoneroPedido, java.awt.BorderLayout.EAST);
        cbMesoneroPedido.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED && e.getItem() instanceof Mesonero) {
                quitarMesoneroEdicion = false;
            }
        });

        javax.swing.GroupLayout jPanel23Layout = new javax.swing.GroupLayout(jPanel23);
        jPanel23.setLayout(jPanel23Layout);
        jPanel23Layout.setHorizontalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jScrollPane12, javax.swing.GroupLayout.PREFERRED_SIZE, 380, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE)
                            .addComponent(pnlBotonesCantidad, javax.swing.GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE)
                            .addComponent(btnEliminarTempPlato, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                        .addGap(0, 3, Short.MAX_VALUE)
                        .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane11, javax.swing.GroupLayout.PREFERRED_SIZE, 570, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel23Layout.createSequentialGroup()
                                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(pnlMesoneroPedido, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(jPanel23Layout.createSequentialGroup()
                                        .addComponent(txtTempIdSala, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(txtTempNumMesa, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 180, Short.MAX_VALUE)
                                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel11)
                                        .addGroup(jPanel23Layout.createSequentialGroup()
                                            .addGap(10, 10, 10)
                                            .addComponent(totalMenu, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addComponent(btnGenerarPedido, javax.swing.GroupLayout.Alignment.TRAILING))))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel24, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel23Layout.setVerticalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                .addContainerGap(27, Short.MAX_VALUE)
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel6)
                            .addGroup(jPanel23Layout.createSequentialGroup()
                                .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(pnlBotonesCantidad, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnEliminarTempPlato, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jScrollPane12, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jScrollPane11, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pnlMesoneroPedido, javax.swing.GroupLayout.PREFERRED_SIZE, 52, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel23Layout.createSequentialGroup()
                                .addComponent(txtTempIdSala, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(txtTempNumMesa, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel23Layout.createSequentialGroup()
                                .addComponent(jLabel11)
                                .addGap(14, 14, 14)
                                .addComponent(totalMenu)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(btnGenerarPedido, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(39, 39, 39))
                    .addComponent(jPanel24, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Platos", jPanel23);

        jPanel25.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        btnFinalizar.setText("Finalizar");
        btnFinalizar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnFinalizarActionPerformed(evt);
            }
        });
        jPanel25.add(btnFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(1090, 480, 100, 40));

        totalFinalizar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 16)); // NOI18N
        totalFinalizar.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        totalFinalizar.setText("USD $ 0.00  |  Bs. 0.00");
        jPanel25.add(totalFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(910, 390, 120, -1));

        jLabel17.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15)); // NOI18N
        jLabel17.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/money.png"))); // NOI18N
        jLabel17.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jLabel17.setText("Total a pagar (IVA incluido)");
        jPanel25.add(jLabel17, new org.netbeans.lib.awtextra.AbsoluteConstraints(900, 340, -1, -1));

        tableFinalizar.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Plato", "Cant.", "Precio", "Sub-Total", "Comentario"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tableFinalizar.setRowHeight(23);
        jScrollPane13.setViewportView(tableFinalizar);
        if (tableFinalizar.getColumnModel().getColumnCount() > 0) {
            tableFinalizar.getColumnModel().getColumn(0).setMinWidth(30);
            tableFinalizar.getColumnModel().getColumn(0).setPreferredWidth(30);
            tableFinalizar.getColumnModel().getColumn(0).setMaxWidth(50);
            tableFinalizar.getColumnModel().getColumn(1).setPreferredWidth(100);
            tableFinalizar.getColumnModel().getColumn(2).setMinWidth(40);
            tableFinalizar.getColumnModel().getColumn(2).setPreferredWidth(40);
            tableFinalizar.getColumnModel().getColumn(2).setMaxWidth(50);
            tableFinalizar.getColumnModel().getColumn(3).setPreferredWidth(50);
            tableFinalizar.getColumnModel().getColumn(4).setPreferredWidth(60);
        }

        jPanel25.add(jScrollPane13, new org.netbeans.lib.awtextra.AbsoluteConstraints(39, 13, 1030, 316));
        jPanel25.add(txtIdPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(620, 380, 50, -1));

        jLabel7.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel7.setText("Fecha y Hora:");
        jPanel25.add(jLabel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 350, -1, -1));

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel8.setText("Sala:");
        jPanel25.add(jLabel8, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 400, -1, -1));

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel9.setText("N° Mesa:");
        jPanel25.add(jLabel9, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 450, -1, -1));

        txtFechaHora.setEditable(false);
        jPanel25.add(txtFechaHora, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 350, 240, 30));

        txtSalaFinalizar.setEditable(false);
        jPanel25.add(txtSalaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 400, 240, 30));

        txtNumMesaFinalizar.setEditable(false);
        jPanel25.add(txtNumMesaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 450, 240, 30));

        lblMesoneroFin = new javax.swing.JLabel("Mesonero:");
        lblMesoneroFin.setFont(new java.awt.Font("Segoe UI", 1, 14));
        jPanel25.add(lblMesoneroFin, new org.netbeans.lib.awtextra.AbsoluteConstraints(440, 350, -1, -1));

        txtMesoneroFinalizar = new javax.swing.JTextField();
        txtMesoneroFinalizar.setEditable(false);
        txtMesoneroFinalizar.setBackground(new java.awt.Color(255, 255, 255));
        jPanel25.add(txtMesoneroFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(540, 350, 220, 30));

        btnPdfPedido.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/pdf.png"))); // NOI18N
        btnPdfPedido.setToolTipText("Generar y ver el PDF del pedido");
        btnPdfPedido.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPdfPedidoActionPerformed(evt);
            }
        });
        jPanel25.add(btnPdfPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(974, 480, 110, 40));
        
        btnModificarPedido.setText("Agregar Productos");
        btnModificarPedido.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/nuevo.png"))); // Reuse "nuevo.png" if exists or just leave text
        btnModificarPedido.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnModificarPedidoActionPerformed(evt);
            }
        });
        jPanel25.add(btnModificarPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(633, 480, 180, 40));
        jPanel25.add(txtIdHistorialPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(630, 450, 50, -1));

        jTabbedPane1.addTab("Finalizar Pedido", jPanel25);

        jPanel6.setBackground(new java.awt.Color(204, 204, 204));
        jPanel6.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        TablePedidos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Id", "Sala", "Atendido", "N° Mesa", "Fecha", "Total", "Estado"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        TablePedidos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        TablePedidos.setRowHeight(23);
        TablePedidos.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                TablePedidosMouseClicked(evt);
            }
        });
        jScrollPane5.setViewportView(TablePedidos);
        if (TablePedidos.getColumnModel().getColumnCount() > 0) {
            TablePedidos.getColumnModel().getColumn(0).setMinWidth(80);
            TablePedidos.getColumnModel().getColumn(0).setPreferredWidth(80);
            TablePedidos.getColumnModel().getColumn(0).setMaxWidth(120);
            TablePedidos.getColumnModel().getColumn(2).setPreferredWidth(60);
            TablePedidos.getColumnModel().getColumn(3).setMinWidth(100);
            TablePedidos.getColumnModel().getColumn(3).setPreferredWidth(100);
            TablePedidos.getColumnModel().getColumn(3).setMaxWidth(150);
            TablePedidos.getColumnModel().getColumn(4).setPreferredWidth(60);
        }

        jPanel6.add(jScrollPane5, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 90, 1020, 480));

        jLabel16.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel16.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel16.setText("Historial Pedidos");
        jPanel6.add(jLabel16, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 50, 280, -1));

        jTabbedPane1.addTab("Historial Pedidos", jPanel6);

        jPanel7.setBackground(new java.awt.Color(255, 255, 255));
        jPanel7.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel32.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel32.setText("DATOS DE LA EMPRESA");
        jPanel7.add(jLabel32, new org.netbeans.lib.awtextra.AbsoluteConstraints(290, 40, -1, -1));

        jPanel8.setBackground(new java.awt.Color(204, 204, 204));
        jPanel8.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        txtIdConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtIdConfigActionPerformed(evt);
            }
        });
        jPanel8.add(txtIdConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 410, 24, -1));

        jLabel30.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel30.setText("Dirección");
        jPanel8.add(jLabel30, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 160, -1, -1));

        jLabel29.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel29.setText("Teléfono");
        jPanel8.add(jLabel29, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 160, -1, -1));

        txtTelefonoConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtTelefonoConfig.setBorder(null);
        jPanel8.add(txtTelefonoConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 190, 218, 30));

        txtDireccionConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtDireccionConfig.setBorder(null);
        jPanel8.add(txtDireccionConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 190, 147, 30));

        jLabelTasaConfig.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabelTasaConfig.setText("Tasa de Cambio (Bs. / $)");
        jPanel8.add(jLabelTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 230, -1, -1));

        txtTasaConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtTasaConfig.setBorder(null);
        jPanel8.add(txtTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 255, 147, 30));

        jLabel31.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel31.setText("Mensaje");
        jPanel8.add(jLabel31, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 230, -1, -1));

        txtMensaje.setBackground(new java.awt.Color(204, 204, 204));
        txtMensaje.setBorder(null);
        jPanel8.add(txtMensaje, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 255, 220, 30));

        jLabelIvaConfig.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabelIvaConfig.setText("IVA (%)");
        jPanel8.add(jLabelIvaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 302, -1, -1));

        txtIvaConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtIvaConfig.setBorder(null);
        jPanel8.add(txtIvaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 325, 147, 30));

        btnActualizarConfig.setBackground(new java.awt.Color(255, 255, 255));
        btnActualizarConfig.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnActualizarConfig.setText("Modificar");
        btnActualizarConfig.setBorder(null);
        btnActualizarConfig.setFocusable(false);
        btnActualizarConfig.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnActualizarConfig.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnActualizarConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnActualizarConfigActionPerformed(evt);
            }
        });
        jPanel8.add(btnActualizarConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 405, 220, 50));

        jLabel27.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel27.setText("Ruc");
        jPanel8.add(jLabel27, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, -1, -1));

        txtRucConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtRucConfig.setBorder(null);
        jPanel8.add(txtRucConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 60, 147, 30));

        jLabel28.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel28.setText("Nombre");
        jPanel8.add(jLabel28, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 30, -1, -1));

        txtNombreConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtNombreConfig.setBorder(null);
        jPanel8.add(txtNombreConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 60, 220, 30));

        jPanel41.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel41Layout = new javax.swing.GroupLayout(jPanel41);
        jPanel41.setLayout(jPanel41Layout);
        jPanel41Layout.setHorizontalGroup(
            jPanel41Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel41Layout.setVerticalGroup(
            jPanel41Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel8.add(jPanel41, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 90, 147, 2));

        jPanel42.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel42Layout = new javax.swing.GroupLayout(jPanel42);
        jPanel42.setLayout(jPanel42Layout);
        jPanel42Layout.setHorizontalGroup(
            jPanel42Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel42Layout.setVerticalGroup(
            jPanel42Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel8.add(jPanel42, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 220, 147, 2));

        jPanel43.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel43Layout = new javax.swing.GroupLayout(jPanel43);
        jPanel43.setLayout(jPanel43Layout);
        jPanel43Layout.setHorizontalGroup(
            jPanel43Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel43Layout.setVerticalGroup(
            jPanel43Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel8.add(jPanel43, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 357, 400, 2));

        jPanel44.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel44Layout = new javax.swing.GroupLayout(jPanel44);
        jPanel44.setLayout(jPanel44Layout);
        jPanel44Layout.setHorizontalGroup(
            jPanel44Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel44Layout.setVerticalGroup(
            jPanel44Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel8.add(jPanel44, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 90, 220, 2));

        jPanel45.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel45Layout = new javax.swing.GroupLayout(jPanel45);
        jPanel45.setLayout(jPanel45Layout);
        jPanel45Layout.setHorizontalGroup(
            jPanel45Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel45Layout.setVerticalGroup(
            jPanel45Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel8.add(jPanel45, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 220, 220, 2));

        jPanel7.add(jPanel8, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 100, 420, 470));

        jLabel21.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel21.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/portada.png"))); // NOI18N
        jPanel7.add(jLabel21, new org.netbeans.lib.awtextra.AbsoluteConstraints(440, 100, 620, 470));

        javax.swing.GroupLayout jPanel40Layout = new javax.swing.GroupLayout(jPanel40);
        jPanel40.setLayout(jPanel40Layout);
        jPanel40Layout.setHorizontalGroup(
            jPanel40Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel40Layout.setVerticalGroup(
            jPanel40Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 10, Short.MAX_VALUE)
        );

        jPanel7.add(jPanel40, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 160, -1, 10));

        jTabbedPane1.addTab("Datos de la Empresa", jPanel7);

        jPanel12.setLayout(new java.awt.BorderLayout(15, 0));
        jPanel12.setBorder(javax.swing.BorderFactory.createEmptyBorder(25, 30, 25, 30));

        TableUsuarios.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Id", "Nombre", "Correo", "Rol", "Estado"
            }
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        TableUsuarios.setRowHeight(23);
        jScrollPane6.setViewportView(TableUsuarios);
        if (TableUsuarios.getColumnModel().getColumnCount() > 0) {
            TableUsuarios.getColumnModel().getColumn(0).setMinWidth(50);
            TableUsuarios.getColumnModel().getColumn(0).setPreferredWidth(60);
            TableUsuarios.getColumnModel().getColumn(0).setMaxWidth(80);
            TableUsuarios.getColumnModel().getColumn(3).setMinWidth(110);
            TableUsuarios.getColumnModel().getColumn(3).setPreferredWidth(130);
            TableUsuarios.getColumnModel().getColumn(3).setMaxWidth(160);
            TableUsuarios.getColumnModel().getColumn(4).setMinWidth(90);
            TableUsuarios.getColumnModel().getColumn(4).setPreferredWidth(100);
            TableUsuarios.getColumnModel().getColumn(4).setMaxWidth(120);
        }

        javax.swing.JPanel panelTablaUsuariosConAcciones = new javax.swing.JPanel(new java.awt.BorderLayout(0, 10));
        panelTablaUsuariosConAcciones.setOpaque(false);
        panelTablaUsuariosConAcciones.add(jScrollPane6, java.awt.BorderLayout.CENTER);

        javax.swing.JPanel barraAccionesUsuarios = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 5));
        barraAccionesUsuarios.setOpaque(false);

        btnEstadoUsuario = new javax.swing.JButton("Desactivar");
        btnEstadoUsuario.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnEstadoUsuario.setEnabled(false);
        btnEstadoUsuario.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnEstadoUsuario.setToolTipText("Desactiva o reactiva la cuenta de usuario seleccionada");
        btnEstadoUsuario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alternarEstadoUsuarioSeleccionado();
            }
        });

        btnEliminarUsuario = new javax.swing.JButton("Eliminar");
        btnEliminarUsuario.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnEliminarUsuario.setForeground(java.awt.Color.WHITE);
        btnEliminarUsuario.setBackground(COLOR_PELIGRO);
        btnEliminarUsuario.setContentAreaFilled(true);
        btnEliminarUsuario.setOpaque(true);
        btnEliminarUsuario.setEnabled(false);
        btnEliminarUsuario.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnEliminarUsuario.setToolTipText("Elimina permanentemente la cuenta de usuario seleccionada");
        btnEliminarUsuario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                eliminarUsuarioSeleccionado();
            }
        });

        btnCambiarClaveUsuario = new javax.swing.JButton("Cambiar contraseña");
        btnCambiarClaveUsuario.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnCambiarClaveUsuario.setEnabled(false);
        btnCambiarClaveUsuario.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnCambiarClaveUsuario.setToolTipText("Cambia la contraseña de la cuenta seleccionada");
        btnCambiarClaveUsuario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cambiarContrasenaUsuarioSeleccionado();
            }
        });

        barraAccionesUsuarios.add(btnCambiarClaveUsuario);
        barraAccionesUsuarios.add(btnEstadoUsuario);
        barraAccionesUsuarios.add(btnEliminarUsuario);
        panelTablaUsuariosConAcciones.add(barraAccionesUsuarios, java.awt.BorderLayout.SOUTH);

        jPanel12.add(panelTablaUsuariosConAcciones, java.awt.BorderLayout.CENTER);

        jPanel15.setBackground(new java.awt.Color(204, 204, 204));
        jPanel15.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel34.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel34.setText("Correo Electrónico");
        jPanel15.add(jLabel34, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 102, -1, -1));

        jLabel35.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel35.setText("Password");
        jPanel15.add(jLabel35, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 180, 130, -1));

        txtCorreo.setBackground(new java.awt.Color(204, 204, 204));
        txtCorreo.setBorder(null);
        txtCorreo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtCorreoActionPerformed(evt);
            }
        });
        jPanel15.add(txtCorreo, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 126, 300, 30));

        txtPass.setBackground(new java.awt.Color(204, 204, 204));
        txtPass.setBorder(null);
        jPanel15.add(ControlVisibilidadPassword.crearPanelPassword(txtPass, new java.awt.Color(204, 204, 204)), new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 200, 300, 30));

        btnIniciar.setBackground(new java.awt.Color(0, 0, 0));
        btnIniciar.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnIniciar.setForeground(new java.awt.Color(255, 255, 255));
        btnIniciar.setText("Registrar");
        btnIniciar.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnIniciar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIniciarActionPerformed(evt);
            }
        });
        jPanel15.add(btnIniciar, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 440, 300, 50));

        jLabel36.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel36.setText("Nombre:");
        jPanel15.add(jLabel36, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 280, -1, -1));

        txtNombre.setBackground(new java.awt.Color(204, 204, 204));
        txtNombre.setBorder(null);
        jPanel15.add(txtNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 300, 300, 30));

        jLabel37.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel37.setText("Rol:");
        jPanel15.add(jLabel37, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 360, 90, -1));

        cbxRol.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Administrador", "Asistente" }));
        jPanel15.add(cbxRol, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 380, 300, 30));

        jPanel16.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 2, Short.MAX_VALUE)
        );

        jPanel15.add(jPanel16, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 156, 300, 2));

        jPanel17.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 2, Short.MAX_VALUE)
        );

        jPanel15.add(jPanel17, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 230, 300, 2));

        jPanel18.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 2, Short.MAX_VALUE)
        );

        jPanel15.add(jPanel18, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 330, 300, 2));

        jPanel21.setBackground(new java.awt.Color(0, 0, 0));
        jPanel21.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel39.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel39.setForeground(new java.awt.Color(255, 255, 255));
        jLabel39.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel39.setText("Nuevo Usuario");
        jPanel21.add(jLabel39, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 35));

        jPanel15.add(jPanel21, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 360, 35));

        jPanel12.add(jPanel15, java.awt.BorderLayout.WEST);

        jTabbedPane1.addTab("Usuarios", jPanel12);

        jPanel11.setBackground(new java.awt.Color(204, 204, 204));
        jPanel11.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel23.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel23.setText("Nombre:");
        jPanel11.add(jLabel23, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 110, -1, -1));

        txtNombrePlato.setBackground(new java.awt.Color(204, 204, 204));
        txtNombrePlato.setBorder(null);
        jPanel11.add(txtNombrePlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 100, 170, 30));

        jLabel25.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel25.setText("Precio:");
        jPanel11.add(jLabel25, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 180, -1, -1));

        txtPrecioPlato.setBackground(new java.awt.Color(204, 204, 204));
        txtPrecioPlato.setBorder(null);
        txtPrecioPlato.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                txtPrecioPlatoKeyTyped(evt);
            }
        });
        jPanel11.add(txtPrecioPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 170, 170, 30));

        btnGuardarPlato.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/GuardarTodo.png"))); // NOI18N
        btnGuardarPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGuardarPlatoActionPerformed(evt);
            }
        });
        jPanel11.add(btnGuardarPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 270, 110, 50));

        btnEditarPlato.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/Actualizar (2).png"))); // NOI18N
        btnEditarPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEditarPlatoActionPerformed(evt);
            }
        });
        jPanel11.add(btnEditarPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(167, 270, 100, 50));

        btnEliminarPlato.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/eliminar.png"))); // NOI18N
        btnEliminarPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEliminarPlatoActionPerformed(evt);
            }
        });
        jPanel11.add(btnEliminarPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 370, 130, 50));

        btnNuevoPlato.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/nuevo.png"))); // NOI18N
        btnNuevoPlato.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNuevoPlatoActionPerformed(evt);
            }
        });
        jPanel11.add(btnNuevoPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(170, 370, 100, 50));

        jPanel31.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel31Layout = new javax.swing.GroupLayout(jPanel31);
        jPanel31.setLayout(jPanel31Layout);
        jPanel31Layout.setHorizontalGroup(
            jPanel31Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel31Layout.setVerticalGroup(
            jPanel31Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel11.add(jPanel31, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 130, 170, 2));

        jPanel33.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jPanel33Layout = new javax.swing.GroupLayout(jPanel33);
        jPanel33.setLayout(jPanel33Layout);
        jPanel33Layout.setHorizontalGroup(
            jPanel33Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel33Layout.setVerticalGroup(
            jPanel33Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jPanel11.add(jPanel33, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 200, 170, 2));

        jPanel39.setBackground(new java.awt.Color(0, 0, 0));

        jLabel40.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel40.setForeground(new java.awt.Color(255, 255, 255));
        jLabel40.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel40.setText("Platos del Día");

        javax.swing.GroupLayout jPanel39Layout = new javax.swing.GroupLayout(jPanel39);
        jPanel39.setLayout(jPanel39Layout);
        jPanel39Layout.setHorizontalGroup(
            jPanel39Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabel40, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
        );
        jPanel39Layout.setVerticalGroup(
            jPanel39Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel39Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel40, javax.swing.GroupLayout.DEFAULT_SIZE, 24, Short.MAX_VALUE)
                .addContainerGap())
        );

        jPanel11.add(jPanel39, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 290, 50));
        jPanel11.add(txtIdPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 470, 80, -1));

        TablePlatos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "DESCRIPCIÓN", "PRECIO"
            }
        ));
        TablePlatos.setRowHeight(23);
        TablePlatos.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                TablePlatosMouseClicked(evt);
            }
        });
        jScrollPane4.setViewportView(TablePlatos);
        if (TablePlatos.getColumnModel().getColumnCount() > 0) {
            TablePlatos.getColumnModel().getColumn(0).setMinWidth(100);
            TablePlatos.getColumnModel().getColumn(0).setPreferredWidth(100);
            TablePlatos.getColumnModel().getColumn(0).setMaxWidth(150);
            TablePlatos.getColumnModel().getColumn(2).setMinWidth(200);
            TablePlatos.getColumnModel().getColumn(2).setPreferredWidth(200);
            TablePlatos.getColumnModel().getColumn(2).setMaxWidth(300);
        }

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, 289, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(30, 30, 30)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 718, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane4)
                    .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, 543, Short.MAX_VALUE))
                .addContainerGap(22, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Platos", jPanel2);

        getContentPane().add(jTabbedPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(150, 95, 1080, 620));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.CONSULTAR_SALAS)) return;
        LimpiarTableMenu();
        ListarSalas();
        jTabbedPane1.setSelectedIndex(1);
    }//GEN-LAST:event_btnSalaActionPerformed

    private void btnConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConfigActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        ListarConfig();
        jTabbedPane1.setSelectedIndex(6);
    }//GEN-LAST:event_btnConfigActionPerformed

    private void btnVentasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnVentasActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        ListarPedidos();
        jTabbedPane1.setSelectedIndex(5);
    }//GEN-LAST:event_btnVentasActionPerformed

    private void btnUsuariosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUsuariosActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        ListarUsuarios();
        jTabbedPane1.setSelectedIndex(7);
    }//GEN-LAST:event_btnUsuariosActionPerformed

    private void btnActualizarConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnActualizarConfigActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        if (!txtRucConfig.getText().trim().isEmpty()
                && !txtNombreConfig.getText().trim().isEmpty()
                && !txtTelefonoConfig.getText().trim().isEmpty()
                && !txtDireccionConfig.getText().trim().isEmpty()) {
            Config candidata = copiarConfiguracion(conf);
            candidata.setRif(txtRucConfig.getText().trim());
            candidata.setNombre(txtNombreConfig.getText().trim());
            candidata.setTelefono(txtTelefonoConfig.getText().trim());
            candidata.setDireccion(txtDireccionConfig.getText().trim());
            candidata.setMensaje(txtMensaje.getText().trim());
            if (txtTasaConfig == null || txtTasaConfig.getText().trim().isEmpty()) {
                mostrarAlertaCampo(txtTasaConfig, "La tasa de cambio oficial es obligatoria.");
                return;
            }
            try {
                BigDecimal tasa = new BigDecimal(txtTasaConfig.getText().trim());
                if (tasa.compareTo(BigDecimal.ZERO) <= 0 || tasa.scale() > 4 || tasa.precision() - tasa.scale() > 8) {
                    mostrarAlertaCampo(txtTasaConfig, "La tasa debe ser positiva y tener hasta cuatro decimales.");
                    return;
                }
                candidata.setTasaDolar(tasa);
            } catch (NumberFormatException ex) {
                mostrarAlertaCampo(txtTasaConfig, "Ingresa una tasa de cambio numérica válida.");
                return;
            }
            if (txtIvaConfig == null || txtIvaConfig.getText().trim().isEmpty()) {
                mostrarAlertaCampo(txtIvaConfig, "El porcentaje de IVA es obligatorio.");
                return;
            }
            try {
                BigDecimal iva = new BigDecimal(txtIvaConfig.getText().trim());
                if (iva.compareTo(BigDecimal.ZERO) < 0 || iva.compareTo(new BigDecimal("100.00")) > 0 || iva.scale() > 2) {
                    mostrarAlertaCampo(txtIvaConfig, "El IVA debe estar entre 0 y 100%, con hasta dos decimales.");
                    return;
                }
                candidata.setIvaPorcentaje(iva);
            } catch (NumberFormatException ex) {
                mostrarAlertaCampo(txtIvaConfig, "Ingresa un porcentaje de IVA numérico válido.");
                return;
            }
            if (txtRutaLogo != null) {
                String rutaLogo = txtRutaLogo.getText().trim();
                candidata.setLogoPath(rutaLogo.isEmpty() ? null : rutaLogo);
            }
            if (chkImprimirLogoTicket != null) {
                candidata.setImprimirLogoTicket(chkImprimirLogoTicket.isSelected());
            }
            if (txtClienteDefaultNombre != null && !txtClienteDefaultNombre.getText().trim().isEmpty()) {
                candidata.setClienteDefaultNombre(txtClienteDefaultNombre.getText().trim());
            }
            if (txtClienteDefaultDoc != null && !txtClienteDefaultDoc.getText().trim().isEmpty()) {
                candidata.setClienteDefaultDocumento(txtClienteDefaultDoc.getText().trim());
            }
            if (spMesesRetencion != null) {
                try {
                    int meses = (Integer) spMesesRetencion.getValue();
                    if (meses < 1) {
                        mostrarAlertaCampo(spMesesRetencion, "El período de retención debe ser de al menos 1 mes.");
                        return;
                    }
                    candidata.setMesesRetencionPedidos(meses);
                } catch (Exception ex) {
                    mostrarAlertaCampo(spMesesRetencion, "Indica un período de retención válido.");
                    return;
                }
            }
            try {
                String idTxt = txtIdConfig.getText().trim();
                candidata.setId(!idTxt.isEmpty() ? Integer.parseInt(idTxt) : (candidata.getId() > 0 ? candidata.getId() : 1));
            } catch (NumberFormatException ex) {
                candidata.setId(candidata.getId() > 0 ? candidata.getId() : 1);
            }
            if (cbImpresorasConfig != null && cbImpresorasConfig.getSelectedItem() != null) {
                String impSeleccionada = cbImpresorasConfig.getSelectedItem().toString();
                candidata.setImpresoraTickets(impSeleccionada);
            }
            if (cbModoSalidaConfig != null && cbModoSalidaConfig.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                candidata.setModoSalidaTickets(m);
            }
            candidata.setModoAtencion(modoAtencionActual);
            try {
                boolean tasaCambio = candidata.getTasaDolar() != null && (
                        conf == null || conf.getTasaDolar() == null
                        || conf.getTasaDolar().compareTo(candidata.getTasaDolar()) != 0
                );
                Modelo.ResultadoActualizacionPrecios resTasa = null;
                if (tasaCambio) {
                    int estimados = configServicio.contarPedidosAbiertosAfectadosPorTasa();
                    int confirmacion = JOptionPane.showConfirmDialog(
                            this,
                            "La nueva tasa de cambio (" + candidata.getTasaDolar().toPlainString() + ") recalculará "
                                    + (estimados > 0 ? "aproximadamente " + estimados + " pedido(s) abierto(s)" : "los pedidos abiertos")
                                    + " pendientes de cobro.\n¿Desea continuar con la actualización?",
                            "Confirmar cambio de tasa global",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (confirmacion != JOptionPane.YES_OPTION) {
                        return;
                    }
                    try {
                        resTasa = configServicio.actualizarTasaYRecalcularPendientes(candidata.getTasaDolar(), getNombreUsuarioActual());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this,
                                "La operación fue completamente revertida. No se aplicaron cambios a la tasa ni a los pedidos:\n"
                                        + (ex.getMessage() != null ? ex.getMessage() : ex.toString()),
                                "Error en actualización de tasa", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                if (lgDao.ModificarDatos(candidata) || resTasa != null) {
                    conf = candidata;
                    actualizarNombreRestaurante(conf.getNombre());
                    actualizarLogoEIcono(conf.getLogoPath());
                    Servicio.ServicioImpresionTicket.setImpresoraGlobal(conf.getImpresoraTickets());
                    cambiarModoSalidaGlobal(conf.getModoSalidaTickets());
                    actualizarEstadoTasaUI();
                    actualizarTextoCheckboxIvaPlato();
                    if (resTasa != null) {
                        RefrescoVistas.notificar(RefrescoVistas.PLATOS, resTasa);
                        RefrescoVistas.notificar(RefrescoVistas.PEDIDOS, resTasa);
                    }
                    versionCargaConfig++;
                    String aviso = "Configuración guardada";
                    if (resTasa != null && resTasa.pedidosRecalculados() > 0) {
                        aviso += " (Recalculados " + resTasa.pedidosRecalculados() + " pedidos abiertos con la nueva tasa).";
                    }
                    mostrarAvisoEnBanda(aviso);
                } else {
                    mostrarAvisoEnBanda("No había cambios en la configuración");
                }
            } catch (Exception ex) {
                String mensaje = (ex instanceof ErrorAplicacionException || ex instanceof DataAccessException)
                        ? ex.getMessage() : "No se pudo actualizar la configuración: " + ex.getMessage();
                JOptionPane.showMessageDialog(this, mensaje, "Error al guardar configuración", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            if (txtRucConfig.getText().trim().isEmpty()) mostrarAlertaCampo(txtRucConfig, "El RIF es obligatorio.");
            else if (txtNombreConfig.getText().trim().isEmpty()) mostrarAlertaCampo(txtNombreConfig, "El nombre de la empresa es obligatorio.");
            else if (txtTelefonoConfig.getText().trim().isEmpty()) mostrarAlertaCampo(txtTelefonoConfig, "El teléfono es obligatorio.");
            else mostrarAlertaCampo(txtDireccionConfig, "La dirección es obligatoria.");
        }
    }//GEN-LAST:event_btnActualizarConfigActionPerformed

    private Config copiarConfiguracion(Config original) {
        Config copia = new Config(original.getId(), original.getRuc(), original.getNombre(),
                original.getTelefono(), original.getDireccion(), original.getMensaje(),
                original.getTasaDolar(), original.getIvaPorcentaje(), original.getLogoPath());
        copia.setClienteDefaultNombre(original.getClienteDefaultNombre());
        copia.setClienteDefaultDocumento(original.getClienteDefaultDocumento());
        copia.setMesesRetencionPedidos(original.getMesesRetencionPedidos());
        copia.setImprimirLogoTicket(original.isImprimirLogoTicket());
        copia.setImpresoraTickets(original.getImpresoraTickets());
        copia.setModoSalidaTickets(original.getModoSalidaTickets());
        copia.setModoAtencion(original.getModoAtencion());
        return copia;
    }

    private void btnPdfPedidoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPdfPedidoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;

        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            idStr = txtIdPedido.getText().trim();
        }
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Selecciona una fila");
        } else {
            try {
                int id = Integer.parseInt(idStr);
                String motivo = solicitarMotivoAccion("Reimpresión de Factura",
                        "Ingrese el motivo de la reimpresión del pedido #" + id + ":", "Copia para el cliente");
                if (motivo == null) return;
                String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
                boolean impreso = pedidosControlador.reimprimirPdfPedido(id, motivo, usr);
                if (!impreso) {
                    mostrarAvisoEnBanda("PDF generado; impresión abierta en visor PDF");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnPdfPedidoActionPerformed

    private void btnModificarPedidoActionPerformed(java.awt.event.ActionEvent evt) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        if (ped == null || ped.getId() <= 0) {
            mostrarAvisoEnBanda("Selecciona un pedido para modificar");
            return;
        }

        final int idPedMod = ped.getId();
        final int idSalaMod = ped.getId_sala();
        final int numMesaMod = ped.getNum_mesa();
        final String nombreSalaMod = ped.getSala();

        vaciarCarritoVisual();
        idPedidoEdicion = idPedMod;
        idSalaEdicion = idSalaMod;
        numMesaEdicion = numMesaMod;
        txtTempIdSala.setText(String.valueOf(idSalaMod));
        txtTempNumMesa.setText(String.valueOf(numMesaMod));

        jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)),
                "MODIFICANDO PEDIDO N° " + idPedMod + " - " + (nombreSalaMod != null ? nombreSalaMod : "") + " | MESA " + numMesaMod,
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14),
                new java.awt.Color(0, 102, 102)));

        btnGenerarPedido.setText("Actualizar Pedido");
        jTabbedPane1.setSelectedIndex(3);
        txtBuscarPlato.requestFocusInWindow();

        DefaultTableModel tmp = (DefaultTableModel) tableMenu.getModel();
        boolean tieneColumnaIva = tmp.getColumnCount() >= 7;

        try {
            java.util.List<Modelo.DetallePedido> detalles = pedidosControlador.verPedidoDetalle(idPedMod);

            for (Modelo.DetallePedido det : detalles) {
                String nombre = det.getNombre();
                int idPlato = 0;
                boolean aplicaIva = det.getAplicaIva() != null ? det.getAplicaIva() : true;
                for (int k = 0; k < tblTemPlatos.getRowCount(); k++) {
                    if (Tables.valorModelo(tblTemPlatos, k, 1).toString().equalsIgnoreCase(nombre)) {
                        idPlato = Integer.parseInt(Tables.valorModelo(tblTemPlatos, k, 0).toString());
                        if (det.getAplicaIva() == null) {
                            aplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                        }
                        break;
                    }
                }
                Object[] O = new Object[tieneColumnaIva ? 7 : 6];
                O[0] = idPlato > 0 ? idPlato : ""; 
                O[1] = nombre;
                O[2] = det.getCantidad();
                java.math.BigDecimal precio = det.getPrecioDecimal().setScale(2, java.math.RoundingMode.HALF_UP);
                String monedaDet = (det.getMonedaBase() != null && !det.getMonedaBase().isBlank())
                        ? det.getMonedaBase()
                        : "USD";
                BigDecimal tasaActual = getTasaActual();
                java.math.BigDecimal subtotal = precio.multiply(new java.math.BigDecimal(det.getCantidad())).setScale(2, java.math.RoundingMode.HALF_UP);
                O[3] = formatearMontoCelda(precio, monedaDet, tasaActual);
                O[4] = formatearMontoCelda(subtotal, monedaDet, tasaActual);
                O[5] = det.getComentario();
                if (tieneColumnaIva) {
                    BigDecimal ivaRef = (ped != null && ped.getIvaPorcentaje() != null)
                            ? ped.getIvaPorcentaje()
                            : ((conf != null && conf.getIvaPorcentaje() != null) ? conf.getIvaPorcentaje() : null);
                    O[6] = formatearEtiquetaIva(aplicaIva, ivaRef);
                }
                tmp.addRow(O);
            }
            TotalPagar(tableMenu, totalMenu);
            actualizarEstadoBotonesCarrito();

            if (ped.getIdMesonero() != null && ped.getIdMesonero() > 0) {
                boolean encontrado = false;
                for (int i = 0; i < cbMesoneroPedido.getItemCount(); i++) {
                    Object mObj = cbMesoneroPedido.getItemAt(i);
                    if (mObj instanceof Modelo.Mesonero m) {
                        if (m.getId() == ped.getIdMesonero()) {
                            cbMesoneroPedido.setSelectedIndex(i);
                            encontrado = true;
                            break;
                        }
                    }
                }
                if (encontrado) {
                    idMesoneroEdicionOriginal = ped.getIdMesonero();
                    nombreMesoneroEdicionOriginal = ped.getMesoneroNombre();
                    quitarMesoneroEdicion = false;
                } else {
                    idMesoneroEdicionOriginal = ped.getIdMesonero();
                    nombreMesoneroEdicionOriginal = ped.getMesoneroNombre();
                    cbMesoneroPedido.setSelectedIndex(0);
                    quitarMesoneroEdicion = false;
                    mostrarAvisoEnBanda("El mesonero asignado ('"
                            + (nombreMesoneroEdicionOriginal != null ? nombreMesoneroEdicionOriginal : ("#" + idMesoneroEdicionOriginal))
                            + "') ya no está activo; seleccione uno activo, use Quitar para liberarlo o se conservará la asignación.");
                }
            } else {
                idMesoneroEdicionOriginal = null;
                nombreMesoneroEdicionOriginal = null;
                quitarMesoneroEdicion = false;
            }
        } catch (Exception ex) {
            LimpiarTableMenu();
            mostrarAvisoEnBanda("Error al cargar detalles del pedido #" + idPedMod + ": " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "No se pudieron cargar los detalles del pedido #" + idPedMod + " para edición:\n" + ex.getMessage(),
                    "Error de Carga", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void btnPrevisualizarPedidoActionPerformed(java.awt.event.ActionEvent evt) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;

        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            idStr = txtIdPedido.getText().trim();
        }
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un pedido para previsualizar");
            return;
        }
        try {
            int id = Integer.parseInt(idStr);
            pedidosControlador.previsualizarPdfPedido(id);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo previsualizar el PDF: " + mensajeError(ex),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void TablePedidosMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_TablePedidosMouseClicked
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        int fila = TablePedidos.rowAtPoint(evt.getPoint());
        if (fila < 0) {
            return;
        }
        int id_pedido = Integer.parseInt(Tables.valorModelo(TablePedidos, fila, 0).toString());
        cargarPedidoEnPantalla(id_pedido, false);
    }//GEN-LAST:event_TablePedidosMouseClicked

    private void tableSalaMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tableSalaMouseClicked
        int fila = tableSala.rowAtPoint(evt.getPoint());
        if (fila < 0) {
            return;
        }
        txtIdSala.setText(Tables.valorModelo(tableSala, fila, 0).toString());
        txtNombreSala.setText(Tables.valorModelo(tableSala, fila, 1).toString());
        txtMesas.setText(Tables.valorModelo(tableSala, fila, 2).toString());
        try {
            int idSala = Integer.parseInt(Tables.valorModelo(tableSala, fila, 0).toString());
            String tipo = salaTipoMap.getOrDefault(idSala, "SALON");
            if (tableSala.getModel().getColumnCount() > 3 && Tables.valorModelo(tableSala, fila, 3) != null) {
                tipo = Tables.valorModelo(tableSala, fila, 3).toString();
            }
            boolean esBarra = "BARRA".equalsIgnoreCase(tipo);
            if (cbTipoSala != null) {
                cbTipoSala.setSelectedItem(esBarra ? "BARRA" : "SALÓN");
            }
            if (jLabel19 != null) {
                jLabel19.setText(esBarra ? "Puestos:" : "Mesas:");
            }
        } catch (Exception ignored) {}
    }//GEN-LAST:event_tableSalaMouseClicked

    private void txtIdConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtIdConfigActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtIdConfigActionPerformed

    private void btnRegistrarSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRegistrarSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        if (txtNombreSala.getText().trim().isEmpty() || txtMesas.getText().trim().isEmpty()) {
            if (txtNombreSala.getText().trim().isEmpty()) mostrarAlertaCampo(txtNombreSala, "El nombre de la sala es obligatorio.");
            else mostrarAlertaCampo(txtMesas, "El número de mesas es obligatorio.");
        } else {
            try {
                sl.setNombre(txtNombreSala.getText().trim());
                sl.setMesas(Integer.parseInt(txtMesas.getText().trim()));
                if (cbTipoSala != null) {
                    sl.setTipo(String.valueOf(cbTipoSala.getSelectedItem()));
                }
                sl.validarParaGuardar();
                if (salasControlador.registrar(sl)) {
                    LimpiarSala();
                    ListarSalas();
                    RefrescoVistas.notificar(RefrescoVistas.SALAS);
                    mostrarAvisoEnBanda("Sala registrada");
                } else {
                    mostrarAvisoEnBanda("No se registró la sala");
                }
            } catch (NumberFormatException ex) {
                mostrarAlertaCampo(txtMesas, "Indica el número de mesas como un entero válido.");
            } catch (Modelo.DataAccessException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error al registrar sala", JOptionPane.ERROR_MESSAGE);
            } catch (ErrorAplicacionException ex) {
                mostrarAlertaCampo(ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("nombre")
                        ? txtNombreSala : txtMesas, ex.getMessage());
            }
        }
    }//GEN-LAST:event_btnRegistrarSalaActionPerformed

    private void btnActualizarSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnActualizarSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        if ("".equals(txtIdSala.getText())) {
            mostrarAvisoEnBanda("Seleccione una sala de la tabla");
        } else {
            if (!txtNombreSala.getText().trim().isEmpty()) {
                try {
                    sl.setNombre(txtNombreSala.getText().trim());
                    sl.setId(Integer.parseInt(txtIdSala.getText().trim()));
                    if (!txtMesas.getText().trim().isEmpty()) {
                        sl.setMesas(Integer.parseInt(txtMesas.getText().trim()));
                    }
                    if (cbTipoSala != null) {
                        sl.setTipo(String.valueOf(cbTipoSala.getSelectedItem()));
                    }
                    sl.validarParaGuardar();
                    if (salasControlador.modificar(sl)) {
                        LimpiarSala();
                        ListarSalas();
                        RefrescoVistas.notificar(RefrescoVistas.SALAS);
                        mostrarAvisoEnBanda("Sala modificada");
                    } else {
                        mostrarAvisoEnBanda("No se aplicaron cambios a la sala");
                    }
                } catch (NumberFormatException ex) {
                    mostrarAlertaCampo(txtMesas, "El número de mesas debe ser un entero válido.");
                } catch (Modelo.DataAccessException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error al actualizar sala", JOptionPane.ERROR_MESSAGE);
                } catch (ErrorAplicacionException ex) {
                    mostrarAlertaCampo(ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("nombre")
                            ? txtNombreSala : txtMesas, ex.getMessage());
                }
            } else {
                mostrarAlertaCampo(txtNombreSala, "El nombre de la sala es obligatorio.");
            }
        }
    }//NOI18N:event_btnActualizarSalaActionPerformed

    private void btnNuevoSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNuevoSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        // TODO add your handling code here:
        LimpiarSala();
    }//GEN-LAST:event_btnNuevoSalaActionPerformed

    private void btnEliminarSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        if (!"".equals(txtIdSala.getText())) {
            try {
                int id = Integer.parseInt(txtIdSala.getText().trim());
                int confirmacion = JOptionPane.showConfirmDialog(this,
                        "¿Eliminar la sala '" + txtNombreSala.getText().trim() + "'? Esta acción no se puede deshacer.",
                        "Confirmar eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirmacion != JOptionPane.YES_OPTION) return;
                if (salasControlador.eliminar(id)) {
                    LimpiarSala();
                    ListarSalas();
                    RefrescoVistas.notificar(RefrescoVistas.SALAS);
                    mostrarAvisoEnBanda("Sala eliminada");
                } else {
                    mostrarAvisoEnBanda("No se eliminó la sala");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Identificador de sala inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            mostrarAvisoEnBanda("Seleccione una fila de la tabla");
        }
    }//GEN-LAST:event_btnEliminarSalaActionPerformed

    private void txtCorreoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtCorreoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtCorreoActionPerformed

    private void btnIniciarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIniciarActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        char[] passChars = txtPass.getPassword();
        try {
            if (txtNombre.getText().trim().isEmpty() || txtCorreo.getText().trim().isEmpty() || passChars.length == 0 || LoginDao.esBlanco(passChars)) {
                if (txtNombre.getText().trim().isEmpty()) mostrarAlertaCampo(txtNombre, "El nombre es obligatorio.");
                else if (txtCorreo.getText().trim().isEmpty()) mostrarAlertaCampo(txtCorreo, "El correo es obligatorio.");
                else mostrarAlertaCampo(txtPass, "La contraseña es obligatoria.");
                return;
            }
            if (LoginDao.contarLongitudSinEspacios(passChars) < LoginDao.LONGITUD_MINIMA_CONTRASENA) {
                mostrarAlertaCampo(txtPass, "La contraseña debe tener al menos " + LoginDao.LONGITUD_MINIMA_CONTRASENA + " caracteres.");
                return;
            }
            Usuario lg = new Usuario();
            String correo;
            try {
                correo = LoginDao.normalizarCorreoRegistro(txtCorreo.getText());
            } catch (ErrorAplicacionException ex) {
                mostrarAlertaCampo(txtCorreo, ex.getMessage());
                return;
            }
            String pass = new String(passChars);
            String nom = txtNombre.getText();
            String rol = cbxRol.getSelectedItem().toString();
            lg.setNombre(nom);
            lg.setCorreo(correo);
            lg.setPassword(pass);
            lg.setRol(rol);
            try {
                if (lgDao.Registrar(lg)) {
                    mostrarAvisoEnBanda("Usuario registrado");
                    ListarUsuarios();
                    RefrescoVistas.notificar(RefrescoVistas.USUARIOS);
                    txtPass.setText("");
                    txtNombre.setText("");
                    txtCorreo.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "No se pudo registrar el usuario.", "Error al registrar usuario", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Modelo.DataAccessException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error al registrar usuario", JOptionPane.ERROR_MESSAGE);
            } catch (ErrorAplicacionException ex) {
                String mensaje = ex.getMessage() != null ? ex.getMessage() : "No se pudo validar el usuario.";
                String lower = mensaje.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("nombre")) {
                    mostrarAlertaCampo(txtNombre, mensaje);
                } else if (lower.contains("contraseña") || lower.contains("clave")) {
                    mostrarAlertaCampo(txtPass, mensaje);
                } else {
                    mostrarAlertaCampo(txtCorreo, mensaje);
                }
            }
        } finally {
            java.util.Arrays.fill(passChars, '\0');
        }
    }//GEN-LAST:event_btnIniciarActionPerformed

    private void labelLogoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_labelLogoMouseClicked
                LimpiarTableMenu();
                jTabbedPane1.setSelectedIndex(0);
                RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
                RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
        panelSalas();
    }//GEN-LAST:event_labelLogoMouseClicked

    private void txtBuscarPlatoKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtBuscarPlatoKeyReleased
        ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
        if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
            // A1: Enter agrega la fila SELECCIONADA (nunca se fuerza la primera).
            int fila = tblTemPlatos.getSelectedRow();
            if (fila >= 0 && fila < tblTemPlatos.getRowCount()) {
                btnAddPlato.doClick();
                // Animación visual rápida de feedback (Verde) en la tabla
                tblTemPlatos.setSelectionBackground(new java.awt.Color(144, 238, 144));
                new javax.swing.Timer(300, e -> tblTemPlatos.setSelectionBackground(javax.swing.UIManager.getColor("Table.selectionBackground"))).start();
            }
        }
    }//GEN-LAST:event_txtBuscarPlatoKeyReleased

    private void btnAddPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddPlatoActionPerformed
        if (tblTemPlatos.getSelectedRow() >= 0) {
            int filaSel = tblTemPlatos.getSelectedRow();
            int id = Integer.parseInt(Tables.valorModelo(tblTemPlatos, filaSel, 0).toString());
            String descripcion = Tables.valorModelo(tblTemPlatos, filaSel, 1).toString();
            Platos plato = platosPorId.get(id);
            BigDecimal precio = plato != null
                    ? ("VES".equalsIgnoreCase(plato.getMonedaBase()) && plato.getPrecioVes() != null
                            ? plato.getPrecioVes()
                            : (plato.getPrecioUsd() != null ? plato.getPrecioUsd() : plato.getPrecioDecimal()))
                    : importeMonetario(Tables.valorModelo(tblTemPlatos, filaSel, 2));
            BigDecimal total = precio;
            item = item + 1;
            tmp = (DefaultTableModel) tableMenu.getModel();
            for (int i = 0; i < tableMenu.getRowCount(); i++) {
                String idFila = Tables.valorModelo(tableMenu, i, 0).toString();
                String nombreFila = Tables.valorModelo(tableMenu, i, 1).toString();
                boolean coincide = false;
                if (!idFila.isBlank() && idFila.matches("\\d+")) {
                    coincide = Integer.parseInt(idFila) == id;
                }
                if (!coincide && nombreFila.equalsIgnoreCase(descripcion)) {
                    coincide = true;
                }
                if (coincide) {
                    int cantActual = Integer.parseInt(Tables.valorModelo(tableMenu, i, 2).toString());
                    if (cantActual >= Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
                        tableMenu.setRowSelectionInterval(i, i);
                        mostrarAlertaCampo(txtCantidadManual, "La cantidad máxima por plato es "
                                + Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
                        return;
                    }
                    setCantidadPlatoPorFila(i, cantActual + 1);
                    autoLimpiarFiltroPlatosTrasAgregar(id);
                    return;
                }
            }
            ArrayList lista = new ArrayList();
            lista.add(item);
            lista.add(id);
            lista.add(descripcion);
            lista.add(1);
            BigDecimal precio2Dec = precio.setScale(2, RoundingMode.HALF_UP);
            String precioStr = String.format(java.util.Locale.US, "%.2f", precio2Dec);
            lista.add(precioStr);
            lista.add(precioStr);
            boolean aplicaIva = platoAplicaIvaMap.getOrDefault(id, true);
            boolean tieneColumnaIva = tmp.getColumnCount() >= 7;
            String mb = (plato != null && plato.getMonedaBase() != null) ? plato.getMonedaBase() : "USD";
            BigDecimal tasaActual = getTasaActual();
            Object[] O = new Object[tieneColumnaIva ? 7 : 6];
            O[0] = lista.get(1);
            O[1] = lista.get(2);
            O[2] = lista.get(3);
            O[3] = formatearMontoCelda(precio2Dec, mb, tasaActual);
            O[4] = formatearMontoCelda(precio2Dec, mb, tasaActual);
            O[5] = "";
            if (tieneColumnaIva) {
                O[6] = formatearEtiquetaIva(aplicaIva);
            }
            tmp.addRow(O);
            tableMenu.setModel(tmp);
            Tables.ocultarColumnaModelo(tableMenu, 0); // ID interno oculto tras reconstruir columnas
            Tables.agregarColumnaMoneda(tableMenu, 3, mf -> monedaUbFila(tableMenu, mf));
            TotalPagar(tableMenu, totalMenu);
            actualizarEstadoBotonesCarrito();
            autoLimpiarFiltroPlatosTrasAgregar(id);
            // UX: dejar preseleccionada la fila agregada para ajustar cantidad al instante (+/- o campo).
            int nuevaFila = tmp.getRowCount() - 1;
            if (nuevaFila >= 0) {
                tableMenu.setRowSelectionInterval(nuevaFila, nuevaFila);
                tableMenu.scrollRectToVisible(tableMenu.getCellRect(nuevaFila, 0, true));
                actualizarEstadoBotonesCarrito();
                // Foco en el input de cantidad para tipeo manual inmediato (con texto seleccionado).
                if (txtCantidadManual != null) {
                    txtCantidadManual.requestFocusInWindow();
                    txtCantidadManual.selectAll();
                }
            }
        } else {
            mostrarAvisoEnBanda("Seleccione un plato del menú");
        }
    }//GEN-LAST:event_btnAddPlatoActionPerformed

    /**
     * Filtro rápido: tras agregar un plato, limpia el filtro y muestra la lista
     * completa dejando seleccionado y visible el plato elegido.
     */
    private void autoLimpiarFiltroPlatosTrasAgregar(int idPlato) {
        if (chkFiltroRapido != null && chkFiltroRapido.isSelected()
                && txtBuscarPlato != null && !txtBuscarPlato.getText().trim().isEmpty()) {
            desplazarAPlatoIdToma = idPlato;
            txtBuscarPlato.setText("");
            ListarPlatos(tblTemPlatos, "");
        }
    }

    private void btnGenerarPedidoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenerarPedidoActionPerformed
        if (tableMenu.getRowCount() > 0) {
            // Envio directo: sin confirmacion y sin mostrar numero de pedido.
            boolean eraEdicion = idPedidoEdicion > 0;
            boolean esPedidoRapido = modoPedidoRapido;
            String numMesa = esPedidoRapido ? "" : (txtTempNumMesa != null ? txtTempNumMesa.getText().trim() : "");
            String idSalaStr = esPedidoRapido ? "" : (txtTempIdSala != null ? txtTempIdSala.getText().trim() : "");

            if (!eraEdicion && !esPedidoRapido && !numMesa.isEmpty() && !idSalaStr.isEmpty()) {
                try {
                    int numMesaInt = Integer.parseInt(numMesa);
                    int idSalaInt = Integer.parseInt(idSalaStr);
                    if (numMesaInt > 0 && idSalaInt > 0) {
                        int pedidoActivo = pedidosControlador.buscarPedidoPendiente(numMesaInt, idSalaInt);
                        if (pedidoActivo > 0) {
                            javax.swing.JOptionPane.showMessageDialog(this,
                                    "La mesa " + numMesa + " ya posee una comanda activa (#" + pedidoActivo + ").\n"
                                    + "Debes pulsar 'Agregar Productos' para complementarla o finalizarla antes de abrir otra.",
                                    "Mesa Ocupada", javax.swing.JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            try {
                int idPedidoRegistrado = -1;
                if (eraEdicion) {
                    actualizarPedidoEnEdicion();
                    idPedidoEdicion = -1;
                    idSalaEdicion = -1;
                    numMesaEdicion = -1;
                    idMesoneroEdicionOriginal = null;
                    nombreMesoneroEdicionOriginal = null;
                    quitarMesoneroEdicion = false;
                    btnGenerarPedido.setText("Realizar Pedido");
                } else {
                    idPedidoRegistrado = registrarPedidoCompleto();
                }
                LimpiarTableMenu();
                RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
                RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                if (esPedidoRapido && idPedidoRegistrado > 0
                        && politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                    modoPedidoRapido = false;
                    cargarPedidoEnPantalla(idPedidoRegistrado, true);
                    mostrarAvisoEnBanda("Pedido rápido listo para cobrar");
                } else if (esPedidoRapido && idPedidoRegistrado > 0) {
                    modoPedidoRapido = false;
                    jTabbedPane1.setSelectedIndex(0);
                    mostrarAvisoEnBanda("Pedido rápido enviado a cocina");
                } else {
                    modoPedidoRapido = false;
                    if (idSalaActualMesas > 0 && cantMesasActual > 0) {
                        panelMesas(idSalaActualMesas, nombreSalaActualMesas, cantMesasActual);
                        int idxMesas = jTabbedPane1.indexOfTab("Mesas");
                        if (idxMesas != -1) jTabbedPane1.setSelectedIndex(idxMesas);
                        else jTabbedPane1.setSelectedIndex(0);
                    } else {
                        jTabbedPane1.setSelectedIndex(0);
                    }
                    mostrarAvisoEnBanda(numMesa.isEmpty()
                            ? (eraEdicion ? "Pedido actualizado" : "Pedido enviado a cocina")
                            : (eraEdicion ? "Pedido actualizado — Mesa " + numMesa : "Pedido enviado a cocina — Mesa " + numMesa));
                }
            } catch (Modelo.PedidoPendienteExistenteException ex) {
                JOptionPane.showMessageDialog(this,
                        "Otra sesión ya registró un pedido para esta mesa. Vuelve a seleccionar la sala para actualizar su estado.",
                        "Mesa ocupada", JOptionPane.WARNING_MESSAGE);
            } catch (Modelo.DataAccessException ex) {
                JOptionPane.showMessageDialog(this,
                        ex.getMessage() + " El carrito se conservó; verifica el historial antes de reintentar.",
                        "Error al guardar pedido", JOptionPane.ERROR_MESSAGE);
            } catch (ErrorAplicacionException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "No se pudo registrar el pedido", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            mostrarAvisoEnBanda("El carrito está vacío: agrega platos antes de enviar");
        }
    }//GEN-LAST:event_btnGenerarPedidoActionPerformed

    private void btnEliminarTempPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarTempPlatoActionPerformed
        if (tableMenu.getSelectedRow() < 0) {
            mostrarAvisoEnBanda("Selecciona un producto del pedido");
            return;
        }
        modelo = (DefaultTableModel) tableMenu.getModel();
        int fila = tableMenu.getSelectedRow();
        modelo.removeRow(fila);
        int totalFilas = tableMenu.getRowCount();
        if (totalFilas > 0) {
            int nuevaFila = Math.min(fila, totalFilas - 1);
            tableMenu.setRowSelectionInterval(nuevaFila, nuevaFila);
        } else {
            tableMenu.clearSelection();
        }
        TotalPagar(tableMenu, totalMenu);
        actualizarEstadoBotonesCarrito();
    }//GEN-LAST:event_btnEliminarTempPlatoActionPerformed

    private void aplicarCantidadManual(String qtyText, int fila) {
        if (fila == -1) return;
        try {
            int nuevaCant = Integer.parseInt(qtyText.trim());
            if (nuevaCant < 1 || nuevaCant > Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
                javax.swing.table.DefaultTableModel tmp = (javax.swing.table.DefaultTableModel) tableMenu.getModel();
                if (fila >= 0 && fila < tmp.getRowCount()) {
                    Object valOld = tmp.getValueAt(fila, 2);
                    if (txtCantidadManual != null && valOld != null) {
                        isUpdatingUI = true;
                        txtCantidadManual.setText(valOld.toString());
                        isUpdatingUI = false;
                    }
                }
                actualizarEstadoBotonesCarrito();
                mostrarAlertaCampo(txtCantidadManual, "La cantidad debe estar entre 1 y "
                        + Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
                return;
            }
            setCantidadPlatoPorFila(fila, nuevaCant);
        } catch (NumberFormatException e) {
            // Revertir en UI al valor numérico válido previo de la fila
            javax.swing.table.DefaultTableModel tmp = (javax.swing.table.DefaultTableModel) tableMenu.getModel();
            if (fila >= 0 && fila < tmp.getRowCount()) {
                Object valOld = tmp.getValueAt(fila, 2);
                if (txtCantidadManual != null && valOld != null) {
                    isUpdatingUI = true;
                    txtCantidadManual.setText(valOld.toString());
                    isUpdatingUI = false;
                }
            }
            actualizarEstadoBotonesCarrito();
        }
    }

    private void setCantidadPlatoPorFila(int fila, int nuevaCant) {
        if (nuevaCant < 1 || nuevaCant > Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
            mostrarAlertaCampo(txtCantidadManual, "La cantidad debe estar entre 1 y "
                    + Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
            return;
        }
        javax.swing.table.DefaultTableModel tmp = (javax.swing.table.DefaultTableModel) tableMenu.getModel();
        if (fila >= 0 && fila < tmp.getRowCount()) {
            java.math.BigDecimal precio = importeMonetario(tmp.getValueAt(fila, 3));
            String monedaFila = monedaFilaEnCarrito(tableMenu, fila);
            BigDecimal nuevoSub = precio.multiply(new java.math.BigDecimal(nuevaCant)).setScale(2, RoundingMode.HALF_UP);
            tmp.setValueAt(nuevaCant, fila, 2);
            tmp.setValueAt(formatearMontoCelda(nuevoSub, monedaFila, getTasaActual()), fila, 4);
            TotalPagar(tableMenu, totalMenu);
            // Solo sincronizar selección si la fila era la seleccionada o no había selección,
            // evitando secuestrar la selección cuando el usuario hizo clic sobre otra fila de la tabla.
            if (tableMenu.getSelectedRow() == -1 || tableMenu.getSelectedRow() == fila) {
                tableMenu.setRowSelectionInterval(fila, fila);
            }
            if (tableMenu.getSelectedRow() == fila) {
                isUpdatingUI = true;
                if(txtCantidadManual != null) txtCantidadManual.setText(String.valueOf(nuevaCant));
                isUpdatingUI = false;
            }
        }
    }

    private void setCantidadPlatoSeleccionadoById(int platoId, int nuevaCant) {
        javax.swing.table.DefaultTableModel tmp = (javax.swing.table.DefaultTableModel) tableMenu.getModel();
        for (int i = 0; i < tmp.getRowCount(); i++) {
            Object idObj = tmp.getValueAt(i, 0);
            if (idObj != null && !idObj.toString().isEmpty()) {
                try {
                    if (Integer.parseInt(idObj.toString()) == platoId) {
                        setCantidadPlatoPorFila(i, nuevaCant);
                        break;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }


    public void aumentarCantidadSeleccionada() {
        int fila = tableMenu.getSelectedRow();
        if (fila < 0) {
            mostrarAvisoEnBanda("Selecciona un producto del pedido");
            return;
        }
        try {
            int cantActual = Integer.parseInt(Tables.valorModelo(tableMenu, fila, 2).toString().trim());
            if (cantActual < 0) {
                cantActual = 0;
            }
            if (cantActual >= Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO) {
                mostrarAlertaCampo(txtCantidadManual, "La cantidad máxima por plato es "
                        + Modelo.DetallePedido.CANTIDAD_MAXIMA_POR_PLATO + ".");
                return;
            }
            int nuevaCantidad = Math.addExact(cantActual, 1);
            BigDecimal precioUnitario = importeMonetario(Tables.valorModelo(tableMenu, fila, 3));
            String monedaFila = monedaFilaEnCarrito(tableMenu, fila);
            BigDecimal nuevoSub = precioUnitario.multiply(BigDecimal.valueOf(nuevaCantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            tmp = (DefaultTableModel) tableMenu.getModel();
            tmp.setValueAt(nuevaCantidad, fila, 2);
            tmp.setValueAt(formatearMontoCelda(nuevoSub, monedaFila, getTasaActual()), fila, 4);
            TotalPagar(tableMenu, totalMenu);
            tableMenu.setRowSelectionInterval(fila, fila);
            actualizarEstadoBotonesCarrito();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo actualizar la cantidad: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void disminuirCantidadSeleccionada() {
        int fila = tableMenu.getSelectedRow();
        if (fila < 0) {
            mostrarAvisoEnBanda("Selecciona un producto del pedido");
            return;
        }
        try {
            int cantActual = Integer.parseInt(Tables.valorModelo(tableMenu, fila, 2).toString().trim());
            tmp = (DefaultTableModel) tableMenu.getModel();
            if (cantActual > 1) {
                int nuevaCantidad = cantActual - 1;
                BigDecimal precioUnitario = importeMonetario(Tables.valorModelo(tableMenu, fila, 3));
                String monedaFila = monedaFilaEnCarrito(tableMenu, fila);
                BigDecimal nuevoSub = precioUnitario.multiply(BigDecimal.valueOf(nuevaCantidad))
                        .setScale(2, RoundingMode.HALF_UP);
                tmp.setValueAt(nuevaCantidad, fila, 2);
                tmp.setValueAt(formatearMontoCelda(nuevoSub, monedaFila, getTasaActual()), fila, 4);
                tableMenu.setRowSelectionInterval(fila, fila);
            } else {
                tmp.removeRow(fila);
                int totalFilas = tableMenu.getRowCount();
                if (totalFilas > 0) {
                    int nuevaFila = Math.min(fila, totalFilas - 1);
                    tableMenu.setRowSelectionInterval(nuevaFila, nuevaFila);
                } else {
                    tableMenu.clearSelection();
                }
            }
            TotalPagar(tableMenu, totalMenu);
            actualizarEstadoBotonesCarrito();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo actualizar la cantidad: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void actualizarEstadoBotonesCarrito() {
        boolean haySeleccion = tableMenu != null && tableMenu.getSelectedRow() >= 0;
        if (btnMasCantidad != null) btnMasCantidad.setEnabled(haySeleccion);
        if (btnMenosCantidad != null) btnMenosCantidad.setEnabled(haySeleccion);
        if (btnEliminarTempPlato != null) btnEliminarTempPlato.setEnabled(haySeleccion);
        if (txtCantidadManual != null) {
            txtCantidadManual.setEnabled(haySeleccion);
            if (haySeleccion && !isUpdatingUI) {
                if (!txtCantidadManual.hasFocus()) {
                    isUpdatingUI = true;
                    Object val = Tables.valorModelo(tableMenu, tableMenu.getSelectedRow(), 2);
                    txtCantidadManual.setText(val != null ? val.toString() : "1");
                    isUpdatingUI = false;
                }
            } else if (!haySeleccion) {
                txtCantidadManual.setText("");
            }
        }
    }

    public javax.swing.JButton getBtnMasCantidad() {
        return btnMasCantidad;
    }

    public javax.swing.JButton getBtnMenosCantidad() {
        return btnMenosCantidad;
    }

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        if (tableMenu.getSelectedRow() < 0) {
            mostrarAvisoEnBanda("Seleccione una fila del carrito");
        } else {
            int id = Integer.parseInt(Tables.valorModelo(tableMenu, tableMenu.getSelectedRow(), 0).toString());
            for (int i = 0; i < tableMenu.getRowCount(); i++) {
                if (Tables.valorModelo(tableMenu, i, 0).equals(id)) {
                    tmp.setValueAt(txtComentario.getText(), i, 5);
                    txtComentario.setText("");
                    tableMenu.clearSelection();
                    return;
                }
            }
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    private void btnFinalizarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnFinalizarActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        if (txtIdPedido.getText().trim().isEmpty()) {
            mostrarAvisoEnBanda("Selecciona un pedido antes de finalizarlo");
            return;
        }
        Config cfg = conf != null ? conf : lgDao.datosEmpresa();
        String defaultNombre = (cfg != null && cfg.getClienteDefaultNombre() != null)
                ? cfg.getClienteDefaultNombre() : "Consumidor Final";
        String defaultDoc = (cfg != null && cfg.getClienteDefaultDocumento() != null)
                ? cfg.getClienteDefaultDocumento() : "V-00000000";

        // La tasa de cobro corresponde al pedido ya persistido en la BD.
        if (configServicio != null && configServicio.esTasaDolarDesactualizada()) {
            mostrarAvisoEnBanda("⚠ Tasa desactualizada: se factura con Bs. "
                    + (conf != null && conf.getTasaDolar() != null ? conf.getTasaDolar().toPlainString() : "no configurada"));
        }
        if (cbModoSalidaFinalizar.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
            cambiarModoSalidaGlobal(m);
        }
        String clienteNombre = txtClienteNombreFac.getText().trim().isEmpty() ? defaultNombre : txtClienteNombreFac.getText().trim();
        String clienteDoc = txtClienteDocFac.getText().trim().isEmpty() ? defaultDoc : txtClienteDocFac.getText().trim();
        List<Modelo.PagoPedido> pagos = leerPagosInline();
        if (pagos == null) return;
        String metodoPagoSel = pagos.size() == 1 ? pagos.getFirst().metodo() : "MIXTO";
        {
            String mesaInfo = ped != null && ped.esPedidoRapido() ? "" : txtNumMesaFinalizar.getText().trim();
            String totalInfo = totalFinalizar.getText().trim();

            int idPedido = Integer.parseInt(txtIdPedido.getText());
            long version = versionPedidoPantalla;
            btnFinalizar.setEnabled(false);
            new FinalizarPedidoSwingWorker(pedidosControlador, idPedido, clienteNombre, clienteDoc, metodoPagoSel,
                    pagos, resultado -> {
                boolean finalizado = resultado.finalizado();
                boolean impresoDirecto = resultado.impresoDirecto();
                boolean mismoPedido = version == versionPedidoPantalla
                        && String.valueOf(idPedido).equals(txtIdPedido.getText());
                if (finalizado) {
                    if (mismoPedido) {
                        volverAInicioSeleccionSala();
                    }
                    ListarPedidos();
                    RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
                    RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                    RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                    if (impresoDirecto) {
                        mostrarAvisoEnBanda(mesaInfo.isEmpty()
                                ? "Pedido facturado — " + totalInfo
                                : "Mesa " + mesaInfo + " facturada — " + totalInfo);
                    } else {
                        mostrarAvisoEnBanda("Pedido finalizado; falló la impresión directa y el PDF se abrió en el visor");
                    }
                } else {
                    if (mismoPedido) btnFinalizar.setEnabled(true);
                    mostrarAvisoEnBanda("El pedido no se finalizó");
                }
            }, (error, finalizacionConfirmada) -> {
                if (finalizacionConfirmada) {
                    ListarPedidos();
                    RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
                    RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                    RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                }
                boolean mismoPedido = version == versionPedidoPantalla
                        && String.valueOf(idPedido).equals(txtIdPedido.getText());
                if (finalizacionConfirmada) {
                    if (mismoPedido) {
                        volverAInicioSeleccionSala();
                    }
                    mostrarErrorPdfFinalizado(idPedido, error);
                } else {
                    if (mismoPedido) btnFinalizar.setEnabled(true);
                    JOptionPane.showMessageDialog(this,
                            "No se pudo finalizar el pedido: " + mensajeError(error),
                            "Error al finalizar", JOptionPane.ERROR_MESSAGE);
                }
            }).execute();
        }
    }//GEN-LAST:event_btnFinalizarActionPerformed

    
    
    
    private void actualizarResumenPagosFac() {
        if (lblResumenPagosFac == null || modeloPagosFac == null) return;
        if (tasaPagoFac == null || tasaPagoFac.signum() <= 0 || totalPagoBsFac == null) {
            lblResumenPagosFac.setText("Tasa del pedido no disponible en BD; el cobro está bloqueado.");
            return;
        }
        BigDecimal asignadoBs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int fila = 0; fila < modeloPagosFac.getRowCount(); fila++) {
            try {
                String metodo = String.valueOf(modeloPagosFac.getValueAt(fila, 0));
                String moneda = String.valueOf(modeloPagosFac.getValueAt(fila, 1));
                String texto = String.valueOf(modeloPagosFac.getValueAt(fila, 2)).trim().replace(',', '.');
                BigDecimal monto = new BigDecimal(texto);
                asignadoBs = asignadoBs.add(new Modelo.PagoPedido(metodo, moneda, monto).equivalenteBs(tasaPagoFac));
            } catch (RuntimeException ignorado) {
                // La fila incompleta se señala al intentar finalizar; el resumen no interrumpe la edición.
            }
        }
        asignadoBs = asignadoBs.setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoBs = totalPagoBsFac.subtract(asignadoBs).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoUsd = saldoBs.abs().divide(tasaPagoFac, 2, RoundingMode.HALF_UP);
        String estado = saldoBs.signum() > 0 ? "Saldo" : saldoBs.signum() < 0 ? "Exceso" : "Saldo";
        lblResumenPagosFac.setText("Tasa Bs. " + tasaPagoFac.stripTrailingZeros().toPlainString()
                + "/USD | Pagado Bs. " + asignadoBs.toPlainString() + " | " + estado + " Bs. "
                + saldoBs.abs().toPlainString() + " (USD " + saldoUsd.toPlainString() + ")");
    }

    private List<Modelo.PagoPedido> leerPagosInline() {
        if (tablaPagosFac.isEditing() && !tablaPagosFac.getCellEditor().stopCellEditing()) return null;
        if (tasaPagoFac == null || tasaPagoFac.signum() <= 0 || totalPagoBsFac == null) {
            lblAlertaPagosFac.setText("No se puede cobrar: falta una tasa válida guardada en el pedido.");
            lblResumenPagosFac.setForeground(COLOR_PELIGRO);
            return null;
        }
        List<Modelo.PagoPedido> pagos = new ArrayList<>();
        BigDecimal sumaBs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int fila = 0; fila < modeloPagosFac.getRowCount(); fila++) {
            try {
                String metodo = String.valueOf(modeloPagosFac.getValueAt(fila, 0));
                String moneda = String.valueOf(modeloPagosFac.getValueAt(fila, 1));
                String texto = String.valueOf(modeloPagosFac.getValueAt(fila, 2)).trim().replace(',', '.');
                if (texto.isEmpty()) throw new IllegalArgumentException("Ingrese el monto.");
                BigDecimal monto = new BigDecimal(texto);
                Modelo.PagoPedido pago = new Modelo.PagoPedido(metodo, moneda, monto);
                pagos.add(pago);
                sumaBs = sumaBs.add(pago.equivalenteBs(tasaPagoFac));
            } catch (RuntimeException ex) {
                lblAlertaPagosFac.setText("Revise método, moneda y monto de la fila " + (fila + 1) + ".");
                lblResumenPagosFac.setForeground(COLOR_TEXTO_INPUT);
                tablaPagosFac.changeSelection(fila, 2, false, false);
                tablaPagosFac.requestFocusInWindow();
                return null;
            }
        }
        sumaBs = sumaBs.setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetBs = totalPagoBsFac.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ajusteCambioBs = targetBs.subtract(sumaBs);
        boolean tieneUsd = pagos.stream().anyMatch(p -> "USD".equalsIgnoreCase(p.moneda()));
        boolean descuadrado;
        if (ajusteCambioBs.signum() == 0) {
            descuadrado = false;
        } else if (!tieneUsd) {
            descuadrado = true;
        } else {
            BigDecimal maxTolBs = new BigDecimal("0.005").multiply(tasaPagoFac).setScale(2, RoundingMode.HALF_UP);
            if (maxTolBs.compareTo(new BigDecimal("0.05")) < 0) {
                maxTolBs = new BigDecimal("0.05");
            }
            descuadrado = ajusteCambioBs.abs().compareTo(maxTolBs) > 0;
        }
        if (descuadrado) {
            int comparacion = sumaBs.compareTo(targetBs);
            BigDecimal diferencia = ajusteCambioBs.abs().setScale(2, RoundingMode.HALF_UP);
            BigDecimal diferenciaUsd = diferencia.divide(tasaPagoFac, 2, RoundingMode.HALF_UP);
            lblAlertaPagosFac.setText((comparacion < 0 ? "Falta " : "Excede ") + "Bs. "
                    + diferencia.toPlainString() + " (USD " + diferenciaUsd.toPlainString() + "). Ajuste los pagos al total.");
            lblResumenPagosFac.setForeground(COLOR_PELIGRO);
            return null;
        }
        lblAlertaPagosFac.setText(" ");
        lblResumenPagosFac.setForeground(COLOR_TEXTO_INPUT);
        return List.copyOf(pagos);
    }

    private void volverAInicioSeleccionSala() {
        LimpiarTableMenu();
        // Respetar el modo de atención del selector (A/B/C/Clásico) en vez de ir fijo a "Mesas".
        abrirAtencionDirectaSegunModo();
    }

    private String mensajeError(Throwable error) {
        if (error == null) return "Error desconocido";
        Throwable causa = error;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String principal = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        String detalle = causa.getMessage();
        if (detalle != null && !detalle.equals(principal)) {
            return principal + " (" + detalle + ")";
        }
        return principal;
    }

    private void btnPlatosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPlatosActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        ListarPlatos(TablePlatos, "");
        jTabbedPane1.setSelectedIndex(8);
    }//GEN-LAST:event_btnPlatosActionPerformed

    private void txtPrecioPlatoKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtPrecioPlatoKeyTyped
        // TODO add your handling code here:
        event.numberDecimalKeyPress(evt, txtPrecioPlato);
    }//GEN-LAST:event_txtPrecioPlatoKeyTyped

    private void btnGuardarPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGuardarPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        if (!txtIdPlato.getText().trim().isEmpty()) {
            mostrarAvisoEnBanda("Ya hay un plato cargado en el formulario: use Editar para modificarlo o Nuevo para limpiar.");
            return;
        }
        if (txtNombrePlato.getText().trim().isEmpty()) {
            mostrarAlertaCampo(txtNombrePlato, "El nombre del plato es obligatorio.");
            return;
        }
        if (txtPrecioPlato.getText().trim().isEmpty()) {
            mostrarAlertaCampo(txtPrecioPlato, "El precio del plato es obligatorio.");
            return;
        }
        try {
            pla.setNombre(txtNombrePlato.getText().trim());
            pla.setPrecioDecimal(importeMonetario(txtPrecioPlato.getText().trim()));
            pla.setFecha(fechaActual());
            String moneda = cbMonedaBasePlato != null ? String.valueOf(cbMonedaBasePlato.getSelectedItem()) : "USD";
            pla.setMonedaBase(moneda);
            boolean aplicaIva = chkAplicaIvaPlato == null || chkAplicaIvaPlato.isSelected();
            pla.setAplicaIva(aplicaIva);
            pla.setPrecioIncluyeIva(aplicaIva && chkPrecioIncluyeIvaPlato != null && chkPrecioIncluyeIvaPlato.isSelected());
            BigDecimal tasa = getTasaActual();
            if (platosControlador.registrar(pla, tasa)) {
                aplicarCategoriaFicha(pla.getNombre());
                ListarPlatos(TablePlatos, "");
                LimpiarPlatos();
                RefrescoVistas.notificar(RefrescoVistas.PLATOS);
                mostrarAvisoEnBanda("Plato registrado");
            } else {
                mostrarAvisoEnBanda("No se registró el plato");
            }
        } catch (ErrorAplicacionException ex) {
            javax.swing.JComponent campo = (chkPrecioIncluyeIvaPlato != null && ex.getMessage().toLowerCase().contains("iva")) ? chkPrecioIncluyeIvaPlato : txtPrecioPlato;
            mostrarAlertaCampo(campo, ex.getMessage());
            campo.requestFocusInWindow();
        }
    }//GEN-LAST:event_btnGuardarPlatoActionPerformed

    private void btnEditarPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditarPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        if ("".equals(txtIdPlato.getText())) {
            mostrarAvisoEnBanda("Seleccione un plato de la tabla");
        } else {
            if (txtNombrePlato.getText().trim().isEmpty()) {
                mostrarAlertaCampo(txtNombrePlato, "El nombre del plato es obligatorio.");
            } else if (txtPrecioPlato.getText().trim().isEmpty()) {
                mostrarAlertaCampo(txtPrecioPlato, "El precio del plato es obligatorio.");
            } else {
                try {
                    pla.setNombre(txtNombrePlato.getText().trim());
                    pla.setPrecioDecimal(importeMonetario(txtPrecioPlato.getText().trim()));
                    pla.setId(Integer.parseInt(txtIdPlato.getText().trim()));
                    if (pla.getFecha() == null || pla.getFecha().isBlank()) {
                        pla.setFecha(fechaActual());
                    }
                    String moneda = cbMonedaBasePlato != null ? String.valueOf(cbMonedaBasePlato.getSelectedItem()) : "USD";
                    pla.setMonedaBase(moneda);
                    boolean aplicaIva = chkAplicaIvaPlato == null || chkAplicaIvaPlato.isSelected();
                    pla.setAplicaIva(aplicaIva);
                    pla.setPrecioIncluyeIva(aplicaIva && chkPrecioIncluyeIvaPlato != null && chkPrecioIncluyeIvaPlato.isSelected());
                    BigDecimal tasa = getTasaActual();
                    int estimados = platosControlador.contarPedidosAbiertosConPlato(pla.getId());
                    if (estimados > 0) {
                        int confirmacion = JOptionPane.showConfirmDialog(
                                this,
                                "Modificar este plato recalculará aproximadamente " + estimados + " pedido(s) abierto(s) pendiente(s) de cobro.\n¿Desea continuar?",
                                "Confirmar modificación de catálogo",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                        if (confirmacion != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                    Modelo.ResultadoActualizacionPrecios res;
                    try {
                        res = platosControlador.modificarProductoYRecalcularPendientes(pla, tasa, getNombreUsuarioActual());
                    } catch (ErrorAplicacionException ex) {
                        javax.swing.JComponent campo = (chkPrecioIncluyeIvaPlato != null && ex.getMessage().toLowerCase().contains("iva")) ? chkPrecioIncluyeIvaPlato : txtPrecioPlato;
                        mostrarAlertaCampo(campo, ex.getMessage());
                        campo.requestFocusInWindow();
                        return;
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this,
                                "La operación fue completamente revertida. No se aplicaron cambios al catálogo ni a los pedidos:\n"
                                        + (ex.getMessage() != null ? ex.getMessage() : ex.toString()),
                                "Error al modificar plato", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (res != null) {
                        aplicarCategoriaFicha(pla.getNombre());
                        ListarPlatos(TablePlatos, "");
                        LimpiarPlatos();
                        RefrescoVistas.notificar(RefrescoVistas.PLATOS, res);
                        RefrescoVistas.notificar(RefrescoVistas.PEDIDOS, res);
                        String mensaje = "Plato modificado.";
                        if (res.pedidosRecalculados() > 0) {
                            mensaje += " Recalculados " + res.pedidosRecalculados() + " pedidos abiertos.";
                        }
                        if (res.lineasNoEnlazadasOmitidas() > 0) {
                            mensaje += " (" + res.lineasNoEnlazadasOmitidas() + " líneas históricas no vinculadas permanecen intactas).";
                        }
                        mostrarAvisoEnBanda(mensaje);
                    } else {
                        mostrarAvisoEnBanda("No se aplicaron cambios al plato");
                    }
                } catch (NumberFormatException ex) {
                    mostrarAvisoEnBanda("No se pudo leer el identificador del plato");
                } catch (ErrorAplicacionException ex) {
                    javax.swing.JComponent campo = (chkPrecioIncluyeIvaPlato != null && ex.getMessage().toLowerCase().contains("iva")) ? chkPrecioIncluyeIvaPlato : txtPrecioPlato;
                    mostrarAlertaCampo(campo, ex.getMessage());
                    campo.requestFocusInWindow();
                }
            }
        }
    }//GEN-LAST:event_btnEditarPlatoActionPerformed

    private void btnEliminarPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        if (!"".equals(txtIdPlato.getText())) {
            try {
                int id = Integer.parseInt(txtIdPlato.getText().trim());
                if (platosControlador.desactivar(id)) {
                    LimpiarPlatos();
                    ListarPlatos(TablePlatos, "");
                    RefrescoVistas.notificar(RefrescoVistas.PLATOS);
                    mostrarAvisoEnBanda("Plato desactivado");
                } else {
                    mostrarAvisoEnBanda("No se desactivó el plato");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Identificador de plato inválido.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            mostrarAvisoEnBanda("Selecciona un plato de la tabla");
        }
    }//GEN-LAST:event_btnEliminarPlatoActionPerformed

    private void btnNuevoPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNuevoPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        // TODO add your handling code here:
        LimpiarPlatos();
    }//GEN-LAST:event_btnNuevoPlatoActionPerformed

    private void TablePlatosMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_TablePlatosMouseClicked
        int fila = TablePlatos.rowAtPoint(evt.getPoint());
        if (fila < 0) {
            return;
        }
        cargarPlatoEnFormulario(fila);
    }//GEN-LAST:event_TablePlatosMouseClicked

    /**
     * Sincroniza el formulario de plato con la fila indicada (tabla→detalles).
     * La edición inversa vive en {@link #reflejarFormularioEnFilaPlato()}.
     */
    private void cargarPlatoEnFormulario(int filaVista) {
        if (sincronizandoPlatoForm) {
            return;
        }
        sincronizandoPlatoForm = true;
        int idPlato = -1;
        try {
        txtIdPlato.setText(Tables.valorModelo(TablePlatos, filaVista, 0).toString());
        txtNombrePlato.setText(Tables.valorModelo(TablePlatos, filaVista, 1).toString());
        try {
            idPlato = Integer.parseInt(Tables.valorModelo(TablePlatos, filaVista, 0).toString());
            Platos platoSel = platosPorId.get(idPlato);
            if (platoSel != null) {
                String mb = platoSel.getMonedaBase() != null ? platoSel.getMonedaBase().toUpperCase() : "USD";
                if (cbMonedaBasePlato != null) {
                    cbMonedaBasePlato.setSelectedItem(mb);
                }
                BigDecimal precioPrimario = "VES".equals(mb) && platoSel.getPrecioVes() != null
                        ? platoSel.getPrecioVes()
                        : (platoSel.getPrecioUsd() != null ? platoSel.getPrecioUsd() : platoSel.getPrecioDecimal());
                if (precioPrimario != null) {
                    txtPrecioPlato.setText(precioPrimario.setScale(2, RoundingMode.HALF_UP).toPlainString());
                } else {
                    txtPrecioPlato.setText(Tables.valorModelo(TablePlatos, filaVista, 2).toString().replaceAll("[^0-9.]", "").trim());
                }
                if (chkAplicaIvaPlato != null) {
                    chkAplicaIvaPlato.setSelected(platoSel.isAplicaIva());
                }
                if (chkPrecioIncluyeIvaPlato != null) {
                    chkPrecioIncluyeIvaPlato.setEnabled(platoSel.isAplicaIva());
                    chkPrecioIncluyeIvaPlato.setSelected(platoSel.isAplicaIva() && Boolean.TRUE.equals(platoSel.getPrecioIncluyeIva()));
                }
                seleccionarCategoriaFicha(platoSel.getIdCategoria());
                actualizarPrecioReferenciaPlato();
            } else {
                txtPrecioPlato.setText(Tables.valorModelo(TablePlatos, filaVista, 2).toString().replaceAll("[^0-9.]", "").trim());
                boolean aplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                if (TablePlatos.getModel().getColumnCount() > 3 && Tables.valorModelo(TablePlatos, filaVista, 3) != null) {
                    String val = Tables.valorModelo(TablePlatos, filaVista, 3).toString().toLowerCase(java.util.Locale.ROOT);
                    aplicaIva = !val.contains("exento") && !val.contains("no");
                }
                if (chkAplicaIvaPlato != null) {
                    chkAplicaIvaPlato.setSelected(aplicaIva);
                }
                if (chkPrecioIncluyeIvaPlato != null) {
                    chkPrecioIncluyeIvaPlato.setEnabled(aplicaIva);
                    chkPrecioIncluyeIvaPlato.setSelected(false);
                }
                actualizarPrecioReferenciaPlato();
            }
        } catch (Exception ignored) {
            txtPrecioPlato.setText(Tables.valorModelo(TablePlatos, filaVista, 2).toString().replaceAll("[^0-9.]", "").trim());
        }
        } finally {
            sincronizandoPlatoForm = false;
        }
        if (idPlato > 0) {
            autoLimpiarFiltroAdminTrasSeleccionar(idPlato);
        }
    }

    private void autoLimpiarFiltroAdminTrasSeleccionar(int idPlato) {
        if (chkFiltroRapidoAdminPlatos != null && chkFiltroRapidoAdminPlatos.isSelected()
                && txtBuscarAdminPlatos != null && !txtBuscarAdminPlatos.getText().trim().isEmpty()) {
            desplazarAPlatoIdAdmin = idPlato;
            txtBuscarAdminPlatos.setText("");
            ListarPlatos(TablePlatos, "");
        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel LabelVendedor;
    private javax.swing.JPanel PanelMesas;
    private javax.swing.JPanel PanelSalas;
    private javax.swing.JTable TablePedidos;
    private javax.swing.JTable TablePlatos;
    public javax.swing.JTable TableUsuarios;
    private javax.swing.JButton btnActualizarConfig;
    private javax.swing.JButton btnActualizarSala;
    private javax.swing.JButton btnAddPlato;
    private javax.swing.JButton btnLimpiarBuscar;
    private javax.swing.JButton btnConfig;
    private javax.swing.JButton btnEditarPlato;
    private javax.swing.JButton btnEliminarPlato;
    private javax.swing.JButton btnEliminarSala;
    private javax.swing.JButton btnEliminarTempPlato;
    private javax.swing.JButton btnFinalizar;
    private javax.swing.JButton btnModificarPedido;
    private javax.swing.JButton btnGenerarPedido;
    private javax.swing.JButton btnGuardarPlato;
    private javax.swing.JButton btnIniciar;
    public javax.swing.JButton btnCambiarClaveUsuario;
    public javax.swing.JButton btnEstadoUsuario;
    public javax.swing.JButton btnEliminarUsuario;
    private javax.swing.JButton btnNuevoPlato;
    private javax.swing.JButton btnNuevoSala;
    private javax.swing.JButton btnPdfPedido;
    private javax.swing.JButton btnCategorias;
    private javax.swing.JButton btnInicio;
    private javax.swing.JButton btnPlatos;
    private javax.swing.JButton btnRegistrarSala;
    private javax.swing.JButton btnSala;
    private javax.swing.JButton btnUsuarios;
    private javax.swing.JButton btnVentas;
    private javax.swing.JComboBox<String> cbxRol;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JLabel jLabel40;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    /**
     * AVISO DISEÑADOR (NetBeans/Matisse): este scroll y btnDashboard son edición
     * manual fuera del .form (ver creación en initComponents y reacomodarComponentes).
     * No regenerar Sistema.java desde el diseñador: se perderían y Dashboard volvería
     * a quedar recortado. Si se edita el .form, remodelar el JScrollPane allí.
     */
    private javax.swing.JScrollPane scrollBarraLateral;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel21;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel23;
    private javax.swing.JPanel jPanel24;
    private javax.swing.JPanel jPanel25;
    private javax.swing.JPanel jPanel31;
    private javax.swing.JPanel jPanel33;
    private javax.swing.JPanel jPanel35;
    private javax.swing.JPanel jPanel36;
    private javax.swing.JPanel jPanel38;
    private javax.swing.JPanel jPanel39;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel40;
    private javax.swing.JPanel jPanel41;
    private javax.swing.JPanel jPanel42;
    private javax.swing.JPanel jPanel43;
    private javax.swing.JPanel jPanel44;
    private javax.swing.JPanel jPanel45;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane13;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel labelLogo;
    private javax.swing.JTable tableFinalizar;
    private javax.swing.JTable tableMenu;
    private javax.swing.JTable tableSala;
    private javax.swing.JTable tblTemPlatos;
    private javax.swing.JLabel tipo;
    private javax.swing.JLabel totalFinalizar;
    private javax.swing.JLabel totalMenu;
    private javax.swing.JTextField txtBuscarPlato;
    private javax.swing.JTextPane txtComentario;
    private javax.swing.JTextField txtCorreo;
    private javax.swing.JTextField txtDireccionConfig;
    private javax.swing.JTextField txtFechaHora;
    private javax.swing.JTextField txtIdConfig;
    private javax.swing.JTextField txtIdHistorialPedido;
    private javax.swing.JTextField txtIdPedido;
    private javax.swing.JTextField txtClienteNombreFac;
    private javax.swing.JTextField txtClienteDocFac;
    private javax.swing.table.DefaultTableModel modeloPagosFac;
    private javax.swing.JTable tablaPagosFac;
    private javax.swing.JButton btnAgregarPagoFac;
    private javax.swing.JButton btnQuitarPagoFac;
    private javax.swing.JScrollPane scrollPagosFac;
    private javax.swing.JLabel lblClienteFac;
    private javax.swing.JLabel lblDocFac;
    private javax.swing.JLabel lblPagoFac;
    private javax.swing.JLabel lblMesoneroFin;
    private javax.swing.JLabel lblResumenPagosFac;
    private javax.swing.JLabel lblAlertaPagosFac;
    private BigDecimal tasaPagoFac;
    private BigDecimal totalPagoBsFac;
    private javax.swing.JTextField txtIdPlato;
    private javax.swing.JTextField txtIdSala;
    private javax.swing.JTextField txtMensaje;
    private javax.swing.JTextField txtMesas;
    private javax.swing.JTextField txtNombre;
    private javax.swing.JTextField txtNombreConfig;
    private javax.swing.JTextField txtNombrePlato;
    private javax.swing.JTextField txtNombreSala;
    private javax.swing.JTextField txtNumMesaFinalizar;
    private javax.swing.JPasswordField txtPass;
    private javax.swing.JTextField txtPrecioPlato;
    private javax.swing.JTextField txtRucConfig;
    private javax.swing.JTextField txtSalaFinalizar;
    private javax.swing.JTextField txtTasaConfig;
    private javax.swing.JLabel jLabelTasaConfig;
    private javax.swing.JTextField txtIvaConfig;
    private javax.swing.JLabel jLabelIvaConfig;
    private javax.swing.JTextField txtTelefonoConfig;
    private javax.swing.JTextField txtTempIdSala;
    private javax.swing.JTextField txtTempNumMesa;
    // End of variables declaration//GEN-END:variables

    private void TotalPagar(JTable tabla, JLabel label) {
        int numFila = tabla.getRowCount();
        if (numFila == 0) {
            Totalpagar = BigDecimal.ZERO.setScale(2);
            label.setText("$ 0.00");
            label.setToolTipText("Consumo en mesa: $ 0.00 (IVA y total en Bs. se calculan al facturar)");
            if (tabla == tableMenu) {
                actualizarCabecerasMonedaMenu();
            }
            return;
        }

        BigDecimal tasa = getTasaActual();
        boolean tasaValida = tasa != null && tasa.signum() > 0;
        BigDecimal tasaNorm = tasaValida ? tasa.setScale(4, RoundingMode.HALF_UP) : null;

        BigDecimal ivaPorcentaje = getIvaPorcentajeActual();
        BigDecimal ivaNorm = (ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) >= 0)
                ? ivaPorcentaje.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        boolean tienePlatosVes = false;
        List<DetallePedido> detalles = new ArrayList<>();
        BigDecimal totalUsdDirecto = BigDecimal.ZERO.setScale(2);
        BigDecimal totalBsDirecto = BigDecimal.ZERO.setScale(2);

        for (int i = 0; i < numFila; i++) {
            BigDecimal subtotal = importeMonetario(tabla.getModel().getValueAt(i, 4));
            int cant = 1;
            try {
                cant = Integer.parseInt(tabla.getModel().getValueAt(i, 2).toString().trim());
                if (cant < 1) cant = 1;
            } catch (Exception ignored) {}

            BigDecimal precioUnit;
            try {
                precioUnit = importeMonetario(tabla.getModel().getValueAt(i, 3));
            } catch (Exception ex) {
                precioUnit = subtotal.divide(BigDecimal.valueOf(cant), 2, RoundingMode.HALF_UP);
            }

            Object idObj = tabla.getModel().getValueAt(i, 0);
            Integer idPlato = null;
            if (idObj != null && !idObj.toString().isBlank()) {
                try {
                    idPlato = Integer.parseInt(idObj.toString().trim());
                } catch (NumberFormatException ignored) {}
            }
            Platos plato = idPlato != null ? platosPorId.get(idPlato) : null;
            String nombre = tabla.getModel().getValueAt(i, 1) != null ? tabla.getModel().getValueAt(i, 1).toString() : "";
            if (plato == null && !nombre.isBlank()) {
                for (Platos p : platosPorId.values()) {
                    if (p != null && p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre)) {
                        plato = p;
                        break;
                    }
                }
            }

            String mb = (plato != null && plato.getMonedaBase() != null)
                    ? plato.getMonedaBase().toUpperCase(java.util.Locale.ROOT)
                    : null;
            if (mb == null) {
                String valCelda = String.valueOf(tabla.getModel().getValueAt(i, 3)).trim().toUpperCase(java.util.Locale.ROOT);
                if (valCelda.startsWith("BS") || valCelda.contains("BS.")) {
                    mb = "VES";
                } else if (valCelda.startsWith("$")) {
                    mb = "USD";
                } else {
                    String r = monedaUbFila(tabla, i);
                    if ("B".equalsIgnoreCase(r) || "VES".equalsIgnoreCase(r)) {
                        mb = "VES";
                    } else {
                        mb = "USD";
                    }
                }
            }
            if ("VES".equals(mb)) {
                tienePlatosVes = true;
                totalBsDirecto = totalBsDirecto.add(subtotal);
            } else {
                totalUsdDirecto = totalUsdDirecto.add(subtotal);
            }

            boolean itemAplicaIva = true;
            if (tabla.getModel().getColumnCount() > 6 && tabla.getModel().getValueAt(i, 6) != null) {
                String valIva = tabla.getModel().getValueAt(i, 6).toString().toLowerCase(java.util.Locale.ROOT);
                if (valIva.contains("exento") || valIva.contains("no") || valIva.equals("false") || valIva.equals("0")) {
                    itemAplicaIva = false;
                }
            } else if (plato != null) {
                itemAplicaIva = plato.isAplicaIva();
            } else if (idPlato != null) {
                itemAplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
            }

            Boolean incIva = (plato != null) ? plato.getPrecioIncluyeIva() : Boolean.FALSE;
            if (incIva == null || !itemAplicaIva) incIva = Boolean.FALSE;

            DetallePedido det = new DetallePedido();
            det.setNombre(nombre.isBlank() ? "Plato" : nombre);
            det.setCantidad(cant);
            det.setPrecioDecimal(precioUnit);
            det.setMonedaBase(mb);
            det.setAplicaIva(itemAplicaIva);
            det.setPrecioIncluyeIva(incIva);
            det.setIvaPorcentaje(itemAplicaIva ? ivaNorm : BigDecimal.ZERO.setScale(2));
            detalles.add(det);
        }

        BigDecimal totalUsd;
        BigDecimal totalBs;
        BigDecimal subUsd;
        BigDecimal subBs;
        if (tasaValida) {
            try {
                Servicio.CalculoMonetarioPedido.CalculoPedido calc = Servicio.CalculoMonetarioPedido.calcularPedido(detalles, tasaNorm);
                totalUsd = calc.totalUsd();
                totalBs = calc.totalVes();
                subUsd = calc.subtotalUsd();
                subBs = calc.subtotalVes();
            } catch (Exception ex) {
                totalUsd = totalUsdDirecto.add(totalBsDirecto.divide(tasaNorm, 2, RoundingMode.HALF_UP));
                totalBs = totalBsDirecto.add(totalUsdDirecto.multiply(tasaNorm).setScale(2, RoundingMode.HALF_UP));
                subUsd = totalUsd;
                subBs = totalBs;
            }
        } else {
            totalUsd = totalUsdDirecto;
            totalBs = totalBsDirecto;
            subUsd = totalUsdDirecto;
            subBs = totalBsDirecto;
        }

        Totalpagar = totalUsd.setScale(2, RoundingMode.HALF_UP);
        String totalUsdStr = String.format(java.util.Locale.US, "%.2f", Totalpagar);
        String totalBsStr = String.format(java.util.Locale.US, "%.2f", totalBs.setScale(2, RoundingMode.HALF_UP));

        if (tienePlatosVes) {
            label.setText("USD $ " + totalUsdStr + "  |  Bs. " + totalBsStr);
            label.setToolTipText("Consumo en mesa: USD $ " + totalUsdStr + " | Bs. " + totalBsStr
                    + " (Subtotal: $ " + String.format(java.util.Locale.US, "%.2f", subUsd)
                    + " / Bs. " + String.format(java.util.Locale.US, "%.2f", subBs)
                    + " - IVA y total oficial se confirman al facturar)");
        } else {
            label.setText("$ " + totalUsdStr);
            if (tasaValida) {
                label.setToolTipText("Consumo en mesa: $ " + totalUsdStr + " (Bs. " + totalBsStr + ")"
                        + " - IVA y total en Bs. se calculan al facturar");
            } else {
                label.setToolTipText("Consumo en mesa: $ " + totalUsdStr
                        + " (IVA y total en Bs. se calculan al facturar)");
            }
        }

        if (tabla == tableMenu) {
            actualizarCabecerasMonedaMenu();
        }
    }

    /** Cabeceras Precio/SubTotal del carrito según la moneda de sus líneas. */
    private void actualizarCabecerasMonedaMenu() {
        if (tableMenu == null || !(tableMenu.getModel() instanceof DefaultTableModel m)) {
            return;
        }
        java.util.List<String> monedas = new java.util.ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            Object idObj = m.getValueAt(i, 0);
            if (idObj == null) {
                continue;
            }
            try {
                Platos p = platosPorId.get(Integer.parseInt(idObj.toString()));
                if (p != null && p.getMonedaBase() != null) {
                    monedas.add(p.getMonedaBase());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        Tables.fijarCabeceraMoneda(tableMenu, 3, monedas);
        Tables.fijarCabeceraMoneda(tableMenu, 4, monedas);
    }

    private void envolverConEncabezado(java.awt.Component target, String titulo, java.awt.Color bg, Runnable accionVolver) {
        envolverConEncabezado(target, titulo, bg, accionVolver, null);
    }

    private void envolverConEncabezado(java.awt.Component target, String titulo, java.awt.Color bg, Runnable accionVolver, javax.swing.JComponent extra) {
        int idx = jTabbedPane1.indexOfComponent(target);
        if (idx == -1) return;
        
        javax.swing.JPanel wrap = new javax.swing.JPanel(new java.awt.BorderLayout());
        javax.swing.JPanel header = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 15, 10));
        header.setBackground(bg);
        
        if (accionVolver != null) {
            javax.swing.JButton btnVolver = new javax.swing.JButton("⬅ Volver");
            btnVolver.setToolTipText("Volver a la pantalla anterior");
            btnVolver.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
            btnVolver.setForeground(new java.awt.Color(50, 50, 50));
            btnVolver.setBackground(new java.awt.Color(255, 255, 255));
            btnVolver.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            btnVolver.setFocusable(false);
            btnVolver.addActionListener(e -> accionVolver.run());
            header.add(btnVolver);
        }
        
        javax.swing.JLabel lblTitulo = new javax.swing.JLabel(titulo);
        lblTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 18));
        lblTitulo.setForeground(COLOR_CABECERA);
        lblTitulo.setName("uxTituloEncabezado");
        header.add(lblTitulo);

        if (extra != null) {
            header.add(extra);
        }
        
        wrap.add(header, java.awt.BorderLayout.NORTH);
        jTabbedPane1.setComponentAt(idx, wrap);

        if (target == panelSplitScreen) {
            wrap.add(target, java.awt.BorderLayout.CENTER);
            target.setVisible(true);
            return;
        }

        javax.swing.JPanel contenido = new PanelScrollable(new java.awt.BorderLayout());
        contenido.add(target, java.awt.BorderLayout.CENTER);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(contenido,
                javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        wrap.add(scroll, java.awt.BorderLayout.CENTER);
        target.setVisible(true);
    }

    /**
     * Panel que delega su tamano preferido en su contenido y que se estira para
     * llenar el viewport del JScrollPane cuando este es mas grande, mostrando
     * barras de desplazamiento (horizontal y vertical) cuando es mas pequeno.
     */
    private static final class PanelScrollable extends javax.swing.JPanel implements javax.swing.Scrollable {
        PanelScrollable(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getParent().getWidth() >= getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private void mostrarErrorPdfFinalizado(int idPedido, Throwable error) {
        String[] opciones = {"Reintentar PDF", "Abrir historial", "Cerrar"};
        int opcion = JOptionPane.showOptionDialog(this,
                "La factura del pedido #" + idPedido + " quedó registrada y la mesa está cerrada, "
                + "pero no se pudo generar o abrir el PDF tras dos intentos.\n\n"
                + mensajeError(error) + "\n\nPuede reintentar el PDF sin volver a facturar.",
                "Factura registrada; PDF pendiente",
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, opciones, opciones[0]);
        if (opcion == 0) {
            reintentarPdfFinalizado(idPedido);
        } else if (opcion == 1) {
            abrirHistorialPedido(idPedido);
        }
    }

    private void reintentarPdfFinalizado(int idPedido) {
        new javax.swing.SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                // Este flujo solo vuelve a generar/abrir el PDF; no toca el estado de la mesa ni la factura.
                return pedidosControlador.generarPdfPedido(idPedido);
            }

            @Override
            protected void done() {
                try {
                    boolean salidaDirecta = get();
                    mostrarAvisoEnBanda(salidaDirecta
                            ? "PDF del pedido #" + idPedido + " generado y enviado correctamente"
                            : "PDF del pedido #" + idPedido + " generado; revise el visor o reintente desde el historial");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mostrarErrorPdfFinalizado(idPedido, ex);
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    mostrarErrorPdfFinalizado(idPedido, causa);
                }
            }
        }.execute();
    }

    private void abrirHistorialPedido(int idPedido) {
        filtroEstadoSeleccionado = null;
        filtroSoloHoyActivo = false;
        if (txtFiltroFechaDesde != null) txtFiltroFechaDesde.setText("");
        if (txtFiltroFechaHasta != null) txtFiltroFechaHasta.setText("");
        if (txtBuscarHistorial != null) txtBuscarHistorial.setText(String.valueOf(idPedido));
        int indiceHistorial = jTabbedPane1.indexOfComponent(jPanel6);
        if (indiceHistorial >= 0) jTabbedPane1.setSelectedIndex(indiceHistorial);
        aplicarFiltroHistorial();
    }
        

    private void aplicarEstiloInputs(java.awt.Container contenedor) {
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.JTextField) {
                c.setBackground(java.awt.Color.WHITE);
                ((javax.swing.JTextField) c).setOpaque(true);
            } else if (c instanceof java.awt.Container) {
                aplicarEstiloInputs((java.awt.Container) c);
            }
        }
    }

    private java.util.List<java.awt.Component> todosLosComponentes(java.awt.Container contenedor) {
        java.util.List<java.awt.Component> lista = new java.util.ArrayList<>();
        for (java.awt.Component c : contenedor.getComponents()) {
            lista.add(c);
            if (c instanceof java.awt.Container) {
                lista.addAll(todosLosComponentes((java.awt.Container) c));
            }
        }
        return lista;
    }

    private void confirmarCierreSistema() {
        boolean hayMesasAbiertas = false;
        try {
            java.util.Map<Integer, Integer> ocupadas = pedidosControlador.contarMesasOcupadasPorSala();
            for (Integer cant : ocupadas.values()) {
                if (cant != null && cant > 0) {
                    hayMesasAbiertas = true;
                    break;
                }
            }
        } catch (Exception ex) {
            // Ignorar para no bloquear el cierre si falla DB
        }

        String titulo = "Confirmar Cierre";
        String mensaje = "¿Está seguro de que desea salir del sistema de administración?";
        
        if (hayMesasAbiertas) {
            titulo = "¡ADVERTENCIA: Mesas abiertas!";
            mensaje = "Hay mesas o puestos que todavía tienen pedidos sin finalizar ni cobrar.\n\n"
                    + "Cerrar el programa ahora no perderá los pedidos, pero asegúrese de\n"
                    + "que no hay clientes esperando la cuenta o platos en preparación.\n\n"
                    + "¿Aún así desea salir del sistema?";
        }

        int opcion = javax.swing.JOptionPane.showConfirmDialog(
                this,
                mensaje,
                titulo,
                javax.swing.JOptionPane.YES_NO_OPTION,
                hayMesasAbiertas ? javax.swing.JOptionPane.WARNING_MESSAGE : javax.swing.JOptionPane.QUESTION_MESSAGE
        );

        if (opcion == javax.swing.JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    private void aplicarEncabezados() {
        // --- UX: un color pastel distinto por pantalla, acorde a su funcion ---
        envolverConEncabezado(jPanel9, "INICIO - SELECCIÓN DE SALA", new java.awt.Color(214, 234, 251), null);   // Azul cielo: recepcion
        envolverConEncabezado(jPanel4, "GESTIÓN DE SALAS", new java.awt.Color(253, 235, 208), null);             // Melocoton: espacios
        envolverConEncabezado(jPanel22, "SELECCIÓN DE MESAS", new java.awt.Color(209, 242, 235), null);          // Turquesa: comedor

        envolverConEncabezado(jPanel23, "TOMA DE PEDIDO", new java.awt.Color(213, 245, 227), () -> {             // Verde menta: accion
            if (idSalaActualMesas > 0 && cantMesasActual > 0) {
                panelMesas(idSalaActualMesas, nombreSalaActualMesas, cantMesasActual);
                int idx = jTabbedPane1.indexOfTab("Mesas");
                if(idx != -1) jTabbedPane1.setSelectedIndex(idx);
            } else {
                int idx = jTabbedPane1.indexOfTab("Salas");
                if(idx != -1) jTabbedPane1.setSelectedIndex(idx);
            }
        }, panelCabeceraMesaDirecta);

        envolverConEncabezado(jPanel25, "CAJA Y FACTURACIÓN", new java.awt.Color(254, 249, 231), () -> {          // Amarillo manteca: dinero
            int idx = jTabbedPane1.indexOfTab("Platos");
            if(idx != -1) jTabbedPane1.setSelectedIndex(idx);
        });
        envolverConEncabezado(jPanel6, "HISTORIAL Y CIERRES", new java.awt.Color(234, 236, 238), null);           // Gris perla: archivo

        envolverConEncabezado(jPanel7, "CONFIGURACIÓN", new java.awt.Color(232, 218, 239), null);               // Lila: ajustes
        envolverConEncabezado(jPanel12, "USUARIOS", new java.awt.Color(249, 235, 234), null);                   // Rosa palido: personas
        envolverConEncabezado(jPanel2, "GESTIÓN DE PLATOS", new java.awt.Color(250, 215, 160), null);             // Terracota: comida
        if(panelCategorias != null) envolverConEncabezado(panelCategorias, "GESTIÓN DE CATEGORÍAS", new java.awt.Color(169, 223, 191), null); // Salvia: clasificar
        if(panelDashboard != null) envolverConEncabezado(panelDashboard, "DASHBOARD", new java.awt.Color(212, 230, 241), null);               // Acero: metricas
        if(panelSplitScreen != null) envolverConEncabezado(panelSplitScreen, "ATENCIÓN DIRECTA (SPLIT)", new java.awt.Color(209, 242, 235), null);
        // -----------------------------------------------------------------
    }

    private void aplicarMejorasDeUX() {
        // Force white background and better borders on all TextFields inside this frame
        aplicarEstiloInputs(this.getContentPane());

        // Highlighting
        java.awt.event.FocusAdapter highlighter = new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (evt.getComponent() instanceof javax.swing.JTextField) {
                    javax.swing.JTextField f = (javax.swing.JTextField) evt.getComponent();
                    f.setBackground(new java.awt.Color(230, 245, 255));
                    f.selectAll();
                }
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (evt.getComponent() instanceof javax.swing.JTextField) {
                    evt.getComponent().setBackground(COLOR_TIFFANY);
                }
            }
        };
        txtBuscarPlato.addFocusListener(highlighter);
        txtCantidadManual.addFocusListener(highlighter);
        
        // Loop recursively through all components
        for (java.awt.Component c : todosLosComponentes(this)) {
            if (c instanceof javax.swing.JTextField) {
                c.addFocusListener(highlighter);
            }
        }
        
        configurarAtajos();
    }

    /**
     * A2: atajos via InputMap/ActionMap del rootPane (WHEN_IN_FOCUSED_WINDOW) en
     * lugar de un KeyEventDispatcher global (process-wide). ESC se acota al panel
     * del carrito (jPanel23) para no secuestrar la tecla en combos, dialogos ni
     * editores de celda.
     */
    private void configurarAtajos() {
        javax.swing.JRootPane rootPane = getRootPane();
        javax.swing.InputMap im = rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = rootPane.getActionMap();

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0), "ux.ayuda");
        am.put("ux.ayuda", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                mostrarPanelAyuda();
            }
        });

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0), "ux.irMenu");
        am.put("ux.irMenu", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                jTabbedPane1.setSelectedIndex(3);
                txtBuscarPlato.requestFocusInWindow();
                txtBuscarPlato.requestFocus();
            }
        });

        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0), "ux.generar");
        am.put("ux.generar", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (jTabbedPane1.getSelectedIndex() == 3) {
                    btnGenerarPedido.doClick();
                }
            }
        });

        javax.swing.InputMap imCarrito = jPanel23.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap amCarrito = jPanel23.getActionMap();
        imCarrito.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "ux.cancelarPedido");
        amCarrito.put("ux.cancelarPedido", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                LimpiarTableMenu();
            }
        });
    }

    private void mostrarPanelAyuda() {
        String ayuda = "<html><h3>Atajos de Teclado (Mejoras UX)</h3>"
            + "<ul>"
            + "<li><b>[F1]</b>: Mostrar este panel de ayuda.</li>"
            + "<li><b>[F2]</b>: Ir directo al buscador de platos de la mesa actual.</li>"
            + "<li><b>[F5]</b>: Finalizar o Actualizar Pedido (cuando estás en el carrito).</li>"
            + "<li><b>[ESC]</b>: Limpiar el carrito de la mesa actual.</li>"
            + "<li><b>[Enter] en Buscador</b>: Agrega el plato actualmente seleccionado de la lista.</li>"
            + "</ul><p>¡Usar estos atajos en horas pico agilizará tus pedidos significativamente!</p></html>";
        JOptionPane.showMessageDialog(this, ayuda, "Ayuda de Atajos", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Vacía exclusivamente las filas y totales del carrito visual (tableMenu)
     * sin alterar el estado de edición del pedido (idPedidoEdicion, sala, mesa).
     */
    private void vaciarCarritoVisual() {
        tmp = (DefaultTableModel) tableMenu.getModel();
        tmp.setRowCount(0);
        item = 0;
        Totalpagar = BigDecimal.ZERO.setScale(2);
        if (totalMenu != null) {
            TotalPagar(tableMenu, totalMenu);
        }
        actualizarEstadoBotonesCarrito();
    }

    private void LimpiarTableMenu() {
        idPedidoEdicion = -1;
        idSalaEdicion = -1;
        numMesaEdicion = -1;
        idMesoneroEdicionOriginal = null;
        nombreMesoneroEdicionOriginal = null;
        quitarMesoneroEdicion = false;
        if (btnGenerarPedido != null) btnGenerarPedido.setText("Realizar Pedido");
        vaciarCarritoVisual();
    }

    public void ListarConfig() {
        if (conf == null) {
            conf = nuevoConfigPorDefecto();
        }
        final long miVersion = ++versionCargaConfig;
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                this::cargarConfigEnFondo,
                carga -> { if (miVersion == versionCargaConfig) mostrarConfigEnPantalla(carga); },
                ex -> { if (miVersion == versionCargaConfig) mostrarConfigEnPantalla(new CargaConfig(null, java.util.List.of())); });
    }

    private static Config nuevoConfigPorDefecto() {
        java.util.logging.Logger.getLogger(Sistema.class.getName()).log(
                java.util.logging.Level.WARNING,
                "nuevoConfigPorDefecto: Instanciando configuración referencial por defecto para arranque UI.");
        Config c = new Config();
        c.setId(1);
        c.setTasaDolar(null);
        c.setIvaPorcentaje(new BigDecimal("16.00"));
        return c;
    }

    /** Lectura de configuracion + impresoras fuera del EDT. */
    private CargaConfig cargarConfigEnFondo() {
        Config c;
        try {
            c = lgDao.datosEmpresa();
        } catch (Exception ex) {
            c = null;
        }
        java.util.List<String> impresoras;
        try {
            impresoras = Servicio.ServicioImpresionTicket.listarImpresorasDisponibles();
        } catch (Exception ex) {
            impresoras = java.util.List.of();
        }
        return new CargaConfig(c, impresoras);
    }

    private record CargaConfig(Config conf, java.util.List<String> impresoras) {}

    private void mostrarConfigEnPantalla(CargaConfig carga) {
        conf = carga.conf();
        if (conf == null) {
            conf = nuevoConfigPorDefecto();
        }
        txtIdConfig.setText("" + conf.getId());
        txtRucConfig.setText(conf.getRif() != null ? conf.getRif() : "");
        txtNombreConfig.setText(conf.getNombre() != null ? conf.getNombre() : "");
        txtTelefonoConfig.setText(conf.getTelefono() != null ? conf.getTelefono() : "");
        txtDireccionConfig.setText(conf.getDireccion() != null ? conf.getDireccion() : "");
        txtMensaje.setText(conf.getMensaje() != null ? conf.getMensaje() : "");
        if (txtTasaConfig != null) {
            txtTasaConfig.setText(conf.getTasaDolar() != null ? conf.getTasaDolar().toPlainString() : "");
        }
        if (txtIvaConfig != null) {
            txtIvaConfig.setText(conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje().toPlainString() : "");
            actualizarTextoCheckboxIvaPlato();
        }
        if (txtRutaLogo != null) {
            txtRutaLogo.setText(conf.getLogoPath() != null ? conf.getLogoPath() : "");
        }
        if (chkImprimirLogoTicket != null) {
            chkImprimirLogoTicket.setSelected(conf.isImprimirLogoTicket());
        }
        if (txtClienteDefaultNombre != null) {
            txtClienteDefaultNombre.setText(conf.getClienteDefaultNombre());
        }
        if (txtClienteDefaultDoc != null) {
            txtClienteDefaultDoc.setText(conf.getClienteDefaultDocumento());
        }
        if (spMesesRetencion != null) {
            spMesesRetencion.setValue(conf.getMesesRetencionPedidos() > 0 ? conf.getMesesRetencionPedidos() : 24);
        }
        if (cbImpresorasConfig != null) {
            refrescarImpresorasEnCombo(conf.getImpresoraTickets(), carga.impresoras());
        }
        if (conf.getImpresoraTickets() != null) {
            Servicio.ServicioImpresionTicket.setImpresoraGlobal(conf.getImpresoraTickets());
        }
        if (conf.getModoSalidaTickets() != null) {
            cambiarModoSalidaGlobal(conf.getModoSalidaTickets());
        }
        if (conf.getModoAtencion() != null) {
            sincronizarCombosModo(conf.getModoAtencion());
        }
        actualizarNombreRestaurante(conf.getNombre());
        actualizarLogoEIcono(conf.getLogoPath());
        actualizarEstadoTasaUI();
    }

    public void actualizarNombreRestaurante(String nombre) {
        String texto = (nombre != null && !nombre.trim().isEmpty()) ? nombre.trim() : "Restaurante";
        if (jLabel38 != null) {
            jLabel38.setText(texto);
            jLabel38.setToolTipText(texto);
            int longitud = texto.length();
            int tamFuente = 48;
            if (longitud > 28) {
                tamFuente = 24;
            } else if (longitud > 22) {
                tamFuente = 28;
            } else if (longitud > 16) {
                tamFuente = 34;
            } else if (longitud > 12) {
                tamFuente = 40;
            }
            jLabel38.setFont(new java.awt.Font("Zilla Slab", java.awt.Font.BOLD | java.awt.Font.ITALIC, tamFuente));
        }
        String nombreUsuario = (usuarioActual != null && usuarioActual.getNombre() != null && !usuarioActual.getNombre().isBlank())
                ? usuarioActual.getNombre() : "Usuario";
        setTitle("Panel de Administración - " + nombreUsuario + " [" + texto + "]");
    }

    public javax.swing.JLabel getLabelTituloRestaurante() {
        return jLabel38;
    }

    private void seleccionarNuevoLogo() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        javax.swing.JFileChooser selector = new javax.swing.JFileChooser();
        selector.setDialogTitle("Seleccionar Logo del Negocio");
        selector.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Imágenes (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
        int resultado = selector.showOpenDialog(this);
        if (resultado == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File archivoSeleccionado = selector.getSelectedFile();
            if (archivoSeleccionado != null && archivoSeleccionado.isFile()) {
                try {
                    java.nio.file.Path carpetaConfig = java.nio.file.Path.of("config");
                    java.nio.file.Files.createDirectories(carpetaConfig);
                    String nombreArchivo = archivoSeleccionado.getName();
                    String extension = nombreArchivo.contains(".")
                            ? nombreArchivo.substring(nombreArchivo.lastIndexOf(".")) : ".png";
                    java.nio.file.Path destino = carpetaConfig.resolve("logo_empresa" + extension);
                    java.nio.file.Files.copy(archivoSeleccionado.toPath(), destino,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    String rutaGuardada = destino.toAbsolutePath().toString();
                    if (txtRutaLogo != null) {
                        txtRutaLogo.setText(rutaGuardada);
                    }
                    actualizarLogoEIcono(rutaGuardada);
                } catch (Exception ex) {
                    String rutaDirecta = archivoSeleccionado.getAbsolutePath();
                    if (txtRutaLogo != null) {
                        txtRutaLogo.setText(rutaDirecta);
                    }
                    actualizarLogoEIcono(rutaDirecta);
                }
            }
        }
    }

    public void actualizarLogoEIcono(String rutaPersonalizada) {
        Image imagenBase = null;
        if (rutaPersonalizada != null && !rutaPersonalizada.isBlank()) {
            java.nio.file.Path p = java.nio.file.Path.of(rutaPersonalizada);
            if (java.nio.file.Files.isRegularFile(p)) {
                try {
                    imagenBase = new ImageIcon(p.toAbsolutePath().toString()).getImage();
                } catch (Exception ignored) {
                }
            }
        }
        if (imagenBase == null) {
            java.net.URL url = getClass().getResource("/Img/logo.png");
            if (url != null) {
                imagenBase = new ImageIcon(url).getImage();
            }
        }
        if (imagenBase != null) {
            try {
                this.setIconImage(imagenBase);
            } catch (Exception ignored) {
            }
            if (labelLogo != null && labelLogo.getWidth() > 0 && labelLogo.getHeight() > 0) {
                Image escalada = imagenBase.getScaledInstance(labelLogo.getWidth(), labelLogo.getHeight(), Image.SCALE_SMOOTH);
                labelLogo.setIcon(new ImageIcon(escalada));
            }
            if (lblLogoPreview != null) {
                int w = Math.max(lblLogoPreview.getWidth(), 180);
                int h = Math.max(lblLogoPreview.getHeight(), 130);
                Image previa = imagenBase.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                lblLogoPreview.setIcon(new ImageIcon(previa));
            }
        }
    }

    private void initDashboardYClientes() {
        panelDashboard = new javax.swing.JPanel(new java.awt.BorderLayout(10, 10));
        panelDashboard.setBackground(new java.awt.Color(245, 247, 250));
        panelDashboard.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // 1. Barra Superior con Título y Botón de Refresco
        javax.swing.JPanel pnlHeader = new javax.swing.JPanel(new java.awt.BorderLayout());
        pnlHeader.setOpaque(false);
        javax.swing.JLabel lblTituloDash = new javax.swing.JLabel("DASHBOARD DE VENTAS & CLIENTES");
        lblTituloDash.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 18));
        lblTituloDash.setForeground(new java.awt.Color(20, 40, 80));
        pnlHeader.add(lblTituloDash, java.awt.BorderLayout.WEST);

        javax.swing.JPanel pnlAccionesDash = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        pnlAccionesDash.setOpaque(false);

        javax.swing.JButton btnCierreXDash = new javax.swing.JButton("Cierre Parcial (X)");
        btnCierreXDash.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnCierreXDash.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        java.net.URL urlPrintDash = getClass().getResource("/Img/print.png");
        if (urlPrintDash != null) btnCierreXDash.setIcon(new javax.swing.ImageIcon(urlPrintDash));
        btnCierreXDash.setToolTipText("Generar e imprimir Cierre Parcial de Caja (Corte X)");
        btnCierreXDash.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.PARCIAL));
        pnlAccionesDash.add(btnCierreXDash);

        javax.swing.JButton btnCierreZDash = new javax.swing.JButton("Cierre Total (Z)");
        btnCierreZDash.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnCierreZDash.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        if (urlPrintDash != null) btnCierreZDash.setIcon(new javax.swing.ImageIcon(urlPrintDash));
        btnCierreZDash.setToolTipText("Generar e imprimir Cierre Total definitivo de la jornada (Corte Z)");
        btnCierreZDash.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.TOTAL));
        pnlAccionesDash.add(btnCierreZDash);

        javax.swing.JButton btnLogsDash = new javax.swing.JButton("Logs del Día");
        btnLogsDash.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnLogsDash.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        java.net.URL urlLogsDash = getClass().getResource("/Img/lupa.png");
        if (urlLogsDash != null) btnLogsDash.setIcon(new javax.swing.ImageIcon(urlLogsDash));
        btnLogsDash.setToolTipText("Consultar los logs del día, errores SQL y eventos de carga");
        btnLogsDash.addActionListener(e -> mostrarDialogoLogsDelDia());
        pnlAccionesDash.add(btnLogsDash);

        javax.swing.JButton btnRefrescar = new javax.swing.JButton("Actualizar Métricas");
        btnRefrescar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnRefrescar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        java.net.URL urlRefresh = getClass().getResource("/Img/Actualizar (2).png");
        if (urlRefresh != null) {
            btnRefrescar.setIcon(new javax.swing.ImageIcon(urlRefresh));
        }
        btnRefrescar.addActionListener(e -> cargarDashboardYClientes());
        pnlAccionesDash.add(btnRefrescar);

        pnlHeader.add(pnlAccionesDash, java.awt.BorderLayout.EAST);

        // 2. Tarjetas KPI
        javax.swing.JPanel pnlKpis = new javax.swing.JPanel(new java.awt.GridLayout(1, 4, 12, 10));
        pnlKpis.setOpaque(false);
        pnlKpis.setPreferredSize(new java.awt.Dimension(1000, 75));

        lblVentasHoy = new javax.swing.JLabel("Cargando...", javax.swing.SwingConstants.CENTER);
        lblTicketPromedio = new javax.swing.JLabel("Cargando...", javax.swing.SwingConstants.CENTER);
        lblVentasHistoricas = new javax.swing.JLabel("Cargando...", javax.swing.SwingConstants.CENTER);
        lblClientesYMesas = new javax.swing.JLabel("Cargando...", javax.swing.SwingConstants.CENTER);

        pnlKpis.add(crearCardKpi("VENTAS HOY", lblVentasHoy, new java.awt.Color(230, 248, 235), new java.awt.Color(40, 167, 69)));
        pnlKpis.add(crearCardKpi("TICKET PROMEDIO", lblTicketPromedio, new java.awt.Color(235, 245, 255), new java.awt.Color(0, 123, 255)));
        pnlKpis.add(crearCardKpi("VENTAS TOTALES", lblVentasHistoricas, new java.awt.Color(255, 245, 230), new java.awt.Color(253, 126, 20)));
        pnlKpis.add(crearCardKpi("CLIENTES Y ACTIVIDAD", lblClientesYMesas, new java.awt.Color(245, 235, 255), new java.awt.Color(111, 66, 193)));

        javax.swing.JPanel pnlNorte = new javax.swing.JPanel(new java.awt.BorderLayout(5, 8));
        pnlNorte.setOpaque(false);
        pnlNorte.add(pnlHeader, java.awt.BorderLayout.NORTH);
        pnlNorte.add(pnlKpis, java.awt.BorderLayout.CENTER);
        panelDashboard.add(pnlNorte, java.awt.BorderLayout.NORTH);

        // 3. Área Central (Split Izquierda: Top Platos | Derecha: Clientes y Facturas)
        javax.swing.JSplitPane splitPrincipal = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        splitPrincipal.setDividerLocation(300);
        splitPrincipal.setOpaque(false);

        // Panel Izquierdo: Tabbed con Top Platos y Rendimiento Mesoneros
        javax.swing.JTabbedPane tabLeftDashboard = new javax.swing.JTabbedPane();
        tabLeftDashboard.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));

        javax.swing.JPanel pnlTopPlatos = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        pnlTopPlatos.setBackground(java.awt.Color.WHITE);
        pnlTopPlatos.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 200, 200)),
                "Top Platos Más Vendidos",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13),
                new java.awt.Color(40, 40, 40)));

        tableTopPlatos = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(
                new Object[][]{}, new String[]{"Plato", "Cant.", "Total ($)", "Total (Bs.)"}
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        tableTopPlatos.setRowHeight(22);
        pnlTopPlatos.add(new javax.swing.JScrollPane(tableTopPlatos), java.awt.BorderLayout.CENTER);
        configurarTablaIndividual(tableTopPlatos);
        tabLeftDashboard.addTab("Top Platos", pnlTopPlatos);

        javax.swing.JPanel pnlMesonerosDashboard = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        pnlMesonerosDashboard.setBackground(java.awt.Color.WHITE);
        pnlMesonerosDashboard.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 200, 200)),
                "Atención por Mesonero",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13),
                new java.awt.Color(40, 40, 40)));

        tableMesonerosDashboard = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(
                new Object[][]{}, new String[]{"Mesonero / Atención", "Pedidos", "Total ($)", "Total (Bs.)"}
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        tableMesonerosDashboard.setRowHeight(22);
        pnlMesonerosDashboard.add(new javax.swing.JScrollPane(tableMesonerosDashboard), java.awt.BorderLayout.CENTER);
        configurarTablaIndividual(tableMesonerosDashboard);
        tabLeftDashboard.addTab("Mesoneros", pnlMesonerosDashboard);
        // Tope de ancho: evita scroll horizontal en pantallas de 1366 px.
        tabLeftDashboard.setPreferredSize(new java.awt.Dimension(360, 600));

        splitPrincipal.setDividerLocation(360);
        splitPrincipal.setResizeWeight(0.30);
        splitPrincipal.setLeftComponent(tabLeftDashboard);

        // Panel Derecho: Clientes y Facturas
        javax.swing.JPanel pnlClientesYFacturas = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        pnlClientesYFacturas.setOpaque(false);

        // Subpanel Clientes (Norte)
        javax.swing.JPanel pnlClientes = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        pnlClientes.setBackground(java.awt.Color.WHITE);
        pnlClientes.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 200, 200)),
                "Búsqueda y Directorio de Clientes",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13),
                new java.awt.Color(40, 40, 40)));

        javax.swing.JPanel pnlBarraBuscar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
        pnlBarraBuscar.setOpaque(false);
        pnlBarraBuscar.add(new javax.swing.JLabel("Buscar por Nombre o Cédula/RIF:"));
        txtBuscarCliente = new javax.swing.JTextField(16);
        txtBuscarCliente.addActionListener(e -> buscarYListarClientes(txtBuscarCliente.getText().trim()));
        pnlBarraBuscar.add(txtBuscarCliente);

        javax.swing.JButton btnBuscar = new javax.swing.JButton("Buscar");
        java.net.URL urlLupa = getClass().getResource("/Img/lupa.png");
        if (urlLupa != null) btnBuscar.setIcon(new javax.swing.ImageIcon(urlLupa));
        btnBuscar.addActionListener(e -> buscarYListarClientes(txtBuscarCliente.getText().trim()));
        pnlBarraBuscar.add(btnBuscar);

        javax.swing.JButton btnTodos = new javax.swing.JButton("Todos");
        btnTodos.addActionListener(e -> { txtBuscarCliente.setText(""); buscarYListarClientes(""); });
        pnlBarraBuscar.add(btnTodos);

        javax.swing.JButton btnVerFacCli = new javax.swing.JButton("Ver Facturas");
        btnVerFacCli.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnVerFacCli.addActionListener(e -> cargarFacturasClienteSeleccionado());
        pnlBarraBuscar.add(btnVerFacCli);

        pnlClientes.add(pnlBarraBuscar, java.awt.BorderLayout.NORTH);

        tableClientes = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(
                new Object[][]{}, new String[]{"Cédula / RIF", "Nombre", "Teléfono", "Facturas", "Total ($)", "Total (Bs.)"}
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        tableClientes.setRowHeight(22);
        tableClientes.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarFacturasClienteSeleccionado();
            }
        });
        pnlClientes.add(new javax.swing.JScrollPane(tableClientes), java.awt.BorderLayout.CENTER);
        configurarTablaIndividual(tableClientes);

        // Subpanel Facturas del Cliente (Sur)
        javax.swing.JPanel pnlFacturas = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        pnlFacturas.setBackground(java.awt.Color.WHITE);
        pnlFacturas.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 200, 200)),
                "Facturas del Cliente (Desde la más reciente en adelante)",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13),
                new java.awt.Color(0, 102, 153)));

        javax.swing.JPanel pnlBarraFacturas = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 4));
        pnlBarraFacturas.setOpaque(false);

        cbModoSalidaClientes = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbModoSalidaClientes.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        cbModoSalidaClientes.setToolTipText("Modo de salida del comprobante para clientes");
        cbModoSalidaClientes.addActionListener(e -> {
            if (!sincronizandoModoSalida && cbModoSalidaClientes.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
        });
        javax.swing.JLabel lblSalidaCli = new javax.swing.JLabel("Salida:");
        lblSalidaCli.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        pnlBarraFacturas.add(lblSalidaCli);
        pnlBarraFacturas.add(cbModoSalidaClientes);

        javax.swing.JButton btnPrevisualizarFacturaCliente = new javax.swing.JButton("Previsualizar Factura");
        btnPrevisualizarFacturaCliente.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnPrevisualizarFacturaCliente.setToolTipText("Pre-visualizar ticket en pantalla sin imprimir directo (opcional)");
        btnPrevisualizarFacturaCliente.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        if (urlLupa != null) btnPrevisualizarFacturaCliente.setIcon(new javax.swing.ImageIcon(urlLupa));
        btnPrevisualizarFacturaCliente.addActionListener(e -> previsualizarFacturaClienteSeleccionada());
        pnlBarraFacturas.add(btnPrevisualizarFacturaCliente);

        javax.swing.JButton btnReimprimirPdf = new javax.swing.JButton("Ver / Reimprimir PDF Factura");
        btnReimprimirPdf.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        java.net.URL urlPdf = getClass().getResource("/Img/pdf.png");
        if (urlPdf != null) btnReimprimirPdf.setIcon(new javax.swing.ImageIcon(urlPdf));
        btnReimprimirPdf.addActionListener(e -> reimprimirFacturaClienteSeleccionada());
        pnlBarraFacturas.add(btnReimprimirPdf);
        pnlFacturas.add(pnlBarraFacturas, java.awt.BorderLayout.NORTH);

        tableFacturasCliente = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(
                new Object[][]{}, new String[]{"N° Factura", "Fecha", "Sala", "Mesa", "Total ($)", "Total (Bs.)", "Atendido Por", "Estado"}
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        tableFacturasCliente.setRowHeight(22);
        pnlFacturas.add(new javax.swing.JScrollPane(tableFacturasCliente), java.awt.BorderLayout.CENTER);
        configurarTablaIndividual(tableFacturasCliente);

        javax.swing.JSplitPane splitDerecha = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, pnlClientes, pnlFacturas);
        splitDerecha.setDividerLocation(180);
        splitDerecha.setOpaque(false);
        pnlClientesYFacturas.add(splitDerecha, java.awt.BorderLayout.CENTER);

        splitPrincipal.setRightComponent(pnlClientesYFacturas);
        panelDashboard.add(splitPrincipal, java.awt.BorderLayout.CENTER);

        // Añadir Pestaña a jTabbedPane1
        jTabbedPane1.addTab("Dashboard", panelDashboard);

        // El boton Dashboard vive en el GroupLayout de la barra lateral (ver initComponents).
    }

    private javax.swing.JPanel crearCardKpi(String titulo, javax.swing.JLabel lblValor, java.awt.Color fondo, java.awt.Color borde) {
        javax.swing.JPanel card = new javax.swing.JPanel(new java.awt.BorderLayout(5, 5));
        card.setBackground(fondo);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(borde, 2, true),
                javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        javax.swing.JLabel lblT = new javax.swing.JLabel(titulo);
        lblT.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        lblT.setForeground(borde.darker());
        card.add(lblT, java.awt.BorderLayout.NORTH);
        card.add(lblValor, java.awt.BorderLayout.CENTER);
        return card;
    }

    public void cargarDashboardYClientes() {
        long version = ++versionDashboard;
        if (lblVentasHoy != null) lblVentasHoy.setText("<html><small>Cargando métricas...</small></html>");
        if (lblTicketPromedio != null) lblTicketPromedio.setText("<html><small>Cargando métricas...</small></html>");
        if (lblVentasHistoricas != null) lblVentasHistoricas.setText("<html><small>Cargando métricas...</small></html>");
        if (lblClientesYMesas != null) lblClientesYMesas.setText("<html><small>Cargando métricas...</small></html>");

        String criterio = (txtBuscarCliente != null) ? txtBuscarCliente.getText().trim() : "";
        new DashboardSwingWorker(clienteDao, criterio,
                datos -> {
                    if (version == versionDashboard) {
                        mostrarDashboardEnPantalla(datos);
                    }
                },
                error -> {
                    if (version == versionDashboard) {
                        mostrarErrorDashboard(error);
                    }
                }).execute();
    }

    private void mostrarDashboardEnPantalla(DashboardSwingWorker.DatosDashboard datos) {
        if (datos == null) return;
        Modelo.EstadisticasDashboard stats = datos.getStats();
        if (stats != null) {
            if (lblVentasHoy != null) {
                lblVentasHoy.setText("<html><b style='font-size:13px;color:#007700;'>$ " + String.format(java.util.Locale.US, "%.2f", stats.getVentasHoyDolares())
                        + "</b> &nbsp;|&nbsp; <span style='color:#003366;'>Bs. " + String.format(java.util.Locale.US, "%.2f", stats.getVentasHoyBs())
                        + "</span><br><small style='color:#555555;'>" + stats.getPedidosHoy() + " pedidos cerrados hoy</small></html>");
            }
            if (lblTicketPromedio != null) {
                lblTicketPromedio.setText("<html><b style='font-size:13px;color:#004488;'>$ " + String.format(java.util.Locale.US, "%.2f", stats.getTicketPromedioHoyDolares())
                        + "</b> &nbsp;|&nbsp; <span style='color:#003366;'>Bs. " + String.format(java.util.Locale.US, "%.2f", stats.getTicketPromedioHoyBs())
                        + "</span><br><small style='color:#555555;'>Promedio hoy (" + String.format(java.util.Locale.US, "%.2f", stats.getTicketPromedioHistoricoDolares()) + " $ global)</small></html>");
            }
            if (lblVentasHistoricas != null) {
                lblVentasHistoricas.setText("<html><b style='font-size:13px;color:#772200;'>$ " + String.format(java.util.Locale.US, "%.2f", stats.getVentasHistoricasDolares())
                        + "</b> &nbsp;|&nbsp; <span style='color:#003366;'>Bs. " + String.format(java.util.Locale.US, "%.2f", stats.getVentasHistoricasBs())
                        + "</span><br><small style='color:#555555;'>" + stats.getPedidosHistoricos() + " pedidos finalizados</small></html>");
            }
            if (lblClientesYMesas != null) {
                lblClientesYMesas.setText("<html><b style='font-size:13px;color:#440077;'>" + stats.getTotalClientes() + " Clientes</b>"
                        + "<br><small style='color:#555555;'>" + stats.getMesasOcupadasActuales() + " mesas ocupadas en vivo</small></html>");
            }

            if (tableTopPlatos != null) {
                javax.swing.table.DefaultTableModel modelPlatos = (javax.swing.table.DefaultTableModel) tableTopPlatos.getModel();
                modelPlatos.setRowCount(0);
                for (Modelo.EstadisticasDashboard.ItemEstadistica item : stats.getTopPlatos()) {
                    modelPlatos.addRow(new Object[]{
                        item.getNombre(),
                        item.getCantidad(),
                        "$ " + String.format(java.util.Locale.US, "%.2f", item.getTotalDolares()),
                        "Bs. " + String.format(java.util.Locale.US, "%.2f", item.getTotalBs())
                    });
                }
            }

            if (tableMesonerosDashboard != null && stats.getEstadisticasMesoneros() != null) {
                javax.swing.table.DefaultTableModel modelMesoneros = (javax.swing.table.DefaultTableModel) tableMesonerosDashboard.getModel();
                modelMesoneros.setRowCount(0);
                for (Modelo.EstadisticasDashboard.ItemEstadistica item : stats.getEstadisticasMesoneros()) {
                    modelMesoneros.addRow(new Object[]{
                        item.getNombre(),
                        item.getCantidad(),
                        "$ " + String.format(java.util.Locale.US, "%.2f", item.getTotalDolares()),
                        "Bs. " + String.format(java.util.Locale.US, "%.2f", item.getTotalBs())
                    });
                }
            }
        }
        mostrarClientesEnTabla(datos.getClientes());
    }

    private void mostrarClientesEnTabla(List<Modelo.Cliente> clientes) {
        if (tableClientes == null) return;
        javax.swing.table.DefaultTableModel modelCli = (javax.swing.table.DefaultTableModel) tableClientes.getModel();
        modelCli.setRowCount(0);
        if (clientes != null) {
            for (Modelo.Cliente c : clientes) {
                modelCli.addRow(new Object[]{
                    c.getDocumento(),
                    c.getNombre(),
                    c.getTelefono() != null ? c.getTelefono() : "-",
                    c.getTotalFacturas(),
                    "$ " + String.format(java.util.Locale.US, "%.2f", c.getTotalGastadoDolares()),
                    "Bs. " + String.format(java.util.Locale.US, "%.2f", c.getTotalGastadoBs())
                });
            }
        }
        if (clientes == null || clientes.isEmpty()) {
            if (tableFacturasCliente != null) {
                javax.swing.table.DefaultTableModel modelFac = (javax.swing.table.DefaultTableModel) tableFacturasCliente.getModel();
                modelFac.setRowCount(0);
            }
        }
    }

    private void mostrarErrorDashboard(Throwable error) {
        java.util.logging.Logger.getLogger(Sistema.class.getName())
                .log(java.util.logging.Level.WARNING, "Fallo al actualizar métricas del dashboard", error);
        String msg = mensajeError(error);
        if (lblVentasHoy != null) lblVentasHoy.setText("<html><span style='color:red;'>Error al cargar</span></html>");
        if (lblTicketPromedio != null) lblTicketPromedio.setText("<html><span style='color:red;'>Error al cargar</span></html>");
        if (lblVentasHistoricas != null) lblVentasHistoricas.setText("<html><span style='color:red;'>Error al cargar</span></html>");
        if (lblClientesYMesas != null) lblClientesYMesas.setText("<html><span style='color:red;'>Error al cargar</span></html>");

        String sugerencia = "";
        if (msg.contains("clientes") || msg.contains("Unknown column") || msg.contains("doesn't exist")) {
            sugerencia = "\n\nSugerencia: Ejecute 'actualizar_bd.bat' en la carpeta del sistema para sincronizar las tablas y columnas.";
        }

        JOptionPane.showMessageDialog(this, "No se pudieron actualizar las métricas del dashboard: " + msg + sugerencia,
                "Error en Dashboard", JOptionPane.WARNING_MESSAGE);
    }

    public void mostrarDialogoLogsDelDia() {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Logs del Sistema — " + hoy, true);
        dialog.setSize(950, 600);
        dialog.setMinimumSize(new java.awt.Dimension(720, 450));
        dialog.setLocationRelativeTo(this);

        javax.swing.JPanel pnlPrincipal = new javax.swing.JPanel(new java.awt.BorderLayout(10, 10));
        pnlPrincipal.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header con filtros y botones
        javax.swing.JPanel pnlTop = new javax.swing.JPanel(new java.awt.BorderLayout(8, 8));

        javax.swing.JPanel pnlFiltros = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
        pnlFiltros.add(new javax.swing.JLabel("Filtrar por:"));

        javax.swing.JComboBox<String> cmbNivel = new javax.swing.JComboBox<>(new String[]{
            "Todos los eventos",
            "Errores SQL ([SQL_ERROR])",
            "Carga de datos 0 items ([CARGA_DATOS])",
            "Solo Advertencias y Errores (WARNING/SEVERE)",
            "Fallbacks Fiscales/Monetarios"
        });
        cmbNivel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        pnlFiltros.add(cmbNivel);

        javax.swing.JTextField txtBusqueda = new javax.swing.JTextField(16);
        txtBusqueda.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        txtBusqueda.setToolTipText("Buscar texto en los logs...");
        pnlFiltros.add(new javax.swing.JLabel("Buscar:"));
        pnlFiltros.add(txtBusqueda);

        javax.swing.JPanel pnlBotones = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 4));
        javax.swing.JButton btnRecargar = new javax.swing.JButton("Actualizar");
        btnRecargar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        javax.swing.JButton btnCopiar = new javax.swing.JButton("Copiar");
        btnCopiar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        javax.swing.JButton btnCerrar = new javax.swing.JButton("Cerrar");
        btnCerrar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        pnlBotones.add(btnRecargar);
        pnlBotones.add(btnCopiar);
        pnlBotones.add(btnCerrar);

        pnlTop.add(pnlFiltros, java.awt.BorderLayout.WEST);
        pnlTop.add(pnlBotones, java.awt.BorderLayout.EAST);

        // Área central de texto
        javax.swing.JTextArea txtLogs = new javax.swing.JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        txtLogs.setBackground(new java.awt.Color(30, 30, 30));
        txtLogs.setForeground(new java.awt.Color(220, 220, 220));
        txtLogs.setCaretColor(java.awt.Color.WHITE);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(txtLogs);

        // Barra inferior de estado
        javax.swing.JLabel lblEstado = new javax.swing.JLabel("Archivo: " + infraestructura.ArchivoLogDiario.obtenerRutaLog(hoy).toAbsolutePath());
        lblEstado.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        lblEstado.setForeground(java.awt.Color.GRAY);

        pnlPrincipal.add(pnlTop, java.awt.BorderLayout.NORTH);
        pnlPrincipal.add(scroll, java.awt.BorderLayout.CENTER);
        pnlPrincipal.add(lblEstado, java.awt.BorderLayout.SOUTH);

        java.util.function.Consumer<Void> cargarLogs = (v) -> {
            List<String> lineas = infraestructura.ArchivoLogDiario.leerLineasLog(hoy);
            String filtroNivel = (String) cmbNivel.getSelectedItem();
            String query = txtBusqueda.getText().trim().toLowerCase(java.util.Locale.ROOT);

            StringBuilder sb = new StringBuilder();
            int coincidencia = 0;
            for (String linea : lineas) {
                boolean pasaNivel = true;
                if ("Errores SQL ([SQL_ERROR])".equals(filtroNivel)) {
                    pasaNivel = linea.contains("[SQL_ERROR]");
                } else if ("Carga de datos 0 items ([CARGA_DATOS])".equals(filtroNivel)) {
                    pasaNivel = linea.contains("[CARGA_DATOS]");
                } else if ("Solo Advertencias y Errores (WARNING/SEVERE)".equals(filtroNivel)) {
                    pasaNivel = linea.contains("WARNING") || linea.contains("SEVERE");
                } else if ("Fallbacks Fiscales/Monetarios".equals(filtroNivel)) {
                    pasaNivel = linea.contains("Fallback aplicado") || linea.contains("referencial");
                }

                boolean pasaTexto = query.isEmpty() || linea.toLowerCase(java.util.Locale.ROOT).contains(query);

                if (pasaNivel && pasaTexto) {
                    sb.append(linea).append("\n");
                    coincidencia++;
                }
            }
            txtLogs.setText(sb.toString());
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
            lblEstado.setText(String.format("Eventos/Líneas: %d de %d | Archivo: %s | Actualizado: %s",
                    coincidencia, lineas.size(),
                    infraestructura.ArchivoLogDiario.obtenerRutaLog(hoy).getFileName(),
                    java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        };

        btnRecargar.addActionListener(e -> cargarLogs.accept(null));
        cmbNivel.addActionListener(e -> cargarLogs.accept(null));
        txtBusqueda.addActionListener(e -> cargarLogs.accept(null));
        txtBusqueda.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { cargarLogs.accept(null); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { cargarLogs.accept(null); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { cargarLogs.accept(null); }
        });

        btnCopiar.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(txtLogs.getText());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            JOptionPane.showMessageDialog(dialog, "Contenido copiado al portapapeles.", "Logs", JOptionPane.INFORMATION_MESSAGE);
        });

        btnCerrar.addActionListener(e -> dialog.dispose());

        cargarLogs.accept(null);
        dialog.setContentPane(pnlPrincipal);
        aplicarTooltipsDefecto(dialog.getContentPane());
        dialog.setVisible(true);
    }

    public void buscarYListarClientes(String criterio) {
        long version = ++versionBusquedaClientes;
        new javax.swing.SwingWorker<List<Modelo.Cliente>, Void>() {
            @Override
            protected List<Modelo.Cliente> doInBackground() {
                return clienteDao.buscarClientes(criterio);
            }

            @Override
            protected void done() {
                if (version != versionBusquedaClientes) return;
                try {
                    mostrarClientesEnTabla(get());
                } catch (Exception ex) {
                    System.err.println("Error al buscar clientes: " + ex.getMessage());
                }
            }
        }.execute();
    }

    public void cargarFacturasClienteSeleccionado() {
        if (tableClientes == null) return;
        int fila = tableClientes.getSelectedRow();
        if (fila < 0) {
            return;
        }
        String doc = String.valueOf(tableClientes.getValueAt(fila, 0));
        long version = ++versionFacturasCliente;
        new javax.swing.SwingWorker<List<Pedidos>, Void>() {
            @Override
            protected List<Pedidos> doInBackground() {
                return clienteDao.listarFacturasCliente(doc);
            }

            @Override
            protected void done() {
                if (version != versionFacturasCliente) return;
                try {
                    List<Pedidos> facturas = get();
                    if (tableFacturasCliente != null) {
                        javax.swing.table.DefaultTableModel modelFac = (javax.swing.table.DefaultTableModel) tableFacturasCliente.getModel();
                        modelFac.setRowCount(0);
                        if (facturas != null) {
                            for (Pedidos p : facturas) {
                                modelFac.addRow(new Object[]{
                                    p.getId(),
                                    p.getFecha(),
                                    p.esPedidoRapido() ? "Pedido rápido" : p.getSala(),
                                    p.esPedidoRapido() ? "—" : p.getNum_mesa(),
                                    "$ " + (p.getTotalDecimal() != null ? p.getTotalDecimal().toPlainString() : "0.00"),
                                    "Bs. " + (p.getTotalBs() != null ? p.getTotalBs().toPlainString() : "0.00"),
                                    p.getUsuario(),
                                    p.getEstado()
                                });
                            }
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Sistema.this, "Error al listar facturas del cliente: " + mensajeError(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public void reimprimirFacturaClienteSeleccionada() {
        int fila = tableFacturasCliente.getSelectedRow();
        if (fila < 0) {
            mostrarAvisoEnBanda("Selecciona una factura de la tabla para ver/reimprimir");
            return;
        }
        int idPedido = Integer.parseInt(String.valueOf(Tables.valorModelo(tableFacturasCliente, fila, 0)));
        try {
            String motivo = solicitarMotivoAccion("Reimpresión de Factura de Cliente",
                    "Ingrese el motivo de la reimpresión del pedido #" + idPedido + ":", "Copia solicitada por cliente");
            if (motivo == null) return;
            String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
            boolean impreso = pedidosControlador.reimprimirPdfPedido(idPedido, motivo, usr);
            if (!impreso) mostrarAvisoEnBanda("PDF generado; impresión abierta en visor PDF");
            } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void previsualizarFacturaClienteSeleccionada() {
        int fila = tableFacturasCliente.getSelectedRow();
        if (fila < 0) {
            mostrarAvisoEnBanda("Selecciona una factura de la tabla para previsualizar");
            return;
        }
        int idPedido = Integer.parseInt(String.valueOf(Tables.valorModelo(tableFacturasCliente, fila, 0)));
        try {
            pedidosControlador.previsualizarPdfPedido(idPedido);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo previsualizar el PDF: " + mensajeError(ex),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ListarPedidos() {
        long version = ++versionHistorialPedidos;
        new ListaPedidosSwingWorker(pedidosControlador,
                pedidos -> {
                    if (version == versionHistorialPedidos) {
                        mostrarPedidosEnTabla(pedidos);
                    }
                }, error -> {
                    if (version == versionHistorialPedidos) {
                        mostrarErrorCargaPedidos(error);
                    }
                }).execute();
    }

    private void mostrarPedidosEnTabla(List<Pedidos> Listar) {
        Tables color = new Tables();
        modelo = (DefaultTableModel) TablePedidos.getModel();
        modelo.setRowCount(0);
        Object[] ob = new Object[7];
        for (int i = 0; i < Listar.size(); i++) {
            ob[0] = Listar.get(i).getId();
            ob[1] = Listar.get(i).esPedidoRapido() ? "Pedido rápido" : Listar.get(i).getSala();
            ob[2] = Listar.get(i).getUsuario();
            ob[3] = Listar.get(i).esPedidoRapido() ? "—" : Listar.get(i).getNum_mesa();
            ob[4] = Listar.get(i).getFecha();
            BigDecimal montoUsd = Listar.get(i).getTotalDecimal() != null ? Listar.get(i).getTotalDecimal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal montoBs = Listar.get(i).getTotalBs() != null ? Listar.get(i).getTotalBs().setScale(2, RoundingMode.HALF_UP) : null;
            String usdStr = String.format(java.util.Locale.US, "%.2f", montoUsd);
            ob[5] = montoBs != null
                    ? "Bs. " + String.format(java.util.Locale.US, "%.2f", montoBs) + " ($ " + usdStr + ")"
                    : "$ " + usdStr;
            ob[6] = Listar.get(i).getEstado();
            modelo.addRow(ob);
        }
        colorHeader(TablePedidos);
        TablePedidos.setDefaultRenderer(Object.class, color);
        TablePedidos.setAutoCreateRowSorter(true);
        aplicarFiltroHistorial();
    }

    public void filtrarHistorialHoy() {
        filtroSoloHoyActivo = !filtroSoloHoyActivo;
        if (filtroSoloHoyActivo) {
            String hoy = java.time.LocalDate.now().toString();
            if (txtFiltroFechaDesde != null) txtFiltroFechaDesde.setText(hoy);
            if (txtFiltroFechaHasta != null) txtFiltroFechaHasta.setText(hoy);
        } else {
            if (txtFiltroFechaDesde != null) txtFiltroFechaDesde.setText("");
            if (txtFiltroFechaHasta != null) txtFiltroFechaHasta.setText("");
        }
        aplicarFiltroHistorial();
    }

    public void filtrarHistorialPendientes() {
        if ("PENDIENTE".equals(filtroEstadoSeleccionado)) {
            filtroEstadoSeleccionado = null;
        } else {
            filtroEstadoSeleccionado = "PENDIENTE";
        }
        aplicarFiltroHistorial();
    }

    public void filtrarHistorialFinalizados() {
        if ("FINALIZADO".equals(filtroEstadoSeleccionado)) {
            filtroEstadoSeleccionado = null;
        } else {
            filtroEstadoSeleccionado = "FINALIZADO";
        }
        aplicarFiltroHistorial();
    }

    public void filtrarHistorialRangoFechas(String desde, String hasta) {
        filtroSoloHoyActivo = false;
        if (txtFiltroFechaDesde != null) txtFiltroFechaDesde.setText(desde != null ? desde.trim() : "");
        if (txtFiltroFechaHasta != null) txtFiltroFechaHasta.setText(hasta != null ? hasta.trim() : "");
        aplicarFiltroHistorial();
    }

    public void limpiarFiltrosHistorial() {
        filtroEstadoSeleccionado = null;
        filtroSoloHoyActivo = false;
        if (txtBuscarHistorial != null) txtBuscarHistorial.setText("");
        if (txtFiltroFechaDesde != null) txtFiltroFechaDesde.setText("");
        if (txtFiltroFechaHasta != null) txtFiltroFechaHasta.setText("");
        aplicarFiltroHistorial();
    }

    public void aplicarFiltroHistorial() {
        if (TablePedidos == null) return;
        if (!(TablePedidos.getRowSorter() instanceof javax.swing.table.TableRowSorter<?> sorter)) {
            return;
        }

        List<javax.swing.RowFilter<Object, Object>> filtros = new ArrayList<>();

        // 1. Filtro libre de texto
        if (txtBuscarHistorial != null) {
            String texto = txtBuscarHistorial.getText().trim();
            if (!texto.isEmpty()) {
                filtros.add(javax.swing.RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto)));
            }
        }

        // 2. Filtro de estado ("PENDIENTE" o "FINALIZADO") en columna 6
        if (filtroEstadoSeleccionado != null && !filtroEstadoSeleccionado.isBlank()) {
            filtros.add(javax.swing.RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(filtroEstadoSeleccionado) + "$", 6));
        }

        // 3. Filtro por fecha (Hoy o Rango) en columna 4
        if (filtroSoloHoyActivo) {
            String hoy = java.time.LocalDate.now().toString();
            filtros.add(javax.swing.RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(hoy), 4));
        } else {
            String desde = txtFiltroFechaDesde != null ? txtFiltroFechaDesde.getText().trim() : "";
            String hasta = txtFiltroFechaHasta != null ? txtFiltroFechaHasta.getText().trim() : "";
            if (!desde.isEmpty() || !hasta.isEmpty()) {
                filtros.add(crearFiltroRangoFechas(desde, hasta, 4));
            }
        }

        if (filtros.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(javax.swing.RowFilter.andFilter(filtros));
        }
    }

    private javax.swing.RowFilter<Object, Object> crearFiltroRangoFechas(String desdeStr, String hastaStr, int colFecha) {
        LocalDate parsedDesde = null;
        LocalDate parsedHasta = null;
        try {
            if (desdeStr != null && !desdeStr.isBlank()) {
                parsedDesde = LocalDate.parse(desdeStr.trim());
            }
        } catch (Exception ignored) {
        }
        try {
            if (hastaStr != null && !hastaStr.isBlank()) {
                parsedHasta = LocalDate.parse(hastaStr.trim());
            }
        } catch (Exception ignored) {
        }

        final LocalDate d = parsedDesde;
        final LocalDate h = parsedHasta;

        return new javax.swing.RowFilter<Object, Object>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                Object val = entry.getValue(colFecha);
                if (val == null) return false;
                String fechaTexto = val.toString().trim();
                if (fechaTexto.isEmpty()) return false;
                String fechaParte = fechaTexto.length() >= 10 ? fechaTexto.substring(0, 10) : fechaTexto;
                try {
                    LocalDate f = LocalDate.parse(fechaParte);
                    if (d != null && f.isBefore(d)) return false;
                    if (h != null && f.isAfter(h)) return false;
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        };
    }

    public javax.swing.JButton getBtnFiltroHoy() { return btnFiltroHoy; }
    public javax.swing.JButton getBtnFiltroPendientes() { return btnFiltroPendientes; }
    public javax.swing.JButton getBtnFiltroFinalizados() { return btnFiltroFinalizados; }
    public javax.swing.JTextField getTxtFiltroFechaDesde() { return txtFiltroFechaDesde; }
    public javax.swing.JTextField getTxtFiltroFechaHasta() { return txtFiltroFechaHasta; }
    public javax.swing.JButton getBtnLimpiarFiltrosHistorial() { return btnLimpiarFiltrosHistorial; }
    public javax.swing.JButton getBtnReimprimirHistorial() { return btnReimprimirHistorial; }
    public javax.swing.JButton getBtnPrevisualizarHistorial() { return btnPrevisualizarHistorial; }
    public javax.swing.JButton getBtnPrevisualizarPedido() { return btnPrevisualizarPedido; }
    public javax.swing.JButton getBtnPdfPedido() { return btnPdfPedido; }
    public javax.swing.JButton getBtnReactivarPlato() { return btnReactivarPlato; }
    public javax.swing.JSpinner getSpMesesRetencion() { return spMesesRetencion; }
    public javax.swing.JButton getBtnPurgarHistorial() { return btnPurgarHistorial; }
    public javax.swing.JButton getBtnCierreParcial() { return btnCierreParcial; }
    public javax.swing.JButton getBtnCierreTotal() { return btnCierreTotal; }
    public javax.swing.JCheckBox getChkImprimirLogoTicket() { return chkImprimirLogoTicket; }
    public Servicio.CierreCajaServicio getCierreCajaServicio() { return cierreCajaServicio; }
    public void setCierreCajaServicio(Servicio.CierreCajaServicio servicio) { this.cierreCajaServicio = servicio; }

    public javax.swing.JComboBox<String> getCbImpresorasConfig() { return cbImpresorasConfig; }
    public javax.swing.JComboBox<Modelo.ModoSalidaTicket> getCbModoSalidaConfig() { return cbModoSalidaConfig; }
    public javax.swing.JComboBox<Modelo.ModoSalidaTicket> getCbModoSalidaFinalizar() { return cbModoSalidaFinalizar; }
    public javax.swing.JComboBox<Modelo.ModoSalidaTicket> getCbModoSalidaHistorial() { return cbModoSalidaHistorial; }
    public javax.swing.JComboBox<Modelo.ModoSalidaTicket> getCbModoSalidaClientes() { return cbModoSalidaClientes; }
    public javax.swing.JButton getBtnRefrescarImpresoras() { return btnRefrescarImpresoras; }
    public javax.swing.JButton getBtnProbarImpresion() { return btnProbarImpresion; }
    public javax.swing.JButton getBtnAnularPedido() { return btnAnularPedido; }
    public javax.swing.JButton getBtnAuditoriaPedido() { return btnAuditoriaPedido; }
    public javax.swing.JLabel getLblBannerTasa() { return lblBannerTasa; }
    public javax.swing.JLabel getLblFechaTasaConfig() { return lblFechaTasaConfig; }
    public Servicio.ServicioRespaldoBaseDatos getServicioRespaldo() { return servicioRespaldo; }

    public void cambiarModoSalidaGlobal(Modelo.ModoSalidaTicket nuevoModo) {
        if (nuevoModo == null || sincronizandoModoSalida) {
            return;
        }
        sincronizandoModoSalida = true;
        try {
            Servicio.ServicioImpresionTicket.setModoGlobal(nuevoModo);
            if (cbModoSalidaConfig != null && cbModoSalidaConfig.getSelectedItem() != nuevoModo) {
                cbModoSalidaConfig.setSelectedItem(nuevoModo);
            }
            if (cbModoSalidaFinalizar != null && cbModoSalidaFinalizar.getSelectedItem() != nuevoModo) {
                cbModoSalidaFinalizar.setSelectedItem(nuevoModo);
            }
            if (cbModoSalidaHistorial != null && cbModoSalidaHistorial.getSelectedItem() != nuevoModo) {
                cbModoSalidaHistorial.setSelectedItem(nuevoModo);
            }
            if (cbModoSalidaClientes != null && cbModoSalidaClientes.getSelectedItem() != nuevoModo) {
                cbModoSalidaClientes.setSelectedItem(nuevoModo);
            }
        } finally {
            sincronizandoModoSalida = false;
        }
    }

    public void refrescarImpresorasEnCombo(String seleccionPrevia) {
        refrescarImpresorasEnCombo(seleccionPrevia, Servicio.ServicioImpresionTicket.listarImpresorasDisponibles());
    }

    public void refrescarImpresorasEnCombo(String seleccionPrevia, java.util.List<String> impresoras) {
        if (cbImpresorasConfig == null) {
            return;
        }
        sincronizandoImpresora = true;
        try {
            cbImpresorasConfig.removeAllItems();
            cbImpresorasConfig.addItem("DEFAULT");
            java.util.List<String> lista = impresoras != null ? impresoras : java.util.List.of();
            for (String imp : lista) {
                cbImpresorasConfig.addItem(imp);
            }
            if (seleccionPrevia != null && !seleccionPrevia.trim().isEmpty()) {
                boolean encontrado = false;
                for (int i = 0; i < cbImpresorasConfig.getItemCount(); i++) {
                    if (seleccionPrevia.equalsIgnoreCase(cbImpresorasConfig.getItemAt(i))) {
                        cbImpresorasConfig.setSelectedIndex(i);
                        encontrado = true;
                        break;
                    }
                }
                if (!encontrado && !"DEFAULT".equalsIgnoreCase(seleccionPrevia)) {
                    cbImpresorasConfig.addItem(seleccionPrevia);
                    cbImpresorasConfig.setSelectedItem(seleccionPrevia);
                }
            } else {
                cbImpresorasConfig.setSelectedIndex(0);
            }
        } finally {
            sincronizandoImpresora = false;
        }
    }

    public void probarImpresionTicket() {
        String imp = (cbImpresorasConfig != null && cbImpresorasConfig.getSelectedItem() != null)
                ? cbImpresorasConfig.getSelectedItem().toString()
                : "DEFAULT";
        Modelo.ModoSalidaTicket modo = (cbModoSalidaConfig != null && cbModoSalidaConfig.getSelectedItem() instanceof Modelo.ModoSalidaTicket m)
                ? m
                : Servicio.ServicioImpresionTicket.getModoGlobal();

        if (btnProbarImpresion != null) {
            btnProbarImpresion.setEnabled(false);
        }
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        new javax.swing.SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Servicio.ServicioImpresionTicket.imprimirTicketPrueba(imp, modo);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                if (btnProbarImpresion != null) {
                    btnProbarImpresion.setEnabled(true);
                }
                try {
                    boolean ok = get();
                    if (ok) {
                        mostrarAvisoEnBanda("Ticket de prueba enviado a " + imp + " (" + modo.getEtiqueta() + ")");
                    } else {
                        JOptionPane.showMessageDialog(Sistema.this,
                                "No se pudo completar la prueba de impresión hacia '" + imp + "'. Revise la conexión de la tickera.",
                                "Error de Impresión", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Sistema.this,
                            "Error ejecutando la prueba de impresión: " + mensajeError(ex),
                            "Error de Impresión", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public void solicitarCierreCaja(Modelo.CierreCaja.TipoCierre tipo) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;

        String fechaDefecto = java.time.LocalDate.now().toString();
        javax.swing.JTextField txtFecha = new javax.swing.JTextField(fechaDefecto, 10);
        txtFecha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 6, 6));
        String desc = tipo == Modelo.CierreCaja.TipoCierre.TOTAL
                ? "<html><b>¿Desea generar el CIERRE TOTAL (CORTE Z) del día?</b><br><small>Resume la jornada completa definitiva y totaliza ventas, impuestos y formas de pago.</small></html>"
                : "<html><b>¿Desea generar el CIERRE PARCIAL (CORTE X) del día?</b><br><small>Arqueo preliminar de caja al momento actual (cambio de turno o corte temporal).</small></html>";
        panel.add(new javax.swing.JLabel(desc));
        panel.add(new javax.swing.JLabel("Fecha de la jornada a cerrar (AAAA-MM-DD):"));
        panel.add(txtFecha);
        javax.swing.JLabel alertaFecha = new javax.swing.JLabel(" ");
        alertaFecha.setForeground(COLOR_PELIGRO);
        panel.add(alertaFecha);
        javax.swing.JTextField txtEfectivoContadoBs = new javax.swing.JTextField("", 10);
        txtEfectivoContadoBs.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        panel.add(new javax.swing.JLabel("Efectivo Contado en Gaveta (Bs. - dejar vacío para omitir):"));
        panel.add(txtEfectivoContadoBs);
        javax.swing.JLabel alertaEfectivoBs = new javax.swing.JLabel(" ");
        alertaEfectivoBs.setForeground(COLOR_PELIGRO);
        panel.add(alertaEfectivoBs);
        javax.swing.JTextField txtEfectivoContadoUsd = new javax.swing.JTextField("", 10);
        txtEfectivoContadoUsd.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        panel.add(new javax.swing.JLabel("Efectivo Contado en Gaveta ($ USD - dejar vacío para omitir):"));
        panel.add(txtEfectivoContadoUsd);
        javax.swing.JLabel alertaEfectivoUsd = new javax.swing.JLabel(" ");
        alertaEfectivoUsd.setForeground(COLOR_PELIGRO);
        panel.add(alertaEfectivoUsd);
        javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbDestinoCierre = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbDestinoCierre.setSelectedItem(Servicio.ServicioImpresionTicket.getModoGlobal());
        panel.add(new javax.swing.JLabel("Destino de Impresión:"));
        panel.add(cbDestinoCierre);

        javax.swing.JDialog dialogoCierre = new javax.swing.JDialog(this, tipo.getEtiqueta(), true);
        dialogoCierre.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dialogoCierre.add(panel, java.awt.BorderLayout.CENTER);
        javax.swing.JButton imprimir = new javax.swing.JButton("Imprimir Directo (80mm)");
        javax.swing.JButton previsualizar = new javax.swing.JButton("Previsualizar");
        javax.swing.JButton cancelar = new javax.swing.JButton("Cancelar");
        JPanel botonesCierre = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botonesCierre.add(cancelar);
        botonesCierre.add(previsualizar);
        botonesCierre.add(imprimir);
        dialogoCierre.add(botonesCierre, java.awt.BorderLayout.SOUTH);
        java.util.concurrent.atomic.AtomicInteger seleccionRef = new java.util.concurrent.atomic.AtomicInteger(-1);
        Runnable validarYContinuar = () -> {
            alertaFecha.setText(" ");
            alertaEfectivoBs.setText(" ");
            alertaEfectivoUsd.setText(" ");
            String fechaIngresada = txtFecha.getText().trim();
            try {
                java.time.LocalDate parsed = java.time.LocalDate.parse(fechaIngresada.isEmpty() ? fechaDefecto : fechaIngresada,
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                if (parsed.isAfter(java.time.LocalDate.now())) {
                    alertaFecha.setText("La fecha del cierre no puede ser futura.");
                    txtFecha.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
                    txtFecha.requestFocusInWindow();
                    return;
                }
            } catch (Exception ex) {
                alertaFecha.setText("Usa una fecha válida con formato AAAA-MM-DD.");
                txtFecha.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
                txtFecha.requestFocusInWindow();
                return;
            }
            if (!montoArqueoValido(txtEfectivoContadoBs, alertaEfectivoBs)
                    || !montoArqueoValido(txtEfectivoContadoUsd, alertaEfectivoUsd)) return;
        };
        imprimir.addActionListener(e -> {
            validarYContinuar.run();
            if (alertaFecha.getText().isBlank() && alertaEfectivoBs.getText().isBlank() && alertaEfectivoUsd.getText().isBlank()) {
                seleccionRef.set(0);
                dialogoCierre.dispose();
            }
        });
        previsualizar.addActionListener(e -> {
            validarYContinuar.run();
            if (alertaFecha.getText().isBlank() && alertaEfectivoBs.getText().isBlank() && alertaEfectivoUsd.getText().isBlank()) {
                seleccionRef.set(1);
                dialogoCierre.dispose();
            }
        });
        cancelar.addActionListener(e -> dialogoCierre.dispose());
        dialogoCierre.pack();
        dialogoCierre.setLocationRelativeTo(this);
        aplicarTooltipsDefecto(dialogoCierre.getContentPane());
        dialogoCierre.setVisible(true);
        int seleccion = seleccionRef.get();
        if (seleccion < 0) return;

        if (seleccion == 0 || seleccion == 1) {
            if (cbDestinoCierre.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
            String fecha = txtFecha.getText().trim();
            if (fecha.isEmpty()) {
                fecha = fechaDefecto;
            }
            java.time.LocalDate fechaParsed;
            try {
                fechaParsed = java.time.LocalDate.parse(fecha, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ex) {
                mostrarAlertaCampo(txtFecha, "Usa una fecha válida con formato AAAA-MM-DD.");
                return;
            }
            if (fechaParsed.isAfter(java.time.LocalDate.now())) {
                mostrarAlertaCampo(txtFecha, "La fecha del cierre no puede ser futura.");
                return;
            }

            java.math.BigDecimal efBs = null;
            String efBsTxt = txtEfectivoContadoBs.getText().trim();
            if (!efBsTxt.isEmpty()) {
                try {
                    efBs = new java.math.BigDecimal(efBsTxt.replace(',', '.'));
                    if (efBs.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        mostrarAlertaCampo(txtEfectivoContadoBs, "El efectivo contado en Bs. no puede ser negativo.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    mostrarAlertaCampo(txtEfectivoContadoBs, "Ingresa un monto válido o deja el campo vacío.");
                    return;
                }
            }

            java.math.BigDecimal efUsd = null;
            String efUsdTxt = txtEfectivoContadoUsd.getText().trim();
            if (!efUsdTxt.isEmpty()) {
                try {
                    efUsd = new java.math.BigDecimal(efUsdTxt.replace(',', '.'));
                    if (efUsd.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        mostrarAlertaCampo(txtEfectivoContadoUsd, "El efectivo contado en USD no puede ser negativo.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    mostrarAlertaCampo(txtEfectivoContadoUsd, "Ingresa un monto válido o deja el campo vacío.");
                    return;
                }
            }

            if (fechaParsed.isBefore(java.time.LocalDate.now()) && (efBs != null || efUsd != null)) {
                int confArqueoPasado = JOptionPane.showConfirmDialog(this,
                        "Está realizando un arqueo de efectivo físico para una jornada anterior (" + fecha + ").\n"
                        + "¿Desea continuar con los montos de efectivo ingresados?",
                        "Confirmar Arqueo en Fecha Pasada", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confArqueoPasado != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            final java.math.BigDecimal fEfBs = efBs;
            final java.math.BigDecimal fEfUsd = efUsd;
            String usuarioEmisor = (LabelVendedor != null && !LabelVendedor.getText().isBlank())
                    ? LabelVendedor.getText().trim() : "Sistema";
            boolean esImpresionDirecta = (seleccion == 0);

            final String fFecha = fecha;
            new javax.swing.SwingWorker<Servicio.CierreCajaServicio.ResultadoCierre, Void>() {
                @Override
                protected Servicio.CierreCajaServicio.ResultadoCierre doInBackground() throws Exception {
                    if (cierreCajaServicio == null) {
                        cierreCajaServicio = new Servicio.CierreCajaServicio(
                                new Modelo.CierreCajaDao(),
                                () -> conf != null ? conf : lgDao.datosEmpresa(),
                                new Servicio.GeneradorPdfCierre());
                    }
                    Servicio.CierreCajaServicio.ResultadoCierre resultadoGenerado;
                    try {
                        if (esImpresionDirecta) {
                            resultadoGenerado = cierreCajaServicio.imprimirCierre(fFecha, tipo, usuarioEmisor, fEfBs, fEfUsd);
                        } else {
                            resultadoGenerado = cierreCajaServicio.previsualizarCierre(fFecha, tipo, usuarioEmisor, fEfBs, fEfUsd);
                        }
                    } finally {
                        if (tipo == Modelo.CierreCaja.TipoCierre.TOTAL) {
                            try {
                                servicioRespaldo.crearRespaldoAutomaticoSiEsNecesario();
                            } catch (Exception ex) {
                                java.util.logging.Logger.getLogger(Sistema.class.getName())
                                        .log(java.util.logging.Level.WARNING, "Aviso creando respaldo automático al cierre Z: " + ex.getMessage());
                            }
                        }
                    }
                    return resultadoGenerado;
                }

                @Override
                protected void done() {
                    try {
                        Servicio.CierreCajaServicio.ResultadoCierre resultado = get();
                        if (esImpresionDirecta) {
                            Modelo.ModoSalidaTicket modoActual = Servicio.ServicioImpresionTicket.getModoGlobal();
                            String detalleDestino;
                            if (resultado.impresoDirecto()) {
                                detalleDestino = switch (modoActual) {
                                    case TERMICA_DIRECTA -> "enviado a la impresora térmica (" + Servicio.ServicioImpresionTicket.getImpresoraGlobal() + ").";
                                    case PDF24_CREATOR -> "despachado a PDF24 Creator.";
                                    case VISOR_PDF -> "abierto en el visor de documentos PDF.";
                                };
                            } else {
                                detalleDestino = "falló el envío a la impresora, degradado a visor PDF.";
                            }
                            mostrarAvisoEnBanda("Cierre " + tipo.getEtiqueta() + " procesado (" + detalleDestino + ")");
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(Sistema.this,
                                "No se pudo completar el " + tipo.getEtiqueta() + ": " + mensajeError(ex),
                                "Error en Cierre de Caja", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private boolean montoArqueoValido(JTextField campo, JLabel alerta) {
        String texto = campo.getText().trim();
        if (texto.isEmpty()) return true;
        try {
            BigDecimal monto = new BigDecimal(texto.replace(',', '.'));
            if (monto.signum() < 0 || monto.scale() > 2) throw new NumberFormatException();
            return true;
        } catch (NumberFormatException ex) {
            alerta.setText("Ingresa un monto no negativo con hasta dos decimales, o deja el campo vacío.");
            campo.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
            campo.requestFocusInWindow();
            return false;
        }
    }

    void purgarHistorialPedidos() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION) || !politicaAcceso.esAdministrador()) {
            JOptionPane.showMessageDialog(this, "Solo los administradores pueden purgar el historial de pedidos.",
                    "Acceso Denegado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int meses = 24;
        if (spMesesRetencion != null) {
            meses = (Integer) spMesesRetencion.getValue();
        }
        if (meses < 1) {
            mostrarAlertaCampo(spMesesRetencion, "El período de retención debe ser de al menos 1 mes.");
            return;
        }

        // Diálogo 1: Confirmación de purga irreversible
        int pregunta = JOptionPane.showConfirmDialog(this,
                "¿Eliminar pedidos finalizados anteriores a " + meses + " meses? Esta acción es irreversible.",
                "Confirmar Purga Histórica", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (pregunta != JOptionPane.YES_OPTION) {
            return;
        }

        // Diálogo 2: Contraseña de administrador obligatoria
        javax.swing.JPasswordField pwd = new javax.swing.JPasswordField(18);
        javax.swing.JDialog dialogoClave = new javax.swing.JDialog(this, "Autorización de administrador", true);
        dialogoClave.setLayout(new java.awt.BorderLayout(8, 8));
        JPanel panelClave = new JPanel(new java.awt.BorderLayout(6, 6));
        panelClave.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 4, 12));
        panelClave.add(new JLabel("Ingrese la contraseña de administrador para autorizar la purga:"), java.awt.BorderLayout.NORTH);
        panelClave.add(pwd, java.awt.BorderLayout.CENTER);
        JLabel alertaClave = new JLabel(" ");
        alertaClave.setForeground(COLOR_PELIGRO);
        panelClave.add(alertaClave, java.awt.BorderLayout.SOUTH);
        dialogoClave.add(panelClave, java.awt.BorderLayout.CENTER);
        JButton confirmarClave = new JButton("Autorizar purga");
        JButton cancelarClave = new JButton("Cancelar");
        JPanel botonesClave = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botonesClave.add(cancelarClave);
        botonesClave.add(confirmarClave);
        dialogoClave.add(botonesClave, java.awt.BorderLayout.SOUTH);
        java.util.concurrent.atomic.AtomicBoolean claveConfirmada = new java.util.concurrent.atomic.AtomicBoolean();
        confirmarClave.addActionListener(e -> {
            char[] pass = pwd.getPassword();
            boolean vacio = pass.length == 0 || LoginDao.esBlanco(pass);
            java.util.Arrays.fill(pass, '\0');
            if (vacio) {
                alertaClave.setText("La contraseña es obligatoria.");
                pwd.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
                pwd.requestFocusInWindow();
                return;
            }
            claveConfirmada.set(true);
            dialogoClave.dispose();
        });
        cancelarClave.addActionListener(e -> {
            pwd.setText("");
            dialogoClave.dispose();
        });
        dialogoClave.getRootPane().setDefaultButton(confirmarClave);
        dialogoClave.pack();
        dialogoClave.setLocationRelativeTo(this);
        aplicarTooltipsDefecto(dialogoClave.getContentPane());
        dialogoClave.setVisible(true);
        if (!claveConfirmada.get()) {
            pwd.setText("");
            return;
        }

        char[] claveChars = pwd.getPassword();
        pwd.setText("");
        try {
            AutenticacionServicio auth = new AutenticacionServicio(lgDao);
            String correoAdmin = (usuarioActual != null && usuarioActual.getCorreo() != null)
                    ? usuarioActual.getCorreo().trim() : "";
            if (correoAdmin.isEmpty()) {
                String inputCorreo = JOptionPane.showInputDialog(this,
                        "Confirme el usuario o correo del administrador:", "Identificación de Administrador",
                        JOptionPane.QUESTION_MESSAGE);
                if (inputCorreo == null || inputCorreo.trim().isEmpty()) {
                    return;
                }
                correoAdmin = inputCorreo.trim();
            }

            java.util.Optional<Usuario> usuarioAuth = auth.autenticar(correoAdmin, claveChars);
            if (usuarioAuth.isEmpty() || !"Administrador".equalsIgnoreCase(usuarioAuth.get().getRol())) {
                JOptionPane.showMessageDialog(this, "Contraseña incorrecta o usuario sin permisos de administrador.",
                        "Autenticación Fallida", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Fallo al verificar credenciales: " + mensajeError(ex),
                    "Error de Seguridad", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            java.util.Arrays.fill(claveChars, '\0');
        }

        final int mesesFinal = meses;
        if (btnPurgarHistorial != null) {
            btnPurgarHistorial.setEnabled(false);
        }

        new javax.swing.SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return pedidosControlador.purgarPedidosFinalizados(mesesFinal);
            }

            @Override
            protected void done() {
                if (btnPurgarHistorial != null) {
                    btnPurgarHistorial.setEnabled(politicaAcceso.esAdministrador());
                }
                try {
                    int eliminados = get();
                    mostrarAvisoEnBanda("Purga completada: " + eliminados + " pedidos finalizados eliminados");
                    if (jTabbedPane1.getSelectedIndex() == 5) {
                        ListarPedidos();
                    }
                    RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                    RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Sistema.this,
                            "No se pudo completar la purga: " + mensajeError(ex),
                            "Error en Purga", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void mostrarDialogoReactivarPlatos() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            return;
        }
        List<Platos> inactivos = platosControlador.listarInactivos();
        if (inactivos == null || inactivos.isEmpty()) {
            mostrarAvisoEnBanda("No hay platos desactivados para reactivar");
            return;
        }

        javax.swing.JDialog dialogo = new javax.swing.JDialog(this, "Reactivar Platos Inactivos", true);
        dialogo.setSize(520, 360);
        dialogo.setLocationRelativeTo(this);
        dialogo.setLayout(new java.awt.BorderLayout(10, 10));

        javax.swing.table.DefaultTableModel modeloInactivos = new javax.swing.table.DefaultTableModel(
                new Object[]{"ID", "Nombre", "Precio", "Desactivado En"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Platos p : inactivos) {
            String fechaBaja = p.getDesactivadoEn() != null ? p.getDesactivadoEn().format(dtf) : "-";
            modeloInactivos.addRow(new Object[]{
                    p.getId(),
                    p.getNombre(),
                    p.getPrecioDecimal(),
                    fechaBaja
            });
        }

        javax.swing.JTable tablaInactivos = new javax.swing.JTable(modeloInactivos);
        tablaInactivos.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaInactivos.setRowHeight(24);
        Tables.ocultarColumnaModelo(tablaInactivos, 0); // ID interno oculto, sigue en el modelo
        java.util.Map<Integer, String> monedaInact = new java.util.HashMap<>();
        for (int i = 0; i < inactivos.size(); i++) {
            monedaInact.put(i, ub(inactivos.get(i).getMonedaBase()));
        }
        final java.util.Map<Integer, String> monedaInactFija = java.util.Collections.unmodifiableMap(monedaInact);
        Tables.agregarColumnaMoneda(tablaInactivos, 2, mf -> monedaInactFija.getOrDefault(mf, ""));
        Tables.aplicarAlineacionNumerica(tablaInactivos);
        java.util.List<String> monedasInact = new java.util.ArrayList<>();
        for (Platos p : inactivos) {
            monedasInact.add(p.getMonedaBase());
        }
        Tables.fijarCabeceraMoneda(tablaInactivos, 2, monedasInact);
        if (tablaInactivos.getRowCount() > 0) {
            tablaInactivos.setRowSelectionInterval(0, 0);
        }

        javax.swing.JPanel panelBotones = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 10));
        javax.swing.JButton btnConfirmar = new javax.swing.JButton("Reactivar Seleccionado");
        btnConfirmar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        javax.swing.JButton btnCancelar = new javax.swing.JButton("Cerrar");

        btnConfirmar.addActionListener(e -> {
            int fila = tablaInactivos.getSelectedRow();
            if (fila < 0) {
                JOptionPane.showMessageDialog(dialogo, "Seleccione un plato de la lista para reactivar.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int id = (int) Tables.valorModelo(tablaInactivos, fila, 0);
            String nombrePlato = Tables.valorModelo(tablaInactivos, fila, 1).toString();
            try {
                if (platosControlador.reactivar(id)) {
                    dialogo.dispose();
                    LimpiarPlatos();
                    ListarPlatos(TablePlatos, "");
                    RefrescoVistas.notificar(RefrescoVistas.PLATOS);
                    mostrarAvisoEnBanda("Plato reactivado: " + nombrePlato);
                } else {
                    JOptionPane.showMessageDialog(dialogo, "No se pudo reactivar el plato.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialogo, "Error al reactivar el plato: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCancelar.addActionListener(e -> dialogo.dispose());

        panelBotones.add(btnConfirmar);
        panelBotones.add(btnCancelar);

        javax.swing.JLabel lblTitulo = new javax.swing.JLabel(" Seleccione el plato que desea reactivar en el menú:");
        lblTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        lblTitulo.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 4, 10));

        dialogo.add(lblTitulo, java.awt.BorderLayout.NORTH);
        dialogo.add(new javax.swing.JScrollPane(tablaInactivos), java.awt.BorderLayout.CENTER);
        configurarTablaIndividual(tablaInactivos);
        dialogo.add(panelBotones, java.awt.BorderLayout.SOUTH);

        aplicarTooltipsDefecto(dialogo.getContentPane());
        dialogo.setVisible(true);
    }

    private void mostrarErrorCargaPedidos(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudo cargar el historial. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar historial", JOptionPane.ERROR_MESSAGE);
    }

    public void actualizarEstadoTasaUI() {
        try {
            configServicio.recargar();
            BigDecimal tasa = configServicio.getDecimal(Modelo.ConfigClaves.TASA_DOLAR, null);
            if (tasa == null || tasa.compareTo(BigDecimal.ZERO) <= 0) {
                if (lblBannerTasa != null) {
                    lblBannerTasa.setBackground(new java.awt.Color(248, 215, 218));
                    lblBannerTasa.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                            javax.swing.BorderFactory.createLineBorder(new java.awt.Color(220, 53, 69), 1),
                            javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    ));
                    lblBannerTasa.setText("<html><b style='color:#721c24;font-size:11px;'>⚠️ TASA NO CONFIGURADA</b><br>"
                            + "<span style='font-size:12px;font-weight:bold;color:#721c24;'>Configure la tasa en Ajustes</span></html>");
                }
                return;
            }
            java.sql.Timestamp ts = configServicio.getFechaActualizacionTasaDolar();
            boolean desactualizada = configServicio.esTasaDolarDesactualizada();
            String tasaStr = String.format(java.util.Locale.US, "%.2f", tasa);
            String fechaStr = ts != null
                    ? ts.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "No registrada";

            if (lblBannerTasa != null) {
                if (desactualizada) {
                    lblBannerTasa.setBackground(new java.awt.Color(255, 243, 205));
                    lblBannerTasa.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                            javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 193, 7), 1),
                            javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    ));
                    lblBannerTasa.setText("<html><b style='color:#856404;font-size:11px;'>⚠️ TASA NO ACTUALIZADA HOY</b><br>"
                            + "<span style='font-size:12px;font-weight:bold;color:#111111;'>Bs. " + tasaStr + " / $</span> "
                            + "<small style='color:#6c757d;'>(Últ: " + fechaStr + ")</small></html>");
                } else {
                    lblBannerTasa.setBackground(new java.awt.Color(235, 247, 238));
                    lblBannerTasa.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                            javax.swing.BorderFactory.createLineBorder(new java.awt.Color(40, 167, 69), 1),
                            javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)
                    ));
                    lblBannerTasa.setText("<html><b style='color:#155724;font-size:11px;'>💵 TASA ACTIVA (INGRESADA HOY)</b><br>"
                            + "<span style='font-size:12px;font-weight:bold;color:#111111;'>Bs. " + tasaStr + " / $</span> "
                            + "<small style='color:#6c757d;'>(Act: " + fechaStr + ")</small></html>");
                }
            }

            if (lblFechaTasaConfig != null) {
                if (desactualizada) {
                    lblFechaTasaConfig.setText("⚠️ Act: " + fechaStr + " (Sin actualizar hoy)");
                    lblFechaTasaConfig.setForeground(new java.awt.Color(180, 40, 40));
                } else {
                    lblFechaTasaConfig.setText("Actualizada: " + fechaStr);
                    lblFechaTasaConfig.setForeground(new java.awt.Color(40, 120, 40));
                }
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Sistema.class.getName())
                    .log(java.util.logging.Level.FINE, "Error al actualizar estado visual de tasa: " + ex.getMessage());
        }
    }

    private javax.swing.JPanel crearPanelRespaldoConfig() {
        javax.swing.JPanel panel = new javax.swing.JPanel(null);
        panel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.JLabel lblTitulo = new javax.swing.JLabel("Copias de Seguridad y Restauración de Base de Datos");
        lblTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        lblTitulo.setBounds(25, 20, 520, 25);
        panel.add(lblTitulo);

        javax.swing.JTextArea txtDesc = new javax.swing.JTextArea(
                "Proteja toda la información operativa del restaurante (pedidos, clientes, platos, "
                + "cierres y auditoría) ante posibles cortes de energía, fallas de equipo o errores de disco.\n\n"
                + "• Respaldo Manual: Genera un archivo SQL completo con toda la estructura y registros.\n"
                + "• Respaldo Automático: Se efectúa automáticamente cada día al realizar el Cierre Total (Corte Z).\n"
                + "• Restauración: Permite recuperar la base de datos a partir de cualquier archivo de respaldo .sql."
        );
        txtDesc.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        txtDesc.setForeground(new java.awt.Color(60, 60, 60));
        txtDesc.setEditable(false);
        txtDesc.setOpaque(false);
        txtDesc.setLineWrap(true);
        txtDesc.setWrapStyleWord(true);
        txtDesc.setBounds(25, 55, 525, 110);
        panel.add(txtDesc);

        javax.swing.JButton btnCrear = new javax.swing.JButton("Crear Respaldo Ahora (.sql)");
        btnCrear.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnCrear.setBounds(25, 180, 230, 42);
        btnCrear.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnCrear.addActionListener(e -> ejecutarCreacionRespaldo());
        panel.add(btnCrear);

        javax.swing.JButton btnRestaurar = new javax.swing.JButton("Restaurar Copia (.sql)");
        btnRestaurar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnRestaurar.setBounds(275, 180, 230, 42);
        btnRestaurar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnRestaurar.addActionListener(e -> ejecutarRestauracionRespaldo());
        panel.add(btnRestaurar);

        javax.swing.JLabel lblRuta = new javax.swing.JLabel("Los respaldos se almacenan de forma organizada en la carpeta 'respaldos/'.");
        lblRuta.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 11));
        lblRuta.setForeground(new java.awt.Color(110, 110, 110));
        lblRuta.setBounds(25, 235, 520, 20);
        panel.add(lblRuta);

        return panel;
    }

    private javax.swing.JPanel crearPanelMesonerosConfig() {
        javax.swing.JPanel panel = new javax.swing.JPanel(null);
        panel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.JLabel lblTitulo = new javax.swing.JLabel("Gestión de Mesoneros / Personal de Atención");
        lblTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        lblTitulo.setBounds(15, 10, 400, 20);
        panel.add(lblTitulo);

        txtMesoneroId = new javax.swing.JTextField();
        txtMesoneroId.setVisible(false);

        javax.swing.JLabel lblNom = new javax.swing.JLabel("Nombre Completo *:");
        lblNom.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        lblNom.setBounds(15, 35, 130, 18);
        panel.add(lblNom);

        txtMesoneroNombre = new javax.swing.JTextField();
        txtMesoneroNombre.setBounds(15, 55, 190, 26);
        panel.add(txtMesoneroNombre);

        javax.swing.JLabel lblCed = new javax.swing.JLabel("Cédula / Documento *:");
        lblCed.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        lblCed.setBounds(215, 35, 140, 18);
        panel.add(lblCed);

        txtMesoneroCedula = new javax.swing.JTextField();
        txtMesoneroCedula.setBounds(215, 55, 140, 26);
        panel.add(txtMesoneroCedula);

        javax.swing.JLabel lblTel = new javax.swing.JLabel("Teléfono:");
        lblTel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        lblTel.setBounds(365, 35, 100, 18);
        panel.add(lblTel);

        txtMesoneroTelefono = new javax.swing.JTextField();
        txtMesoneroTelefono.setBounds(365, 55, 120, 26);
        panel.add(txtMesoneroTelefono);

        chkMesoneroActivo = new javax.swing.JCheckBox("Activo", true);
        chkMesoneroActivo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        chkMesoneroActivo.setBackground(java.awt.Color.WHITE);
        chkMesoneroActivo.setToolTipText("Desmarcar si sale de vacaciones o está temporalmente inactivo");
        chkMesoneroActivo.setBounds(495, 55, 75, 26);
        panel.add(chkMesoneroActivo);

        javax.swing.JButton btnGuardar = new javax.swing.JButton("Guardar");
        btnGuardar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnGuardar.setBounds(15, 90, 95, 28);
        btnGuardar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnGuardar.addActionListener(e -> guardarMesoneroConfig());
        panel.add(btnGuardar);

        javax.swing.JButton btnModificar = new javax.swing.JButton("Modificar");
        btnModificar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnModificar.setBounds(118, 90, 95, 28);
        btnModificar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnModificar.addActionListener(e -> modificarMesoneroConfig());
        panel.add(btnModificar);

        javax.swing.JButton btnVacaciones = new javax.swing.JButton("Vacaciones");
        btnVacaciones.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnVacaciones.setToolTipText("Alternar estado Activo / Vacaciones del mesonero seleccionado");
        btnVacaciones.setBounds(221, 90, 110, 28);
        btnVacaciones.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnVacaciones.addActionListener(e -> alternarVacacionesMesoneroConfig());
        panel.add(btnVacaciones);

        javax.swing.JButton btnEliminar = new javax.swing.JButton("Eliminar");
        btnEliminar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnEliminar.setForeground(java.awt.Color.WHITE);
        btnEliminar.setBackground(COLOR_PELIGRO);
        btnEliminar.setContentAreaFilled(true);
        btnEliminar.setOpaque(true);
        btnEliminar.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btnEliminar.setToolTipText("Eliminar lógicamente (soft-delete)");
        btnEliminar.setBounds(339, 90, 95, 28);
        btnEliminar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnEliminar.addActionListener(e -> eliminarMesoneroConfig());
        panel.add(btnEliminar);

        javax.swing.JButton btnNuevo = new javax.swing.JButton("Nuevo");
        btnNuevo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnNuevo.setBounds(442, 90, 85, 28);
        btnNuevo.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnNuevo.addActionListener(e -> limpiarFormularioMesonero());
        panel.add(btnNuevo);

        tableMesonerosConfig = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(
                new Object[][]{},
                new String[]{"ID", "Nombre Completo", "Cédula", "Teléfono", "Estado"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        });
        tableMesonerosConfig.setRowHeight(22);
        tableMesonerosConfig.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                seleccionarMesoneroDeTabla();
            }
        });

        javax.swing.JScrollPane scrollTabla = new javax.swing.JScrollPane(tableMesonerosConfig);
        scrollTabla.setBounds(15, 130, 545, 310);
        panel.add(scrollTabla);
        configurarTablaIndividual(tableMesonerosConfig);
        // Scroll proporcional al tamano del panel (el panel usa layout nulo).
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = panel.getWidth();
                int h = panel.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                scrollTabla.setBounds(15, 130, Math.max(300, w - 30), Math.max(200, h - 145));
                scrollTabla.validate();
            }
        });

        listarMesonerosConfig();
        return panel;
    }

    private void listarMesonerosConfig() {
        if (tableMesonerosConfig == null) return;
        DefaultTableModel model = (DefaultTableModel) tableMesonerosConfig.getModel();
        model.setRowCount(0);
        try {
            List<Mesonero> lista = mesoneroDao.listarTodos();
            for (Mesonero m : lista) {
                model.addRow(new Object[]{
                    m.getId(),
                    m.getNombreCompleto(),
                    m.getCedula(),
                    m.getTelefono() != null ? m.getTelefono() : "",
                    m.isActivo() ? "Activo" : "Vacaciones / Inactivo"
                });
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Sistema.class.getName())
                    .log(java.util.logging.Level.WARNING, "Error al listar mesoneros: " + ex.getMessage());
        }
    }

    private void limpiarFormularioMesonero() {
        if (txtMesoneroId != null) txtMesoneroId.setText("");
        if (txtMesoneroNombre != null) txtMesoneroNombre.setText("");
        if (txtMesoneroCedula != null) txtMesoneroCedula.setText("");
        if (txtMesoneroTelefono != null) txtMesoneroTelefono.setText("");
        if (chkMesoneroActivo != null) chkMesoneroActivo.setSelected(true);
        if (tableMesonerosConfig != null) tableMesonerosConfig.clearSelection();
    }

    private void seleccionarMesoneroDeTabla() {
        if (tableMesonerosConfig == null) return;
        int fila = tableMesonerosConfig.getSelectedRow();
        if (fila >= 0) {
            int modelRow = tableMesonerosConfig.convertRowIndexToModel(fila);
            Object id = tableMesonerosConfig.getModel().getValueAt(modelRow, 0);
            Object nom = tableMesonerosConfig.getModel().getValueAt(modelRow, 1);
            Object ced = tableMesonerosConfig.getModel().getValueAt(modelRow, 2);
            Object tel = tableMesonerosConfig.getModel().getValueAt(modelRow, 3);
            Object est = tableMesonerosConfig.getModel().getValueAt(modelRow, 4);

            if (txtMesoneroId != null) txtMesoneroId.setText(id != null ? id.toString() : "");
            if (txtMesoneroNombre != null) txtMesoneroNombre.setText(nom != null ? nom.toString() : "");
            if (txtMesoneroCedula != null) txtMesoneroCedula.setText(ced != null ? ced.toString() : "");
            if (txtMesoneroTelefono != null) txtMesoneroTelefono.setText(tel != null ? tel.toString() : "");
            if (chkMesoneroActivo != null) chkMesoneroActivo.setSelected(est != null && est.toString().startsWith("Activo"));
        }
    }

    private void guardarMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String nombre = txtMesoneroNombre.getText().trim();
        String cedula = txtMesoneroCedula.getText().trim();
        String telefono = txtMesoneroTelefono.getText().trim();
        boolean activo = chkMesoneroActivo.isSelected();

        if (nombre.isEmpty()) {
            mostrarAlertaCampo(txtMesoneroNombre, "El nombre completo del mesonero es obligatorio.");
            return;
        }
        if (cedula.isEmpty()) {
            mostrarAlertaCampo(txtMesoneroCedula, "La cédula o documento del mesonero es obligatorio.");
            return;
        }

        try {
            Mesonero m = new Mesonero(nombre, cedula, telefono, activo);
            mesoneroDao.registrar(m);
            mostrarAvisoEnBanda("Mesonero '" + nombre + "' registrado");
            limpiarFormularioMesonero();
            listarMesonerosConfig();
            cargarComboMesoneros();
            RefrescoVistas.notificar(RefrescoVistas.CONFIG);
            RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al registrar mesonero: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void modificarMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String idStr = txtMesoneroId.getText().trim();
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un mesonero para modificar");
            return;
        }
        int id = Integer.parseInt(idStr);
        String nombre = txtMesoneroNombre.getText().trim();
        String cedula = txtMesoneroCedula.getText().trim();
        String telefono = txtMesoneroTelefono.getText().trim();
        boolean activo = chkMesoneroActivo.isSelected();

        if (nombre.isEmpty() || cedula.isEmpty()) {
            mostrarAlertaCampo(nombre.isEmpty() ? txtMesoneroNombre : txtMesoneroCedula,
                    nombre.isEmpty() ? "El nombre completo es obligatorio." : "La cédula o documento es obligatorio.");
            return;
        }

        try {
            Mesonero m = new Mesonero(id, nombre, cedula, telefono, activo, false);
            boolean ok = mesoneroDao.modificar(m);
            if (ok) {
                mostrarAvisoEnBanda("Mesonero actualizado");
                limpiarFormularioMesonero();
                listarMesonerosConfig();
                cargarComboMesoneros();
                RefrescoVistas.notificar(RefrescoVistas.CONFIG);
                RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo actualizar el mesonero.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al modificar mesonero: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void alternarVacacionesMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String idStr = txtMesoneroId.getText().trim();
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un mesonero para cambiar su estado");
            return;
        }
        int id = Integer.parseInt(idStr);
        boolean actual = chkMesoneroActivo.isSelected();
        boolean nuevo = !actual;

        try {
            boolean ok = mesoneroDao.cambiarActivo(id, nuevo);
            if (ok) {
                String estadoStr = nuevo ? "Activo / Disponible" : "De Vacaciones / Inactivo";
                mostrarAvisoEnBanda("Estado del mesonero: " + estadoStr);
                chkMesoneroActivo.setSelected(nuevo);
                listarMesonerosConfig();
                cargarComboMesoneros();
                RefrescoVistas.notificar(RefrescoVistas.CONFIG);
                RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al alternar estado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminarMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String idStr = txtMesoneroId.getText().trim();
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un mesonero para eliminar");
            return;
        }
        int id = Integer.parseInt(idStr);
        String nombre = txtMesoneroNombre.getText().trim();

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de eliminar al mesonero '" + nombre + "'?\nNo se borrarán los pedidos históricos que atendió.",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                boolean ok = mesoneroDao.eliminarLogico(id);
                if (ok) {
                    mostrarAvisoEnBanda("Mesonero eliminado");
                    limpiarFormularioMesonero();
                    listarMesonerosConfig();
                    cargarComboMesoneros();
                    RefrescoVistas.notificar(RefrescoVistas.CONFIG);
                    RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar mesonero: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Acción explícita para dejar el pedido en edición sin mesonero (con confirmación). */
    private void quitarMesoneroAsignadoPedido() {
        int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
                "¿Quitar al mesonero asignado? Al guardar, el pedido quedará sin mesonero.",
                "Quitar mesonero", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (confirm != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }
        idMesoneroEdicionOriginal = null;
        nombreMesoneroEdicionOriginal = null;
        if (cbMesoneroPedido != null) {
            cbMesoneroPedido.setSelectedIndex(0);
        }
        quitarMesoneroEdicion = true;
        mostrarAvisoEnBanda("Asignación de mesonero retirada; se guardará sin mesonero.");
    }

    private void cargarComboMesoneros() {
        if (cbMesoneroPedido == null) return;
        cbMesoneroPedido.removeAllItems();
        cbMesoneroPedido.addItem("- Sin Mesonero Asignado -");
        try {
            List<Mesonero> activos = mesoneroDao.listarActivos();
            for (Mesonero m : activos) {
                cbMesoneroPedido.addItem(m);
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Sistema.class.getName())
                    .log(java.util.logging.Level.WARNING, "Error al cargar mesoneros activos: " + ex.getMessage());
        }
        cbMesoneroPedido.setSelectedIndex(0);
        if (panelSplitScreen != null) {
            panelSplitScreen.recargarMesoneros();
        }
    }

    void ejecutarCreacionRespaldo() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;

        new javax.swing.SwingWorker<java.nio.file.Path, Void>() {
            @Override
            protected java.nio.file.Path doInBackground() throws Exception {
                return servicioRespaldo.crearRespaldo();
            }

            @Override
            protected void done() {
                try {
                    java.nio.file.Path p = get();
                    long tamBytes = java.nio.file.Files.size(p);
                    mostrarAvisoEnBanda("Respaldo creado: " + p.getFileName() + " (" + (tamBytes / 1024) + " KB)");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Sistema.this,
                            "No se pudo crear el respaldo: " + mensajeError(ex),
                            "Error de Respaldo", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void ejecutarRestauracionRespaldo() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION) || !politicaAcceso.esAdministrador()) {
            JOptionPane.showMessageDialog(this, "Solo los administradores pueden restaurar la base de datos.",
                    "Acceso Denegado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        javax.swing.JFileChooser fc = new javax.swing.JFileChooser(servicioRespaldo.getDirectorioRespaldos().toFile());
        fc.setDialogTitle("Seleccionar archivo de respaldo SQL para restaurar");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos SQL (*.sql)", "sql"));
        int res = fc.showOpenDialog(this);
        if (res != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }

        java.io.File archivoSel = fc.getSelectedFile();
        if (archivoSel == null || !archivoSel.exists()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "⚠️ ¡ADVERTENCIA DE SEGURIDAD!\n\n"
                + "Restaurar reemplazará las tablas y datos actuales de la base de datos\n"
                + "por la información contenida en el archivo:\n" + archivoSel.getName() + "\n\n"
                + "¿Está seguro de continuar con la restauración?",
                "Confirmar Restauración de BD", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        new javax.swing.SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                servicioRespaldo.restaurarRespaldo(archivoSel.toPath());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    mostrarAvisoEnBanda("Base de datos restaurada: " + archivoSel.getName());
                    ListarConfig();
                    ListarPedidos();
                    ListarPlatos(TablePlatos, "");
                    panelSalas();
                    actualizarEstadoTasaUI();
                    ListarUsuarios();
                    cargarCategorias();
                    RefrescoVistas.notificar(RefrescoVistas.DASHBOARD);
                    RefrescoVistas.notificar(RefrescoVistas.PLATOS);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Sistema.this,
                            "No se pudo completar la restauración: " + mensajeError(ex),
                            "Error de Restauración", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void anularPedidoSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un pedido de la tabla para anular");
            return;
        }
        int id = Integer.parseInt(idStr);
        String motivo = solicitarMotivoAnulacion(id);
        if (motivo == null) return;
        int confirmacion = JOptionPane.showConfirmDialog(this,
                "¿Confirma anular el pedido #" + id + "? Esta acción no se puede deshacer.",
                "Confirmar anulación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmacion != JOptionPane.YES_OPTION) return;

        String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
        try {
            boolean exito = pedidosControlador.anularPedido(id, motivo.trim(), usr);
            if (exito) {
                mostrarAvisoEnBanda("Pedido #" + id + " anulado");
                ListarPedidos();
                RefrescoVistas.notificar(RefrescoVistas.HISTORIAL);
                RefrescoVistas.notificar(RefrescoVistas.PEDIDOS);
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo anular el pedido #" + id + ".", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al anular pedido: " + mensajeError(ex), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String solicitarMotivoAnulacion(int idPedido) {
        javax.swing.JDialog dialogo = new javax.swing.JDialog(this, "Motivo de anulación", true);
        dialogo.setLayout(new java.awt.BorderLayout(8, 8));
        JPanel contenido = new JPanel(new java.awt.BorderLayout(6, 6));
        contenido.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 4, 12));
        contenido.add(new JLabel("Indique el motivo para anular el pedido #" + idPedido + ":"), java.awt.BorderLayout.NORTH);
        JTextField campoMotivo = new JTextField(32);
        contenido.add(campoMotivo, java.awt.BorderLayout.CENTER);
        JLabel alerta = new JLabel(" ");
        alerta.setForeground(COLOR_PELIGRO);
        alerta.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        contenido.add(alerta, java.awt.BorderLayout.SOUTH);
        dialogo.add(contenido, java.awt.BorderLayout.CENTER);
        JButton aceptar = new JButton("Continuar");
        JButton cancelar = new JButton("Cancelar");
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.add(cancelar);
        acciones.add(aceptar);
        dialogo.add(acciones, java.awt.BorderLayout.SOUTH);
        java.util.concurrent.atomic.AtomicReference<String> resultado = new java.util.concurrent.atomic.AtomicReference<>();
        aceptar.addActionListener(e -> {
            if (campoMotivo.getText().trim().isEmpty()) {
                alerta.setText("El motivo es obligatorio.");
                campoMotivo.setBorder(javax.swing.BorderFactory.createLineBorder(COLOR_PELIGRO, 2));
                campoMotivo.requestFocusInWindow();
                return;
            }
            resultado.set(campoMotivo.getText().trim());
            dialogo.dispose();
        });
        cancelar.addActionListener(e -> dialogo.dispose());
        dialogo.getRootPane().setDefaultButton(aceptar);
        dialogo.pack();
        dialogo.setLocationRelativeTo(this);
        aplicarTooltipsDefecto(dialogo.getContentPane());
        dialogo.setVisible(true);
        return resultado.get();
    }

    void mostrarAuditoriaPedidoSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            mostrarAvisoEnBanda("Seleccione un pedido de la tabla para ver su auditoría");
            return;
        }
        int id = Integer.parseInt(idStr);
        List<Modelo.AuditoriaPedido> lista = pedidosControlador.obtenerAuditoriaPedido(id);
        if (lista == null || lista.isEmpty()) {
            mostrarAvisoEnBanda("No hay eventos de auditoría para el pedido #" + id);
            return;
        }

        javax.swing.JDialog dlg = new javax.swing.JDialog(this, "Auditoría de Acciones - Pedido #" + id, true);
        dlg.setSize(620, 320);
        dlg.setLocationRelativeTo(this);

        String[] cols = {"Fecha / Hora", "Acción", "Usuario", "Motivo"};
        javax.swing.table.DefaultTableModel tm = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Modelo.AuditoriaPedido a : lista) {
            tm.addRow(new Object[]{a.getFechaHoraFormateada(), a.getAccion(), a.getUsuario(), a.getMotivo()});
        }
        javax.swing.JTable tablaAud = new javax.swing.JTable(tm);
        tablaAud.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        tablaAud.setRowHeight(22);
        dlg.add(new javax.swing.JScrollPane(tablaAud));
        configurarTablaIndividual(tablaAud);
        aplicarTooltipsDefecto(dlg.getContentPane());        dlg.setVisible(true);
    }

    private java.util.function.Function<String, String> proveedorMotivoAccion = null;

    void setProveedorMotivoAccion(java.util.function.Function<String, String> proveedor) {
        this.proveedorMotivoAccion = proveedor;
    }

    private String solicitarMotivoAccion(String titulo, String mensaje, String valorDefecto) {
        if (proveedorMotivoAccion != null) {
            String resp = proveedorMotivoAccion.apply(titulo);
            return (resp != null && !resp.trim().isEmpty()) ? resp.trim() : valorDefecto;
        }
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return valorDefecto;
        }
        Object res = JOptionPane.showInputDialog(this, mensaje, titulo, JOptionPane.QUESTION_MESSAGE, null, null, valorDefecto);
        if (res == null) return null;
        String m = res.toString().trim();
        return m.isEmpty() ? valorDefecto : m;
    }

    private void ListarUsuarios() {
        final long miVersion = ++versionCargaUsuarios;
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                () -> lgDao.ListarUsuarios(),
                lista -> { if (miVersion == versionCargaUsuarios) mostrarUsuariosEnTabla(lista); },
                ex -> { if (miVersion == versionCargaUsuarios) mostrarErrorCargaUsuarios(ex); });
    }

    private void mostrarUsuariosEnTabla(List<Usuario> Listar) {
        modelo = (DefaultTableModel) TableUsuarios.getModel();
        modelo.setRowCount(0);
        Object[] ob = new Object[5];
        for (int i = 0; i < Listar.size(); i++) {
            Usuario u = Listar.get(i);
            ob[0] = u.getId();
            ob[1] = u.getNombre();
            ob[2] = u.getCorreo();
            ob[3] = u.getRol();
            ob[4] = u.isActivo() ? "Activo" : "Inactivo";
            modelo.addRow(ob);
        }
        colorHeader(TableUsuarios);
        actualizarEstadoBotonesUsuarios();
    }

    private void initGestionUsuarios() {
        if (TableUsuarios == null) return;

        TableUsuarios.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                actualizarEstadoBotonesUsuarios();
            }
        });

        if (TableUsuarios.getModel().getColumnCount() >= 5) {
            int vistaEstado = TableUsuarios.convertColumnIndexToView(4);
            if (vistaEstado >= 0) {
                TableUsuarios.getColumnModel().getColumn(vistaEstado).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public java.awt.Component getTableCellRendererComponent(
                        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (!isSelected) {
                        if ("Inactivo".equalsIgnoreCase(String.valueOf(value))) {
                            c.setForeground(new java.awt.Color(180, 40, 40));
                            c.setFont(c.getFont().deriveFont(java.awt.Font.ITALIC | java.awt.Font.BOLD));
                        } else {
                            c.setForeground(new java.awt.Color(20, 120, 40));
                            c.setFont(c.getFont().deriveFont(java.awt.Font.BOLD));
                        }
                    }
                    setHorizontalAlignment(CENTER);
                    return c;
                }
            });
            }
        }

        javax.swing.JPopupMenu popupUsuarios = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem itemCambiarClave = new javax.swing.JMenuItem("Cambiar contraseña");
        itemCambiarClave.addActionListener(e -> cambiarContrasenaUsuarioSeleccionado());
        javax.swing.JMenuItem itemCambiarEstado = new javax.swing.JMenuItem("Desactivar cuenta");
        itemCambiarEstado.addActionListener(e -> alternarEstadoUsuarioSeleccionado());
        javax.swing.JMenuItem itemEliminar = new javax.swing.JMenuItem("Eliminar cuenta");
        itemEliminar.addActionListener(e -> eliminarUsuarioSeleccionado());
        popupUsuarios.add(itemCambiarClave);
        popupUsuarios.add(itemCambiarEstado);
        popupUsuarios.add(itemEliminar);

        TableUsuarios.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                evaluarPopup(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                evaluarPopup(e);
            }
            private void evaluarPopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int r = TableUsuarios.rowAtPoint(e.getPoint());
                    if (r >= 0 && r < TableUsuarios.getRowCount()) {
                        TableUsuarios.setRowSelectionInterval(r, r);
                    } else {
                        TableUsuarios.clearSelection();
                    }
                    actualizarEstadoBotonesUsuarios();
                    if (TableUsuarios.getSelectedRow() >= 0 && politicaAcceso != null && politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) {
                        int sel = TableUsuarios.getSelectedRow();
                        Object est = Tables.valorModelo(TableUsuarios, sel, 4);
                        boolean act = est == null || !"Inactivo".equalsIgnoreCase(est.toString());
                        itemCambiarEstado.setText(act ? "Desactivar cuenta" : "Reactivar cuenta");
                        popupUsuarios.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    public void actualizarEstadoBotonesUsuarios() {
        if (btnEstadoUsuario == null || btnEliminarUsuario == null || TableUsuarios == null) {
            return;
        }
        int fila = TableUsuarios.getSelectedRow();
        boolean hayFila = fila >= 0 && fila < TableUsuarios.getRowCount();
        boolean puedeGestionar = politicaAcceso != null && politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_USUARIOS);
        if (btnCambiarClaveUsuario != null) {
            btnCambiarClaveUsuario.setEnabled(puedeGestionar && hayFila);
        }
        if (!puedeGestionar || !hayFila) {
            btnEstadoUsuario.setEnabled(false);
            btnEliminarUsuario.setEnabled(false);
            btnEstadoUsuario.setText("Desactivar");
            return;
        }
        Object estadoObj = Tables.valorModelo(TableUsuarios, fila, 4);
        boolean estaActivo = estadoObj == null || !"Inactivo".equalsIgnoreCase(estadoObj.toString());
        btnEstadoUsuario.setEnabled(true);
        btnEstadoUsuario.setText(estaActivo ? "Desactivar" : "Reactivar");
        btnEliminarUsuario.setEnabled(true);
    }

    public void cambiarContrasenaUsuarioSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        int fila = TableUsuarios.getSelectedRow();
        if (fila < 0 || fila >= TableUsuarios.getRowCount()) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idUsuario = Integer.parseInt(Tables.valorModelo(TableUsuarios, fila, 0).toString());
        String nombre = Tables.valorModelo(TableUsuarios, fila, 1).toString();

        JPasswordField pwd = new JPasswordField(20);
        JPasswordField pwdConfirm = new JPasswordField(20);
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Nueva contraseña para '" + nombre + "' (mínimo " + LoginDao.LONGITUD_MINIMA_CONTRASENA + " caracteres):"));
        panel.add(ControlVisibilidadPassword.crearPanelPassword(pwd, null));
        panel.add(new JLabel("Confirmar contraseña:"));
        panel.add(ControlVisibilidadPassword.crearPanelPassword(pwdConfirm, null));

        int opt = JOptionPane.showConfirmDialog(this, panel, "Cambiar contraseña de usuario",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) {
            pwd.setText("");
            pwdConfirm.setText("");
            return;
        }

        char[] pass1 = pwd.getPassword();
        char[] pass2 = pwdConfirm.getPassword();
        pwd.setText("");
        pwdConfirm.setText("");

        try {
            if (pass1.length == 0 || LoginDao.esBlanco(pass1)) {
                JOptionPane.showMessageDialog(this, "La contraseña es obligatoria.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (LoginDao.contarLongitudSinEspacios(pass1) < LoginDao.LONGITUD_MINIMA_CONTRASENA) {
                JOptionPane.showMessageDialog(this, "La contraseña debe tener al menos " + LoginDao.LONGITUD_MINIMA_CONTRASENA + " caracteres.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!java.util.Arrays.equals(pass1, pass2)) {
                JOptionPane.showMessageDialog(this, "Las contraseñas no coinciden.", "Validación", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                boolean exito = lgDao.cambiarContrasena(idUsuario, pass1);
                if (exito) {
                    mostrarAvisoEnBanda("Contraseña actualizada con éxito para " + nombre);
                } else {
                    JOptionPane.showMessageDialog(this, "No se pudo actualizar la contraseña.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (ErrorAplicacionException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validación de contraseña", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al actualizar contraseña: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } finally {
            java.util.Arrays.fill(pass1, '\0');
            java.util.Arrays.fill(pass2, '\0');
        }
    }

    public void alternarEstadoUsuarioSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        int fila = TableUsuarios.getSelectedRow();
        if (fila < 0 || fila >= TableUsuarios.getRowCount()) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idUsuario = Integer.parseInt(Tables.valorModelo(TableUsuarios, fila, 0).toString());
        String nombre = Tables.valorModelo(TableUsuarios, fila, 1).toString();
        Object estadoObj = Tables.valorModelo(TableUsuarios, fila, 4);
        boolean estaActivo = estadoObj == null || !"Inactivo".equalsIgnoreCase(estadoObj.toString());

        String accion = estaActivo ? "desactivar" : "reactivar";
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "¿Estás seguro de que deseas " + accion + " al usuario '" + nombre + "'?",
                "Confirmar " + accion,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean exito = estaActivo ? lgDao.desactivarUsuario(idUsuario) : lgDao.reactivarUsuario(idUsuario);
            if (exito) {
                mostrarAvisoEnBanda("Usuario " + (estaActivo ? "desactivado" : "reactivado") + " con éxito");
                ListarUsuarios();
                RefrescoVistas.notificar(RefrescoVistas.USUARIOS);
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo " + accion + " el usuario.", "Operación no realizada", JOptionPane.WARNING_MESSAGE);
            }
        } catch (ErrorAplicacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso de seguridad", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al " + accion + " usuario: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void eliminarUsuarioSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        int fila = TableUsuarios.getSelectedRow();
        if (fila < 0 || fila >= TableUsuarios.getRowCount()) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idUsuario = Integer.parseInt(Tables.valorModelo(TableUsuarios, fila, 0).toString());
        String nombre = Tables.valorModelo(TableUsuarios, fila, 1).toString();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "¿Estás seguro de que deseas ELIMINAR permanentemente al usuario '" + nombre + "'?\nEsta acción no se puede deshacer.",
                "Confirmar eliminación de usuario",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean exito = lgDao.eliminarUsuario(idUsuario);
            if (exito) {
                mostrarAvisoEnBanda("Usuario eliminado con éxito");
                ListarUsuarios();
                RefrescoVistas.notificar(RefrescoVistas.USUARIOS);
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo eliminar el usuario.", "Operación no realizada", JOptionPane.WARNING_MESSAGE);
            }
        } catch (ErrorAplicacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso de seguridad", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al eliminar usuario: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String formatearEtiquetaIva(boolean aplicaIva, BigDecimal ivaPorcentaje) {
        if (!aplicaIva) {
            return "Exento (0%)";
        }
        BigDecimal pct = ivaPorcentaje != null ? ivaPorcentaje
                : (conf != null && conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : null);
        if (pct == null) {
            return "Sí";
        }
        String pctStr = (pct.scale() <= 0 || pct.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0)
                ? String.valueOf(pct.intValue())
                : pct.stripTrailingZeros().toPlainString();
        return "Sí (" + pctStr + "%)";
    }

    public String formatearEtiquetaIva(boolean aplicaIva) {
        return formatearEtiquetaIva(aplicaIva, null);
    }

    public String textoCheckboxIvaPlato() {
        BigDecimal pct = (conf != null && conf.getIvaPorcentaje() != null) ? conf.getIvaPorcentaje() : null;
        if (pct == null) {
            return "Aplica IVA";
        }
        String pctStr = (pct.scale() <= 0 || pct.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0)
                ? String.valueOf(pct.intValue())
                : pct.stripTrailingZeros().toPlainString();
        return "Aplica IVA (" + pctStr + "%)";
    }

    public void actualizarTextoCheckboxIvaPlato() {
        if (chkAplicaIvaPlato != null) {
            chkAplicaIvaPlato.setText(textoCheckboxIvaPlato());
        }
    }

    private void mostrarErrorCargaUsuarios(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudieron cargar los usuarios. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar usuarios", JOptionPane.ERROR_MESSAGE);
    }

    private void ListarSalas() {
        new ListaSalasSwingWorker(salasControlador, this::mostrarSalasEnTabla,
                this::mostrarErrorCargaSalas).execute();
    }

    private void mostrarSalasEnTabla(List<Salas> Listar) {
        modelo = (DefaultTableModel) tableSala.getModel();
        modelo.setRowCount(0);
        boolean tieneColumnaTipo = modelo.getColumnCount() >= 4;
        Object[] ob = new Object[tieneColumnaTipo ? 4 : 3];
        for (int i = 0; i < Listar.size(); i++) {
            Salas s = Listar.get(i);
            salaTipoMap.put(s.getId(), s.getTipo());
            ob[0] = s.getId();
            ob[1] = s.getNombre();
            ob[2] = s.getMesas();
            if (tieneColumnaTipo) {
                ob[3] = s.esBarra() ? "Barra" : "Salón";
            }
            modelo.addRow(ob);
        }
        colorHeader(tableSala);

    }

    private void mostrarErrorCargaSalas(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudieron cargar las salas. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar salas", JOptionPane.ERROR_MESSAGE);
    }

    private void colorHeader(JTable tabla) {
        JTableHeader header = tabla.getTableHeader();
        header.setOpaque(false);
        header.setBackground(new Color(0, 110, 255));
        header.setForeground(Color.white);
    }

    /**
     * Hace que todas las columnas de la tabla sean redimensionables por el usuario
     * y que ocupen el 100 % del ancho del viewport (AUTO_RESIZE_ALL_COLUMNS reparte
     * el espacio según los preferredWidth; los min/max se respetan y el scroll
     * vertical sigue disponible en su JScrollPane).
     */
    public static void configurarTablaIndividual(javax.swing.JTable tabla) {
        if (tabla == null) {
            return;
        }
        tabla.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);
        javax.swing.table.TableColumnModel columnas = tabla.getColumnModel();
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            columnas.getColumn(i).setResizable(true);
        }
        Tables.aplicarAlineacionNumerica(tabla);
        java.awt.Container padre = tabla.getParent();
        while (padre != null && !(padre instanceof javax.swing.JScrollPane)) {
            padre = padre.getParent();
        }
        if (padre instanceof javax.swing.JScrollPane scroll) {
            scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        }
    }

    /** Recorre el arbol de componentes y aplica {@link #configurarTablaIndividual} a cada JTable. */
    private void configurarTodasLasTablas(java.awt.Container raiz) {
        if (raiz == null) {
            return;
        }
        for (java.awt.Component c : raiz.getComponents()) {
            if (c instanceof javax.swing.JTable tabla) {
                configurarTablaIndividual(tabla);
            }
            if (c instanceof java.awt.Container contenedor) {
                configurarTodasLasTablas(contenedor);
            }
        }
    }

    /** Centra horizontalmente el texto de todos los botones de la barra lateral izquierda. */
    private void centrarTextoBarraLateral() {
        centrarBotonesDe(jPanel1);
    }

    private static void centrarBotonesDe(java.awt.Container contenedor) {
        if (contenedor == null) {
            return;
        }
        for (java.awt.Component c : contenedor.getComponents()) {
            if (c instanceof javax.swing.AbstractButton boton) {
                boton.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            }
            if (c instanceof java.awt.Container hijo) {
                centrarBotonesDe(hijo);
            }
        }
    }

    private void LimpiarSala() {
        txtIdSala.setText("");
        txtNombreSala.setText("");
        txtMesas.setText("");
        if (cbTipoSala != null) {
            cbTipoSala.setSelectedIndex(0);
        }
        if (jLabel19 != null) {
            jLabel19.setText("Mesas:");
        }
    }

    private void LimpiarPlatos() {
        txtIdPlato.setText("");
        txtNombrePlato.setText("");
        txtPrecioPlato.setText("");
        if (cbMonedaBasePlato != null) {
            cbMonedaBasePlato.setSelectedItem("USD");
        }
        if (chkAplicaIvaPlato != null) {
            chkAplicaIvaPlato.setSelected(true);
        }
        if (chkPrecioIncluyeIvaPlato != null) {
            chkPrecioIncluyeIvaPlato.setSelected(false);
            chkPrecioIncluyeIvaPlato.setEnabled(true);
        }
        if (lblPrecioReferenciaPlato != null) {
            lblPrecioReferenciaPlato.setText("Ref: ---");
        }
    }

    public void actualizarPrecioReferenciaPlato() {
        if (lblPrecioReferenciaPlato == null || txtPrecioPlato == null || cbMonedaBasePlato == null) {
            return;
        }
        String texto = txtPrecioPlato.getText().trim();
        if (texto.isEmpty()) {
            lblPrecioReferenciaPlato.setText("Ref: ---");
            return;
        }
        try {
            BigDecimal precio = importeMonetario(texto);
            BigDecimal tasa = getTasaActual();
            if (tasa == null || tasa.signum() <= 0) {
                lblPrecioReferenciaPlato.setText("Ref: (sin tasa)");
                return;
            }
            String moneda = String.valueOf(cbMonedaBasePlato.getSelectedItem());
            if ("VES".equalsIgnoreCase(moneda)) {
                BigDecimal refUsd = precio.divide(tasa, 2, RoundingMode.HALF_UP);
                lblPrecioReferenciaPlato.setText("Ref: $ " + String.format(java.util.Locale.US, "%.2f", refUsd));
            } else {
                BigDecimal refVes = precio.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                lblPrecioReferenciaPlato.setText("Ref: Bs. " + String.format(java.util.Locale.US, "%.2f", refVes));
            }
        } catch (Exception ex) {
            lblPrecioReferenciaPlato.setText("Ref: ---");
        }
        reflejarFormularioEnFilaPlato();
    }

    /**
     * Sincroniza los detalles editados con la fila del plato (detalles→tabla, solo vista).
     * La persistencia real sigue ocurriendo en Guardar/Editar + refresco de catálogo.
     */
    private void reflejarFormularioEnFilaPlato() {
        if (sincronizandoPlatoForm || TablePlatos == null || txtIdPlato == null) {
            return;
        }
        String idTxt = txtIdPlato.getText().trim();
        if (idTxt.isEmpty() || !(TablePlatos.getModel() instanceof DefaultTableModel m)) {
            return;
        }
        int filaModelo = -1;
        for (int i = 0; i < m.getRowCount(); i++) {
            Object v = m.getValueAt(i, 0);
            if (v != null && idTxt.equals(v.toString())) {
                filaModelo = i;
                break;
            }
        }
        if (filaModelo < 0) {
            return;
        }
        sincronizandoPlatoForm = true;
        try {
            if (txtNombrePlato != null) {
                m.setValueAt(txtNombrePlato.getText().trim(), filaModelo, 1);
            }
            String actual = String.valueOf(m.getValueAt(filaModelo, 2));
            String sufijo = actual.contains("[!]") ? " [!]" : "";
            String moneda = (cbMonedaBasePlato != null && cbMonedaBasePlato.getSelectedItem() != null)
                    ? String.valueOf(cbMonedaBasePlato.getSelectedItem()) : "USD";
            String numTxt = txtPrecioPlato != null ? txtPrecioPlato.getText().trim() : "";
            String precioCelda;
            try {
                precioCelda = ("VES".equalsIgnoreCase(moneda) ? "Bs. " : "$ ")
                        + String.format(java.util.Locale.US, "%.2f", importeMonetario(numTxt)) + sufijo;
            } catch (Exception ex) {
                precioCelda = numTxt + sufijo;
            }
            m.setValueAt(precioCelda, filaModelo, 2);
            if (m.getColumnCount() > 3) {
                boolean aplica = chkAplicaIvaPlato == null || chkAplicaIvaPlato.isSelected();
                boolean incluye = aplica && chkPrecioIncluyeIvaPlato != null && chkPrecioIncluyeIvaPlato.isSelected();
                m.setValueAt(formatearEtiquetaIva(aplica) + (incluye ? " (Inc.)" : ""), filaModelo, 3);
            }
        } finally {
            sincronizandoPlatoForm = false;
        }
    }

    public javax.swing.JComboBox<String> getCbMonedaBasePlato() {
        return cbMonedaBasePlato;
    }

    public javax.swing.JCheckBox getChkPrecioIncluyeIvaPlato() {
        return chkPrecioIncluyeIvaPlato;
    }

    public javax.swing.JLabel getLblPrecioReferenciaPlato() {
        return lblPrecioReferenciaPlato;
    }

    public javax.swing.JCheckBox getChkAplicaIvaPlato() {
        return chkAplicaIvaPlato;
    }

    public javax.swing.JTextField getTxtNombrePlato() {
        return txtNombrePlato;
    }

    public javax.swing.JTextField getTxtIdPlato() {
        return txtIdPlato;
    }

    public javax.swing.JTextField getTxtPrecioPlato() {
        return txtPrecioPlato;
    }

    public javax.swing.JButton getBtnGuardarPlato() {
        return btnGuardarPlato;
    }

    public javax.swing.JButton getBtnEditarPlato() {
        return btnEditarPlato;
    }

    private void panelSalas() {
        new ListaSalasSwingWorker(salasControlador, this::mostrarSalasEnPanel,
                this::mostrarErrorCargaSalas).execute();
    }

    private void abrirPedidoRapido() {
        if (!autorizar(PoliticaAcceso.Accion.REGISTRAR_PEDIDOS)) return;
        LimpiarTableMenu();
        modoPedidoRapido = true;
        idPedidoEdicion = -1;
        txtTempIdSala.setText("0");
        txtTempNumMesa.setText("0");
        txtComentario.setText("");
        txtBuscarPlato.setText("");
        actualizarCabeceraMesaDirecta(0, "Pedido Rápido", 0, "RÁPIDO");
        jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(COLOR_CABECERA, 1),
                "PEDIDO RÁPIDO | PARA LLEVAR",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14), COLOR_CABECERA));
        btnGenerarPedido.setText(politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)
                ? "Cobrar y finalizar" : "Enviar a cocina");
        cargarComboMesoneros();
        ListarPlatos(tblTemPlatos, "");
        jTabbedPane1.setSelectedIndex(3);
        txtBuscarPlato.requestFocusInWindow();
    }

    private void mostrarSalasEnPanel(List<Salas> Listar) {
        this.salasCache = new ArrayList<>(Listar);
        this.salaIdAPrefijo.clear();
        this.salaIdAPrefijo.putAll(ServicioMesa.generarPrefijosSalas(Listar));
        this.prefijoASalaId.clear();
        this.prefijoASalaId.putAll(ServicioMesa.invertirPrefijos(this.salaIdAPrefijo));
        refrescarBarraSalasMesas();
        if (panelSplitScreen != null) {
            panelSplitScreen.actualizarSalas(Listar, this.salaIdAPrefijo);
        }

        PanelSalas.removeAll();
        Map<Integer, Integer> ocupadasPorSala = Collections.emptyMap();
        try {
            ocupadasPorSala = pedidosControlador.contarMesasOcupadasPorSala();
        } catch (Exception e) {
            // Si la consulta falla o no hay permisos, continuar con 0
        }
        for (int i = 0; i < Listar.size(); i++) {
            Salas s = Listar.get(i);
            int id = s.getId();
            String nombre = s.getNombre();
            int cantidad = s.getMesas();
            boolean esBarra = s.esBarra();
            salaTipoMap.put(id, s.getTipo());

            int ocupadas = ocupadasPorSala.getOrDefault(id, 0);
            String unidad = esBarra ? "puestos" : "ocupadas";
            String colorOcupacion = ocupadas >= cantidad ? "#B42828" : (ocupadas == 0 ? "#1C7430" : "#B1580E");
            String nombreHtml = nombre.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            javax.swing.ImageIcon iconoSala = new javax.swing.ImageIcon(getClass().getResource("/Img/salas.png"));
            java.awt.Image imgSala = iconoSala.getImage().getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH);
            JButton boton = new JButton();
            boton.setIcon(new javax.swing.ImageIcon(imgSala));
            boton.setText("<html><center><b>" + nombreHtml + "</b><br><span style='color:" + colorOcupacion + ";'>"
                    + ocupadas + "/" + cantidad + " " + unidad + "</span></center></html>");
            boton.setToolTipText(nombre + (esBarra ? " [BARRA]" : "") + " - " + ocupadas + " de " + cantidad + " " + (esBarra ? "puestos ocupados" : "mesas ocupadas"));
            boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            boton.setHorizontalTextPosition(JButton.CENTER);
            boton.setVerticalTextPosition(JButton.BOTTOM);
            boton.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            boton.setBackground(new Color(255, 255, 255));
            boton.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(COLOR_CABECERA, 1),
                    javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            boton.setFocusPainted(false);
            PanelSalas.add(boton);
            boton.addActionListener((ActionEvent e) -> {
                panelMesas(id, nombre, cantidad, esBarra);
                jTabbedPane1.setSelectedIndex(2);
            });
        }
        PanelSalas.revalidate();
        PanelSalas.repaint();
        ajustarColumnasSalas();
    }

    /** Ancho objetivo de cada tarjeta de sala para el calculo proporcional de columnas. */
    private static final int ANCHO_TARJETA_SALA_OBJETIVO = 220;

    /**
     * Rejilla proporcional de salas: las columnas se calculan como porcentaje del
     * ancho visible (ancho viewport / ancho objetivo), limitadas al numero de salas.
     * Asi en pantallas angostas salen 2 o mas filas y en pantallas anchas las tarjetas
     * se reparten el 100 % del ancho sin cortarse. Si hay mas salas de las que caben,
     * el scroll vertical las muestra todas.
     */
    private void ajustarColumnasSalas() {
        if (PanelSalas == null || jScrollPane8 == null || jScrollPane8.getViewport() == null) {
            return;
        }
        int ancho = jScrollPane8.getViewport().getExtentSize().width;
        if (ancho <= 0) {
            ancho = jScrollPane8.getWidth();
        }
        if (ancho <= 0) {
            return;
        }
        int n = PanelSalas.getComponentCount();
        if (n == 0) {
            return;
        }
        int cols = Math.max(1, ancho / ANCHO_TARJETA_SALA_OBJETIVO);
        cols = Math.min(cols, n);
        if (PanelSalas.getLayout() instanceof java.awt.GridLayout rejilla && rejilla.getColumns() != cols) {
            rejilla.setColumns(cols);
            PanelSalas.revalidate();
        }
    }

    private void initIvaPlatosYBarraSalas() {
        // 1. Selector moneda, referencia convertida y checkboxes IVA en formulario de Platos (Tab 2)
        if (jPanel11 != null) {
            javax.swing.JLabel lblMoneda = new javax.swing.JLabel("Moneda:");
            lblMoneda.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            jPanel11.add(lblMoneda, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 145, 60, 25));

            cbMonedaBasePlato = new javax.swing.JComboBox<>(new String[]{"USD", "VES"});
            cbMonedaBasePlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
            cbMonedaBasePlato.setBackground(java.awt.Color.WHITE);
            cbMonedaBasePlato.setToolTipText("Moneda principal fijada para este producto");
            cbMonedaBasePlato.addActionListener(e -> actualizarPrecioReferenciaPlato());
            jPanel11.add(cbMonedaBasePlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 140, 75, 25));

            lblPrecioReferenciaPlato = new javax.swing.JLabel("Ref: ---");
            lblPrecioReferenciaPlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.ITALIC, 11));
            lblPrecioReferenciaPlato.setForeground(new java.awt.Color(90, 90, 90));
            lblPrecioReferenciaPlato.setToolTipText("Precio convertido referencialmente con la tasa activa del sistema");
            jPanel11.add(lblPrecioReferenciaPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 140, 105, 25));

            chkAplicaIvaPlato = new javax.swing.JCheckBox(textoCheckboxIvaPlato(), true);
            chkAplicaIvaPlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
            chkAplicaIvaPlato.setBackground(new java.awt.Color(204, 204, 204));
            chkAplicaIvaPlato.setOpaque(false);
            chkAplicaIvaPlato.setFocusable(false);
            chkAplicaIvaPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            chkAplicaIvaPlato.setToolTipText("Marcar si el plato genera IVA o desmarcar si está exento");
            jPanel11.add(chkAplicaIvaPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 215, 140, 25));

            chkPrecioIncluyeIvaPlato = new javax.swing.JCheckBox("Incluye IVA", false);
            chkPrecioIncluyeIvaPlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
            chkPrecioIncluyeIvaPlato.setBackground(new java.awt.Color(204, 204, 204));
            chkPrecioIncluyeIvaPlato.setOpaque(false);
            chkPrecioIncluyeIvaPlato.setFocusable(false);
            chkPrecioIncluyeIvaPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            chkPrecioIncluyeIvaPlato.setToolTipText("Indica si el precio ingresado ya incluye el porcentaje de IVA");
            jPanel11.add(chkPrecioIncluyeIvaPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(155, 215, 125, 25));

            chkAplicaIvaPlato.addActionListener(e -> {
                boolean gravado = chkAplicaIvaPlato.isSelected();
                chkPrecioIncluyeIvaPlato.setEnabled(gravado);
                if (!gravado) {
                    chkPrecioIncluyeIvaPlato.setSelected(false);
                }
            });

            javax.swing.JLabel lblCategoriaFicha = new javax.swing.JLabel("Categoría:");
            lblCategoriaFicha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            jPanel11.add(lblCategoriaFicha, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 242, 80, 24));

            cbCategoriaFicha = new javax.swing.JComboBox<>();
            cbCategoriaFicha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
            cbCategoriaFicha.setBackground(java.awt.Color.WHITE);
            cbCategoriaFicha.setRenderer(new RendererCategoria());
            cbCategoriaFicha.setToolTipText("Categoría del plato (se guarda con Guardar/Editar)");
            jPanel11.add(cbCategoriaFicha, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 242, 170, 24));
            cargarCategoriasFicha();

            if (txtPrecioPlato != null) {
                txtPrecioPlato.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        actualizarPrecioReferenciaPlato();
                    }
                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        actualizarPrecioReferenciaPlato();
                    }
                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        actualizarPrecioReferenciaPlato();
                    }
                });
            }
        }

        // 2. Combo Tipo de Sala en formulario de Salas (Tab 8)
        if (jPanel10 != null) {
            javax.swing.JLabel lblTipoSala = new javax.swing.JLabel("Tipo:");
            lblTipoSala.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            jPanel10.add(lblTipoSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 185, 60, 25));

            cbTipoSala = new javax.swing.JComboBox<>(new String[]{"SALÓN", "BARRA"});
            cbTipoSala.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
            cbTipoSala.setBackground(java.awt.Color.WHITE);
            cbTipoSala.setToolTipText("Selecciona si el espacio es un Salón tradicional (mesas) o Barra (puestos)");
            cbTipoSala.addActionListener(e -> {
                boolean esBarra = "BARRA".equalsIgnoreCase(String.valueOf(cbTipoSala.getSelectedItem()));
                if (jLabel19 != null) {
                    jLabel19.setText(esBarra ? "Puestos:" : "Mesas:");
                }
            });
            jPanel10.add(cbTipoSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 185, 190, 28));
        }

        // 3. Agregar columna IVA y buscador estándar a TablePlatos
        if (TablePlatos != null && TablePlatos.getModel() instanceof DefaultTableModel modelPlatos) {
            if (modelPlatos.getColumnCount() == 3) {
                modelPlatos.addColumn("IVA");
            }
            TablePlatos.setRowHeight(25);
        }
        // 3b. Sincronización bidireccional tabla <-> formulario de plato:
        // selección (clic o teclado) carga detalles; editar detalles refleja la fila.
        if (TablePlatos != null) {
            TablePlatos.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || sincronizandoPlatoForm) {
                    return;
                }
                int fila = TablePlatos.getSelectedRow();
                if (fila >= 0) {
                    cargarPlatoEnFormulario(fila);
                }
            });
        }
        javax.swing.event.DocumentListener reflejoPlato = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { reflejarFormularioEnFilaPlato(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { reflejarFormularioEnFilaPlato(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { reflejarFormularioEnFilaPlato(); }
        };
        if (txtNombrePlato != null) {
            txtNombrePlato.getDocument().addDocumentListener(reflejoPlato);
        }
        java.awt.event.ItemListener reflejoIvaPlato = e -> reflejarFormularioEnFilaPlato();
        if (chkAplicaIvaPlato != null) {
            chkAplicaIvaPlato.addItemListener(reflejoIvaPlato);
        }
        if (chkPrecioIncluyeIvaPlato != null) {
            chkPrecioIncluyeIvaPlato.addItemListener(reflejoIvaPlato);
        }
        if (jPanel2 != null && jScrollPane4 != null && TablePlatos != null) {
            javax.swing.JPanel pnlDerechaPlatos = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
            pnlDerechaPlatos.setOpaque(false);

            javax.swing.JPanel pnlBuscarAdmin = new javax.swing.JPanel(new java.awt.BorderLayout(6, 0));
            pnlBuscarAdmin.setOpaque(false);
            pnlBuscarAdmin.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 6, 0));

            javax.swing.JLabel lblBuscarAdmin = new javax.swing.JLabel(" Buscar plato: ");
            lblBuscarAdmin.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            pnlBuscarAdmin.add(lblBuscarAdmin, java.awt.BorderLayout.WEST);

            txtBuscarAdminPlatos = new javax.swing.JTextField();
            txtBuscarAdminPlatos.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
            txtBuscarAdminPlatos.putClientProperty("JTextField.placeholderText", "Buscar por nombre o categoría...");
            txtBuscarAdminPlatos.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyReleased(java.awt.event.KeyEvent e) {
                    ListarPlatos(TablePlatos, txtBuscarAdminPlatos.getText().trim());
                }
            });
            pnlBuscarAdmin.add(txtBuscarAdminPlatos, java.awt.BorderLayout.CENTER);

            javax.swing.JPanel pnlAccionesAdmin = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
            pnlAccionesAdmin.setOpaque(false);

            javax.swing.JButton btnLimpiarAdmin = new javax.swing.JButton("x");
            btnLimpiarAdmin.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
            btnLimpiarAdmin.setFocusable(false);
            btnLimpiarAdmin.setToolTipText("Limpiar filtro");
            btnLimpiarAdmin.addActionListener(e -> {
                txtBuscarAdminPlatos.setText("");
                ListarPlatos(TablePlatos, "");
                txtBuscarAdminPlatos.requestFocusInWindow();
            });
            pnlAccionesAdmin.add(btnLimpiarAdmin);

            chkFiltroRapidoAdminPlatos = new javax.swing.JCheckBox("Auto-limpiar", true);
            chkFiltroRapidoAdminPlatos.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
            chkFiltroRapidoAdminPlatos.setOpaque(false);
            chkFiltroRapidoAdminPlatos.setFocusable(false);
            chkFiltroRapidoAdminPlatos.setToolTipText("Al elegir un plato se limpia el filtro y se muestra en la lista completa");
            chkFiltroRapidoAdminPlatos.addItemListener(e -> {
                guardarPreferenciaFiltroRapido(chkFiltroRapidoAdminPlatos.isSelected());
            });
            pnlAccionesAdmin.add(chkFiltroRapidoAdminPlatos);
            pnlBuscarAdmin.add(pnlAccionesAdmin, java.awt.BorderLayout.EAST);

            jPanel2.removeAll();
            jPanel2.setLayout(new java.awt.BorderLayout(20, 10));
            jPanel2.setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 20, 15, 20));
            jPanel2.add(jPanel11, java.awt.BorderLayout.WEST);
            pnlDerechaPlatos.add(pnlBuscarAdmin, java.awt.BorderLayout.NORTH);
            pnlDerechaPlatos.add(jScrollPane4, java.awt.BorderLayout.CENTER);
            jPanel2.add(pnlDerechaPlatos, java.awt.BorderLayout.CENTER);
            jPanel2.revalidate();
            jPanel2.repaint();
        }

        // 4. Agregar columna Tipo a tableSala si no la tiene
        if (tableSala != null && tableSala.getModel() instanceof DefaultTableModel modelSala) {
            if (modelSala.getColumnCount() == 3) {
                modelSala.addColumn("Tipo");
            }
        }

        // 5. Agregar columna IVA a tableMenu si no la tiene
        if (tableMenu != null && tableMenu.getModel() instanceof DefaultTableModel modelMenu) {
            if (modelMenu.getColumnCount() == 6) {
                modelMenu.addColumn("IVA");
            }
        }
    }

    /**
     * Llena el combo de categoría de la ficha (con opción "Sin categoría").
     * Preserva la selección actual si sigue existiendo.
     */
    private void cargarCategoriasFicha() {
        if (cbCategoriaFicha == null) {
            return;
        }
        Object sel = cbCategoriaFicha.getSelectedItem();
        int idSel = (sel instanceof Modelo.Categoria c) ? c.getId() : -1;
        cbCategoriaFicha.removeAllItems();
        cbCategoriaFicha.addItem(new Modelo.Categoria(0, "Sin categoría", null, 0));
        try {
            for (Modelo.Categoria c : categoriaControlador().listar()) {
                if (c != null) {
                    cbCategoriaFicha.addItem(c);
                }
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Sistema.class.getName())
                    .log(java.util.logging.Level.WARNING, "No se pudieron cargar categorías: " + ex.getMessage());
        }
        if (idSel >= 0) {
            seleccionarCategoriaFicha(idSel);
        }
    }

    private void seleccionarCategoriaFicha(int idCategoria) {
        if (cbCategoriaFicha == null) {
            return;
        }
        for (int i = 0; i < cbCategoriaFicha.getItemCount(); i++) {
            Object o = cbCategoriaFicha.getItemAt(i);
            if (o instanceof Modelo.Categoria c && c.getId() == idCategoria) {
                cbCategoriaFicha.setSelectedIndex(i);
                return;
            }
        }
        cbCategoriaFicha.setSelectedIndex(0);
    }

    /** Guarda la categoría elegida en la ficha para el plato indicado. */
    private void aplicarCategoriaFicha(String nombrePlato) {
        if (cbCategoriaFicha == null || nombrePlato == null || nombrePlato.isBlank()) {
            return;
        }
        Object sel = cbCategoriaFicha.getSelectedItem();
        int idCat = (sel instanceof Modelo.Categoria c) ? c.getId() : 0;
        try {
            categoriaControlador().asignarPlato(nombrePlato.trim(), idCat);
        } catch (Exception ex) {
            mostrarAvisoEnBanda("Plato guardado, pero la categoría no se aplicó: " + mensajeError(ex));
        }
    }

    private void initPanelMesasMejorado() {
        jPanel22.removeAll();
        jPanel22.setLayout(new java.awt.BorderLayout(8, 8));
        jPanel22.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Contenedor Norte: Pestañas de Sala arriba + Header de control abajo
        javax.swing.JPanel panelNorteContenedor = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
        panelNorteContenedor.setOpaque(false);

        // Barra superior de pestañas de sala (Opción B: 1 solo clic para cambiar de sala)
        panelTabsSalasDirecto = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        panelTabsSalasDirecto.setOpaque(false);
        javax.swing.JScrollPane scrollTabsSalas = new javax.swing.JScrollPane(panelTabsSalasDirecto,
                javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollTabsSalas.setBorder(null);
        scrollTabsSalas.setOpaque(false);
        scrollTabsSalas.getViewport().setOpaque(false);
        panelNorteContenedor.add(scrollTabsSalas, java.awt.BorderLayout.NORTH);

        // Header Panel (Norte - Línea 2)
        javax.swing.JPanel panelHeader = new javax.swing.JPanel(new java.awt.BorderLayout(10, 0));
        panelHeader.setOpaque(false);

        lblTituloSalaMesas = new javax.swing.JLabel("Selecciona una sala para ver sus mesas");
        lblTituloSalaMesas.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15));
        lblTituloSalaMesas.setForeground(new java.awt.Color(0, 102, 102));
        panelHeader.add(lblTituloSalaMesas, java.awt.BorderLayout.WEST);

        javax.swing.JPanel panelAcciones = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        panelAcciones.setOpaque(false);

        lblUltimaCargaMesas = new javax.swing.JLabel("Última actualización: --:--:--");
        lblUltimaCargaMesas.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        lblUltimaCargaMesas.setForeground(new java.awt.Color(100, 100, 100));
        panelAcciones.add(lblUltimaCargaMesas);

        btnActualizarMesas = new javax.swing.JButton("Actualizar mesas");
        btnActualizarMesas.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnActualizarMesas.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnActualizarMesas.setToolTipText("Recargar el estado de las mesas de esta sala");
        btnActualizarMesas.setEnabled(false);
        java.net.URL urlRefresh = getClass().getResource("/Img/Actualizar (2).png");
        if (urlRefresh != null) {
            btnActualizarMesas.setIcon(new javax.swing.ImageIcon(urlRefresh));
        }
        btnActualizarMesas.addActionListener(e -> {
            if (idSalaActualMesas > 0 && cantMesasActual > 0) {
                panelMesas(idSalaActualMesas, nombreSalaActualMesas, cantMesasActual);
            }
        });
        panelAcciones.add(btnActualizarMesas);

        if (cbModoAtencion != null) {
            javax.swing.JPanel pnlModo = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
            pnlModo.setOpaque(false);
            javax.swing.JLabel lblModo = new javax.swing.JLabel("Modo:");
            lblModo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
            lblModo.setForeground(COLOR_CABECERA);
            pnlModo.add(lblModo);
            pnlModo.add(cbModoAtencion);
            panelAcciones.add(pnlModo);
        }

        panelHeader.add(panelAcciones, java.awt.BorderLayout.EAST);
        panelNorteContenedor.add(panelHeader, java.awt.BorderLayout.SOUTH);

        jPanel22.add(panelNorteContenedor, java.awt.BorderLayout.NORTH);

        // Centro (Scroll de mesas)
        jPanel22.add(jScrollPane9, java.awt.BorderLayout.CENTER);

        // Footer Panel (Sur - Leyenda)
        javax.swing.JPanel panelFooter = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 15, 4));
        panelFooter.setOpaque(false);
        javax.swing.JLabel lblLeyendaTitulo = new javax.swing.JLabel("Leyenda:");
        lblLeyendaTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        panelFooter.add(lblLeyendaTitulo);
        panelFooter.add(cuadroColor(Servicio.ServicioMesa.FONDO_MESA_LIBRE, "Libre"));
        panelFooter.add(cuadroColor(Servicio.ServicioMesa.FONDO_MESA_OCUPADA, "Ocupada"));

        jPanel22.add(panelFooter, java.awt.BorderLayout.SOUTH);
        jPanel22.revalidate();
        jPanel22.repaint();
    }

    javax.swing.JLabel cuadroColor(java.awt.Color color, String texto) {
        javax.swing.JLabel label = new javax.swing.JLabel("  " + texto + "  ");
        label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        label.setOpaque(true);
        label.setBackground(color);
        double luminancia = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255.0;
        label.setForeground(luminancia > 0.6 ? java.awt.Color.BLACK : java.awt.Color.WHITE);
        label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(color.darker(), 1, true),
                javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        return label;
    }

    //crear mesas / puestos
    private void panelMesas(int id_sala, int cant) {
        panelMesas(id_sala, "SALA " + id_sala, cant, false);
    }

    private void panelMesas(int id_sala, String nombreSala, int cant) {
        boolean esBarra = "BARRA".equalsIgnoreCase(salaTipoMap.getOrDefault(id_sala, "SALON"));
        panelMesas(id_sala, nombreSala, cant, esBarra);
    }

    private void panelMesas(int id_sala, String nombreSala, int cant, boolean esBarra) {
        this.idSalaActualMesas = id_sala;
        this.nombreSalaActualMesas = nombreSala;
        this.cantMesasActual = cant;
        this.salaActualEsBarra = esBarra;
        if (lblTituloSalaMesas != null) {
            String etiqueta = esBarra ? "puestos de la Barra: " : "mesas de: ";
            lblTituloSalaMesas.setText("Cargando " + etiqueta + (nombreSala != null ? nombreSala : ("SALA " + id_sala)) + "...");
        }
        if (btnActualizarMesas != null) {
            btnActualizarMesas.setEnabled(true);
        }
        PanelMesas.removeAll();
        PanelMesas.revalidate();
        PanelMesas.repaint();
        long version = ++versionPanelMesas;
        new PanelMesasSwingWorker(pedidosControlador, id_sala, cant,
                datos -> {
                    if (version == versionPanelMesas) {
                        mostrarPanelMesas(id_sala, nombreSala, cant, datos);
                    }
                }, error -> {
                    if (version == versionPanelMesas) {
                        mostrarErrorConsultaMesas(error);
                    }
                }).execute();
    }

    private void mostrarPanelMesas(int id_sala, int cant, Map<Integer, Integer> estados) {
        mostrarPanelMesas(id_sala, "SALA " + id_sala, cant, new PanelMesasSwingWorker.DatosMesasSala(estados, Collections.emptyMap()));
    }

    private void mostrarPanelMesas(int id_sala, String nombreSala, int cant, Map<Integer, Integer> estados) {
        mostrarPanelMesas(id_sala, nombreSala, cant, new PanelMesasSwingWorker.DatosMesasSala(estados, Collections.emptyMap()));
    }

    private void mostrarPanelMesas(int id_sala, String nombreSala, int cant, PanelMesasSwingWorker.DatosMesasSala datos) {
        this.idSalaActualMesas = id_sala;
        this.nombreSalaActualMesas = nombreSala;
        this.cantMesasActual = cant;
        boolean esBarra = this.salaActualEsBarra || "BARRA".equalsIgnoreCase(salaTipoMap.getOrDefault(id_sala, "SALON"));
        if (lblTituloSalaMesas != null) {
            String etiqueta = esBarra ? "Puestos de la Barra: " : "Mesas de: ";
            String unidad = esBarra ? " puestos" : " mesas";
            lblTituloSalaMesas.setText(etiqueta + (nombreSala != null ? nombreSala : ("SALA " + id_sala)) + " (" + cant + unidad + ")");
        }
        if (lblUltimaCargaMesas != null) {
            lblUltimaCargaMesas.setText("Última actualización: "
                    + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
        if (btnActualizarMesas != null) {
            btnActualizarMesas.setEnabled(true);
        }
        Map<Integer, Integer> estados = datos != null ? datos.getPedidosPendientes() : Collections.emptyMap();
        Map<Integer, String> mesonerosMesas = datos != null ? datos.getMesonerosMesas() : Collections.emptyMap();
        Map<String, String> nombresVisuales = ServicioMesoneroNombre.resolverNombresVisualesDesdeStrings(mesonerosMesas.values());

        String prefijoSala = salaIdAPrefijo.getOrDefault(id_sala, ServicioMesa.sugerirPrefijoBase(nombreSala, esBarra));

        List<JButton> botonesMesa = new ArrayList<>();
        for (int i = 1; i <= cant; i++) {
            int num_mesa = i;
            String codigoMesa = ServicioMesa.formatearCodigo(prefijoSala, num_mesa);
            JButton boton = new JButton() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    super.paintComponent(g2);
                    g2.dispose();
                }
            };
            boton.setContentAreaFilled(false);
            boton.setOpaque(true);
            java.net.URL urlMesa = getClass().getResource("/Img/mesa.png");
            if (urlMesa != null) {
                java.awt.Image imgMesa = new ImageIcon(urlMesa).getImage()
                        .getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
                boton.setIcon(new ImageIcon(imgMesa));
            }
            boton.setHorizontalTextPosition(JButton.CENTER);
            boton.setVerticalTextPosition(JButton.BOTTOM);
            int verificar = estados.getOrDefault(num_mesa, 0);
            String nombreMesonero = mesonerosMesas.get(num_mesa);
            String visual = (nombreMesonero != null && !nombreMesonero.isBlank())
                    ? nombresVisuales.getOrDefault(nombreMesonero.trim(), ServicioMesoneroNombre.extraerPrimerNombre(nombreMesonero))
                    : null;

            String prefijoTexto = esBarra ? "PUESTO " : "MESA ";
            if (verificar > 0) {
                boton.setBackground(Servicio.ServicioMesa.FONDO_MESA_OCUPADA);
                if (visual != null && !visual.isBlank()) {
                    boton.setText("<html><center><font size='+2'><b>" + codigoMesa + "</b></font><br><span style='font-size:12px;'><b>" + prefijoTexto + i + "</b></span><br><span style='font-size:12px;'><b>" + visual + "</b></span></center></html>");
                    boton.setToolTipText((esBarra ? "Puesto Ocupado (" : "Mesa Ocupada (") + codigoMesa + ") - Atendida por: " + nombreMesonero);
                } else {
                    boton.setText("<html><center><font size='+2'><b>" + codigoMesa + "</b></font><br><span style='font-size:12px;'><b>" + prefijoTexto + i + "</b></span></center></html>");
                    boton.setToolTipText((esBarra ? "Puesto Ocupado (" : "Mesa Ocupada (") + codigoMesa + ") (Sin mesonero asignado)");
                }
            } else {
                boton.setBackground(Servicio.ServicioMesa.FONDO_MESA_LIBRE);
                boton.setText("<html><center><font size='+2'><b>" + codigoMesa + "</b></font><br><span style='font-size:12px;'><b>" + prefijoTexto + i + "</b></span><br><span style='font-size:12px;'><b>LIBRE</b></span></center></html>");
                boton.setToolTipText((esBarra ? "Puesto " : "Mesa ") + i + " (" + codigoMesa + ") Disponible");
            }
            boton.setForeground(Color.WHITE);
            boton.setFocusable(false);
            boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            botonesMesa.add(boton);
            boton.addActionListener((ActionEvent e) -> {
                if (verificar > 0) {
                    if (!politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                        JOptionPane.showMessageDialog(this,
                                "Este espacio tiene un pedido pendiente. Solicita a un administrador que lo gestione.",
                                "Pedido pendiente", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    cargarPedidoEnPantalla(verificar, true);
                } else {
                    LimpiarTableMenu();
                    txtComentario.setText("");
                    txtBuscarPlato.setText("");
                    ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
                    txtTempIdSala.setText("" + id_sala);
                    txtTempNumMesa.setText("" + num_mesa);
                    cargarComboMesoneros();
                    actualizarCabeceraMesaDirecta(id_sala, nombreSala, num_mesa, codigoMesa);
                    jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                            javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)),
                            "PEDIDO - " + (nombreSala != null ? nombreSala : ("SALA " + id_sala)) + " | " + codigoMesa + " (" + (esBarra ? "PUESTO " : "MESA ") + num_mesa + ")",
                            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                            javax.swing.border.TitledBorder.DEFAULT_POSITION,
                            new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14),
                            new java.awt.Color(0, 102, 102)));
                    jTabbedPane1.setSelectedIndex(3);
                    txtBuscarPlato.requestFocusInWindow();
                }
            });
        }
        PanelMesas.removeAll();
        for (JButton boton : botonesMesa) {
            PanelMesas.add(boton);
        }
        PanelMesas.revalidate();
        PanelMesas.repaint();
        refrescarBarraSalasMesas();
    }

    private void mostrarErrorConsultaMesas(Throwable error) {
        if (lblTituloSalaMesas != null && idSalaActualMesas > 0) {
            lblTituloSalaMesas.setText("Error cargando mesas de: " + (nombreSalaActualMesas != null ? nombreSalaActualMesas : ("SALA " + idSalaActualMesas)));
        }
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudo consultar el estado de las mesas. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar mesas", JOptionPane.ERROR_MESSAGE);
    }

    private void refrescarBarraSalasMesas() {
        if (panelTabsSalasDirecto == null) return;
        panelTabsSalasDirecto.removeAll();
        if (salasCache != null && !salasCache.isEmpty()) {
            for (Salas sala : salasCache) {
                if (sala == null) continue;
                String prefijo = salaIdAPrefijo.getOrDefault(sala.getId(),
                        ServicioMesa.sugerirPrefijoBase(sala.getNombre(), sala.esBarra()));
                boolean esSeleccionada = (sala.getId() == idSalaActualMesas);
                JButton btn = new JButton(sala.getNombre() + " (" + prefijo + ")");
                btn.setToolTipText("Ver mesas de " + sala.getNombre());
                btn.setFont(new java.awt.Font("Segoe UI", esSeleccionada ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 12));
                btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                btn.setFocusable(false);
                if (esSeleccionada) {
                    btn.setBackground(new java.awt.Color(0, 102, 102));
                    btn.setForeground(java.awt.Color.WHITE);
                } else {
                    btn.setBackground(new java.awt.Color(235, 240, 245));
                    btn.setForeground(new java.awt.Color(40, 40, 40));
                }
                btn.addActionListener(e -> {
                    panelMesas(sala.getId(), sala.getNombre(), sala.getMesas());
                });
                panelTabsSalasDirecto.add(btn);
            }
        }
        panelTabsSalasDirecto.revalidate();
        panelTabsSalasDirecto.repaint();
    }

    private void initCabeceraMesaDirecta() {
        panelCabeceraMesaDirecta = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 2));
        panelCabeceraMesaDirecta.setOpaque(false);

        btnSelectorMesaCabecera = new javax.swing.JButton("📍 Mesa: -- ▾");
        btnSelectorMesaCabecera.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnSelectorMesaCabecera.setForeground(new java.awt.Color(0, 102, 102));
        btnSelectorMesaCabecera.setBackground(new java.awt.Color(255, 255, 255));
        btnSelectorMesaCabecera.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnSelectorMesaCabecera.setFocusable(false);
        btnSelectorMesaCabecera.setToolTipText("Clic para elegir o cambiar de mesa rápidamente");
        btnSelectorMesaCabecera.addActionListener(e -> mostrarMenuSeleccionMesaDirecta(btnSelectorMesaCabecera));

        javax.swing.JLabel lblIr = new javax.swing.JLabel("Mesa rápida:");
        lblIr.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        lblIr.setForeground(new java.awt.Color(80, 80, 80));

        txtCodigoMesaDirecto = new javax.swing.JTextField(4);
        txtCodigoMesaDirecto.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        txtCodigoMesaDirecto.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        txtCodigoMesaDirecto.setToolTipText("Escribe el código (ej: P1, T2) y pulsa Enter");
        txtCodigoMesaDirecto.addActionListener(e -> procesarDigitacionMesaDirecta(txtCodigoMesaDirecto.getText()));

        panelCabeceraMesaDirecta.add(lblIr);
        panelCabeceraMesaDirecta.add(txtCodigoMesaDirecto);
        panelCabeceraMesaDirecta.add(btnSelectorMesaCabecera);
    }

    private void initPanelAtencionSplit() {
        panelSplitScreen = new PanelAtencionMesasSplit(this, salasControlador, platosControlador, pedidosControlador, politicaAcceso);
        envolverConEncabezado(panelSplitScreen, "ATENCIÓN DIRECTA (SPLIT-SCREEN)", new java.awt.Color(230, 245, 245), () -> {
            int idx = jTabbedPane1.indexOfTab("Salas");
            if (idx != -1) jTabbedPane1.setSelectedIndex(idx);
        });
        jTabbedPane1.addTab("Atención Directa (Split)", panelSplitScreen);
    }

    private void initConmutadorModosAtencion() {
        int idxInicial = switch (modoAtencionActual != null ? modoAtencionActual : "OPCION_B") {
            case "OPCION_B" -> 0;
            case "OPCION_A" -> 1;
            case "OPCION_C" -> 2;
            case "CLASICO" -> 3;
            default -> 0;
        };

        cbModoAtencion = new javax.swing.JComboBox<>(NOMBRES_MODOS_ATENCION);
        cbModoAtencion.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        cbModoAtencion.setBackground(java.awt.Color.WHITE);
        cbModoAtencion.setFocusable(false);
        cbModoAtencion.setToolTipText("Selecciona el modo de atención directa para mesas");
        cbModoAtencion.setSelectedIndex(idxInicial);

        cbModoAtencionGlobal = new javax.swing.JComboBox<>(NOMBRES_MODOS_ATENCION);
        cbModoAtencionGlobal.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        cbModoAtencionGlobal.setBackground(java.awt.Color.WHITE);
        cbModoAtencionGlobal.setFocusable(false);
        cbModoAtencionGlobal.setToolTipText("Modo de atención activo para mesas");
        cbModoAtencionGlobal.setSelectedIndex(idxInicial);

        cbModoAtencion.addActionListener(e -> {
            if (cambiandoModoProgramaticamente) return;
            cambiarModoAtencion(cbModoAtencion.getSelectedIndex());
        });

        cbModoAtencionGlobal.addActionListener(e -> {
            if (cambiandoModoProgramaticamente) return;
            cambiarModoAtencion(cbModoAtencionGlobal.getSelectedIndex());
        });

        panelConmutadorModoGlobal = new javax.swing.JPanel(new java.awt.BorderLayout(4, 2));
        panelConmutadorModoGlobal.setOpaque(true);
        panelConmutadorModoGlobal.setBackground(new java.awt.Color(245, 247, 250));
        panelConmutadorModoGlobal.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(200, 205, 215), 1),
                javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        javax.swing.JLabel lblTituloModoG = new javax.swing.JLabel("Modo de Atención:");
        lblTituloModoG.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        lblTituloModoG.setForeground(new java.awt.Color(0, 102, 102));
        panelConmutadorModoGlobal.add(lblTituloModoG, java.awt.BorderLayout.NORTH);
        panelConmutadorModoGlobal.add(cbModoAtencionGlobal, java.awt.BorderLayout.CENTER);
        getContentPane().add(panelConmutadorModoGlobal, new org.netbeans.lib.awtextra.AbsoluteConstraints(520, 15, 275, 65));
    }

    void cambiarModoAtencion(int nuevoIndice) {
        if (cambiandoModoProgramaticamente) return;
        cambiandoModoProgramaticamente = true;
        try {
            switch (nuevoIndice) {
                case 0 -> modoAtencionActual = "OPCION_B";
                case 1 -> modoAtencionActual = "OPCION_A";
                case 2 -> modoAtencionActual = "OPCION_C";
                case 3 -> modoAtencionActual = "CLASICO";
                default -> modoAtencionActual = "OPCION_B";
            }
            if (cbModoAtencion != null && cbModoAtencion.getSelectedIndex() != nuevoIndice) {
                cbModoAtencion.setSelectedIndex(nuevoIndice);
            }
            if (cbModoAtencionGlobal != null && cbModoAtencionGlobal.getSelectedIndex() != nuevoIndice) {
                cbModoAtencionGlobal.setSelectedIndex(nuevoIndice);
            }
            guardarModoAtencionPersistente(modoAtencionActual);
        } finally {
            cambiandoModoProgramaticamente = false;
        }
        abrirAtencionDirectaSegunModo();
    }

    private void guardarModoAtencionPersistente(String modo) {
        if (conf != null) {
            conf.setModoAtencion(modo);
        }
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                () -> lgDao.actualizarModoAtencion(modo),
                exito -> java.util.logging.Logger.getLogger(Sistema.class.getName())
                        .log(java.util.logging.Level.FINE, "Modo de atención persistido en configuración: {0}", modo),
                ex -> java.util.logging.Logger.getLogger(Sistema.class.getName())
                        .log(java.util.logging.Level.FINE, "Aviso al persistir modo de atención: " + ex.getMessage())
        );
    }

    /** Lee la preferencia de auto-limpiar filtro y la aplica a ambos checkboxes. */
    private void cargarPreferenciaFiltroRapido() {
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                () -> {
                    try {
                        return new Modelo.ConfiguracionSistemaDao()
                                .obtener(Modelo.ConfigClaves.FILTRO_RAPIDO_PLATOS, "1");
                    } catch (Exception ex) {
                        return "1";
                    }
                },
                valor -> {
                    boolean sel = !"0".equals(valor);
                    if (chkFiltroRapido != null) {
                        chkFiltroRapido.setSelected(sel);
                    }
                    if (chkFiltroRapidoAdminPlatos != null) {
                        chkFiltroRapidoAdminPlatos.setSelected(sel);
                    }
                    if (panelSplitScreen != null) {
                        panelSplitScreen.setFiltroRapido(sel);
                    }
                },
                ex -> {
                });
    }

    void guardarPreferenciaFiltroRapido(boolean sel) {
        if (chkFiltroRapido != null && chkFiltroRapido.isSelected() != sel) {
            chkFiltroRapido.setSelected(sel);
        }
        if (chkFiltroRapidoAdminPlatos != null && chkFiltroRapidoAdminPlatos.isSelected() != sel) {
            chkFiltroRapidoAdminPlatos.setSelected(sel);
        }
        if (panelSplitScreen != null) {
            panelSplitScreen.setFiltroRapido(sel);
        }
        final String valor = sel ? "1" : "0";
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                () -> {
                    new Modelo.ConfiguracionSistemaDao().guardar(Modelo.ConfigClaves.FILTRO_RAPIDO_PLATOS, valor);
                    return null;
                },
                exito -> {
                },
                ex -> java.util.logging.Logger.getLogger(Sistema.class.getName())
                        .log(java.util.logging.Level.FINE, "Aviso al persistir filtro rápido: " + ex.getMessage()));
    }

    private void sincronizarCombosModo(String modo) {
        if (cambiandoModoProgramaticamente) return;
        cambiandoModoProgramaticamente = true;
        try {
            modoAtencionActual = modo;
            int idx = switch (modo) {
                case "OPCION_B" -> 0;
                case "OPCION_A" -> 1;
                case "OPCION_C" -> 2;
                case "CLASICO" -> 3;
                default -> 0;
            };
            if (cbModoAtencion != null && cbModoAtencion.getSelectedIndex() != idx) {
                cbModoAtencion.setSelectedIndex(idx);
            }
            if (cbModoAtencionGlobal != null && cbModoAtencionGlobal.getSelectedIndex() != idx) {
                cbModoAtencionGlobal.setSelectedIndex(idx);
            }
        } finally {
            cambiandoModoProgramaticamente = false;
        }
    }

    private void asegurarSalasCargadas() {
        if (salasCache == null || salasCache.isEmpty()) {
            try {
                List<Salas> cargadas = salasControlador.listar();
                if (cargadas != null && !cargadas.isEmpty()) {
                    mostrarSalasEnPanel(cargadas);
                }
            } catch (Exception ignored) {}
        }
    }

    private void abrirAtencionDirectaSegunModo() {
        switch (modoAtencionActual) {
            case "OPCION_A" -> abrirModoA();
            case "OPCION_C" -> abrirModoC();
            case "CLASICO" -> abrirModoClasico();
            case "OPCION_B" -> abrirModoB();
            default -> abrirModoB();
        }
    }

    private void abrirModoB() {
        asegurarSalasCargadas();
        if (idSalaActualMesas <= 0 && !salasCache.isEmpty()) {
            Salas primera = salasCache.get(0);
            panelMesas(primera.getId(), primera.getNombre(), primera.getMesas(), primera.esBarra());
        } else if (idSalaActualMesas > 0) {
            refrescarPanelMesasActual();
        } else {
            panelSalas();
        }
        int idx = jTabbedPane1.indexOfTab("Mesas");
        if (idx != -1) {
            jTabbedPane1.setSelectedIndex(idx);
        }
        mostrarAvisoEnBanda("Modo B activo: Mapa con pestañas de sala");
    }

    private void abrirModoA() {
        asegurarSalasCargadas();
        if (tblTemPlatos != null) {
            ListarPlatos(tblTemPlatos, "");
        }
        cargarComboMesoneros();
        int idx = 3;
        if (jTabbedPane1.getTabCount() > 3) {
            jTabbedPane1.setSelectedIndex(idx);
        }
        if (txtCodigoMesaDirecto != null) {
            txtCodigoMesaDirecto.requestFocusInWindow();
            txtCodigoMesaDirecto.selectAll();
        }
        mostrarAvisoEnBanda("Modo A activo: Digita código de mesa (ej: P1, T2) o usa el selector");
    }

    private void abrirModoC() {
        asegurarSalasCargadas();
        if (salaIdAPrefijo.isEmpty() && !salasCache.isEmpty()) {
            salaIdAPrefijo.putAll(ServicioMesa.generarPrefijosSalas(salasCache));
            prefijoASalaId.putAll(ServicioMesa.invertirPrefijos(salaIdAPrefijo));
        }
        int idx = jTabbedPane1.indexOfTab("Atención Directa (Split)");
        if (idx != -1) {
            jTabbedPane1.setSelectedIndex(idx);
            if (panelSplitScreen != null && !salasCache.isEmpty()) {
                panelSplitScreen.actualizarSalas(salasCache, salaIdAPrefijo);
            }
        }
        mostrarAvisoEnBanda("Modo C activo: Pantalla dividida (Split-Screen)");
    }

    private void abrirModoClasico() {
        panelSalas();
        int idx = jTabbedPane1.indexOfTab("Panel");
        if (idx != -1) {
            jTabbedPane1.setSelectedIndex(idx);
        }
        mostrarAvisoEnBanda("Modo Clásico activo: Selecciona una sala");
    }

    private void actualizarCabeceraMesaDirecta(int idSala, String nombreSala, int numMesa, String codigoMesa) {
        if (btnSelectorMesaCabecera == null) return;
        if (codigoMesa == null || codigoMesa.isBlank() || codigoMesa.equals("--")) {
            if (idSala > 0 && numMesa > 0) {
                String pref = salaIdAPrefijo.getOrDefault(idSala, ServicioMesa.sugerirPrefijoBase(nombreSala, false));
                codigoMesa = ServicioMesa.formatearCodigo(pref, numMesa);
            } else if ("RÁPIDO".equalsIgnoreCase(codigoMesa)) {
                codigoMesa = "RÁPIDO";
            } else {
                codigoMesa = "--";
            }
        }
        btnSelectorMesaCabecera.setText("📍 Mesa: " + codigoMesa + " ▾");
        if (txtCodigoMesaDirecto != null) {
            txtCodigoMesaDirecto.setText(codigoMesa.equals("--") ? "" : codigoMesa);
        }
    }

    private void procesarDigitacionMesaDirecta(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return;
        }
        asegurarSalasCargadas();
        if (prefijoASalaId.isEmpty() && !salasCache.isEmpty()) {
            salaIdAPrefijo.clear();
            salaIdAPrefijo.putAll(ServicioMesa.generarPrefijosSalas(salasCache));
            prefijoASalaId.clear();
            prefijoASalaId.putAll(ServicioMesa.invertirPrefijos(salaIdAPrefijo));
        }
        java.util.Optional<ServicioMesa.MesaIdentificada> opt = ServicioMesa.parsearCodigoMesa(texto, prefijoASalaId);
        if (opt.isEmpty()) {
            mostrarAvisoEnBanda("Código de mesa no encontrado o formato incorrecto (ej: P1, T2): " + texto);
            return;
        }
        ServicioMesa.MesaIdentificada mesa = opt.get();
        Salas salaEncontrada = salasCache.stream()
                .filter(s -> s.getId() == mesa.idSala())
                .findFirst().orElse(null);
        if (salaEncontrada == null) {
            mostrarAvisoEnBanda("Sala con ID " + mesa.idSala() + " no encontrada.");
            return;
        }
        if (mesa.numMesa() > salaEncontrada.getMesas()) {
            mostrarAvisoEnBanda("La sala " + salaEncontrada.getNombre() + " solo tiene " + salaEncontrada.getMesas() + " mesas.");
            return;
        }
        int idPedido = pedidosControlador.buscarPedidoPendiente(mesa.numMesa(), mesa.idSala());
        asignarMesaDirecta(mesa.idSala(), salaEncontrada.getNombre(), mesa.numMesa(), mesa.codigoFormateado(), idPedido);
    }

    private void asignarMesaDirecta(int idSala, String nombreSala, int numMesa, String codigoMesa, int idPedidoExistente) {
        idSalaActualMesas = idSala;
        nombreSalaActualMesas = nombreSala;
        if (idPedidoExistente > 0) {
            if (!politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) {
                JOptionPane.showMessageDialog(this,
                        "Este espacio tiene un pedido pendiente (#" + idPedidoExistente + ").",
                        "Pedido pendiente", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cargarPedidoEnPantalla(idPedidoExistente, true);
            actualizarCabeceraMesaDirecta(idSala, nombreSala, numMesa, codigoMesa);
        } else {
            LimpiarTableMenu();
            txtComentario.setText("");
            txtBuscarPlato.setText("");
            ListarPlatos(tblTemPlatos, "");
            txtTempIdSala.setText(String.valueOf(idSala));
            txtTempNumMesa.setText(String.valueOf(numMesa));
            cargarComboMesoneros();
            actualizarCabeceraMesaDirecta(idSala, nombreSala, numMesa, codigoMesa);
            jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                    javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)),
                    "PEDIDO - " + (nombreSala != null ? nombreSala : ("SALA " + idSala)) + " | " + codigoMesa + " (MESA " + numMesa + ")",
                    javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                    javax.swing.border.TitledBorder.DEFAULT_POSITION,
                    new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14),
                    new java.awt.Color(0, 102, 102)));
            jTabbedPane1.setSelectedIndex(3);
            txtBuscarPlato.requestFocusInWindow();
            mostrarAvisoEnBanda("Mesa " + codigoMesa + " seleccionada");
        }
    }

    private void mostrarMenuSeleccionMesaDirecta(java.awt.Component invoker) {
        asegurarSalasCargadas();
        if (salasCache == null || salasCache.isEmpty()) {
            mostrarAvisoEnBanda("Cargando salas...");
            panelSalas();
            return;
        }
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        javax.swing.JLabel lblTitulo = new javax.swing.JLabel("  Seleccione Mesa Rápida:");
        lblTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        lblTitulo.setForeground(new java.awt.Color(0, 102, 102));
        popup.add(lblTitulo);
        popup.addSeparator();

        for (Salas sala : salasCache) {
            if (sala == null) continue;
            String prefijo = salaIdAPrefijo.getOrDefault(sala.getId(),
                    ServicioMesa.sugerirPrefijoBase(sala.getNombre(), sala.esBarra()));
            javax.swing.JMenu menuSala = new javax.swing.JMenu(sala.getNombre() + " (" + prefijo + ")");
            menuSala.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));

            int totalMesas = sala.getMesas();
            for (int m = 1; m <= totalMesas; m++) {
                final int numMesa = m;
                String cod = ServicioMesa.formatearCodigo(prefijo, numMesa);
                int verif = pedidosControlador.buscarPedidoPendiente(numMesa, sala.getId());
                boolean ocupada = (verif > 0);
                String estadoStr = ocupada ? " [Ocupada #" + verif + "]" : " [Libre]";
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(cod + (sala.esBarra() ? " (Puesto " : " (Mesa ") + numMesa + ")" + estadoStr);
                item.setFont(new java.awt.Font("Segoe UI", ocupada ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 11));
                if (ocupada) {
                    item.setForeground(new java.awt.Color(180, 40, 40));
                } else {
                    item.setForeground(new java.awt.Color(0, 120, 80));
                }
                item.addActionListener(e -> asignarMesaDirecta(sala.getId(), sala.getNombre(), numMesa, cod, verif));
                menuSala.add(item);
            }
            popup.add(menuSala);
        }
        popup.show(invoker, 0, invoker.getHeight());
    }

    public BigDecimal getTasaActual() {
        if (conf != null && conf.getTasaDolar() != null && conf.getTasaDolar().compareTo(BigDecimal.ZERO) > 0) {
            return conf.getTasaDolar();
        }
        if (txtTasaConfig != null && !txtTasaConfig.getText().trim().isEmpty()) {
            try {
                BigDecimal t = new BigDecimal(txtTasaConfig.getText().trim());
                if (t.compareTo(BigDecimal.ZERO) > 0) {
                    return t;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public BigDecimal getIvaPorcentajeActual() {
        if (conf != null && conf.getIvaPorcentaje() != null && conf.getIvaPorcentaje().compareTo(BigDecimal.ZERO) >= 0) {
            return conf.getIvaPorcentaje();
        }
        if (txtIvaConfig != null && !txtIvaConfig.getText().trim().isEmpty()) {
            try {
                BigDecimal iva = new BigDecimal(txtIvaConfig.getText().trim());
                if (iva.compareTo(BigDecimal.ZERO) >= 0) {
                    return iva;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public String getNombreUsuarioActual() {
        if (LabelVendedor != null && !LabelVendedor.getText().isBlank()) {
            return LabelVendedor.getText().trim();
        }
        if (usuarioActual != null && usuarioActual.getNombre() != null && !usuarioActual.getNombre().isBlank()) {
            return usuarioActual.getNombre().trim();
        }
        return "Sistema";
    }

    public void cargarPedidoEnPantallaDesdeSplit(int idPedido) {
        cargarPedidoEnPantalla(idPedido, true);
    }

    public javax.swing.JComboBox<String> getCbModoAtencion() {
        return cbModoAtencion;
    }

    public javax.swing.JComboBox<String> getCbModoAtencionGlobal() {
        return cbModoAtencionGlobal;
    }

    public String getModoAtencionActual() {
        return modoAtencionActual;
    }

    public javax.swing.JButton getBtnAtencionDirecta() {
        return btnAtencionDirecta;
    }

    public javax.swing.JTextField getTxtCodigoMesaDirecto() {
        return txtCodigoMesaDirecto;
    }

    public javax.swing.JButton getBtnSelectorMesaCabecera() {
        return btnSelectorMesaCabecera;
    }

    public PanelAtencionMesasSplit getPanelSplitScreen() {
        return panelSplitScreen;
    }

    /** Id del usuario autenticado (0 si no hay); usado para preferencias por usuario. */
    public int getIdUsuarioActual() {
        return usuarioActual != null ? usuarioActual.getId() : 0;
    }

    // platos
    private void ListarPlatos(JTable tabla, String filtro) {
        boolean menuPedido = tabla == tblTemPlatos;
        long version = menuPedido ? ++versionMenuPlatos : ++versionCatalogoPlatos;
        String fecha = fechaActual();
        new ListaPlatosSwingWorker(platosControlador, filtro, fecha, menuPedido,
                platos -> {
                    long actual = menuPedido ? versionMenuPlatos : versionCatalogoPlatos;
                    if (actual == version) {
                        mostrarPlatosEnTabla(tabla, platos);
                    }
                }, error -> {
                    long actual = menuPedido ? versionMenuPlatos : versionCatalogoPlatos;
                    if (actual == version) {
                        mostrarErrorCargaPlatos(error);
                    }
                }).execute();
    }

    private void mostrarPlatosEnTabla(JTable tabla, List<Platos> Listar) {
        // A1: preservar la seleccion por ID entre refrescos (el filtrado no debe perderla).
        int filaSel = tabla.getSelectedRow();
        String idSel = (filaSel >= 0 && filaSel < tabla.getRowCount())
                ? String.valueOf(Tables.valorModelo(tabla, filaSel, 0)) : null;
        modelo = (DefaultTableModel) tabla.getModel();
        modelo.setRowCount(0);
        boolean tieneColumnaIva = modelo.getColumnCount() >= 4;
        Object[] ob = new Object[tieneColumnaIva ? 4 : 3];
        int filaRestaurar = -1;
        for (int i = 0; i < Listar.size(); i++) {
            Platos p = Listar.get(i);
            platoAplicaIvaMap.put(p.getId(), p.isAplicaIva());
            platosPorId.put(p.getId(), p);
            ob[0] = p.getId();
            ob[1] = p.getNombre();
            String moneda = p.getMonedaBase() != null ? p.getMonedaBase().toUpperCase() : "USD";
            BigDecimal precioPrimario = "VES".equals(moneda) && p.getPrecioVes() != null
                    ? p.getPrecioVes()
                    : (p.getPrecioUsd() != null ? p.getPrecioUsd() : p.getPrecioDecimal());
            if (precioPrimario == null) precioPrimario = BigDecimal.ZERO;
            String precioStr = String.format(java.util.Locale.US, "%.2f", precioPrimario.setScale(2, RoundingMode.HALF_UP));
            String clasif = !p.isPrecioClasificado() ? " [!]" : "";
            if ("VES".equals(moneda)) {
                BigDecimal tasa = getTasaActual();
                BigDecimal refUsd = (tasa != null && tasa.signum() > 0)
                        ? precioPrimario.divide(tasa, 2, RoundingMode.HALF_UP)
                        : null;
                ob[2] = "Bs. " + precioStr + (refUsd != null ? " ($ " + String.format(java.util.Locale.US, "%.2f", refUsd) + ")" : "") + clasif;
            } else {
                ob[2] = "$ " + precioStr + clasif;
            }
            if (tieneColumnaIva) {
                String ivaLabel = formatearEtiquetaIva(p.isAplicaIva());
                if (p.isAplicaIva() && Boolean.TRUE.equals(p.getPrecioIncluyeIva())) {
                    ivaLabel += " (Inc.)";
                }
                ob[3] = ivaLabel;
            }
            modelo.addRow(ob);
            if (idSel != null && idSel.equals(String.valueOf(p.getId()))) {
                filaRestaurar = modelo.getRowCount() - 1;
            }
        }
        java.util.List<String> monedasPlatos = new java.util.ArrayList<>();
        for (Platos p : Listar) {
            monedasPlatos.add(p.getMonedaBase());
        }
        Tables.fijarCabeceraMoneda(tabla, 2, monedasPlatos);
            if (filaRestaurar >= 0 && filaRestaurar < tabla.getRowCount()) {
                tabla.setRowSelectionInterval(filaRestaurar, filaRestaurar);
            }
            Integer desplazarObj = (tabla == tblTemPlatos) ? desplazarAPlatoIdToma
                    : (tabla == TablePlatos ? desplazarAPlatoIdAdmin : null);
            if (desplazarObj != null) {
                int objetivo = desplazarObj;
                if (tabla == tblTemPlatos) {
                    desplazarAPlatoIdToma = null;
                }
                if (tabla == TablePlatos) {
                    desplazarAPlatoIdAdmin = null;
                }
                for (int i = 0; i < modelo.getRowCount(); i++) {
                    Object v = modelo.getValueAt(i, 0);
                    if (v != null && String.valueOf(objetivo).equals(v.toString())) {
                        int filaV = tabla.convertRowIndexToView(i);
                        if (filaV >= 0) {
                            tabla.setRowSelectionInterval(filaV, filaV);
                            tabla.scrollRectToVisible(tabla.getCellRect(filaV, 0, true));
                        }
                        break;
                    }
                }
            }
            installarRenderersPlatos(tabla);
        colorHeader(tabla);
    }

    private void installarRenderersPlatos(JTable tabla) {
        RendererPlato renderer = new RendererPlato(platosPorId);
        for (int i = 0; i < tabla.getColumnCount(); i++) {
            if (Tables.ID_COLUMNA_MONEDA.equals(tabla.getColumnModel().getColumn(i).getIdentifier())) {
                continue;
            }
            tabla.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    private void mostrarErrorCargaPlatos(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudieron cargar los platos. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar platos", JOptionPane.ERROR_MESSAGE);
    }

    // categorias
    private void configurarTabCategorias() {
        panelCategorias = new JPanel(new BorderLayout(12, 12));
        panelCategorias.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        modeloListaCategorias = new DefaultListModel<>();
        listaCategorias = new JList<>(modeloListaCategorias);
        listaCategorias.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaCategorias.setCellRenderer(new RendererCategoria());
        listaCategorias.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarCategoriaSeleccionadaEnFormulario();
            }
        });
        JScrollPane scrollCategorias = new JScrollPane(listaCategorias);
        scrollCategorias.setPreferredSize(new Dimension(300, 320));

        JButton btnSubirCategoria = new JButton("↑ Subir");
        JButton btnBajarCategoria = new JButton("↓ Bajar");
        btnSubirCategoria.setToolTipText("Mueve la categoría seleccionada hacia arriba en el orden visual.");
        btnBajarCategoria.setToolTipText("Mueve la categoría seleccionada hacia abajo en el orden visual.");
        btnSubirCategoria.addActionListener(e -> intercambiarCategorias(-1));
        btnBajarCategoria.addActionListener(e -> intercambiarCategorias(1));

        JPanel panelListaConBotones = new JPanel(new BorderLayout(4, 0));
        panelListaConBotones.add(scrollCategorias, BorderLayout.CENTER);
        
        JPanel panelBotonesMover = new JPanel(new GridLayout(2, 1, 0, 4));
        panelBotonesMover.add(btnSubirCategoria);
        panelBotonesMover.add(btnBajarCategoria);
        
        JPanel wrapperBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapperBotones.add(panelBotonesMover);
        panelListaConBotones.add(wrapperBotones, BorderLayout.EAST);

        txtNombreCategoria = new JTextField(16);
        cbColorCategoria = new JComboBox<>();
        cbColorCategoria.setRenderer(new RendererColor());

        btnGuardarCategoria = new JButton("Agregar");
        btnEditarCategoria = new JButton("Guardar cambios");
        btnEliminarCategoria = new JButton("Eliminar");
        btnGuardarCategoria.addActionListener(e -> registrarCategoria());
        btnEditarCategoria.addActionListener(e -> modificarCategoria());
        btnEliminarCategoria.addActionListener(e -> eliminarCategoria());

        boolean puedeGestionar = politicaAcceso.permite(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        btnGuardarCategoria.setEnabled(puedeGestionar);
        btnEditarCategoria.setEnabled(puedeGestionar);
        btnEliminarCategoria.setEnabled(puedeGestionar);
        txtNombreCategoria.setEnabled(puedeGestionar);
        cbColorCategoria.setEnabled(puedeGestionar);
        btnSubirCategoria.setEnabled(puedeGestionar);
        btnBajarCategoria.setEnabled(puedeGestionar);

        JPanel panelCrud = new JPanel(new GridBagLayout());
        panelCrud.setBorder(BorderFactory.createTitledBorder("Categorías del menú"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1; gbc.weighty = 1;
        panelCrud.add(panelListaConBotones, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = 1; gbc.weighty = 0;
        gbc.gridy = 1; panelCrud.add(new JLabel("Nombre:"), gbc);
        gbc.gridx = 1; panelCrud.add(txtNombreCategoria, gbc);
        gbc.gridx = 0; gbc.gridy = 2; panelCrud.add(new JLabel("Color:"), gbc);
        gbc.gridx = 1; panelCrud.add(cbColorCategoria, gbc);
        JPanel accionesCrud = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        accionesCrud.add(btnGuardarCategoria);
        accionesCrud.add(btnEditarCategoria);
        accionesCrud.add(btnEliminarCategoria);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panelCrud.add(accionesCrud, gbc);

        cbPlatoAsignacion = new JComboBox<>();
        cbPlatoAsignacion.setRenderer(new RendererPlatoCombo());
        cbCategoriaPlato = new JComboBox<>();
        cbCategoriaPlato.setRenderer(new RendererCategoria());
        chkFavoritoPlato = new JCheckBox("Marcar como favorito (siempre visible arriba en el pedido)");
        btnAsignarCategoria = new JButton("Aplicar al plato");
        btnAsignarCategoria.setEnabled(puedeGestionar);
        cbPlatoAsignacion.setEnabled(puedeGestionar);
        cbCategoriaPlato.setEnabled(puedeGestionar);
        chkFavoritoPlato.setEnabled(puedeGestionar);
        cbPlatoAsignacion.addActionListener(e -> sincronizarFavoritoDelPlato());
        btnAsignarCategoria.addActionListener(e -> aplicarCategoriaYFavorito());

        JPanel panelAsignacion = new JPanel(new GridBagLayout());
        panelAsignacion.setBorder(BorderFactory.createTitledBorder("Plato: categoría y favorito"));
        GridBagConstraints gbcA = new GridBagConstraints();
        gbcA.insets = new Insets(6, 4, 6, 4);
        gbcA.anchor = GridBagConstraints.WEST;
        gbcA.fill = GridBagConstraints.HORIZONTAL;
        gbcA.weightx = 1;
        gbcA.gridx = 0; gbcA.gridy = 0; gbcA.gridwidth = 2;
        panelAsignacion.add(new JLabel("Plato del menú de hoy:"), gbcA);
        gbcA.gridy = 1; panelAsignacion.add(cbPlatoAsignacion, gbcA);
        gbcA.gridy = 2; panelAsignacion.add(new JLabel("Categoría:"), gbcA);
        gbcA.gridy = 3; panelAsignacion.add(cbCategoriaPlato, gbcA);
        gbcA.gridy = 4; panelAsignacion.add(chkFavoritoPlato, gbcA);
        gbcA.gridy = 5; gbcA.fill = GridBagConstraints.NONE;
        panelAsignacion.add(btnAsignarCategoria, gbcA);

        panelCategorias.add(panelCrud, BorderLayout.WEST);
        panelCategorias.add(panelAsignacion, BorderLayout.CENTER);
        jTabbedPane1.addTab("Categorías", panelCategorias);
        jTabbedPane1.addChangeListener(e -> {
            int idx = jTabbedPane1.getSelectedIndex();
            if (idx == -1) return;
            if ("Categorías".equals(jTabbedPane1.getTitleAt(idx))) {
                cargarCategorias();
                cargarPlatosAsignacion();
            }
        });
    }

    private void cargarCategorias() {
        final Modelo.Categoria seleccionada = listaCategorias != null ? listaCategorias.getSelectedValue() : null;
        final long miVersion = ++versionCargaCategorias;
        Servicio.ConcurrenciaServicio.ejecutarAsync(
                () -> categoriaControlador().listar(),
                categorias -> { if (miVersion == versionCargaCategorias) mostrarCategoriasEnLista(categorias, seleccionada); },
                ex -> { if (miVersion == versionCargaCategorias) mostrarErrorCategorias(ex); });
    }

    private void mostrarCategoriasEnLista(java.util.List<Modelo.Categoria> categorias,
            Modelo.Categoria seleccionada) {
        try {
            modeloListaCategorias.clear();
            for (Modelo.Categoria c : categorias) {
                modeloListaCategorias.addElement(c);
            }
            poblarComboCategorias(categorias);
            if (seleccionada != null) {
                for (Modelo.Categoria c : categorias) {
                    if (c.getId() == seleccionada.getId()) {
                        listaCategorias.setSelectedValue(c, true);
                        break;
                    }
                }
            }
            refrescarColoresDisponibles();
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void poblarComboCategorias(List<Modelo.Categoria> categorias) {
        Modelo.Categoria actual = (Modelo.Categoria) cbCategoriaPlato.getSelectedItem();
        cbCategoriaPlato.removeAllItems();
        Modelo.Categoria sinCategoria = new Modelo.Categoria(0, "(Sin categoría)", PaletaCategorias.COLOR_CATEGORIA_GENERAL, 0);
        cbCategoriaPlato.addItem(sinCategoria);
        for (Modelo.Categoria c : categorias) {
            cbCategoriaPlato.addItem(c);
        }
        if (actual != null) {
            for (int i = 0; i < cbCategoriaPlato.getItemCount(); i++) {
                if (cbCategoriaPlato.getItemAt(i).getId() == actual.getId()) {
                    cbCategoriaPlato.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    private void refrescarColoresDisponibles() {
        Modelo.Categoria seleccionada = listaCategorias.getSelectedValue();
        java.util.Set<String> usados = new java.util.HashSet<>();
        for (int i = 0; i < modeloListaCategorias.size(); i++) {
            Modelo.Categoria c = modeloListaCategorias.getElementAt(i);
            if (c.getColor() != null) {
                usados.add(c.getColor().toUpperCase(java.util.Locale.ROOT));
            }
        }
        String colorActual = seleccionada != null ? seleccionada.getColor() : null;
        String elegido = (String) cbColorCategoria.getSelectedItem();
        cbColorCategoria.removeAllItems();
        for (String hex : PaletaCategorias.coloresDisponibles(usados)) {
            cbColorCategoria.addItem(hex);
        }
        if (colorActual != null && !contieneColor(colorActual)) {
            cbColorCategoria.addItem(colorActual);
        }
        if (elegido != null && contieneColor(elegido)) {
            cbColorCategoria.setSelectedItem(elegido);
        } else if (colorActual != null && contieneColor(colorActual)) {
            cbColorCategoria.setSelectedItem(colorActual);
        }
    }

    private boolean contieneColor(String hex) {
        for (int i = 0; i < cbColorCategoria.getItemCount(); i++) {
            if (hex != null && hex.equalsIgnoreCase(cbColorCategoria.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void cargarCategoriaSeleccionadaEnFormulario() {
        Modelo.Categoria c = listaCategorias.getSelectedValue();
        if (c == null) {
            return;
        }
        txtNombreCategoria.setText(c.getNombre());
        refrescarColoresDisponibles();
        if (c.getColor() != null) {
            cbColorCategoria.setSelectedItem(c.getColor());
        }
    }

    private void registrarCategoria() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            return;
        }
        if (txtNombreCategoria.getText().trim().isEmpty()) {
            mostrarAlertaCampo(txtNombreCategoria, "El nombre de la categoría es obligatorio.");
            return;
        }
        try {
            Modelo.Categoria c = new Modelo.Categoria();
            c.setNombre(txtNombreCategoria.getText());
            c.setColor((String) cbColorCategoria.getSelectedItem());
            categoriaControlador().registrar(c);
            limpiarFormularioCategoria();
            cargarCategorias();
            RefrescoVistas.notificar(RefrescoVistas.CATEGORIAS);
            mostrarAvisoEnBanda("Categoría registrada");
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void modificarCategoria() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            return;
        }
        Modelo.Categoria seleccionada = listaCategorias.getSelectedValue();
        if (seleccionada == null) {
            mostrarAvisoEnBanda("Selecciona una categoría para modificar");
            return;
        }
        if (txtNombreCategoria.getText().trim().isEmpty()) {
            mostrarAlertaCampo(txtNombreCategoria, "El nombre de la categoría es obligatorio.");
            return;
        }
        try {
            Modelo.Categoria c = new Modelo.Categoria(seleccionada.getId(), txtNombreCategoria.getText(),
                    (String) cbColorCategoria.getSelectedItem(), seleccionada.getOrden());
            categoriaControlador().modificar(c);
            cargarCategorias();
            RefrescoVistas.notificar(RefrescoVistas.CATEGORIAS);
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }
    private void intercambiarCategorias(int direccion) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        
        int idx = listaCategorias.getSelectedIndex();
        if (idx == -1) return;
        
        int nuevoIdx = idx + direccion;
        if (nuevoIdx < 0 || nuevoIdx >= modeloListaCategorias.getSize()) return;

        try {
            // Normalizamos todo a la secuencia visual
            for (int i = 0; i < modeloListaCategorias.getSize(); i++) {
                Modelo.Categoria c = modeloListaCategorias.getElementAt(i);
                c.setOrden(i);
            }
            
            Modelo.Categoria catActual = modeloListaCategorias.getElementAt(idx);
            Modelo.Categoria catDestino = modeloListaCategorias.getElementAt(nuevoIdx);
            
            int tempOrden = catActual.getOrden();
            catActual.setOrden(catDestino.getOrden());
            catDestino.setOrden(tempOrden);
            
            for (int i = 0; i < modeloListaCategorias.getSize(); i++) {
                categoriaControlador().modificar(modeloListaCategorias.getElementAt(i));
            }
            
            cargarCategorias();
            ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
            cargarPlatosAsignacion();
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void eliminarCategoria() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            return;
        }
        Modelo.Categoria seleccionada = listaCategorias.getSelectedValue();
        if (seleccionada == null) {
            mostrarAvisoEnBanda("Selecciona una categoría para eliminar");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Eliminar la categoría \"" + seleccionada.getNombre() + "\"?",
                "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            categoriaControlador().eliminar(seleccionada.getId());
            limpiarFormularioCategoria();
            cargarCategorias();
            RefrescoVistas.notificar(RefrescoVistas.CATEGORIAS);
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void limpiarFormularioCategoria() {
        txtNombreCategoria.setText("");
        listaCategorias.clearSelection();
        refrescarColoresDisponibles();
    }

    private void cargarPlatosAsignacion() {
        new ListaPlatosSwingWorker(platosControlador, "", fechaActual(),
                this::poblarComboPlatos, this::mostrarErrorCargaPlatos).execute();
    }

    private void poblarComboPlatos(List<Platos> platos) {
        Platos actual = (Platos) cbPlatoAsignacion.getSelectedItem();
        cbPlatoAsignacion.removeAllItems();
        for (Platos p : platos) {
            platosPorId.put(p.getId(), p);
            cbPlatoAsignacion.addItem(p);
        }
        if (actual != null) {
            for (int i = 0; i < cbPlatoAsignacion.getItemCount(); i++) {
                if (cbPlatoAsignacion.getItemAt(i).getId() == actual.getId()) {
                    cbPlatoAsignacion.setSelectedIndex(i);
                    return;
                }
            }
        }
        sincronizarFavoritoDelPlato();
    }

    private void sincronizarFavoritoDelPlato() {
        Platos p = (Platos) cbPlatoAsignacion.getSelectedItem();
        if (p == null) {
            chkFavoritoPlato.setSelected(false);
            return;
        }
        chkFavoritoPlato.setSelected(p.isFavorito());
        for (int i = 0; i < cbCategoriaPlato.getItemCount(); i++) {
            if (cbCategoriaPlato.getItemAt(i).getId() == p.getIdCategoria()) {
                cbCategoriaPlato.setSelectedIndex(i);
                break;
            }
        }
    }

    private void aplicarCategoriaYFavorito() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            return;
        }
        Platos p = (Platos) cbPlatoAsignacion.getSelectedItem();
        Modelo.Categoria c = (Modelo.Categoria) cbCategoriaPlato.getSelectedItem();
        if (p == null) {
            mostrarAlertaCampo(cbPlatoAsignacion, "Selecciona un plato.");
            return;
        }
        if (c == null) {
            mostrarAlertaCampo(cbCategoriaPlato, "Selecciona una categoría.");
            return;
        }
        try {
            categoriaControlador().asignarPlato(p.getNombre(), c.getId());
            favoritoControlador().marcar(p.getNombre(), chkFavoritoPlato.isSelected());
            cargarPlatosAsignacion();
            ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
            RefrescoVistas.notificar(RefrescoVistas.CATEGORIAS);
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void mostrarErrorCategorias(Throwable error) {
        if (error instanceof ErrorAplicacionException) {
            mostrarAlertaCampo(txtNombreCategoria, error.getMessage());
            return;
        }
        String mensaje = "No se pudieron actualizar las categorías. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Categorías", JOptionPane.ERROR_MESSAGE);
    }

    private final class RendererCategoria extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Modelo.Categoria c) {
                setText(c.getNombre() + (c.getId() == 0 ? "" : "  " + (c.getColor() != null ? c.getColor() : "")));
                setIcon(new IconoColor(c.getColor()));
            }
            return this;
        }
    }

    private final class RendererColor extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String hex) {
                setText(hex);
                setIcon(new IconoColor(hex));
            }
            return this;
        }
    }

    private final class RendererPlatoCombo extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Platos p) {
                String categoria = p.getCategoriaNombre() != null ? " · " + p.getCategoriaNombre() : "";
                StringBuilder prefijo = new StringBuilder();
                if (p.isFavorito()) {
                    prefijo.append("★ ");
                }
                if (p.getRankingTotal() != null && p.getRankingTotal().compareTo(BigDecimal.ZERO) > 0) {
                    prefijo.append("🔥 ");
                }
                setText(prefijo.toString() + p.getNombre() + categoria);
                if (p.getCategoriaColor() != null) {
                    setIcon(new IconoColor(p.getCategoriaColor()));
                } else {
                    setIcon(null);
                }
            }
            return this;
        }
    }

    private static final class IconoColor implements Icon {
        private final Color color;

        private IconoColor(String hex) {
            Color c;
            try {
                c = hex != null ? Modelo.PaletaCategorias.atenuar(hex, 0.30) : Color.GRAY;
            } catch (NumberFormatException e) {
                c = Color.GRAY;
            }
            this.color = c;
        }

        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 3, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y + 3, 12, 12);
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }


    //registrar pedido
    private int registrarPedidoCompleto() {
        int id_sala = Integer.parseInt(txtTempIdSala.getText());
        int num_mesa = Integer.parseInt(txtTempNumMesa.getText());
        BigDecimal tasa = getTasaActual();
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio configurada en el sistema es inválida o no ha sido establecida.");
        }
        tasa = tasa.setScale(4, RoundingMode.HALF_UP);
        BigDecimal ivaPorcentaje = getIvaPorcentajeActual();
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA configurado en el sistema es inválido o no ha sido establecido.");
        }
        ivaPorcentaje = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);
        List<DetallePedido> detalles = construirDetallesDesdeCarrito();
        Servicio.CalculoMonetarioPedido.CalculoPedido calc = Servicio.CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(id_sala);
        pedido.setNum_mesa(num_mesa);
        pedido.setTipoPedido(modoPedidoRapido ? "RAPIDO" : "MESA");
        pedido.setSubtotal(calc.subtotalUsd());
        pedido.setIvaPorcentaje(ivaPorcentaje);
        pedido.setIvaMonto(calc.ivaUsd());
        pedido.setTotalDecimal(calc.totalUsd());
        pedido.setTasaCambio(tasa);
        pedido.setSubtotalBs(calc.subtotalVes());
        pedido.setIvaBs(calc.ivaVes());
        pedido.setTotalBs(calc.totalVes());
        pedido.setUsuario(LabelVendedor.getText());
        if (cbMesoneroPedido != null && cbMesoneroPedido.getSelectedItem() instanceof Mesonero m) {
            pedido.setIdMesonero(m.getId());
            pedido.setMesoneroNombre(m.getNombreCompleto());
        }

        return pedidosControlador.registrarPedidoCompleto(pedido, detalles);
    }

    private void actualizarPedidoEnEdicion() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        int id_sala = Integer.parseInt(txtTempIdSala.getText());
        int num_mesa = Integer.parseInt(txtTempNumMesa.getText());
        BigDecimal tasa = getTasaActual();
        if (tasa == null || tasa.signum() <= 0) {
            throw ErrorAplicacionException.validacion("La tasa de cambio configurada en el sistema es inválida o no ha sido establecida.");
        }
        tasa = tasa.setScale(4, RoundingMode.HALF_UP);
        BigDecimal ivaPorcentaje = getIvaPorcentajeActual();
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA configurado en el sistema es inválido o no ha sido establecido.");
        }
        ivaPorcentaje = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);

        List<DetallePedido> detalles = construirDetallesDesdeCarrito();
        Servicio.CalculoMonetarioPedido.CalculoPedido calc = Servicio.CalculoMonetarioPedido.calcularPedido(detalles, tasa);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(id_sala);
        pedido.setNum_mesa(num_mesa);
        pedido.setSubtotal(calc.subtotalUsd());
        pedido.setIvaPorcentaje(ivaPorcentaje);
        pedido.setIvaMonto(calc.ivaUsd());
        pedido.setTotalDecimal(calc.totalUsd());
        pedido.setTasaCambio(tasa);
        pedido.setSubtotalBs(calc.subtotalVes());
        pedido.setIvaBs(calc.ivaVes());
        pedido.setTotalBs(calc.totalVes());
        pedido.setUsuario(LabelVendedor.getText());
        if (cbMesoneroPedido != null && cbMesoneroPedido.getSelectedItem() instanceof Mesonero m) {
            pedido.setIdMesonero(m.getId());
            pedido.setMesoneroNombre(m.getNombreCompleto());
        } else if (!quitarMesoneroEdicion && idPedidoEdicion > 0 && idMesoneroEdicionOriginal != null) {
            pedido.setIdMesonero(idMesoneroEdicionOriginal);
            pedido.setMesoneroNombre(nombreMesoneroEdicionOriginal);
        }

        pedidosControlador.actualizarPedidoCompleto(idPedidoEdicion, pedido, detalles);
    }

    private List<DetallePedido> construirDetallesDesdeCarrito() {
        BigDecimal ivaPorcentaje = getIvaPorcentajeActual();
        if (ivaPorcentaje == null || ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA configurado en el sistema es inválido o no ha sido establecido.");
        }
        ivaPorcentaje = ivaPorcentaje.setScale(2, RoundingMode.HALF_UP);
        List<DetallePedido> detalles = new ArrayList<>();
        for (int i = 0; i < tableMenu.getRowCount(); i++) {
            DetallePedido detalle = new DetallePedido();
            detalle.setNombre(Tables.valorModelo(tableMenu, i, 1).toString());
            detalle.setCantidad(Integer.parseInt(Tables.valorModelo(tableMenu, i, 2).toString()));
            detalle.setPrecioDecimal(importeMonetario(Tables.valorModelo(tableMenu, i, 3)));
            Object comentario = Tables.valorModelo(tableMenu, i, 5);
            detalle.setComentario(comentario == null ? "" : comentario.toString());

            Object idObj = Tables.valorModelo(tableMenu, i, 0);
            Integer idPlato = null;
            if (idObj != null && !idObj.toString().isBlank()) {
                try {
                    idPlato = Integer.parseInt(idObj.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
            detalle.setIdPlato(idPlato);

            Platos plato = idPlato != null ? platosPorId.get(idPlato) : null;
            if (plato == null) {
                for (Platos p : platosPorId.values()) {
                    if (p != null && p.getNombre() != null && p.getNombre().equalsIgnoreCase(detalle.getNombre())) {
                        plato = p;
                        detalle.setIdPlato(p.getId());
                        break;
                    }
                }
            }

            if (plato != null) {
                detalle.setMonedaBase(plato.getMonedaBase());
                detalle.setAplicaIva(plato.isAplicaIva());
                detalle.setPrecioIncluyeIva(plato.getPrecioIncluyeIva());
                detalle.setIvaPorcentaje(plato.isAplicaIva() ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));
            } else {
                String valCelda = String.valueOf(Tables.valorModelo(tableMenu, i, 3)).trim().toUpperCase(java.util.Locale.ROOT);
                if (valCelda.startsWith("BS") || valCelda.contains("BS.")) {
                    detalle.setMonedaBase("VES");
                } else {
                    detalle.setMonedaBase("USD");
                }
                boolean aplica = aplicaIvaEnFila(i);
                detalle.setAplicaIva(aplica);
                detalle.setPrecioIncluyeIva(Boolean.FALSE);
                detalle.setIvaPorcentaje(aplica ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));
            }
            detalles.add(detalle);
        }
        return detalles;
    }

    private boolean aplicaIvaEnFila(int fila) {
        if (tableMenu.getModel().getColumnCount() > 6 && Tables.valorModelo(tableMenu, fila, 6) != null) {
            String valor = Tables.valorModelo(tableMenu, fila, 6).toString().toLowerCase(java.util.Locale.ROOT);
            return !(valor.contains("exento") || valor.contains("no") || valor.equals("false") || valor.equals("0"));
        }
        Object idObj = Tables.valorModelo(tableMenu, fila, 0);
        if (idObj != null) {
            try {
                return platoAplicaIvaMap.getOrDefault(Integer.parseInt(idObj.toString()), true);
            } catch (NumberFormatException ignored) {
                // Las líneas sin ID de plato conservan el comportamiento gravado por defecto.
            }
        }
        return true;
    }

    private String fechaActual() {
        return LocalDate.now().toString();
    }

    public static BigDecimal importeMonetario(Object valor) {
        if (valor == null) {
            throw ErrorAplicacionException.validacion("El importe es obligatorio.");
        }
        if (valor instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        String str = String.valueOf(valor).trim();
        if (str.isEmpty()) {
            throw ErrorAplicacionException.validacion("El importe es obligatorio.");
        }
        // Si contiene información adicional como "$ 12.25 (Bs. 450.00)", quedarse con el primer monto
        if (str.contains("(")) {
            str = str.substring(0, str.indexOf('(')).trim();
        }
        // Limpiar etiqueta de producto no clasificado si existe
        str = str.replace("[!]", "").trim();
        // Limpiar prefijos de moneda como $, Bs., Bs
        str = str.replace("Bs.", "").replace("Bs", "").replace("$", "").trim();
        // Aceptar coma decimal convirtiéndola a punto
        str = str.replace(',', '.');
        try {
            BigDecimal importe = new BigDecimal(str);
            if (importe.precision() - importe.scale() > 12) {
                throw new ArithmeticException("El importe excede DECIMAL(14,2).");
            }
            return importe.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw ErrorAplicacionException.validacion("Ingresa un importe válido.");
        }
    }

    public static String formatearMontoCelda(BigDecimal monto, String monedaBase, BigDecimal tasa) {
        if (monto == null) {
            return "0.00";
        }
        BigDecimal montoNorm = monto.setScale(2, RoundingMode.HALF_UP);
        boolean tasaValida = tasa != null && tasa.signum() > 0;
        if ("VES".equalsIgnoreCase(monedaBase)) {
            BigDecimal refUsd = tasaValida ? montoNorm.divide(tasa, 2, RoundingMode.HALF_UP) : null;
            return "Bs. " + String.format(java.util.Locale.US, "%.2f", montoNorm)
                    + (refUsd != null ? " ($ " + String.format(java.util.Locale.US, "%.2f", refUsd) + ")" : "");
        } else {
            BigDecimal refBs = tasaValida ? montoNorm.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : null;
            return "$ " + String.format(java.util.Locale.US, "%.2f", montoNorm)
                    + (refBs != null ? " (Bs. " + String.format(java.util.Locale.US, "%.2f", refBs) + ")" : "");
        }
    }

    private String monedaFilaEnCarrito(JTable tabla, int fila) {
        if (tabla == null || fila < 0 || fila >= tabla.getRowCount()) {
            return "USD";
        }
        Object idObj = tabla.getModel().getValueAt(fila, 0);
        if (idObj != null && !idObj.toString().isBlank()) {
            try {
                Platos p = platosPorId.get(Integer.parseInt(idObj.toString().trim()));
                if (p != null && p.getMonedaBase() != null) {
                    return p.getMonedaBase().toUpperCase(java.util.Locale.ROOT);
                }
            } catch (NumberFormatException ignored) {}
        }
        Object celda = tabla.getModel().getValueAt(fila, 3);
        if (celda != null) {
            String s = celda.toString().trim().toUpperCase(java.util.Locale.ROOT);
            if (s.startsWith("BS") || s.contains("BS.")) {
                return "VES";
            }
        }
        return "USD";
    }

    private void cargarPedidoEnPantalla(int id_pedido, boolean permitirFinalizar) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        long version = ++versionPedidoPantalla;
        btnFinalizar.setEnabled(false);
        new PedidoEnPantallaSwingWorker(pedidosControlador, id_pedido,
                resultado -> {
                    if (version != versionPedidoPantalla) return;
                    mostrarPedidoEnPantalla(resultado.getPedido(), resultado.getDetalles());
                    btnFinalizar.setEnabled(permitirFinalizar);
                    btnPdfPedido.setEnabled(!permitirFinalizar);
                    if (btnPrevisualizarPedido != null) {
                        btnPrevisualizarPedido.setEnabled(true);
                    }
                    txtIdHistorialPedido.setText(permitirFinalizar ? "" : String.valueOf(id_pedido));
                    jTabbedPane1.setSelectedIndex(4);
                }, error -> {
                    if (version == versionPedidoPantalla) {
                        mostrarErrorCargaPedido(error);
                    }
                }).execute();
    }

    private void mostrarPedidoEnPantalla(Pedidos pedido, List<DetallePedido> Listar) {
        ped = pedido;
        BigDecimal tasa = ped.getTasaCambio();
        boolean tasaValida = tasa != null && tasa.signum() > 0;
        BigDecimal totalBs = ped.getTotalBs();
        if (totalBs == null && tasaValida) {
            totalBs = ped.getTotalDecimal().multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalBs2Dec = totalBs != null ? totalBs.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal totalUsd2Dec = ped.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotalUsd = ped.getSubtotal() != null ? ped.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd2Dec;
        BigDecimal ivaUsd = ped.getIvaMonto() != null ? ped.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal ivaBs = ped.getIvaBs() != null
                ? ped.getIvaBs().setScale(2, RoundingMode.HALF_UP)
                : tasaValida ? ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal ivaPct = ped.getIvaPorcentaje() != null ? ped.getIvaPorcentaje().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        String totalBsStr = totalBs2Dec != null
                ? String.format(java.util.Locale.US, "%.2f", totalBs2Dec) : "N/D";
        String totalUsdStr = String.format(java.util.Locale.US, "%.2f", totalUsd2Dec);
        String subtotalUsdStr = String.format(java.util.Locale.US, "%.2f", subtotalUsd);
        String ivaUsdStr = String.format(java.util.Locale.US, "%.2f", ivaUsd);
        String ivaBsStr = ivaBs != null ? String.format(java.util.Locale.US, "%.2f", ivaBs) : "N/D";
        String ivaPctStr = String.format(java.util.Locale.US, "%.2f", ivaPct);

        totalFinalizar.setText("USD $ " + totalUsdStr + "  |  Bs. " + totalBsStr);
        if (ivaPct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseImpUsd = ivaUsd.multiply(new BigDecimal("100")).divide(ivaPct, 2, RoundingMode.HALF_UP);
            BigDecimal exentoUsd = subtotalUsd.subtract(baseImpUsd);
            if (exentoUsd.compareTo(BigDecimal.ZERO) > 0) {
                totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                        + " (Base Imponible: $ " + String.format(java.util.Locale.US, "%.2f", baseImpUsd)
                        + ", Exento: $ " + String.format(java.util.Locale.US, "%.2f", exentoUsd) + ")"
                        + " | IVA (" + ivaPctStr + "%): $ " + ivaUsdStr + " (Bs. " + ivaBsStr + ")"
                        + " | Total a Pagar (IVA incluido): USD $ " + totalUsdStr
                        + " | Bs. " + totalBsStr);
            } else {
                totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                        + " | IVA (" + ivaPctStr + "%): $ " + ivaUsdStr + " (Bs. " + ivaBsStr + ")"
                        + " | Total a Pagar (IVA incluido): USD $ " + totalUsdStr
                        + " | Bs. " + totalBsStr);
            }
        } else {
            totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                    + " | IVA: $ " + ivaUsdStr + " (Bs. " + ivaBsStr + ")"
                    + " | Total a Pagar (IVA incluido): USD $ " + totalUsdStr
                    + " | Bs. " + totalBsStr);
        }

        txtFechaHora.setText("" + ped.getFecha());
        txtSalaFinalizar.setText(ped.esPedidoRapido() ? "Para llevar" : String.valueOf(ped.getSala()));
        txtNumMesaFinalizar.setText(ped.esPedidoRapido() ? "—" : String.valueOf(ped.getNum_mesa()));
        jLabel8.setText(ped.esPedidoRapido() ? "Tipo:" : "Sala:");
        jLabel9.setText(ped.esPedidoRapido() ? "Mesa:" : "N° Mesa:");
        tasaPagoFac = tasa;
        totalPagoBsFac = totalBs2Dec;
        modeloPagosFac.setRowCount(0);
        modeloPagosFac.addRow(new Object[]{"EFECTIVO_BS", "VES",
                totalBs2Dec == null ? "" : totalBs2Dec.toPlainString()});
        if (!tasaValida || totalBs2Dec == null) {
            lblAlertaPagosFac.setText("No se puede cobrar: el pedido no tiene tasa/total Bs. válidos guardados en BD.");
        } else {
            lblAlertaPagosFac.setText(" ");
        }
        actualizarResumenPagosFac();
        if (ped.getSala() != null && ped.getSala().toUpperCase(java.util.Locale.ROOT).contains("BARRA")) {
            txtNumMesaFinalizar.setToolTipText("Puesto N° " + ped.getNum_mesa());
        }
        txtIdPedido.setText("" + ped.getId());
        if (txtMesoneroFinalizar != null) {
            String mNom = ped.getMesoneroNombre();
            txtMesoneroFinalizar.setText((mNom != null && !mNom.isBlank()) ? mNom : "Sin Mesonero Asignado");
        }

        modelo = (DefaultTableModel) tableFinalizar.getModel();
        modelo.setRowCount(0);
        monedaFilaFinalizar.clear();
        boolean esClasificado = tasaValida && Listar.stream().allMatch(d -> d.getMonedaBase() != null && d.getAplicaIva() != null && d.getPrecioIncluyeIva() != null);
        Servicio.CalculoMonetarioPedido.CalculoPedido calcPantalla = esClasificado ? Servicio.CalculoMonetarioPedido.calcularPedido(Listar, tasa) : null;
        Object[] ob = new Object[6];

        for (int i = 0; i < Listar.size(); i++) {
            DetallePedido det = Listar.get(i);
            ob[0] = det.getId();
            ob[1] = det.getNombre();
            ob[2] = det.getCantidad();
            BigDecimal precioUsd;
            BigDecimal subtotalPlatoUsd;
            String precioBs;
            if (calcPantalla != null) {
                Servicio.CalculoMonetarioPedido.CalculoLinea lin = calcPantalla.lineas().get(i);
                if ("VES".equalsIgnoreCase(lin.monedaBase())) {
                    BigDecimal unitBs = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal unitUsd = lin.totalUsdReferencia().divide(BigDecimal.valueOf(det.getCantidad()), 2, RoundingMode.HALF_UP);
                    precioUsd = unitUsd;
                    subtotalPlatoUsd = lin.totalUsdReferencia();
                    precioBs = "Bs. " + String.format(java.util.Locale.US, "%.2f", unitBs);
                } else {
                    precioUsd = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    subtotalPlatoUsd = lin.totalUsdReferencia();
                    precioBs = "Bs. " + String.format(java.util.Locale.US, "%.2f", precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
                }
            } else {
                precioUsd = det.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                subtotalPlatoUsd = precioUsd.multiply(BigDecimal.valueOf(det.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                precioBs = tasaValida
                        ? "Bs. " + String.format(java.util.Locale.US, "%.2f", precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP))
                        : "Bs. N/D";
            }
            String subtotalBsStr;
            if (calcPantalla != null) {
                Servicio.CalculoMonetarioPedido.CalculoLinea lin = calcPantalla.lineas().get(i);
                subtotalBsStr = "Bs. " + String.format(java.util.Locale.US, "%.2f", lin.totalVes());
            } else {
                BigDecimal subBs = tasaValida ? subtotalPlatoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : null;
                subtotalBsStr = subBs != null ? "Bs. " + String.format(java.util.Locale.US, "%.2f", subBs) : "Bs. N/D";
            }
            ob[3] = "$ " + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (" + precioBs + ")";
            ob[4] = "$ " + String.format(java.util.Locale.US, "%.2f", subtotalPlatoUsd) + " (" + subtotalBsStr + ")";
            ob[5] = det.getComentario();
            modelo.addRow(ob);
            monedaFilaFinalizar.put(modelo.getRowCount() - 1, ub(det.getMonedaBase()));
        }
        java.util.List<String> monedasFin = new java.util.ArrayList<>();
        for (Modelo.DetallePedido det : Listar) {
            monedasFin.add(det.getMonedaBase());
        }
        Tables.fijarCabeceraMoneda(tableFinalizar, 3, monedasFin);
        Tables.fijarCabeceraMoneda(tableFinalizar, 4, monedasFin);
        colorHeader(tableFinalizar);
    }

    private void mostrarErrorCargaPedido(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudo cargar el pedido. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar pedido", JOptionPane.ERROR_MESSAGE);
    }

}
