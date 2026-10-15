package Modelo;

import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoginDaoCicloVidaTest {

    @Test
    public void cambiarContrasenaRechazaParametrosInvalidos() {
        LoginDao dao = new LoginDao();
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(0, "nuevaClave123"));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, (String) null));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "   "));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "123"));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "a"));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, " 12 "));

        // Pruebas directas sobre sobrecarga segura char[]
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(0, "nuevaClave123".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, (char[]) null));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "   ".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "123".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, "a".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> dao.cambiarContrasena(1, " 12 ".toCharArray()));
    }

    @Test
    public void validarLongitudContrasenaConCharArrayRechazaInvalidosYAceptaValidos() {
        assertThrows(ErrorAplicacionException.class, () -> LoginDao.validarLongitudContrasena((char[]) null));
        assertThrows(ErrorAplicacionException.class, () -> LoginDao.validarLongitudContrasena(new char[0]));
        assertThrows(ErrorAplicacionException.class, () -> LoginDao.validarLongitudContrasena("   ".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> LoginDao.validarLongitudContrasena("123".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () -> LoginDao.validarLongitudContrasena(" 12 ".toCharArray()));

        // Validas (>= 4 caracteres no blancos)
        LoginDao.validarLongitudContrasena("1234".toCharArray());
        LoginDao.validarLongitudContrasena("admin".toCharArray());
        LoginDao.validarLongitudContrasena("  abcd  ".toCharArray());
        assertEquals(4, LoginDao.contarLongitudSinEspacios("  abcd  ".toCharArray()));
        assertTrue(LoginDao.esBlanco("   ".toCharArray()));
        assertFalse(LoginDao.esBlanco("  a ".toCharArray()));
    }

    @Test
    public void registrarRechazaContrasenaMenorDeCuatroCaracteres() {
        LoginDao dao = new LoginDao();
        Usuario u1 = new Usuario(0, "Mozo Test", "mozo1@test.com", "123", "Asistente");
        ErrorAplicacionException ex1 = assertThrows(ErrorAplicacionException.class, () -> dao.Registrar(u1));
        assertTrue(ex1.getMessage().contains("al menos 4 caracteres"));

        Usuario u2 = new Usuario(0, "Mozo Test", "mozo2@test.com", "", "Asistente");
        ErrorAplicacionException ex2 = assertThrows(ErrorAplicacionException.class, () -> dao.Registrar(u2));
        assertTrue(ex2.getMessage().contains("obligatoria"));

        Usuario u3 = new Usuario(0, "Mozo Test", "mozo3@test.com", "   ", "Asistente");
        ErrorAplicacionException ex3 = assertThrows(ErrorAplicacionException.class, () -> dao.Registrar(u3));
        assertTrue(ex3.getMessage().contains("obligatoria"));

        Usuario u4 = new Usuario(0, "Mozo Test", "mozo4@test.com", " a ", "Asistente");
        ErrorAplicacionException ex4 = assertThrows(ErrorAplicacionException.class, () -> dao.Registrar(u4));
        assertTrue(ex4.getMessage().contains("al menos 4 caracteres"));
    }

    @Test
    public void desactivarYEliminarUsuarioRechazaIdInvalido() {
        LoginDao dao = new LoginDao();
        assertThrows(ErrorAplicacionException.class, () -> dao.desactivarUsuario(0));
        assertThrows(ErrorAplicacionException.class, () -> dao.eliminarUsuario(-1));
    }

    @Test
    public void desactivarUsuarioImpideDesactivarAlUltimoAdministradorActivo() {
        AtomicReference<String> sqlEjecutado = new AtomicReference<>();
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        String sql = sqlEjecutado.get();
                        if (sql != null && sql.contains("SELECT rol") && sql.contains("FROM usuarios")) {
                            return mockResultSetRow(Map.of("rol", "Administrador", "correo", "carlos@rest.com", "nombre", "Carlos"));
                        }
                        if (sql != null && sql.contains("SELECT COUNT(*) FROM usuarios WHERE LOWER(rol) = 'administrador'")) {
                            return mockResultSetSingleInt(0);
                        }
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        sqlEjecutado.set((String) args[0]);
                        return mockStatement;
                    }
                    if ("close".equals(name) || "setAutoCommit".equals(name) || "commit".equals(name) || "rollback".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return true;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () -> dao.desactivarUsuario(1));
        assertTrue(ex.getMessage().contains("único administrador activo"));

        ErrorAplicacionException exEliminar = assertThrows(ErrorAplicacionException.class, () -> dao.eliminarUsuario(1));
        assertTrue(exEliminar.getMessage().contains("único administrador activo"));
    }

    @Test
    public void desactivarUsuarioPermitidoSiExistenOtrosAdministradoresActivos() {
        AtomicInteger updates = new AtomicInteger();
        AtomicReference<String> currentSqlRef = new AtomicReference<>("");
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        String sql = currentSqlRef.get();
                        if (sql.contains("SELECT rol") && sql.contains("FROM usuarios")) {
                            return mockResultSetRow(Map.of("rol", "Administrador", "correo", "carlos@rest.com", "nombre", "Carlos"));
                        }
                        if (sql.contains("SELECT COUNT(*)")) {
                            return mockResultSetSingleInt(2);
                        }
                    }
                    if ("executeUpdate".equals(name)) {
                        updates.incrementAndGet();
                        return 1;
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        currentSqlRef.set((String) args[0]);
                        return mockStatement;
                    }
                    if ("close".equals(name) || "setAutoCommit".equals(name) || "commit".equals(name) || "rollback".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return true;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        boolean exito = dao.desactivarUsuario(2);
        assertTrue(exito);
        assertEquals(1, updates.get());
    }

    @Test
    public void eliminarUsuarioImpideEliminarAlAdministradorPrimarioAdmin() {
        AtomicReference<String> currentSqlRef = new AtomicReference<>("");
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        String sql = currentSqlRef.get();
                        if (sql.contains("SELECT rol") && sql.contains("FROM usuarios")) {
                            return mockResultSetRow(Map.of("rol", "Administrador", "correo", "admin", "nombre", "Administrador"));
                        }
                        if (sql.contains("SELECT COUNT(*)")) {
                            return mockResultSetSingleInt(3);
                        }
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        currentSqlRef.set((String) args[0]);
                        return mockStatement;
                    }
                    if ("close".equals(name) || "setAutoCommit".equals(name) || "commit".equals(name) || "rollback".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return true;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () -> dao.eliminarUsuario(1));
        assertTrue("Debe indicar que el usuario admin es primario y no puede ser eliminado",
                ex.getMessage().contains("administrador primario") && ex.getMessage().contains("no puede ser eliminado"));
    }

    @Test
    public void carreraConcurrenteNoPermiteDesactivarMutuamenteAUltimosAdministradores() throws Exception {
        final AtomicInteger adminsActivos = new AtomicInteger(2);
        final AtomicInteger desactivacionesExitosas = new AtomicInteger(0);
        final AtomicInteger excepcionesLanzadas = new AtomicInteger(0);

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if ("prepareStatement".equals(name)) {
                                String sql = (String) args[0];
                                return (PreparedStatement) Proxy.newProxyInstance(
                                        PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class},
                                        (pStmt, mStmt, aStmt) -> {
                                            String stName = mStmt.getName();
                                            if ("executeQuery".equals(stName)) {
                                                if (sql.contains("SELECT rol") && sql.contains("FROM usuarios")) {
                                                    return mockResultSetRow(Map.of("rol", "Administrador", "correo", "adminX@rest.com", "nombre", "AdminX"));
                                                }
                                                if (sql.contains("SELECT COUNT(*)")) {
                                                    int otros = adminsActivos.get() - 1;
                                                    return mockResultSetSingleInt(Math.max(0, otros));
                                                }
                                            }
                                            if ("executeUpdate".equals(stName)) {
                                                if (sql.contains("UPDATE usuarios SET activo = 0")) {
                                                    adminsActivos.decrementAndGet();
                                                    return 1;
                                                }
                                            }
                                            if ("close".equals(stName)) return null;
                                            return null;
                                        }
                                );
                            }
                            if ("close".equals(name) || "setAutoCommit".equals(name) || "commit".equals(name) || "rollback".equals(name)) return null;
                            if ("getAutoCommit".equals(name)) return true;
                            return null;
                        }
                );
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> f1 = pool.submit(() -> {
            try {
                startLatch.await();
                dao.desactivarUsuario(1);
                desactivacionesExitosas.incrementAndGet();
            } catch (ErrorAplicacionException ex) {
                excepcionesLanzadas.incrementAndGet();
            } catch (Exception ex) {
                fail("Excepción inesperada: " + ex);
            }
        });

        Future<?> f2 = pool.submit(() -> {
            try {
                startLatch.await();
                dao.desactivarUsuario(2);
                desactivacionesExitosas.incrementAndGet();
            } catch (ErrorAplicacionException ex) {
                excepcionesLanzadas.incrementAndGet();
            } catch (Exception ex) {
                fail("Excepción inesperada: " + ex);
            }
        });

        startLatch.countDown();
        f1.get();
        f2.get();
        pool.shutdown();

        assertEquals("Exactamente una desactivación debe tener éxito", 1, desactivacionesExitosas.get());
        assertEquals("Exactamente una operación debe ser rechazada por proteger al último administrador", 1, excepcionesLanzadas.get());
        assertEquals("Debe quedar exactamente 1 administrador activo", 1, adminsActivos.get());
    }

    private static ResultSet mockResultSetRow(Map<String, String> valores) {
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return !nextCalled.getAndSet(true);
                    if ("getString".equals(method.getName())) {
                        String col = (String) args[0];
                        return valores.getOrDefault(col, "");
                    }
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );
    }

    private static ResultSet mockResultSetSingleInt(int val) {
        AtomicBoolean nextCalled = new AtomicBoolean(false);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return !nextCalled.getAndSet(true);
                    if ("getInt".equals(method.getName())) return val;
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );
    }

    @Test
    public void listarUsuariosReflejaCorrectamenteUsuariosDesactivados() {
        AtomicInteger cursor = new AtomicInteger(0);
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        return (ResultSet) Proxy.newProxyInstance(
                                ResultSet.class.getClassLoader(),
                                new Class<?>[]{ResultSet.class},
                                (pRs, mRs, aRs) -> {
                                    String rsMethod = mRs.getName();
                                    if ("next".equals(rsMethod)) {
                                        return cursor.incrementAndGet() <= 2;
                                    }
                                    if ("getInt".equals(rsMethod)) {
                                        return cursor.get();
                                    }
                                    if ("getString".equals(rsMethod)) {
                                        String col = (String) aRs[0];
                                        if ("nombre".equals(col)) return "Usuario " + cursor.get();
                                        if ("correo".equals(col)) return "user" + cursor.get() + "@rest.com";
                                        if ("rol".equals(col)) return "Administrador";
                                        return "";
                                    }
                                    if ("getObject".equals(rsMethod)) {
                                        String col = (String) aRs[0];
                                        if ("activo".equals(col)) {
                                            return cursor.get() == 1 ? 1 : 0;
                                        }
                                    }
                                    if ("close".equals(rsMethod)) return null;
                                    return null;
                                }
                        );
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return mockStatement;
                    }
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        java.util.List<Usuario> lista = dao.ListarUsuarios();
        assertEquals(2, lista.size());
        assertTrue("El usuario 1 debe reportarse como activo", lista.get(0).isActivo());
        assertFalse("El usuario 2 debe reportarse fielmente como inactivo", lista.get(1).isActivo());
    }

    @Test
    public void desactivarUsuarioImpideDesactivarAlAdministradorPrimarioAdmin() {
        AtomicReference<String> currentSqlRef = new AtomicReference<>("");
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeQuery".equals(name)) {
                        String sql = currentSqlRef.get();
                        if (sql.contains("SELECT rol") && sql.contains("FROM usuarios")) {
                            return mockResultSetRow(Map.of("rol", "Administrador", "correo", "admin", "nombre", "Administrador"));
                        }
                        if (sql.contains("SELECT COUNT(*)")) {
                            return mockResultSetSingleInt(3);
                        }
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("prepareStatement".equals(name)) {
                        currentSqlRef.set((String) args[0]);
                        return mockStatement;
                    }
                    if ("close".equals(name) || "setAutoCommit".equals(name) || "commit".equals(name) || "rollback".equals(name)) return null;
                    if ("getAutoCommit".equals(name)) return true;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        ErrorAplicacionException ex = assertThrows(ErrorAplicacionException.class, () -> dao.desactivarUsuario(1));
        assertTrue("Debe indicar que el usuario admin es primario y no puede ser desactivado",
                ex.getMessage().contains("administrador primario") && ex.getMessage().contains("no puede ser desactivado"));
    }

    @Test
    public void esUsuarioActivoVerificaEstadoEnBaseDeDatos() {
        AtomicInteger idConsultado = new AtomicInteger();
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setInt".equals(name)) {
                        idConsultado.set((int) args[1]);
                    }
                    if ("executeQuery".equals(name)) {
                        return (ResultSet) Proxy.newProxyInstance(
                                ResultSet.class.getClassLoader(),
                                new Class<?>[]{ResultSet.class},
                                (rProxy, rMethod, rArgs) -> {
                                    String rName = rMethod.getName();
                                    if ("next".equals(rName)) return true;
                                    if ("getObject".equals(rName)) {
                                        return idConsultado.get() == 1 ? 1 : 0;
                                    }
                                    if ("close".equals(rName)) return null;
                                    return null;
                                }
                        );
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return mockStatement;
                    }
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        assertTrue(dao.esUsuarioActivo(1));
        assertFalse(dao.esUsuarioActivo(2));
        assertFalse(dao.esUsuarioActivo(0));
    }

    @Test
    public void configModoAtencionManejoDeValoresPorDefecto() {
        Config conf = new Config();
        assertEquals("OPCION_B", conf.getModoAtencion());
        conf.setModoAtencion("OPCION_A");
        assertEquals("OPCION_A", conf.getModoAtencion());
        conf.setModoAtencion(null);
        assertEquals("OPCION_B", conf.getModoAtencion());
        conf.setModoAtencion("   ");
        assertEquals("OPCION_B", conf.getModoAtencion());
    }

    @Test
    public void actualizarModoAtencionEjecutaSentencias() {
        java.util.List<String> sentenciasEjecutadas = new java.util.ArrayList<>();
        PreparedStatement mockStatement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        Connection mockConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sentenciasEjecutadas.add((String) args[0]);
                        return mockStatement;
                    }
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc() {
            @Override
            public Connection getConnection() {
                return mockConnection;
            }
        };

        LoginDao dao = new LoginDao(proveedor, new PasswordHasher());
        boolean ok = dao.actualizarModoAtencion("OPCION_C");
        assertTrue(ok);
        assertFalse(sentenciasEjecutadas.stream().anyMatch(s -> s.contains("UPDATE config")));
        assertTrue(sentenciasEjecutadas.stream().anyMatch(s -> s.contains("configuracion_sistema")));
    }
}
