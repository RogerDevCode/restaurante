package infraestructura.seguridad;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validador de Licencia Comercial y Motor de Cifrado Funcional para Restaurante 2026.
 *
 * Arquitectura:
 * 1. Verifica la firma digital ECDSA (SHA256withECDSA) de 'licencia.lic' contra la
 *    clave pública 'capip_publica.der' (embebida en el classpath o en el directorio raíz).
 * 2. Si la firma es auténtica, extrae la clave simétrica AES-256 única del cliente (DEK)
 *    para descifrar y cifrar datos sensibles en la base de datos (formato ENC:AES256GCM:...).
 * 3. Funciona de manera transparente tanto en Linux 24+ como en Windows 10/11.
 */
public final class ValidadorLicencia {

    private static final Logger LOGGER = Logger.getLogger(ValidadorLicencia.class.getName());
    private static final String PREFIJO_ENC = "ENC:AES256GCM:";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes

    private static volatile ValidadorLicencia instancia;

    private final boolean activa;
    private final String idLicencia;
    private final String rif;
    private final String nombreEmpresa;
    private final int maxMesas;
    private final String vencimiento;
    private final SecretKey dekKey;
    private final String errorMensaje;

    private ValidadorLicencia(boolean activa, String id, String rif, String nombre,
                              int maxMesas, String vencimiento, SecretKey dekKey, String error) {
        this.activa = activa;
        this.idLicencia = id != null ? id : "";
        this.rif = rif != null ? rif : "";
        this.nombreEmpresa = nombre != null ? nombre : "";
        this.maxMesas = maxMesas;
        this.vencimiento = vencimiento != null ? vencimiento : "DESCONOCIDO";
        this.dekKey = dekKey;
        this.errorMensaje = error != null ? error : "";
    }

    /**
     * Obtiene la instancia singleton del validador de licencias.
     */
    public static ValidadorLicencia getInstancia() {
        if (instancia == null) {
            synchronized (ValidadorLicencia.class) {
                if (instancia == null) {
                    instancia = cargarLicenciaDesdeSistema();
                }
            }
        }
        return instancia;
    }

    /**
     * Fuerza la recarga de la licencia (útil tras copiar un nuevo archivo licencia.lic).
     */
    public static synchronized void recargar() {
        instancia = cargarLicenciaDesdeSistema();
    }

    private static ValidadorLicencia cargarLicenciaDesdeSistema() {
        Path pathLicencia = localizarArchivoLicencia();
        if (pathLicencia == null || !Files.isRegularFile(pathLicencia)) {
            LOGGER.log(Level.INFO, "No se encontró archivo 'licencia.lic'. Operando en modo predeterminado.");
            return new ValidadorLicencia(false, "", "", "", 0, "", null, "Archivo licencia.lic no encontrado");
        }

        try {
            PublicKey pubKey = cargarClavePublica();
            if (pubKey == null) {
                LOGGER.log(Level.WARNING, "No se localizó la clave pública capip_publica.der. No se puede autenticar la licencia.");
                return new ValidadorLicencia(false, "", "", "", 0, "", null, "Clave pública CAPIP no disponible");
            }

            // Leer archivo licencia.lic
            String contenido = Files.readString(pathLicencia, StandardCharsets.UTF_8);
            String payloadB64 = null;
            String firmaB64 = null;

            for (String linea : contenido.split("\\R")) {
                linea = linea.trim();
                if (linea.startsWith("PAYLOAD_B64=")) {
                    payloadB64 = linea.substring("PAYLOAD_B64=".length()).trim();
                } else if (linea.startsWith("FIRMA_B64=")) {
                    firmaB64 = linea.substring("FIRMA_B64=".length()).trim();
                }
            }

            if (payloadB64 == null || firmaB64 == null) {
                return new ValidadorLicencia(false, "", "", "", 0, "", null, "Formato de licencia.lic corrupto o inválido");
            }

            byte[] payloadBytes = Base64.getDecoder().decode(payloadB64);
            byte[] firmaBytes = Base64.getDecoder().decode(firmaB64);

            // Verificar la firma criptográfica con la Clave Pública de CAPIP
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(pubKey);
            verifier.update(payloadBytes);

            if (!verifier.verify(firmaBytes)) {
                LOGGER.log(Level.SEVERE, "¡ALERTA DE SEGURIDAD! La firma digital de 'licencia.lic' NO coincide. Archivo alterado o falsificado.");
                return new ValidadorLicencia(false, "", "", "", 0, "", null, "Firma digital no válida (licencia alterada)");
            }

            // Parsear propiedades del payload
            Properties props = new Properties();
            props.load(new java.io.ByteArrayInputStream(payloadBytes));

            String licId = props.getProperty("LICENCIA_ID", "");
            String rif = props.getProperty("RIF", "");
            String nombre = props.getProperty("NOMBRE", "");
            String maxMesasStr = props.getProperty("MAX_MESAS", "0");
            String vence = props.getProperty("VENCIMIENTO", "PERPETUA");
            String dekHex = props.getProperty("DEK_AES_256", "");

            int maxMesas = 0;
            try {
                maxMesas = Integer.parseInt(maxMesasStr);
            } catch (NumberFormatException ignored) {
            }

            // Validar vencimiento si tiene formato de fecha ISO
            if (!"PERPETUA".equalsIgnoreCase(vence)) {
                try {
                    LocalDate fechaVence = LocalDate.parse(vence);
                    if (LocalDate.now().isAfter(fechaVence)) {
                        LOGGER.log(Level.WARNING, "La licencia comercial ha expirado el " + fechaVence);
                        return new ValidadorLicencia(false, licId, rif, nombre, maxMesas, vence, null, "Licencia expirada el " + vence);
                    }
                } catch (DateTimeParseException ignored) {
                }
            }

            // Derivar la clave simétrica AES-256
            SecretKey dekKey = null;
            if (dekHex != null && dekHex.length() == 64) {
                byte[] dekRaw = HexFormat.of().parseHex(dekHex);
                dekKey = new SecretKeySpec(dekRaw, "AES");
            }

            LOGGER.log(Level.INFO, "Licencia comercial verificada exitosamente. Cliente: {0} | RIF: {1}",
                    new Object[]{nombre, rif});

            return new ValidadorLicencia(true, licId, rif, nombre, maxMesas, vence, dekKey, null);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo durante la validación criptográfica de la licencia: " + ex.getMessage(), ex);
            return new ValidadorLicencia(false, "", "", "", 0, "", null, "Error interno: " + ex.getMessage());
        }
    }

