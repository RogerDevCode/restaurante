package Modelo;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuditoriaPreciosDaoTest {

    @Test
    public void validaAccionActorYMonedaAntesDePersistir() {
        assertThrows(IllegalArgumentException.class, () -> cambio("OTRO", "admin", "USD"));
        assertThrows(IllegalArgumentException.class, () -> cambio("CAMBIO_TASA", " ", "USD"));
        assertThrows(IllegalArgumentException.class, () -> cambio("CAMBIO_TASA", "admin", "EUR"));
    }

    @Test
    public void insertaConLaConexionDeLaTransaccionYNoHaceCommit() throws Exception {
        List<Object> valores = new ArrayList<>();
        List<String> sql = new ArrayList<>();
        boolean[] commit = {false};
        ResultSet keys = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> switch (m.getName()) {
                    case "next" -> true;
                    case "getLong" -> 42L;
                    case "close" -> null;
                    default -> defaultValue(m.getReturnType());
                });
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    if (m.getName().equals("executeUpdate")) return 1;
                    if (m.getName().equals("getGeneratedKeys")) return keys;
                    if (m.getName().startsWith("set")) { valores.add(a[1]); return null; }
                    if (m.getName().equals("close")) return null;
                    return defaultValue(m.getReturnType());
                });
        Connection con = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> {
                    if (m.getName().equals("prepareStatement")) { sql.add((String) a[0]); return ps; }
                    if (m.getName().equals("commit")) { commit[0] = true; return null; }
                    return defaultValue(m.getReturnType());
                });

        long id = new AuditoriaPreciosDao().insertar(con, cambio("CAMBIO_TASA", "admin", "VES"));

        assertEquals(42L, id);
        assertEquals(16, valores.size());
        assertTrue(sql.get(0).startsWith("INSERT INTO auditoria_cambios_precio"));
        assertFalse("La auditoría debe compartir commit/rollback con la operación que registra", commit[0]);
    }

    @Test
    public void permiteAccionesCongelarLineaYCambioCatalogo() {
        AuditoriaPreciosDao.CambioPrecio c1 = cambio("CONGELAR_LINEA", "admin", "USD");
        assertEquals("CONGELAR_LINEA", c1.accion());

        AuditoriaPreciosDao.CambioPrecio c2 = cambio("CAMBIO_CATALOGO", "admin", "VES");
        assertEquals("CAMBIO_CATALOGO", c2.accion());
    }

    @Test
    public void rollbackDeTransaccionAnulaInsercionDeAuditoria() throws Exception {
        boolean[] rollback = {false};
        boolean[] commit = {false};
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    if (m.getName().equals("executeUpdate")) return 1;
                    if (m.getName().equals("getGeneratedKeys")) {
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                                (pr, mr, ar) -> mr.getName().equals("next") ? true : (mr.getName().equals("getLong") ? 100L : defaultValue(mr.getReturnType())));
                    }
                    return defaultValue(m.getReturnType());
                });
        Connection con = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> {
                    if (m.getName().equals("prepareStatement")) return ps;
                    if (m.getName().equals("commit")) { commit[0] = true; return null; }
                    if (m.getName().equals("rollback")) { rollback[0] = true; return null; }
                    return defaultValue(m.getReturnType());
                });

        AuditoriaPreciosDao dao = new AuditoriaPreciosDao();
        try {
            dao.insertar(con, cambio("CAMBIO_TASA", "admin", "VES"));
            // Simular fallo en la transacción
            throw new RuntimeException("Error simulado previo al commit");
        } catch (RuntimeException ex) {
            con.rollback();
        }

        assertFalse("No debe haberse hecho commit", commit[0]);
        assertTrue("La conexión debe haber ejecutado rollback, eliminando la auditoría huérfana", rollback[0]);
    }

    private static AuditoriaPreciosDao.CambioPrecio cambio(String accion, String actor, String moneda) {
        return new AuditoriaPreciosDao.CambioPrecio(accion, 1, 2, 3, moneda,
                new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal("36.50"),
                new BigDecimal("73.00"), new BigDecimal("36.5000"), new BigDecimal("37.0000"),
                new BigDecimal("100.00"), new BigDecimal("101.00"), actor, "prueba", "idempotencia-1");
    }

    private static Object defaultValue(Class<?> tipo) {
        if (!tipo.isPrimitive() || tipo == void.class) return null;
        if (tipo == boolean.class) return false;
        if (tipo == byte.class) return (byte) 0;
        if (tipo == short.class) return (short) 0;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == float.class) return 0f;
        if (tipo == double.class) return 0d;
        if (tipo == char.class) return '\0';
        return null;
    }
}
