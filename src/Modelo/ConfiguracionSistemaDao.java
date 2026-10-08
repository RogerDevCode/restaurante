package Modelo;

import infraestructura.ProveedorConexionJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación JDBC para la tabla clave-valor `configuracion_sistema`.
 * Almacena parámetros operativos y técnicos en pares clave:valor, evitando
 * la alteración de esquemas (DDL) por cada nueva opción introducida.
 */
public class ConfiguracionSistemaDao implements ConfiguracionRepositorio {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionSistemaDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public ConfiguracionSistemaDao() {
        this(new ProveedorConexionJdbc());
    }

    public ConfiguracionSistemaDao(ProveedorConexionJdbc conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones no puede ser nulo.");
    }

    @Override
    public String obtener(String clave, String valorPorDefecto) {
        validarClave(clave);
        String sql = "SELECT valor FROM configuracion_sistema WHERE clave = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString("valor");
                    return v != null ? v : valorPorDefecto;
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error consultando clave de configuración '" + clave + "': " + ex.getMessage());
        }
        return valorPorDefecto;
    }

    @Override
    public java.sql.Timestamp obtenerFechaActualizacion(String clave) {
        validarClave(clave);
        String sql = "SELECT actualizado_en FROM configuracion_sistema WHERE clave = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("actualizado_en");
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error consultando fecha de actualización de '" + clave + "': " + ex.getMessage());
        }
        return null;
    }

    @Override
    public void guardar(String clave, String valor) {
        validarClave(clave);
        String valorAGuardar = valor != null ? valor : "";
        String sql = """
            INSERT INTO configuracion_sistema (clave, valor)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE valor = VALUES(valor), actualizado_en = CURRENT_TIMESTAMP
            """;
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave.trim());
            ps.setString(2, valorAGuardar);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo guardar la configuración para la clave '" + clave + "'.", ex);
        }
    }

    @Override
    public Map<String, String> obtenerTodos() {
        Map<String, String> resultado = new HashMap<>();
        String sql = "SELECT clave, valor FROM configuracion_sistema";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String c = rs.getString("clave");
                String v = rs.getString("valor");
                if (c != null) {
                    resultado.put(c, v != null ? v : "");
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error cargando conjunto de configuraciones: " + ex.getMessage());
        }
        return resultado;
    }

    @Override
    public void guardarVarios(Map<String, String> configuraciones) {
        if (configuraciones == null || configuraciones.isEmpty()) {
            return;
        }
        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            try {
                con.setAutoCommit(false);
                guardarVarios(con, configuraciones);
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    con.rollback();
                } catch (SQLException errorRollback) {
                    ex.addSuppressed(errorRollback);
                }
                throw ex;
            } finally {
                try {
                    con.setAutoCommit(autoCommitPrevio);
                } catch (SQLException errorRestauracion) {
                    LOGGER.log(Level.WARNING, "No se pudo restaurar el autocommit de la conexión de configuración.",
                            errorRestauracion);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron guardar las configuraciones en lote.", ex);
        }
    }

    @Override
    public void guardarVarios(Connection con, Map<String, String> configuraciones) throws SQLException {
        Objects.requireNonNull(con, "La conexión transaccional es obligatoria.");
        if (configuraciones == null || configuraciones.isEmpty()) {
            return;
        }
        String sql = """
            INSERT INTO configuracion_sistema (clave, valor)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE valor = VALUES(valor)
            """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Map.Entry<String, String> e : configuraciones.entrySet()) {
                String clave = e.getKey();
                if (clave != null && !clave.trim().isEmpty()) {
                    validarClave(clave);
                    ps.setString(1, clave.trim());
                    ps.setString(2, e.getValue() != null ? e.getValue() : "");
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    @Override
    public boolean existe(String clave) {
        validarClave(clave);
        String sql = "SELECT 1 FROM configuracion_sistema WHERE clave = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error verificando existencia de clave '" + clave + "': " + ex.getMessage());
            return false;
        }
    }

    @Override
    public void eliminar(String clave) {
        validarClave(clave);
        String sql = "DELETE FROM configuracion_sistema WHERE clave = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, clave.trim());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo eliminar la clave de configuración '" + clave + "'.", ex);
        }
    }

    private void validarClave(String clave) {
        if (clave == null || clave.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La clave de configuración no puede ser nula ni vacía.");
        }
        if (clave.length() > 80) {
            throw ErrorAplicacionException.validacion("La clave de configuración excede la longitud máxima permitida (80 caracteres).");
        }
    }
}
