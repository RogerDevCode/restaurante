package infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Provee conexiones JDBC usando propiedades del proceso, entorno o archivo local. */
public class ProveedorConexionJdbc {

    public Connection getConnection() throws SQLException {
        Properties localConfig = cargarConfiguracionLocal();

        String database = setting("MYSQL_DATABASE", localConfig);
        String user = setting("DB_USER", localConfig);
        if (user == null || user.trim().isEmpty()) {
            user = setting("MYSQL_USER", localConfig);
        }
        String password = setting("DB_PASSWORD", localConfig);
        if (password == null) {
            password = setting("MYSQL_PASSWORD", localConfig);
        }
        String port = setting("MYSQL_PORT", localConfig);
        if (port == null || port.trim().isEmpty()) {
            port = "3306";
        }
        String url = setting("DB_URL", localConfig);

        if (database == null || database.trim().isEmpty()
                || user == null || user.trim().isEmpty()
                || password == null || password.isEmpty()) {
            throw new SQLException(
                    "Falta configurar MYSQL_DATABASE, MYSQL_USER y MYSQL_PASSWORD en .env o en el entorno");
        }
        if (url == null || url.trim().isEmpty()) {
            url = "jdbc:mysql://127.0.0.1:" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }

        return DriverManager.getConnection(url, user, password);
    }

    String setting(String name, Properties localConfig) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(name);
        }
        if (value == null || value.trim().isEmpty()) {
            value = localConfig.getProperty(name);
        }
        return value;
    }

    private Properties cargarConfiguracionLocal() throws SQLException {
        Properties localConfig = new Properties();
        try {
            String configuredEnvFile = System.getProperty("restaurante.env");
            Path envFile = Paths.get(configuredEnvFile == null ? ".env" : configuredEnvFile);
            if (configuredEnvFile == null && !Files.isRegularFile(envFile)) {
                envFile = Paths.get("..", ".env");
            }
            if (Files.isRegularFile(envFile)) {
                try (java.io.Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
                    localConfig.load(reader);
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            throw new SQLException("No se pudo leer o interpretar el archivo de configuración local", error);
        }
        return localConfig;
    }
}
