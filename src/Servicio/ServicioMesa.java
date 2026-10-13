package Servicio;

import Modelo.Salas;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio encargado de la identificación mnemotécnica y visual de mesas mediante prefijos de sala.
 *
 * Permite que cada mesa del restaurante posea un código directo compacto y unívoco
 * (ej. "P-1" para Sala Principal mesa 1, "T-2" para Terraza mesa 2, "B-3" para Barra puesto 3),
 * facilitando la búsqueda, digitación directa y visualización rápida sin ambigüedades.
 */
public final class ServicioMesa {

    private static final Set<String> PALABRAS_IGNORADAS = Set.of(
            "SALA", "SALON", "AREA", "ZONA", "ESPACIO", "SECTOR", "DE", "DEL", "LA", "EL", "LOS", "LAS"
    );

    private static final Pattern PATRON_CODIGO_MESA = Pattern.compile(
            "^([A-Z0-9]+)\\s*[-/._#]?\\s*0*(\\d+)$"
    );

    private ServicioMesa() {
    }

    /**
     * Registro inmutable que representa una mesa identificada tras parsear un código directo.
     */
    public record MesaIdentificada(int idSala, int numMesa, String codigoFormateado) {}

    /**
     * Modelo de vista con la información visual completa de una mesa para cualquiera de las interfaces.
     */
    public record MesaVisual(
            int idSala,
            String nombreSala,
            String prefijoSala,
            int numMesa,
            String codigoMesa,
            boolean esBarra,
            int idPedidoPendiente,
            String nombreMesonero,
            String nombreVisualMesonero,
            boolean ocupada
    ) {
        public String etiquetaBoton() {
            String etiqueta = esBarra ? "Puesto " : "Mesa ";
            return codigoMesa + " (" + etiqueta + numMesa + ")";
        }
    }

