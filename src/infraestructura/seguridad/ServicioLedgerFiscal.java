package infraestructura.seguridad;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Servicio de Hash Chaining Fiscal y Ledger Inmutable (RFC 8785 inspired).
 * Garantiza que cada Factura cobrada, Anulación y Cierre Z quede sellado
 * en una cadena criptográfica acumulativa e inalterable en MySQL.
 *
 * Emplea bloqueo pesimista en base de datos ('SELECT ... FOR UPDATE') sobre
 * la tabla ancla para prevenir bifurcaciones ('chain forking') ante transacciones
 * concurrentes en hora pico.
 */
public final class ServicioLedgerFiscal {

    private static final Logger LOGGER = Logger.getLogger(ServicioLedgerFiscal.class.getName());
    private static final String HASH_GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String HMAC_ALGORITMO = "HmacSHA256";
    private static final byte[] SALT_DERIVACION = "CAPIP_LEDGER_FISCAL_SALT_2026".getBytes(StandardCharsets.UTF_8);

    private ServicioLedgerFiscal() {
    }

    /**
     * Registra una Factura Cobrada (FINALIZADO) en el ledger fiscal inmutable.
     */
    public static synchronized void registrarFactura(
            Connection con,
            int idPedido,
            String correlativo,
            LocalDateTime fecha,
            BigDecimal subtotal,
            BigDecimal iva,
            BigDecimal total,
            String rucEmpresa) throws SQLException {

        String corr = correlativo != null ? correlativo.trim() : "FAC-" + idPedido;
        String fechaStr = fecha != null ? fecha.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String subtotalStr = formatoMoneda(subtotal);
        String ivaStr = formatoMoneda(iva);
        String totalStr = formatoMoneda(total);
        String rucStr = sanitizarRif(rucEmpresa);

        String payloadParcial = "FACTURA|id=" + idPedido
                + "|corr=" + corr
                + "|fecha=" + fechaStr
                + "|sub=" + subtotalStr
                + "|iva=" + ivaStr
                + "|tot=" + totalStr
                + "|rif=" + rucStr;

        anexarBloque(con, "FACTURA", idPedido, corr, payloadParcial);
    }

    /**
     * Registra una Anulación de Factura (Contra-asiento formal) en el ledger fiscal inmutable.
     */
    public static synchronized void registrarAnulacionFactura(
            Connection con,
            int idPedido,
            String correlativo,
            LocalDateTime fecha,
            BigDecimal totalRevertido,
            String motivo,
            String rucEmpresa) throws SQLException {

        String corr = correlativo != null ? correlativo.trim() : "ANUL-" + idPedido;
        String fechaStr = fecha != null ? fecha.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String totalStr = formatoMoneda(totalRevertido);
        String mot = motivo != null ? motivo.replaceAll("[|\\r\\n]", " ").trim() : "ANULACION";
        String rucStr = sanitizarRif(rucEmpresa);

        String payloadParcial = "ANULACION|id=" + idPedido
                + "|corr=" + corr
                + "|fecha=" + fechaStr
                + "|tot_rev=" + totalStr
                + "|motivo=" + mot
                + "|rif=" + rucStr;

        anexarBloque(con, "ANULACION_FACTURA", idPedido, corr, payloadParcial);
    }

    /**
     * Registra un Cierre Z (Cierre definitivo de jornada) en el ledger fiscal inmutable.
     */
    public static synchronized void registrarCierreZ(
            Connection con,
            int idCierre,
            String fechaJornada,
            BigDecimal ventasBs,
            BigDecimal ventasUsd,
            String rucEmpresa) throws SQLException {

        String jornadaStr = fechaJornada != null ? fechaJornada.trim() : LocalDate.now().toString();
        String bsStr = formatoMoneda(ventasBs);
        String usdStr = formatoMoneda(ventasUsd);
        String rucStr = sanitizarRif(rucEmpresa);
        String corr = "Z-" + jornadaStr;

        String payloadParcial = "CIERRE_Z|id=" + idCierre
                + "|jornada=" + jornadaStr
                + "|bs=" + bsStr
                + "|usd=" + usdStr
                + "|rif=" + rucStr;

        anexarBloque(con, "CIERRE_Z", idCierre, corr, payloadParcial);
    }

