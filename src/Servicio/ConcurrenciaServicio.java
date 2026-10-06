package Servicio;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Gestor central de concurrencia basado en Virtual Threads de Java 21 (JEP 444).
 * Proporciona despacho de tareas I/O asíncronas no bloqueantes con puente garantizado
 * al Event Dispatch Thread (EDT) de Swing para actualización segura de interfaces gráficas.
 */
public final class ConcurrenciaServicio {

    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private ConcurrenciaServicio() {
        // Constructor privado para clase de utilidad estática
    }

    /**
     * Ejecuta una tarea I/O o de cómputo en un hilo virtual ligero de Java 21
     * y entrega el resultado o error de manera segura en el Event Dispatch Thread (EDT).
     *
     * @param <T> Tipo del valor retornado por la tarea
     * @param backgroundTask Proveedor de la tarea a ejecutar en segundo plano
     * @param onExitoEnEdt Consumidor para procesar el resultado en el EDT (opcional)
     * @param onErrorEnEdt Consumidor para procesar errores en el EDT (opcional)
     * @return Future que representa la ejecución asíncrona
     */
    public static <T> Future<?> ejecutarAsync(Supplier<T> backgroundTask,
                                             Consumer<T> onExitoEnEdt,
                                             Consumer<Throwable> onErrorEnEdt) {
        Objects.requireNonNull(backgroundTask, "backgroundTask no puede ser nula");

        return VIRTUAL_EXECUTOR.submit(() -> {
            try {
                T resultado = backgroundTask.get();
                if (onExitoEnEdt != null) {
                    SwingUtilities.invokeLater(() -> onExitoEnEdt.accept(resultado));
                }
            } catch (Throwable ex) {
                if (onErrorEnEdt != null) {
                    SwingUtilities.invokeLater(() -> onErrorEnEdt.accept(ex));
                }
            }
        });
    }

    /**
     * Ejecuta una acción Runnable asíncrona en un hilo virtual.
     *
     * @param tarea Acción a ejecutar en segundo plano
     * @return Future de la tarea enviada
     */
    public static Future<?> ejecutar(Runnable tarea) {
        Objects.requireNonNull(tarea, "La tarea no puede ser nula");
        return VIRTUAL_EXECUTOR.submit(tarea);
    }

    /**
     * Obtiene el ExecutorService de Virtual Threads de Java 21.
     *
     * @return Instancia compartida de ExecutorService basado en Virtual Threads
     */
    public static ExecutorService getVirtualExecutor() {
        return VIRTUAL_EXECUTOR;
    }
}
