package Vista;

import Modelo.AuditoriaPedidosDao;
import Modelo.Config;
import Modelo.FiltroReporteAnulados;
import Modelo.TicketAnuladoReporte;
import Servicio.GeneradorHtmlReporteAnulados;
import Servicio.GeneradorPdfReporteAnulados;
import Servicio.ServicioImpresionTicket;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo ejecutivo para la consulta, filtrado multicriterio, visualización,
 * impresión y exportación en PDF/HTML del reporte de tickets y facturas anuladas.
 */
public class DialogoReporteAnulados extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(DialogoReporteAnulados.class.getName());

    private static final Color COLOR_FONDO = new Color(248, 250, 252);
    private static final Color COLOR_PRIMARIO = new Color(30, 41, 59);
    private static final Color COLOR_PELIGRO = new Color(192, 57, 43);
    private static final Color COLOR_EXITO = new Color(39, 174, 96);
    private static final Color COLOR_ADVERTENCIA = new Color(211, 84, 0);
    private static final Color COLOR_BORDE = new Color(226, 232, 240);

    private final AuditoriaPedidosDao auditoriaDao;
    private final Config configEmpresa;
    private final String usuarioActual;

    // Filtros
    private JTextField txtFechaDesde;
    private JTextField txtFechaHasta;
    private JTextField txtMesa;
    private JComboBox<String> cbUsuario;
    private JComboBox<FiltroReporteAnulados.TipoFiltroAnulacion> cbTipoAnulacion;
    private JTextField txtBuscarTexto;

    // Tarjetas KPI
    private JLabel lblKpiTotalTickets;
    private JLabel lblKpiTotalUsd;
    private JLabel lblKpiTotalBs;
    private JLabel lblKpiDesglose;

    // Tabla
    private JTable tblResultados;
    private DefaultTableModel modeloTabla;
    private TableRowSorter<DefaultTableModel> sorter;

    // Botones de acción
    private JButton btnImprimir;
    private JButton btnExportarPdf;
    private JButton btnVerHtml;
    private JButton btnCerrar;

    private List<TicketAnuladoReporte> ticketsActuales = new ArrayList<>();

    public DialogoReporteAnulados(Window owner, AuditoriaPedidosDao auditoriaDao, Config configEmpresa, String usuarioActual) {
        super(owner, "Reporte Ejecutivo de Auditoría: Tickets y Facturas Anuladas", ModalityType.APPLICATION_MODAL);
        this.auditoriaDao = auditoriaDao != null ? auditoriaDao : new AuditoriaPedidosDao();
        this.configEmpresa = configEmpresa;
        this.usuarioActual = (usuarioActual != null && !usuarioActual.isBlank()) ? usuarioActual.trim() : "Sistema";

        construirUi();
        cargarUsuariosDisponiblesAsync();
        aplicarFiltroHoy();
    }

    public DialogoReporteAnulados(Frame owner, AuditoriaPedidosDao auditoriaDao, Config configEmpresa, String usuarioActual) {
        this((Window) owner, auditoriaDao, configEmpresa, usuarioActual);
    }

    public DialogoReporteAnulados(Dialog owner, AuditoriaPedidosDao auditoriaDao, Config configEmpresa, String usuarioActual) {
        this((Window) owner, auditoriaDao, configEmpresa, usuarioActual);
    }

    private void construirUi() {
        setSize(1100, 680);
        setMinimumSize(new Dimension(980, 580));
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(COLOR_FONDO);
        content.setBorder(new EmptyBorder(14, 16, 14, 16));

        // 1. Cabecera
        JPanel pnlCabecera = new JPanel(new BorderLayout());
        pnlCabecera.setOpaque(false);

        JLabel lblTitulo = new JLabel("📊 Reporte de Auditoría: Tickets y Facturas Anuladas");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(COLOR_PRIMARIO);

        JLabel lblSub = new JLabel("Consulte, filtre e imprima el detalle oficial de comprobantes cancelados por fecha, mesa o usuario responsable.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 116, 139));

        pnlCabecera.add(lblTitulo, BorderLayout.NORTH);
        pnlCabecera.add(lblSub, BorderLayout.SOUTH);
        content.add(pnlCabecera, BorderLayout.NORTH);

        // 2. Centro (Filtros + KPIs + Tabla)
        JPanel pnlCentro = new JPanel(new BorderLayout(0, 10));
        pnlCentro.setOpaque(false);

        // 2.1. Panel de Filtros Multicriterio
        pnlCentro.add(crearPanelFiltros(), BorderLayout.NORTH);

        // 2.2. Panel Central (KPIs + Tabla de Resultados)
        JPanel pnlGridYTabla = new JPanel(new BorderLayout(0, 8));
        pnlGridYTabla.setOpaque(false);
        pnlGridYTabla.add(crearPanelKpis(), BorderLayout.NORTH);
        pnlGridYTabla.add(crearPanelTabla(), BorderLayout.CENTER);

        pnlCentro.add(pnlGridYTabla, BorderLayout.CENTER);
        content.add(pnlCentro, BorderLayout.CENTER);

        // 3. Botonera Inferior
        content.add(crearPanelBotonera(), BorderLayout.SOUTH);

        setContentPane(content);

        // Atajo ESC para cerrar
        KeyStroke strokeEsc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> dispose(), strokeEsc, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel crearPanelFiltros() {
        JPanel pnlFiltros = new JPanel();
        pnlFiltros.setLayout(new BoxLayout(pnlFiltros, BoxLayout.Y_AXIS));
        pnlFiltros.setBackground(Color.WHITE);
        pnlFiltros.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_BORDE, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Fila 1: Botones rápidos de período y rango de fechas
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        fila1.setOpaque(false);

        JButton btnHoy = new JButton("Hoy");
        btnHoy.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnHoy.setToolTipText("<html><body style='width: 220px; padding: 2px;'>"
                + "<b style='color:#0284c7;'>Período Hoy</b><br>"
                + "Filtra únicamente los comprobantes anulados durante el día actual."
                + "</body></html>");
        btnHoy.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnHoy.addActionListener(e -> aplicarFiltroHoy());

        JButton btnMes = new JButton("Mes Actual");
        btnMes.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnMes.setToolTipText("<html><body style='width: 220px; padding: 2px;'>"
                + "<b style='color:#0284c7;'>Período Mensual</b><br>"
                + "Filtra todos los comprobantes anulados desde el 1° del mes actual hasta hoy."
                + "</body></html>");
        btnMes.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMes.addActionListener(e -> aplicarFiltroMesActual());

        JButton btnTodos = new JButton("Histórico Completo");
        btnTodos.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnTodos.setToolTipText("<html><body style='width: 200px; padding: 2px;'>"
                + "Muestra todas las anulaciones históricas registradas en el sistema."
                + "</body></html>");
        btnTodos.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTodos.addActionListener(e -> {
            txtFechaDesde.setText("");
            txtFechaHasta.setText("");
            ejecutarConsultaFiltros();
        });

        JLabel lblDesde = new JLabel("Desde:");
        lblDesde.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtFechaDesde = new JTextField(8);
        txtFechaDesde.setToolTipText("Fecha inicial en formato AAAA-MM-DD");
        txtFechaDesde.addActionListener(e -> ejecutarConsultaFiltros());

        JLabel lblHasta = new JLabel("Hasta:");
        lblHasta.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtFechaHasta = new JTextField(8);
        txtFechaHasta.setToolTipText("Fecha final en formato AAAA-MM-DD");
        txtFechaHasta.addActionListener(e -> ejecutarConsultaFiltros());

        JLabel lblMesa = new JLabel("Mesa:");
        lblMesa.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtMesa = new JTextField(4);
        txtMesa.setHorizontalAlignment(JTextField.CENTER);
        txtMesa.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtMesa.setToolTipText("<html><body style='width: 230px; padding: 2px;'>"
                + "<b style='color:#0284c7;'>Filtro por Número de Mesa</b><br>"
                + "Indique el número de mesa (ej. 1, 2) o '0'/'rapido' para barra."
                + "</body></html>");
        txtMesa.addActionListener(e -> ejecutarConsultaFiltros());

        JLabel lblUser = new JLabel("Usuario:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cbUsuario = new JComboBox<>(new String[]{"(Todos los usuarios)"});
        cbUsuario.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cbUsuario.setToolTipText("Filtra por el usuario que anuló o atendió el pedido");
        cbUsuario.addActionListener(e -> ejecutarConsultaFiltros());

        fila1.add(btnHoy);
        fila1.add(btnMes);
        fila1.add(btnTodos);
        fila1.add(new JSeparator(SwingConstants.VERTICAL));
        fila1.add(lblDesde);
        fila1.add(txtFechaDesde);
        fila1.add(lblHasta);
        fila1.add(txtFechaHasta);
        fila1.add(lblMesa);
        fila1.add(txtMesa);
        fila1.add(lblUser);
        fila1.add(cbUsuario);

        // Fila 2: Categoría/Tipo, Búsqueda de texto y botones de ejecución
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        fila2.setOpaque(false);

        JLabel lblTipo = new JLabel("Categoría:");
        lblTipo.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cbTipoAnulacion = new JComboBox<>(FiltroReporteAnulados.TipoFiltroAnulacion.values());
        cbTipoAnulacion.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cbTipoAnulacion.setToolTipText("Filtra entre cuentas que se fugaron/abandonaron y facturas cobradas formalmente revertidas");
        cbTipoAnulacion.addActionListener(e -> ejecutarConsultaFiltros());

        JLabel lblBuscar = new JLabel("Buscar:");
        lblBuscar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        txtBuscarTexto = new JTextField(14);
        txtBuscarTexto.setToolTipText("Búsqueda libre por motivo, cliente o N° comprobante");
        txtBuscarTexto.addActionListener(e -> ejecutarConsultaFiltros());

        JButton btnFiltrar = new JButton("🔍 Aplicar Filtros");
        btnFiltrar.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnFiltrar.setBackground(COLOR_PRIMARIO);
        btnFiltrar.setForeground(Color.WHITE);
        btnFiltrar.setOpaque(true);
        btnFiltrar.setContentAreaFilled(true);
        btnFiltrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnFiltrar.setToolTipText("Ejecutar consulta con los criterios seleccionados");
        btnFiltrar.addActionListener(e -> ejecutarConsultaFiltros());

        JButton btnLimpiar = new JButton("🔄 Limpiar");
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLimpiar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLimpiar.setToolTipText("Restablecer todos los filtros a sus valores predeterminados");
        btnLimpiar.addActionListener(e -> {
            txtFechaDesde.setText("");
            txtFechaHasta.setText("");
            txtMesa.setText("");
            cbUsuario.setSelectedIndex(0);
            cbTipoAnulacion.setSelectedIndex(0);
            txtBuscarTexto.setText("");
            ejecutarConsultaFiltros();
        });

        fila2.add(lblTipo);
        fila2.add(cbTipoAnulacion);
        fila2.add(lblBuscar);
        fila2.add(txtBuscarTexto);
        fila2.add(btnFiltrar);
        fila2.add(btnLimpiar);

        pnlFiltros.add(fila1);
        pnlFiltros.add(fila2);
        return pnlFiltros;
    }

    private JPanel crearPanelKpis() {
        JPanel pnl = new JPanel(new GridLayout(1, 4, 10, 0));
        pnl.setOpaque(false);

        lblKpiTotalTickets = new JLabel("0 tickets", SwingConstants.CENTER);
        lblKpiTotalUsd = new JLabel("$ 0.00", SwingConstants.CENTER);
        lblKpiTotalBs = new JLabel("Bs. 0.00", SwingConstants.CENTER);
        lblKpiDesglose = new JLabel("0 Fugas / 0 Facturas", SwingConstants.CENTER);

        pnl.add(crearCardKpi("TOTAL ANULACIONES", lblKpiTotalTickets, COLOR_PELIGRO));
        pnl.add(crearCardKpi("MONTO ANULADO (USD)", lblKpiTotalUsd, COLOR_PELIGRO));
        pnl.add(crearCardKpi("MONTO ANULADO (VES)", lblKpiTotalBs, COLOR_ADVERTENCIA));
        pnl.add(crearCardKpi("DESGLOSE OPERATIVO", lblKpiDesglose, COLOR_PRIMARIO));

        return pnl;
    }

    private JPanel crearCardKpi(String titulo, JLabel lblValor, Color colorAcento) {
        JPanel c = new JPanel(new BorderLayout(0, 3));
        c.setBackground(new Color(254, 242, 242));
        c.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(252, 165, 165), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JLabel lblTit = new JLabel(titulo, SwingConstants.CENTER);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTit.setForeground(COLOR_PRIMARIO);

        lblValor.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblValor.setForeground(colorAcento);

        c.add(lblTit, BorderLayout.NORTH);
        c.add(lblValor, BorderLayout.CENTER);
        return c;
    }

    private JScrollPane crearPanelTabla() {
        String[] columnas = {
                "N° Ticket", "Fecha Anulación", "Ubicación / Mesa", "Atendido Por",
                "Anulado Por", "Motivo Formal Registrado", "Total USD", "Total Bs", "Tipo de Anulación"
        };

        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblResultados = new JTable(modeloTabla);
        tblResultados.setRowHeight(25);
        tblResultados.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblResultados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sorter = new TableRowSorter<>(modeloTabla);
        tblResultados.setRowSorter(sorter);

        // Anchos preferidos
        tblResultados.getColumnModel().getColumn(0).setPreferredWidth(80);
        tblResultados.getColumnModel().getColumn(1).setPreferredWidth(130);
        tblResultados.getColumnModel().getColumn(2).setPreferredWidth(130);
        tblResultados.getColumnModel().getColumn(3).setPreferredWidth(100);
        tblResultados.getColumnModel().getColumn(4).setPreferredWidth(110);
        tblResultados.getColumnModel().getColumn(5).setPreferredWidth(250);
        tblResultados.getColumnModel().getColumn(6).setPreferredWidth(85);
        tblResultados.getColumnModel().getColumn(7).setPreferredWidth(95);
        tblResultados.getColumnModel().getColumn(8).setPreferredWidth(120);

        // Renderers estilizados
        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(SwingConstants.CENTER);
        tblResultados.getColumnModel().getColumn(0).setCellRenderer(centerRender);
        tblResultados.getColumnModel().getColumn(1).setCellRenderer(centerRender);

        DefaultTableCellRenderer rightRender = new DefaultTableCellRenderer();
        rightRender.setHorizontalAlignment(SwingConstants.RIGHT);
        tblResultados.getColumnModel().getColumn(6).setCellRenderer(rightRender);
        tblResultados.getColumnModel().getColumn(7).setCellRenderer(rightRender);

        tblResultados.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(new Font("Segoe UI", Font.BOLD, 11));
                String v = value != null ? value.toString() : "";
                if (v.contains("Factura")) {
                    setForeground(COLOR_EXITO);
                } else {
                    setForeground(COLOR_PELIGRO);
                }
                return comp;
            }
        });

        JScrollPane scroll = new JScrollPane(tblResultados);
        scroll.setBorder(new LineBorder(COLOR_BORDE, 1));
        return scroll;
    }

    private JPanel crearPanelBotonera() {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setOpaque(false);

        JPanel pnlIzq = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pnlIzq.setOpaque(false);

        btnImprimir = new JButton("🖨️ Imprimir Reporte");
        btnImprimir.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnImprimir.setBackground(COLOR_PRIMARIO);
        btnImprimir.setForeground(Color.WHITE);
        btnImprimir.setOpaque(true);
        btnImprimir.setContentAreaFilled(true);
        btnImprimir.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnImprimir.setToolTipText("<html><body style='width: 250px; padding: 2px;'>"
                + "<b style='color:#0284c7;'>Imprimir a la Impresora</b><br>"
                + "Envía el reporte completo directamente a la cola de impresión predeterminada o tickera."
                + "</body></html>");
        btnImprimir.addActionListener(e -> imprimirReporteAsync());

        btnExportarPdf = new JButton("📄 Guardar / Ver PDF");
        btnExportarPdf.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExportarPdf.setBackground(new Color(14, 165, 233));
        btnExportarPdf.setForeground(Color.WHITE);
        btnExportarPdf.setOpaque(true);
        btnExportarPdf.setContentAreaFilled(true);
        btnExportarPdf.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnExportarPdf.setToolTipText("<html><body style='width: 250px; padding: 2px;'>"
                + "<b style='color:#0284c7;'>Exportar a Documento PDF</b><br>"
                + "Genera un archivo PDF formal con formato A4 apaisado y abre el visor del sistema."
                + "</body></html>");
        btnExportarPdf.addActionListener(e -> exportarPdfAsync());

        btnVerHtml = new JButton("🌐 Ver en Navegador (HTML)");
        btnVerHtml.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnVerHtml.setBackground(new Color(139, 92, 246));
        btnVerHtml.setForeground(Color.WHITE);
        btnVerHtml.setOpaque(true);
        btnVerHtml.setContentAreaFilled(true);
        btnVerHtml.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnVerHtml.setToolTipText("<html><body style='width: 250px; padding: 2px;'>"
                + "<b style='color:#8b5cf6;'>Reporte Interactivo HTML</b><br>"
                + "Abre el reporte en su navegador web con estilos modernos, tablas interactivas y opción de imprimir."
                + "</body></html>");
        btnVerHtml.addActionListener(e -> exportarHtmlAsync());

        pnlIzq.add(btnImprimir);
        pnlIzq.add(btnExportarPdf);
        pnlIzq.add(btnVerHtml);

        JPanel pnlDer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pnlDer.setOpaque(false);

        btnCerrar = new JButton("Cerrar (ESC)");
        btnCerrar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCerrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCerrar.addActionListener(e -> dispose());

        pnlDer.add(btnCerrar);

        pnl.add(pnlIzq, BorderLayout.WEST);
        pnl.add(pnlDer, BorderLayout.EAST);
        return pnl;
    }

    private void aplicarFiltroHoy() {
        LocalDate hoy = LocalDate.now();
        txtFechaDesde.setText(hoy.toString());
        txtFechaHasta.setText(hoy.toString());
        ejecutarConsultaFiltros();
    }

    private void aplicarFiltroMesActual() {
        LocalDate hoy = LocalDate.now();
        txtFechaDesde.setText(hoy.withDayOfMonth(1).toString());
        txtFechaHasta.setText(hoy.withDayOfMonth(hoy.lengthOfMonth()).toString());
        ejecutarConsultaFiltros();
    }

    private FiltroReporteAnulados construirFiltroDesdeUi() {
        FiltroReporteAnulados f = new FiltroReporteAnulados();

        String desdeStr = txtFechaDesde.getText().trim();
        if (!desdeStr.isEmpty()) {
            try {
                f.setFechaDesde(LocalDate.parse(desdeStr));
            } catch (Exception ignored) {}
        }

        String hastaStr = txtFechaHasta.getText().trim();
        if (!hastaStr.isEmpty()) {
            try {
                f.setFechaHasta(LocalDate.parse(hastaStr));
            } catch (Exception ignored) {}
        }

        String mesaStr = txtMesa.getText().trim().replaceAll("(?i)[^0-9]", "");
        if (!mesaStr.isEmpty()) {
            try {
                f.setNumMesa(Integer.parseInt(mesaStr));
            } catch (Exception ignored) {}
        } else if (txtMesa.getText().trim().equalsIgnoreCase("rapido") || txtMesa.getText().trim().equalsIgnoreCase("barra")) {
            f.setNumMesa(0);
        }

        if (cbUsuario.getSelectedIndex() > 0) {
            f.setUsuario(cbUsuario.getSelectedItem().toString());
        }

        f.setTipo((FiltroReporteAnulados.TipoFiltroAnulacion) cbTipoAnulacion.getSelectedItem());

        String buscar = txtBuscarTexto.getText().trim();
        if (!buscar.isEmpty()) {
            f.setTextoBusqueda(buscar);
        }

        return f;
    }

    private void ejecutarConsultaFiltros() {
        FiltroReporteAnulados filtro = construirFiltroDesdeUi();

        new SwingWorker<List<TicketAnuladoReporte>, Void>() {
            @Override
            protected List<TicketAnuladoReporte> doInBackground() {
                return auditoriaDao.consultarReporteTicketsAnulados(filtro);
            }

            @Override
            protected void done() {
                try {
                    ticketsActuales = get();
                    actualizarTabla(ticketsActuales);
                    actualizarKpis(ticketsActuales);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error al cargar reporte de anulados: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                            "Error al consultar comprobantes anulados: " + ex.getMessage(),
                            "Error de Consulta", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void actualizarTabla(List<TicketAnuladoReporte> tickets) {
        modeloTabla.setRowCount(0);
        for (TicketAnuladoReporte t : tickets) {
            modeloTabla.addRow(new Object[]{
                    String.format(java.util.Locale.US, "#%06d", t.getIdPedido()),
                    t.getFechaAnulacionFormateada(),
                    t.getUbicacionFormateada(),
                    t.getAtendidoPor(),
                    t.getAnuladoPor(),
                    t.getMotivo(),
                    String.format(java.util.Locale.US, "$ %.2f", t.getTotalUsd()),
                    String.format(java.util.Locale.US, "Bs. %.2f", t.getTotalBs()),
                    t.isEraFacturaCobrada() ? "Factura Cobrada" : "Cuenta Pendiente"
            });
        }
    }

    private void actualizarKpis(List<TicketAnuladoReporte> tickets) {
        int total = tickets.size();
        BigDecimal totalUsd = BigDecimal.ZERO;
        BigDecimal totalBs = BigDecimal.ZERO;
        int cantFugas = 0;
        int cantFacturas = 0;

        for (TicketAnuladoReporte t : tickets) {
            totalUsd = totalUsd.add(t.getTotalUsd());
            totalBs = totalBs.add(t.getTotalBs());
            if (t.isEraFacturaCobrada()) cantFacturas++;
            else cantFugas++;
        }

        lblKpiTotalTickets.setText(total + " tickets");
        lblKpiTotalUsd.setText(String.format(java.util.Locale.US, "$ %.2f", totalUsd));
        lblKpiTotalBs.setText(String.format(java.util.Locale.US, "Bs. %.2f", totalBs));
        lblKpiDesglose.setText(cantFugas + " Fugas / " + cantFacturas + " Facturas");
    }

    private void cargarUsuariosDisponiblesAsync() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return auditoriaDao.listarUsuariosInvolucrados();
            }

            @Override
            protected void done() {
                try {
                    List<String> usuarios = get();
                    for (String u : usuarios) {
                        cbUsuario.addItem(u);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "No se pudieron precargar los usuarios: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void exportarPdfAsync() {
        if (ticketsActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay tickets anulados para exportar con los filtros actuales.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FiltroReporteAnulados filtro = construirFiltroDesdeUi();
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() {
                GeneradorPdfReporteAnulados gen = new GeneradorPdfReporteAnulados();
                return gen.generar(ticketsActuales, filtro, configEmpresa, usuarioActual);
            }

            @Override
            protected void done() {
                try {
                    Path pdf = get();
                    ServicioImpresionTicket.abrirVisor(pdf);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Fallo al generar PDF de anulaciones: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                            "No se pudo generar el documento PDF: " + ex.getMessage(),
                            "Error de Exportación", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void exportarHtmlAsync() {
        if (ticketsActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay tickets anulados para mostrar con los filtros actuales.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FiltroReporteAnulados filtro = construirFiltroDesdeUi();
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() {
                GeneradorHtmlReporteAnulados gen = new GeneradorHtmlReporteAnulados();
                return gen.generar(ticketsActuales, filtro, configEmpresa, usuarioActual);
            }

            @Override
            protected void done() {
                try {
                    Path html = get();
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(html.toUri());
                    } else {
                        JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                                "Reporte HTML generado exitosamente en:\n" + html.toAbsolutePath(),
                                "Reporte Generado", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Fallo al generar HTML de anulaciones: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                            "No se pudo generar el reporte HTML: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void imprimirReporteAsync() {
        if (ticketsActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay tickets anulados para imprimir con los filtros actuales.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Desea enviar el reporte de tickets anulados a la impresora configurada?",
                "Confirmación de Impresión", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        FiltroReporteAnulados filtro = construirFiltroDesdeUi();
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                GeneradorPdfReporteAnulados gen = new GeneradorPdfReporteAnulados();
                Path pdf = gen.generar(ticketsActuales, filtro, configEmpresa, usuarioActual);
                return ServicioImpresionTicket.imprimirDirectoJavaSpooler(pdf, null);
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                                "El reporte ha sido enviado exitosamente a la cola de impresión.",
                                "Impresión Exitosa", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                                "No se pudo imprimir directamente. Puede usar 'Guardar / Ver PDF' para imprimir desde el visor.",
                                "Aviso de Impresión", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error en impresión directa: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(DialogoReporteAnulados.this,
                            "Error al enviar a la impresora: " + ex.getMessage(),
                            "Error de Impresión", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
