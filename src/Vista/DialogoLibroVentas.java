package Vista;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroLibroVentas;
import Modelo.RegistroLibroVenta;
import Modelo.ResumenFiscalVentas;
import Servicio.GeneradorExcelLibroVentas;
import Servicio.GeneradorHtmlLibroVentas;
import Servicio.GeneradorPdfLibroVentas;
import Servicio.ServicioLibroVentas;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo modal para la consulta, visualización, auditoría y exportación del
 * Libro de Ventas Fiscal conforme a la normativa del SENIAT (Venezuela).
 * Totalmente seguro para el Event Dispatch Thread (EDT) mediante procesamiento asíncrono con SwingWorker.
 */
public class DialogoLibroVentas extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(DialogoLibroVentas.class.getName());

    private final ServicioLibroVentas servicioLibroVentas;
    private final GeneradorHtmlLibroVentas generadorHtml;
    private final GeneradorExcelLibroVentas generadorExcel;
    private final GeneradorPdfLibroVentas generadorPdf;
    private final Config configEmpresa;
    private final String usuarioActual;

    private JComboBox<Integer> cbAno;
    private JComboBox<String> cbMes;
    private JRadioButton rbMesCompleto;
    private JRadioButton rbQuincena1;
    private JRadioButton rbQuincena2;
    private JButton btnConsultar;

    private JLabel lblKpiOperaciones;
    private JLabel lblKpiTotalBs;
    private JLabel lblKpiBaseBs;
    private JLabel lblKpiIvaBs;
    private JLabel lblKpiIgtfBs;

    private JTable tablaLibro;
    private DefaultTableModel modeloTabla;
    private JButton btnVerHtml;
    private JButton btnExportarExcel;
    private JButton btnGenerarPdf;
    private JButton btnCerrar;
    private JProgressBar progressBar;

    private List<RegistroLibroVenta> registrosActuales = new ArrayList<>();
    private ResumenFiscalVentas resumenActual;

    public DialogoLibroVentas(Frame owner, Config configEmpresa, String usuarioActual) {
        super(owner, "Libro de Ventas Fiscal (Normativa SENIAT) · Restaurante 2026", true);
        this.configEmpresa = configEmpresa;
        this.usuarioActual = (usuarioActual != null && !usuarioActual.isBlank()) ? usuarioActual.trim() : "Sistema";

        this.servicioLibroVentas = new ServicioLibroVentas();
        this.generadorHtml = new GeneradorHtmlLibroVentas();
        this.generadorExcel = new GeneradorExcelLibroVentas();
        this.generadorPdf = new GeneradorPdfLibroVentas();

        inicializarComponentes();
        cargarDatosAsincronos();
    }

    public DialogoLibroVentas(Dialog owner, Config configEmpresa, String usuarioActual) {
        super(owner, "Libro de Ventas Fiscal (Normativa SENIAT) · Restaurante 2026", true);
        this.configEmpresa = configEmpresa;
        this.usuarioActual = (usuarioActual != null && !usuarioActual.isBlank()) ? usuarioActual.trim() : "Sistema";

        this.servicioLibroVentas = new ServicioLibroVentas();
        this.generadorHtml = new GeneradorHtmlLibroVentas();
        this.generadorExcel = new GeneradorExcelLibroVentas();
        this.generadorPdf = new GeneradorPdfLibroVentas();

        inicializarComponentes();
        cargarDatosAsincronos();
    }

    private void inicializarComponentes() {
        setSize(1180, 720);
        setMinimumSize(new Dimension(980, 580));
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(0, 8));

        // 1. PANEL SUPERIOR: FILTROS Y CONTROLES
        JPanel pnlNorte = new JPanel(new BorderLayout(8, 8));
        pnlNorte.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));

        JPanel pnlFiltros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        pnlFiltros.setBorder(BorderFactory.createTitledBorder("Período Fiscal SENIAT"));

        pnlFiltros.add(new JLabel("Año:"));
        cbAno = new JComboBox<>();
        int anoActual = LocalDate.now().getYear();
        for (int a = anoActual - 2; a <= anoActual + 2; a++) {
            cbAno.addItem(a);
        }
        cbAno.setSelectedItem(anoActual);
        pnlFiltros.add(cbAno);

        pnlFiltros.add(new JLabel("Mes:"));
        String[] meses = {
                "01 - Enero", "02 - Febrero", "03 - Marzo", "04 - Abril",
                "05 - Mayo", "06 - Junio", "07 - Julio", "08 - Agosto",
                "09 - Septiembre", "10 - Octubre", "11 - Noviembre", "12 - Diciembre"
        };
        cbMes = new JComboBox<>(meses);
        cbMes.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        pnlFiltros.add(cbMes);

        rbMesCompleto = new JRadioButton("Mes Completo", true);
        rbQuincena1 = new JRadioButton("1ra Quincena (1-15)");
        rbQuincena2 = new JRadioButton("2da Quincena (16-fin)");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbMesCompleto);
        bg.add(rbQuincena1);
        bg.add(rbQuincena2);

        pnlFiltros.add(Box.createHorizontalStrut(8));
        pnlFiltros.add(rbMesCompleto);
        pnlFiltros.add(rbQuincena1);
        pnlFiltros.add(rbQuincena2);

        btnConsultar = new JButton("🔍 Consultar");
        btnConsultar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConsultar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConsultar.addActionListener(e -> cargarDatosAsincronos());
        pnlFiltros.add(Box.createHorizontalStrut(10));
        pnlFiltros.add(btnConsultar);

        pnlNorte.add(pnlFiltros, BorderLayout.NORTH);

        // 2. PANEL DE KPIS TRIBUTARIOS
        JPanel pnlKpis = new JPanel(new GridLayout(1, 5, 8, 0));
        pnlKpis.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        lblKpiOperaciones = crearTarjetaKpi("Facturas / Ops", "0", new Color(30, 41, 59));
        lblKpiTotalBs = crearTarjetaKpi("Total Ventas (Bs.)", "0,00", new Color(2, 132, 199));
        lblKpiBaseBs = crearTarjetaKpi("Base Imponible 16%", "0,00", new Color(15, 118, 110));
        lblKpiIvaBs = crearTarjetaKpi("Débito IVA 16% (Bs.)", "0,00", new Color(180, 83, 9));
        lblKpiIgtfBs = crearTarjetaKpi("IGTF 3% Percibido (Bs.)", "0,00", new Color(134, 25, 143));

        pnlKpis.add(lblKpiOperaciones.getParent());
        pnlKpis.add(lblKpiTotalBs.getParent());
        pnlKpis.add(lblKpiBaseBs.getParent());
        pnlKpis.add(lblKpiIvaBs.getParent());
        pnlKpis.add(lblKpiIgtfBs.getParent());

        pnlNorte.add(pnlKpis, BorderLayout.SOUTH);
        add(pnlNorte, BorderLayout.NORTH);

        // 3. TABLA CENTRAL
        String[] columnas = {
                "Op", "Fecha", "RIF/C.I.", "Nombre Cliente / Razón Social", "N° Factura",
                "N° Control", "N° N/C", "Fact Afect", "Tipo", "Total Bs.",
                "Exento Bs.", "Base Imp. (Bs.)", "%", "IVA 16% (Bs.)", "IVA Ret. (Bs.)", "IGTF 3% (Bs.)"
        };

        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tablaLibro = new JTable(modeloTabla);
        tablaLibro.setRowHeight(22);
        tablaLibro.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tablaLibro.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tablaLibro.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Configuración de anchos de columna
        int[] anchos = {40, 75, 95, 180, 75, 75, 75, 75, 60, 95, 85, 95, 45, 95, 85, 85};
        for (int i = 0; i < anchos.length && i < tablaLibro.getColumnCount(); i++) {
            tablaLibro.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }

        // Renderers
        DefaultTableCellRenderer renderCentro = new DefaultTableCellRenderer();
        renderCentro.setHorizontalAlignment(SwingConstants.CENTER);

        DefaultTableCellRenderer renderDerecha = new DefaultTableCellRenderer();
        renderDerecha.setHorizontalAlignment(SwingConstants.RIGHT);

        tablaLibro.getColumnModel().getColumn(0).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(1).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(4).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(5).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(6).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(7).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(8).setCellRenderer(renderCentro);
        tablaLibro.getColumnModel().getColumn(12).setCellRenderer(renderCentro);

        for (int c : new int[]{9, 10, 11, 13, 14, 15}) {
            tablaLibro.getColumnModel().getColumn(c).setCellRenderer(renderDerecha);
        }

        JScrollPane scrollTabla = new JScrollPane(tablaLibro);
        scrollTabla.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        add(scrollTabla, BorderLayout.CENTER);

        // 4. PANEL INFERIOR: BOTONES DE EXPORTACIÓN Y ESTADO
        JPanel pnlSur = new JPanel(new BorderLayout(8, 8));
        pnlSur.setBorder(BorderFactory.createEmptyBorder(6, 14, 12, 14));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        pnlSur.add(progressBar, BorderLayout.NORTH);

        JPanel pnlAcciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        btnVerHtml = new JButton("🌐 Ver en Navegador (HTML)");
        btnVerHtml.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnVerHtml.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnVerHtml.addActionListener(e -> exportarHtml());

        btnExportarExcel = new JButton("📊 Exportar Excel / CSV");
        btnExportarExcel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnExportarExcel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnExportarExcel.addActionListener(e -> exportarExcel());

        btnGenerarPdf = new JButton("📄 Generar PDF Legal");
        btnGenerarPdf.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnGenerarPdf.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnGenerarPdf.addActionListener(e -> exportarPdf());

        btnCerrar = new JButton("Cerrar");
        btnCerrar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnCerrar.addActionListener(e -> dispose());

        pnlAcciones.add(btnVerHtml);
        pnlAcciones.add(btnExportarExcel);
        pnlAcciones.add(btnGenerarPdf);
        pnlAcciones.add(Box.createHorizontalStrut(10));
        pnlAcciones.add(btnCerrar);

        pnlSur.add(pnlAcciones, BorderLayout.SOUTH);
        add(pnlSur, BorderLayout.SOUTH);
    }

    private JLabel crearTarjetaKpi(String titulo, String valorInicial, Color colorBorde) {
        JPanel pnl = new JPanel(new BorderLayout(2, 2));
        pnl.setBackground(new Color(248, 250, 252));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colorBorde, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JLabel lblTit = new JLabel(titulo);
        lblTit.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTit.setForeground(new Color(100, 116, 139));

        JLabel lblVal = new JLabel(valorInicial, SwingConstants.RIGHT);
        lblVal.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblVal.setForeground(colorBorde);

        pnl.add(lblTit, BorderLayout.NORTH);
        pnl.add(lblVal, BorderLayout.CENTER);
        return lblVal;
    }

    private FiltroLibroVentas construirFiltroDesdeUi() {
        int ano = (Integer) cbAno.getSelectedItem();
        int mes = cbMes.getSelectedIndex() + 1;
        FiltroLibroVentas.TipoPeriodo tipo = FiltroLibroVentas.TipoPeriodo.MES_COMPLETO;
        if (rbQuincena1.isSelected()) {
            tipo = FiltroLibroVentas.TipoPeriodo.PRIMERA_QUINCENA;
        } else if (rbQuincena2.isSelected()) {
            tipo = FiltroLibroVentas.TipoPeriodo.SEGUNDA_QUINCENA;
        }
        return new FiltroLibroVentas(ano, mes, tipo);
    }

    private void cargarDatosAsincronos() {
        FiltroLibroVentas filtro = construirFiltroDesdeUi();
        habilitarControles(false);
        progressBar.setVisible(true);

        SwingWorker<List<RegistroLibroVenta>, Void> worker = new SwingWorker<>() {
            private ResumenFiscalVentas resumenCalculado;

            @Override
            protected List<RegistroLibroVenta> doInBackground() {
                List<RegistroLibroVenta> registros = servicioLibroVentas.consultarLibro(filtro);
                resumenCalculado = servicioLibroVentas.calcularResumen(registros);
                return registros;
            }

            @Override
            protected void done() {
                try {
                    registrosActuales = get();
                    resumenActual = resumenCalculado;
                    poblarTabla(registrosActuales, resumenActual);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error al consultar libro de ventas: " + ex.getMessage(), ex);
                    JOptionPane.showMessageDialog(DialogoLibroVentas.this,
                            "Error al consultar libro de ventas fiscal: " + ex.getMessage(),
                            "Error Fiscal", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setVisible(false);
                    habilitarControles(true);
                }
            }
        };

        worker.execute();
    }

    private void poblarTabla(List<RegistroLibroVenta> registros, ResumenFiscalVentas resumen) {
        modeloTabla.setRowCount(0);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (RegistroLibroVenta r : registros) {
            modeloTabla.addRow(new Object[]{
                    r.getNumOperacion(),
                    r.getFechaOperacion().format(df),
                    r.getClienteDocumento(),
                    r.getClienteNombre(),
                    r.getNroFactura(),
                    r.getNroControl(),
                    r.getNroNotaCredito(),
                    r.getFacturaAfectada(),
                    r.getTipoTransaccion(),
                    formatearBs(r.getTotalVentasBs()),
                    formatearBs(r.getVentasExentasBs()),
                    formatearBs(r.getBaseImponibleBs()),
                    r.getAlicuotaPorcentaje().setScale(0).toString() + "%",
                    formatearBs(r.getIvaBs()),
                    formatearBs(r.getIvaRetenidoBs()),
                    formatearBs(r.getIgtfPercibidoBs())
            });
        }

        if (resumen != null) {
            lblKpiOperaciones.setText(String.format("%d (Anuladas: %d)", resumen.totalOperaciones(), resumen.totalFacturasAnuladas()));
            lblKpiTotalBs.setText(formatearBs(resumen.totalVentasConIvaBs()));
            lblKpiBaseBs.setText(formatearBs(resumen.totalBaseImponibleGeneralBs()));
            lblKpiIvaBs.setText(formatearBs(resumen.totalDebitoFiscalGeneralBs()));
            lblKpiIgtfBs.setText(formatearBs(resumen.totalIgtfPercibidoBs()));
        }

        // Alerta de saltos correlativos si hubiese
        List<Integer> saltos = servicioLibroVentas.detectarSaltosCorrelativo(registros);
        if (!saltos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Atención Tributaria: Se detectaron " + saltos.size() + " saltos en el correlativo de facturas:\n"
                            + saltos.stream().limit(10).map(String::valueOf).toList()
                            + (saltos.size() > 10 ? "... y más" : "")
                            + "\nVerifique los números faltantes antes del cierre fiscal.",
                    "Aviso de Correlatividad SENIAT", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void exportarHtml() {
        if (registrosActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay registros para exportar en el período seleccionado.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            FiltroLibroVentas filtro = construirFiltroDesdeUi();
            Path ruta = generadorHtml.generar(registrosActuales, resumenActual, filtro, configEmpresa, usuarioActual);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(ruta.toUri());
            } else {
                JOptionPane.showMessageDialog(this, "Libro de Ventas generado con éxito en:\n" + ruta.toAbsolutePath(), "Exportación Exitosa", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al exportar HTML del libro de ventas: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error al generar vista HTML: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarExcel() {
        if (registrosActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay registros para exportar en el período seleccionado.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            FiltroLibroVentas filtro = construirFiltroDesdeUi();
            Path ruta = generadorExcel.generar(registrosActuales, resumenActual, filtro, configEmpresa);

            int opcion = JOptionPane.showConfirmDialog(this,
                    "Archivo CSV/Excel generado exitosamente en:\n" + ruta.toAbsolutePath() + "\n\n¿Desea abrir el archivo ahora?",
                    "Exportación a Excel / CSV", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

            if (opcion == JOptionPane.YES_OPTION && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(ruta.toFile());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al exportar CSV del libro de ventas: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error al exportar a Excel/CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarPdf() {
        if (registrosActuales.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay registros para exportar en el período seleccionado.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            FiltroLibroVentas filtro = construirFiltroDesdeUi();
            Path ruta = generadorPdf.generar(registrosActuales, resumenActual, filtro, configEmpresa, usuarioActual);

            int opcion = JOptionPane.showConfirmDialog(this,
                    "PDF Oficial del Libro de Ventas generado exitosamente en:\n" + ruta.toAbsolutePath() + "\n\n¿Desea abrir el PDF ahora?",
                    "PDF Legal SENIAT", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

            if (opcion == JOptionPane.YES_OPTION && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(ruta.toFile());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al exportar PDF del libro de ventas: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error al generar PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void habilitarControles(boolean habilitado) {
        btnConsultar.setEnabled(habilitado);
        btnVerHtml.setEnabled(habilitado);
        btnExportarExcel.setEnabled(habilitado);
        btnGenerarPdf.setEnabled(habilitado);
    }

    private static String formatearBs(BigDecimal monto) {
        if (monto == null) return "0,00";
        return String.format(java.util.Locale.GERMANY, "%,.2f", monto);
    }
}
