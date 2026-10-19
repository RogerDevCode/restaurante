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
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final ResolvedorTasaHistorica resolvedorTasaHistorica;
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(GeneradorPdfPedido.class.getName());

    @FunctionalInterface
    public interface ResolvedorTasaHistorica {
        BigDecimal resolver(String fecha, int idPedido);
    }

    public GeneradorPdfPedido(Path directorioSalida) {
        this(directorioSalida, false, resolverFormatoPorDefecto(), null);
    }

    public GeneradorPdfPedido(Path directorioSalida, boolean estructurarPorPeriodo) {
        this(directorioSalida, estructurarPorPeriodo, resolverFormatoPorDefecto(), null);
    }

    public GeneradorPdfPedido(Path directorioSalida, boolean estructurarPorPeriodo, FormatoPapel formatoPapel) {
        this(directorioSalida, estructurarPorPeriodo, formatoPapel, null);
    }

    public GeneradorPdfPedido(Path directorioSalida, boolean estructurarPorPeriodo, FormatoPapel formatoPapel,
            ResolvedorTasaHistorica resolvedorTasaHistorica) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida del PDF es obligatorio.");
        }
        this.directorioSalida = directorioSalida;
        this.estructurarPorPeriodo = estructurarPorPeriodo;
        this.formatoPapel = formatoPapel != null ? formatoPapel : FormatoPapel.TICKET_80MM;
        this.resolvedorTasaHistorica = resolvedorTasaHistorica;
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

    public Path generarPreFactura(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        if (pedido == null || pedido.getId() <= 0) {
            throw ErrorAplicacionException.validacion("El pedido debe tener un identificador válido para emitir la pre-factura.");
        }
        if (configuracion == null) {
            throw ErrorAplicacionException.validacion("La configuración de la empresa es obligatoria.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido no tiene consumos registrados para emitir la pre-factura.");
        }
        validar(pedido, configuracion, detalles);

        Path dirDestino = Path.of("tickets", "prefacturas");
        try {
            Files.createDirectories(dirDestino);
        } catch (IOException e) {
            dirDestino = resolverDirectorioDestino();
        }
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path salida = dirDestino.resolve("prefactura-" + pedido.getId() + "_" + timestamp + ".pdf");
        generarArchivoPreFactura(dirDestino, salida, pedido, configuracion, detalles);
        return salida;
    }

    private void generarArchivoPreFactura(Path dirDestino, Path salida, Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        Image imagen = obtenerLogo(configuracion);
        boolean tieneIva = pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0;
        float alto = calcularAltoTicket80mm(pedido, configuracion, detalles, imagen != null, tieneIva) + 45f;
        Rectangle tamanoTicket = new Rectangle(ANCHO_80MM, alto);
        Document documento = new Document(tamanoTicket, 10f, 10f, 10f, 15f);

        try {
            Files.createDirectories(dirDestino);
            try (OutputStream archivo = Files.newOutputStream(salida)) {
                PdfWriter.getInstance(documento, archivo);
                documento.open();
                agregarEncabezadoPreFactura80mm(documento, pedido, configuracion, imagen);
                agregarDetallesTicket80mm(documento, pedido, configuracion, detalles);
                agregarCierreTicket80mm(documento, pedido, configuracion, detalles, true);
                documento.close();
            }
        } catch (Exception error) {
            if (documento.isOpen()) {
                try {
                    documento.close();
                } catch (RuntimeException errorCierre) {
                    error.addSuppressed(errorCierre);
                }
            }
            throw new ErrorAplicacionException("No se pudo generar la pre-factura del pedido.", error);
        }
    }

    private void generarArchivo(Path dirDestino, Path salida, Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        Image imagen = obtenerLogo(configuracion);
        boolean tieneIva = pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0;
        Document documento;

        if (formatoPapel == FormatoPapel.TICKET_80MM) {
            float alto = calcularAltoTicket80mm(pedido, configuracion, detalles, imagen != null, tieneIva);
            Rectangle tamanoTicket = new Rectangle(ANCHO_80MM, alto);
            documento = new Document(tamanoTicket, 10f, 10f, 10f, 15f);
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
                    agregarCierreTicket80mm(documento, pedido, configuracion, detalles);
                } else {
                    agregarEncabezado(documento, pedido, configuracion, imagen);
                    agregarDetalles(documento, pedido, configuracion, detalles);
                    agregarCierre(documento, pedido, configuracion, detalles);
                }
                documento.close();
            }
        } catch (ErrorAplicacionException error) {
            if (documento.isOpen()) {
                try {
                    documento.close();
                } catch (RuntimeException errorCierre) {
                    error.addSuppressed(errorCierre);
                }
            }
            throw error;
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
        float alto = 420f;
        if (tieneLogo) {
            alto += 45f;
        }
        if (tieneIva) {
            alto += 40f;
        }
        alto += (detalles.size() * 20f);
        if (configuracion != null && configuracion.getMensaje() != null && configuracion.getMensaje().length() > 30) {
            alto += 25f;
        }
        return Math.max(alto, 480f);
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
        for (DetallePedido detalle : detalles) {
            if (detalle == null || detalle.getNombre() == null || detalle.getNombre().trim().isEmpty()
                    || detalle.getCantidad() <= 0 || !importeValido(detalle.getPrecioDecimal())) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle no válido para el PDF.");
            }
        }

        boolean esLegacy = detalles.stream().anyMatch(d -> d == null || d.getAplicaIva() == null || d.getMonedaBase() == null || d.getPrecioIncluyeIva() == null);
        BigDecimal tol = new BigDecimal("0.01");
        // Rama moderna (fiscalidad clasificada): el recibo debe cuadrar exacto al centavo.
        BigDecimal tolEstricta = new BigDecimal("0.001");
        if (esLegacy) {
            BigDecimal tasa = null;
            try {
                tasa = obtenerTasa(pedido, configuracion);
            } catch (Exception ignored) {
            }

            BigDecimal totalDetalles = BigDecimal.ZERO.setScale(2);
            for (DetallePedido detalle : detalles) {
                if (detalle == null) continue;
                BigDecimal precio = detalle.getPrecioDecimal() != null ? detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal sub = precio.multiply(BigDecimal.valueOf(detalle.getCantidad()));
                if (Boolean.TRUE.equals(detalle.getPrecioIncluyeIva()) && detalle.getIvaPorcentaje() != null && detalle.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal factor = BigDecimal.ONE.add(detalle.getIvaPorcentaje().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                    sub = sub.divide(factor, 2, RoundingMode.HALF_UP);
                }
                if ("VES".equalsIgnoreCase(detalle.getMonedaBase()) && tasa != null && tasa.signum() > 0) {
                    sub = sub.divide(tasa, 2, RoundingMode.HALF_UP);
                }
                totalDetalles = totalDetalles.add(sub);
            }
            BigDecimal subtotalEsperado = totalDetalles;
            boolean fiscalidadCompleta = detalles.stream().allMatch(d -> d != null && d.getAplicaIva() != null);
            BigDecimal baseImponible = fiscalidadCompleta ? calcularBaseImponible(detalles, tasa) : subtotalEsperado;
            BigDecimal ivaEsperado = (pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0)
                    ? baseImponible.multiply(pedido.getIvaPorcentaje()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);

            if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP).subtract(subtotalEsperado).abs().compareTo(tol) > 0) {
                throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
            }
            if (pedido.getIvaMonto() != null && (pedido.getIvaMonto().compareTo(BigDecimal.ZERO) < 0
                    || (fiscalidadCompleta && pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP).subtract(ivaEsperado).abs().compareTo(tol) > 0)
                    || (!fiscalidadCompleta && pedido.getIvaMonto().subtract(ivaEsperado).compareTo(tol) > 0))) {
                throw ErrorAplicacionException.validacion("El monto del IVA no coincide con la condición fiscal de los detalles.");
            }
            BigDecimal ivaEfectivo = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : ivaEsperado;
            BigDecimal totalEsperado = subtotalEsperado.add(ivaEfectivo).setScale(2, RoundingMode.HALF_UP);
            if (!importeValido(pedido.getTotalDecimal())
                    || pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP).subtract(totalEsperado).abs().compareTo(tol) > 0) {
                throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles y cálculo de IVA.");
            }
        } else {
            BigDecimal tasa = obtenerTasa(pedido, configuracion);
            CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detalles, tasa);
            if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP).subtract(calc.subtotalUsd()).abs().compareTo(tolEstricta) > 0) {
                throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
            }
            if (pedido.getIvaMonto() != null && pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP).subtract(calc.ivaUsd()).abs().compareTo(tolEstricta) > 0) {
                throw ErrorAplicacionException.validacion("El monto del IVA no coincide con la condición fiscal de los detalles.");
            }
            if (!importeValido(pedido.getTotalDecimal())
                    || pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP).subtract(calc.totalUsd()).abs().compareTo(tolEstricta) > 0) {
                throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles y cálculo de IVA.");
            }
        }
    }

    private boolean tieneFiscalidadCompleta(List<DetallePedido> detalles) {
        return detalles != null && detalles.stream().allMatch(d -> d != null && d.getAplicaIva() != null);
    }

    private BigDecimal calcularBaseImponible(List<DetallePedido> detalles, BigDecimal tasa) {
        return calcularImporteFiscal(detalles, true, tasa);
    }

    private BigDecimal calcularExento(List<DetallePedido> detalles, BigDecimal tasa) {
        return calcularImporteFiscal(detalles, false, tasa);
    }

    private BigDecimal calcularImporteFiscal(List<DetallePedido> detalles, boolean gravado, BigDecimal tasa) {
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        for (DetallePedido detalle : detalles) {
            if (detalle == null) continue;
            if (Boolean.valueOf(gravado).equals(detalle.getAplicaIva())) {
                BigDecimal sub = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(detalle.getCantidad()));
                if (gravado && Boolean.TRUE.equals(detalle.getPrecioIncluyeIva()) && detalle.getIvaPorcentaje() != null && detalle.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal factor = BigDecimal.ONE.add(detalle.getIvaPorcentaje().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                    sub = sub.divide(factor, 2, RoundingMode.HALF_UP);
                }
                if ("VES".equalsIgnoreCase(detalle.getMonedaBase()) && tasa != null && tasa.signum() > 0) {
                    sub = sub.divide(tasa, 2, RoundingMode.HALF_UP);
                }
                importe = importe.add(sub);
            }
        }
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean importeValido(BigDecimal importe) {
        return importe != null && importe.signum() >= 0 && importe.scale() <= 2
                && importe.precision() - importe.scale() <= 12;
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

        Font fontTicketDestacado = new Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD, BaseColor.BLACK);
        Paragraph pNumeroTicket = new Paragraph(String.format("TICKET / FACTURA N°: #%08d", pedido.getId()), fontTicketDestacado);
        pNumeroTicket.setAlignment(Element.ALIGN_CENTER);
        pNumeroTicket.setSpacingAfter(2f);
        documento.add(pNumeroTicket);

        Font fontNegrita = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);
        Font fontNormal = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);

        Paragraph info = new Paragraph();
        info.setFont(fontNormal);
        info.add(new Phrase("Fecha: " + texto(pedido.getFecha()) + "\n", fontNegrita));
        info.add("N° Pedido: " + pedido.getId() + "\n");
        if (pedido.esPedidoRapido()) {
            info.add("Modalidad: Pedido rápido para llevar\n");
        } else {
            info.add("Sala: " + texto(pedido.getSala()) + "   N° Mesa: " + pedido.getNum_mesa() + "\n");
        }
        info.add("Atendido: " + texto(pedido.getUsuario()) + "\n");
        info.add("Cliente: " + texto(pedido.getClienteNombre()) + "\n");
        info.add("Cédula/RIF: " + texto(pedido.getClienteDocumento()) + "\n");
        info.add("Método de Pago: " + textoFormaPago(pedido) + "\n");
        documento.add(info);

        Paragraph sep2 = new Paragraph("----------------------------------------------------------------", fontSep);
        sep2.setAlignment(Element.ALIGN_CENTER);
        documento.add(sep2);
    }

    private void agregarEncabezadoPreFactura80mm(Document documento, Pedidos pedido, Config configuracion, Image imagen)
            throws DocumentException {
        if (imagen != null) {
            imagen.setAlignment(Element.ALIGN_CENTER);
            imagen.scaleToFit(120f, 40f);
            documento.add(imagen);
        }
        Font fontTitulo = new Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.BLACK);
        Font fontDatos = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
        Font fontSep = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.GRAY);
        Font fontPreFacTit = new Font(Font.FontFamily.HELVETICA, 11f, Font.BOLD, BaseColor.BLACK);
        Font fontAvisoNoFiscal = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.DARK_GRAY);

        Paragraph pNombre = new Paragraph(texto(configuracion.getNombre()), fontTitulo);
        pNombre.setAlignment(Element.ALIGN_CENTER);
        documento.add(pNombre);

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

        Paragraph pBanner = new Paragraph("=== PRE-FACTURA / CUENTA ===", fontPreFacTit);
        pBanner.setAlignment(Element.ALIGN_CENTER);
        documento.add(pBanner);

        Paragraph pSubBanner = new Paragraph("(COMPROBANTE NO FISCAL - PENDIENTE DE PAGO)", fontAvisoNoFiscal);
        pSubBanner.setAlignment(Element.ALIGN_CENTER);
        pSubBanner.setSpacingAfter(3f);
        documento.add(pSubBanner);

        Font fontTicketDestacado = new Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD, BaseColor.BLACK);
        Paragraph pNumeroTicket = new Paragraph(String.format("PEDIDO N°: #%08d", pedido.getId()), fontTicketDestacado);
        pNumeroTicket.setAlignment(Element.ALIGN_CENTER);
        pNumeroTicket.setSpacingAfter(2f);
        documento.add(pNumeroTicket);

        Font fontNormal = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK);
        Font fontNegrita = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.BLACK);

        Paragraph info = new Paragraph();
        info.setFont(fontNormal);
        String fechaActual = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        info.add(new Phrase("Fecha/Hora: " + fechaActual + "\n", fontNegrita));
        if (pedido.esPedidoRapido()) {
            info.add("Modalidad: Pedido rápido para llevar\n");
        } else {
            info.add("Sala: " + texto(pedido.getSala()) + "   N° Mesa: " + pedido.getNum_mesa() + "\n");
        }
        String mesonero = (pedido.getMesoneroNombre() != null && !pedido.getMesoneroNombre().isBlank())
                ? pedido.getMesoneroNombre() : pedido.getUsuario();
        info.add("Atendido por: " + texto(mesonero) + "\n");
        if (pedido.getClienteNombre() != null && !pedido.getClienteNombre().isBlank()) {
            info.add("Cliente: " + texto(pedido.getClienteNombre()) + "\n");
        }
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
        tabla.setWidths(new float[]{9f, 38f, 26.5f, 26.5f});
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell[] cabeceras = {
            new PdfPCell(new Phrase("Cant.", negrita)),
            new PdfPCell(new Phrase("Plato", negrita)),
            new PdfPCell(new Phrase("P. Unit.\nUSD / Bs.", negrita)),
            new PdfPCell(new Phrase("Total\nUSD / Bs.", negrita))
        };
        cabeceras[2].setHorizontalAlignment(Element.ALIGN_RIGHT);
        cabeceras[3].setHorizontalAlignment(Element.ALIGN_RIGHT);
        cabeceras[2].setPaddingRight(1f);
        cabeceras[3].setPaddingRight(1f);
        for (PdfPCell celda : cabeceras) {
            celda.setBorder(Rectangle.BOTTOM);
            celda.setBorderWidth(0.5f);
            celda.setPaddingBottom(2f);
            tabla.addCell(celda);
        }

        boolean esLegacy = detalles.stream().anyMatch(d -> d == null || d.getAplicaIva() == null || d.getMonedaBase() == null || d.getPrecioIncluyeIva() == null);
        CalculoMonetarioPedido.CalculoPedido calc = !esLegacy ? CalculoMonetarioPedido.calcularPedido(detalles, tasa) : null;

        for (int i = 0; i < detalles.size(); i++) {
            DetallePedido detalle = detalles.get(i);
            BigDecimal precioUsd;
            BigDecimal subtotalUsd;
            BigDecimal precioBs;
            BigDecimal subtotalBs;

            if (esLegacy || calc == null) {
                if ("VES".equalsIgnoreCase(detalle.getMonedaBase())) {
                    precioBs = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    subtotalBs = precioBs.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                    precioUsd = (tasa != null && tasa.signum() > 0) ? precioBs.divide(tasa, 2, RoundingMode.HALF_UP) : precioBs;
                    subtotalUsd = (tasa != null && tasa.signum() > 0) ? subtotalBs.divide(tasa, 2, RoundingMode.HALF_UP) : subtotalBs;
                } else {
                    precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    subtotalUsd = precioUsd.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                    precioBs = (tasa != null && tasa.signum() > 0) ? precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : precioUsd;
                    subtotalBs = (tasa != null && tasa.signum() > 0) ? subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : subtotalUsd;
                }
            } else {
                CalculoMonetarioPedido.CalculoLinea linea = calc.lineas().get(i);
                subtotalUsd = linea.subtotalUsd(tasa);
                subtotalBs = linea.subtotalVes();
                precioUsd = subtotalUsd.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
                precioBs = subtotalBs.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            }

            PdfPCell cCant = new PdfPCell(new Phrase(String.valueOf(detalle.getCantidad()), cuerpo));
            PdfPCell cPlato = new PdfPCell(new Phrase(detalle.getNombre(), cuerpo));
            String precioUsdTexto = "$" + String.format(java.util.Locale.US, "%.2f", precioUsd);
            String precioBsTexto = "Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs);
            String subtotalUsdTexto = "$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd);
            String subtotalBsTexto = "Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs);
            PdfPCell cUnit = new PdfPCell(new Phrase(precioUsdTexto + "\n" + precioBsTexto,
                    fuenteMontoTicket(precioUsdTexto, precioBsTexto)));
            PdfPCell cTot = new PdfPCell(new Phrase(subtotalUsdTexto + "\n" + subtotalBsTexto,
                    fuenteMontoTicket(subtotalUsdTexto, subtotalBsTexto)));

            cCant.setBorder(Rectangle.NO_BORDER);
            cPlato.setBorder(Rectangle.NO_BORDER);
            cUnit.setBorder(Rectangle.NO_BORDER);
            cTot.setBorder(Rectangle.NO_BORDER);
            cUnit.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cTot.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cUnit.setPaddingLeft(1f);
            cTot.setPaddingLeft(1f);
            cUnit.setPaddingRight(1f);
            cTot.setPaddingRight(1f);

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

    private Font fuenteMontoTicket(String... lineas) {
        float anchoColumna = (ANCHO_80MM - 20f) * 0.265f;
        float anchoDisponible = anchoColumna - 2f;
        for (float tamaño = 6.5f; tamaño >= 4.0f; tamaño -= 0.25f) {
            Font fuente = new Font(Font.FontFamily.HELVETICA, tamaño, Font.NORMAL, BaseColor.BLACK);
            BaseFont base = fuente.getCalculatedBaseFont(false);
            boolean cabe = true;
            for (String linea : lineas) {
                if (base.getWidthPoint(linea, tamaño) > anchoDisponible) {
                    cabe = false;
                    break;
                }
            }
            if (cabe) {
                return fuente;
            }
        }
        return new Font(Font.FontFamily.HELVETICA, 4.0f, Font.NORMAL, BaseColor.BLACK);
    }

    private void agregarCierreTicket80mm(Document documento, Pedidos pedido, Config configuracion, List<DetallePedido> detalles)
            throws DocumentException {
        agregarCierreTicket80mm(documento, pedido, configuracion, detalles, false);
    }

    private void agregarCierreTicket80mm(Document documento, Pedidos pedido, Config configuracion, List<DetallePedido> detalles, boolean esPreFactura)
            throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        boolean esLegacy = detalles.stream().anyMatch(d -> d == null || d.getAplicaIva() == null || d.getMonedaBase() == null || d.getPrecioIncluyeIva() == null);
        CalculoMonetarioPedido.CalculoPedido calc = !esLegacy ? CalculoMonetarioPedido.calcularPedido(detalles, tasa) : null;

        BigDecimal totalUsd = pedido.getTotalDecimal() != null ? pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP)
                : (calc != null ? calc.totalUsd() : BigDecimal.ZERO.setScale(2));
        BigDecimal totalBs = pedido.getTotalBs() != null ? pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP)
                : (calc != null ? calc.totalVes() : totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP));

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        boolean tieneIva = ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0;

        Font fontSep = new Font(Font.FontFamily.COURIER, 7, Font.NORMAL, BaseColor.DARK_GRAY);
        Paragraph sep = new Paragraph("----------------------------------------------------------------", fontSep);
        sep.setAlignment(Element.ALIGN_CENTER);
        documento.add(sep);

        StringBuilder sb = new StringBuilder();

        if (esLegacy || calc == null) {
            BigDecimal exentoUsd = tieneFiscalidadCompleta(detalles)
                    ? calcularExento(detalles, tasa)
                    : BigDecimal.ZERO.setScale(2);
            boolean tieneExento = exentoUsd.compareTo(BigDecimal.ZERO) > 0;

            if (tieneIva || tieneExento) {
                BigDecimal subtotalUsd = pedido.getSubtotal() != null ? pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd;
                BigDecimal ivaUsd = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                BigDecimal subtotalBs = pedido.getSubtotalBs() != null ? pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP) : subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP) : ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

                sb.append("Subtotal: $").append(String.format(java.util.Locale.US, "%.2f", subtotalUsd))
                  .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", subtotalBs)).append(")\n");

                if (tieneIva) {
                    BigDecimal baseImpUsd = tieneFiscalidadCompleta(detalles)
                            ? calcularBaseImponible(detalles, tasa)
                            : (ivaUsd.compareTo(BigDecimal.ZERO) > 0
                                    ? ivaUsd.multiply(new BigDecimal("100")).divide(ivaPorcentaje, 2, RoundingMode.HALF_UP).min(subtotalUsd)
                                    : BigDecimal.ZERO.setScale(2));
                    BigDecimal exentoCalculado = tieneFiscalidadCompleta(detalles)
                            ? exentoUsd : subtotalUsd.subtract(baseImpUsd).max(BigDecimal.ZERO);
                    BigDecimal baseImpBs = baseImpUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal exentoBs = exentoCalculado.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    if (baseImpBs.compareTo(BigDecimal.ZERO) > 0) {
                        sb.append("Base Imponible: $").append(String.format(java.util.Locale.US, "%.2f", baseImpUsd))
                          .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", baseImpBs)).append(")\n");
                    }
                    if (exentoCalculado.compareTo(BigDecimal.ZERO) > 0) {
                        sb.append("Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoCalculado))
                          .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                    }
                    String ivaPct = String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP));
                    sb.append("IVA (").append(ivaPct).append("%): $")
                      .append(String.format(java.util.Locale.US, "%.2f", ivaUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", ivaBs)).append(")\n");
                } else {
                    BigDecimal exentoBs = exentoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    sb.append("Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                }
            }
        } else {
            BigDecimal subtotalUsd = calc.subtotalUsd();
            BigDecimal subtotalBs = calc.subtotalVes();
            BigDecimal ivaUsd = calc.ivaUsd();
            BigDecimal ivaBs = calc.ivaVes();
            totalUsd = calc.totalUsd();
            totalBs = calc.totalVes();

            BigDecimal exentoBs = calc.exentoVes();
            BigDecimal baseImpBs = calc.baseImponibleVes();
            BigDecimal exentoUsd = calc.exentoUsd();
            BigDecimal baseImpUsd = calc.baseImponibleUsd();

            boolean tieneExento = exentoBs.compareTo(BigDecimal.ZERO) > 0;
            boolean aplicaIvaEnTotales = tieneIva && ivaBs.compareTo(BigDecimal.ZERO) > 0;

            if (aplicaIvaEnTotales || tieneExento) {
                sb.append("Subtotal: $").append(String.format(java.util.Locale.US, "%.2f", subtotalUsd))
                  .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", subtotalBs)).append(")\n");

                if (aplicaIvaEnTotales) {
                    if (baseImpBs.compareTo(BigDecimal.ZERO) > 0) {
                        sb.append("Base Imponible: $").append(String.format(java.util.Locale.US, "%.2f", baseImpUsd))
                          .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", baseImpBs)).append(")\n");
                    }
                    if (tieneExento) {
                        sb.append("Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoUsd))
                          .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                    }
                    String ivaPct = String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP));
                    sb.append("IVA (").append(ivaPct).append("%): $")
                      .append(String.format(java.util.Locale.US, "%.2f", ivaUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", ivaBs)).append(")\n");
                } else {
                    sb.append("Monto Exento: $").append(String.format(java.util.Locale.US, "%.2f", exentoUsd))
                      .append(" (Bs. ").append(String.format(java.util.Locale.US, "%.2f", exentoBs)).append(")\n");
                }
            }
        }

        String etiTasa = (pedido != null && pedido.isTasaEsEstimada()) ? "Tasa (Estimada):" : "Tasa de Cambio:";
        sb.append(etiTasa).append(" Bs. ").append(tasa.setScale(4, RoundingMode.HALF_UP).toPlainString()).append(" / $\n")
          .append("Total USD: $").append(String.format(java.util.Locale.US, "%.2f", totalUsd)).append("\n")
          .append("TOTAL A PAGAR (Bs.): Bs. ").append(String.format(java.util.Locale.US, "%.2f", totalBs));

        Font fuenteResumen = new Font(Font.FontFamily.HELVETICA, 8.0f, Font.BOLD, BaseColor.BLACK);
        Paragraph resumen = new Paragraph(sb.toString(), fuenteResumen);
        resumen.setAlignment(Element.ALIGN_CENTER);
        documento.add(resumen);

        if (esPreFactura) {
            Paragraph aviso = new Paragraph(
                    "\n*** COMPROBANTE PROVISIONAL (NO VÁLIDO COMO FACTURA) ***\n"
                    + "Favor indicar su forma de pago al mesonero o en caja:\n"
                    + "Efectivo $, Efectivo Bs., Pago Móvil, Tarjeta o Mixto\n\n"
                    + "¡Muchas gracias por su preferencia!\n",
                    new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.DARK_GRAY));
            aviso.setAlignment(Element.ALIGN_CENTER);
            documento.add(aviso);
        } else {
            Paragraph firma = new Paragraph("Forma de Pago: " + textoFormaPago(pedido) + "\n\n------------------------------------\nFirma\n",
                    new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, BaseColor.BLACK));
            firma.setAlignment(Element.ALIGN_CENTER);
            documento.add(firma);

            Paragraph agradecimiento = new Paragraph(texto(configuracion.getMensaje()),
                    new Font(Font.FontFamily.HELVETICA, 7.5f, Font.ITALIC, BaseColor.DARK_GRAY));
            agradecimiento.setAlignment(Element.ALIGN_CENTER);
            documento.add(agradecimiento);
        }
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
        Paragraph informacion = new Paragraph("FACTURA / CONTROL N°: #" + String.format("%08d", pedido.getId())
                + "\nAtendido: " + texto(pedido.getUsuario())
                + "\nFecha: " + texto(pedido.getFecha())
                + (pedido.esPedidoRapido()
                        ? "\nModalidad: Pedido rápido para llevar"
                        : "\nSala: " + texto(pedido.getSala()) + "\nN° Mesa: " + pedido.getNum_mesa())
                + "\nCliente: " + texto(pedido.getClienteNombre())
                + "\nCédula/RIF: " + texto(pedido.getClienteDocumento())
                + "\nMétodo de Pago: " + textoFormaPago(pedido));
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

        boolean esLegacy = detalles.stream().anyMatch(d -> d == null || d.getAplicaIva() == null || d.getMonedaBase() == null || d.getPrecioIncluyeIva() == null);
        CalculoMonetarioPedido.CalculoPedido calc = !esLegacy ? CalculoMonetarioPedido.calcularPedido(detalles, tasa) : null;

        for (int i = 0; i < detalles.size(); i++) {
            DetallePedido detalle = detalles.get(i);
            BigDecimal precioUsd;
            BigDecimal subtotalUsd;
            BigDecimal precioBs;
            BigDecimal subtotalBs;

            if (esLegacy || calc == null) {
                if ("VES".equalsIgnoreCase(detalle.getMonedaBase())) {
                    precioBs = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    subtotalBs = precioBs.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                    precioUsd = (tasa != null && tasa.signum() > 0) ? precioBs.divide(tasa, 2, RoundingMode.HALF_UP) : precioBs;
                    subtotalUsd = (tasa != null && tasa.signum() > 0) ? subtotalBs.divide(tasa, 2, RoundingMode.HALF_UP) : subtotalBs;
                } else {
                    precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.HALF_UP);
                    subtotalUsd = precioUsd.multiply(BigDecimal.valueOf(detalle.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                    precioBs = (tasa != null && tasa.signum() > 0) ? precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : precioUsd;
                    subtotalBs = (tasa != null && tasa.signum() > 0) ? subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP) : subtotalUsd;
                }
            } else {
                CalculoMonetarioPedido.CalculoLinea linea = calc.lineas().get(i);
                subtotalUsd = linea.subtotalUsd(tasa);
                subtotalBs = linea.subtotalVes();
                precioUsd = subtotalUsd.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
                precioBs = subtotalBs.divide(BigDecimal.valueOf(detalle.getCantidad()), 2, RoundingMode.HALF_UP);
            }

            tabla.addCell(String.valueOf(detalle.getCantidad()));
            tabla.addCell(detalle.getNombre());
            tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", precioUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", precioBs) + ")");
            tabla.addCell("$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")");
        }
        documento.add(tabla);
    }

    private void agregarCierre(Document documento, Pedidos pedido, Config configuracion, List<DetallePedido> detalles) throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        boolean esLegacy = detalles.stream().anyMatch(d -> d == null || d.getAplicaIva() == null || d.getMonedaBase() == null || d.getPrecioIncluyeIva() == null);
        CalculoMonetarioPedido.CalculoPedido calc = !esLegacy ? CalculoMonetarioPedido.calcularPedido(detalles, tasa) : null;

        BigDecimal totalUsd = pedido.getTotalDecimal() != null ? pedido.getTotalDecimal().setScale(2, RoundingMode.HALF_UP)
                : (calc != null ? calc.totalUsd() : BigDecimal.ZERO.setScale(2));
        BigDecimal totalBs = pedido.getTotalBs() != null ? pedido.getTotalBs().setScale(2, RoundingMode.HALF_UP)
                : (calc != null ? calc.totalVes() : totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP));

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        boolean tieneIva = ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0;

        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(55);
        tablaTotales.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tablaTotales.setWidths(new float[]{45f, 55f});
        tablaTotales.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        Font fontEtiq = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD);
        Font fontVal = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL);
        Font fontTotEtiq = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);
        Font fontTotVal = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);

        if (esLegacy || calc == null) {
            BigDecimal exentoUsd = tieneFiscalidadCompleta(detalles)
                    ? calcularExento(detalles, tasa)
                    : BigDecimal.ZERO.setScale(2);
            boolean tieneExento = exentoUsd.compareTo(BigDecimal.ZERO) > 0;

            if (tieneIva || tieneExento) {
                BigDecimal subtotalUsd = pedido.getSubtotal() != null ? pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd;
                BigDecimal ivaUsd = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                BigDecimal subtotalBs = pedido.getSubtotalBs() != null ? pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP) : subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP) : ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

                agregarFilaTotales(tablaTotales, "Subtotal:", "$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")", fontEtiq, fontVal, 8f);

                if (tieneIva) {
                    BigDecimal baseImpUsd = tieneFiscalidadCompleta(detalles)
                            ? calcularBaseImponible(detalles, tasa)
                            : (ivaUsd.compareTo(BigDecimal.ZERO) > 0
                                    ? ivaUsd.multiply(new BigDecimal("100")).divide(ivaPorcentaje, 2, RoundingMode.HALF_UP).min(subtotalUsd)
                                    : BigDecimal.ZERO.setScale(2));
                    BigDecimal exentoCalculado = tieneFiscalidadCompleta(detalles)
                            ? exentoUsd : subtotalUsd.subtract(baseImpUsd).max(BigDecimal.ZERO);
                    BigDecimal baseImpBs = baseImpUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal exentoBs = exentoCalculado.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    if (baseImpBs.compareTo(BigDecimal.ZERO) > 0) {
                        agregarFilaTotales(tablaTotales, "Base Imponible:", "$" + String.format(java.util.Locale.US, "%.2f", baseImpUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", baseImpBs) + ")", fontEtiq, fontVal, 8f);
                    }
                    if (exentoCalculado.compareTo(BigDecimal.ZERO) > 0) {
                        agregarFilaTotales(tablaTotales, "Monto Exento:", "$" + String.format(java.util.Locale.US, "%.2f", exentoCalculado) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", exentoBs) + ")", fontEtiq, fontVal, 8f);
                    }
                    String ivaPct = String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP));
                    agregarFilaTotales(tablaTotales, "IVA (" + ivaPct + "%):", "$" + String.format(java.util.Locale.US, "%.2f", ivaUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", ivaBs) + ")", fontEtiq, fontVal, 8f);
                } else {
                    BigDecimal exentoBs = exentoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    agregarFilaTotales(tablaTotales, "Monto Exento:", "$" + String.format(java.util.Locale.US, "%.2f", exentoUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", exentoBs) + ")", fontEtiq, fontVal, 8f);
                }
            }
        } else {
            BigDecimal subtotalUsd = calc.subtotalUsd();
            BigDecimal subtotalBs = calc.subtotalVes();
            BigDecimal ivaUsd = calc.ivaUsd();
            BigDecimal ivaBs = calc.ivaVes();
            totalUsd = calc.totalUsd();
            totalBs = calc.totalVes();

            BigDecimal exentoBs = calc.exentoVes();
            BigDecimal baseImpBs = calc.baseImponibleVes();
            BigDecimal exentoUsd = calc.exentoUsd();
            BigDecimal baseImpUsd = calc.baseImponibleUsd();

            boolean tieneExento = exentoBs.compareTo(BigDecimal.ZERO) > 0;
            boolean aplicaIvaEnTotales = tieneIva && ivaBs.compareTo(BigDecimal.ZERO) > 0;

            if (aplicaIvaEnTotales || tieneExento) {
                agregarFilaTotales(tablaTotales, "Subtotal:", "$" + String.format(java.util.Locale.US, "%.2f", subtotalUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", subtotalBs) + ")", fontEtiq, fontVal, 8f);

                if (aplicaIvaEnTotales) {
                    if (baseImpBs.compareTo(BigDecimal.ZERO) > 0) {
                        agregarFilaTotales(tablaTotales, "Base Imponible:", "$" + String.format(java.util.Locale.US, "%.2f", baseImpUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", baseImpBs) + ")", fontEtiq, fontVal, 8f);
                    }
                    if (tieneExento) {
                        agregarFilaTotales(tablaTotales, "Monto Exento:", "$" + String.format(java.util.Locale.US, "%.2f", exentoUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", exentoBs) + ")", fontEtiq, fontVal, 8f);
                    }
                    String ivaPct = String.format(java.util.Locale.US, "%.2f", ivaPorcentaje.setScale(2, RoundingMode.HALF_UP));
                    agregarFilaTotales(tablaTotales, "IVA (" + ivaPct + "%):", "$" + String.format(java.util.Locale.US, "%.2f", ivaUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", ivaBs) + ")", fontEtiq, fontVal, 8f);
                } else {
                    agregarFilaTotales(tablaTotales, "Monto Exento:", "$" + String.format(java.util.Locale.US, "%.2f", exentoUsd) + " (Bs. " + String.format(java.util.Locale.US, "%.2f", exentoBs) + ")", fontEtiq, fontVal, 8f);
                }
            }
        }

        String etiTasa = (pedido != null && pedido.isTasaEsEstimada()) ? "Tasa (Estimada):" : "Tasa de Cambio:";
        agregarFilaTotales(tablaTotales, etiTasa, "Bs. " + tasa.setScale(4, RoundingMode.HALF_UP).toPlainString() + " / $", fontEtiq, fontVal, 8f);
        agregarFilaTotales(tablaTotales, "Total USD:", "$" + String.format(java.util.Locale.US, "%.2f", totalUsd), fontEtiq, fontVal, 8f);
        agregarFilaTotales(tablaTotales, "TOTAL A PAGAR (Bs.):", "Bs. " + String.format(java.util.Locale.US, "%.2f", totalBs), fontTotEtiq, fontTotVal, 8f, true);

        documento.add(Chunk.NEWLINE);
        documento.add(tablaTotales);

        if (pedido != null && pedido.isTasaEsEstimada()) {
            Font fuenteNota = new Font(Font.FontFamily.TIMES_ROMAN, 9, Font.ITALIC);
            Paragraph notaEstimada = new Paragraph("* NOTA: Tasa de cambio estimada por migración histórica del sistema.", fuenteNota);
            notaEstimada.setAlignment(Element.ALIGN_CENTER);
            documento.add(notaEstimada);
        }

        Paragraph firma = new Paragraph("Forma de Pago: " + textoFormaPago(pedido) + "\n\n------------------------------------\nFirma\n");
        firma.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(firma);

        Paragraph agradecimiento = new Paragraph(texto(configuracion.getMensaje()));
        agradecimiento.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(agradecimiento);
    }

    private void agregarFilaTotales(PdfPTable tabla, String etiqueta, String valor, Font fontEtiq, Font fontVal, float paddingDerecho) {
        agregarFilaTotales(tabla, etiqueta, valor, fontEtiq, fontVal, paddingDerecho, false);
    }

    private void agregarFilaTotales(PdfPTable tabla, String etiqueta, String valor, Font fontEtiq, Font fontVal, float paddingDerecho, boolean esTotalFinal) {
        PdfPCell cEtiq = new PdfPCell(new Phrase(etiqueta, fontEtiq));
        cEtiq.setBorder(Rectangle.NO_BORDER);
        cEtiq.setHorizontalAlignment(Element.ALIGN_LEFT);
        cEtiq.setPaddingTop(esTotalFinal ? 3f : 1.5f);
        cEtiq.setPaddingBottom(esTotalFinal ? 3f : 1.5f);

        PdfPCell cVal = new PdfPCell(new Phrase(valor, fontVal));
        cVal.setBorder(Rectangle.NO_BORDER);
        cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cVal.setPaddingRight(paddingDerecho);
        cVal.setPaddingTop(esTotalFinal ? 3f : 1.5f);
        cVal.setPaddingBottom(esTotalFinal ? 3f : 1.5f);

        if (esTotalFinal) {
            cEtiq.setBorder(Rectangle.TOP);
            cEtiq.setBorderWidth(0.5f);
            cVal.setBorder(Rectangle.TOP);
            cVal.setBorderWidth(0.5f);
        }

        tabla.addCell(cEtiq);
        tabla.addCell(cVal);
    }

    BigDecimal obtenerTasa(Pedidos pedido, Config configuracion) {
        boolean tasaEstimada = pedido != null && pedido.isTasaEsEstimada();

        // Nivel 1: Tasa guardada explícitamente en el pedido (solo si es real / no estimada)
        if (!tasaEstimada && pedido != null && pedido.getTasaCambio() != null && pedido.getTasaCambio().compareTo(BigDecimal.ZERO) > 0) {
            return pedido.getTasaCambio().setScale(4, RoundingMode.HALF_UP);
        }

        // Nivel 2: Inferencia contable interna del pedido
        if (pedido != null) {
            // 2.A: Por total Bs / total USD
            if (pedido.getTotalBs() != null && pedido.getTotalDecimal() != null
                    && pedido.getTotalDecimal().compareTo(BigDecimal.ZERO) > 0
                    && pedido.getTotalBs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tasaInferida = pedido.getTotalBs().divide(pedido.getTotalDecimal(), 4, RoundingMode.HALF_UP);
                if (tasaInferida.compareTo(BigDecimal.ZERO) > 0) {
                    return tasaInferida;
                }
            }
            // 2.B: Por histórico del IVA: iva_bs / iva_monto
            if (pedido.getIvaBs() != null && pedido.getIvaMonto() != null
                    && pedido.getIvaMonto().compareTo(BigDecimal.ZERO) > 0
                    && pedido.getIvaBs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tasaIva = pedido.getIvaBs().divide(pedido.getIvaMonto(), 4, RoundingMode.HALF_UP);
                if (tasaIva.compareTo(BigDecimal.ZERO) > 0) {
                    return tasaIva;
                }
            }
            // 2.C: Por subtotal: subtotal_bs / subtotal
            if (pedido.getSubtotalBs() != null && pedido.getSubtotal() != null
                    && pedido.getSubtotal().compareTo(BigDecimal.ZERO) > 0
                    && pedido.getSubtotalBs().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tasaSubtotal = pedido.getSubtotalBs().divide(pedido.getSubtotal(), 4, RoundingMode.HALF_UP);
                if (tasaSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                    return tasaSubtotal;
                }
            }
        }

        // Nivel 2.D: Si era tasa estimada por migración y falló inferencia contable directa,
        // usar la tasa estimada guardada (se advertirá en el cuerpo del documento)
        if (tasaEstimada && pedido != null && pedido.getTasaCambio() != null && pedido.getTasaCambio().compareTo(BigDecimal.ZERO) > 0) {
            return pedido.getTasaCambio().setScale(4, RoundingMode.HALF_UP);
        }

        // Nivel 3: Inferencia a partir de registros con la misma fecha
        if (pedido != null && pedido.getFecha() != null && !pedido.getFecha().isBlank() && resolvedorTasaHistorica != null) {
            BigDecimal tasaMismaFecha = resolvedorTasaHistorica.resolver(pedido.getFecha(), pedido.getId());
            if (tasaMismaFecha != null && tasaMismaFecha.compareTo(BigDecimal.ZERO) > 0) {
                LOGGER.info("El pedido #" + pedido.getId() + " no poseía tasa ni totales para inferirla; resuelta a partir de registros de su misma fecha ("
                        + pedido.getFecha() + "): " + tasaMismaFecha);
                return tasaMismaFecha.setScale(4, RoundingMode.HALF_UP);
            }
        }

        // Nivel 4: Si el pedido está PENDIENTE (o en curso/no cerrado), es legítimo usar la tasa configurada actual
        boolean esHistorico = pedido != null && ("FINALIZADO".equalsIgnoreCase(pedido.getEstado())
                || "ANULADO".equalsIgnoreCase(pedido.getEstado()));

        if (!esHistorico && configuracion != null && configuracion.getTasaDolar() != null
                && configuracion.getTasaDolar().compareTo(BigDecimal.ZERO) > 0) {
            return configuracion.getTasaDolar().setScale(4, RoundingMode.HALF_UP);
        }

        // Si es un pedido histórico (FINALIZADO/ANULADO) y fallaron las inferencias directas del pedido,
        // no se permite conjeturar tasas de otros registros ni aplicar la tasa actual para evitar discrepancias fiscales.
        if (esHistorico) {
            throw ErrorAplicacionException.validacion(
                    "No se puede generar la factura del pedido histórico #" + pedido.getId()
                            + " (" + pedido.getEstado() + ", fecha " + pedido.getFecha() + "): "
                            + "no posee tasa de cambio registrada ni fue posible inferirla con exactitud a partir de sus montos de IVA o totales en Bs. "
                            + "Para preservar la integridad contable y fiscal, no se permite conjeturar la tasa a partir de otros pedidos ni aplicar la tasa actual a pedidos cerrados.");
        }

        // Si no hay tasa válida en pedido ni en configuración, no se permite inventar un fallback
        throw ErrorAplicacionException.validacion(
                "No se puede generar la factura: no hay una tasa de cambio válida guardada en el pedido ni configurada en el sistema.");
    }

    private String textoFormaPago(Pedidos pedido) {
        if (pedido.getPagos() == null || pedido.getPagos().isEmpty()) return texto(pedido.getMetodoPago());
        java.util.List<String> lineas = new java.util.ArrayList<>();
        for (Modelo.PagoPedido pago : pedido.getPagos()) {
            String metodo = switch (pago.metodo()) {
                case "EFECTIVO_BS" -> "Efectivo Bs.";
                case "EFECTIVO_USD" -> "Efectivo USD";
                case "PAGO_MOVIL" -> "Pago móvil";
                case "CORTESIA" -> "Cortesía / Casa";
                default -> pago.metodo();
            };
            lineas.add(metodo + " " + String.format(java.util.Locale.US, "%.2f", pago.monto()) + " " + pago.moneda());
        }
        return lineas.size() == 1 ? lineas.getFirst() : "MIXTO: " + String.join(" + ", lineas);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
