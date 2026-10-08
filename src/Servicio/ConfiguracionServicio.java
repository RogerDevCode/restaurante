package Servicio;

import Modelo.ConfigClaves;
import Modelo.ConfiguracionRepositorio;
import Modelo.ConfiguracionSistemaDao;
import Modelo.ErrorAplicacionException;
import Modelo.ModoSalidaTicket;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de alto nivel para gestionar configuraciones clave-valor tipadas con caché en memoria.
 * Proporciona acceso seguro a parámetros de sistema (moneda, impuestos, hardware, retención)
 * con tolerancia a fallos y conversión de tipos resiliente (fail-safe defaults).
 */
public class ConfiguracionServicio {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionServicio.class.getName());
    private final ConfiguracionRepositorio repositorio;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheInicializada = false;

    public ConfiguracionServicio() {
        this(new ConfiguracionSistemaDao());
    }

    public ConfiguracionServicio(ConfiguracionRepositorio repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "El repositorio de configuración no puede ser nulo.");
    }

    /**
     * Asegura que la caché en memoria esté cargada desde la base de datos.
     */
    private void asegurarCache() {
        if (!cacheInicializada) {
            synchronized (this) {
                if (!cacheInicializada) {
                    try {
                        Map<String, String> todos = repositorio.obtenerTodos();
                        cache.putAll(todos);
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "No se pudo inicializar la caché de configuraciones: " + ex.getMessage());
                    }
                    cacheInicializada = true;
                }
            }
        }
    }

    /**
     * Recarga toda la configuración desde el repositorio descartando la caché en memoria.
     */
    public synchronized void recargar() {
        cache.clear();
        try {
            Map<String, String> todos = repositorio.obtenerTodos();
            cache.putAll(todos);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al recargar configuraciones: " + ex.getMessage());
        }
        cacheInicializada = true;
    }

    public String getString(String clave, String porDefecto) {
        if (clave == null || clave.trim().isEmpty()) {
            return porDefecto;
        }
        asegurarCache();
        String val = cache.get(clave.trim());
        if (val != null) {
            return val;
        }
        try {
            String deBd = repositorio.obtener(clave.trim(), porDefecto);
            if (deBd != null) {
                cache.put(clave.trim(), deBd);
            }
            return deBd != null ? deBd : porDefecto;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al consultar clave '" + clave + "' en repositorio: " + ex.getMessage());
            return porDefecto;
        }
    }

    public BigDecimal getDecimal(String clave, BigDecimal porDefecto) {
        String texto = getString(clave, null);
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException ex) {
            LOGGER.warning(() -> "Valor decimal inválido para la clave '" + clave + "': '" + texto + "'. Usando valor por defecto: " + porDefecto);
            return porDefecto;
        }
    }

    public int getInt(String clave, int porDefecto) {
        String texto = getString(clave, null);
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException ex) {
            LOGGER.warning(() -> "Valor entero inválido para la clave '" + clave + "': '" + texto + "'. Usando valor por defecto: " + porDefecto);
            return porDefecto;
        }
    }

    public boolean getBoolean(String clave, boolean porDefecto) {
        String texto = getString(clave, null);
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        String normalizado = texto.trim().toLowerCase();
        if ("true".equals(normalizado) || "1".equals(normalizado) || "si".equals(normalizado) || "yes".equals(normalizado) || "on".equals(normalizado)) {
            return true;
        }
        if ("false".equals(normalizado) || "0".equals(normalizado) || "no".equals(normalizado) || "off".equals(normalizado)) {
            return false;
        }
        return porDefecto;
    }

    public ModoSalidaTicket getModoSalida(String clave, ModoSalidaTicket porDefecto) {
        String texto = getString(clave, null);
        if (texto == null || texto.trim().isEmpty()) {
            return porDefecto;
        }
        return ModoSalidaTicket.desdeCodigo(texto);
    }

    public void set(String clave, Object valor) {
        if (clave == null || clave.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La clave de configuración no puede ser nula ni vacía.");
        }
        String claveLimpia = clave.trim();
        String valorStr = valor != null ? valor.toString() : "";
        repositorio.guardar(claveLimpia, valorStr);
        cache.put(claveLimpia, valorStr);
    }

    public void guardarVarios(Map<String, String> valores) {
        if (valores == null || valores.isEmpty()) {
            return;
        }
        repositorio.guardarVarios(valores);
        cache.putAll(valores);
    }

    public java.sql.Timestamp getFechaActualizacion(String clave) {
        if (clave == null || clave.trim().isEmpty()) {
            return null;
        }
        try {
            return repositorio.obtenerFechaActualizacion(clave.trim());
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Error al obtener fecha de actualización para '" + clave + "': " + ex.getMessage());
            return null;
        }
    }

    public java.sql.Timestamp getFechaActualizacionTasaDolar() {
        return getFechaActualizacion(ConfigClaves.TASA_DOLAR);
    }

    /**
     * Determina si la tasa de cambio está desactualizada (no ha sido actualizada hoy o no existe fecha).
     */
    public boolean esTasaDolarDesactualizada() {
        java.sql.Timestamp ts = getFechaActualizacionTasaDolar();
        if (ts == null) {
            return true;
        }
        java.time.LocalDate fechaActualizacion = ts.toLocalDateTime().toLocalDate();
        return fechaActualizacion.isBefore(java.time.LocalDate.now());
    }
}
