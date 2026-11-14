package Servicio;

import Modelo.Config;
import Modelo.ErrorAplicacionException;
import Modelo.FiltroLibroVentas;
import Modelo.RegistroLibroVenta;
import Modelo.ResumenFiscalVentas;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generador de Libro de Ventas Fiscal oficial en formato PDF legal apaisado (A4 Landscape)
 * cumpliendo con la diagramación del SENIAT y la Ley del IVA.
 */
public final class GeneradorPdfLibroVentas {

    private static final Logger LOGGER = Logger.getLogger(GeneradorPdfLibroVentas.class.getName());

    private static final BaseColor COLOR_PRIMARIO = new BaseColor(15, 23, 42);
    private static final BaseColor COLOR_AZUL_FISCAL = new BaseColor(2, 132, 199);
    private static final BaseColor COLOR_ANULADA = new BaseColor(254, 226, 226);
    private static final BaseColor COLOR_ANULADA_TEXTO = new BaseColor(153, 27, 27);
    private static final BaseColor COLOR_ZEBRA = new BaseColor(248, 250, 252);
    private static final BaseColor COLOR_LINEA = new BaseColor(203, 213, 225);

    private static final Font FONT_TITULO = new Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD, COLOR_AZUL_FISCAL);
    private static final Font FONT_EMPRESA = new Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_SUBTITULO = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.DARK_GRAY);
    private static final Font FONT_PERIODO = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, COLOR_AZUL_FISCAL);
    private static final Font FONT_TH = new Font(Font.FontFamily.HELVETICA, 6.5f, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_TD = new Font(Font.FontFamily.HELVETICA, 6.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_TD_NUM = new Font(Font.FontFamily.COURIER, 6.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_TD_ANU = new Font(Font.FontFamily.HELVETICA, 6.5f, Font.BOLD, COLOR_ANULADA_TEXTO);
    private static final Font FONT_TOTALES = new Font(Font.FontFamily.HELVETICA, 7f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_TOTALES_NUM = new Font(Font.FontFamily.COURIER, 7f, Font.BOLD, COLOR_PRIMARIO);
    private static final Font FONT_RESUMEN_TITULO = new Font(Font.FontFamily.HELVETICA, 8f, Font.BOLD, COLOR_AZUL_FISCAL);
    private static final Font FONT_RESUMEN_TEXTO = new Font(Font.FontFamily.HELVETICA, 7f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_PIE = new Font(Font.FontFamily.HELVETICA, 6f, Font.ITALIC, BaseColor.GRAY);

    private final Path directorioSalida;

    public GeneradorPdfLibroVentas() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorPdfLibroVentas(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida no puede ser nulo.");
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

    public Path generar(List<RegistroLibroVenta> registros, ResumenFiscalVentas resumen, FiltroLibroVentas filtro, Config config, String usuarioEmisor) {
        if (registros == null) {
            throw ErrorAplicacionException.validacion("La lista de registros no puede ser nula.");
        }
        if (resumen == null) {
            resumen = ResumenFiscalVentas.desdeRegistros(registros);
        }

        try {
            Files.createDirectories(directorioSalida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio para el libro de ventas: " + ex.getMessage(), ex);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = String.format("libro_ventas_seniat_%d_%02d_%s.pdf", filtro.getAno(), filtro.getMes(), UUID.randomUUID().toString().substring(0, 8));
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        Document documento = new Document(PageSize.A4.rotate(), 14f, 14f, 18f, 18f);

        try (OutputStream fos = new FileOutputStream(archivoSalida.toFile())) {
            PdfWriter writer = PdfWriter.getInstance(documento, fos);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);

            documento.open();

            // 1. Cabecera Institucional
            agregarCabecera(documento, config, filtro, usuarioEmisor);

            // 2. Tabla Principal de 16 Columnas
            agregarTablaPrincipal(documento, registros, resumen);

            // 3. Resumen Tributario Forma 30 al pie
            agregarResumenTributario(documento, resumen);

            // 4. Pie Legal
            Paragraph pie = new Paragraph(
                    "Documento tributario probatorio expedido conforme al Reglamento de la Ley del IVA (Art. 70 y 72) y Providencias SENIAT SNAT/2011/0071 y SNAT/2014/0032.",
                    FONT_PIE);
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.setSpacingBefore(10f);
            documento.add(pie);

            documento.close();
            return archivoSalida;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al generar el PDF del libro de ventas SENIAT: " + ex.getMessage(), ex);
            throw new ErrorAplicacionException("No se pudo generar el archivo PDF del libro de ventas: " + ex.getMessage(), ex);
        }
    }

    private void agregarCabecera(Document doc, Config conf, FiltroLibroVentas filtro, String usuarioEmisor) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{65f, 35f});
        header.setSpacingAfter(8f);

        String nomEmpresa = (conf != null && conf.getNombre() != null && !conf.getNombre().isBlank())
                ? conf.getNombre().trim() : "RESTAURANTE 2026";
        String rifEmpresa = (conf != null && conf.getRif() != null && !conf.getRif().isBlank())
                ? conf.getRif().trim() : "RIF: J-00000000-0";
        String dirEmpresa = (conf != null && conf.getDireccion() != null && !conf.getDireccion().isBlank())
                ? conf.getDireccion().trim() : "";

        PdfPCell celdaIzq = new PdfPCell();
        celdaIzq.setBorder(Rectangle.NO_BORDER);
        celdaIzq.addElement(new Paragraph("LIBRO DE VENTAS FISCAL (NORMATIVA SENIAT)", FONT_TITULO));
        celdaIzq.addElement(new Paragraph(nomEmpresa, FONT_EMPRESA));
        celdaIzq.addElement(new Paragraph(rifEmpresa + (dirEmpresa.isEmpty() ? "" : " — " + dirEmpresa), FONT_SUBTITULO));
        header.addCell(celdaIzq);

        PdfPCell celdaDer = new PdfPCell();
        celdaDer.setBorder(Rectangle.NO_BORDER);
        celdaDer.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pPeriodo = new Paragraph("PERÍODO: " + filtro.getDescripcionPeriodo(), FONT_PERIODO);
        pPeriodo.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(pPeriodo);

        Paragraph pEmision = new Paragraph("Emisión: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                + " · Usuario: " + (usuarioEmisor != null ? usuarioEmisor : "Sistema"), FONT_SUBTITULO);
        pEmision.setAlignment(Element.ALIGN_RIGHT);
        celdaDer.addElement(pEmision);
        header.addCell(celdaDer);

        doc.add(header);
    }

    private void agregarTablaPrincipal(Document doc, List<RegistroLibroVenta> registros, ResumenFiscalVentas resumen) throws DocumentException {
        // 16 columnas consolidadas
        PdfPTable table = new PdfPTable(16);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{
                3f,  // Op
                6f,  // Fecha
                7.5f,// RIF
                14f, // Cliente
                6f,  // N° Fact
                6f,  // N° Control
                6f,  // N° NC
                6f,  // Fact Afect
                4.5f,// Tipo
                7.5f,// Total Bs
                6.5f,// Exento
                7.5f,// Base Imp
                3.5f,// %
                7.5f,// IVA Bs
                7f,  // IVA Ret
                7f   // IGTF 3%
        });
        table.setSpacingAfter(8f);

        // Encabezados
        String[] titulos = {
                "Op", "Fecha", "RIF/C.I.", "Nombre o Razón Social", "N° Fact", "N° Control",
                "N° N/C", "Fact Afect", "Tipo", "Total Bs.", "Exento Bs.", "Base Imp. Bs.",
                "%", "IVA (16%)", "IVA Ret.", "IGTF 3%"
        };

        for (String t : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(t, FONT_TH));
            c.setBackgroundColor(COLOR_PRIMARIO);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPadding(4f);
            table.addCell(c);
        }

        boolean zebra = false;
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy");

        for (RegistroLibroVenta r : registros) {
            boolean anulada = r.esAnulada();
            BaseColor fondo = anulada ? COLOR_ANULADA : (zebra ? COLOR_ZEBRA : BaseColor.WHITE);
            Font fTexto = anulada ? FONT_TD_ANU : FONT_TD;
            Font fNum = anulada ? FONT_TD_ANU : FONT_TD_NUM;

            table.addCell(crearCelda(String.valueOf(r.getNumOperacion()), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getFechaOperacion().format(df), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getClienteDocumento(), fTexto, Element.ALIGN_LEFT, fondo));
            table.addCell(crearCelda(truncar(r.getClienteNombre(), 22), fTexto, Element.ALIGN_LEFT, fondo));
            table.addCell(crearCelda(r.getNroFactura(), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getNroControl(), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getNroNotaCredito(), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getFacturaAfectada(), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(r.getTipoTransaccion(), fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(formatearBs(r.getTotalVentasBs()), fNum, Element.ALIGN_RIGHT, fondo));
            table.addCell(crearCelda(formatearBs(r.getVentasExentasBs()), fNum, Element.ALIGN_RIGHT, fondo));
            table.addCell(crearCelda(formatearBs(r.getBaseImponibleBs()), fNum, Element.ALIGN_RIGHT, fondo));
            table.addCell(crearCelda(r.getAlicuotaPorcentaje().setScale(0).toString() + "%", fTexto, Element.ALIGN_CENTER, fondo));
            table.addCell(crearCelda(formatearBs(r.getIvaBs()), fNum, Element.ALIGN_RIGHT, fondo));
            table.addCell(crearCelda(formatearBs(r.getIvaRetenidoBs()), fNum, Element.ALIGN_RIGHT, fondo));
            table.addCell(crearCelda(formatearBs(r.getIgtfPercibidoBs()), fNum, Element.ALIGN_RIGHT, fondo));

            zebra = !zebra;
        }

        // Fila de Totales
        PdfPCell cTotLab = new PdfPCell(new Phrase("TOTALES PERÍODO (Bs.):", FONT_TOTALES));
        cTotLab.setColspan(9);
        cTotLab.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTotLab.setBackgroundColor(COLOR_ZEBRA);
        cTotLab.setPadding(4f);
        table.addCell(cTotLab);

        table.addCell(crearCelda(formatearBs(resumen.totalVentasConIvaBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        table.addCell(crearCelda(formatearBs(resumen.totalVentasExentasBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        table.addCell(crearCelda(formatearBs(resumen.totalBaseImponibleGeneralBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        table.addCell(crearCelda("16%", FONT_TOTALES, Element.ALIGN_CENTER, COLOR_ZEBRA));
        table.addCell(crearCelda(formatearBs(resumen.totalDebitoFiscalGeneralBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        table.addCell(crearCelda(formatearBs(resumen.totalIvaRetenidoBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        table.addCell(crearCelda(formatearBs(resumen.totalIgtfPercibidoBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));

        doc.add(table);
    }

    private void agregarResumenTributario(Document doc, ResumenFiscalVentas resumen) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{55f, 45f});
        wrapper.setSpacingBefore(6f);

        // Tabla Izquierda: Forma 30 IVA
        PdfPCell celdaIzq = new PdfPCell();
        celdaIzq.setBorder(Rectangle.NO_BORDER);
        celdaIzq.addElement(new Paragraph("RESUMEN DECLARACIÓN DE IVA (FORMA 30 SENIAT)", FONT_RESUMEN_TITULO));

        PdfPTable tIva = new PdfPTable(3);
        tIva.setWidthPercentage(100);
        tIva.setWidths(new float[]{60f, 20f, 20f});
        tIva.addCell(crearCelda("Concepto Fiscal", FONT_TH, Element.ALIGN_LEFT, COLOR_AZUL_FISCAL));
        tIva.addCell(crearCelda("Base (Bs.)", FONT_TH, Element.ALIGN_RIGHT, COLOR_AZUL_FISCAL));
        tIva.addCell(crearCelda("Débito (Bs.)", FONT_TH, Element.ALIGN_RIGHT, COLOR_AZUL_FISCAL));

        tIva.addCell(crearCelda("Ventas Internas Exentas / No Sujetas (R. 20)", FONT_RESUMEN_TEXTO, Element.ALIGN_LEFT, BaseColor.WHITE));
        tIva.addCell(crearCelda(formatearBs(resumen.totalVentasExentasBs()), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));
        tIva.addCell(crearCelda("-", FONT_TD_NUM, Element.ALIGN_CENTER, BaseColor.WHITE));

        tIva.addCell(crearCelda("Ventas Internas Gravadas al 16% (R. 22 y 23)", FONT_RESUMEN_TEXTO, Element.ALIGN_LEFT, BaseColor.WHITE));
        tIva.addCell(crearCelda(formatearBs(resumen.totalBaseImponibleGeneralBs()), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));
        tIva.addCell(crearCelda(formatearBs(resumen.totalDebitoFiscalGeneralBs()), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));

        tIva.addCell(crearCelda("TOTAL VENTAS Y DÉBITOS FISCALES", FONT_TOTALES, Element.ALIGN_LEFT, COLOR_ZEBRA));
        tIva.addCell(crearCelda(formatearBs(resumen.totalBaseImponibleGeneralBs().add(resumen.totalVentasExentasBs())), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));
        tIva.addCell(crearCelda(formatearBs(resumen.totalDebitoFiscalGeneralBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));

        celdaIzq.addElement(tIva);
        wrapper.addCell(celdaIzq);

        // Tabla Derecha: IGTF y Métricas
        PdfPCell celdaDer = new PdfPCell();
        celdaDer.setBorder(Rectangle.NO_BORDER);
        celdaDer.addElement(new Paragraph("RESUMEN DE IGTF Y MÉTRICAS OPERATIVAS", FONT_RESUMEN_TITULO));

        PdfPTable tIgtf = new PdfPTable(2);
        tIgtf.setWidthPercentage(100);
        tIgtf.setWidths(new float[]{70f, 30f});
        tIgtf.addCell(crearCelda("Métrica Fiscal", FONT_TH, Element.ALIGN_LEFT, COLOR_AZUL_FISCAL));
        tIgtf.addCell(crearCelda("Monto / Cantidad", FONT_TH, Element.ALIGN_RIGHT, COLOR_AZUL_FISCAL));

        tIgtf.addCell(crearCelda("Total Operaciones Fiscales Registradas", FONT_RESUMEN_TEXTO, Element.ALIGN_LEFT, BaseColor.WHITE));
        tIgtf.addCell(crearCelda(String.valueOf(resumen.totalOperaciones()), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));

        tIgtf.addCell(crearCelda("Total Facturas Emitidas / Anuladas", FONT_RESUMEN_TEXTO, Element.ALIGN_LEFT, BaseColor.WHITE));
        tIgtf.addCell(crearCelda(resumen.totalFacturasEmitidas() + " / " + resumen.totalFacturasAnuladas(), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));

        tIgtf.addCell(crearCelda("Total IVA Retenido por Clientes Especiales", FONT_RESUMEN_TEXTO, Element.ALIGN_LEFT, BaseColor.WHITE));
        tIgtf.addCell(crearCelda(formatearBs(resumen.totalIvaRetenidoBs()), FONT_TD_NUM, Element.ALIGN_RIGHT, BaseColor.WHITE));

        tIgtf.addCell(crearCelda("Total Percepción IGTF 3% Divisas a Enterar", FONT_TOTALES, Element.ALIGN_LEFT, COLOR_ZEBRA));
        tIgtf.addCell(crearCelda(formatearBs(resumen.totalIgtfPercibidoBs()), FONT_TOTALES_NUM, Element.ALIGN_RIGHT, COLOR_ZEBRA));

        celdaDer.addElement(tIgtf);
        wrapper.addCell(celdaDer);

        doc.add(wrapper);
    }

    private static PdfPCell crearCelda(String contenido, Font font, int alineacion, BaseColor fondo) {
        PdfPCell c = new PdfPCell(new Phrase(contenido != null ? contenido : "", font));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(fondo);
        c.setBorderColor(COLOR_LINEA);
        c.setPadding(3f);
        return c;
    }

    private static String formatearBs(BigDecimal monto) {
        if (monto == null) return "0,00";
        return String.format(java.util.Locale.GERMANY, "%,.2f", monto);
    }

    private static String truncar(String texto, int maxLen) {
        if (texto == null) return "";
        if (texto.length() <= maxLen) return texto;
        return texto.substring(0, maxLen - 1) + ".";
    }
}
