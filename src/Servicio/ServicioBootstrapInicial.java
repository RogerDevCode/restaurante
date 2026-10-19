package Servicio;

import Modelo.DataAccessException;
import Modelo.ErrorAplicacionException;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Servicio seguro de bootstrap para inicializar las credenciales maestras
 * del Administrador en una instalación nueva de Restaurante 2026.
 */
public class ServicioBootstrapInicial {

    private static final Logger LOGGER = Logger.getLogger(ServicioBootstrapInicial.class.getName());
    private static final String LOCK_BOOTSTRAP = "restaurante_bootstrap_lock";

    private final ProveedorConexionJdbc conexiones;
    private final PasswordHasher passwordHasher;

    public ServicioBootstrapInicial(ProveedorConexionJdbc conexiones) {
        this(conexiones, new PasswordHasher());
    }

    public ServicioBootstrapInicial(ProveedorConexionJdbc conexiones, PasswordHasher passwordHasher) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones es obligatorio.");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "El hasher de contraseñas es obligatorio.");
    }

    public boolean esBootstrapCompletado() {
        String sql = "SELECT valor FROM configuracion_sistema WHERE clave = 'bootstrap_inicial_completado'";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return "1".equals(rs.getString("valor"));
            }
            return false;
        } catch (SQLException ex) {
            LOGGER.warning("No se pudo consultar el estado de bootstrap; asumiendo no completado: " + ex.getMessage());
            return false;
        }
    }

    public void completarBootstrap(String nombre, String correo, char[] password) {
        try {
            validarDatos(nombre, correo, password);

            try (Connection con = conexiones.getConnection()) {
                adquirirBloqueo(con);
                try {
                    if (esBootstrapCompletado(con)) {
                        LOGGER.info("El bootstrap ya fue completado previamente por otra terminal.");
                        return;
                    }

                boolean autoCommitPrevio = con.getAutoCommit();
                con.setAutoCommit(false);
                try {
                    String passwordHash = passwordHasher.hash(password);

                    // Insertar o actualizar el Administrador inicial con id=1
                    boolean existeId1 = false;
                    try (PreparedStatement psCheck = con.prepareStatement("SELECT id FROM usuarios WHERE id = 1 FOR UPDATE");
                         ResultSet rsCheck = psCheck.executeQuery()) {
                        existeId1 = rsCheck.next();
                    }

                    if (existeId1) {
                        try (PreparedStatement psUpd = con.prepareStatement(
                                "UPDATE usuarios SET nombre = ?, correo = ?, pass = ?, rol = 'Administrador', activo = 1 WHERE id = 1")) {
                            psUpd.setString(1, nombre.trim());
                            psUpd.setString(2, correo.trim());
                            psUpd.setString(3, passwordHash);
                            psUpd.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement psIns = con.prepareStatement(
                                "INSERT INTO usuarios (id, nombre, correo, pass, rol, activo) VALUES (1, ?, ?, ?, 'Administrador', 1)")) {
                            psIns.setString(1, nombre.trim());
                            psIns.setString(2, correo.trim());
                            psIns.setString(3, passwordHash);
                            psIns.executeUpdate();
                        }
                    }

                    // Actualizar configuracion_sistema
                    try (PreparedStatement psConf = con.prepareStatement(
                            "INSERT INTO configuracion_sistema (clave, valor, descripcion) VALUES ('bootstrap_inicial_completado', '1', 'Indica si se ha completado el asistente de bootstrap inicial') "
                            + "ON DUPLICATE KEY UPDATE valor = '1'")) {
                        psConf.executeUpdate();
                    }

                    con.commit();
                    LOGGER.info("Bootstrap inicial completado exitosamente para el administrador: " + correo.trim());
                } catch (SQLException | RuntimeException ex) {
                    try { con.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                    throw new DataAccessException("Falló la inicialización del Administrador durante el bootstrap: " + ex.getMessage(), ex);
                } finally {
                    try { con.setAutoCommit(autoCommitPrevio); } catch (SQLException ignored) {}
                }
            } finally {
                liberarBloqueo(con);
            }
            } catch (SQLException ex) {
                throw new DataAccessException("Error de conexión durante el bootstrap inicial: " + ex.getMessage(), ex);
            }
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private boolean esBootstrapCompletado(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT valor FROM configuracion_sistema WHERE clave = 'bootstrap_inicial_completado'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && "1".equals(rs.getString("valor"));
        }
    }

    private void adquirirBloqueo(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            ps.setString(1, LOCK_BOOTSTRAP);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new SQLException("No se pudo adquirir el bloqueo de concurrencia para el bootstrap inicial (" + LOCK_BOOTSTRAP + ").");
                }
            }
        }
    }

    private void liberarBloqueo(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, LOCK_BOOTSTRAP);
            ps.executeQuery();
        } catch (Exception ex) {
            LOGGER.warning("No se pudo liberar el bloqueo de bootstrap: " + ex.getMessage());
        }
    }

    private void validarDatos(String nombre, String correo, char[] password) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre del administrador es obligatorio.");
        }
        String nombreTrim = nombre.trim();
        if (nombreTrim.length() < 3 || nombreTrim.length() > 60) {
            throw ErrorAplicacionException.validacion("El nombre debe tener entre 3 y 60 caracteres.");
        }
        if ("admin".equalsIgnoreCase(nombreTrim)) {
            throw ErrorAplicacionException.validacion("El nombre 'admin' está reservado por el sistema. Use su nombre real o un nombre descriptivo.");
        }
        if (!nombreTrim.matches("^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ][a-zA-Z0-9áéíóúÁÉÍÓÚñÑ ._-]{1,58}[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]$")) {
            throw ErrorAplicacionException.validacion("El nombre contiene caracteres inválidos.");
        }

        if (correo == null || correo.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo electrónico o nombre de usuario es obligatorio.");
        }
        String correoTrim = correo.trim();
        if (correoTrim.length() < 3 || correoTrim.length() > 100) {
            throw ErrorAplicacionException.validacion("El correo debe tener entre 3 y 100 caracteres.");
        }

        if (password == null || password.length < 8) {
            throw ErrorAplicacionException.validacion("La contraseña debe tener al menos 8 caracteres.");
        }
    }
}
