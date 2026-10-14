package Modelo;

import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Validación y normalización compartida para nombres de acceso y esquema legado. */
public final class NombreUsuario {
    private static final Pattern FORMATO = Pattern.compile(
            "^[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} ._-]{1,58}[\\p{L}\\p{M}\\p{N}]$");

    private NombreUsuario() {}

    public static String normalizar(String nombre) {
        if (nombre == null) {
            throw ErrorAplicacionException.validacion("El nombre de usuario es obligatorio.");
        }
        String normalizado = Normalizer.normalize(nombre.trim(), Normalizer.Form.NFC);
        int caracteres = normalizado.codePointCount(0, normalizado.length());
        if (caracteres < 3 || caracteres > 60 || !FORMATO.matcher(normalizado).matches()) {
            throw ErrorAplicacionException.validacion(
                    "El nombre de usuario debe tener entre 3 y 60 caracteres; usa letras, números, espacios, punto, guion o guion bajo y no dejes espacios en los extremos.");
        }
        return normalizado;
    }

    public static String clave(String nombre) {
        return normalizar(nombre).toLowerCase(Locale.ROOT);
    }
}
