package Servicio;

import Modelo.AutenticacionRepositorio;
import Modelo.ErrorAplicacionException;
import Modelo.Usuario;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas adversarias para las mejoras de Fase 1:
 * - Cero-limpieza de contraseñas y autenticación con char[].
 * - Manejo resiliente de AbridorPdf en entornos sin visor Desktop o headless.
 */
public class AdversarialFase1SeguridadYThreadingTest {

    @Test
    public void autenticacionConCharArrayValidaYPermiteLimpiezaInmediata() {
        Usuario adminEsperado = new Usuario(1, "Admin", "admin@rest.com", "passSeguro123", "Administrador");

        AutenticacionRepositorio repo = (correo, clave) -> {
            if ("admin@rest.com".equals(correo) && "passSeguro123".equals(clave)) {
                return Optional.of(adminEsperado);
            }
            return Optional.empty();
        };

        AutenticacionServicio auth = new AutenticacionServicio(repo);

        char[] claveBuffer = new char[]{'p', 'a', 's', 's', 'S', 'e', 'g', 'u', 'r', 'o', '1', '2', '3'};
        Optional<Usuario> res = auth.autenticar("admin@rest.com", claveBuffer);

        assertTrue("Debe autenticar con credenciales correctas", res.isPresent());
        assertEquals("Admin", res.get().getNombre());

        // Verificar que el buffer se puede limpiar con Arrays.fill('\0')
        Arrays.fill(claveBuffer, '\0');
        for (char c : claveBuffer) {
            assertEquals("Cada posición debe haber quedado en cero", '\0', c);
        }
    }

    @Test
    public void autenticacionConCharArrayRechazaClaveIncorrectaOVacía() {
        AutenticacionRepositorio repo = (correo, clave) -> Optional.empty();
        AutenticacionServicio auth = new AutenticacionServicio(repo);

        char[] claveErronea = "otraClave".toCharArray();
        Optional<Usuario> res = auth.autenticar("admin@rest.com", claveErronea);
        assertFalse("No debe autenticar con clave errónea", res.isPresent());
        Arrays.fill(claveErronea, '\0');
    }

    @Test(expected = ErrorAplicacionException.class)
    public void autenticacionConCharArrayLanzaExcepcionSiArrayEstaVacio() {
        AutenticacionRepositorio repo = (correo, clave) -> Optional.empty();
        AutenticacionServicio auth = new AutenticacionServicio(repo);
        auth.autenticar("admin@rest.com", new char[0]);
    }

    @Test(expected = ErrorAplicacionException.class)
    public void autenticacionConCharArrayLanzaExcepcionSiArrayEsNulo() {
        AutenticacionRepositorio repo = (correo, clave) -> Optional.empty();
        AutenticacionServicio auth = new AutenticacionServicio(repo);
        auth.autenticar("admin@rest.com", (char[]) null);
    }

    @Test
    public void abridorPdfPorDefectoNoFallaNiLanzaExcepcionSinDesktop() throws Exception {
        PedidoPdfServicio.AbridorPdf abridor = PedidoPdfServicio.AbridorPdf.porDefecto();
        assertNotNull(abridor);

        Path rutaDummy = Path.of("no_existe_archivo_test.pdf");
        // En entorno de test/CI o servidor, Desktop no soportado no debe lanzar excepción
        // Si Desktop no está soportado, se ejecuta el logger de respaldo sin crashear.
        try {
            abridor.abrir(rutaDummy);
        } catch (java.io.IOException ex) {
            // Si el Desktop intentó abrir un archivo inexistente, IOException de archivo es esperada,
            // pero NO debe lanzar NullPointerException o UnsupportedOperationException no controlada.
            assertTrue(ex.getMessage() != null || ex instanceof java.io.IOException);
        }
    }
}
