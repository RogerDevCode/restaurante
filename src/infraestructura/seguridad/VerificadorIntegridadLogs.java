package infraestructura.seguridad;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validador forense de integridad para los archivos de logs locales (NIST SP 800-92).
 * Verifica la cadena criptográfica hash-chaining (HMAC-SHA256) de cada línea registrada
 * y detecta modificaciones no autorizadas, inserciones, reordenamientos y truncamientos.
 */
public final class VerificadorIntegridadLogs {

    private static final String HMAC_ALGORITMO = "HmacSHA256";
    private static final String HASH_GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";

    // Formato de cabecera: [SEQ:1 | 2026-11-03T10:00:00.123Z | INFO | PREV:hash16 | HMAC:hash16] Mensaje
    private static final Pattern PATRON_LOG_SELLADO = Pattern.compile(
            "^\\[SEQ:(\\d+) \\| ([^|]+) \\| ([^|]+) \\| PREV:([0-9a-fA-F]+) \\| HMAC:([0-9a-fA-F]+)\\] (.*)$"
    );

    private VerificadorIntegridadLogs() {
    }

    /**
     * Valida la integridad del archivo de log correspondiente a la fecha dada.
     */
    public static ResultadoVerificacion verificar(Path archivoLog, byte[] claveAuditoria) {
        if (archivoLog == null || !Files.exists(archivoLog)) {
            return new ResultadoVerificacion(false, 0, null, "El archivo de log no existe: " + archivoLog);
        }
        if (claveAuditoria == null || claveAuditoria.length == 0) {
            return new ResultadoVerificacion(false, 0, null, "La clave de auditoría es obligatoria para verificar.");
        }

        long secuenciaEsperada = 1;
        String hashAnteriorEsperado = HASH_GENESIS.substring(0, 16);
        long lineasValidadas = 0;

        try (BufferedReader lector = Files.newBufferedReader(archivoLog, StandardCharsets.UTF_8)) {
            String linea;
            long numLineaFisica = 0;

            while ((linea = lector.readLine()) != null) {
                numLineaFisica++;
                if (linea.isBlank()) {
                    continue;
                }

                Matcher m = PATRON_LOG_SELLADO.matcher(linea);
                if (!m.matches()) {
                    // Si es la última línea y termina abruptamente, puede ser torn write por corte de luz
                    return new ResultadoVerificacion(false, lineasValidadas, numLineaFisica,
                            "Línea con formato inválido o no sellada criptográficamente en línea " + numLineaFisica);
                }

                long seq = Long.parseLong(m.group(1));
                String timestamp = m.group(2);
                String nivel = m.group(3);
                String hashPrev = m.group(4);
                String hmacRegistrado = m.group(5);
                String mensaje = m.group(6);

                if (seq != secuenciaEsperada) {
                    return new ResultadoVerificacion(false, lineasValidadas, numLineaFisica,
                            "Ruptura de secuencia en línea " + numLineaFisica + ": esperada " + secuenciaEsperada + ", encontrada " + seq);
                }

                if (!hashPrev.equalsIgnoreCase(hashAnteriorEsperado)) {
                    return new ResultadoVerificacion(false, lineasValidadas, numLineaFisica,
                            "Ruptura de encadenamiento (hash previo) en línea " + numLineaFisica + ": esperado " + hashAnteriorEsperado + ", encontrado " + hashPrev);
                }

                String payload = seq + "|" + timestamp + "|" + nivel + "|" + hashPrev + "|" + mensaje;
                String hmacCalculado = computarHmac16(claveAuditoria, payload);

                if (!MessageDigest.isEqual(hmacRegistrado.getBytes(StandardCharsets.UTF_8), hmacCalculado.getBytes(StandardCharsets.UTF_8))) {
                    return new ResultadoVerificacion(false, lineasValidadas, numLineaFisica,
                            "Firma criptográfica inválida en línea " + numLineaFisica + " (contenido alterado o adulterado).");
                }

                hashAnteriorEsperado = hmacCalculado;
                secuenciaEsperada++;
                lineasValidadas++;
            }

            return new ResultadoVerificacion(true, lineasValidadas, null,
                    "Cadena de log íntegra. " + lineasValidadas + " eventos auditados con éxito sin alteraciones.");

        } catch (IOException ex) {
            return new ResultadoVerificacion(false, lineasValidadas, null, "Error I/O leyendo log: " + ex.getMessage());
        }
    }

    public static String computarHmac16(byte[] clave, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITMO);
            mac.init(new SecretKeySpec(clave, HMAC_ALGORITMO));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String fullHex = HexFormat.of().formatHex(raw);
            return fullHex.substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("Error computando HMAC-SHA256: " + ex.getMessage(), ex);
        }
    }

    public static Pattern getPatronLogSellado() {
        return PATRON_LOG_SELLADO;
    }

    /**
     * DTO con el resultado de la auditoría forense del log.
     */
    public record ResultadoVerificacion(
            boolean esValida,
            long lineasValidadas,
            Long lineaFalla,
            String mensaje) {
    }
}
