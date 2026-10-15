package infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Puerta de seguridad para tests que escriben o restauran una BD por JDBC. */
public final class ProteccionEntornoBaseDatos {
    private ProteccionEntornoBaseDatos() { }

    public static boolean permiteEscriturasDestructivas() throws IOException {
        Properties local = cargarConfiguracionLocal();
        String database = primeraConfiguracion(local, "MYSQL_DATABASE", "DB_NAME");
        String host = primeraConfiguracion(local, "MYSQL_HOST", "DB_HOST");
        String port = primeraConfiguracion(local, "MYSQL_PORT", "DB_PORT");
        boolean urlExplicita = noVacio(System.getProperty("DB_URL"))
                || noVacio(System.getenv("DB_URL"))
                || noVacio(local.getProperty("DB_URL"));
        return "YES".equals(System.getenv("RUN_DESTRUCTIVE_DB_TESTS"))
                && "restaurante_test".equals(database)
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                && "3307".equals(port)
                && !urlExplicita;
    }

    private static String primeraConfiguracion(Properties local, String... nombres) {
        for (String nombre : nombres) {
            String valor = System.getProperty(nombre);
            if (noVacio(valor)) return valor;
        }
        for (String nombre : nombres) {
            String valor = System.getenv(nombre);
            if (noVacio(valor)) return valor;
        }
        for (String nombre : nombres) {
            String valor = local.getProperty(nombre);
            if (noVacio(valor)) return valor;
        }
        return null;
    }

    private static boolean noVacio(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static Properties cargarConfiguracionLocal() throws IOException {
        String configurado = System.getProperty("restaurante.env");
        Path archivo = Paths.get(configurado == null ? ".env" : configurado);
        if (configurado == null && !Files.isRegularFile(archivo)) archivo = Paths.get("..", ".env");
        Properties propiedades = new Properties();
        if (Files.isRegularFile(archivo)) {
            try (var reader = Files.newBufferedReader(archivo, java.nio.charset.StandardCharsets.UTF_8)) {
                propiedades.load(reader);
            }
        }
        return propiedades;
    }
}
