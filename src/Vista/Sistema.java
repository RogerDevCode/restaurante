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
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

public final class Sistema extends javax.swing.JFrame {

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
    private String ultimoTextoIngresado = "";
    private boolean isUpdatingUI = false;
    private Servicio.CierreCajaServicio cierreCajaServicio;
    private javax.swing.JLabel lblTituloSalaMesas;
    private javax.swing.JLabel lblUltimaCargaMesas;
    private javax.swing.JButton btnActualizarMesas;
    private int idSalaActualMesas;
    private String nombreSalaActualMesas;
    private int cantMesasActual;
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
    private boolean salaActualEsBarra = false;
    private javax.swing.JCheckBox chkAplicaIvaPlato;
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
        initComponents();
        aplicarMejorasDeUX();
        tableMenu.setRowHeight(25);
        tblTemPlatos.setRowHeight(30);
        tblTemPlatos.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        politicaAcceso = new PoliticaAcceso(priv);
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
                    actualizarEstadoBotonesCarrito();
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
        btnAnularPedido.setForeground(new java.awt.Color(180, 40, 40));
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
        jPanel6.add(cbModoSalidaHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(535, 10, 175, 32));

        btnCierreParcial = new javax.swing.JButton("Cierre Parcial (X)");
        btnCierreParcial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnCierreParcial.setToolTipText("Generar e imprimir Cierre Parcial de Caja (Corte X) de la jornada");
        btnCierreParcial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        java.net.URL urlPrintHist = getClass().getResource("/Img/print.png");
        if (urlPrintHist != null) {
            btnCierreParcial.setIcon(new javax.swing.ImageIcon(urlPrintHist));
        }
        btnCierreParcial.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.PARCIAL));
        jPanel6.add(btnCierreParcial, new org.netbeans.lib.awtextra.AbsoluteConstraints(720, 10, 165, 32));

        btnCierreTotal = new javax.swing.JButton("Cierre Total (Z)");
        btnCierreTotal.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnCierreTotal.setToolTipText("Generar e imprimir Cierre Total definitivo de Caja (Corte Z) de la jornada");
        btnCierreTotal.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        if (urlPrintHist != null) {
            btnCierreTotal.setIcon(new javax.swing.ImageIcon(urlPrintHist));
        }
        btnCierreTotal.addActionListener(e -> solicitarCierreCaja(Modelo.CierreCaja.TipoCierre.TOTAL));
        jPanel6.add(btnCierreTotal, new org.netbeans.lib.awtextra.AbsoluteConstraints(895, 10, 165, 32));

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
        jPanel6.add(txtBuscarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 48, 150, 30));

        btnFiltroHoy = new javax.swing.JButton("Hoy");
        btnFiltroHoy.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroHoy.setToolTipText("Mostrar sólo pedidos realizados hoy");
        btnFiltroHoy.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroHoy.addActionListener(e -> filtrarHistorialHoy());
        jPanel6.add(btnFiltroHoy, new org.netbeans.lib.awtextra.AbsoluteConstraints(245, 48, 55, 30));

        btnFiltroPendientes = new javax.swing.JButton("Pendientes");
        btnFiltroPendientes.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroPendientes.setToolTipText("Mostrar sólo pedidos en estado PENDIENTE");
        btnFiltroPendientes.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroPendientes.addActionListener(e -> filtrarHistorialPendientes());
        jPanel6.add(btnFiltroPendientes, new org.netbeans.lib.awtextra.AbsoluteConstraints(305, 48, 85, 30));

        btnFiltroFinalizados = new javax.swing.JButton("Finalizados");
        btnFiltroFinalizados.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroFinalizados.setToolTipText("Mostrar sólo pedidos en estado FINALIZADO");
        btnFiltroFinalizados.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroFinalizados.addActionListener(e -> filtrarHistorialFinalizados());
        jPanel6.add(btnFiltroFinalizados, new org.netbeans.lib.awtextra.AbsoluteConstraints(395, 48, 85, 30));

        javax.swing.JLabel lblDesde = new javax.swing.JLabel("Desde:");
        lblDesde.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        jPanel6.add(lblDesde, new org.netbeans.lib.awtextra.AbsoluteConstraints(485, 48, 40, 30));

        txtFiltroFechaDesde = new javax.swing.JTextField();
        txtFiltroFechaDesde.setToolTipText("Fecha inicial (AAAA-MM-DD)");
        txtFiltroFechaDesde.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(txtFiltroFechaDesde, new org.netbeans.lib.awtextra.AbsoluteConstraints(528, 48, 75, 30));

        javax.swing.JLabel lblHasta = new javax.swing.JLabel("Hasta:");
        lblHasta.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        jPanel6.add(lblHasta, new org.netbeans.lib.awtextra.AbsoluteConstraints(608, 48, 38, 30));

        txtFiltroFechaHasta = new javax.swing.JTextField();
        txtFiltroFechaHasta.setToolTipText("Fecha final (AAAA-MM-DD)");
        txtFiltroFechaHasta.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(txtFiltroFechaHasta, new org.netbeans.lib.awtextra.AbsoluteConstraints(648, 48, 75, 30));

        btnFiltroRangoFecha = new javax.swing.JButton("Rango");
        btnFiltroRangoFecha.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnFiltroRangoFecha.setToolTipText("Aplicar filtro de rango de fechas");
        btnFiltroRangoFecha.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnFiltroRangoFecha.addActionListener(e -> { filtroSoloHoyActivo = false; aplicarFiltroHistorial(); });
        jPanel6.add(btnFiltroRangoFecha, new org.netbeans.lib.awtextra.AbsoluteConstraints(728, 48, 60, 30));

        btnLimpiarFiltrosHistorial = new javax.swing.JButton("Limpiar");
        btnLimpiarFiltrosHistorial.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
        btnLimpiarFiltrosHistorial.setToolTipText("Limpiar todos los filtros y restaurar historial completo");
        btnLimpiarFiltrosHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnLimpiarFiltrosHistorial.addActionListener(e -> limpiarFiltrosHistorial());
        jPanel6.add(btnLimpiarFiltrosHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(793, 48, 65, 30));

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
                JOptionPane.showMessageDialog(this, "Seleccione un pedido finalizado de la tabla para reimprimir su factura.",
                        "Pedido requerido", JOptionPane.WARNING_MESSAGE);
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
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
                if (!impreso) {
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        jPanel6.add(btnReimprimirHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(863, 48, 95, 30));

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
                JOptionPane.showMessageDialog(this, "Seleccione un pedido de la tabla para previsualizar su factura.",
                        "Pedido requerido", JOptionPane.WARNING_MESSAGE);
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
        jPanel6.add(btnPrevisualizarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(963, 48, 100, 30));

        btnPrevisualizarPedido = new javax.swing.JButton("Previsualizar");
        btnPrevisualizarPedido.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnPrevisualizarPedido.setToolTipText("Pre-visualizar ticket en pantalla (opcional - puede imprimir desde el visor)");
        btnPrevisualizarPedido.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnPrevisualizarPedido.setEnabled(false);
        if (urlLupa != null) {
            btnPrevisualizarPedido.setIcon(new javax.swing.ImageIcon(urlLupa));
        }
        btnPrevisualizarPedido.addActionListener(this::btnPrevisualizarPedidoActionPerformed);
        jPanel25.add(btnPrevisualizarPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(640, 440, 130, 40));

        javax.swing.JLabel lblDestinoFinalizar = new javax.swing.JLabel("Salida:");
        lblDestinoFinalizar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        jPanel25.add(lblDestinoFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(440, 420, 60, 20));

        cbModoSalidaFinalizar = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbModoSalidaFinalizar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        cbModoSalidaFinalizar.setToolTipText("Modo de salida del comprobante (Tickera térmica, PDF o PDF24 Creator)");
        cbModoSalidaFinalizar.addActionListener(e -> {
            if (!sincronizandoModoSalida && cbModoSalidaFinalizar.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
        });
        jPanel25.add(cbModoSalidaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(440, 442, 185, 36));

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
        btnRestablecerLogo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
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
            JOptionPane.showMessageDialog(this, "Lista de impresoras del sistema actualizada (" + (cbImpresorasConfig.getItemCount() - 1) + " impresoras detectadas).", "Impresoras", JOptionPane.INFORMATION_MESSAGE);
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
        jPanel8.add(lblFechaTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 285, 175, 15));

        // Campos para configuración de Cliente Predeterminado en jPanel8
        javax.swing.JLabel lblCliDef = new javax.swing.JLabel("Cliente Predeterminado");
        lblCliDef.setFont(new java.awt.Font("Times New Roman", java.awt.Font.BOLD, 13));
        jPanel8.add(lblCliDef, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 295, 200, 20));

        txtClienteDefaultNombre = new javax.swing.JTextField();
        txtClienteDefaultNombre.setBackground(new java.awt.Color(255, 255, 255));
        jPanel8.add(txtClienteDefaultNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 318, 220, 30));

        javax.swing.JLabel lblDocDef = new javax.swing.JLabel("Cédula / RIF Predeterminado");
        lblDocDef.setFont(new java.awt.Font("Times New Roman", java.awt.Font.BOLD, 13));
        jPanel8.add(lblDocDef, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 355, 200, 20));

        txtClienteDefaultDoc = new javax.swing.JTextField();
        txtClienteDefaultDoc.setBackground(new java.awt.Color(255, 255, 255));
        jPanel8.add(txtClienteDefaultDoc, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 375, 147, 28));

        // Retención de historial y purga en jPanel8
        lblRetencionConfig = new javax.swing.JLabel("Retención Historial (meses):");
        lblRetencionConfig.setFont(new java.awt.Font("Times New Roman", java.awt.Font.BOLD, 13));
        jPanel8.add(lblRetencionConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 355, 220, 20));

        spMesesRetencion = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(24, 1, 120, 1));
        spMesesRetencion.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        spMesesRetencion.setToolTipText("Cantidad de meses que se conservan los pedidos finalizados antes de purgar");
        spMesesRetencion.setEnabled(politicaAcceso.esAdministrador());
        jPanel8.add(spMesesRetencion, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 375, 120, 28));

        jPanel8.remove(btnActualizarConfig);
        btnActualizarConfig.setBounds(15, 415, 185, 45);
        jPanel8.add(btnActualizarConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(15, 415, 185, 45));

        btnPurgarHistorial = new javax.swing.JButton("Purgar Historial");
        btnPurgarHistorial.setFont(new java.awt.Font("Times New Roman", java.awt.Font.BOLD, 13));
        btnPurgarHistorial.setBackground(new java.awt.Color(200, 35, 51));
        btnPurgarHistorial.setForeground(java.awt.Color.WHITE);
        btnPurgarHistorial.setToolTipText("Elimina pedidos finalizados anteriores al período de retención");
        btnPurgarHistorial.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        boolean esAdminRetencion = politicaAcceso.esAdministrador();
        btnPurgarHistorial.setVisible(esAdminRetencion);
        btnPurgarHistorial.setEnabled(esAdminRetencion);
        btnPurgarHistorial.addActionListener(e -> purgarHistorialPedidos());
        jPanel8.add(btnPurgarHistorial, new org.netbeans.lib.awtextra.AbsoluteConstraints(215, 415, 195, 45));

        initPanelMesasMejorado();
        initDashboardYClientes();
        initIvaPlatosYBarraSalas();

        if (politicaAcceso.esAdministrador() && panelDashboard != null) {
            int idxDashboard = jTabbedPane1.indexOfComponent(panelDashboard);
            if (idxDashboard >= 0) {
                jTabbedPane1.setEnabledAt(idxDashboard, true);
                jTabbedPane1.setSelectedIndex(idxDashboard);
            }
            cargarDashboardYClientes();
        }

        panelSalas();
        ListarConfig();
        actualizarEstadoTasaUI();
        configurarTabCategorias();

        jTabbedPane1.addChangeListener(e -> {
            java.awt.Component sel = jTabbedPane1.getSelectedComponent();
            if (sel == jPanel22) {
                if (idSalaActualMesas > 0 && cantMesasActual > 0) {
                    panelMesas(idSalaActualMesas, nombreSalaActualMesas, cantMesasActual);
                }
            } else if (sel == jPanel9) {
                panelSalas();
            } else if (panelDashboard != null && sel == panelDashboard
                    && politicaAcceso.esAdministrador()) {
                cargarDashboardYClientes();
            } else if (sel == jPanel12) {
                ListarUsuarios();
            }
        });
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
        btnSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/sala.png"))); // NOI18N
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
        btnVentas.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/pedidos.png"))); // NOI18N
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
        btnConfig.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/config.png"))); // NOI18N
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
        btnUsuarios.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/usuarios.png"))); // NOI18N
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
        btnPlatos.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/platos.png"))); // NOI18N
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
        btnCategorias.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/platos.png"))); 
        btnCategorias.setText("Categorías");
        btnCategorias.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnCategorias.setFocusable(false);
        btnCategorias.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
                jTabbedPane1.setSelectedComponent(panelCategorias);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnSala, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnVentas, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnConfig, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(LabelVendedor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnUsuarios, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(74, 74, 74)
                .addComponent(tipo)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(btnPlatos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnCategorias, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(labelLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 199, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(labelLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(tipo)
                .addGap(18, 18, 18)
                .addComponent(LabelVendedor)
                .addGap(18, 18, 18)
                .addComponent(btnPlatos, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnCategorias, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnSala, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnVentas, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnConfig, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(btnUsuarios, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(50, Short.MAX_VALUE))
        );

        getContentPane().add(jPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 200, 720));

        jLabel38.setFont(new java.awt.Font("Zilla Slab", 3, 48)); // NOI18N
        jLabel38.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/titulo.png"))); // NOI18N
        jLabel38.setText("Restaurante");
        jLabel38.setFocusable(false);
        jLabel38.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        getContentPane().add(jLabel38, new org.netbeans.lib.awtextra.AbsoluteConstraints(520, 0, 540, 90));

        jTabbedPane1.setBackground(new java.awt.Color(255, 255, 255));

        PanelSalas.setBackground(new java.awt.Color(255, 255, 255));
        PanelSalas.setLayout(new java.awt.GridLayout(0, 5));
        jScrollPane8.setViewportView(PanelSalas);

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 1030, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 540, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jTabbedPane1.addTab("Panel", jPanel9);

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));
        jPanel4.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

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

        jPanel4.add(jScrollPane3, new org.netbeans.lib.awtextra.AbsoluteConstraints(510, 80, 490, 470));

        jPanel10.setBackground(new java.awt.Color(204, 204, 204));
        jPanel10.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel18.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel18.setText("Nombre:");
        jPanel10.add(jLabel18, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 80, -1, -1));

        txtNombreSala.setBackground(new java.awt.Color(204, 204, 204));
        txtNombreSala.setBorder(null);
        jPanel10.add(txtNombreSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 70, 190, 30));

        btnRegistrarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/GuardarTodo.png"))); // NOI18N
        btnRegistrarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRegistrarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnRegistrarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 230, 100, 40));

        btnActualizarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/Actualizar (2).png"))); // NOI18N
        btnActualizarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnActualizarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnActualizarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 230, 100, 40));

        btnNuevoSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/nuevo.png"))); // NOI18N
        btnNuevoSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNuevoSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnNuevoSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 300, 100, 40));

        btnEliminarSala.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/eliminar.png"))); // NOI18N
        btnEliminarSala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEliminarSalaActionPerformed(evt);
            }
        });
        jPanel10.add(btnEliminarSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 300, 100, 40));
        jPanel10.add(txtIdSala, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 50, 24, -1));

        jPanel35.setBackground(new java.awt.Color(0, 0, 0));

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

        jPanel38.setBackground(new java.awt.Color(0, 0, 0));
        jPanel38.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel33.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel33.setForeground(new java.awt.Color(255, 255, 255));
        jLabel33.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel33.setText("Nuevo Sala");
        jPanel38.add(jLabel33, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 30));

        jPanel10.add(jPanel38, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 35));

        jPanel36.setBackground(new java.awt.Color(0, 0, 0));

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

        txtMesas.setBackground(new java.awt.Color(204, 204, 204));
        txtMesas.setBorder(null);
        jPanel10.add(txtMesas, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 140, 190, 30));

        jLabel19.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel19.setText("Mesas:");
        jPanel10.add(jLabel19, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 150, -1, -1));

        jPanel4.add(jPanel10, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 140, 310, 370));

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
                        int id = Integer.parseInt(tblTemPlatos.getValueAt(row, 0).toString());
                        String nombreNuevo = tblTemPlatos.getValueAt(row, 1).toString();
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


        btnAddPlato.setBackground(new java.awt.Color(0, 0, 0));
        btnAddPlato.setFont(new java.awt.Font("Arial Black", 1, 24)); // NOI18N
        btnAddPlato.setForeground(new java.awt.Color(255, 255, 255));
        btnAddPlato.setText("+");
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
                        .addComponent(txtBuscarPlato, javax.swing.GroupLayout.PREFERRED_SIZE, 349, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnLimpiarBuscar, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                    .addComponent(btnAddPlato, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                .addContainerGap())
        );

        tableMenu.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Plato", "Cant", "Precio", "SubTotal", "Comentario"
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
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                javax.swing.SwingUtilities.invokeLater(() -> txtCantidadManual.selectAll());
                if (tableMenu.getSelectedRow() >= 0) {
                    platoSeleccionadoFilaFoco = tableMenu.getSelectedRow();
                } else {
                    platoSeleccionadoFilaFoco = -1;
                }
                ultimoTextoIngresado = txtCantidadManual.getText();
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                aplicarCantidadManual(ultimoTextoIngresado, platoSeleccionadoFilaFoco);
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
                                            .addComponent(totalMenu, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
        jPanel25.add(btnFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(920, 440, 110, 40));

        totalFinalizar.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        totalFinalizar.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        totalFinalizar.setText("00.00");
        jPanel25.add(totalFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(910, 390, 120, -1));

        jLabel17.setFont(new java.awt.Font("Segoe UI", 0, 18)); // NOI18N
        jLabel17.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/money.png"))); // NOI18N
        jLabel17.setText("Total a Pagar");
        jPanel25.add(jLabel17, new org.netbeans.lib.awtextra.AbsoluteConstraints(900, 340, -1, -1));

        tableFinalizar.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", "Plato", "Cant", "Precio", "SubTotal", "Comentario"
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

        jLabel7.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel7.setText("Fecha y Hora:");
        jPanel25.add(jLabel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 350, -1, -1));

        jLabel8.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel8.setText("Sala:");
        jPanel25.add(jLabel8, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 400, -1, -1));

        jLabel9.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel9.setText("N° Mesa:");
        jPanel25.add(jLabel9, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 450, -1, -1));

        txtFechaHora.setEditable(false);
        jPanel25.add(txtFechaHora, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 350, 240, 30));

        txtSalaFinalizar.setEditable(false);
        jPanel25.add(txtSalaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 400, 240, 30));

        txtNumMesaFinalizar.setEditable(false);
        jPanel25.add(txtNumMesaFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(180, 450, 240, 30));

        javax.swing.JLabel lblMesoneroFin = new javax.swing.JLabel("Mesonero:");
        lblMesoneroFin.setFont(new java.awt.Font("Times New Roman", 1, 18));
        jPanel25.add(lblMesoneroFin, new org.netbeans.lib.awtextra.AbsoluteConstraints(440, 350, -1, -1));

        txtMesoneroFinalizar = new javax.swing.JTextField();
        txtMesoneroFinalizar.setEditable(false);
        txtMesoneroFinalizar.setBackground(new java.awt.Color(255, 255, 255));
        jPanel25.add(txtMesoneroFinalizar, new org.netbeans.lib.awtextra.AbsoluteConstraints(540, 350, 220, 30));

        btnPdfPedido.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/pdf.png"))); // NOI18N
        btnPdfPedido.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPdfPedidoActionPerformed(evt);
            }
        });
        jPanel25.add(btnPdfPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(780, 440, 110, 40));
        
        btnModificarPedido.setText("Agregar Productos");
        btnModificarPedido.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Img/nuevo.png"))); // Reuse "nuevo.png" if exists or just leave text
        btnModificarPedido.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnModificarPedidoActionPerformed(evt);
            }
        });
        jPanel25.add(btnModificarPedido, new org.netbeans.lib.awtextra.AbsoluteConstraints(580, 440, 180, 40));
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

        jLabel16.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel16.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel16.setText("Historial Pedidos");
        jPanel6.add(jLabel16, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 50, 280, -1));

        jTabbedPane1.addTab("Historial Pedidos", jPanel6);

        jPanel7.setBackground(new java.awt.Color(255, 255, 255));
        jPanel7.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel32.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
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

        jLabel30.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel30.setText("Dirección");
        jPanel8.add(jLabel30, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 160, -1, -1));

        jLabel29.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel29.setText("Teléfono");
        jPanel8.add(jLabel29, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 160, -1, -1));

        txtTelefonoConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtTelefonoConfig.setBorder(null);
        jPanel8.add(txtTelefonoConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 190, 218, 30));

        txtDireccionConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtDireccionConfig.setBorder(null);
        jPanel8.add(txtDireccionConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 190, 147, 30));

        jLabelTasaConfig.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabelTasaConfig.setText("Tasa de Cambio (Bs. / $)");
        jPanel8.add(jLabelTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 230, -1, -1));

        txtTasaConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtTasaConfig.setBorder(null);
        jPanel8.add(txtTasaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 255, 147, 30));

        jLabel31.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel31.setText("Mensaje");
        jPanel8.add(jLabel31, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 230, -1, -1));

        txtMensaje.setBackground(new java.awt.Color(204, 204, 204));
        txtMensaje.setBorder(null);
        jPanel8.add(txtMensaje, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 255, 220, 30));

        jLabelIvaConfig.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabelIvaConfig.setText("IVA (%)");
        jPanel8.add(jLabelIvaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 295, -1, -1));

        txtIvaConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtIvaConfig.setBorder(null);
        jPanel8.add(txtIvaConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 318, 147, 30));

        btnActualizarConfig.setBackground(new java.awt.Color(255, 255, 255));
        btnActualizarConfig.setFont(new java.awt.Font("Times New Roman", 1, 13)); // NOI18N
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

        jLabel27.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel27.setText("Ruc");
        jPanel8.add(jLabel27, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, -1, -1));

        txtRucConfig.setBackground(new java.awt.Color(204, 204, 204));
        txtRucConfig.setBorder(null);
        jPanel8.add(txtRucConfig, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 60, 147, 30));

        jLabel28.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
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

        jPanel8.add(jPanel43, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 350, 400, 2));

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

        jPanel12.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        TableUsuarios.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Id", "Nombre", "Correo", "Rol"
            }
        ));
        TableUsuarios.setRowHeight(23);
        jScrollPane6.setViewportView(TableUsuarios);
        if (TableUsuarios.getColumnModel().getColumnCount() > 0) {
            TableUsuarios.getColumnModel().getColumn(0).setMinWidth(50);
            TableUsuarios.getColumnModel().getColumn(0).setPreferredWidth(50);
            TableUsuarios.getColumnModel().getColumn(0).setMaxWidth(80);
            TableUsuarios.getColumnModel().getColumn(3).setMinWidth(150);
            TableUsuarios.getColumnModel().getColumn(3).setPreferredWidth(150);
            TableUsuarios.getColumnModel().getColumn(3).setMaxWidth(200);
        }

        jPanel12.add(jScrollPane6, new org.netbeans.lib.awtextra.AbsoluteConstraints(410, 40, 660, 520));

        jPanel15.setBackground(new java.awt.Color(204, 204, 204));
        jPanel15.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel34.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel34.setText("Correo Electrónico");
        jPanel15.add(jLabel34, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 102, -1, -1));

        jLabel35.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
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
        jPanel15.add(txtPass, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 200, 300, 30));

        btnIniciar.setBackground(new java.awt.Color(0, 0, 0));
        btnIniciar.setFont(new java.awt.Font("Times New Roman", 1, 13)); // NOI18N
        btnIniciar.setForeground(new java.awt.Color(255, 255, 255));
        btnIniciar.setText("Registrar");
        btnIniciar.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        btnIniciar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIniciarActionPerformed(evt);
            }
        });
        jPanel15.add(btnIniciar, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 440, 300, 50));

        jLabel36.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
        jLabel36.setText("Nombre:");
        jPanel15.add(jLabel36, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 280, -1, -1));

        txtNombre.setBackground(new java.awt.Color(204, 204, 204));
        txtNombre.setBorder(null);
        jPanel15.add(txtNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 300, 300, 30));

        jLabel37.setFont(new java.awt.Font("Times New Roman", 3, 14)); // NOI18N
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

        jLabel39.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        jLabel39.setForeground(new java.awt.Color(255, 255, 255));
        jLabel39.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel39.setText("Nuevo Usuario");
        jPanel21.add(jLabel39, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 310, 35));

        jPanel15.add(jPanel21, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 360, 35));

        jPanel12.add(jPanel15, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 40, 360, 520));

        jTabbedPane1.addTab("Usuarios", jPanel12);

        jPanel11.setBackground(new java.awt.Color(204, 204, 204));
        jPanel11.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel23.setFont(new java.awt.Font("Tahoma", 3, 12)); // NOI18N
        jLabel23.setText("Nombre:");
        jPanel11.add(jLabel23, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 110, -1, -1));

        txtNombrePlato.setBackground(new java.awt.Color(204, 204, 204));
        txtNombrePlato.setBorder(null);
        jPanel11.add(txtNombrePlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 100, 170, 30));

        jLabel25.setFont(new java.awt.Font("Tahoma", 3, 12)); // NOI18N
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
        jPanel11.add(btnEliminarPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 370, 110, 50));

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

        jLabel40.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
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

        getContentPane().add(jTabbedPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(200, 95, 1080, 620));

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
            conf.setRif(txtRucConfig.getText().trim());
            conf.setNombre(txtNombreConfig.getText().trim());
            conf.setTelefono(txtTelefonoConfig.getText().trim());
            conf.setDireccion(txtDireccionConfig.getText().trim());
            conf.setMensaje(txtMensaje.getText().trim());
            if (txtTasaConfig != null && !txtTasaConfig.getText().trim().isEmpty()) {
                try {
                    BigDecimal tasa = new BigDecimal(txtTasaConfig.getText().trim());
                    if (tasa.compareTo(BigDecimal.ZERO) <= 0 || tasa.scale() > 4 || tasa.precision() - tasa.scale() > 8) {
                        JOptionPane.showMessageDialog(this, "La tasa de cambio debe ser positiva y caber en DECIMAL(12,4).", "Tasa inválida", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    conf.setTasaDolar(tasa);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Ingresa una tasa de cambio numérica válida.", "Tasa inválida", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            if (txtIvaConfig != null && !txtIvaConfig.getText().trim().isEmpty()) {
                try {
                    BigDecimal iva = new BigDecimal(txtIvaConfig.getText().trim());
                    if (iva.compareTo(BigDecimal.ZERO) < 0 || iva.compareTo(new BigDecimal("100.00")) > 0 || iva.scale() > 2) {
                        JOptionPane.showMessageDialog(this, "El IVA debe estar entre 0.00 y 100.00% con hasta dos decimales.", "IVA inválido", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    conf.setIvaPorcentaje(iva);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Ingresa un porcentaje de IVA numérico válido.", "IVA inválido", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            if (txtRutaLogo != null) {
                String rutaLogo = txtRutaLogo.getText().trim();
                conf.setLogoPath(rutaLogo.isEmpty() ? null : rutaLogo);
            }
            if (chkImprimirLogoTicket != null) {
                conf.setImprimirLogoTicket(chkImprimirLogoTicket.isSelected());
            }
            if (txtClienteDefaultNombre != null && !txtClienteDefaultNombre.getText().trim().isEmpty()) {
                conf.setClienteDefaultNombre(txtClienteDefaultNombre.getText().trim());
            }
            if (txtClienteDefaultDoc != null && !txtClienteDefaultDoc.getText().trim().isEmpty()) {
                conf.setClienteDefaultDocumento(txtClienteDefaultDoc.getText().trim());
            }
            if (spMesesRetencion != null) {
                try {
                    int meses = (Integer) spMesesRetencion.getValue();
                    if (meses < 1) {
                        JOptionPane.showMessageDialog(this, "El período de retención debe ser de al menos 1 mes.", "Valor inválido", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    conf.setMesesRetencionPedidos(meses);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Meses de retención inválidos.", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            try {
                String idTxt = txtIdConfig.getText().trim();
                conf.setId(!idTxt.isEmpty() ? Integer.parseInt(idTxt) : (conf.getId() > 0 ? conf.getId() : 1));
            } catch (NumberFormatException ex) {
                conf.setId(conf.getId() > 0 ? conf.getId() : 1);
            }
            if (cbImpresorasConfig != null && cbImpresorasConfig.getSelectedItem() != null) {
                String impSeleccionada = cbImpresorasConfig.getSelectedItem().toString();
                conf.setImpresoraTickets(impSeleccionada);
                Servicio.ServicioImpresionTicket.setImpresoraGlobal(impSeleccionada);
            }
            if (cbModoSalidaConfig != null && cbModoSalidaConfig.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                conf.setModoSalidaTickets(m);
                cambiarModoSalidaGlobal(m);
            }
            try {
                if (lgDao.ModificarDatos(conf)) {
                    actualizarNombreRestaurante(conf.getNombre());
                    actualizarLogoEIcono(conf.getLogoPath());
                    actualizarEstadoTasaUI();
                    JOptionPane.showMessageDialog(this, "Datos de la empresa modificados.");
                } else {
                    JOptionPane.showMessageDialog(this, "No se aplicaron cambios a la configuración.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                String mensaje = (ex instanceof ErrorAplicacionException || ex instanceof DataAccessException)
                        ? ex.getMessage() : "No se pudo actualizar la configuración: " + ex.getMessage();
                JOptionPane.showMessageDialog(this, mensaje, "Error al guardar configuración", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(null, "Los campos estan vacios");
        }
    }//GEN-LAST:event_btnActualizarConfigActionPerformed

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
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
                if (!impreso) {
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnPdfPedidoActionPerformed

    private void btnModificarPedidoActionPerformed(java.awt.event.ActionEvent evt) {
        if (ped == null || ped.getId() <= 0) {
            JOptionPane.showMessageDialog(this, "No hay pedido seleccionado para modificar.");
            return;
        }
        idPedidoEdicion = ped.getId();
        txtTempIdSala.setText(String.valueOf(ped.getId_sala()));
        txtTempNumMesa.setText(String.valueOf(ped.getNum_mesa()));
        
        jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)),
                "MODIFICANDO PEDIDO N° " + ped.getId() + " - " + ped.getSala() + " | MESA " + ped.getNum_mesa(),
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14),
                new java.awt.Color(0, 102, 102)));
                
        btnGenerarPedido.setText("Actualizar Pedido");
        jTabbedPane1.setSelectedIndex(3);
                    txtBuscarPlato.requestFocusInWindow();
        LimpiarTableMenu();
        
        DefaultTableModel tmp = (DefaultTableModel) tableMenu.getModel();
        boolean tieneColumnaIva = tmp.getColumnCount() >= 7;
        
        java.util.List<Modelo.DetallePedido> detalles = pedidosControlador.verPedidoDetalle(ped.getId());
        
        for (Modelo.DetallePedido det : detalles) {
            String nombre = det.getNombre();
            int idPlato = 0;
            boolean aplicaIva = true;
            for (int k = 0; k < tblTemPlatos.getRowCount(); k++) {
                if (tblTemPlatos.getValueAt(k, 1).toString().equalsIgnoreCase(nombre)) {
                    idPlato = Integer.parseInt(tblTemPlatos.getValueAt(k, 0).toString());
                    aplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                    break;
                }
            }
            Object[] O = new Object[tieneColumnaIva ? 7 : 6];
            O[0] = idPlato > 0 ? idPlato : ""; 
            O[1] = nombre;
            O[2] = det.getCantidad();
            java.math.BigDecimal precio = det.getPrecioDecimal().setScale(2, java.math.RoundingMode.HALF_UP);
            String precioStr = String.format(java.util.Locale.US, "%.2f", precio);
            O[3] = precioStr;
            java.math.BigDecimal subtotal = precio.multiply(new java.math.BigDecimal(det.getCantidad())).setScale(2, java.math.RoundingMode.HALF_UP);
            O[4] = String.format(java.util.Locale.US, "%.2f", subtotal);
            O[5] = det.getComentario();
            if (tieneColumnaIva) {
                O[6] = aplicaIva ? "Sí (16%)" : "Exento (0%)";
            }
            tmp.addRow(O);
        }
        TotalPagar(tableMenu, totalMenu);
        actualizarEstadoBotonesCarrito();
        
        if (ped.getIdMesonero() != null && ped.getIdMesonero() > 0) {
            for (int i = 0; i < cbMesoneroPedido.getItemCount(); i++) {
                Object mObj = cbMesoneroPedido.getItemAt(i);
                if (mObj instanceof Modelo.Mesonero m) {
                    if (m.getId() == ped.getIdMesonero()) {
                        cbMesoneroPedido.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    private void btnPrevisualizarPedidoActionPerformed(java.awt.event.ActionEvent evt) {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;

        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            idStr = txtIdPedido.getText().trim();
        }
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un pedido para previsualizar.",
                    "Pedido requerido", JOptionPane.WARNING_MESSAGE);
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
        int id_pedido = Integer.parseInt(TablePedidos.getValueAt(fila, 0).toString());
        cargarPedidoEnPantalla(id_pedido, false);
    }//GEN-LAST:event_TablePedidosMouseClicked

    private void tableSalaMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tableSalaMouseClicked
        int fila = tableSala.rowAtPoint(evt.getPoint());
        if (fila < 0) {
            return;
        }
        txtIdSala.setText(tableSala.getValueAt(fila, 0).toString());
        txtNombreSala.setText(tableSala.getValueAt(fila, 1).toString());
        txtMesas.setText(tableSala.getValueAt(fila, 2).toString());
        try {
            int idSala = Integer.parseInt(tableSala.getValueAt(fila, 0).toString());
            String tipo = salaTipoMap.getOrDefault(idSala, "SALON");
            if (tableSala.getColumnCount() > 3 && tableSala.getValueAt(fila, 3) != null) {
                tipo = tableSala.getValueAt(fila, 3).toString();
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
        // TODO add your handling code here:
        if (txtNombreSala.getText().trim().isEmpty() || txtMesas.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Los campos esta vacios");
        } else {
            try {
                sl.setNombre(txtNombreSala.getText().trim());
                sl.setMesas(Integer.parseInt(txtMesas.getText().trim()));
                if (cbTipoSala != null) {
                    sl.setTipo(String.valueOf(cbTipoSala.getSelectedItem()));
                }
                if (salasControlador.registrar(sl)) {
                    JOptionPane.showMessageDialog(this, "Sala registrada.");
                    LimpiarSala();
                    ListarSalas();
                } else {
                    JOptionPane.showMessageDialog(this, "No se registró la sala.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "El número de mesas debe ser un número entero válido.", "Formato inválido", JOptionPane.WARNING_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnRegistrarSalaActionPerformed

    private void btnActualizarSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnActualizarSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        // TODO add your handling code here:
        if ("".equals(txtIdSala.getText())) {
            JOptionPane.showMessageDialog(null, "Seleecione una fila");
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
                    if (salasControlador.modificar(sl)) {
                        JOptionPane.showMessageDialog(this, "Sala modificada.");
                        LimpiarSala();
                        ListarSalas();
                    } else {
                        JOptionPane.showMessageDialog(this, "No se aplicaron cambios a la sala.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Los identificadores y número de mesas deben ser números enteros válidos.", "Formato inválido", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "El nombre de la sala es obligatorio.",
                        "Dato requerido", JOptionPane.WARNING_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnActualizarSalaActionPerformed

    private void btnNuevoSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNuevoSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        // TODO add your handling code here:
        LimpiarSala();
    }//GEN-LAST:event_btnNuevoSalaActionPerformed

    private void btnEliminarSalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarSalaActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_SALAS)) return;
        // TODO add your handling code here:
        if (!"".equals(txtIdSala.getText())) {
            int pregunta = JOptionPane.showConfirmDialog(null, "Esta seguro de eliminar");
            if (pregunta == 0) {
                int id = Integer.parseInt(txtIdSala.getText());
                if (salasControlador.eliminar(id)) {
                    LimpiarSala();
                    ListarSalas();
                } else {
                    JOptionPane.showMessageDialog(this, "No se eliminó la sala.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(null, "Seleccione una fila");
        }
    }//GEN-LAST:event_btnEliminarSalaActionPerformed

    private void txtCorreoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtCorreoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtCorreoActionPerformed

    private void btnIniciarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIniciarActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_USUARIOS)) return;
        if (txtNombre.getText().trim().isEmpty() || txtCorreo.getText().trim().isEmpty() || txtPass.getPassword().length == 0) {
            JOptionPane.showMessageDialog(null, "Todo los campos son requeridos");
        } else {
            Usuario lg = new Usuario();
            String correo = txtCorreo.getText();
            String pass = String.valueOf(txtPass.getPassword());
            String nom = txtNombre.getText();
            String rol = cbxRol.getSelectedItem().toString();
            lg.setNombre(nom);
            lg.setCorreo(correo);
            lg.setPassword(pass);
            lg.setRol(rol);
            if (lgDao.Registrar(lg)) {
                JOptionPane.showMessageDialog(this, "Usuario registrado.");
                ListarUsuarios();
            } else {
                JOptionPane.showMessageDialog(this, "No se registró el usuario.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnIniciarActionPerformed

    private void labelLogoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_labelLogoMouseClicked
        LimpiarTableMenu();
        jTabbedPane1.setSelectedIndex(0);
        panelSalas();
    }//GEN-LAST:event_labelLogoMouseClicked

    private void txtBuscarPlatoKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtBuscarPlatoKeyReleased
        ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
        if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
            if (tblTemPlatos.getRowCount() > 0) {
                tblTemPlatos.setRowSelectionInterval(0, 0);
                btnAddPlato.doClick();
                // Animación visual rápida de feedback (Verde) en la tabla
                tblTemPlatos.setSelectionBackground(new java.awt.Color(144, 238, 144));
                new javax.swing.Timer(300, e -> tblTemPlatos.setSelectionBackground(javax.swing.UIManager.getColor("Table.selectionBackground"))).start();
            }
        }
    }//GEN-LAST:event_txtBuscarPlatoKeyReleased

    private void btnAddPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddPlatoActionPerformed
        if (tblTemPlatos.getSelectedRow() >= 0) {
            int id = Integer.parseInt(tblTemPlatos.getValueAt(tblTemPlatos.getSelectedRow(), 0).toString());
            String descripcion = tblTemPlatos.getValueAt(tblTemPlatos.getSelectedRow(), 1).toString();
            BigDecimal precio = importeMonetario(tblTemPlatos.getValueAt(tblTemPlatos.getSelectedRow(), 2));
            BigDecimal total = precio;
            item = item + 1;
            tmp = (DefaultTableModel) tableMenu.getModel();
            for (int i = 0; i < tableMenu.getRowCount(); i++) {
                String idFila = tableMenu.getValueAt(i, 0).toString();
                String nombreFila = tableMenu.getValueAt(i, 1).toString();
                boolean coincide = false;
                if (!idFila.isBlank() && idFila.matches("\\d+")) {
                    coincide = Integer.parseInt(idFila) == id;
                }
                if (!coincide && nombreFila.equalsIgnoreCase(descripcion)) {
                    coincide = true;
                }
                if (coincide) {
                    int cantActual = Integer.parseInt(tableMenu.getValueAt(i, 2).toString());
                    setCantidadPlatoPorFila(i, cantActual + 1);
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
            Object[] O = new Object[tieneColumnaIva ? 7 : 6];
            O[0] = lista.get(1);
            O[1] = lista.get(2);
            O[2] = lista.get(3);
            O[3] = lista.get(4);
            O[4] = lista.get(5);
            O[5] = "";
            if (tieneColumnaIva) {
                O[6] = aplicaIva ? "Sí (16%)" : "Exento (0%)";
            }
            tmp.addRow(O);
            tableMenu.setModel(tmp);
            TotalPagar(tableMenu, totalMenu);
            actualizarEstadoBotonesCarrito();
        } else {
            JOptionPane.showMessageDialog(null, "SELECCIONA UNA FILA");
        }
    }//GEN-LAST:event_btnAddPlatoActionPerformed

    private void btnGenerarPedidoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGenerarPedidoActionPerformed
        if (tableMenu.getRowCount() > 0) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    idPedidoEdicion > 0 ? "¿Está seguro de actualizar este pedido?" : "¿Está seguro de realizar este pedido?",
                    idPedidoEdicion > 0 ? "Actualizar Pedido" : "Confirmar Pedido",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                int idPedido;
                if (idPedidoEdicion > 0) {
                    actualizarPedidoEnEdicion();
                    idPedido = idPedidoEdicion;
                    JOptionPane.showMessageDialog(this, "PEDIDO N° " + idPedido + " ACTUALIZADO");
                    idPedidoEdicion = -1;
                    btnGenerarPedido.setText("Realizar Pedido");
                } else {
                    idPedido = registrarPedidoCompleto();
                    JOptionPane.showMessageDialog(this, "PEDIDO N° " + idPedido + " REGISTRADO");
                }
                LimpiarTableMenu();
                jTabbedPane1.setSelectedIndex(0);
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
            JOptionPane.showMessageDialog(null, "NO HAY PRODUCTO EN LA PEDIDO");
        }
    }//GEN-LAST:event_btnGenerarPedidoActionPerformed

    private void btnEliminarTempPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarTempPlatoActionPerformed
        if (tableMenu.getSelectedRow() < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un producto del pedido.",
                    "Selección requerida", JOptionPane.WARNING_MESSAGE);
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
            if (nuevaCant <= 0) nuevaCant = 1; // Minimum 1
            setCantidadPlatoPorFila(fila, nuevaCant);
        } catch (NumberFormatException e) {
            // Revert to old value
            actualizarEstadoBotonesCarrito();
        }
    }

    private void setCantidadPlatoPorFila(int fila, int nuevaCant) {
        javax.swing.table.DefaultTableModel tmp = (javax.swing.table.DefaultTableModel) tableMenu.getModel();
        if (fila >= 0 && fila < tmp.getRowCount()) {
            java.math.BigDecimal precio = importeMonetario(tmp.getValueAt(fila, 3));
            tmp.setValueAt(nuevaCant, fila, 2);
            tmp.setValueAt(String.format(java.util.Locale.US, "%.2f", precio.multiply(new java.math.BigDecimal(nuevaCant))), fila, 4);
            TotalPagar(tableMenu, totalMenu);
            tableMenu.setRowSelectionInterval(fila, fila);
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
            JOptionPane.showMessageDialog(this, "Selecciona un producto del pedido.",
                    "Selección requerida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            int cantActual = Integer.parseInt(tableMenu.getValueAt(fila, 2).toString().trim());
            if (cantActual < 0) {
                cantActual = 0;
            }
            if (cantActual >= 999) {
                JOptionPane.showMessageDialog(this, "La cantidad máxima por plato es 999.",
                        "Límite alcanzado", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int nuevaCantidad = Math.addExact(cantActual, 1);
            BigDecimal precioUnitario = importeMonetario(tableMenu.getValueAt(fila, 3));
            BigDecimal nuevoSub = precioUnitario.multiply(BigDecimal.valueOf(nuevaCantidad))
                    .setScale(2, RoundingMode.HALF_UP);

            tmp = (DefaultTableModel) tableMenu.getModel();
            tmp.setValueAt(nuevaCantidad, fila, 2);
            tmp.setValueAt(String.format(java.util.Locale.US, "%.2f", nuevoSub), fila, 4);
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
            JOptionPane.showMessageDialog(this, "Selecciona un producto del pedido.",
                    "Selección requerida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            int cantActual = Integer.parseInt(tableMenu.getValueAt(fila, 2).toString().trim());
            tmp = (DefaultTableModel) tableMenu.getModel();
            if (cantActual > 1) {
                int nuevaCantidad = cantActual - 1;
                BigDecimal precioUnitario = importeMonetario(tableMenu.getValueAt(fila, 3));
                BigDecimal nuevoSub = precioUnitario.multiply(BigDecimal.valueOf(nuevaCantidad))
                        .setScale(2, RoundingMode.HALF_UP);
                tmp.setValueAt(nuevaCantidad, fila, 2);
                tmp.setValueAt(String.format(java.util.Locale.US, "%.2f", nuevoSub), fila, 4);
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
                isUpdatingUI = true;
                Object val = tableMenu.getValueAt(tableMenu.getSelectedRow(), 2);
                txtCantidadManual.setText(val != null ? val.toString() : "1");
                isUpdatingUI = false;
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
            JOptionPane.showMessageDialog(null, "SELECCIONE UNA FILA");
        } else {
            int id = Integer.parseInt(tableMenu.getValueAt(tableMenu.getSelectedRow(), 0).toString());
            for (int i = 0; i < tableMenu.getRowCount(); i++) {
                if (tableMenu.getValueAt(i, 0).equals(id)) {
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
            JOptionPane.showMessageDialog(this, "Selecciona un pedido antes de finalizarlo.",
                    "Pedido requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Config cfg = conf != null ? conf : lgDao.datosEmpresa();
        String defaultNombre = (cfg != null && cfg.getClienteDefaultNombre() != null)
                ? cfg.getClienteDefaultNombre() : "Consumidor Final";
        String defaultDoc = (cfg != null && cfg.getClienteDefaultDocumento() != null)
                ? cfg.getClienteDefaultDocumento() : "V-00000000";

        javax.swing.JTextField txtNombreCli = new javax.swing.JTextField(defaultNombre, 20);
        javax.swing.JTextField txtDocCli = new javax.swing.JTextField(defaultDoc, 15);

        javax.swing.JComboBox<String> cbMetodoPago = new javax.swing.JComboBox<>(
                new String[]{"EFECTIVO_BS", "EFECTIVO_USD", "EFECTIVO", "TRANSFERENCIA", "TARJETA", "PAGO_MOVIL", "MIXTO"});
        javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbDestinoModal = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbDestinoModal.setSelectedItem(Servicio.ServicioImpresionTicket.getModoGlobal());

        javax.swing.JPanel panelFacturar = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panelFacturar.add(new javax.swing.JLabel("¿Está seguro de finalizar el pedido y generar la factura?"));
        panelFacturar.add(new javax.swing.JLabel("Nombre del Cliente:"));
        panelFacturar.add(txtNombreCli);
        panelFacturar.add(new javax.swing.JLabel("Cédula / RIF del Cliente:"));
        panelFacturar.add(txtDocCli);
        panelFacturar.add(new javax.swing.JLabel("Método de Pago:"));
        panelFacturar.add(cbMetodoPago);
        panelFacturar.add(new javax.swing.JLabel("Destino de Impresión / Tickera:"));
        panelFacturar.add(cbDestinoModal);

        if (configServicio != null && configServicio.esTasaDolarDesactualizada()) {
            javax.swing.JLabel lblAlertaTasaModal = new javax.swing.JLabel("<html><b style='color:#c82333;'>⚠️ ATENCIÓN:</b> La tasa de cambio no ha sido actualizada hoy.</html>");
            lblAlertaTasaModal.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 11));
            panelFacturar.add(lblAlertaTasaModal);
        }

        int pregunta = JOptionPane.showConfirmDialog(this,
                panelFacturar,
                "Confirmar Finalización y Facturación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (pregunta == JOptionPane.YES_OPTION) {
            if (configServicio != null && configServicio.esTasaDolarDesactualizada()) {
                Object[] opcionesTasa = new Object[]{"Ir a Configuración y Actualizar", "Continuar con Tasa Registrada"};
                int decisionTasa = JOptionPane.showOptionDialog(this,
                        "⚠️ La tasa de cambio del dólar no ha sido actualizada en la fecha de hoy.\n"
                        + "Tasa registrada actual: Bs. " + (conf != null && conf.getTasaDolar() != null ? conf.getTasaDolar() : "36.50") + "\n\n"
                        + "¿Desea actualizar la tasa primero (Recomendado) o continuar facturando con la tasa registrada?",
                        "Advertencia de Tasa Desactualizada",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        opcionesTasa,
                        opcionesTasa[0]);
                if (decisionTasa == 0) {
                    jTabbedPane1.setSelectedIndex(5);
                    if (txtTasaConfig != null) {
                        txtTasaConfig.requestFocus();
                        txtTasaConfig.selectAll();
                    }
                    return;
                } else if (decisionTasa != 1) {
                    return;
                }
            }
            if (cbDestinoModal.getSelectedItem() instanceof Modelo.ModoSalidaTicket m) {
                cambiarModoSalidaGlobal(m);
            }
            String clienteNombre = txtNombreCli.getText().trim().isEmpty() ? defaultNombre : txtNombreCli.getText().trim();
            String clienteDoc = txtDocCli.getText().trim().isEmpty() ? defaultDoc : txtDocCli.getText().trim();
            String metodoPagoSel = (String) cbMetodoPago.getSelectedItem();
            if (metodoPagoSel == null || metodoPagoSel.isBlank()) metodoPagoSel = "EFECTIVO";

            java.math.BigDecimal efectivoPagoBs = null;
            java.math.BigDecimal efectivoPagoUsd = null;
            if ("MIXTO".equalsIgnoreCase(metodoPagoSel)) {
                java.math.BigDecimal[] desglose = solicitarEfectivoPagoMixto();
                if (desglose == null) return;
                efectivoPagoBs = desglose[0];
                efectivoPagoUsd = desglose[1];
            }



            if (metodoPagoSel.contains("EFECTIVO") && !"MIXTO".equalsIgnoreCase(metodoPagoSel)) {
                try {
                    double totalFactura = 0;
                    String simbolo = "$";
                    if (ped != null) {
                        if ("EFECTIVO_BS".equals(metodoPagoSel) && ped.getTotalBs() != null) {
                            totalFactura = ped.getTotalBs().doubleValue();
                            simbolo = "Bs.";
                        } else if (ped.getTotalDecimal() != null) {
                            totalFactura = ped.getTotalDecimal().doubleValue();
                        }
                    }
                    if (totalFactura > 0) {
                        calcularVueltoUx(metodoPagoSel, totalFactura, simbolo);
                    }
                } catch(Exception ignored) {}
            }
            int idPedido = Integer.parseInt(txtIdPedido.getText());
            long version = versionPedidoPantalla;
            btnFinalizar.setEnabled(false);
            new FinalizarPedidoSwingWorker(pedidosControlador, idPedido, clienteNombre, clienteDoc, metodoPagoSel,
                    efectivoPagoBs, efectivoPagoUsd, resultado -> {
                boolean finalizado = resultado.finalizado();
                boolean impresoDirecto = resultado.impresoDirecto();
                boolean mismoPedido = version == versionPedidoPantalla
                        && String.valueOf(idPedido).equals(txtIdPedido.getText());
                if (finalizado) {
                    if (mismoPedido) {
                        btnFinalizar.setEnabled(false);
                        btnPdfPedido.setEnabled(true);
                        if (btnPrevisualizarPedido != null) btnPrevisualizarPedido.setEnabled(true);
                    }
                    ListarPedidos();
                    if (impresoDirecto) {
                        JOptionPane.showMessageDialog(this, "Pedido finalizado y PDF generado a nombre de " + clienteNombre + ".",
                                "Pedido finalizado", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Pedido finalizado a nombre de " + clienteNombre + ".\\nSin embargo, falló el envío a la impresora (degradado a visor PDF).",
                                "Pedido finalizado - Impresión degradada", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    if (mismoPedido) btnFinalizar.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "El pedido no se finalizó.",
                            "Sin cambios", JOptionPane.WARNING_MESSAGE);
                }
            }, (error, finalizacionConfirmada) -> {
                if (finalizacionConfirmada) ListarPedidos();
                boolean mismoPedido = version == versionPedidoPantalla
                        && String.valueOf(idPedido).equals(txtIdPedido.getText());
                if (finalizacionConfirmada) {
                    if (mismoPedido) {
                        btnFinalizar.setEnabled(false);
                        btnPdfPedido.setEnabled(true);
                        if (btnPrevisualizarPedido != null) btnPrevisualizarPedido.setEnabled(true);
                    }
                    JOptionPane.showMessageDialog(this,
                            "El pedido quedó finalizado, pero no se pudo completar el PDF: " + mensajeError(error),
                            "Pedido finalizado", JOptionPane.WARNING_MESSAGE);
                } else {
                    if (mismoPedido) btnFinalizar.setEnabled(true);
                    JOptionPane.showMessageDialog(this,
                            "No se pudo finalizar el pedido: " + mensajeError(error),
                            "Error al finalizar", JOptionPane.ERROR_MESSAGE);
                }
            }).execute();
        }
    }//GEN-LAST:event_btnFinalizarActionPerformed

    
    
    
    private void calcularVueltoUx(String metodo, double totalFactura, String simbolo) {
        if (!metodo.contains("EFECTIVO") || totalFactura <= 0) return;
        
        javax.swing.JPanel pnl = new javax.swing.JPanel(new java.awt.BorderLayout(8, 8));
        javax.swing.JLabel lblInfo = new javax.swing.JLabel("Total a pagar: " + simbolo + " " + String.format(java.util.Locale.US, "%.2f", totalFactura));
        lblInfo.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
        pnl.add(lblInfo, java.awt.BorderLayout.NORTH);
        
        javax.swing.JTextField txtRecibido = new javax.swing.JTextField(10);
        txtRecibido.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        javax.swing.JLabel lblVuelto = new javax.swing.JLabel("Vuelto: " + simbolo + " 0.00");
        lblVuelto.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
        lblVuelto.setForeground(java.awt.Color.BLUE);
        
        javax.swing.JPanel pnlCentro = new javax.swing.JPanel(new java.awt.GridLayout(2, 2, 4, 4));
        pnlCentro.add(new javax.swing.JLabel("Monto Entregado:"));
        pnlCentro.add(txtRecibido);
        pnlCentro.add(new javax.swing.JLabel(""));
        pnlCentro.add(lblVuelto);
        pnl.add(pnlCentro, java.awt.BorderLayout.CENTER);
        
        javax.swing.JPanel pnlBotones = new javax.swing.JPanel(new java.awt.FlowLayout());
        double[] billetes = {10, 20, 50, 100};
        for (double b : billetes) {
            if (b >= totalFactura || b == 100) {
                javax.swing.JButton btnB = new javax.swing.JButton(simbolo + " " + (int)b);
                btnB.addActionListener(e -> {
                    txtRecibido.setText(String.valueOf(b));
                    double vuelto = b - totalFactura;
                    lblVuelto.setText("Vuelto: " + simbolo + " " + String.format(java.util.Locale.US, "%.2f", vuelto));
                });
                pnlBotones.add(btnB);
            }
        }
        javax.swing.JButton btnExacto = new javax.swing.JButton("Exacto");
        btnExacto.addActionListener(e -> {
            txtRecibido.setText(String.format(java.util.Locale.US, "%.2f", totalFactura));
            lblVuelto.setText("Vuelto: " + simbolo + " 0.00");
        });
        pnlBotones.add(btnExacto);
        pnl.add(pnlBotones, java.awt.BorderLayout.SOUTH);
        
        txtRecibido.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                try {
                    double rec = Double.parseDouble(txtRecibido.getText().replace(",", "."));
                    double vuelto = rec - totalFactura;
                    lblVuelto.setText("Vuelto: " + simbolo + " " + String.format(java.util.Locale.US, "%.2f", vuelto));
                } catch(Exception ignored){}
            }
        });
        
        // Auto-select text on focus
        txtRecibido.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                txtRecibido.setBackground(new java.awt.Color(230, 245, 255));
                txtRecibido.selectAll();
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtRecibido.setBackground(java.awt.Color.WHITE);
            }
        });
        
        // Poner foco después de renderizar
        javax.swing.SwingUtilities.invokeLater(txtRecibido::requestFocusInWindow);
        JOptionPane.showMessageDialog(this, pnl, "Calculadora Rápida de Vuelto", JOptionPane.PLAIN_MESSAGE);
    }

    private java.math.BigDecimal[] solicitarEfectivoPagoMixto() {
        javax.swing.JTextField txtBs = new javax.swing.JTextField("0.00", 12);
        javax.swing.JTextField txtUsd = new javax.swing.JTextField("0.00", 12);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panel.add(new javax.swing.JLabel("Efectivo recibido en Bs. (0 si no aplica):"));
        panel.add(txtBs);
        panel.add(new javax.swing.JLabel("Efectivo recibido en USD (0 si no aplica):"));
        panel.add(txtUsd);
        int opcion = JOptionPane.showConfirmDialog(this, panel,
                "Desglose del pago mixto", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) return null;
        try {
            java.math.BigDecimal bs = new java.math.BigDecimal(txtBs.getText().trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
            java.math.BigDecimal usd = new java.math.BigDecimal(txtUsd.getText().trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (bs.signum() < 0 || usd.signum() < 0) throw new NumberFormatException("El monto no puede ser negativo.");
            return new java.math.BigDecimal[]{bs, usd};
        } catch (NumberFormatException | ArithmeticException ex) {
            JOptionPane.showMessageDialog(this,
                    "Indica montos no negativos con hasta dos decimales.",
                    "Desglose inválido", JOptionPane.ERROR_MESSAGE);
            return null;
        }
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
        // TODO add your handling code here:
        if (!txtNombrePlato.getText().trim().isEmpty() && !txtPrecioPlato.getText().trim().isEmpty()) {
            pla.setNombre(txtNombrePlato.getText());
            pla.setPrecioDecimal(importeMonetario(txtPrecioPlato.getText()));
            pla.setFecha(fechaActual());
            pla.setAplicaIva(chkAplicaIvaPlato == null || chkAplicaIvaPlato.isSelected());
            if (platosControlador.registrar(pla)) {
                JOptionPane.showMessageDialog(null, "Plato Registrado");
                ListarPlatos(TablePlatos, "");
                LimpiarPlatos();
            } else {
                JOptionPane.showMessageDialog(this, "No se registró el plato.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
            }

        } else {
            JOptionPane.showMessageDialog(null, "Los campos estan vacios");
        }
    }//GEN-LAST:event_btnGuardarPlatoActionPerformed

    private void btnEditarPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditarPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        // TODO add your handling code here:
        if ("".equals(txtIdPlato.getText())) {
            JOptionPane.showMessageDialog(null, "Seleecione una fila");
        } else {
            if (!txtNombrePlato.getText().trim().isEmpty() && !txtPrecioPlato.getText().trim().isEmpty()) {
                try {
                    pla.setNombre(txtNombrePlato.getText().trim());
                    pla.setPrecioDecimal(importeMonetario(txtPrecioPlato.getText().trim()));
                    pla.setId(Integer.parseInt(txtIdPlato.getText().trim()));
                    pla.setAplicaIva(chkAplicaIvaPlato == null || chkAplicaIvaPlato.isSelected());
                    if (platosControlador.modificar(pla)) {
                        JOptionPane.showMessageDialog(null, "Plato Modificado");
                        ListarPlatos(TablePlatos, "");
                        LimpiarPlatos();
                    } else {
                        JOptionPane.showMessageDialog(this, "No se aplicaron cambios al plato.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Identificador de plato inválido.", "Error", JOptionPane.WARNING_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "El nombre y el precio del plato son obligatorios.",
                        "Datos requeridos", JOptionPane.WARNING_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnEditarPlatoActionPerformed

    private void btnEliminarPlatoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEliminarPlatoActionPerformed
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) return;
        if (!"".equals(txtIdPlato.getText())) {
            int pregunta = JOptionPane.showConfirmDialog(this,
                    "¿Está seguro de desactivar este plato? Dejará de mostrarse en el menú.",
                    "Confirmar Desactivación", JOptionPane.YES_NO_OPTION);
            if (pregunta == JOptionPane.YES_OPTION) {
                try {
                    int id = Integer.parseInt(txtIdPlato.getText().trim());
                    if (platosControlador.desactivar(id)) {
                        JOptionPane.showMessageDialog(this, "Plato desactivado con éxito.", "Platos", JOptionPane.INFORMATION_MESSAGE);
                        LimpiarPlatos();
                        ListarPlatos(TablePlatos, "");
                    } else {
                        JOptionPane.showMessageDialog(this, "No se desactivó el plato.", "Sin cambios", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Identificador de plato inválido.", "Error", JOptionPane.WARNING_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecciona una fila", "Aviso", JOptionPane.WARNING_MESSAGE);
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
        txtIdPlato.setText(TablePlatos.getValueAt(fila, 0).toString());
        txtNombrePlato.setText(TablePlatos.getValueAt(fila, 1).toString());
        txtPrecioPlato.setText(TablePlatos.getValueAt(fila, 2).toString());
        try {
            int idPlato = Integer.parseInt(TablePlatos.getValueAt(fila, 0).toString());
            boolean aplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
            if (TablePlatos.getColumnCount() > 3 && TablePlatos.getValueAt(fila, 3) != null) {
                String val = TablePlatos.getValueAt(fila, 3).toString().toLowerCase(java.util.Locale.ROOT);
                aplicaIva = !val.contains("exento") && !val.contains("no");
            }
            if (chkAplicaIvaPlato != null) {
                chkAplicaIvaPlato.setSelected(aplicaIva);
            }
        } catch (Exception ignored) {}
    }//GEN-LAST:event_TablePlatosMouseClicked


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
    private javax.swing.JButton btnNuevoPlato;
    private javax.swing.JButton btnNuevoSala;
    private javax.swing.JButton btnPdfPedido;
    private javax.swing.JButton btnCategorias;
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
        Totalpagar = BigDecimal.ZERO.setScale(2);
        BigDecimal baseImponible = BigDecimal.ZERO.setScale(2);
        BigDecimal exento = BigDecimal.ZERO.setScale(2);
        int numFila = tabla.getRowCount();
        for (int i = 0; i < numFila; i++) {
            BigDecimal subtotal = importeMonetario(tabla.getModel().getValueAt(i, 4));
            boolean itemAplicaIva = true;
            if (tabla.getModel().getColumnCount() > 6 && tabla.getModel().getValueAt(i, 6) != null) {
                String valIva = tabla.getModel().getValueAt(i, 6).toString().toLowerCase(java.util.Locale.ROOT);
                if (valIva.contains("exento") || valIva.contains("no") || valIva.equals("false") || valIva.equals("0")) {
                    itemAplicaIva = false;
                }
            } else {
                Object idObj = tabla.getModel().getValueAt(i, 0);
                if (idObj != null) {
                    try {
                        int idPlato = Integer.parseInt(idObj.toString());
                        itemAplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                    } catch (Exception ignored) {}
                }
            }
            if (itemAplicaIva) {
                baseImponible = baseImponible.add(subtotal);
            } else {
                exento = exento.add(subtotal);
            }
            Totalpagar = Totalpagar.add(subtotal);
        }
        Totalpagar = Totalpagar.setScale(2, RoundingMode.HALF_UP);
        baseImponible = baseImponible.setScale(2, RoundingMode.HALF_UP);
        exento = exento.setScale(2, RoundingMode.HALF_UP);
        String totalUsdStr = String.format(java.util.Locale.US, "%.2f", Totalpagar);
        label.setText("$ " + totalUsdStr);
        if (exento.signum() > 0) {
            label.setToolTipText("Consumo en mesa: $ " + totalUsdStr
                    + " (Base Imponible: $ " + String.format(java.util.Locale.US, "%.2f", baseImponible)
                    + ", Exento: $ " + String.format(java.util.Locale.US, "%.2f", exento)
                    + " - IVA y total en Bs. se calculan al facturar)");
        } else {
            label.setToolTipText("Consumo en mesa: $ " + totalUsdStr
                    + " (IVA y total en Bs. se calculan al facturar)");
        }
    }

    
    private void aplicarMejorasDeUX() {
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
                    evt.getComponent().setBackground(java.awt.Color.WHITE);
                }
            }
        };
        txtBuscarPlato.addFocusListener(highlighter);
        txtCantidadManual.addFocusListener(highlighter);
        
        // Global Shortcuts via KeyboardFocusManager
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F1) {
                    mostrarPanelAyuda();
                    return true;
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F2) {
                    jTabbedPane1.setSelectedIndex(3);
                    txtBuscarPlato.requestFocusInWindow(); // Ir a Tab Pedidos
                    txtBuscarPlato.requestFocus();
                    return true;
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F5) {
                    if (jTabbedPane1.getSelectedIndex() == 3) {
                        btnGenerarPedido.doClick(); // Click en Actualizar/Generar
                        return true;
                    }
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    if (jTabbedPane1.getSelectedIndex() == 3) {
                        // Opcional: Limpiar mesa o cancelar
                        LimpiarTableMenu();
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void mostrarPanelAyuda() {
        String ayuda = "<html><h3>Atajos de Teclado (Mejoras UX)</h3>"
            + "<ul>"
            + "<li><b>[F1]</b>: Mostrar este panel de ayuda.</li>"
            + "<li><b>[F2]</b>: Ir directo al buscador de platos de la mesa actual.</li>"
            + "<li><b>[F5]</b>: Finalizar o Actualizar Pedido (cuando estás en el carrito).</li>"
            + "<li><b>[ESC]</b>: Limpiar el carrito de la mesa actual.</li>"
            + "<li><b>[Enter] en Buscador</b>: Agrega automáticamente el primer plato de la lista.</li>"
            + "</ul><p>¡Usar estos atajos en horas pico agilizará tus pedidos significativamente!</p></html>";
        JOptionPane.showMessageDialog(this, ayuda, "Ayuda de Atajos", JOptionPane.INFORMATION_MESSAGE);
    }

    private void LimpiarTableMenu() {
        idPedidoEdicion = -1;
        if (btnGenerarPedido != null) btnGenerarPedido.setText("Realizar Pedido");
        tmp = (DefaultTableModel) tableMenu.getModel();
        tmp.setRowCount(0);
        item = 0;
        Totalpagar = BigDecimal.ZERO.setScale(2);
        if (totalMenu != null) {
            TotalPagar(tableMenu, totalMenu);
        }
        actualizarEstadoBotonesCarrito();
    }

    public void ListarConfig() {
        try {
            conf = lgDao.datosEmpresa();
        } catch (Exception ex) {
            conf = null;
        }
        if (conf == null) {
            conf = new Config();
            conf.setId(1);
            conf.setTasaDolar(new BigDecimal("36.5000"));
            conf.setIvaPorcentaje(new BigDecimal("16.00"));
        }
        txtIdConfig.setText("" + conf.getId());
        txtRucConfig.setText(conf.getRif() != null ? conf.getRif() : "");
        txtNombreConfig.setText(conf.getNombre() != null ? conf.getNombre() : "");
        txtTelefonoConfig.setText(conf.getTelefono() != null ? conf.getTelefono() : "");
        txtDireccionConfig.setText(conf.getDireccion() != null ? conf.getDireccion() : "");
        txtMensaje.setText(conf.getMensaje() != null ? conf.getMensaje() : "");
        if (txtTasaConfig != null) {
            txtTasaConfig.setText(conf.getTasaDolar() != null ? conf.getTasaDolar().toPlainString() : "36.5000");
        }
        if (txtIvaConfig != null) {
            txtIvaConfig.setText(conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje().toPlainString() : "16.00");
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
            refrescarImpresorasEnCombo(conf.getImpresoraTickets());
        }
        if (conf.getImpresoraTickets() != null) {
            Servicio.ServicioImpresionTicket.setImpresoraGlobal(conf.getImpresoraTickets());
        }
        if (conf.getModoSalidaTickets() != null) {
            cambiarModoSalidaGlobal(conf.getModoSalidaTickets());
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
                new Object[][]{}, new String[]{"Plato", "Cant.", "Total ($)"}
        ) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        });
        tableTopPlatos.setRowHeight(22);
        pnlTopPlatos.add(new javax.swing.JScrollPane(tableTopPlatos), java.awt.BorderLayout.CENTER);
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
        tabLeftDashboard.addTab("Mesoneros", pnlMesonerosDashboard);

        splitPrincipal.setDividerLocation(360);
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

        javax.swing.JSplitPane splitDerecha = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, pnlClientes, pnlFacturas);
        splitDerecha.setDividerLocation(180);
        splitDerecha.setOpaque(false);
        pnlClientesYFacturas.add(splitDerecha, java.awt.BorderLayout.CENTER);

        splitPrincipal.setRightComponent(pnlClientesYFacturas);
        panelDashboard.add(splitPrincipal, java.awt.BorderLayout.CENTER);

        // Añadir Pestaña a jTabbedPane1
        jTabbedPane1.addTab("Dashboard", panelDashboard);

        // Añadir botón en la barra lateral
        btnDashboard = new javax.swing.JButton("Dashboard");
        btnDashboard.setBackground(new java.awt.Color(0, 0, 0));
        btnDashboard.setForeground(new java.awt.Color(255, 255, 255));
        btnDashboard.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        btnDashboard.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnDashboard.setFocusable(false);
        java.net.URL urlDash = getClass().getResource("/Img/pedidos.png");
        if (urlDash != null) btnDashboard.setIcon(new javax.swing.ImageIcon(urlDash));
        btnDashboard.addActionListener(e -> {
            jTabbedPane1.setSelectedComponent(panelDashboard);
            cargarDashboardYClientes();
        });
        getContentPane().add(btnDashboard, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 625, 200, 45));
        getContentPane().setComponentZOrder(btnDashboard, 0);
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
                    modelPlatos.addRow(new Object[]{item.getNombre(), item.getCantidad(), "$ " + String.format(java.util.Locale.US, "%.2f", item.getTotalDolares())});
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
                                    p.getSala(),
                                    p.getNum_mesa(),
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
            JOptionPane.showMessageDialog(this, "Selecciona una factura de la tabla para ver/reimprimir.",
                    "Factura requerida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idPedido = Integer.parseInt(String.valueOf(tableFacturasCliente.getValueAt(fila, 0)));
        try {
            String motivo = solicitarMotivoAccion("Reimpresión de Factura de Cliente",
                    "Ingrese el motivo de la reimpresión del pedido #" + idPedido + ":", "Copia solicitada por cliente");
            if (motivo == null) return;
            String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
            boolean impreso = pedidosControlador.reimprimirPdfPedido(idPedido, motivo, usr);
                if (!impreso) {
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
                if (!impreso) {
                    JOptionPane.showMessageDialog(this, "El PDF fue generado pero falló el envío directo a la impresora (degradado a visor PDF).",
                            "Impresión Degradada", JOptionPane.WARNING_MESSAGE);
                }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo generar el PDF: " + mensajeError(ex),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void previsualizarFacturaClienteSeleccionada() {
        int fila = tableFacturasCliente.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona una factura de la tabla para previsualizar.",
                    "Factura requerida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idPedido = Integer.parseInt(String.valueOf(tableFacturasCliente.getValueAt(fila, 0)));
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
            ob[1] = Listar.get(i).getSala();
            ob[2] = Listar.get(i).getUsuario();
            ob[3] = Listar.get(i).getNum_mesa();
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
        if (cbImpresorasConfig == null) {
            return;
        }
        sincronizandoImpresora = true;
        try {
            cbImpresorasConfig.removeAllItems();
            cbImpresorasConfig.addItem("DEFAULT");
            java.util.List<String> lista = Servicio.ServicioImpresionTicket.listarImpresorasDisponibles();
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
                        JOptionPane.showMessageDialog(Sistema.this,
                                "Ticket de prueba generado y enviado correctamente.\n• Impresora: " + imp + "\n• Modo: " + modo.getEtiqueta(),
                                "Impresión de Prueba Exitosa", JOptionPane.INFORMATION_MESSAGE);
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
        javax.swing.JTextField txtEfectivoContadoBs = new javax.swing.JTextField("", 10);
        txtEfectivoContadoBs.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        panel.add(new javax.swing.JLabel("Efectivo Contado en Gaveta (Bs. - dejar vacío para omitir):"));
        panel.add(txtEfectivoContadoBs);
        javax.swing.JTextField txtEfectivoContadoUsd = new javax.swing.JTextField("", 10);
        txtEfectivoContadoUsd.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 13));
        panel.add(new javax.swing.JLabel("Efectivo Contado en Gaveta ($ USD - dejar vacío para omitir):"));
        panel.add(txtEfectivoContadoUsd);
        javax.swing.JComboBox<Modelo.ModoSalidaTicket> cbDestinoCierre = new javax.swing.JComboBox<>(Modelo.ModoSalidaTicket.values());
        cbDestinoCierre.setSelectedItem(Servicio.ServicioImpresionTicket.getModoGlobal());
        panel.add(new javax.swing.JLabel("Destino de Impresión:"));
        panel.add(cbDestinoCierre);

        Object[] opciones = new Object[]{"Imprimir Directo (80mm)", "Previsualizar", "Cancelar"};
        int seleccion = JOptionPane.showOptionDialog(
                this,
                panel,
                tipo.getEtiqueta(),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                opciones,
                opciones[0]
        );

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
                JOptionPane.showMessageDialog(this,
                        "La fecha ingresada no es válida. Debe tener formato AAAA-MM-DD (ej: " + fechaDefecto + ").",
                        "Fecha inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (fechaParsed.isAfter(java.time.LocalDate.now())) {
                JOptionPane.showMessageDialog(this,
                        "No es posible generar el cierre para una fecha futura (" + fecha + ").",
                        "Fecha inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }

            java.math.BigDecimal efBs = null;
            String efBsTxt = txtEfectivoContadoBs.getText().trim();
            if (!efBsTxt.isEmpty()) {
                try {
                    efBs = new java.math.BigDecimal(efBsTxt);
                    if (efBs.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        JOptionPane.showMessageDialog(this,
                                "El efectivo contado en Bs no puede ser negativo.",
                                "Monto inválido", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                            "El monto de efectivo en Bs no es un número válido: '" + efBsTxt + "'.\nIngrese un número positivo o deje la casilla en blanco para omitir arqueo.",
                            "Monto inválido", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            java.math.BigDecimal efUsd = null;
            String efUsdTxt = txtEfectivoContadoUsd.getText().trim();
            if (!efUsdTxt.isEmpty()) {
                try {
                    efUsd = new java.math.BigDecimal(efUsdTxt);
                    if (efUsd.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        JOptionPane.showMessageDialog(this,
                                "El efectivo contado en USD no puede ser negativo.",
                                "Monto inválido", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                            "El monto de efectivo en USD no es un número válido: '" + efUsdTxt + "'.\nIngrese un número positivo o deje la casilla en blanco para omitir arqueo.",
                            "Monto inválido", JOptionPane.ERROR_MESSAGE);
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
                            JOptionPane.showMessageDialog(Sistema.this,
                                    "Ticket de " + tipo.getEtiqueta() + " procesado (" + detalleDestino + ")\nArchivo: " + resultado.archivo().getFileName(),
                                    "Cierre de Caja", JOptionPane.INFORMATION_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "El período de retención debe ser de al menos 1 mes.",
                    "Valor inválido", JOptionPane.WARNING_MESSAGE);
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
        javax.swing.JPasswordField pwd = new javax.swing.JPasswordField();
        int opcionPwd = JOptionPane.showConfirmDialog(this,
                new Object[]{"Ingrese la contraseña de administrador para autorizar la purga:", pwd},
                "Autorización de Administrador Requerida",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcionPwd != JOptionPane.OK_OPTION) {
            return;
        }

        char[] claveChars = pwd.getPassword();
        try {
            if (claveChars.length == 0) {
                JOptionPane.showMessageDialog(this, "Debe ingresar una contraseña válida.",
                        "Contraseña requerida", JOptionPane.ERROR_MESSAGE);
                return;
            }

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
                    JOptionPane.showMessageDialog(Sistema.this,
                            "Purga completada exitosamente. Se eliminaron " + eliminados + " pedidos finalizados.",
                            "Purga Exitosa", JOptionPane.INFORMATION_MESSAGE);
                    if (jTabbedPane1.getSelectedIndex() == 5) {
                        ListarPedidos();
                    }
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
            JOptionPane.showMessageDialog(this,
                    "No hay platos desactivados en el sistema.",
                    "Reactivar Platos", JOptionPane.INFORMATION_MESSAGE);
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
            int id = (int) tablaInactivos.getValueAt(fila, 0);
            String nombrePlato = tablaInactivos.getValueAt(fila, 1).toString();
            int confirm = JOptionPane.showConfirmDialog(dialogo,
                    "¿Desea reactivar el plato \"" + nombrePlato + "\" para que aparezca nuevamente en el menú?",
                    "Confirmar reactivación", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    if (platosControlador.reactivar(id)) {
                        JOptionPane.showMessageDialog(dialogo, "El plato ha sido reactivado con éxito.",
                                "Éxito", JOptionPane.INFORMATION_MESSAGE);
                        dialogo.dispose();
                        LimpiarPlatos();
                        ListarPlatos(TablePlatos, "");
                    } else {
                        JOptionPane.showMessageDialog(dialogo, "No se pudo reactivar el plato.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialogo, "Error al reactivar el plato: " + mensajeError(ex),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
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
        dialogo.add(panelBotones, java.awt.BorderLayout.SOUTH);

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
            BigDecimal tasa = configServicio.getDecimal(Modelo.ConfigClaves.TASA_DOLAR, new BigDecimal("36.5000"));
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
        btnVacaciones.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        btnVacaciones.setToolTipText("Alternar estado Activo / Vacaciones del mesonero seleccionado");
        btnVacaciones.setBounds(221, 90, 110, 28);
        btnVacaciones.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnVacaciones.addActionListener(e -> alternarVacacionesMesoneroConfig());
        panel.add(btnVacaciones);

        javax.swing.JButton btnEliminar = new javax.swing.JButton("Eliminar");
        btnEliminar.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        btnEliminar.setForeground(new java.awt.Color(180, 0, 0));
        btnEliminar.setToolTipText("Eliminar lógicamente (soft-delete)");
        btnEliminar.setBounds(339, 90, 95, 28);
        btnEliminar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnEliminar.addActionListener(e -> eliminarMesoneroConfig());
        panel.add(btnEliminar);

        javax.swing.JButton btnNuevo = new javax.swing.JButton("Nuevo");
        btnNuevo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
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
            JOptionPane.showMessageDialog(this, "El nombre completo del mesonero es obligatorio.", "Validación", JOptionPane.WARNING_MESSAGE);
            txtMesoneroNombre.requestFocus();
            return;
        }
        if (cedula.isEmpty()) {
            JOptionPane.showMessageDialog(this, "La cédula / documento del mesonero es obligatoria.", "Validación", JOptionPane.WARNING_MESSAGE);
            txtMesoneroCedula.requestFocus();
            return;
        }

        try {
            Mesonero m = new Mesonero(nombre, cedula, telefono, activo);
            mesoneroDao.registrar(m);
            JOptionPane.showMessageDialog(this, "Mesonero '" + nombre + "' registrado con éxito.", "Registro exitoso", JOptionPane.INFORMATION_MESSAGE);
            limpiarFormularioMesonero();
            listarMesonerosConfig();
            cargarComboMesoneros();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al registrar mesonero: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void modificarMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String idStr = txtMesoneroId.getText().trim();
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un mesonero de la tabla para modificar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idStr);
        String nombre = txtMesoneroNombre.getText().trim();
        String cedula = txtMesoneroCedula.getText().trim();
        String telefono = txtMesoneroTelefono.getText().trim();
        boolean activo = chkMesoneroActivo.isSelected();

        if (nombre.isEmpty() || cedula.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El nombre completo y la cédula son obligatorios.", "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Mesonero m = new Mesonero(id, nombre, cedula, telefono, activo, false);
            boolean ok = mesoneroDao.modificar(m);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Mesonero actualizado correctamente.", "Actualizado", JOptionPane.INFORMATION_MESSAGE);
                limpiarFormularioMesonero();
                listarMesonerosConfig();
                cargarComboMesoneros();
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
            JOptionPane.showMessageDialog(this, "Seleccione un mesonero de la tabla para alternar su estado.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idStr);
        boolean actual = chkMesoneroActivo.isSelected();
        boolean nuevo = !actual;

        try {
            boolean ok = mesoneroDao.cambiarActivo(id, nuevo);
            if (ok) {
                String estadoStr = nuevo ? "Activo / Disponible" : "De Vacaciones / Inactivo";
                JOptionPane.showMessageDialog(this, "El estado del mesonero se cambió a: " + estadoStr, "Estado actualizado", JOptionPane.INFORMATION_MESSAGE);
                chkMesoneroActivo.setSelected(nuevo);
                listarMesonerosConfig();
                cargarComboMesoneros();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al alternar estado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminarMesoneroConfig() {
        if (!autorizar(PoliticaAcceso.Accion.EDITAR_CONFIGURACION)) return;
        String idStr = txtMesoneroId.getText().trim();
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un mesonero de la tabla para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
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
                    JOptionPane.showMessageDialog(this, "Mesonero eliminado correctamente.", "Eliminado", JOptionPane.INFORMATION_MESSAGE);
                    limpiarFormularioMesonero();
                    listarMesonerosConfig();
                    cargarComboMesoneros();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al eliminar mesonero: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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
                    JOptionPane.showMessageDialog(Sistema.this,
                            "Respaldo generado exitosamente.\n\n"
                            + "Archivo: " + p.getFileName() + "\n"
                            + "Tamaño: " + (tamBytes / 1024) + " KB\n"
                            + "Ruta: " + p.toAbsolutePath(),
                            "Respaldo Creado", JOptionPane.INFORMATION_MESSAGE);
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
                    JOptionPane.showMessageDialog(Sistema.this,
                            "Base de datos restaurada correctamente a partir de:\n" + archivoSel.getName(),
                            "Restauración Exitosa", JOptionPane.INFORMATION_MESSAGE);
                    ListarConfig();
                    ListarPedidos();
                    ListarPlatos(TablePlatos, "");
                    panelSalas();
                    actualizarEstadoTasaUI();
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
            JOptionPane.showMessageDialog(this, "Seleccione un pedido de la tabla para anular.", "Pedido requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idStr);
        String motivo = JOptionPane.showInputDialog(this,
                "Ingrese el motivo de la anulación del pedido #" + id + ":",
                "Anulación de Pedido", JOptionPane.WARNING_MESSAGE);
        if (motivo == null) return;
        if (motivo.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe ingresar un motivo para anular el pedido.", "Motivo obligatorio", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirmar = JOptionPane.showConfirmDialog(this,
                "¿Confirma la anulación del pedido #" + id + "?\nMotivo: " + motivo.trim(),
                "Confirmar Anulación", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmar != JOptionPane.YES_OPTION) return;

        String usr = (LabelVendedor != null && !LabelVendedor.getText().isBlank()) ? LabelVendedor.getText().trim() : "Sistema";
        try {
            boolean exito = pedidosControlador.anularPedido(id, motivo.trim(), usr);
            if (exito) {
                JOptionPane.showMessageDialog(this, "El pedido #" + id + " ha sido anulado con éxito.", "Pedido Anulado", JOptionPane.INFORMATION_MESSAGE);
                ListarPedidos();
            } else {
                JOptionPane.showMessageDialog(this, "No se pudo anular el pedido #" + id + ".", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al anular pedido: " + mensajeError(ex), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    void mostrarAuditoriaPedidoSeleccionado() {
        if (!autorizar(PoliticaAcceso.Accion.GESTIONAR_PEDIDOS)) return;
        String idStr = txtIdHistorialPedido.getText().trim();
        if (idStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un pedido de la tabla para ver su auditoría.", "Pedido requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int id = Integer.parseInt(idStr);
        List<Modelo.AuditoriaPedido> lista = pedidosControlador.obtenerAuditoriaPedido(id);
        if (lista == null || lista.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No se encontraron eventos de auditoría para el pedido #" + id + ".", "Auditoría Vacía", JOptionPane.INFORMATION_MESSAGE);
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
        dlg.setVisible(true);
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
        List<Usuario> Listar = lgDao.ListarUsuarios();
        modelo = (DefaultTableModel) TableUsuarios.getModel();
        modelo.setRowCount(0);
        Object[] ob = new Object[4];
        for (int i = 0; i < Listar.size(); i++) {
            ob[0] = Listar.get(i).getId();
            ob[1] = Listar.get(i).getNombre();
            ob[2] = Listar.get(i).getCorreo();
            ob[3] = Listar.get(i).getRol();
            modelo.addRow(ob);
        }
        colorHeader(TableUsuarios);
    }

    private void ListarSalas() {
        new ListaSalasSwingWorker(salasControlador, this::mostrarSalasEnTabla,
                this::mostrarErrorCargaSalas).execute();
    }

    private void mostrarSalasEnTabla(List<Salas> Listar) {
        modelo = (DefaultTableModel) tableSala.getModel();
        modelo.setRowCount(0);
        boolean tieneColumnaTipo = tableSala.getColumnCount() >= 4;
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
        if (chkAplicaIvaPlato != null) {
            chkAplicaIvaPlato.setSelected(true);
        }
    }

    private void panelSalas() {
        new ListaSalasSwingWorker(salasControlador, this::mostrarSalasEnPanel,
                this::mostrarErrorCargaSalas).execute();
    }

    private void mostrarSalasEnPanel(List<Salas> Listar) {
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
            String unidad = esBarra ? "puestos" : "mesas";
            String textoBoton = esBarra
                    ? nombre + " (" + ocupadas + "/" + cantidad + " puestos)"
                    : nombre + " (" + ocupadas + "/" + cantidad + " ocupadas)";
            JButton boton = new JButton(textoBoton, new ImageIcon(getClass().getResource("/Img/salas.png")));
            boton.setToolTipText(nombre + (esBarra ? " [BARRA]" : "") + " - " + ocupadas + " de " + cantidad + " " + (esBarra ? "puestos ocupados" : "mesas ocupadas"));
            boton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            boton.setHorizontalTextPosition(JButton.CENTER);
            boton.setVerticalTextPosition(JButton.BOTTOM);
            boton.setBackground(new Color(204, 204, 204));
            PanelSalas.add(boton);
            boton.addActionListener((ActionEvent e) -> {
                panelMesas(id, nombre, cantidad, esBarra);
                jTabbedPane1.setSelectedIndex(2);
            });
        }
        PanelSalas.revalidate();
        PanelSalas.repaint();
    }

    private void initIvaPlatosYBarraSalas() {
        // 1. Checkbox Aplica IVA en formulario de Platos (Tab 2)
        if (jPanel11 != null) {
            chkAplicaIvaPlato = new javax.swing.JCheckBox("Aplica IVA (16%)", true);
            chkAplicaIvaPlato.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            chkAplicaIvaPlato.setBackground(new java.awt.Color(204, 204, 204));
            chkAplicaIvaPlato.setOpaque(false);
            chkAplicaIvaPlato.setFocusable(false);
            chkAplicaIvaPlato.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            chkAplicaIvaPlato.setToolTipText("Marcar si el plato genera IVA o desmarcar si está exento");
            jPanel11.add(chkAplicaIvaPlato, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 220, 170, 25));
        }

        // 2. Combo Tipo de Sala en formulario de Salas (Tab 8)
        if (jPanel10 != null) {
            javax.swing.JLabel lblTipoSala = new javax.swing.JLabel("Tipo:");
            lblTipoSala.setFont(new java.awt.Font("Times New Roman", java.awt.Font.BOLD | java.awt.Font.ITALIC, 14));
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

        // 3. Agregar columna IVA a TablePlatos si no la tiene
        if (TablePlatos != null && TablePlatos.getModel() instanceof DefaultTableModel modelPlatos) {
            if (modelPlatos.getColumnCount() == 3) {
                modelPlatos.addColumn("IVA");
            }
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

    private void initPanelMesasMejorado() {
        jPanel22.removeAll();
        jPanel22.setLayout(new java.awt.BorderLayout(8, 8));
        jPanel22.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Header Panel (Norte)
        javax.swing.JPanel panelHeader = new javax.swing.JPanel(new java.awt.BorderLayout(10, 0));
        panelHeader.setOpaque(false);

        lblTituloSalaMesas = new javax.swing.JLabel("Selecciona una sala para ver sus mesas");
        lblTituloSalaMesas.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15));
        lblTituloSalaMesas.setForeground(new java.awt.Color(0, 102, 102));
        panelHeader.add(lblTituloSalaMesas, java.awt.BorderLayout.WEST);

        javax.swing.JPanel panelAcciones = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0));
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
        panelHeader.add(panelAcciones, java.awt.BorderLayout.EAST);

        jPanel22.add(panelHeader, java.awt.BorderLayout.NORTH);

        // Centro (Scroll de mesas)
        jPanel22.add(jScrollPane9, java.awt.BorderLayout.CENTER);

        // Footer Panel (Sur - Leyenda)
        javax.swing.JPanel panelFooter = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 15, 4));
        panelFooter.setOpaque(false);
        javax.swing.JLabel lblLeyendaTitulo = new javax.swing.JLabel("Leyenda:");
        lblLeyendaTitulo.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        panelFooter.add(lblLeyendaTitulo);
        panelFooter.add(cuadroColor(new java.awt.Color(0, 102, 102), "Libre"));
        panelFooter.add(cuadroColor(new java.awt.Color(255, 51, 51), "Ocupada"));

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

        List<JButton> botonesMesa = new ArrayList<>();
        for (int i = 1; i <= cant; i++) {
            int num_mesa = i;
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
            boton.setIcon(new ImageIcon(getClass().getResource("/Img/mesa.png")));
            boton.setHorizontalTextPosition(JButton.CENTER);
            boton.setVerticalTextPosition(JButton.BOTTOM);
            int verificar = estados.getOrDefault(num_mesa, 0);
            String nombreMesonero = mesonerosMesas.get(num_mesa);
            String visual = (nombreMesonero != null && !nombreMesonero.isBlank())
                    ? nombresVisuales.getOrDefault(nombreMesonero.trim(), ServicioMesoneroNombre.extraerPrimerNombre(nombreMesonero))
                    : null;

            String prefijo = esBarra ? "PUESTO N°: " : "MESA N°: ";
            if (verificar > 0) {
                boton.setBackground(new Color(255, 51, 51));
                if (visual != null && !visual.isBlank()) {
                    boton.setText("<html><center>" + prefijo + i + "<br><font color='#FFFF99' size='2'><b>" + visual + "</b></font></center></html>");
                    boton.setToolTipText((esBarra ? "Puesto Ocupado" : "Ocupada") + " - Atendida por: " + nombreMesonero);
                } else {
                    boton.setText(prefijo + i);
                    boton.setToolTipText((esBarra ? "Puesto Ocupado" : "Ocupada") + " (Sin mesonero asignado)");
                }
            } else {
                boton.setBackground(new Color(0, 102, 102));
                boton.setText(prefijo + i);
                boton.setToolTipText((esBarra ? "Puesto " : "Mesa ") + i + " Disponible");
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
                    jPanel23.setBorder(javax.swing.BorderFactory.createTitledBorder(
                            javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 102, 102)),
                            "PEDIDO - " + (nombreSala != null ? nombreSala : ("SALA " + id_sala)) + " | " + (esBarra ? "PUESTO " : "MESA ") + num_mesa,
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

    // platos
    private void ListarPlatos(JTable tabla, String filtro) {
        boolean menuPedido = tabla == tblTemPlatos;
        long version = menuPedido ? ++versionMenuPlatos : ++versionCatalogoPlatos;
        String fecha = fechaActual();
        new ListaPlatosSwingWorker(platosControlador, filtro, fecha,
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
        modelo = (DefaultTableModel) tabla.getModel();
        modelo.setRowCount(0);
        boolean tieneColumnaIva = tabla.getColumnCount() >= 4;
        Object[] ob = new Object[tieneColumnaIva ? 4 : 3];
        for (int i = 0; i < Listar.size(); i++) {
            Platos p = Listar.get(i);
            platoAplicaIvaMap.put(p.getId(), p.isAplicaIva());
            platosPorId.put(p.getId(), p);
            ob[0] = p.getId();
            ob[1] = p.getNombre();
            BigDecimal precioUsd = p.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
            String precioStr = String.format(java.util.Locale.US, "%.2f", precioUsd);
            if (tabla == tblTemPlatos) {
                ob[2] = "$ " + precioStr;
            } else {
                ob[2] = precioStr;
            }
            if (tieneColumnaIva) {
                ob[3] = p.isAplicaIva() ? "Sí (16%)" : "Exento (0%)";
            }
            modelo.addRow(ob);
        }
        installarRenderersPlatos(tabla);
        colorHeader(tabla);
    }

    private void installarRenderersPlatos(JTable tabla) {
        RendererPlato renderer = new RendererPlato(tabla == tblTemPlatos, platosPorId);
        for (int i = 0; i < tabla.getColumnCount(); i++) {
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
            if (jTabbedPane1.getSelectedComponent() == panelCategorias) {
                cargarCategorias();
                cargarPlatosAsignacion();
            }
        });
    }

    private void cargarCategorias() {
        try {
            List<Modelo.Categoria> categorias = categoriaControlador().listar();
            Modelo.Categoria seleccionada = listaCategorias.getSelectedValue();
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
        try {
            Modelo.Categoria c = new Modelo.Categoria();
            c.setNombre(txtNombreCategoria.getText());
            c.setColor((String) cbColorCategoria.getSelectedItem());
            categoriaControlador().registrar(c);
            limpiarFormularioCategoria();
            cargarCategorias();
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
            JOptionPane.showMessageDialog(this, "Selecciona una categoría para modificar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Modelo.Categoria c = new Modelo.Categoria(seleccionada.getId(), txtNombreCategoria.getText(),
                    (String) cbColorCategoria.getSelectedItem(), seleccionada.getOrden());
            categoriaControlador().modificar(c);
            cargarCategorias();
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
            JOptionPane.showMessageDialog(this, "Selecciona una categoría para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
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
        if (p == null || c == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un plato y una categoría.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            categoriaControlador().asignarPlato(p.getNombre(), c.getId());
            favoritoControlador().marcar(p.getNombre(), chkFavoritoPlato.isSelected());
            cargarPlatosAsignacion();
            ListarPlatos(tblTemPlatos, txtBuscarPlato.getText());
        } catch (Exception ex) {
            mostrarErrorCategorias(ex);
        }
    }

    private void mostrarErrorCategorias(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudieron actualizar las categorías. El detalle quedó registrado.";
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
                setText((p.isFavorito() ? "★ " : "") + p.getNombre() + categoria);
            }
            return this;
        }
    }

    private static final class IconoColor implements Icon {
        private final Color color;

        private IconoColor(String hex) {
            Color c;
            try {
                c = hex != null ? Color.decode(hex) : Color.GRAY;
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

    private final class RendererPlato implements javax.swing.table.TableCellRenderer {
        private final boolean menuPedido;
        private final Map<Integer, Platos> porId;
        private final javax.swing.JLabel etiqueta;

        private RendererPlato(boolean menuPedido, Map<Integer, Platos> porId) {
            this.menuPedido = menuPedido;
            this.porId = porId;
            this.etiqueta = new JLabel();
            this.etiqueta.setOpaque(true);
            this.etiqueta.setHorizontalAlignment(SwingConstants.LEFT);
            etiqueta.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        }

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
                boolean enfocado, int fila, int columna) {
            etiqueta.setFont(tabla.getFont());
            etiqueta.setText(valor == null ? "" : valor.toString());
            etiqueta.setToolTipText(null);
            Object idObj = tabla.getModel().getValueAt(fila, 0);
            Platos p = null;
            if (idObj != null) {
                try {
                    p = porId.get(Integer.parseInt(idObj.toString()));
                } catch (NumberFormatException ignored) {}
            }
            if (seleccionado) {
                etiqueta.setBackground(tabla.getSelectionBackground());
                etiqueta.setForeground(tabla.getSelectionForeground());
            } else {
                Color fondo = null;
                if (menuPedido && p != null && p.getCategoriaColor() != null) {
                    try {
                        fondo = Color.decode(p.getCategoriaColor());
                    } catch (NumberFormatException ignored) {}
                }
                if (fondo != null) {
                    etiqueta.setBackground(fondo);
                    etiqueta.setForeground(colorContraste(fondo));
                } else {
                    etiqueta.setBackground(tabla.getBackground());
                    etiqueta.setForeground(tabla.getForeground());
                }
            }
            if (menuPedido && columna == 1 && p != null && p.isFavorito()) {
                etiqueta.setText("★ " + etiqueta.getText());
                etiqueta.setToolTipText("Favorito: siempre visible entre los primeros platos.");
            }
            if (p != null && p.getCategoriaNombre() != null) {
                etiqueta.setToolTipText("Categoría: " + p.getCategoriaNombre());
            }
            return etiqueta;
        }
    }

    private Color colorContraste(Color fondo) {
        double luminancia = (0.299 * fondo.getRed() + 0.587 * fondo.getGreen() + 0.114 * fondo.getBlue()) / 255.0;
        return luminancia > 0.5 ? Color.BLACK : Color.WHITE;
    }

    //registrar pedido
    private int registrarPedidoCompleto() {
        int id_sala = Integer.parseInt(txtTempIdSala.getText());
        int num_mesa = Integer.parseInt(txtTempNumMesa.getText());
        BigDecimal tasa = (conf != null && conf.getTasaDolar() != null) ? conf.getTasaDolar() : new BigDecimal("36.5000");
        BigDecimal ivaPorcentaje = (conf != null && conf.getIvaPorcentaje() != null) ? conf.getIvaPorcentaje() : new BigDecimal("16.00");

        BigDecimal baseImponible = BigDecimal.ZERO.setScale(2);
        BigDecimal exento = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < tableMenu.getRowCount(); i++) {
            BigDecimal subtotal = importeMonetario(tableMenu.getValueAt(i, 4));
            boolean itemAplicaIva = true;
            if (tableMenu.getColumnCount() > 6 && tableMenu.getValueAt(i, 6) != null) {
                String valIva = tableMenu.getValueAt(i, 6).toString().toLowerCase(java.util.Locale.ROOT);
                if (valIva.contains("exento") || valIva.contains("no") || valIva.equals("false") || valIva.equals("0")) {
                    itemAplicaIva = false;
                }
            } else {
                Object idObj = tableMenu.getValueAt(i, 0);
                if (idObj != null) {
                    try {
                        int idPlato = Integer.parseInt(idObj.toString());
                        itemAplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                    } catch (Exception ignored) {}
                }
            }
            if (itemAplicaIva) {
                baseImponible = baseImponible.add(subtotal);
            } else {
                exento = exento.add(subtotal);
            }
        }

        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(baseImponible, exento, ivaPorcentaje, tasa);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(id_sala);
        pedido.setNum_mesa(num_mesa);
        pedido.setSubtotal(fiscal.subtotalUsd());
        pedido.setIvaPorcentaje(fiscal.ivaPorcentaje());
        pedido.setIvaMonto(fiscal.ivaUsd());
        pedido.setTotalDecimal(fiscal.totalUsd());
        pedido.setTasaCambio(fiscal.tasaCambio());
        pedido.setSubtotalBs(fiscal.subtotalBs());
        pedido.setIvaBs(fiscal.ivaBs());
        pedido.setTotalBs(fiscal.totalBs());
        pedido.setUsuario(LabelVendedor.getText());
        if (cbMesoneroPedido != null && cbMesoneroPedido.getSelectedItem() instanceof Mesonero m) {
            pedido.setIdMesonero(m.getId());
            pedido.setMesoneroNombre(m.getNombreCompleto());
        }

        List<DetallePedido> detalles = new ArrayList<>();
        for (int i = 0; i < tableMenu.getRowCount(); i++) {
            DetallePedido detalle = new DetallePedido();
            detalle.setNombre(tableMenu.getValueAt(i, 1).toString());
            detalle.setCantidad(Integer.parseInt(tableMenu.getValueAt(i, 2).toString()));
            detalle.setPrecioDecimal(importeMonetario(tableMenu.getValueAt(i, 3)));
            Object comentario = tableMenu.getValueAt(i, 5);
            detalle.setComentario(comentario == null ? "" : comentario.toString());
            detalles.add(detalle);
        }
        return pedidosControlador.registrarPedidoCompleto(pedido, detalles);
    }

    private void actualizarPedidoEnEdicion() {
        int id_sala = Integer.parseInt(txtTempIdSala.getText());
        int num_mesa = Integer.parseInt(txtTempNumMesa.getText());
        BigDecimal tasa = (conf != null && conf.getTasaDolar() != null) ? conf.getTasaDolar() : new BigDecimal("36.5000");
        BigDecimal ivaPorcentaje = (conf != null && conf.getIvaPorcentaje() != null) ? conf.getIvaPorcentaje() : new BigDecimal("16.00");

        BigDecimal baseImponible = BigDecimal.ZERO.setScale(2);
        BigDecimal exento = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < tableMenu.getRowCount(); i++) {
            BigDecimal subtotal = importeMonetario(tableMenu.getValueAt(i, 4));
            boolean itemAplicaIva = true;
            if (tableMenu.getColumnCount() > 6 && tableMenu.getValueAt(i, 6) != null) {
                String valIva = tableMenu.getValueAt(i, 6).toString().toLowerCase(java.util.Locale.ROOT);
                if (valIva.contains("exento") || valIva.contains("no") || valIva.equals("false") || valIva.equals("0")) {
                    itemAplicaIva = false;
                }
            } else {
                Object idObj = tableMenu.getValueAt(i, 0);
                if (idObj != null) {
                    try {
                        int idPlato = Integer.parseInt(idObj.toString());
                        itemAplicaIva = platoAplicaIvaMap.getOrDefault(idPlato, true);
                    } catch (Exception ignored) {}
                }
            }
            if (itemAplicaIva) {
                baseImponible = baseImponible.add(subtotal);
            } else {
                exento = exento.add(subtotal);
            }
        }

        CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(baseImponible, exento, ivaPorcentaje, tasa);

        Pedidos pedido = new Pedidos();
        pedido.setId_sala(id_sala);
        pedido.setNum_mesa(num_mesa);
        pedido.setSubtotal(fiscal.subtotalUsd());
        pedido.setIvaPorcentaje(fiscal.ivaPorcentaje());
        pedido.setIvaMonto(fiscal.ivaUsd());
        pedido.setTotalDecimal(fiscal.totalUsd());
        pedido.setTasaCambio(fiscal.tasaCambio());
        pedido.setSubtotalBs(fiscal.subtotalBs());
        pedido.setIvaBs(fiscal.ivaBs());
        pedido.setTotalBs(fiscal.totalBs());
        pedido.setUsuario(LabelVendedor.getText());
        if (cbMesoneroPedido != null && cbMesoneroPedido.getSelectedItem() instanceof Mesonero m) {
            pedido.setIdMesonero(m.getId());
            pedido.setMesoneroNombre(m.getNombreCompleto());
        }

        List<DetallePedido> detalles = new ArrayList<>();
        for (int i = 0; i < tableMenu.getRowCount(); i++) {
            DetallePedido detalle = new DetallePedido();
            detalle.setNombre(tableMenu.getValueAt(i, 1).toString());
            detalle.setCantidad(Integer.parseInt(tableMenu.getValueAt(i, 2).toString()));
            detalle.setPrecioDecimal(importeMonetario(tableMenu.getValueAt(i, 3)));
            Object comentario = tableMenu.getValueAt(i, 5);
            detalle.setComentario(comentario == null ? "" : comentario.toString());
            detalles.add(detalle);
        }
        pedidosControlador.actualizarPedidoCompleto(idPedidoEdicion, pedido, detalles);
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
        // Limpiar prefijos de moneda como $, Bs., Bs
        str = str.replace("Bs.", "").replace("Bs", "").replace("$", "").trim();
        // Aceptar coma decimal convirtiéndola a punto
        str = str.replace(',', '.');
        try {
            BigDecimal importe = new BigDecimal(str);
            if (importe.precision() - importe.scale() > 8) {
                throw new ArithmeticException("El importe excede DECIMAL(10,2).");
            }
            return importe.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw ErrorAplicacionException.validacion("Ingresa un importe válido.");
        }
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
        BigDecimal tasa = (ped.getTasaCambio() != null && ped.getTasaCambio().compareTo(BigDecimal.ZERO) > 0)
                ? ped.getTasaCambio()
                : ((conf != null && conf.getTasaDolar() != null) ? conf.getTasaDolar() : new BigDecimal("36.5000"));
        BigDecimal totalBs = ped.getTotalBs() != null ? ped.getTotalBs() : ped.getTotalDecimal().multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBs2Dec = totalBs.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalUsd2Dec = ped.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotalUsd = ped.getSubtotal() != null ? ped.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd2Dec;
        BigDecimal ivaUsd = ped.getIvaMonto() != null ? ped.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal ivaPct = ped.getIvaPorcentaje() != null ? ped.getIvaPorcentaje().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        String totalBsStr = String.format(java.util.Locale.US, "%.2f", totalBs2Dec);
        String totalUsdStr = String.format(java.util.Locale.US, "%.2f", totalUsd2Dec);
        String subtotalUsdStr = String.format(java.util.Locale.US, "%.2f", subtotalUsd);
        String ivaUsdStr = String.format(java.util.Locale.US, "%.2f", ivaUsd);
        String ivaPctStr = String.format(java.util.Locale.US, "%.2f", ivaPct);

        totalFinalizar.setText("Bs. " + totalBsStr + " ($ " + totalUsdStr + ")");
        if (ivaPct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseImpUsd = ivaUsd.multiply(new BigDecimal("100")).divide(ivaPct, 2, RoundingMode.HALF_UP);
            BigDecimal exentoUsd = subtotalUsd.subtract(baseImpUsd);
            if (exentoUsd.compareTo(BigDecimal.ZERO) > 0) {
                totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                        + " (Base Imponible: $ " + String.format(java.util.Locale.US, "%.2f", baseImpUsd)
                        + ", Exento: $ " + String.format(java.util.Locale.US, "%.2f", exentoUsd) + ")"
                        + " | IVA (" + ivaPctStr + "%): $ " + ivaUsdStr
                        + " | Total a Pagar: $ " + totalUsdStr
                        + " (Bs. " + totalBsStr + ")");
            } else {
                totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                        + " | IVA (" + ivaPctStr + "%): $ " + ivaUsdStr
                        + " | Total a Pagar: $ " + totalUsdStr
                        + " (Bs. " + totalBsStr + ")");
            }
        } else {
            totalFinalizar.setToolTipText("Subtotal: $ " + subtotalUsdStr
                    + " | Total a Pagar: $ " + totalUsdStr
                    + " (Bs. " + totalBsStr + ")");
        }

        txtFechaHora.setText("" + ped.getFecha());
        txtSalaFinalizar.setText("" + ped.getSala());
        txtNumMesaFinalizar.setText("" + ped.getNum_mesa());
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
        Object[] ob = new Object[6];
        for (int i = 0; i < Listar.size(); i++) {
            ob[0] = Listar.get(i).getId();
            ob[1] = Listar.get(i).getNombre();
            ob[2] = Listar.get(i).getCantidad();
            BigDecimal precio = Listar.get(i).getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalPlato = precio.multiply(BigDecimal.valueOf(Listar.get(i).getCantidad())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioBs = precio.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            ob[3] = "$ " + String.format(java.util.Locale.US, "%.2f", precio) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")";
            ob[4] = "$ " + String.format(java.util.Locale.US, "%.2f", subtotalPlato);
            ob[5] = Listar.get(i).getComentario();
            modelo.addRow(ob);
        }
        colorHeader(tableFinalizar);
    }

    private void mostrarErrorCargaPedido(Throwable error) {
        String mensaje = error instanceof ErrorAplicacionException
                ? error.getMessage()
                : "No se pudo cargar el pedido. El detalle quedó registrado.";
        JOptionPane.showMessageDialog(this, mensaje, "Error al cargar pedido", JOptionPane.ERROR_MESSAGE);
    }

}
