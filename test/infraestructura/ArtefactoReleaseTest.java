package infraestructura;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Contratos del artefacto de release: inventario SHA-256 y contenido del ZIP.
 * Los contratos estáticos del script corren siempre; la verificación del
 * artefacto se omite si aún no se ha generado (no está versionado).
 */
public class ArtefactoReleaseTest {

    private static final Path DIR = Path.of("Restaurante_Ejecutable");
    private static final Path MANIFIESTO = DIR.resolve("ARTEFACTOS.sha256");
    private static final Path ZIP = Path.of("Restaurante_Ejecutable.zip");

    @Test
    public void empaquetadorExigeArbolLimpioYVerificaReproducibilidad() throws Exception {
        String script = Files.readString(Path.of("generar_restaurante_ejecutable.sh"));
        assertTrue(script.contains("EMPAQUETAR_CON_ARBOL_SUCIO"));
        assertTrue(script.contains("status --porcelain"));
        assertTrue(script.contains("ARTEFACTOS.sha256"));
        assertTrue(script.contains("VERIFICAR_ZIP_REPRODUCIBLE"));
        assertTrue(script.contains("zip -X -qr"));
        assertTrue(script.contains("sha256sum \"$ZIP_FILE\" > \"$ZIP_FILE.sha256\""));
    }

    @Test
    public void empaquetadorDeProduccionExigeArbolLimpio() throws Exception {
        String script = Files.readString(Path.of("empaquetar_produccion.sh"));
        assertTrue(script.contains("EMPAQUETAR_CON_ARBOL_SUCIO"));
        assertTrue(script.contains("status --porcelain"));
        assertTrue(script.contains("sha256sum"));
    }

    @Test
    public void inventarioSha256CoincideConLosArchivosDelArtefacto() throws Exception {
        Assume.assumeTrue("Sin artefacto generado", Files.isRegularFile(MANIFIESTO));
        List<String> entradas = entradasManifiesto();
        assertTrue("El inventario no debe estar vacío", entradas.size() >= 10);
        for (String entrada : entradas) {
            String[] partes = entrada.split("\\s{2}", 2);
            assertEquals("Línea de inventario inválida: " + entrada, 2, partes.length);
            Path archivo = DIR.resolve(partes[0]);
            assertTrue("Falta en el artefacto: " + archivo, Files.isRegularFile(archivo));
            assertEquals("SHA-256 no coincide para " + partes[0], partes[1], sha256(archivo));
        }
    }

    @Test
    public void zipContieneLoRequeridoYNoExponesSecretos() throws Exception {
        Assume.assumeTrue("Sin ZIP generado", Files.isRegularFile(ZIP));
        List<String> nombres = new ArrayList<>();
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            var it = zip.entries();
            while (it.hasMoreElements()) {
                nombres.add(it.nextElement().getName());
            }
        }
        String raiz = "Restaurante_Ejecutable/";
        assertTrue(nombres.contains(raiz + "Restaurante.jar"));
        assertTrue(nombres.contains(raiz + "BD.sql"));
        assertTrue(nombres.contains(raiz + "actualizar_bd.sql"));
        assertTrue(nombres.contains(raiz + ".env.example"));
        assertTrue(nombres.contains(raiz + "lib/mysql-connector-j-8.0.31.jar"));
        assertTrue(nombres.contains(raiz + "ARTEFACTOS.sha256"));

        for (String nombre : nombres) {
            String bajo = nombre.toLowerCase(Locale.ROOT);
            assertFalse("Archivo prohibido en el ZIP: " + nombre,
                    bajo.endsWith(".log") || bajo.endsWith(".key") || bajo.endsWith(".pem")
                            || bajo.endsWith(".jks") || bajo.contains("/respaldos/")
                            || (bajo.contains(".env") && !bajo.endsWith(".env.example")));
        }
    }

    @Test
    public void jarDentroDelZipCoincideConElInventariado() throws Exception {
        Assume.assumeTrue("Sin artefacto generado", Files.isRegularFile(DIR.resolve("Restaurante.jar")));
        Assume.assumeTrue("Sin ZIP generado", Files.isRegularFile(ZIP));
        String esperado = sha256(DIR.resolve("Restaurante.jar"));
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            ZipEntry entrada = zip.getEntry("Restaurante_Ejecutable/Restaurante.jar");
            assertNotNull("El ZIP no incluye Restaurante.jar", entrada);
            try (InputStream in = zip.getInputStream(entrada)) {
                assertEquals("El JAR dentro del ZIP difiere del inventariado", esperado, sha256(in));
            }
        }
        boolean declarado = entradasManifiesto().stream()
                .anyMatch(linea -> linea.startsWith("Restaurante.jar  ") && linea.endsWith(esperado));
        assertTrue("El manifiesto no declara el SHA-256 del JAR empaquetado", declarado);
    }

    private static List<String> entradasManifiesto() throws Exception {
        List<String> entradas = new ArrayList<>();
        for (String linea : Files.readAllLines(MANIFIESTO)) {
            String limpia = linea.strip();
            if (limpia.isEmpty() || limpia.startsWith("#")) {
                continue;
            }
            entradas.add(limpia);
        }
        return entradas;
    }

    private static String sha256(Path archivo) throws Exception {
        try (InputStream in = Files.newInputStream(archivo)) {
            return sha256(in);
        }
    }

    private static String sha256(InputStream in) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int leidos;
        while ((leidos = in.read(buffer)) > 0) {
            digest.update(buffer, 0, leidos);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