    /**
     * Genera un mapa de id_sala -> prefijo único garantizado para toda la colección de salas.
     * Si dos salas comparten la misma letra inicial (ej. "Principal" y "Patio"),
     * desempata asignando 2 letras o sufijo numérico (ej. "PR", "PA").
     */
    public static Map<Integer, String> generarPrefijosSalas(Collection<Salas> salas) {
        if (salas == null || salas.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> candidatos = new LinkedHashMap<>();
        for (Salas s : salas) {
            if (s != null) {
                candidatos.put(s.getId(), sugerirPrefijoBase(s.getNombre(), s.esBarra()));
            }
        }

        // Resolver colisiones
        Map<Integer, String> resultado = new LinkedHashMap<>();
        Set<String> prefijosAsignados = new HashSet<>();

        for (Map.Entry<Integer, String> entry : candidatos.entrySet()) {
            int idSala = entry.getKey();
            String base = entry.getValue();
            String elegido = base;
            int sufijo = 2;

            if (prefijosAsignados.contains(elegido)) {
                Salas sala = salas.stream().filter(s -> s != null && s.getId() == idSala).findFirst().orElse(null);
                String extendido = sugerirPrefijoExtendido(sala != null ? sala.getNombre() : null);
                if (extendido != null && !prefijosAsignados.contains(extendido)) {
                    elegido = extendido;
                } else {
                    while (prefijosAsignados.contains(base + sufijo)) {
                        sufijo++;
                    }
                    elegido = base + sufijo;
                }
            }

            prefijosAsignados.add(elegido);
            resultado.put(idSala, elegido);
        }

        return Collections.unmodifiableMap(resultado);
    }

    /**
     * Sugiere el prefijo base de 1 o 2 caracteres para el nombre de una sala.
     */
    public static String sugerirPrefijoBase(String nombreSala, boolean esBarra) {
        if (esBarra) {
            return "B";
        }
        if (nombreSala == null || nombreSala.trim().isEmpty()) {
            return "S";
        }

        String limpio = nombreSala.trim().toUpperCase(Locale.ROOT);
        if (limpio.contains("VIP")) {
            return "V";
        }
        if (limpio.contains("BARRA")) {
            return "B";
        }

        String[] palabras = limpio.split("[\\s_\\-]+");
        for (String palabra : palabras) {
            if (!palabra.isEmpty() && !PALABRAS_IGNORADAS.contains(palabra)) {
                if (palabra.matches("^\\d+.*")) {
                    return palabra.substring(0, Math.min(2, palabra.length()));
                }
                return String.valueOf(palabra.charAt(0));
            }
        }

        // Si todas eran palabras ignoradas (ej. "SALA"), usar la inicial de la primera palabra
        return palabras.length > 0 && !palabras[0].isEmpty()
                ? String.valueOf(palabras[0].charAt(0))
                : "S";
    }

    private static String sugerirPrefijoExtendido(String nombreSala) {
        if (nombreSala == null || nombreSala.trim().isEmpty()) {
            return null;
        }
        String limpio = nombreSala.trim().toUpperCase(Locale.ROOT);
        String[] palabras = limpio.split("[\\s_\\-]+");
        for (String palabra : palabras) {
            if (!palabra.isEmpty() && !PALABRAS_IGNORADAS.contains(palabra)) {
                if (palabra.length() >= 2) {
                    return palabra.substring(0, 2);
                }
            }
        }
        return null;
    }

    /**
     * Formatea el código de la mesa a partir del prefijo y el número de mesa.
     * Ejemplo: ("P", 1) -> "P-1"
     */
    public static String formatearCodigo(String prefijo, int numMesa) {
        String p = (prefijo != null && !prefijo.trim().isEmpty()) ? prefijo.trim().toUpperCase(Locale.ROOT) : "M";
        return p + "-" + Math.max(1, numMesa);
    }

    /**
     * Parsea un texto ingresado por el usuario (ej. "P-1", "p1", "T 4", "B-03")
     * y resuelve la sala y número de mesa correspondiente.
     *
     * @param texto Código escrito o buscado por el operador.
     * @param prefijoASalaId Mapa de Prefijo (en mayúsculas) -> ID de Sala.
     * @return MesaIdentificada si el formato y el prefijo son válidos, vacío si no coincide.
     */
    public static Optional<MesaIdentificada> parsearCodigoMesa(String texto, Map<String, Integer> prefijoASalaId) {
        if (texto == null || prefijoASalaId == null || prefijoASalaId.isEmpty()) {
            return Optional.empty();
        }

        String limpio = texto.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = PATRON_CODIGO_MESA.matcher(limpio);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String prefijo = matcher.group(1);
        int numMesa;
        try {
            numMesa = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        Integer idSala = prefijoASalaId.get(prefijo);
        if (idSala == null || numMesa <= 0) {
            return Optional.empty();
        }

        return Optional.of(new MesaIdentificada(idSala, numMesa, formatearCodigo(prefijo, numMesa)));
    }

    /**
     * Construye un mapa inverso de Prefijo (en mayúsculas) -> ID de Sala
     * a partir del mapa de ID -> Prefijo.
     */
    public static Map<String, Integer> invertirPrefijos(Map<Integer, String> salaIdAPrefijo) {
        if (salaIdAPrefijo == null || salaIdAPrefijo.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> invertido = new HashMap<>();
        for (Map.Entry<Integer, String> e : salaIdAPrefijo.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                invertido.put(e.getValue().toUpperCase(Locale.ROOT), e.getKey());
            }
        }
        return Collections.unmodifiableMap(invertido);
    }

    /**
     * Construye la lista de mesas visuales para una sala dada, cruzando el estado de pedidos y mesoneros.
     */
    public static List<MesaVisual> construirMesasVisuales(
            Salas sala,
            String prefijoSala,
            Map<Integer, Integer> estadosMesas,
            Map<Integer, String> mesonerosMesas,
            Map<String, String> nombresVisualesMesoneros
    ) {
        if (sala == null || sala.getMesas() <= 0) {
            return Collections.emptyList();
        }

        int idSala = sala.getId();
        String nombreSala = sala.getNombre();
        boolean esBarra = sala.esBarra();
        int cant = sala.getMesas();
        String prefijo = (prefijoSala != null && !prefijoSala.isBlank()) ? prefijoSala : sugerirPrefijoBase(nombreSala, esBarra);

        List<MesaVisual> resultado = new ArrayList<>(cant);
        for (int i = 1; i <= cant; i++) {
            int numMesa = i;
            int idPedido = estadosMesas != null ? estadosMesas.getOrDefault(numMesa, 0) : 0;
            String mesoneroCompleto = mesonerosMesas != null ? mesonerosMesas.get(numMesa) : null;
            String visualMesonero = null;
            if (mesoneroCompleto != null && !mesoneroCompleto.isBlank()) {
                visualMesonero = (nombresVisualesMesoneros != null)
                        ? nombresVisualesMesoneros.getOrDefault(mesoneroCompleto.trim(), ServicioMesoneroNombre.extraerPrimerNombre(mesoneroCompleto))
                        : ServicioMesoneroNombre.extraerPrimerNombre(mesoneroCompleto);
            }

            resultado.add(new MesaVisual(
                    idSala,
                    nombreSala,
                    prefijo,
                    numMesa,
                    formatearCodigo(prefijo, numMesa),
                    esBarra,
                    idPedido,
                    mesoneroCompleto,
                    visualMesonero,
                    idPedido > 0
            ));
        }

        return Collections.unmodifiableList(resultado);
    }
}
