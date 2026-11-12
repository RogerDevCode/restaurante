package Servicio;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroReporteAnulados;
import Modelo.TicketAnuladoReporte;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generador de reportes PDF para la auditoría de tickets y comprobantes anulados.
 * Diseñado en formato apaisado (A4 horizontal) para máxima legibilidad de columnas.
 */
public final class GeneradorPdfReporteAnulados {

    private static final Logger LOGGER = Logger.getLogger(GeneradorPdfReporteAnulados.class.getName());

    private static final BaseColor COLOR_PRIMARIO = new BaseColor(30, 41, 59);
    private static final BaseColor COLOR_PELIGRO = new BaseColor(192, 57, 43);
    private static final BaseColor COLOR_GRIS_FONDO = new BaseColor(248, 250, 252);
    private static final BaseColor COLOR_LINEA = new BaseColor(226, 232, 240);
    private static final BaseColor COLOR_ZEBRA = new BaseColor(241, 245, 249);

    private static final Font FONT_TITULO_DOC = new Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, COLOR_PELIGRO);
    private static final Font FONT_SUBTITULO = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.NORMAL, BaseColor.DARK_GRAY);
    private static final Font FONT_EMPRESA = new Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_FILTRO_LABEL = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_FILTRO_VALOR = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_TH = new Font(Font.FontFamily.HELVETICA, 8f, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_TD = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_TD_BOLD = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_KPI_NUM = new Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD, COLOR_PELIGRO);
    private static final Font FONT_KPI_LABEL = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_PIE = new Font(Font.FontFamily.HELVETICA, 7f, Font.ITALIC, BaseColor.GRAY);

    private final Path directorioSalida;

    public GeneradorPdfReporteAnulados() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorPdfReporteAnulados(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida del reporte es obligatorio.");
        }
        this.directorioSalida = directorioSalida;
    }

    private static Path resolverDirectorioPorDefecto() {
        String override = System.getProperty("PDF_FACTURAS_DIR");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("PDF_FACTURAS_DIR");
        }
        if (override != null && !override.trim().isEmpty()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home", "."), "Restaurante", "facturas");
    }

    public Path generar(List<TicketAnuladoReporte> tickets, FiltroReporteAnulados filtro, Config config, String usuarioEmisor) {
        if (tickets == null) {
            throw ErrorAplicacionException.validacion("La lista de tickets anulados no puede ser nula.");
        }

        try {
            Files.createDirectories(directorioSalida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio para el reporte: " + ex.getMessage(), ex);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = String.format("reporte_anulaciones_%s_%s.pdf", timestamp, UUID.randomUUID().toString().substring(0, 8));
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        Document documento = new Document(PageSize.A4.rotate(), 20f, 20f, 24f, 24f);

        try (OutputStream fos = new FileOutputStream(archivoSalida.toFile())) {
            PdfWriter writer = PdfWriter.getInstance(documento, fos);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);

            documento.open();

            // 1. Cabecera Institucional
            agregarCabecera(documento, config, usuarioEmisor);

            // 2. Filtros aplicados
            agregarPanelFiltros(documento, filtro);

            // 3. Resumen Ejecutivo (KPIs)
            agregarResumenKpis(documento, tickets);

            // 4. Tabla Detallada
            agregarTablaDetallada(documento, tickets);

            // 5. Pie de página
            agregarPiePagina(documento);

            documento.close();
            return archivoSalida;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al generar el PDF del reporte de anulaciones: " + ex.getMessage(), ex);
            throw new ErrorAplicacionException("No se pudo generar el archivo PDF del reporte: " + ex.getMessage(), ex);
        }
    }

    private void agregarCabecera(Document doc, Config conf, String usuarioEmisor) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60f, 40f});
        header.setSpacingAfter(8f);

        String nomEmpresa = (conf != null && conf.getNombre() != null && !conf.getNombre().isBlank())
                ? conf.getNombre().trim() : "RESTAURANTE 2026";
        String rifEmpresa = (conf != null && conf.getRif() != null && !conf.getRif().isBlank())
                ? conf.getRif().trim() : "RIF: J-00000000-0";
        String dirEmpresa = (conf != null && conf.getDireccion() != null && !conf.getDireccion().isBlank())
                ? conf.getDireccion().trim() : "";

        PdfPCell celdaIzq = new PdfPCell();
        celdaIzq.setBorder(Rectangle.NO_BORDER);
        celdaIzq.addElement(new Paragraph(nomEmpresa, FONT_EMPRESA));
        celdaIzq.addElement(new Paragraph(rifEmpresa + (dirEmpresa.isEmpty() ? "" : " — " + dirEmpresa), FONT_SUBTITULO));
        header.addCell(celdaIzq);

        PdfPCell celdaDer = new PdfPCell();
        celdaDer.setBorder(Rectangle.NO_BORDER);
        celdaDer.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pTitulo = new Paragraph("AUDITORÍA DE COMPROBANTES ANULADOS", FONT_TITULO_DOC);
        pTitulo.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(pTitulo);

        String fechaGen = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph pMeta = new Paragraph("Emitido: " + fechaGen + " | Operador: " + (usuarioEmisor != null ? usuarioEmisor : "Sistema"), FONT_SUBTITULO);
        pMeta.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(pMeta);
        header.addCell(celdaDer);

        doc.add(header);
    }

    private void agregarPanelFiltros(Document doc, FiltroReporteAnulados f) throws DocumentException {
        PdfPTable tFiltros = new PdfPTable(4);
        tFiltros.setWidthPercentage(100);
        tFiltros.setWidths(new float[]{25f, 25f, 25f, 25f});
        tFiltros.setSpacingAfter(8f);

        String periodo = (f != null) ? f.getDescripcionRangoFechas() : "Histórico General";
        String mesa = (f != null && f.getNumMesa() != null)
                ? (f.getNumMesa() <= 0 ? "Solo Pedidos Rápidos" : "Mesa " + f.getNumMesa())
                : "Todas las Mesas";
        String usuario = (f != null && f.getUsuario() != null && !f.getUsuario().isBlank())
                ? f.getUsuario().trim() : "Todos los Usuarios";
        String tipo = (f != null && f.getTipo() != null) ? f.getTipo().getDescripcion() : "Todas las Anulaciones";

        agregarCeldaFiltro(tFiltros, "Período:", periodo);
        agregarCeldaFiltro(tFiltros, "Filtro Mesa:", mesa);
        agregarCeldaFiltro(tFiltros, "Filtro Usuario:", usuario);
        agregarCeldaFiltro(tFiltros, "Categoría:", tipo);

        doc.add(tFiltros);
    }

    private void agregarCeldaFiltro(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_GRIS_FONDO);
        cell.setBorderColor(COLOR_LINEA);
        cell.setPadding(4f);
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + " ", FONT_FILTRO_LABEL));
        p.add(new Chunk(valor, FONT_FILTRO_VALOR));
        cell.addElement(p);
        tabla.addCell(cell);
    }

    private void agregarResumenKpis(Document doc, List<TicketAnuladoReporte> tickets) throws DocumentException {
        int totalTickets = tickets.size();
        BigDecimal totalUsd = BigDecimal.ZERO;
        BigDecimal totalBs = BigDecimal.ZERO;
        int cantFugas = 0;
        int cantFacturas = 0;

        for (TicketAnuladoReporte t : tickets) {
            totalUsd = totalUsd.add(t.getTotalUsd());
            totalBs = totalBs.add(t.getTotalBs());
            if (t.isEraFacturaCobrada()) {
                cantFacturas++;
            } else {
                cantFugas++;
            }
        }

        PdfPTable tKpis = new PdfPTable(4);
        tKpis.setWidthPercentage(100);
        tKpis.setWidths(new float[]{25f, 25f, 25f, 25f});
        tKpis.setSpacingAfter(10f);

        agregarKpiCard(tKpis, "TOTAL ANULADOS", String.valueOf(totalTickets) + " tickets");
        agregarKpiCard(tKpis, "MONTO TOTAL (USD)", String.format(java.util.Locale.US, "$ %.2f", totalUsd));
        agregarKpiCard(tKpis, "MONTO TOTAL (VES)", String.format(java.util.Locale.US, "Bs. %.2f", totalBs));
        agregarKpiCard(tKpis, "DESGLOSE OPERATIVO", cantFugas + " Fugas / " + cantFacturas + " Facturas");

        doc.add(tKpis);
    }

    private void agregarKpiCard(PdfPTable tabla, String titulo, String valor) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new BaseColor(254, 242, 242));
        c.setBorderColor(new BaseColor(252, 165, 165));
        c.setPadding(6f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pTit = new Paragraph(titulo, FONT_KPI_LABEL);
        pTit.setAlignment(Element.ALIGN_CENTER);
        c.addElement(pTit);

        Paragraph pVal = new Paragraph(valor, FONT_KPI_NUM);
        pVal.setAlignment(Element.ALIGN_CENTER);
        c.addElement(pVal);

        tabla.addCell(c);
    }

    private void agregarTablaDetallada(Document doc, List<TicketAnuladoReporte> tickets) throws DocumentException {
        if (tickets.isEmpty()) {
            Paragraph pVacio = new Paragraph("No se encontraron registros de comprobantes anulados para los filtros seleccionados.", FONT_FILTRO_LABEL);
            pVacio.setAlignment(Element.ALIGN_CENTER);
            pVacio.setSpacingBefore(20f);
            pVacio.setSpacingAfter(20f);
            doc.add(pVacio);
            return;
        }

        // 9 Columnas
        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8f, 13f, 12f, 10f, 10f, 22f, 8f, 9f, 8f});
        table.setHeaderRows(1);
        table.setSpacingAfter(10f);

        // Encabezados
        String[] headers = {
                "Ticket #", "Fecha Anulación", "Ubicación", "Atendido Por", "Anulado Por",
                "Motivo Formal Registrado", "Total USD", "Total Bs", "Tipo"
        };
        for (String h : headers) {
            PdfPCell ch = new PdfPCell(new Phrase(h, FONT_TH));
            ch.setBackgroundColor(COLOR_PRIMARIO);
            ch.setBorderColor(COLOR_PRIMARIO);
            ch.setPadding(5f);
            ch.setHorizontalAlignment(Element.ALIGN_CENTER);
            ch.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(ch);
        }

        // Filas
        boolean zebra = false;
        for (TicketAnuladoReporte t : tickets) {
            BaseColor bg = zebra ? COLOR_ZEBRA : BaseColor.WHITE;
            zebra = !zebra;

            table.addCell(crearCeldaTabla(String.format(java.util.Locale.US, "#%06d", t.getIdPedido()), FONT_TD_BOLD, Element.ALIGN_CENTER, bg));
            table.addCell(crearCeldaTabla(t.getFechaAnulacionFormateada(), FONT_TD, Element.ALIGN_CENTER, bg));
            table.addCell(crearCeldaTabla(t.getUbicacionFormateada(), FONT_TD, Element.ALIGN_LEFT, bg));
            table.addCell(crearCeldaTabla(t.getAtendidoPor(), FONT_TD, Element.ALIGN_LEFT, bg));
            table.addCell(crearCeldaTabla(t.getAnuladoPor(), FONT_TD_BOLD, Element.ALIGN_LEFT, bg));
            table.addCell(crearCeldaTabla(t.getMotivo(), FONT_TD, Element.ALIGN_LEFT, bg));
            table.addCell(crearCeldaTabla(String.format(java.util.Locale.US, "$ %.2f", t.getTotalUsd()), FONT_TD_BOLD, Element.ALIGN_RIGHT, bg));
            table.addCell(crearCeldaTabla(String.format(java.util.Locale.US, "Bs. %.2f", t.getTotalBs()), FONT_TD, Element.ALIGN_RIGHT, bg));

            String tipoStr = t.isEraFacturaCobrada() ? "Factura" : "Fuga/Mesa";
            Font fTipo = t.isEraFacturaCobrada()
                    ? new Font(Font.FontFamily.HELVETICA, 7f, Font.BOLD, new BaseColor(16, 185, 129))
                    : new Font(Font.FontFamily.HELVETICA, 7f, Font.BOLD, COLOR_PELIGRO);
            table.addCell(crearCeldaTabla(tipoStr, fTipo, Element.ALIGN_CENTER, bg));
        }

        doc.add(table);
    }

    private PdfPCell crearCeldaTabla(String texto, Font font, int alineacion, BaseColor fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        c.setBackgroundColor(fondo);
        c.setBorderColor(COLOR_LINEA);
        c.setPadding(4.5f);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private void agregarPiePagina(Document doc) throws DocumentException {
        Paragraph pie = new Paragraph(
                "Documento confidencial emitido exclusivamente para fines de control administrativo y auditoría fiscal interna — Sistema Restaurante 2026",
                FONT_PIE);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setSpacingBefore(8f);
        doc.add(pie);
    }
}
