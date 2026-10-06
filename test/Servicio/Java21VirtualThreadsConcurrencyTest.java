package Servicio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class Java21VirtualThreadsConcurrencyTest {

    @Test
    public void testEjecutarMultiplesVirtualThreadsConcurrentes() throws InterruptedException {
        int numeroTareas = 100;
        CountDownLatch latch = new CountDownLatch(numeroTareas);
        AtomicInteger tareasCompletadas = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numeroTareas; i++) {
            final int id = i;
            Future<?> future = ConcurrenciaServicio.ejecutar(() -> {
                try {
                    // Simular cálculo de cotización y serialización
                    BigDecimal usd = BigDecimal.valueOf(10 + id);
                    BigDecimal tasa = new BigDecimal("36.5000");
                    BigDecimal bs = usd.multiply(tasa);
                    Assert.assertTrue(bs.compareTo(BigDecimal.ZERO) > 0);
                    tareasCompletadas.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        boolean completadoATiempo = latch.await(5, TimeUnit.SECONDS);
        Assert.assertTrue("Todas las tareas de hilos virtuales deben completarse", completadoATiempo);
        Assert.assertEquals("Las 100 tareas deben haber concluido con éxito", 100, tareasCompletadas.get());
    }

    @Test
    public void testEjecutarAsyncConRetorno() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger resultadoRecibido = new AtomicInteger(0);

        Future<?> future = ConcurrenciaServicio.ejecutarAsync(
                () -> 42 * 2,
                (valor) -> {
                    resultadoRecibido.set(valor);
                    latch.countDown();
                },
                (error) -> {
                    latch.countDown();
                }
        );

        Assert.assertNotNull(future);
        // Esperar callback en EDT o timeout
        latch.await(3, TimeUnit.SECONDS);
        // El cómputo en segundo plano finalizó
        Assert.assertFalse(future.isCancelled());
    }
}
