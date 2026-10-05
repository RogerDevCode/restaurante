package Servicio;

import Modelo.DataAccessException;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.Pedidos;
import Modelo.PedidosRepositorio;
import Modelo.PedidosRepositorioFalso;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PedidoServicioTest {
    private final Logger logger = Logger.getLogger(ErrorAplicacionException.class.getName());
    private final CapturadorLogs capturador = new CapturadorLogs();
    private Level nivelOriginal;
    private boolean padresOriginales;

    @Before
    public void capturarLogs() {
        nivelOriginal = logger.getLevel();
        padresOriginales = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(capturador);
    }

    @After
    public void restaurarLogger() {
        logger.removeHandler(capturador);
        logger.setLevel(nivelOriginal);
        logger.setUseParentHandlers(padresOriginales);
    }

    @Test
    public void pedidoSinDetallesFallaAntesDeAccederAlRepositorio() {
        AtomicInteger llamadas = new AtomicInteger();
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
                llamadas.incrementAndGet();
                return 12;
            }
        });

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(pedidoValido(), new ArrayList<DetallePedido>()));

        assertEquals(0, llamadas.get());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.WARNING, capturador.registros.get(0).getLevel());
    }

    @Test
    public void rechazaTotalQueNoCoincideConLaSumaDeDetalles() {
        AtomicInteger llamadas = new AtomicInteger();
        Pedidos pedido = pedidoValido();
        pedido.setTotalDecimal(new BigDecimal("2499.99"));
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos recibido, List<DetallePedido> detalles) {
                llamadas.incrementAndGet();
                return 12;
            }
        });

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> servicio.registrarPedidoCompleto(pedido, Arrays.asList(detalleValido())));

        assertEquals(0, llamadas.get());
    }

    @Test
    public void delegaPedidoCompletoYDevuelveElIdPersistido() {
        Pedidos pedido = pedidoValido();
        List<DetallePedido> detalles = Arrays.asList(detalleValido());
        PedidosRepositorio repositorio = new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos recibido, List<DetallePedido> recibidos) {
                assertSame(pedido, recibido);
                assertSame(detalles, recibidos);
                return 42;
            }
        };

        assertEquals(42, new PedidoServicio(repositorio).registrarPedidoCompleto(pedido, detalles));
    }

    @Test
    public void propagaFalloDelRepositorioSinVolverARegistrarlo() {
        DataAccessException fallo = new DataAccessException("BD no disponible", new IllegalStateException());
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso() {
            @Override
            public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
                throw fallo;
            }
        });

        DataAccessException propagado = org.junit.Assert.assertThrows(DataAccessException.class,
                () -> servicio.registrarPedidoCompleto(pedidoValido(), Arrays.asList(detalleValido())));

        assertSame(fallo, propagado);
        assertEquals(1, capturador.registros.size());
    }

    private Pedidos pedidoValido() {
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(3);
        pedido.setTotalDecimal(new BigDecimal("2500.00"));
        pedido.setUsuario("Ana");
        return pedido;
    }

    private DetallePedido detalleValido() {
        DetallePedido detalle = new DetallePedido();
        detalle.setNombre("Plato del día");
        detalle.setCantidad(1);
        detalle.setPrecioDecimal(new BigDecimal("2500.00"));
        return detalle;
    }

    private static final class CapturadorLogs extends Handler {
        private final List<LogRecord> registros = new ArrayList<>();

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
