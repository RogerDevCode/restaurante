package infraestructura;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigradorEsquemaJdbcTest {

    @Test
    public void recalcularFiscalidadConvierteAplicaIvaNullACeroSinExcepciones() throws SQLException {
        List<String> sentenciasEjecutadas = new ArrayList<>();

        Statement mockSt = (Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName()) && args != null && args.length > 0) {
                        sentenciasEjecutadas.add((String) args[0]);
                        return 1;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );

        Connection mockCon = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return mockSt;
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    return null;
                }
        );

        // Debe ejecutarse limpiamente sin lanzar ninguna excepción
        MigradorEsquemaJdbc.recalcularFiscalidadHistorica(mockCon);

        assertFalse("Debe haber ejecutado sentencias SQL", sentenciasEjecutadas.isEmpty());

        // Verificar que asigna COALESCE(p.aplica_iva, 0)
        boolean tieneCoalesceCero = sentenciasEjecutadas.stream()
                .anyMatch(s -> s.contains("SET d.aplica_iva = COALESCE(p.aplica_iva, 0)"));
        assertTrue("Debe asignar COALESCE(p.aplica_iva, 0) para líneas huérfanas o sin IVA", tieneCoalesceCero);

        // Verificar salvaguarda para cualquier remanente NULL
        boolean tieneSalvaguardaCero = sentenciasEjecutadas.stream()
                .anyMatch(s -> s.contains("SET aplica_iva = 0 WHERE aplica_iva IS NULL"));
        assertTrue("Debe garantizar que cualquier aplica_iva IS NULL remanente pase a 0", tieneSalvaguardaCero);
    }
}
