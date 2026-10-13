package integracion;

import Modelo.PreferenciasTablaDao;
import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/** Round-trip del DAO contra MySQL de integracion (docker 3307). */
public class PreferenciasTablaIT {

    private static final int USUARIO_IT = 999991;
    private static final String CLAVE_IT = "it_layout_test";

    @After
    public void limpiar() throws SQLException {
        try (Connection con = DriverManager.getConnection(System.getProperty("DB_URL"),
                System.getProperty("DB_USER"), System.getProperty("DB_PASSWORD"));
             Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM preferencias_tabla WHERE usuario_id = " + USUARIO_IT);
        }
    }

    @Test
    public void testGuardarYCargar() {
        PreferenciasTablaDao dao = new PreferenciasTablaDao(new ProveedorConexionJdbc());
        assertFalse(dao.cargar(USUARIO_IT, CLAVE_IT).isPresent());

        dao.guardar(USUARIO_IT, CLAVE_IT, new double[]{0.2, 0.3, 0.5});
        Optional<double[]> leido = dao.cargar(USUARIO_IT, CLAVE_IT);
        assertTrue(leido.isPresent());
        assertArrayEquals(new double[]{0.2, 0.3, 0.5}, leido.get(), 1e-9);

        dao.guardar(USUARIO_IT, CLAVE_IT, new double[]{0.5, 0.25, 0.25});
        Optional<double[]> sobreescrito = dao.cargar(USUARIO_IT, CLAVE_IT);
        assertTrue(sobreescrito.isPresent());
        assertArrayEquals(new double[]{0.5, 0.25, 0.25}, sobreescrito.get(), 1e-9);

        assertFalse(dao.cargar(USUARIO_IT, "it_clave_inexistente").isPresent());
    }
}
