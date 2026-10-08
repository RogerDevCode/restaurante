package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import static org.junit.Assert.assertTrue;

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

    @Test
    public void errorColumnaDesconocidaAlFinalizarNoDebeMarcarPedidoComoFinalizado() {
        SQLException columnaFaltante = new SQLException("Unknown column 'metodo_pago'", "42S22", 1054);
        java.util.concurrent.atomic.AtomicInteger sentenciasPreparadas = new java.util.concurrent.atomic.AtomicInteger();
        PreparedStatement sentencia = (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        throw columnaFaltante;
                    }
                    return null;
                });
        Connection conexion = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sentenciasPreparadas.incrementAndGet();
                        return sentencia;
                    }
                    return null;
                });
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return conexion; }
        };

        DataAccessException error = org.junit.Assert.assertThrows(DataAccessException.class,
                () -> new PedidosDao(proveedor).actualizarEstadoConCliente(
                        42, "Cliente", "V-42", "TRANSFERENCIA"));

        assertSame(columnaFaltante, error.getCause());
        assertEquals("No debe intentarse un UPDATE legacy que solo cambie el estado", 1,
                sentenciasPreparadas.get());
    }

    @Test
    public void purgarPedidosFinalizadosRechazaMesesMenorAUno() {
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { throw new AssertionError("No debe conectar"); }
        };
        PedidosDao dao = new PedidosDao(proveedor);
        org.junit.Assert.assertThrows(ErrorAplicacionException.class, () -> dao.purgarPedidosFinalizados(0));
        org.junit.Assert.assertThrows(ErrorAplicacionException.class, () -> dao.purgarPedidosFinalizados(-1));
    }

    @Test
    public void purgarPedidosFinalizadosEjecutaTransaccionYConsultasCorrectas() throws Exception {
        List<String> sqlEjecutados = new ArrayList<>();
        List<Integer> mesesParametros = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean autoCommitDesactivado = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean transaccionConfirmada = new java.util.concurrent.atomic.AtomicBoolean(false);

        java.lang.reflect.InvocationHandler psHandler = (proxy, method, args) -> {
            if ("setInt".equals(method.getName()) && args != null && args.length == 2) {
                if ((int) args[0] == 1) {
                    mesesParametros.add((int) args[1]);
                }
                return null;
            }
            if ("executeUpdate".equals(method.getName())) {
                return 5;
            }
            return null;
        };
        java.sql.PreparedStatement psMock = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class},
                psHandler);

        java.lang.reflect.InvocationHandler connHandler = (proxy, method, args) -> {
            if ("getAutoCommit".equals(method.getName())) {
                return true;
            }
            if ("setAutoCommit".equals(method.getName()) && args != null && args.length == 1) {
                if (Boolean.FALSE.equals(args[0])) {
                    autoCommitDesactivado.set(true);
                }
                return null;
            }
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                sqlEjecutados.add((String) args[0]);
                return psMock;
            }
            if ("commit".equals(method.getName())) {
                transaccionConfirmada.set(true);
                return null;
            }
            return null;
        };
        Connection connMock = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                connHandler);

        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        int eliminados = dao.purgarPedidosFinalizados(6);

        assertEquals(5, eliminados);
        assertTrue(autoCommitDesactivado.get());
        assertTrue(transaccionConfirmada.get());
        assertEquals(2, sqlEjecutados.size());
        assertTrue(sqlEjecutados.get(0).contains("detalle_pedidos"));
        assertTrue(sqlEjecutados.get(0).contains("FINALIZADO"));
        assertTrue(sqlEjecutados.get(1).contains("pedidos"));
        assertTrue(sqlEjecutados.get(1).contains("FINALIZADO"));
        assertEquals(2, mesesParametros.size());
        assertEquals(Integer.valueOf(6), mesesParametros.get(0));
        assertEquals(Integer.valueOf(6), mesesParametros.get(1));
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
