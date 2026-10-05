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

/** Compone documentos de pedido sin consultar MySQL ni abrir aplicaciones externas. */
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
                agregarDetalles(documento, detalles);
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
        if (!importeValido(pedido.getTotalDecimal())
                || pedido.getTotalDecimal().setScale(2, RoundingMode.UNNECESSARY).compareTo(totalDetalles) != 0) {
            throw ErrorAplicacionException.validacion("El total del pedido no coincide con sus detalles.");
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
        return "Ruc:    " + texto(configuracion.getRuc())
                + "\nNombre: " + texto(configuracion.getNombre())
                + "\nTeléfono: " + texto(configuracion.getTelefono())
                + "\nDirección: " + texto(configuracion.getDireccion());
    }

    private void agregarDetalles(Document documento, List<DetallePedido> detalles) throws DocumentException {
        Font negrita = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD, BaseColor.BLUE);
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.getDefaultCell().setBorder(0);
        tabla.setWidths(new float[]{10f, 50f, 15f, 15f});
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell[] cabeceras = {
            new PdfPCell(new Phrase("Cant.", negrita)),
            new PdfPCell(new Phrase("Plato.", negrita)),
            new PdfPCell(new Phrase("P. unt.", negrita)),
            new PdfPCell(new Phrase("P. Total", negrita))
        };
        for (PdfPCell celda : cabeceras) {
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setBackgroundColor(BaseColor.LIGHT_GRAY);
            tabla.addCell(celda);
        }
        for (DetallePedido detalle : detalles) {
            BigDecimal precio = detalle.getPrecioDecimal().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(detalle.getCantidad()));
            tabla.addCell(String.valueOf(detalle.getCantidad()));
            tabla.addCell(detalle.getNombre());
            tabla.addCell(precio.toPlainString());
            tabla.addCell(subtotal.toPlainString());
        }
        documento.add(tabla);
    }

    private void agregarCierre(Document documento, Pedidos pedido, Config configuracion) throws DocumentException {
        Paragraph totalPedido = new Paragraph("Total S/: "
                + pedido.getTotalDecimal().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        totalPedido.setAlignment(Element.ALIGN_RIGHT);
        documento.add(Chunk.NEWLINE);
        documento.add(totalPedido);
        Paragraph firma = new Paragraph("Cancelación \n\n------------------------------------\nFirma\n");
        firma.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(firma);
        Paragraph agradecimiento = new Paragraph(texto(configuracion.getMensaje()));
        agradecimiento.setAlignment(Element.ALIGN_CENTER);
        documento.add(Chunk.NEWLINE);
        documento.add(agradecimiento);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
