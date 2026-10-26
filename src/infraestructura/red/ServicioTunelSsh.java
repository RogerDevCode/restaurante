package infraestructura.red;

import Modelo.ConfigClaves;
import Servicio.ConfiguracionServicio;
import infraestructura.api.ServidorRestTablets;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor del Túnel Inverso SSH Zero-Trust para Monitoreo Remoto Gerencial.
 * Inicia una conexión de salida segura (outbound) hacia un servidor bastión en la nube
 * (VPS), reenviando de forma inversa el puerto de la API REST local hacia el puerto
 * configurado en el bastión remoto.
 *
 * Características de seguridad:
 * - No requiere abrir puertos en el router del restaurante ni exponer MySQL a internet.
 * - Conexión autenticada mediante par de claves Ed25519 o RSA.
 * - Monitorización continua con reconexión automática ante microcortes de red.
 */
public final class ServicioTunelSsh {

    private static final Logger LOGGER = Logger.getLogger(ServicioTunelSsh.class.getName());

    public enum EstadoTunel {
        DESHABILITADO,
        INICIANDO,
        ACTIVO,
        ERROR,
        DETENIDO
    }

    private static final AtomicReference<EstadoTunel> ESTADO = new AtomicReference<>(EstadoTunel.DESHABILITADO);
    private static final AtomicReference<String> ULTIMO_MENSAJE = new AtomicReference<>("Túnel no iniciado.");
    private static final AtomicBoolean EJECUTANDO = new AtomicBoolean(false);
    private static Process procesoSsh = null;
    private static Thread hiloWatchdog = null;

    private ServicioTunelSsh() {
    }

    public static synchronized void iniciarSiEstaHabilitado() {
        if (EJECUTANDO.get()) {
            return;
        }

        if (!isHabilitado()) {
            ESTADO.set(EstadoTunel.DESHABILITADO);
            ULTIMO_MENSAJE.set("Túnel SSH deshabilitado en configuración.");
            LOGGER.info("Túnel Inverso SSH para monitoreo remoto: DESHABILITADO.");
            return;
        }

        EJECUTANDO.set(true);
        ESTADO.set(EstadoTunel.INICIANDO);
        ULTIMO_MENSAJE.set("Iniciando conexión hacia servidor bastión...");

        hiloWatchdog = Thread.ofVirtual().name("watchdog-tunel-ssh").start(ServicioTunelSsh::cicloWatchdog);
    }

    public static synchronized void detener() {
        EJECUTANDO.set(false);
        if (procesoSsh != null && procesoSsh.isAlive()) {
            procesoSsh.destroy();
            procesoSsh = null;
        }
        if (hiloWatchdog != null) {
            hiloWatchdog.interrupt();
            hiloWatchdog = null;
        }
        ESTADO.set(EstadoTunel.DETENIDO);
        ULTIMO_MENSAJE.set("Túnel detenido por el usuario o cierre de la aplicación.");
        LOGGER.info("Túnel Inverso SSH detenido.");
    }

    public static EstadoTunel getEstado() {
        return ESTADO.get();
    }

    public static String getUltimoMensaje() {
        return ULTIMO_MENSAJE.get();
    }

