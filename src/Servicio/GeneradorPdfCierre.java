package Servicio;

import Modelo.CierreCaja;
import Modelo.Config;
import Modelo.ErrorAplicacionException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Genera el documento PDF del Cierre Parcial (Corte X) o Cierre Total (Corte Z)
 * en formato optimizado para tickera térmica de 80 mm (ancho 227 pt, continuous roll).
 */
public final class GeneradorPdfCierre {
    private static final Logger LOGGER = Logger.getLogger(GeneradorPdfCierre.class.getName());

    public static final float ANCHO_80MM = 227.0f;

    private static final Font FONT_TITULO_EMPRESA = new Font(Font.FontFamily.HELVETICA, 10.5f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_DATOS_EMPRESA = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_HEADER_CIERRE = new Font(Font.FontFamily.HELVETICA, 9.5f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_SECCION = new Font(Font.FontFamily.HELVETICA, 8.0f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_REGULAR = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_REGULAR_BOLD = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_TOTAL_GRANDE = new Font(Font.FontFamily.HELVETICA, 9.5f, Font.BOLD, BaseColor.BLACK);
    private static final Font FONT_SEPARADOR = new Font(Font.FontFamily.COURIER, 7.0f, Font.NORMAL, BaseColor.BLACK);
    private static final Font FONT_PIE = new Font(Font.FontFamily.HELVETICA, 7.0f, Font.ITALIC, BaseColor.BLACK);

    private final Path directorioSalida;

    public GeneradorPdfCierre() {
        this(resolverDirectorioPorDefecto());
    }

    public GeneradorPdfCierre(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida del cierre es obligatorio.");
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

    public Path generar(CierreCaja cierre, Config config) {
        if (cierre == null) {
            throw ErrorAplicacionException.validacion("Los datos del cierre son obligatorios para generar el ticket.");
        }
        if (config == null) {
            throw ErrorAplicacionException.validacion("La configuración del restaurante es obligatoria.");
        }

        try {
            Files.createDirectories(directorioSalida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("No se pudo crear el directorio de salida para el cierre.", ex);
        }

        String prefijo = cierre.getTipo() == CierreCaja.TipoCierre.TOTAL ? "cierre-Z" : "cierre-X";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = String.format("%s_%s_%s.pdf", prefijo, cierre.getFecha().replace("-", ""), timestamp);
        Path archivoSalida = directorioSalida.resolve(nombreArchivo);

        float altoEstimado = calcularAltoDinamico(cierre, config);
        Rectangle formatoTicket = new Rectangle(ANCHO_80MM, altoEstimado);
        Document documento = new Document(formatoTicket, 6.0f, 6.0f, 8.0f, 8.0f);

        try (OutputStream canal = Files.newOutputStream(archivoSalida)) {
            PdfWriter writer = PdfWriter.getInstance(documento, canal);
            documento.open();

            // 1. Encabezado del negocio
            agregarEncabezadoNegocio(documento, config);

            // 2. Encabezado del Cierre
            agregarSeparadorDoble(documento);
            Paragraph pTitulo = new Paragraph(cierre.getTipo().getEtiqueta(), FONT_HEADER_CIERRE);
            pTitulo.setAlignment(Element.ALIGN_CENTER);
            documento.add(pTitulo);
            agregarSeparadorDoble(documento);

            PdfPTable tblMeta = crearTabla(2, new float[]{45f, 55f});
            celda(tblMeta, "Fecha Cierre:", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
            celda(tblMeta, cierre.getFecha(), FONT_REGULAR, Element.ALIGN_RIGHT);
            celda(tblMeta, "Emisión:", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
            celda(tblMeta, cierre.getFechaHoraEmision(), FONT_REGULAR, Element.ALIGN_RIGHT);
            celda(tblMeta, "Emitido Por:", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
            celda(tblMeta, cierre.getUsuarioEmisor(), FONT_REGULAR, Element.ALIGN_RIGHT);
            documento.add(tblMeta);

            // 3. Resumen Operativo
            agregarSeparadorSimple(documento);
            documento.add(new Paragraph("RESUMEN OPERATIVO", FONT_SECCION));
            PdfPTable tblOp = crearTabla(2, new float[]{60f, 40f});
            celda(tblOp, "Pedidos Cobrados:", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblOp, String.valueOf(cierre.getPedidosFinalizados()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            celda(tblOp, "Pedidos Pendientes:", FONT_REGULAR, Element.ALIGN_LEFT);
            String txtPend = cierre.getPedidosPendientes() > 0
                    ? cierre.getPedidosPendientes() + " (EN MESA)"
                    : "0";
            celda(tblOp, txtPend, FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            celda(tblOp, "Platos/Arts Vendidos:", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblOp, String.valueOf(cierre.getTotalArticulos()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            celda(tblOp, "Ticket Promedio ($):", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblOp, "$ " + String.format(java.util.Locale.US, "%.2f", cierre.getTicketPromedioUsd()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            celda(tblOp, "Ticket Promedio (Bs.):", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblOp, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getTicketPromedioBs()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            documento.add(tblOp);

            // 4. Resumen Financiero y Fiscal
            agregarSeparadorSimple(documento);
            documento.add(new Paragraph("TOTALES DE VENTA Y FISCAL", FONT_SECCION));
            PdfPTable tblFin = crearTabla(3, new float[]{40f, 30f, 30f});
            celda(tblFin, "Concepto", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
            celda(tblFin, "USD ($)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
            celda(tblFin, "Bs.", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);

            celda(tblFin, "Subtotal Ventas:", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblFin, "$ " + String.format(java.util.Locale.US, "%.2f", cierre.getSubtotalUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
            celda(tblFin, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getSubtotalBs()), FONT_REGULAR, Element.ALIGN_RIGHT);

            celda(tblFin, "Base Imponible:", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblFin, "$ " + String.format(java.util.Locale.US, "%.2f", cierre.getSubtotalUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
            celda(tblFin, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getSubtotalBs()), FONT_REGULAR, Element.ALIGN_RIGHT);

            celda(tblFin, "I.V.A.:", FONT_REGULAR, Element.ALIGN_LEFT);
            celda(tblFin, "$ " + String.format(java.util.Locale.US, "%.2f", cierre.getIvaUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
            celda(tblFin, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getIvaBs()), FONT_REGULAR, Element.ALIGN_RIGHT);

            celda(tblFin, "TOTAL GENERAL:", FONT_TOTAL_GRANDE, Element.ALIGN_LEFT);
            celda(tblFin, "$ " + String.format(java.util.Locale.US, "%.2f", cierre.getTotalVentasUsd()), FONT_TOTAL_GRANDE, Element.ALIGN_RIGHT);
            celda(tblFin, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getTotalVentasBs()), FONT_TOTAL_GRANDE, Element.ALIGN_RIGHT);
            documento.add(tblFin);

            Paragraph pTasa = new Paragraph("Tasa Ref: Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getTasaCambioReferencia()) + " / USD", FONT_REGULAR);
            pTasa.setAlignment(Element.ALIGN_RIGHT);
            documento.add(pTasa);

            // 5. Desglose por Método de Pago
            if (!cierre.getDesgloseMetodos().isEmpty()) {
                agregarSeparadorSimple(documento);
                documento.add(new Paragraph("DESGLOSE FORMAS DE PAGO", FONT_SECCION));
                PdfPTable tblMetodos = crearTabla(4, new float[]{36f, 16f, 24f, 24f});
                celda(tblMetodos, "Método", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                celda(tblMetodos, "Cant", FONT_REGULAR_BOLD, Element.ALIGN_CENTER);
                celda(tblMetodos, "Total ($)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                celda(tblMetodos, "Total (Bs.)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);

                for (CierreCaja.ResumenMetodoPago rm : cierre.getDesgloseMetodos()) {
                    celda(tblMetodos, rm.getMetodo(), FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblMetodos, String.valueOf(rm.getCantidadTransacciones()), FONT_REGULAR, Element.ALIGN_CENTER);
                    celda(tblMetodos, "$" + String.format(java.util.Locale.US, "%.2f", rm.getMontoUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
                    celda(tblMetodos, "Bs. " + String.format(java.util.Locale.US, "%.2f", rm.getMontoBs()), FONT_REGULAR, Element.ALIGN_RIGHT);
                }
                documento.add(tblMetodos);
            }

            // 5.1 Arqueo y Conciliación de Efectivo
            if (cierre.tieneConciliacionEfectivo()) {
                agregarSeparadorSimple(documento);
                documento.add(new Paragraph("ARQUEO Y CONCILIACIÓN DE EFECTIVO", FONT_SECCION));
                if (cierre.getPagosMixtosSinDesglose() > 0) {
                    documento.add(new Paragraph("ADVERTENCIA: " + cierre.getPagosMixtosSinDesglose()
                            + " pago(s) mixto(s) antiguo(s) sin desglose de efectivo; no se incluyen en el esperado.", FONT_REGULAR_BOLD));
                }
                PdfPTable tblConc = crearTabla(2, new float[]{55f, 45f});

                if (cierre.tieneConciliacionBs()) {
                    celda(tblConc, "Efectivo Sistema (Bs.):", FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblConc, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getTotalEfectivoBs()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                    celda(tblConc, "Efectivo Contado (Bs.):", FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblConc, "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getEfectivoDeclaradoBs()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                    celda(tblConc, "Diferencia Bs.:", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                    String estadoBs = cierre.getEstadoConciliacionBs();
                    String signoBs = cierre.getDiferenciaEfectivoBs().compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                    celda(tblConc, signoBs + "Bs. " + String.format(java.util.Locale.US, "%.2f", cierre.getDiferenciaEfectivoBs()) + " (" + estadoBs + ")", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                }

                if (cierre.tieneConciliacionUsd()) {
                    celda(tblConc, "Efectivo Sistema ($):", FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblConc, "$" + String.format(java.util.Locale.US, "%.2f", cierre.getTotalEfectivoUsd()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                    celda(tblConc, "Efectivo Contado ($):", FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblConc, "$" + String.format(java.util.Locale.US, "%.2f", cierre.getEfectivoDeclaradoUsd()), FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                    celda(tblConc, "Diferencia $:", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                    String estadoUsd = cierre.getEstadoConciliacionUsd();
                    String signoUsd = cierre.getDiferenciaEfectivoUsd().compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                    celda(tblConc, signoUsd + "$" + String.format(java.util.Locale.US, "%.2f", cierre.getDiferenciaEfectivoUsd()) + " (" + estadoUsd + ")", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);
                }

                documento.add(tblConc);
            }

            // 6. Ventas por Sala
            if (!cierre.getDesgloseSalas().isEmpty()) {
                agregarSeparadorSimple(documento);
                documento.add(new Paragraph("VENTAS POR SALA / ÁREA", FONT_SECCION));
                PdfPTable tblSalas = crearTabla(3, new float[]{50f, 20f, 30f});
                celda(tblSalas, "Sala", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                celda(tblSalas, "Ped.", FONT_REGULAR_BOLD, Element.ALIGN_CENTER);
                celda(tblSalas, "Total ($)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);

                for (CierreCaja.ResumenSala rs : cierre.getDesgloseSalas()) {
                    celda(tblSalas, rs.getSala(), FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblSalas, String.valueOf(rs.getCantidadPedidos()), FONT_REGULAR, Element.ALIGN_CENTER);
                    celda(tblSalas, "$" + String.format(java.util.Locale.US, "%.2f", rs.getMontoUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
                }
                documento.add(tblSalas);
            }

            // 7. Ventas por Usuario / Mesonero
            if (!cierre.getDesgloseUsuarios().isEmpty()) {
                agregarSeparadorSimple(documento);
                documento.add(new Paragraph("VENTAS POR OPERADOR / MESONERO", FONT_SECCION));
                PdfPTable tblUsr = crearTabla(3, new float[]{50f, 20f, 30f});
                celda(tblUsr, "Operador", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                celda(tblUsr, "Ped.", FONT_REGULAR_BOLD, Element.ALIGN_CENTER);
                celda(tblUsr, "Total ($)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);

                for (CierreCaja.ResumenUsuario ru : cierre.getDesgloseUsuarios()) {
                    celda(tblUsr, ru.getUsuario(), FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblUsr, String.valueOf(ru.getCantidadPedidos()), FONT_REGULAR, Element.ALIGN_CENTER);
                    celda(tblUsr, "$" + String.format(java.util.Locale.US, "%.2f", ru.getMontoUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
                }
                documento.add(tblUsr);
            }

            // 8. Top 5 Platos Vendidos
            if (!cierre.getTopPlatos().isEmpty()) {
                agregarSeparadorSimple(documento);
                documento.add(new Paragraph("TOP PLATOS VENDIDOS", FONT_SECCION));
                PdfPTable tblPlatos = crearTabla(3, new float[]{55f, 20f, 25f});
                celda(tblPlatos, "Plato", FONT_REGULAR_BOLD, Element.ALIGN_LEFT);
                celda(tblPlatos, "Cant", FONT_REGULAR_BOLD, Element.ALIGN_CENTER);
                celda(tblPlatos, "Total ($)", FONT_REGULAR_BOLD, Element.ALIGN_RIGHT);

                for (CierreCaja.PlatoVendido pv : cierre.getTopPlatos()) {
                    celda(tblPlatos, pv.getNombre(), FONT_REGULAR, Element.ALIGN_LEFT);
                    celda(tblPlatos, String.valueOf(pv.getCantidad()), FONT_REGULAR, Element.ALIGN_CENTER);
                    celda(tblPlatos, "$" + String.format(java.util.Locale.US, "%.2f", pv.getTotalUsd()), FONT_REGULAR, Element.ALIGN_RIGHT);
                }
                documento.add(tblPlatos);
            }

            // 9. Pie y Firma
            agregarSeparadorDoble(documento);
            String pieMsg = cierre.getTipo() == CierreCaja.TipoCierre.TOTAL
                    ? "*** CIERRE DEFINITIVO DE JORNADA ***"
                    : "*** CORTE PARCIAL - TURNO EN CURSO ***";
            Paragraph pPie = new Paragraph(pieMsg, FONT_REGULAR_BOLD);
            pPie.setAlignment(Element.ALIGN_CENTER);
            documento.add(pPie);

            Paragraph pEspacio = new Paragraph("\n\n____________________________________", FONT_REGULAR);
            pEspacio.setAlignment(Element.ALIGN_CENTER);
            documento.add(pEspacio);

            Paragraph pFirma = new Paragraph("Firma Responsable de Caja", FONT_PIE);
            pFirma.setAlignment(Element.ALIGN_CENTER);
            documento.add(pFirma);

            documento.close();
            return archivoSalida;

        } catch (DocumentException | IOException ex) {
            if (documento.isOpen()) {
                documento.close();
            }
            throw new ErrorAplicacionException("No se pudo componer el ticket PDF de cierre de caja.", ex);
        }
    }

    private void agregarEncabezadoNegocio(Document documento, Config config) throws DocumentException {
        // Logo si existe y la impresión en tickera está habilitada
        if (config.isImprimirLogoTicket() && config.getLogoPath() != null && !config.getLogoPath().trim().isEmpty()) {
            try {
                Path rutaLogo = Path.of(config.getLogoPath().trim());
                if (Files.isRegularFile(rutaLogo)) {
                    Image logoImg = Image.getInstance(rutaLogo.toAbsolutePath().toString());
                    logoImg.setAlignment(Image.ALIGN_CENTER);
                    if (logoImg.getWidth() > 105f) {
                        logoImg.scaleToFit(105f, 40f);
                    }
                    documento.add(logoImg);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "No se pudo incrustar el logo en el cierre: " + ex.getMessage());
            }
        }

        String nombreNegocio = (config.getNombre() != null && !config.getNombre().isBlank())
                ? config.getNombre().trim() : "RESTAURANTE";
        Paragraph pNombre = new Paragraph(nombreNegocio, FONT_TITULO_EMPRESA);
        pNombre.setAlignment(Element.ALIGN_CENTER);
        documento.add(pNombre);

        if (config.getRuc() != null && !config.getRuc().isBlank()) {
            Paragraph pRif = new Paragraph("RIF: " + config.getRuc().trim(), FONT_DATOS_EMPRESA);
            pRif.setAlignment(Element.ALIGN_CENTER);
            documento.add(pRif);
        }

        if (config.getTelefono() != null && !config.getTelefono().isBlank()) {
            Paragraph pTel = new Paragraph("Tel: " + config.getTelefono().trim(), FONT_DATOS_EMPRESA);
            pTel.setAlignment(Element.ALIGN_CENTER);
            documento.add(pTel);
        }

        if (config.getDireccion() != null && !config.getDireccion().isBlank()) {
            Paragraph pDir = new Paragraph(config.getDireccion().trim(), FONT_DATOS_EMPRESA);
            pDir.setAlignment(Element.ALIGN_CENTER);
            documento.add(pDir);
        }
    }

    private void agregarSeparadorSimple(Document documento) throws DocumentException {
        Paragraph p = new Paragraph("--------------------------------------------------", FONT_SEPARADOR);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(1f);
        p.setSpacingAfter(1f);
        documento.add(p);
    }

    private void agregarSeparadorDoble(Document documento) throws DocumentException {
        Paragraph p = new Paragraph("==================================================", FONT_SEPARADOR);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(2f);
        p.setSpacingAfter(2f);
        documento.add(p);
    }

    private PdfPTable crearTabla(int columnas, float[] anchosRelativos) throws DocumentException {
        PdfPTable table = new PdfPTable(columnas);
        table.setWidthPercentage(100f);
        table.setWidths(anchosRelativos);
        table.setSpacingBefore(2f);
        table.setSpacingAfter(2f);
        return table;
    }

    private void celda(PdfPTable table, String texto, Font font, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        celda.setBorder(PdfPCell.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPaddingTop(1f);
        celda.setPaddingBottom(1f);
        celda.setPaddingLeft(0f);
        celda.setPaddingRight(0f);
        table.addCell(celda);
    }

    private float calcularAltoDinamico(CierreCaja cierre, Config config) {
        boolean conLogo = config != null && config.isImprimirLogoTicket()
                && config.getLogoPath() != null && !config.getLogoPath().trim().isEmpty();
        float alto = conLogo ? 335.0f : 290.0f; // Base: Encabezados, datos, resumen operativo y pie
        alto += 85.0f;       // Resumen financiero y fiscal
        alto += 22.0f + (cierre.getDesgloseMetodos().size() * 12.0f);
        alto += 22.0f + (cierre.getDesgloseSalas().size() * 12.0f);
        alto += 22.0f + (cierre.getDesgloseUsuarios().size() * 12.0f);
        alto += 22.0f + (cierre.getTopPlatos().size() * 12.0f);
        alto += 80.0f;       // Firma y márgenes
        if (cierre.getPagosMixtosSinDesglose() > 0) alto += 18.0f;
        return Math.max(450.0f, alto);
    }
}
