package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PedidosDaoTest {
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
    public void propagaFalloDelProveedorConCausaYUnSoloRegistro() {
        SQLException causa = new SQLException("Fallo de conexión inducido", "08001");
        ProveedorConexionJdbc proveedorFalso = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() throws SQLException {
                throw causa;
            }
        };

        DataAccessException error = org.junit.Assert.assertThrows(
                DataAccessException.class,
                () -> new PedidosDao(proveedorFalso).verificarStado(1, 1));

        assertSame(causa, error.getCause());
        assertEquals(1, capturador.registros.size());
        assertEquals(Level.SEVERE, capturador.registros.get(0).getLevel());
        assertSame(causa, capturador.registros.get(0).getThrown());
    }

    @Test
    public void rechazaTotalQueNoCuadraAntesDeAbrirConexion() {
        ProveedorConexionJdbc proveedorFalso = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                throw new AssertionError("El importe debe validarse antes de conectar");
            }
        };
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(1);
        pedido.setNum_mesa(1);
        pedido.setUsuario("Ana");
        pedido.setTotalDecimal(new BigDecimal("4.00"));
        DetallePedido detalle = new DetallePedido(0, "Agua", new BigDecimal("3.00"), 1, "", 0);

        org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new PedidosDao(proveedorFalso).registrarPedidoCompleto(pedido, Arrays.asList(detalle)));
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
