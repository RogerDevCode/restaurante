package Servicio;

import Modelo.Config;
import Modelo.ConfigClaves;
import Modelo.ConfiguracionRepositorio;
import Modelo.ConfiguracionSistemaDao;
import Modelo.ErrorAplicacionException;
import Modelo.LoginDao;
import Modelo.ModoSalidaTicket;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Suite de Pruebas Adversariales (Red Team) para la arquitectura de configuración Clave-Valor.
 * Valida vectores de ataque, resiliencia ante datos corruptos, inyecciones SQL/XSS,
 * concurrencia multi-hilo y tolerancia a fallos en el sistema del restaurante.
 */
public class RedTeamConfiguracionClaveValorTest {

    private ConfiguracionRepositorio repositorioEnMemoria;
    private ConfiguracionServicio servicio;

    @Before
    public void setUp() {
        repositorioEnMemoria = new ConfiguracionRepositorio() {
            private final Map<String, String> tabla = new ConcurrentHashMap<>();

            @Override
            public String obtener(String clave, String valorPorDefecto) {
                if (clave == null || clave.trim().isEmpty()) {
                    throw ErrorAplicacionException.validacion("Clave inválida");
                }
                String val = tabla.get(clave.trim());
                return val != null ? val : valorPorDefecto;
            }

            @Override
            public void guardar(String clave, String valor) {
                if (clave == null || clave.trim().isEmpty()) {
                    throw ErrorAplicacionException.validacion("Clave inválida");
                }
                tabla.put(clave.trim(), valor != null ? valor : "");
            }

            @Override
            public Map<String, String> obtenerTodos() {
                return new HashMap<>(tabla);
            }

            @Override
            public void guardarVarios(Map<String, String> configuraciones) {
                if (configuraciones != null) {
                    for (Map.Entry<String, String> e : configuraciones.entrySet()) {
                        if (e.getKey() != null && !e.getKey().trim().isEmpty()) {
                            tabla.put(e.getKey().trim(), e.getValue() != null ? e.getValue() : "");
                        }
                    }
                }
            }

            @Override
            public boolean existe(String clave) {
                return clave != null && tabla.containsKey(clave.trim());
            }

            @Override
            public void eliminar(String clave) {
                if (clave != null) {
                    tabla.remove(clave.trim());
                }
            }
        };

        servicio = new ConfiguracionServicio(repositorioEnMemoria);
    }

    /**
     * Vector 1: Intento de Inyección SQL y XSS en claves y valores.
     * El sistema no debe interpretar comandos SQL ni romper la integridad.
     */
    @Test
    public void vector1_inyeccionSqlYXssEnClavesYValores() {
        String claveInyeccion = "tasa'; DROP TABLE config; --";
        String valorInyeccion = "36.50' OR '1'='1'; DELETE FROM pedidos; <script>alert('xss')</script>";

        servicio.set(claveInyeccion, valorInyeccion);

        // Se debe recuperar como cadena literal inerte sin ejecución
        assertEquals(valorInyeccion, servicio.getString(claveInyeccion, null));
        assertTrue(repositorioEnMemoria.existe(claveInyeccion));
    }

    /**
     * Vector 2: Envenenamiento de tipos en parámetros decimales (tasa_dolar, iva).
     * Cadenas alfanuméricas, NaN, Infinity o vacías deben activar el fallback seguro sin lanzar NPE ni romper el POS.
     */
    @Test
    public void vector2_envenenamientoDeTiposDecimales() {
        BigDecimal fallbackSeguro = new BigDecimal("36.5000");

        servicio.set(ConfigClaves.TASA_DOLAR, "NaN");
        assertEquals(fallbackSeguro, servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));

