package Servicio;

import Modelo.Mesonero;
import java.util.*;

/**
 * Servicio encargado de dar formato al nombre de los mesoneros para mostrarlos
 * en el espacio reducido de las mesas y resolver colisiones entre nombres idénticos.
 *
 * Reglas:
 * 1. Por defecto se muestra el primer nombre (ej. "Carlos").
 * 2. Si dos o más mesoneros activos tienen el mismo primer nombre (ej. "Carlos Pérez" y "Carlos Gómez"),
 *    se añade un espacio y las 3 primeras letras de su primer apellido (ej. "Carlos Pér" y "Carlos Góm").
 * 3. Si persiste la colisión (ej. "Carlos Romero" y "Carlos Rodríguez" -> ambos darían "Carlos Rom"),
 *    se incrementan las letras del apellido hasta desempatar o se añade la inicial del segundo apellido/cédula.
 */
public final class ServicioMesoneroNombre {

    private ServicioMesoneroNombre() {
    }

    /**
     * Extrae el primer nombre de un nombre completo.
     */
    public static String extraerPrimerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) {
            return "";
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes[0];
    }

    /**
     * Extrae el primer apellido (segunda palabra si existe).
     */
    public static String extraerPrimerApellido(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) {
            return "";
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes.length > 1 ? partes[1] : "";
    }

    /**
     * Resuelve el nombre para visualización compacta de un mesonero dado el universo de mesoneros activos.
     * Retorna un mapa de ID -> Nombre compacto para rápida consulta.
     */
    public static Map<Integer, String> generarNombresVisuales(Collection<Mesonero> mesoneros) {
        Map<Integer, String> resultado = new HashMap<>();
        if (mesoneros == null || mesoneros.isEmpty()) {
            return Collections.emptyMap();
        }

        // Agrupar por primer nombre en minúsculas
        Map<String, List<Mesonero>> gruposPorPrimerNombre = new LinkedHashMap<>();
        for (Mesonero m : mesoneros) {
            if (m == null) continue;
            String primer = extraerPrimerNombre(m.getNombreCompleto());
            String clave = primer.toLowerCase(Locale.ROOT);
            gruposPorPrimerNombre.computeIfAbsent(clave, k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<Mesonero>> entry : gruposPorPrimerNombre.entrySet()) {
            List<Mesonero> lista = entry.getValue();
            if (lista.size() == 1) {
                // Sin colisión: usar solo el primer nombre original
                Mesonero m = lista.get(0);
                resultado.put(m.getId(), extraerPrimerNombre(m.getNombreCompleto()));
            } else {
                // Hay colisión entre 2 o más mesoneros con el mismo primer nombre
                resolverColisionGrupo(lista, resultado);
            }
        }

        return Collections.unmodifiableMap(resultado);
    }

    private static void resolverColisionGrupo(List<Mesonero> lista, Map<Integer, String> resultado) {
        // Intentar con prefijo de 3 letras de apellido
        int longitudApellido = 3;
        boolean hayDuplicados = true;
        Map<Integer, String> intentos = new HashMap<>();

        while (hayDuplicados && longitudApellido <= 20) {
            intentos.clear();
            Map<String, Integer> frecuencias = new HashMap<>();

            for (Mesonero m : lista) {
                String primerNombre = extraerPrimerNombre(m.getNombreCompleto());
                String apellido = extraerPrimerApellido(m.getNombreCompleto());
                String sufijo;
                if (apellido.isEmpty()) {
                    // Si no tiene apellido, diferenciar por su cédula o ID
                    String ced = m.getCedula() != null ? m.getCedula() : "";
                    sufijo = !ced.isEmpty() ? ("(" + (ced.length() > 4 ? ced.substring(ced.length() - 4) : ced) + ")") : ("#" + m.getId());
                } else {
                    int len = Math.min(longitudApellido, apellido.length());
                    sufijo = apellido.substring(0, len);
                }
                String nombreFormateado = primerNombre + " " + sufijo;
                intentos.put(m.getId(), nombreFormateado);
                frecuencias.put(nombreFormateado.toLowerCase(Locale.ROOT),
                        frecuencias.getOrDefault(nombreFormateado.toLowerCase(Locale.ROOT), 0) + 1);
            }

            hayDuplicados = false;
            for (int count : frecuencias.values()) {
                if (count > 1) {
                    hayDuplicados = true;
                    break;
                }
            }

            if (hayDuplicados) {
                longitudApellido++;
            }
        }

        // Si aún hubiera duplicados idénticos en nombre y apellido, desempatar con los últimos 4 dígitos de cédula
        for (Mesonero m : lista) {
            String propuesto = intentos.get(m.getId());
            long coincidencias = intentos.values().stream().filter(v -> v.equalsIgnoreCase(propuesto)).count();
            if (coincidencias > 1) {
                String ced = (m.getCedula() != null && !m.getCedula().isBlank())
                        ? (" (" + (m.getCedula().length() > 4 ? m.getCedula().substring(m.getCedula().length() - 4) : m.getCedula()) + ")")
                        : (" #" + m.getId());
                resultado.put(m.getId(), propuesto + ced);
            } else {
                resultado.put(m.getId(), propuesto);
            }
        }
    }

    /**
     * Resuelve el nombre para un solo mesonero comparándolo contra la lista de mesoneros activos.
     */
    public static String formatearNombreVisual(Mesonero mesonero, Collection<Mesonero> todosActivos) {
        if (mesonero == null) return "";
        List<Mesonero> lista = new ArrayList<>();
        if (todosActivos != null) {
            lista.addAll(todosActivos);
        }
        if (!lista.contains(mesonero)) {
            lista.add(mesonero);
        }
        Map<Integer, String> mapa = generarNombresVisuales(lista);
        return mapa.getOrDefault(mesonero.getId(), extraerPrimerNombre(mesonero.getNombreCompleto()));
    }

    /**
     * Resuelve nombres compactos a partir de una colección de nombres completos en String.
     * Retorna un mapa: Nombre Completo Original -> Nombre Compacto con colisiones resueltas.
     */
    public static Map<String, String> resolverNombresVisualesDesdeStrings(Collection<String> nombresCompletos) {
        if (nombresCompletos == null || nombresCompletos.isEmpty()) {
            return Collections.emptyMap();
        }
        int id = 1;
        List<Mesonero> lista = new ArrayList<>();
        Map<Integer, String> idANombreOriginal = new HashMap<>();
        for (String s : nombresCompletos) {
            if (s != null && !s.isBlank()) {
                String limpio = s.trim();
                Mesonero m = new Mesonero(id, limpio, "", "", true, false);
                lista.add(m);
                idANombreOriginal.put(id, limpio);
                id++;
            }
        }
        Map<Integer, String> visualesPorId = generarNombresVisuales(lista);
        Map<String, String> resultado = new HashMap<>();
        for (Map.Entry<Integer, String> entry : visualesPorId.entrySet()) {
            String original = idANombreOriginal.get(entry.getKey());
            if (original != null) {
                resultado.put(original, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(resultado);
    }
}
