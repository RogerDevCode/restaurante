package infraestructura.impresion;

import Modelo.ComandaMarcha;
import Modelo.MovimientoComanda;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generador nativo de secuencias de bytes binarias ESC/POS para comandas de cocina en tickeras
 * térmicas estándar de 80 mm (Epson, Star, Bixolon, Xprinter).
 * Genera comandos de corte automático, activación de buzzer de cocina y formato de alta visibilidad.
 */
public final class GeneradorEscPosComanda {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Comandos ESC/POS estándar
    private static final byte[] CMD_INIT = new byte[]{0x1B, 0x40};                   // ESC @
    private static final byte[] CMD_ALIGN_LEFT = new byte[]{0x1B, 0x61, 0x00};       // ESC a 0
    private static final byte[] CMD_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01};     // ESC a 1
    private static final byte[] CMD_ALIGN_RIGHT = new byte[]{0x1B, 0x61, 0x02};      // ESC a 2
    private static final byte[] CMD_BOLD_ON = new byte[]{0x1B, 0x45, 0x01};          // ESC E 1
    private static final byte[] CMD_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00};         // ESC E 0
    private static final byte[] CMD_DOUBLE_SIZE = new byte[]{0x1D, 0x21, 0x11};       // GS ! 0x11 (2x Alto + 2x Ancho)
    private static final byte[] CMD_DOUBLE_HEIGHT = new byte[]{0x1D, 0x21, 0x01};     // GS ! 0x01 (2x Alto)
    private static final byte[] CMD_NORMAL_SIZE = new byte[]{0x1D, 0x21, 0x00};       // GS ! 0x00 (Normal)
    private static final byte[] CMD_INVERT_ON = new byte[]{0x1D, 0x42, 0x01};         // GS B 1 (Blanco sobre Negro)
    private static final byte[] CMD_INVERT_OFF = new byte[]{0x1D, 0x42, 0x00};        // GS B 0 (Normal)
    private static final byte[] CMD_BUZZER = new byte[]{0x1B, 0x42, 0x03, 0x02};      // ESC B 3 2 (3 pitidos de aviso)
    private static final byte[] CMD_BELL = new byte[]{0x07};                          // BEL (Pitido alternativo)
    private static final byte[] CMD_CUT_PARTIAL = new byte[]{0x1D, 0x56, 0x42, 0x00}; // GS V B 0 (Corte parcial)

    private GeneradorEscPosComanda() {
    }

    /**
     * Construye los bytes ESC/POS listos para despachar por TCP socket 9100.
     *
     * @param marcha         Ronda o marcha de comanda calculada.
     * @param nombreSala     Nombre de la sala.
     * @param numMesa        Número de mesa (o <= 0 para para llevar / rápido).
     * @param nombreMesonero Nombre del mesonero que atiende.
     * @param esEmergencia   Indica si el ticket se envía en modalidad failover por fallo en cocina.
     * @param activarBuzzer  Indica si se debe hacer sonar la chicharra/campana de la impresora.
     * @return Arreglo de bytes binario ESC/POS.
     */
    public static byte[] generarBytesComanda(
            ComandaMarcha marcha,
            String nombreSala,
            int numMesa,
            String nombreMesonero,
            boolean esEmergencia,
            boolean activarBuzzer) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. Inicialización
        out.write(CMD_INIT);

        // 2. Cabecera de Emergencia / Failover si aplica
        if (esEmergencia) {
            out.write(CMD_ALIGN_CENTER);
            out.write(CMD_INVERT_ON);
            out.write(CMD_BOLD_ON);
            out.write(CMD_DOUBLE_HEIGHT);
            escribirLinea(out, " *** COMANDA DE EMERGENCIA *** ");
            escribirLinea(out, " COCINA OFFLINE / REDIRIGIDO ");
            out.write(CMD_INVERT_OFF);
            out.write(CMD_NORMAL_SIZE);
            out.write(CMD_BOLD_OFF);
            out.write(new byte[]{0x0A});
        }

        // 3. Título de marcha
        out.write(CMD_ALIGN_CENTER);
        out.write(CMD_BOLD_ON);
        String titulo = switch (marcha.tipo()) {
            case INICIAL -> "=== COMANDA DE COCINA ===";
            case INCREMENTO -> "=== COMANDA PARCIAL ===";
            case AJUSTE -> "=== AJUSTE DE COMANDA ===";
            case BASE_LEGADA -> "=== REIMPRESION COMANDA ===";
        };
        escribirLinea(out, titulo);
        out.write(CMD_BOLD_OFF);

        // 4. Identificación Gigante de Mesa y Sala para Cocina
        out.write(CMD_ALIGN_CENTER);
        out.write(CMD_DOUBLE_SIZE);
        out.write(CMD_BOLD_ON);
        if (numMesa <= 0) {
            escribirLinea(out, "PEDIDO RAPIDO");
        } else {
            String salaTexto = (nombreSala != null && !nombreSala.isBlank()) ? " (" + nombreSala.toUpperCase() + ")" : "";
            escribirLinea(out, "MESA #" + numMesa + salaTexto);
        }
        out.write(CMD_NORMAL_SIZE);
        out.write(CMD_BOLD_OFF);

        // 5. Metadatos
        out.write(CMD_ALIGN_LEFT);
        escribirLinea(out, "Pedido: #" + marcha.idPedido() + "   Marcha: #" + marcha.secuencia());
        escribirLinea(out, "Fecha:  " + LocalDateTime.now().format(FORMATO_HORA));
        String mesonero = (nombreMesonero != null && !nombreMesonero.isBlank()) ? nombreMesonero : "N/D";
        out.write(CMD_BOLD_ON);
        escribirLinea(out, "Atiende: " + mesonero);
        out.write(CMD_BOLD_OFF);

        // Separador
        escribirSeparador(out);

        // 6. Cabecera de Columnas
        out.write(CMD_BOLD_ON);
        escribirLinea(out, String.format("%-14s %s", "ACCION / CANT", "DESCRIPCION"));
        out.write(CMD_BOLD_OFF);
        escribirSeparador(out);

        // 7. Lista de Ítems
        int totalPreparar = 0;
        int totalRetirar = 0;

        for (MovimientoComanda m : marcha.movimientos()) {
            if (m.accion() == MovimientoComanda.Accion.PREPARAR) {
                totalPreparar += m.cantidad();
            } else {
                totalRetirar += m.cantidad();
            }

            String etiquetaAccion = (m.accion() == MovimientoComanda.Accion.PREPARAR ? "PREPARAR" : "RETIRAR")
                    + " [" + m.cantidad() + "]";

            out.write(CMD_BOLD_ON);
            out.write(CMD_DOUBLE_HEIGHT);
            escribirLinea(out, String.format("%-14s %s", etiquetaAccion, limpiarTexto(m.nombre())));
            out.write(CMD_NORMAL_SIZE);
            out.write(CMD_BOLD_OFF);

            // Nota de preparación indentada si existe
            if (m.comentario() != null && !m.comentario().trim().isEmpty()) {
                out.write(CMD_BOLD_ON);
                escribirLinea(out, "  >> NOTA: " + limpiarTexto(m.comentario().trim()));
                out.write(CMD_BOLD_OFF);
            }
        }

        escribirSeparador(out);

        // 8. Totales de control
        out.write(CMD_ALIGN_RIGHT);
        out.write(CMD_BOLD_ON);
        escribirLinea(out, "Preparar: " + totalPreparar + "   Retirar: " + totalRetirar);
        out.write(CMD_BOLD_OFF);

        // Pie
        out.write(CMD_ALIGN_CENTER);
        out.write(CMD_BOLD_ON);
        out.write(new byte[]{0x0A});
        escribirLinea(out, "*** ENVIADO A COCINA ***");
        out.write(CMD_BOLD_OFF);

        // 9. Alimentar papel (Feed lines)
        out.write(new byte[]{0x0A, 0x0A, 0x0A, 0x0A});

        // 10. Buzzer si solicitado
        if (activarBuzzer) {
            out.write(CMD_BUZZER);
            out.write(CMD_BELL);
        }

        // 11. Corte parcial de papel
        out.write(CMD_CUT_PARTIAL);

        return out.toByteArray();
    }

    private static void escribirLinea(ByteArrayOutputStream out, String texto) throws IOException {
        String normalizado = limpiarTexto(texto) + "\n";
        out.write(normalizado.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void escribirSeparador(ByteArrayOutputStream out) throws IOException {
        escribirLinea(out, "------------------------------------------------");
    }

    /**
     * Remueve acentos problemáticos que corrompen el juego de caracteres de tickeras genéricas,
     * conservando máxima legibilidad para el personal de cocina.
     */
    public static String limpiarTexto(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "")
                  .replace('ñ', 'n')
                  .replace('Ñ', 'N');
    }
}
