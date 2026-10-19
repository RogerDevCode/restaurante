package Servicio;

import Modelo.ErrorAplicacionException;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ServicioBootstrapInicialTest {

    @Test
    public void rechazaNombreVacioOInvalido() {
        ServicioBootstrapInicial servicio = new ServicioBootstrapInicial(new ProveedorConexionJdbc());
        assertThrows(ErrorAplicacionException.class, () ->
                servicio.completarBootstrap("", "admin@restaurante.com", "claveSegura123".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () ->
                servicio.completarBootstrap("admin", "admin@restaurante.com", "claveSegura123".toCharArray()));
        assertThrows(ErrorAplicacionException.class, () ->
                servicio.completarBootstrap("Al", "admin@restaurante.com", "claveSegura123".toCharArray()));
    }

    @Test
    public void rechazaCorreoVacio() {
        ServicioBootstrapInicial servicio = new ServicioBootstrapInicial(new ProveedorConexionJdbc());
        assertThrows(ErrorAplicacionException.class, () ->
                servicio.completarBootstrap("Propietario", "", "claveSegura123".toCharArray()));
    }

    @Test
    public void rechazaContrasenaCorta() {
        ServicioBootstrapInicial servicio = new ServicioBootstrapInicial(new ProveedorConexionJdbc());
        assertThrows(ErrorAplicacionException.class, () ->
                servicio.completarBootstrap("Propietario", "propietario@local.com", "corta".toCharArray()));
    }

    @Test
    public void limpiaArregloDePasswordEnMemoria() {
        ServicioBootstrapInicial servicio = new ServicioBootstrapInicial(new ProveedorConexionJdbc());
        char[] pass = "12345678".toCharArray();
        try {
            servicio.completarBootstrap("", "correo", pass);
        } catch (ErrorAplicacionException ignored) {
        }
        for (char c : pass) {
            assertEquals("El arreglo de contraseña debe limpiarse a 0", '\0', c);
        }
    }
}
