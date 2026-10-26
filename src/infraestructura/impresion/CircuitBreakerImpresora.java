package infraestructura.impresion;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Patrón Circuit Breaker para proteger el subsistema de comandas de cocina contra impresoras caídas,
 * atascadas o sin papel en la red.
 *
 * Estados:
 * - CLOSED: Operación normal; todos los pedidos intentan la ruta directa TCP.
 * - OPEN: Impresora en falla; no se bloquean sockets y los trabajos se redirigen de inmediato
 *         a cola en disco y/o impresora espejo de barra.
 * - HALF_OPEN: Periodo de prueba para verificar si la impresora se ha recuperado.
 */
public final class CircuitBreakerImpresora {

    private static final Logger LOGGER = Logger.getLogger(CircuitBreakerImpresora.class.getName());

    public enum Estado {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int umbralFallosConsecutivos;
    private final long tiempoEnfriamientoMs;

    private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.CLOSED);
    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    private final AtomicLong ultimoFalloMs = new AtomicLong(0);
    private final AtomicReference<String> ultimaCausaFallo = new AtomicReference<>("");

    public CircuitBreakerImpresora() {
        this(3, 30_000L); // 3 fallos para abrir; 30 segundos de enfriamiento
    }

    public CircuitBreakerImpresora(int umbralFallosConsecutivos, long tiempoEnfriamientoMs) {
        this.umbralFallosConsecutivos = umbralFallosConsecutivos;
        this.tiempoEnfriamientoMs = tiempoEnfriamientoMs;
    }

    /**
     * Determina si la petición de impresión debe intentar el canal directo TCP.
     */
    public boolean permiteIntento() {
        Estado actual = estado.get();
        if (actual == Estado.CLOSED) {
            return true;
        }

        long transcurrido = System.currentTimeMillis() - ultimoFalloMs.get();
        if (actual == Estado.OPEN) {
            if (transcurrido >= tiempoEnfriamientoMs) {
                if (estado.compareAndSet(Estado.OPEN, Estado.HALF_OPEN)) {
                    LOGGER.info("Circuit Breaker Cocina: Tiempo de enfriamiento cumplido (" + transcurrido
                            + " ms). Transición a HALF_OPEN para sondeo de prueba.");
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN
        return true;
    }

    /**
     * Registra un despacho o sondeo exitoso. Cierra el circuito y resetea contadores.
     */
    public void registrarExito() {
        Estado anterior = estado.getAndSet(Estado.CLOSED);
        fallosConsecutivos.set(0);
        ultimaCausaFallo.set("");
        if (anterior != Estado.CLOSED) {
            LOGGER.info("Circuit Breaker Cocina: Impresora restablecida. Circuito CLOSED.");
        }
    }

    /**
     * Registra un fallo de conexión o sondeo insatisfactorio (ej. sin papel).
     */
    public void registrarFallo(String causa) {
        ultimoFalloMs.set(System.currentTimeMillis());
        ultimaCausaFallo.set(causa);
        int fallos = fallosConsecutivos.incrementAndGet();

        Estado actual = estado.get();
        if (actual == Estado.HALF_OPEN) {
            estado.set(Estado.OPEN);
            LOGGER.warning("Circuit Breaker Cocina: Fallo durante HALF_OPEN (" + causa + "). Regreso a OPEN por "
                    + tiempoEnfriamientoMs + " ms.");
        } else if (actual == Estado.CLOSED && fallos >= umbralFallosConsecutivos) {
            estado.set(Estado.OPEN);
            LOGGER.warning("Circuit Breaker Cocina: Umbral de fallos superado (" + fallos + "/"
                    + umbralFallosConsecutivos + " - " + causa + "). Circuito OPEN activado. Failover activo.");
        }
    }

    public Estado getEstado() {
        // Evaluar posible transición temporal a HALF_OPEN
        if (estado.get() == Estado.OPEN && (System.currentTimeMillis() - ultimoFalloMs.get()) >= tiempoEnfriamientoMs) {
            estado.compareAndSet(Estado.OPEN, Estado.HALF_OPEN);
        }
        return estado.get();
    }

    public int getFallosConsecutivos() {
        return fallosConsecutivos.get();
    }

    public String getUltimaCausaFallo() {
        return ultimaCausaFallo.get();
    }

    public void resetear() {
        estado.set(Estado.CLOSED);
        fallosConsecutivos.set(0);
        ultimoFalloMs.set(0);
        ultimaCausaFallo.set("");
    }
}