    public static boolean isHabilitado() {
        String envHab = System.getenv("TUNEL_SSH_HABILITADO");
        if (envHab != null && !envHab.isBlank()) {
            return "true".equalsIgnoreCase(envHab.trim()) || "1".equals(envHab.trim());
        }
        String sysProp = System.getProperty("restaurante.tunel.habilitado");
        if (sysProp != null && !sysProp.isBlank()) {
            return "true".equalsIgnoreCase(sysProp.trim()) || "1".equals(sysProp.trim());
        }
        try {
            ConfiguracionServicio cfg = new ConfiguracionServicio();
            return cfg.getBoolean(ConfigClaves.TUNEL_SSH_HABILITADO, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static String getHostBastion() {
        String prop = System.getProperty("TUNEL_SSH_HOST");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("TUNEL_SSH_HOST");
        if (env != null && !env.isBlank()) return env.trim();
        try {
            return new ConfiguracionServicio().getString(ConfigClaves.TUNEL_SSH_HOST, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static int getPuertoBastion() {
        String prop = System.getProperty("TUNEL_SSH_PUERTO");
        if (prop != null && !prop.isBlank()) {
            try { return Integer.parseInt(prop.trim()); } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv("TUNEL_SSH_PUERTO");
        if (env != null && !env.isBlank()) {
            try { return Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
        }
        try {
            return new ConfiguracionServicio().getInt(ConfigClaves.TUNEL_SSH_PUERTO, 22);
        } catch (Exception e) {
            return 22;
        }
    }

    public static String getUsuarioBastion() {
        String prop = System.getProperty("TUNEL_SSH_USUARIO");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("TUNEL_SSH_USUARIO");
        if (env != null && !env.isBlank()) return env.trim();
        try {
            return new ConfiguracionServicio().getString(ConfigClaves.TUNEL_SSH_USUARIO, "telemetria").trim();
        } catch (Exception e) {
            return "telemetria";
        }
    }

    public static int getPuertoRemoto() {
        String prop = System.getProperty("TUNEL_SSH_PUERTO_REMOTO");
        if (prop != null && !prop.isBlank()) {
            try { return Integer.parseInt(prop.trim()); } catch (NumberFormatException ignored) {}
        }
        String env = System.getenv("TUNEL_SSH_PUERTO_REMOTO");
        if (env != null && !env.isBlank()) {
            try { return Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
        }
        try {
            return new ConfiguracionServicio().getInt(ConfigClaves.TUNEL_SSH_PUERTO_REMOTO, 8443);
        } catch (Exception e) {
            return 8443;
        }
    }

    public static String getRutaClavePrivada() {
        String prop = System.getProperty("TUNEL_SSH_CLAVE_PRIVADA");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("TUNEL_SSH_CLAVE_PRIVADA");
        if (env != null && !env.isBlank()) return env.trim();
        try {
            String cfg = new ConfiguracionServicio().getString(ConfigClaves.TUNEL_SSH_CLAVE_PRIVADA, "").trim();
            if (!cfg.isBlank()) return cfg;
        } catch (Exception ignored) {}

        // Búsqueda en rutas por defecto
        Path p1 = Paths.get("capip_claves_maestras", "tunel_ed25519");
        if (Files.exists(p1)) return p1.toAbsolutePath().toString();
        Path p2 = Paths.get("..", "capip_claves_maestras", "tunel_ed25519");
        if (Files.exists(p2)) return p2.toAbsolutePath().toString();
        Path p3 = Paths.get(System.getProperty("user.home"), ".ssh", "id_ed25519");
        if (Files.exists(p3)) return p3.toAbsolutePath().toString();

        return "";
    }

    public static String getTokenAdmin() {
        String prop = System.getProperty("TUNEL_SSH_TOKEN_ADMIN");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("TUNEL_SSH_TOKEN_ADMIN");
        if (env != null && !env.isBlank()) return env.trim();
        try {
            return new ConfiguracionServicio().getString(ConfigClaves.TUNEL_SSH_TOKEN_ADMIN, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isSshInstalado() {
        try {
            Process p = new ProcessBuilder("ssh", "-V").start();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ciclo continuo de supervisión y reconexión automática del túnel SSH.
     */
    private static void cicloWatchdog() {
        int reintentos = 0;

        while (EJECUTANDO.get()) {
            String host = getHostBastion();
            if (host.isBlank()) {
                ESTADO.set(EstadoTunel.ERROR);
                ULTIMO_MENSAJE.set("Configuración incompleta: No se ha definido el Host del bastión.");
                pausar(15_000);
                continue;
            }

            int puertoBastion = getPuertoBastion();
            String usuario = getUsuarioBastion();
            int puertoRemoto = getPuertoRemoto();
            int puertoLocal = ServidorRestTablets.getPuerto();
            String rutaClave = getRutaClavePrivada();

            List<String> comando = new ArrayList<>();
            comando.add("ssh");
            comando.add("-N"); // Sin comando interactivo ni shell
            comando.add("-R"); // Remote port forward
            comando.add(puertoRemoto + ":localhost:" + puertoLocal);
            comando.add("-p");
            comando.add(String.valueOf(puertoBastion));
            comando.add("-o");
            comando.add("ServerAliveInterval=30");
            comando.add("-o");
            comando.add("ServerAliveCountMax=3");
            comando.add("-o");
            comando.add("ExitOnForwardFailure=yes");
            comando.add("-o");
            comando.add("StrictHostKeyChecking=accept-new");

            if (!rutaClave.isBlank() && new File(rutaClave).exists()) {
                comando.add("-i");
                comando.add(rutaClave);
            }

            comando.add(usuario + "@" + host);

            LOGGER.info("Iniciando Túnel SSH Zero-Trust: " + String.join(" ", comando));

            try {
                ProcessBuilder pb = new ProcessBuilder(comando);
                pb.redirectErrorStream(true);
                procesoSsh = pb.start();

                ESTADO.set(EstadoTunel.ACTIVO);
                ULTIMO_MENSAJE.set("Túnel activo: " + host + ":" + puertoRemoto + " -> localhost:" + puertoLocal);
                LOGGER.info("================================================================================");
                LOGGER.info(" [OK] TÚNEL INVERSO SSH ZERO-TRUST CONECTADO");
                LOGGER.info(" Acceso Gerencial Móvil: http://" + host + ":" + puertoRemoto + "/gerencia");
                LOGGER.info("================================================================================");

                reintentos = 0; // Resetear contador tras conexión exitosa

                // Leer logs del proceso SSH
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(procesoSsh.getInputStream()))) {
                    String linea;
                    while ((linea = reader.readLine()) != null && EJECUTANDO.get()) {
                        LOGGER.fine("[SSH-TUNNEL] " + linea);
                    }
                }

                int exitCode = procesoSsh.waitFor();
                if (EJECUTANDO.get()) {
                    ESTADO.set(EstadoTunel.ERROR);
                    ULTIMO_MENSAJE.set("Túnel SSH desconectado (código " + exitCode + "). Reconectando...");
                    LOGGER.warning("El proceso SSH terminó con código " + exitCode + ". Reconectando...");
                }

            } catch (Exception ex) {
                if (EJECUTANDO.get()) {
                    ESTADO.set(EstadoTunel.ERROR);
                    ULTIMO_MENSAJE.set("Error ejecutando cliente SSH: " + ex.getMessage());
                    LOGGER.log(Level.WARNING, "Error al lanzar proceso SSH: " + ex.getMessage());
                }
            }

            if (EJECUTANDO.get()) {
                reintentos++;
                long esperaMs = Math.min(30_000L, 2_000L * reintentos);
                LOGGER.info("Esperando " + (esperaMs / 1000) + " segundos antes de reintentar túnel...");
                pausar(esperaMs);
            }
        }
    }

    private static void pausar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