    /**
     * Anexa un nuevo bloque atómico con cerrojo pesimista 'FOR UPDATE' sobre el ancla del ledger.
     */
    private static void anexarBloque(
            Connection con,
            String tipoEvento,
            int referenciaId,
            String correlativo,
            String payloadParcial) throws SQLException {

        asegurarTablasLedger(con);

        long secuenciaActual = 0;
        String hashAnterior = HASH_GENESIS;

        // 1. Bloqueo pesimista del ancla para evitar Chain Forking
        String sqlAncla = "SELECT secuencia, hash_actual FROM ledger_fiscal_ancla WHERE id = 1 FOR UPDATE";
        try (PreparedStatement psAncla = con.prepareStatement(sqlAncla);
             ResultSet rsAncla = psAncla.executeQuery()) {

            if (rsAncla.next()) {
                secuenciaActual = rsAncla.getLong("secuencia");
                hashAnterior = rsAncla.getString("hash_actual");
            } else {
                // Inicializar ancla génesis si no existía fila 1
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("INSERT IGNORE INTO ledger_fiscal_ancla (id, secuencia, hash_actual) VALUES (1, 0, '" + HASH_GENESIS + "')");
                }
            }
        }

        long secuenciaNueva = secuenciaActual + 1;
        String payloadCanonico = payloadParcial + "|ant=" + hashAnterior;

        ValidadorLicencia lic = ValidadorLicencia.getInstancia();
        String keyEpochId = lic.isActiva() ? lic.getIdLicencia() : "DEV-GENESIS-KEY";
        byte[] claveHmac = derivarClaveHmac(keyEpochId);

        String hashActual = computarHmacHex(claveHmac, payloadCanonico);

