package infraestructura.api;

import Modelo.ConfigClaves;
import Modelo.ConfiguracionSistemaDao;
import Modelo.Mesonero;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gestor Criptográfico de Sesiones y Tokens API para Comanderas Móviles (NIST SP 800-207 / OWASP A01).
 * Implementa:
 * 1. Tokens estructurados firmados con HMAC-SHA256 con verificación en tiempo constante.
 * 2. Persistencia de clave secreta con ventana de gracia dual (2 horas) para evitar desautenticación en reinicios o cambio de turno.
 * 3. Rate Limiting diferenciado: por IP para login (anti-fuerza bruta) y por token/mesonero para comandeo (anti-DoS en Wi-Fi).
 */
public final class GestorTokensApi {

    private static final Logger LOGGER = Logger.getLogger(GestorTokensApi.class.getName());
    private static final String HMAC_ALGORITMO = "HmacSHA256";
    private static final long TTL_TOKEN_MS = 14 * 3600 * 1000L; // 14 horas de turno
    private static final long VENTANA_GRACIA_ROTACION_MS = 2 * 3600 * 1000L; // 2 horas

    // Rate Limiting en memoria: IP -> [conteo, ventana_inicio_ms]
    private static final Map<String, int[]> RATE_LIMIT_IP = new ConcurrentHashMap<>();
    private static final Map<Integer, int[]> RATE_LIMIT_TOKEN = new ConcurrentHashMap<>();

    private static volatile byte[] claveActual = null;
    private static volatile byte[] claveAnterior = null;
    private static volatile long fechaRotacionClaveMs = 0;

    private GestorTokensApi() {
    }

    /**
     * Inicializa o recupera la clave de firma persistente del sistema.
     */
    public static synchronized void inicializarClaves() {
        if (claveActual != null) {
            return;
        }
        try {
            ConfiguracionSistemaDao configDao = new ConfiguracionSistemaDao();
            String claveHex = configDao.obtener("api_tablets_jwt_secret", "");
            if (claveHex != null && claveHex.length() == 64) {
                claveActual = HexFormat.of().parseHex(claveHex);
            } else {
                byte[] nueva = new byte[32];
                new SecureRandom().nextBytes(nueva);
                claveActual = nueva;
                configDao.guardar("api_tablets_jwt_secret", HexFormat.of().formatHex(nueva));
            }
            fechaRotacionClaveMs = System.currentTimeMillis();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo persistir la clave de tokens en BD; utilizando clave volátil segura: " + ex.getMessage());
            byte[] nueva = new byte[32];
            new SecureRandom().nextBytes(nueva);
            claveActual = nueva;
            fechaRotacionClaveMs = System.currentTimeMillis();
        }
    }

    /**
     * Genera un token firmado criptográficamente para el mesonero especificado.
     */
    public static String generarTokenMesonero(Mesonero m) {
        if (claveActual == null) {
            inicializarClaves();
        }
        long ahora = System.currentTimeMillis();
        long expira = ahora + TTL_TOKEN_MS;
        String nonce = UUID.randomUUID().toString();

        String payloadJson = String.format(
                "{\"sub\":%d,\"cedula\":\"%s\",\"nombre\":\"%s\",\"rol\":\"MESONERO\",\"iat\":%d,\"exp\":%d,\"nonce\":\"%s\"}",
                m.getId(),
                escapar(m.getCedula()),
                escapar(m.getNombreCompleto()),
                ahora,
                expira,
                nonce
        );

        String b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String firma = firmar(b64Payload, claveActual);

        return b64Payload + "." + firma;
    }

