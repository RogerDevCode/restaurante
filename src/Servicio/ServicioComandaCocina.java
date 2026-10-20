package Servicio;

import Modelo.DetallePedido;
import Modelo.ComandaMarcha;
import Modelo.MovimientoComanda;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio encargado de componer e imprimir de forma directa y silenciosa
 * la comanda de cocina en la tickera de 80 mm del restaurante.
 * No incluye precios ni información monetaria/fiscal; resalta cantidades,
 * nombres de platos y notas especiales de preparación.
 */
public final class ServicioComandaCocina {

    private static final Logger LOGGER = Logger.getLogger(ServicioComandaCocina.class.getName());
    private static final float ANCHO_80MM = 226.77f; // 80 mm en puntos tipográficos
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private ServicioComandaCocina() {
    }

    /**
     * Genera el documento PDF de la comanda de cocina optimizado para tickera térmica de 80 mm.
     */
    public static Path generarDocumentoComanda(
            int idPedido,
            String nombreSala,
            int numMesa,
            String nombreMesonero,
            List<DetallePedido> detalles,
            boolean esActualizacion) throws IOException {

        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("No hay detalles de pedido para generar la comanda de cocina.");
        }
        List<MovimientoComanda> movimientos = detalles.stream()
                .map(det -> MovimientoComanda.desde(MovimientoComanda.Accion.PREPARAR, det, det.getCantidad()))
                .toList();
        ComandaMarcha.Tipo tipo = esActualizacion ? ComandaMarcha.Tipo.INCREMENTO : ComandaMarcha.Tipo.INICIAL;
        return generarDocumentoComanda(new ComandaMarcha(0, idPedido, esActualizacion ? 2 : 1, tipo, movimientos),
                nombreSala, numMesa, nombreMesonero);
    }

    /** Genera un ticket fiel a una marcha persistida, incluyendo las acciones de ajuste. */
    public static Path generarDocumentoComanda(
            ComandaMarcha marcha, String nombreSala, int numMesa, String nombreMesonero) throws IOException {

        if (marcha == null || marcha.movimientos().isEmpty() || marcha.tipo() == ComandaMarcha.Tipo.BASE_LEGADA) {
            throw new IllegalArgumentException("No hay detalles de pedido para generar la comanda de cocina.");
        }

        Path dirSalida = Paths.get("tickets", "comandas");
        Files.createDirectories(dirSalida);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path archivoPdf = dirSalida.resolve("comanda-" + marcha.idPedido() + "_m" + marcha.secuencia() + "_" + timestamp + ".pdf");

        // Calcular altura estimada del ticket
        int totalLineas = marcha.movimientos().size();
        int totalComentarios = 0;
        int totalPreparar = 0;
        int totalRetirar = 0;
        for (MovimientoComanda d : marcha.movimientos()) {
            if (d.accion() == MovimientoComanda.Accion.PREPARAR) totalPreparar += d.cantidad();
            else totalRetirar += d.cantidad();
            if (d.comentario() != null && !d.comentario().trim().isEmpty()) {
                totalComentarios++;
            }
        }

        float alto = 160f + (totalLineas * 24f) + (totalComentarios * 18f);
        alto = Math.max(alto, 200f);

        Rectangle tamanoTicket = new Rectangle(ANCHO_80MM, alto);
        Document doc = new Document(tamanoTicket, 8f, 8f, 10f, 12f);

        try (OutputStream os = Files.newOutputStream(archivoPdf)) {
            PdfWriter.getInstance(doc, os);
            doc.open();

            Font fTitulo = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Font fSubtitulo = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
            Font fEncabezadoMesa = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD);
            Font fTexto = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL);
            Font fTextoBold = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
            Font fItemCant = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
            Font fItemDesc = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
            Font fNota = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD | Font.ITALIC);

            // 1. Título principal
            String titulo = switch (marcha.tipo()) {
                case INICIAL -> "=== COMANDA DE COCINA ===";
                case INCREMENTO -> "=== COMANDA PARCIAL ===";
                case AJUSTE -> "=== AJUSTE DE COMANDA ===";
                case BASE_LEGADA -> throw new IllegalArgumentException("Una marcha de respaldo no se imprime.");
            };
            Paragraph pTit = new Paragraph(titulo, fTitulo);
            pTit.setAlignment(Element.ALIGN_CENTER);
            doc.add(pTit);

            // 2. Identificación destacada de la Mesa y Sala
            String mesaTexto;
            if (numMesa <= 0) {
                mesaTexto = "PEDIDO RÁPIDO / PARA LLEVAR";
            } else {
                mesaTexto = "MESA: " + numMesa + (nombreSala != null && !nombreSala.isBlank() ? " (" + nombreSala.toUpperCase() + ")" : "");
            }
            Paragraph pMesa = new Paragraph(mesaTexto, fEncabezadoMesa);
            pMesa.setAlignment(Element.ALIGN_CENTER);
            pMesa.setSpacingBefore(3f);
            pMesa.setSpacingAfter(4f);
            doc.add(pMesa);

            // 3. Metadatos del pedido (ID, fecha/hora, mesonero)
            doc.add(new Paragraph("Pedido: #" + marcha.idPedido() + "    Marcha: #" + marcha.secuencia()
                    + "    Hora: " + LocalDateTime.now().format(FORMATO_FECHA), fTexto));
            String mesoneroTexto = (nombreMesonero != null && !nombreMesonero.isBlank()) ? nombreMesonero : "N/D";
            doc.add(new Paragraph("Mesonero/Atiende: " + mesoneroTexto, fTextoBold));

            // Separador
            Paragraph sep = new Paragraph("----------------------------------------------------------------", fTexto);
            sep.setSpacingBefore(2f);
            sep.setSpacingAfter(2f);
            doc.add(sep);

            // 4. Cabecera de ítems
            // Reservar ancho suficiente para "PREPARAR [cantidad]" sin salir del papel de 80 mm.
            PdfPTable tablaItems = new PdfPTable(new float[]{75f, 135f});
            tablaItems.setWidthPercentage(100);

            PdfPCell cCantTit = new PdfPCell(new Phrase("ACCIÓN / CANT.", fTextoBold));
            cCantTit.setBorder(Rectangle.NO_BORDER);
            cCantTit.setHorizontalAlignment(Element.ALIGN_CENTER);
            tablaItems.addCell(cCantTit);

            PdfPCell cDescTit = new PdfPCell(new Phrase("DESCRIPCIÓN / PLATO", fTextoBold));
            cDescTit.setBorder(Rectangle.NO_BORDER);
            tablaItems.addCell(cDescTit);

            // 5. Filas de platos y notas
            for (MovimientoComanda det : marcha.movimientos()) {
                String accion = det.accion() == MovimientoComanda.Accion.PREPARAR ? "PREPARAR" : "RETIRAR";
                PdfPCell cCant = new PdfPCell(new Phrase(accion + " [" + det.cantidad() + "]", fItemCant));
                cCant.setBorder(Rectangle.NO_BORDER);
                cCant.setHorizontalAlignment(Element.ALIGN_CENTER);
                cCant.setPaddingTop(3f);
                tablaItems.addCell(cCant);

                PdfPCell cDesc = new PdfPCell(new Phrase(det.nombre(), fItemDesc));
                cDesc.setBorder(Rectangle.NO_BORDER);
                cDesc.setPaddingTop(3f);
                tablaItems.addCell(cDesc);

                // Si tiene comentario de preparación, agregar fila con nota indentada
                if (det.comentario() != null && !det.comentario().trim().isEmpty()) {
                    PdfPCell cEspacio = new PdfPCell(new Phrase(""));
                    cEspacio.setBorder(Rectangle.NO_BORDER);
                    tablaItems.addCell(cEspacio);

                    PdfPCell cNota = new PdfPCell(new Phrase(">> NOTA: " + det.comentario().trim(), fNota));
                    cNota.setBorder(Rectangle.NO_BORDER);
                    cNota.setPaddingBottom(3f);
                    tablaItems.addCell(cNota);
                }
            }

            doc.add(tablaItems);

            // Separador de cierre
            doc.add(sep);

            // 6. Resumen de cantidades y pie
            Paragraph pTotales = new Paragraph("Preparar: " + totalPreparar + "   Retirar: " + totalRetirar, fSubtitulo);
            pTotales.setAlignment(Element.ALIGN_RIGHT);
            doc.add(pTotales);

            Paragraph pPie = new Paragraph("*** ENVIADO A COCINA ***", fTextoBold);
            pPie.setAlignment(Element.ALIGN_CENTER);
            pPie.setSpacingBefore(6f);
            doc.add(pPie);

            doc.close();
        } catch (DocumentException ex) {
            throw new IOException("Error generando PDF de comanda de cocina: " + ex.getMessage(), ex);
        }

        return archivoPdf;
    }

    /**
     * Despacha la impresión de la comanda de cocina en un hilo en segundo plano (asíncrono)
     * para no bloquear la interfaz gráfica ni retrasar la atención de la mesa.
     */
    public static void imprimirAsync(
            int idPedido,
            String nombreSala,
            int numMesa,
            String nombreMesonero,
            List<DetallePedido> detalles,
            boolean esActualizacion) {

        List<MovimientoComanda> movimientos = detalles == null ? List.of() : detalles.stream()
                .map(det -> MovimientoComanda.desde(MovimientoComanda.Accion.PREPARAR, det, det.getCantidad()))
                .toList();
        ComandaMarcha.Tipo tipo = esActualizacion ? ComandaMarcha.Tipo.INCREMENTO : ComandaMarcha.Tipo.INICIAL;
        imprimirAsync(new ComandaMarcha(0, idPedido, esActualizacion ? 2 : 1, tipo, movimientos),
                nombreSala, numMesa, nombreMesonero);
    }

    public static void imprimirAsync(ComandaMarcha marcha, String nombreSala, int numMesa, String nombreMesonero) {
        if (marcha == null || marcha.movimientos().isEmpty()) return;
        new Thread(() -> {
            try {
                Path archivoComanda = generarDocumentoComanda(marcha, nombreSala, numMesa, nombreMesonero);
                boolean exito = ServicioImpresionTicket.procesarSalida(archivoComanda);
                if (exito) {
                    LOGGER.info(() -> "Comanda de cocina para pedido #" + marcha.idPedido()
                            + ", marcha #" + marcha.secuencia() + " impresa exitosamente.");
                } else {
                    LOGGER.warning(() -> "Comanda de cocina para pedido #" + marcha.idPedido()
                            + ", marcha #" + marcha.secuencia() + " despachada a visor alternativo.");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "No se pudo imprimir la comanda de cocina del pedido #"
                        + marcha.idPedido() + ", marcha #" + marcha.secuencia() + ": " + ex.getMessage(), ex);
            }
        }, "Hilo-Impresion-Comanda-Cocina").start();
    }
}