        servicio.set(ConfigClaves.TASA_DOLAR, "Infinity");
        assertEquals(fallbackSeguro, servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));

        servicio.set(ConfigClaves.TASA_DOLAR, "corrupto_no_numerico_123");
        assertEquals(fallbackSeguro, servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));

        servicio.set(ConfigClaves.TASA_DOLAR, "--45.00");
        assertEquals(fallbackSeguro, servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));

        servicio.set(ConfigClaves.TASA_DOLAR, "   ");
        assertEquals(fallbackSeguro, servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));

        // Número válido debe parsearse sin problema
        servicio.set(ConfigClaves.TASA_DOLAR, "42.1250");
        assertEquals(new BigDecimal("42.1250"), servicio.getDecimal(ConfigClaves.TASA_DOLAR, fallbackSeguro));
    }

    /**
     * Vector 3: Envenenamiento de tipos en parámetros enteros (meses_retencion).
     * Números flotantes, valores gigantescos que desbordan Integer o texto plano.
     */
    @Test
    public void vector3_envenenamientoDeTiposEnteros() {
        int fallbackMeses = 24;

        servicio.set(ConfigClaves.MESES_RETENCION_PEDIDOS, "9999999999999999999999"); // Desborde int
        assertEquals(fallbackMeses, servicio.getInt(ConfigClaves.MESES_RETENCION_PEDIDOS, fallbackMeses));

        servicio.set(ConfigClaves.MESES_RETENCION_PEDIDOS, "24.5"); // Float en campo int
        assertEquals(fallbackMeses, servicio.getInt(ConfigClaves.MESES_RETENCION_PEDIDOS, fallbackMeses));

        servicio.set(ConfigClaves.MESES_RETENCION_PEDIDOS, "veinticuatro");
        assertEquals(fallbackMeses, servicio.getInt(ConfigClaves.MESES_RETENCION_PEDIDOS, fallbackMeses));

        servicio.set(ConfigClaves.MESES_RETENCION_PEDIDOS, "36");
        assertEquals(36, servicio.getInt(ConfigClaves.MESES_RETENCION_PEDIDOS, fallbackMeses));
    }

    /**
     * Vector 4: Permutaciones booleanas tolerantes y robustas.
     * Evalúa mayúsculas, minúsculas, representaciones numéricas ("1"/"0") e idiomas ("si"/"no").
     */
    @Test
    public void vector4_permutacionesBooleanas() {
        String clave = ConfigClaves.IMPRIMIR_LOGO_TICKET;

        // Casos verdaderos
        for (String v : new String[]{"true", "TRUE", "True", "1", "si", "SI", "yes", "YES", "on", "ON"}) {
            servicio.set(clave, v);
            assertTrue("Debe interpretar como TRUE: " + v, servicio.getBoolean(clave, false));
        }

        // Casos falsos
        for (String v : new String[]{"false", "FALSE", "False", "0", "no", "NO", "off", "OFF"}) {
            servicio.set(clave, v);
            assertFalse("Debe interpretar como FALSE: " + v, servicio.getBoolean(clave, true));
        }

        // Casos ambiguos o inválidos deben respetar el porDefecto
        servicio.set(clave, "talvez");
        assertTrue(servicio.getBoolean(clave, true));
        assertFalse(servicio.getBoolean(clave, false));
    }

    /**
     * Vector 5: Rechazo estricto de claves nulas, vacías o que exceden longitud.
     */
    @Test
    public void vector5_rechazoClavesNulasOInvalidas() {
        assertThrows(ErrorAplicacionException.class, () -> servicio.set(null, "valor"));
        assertThrows(ErrorAplicacionException.class, () -> servicio.set("", "valor"));
        assertThrows(ErrorAplicacionException.class, () -> servicio.set("    ", "valor"));

        ConfiguracionSistemaDao daoJdbc = new ConfiguracionSistemaDao(new ProveedorConexionJdbc());
        assertThrows(ErrorAplicacionException.class, () -> daoJdbc.guardar(null, "valor"));
        assertThrows(ErrorAplicacionException.class, () -> daoJdbc.guardar("", "valor"));

        String claveGigante = "a".repeat(81);
        assertThrows(ErrorAplicacionException.class, () -> daoJdbc.guardar(claveGigante, "valor"));
    }

    /**
     * Vector 6: Cargas masivas y Unicode/Emojis sin corrupción.
     */
    @Test
    public void vector6_cargasMasivasYEmojisUnicode() {
        String textoLargo = "A".repeat(15000);
        servicio.set("texto_largo", textoLargo);
        assertEquals(textoLargo, servicio.getString("texto_largo", null));

        String emojisYMultilingue = "Restaurante Gourmet 🍕🍔🍣 | 🇻🇪 Bs. / $ USD | 中文 | العربية";
        servicio.set("mensaje_bienvenida", emojisYMultilingue);
        assertEquals(emojisYMultilingue, servicio.getString("mensaje_bienvenida", null));
    }

    /**
     * Vector 7: Concurrencia masiva y condiciones de carrera (Race Conditions).
     * 20 hilos escribiendo y leyendo concurrentemente sin bloqueos mutuos ni excepciones de concurrencia.
     */
    @Test
    public void vector7_concurrenciaMasivaMultiHilo() throws InterruptedException {
        int numHilos = 20;
        int operacionesPorHilo = 200;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CountDownLatch latch = new CountDownLatch(numHilos);
        AtomicInteger fallos = new AtomicInteger(0);

        for (int h = 0; h < numHilos; h++) {
            final int idHilo = h;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operacionesPorHilo; i++) {
                        String clave = "param_hilo_" + (idHilo % 5);
                        servicio.set(clave, "valor_" + i);
                        String leido = servicio.getString(clave, "default");
                        assertNotNull(leido);
                    }
                } catch (Throwable t) {
                    fallos.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("Timeout esperando hilos concurrentes", latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals("No debe haber excepciones ni carreras críticas en operaciones concurrentes", 0, fallos.get());
    }

    /**
     * Vector 8: Tolerancia a fallos cuando el repositorio arroja excepciones de red/BD.
     */
    @Test
    public void vector8_resilienciaAnteFalloDeRepositorio() {
        ConfiguracionRepositorio repoFallido = new ConfiguracionRepositorio() {
            @Override public String obtener(String clave, String valorPorDefecto) { throw new RuntimeException("DB Connection Timeout"); }
            @Override public void guardar(String clave, String valor) { throw new RuntimeException("DB Disk Full"); }
            @Override public Map<String, String> obtenerTodos() { throw new RuntimeException("DB Down"); }
            @Override public void guardarVarios(Map<String, String> configuraciones) { throw new RuntimeException("DB Down"); }
            @Override public boolean existe(String clave) { return false; }
            @Override public void eliminar(String clave) {}
        };

        ConfiguracionServicio servicioResiliente = new ConfiguracionServicio(repoFallido);

        // Debe devolver los valores por defecto en lugar de colapsar la aplicación
        assertEquals("default_resiliente", servicioResiliente.getString("clave_caida", "default_resiliente"));
        assertEquals(new BigDecimal("36.50"), servicioResiliente.getDecimal("tasa", new BigDecimal("36.50")));
        assertEquals(24, servicioResiliente.getInt("meses", 24));
        assertTrue(servicioResiliente.getBoolean("logo", true));
    }

    /**
     * Vector 9: Integración de LoginDao con el almacenamiento Clave-Valor.
     * Modificar datos en LoginDao actualiza tanto la entidad empresa como los pares clave-valor,
     * y datosEmpresa() fusiona ambos estados de forma transparente.
     */
    @Test
    public void vector9_integracionLoginDaoConClaveValor() {
        LoginDao loginDao = new LoginDao(new ProveedorConexionJdbc(), new PasswordHasher(), repositorioEnMemoria);
        Config estadoOriginal = null;
        try {
            estadoOriginal = loginDao.datosEmpresa();
        } catch (Exception ignored) {
        }

        try {
            Config c = new Config(1, "J-999999999", "Restaurante RedTeam", "0414-1111111", "Caracas", "Mensaje");
            c.setTasaDolar(new BigDecimal("45.2500"));
            c.setIvaPorcentaje(new BigDecimal("12.00"));
            c.setImpresoraTickets("Xprinter XP-80T");
            c.setModoSalidaTickets(ModoSalidaTicket.PDF24_CREATOR);
            c.setImprimirLogoTicket(true);
            c.setClienteDefaultNombre("Cliente Frecuente");
            c.setClienteDefaultDocumento("V-12345678");
            c.setMesesRetencionPedidos(36);

            // Al invocar ModificarDatos, los pares clave-valor se sincronizan en repositorioEnMemoria
            loginDao.ModificarDatos(c);

            assertEquals("45.2500", repositorioEnMemoria.obtener(ConfigClaves.TASA_DOLAR, null));
            assertEquals("12.00", repositorioEnMemoria.obtener(ConfigClaves.IVA_PORCENTAJE, null));
            assertEquals("Xprinter XP-80T", repositorioEnMemoria.obtener(ConfigClaves.IMPRESORA_TICKETS, null));
            assertEquals("PDF24_CREATOR", repositorioEnMemoria.obtener(ConfigClaves.MODO_SALIDA_TICKETS, null));
            assertEquals("true", repositorioEnMemoria.obtener(ConfigClaves.IMPRIMIR_LOGO_TICKET, null));
            assertEquals("Cliente Frecuente", repositorioEnMemoria.obtener(ConfigClaves.CLIENTE_DEFAULT_NOMBRE, null));
            assertEquals("V-12345678", repositorioEnMemoria.obtener(ConfigClaves.CLIENTE_DEFAULT_DOCUMENTO, null));
            assertEquals("36", repositorioEnMemoria.obtener(ConfigClaves.MESES_RETENCION_PEDIDOS, null));
        } finally {
            if (estadoOriginal != null) {
                try {
                    loginDao.ModificarDatos(estadoOriginal);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