    /**
     * Valida un token recibido en la cabecera Authorization: Bearer <token>.
     * Retorna el DTO de la sesión si es válido o null si es inválido, manipulado o expirado.
     */
    public static SesionMesonero validarToken(String tokenCompleto) {
        if (tokenCompleto == null || tokenCompleto.isBlank()) {
            return null;
        }
        if (claveActual == null) {
            inicializarClaves();
        }
        String[] partes = tokenCompleto.trim().split("\\.", -1);
        if (partes.length != 2) {
            return null;
        }

        String b64Payload = partes[0];
        String firmaRecibida = partes[1];

        // 1. Validar firma con clave actual
        boolean valida = verificarFirma(b64Payload, firmaRecibida, claveActual);

        // 2. Si falló y estamos dentro de la ventana de gracia, probar con clave anterior
        if (!valida && claveAnterior != null && (System.currentTimeMillis() - fechaRotacionClaveMs < VENTANA_GRACIA_ROTACION_MS)) {
            valida = verificarFirma(b64Payload, firmaRecibida, claveAnterior);
        }

        if (!valida) {
            return null;
        }

        // 3. Decodificar y parsear payload
        try {
            byte[] bytesPayload = Base64.getUrlDecoder().decode(b64Payload);
            String json = new String(bytesPayload, StandardCharsets.UTF_8);
            SesionMesonero sesion = parsearSesion(json);
            if (sesion == null) {
                return null;
            }

            // Verificar expiración
            if (System.currentTimeMillis() > sesion.expira()) {
                LOGGER.info("Token expirado para mesonero #" + sesion.idMesonero());
                return null;
            }

            return sesion;
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error decodificando token: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Control de tasa de peticiones (Rate Limiting) por IP para endpoints públicos (ej. login).
     * @return true si la petición está dentro del límite permitido; false si debe responderse HTTP 429.
     */
    public static boolean verificarRateLimitIp(String ip, int maxPorSegundo) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        long ahora = System.currentTimeMillis();
        int[] datos = RATE_LIMIT_IP.computeIfAbsent(ip, k -> new int[]{0, (int) (ahora / 1000)});
        synchronized (datos) {
            int segundoActual = (int) (ahora / 1000);
            if (datos[1] != segundoActual) {
                datos[0] = 1;
                datos[1] = segundoActual;
                return true;
            }
            datos[0]++;
            return datos[0] <= maxPorSegundo;
        }
    }

    /**
     * Control de tasa de peticiones por mesonero autenticado (anti-DoS en Wi-Fi).
     */
    public static boolean verificarRateLimitMesonero(int idMesonero, int maxPorSegundo) {
        if (idMesonero <= 0) {
            return true;
        }
        long ahora = System.currentTimeMillis();
        int[] datos = RATE_LIMIT_TOKEN.computeIfAbsent(idMesonero, k -> new int[]{0, (int) (ahora / 1000)});
        synchronized (datos) {
            int segundoActual = (int) (ahora / 1000);
            if (datos[1] != segundoActual) {
                datos[0] = 1;
                datos[1] = segundoActual;
                return true;
            }
            datos[0]++;
            return datos[0] <= maxPorSegundo;
        }
    }

    private static boolean verificarFirma(String b64Payload, String firmaRecibida, byte[] clave) {
        if (clave == null) return false;
        String firmaEsperada = firmar(b64Payload, clave);
        return MessageDigest.isEqual(
                firmaEsperada.getBytes(StandardCharsets.UTF_8),
                firmaRecibida.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String firmar(String payload, byte[] clave) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITMO);
            mac.init(new SecretKeySpec(clave, HMAC_ALGORITMO));
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Error computando HMAC-SHA256: " + ex.getMessage(), ex);
        }
    }

    private static SesionMesonero parsearSesion(String json) {
        int sub = extraerInt(json, "sub");
        String cedula = extraerString(json, "cedula");
        String nombre = extraerString(json, "nombre");
        String rol = extraerString(json, "rol");
        long exp = extraerLong(json, "exp");
        if (sub <= 0 || exp <= 0) {
            return null;
        }
        return new SesionMesonero(sub, cedula, nombre, rol, exp);
    }

    private static int extraerInt(String json, String campo) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static long extraerLong(String json, String campo) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0L;
    }

    private static String extraerString(String json, String campo) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String escapar(String valor) {
        return valor != null ? valor.replace("\"", "\\\"").replace("\n", " ").replace("\r", "") : "";
    }

    /**
     * DTO inmutable que representa la sesión autenticada de un mesonero.
     */
    public record SesionMesonero(
            int idMesonero,
            String cedula,
            String nombre,
            String rol,
            long expira) {
    }
}