    private static Path localizarArchivoLicencia() {
        Path[] candidatos = new Path[]{
            Path.of("licencia.lic"),
            Path.of("Restaurante_Ejecutable", "licencia.lic"),
            Path.of("..", "Restaurante_Ejecutable", "licencia.lic")
        };
        for (Path c : candidatos) {
            if (Files.isRegularFile(c)) {
                return c.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static PublicKey cargarClavePublica() {
        try {
            byte[] derBytes = null;
            // 1. Intentar cargar desde el classpath del JAR
            InputStream streamClasspath = ValidadorLicencia.class.getResourceAsStream("/infraestructura/capip_publica.der");
            if (streamClasspath == null) {
                streamClasspath = ValidadorLicencia.class.getResourceAsStream("/capip_publica.der");
            }
            if (streamClasspath != null) {
                try (InputStream streamCerrable = streamClasspath) {
                    derBytes = streamCerrable.readAllBytes();
                }
            }
            // 2. Fallback al sistema de archivos local
            if (derBytes == null) {
                Path[] candidatos = new Path[]{
                    Path.of("capip_publica.der"),
                    Path.of("Restaurante_Ejecutable", "capip_publica.der"),
                    Path.of("src", "infraestructura", "capip_publica.der")
                };
                for (Path c : candidatos) {
                    if (Files.isRegularFile(c)) {
                        derBytes = Files.readAllBytes(c);
                        break;
                    }
                }
            }

            if (derBytes != null && derBytes.length > 0) {
                KeyFactory kf = KeyFactory.getInstance("EC");
                return kf.generatePublic(new X509EncodedKeySpec(derBytes));
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo instanciar la clave pública EC: " + ex.getMessage());
        }
        return null;
    }

    /**
     * Cifra un texto plano utilizando AES-256-GCM y la clave de la licencia.
     * Retorna el texto con prefijo: ENC:AES256GCM:<iv_hex>:<ciphertext_hex>
     */
    public String cifrarDato(String textoPlano) {
        if (textoPlano == null || textoPlano.isEmpty()) {
            return textoPlano;
        }
        if (textoPlano.startsWith(PREFIJO_ENC)) {
            return textoPlano; // Ya está cifrado
        }
        if (!activa || dekKey == null) {
            return textoPlano; // Si no hay licencia activa, conserva el texto plano
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dekKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            return PREFIJO_ENC + HexFormat.of().formatHex(iv) + ":" + HexFormat.of().formatHex(cifrado);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error cifrando dato de empresa: " + ex.getMessage(), ex);
            return textoPlano;
        }
    }

    /**
     * Descifra un dato si tiene el formato ENC:AES256GCM:<iv_hex>:<ciphertext_hex>.
     * Si no está cifrado, lo retorna intacto.
     */
    public String descifrarDato(String textoCifrado) {
        if (textoCifrado == null || !textoCifrado.startsWith(PREFIJO_ENC)) {
            return textoCifrado;
        }
        if (!activa || dekKey == null) {
            LOGGER.log(Level.WARNING, "Dato cifrado en BD pero no hay licencia activa para descifrar.");
            return "[BLOQUEADO: REQUIERE LICENCIA VÁLIDA]";
        }

        try {
            String resto = textoCifrado.substring(PREFIJO_ENC.length());
            int idxSep = resto.indexOf(':');
            if (idxSep <= 0) {
                return textoCifrado;
            }

            byte[] iv = HexFormat.of().parseHex(resto.substring(0, idxSep));
            byte[] cifrado = HexFormat.of().parseHex(resto.substring(idxSep + 1));

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dekKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plano = cipher.doFinal(cifrado);

            return new String(plano, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo al descifrar dato con la licencia actual: " + ex.getMessage());
            return "[ERROR DESCIFRADO LICENCIA]";
        }
    }

    public boolean isDatoCifrado(String texto) {
        return texto != null && texto.startsWith(PREFIJO_ENC);
    }

    public boolean isActiva() {
        return activa;
    }

    public String getIdLicencia() {
        return idLicencia;
    }

    public String getRif() {
        return rif;
    }

    public String getNombreEmpresa() {
        return nombreEmpresa;
    }

    public int getMaxMesas() {
        return maxMesas;
    }

    public String getVencimiento() {
        return vencimiento;
    }

    public String getErrorMensaje() {
        return errorMensaje;
    }
}
