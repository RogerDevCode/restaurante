package infraestructura.impresion;

import Modelo.ComandaMarcha;
import Modelo.ConfigClaves;
import Servicio.ConfiguracionServicio;
import Servicio.ServicioComandaCocina;
import Servicio.ServicioImpresionTicket;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spooler bidireccional de cocina TCP RAW (Puerto 9100) con comprobación previa DLE EOT,
 * protección mediante Circuit Breaker y Failover automático a la impresora de barra/caja.
 */
public final class SpoolerCocinaTcp {

    private static final Logger LOGGER = Logger.getLogger(SpoolerCocinaTcp.class.getName());
    private static final Path DIR_COLA_COCINA = Paths.get("tickets", "cola_cocina");
    private static final int TIMEOUT_CONEXION_MS = 2500;
    private static final int TIMEOUT_SONDEO_MS = 1500;

    private static final CircuitBreakerImpresora CIRCUIT_BREAKER = new CircuitBreakerImpresora(3, 30_000L);

    public enum ModoResultado {
        NO_CONFIGURADA,
        EXITO_TCP_DIRECTO,
        FAILOVER_BARRA,
        ENCOLADO_DISCO
    }

    public record ResultadoDespacho(ModoResultado modo, String detalle) {
        public boolean isExitoso() {
            return modo == ModoResultado.EXITO_TCP_DIRECTO || modo == ModoResultado.FAILOVER_BARRA;
        }
    }

    private SpoolerCocinaTcp() {
    }

    public static CircuitBreakerImpresora getCircuitBreaker() {
        return CIRCUIT_BREAKER;
    }

    /**
     * Resuelve la IP configurada para la impresora de cocina desde base de datos,
     * variables de entorno o system properties.
     */
    public static String obtenerIpCocinaConfigurada() {
        String ip = System.getProperty("IMPRESORA_COCINA_IP");
        if (ip == null || ip.isBlank()) {
            ip = System.getenv("IMPRESORA_COCINA_IP");
        }
        if (ip == null || ip.isBlank()) {
            try {
                ConfiguracionServicio cfg = new ConfiguracionServicio();
                ip = cfg.getString(ConfigClaves.IMPRESORA_COCINA_IP, "");
            } catch (Exception ignored) {
            }
        }
        return ip != null ? ip.trim() : "";
    }

