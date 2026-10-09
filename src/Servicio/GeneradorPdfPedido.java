package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
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
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Compone comprobantes de pedido optimizados para tickera térmica de 80 mm
 * con desglose bimonetario en Bolívares y Dólares.
 */
public final class GeneradorPdfPedido {

    /** Formatos de papel soportados. Por defecto se utiliza TICKET_80MM. */
    public enum FormatoPapel {
        TICKET_80MM,
        PAGINA_A4
    }

    /** Ancho físico de 80 mm en puntos tipográficos (80 / 25.4 * 72 pt = ~226.77 pt). */
    public static final float ANCHO_80MM = 227.0f;

    private final Path directorioSalida;
    private final boolean estructurarPorPeriodo;
    private final FormatoPapel formatoPapel;

    public GeneradorPdfPedido(Path directorioSalida) {
        this(directorioSalida, false, resolverFormatoPorDefecto());
    }

    public GeneradorPdfPedido(Path directorioSalida, boolean estructurarPorPeriodo) {
        this(directorioSalida, estructurarPorPeriodo, resolverFormatoPorDefecto());
    }

    public GeneradorPdfPedido(Path directorioSalida, boolean estructurarPorPeriodo, FormatoPapel formatoPapel) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida del PDF es obligatorio.");
        }
        this.directorioSalida = directorioSalida;
        this.estructurarPorPeriodo = estructurarPorPeriodo;
        this.formatoPapel = formatoPapel != null ? formatoPapel : FormatoPapel.TICKET_80MM;
    }

    public static GeneradorPdfPedido conEstructuraMensual(Path directorioBase) {
        return new GeneradorPdfPedido(directorioBase, true, resolverFormatoPorDefecto());
    }

    public static FormatoPapel resolverFormatoPorDefecto() {
        String formatoStr = System.getProperty("PDF_FORMATO");
        if (formatoStr == null || formatoStr.isBlank()) {
            formatoStr = System.getenv("PDF_FORMATO");
        }
        if (formatoStr != null && "A4".equalsIgnoreCase(formatoStr.trim())) {
            return FormatoPapel.PAGINA_A4;
        }
        return FormatoPapel.TICKET_80MM;
    }

    public static Path resolverDirectorioFacturasPorDefecto() {
        String override = System.getProperty("PDF_FACTURAS_DIR");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("PDF_FACTURAS_DIR");
        }
        if (override != null && !override.trim().isEmpty()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home", "."), "Restaurante", "facturas");
    }

    public Path getDirectorioSalida() {
        return directorioSalida;
    }

    public boolean isEstructurarPorPeriodo() {
        return estructurarPorPeriodo;
    }

    public FormatoPapel getFormatoPapel() {
        return formatoPapel;
    }

    public Path resolverDirectorioDestino() {
        if (estructurarPorPeriodo) {
            return directorioSalida.resolve(java.time.YearMonth.now().toString());
        }
        return directorioSalida;
    }

    public Path generar(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        validar(pedido, configuracion, detalles);
        Path dirDestino = resolverDirectorioDestino();
        Path salida = dirDestino.resolve("pedido-" + pedido.getId() + ".pdf");
        if (Files.exists(salida)) {
            String baseTimestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path respaldo = dirDestino.resolve("pedido-" + pedido.getId() + "_" + baseTimestamp + ".pdf");
            int contador = 1;
            while (Files.exists(respaldo)) {
                respaldo = dirDestino.resolve("pedido-" + pedido.getId() + "_" + baseTimestamp + "_" + contador + ".pdf");
                contador++;
            }
            try {
                Files.createDirectories(dirDestino);
                Files.copy(salida, respaldo);
            } catch (IOException ignored) {
            }
        }
        generarArchivo(dirDestino, salida, pedido, configuracion, detalles);
        return salida;
    }

    public Path generarConTimestamp(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        validar(pedido, configuracion, detalles);
        Path dirDestino = resolverDirectorioDestino();
        String baseTimestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path salida = dirDestino.resolve("pedido-" + pedido.getId() + "_" + baseTimestamp + ".pdf");
        int contador = 1;
        while (Files.exists(salida)) {
            salida = dirDestino.resolve("pedido-" + pedido.getId() + "_" + baseTimestamp + "_" + contador + ".pdf");
            contador++;
        }
        generarArchivo(dirDestino, salida, pedido, configuracion, detalles);
        return salida;
    }

    private void generarArchivo(Path dirDestino, Path salida, Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        Image imagen = obtenerLogo(configuracion);
        boolean tieneIva = pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0;
        Document documento;

        if (formatoPapel == FormatoPapel.TICKET_80MM) {
            float alto = calcularAltoTicket80mm(pedido, configuracion, detalles, imagen != null, tieneIva);
            Rectangle tamanoTicket = new Rectangle(ANCHO_80MM, alto);
            documento = new Document(tamanoTicket, 8f, 8f, 10f, 15f);
        } else {
            documento = new Document(com.itextpdf.text.PageSize.A4, 36f, 36f, 36f, 36f);
        }

        try {
            Files.createDirectories(dirDestino);
            try (OutputStream archivo = Files.newOutputStream(salida)) {
                PdfWriter.getInstance(documento, archivo);
                documento.open();
                if (formatoPapel == FormatoPapel.TICKET_80MM) {
                    agregarEncabezadoTicket80mm(documento, pedido, configuracion, imagen);
                    agregarDetallesTicket80mm(documento, pedido, configuracion, detalles);
                    agregarCierreTicket80mm(documento, pedido, configuracion);
                } else {
                    agregarEncabezado(documento, pedido, configuracion, imagen);
                    agregarDetalles(documento, pedido, configuracion, detalles);
                    agregarCierre(documento, pedido, configuracion);
                }
                documento.close();
            }
        } catch (DocumentException | IOException | RuntimeException error) {
            if (documento.isOpen()) {
                try {
                    documento.close();
                } catch (RuntimeException errorCierre) {
                    error.addSuppressed(errorCierre);
                }
            }
            throw new ErrorAplicacionException("No se pudo generar el PDF del pedido.", error);
        }
    }

    private float calcularAltoTicket80mm(Pedidos pedido, Config configuracion, List<DetallePedido> detalles, boolean tieneLogo, boolean tieneIva) {
        float alto = 360f;
        if (tieneLogo) {
            alto += 45f;
        }
        if (tieneIva) {
            alto += 30f;
        }
        alto += (detalles.size() * 19f);
        if (configuracion != null && configuracion.getMensaje() != null && configuracion.getMensaje().length() > 30) {
            alto += 20f;
        }
        return Math.max(alto, 420f);
    }

    private Image obtenerLogo(Config configuracion) {
        if (configuracion == null || !configuracion.isImprimirLogoTicket()) {
            return null;
        }
        Image imagen = null;
        if (configuracion.getLogoPath() != null && !configuracion.getLogoPath().isBlank()) {
            java.nio.file.Path rutaLogo = java.nio.file.Path.of(configuracion.getLogoPath());
            if (java.nio.file.Files.isRegularFile(rutaLogo)) {
                try {
                    imagen = Image.getInstance(rutaLogo.toAbsolutePath().toString());
                } catch (Exception ignored) {
                }
            }
        }
        return imagen;
    }

    private void validar(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        if (pedido == null || pedido.getId() <= 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener un identificador válido para generar el PDF.");
        }
        if (configuracion == null) {
            throw ErrorAplicacionException.validacion("La configuración de la empresa es obligatoria para generar el PDF.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe tener detalles para generar el PDF.");
        }
        BigDecimal totalDetalles = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()
                    || detalle.getCantidad() <= 0 || !importeValido(detalle.getPrecioDecimal())) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle no válido para el PDF.");
            }
            totalDetalles = totalDetalles.add(detalle.getPrecioDecimal().setScale(2, RoundingMode.UNNECESSARY)
                    .multiply(BigDecimal.valueOf(detalle.getCantidad())));
        }
        BigDecimal subtotalEsperado = totalDetalles;
        BigDecimal ivaMaximo = (pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0)
                ? subtotalEsperado.multiply(pedido.getIvaPorcentaje()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.UNNECESSARY).compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
        }
        if (pedido.getIvaMonto() != null && (pedido.getIvaMonto().compareTo(BigDecimal.ZERO) < 0 || pedido.getIvaMonto().compareTo(ivaMaximo) > 0)) {
            throw ErrorAplicacionException.validacion("El monto del IVA no coincide con el porcentaje aplicado.");
        }
        BigDecimal ivaEfectivo = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : ivaMaximo;
        BigDecimal totalEsperado = subtotalEsperado.add(ivaEfectivo).setScale(2, RoundingMode.HALF_UP);
        if (!importeValido(pedido.getTotalDecimal())
                || pedido.getTotalDecimal().setScale(2, RoundingMode.UNNECESSARY).compareTo(totalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles y cálculo de IVA.");
        }
    }

    private boolean importeValido(BigDecimal importe) {
        return importe != null && importe.signum() >= 0 && importe.scale() <= 2
                && importe.precision() - importe.scale() <= 8;
    }

    // =========================================================================
    // DISEÑO PARA TICKERA TÉRMICA DE 80 MM
    // =========================================================================

    private void agregarEncabezadoTicket80mm(Document documento, Pedidos pedido, Config configuracion, Image imagen)
            throws DocumentException {
        if (imagen != null) {
            imagen.setAlignment(Element.ALIGN_CENTER);
            imagen.scaleToFit(110f, 40f);
            documento.add(imagen);
            documento.add(new Paragraph(" ", new Font(Font.FontFamily.HELVETICA, 2)));
        }

        Font fontEmpresa = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.BLACK);
        Font fontDatos = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
        Font fontSep = new Font(Font.FontFamily.COURIER, 7, Font.NORMAL, BaseColor.DARK_GRAY);

        Paragraph pEmpresa = new Paragraph(texto(configuracion.getNombre()), fontEmpresa);
        pEmpresa.setAlignment(Element.ALIGN_CENTER);
        documento.add(pEmpresa);

        Paragraph pDatos = new Paragraph();
        pDatos.setFont(fontDatos);
        pDatos.setAlignment(Element.ALIGN_CENTER);
        pDatos.add("RIF: " + texto(configuracion.getRif()) + "\n");
        pDatos.add("Teléfono: " + texto(configuracion.getTelefono()) + "\n");
        pDatos.add("Dirección: " + texto(configuracion.getDireccion()));
        documento.add(pDatos);

        Paragraph sep1 = new Paragraph("----------------------------------------------------------------", fontSep);
        sep1.setAlignment(Element.ALIGN_CENTER);
        documento.add(sep1);

        Font fontNegrita = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);
        Font fontNormal = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);

        Paragraph info = new Paragraph();
        info.setFont(fontNormal);
        info.add(new Phrase("N° Pedido: " + pedido.getId() + "   Fecha: " + texto(pedido.getFecha()) + "\n", fontNegrita));
        info.add("Sala: " + texto(pedido.getSala()) + "   N° Mesa: " + pedido.getNum_mesa() + "\n");
        info.add("Atendido: " + texto(pedido.getUsuario()) + "\n");
        info.add("Cliente: " + texto(pedido.getClienteNombre()) + "\n");
        info.add("Cédula/RIF: " + texto(pedido.getClienteDocumento()) + "\n");
        info.add("Método de Pago: " + texto(pedido.getMetodoPago()) + "\n");
        documento.add(info);

        Paragraph sep2 = new Paragraph("----------------------------------------------------------------", fontSep);
        sep2.setAlignment(Element.ALIGN_CENTER);
        documento.add(sep2);
    }

    private void agregarDetallesTicket80mm(Document documento, Pedidos pedido, Config configuracion, List<DetallePedido> detalles)
            throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        Font negrita = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);
        Font cuerpo = new Font(Font.FontFamily.HELVETICA, 7.0f, Font.NORMAL, BaseColor.BLACK);

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.getDefaultCell().setBorder(0);
        tabla.setWidths(new float[]{12f, 44f, 22f, 22f});
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell[] cabeceras = {
            new PdfPCell(new Phrase("Cant.", negrita)),
            new PdfPCell(new Phrase("Plato", negrita)),
            new PdfPCell(new Phrase("P. Unit. ($ / Bs.)", negrita)),
            new PdfPCell(new Phrase("Total ($ / Bs.)", negrita))
        };
        for (PdfPCell celda : cabeceras) {
            celda.setBorder(Rectangle.BOTTOM);
            celda.setBorderWidth(0.5f);
            celda.setPaddingBottom(2f);
            tabla.addCell(celda);
        }

        for (DetallePedido detalle : detalles) {
            BigDecimal precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalUsd = precioUsd.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioBs = precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalBs = subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            PdfPCell cCant = new PdfPCell(new Phrase(String.valueOf(detalle.getCantidad()), cuerpo));
            PdfPCell cPlato = new PdfPCell(new Phrase(detalle.getNombre(), cuerpo));
            PdfPCell cUnit = new PdfPCell(new Phrase("$" + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")", cuerpo));
            PdfPCell cTot = new PdfPCell(new Phrase("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")", cuerpo));

            cCant.setBorder(Rectangle.NO_BORDER);
            cPlato.setBorder(Rectangle.NO_BORDER);
            cUnit.setBorder(Rectangle.NO_BORDER);
            cTot.setBorder(Rectangle.NO_BORDER);

            cCant.setPaddingTop(2f);
            cPlato.setPaddingTop(2f);
            cUnit.setPaddingTop(2f);
            cTot.setPaddingTop(2f);

            tabla.addCell(cCant);
            tabla.addCell(cPlato);
            tabla.addCell(cUnit);
            tabla.addCell(cTot);
        }
        documento.add(tabla);
    }

    private void agregarCierreTicket80mm(Document documento, Pedidos pedido, Config configuracion)
            throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        BigDecimal totalUsd = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBs = pedido.getTotalBs() != null ? pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP) : totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        boolean tieneIva = ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0;

        Font fontSep = new Font(Font.FontFamily.COURIER, 7, Font.NORMAL, BaseColor.DARK_GRAY);
        Paragraph sep = new Paragraph("----------------------------------------------------------------", fontSep);
        sep.setAlignment(Element.ALIGN_CENTER);
        documento.add(sep);

        StringBuilder sb = new StringBuilder();
        if (tieneIva) {
            BigDecimal subtotalUsd = pedido.getSubtotal() != null ? pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd;
            BigDecimal ivaUsd = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal subtotalBs = pedido.getSubtotalBs() != null ? pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP) : subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP) : ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            sb.append("Subtotal: $").append(String.format(java.util.Locale.US, "%.2f", subtotalUsd))
              .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", subtotalBs)).append(")\n");

            if (ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal baseImpUsd = ivaUsd.multiply(new BigDecimal("100")).divide(ivaPorcentaje, 2, RoundingMode.HALF_UP);
                BigDecimal exentoUsd = subtotalUsd.subtract(baseImpUsd);
                if (exentoUsd.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal baseImpBs = baseImpUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal exentoBs = exentoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    sb.append("  Base Imponible: $").append(String.format(java.util.Locale.US, "%.2f", baseImpUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", baseImpBs)).append(")\n");
                    sb.append("  Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                }
            }

            sb.append("IVA (").append(String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP))).append("%): $")
              .append(String.format(java.util.Locale.US, "%.2f", ivaUsd))
              .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", ivaBs)).append(")\n");
        }

        sb.append("Tasa de Cambio: Bs. ").append(tasa.setScale(4, RoundingMode.HALF_UP).toPlainString()).append(" / $\n")
          .append("Total USD: $").append(String.format(java.util.Locale.US, "%.2f", totalUsd)).append("\n")
          .append("TOTAL A PAGAR (Bs.): Bs. ").append(String.format(java.util.Locale.US, "%.2f", totalBs));

        Font fuenteResumen = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, BaseColor.BLACK);
        Paragraph resumen = new Paragraph(sb.toString(), fuenteResumen);
        resumen.setAlignment(Element.ALIGN_RIGHT);
        documento.add(resumen);

        Paragraph firma = new Paragraph("Forma de Pago: " + texto(pedido.getMetodoPago()) + "\n\n------------------------------------\nFirma\n",
                new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK));
        firma.setAlignment(Element.ALIGN_CENTER);
        documento.add(firma);

        Paragraph agradecimiento = new Paragraph(texto(configuracion.getMensaje()),
                new Font(Font.FontFamily.HELVETICA, 7.5f, Font.ITALIC, BaseColor.DARK_GRAY));
        agradecimiento.setAlignment(Element.ALIGN_CENTER);
        documento.add(agradecimiento);
    }

    // =========================================================================
    // DISEÑO LEGACY PARA HOJA A4 (OPCIONAL)
    // =========================================================================

    private void agregarEncabezado(Document documento, Pedidos pedido, Config configuracion, Image imagen)
            throws DocumentException {
        PdfPTable encabezado = new PdfPTable(4);
        encabezado.setWidthPercentage(100);
        encabezado.getDefaultCell().setBorder(0);
        encabezado.setWidths(new float[]{20f, 20f, 60f, 60f});
        encabezado.setHorizontalAlignment(Element.ALIGN_LEFT);
        if (imagen != null) {
            encabezado.addCell(imagen);
        } else {
            encabezado.addCell("");
        }
        encabezado.addCell("");
        encabezado.addCell(datosEmpresa(configuracion));
        Paragraph informacion = new Paragraph("Atendido: " + texto(pedido.getUsuario())
                + "\nN° Pedido: " + pedido.getId()
                + "\nFecha: " + texto(pedido.getFecha())
                + "\nSala: " + texto(pedido.getSala())
                + "\nN° Mesa: " + pedido.getNum_mesa()
                + "\nCliente: " + texto(pedido.getClienteNombre())
                + "\nCédula/RIF: " + texto(pedido.getClienteDocumento())
                + "\nMétodo de Pago: " + texto(pedido.getMetodoPago()));
        encabezado.addCell(informacion);
        documento.add(encabezado);
        documento.add(Chunk.NEWLINE);
    }

    private String datosEmpresa(Config configuracion) {
        return texto(configuracion.getNombre())
                + "\nRIF: " + texto(configuracion.getRif())
                + "\nTeléfono: " + texto(configuracion.getTelefono())
                + "\nDirección: " + texto(configuracion.getDireccion());
    }

    private void agregarDetalles(Document documento, Pedidos pedido, Config configuracion, List<DetallePedido> detalles) throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        Font negrita = new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD, BaseColor.BLUE);
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.getDefaultCell().setBorder(0);
        tabla.setWidths(new float[]{10f, 44f, 23f, 23f});
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell[] cabeceras = {
            new PdfPCell(new Phrase("Cant.", negrita)),
            new PdfPCell(new Phrase("Plato", negrita)),
            new PdfPCell(new Phrase("P. Unit. ($ / Bs.)", negrita)),
            new PdfPCell(new Phrase("Total ($ / Bs.)", negrita))
        };
        for (PdfPCell celda : cabeceras) {
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setBackgroundColor(BaseColor.LIGHT_GRAY);
            tabla.addCell(celda);
        }
        for (DetallePedido detalle : detalles) {
            BigDecimal precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalUsd = precioUsd.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal precioBs = precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalBs = subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            tabla.addCell(String.valueOf(detalle.getCantidad()));
            tabla.addCell(detalle.getNombre());
            tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")");
            tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")");
        }
        documento.add(tabla);
    }

    private void agregarCierre(Document documento, Pedidos pedido, Config configuracion) throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        BigDecimal totalUsd = pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBs = pedido.getTotalBs() != null ? pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP) : totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        boolean tieneIva = ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0;

        StringBuilder sb = new StringBuilder();
        if (tieneIva) {
            BigDecimal subtotalUsd = pedido.getSubtotal() != null ? pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd;
            BigDecimal ivaUsd = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal subtotalBs = pedido.getSubtotalBs() != null ? pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP) : subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP) : ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            sb.append("Subtotal: $").append(String.format(java.util.Locale.US, "%.2f", subtotalUsd))
              .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", subtotalBs)).append(")\n");

            if (ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal baseImpUsd = ivaUsd.multiply(new BigDecimal("100")).divide(ivaPorcentaje, 2, RoundingMode.HALF_UP);
                BigDecimal exentoUsd = subtotalUsd.subtract(baseImpUsd);
                if (exentoUsd.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal baseImpBs = baseImpUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal exentoBs = exentoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    sb.append("  Base Imponible: $").append(String.format(java.util.Locale.US, "%.2f", baseImpUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", baseImpBs)).append(")\n");
                    sb.append("  Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                }
            }

            sb.append("IVA (").append(String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP))).append("%): $")
              .append(String.format(java.util.Locale.US, "%.2f", ivaUsd))
              .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", ivaBs)).append(")\n");
        }

        sb.append("Tasa de Cambio: Bs. ").append(tasa.setScale(4, RoundingMode.HALF_UP).toPlainString()).append(" / $\n")
          .append("Total USD: $").append(String.format(java.util.Locale.US, "%.2f", totalUsd)).append("\n")
          .append("TOTAL A PAGAR (Bs.): Bs. ").append(String.format(java.util.Locale.US, "%.2f", totalBs));

        Font fuenteResumen = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);
        Paragraph resumen = new Paragraph(sb.toString(), fuenteResumen);
        resumen.setAlignment(Element.ALIGN_RIGHT);
        documento.add(Chunk.NEWLINE);
        documento.add(resumen);

        Paragraph firma = new Paragraph("Forma de Pago: " + texto(pedido.getMetodoPago()) + "\n\n------------------------------------\nFirma\n");
        firma.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(firma);

        Paragraph agradecimiento = new Paragraph(texto(configuracion.getMensaje()));
        agradecimiento.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(agradecimiento);
    }

    private BigDecimal obtenerTasa(Pedidos pedido, Config configuracion) {
        if (pedido.getTasaCambio() != null && pedido.getTasaCambio().compareTo(BigDecimal.ZERO) > 0) {
            return pedido.getTasaCambio();
        }
        if (configuracion.getTasaDolar() != null && configuracion.getTasaDolar().compareTo(BigDecimal.ZERO) > 0) {
            return configuracion.getTasaDolar();
        }
        return new BigDecimal("36.5000");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
