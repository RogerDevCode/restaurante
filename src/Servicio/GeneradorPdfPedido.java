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

/** Compone documentos de pedido con desglose en Bolívares y Dólares. */
public final class GeneradorPdfPedido {
    private final Path directorioSalida;

    public GeneradorPdfPedido(Path directorioSalida) {
        if (directorioSalida == null) {
            throw ErrorAplicacionException.validacion("El directorio de salida del PDF es obligatorio.");
        }
        this.directorioSalida = directorioSalida;
    }

    public Path generar(Pedidos pedido, Config configuracion, List<DetallePedido> detalles) {
        validar(pedido, configuracion, detalles);
        Path salida = directorioSalida.resolve("pedido-" + pedido.getId() + ".pdf");
        Document documento = new Document();
        try {
            Files.createDirectories(directorioSalida);
            try (OutputStream archivo = Files.newOutputStream(salida)) {
                PdfWriter.getInstance(documento, archivo);
                documento.open();
                agregarEncabezado(documento, pedido, configuracion);
                agregarDetalles(documento, pedido, configuracion, detalles);
                agregarCierre(documento, pedido, configuracion);
                documento.close();
            }
            return salida;
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
        BigDecimal ivaEsperado = BigDecimal.ZERO;
        BigDecimal totalEsperado = subtotalEsperado;

        if (pedido.getIvaPorcentaje() != null && pedido.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
            ivaEsperado = subtotalEsperado.multiply(pedido.getIvaPorcentaje()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalEsperado = subtotalEsperado.add(ivaEsperado).setScale(2, RoundingMode.HALF_UP);
        }

        if (pedido.getSubtotal() != null && pedido.getSubtotal().setScale(2, RoundingMode.UNNECESSARY).compareTo(subtotalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El subtotal del pedido no coincide con sus detalles.");
        }
        if (pedido.getIvaMonto() != null && pedido.getIvaMonto().setScale(2, RoundingMode.UNNECESSARY).compareTo(ivaEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El monto del IVA no coincide con el porcentaje aplicado.");
        }
        if (!importeValido(pedido.getTotalDecimal())
                || pedido.getTotalDecimal().setScale(2, RoundingMode.UNNECESSARY).compareTo(totalEsperado) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles y cálculo de IVA.");
        }
    }

    private boolean importeValido(BigDecimal importe) {
        return importe != null && importe.signum() >= 0 && importe.scale() <= 2
                && importe.precision() - importe.scale() <= 8;
    }

    private void agregarEncabezado(Document documento, Pedidos pedido, Config configuracion)
            throws DocumentException, IOException {
        java.net.URL recursoLogo = GeneradorPdfPedido.class.getResource("/Img/logo.png");
        if (recursoLogo == null) {
            throw new IOException("No está disponible el recurso requerido /Img/logo.png.");
        }
        Image imagen = Image.getInstance(recursoLogo);
        PdfPTable encabezado = new PdfPTable(4);
        encabezado.setWidthPercentage(100);
        encabezado.getDefaultCell().setBorder(0);
        encabezado.setWidths(new float[]{20f, 20f, 60f, 60f});
        encabezado.setHorizontalAlignment(Element.ALIGN_LEFT);
        encabezado.addCell(imagen);
        encabezado.addCell("");
        encabezado.addCell(datosEmpresa(configuracion));
        Paragraph informacion = new Paragraph("Atendido: " + texto(pedido.getUsuario())
                + "\nN° Pedido: " + pedido.getId()
                + "\nFecha: " + texto(pedido.getFecha())
                + "\nSala: " + texto(pedido.getSala())
                + "\nN° Mesa: " + pedido.getNum_mesa());
        encabezado.addCell(informacion);
        documento.add(encabezado);
        documento.add(Chunk.NEWLINE);
    }

    private String datosEmpresa(Config configuracion) {
        return "RIF/RUC: " + texto(configuracion.getRuc())
                + "\nNombre: " + texto(configuracion.getNombre())
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
            BigDecimal precioUsd = detalle.getPrecioDecimal().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal subtotalUsd = precioUsd.multiply(BigDecimal.valueOf(detalle.getCantidad()));
            BigDecimal precioBs = precioUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalBs = subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            tabla.addCell(String.valueOf(detalle.getCantidad()));
            tabla.addCell(detalle.getNombre());
            tabla.addCell("$" + precioUsd.toPlainString() + " (Bs. " + precioBs.toPlainString() + ")");
            tabla.addCell("$" + subtotalUsd.toPlainString() + " (Bs. " + subtotalBs.toPlainString() + ")");
        }
        documento.add(tabla);
    }

    private void agregarCierre(Document documento, Pedidos pedido, Config configuracion) throws DocumentException {
        BigDecimal tasa = obtenerTasa(pedido, configuracion);
        BigDecimal totalUsd = pedido.getTotalDecimal().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal totalBs = pedido.getTotalBs() != null ? pedido.getTotalBs() : totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ivaPorcentaje = pedido.getIvaPorcentaje();
        boolean tieneIva = ivaPorcentaje != null && ivaPorcentaje.compareTo(BigDecimal.ZERO) > 0;

        StringBuilder sb = new StringBuilder();
        if (tieneIva) {
            BigDecimal subtotalUsd = pedido.getSubtotal() != null ? pedido.getSubtotal().setScale(2, RoundingMode.HALF_UP) : totalUsd;
            BigDecimal ivaUsd = pedido.getIvaMonto() != null ? pedido.getIvaMonto().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            BigDecimal subtotalBs = pedido.getSubtotalBs() != null ? pedido.getSubtotalBs().setScale(2, RoundingMode.HALF_UP) : subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ivaBs = pedido.getIvaBs() != null ? pedido.getIvaBs().setScale(2, RoundingMode.HALF_UP) : ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

            sb.append("Subtotal: $").append(subtotalUsd.toPlainString())
              .append(" (Bs. ").append(subtotalBs.toPlainString()).append(")\n");
            sb.append("IVA (").append(ivaPorcentaje.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("%): $")
              .append(ivaUsd.toPlainString())
              .append(" (Bs. ").append(ivaBs.toPlainString()).append(")\n");
        }

        sb.append("Tasa de Cambio: Bs. ").append(tasa.setScale(4, RoundingMode.HALF_UP).toPlainString()).append(" / $\n")
          .append("Total USD: $").append(totalUsd.toPlainString()).append("\n")
          .append("TOTAL A PAGAR (Bs.): Bs. ").append(totalBs.toPlainString());

        Font fuenteResumen = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);
        Paragraph resumen = new Paragraph(sb.toString(), fuenteResumen);
        resumen.setAlignment(Element.ALIGN_RIGHT);
        documento.add(Chunk.NEWLINE);
        documento.add(resumen);

        Paragraph firma = new Paragraph("Cancelación \n\n------------------------------------\nFirma\n");
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
