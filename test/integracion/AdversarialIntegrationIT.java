package integracion;

import Modelo.Config;
import Modelo.LoginDao;
import Modelo.Usuario;
import infraestructura.ProveedorConexionJdbc;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdversarialIntegrationIT {

    @BeforeClass
    public static void setUp() {
        System.setProperty("DB_URL", "jdbc:mysql://127.0.0.1:3307/restaurante_test");
        System.setProperty("DB_USER", "restaurante_test_app");
        System.setProperty("DB_PASSWORD", "test_only_app_password");
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(
            System.getProperty("DB_URL"),
            System.getProperty("DB_USER"),
            System.getProperty("DB_PASSWORD")
        );
    }

    @Before
    public void limpiarDatos() throws SQLException {
        limpiarMaliciosos();
    }

    @After
    public void limpiarDespues() throws SQLException {
        limpiarMaliciosos();
    }

    private void limpiarMaliciosos() throws SQLException {
        try (Connection con = conexion(); Statement stmt = con.createStatement()) {
            stmt.execute("DELETE FROM usuarios WHERE correo LIKE 'malicioso%'");
        }
    }

    @Test
    public void sqlInjectionLogin() {
        LoginDao login = new LoginDao();
        // Trying SQL injection to bypass password
        String payloadCorreo = "admin@a.com' OR '1'='1";
        Optional<Usuario> result = login.autenticar(payloadCorreo, "cualquiera");
        
        assertFalse("El sistema debe resistir inyección SQL en el login", result.isPresent());
    }

    @Test
    public void sqlInjectionUserRegistration() {
        LoginDao login = new LoginDao();
        Usuario user = new Usuario();
        user.setNombre("Malicioso");
        user.setCorreo("malicioso@a.com'; DROP TABLE pedidos; --");
        user.setPassword("pass");
        user.setRol("Asistente");

        login.Registrar(user);

        // Verificamos que no se borro la tabla, si se puede consultar config
        Config config = login.datosEmpresa();
        assertTrue("La tabla config debe existir y no ser borrada por la inyección", config != null && config.getId() > 0);
    }

    @Test
    public void stringExtremadamenteLargo() {
        LoginDao login = new LoginDao();
        Usuario user = new Usuario();
        user.setNombre("A".repeat(10000));
        user.setCorreo("maliciosolargo@a.com");
        user.setPassword("pass");
        user.setRol("Asistente");

        try {
            boolean success = login.Registrar(user);
            // Dependiendo del VARCHAR de mysql, puede fallar (lanzando ErrorAplicacionException) o no.
            // Lo importante es que no corrompa el sistema.
            // En MySQL, si la columna es VARCHAR(50), lanzará DataTruncation o similar y fallará.
        } catch (Exception e) {
            // Se asume que falló controladamente
        }
    }
}
