package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Reglas de aplicación para la autenticación y mitigación contra ataques de fuerza bruta (OWASP A07 / NIST SP 800-63B).
 * No depende de Swing ni de JDBC.
 */
public final class AutenticacionServicio {

    private static final Logger LOGGER = Logger.getLogger(AutenticacionServicio.class.getName());
    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final long DURACION_BLOQUEO_MS = 60_000L; // 60 segundos de bloqueo progresivo

    // Registro en memoria para mitigación de fuerza bruta: identificadorNormalizado -> [conteoFallos, bloqueadoHastaMs]
    private static final Map<String, long[]> INTENTOS_POR_USUARIO = new ConcurrentHashMap<>();

    private final AutenticacionRepositorio repositorio;

    public AutenticacionServicio(AutenticacionRepositorio repositorio) {
        if (repositorio == null) {
            throw ErrorAplicacionException.validacion("El repositorio de autenticación es obligatorio.");
        }
        this.repositorio = repositorio;
    }

    public Optional<Usuario> autenticar(String correo, String clave) {
        validarCredenciales(correo, clave);
        String idNormalizado = correo.trim().toLowerCase(Locale.ROOT);

        long[] estado = INTENTOS_POR_USUARIO.computeIfAbsent(idNormalizado, k -> new long[]{0, 0});
        synchronized (estado) {
            long ahora = System.currentTimeMillis();
            if (ahora < estado[1]) {
                long restantes = (estado[1] - ahora + 999) / 1000;
                LOGGER.warning("[SEGURIDAD] Intento de acceso denegado por bloqueo temporal anti-fuerza bruta: " + idNormalizado);
                throw ErrorAplicacionException.validacion(
                        "Demasiados intentos fallidos. Estación/usuario temporalmente bloqueado. Intente de nuevo en " + restantes + " segundos.");
            }
        }

        Optional<Usuario> resultado = repositorio.autenticar(correo.trim(), clave);

        if (resultado.isPresent()) {
            synchronized (estado) {
                estado[0] = 0;
                estado[1] = 0;
            }
        } else {
            synchronized (estado) {
                estado[0]++;
                LOGGER.warning("[SEGURIDAD] Intento fallido de autenticación para usuario: " + idNormalizado
                        + " (Intento " + estado[0] + "/" + MAX_INTENTOS_FALLIDOS + ")");
                if (estado[0] >= MAX_INTENTOS_FALLIDOS) {
                    estado[1] = System.currentTimeMillis() + DURACION_BLOQUEO_MS;
                    LOGGER.warning("[SEGURIDAD] Usuario bloqueado temporalmente por fuerza bruta: " + idNormalizado
                            + " durante " + (DURACION_BLOQUEO_MS / 1000) + "s");
                }
            }
        }

        return resultado;
    }

    public Optional<Usuario> autenticar(String correo, char[] clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.length == 0) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
        return autenticar(correo, new String(clave));
    }

    private void validarCredenciales(String correo, String clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
    }

    public static void resetearBloqueosParaPruebas() {
        INTENTOS_POR_USUARIO.clear();
    }

    public int getIntentosFallidos(String correo) {
        if (correo == null) return 0;
        long[] estado = INTENTOS_POR_USUARIO.get(correo.trim().toLowerCase(Locale.ROOT));
        return estado != null ? (int) estado[0] : 0;
    }

    public boolean estaBloqueado(String correo) {
        if (correo == null) return false;
        long[] estado = INTENTOS_POR_USUARIO.get(correo.trim().toLowerCase(Locale.ROOT));
        return estado != null && System.currentTimeMillis() < estado[1];
    }

    public long getSegundosRestantesBloqueo(String correo) {
        if (correo == null) return 0;
        long[] estado = INTENTOS_POR_USUARIO.get(correo.trim().toLowerCase(Locale.ROOT));
        if (estado == null || System.currentTimeMillis() >= estado[1]) return 0;
        return (estado[1] - System.currentTimeMillis() + 999) / 1000;
    }
}
