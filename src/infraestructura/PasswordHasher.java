package infraestructura;

import Modelo.ErrorAplicacionException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Almacenamiento verificable de contraseñas con PBKDF2-HMAC-SHA-256. */
public final class PasswordHasher {
    private static final String PREFIJO = "pbkdf2-sha256";
    private static final int ITERACIONES = 600_000;
    private static final int LONGITUD_SAL = 16;
    private static final int LONGITUD_CLAVE_BITS = 256;
    private static final int ITERACIONES_MINIMAS_ACEPTADAS = 100_000;
    private static final int ITERACIONES_MAXIMAS_ACEPTADAS = 1_000_000;
    private final SecureRandom aleatorio;

    public PasswordHasher() {
        this(new SecureRandom());
    }

    PasswordHasher(SecureRandom aleatorio) {
        if (aleatorio == null) {
            throw ErrorAplicacionException.validacion("El generador aleatorio es obligatorio.");
        }
        this.aleatorio = aleatorio;
    }

    public String hash(char[] contrasena) {
        validarContrasena(contrasena);
        byte[] sal = new byte[LONGITUD_SAL];
        aleatorio.nextBytes(sal);
        byte[] clave = derivar(contrasena, sal, ITERACIONES);
        try {
            return PREFIJO + "$" + ITERACIONES + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(sal) + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(clave);
        } finally {
            java.util.Arrays.fill(clave, (byte) 0);
        }
    }

    public boolean verificar(char[] contrasena, String hashGuardado) {
        validarContrasena(contrasena);
        if (!esHash(hashGuardado)) {
            if (hashGuardado != null && hashGuardado.startsWith(PREFIJO + "$")) {
                throw new ErrorAplicacionException("El formato de la contraseña almacenada no es válido.",
                        new IllegalArgumentException("Se esperaba algoritmo, iteraciones, salt y hash."));
            }
            return false;
        }
        String[] partes = hashGuardado.split("\\$", -1);
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < ITERACIONES_MINIMAS_ACEPTADAS
                    || iteraciones > ITERACIONES_MAXIMAS_ACEPTADAS) {
                throw new IllegalArgumentException("El factor de trabajo guardado está fuera de rango.");
            }
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] claveEsperada = Base64.getDecoder().decode(partes[3]);
            if (sal.length != LONGITUD_SAL || claveEsperada.length != LONGITUD_CLAVE_BITS / 8) {
                throw new IllegalArgumentException("La longitud del hash guardado no es válida.");
            }
            byte[] clave = derivar(contrasena, sal, iteraciones);
            try {
                return MessageDigest.isEqual(claveEsperada, clave);
            } finally {
                java.util.Arrays.fill(clave, (byte) 0);
            }
        } catch (IllegalArgumentException ex) {
            throw new ErrorAplicacionException("El formato de la contraseña almacenada no es válido.", ex);
        }
    }

    public boolean esHash(String valor) {
        if (valor == null) {
            return false;
        }
        String[] partes = valor.split("\\$", -1);
        return partes.length == 4 && PREFIJO.equals(partes[0]);
    }

    private byte[] derivar(char[] contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(contrasena, sal, iteraciones, LONGITUD_CLAVE_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new ErrorAplicacionException("No se pudo procesar la contraseña de forma segura.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private void validarContrasena(char[] contrasena) {
        if (contrasena == null || contrasena.length == 0) {
            throw ErrorAplicacionException.validacion("La contraseña es obligatoria.");
        }
    }
}
