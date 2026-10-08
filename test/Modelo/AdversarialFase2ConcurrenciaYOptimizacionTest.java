package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas adversarias para las mejoras de Fase 2:
 * - Control concurrente optimista en cierre de pedidos (prevención de cierre doble).
 * - Consultas SARGable en ClienteDao para uso de índices.
 * - Integridad del archivo de migración 011.
 */
public class AdversarialFase2ConcurrenciaYOptimizacionTest {

    @Test
    public void finalizacionExitosaCuandoPedidoEstaPendiente() throws Exception {
        List<String> sqlEjecutados = new ArrayList<>();

        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return 1; // 1 fila afectada
            }
            return null;
        };
        PreparedStatement psMock = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, psHandler);

        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                sqlEjecutados.add((String) args[0]);
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, connHandler);

        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        boolean exito = dao.actualizarEstadoConCliente(42, "Juan Perez", "V-12345678", "TRANSFERENCIA");

        assertTrue("La finalización debe ser exitosa", exito);
        assertEquals(2, sqlEjecutados.size());
        assertTrue(sqlEjecutados.get(0).contains("WHERE id = ? AND estado = 'PENDIENTE'"));
    }

    @Test
    public void rechazaFinalizacionConcurrenteSiPedidoYaFueFinalizado() throws Exception {
        InvocationHandler rsHandler = (proxy, method, args) -> {
            if ("next".equals(method.getName())) {
                return true;
            }
            if ("getString".equals(method.getName())) {
                return "FINALIZADO"; // Ya fue finalizado previamente
            }
            return null;
        };
        ResultSet rsMock = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, rsHandler);

        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return 0; // 0 filas afectadas (otra sesión ganó la carrera)
            }
            if ("executeQuery".equals(method.getName())) {
                return rsMock;
            }
            return null;
        };
        PreparedStatement psMock = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, psHandler);

        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, connHandler);

        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class,
                () -> dao.actualizarEstadoConCliente(42, "Juan Perez", "V-12345678", "EFECTIVO"));

        assertTrue("Debe alertar que el pedido ya fue finalizado",
                ex.getMessage().contains("ya fue finalizado previamente por otro usuario"));
    }

    @Test
    public void rechazaFinalizacionSiPedidoNoExiste() throws Exception {
        InvocationHandler rsHandler = (proxy, method, args) -> {
            if ("next".equals(method.getName())) {
                return false; // No existe la fila
            }
            return null;
        };
        ResultSet rsMock = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, rsHandler);

        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return 0; // 0 filas afectadas
            }
            if ("executeQuery".equals(method.getName())) {
                return rsMock;
            }
            return null;
        };
        PreparedStatement psMock = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, psHandler);

        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return psMock;
            }
            return null;
        };
        Connection connMock = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Connection.class}, connHandler);

        PedidosDao dao = new PedidosDao(new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connMock; }
        });

        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class,
                () -> dao.actualizarEstadoConCliente(999, "Cliente X", "V-00000000", "EFECTIVO"));

        assertTrue("Debe alertar que el pedido no existe",
                ex.getMessage().contains("No existe el pedido 999"));
    }

    @Test
    public void migracion011ExisteYDefineIndicesRequeridos() throws Exception {
        Path mig011 = Path.of("db/migrations/011_indices_rendimiento.sql");
        assertTrue("El archivo de migración 011 debe existir", Files.exists(mig011));

        String contenido = Files.readString(mig011);
        assertTrue(contenido.contains("idx_pedidos_estado_fecha"));
        assertTrue(contenido.contains("idx_pedidos_cliente_doc"));
        assertTrue(contenido.contains("idx_detalle_pedidos_nombre"));
    }
}