    public static int obtenerPuertoCocinaConfigurado() {
        int puerto = 9100;
        String pStr = System.getProperty("IMPRESORA_COCINA_PUERTO");
        if (pStr == null || pStr.isBlank()) {
            pStr = System.getenv("IMPRESORA_COCINA_PUERTO");
        }
        if (pStr == null || pStr.isBlank()) {
            try {
                ConfiguracionServicio cfg = new ConfiguracionServicio();
                puerto = cfg.getInt(ConfigClaves.IMPRESORA_COCINA_PUERTO, 9100);
            } catch (Exception ignored) {
            }
        } else {
            try {
                puerto = Integer.parseInt(pStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return puerto;
    }

    public static boolean isFailoverHabilitado() {
        try {
            ConfiguracionServicio cfg = new ConfiguracionServicio();
            return cfg.getBoolean(ConfigClaves.IMPRESORA_COCINA_FAILOVER, true);
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isBuzzerHabilitado() {
        try {
            ConfiguracionServicio cfg = new ConfiguracionServicio();
            return cfg.getBoolean(ConfigClaves.IMPRESORA_COCINA_BUZZER, true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Despacha una marcha de comanda hacia la impresora de cocina vía TCP RAW 9100.
     * Si no hay IP configurada, devuelve NO_CONFIGURADA para usar el flujo tradicional de driver.
     * Si la impresora está inaccesible o en fallo (Circuit Breaker OPEN o DLE EOT sin papel),
     * encola en disco y ejecuta el failover hacia la impresora de barra/caja.
     */
    public static ResultadoDespacho despacharComanda(
            ComandaMarcha marcha,
            String nombreSala,
            int numMesa,
            String nombreMesonero) {

        String ip = obtenerIpCocinaConfigurada();
        if (ip.isBlank()) {
            return new ResultadoDespacho(ModoResultado.NO_CONFIGURADA, "Sin impresora TCP configurada; usando spooler del sistema.");
        }

        int puerto = obtenerPuertoCocinaConfigurado();
        boolean failoverActivo = isFailoverHabilitado();
        boolean buzzer = isBuzzerHabilitado();

        // 1. Evaluar Circuit Breaker
        if (!CIRCUIT_BREAKER.permiteIntento()) {
            String motivo = "Circuit Breaker en estado OPEN (" + CIRCUIT_BREAKER.getUltimaCausaFallo() + ")";
            LOGGER.warning(() -> "Spooler Cocina TCP: " + motivo + ". Desviando trabajo a cola y failover.");
            guardarEnColaDisco(marcha, nombreSala, numMesa, nombreMesonero, buzzer);
            if (failoverActivo) {
                ejecutarFailoverBarra(marcha, nombreSala, numMesa, nombreMesonero);
                return new ResultadoDespacho(ModoResultado.FAILOVER_BARRA, motivo);
            }
            return new ResultadoDespacho(ModoResultado.ENCOLADO_DISCO, motivo);
        }

        // 2. Intento de conexión y sondeo DLE EOT
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, puerto), TIMEOUT_CONEXION_MS);

            ProtocoloEscPosDleEot.EstadoImpresora estado = ProtocoloEscPosDleEot.sondear(socket, TIMEOUT_SONDEO_MS);
            if (!estado.isListaParaImprimir()) {
                String errorSondeo = "Impresora no lista: " + estado.descripcion();
                LOGGER.warning(() -> "Spooler Cocina TCP: " + errorSondeo);
                CIRCUIT_BREAKER.registrarFallo(errorSondeo);
                guardarEnColaDisco(marcha, nombreSala, numMesa, nombreMesonero, buzzer);
                if (failoverActivo) {
                    ejecutarFailoverBarra(marcha, nombreSala, numMesa, nombreMesonero);
                    return new ResultadoDespacho(ModoResultado.FAILOVER_BARRA, errorSondeo);
                }
                return new ResultadoDespacho(ModoResultado.ENCOLADO_DISCO, errorSondeo);
            }

            // 3. Generar y transmitir bytes ESC/POS
            byte[] bytes = GeneradorEscPosComanda.generarBytesComanda(marcha, nombreSala, numMesa, nombreMesonero, false, buzzer);
            OutputStream os = socket.getOutputStream();
            os.write(bytes);
            os.flush();

            CIRCUIT_BREAKER.registrarExito();
            LOGGER.info(() -> "Comanda de cocina enviada directamente por TCP RAW a " + ip + ":" + puerto
                    + " [Pedido #" + marcha.idPedido() + ", Marcha #" + marcha.secuencia() + "]");

            // 4. Intentar vaciar cola pendiente acumulada en disco
            vaciarColaDiscoPendiente(ip, puerto, buzzer);

            return new ResultadoDespacho(ModoResultado.EXITO_TCP_DIRECTO, "Impreso exitosamente en " + ip + ":" + puerto);

        } catch (Exception ex) {
            String errorConexion = "Fallo de conexión TCP (" + ex.getMessage() + ")";
            LOGGER.log(Level.WARNING, "Spooler Cocina TCP a " + ip + ":" + puerto + " fallo: " + ex.getMessage());
            CIRCUIT_BREAKER.registrarFallo(errorConexion);
            guardarEnColaDisco(marcha, nombreSala, numMesa, nombreMesonero, buzzer);
            if (failoverActivo) {
                ejecutarFailoverBarra(marcha, nombreSala, numMesa, nombreMesonero);
                return new ResultadoDespacho(ModoResultado.FAILOVER_BARRA, errorConexion);
            }
            return new ResultadoDespacho(ModoResultado.ENCOLADO_DISCO, errorConexion);
        }
    }

    private static void ejecutarFailoverBarra(
            ComandaMarcha marcha,
            String nombreSala,
            int numMesa,
            String nombreMesonero) {
        LOGGER.warning(() -> ">>> ACTIVANDO FAILOVER A IMPRESORA DE BARRA/CAJA PARA PEDIDO #" + marcha.idPedido() + " <<<");
        try {
            // Imprimir vía documento PDF estándar con marca de agua/aviso de emergencia
            Path archivoComanda = ServicioComandaCocina.generarDocumentoComanda(marcha, nombreSala, numMesa, nombreMesonero);
            ServicioImpresionTicket.procesarSalida(archivoComanda);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Fallo crítico en Failover de comanda: " + ex.getMessage(), ex);
        }
    }

    public static synchronized void guardarEnColaDisco(
            ComandaMarcha marcha,
            String nombreSala,
            int numMesa,
            String nombreMesonero,
            boolean buzzer) {
        try {
            Files.createDirectories(DIR_COLA_COCINA);
            byte[] bytes = GeneradorEscPosComanda.generarBytesComanda(marcha, nombreSala, numMesa, nombreMesonero, false, buzzer);
            String nombreArchivo = String.format("comanda_%d_%d_m%d.raw",
                    System.currentTimeMillis(), marcha.idPedido(), marcha.secuencia());
            Path destino = DIR_COLA_COCINA.resolve(nombreArchivo);
            Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LOGGER.info(() -> "Comanda respaldada en cola en disco: " + destino.getFileName());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "No se pudo persistir la comanda en la cola en disco: " + ex.getMessage(), ex);
        }
    }

    public static synchronized void vaciarColaDiscoPendiente(String ip, int puerto, boolean buzzer) {
        if (!Files.isDirectory(DIR_COLA_COCINA)) return;

        try (var stream = Files.list(DIR_COLA_COCINA)) {
            Path[] archivos = stream.filter(p -> p.getFileName().toString().endsWith(".raw"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toArray(Path[]::new);

            if (archivos.length == 0) return;

            LOGGER.info(() -> "Vaciando " + archivos.length + " comandas pendientes desde la cola en disco...");

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, puerto), TIMEOUT_CONEXION_MS);
                ProtocoloEscPosDleEot.EstadoImpresora estado = ProtocoloEscPosDleEot.sondear(socket, TIMEOUT_SONDEO_MS);
                if (!estado.isListaParaImprimir()) return;

                OutputStream os = socket.getOutputStream();
                for (Path archivo : archivos) {
                    byte[] data = Files.readAllBytes(archivo);
                    os.write(data);
                    os.flush();
                    Files.deleteIfExists(archivo);
                    LOGGER.info(() -> "Comanda pendiente recuperada y enviada: " + archivo.getFileName());
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso vaciando cola en disco: " + ex.getMessage());
        }
    }
}
