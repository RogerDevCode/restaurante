package Servicio;

import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PedidoPdfServicioTest {
    @Rule
    public final TemporaryFolder temporal = new TemporaryFolder();

    @Test
    public void coordinaConsultasGeneracionYAperturaPorDependenciasInyectadas() {
        AtomicReference<Path> abierto = new AtomicReference<>();
        PedidoPdfServicio servicio = new PedidoPdfServicio(
                id -> pedido(),
                id -> Collections.singletonList(detalle()),
                this::configuracion,
                new GeneradorPdfPedido(temporal.getRoot().toPath()),
                abierto::set);

        servicio.generar(42);

        assertEquals(temporal.getRoot().toPath().resolve("pedido-42.pdf"), abierto.get());
    }

    @Test
    public void fallaAlAbrirPropagaCausaYRegistraUnSevere() throws Exception {
        Logger logger = Logger.getLogger(ErrorAplicacionException.class.getName());
        CapturadorLogs capturador = new CapturadorLogs();
        Level nivelAnterior = logger.getLevel();
        boolean padresAnteriores = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(capturador);
        IOException causa = new IOException("apertura de PDF fallida");
        try {
            PedidoPdfServicio servicio = new PedidoPdfServicio(
                    id -> pedido(),
                    id -> Collections.singletonList(detalle()),
                    this::configuracion,
                    new GeneradorPdfPedido(temporal.getRoot().toPath()),
                    archivo -> { throw causa; });

            ErrorAplicacionException error = org.junit.Assert.assertThrows(
                    ErrorAplicacionException.class, () -> servicio.generar(42));

            assertSame(causa, error.getCause());
            assertEquals(1, capturador.registros.size());
            assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
            assertSame(causa, capturador.registros.get(0).getThrown());
            assertTrue(temporal.getRoot().toPath().resolve("pedido-42.pdf").toFile().isFile());
        } finally {
            logger.removeHandler(capturador);
            logger.setLevel(nivelAnterior);
            logger.setUseParentHandlers(padresAnteriores);
        }
    }

    private Pedidos pedido() {
        Pedidos pedido = new Pedidos();
        pedido.setId(42);
        pedido.setNum_mesa(4);
        pedido.setSala("Principal");
        pedido.setUsuario("Operador");
        pedido.setFecha("2026-10-05");
        pedido.setTotalDecimal(new BigDecimal("10.00"));
        return pedido;
    }

    private Config configuracion() {
        return new Config(1, "123", "Restaurante de prueba", "+56 9", "Santiago", "Gracias");
    }

    private DetallePedido detalle() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato de prueba");
        detalle.setPrecioDecimal(new BigDecimal("10.00"));
        detalle.setCantidad(1);
        return detalle;
    }

    private static final class CapturadorLogs extends Handler {
        private final java.util.List<LogRecord> registros = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord registro) {
            if (registro != null && isLoggable(registro)) {
                registros.add(registro);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