        // 2. Insertar bloque histórico
        String sqlInsert = """
            INSERT INTO ledger_fiscal
                (secuencia, tipo_evento, referencia_id, correlativo, payload_canonico, hash_anterior, hash_actual, key_epoch_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {
            ps.setLong(1, secuenciaNueva);
            ps.setString(2, tipoEvento);
            ps.setInt(3, referenciaId);
            ps.setString(4, correlativo);
            ps.setString(5, payloadCanonico);
            ps.setString(6, hashAnterior);
            ps.setString(7, hashActual);
            ps.setString(8, keyEpochId);
            ps.executeUpdate();
        }

        // 3. Actualizar ancla
        String sqlUpdateAncla = "UPDATE ledger_fiscal_ancla SET secuencia = ?, hash_actual = ? WHERE id = 1";
        try (PreparedStatement psUp = con.prepareStatement(sqlUpdateAncla)) {
            psUp.setLong(1, secuenciaNueva);
            psUp.setString(2, hashActual);
            psUp.executeUpdate();
        }

        LOGGER.fine(() -> "Bloque ledger fiscal #" + secuenciaNueva + " [" + tipoEvento + "] sellado con éxito.");
    }

    /**
     * Audita la cadena criptográfica completa desde el bloque 1 hasta el ancla.
     * Retorna si la cadena está íntegra o si fue manipulada, truncada o bifurcada.
     */
    public static ResultadoAuditoriaLedger verificarIntegridadCadena(Connection con) {
        try {
            asegurarTablasLedger(con);

            long secuenciaEsperada = 1;
            String hashAnteriorEsperado = HASH_GENESIS;
            long bloquesVerificados = 0;

            String sql = "SELECT secuencia, tipo_evento, payload_canonico, hash_anterior, hash_actual, key_epoch_id FROM ledger_fiscal ORDER BY secuencia ASC";
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                while (rs.next()) {
                    long seq = rs.getLong("secuencia");
                    String payload = rs.getString("payload_canonico");
                    String hAnt = rs.getString("hash_anterior");
                    String hAct = rs.getString("hash_actual");
                    String keyEpoch = rs.getString("key_epoch_id");

                    // 1. Verificación de monotonía secuencial (sin saltos ni duplicados)
                    if (seq != secuenciaEsperada) {
                        return new ResultadoAuditoriaLedger(false, bloquesVerificados, seq,
                                "Falla de secuencia: se esperaba #" + secuenciaEsperada + " pero se encontró #" + seq);
                    }

                    // 2. Verificación de enlace con el bloque previo
                    if (!hashAnteriorEsperado.equalsIgnoreCase(hAnt)) {
                        return new ResultadoAuditoriaLedger(false, bloquesVerificados, seq,
                                "Ruptura de enlace en bloque #" + seq + ": hash anterior no coincide con bloque previo.");
                    }

                    // 3. Verificación de recálculo criptográfico del digest HMAC
                    byte[] claveHmac = derivarClaveHmac(keyEpoch);
                    String hashRecalculado = computarHmacHex(claveHmac, payload);
                    if (!hashRecalculado.equalsIgnoreCase(hAct)) {
                        return new ResultadoAuditoriaLedger(false, bloquesVerificados, seq,
                                "Falsificación de contenido en bloque #" + seq + ": el HMAC recomputado no coincide.");
                    }

                    hashAnteriorEsperado = hAct;
                    secuenciaEsperada++;
                    bloquesVerificados++;
                }
            }

            // 4. Verificación de coincidencia con el ancla de base de datos
            try (Statement stAncla = con.createStatement();
                 ResultSet rsAncla = stAncla.executeQuery("SELECT secuencia, hash_actual FROM ledger_fiscal_ancla WHERE id = 1")) {

                if (rsAncla.next()) {
                    long seqAncla = rsAncla.getLong("secuencia");
                    String hashAncla = rsAncla.getString("hash_actual");

                    if (seqAncla != bloquesVerificados) {
                        return new ResultadoAuditoriaLedger(false, bloquesVerificados, seqAncla,
                                "Desincronización de ancla: el ancla marca secuencia " + seqAncla + " pero hay " + bloquesVerificados + " bloques.");
                    }
                    if (bloquesVerificados > 0 && !hashAnteriorEsperado.equalsIgnoreCase(hashAncla)) {
                        return new ResultadoAuditoriaLedger(false, bloquesVerificados, seqAncla,
                                "Desincronización de ancla: el hash final no coincide con el ancla registrada.");
                    }
                }
            }

            return new ResultadoAuditoriaLedger(true, bloquesVerificados, null,
                    "Cadena criptográfica íntegra. " + bloquesVerificados + " eventos fiscales validados sin alteraciones.");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo durante la auditoría del ledger fiscal: " + ex.getMessage(), ex);
            return new ResultadoAuditoriaLedger(false, 0, null, "Error ejecutando auditoría: " + ex.getMessage());
        }
    }

    /**
     * Asegura la creación idempotente de las tablas del ledger fiscal.
     */
    public static void asegurarTablasLedger(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ledger_fiscal_ancla (
                    id INT NOT NULL PRIMARY KEY,
                    secuencia BIGINT NOT NULL DEFAULT 0,
                    hash_actual VARCHAR(64) NOT NULL,
                    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);

            st.executeUpdate("INSERT IGNORE INTO ledger_fiscal_ancla (id, secuencia, hash_actual) VALUES (1, 0, '" + HASH_GENESIS + "')");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ledger_fiscal (
                    secuencia BIGINT NOT NULL PRIMARY KEY,
                    tipo_evento VARCHAR(30) NOT NULL,
                    referencia_id INT NOT NULL,
                    correlativo VARCHAR(60) NOT NULL,
                    payload_canonico TEXT NOT NULL,
                    hash_anterior VARCHAR(64) NOT NULL,
                    hash_actual VARCHAR(64) NOT NULL,
                    key_epoch_id VARCHAR(64) NOT NULL,
                    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_ledger_referencia (tipo_evento, referencia_id),
                    INDEX idx_ledger_creado (creado_en)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_spanish_ci
                """);
        }
    }

    private static String formatoMoneda(BigDecimal monto) {
        if (monto == null) {
            return "0.00";
        }
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String sanitizarRif(String ruc) {
        if (ruc == null || ruc.isBlank()) {
            ValidadorLicencia lic = ValidadorLicencia.getInstancia();
            if (lic.isActiva() && lic.getRif() != null && !lic.getRif().isBlank()) {
                return lic.getRif();
            }
            return "V-00000000";
        }
        return ruc.replaceAll("[|\\r\\n]", "").trim();
    }

    private static byte[] derivarClaveHmac(String keyEpochId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT_DERIVACION);
            if (keyEpochId != null) {
                md.update(keyEpochId.getBytes(StandardCharsets.UTF_8));
            }
            return md.digest();
        } catch (Exception ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", ex);
        }
    }

    private static String computarHmacHex(byte[] clave, String mensaje) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITMO);
            mac.init(new SecretKeySpec(clave, HMAC_ALGORITMO));
            byte[] rawHmac = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Error computando HMAC-SHA256: " + ex.getMessage(), ex);
        }
    }

    /**
     * DTO inmutable con el resultado de la auditoría forense del ledger.
     */
    public record ResultadoAuditoriaLedger(
            boolean esValida,
            long bloquesVerificados,
            Long bloqueFalla,
            String mensaje) {
    }
}
