package Servicio;

import Modelo.ConfigClaves;
import Modelo.ConfiguracionRepositorio;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Pruebas unitarias para la verificación de frescura de la tasa de cambio y el banner visual.
 * Garantiza que el sistema advierta si la tasa de cambio no ha sido actualizada en la fecha actual.
 */
public class TasaCambioFrescuraYBannerTest {

    private Map<String, String> tablaConfig;
    private Map<String, Timestamp> fechasConfig;
    private ConfiguracionRepositorio repositorio;
    private ConfiguracionServicio servicio;

    @Before
    public void setUp() {
        tablaConfig = new ConcurrentHashMap<>();
        fechasConfig = new ConcurrentHashMap<>();

        repositorio = new ConfiguracionRepositorio() {
            @Override
            public String obtener(String clave, String valorPorDefecto) {
                return tablaConfig.getOrDefault(clave, valorPorDefecto);
            }

            @Override
            public void guardar(String clave, String valor) {
                tablaConfig.put(clave, valor);
                fechasConfig.put(clave, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public Map<String, String> obtenerTodos() {
                return new HashMap<>(tablaConfig);
            }

            @Override
            public void guardarVarios(Map<String, String> configuraciones) {
                Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
                for (Map.Entry<String, String> entry : configuraciones.entrySet()) {
                    tablaConfig.put(entry.getKey(), entry.getValue());
                    fechasConfig.put(entry.getKey(), ahora);
                }
            }

            @Override
            public boolean existe(String clave) {
                return tablaConfig.containsKey(clave);
            }

            @Override
            public void eliminar(String clave) {
                tablaConfig.remove(clave);
                fechasConfig.remove(clave);
            }

            @Override
            public Timestamp obtenerFechaActualizacion(String clave) {
                return fechasConfig.get(clave);
            }
        };

        servicio = new ConfiguracionServicio(repositorio);
    }

    @Test
    public void tasaSinFechaRegistradaSeConsideraDesactualizada() {
        servicio.set(ConfigClaves.TASA_DOLAR, "36.5000");
        fechasConfig.remove(ConfigClaves.TASA_DOLAR);

        assertNull("La fecha de actualización debe ser null", servicio.getFechaActualizacionTasaDolar());
        assertTrue("Sin fecha registrada, la tasa debe considerarse desactualizada para proteger la facturación",
                servicio.esTasaDolarDesactualizada());
    }

    @Test
    public void tasaActualizadaHoyNoEstaDesactualizada() {
        Timestamp hoy = Timestamp.valueOf(LocalDateTime.now());
        fechasConfig.put(ConfigClaves.TASA_DOLAR, hoy);
        servicio.set(ConfigClaves.TASA_DOLAR, "40.2500");

        assertNotNull(servicio.getFechaActualizacionTasaDolar());
        assertFalse("Una tasa actualizada en el día de hoy no debe considerarse desactualizada",
                servicio.esTasaDolarDesactualizada());
    }

    @Test
    public void tasaActualizadaAyerSeConsideraDesactualizada() {
        Timestamp ayer = Timestamp.valueOf(LocalDateTime.now().minusDays(1));
        fechasConfig.put(ConfigClaves.TASA_DOLAR, ayer);

        assertNotNull(servicio.getFechaActualizacionTasaDolar());
        assertTrue("Una tasa de ayer debe reportarse como desactualizada",
                servicio.esTasaDolarDesactualizada());
    }

    @Test
    public void tasaActualizadaHaceVariosDiasSeConsideraDesactualizada() {
        Timestamp haceUnaSemana = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
        fechasConfig.put(ConfigClaves.TASA_DOLAR, haceUnaSemana);

        assertTrue("Una tasa de hace 7 días debe reportarse como desactualizada",
                servicio.esTasaDolarDesactualizada());
    }

    @Test
    public void formateoDeTextoParaBannerVisual() {
        BigDecimal tasa = new BigDecimal("45.5000");
        servicio.set(ConfigClaves.TASA_DOLAR, tasa.toPlainString());

        // Caso 1: Tasa vieja (ayer) -> debe indicar advertencia
        Timestamp ayer = Timestamp.valueOf(LocalDateTime.now().minusDays(1));
        fechasConfig.put(ConfigClaves.TASA_DOLAR, ayer);

        String fechaFormateada = ayer.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String textoBannerDesactualizado = String.format("TASA: Bs. %.2f / $ (⚠️ %s)", tasa.doubleValue(), fechaFormateada);
        assertTrue(textoBannerDesactualizado.contains("⚠️"));
        assertTrue(textoBannerDesactualizado.contains("45.50"));

        // Caso 2: Tasa de hoy -> debe indicar estado al día
        Timestamp hoy = Timestamp.valueOf(LocalDateTime.now());
        fechasConfig.put(ConfigClaves.TASA_DOLAR, hoy);

        String textoBannerAlDia = String.format("TASA: Bs. %.2f / $ (Hoy)", tasa.doubleValue());
        assertFalse(textoBannerAlDia.contains("⚠️"));
        assertTrue(textoBannerAlDia.contains("Hoy"));
    }
}
