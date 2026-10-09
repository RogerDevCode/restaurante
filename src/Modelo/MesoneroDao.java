package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceso a datos para la entidad Mesonero con soporte de soft-delete y toggle de activo.
 */
public class MesoneroDao implements MesoneroRepositorio {

    private static final Logger LOGGER = Logger.getLogger(MesoneroDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public MesoneroDao() {
        this(new ProveedorConexionJdbc());
    }

    public MesoneroDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        try {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo sincronizar el esquema en MesoneroDao: " + ex.getMessage());
        }
    }

    @Override
    public int registrar(Mesonero m) {
        validarMesonero(m);
        String sql = "INSERT INTO mesoneros (nombre_completo, cedula, telefono, activo, eliminado) VALUES (?, ?, ?, ?, 0)";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, m.getNombreCompleto());
            st.setString(2, m.getCedula());
            st.setString(3, m.getTelefono());
            st.setBoolean(4, m.isActivo());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    m.setId(id);
                    return id;
                }
            }
            return 0;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                throw new ErrorAplicacionException("Ya existe un mesonero con la cédula '" + m.getCedula() + "'.", ex);
            }
            throw new DataAccessException("No se pudo registrar el mesonero: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean modificar(Mesonero m) {
        if (m == null || m.getId() <= 0) {
            throw ErrorAplicacionException.validacion("ID de mesonero no válido para modificar.");
        }
        validarMesonero(m);
        String sql = "UPDATE mesoneros SET nombre_completo = ?, cedula = ?, telefono = ?, activo = ? WHERE id = ? AND eliminado = 0";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, m.getNombreCompleto());
            st.setString(2, m.getCedula());
            st.setString(3, m.getTelefono());
            st.setBoolean(4, m.isActivo());
            st.setInt(5, m.getId());
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                throw new ErrorAplicacionException("Ya existe otro mesonero con la cédula '" + m.getCedula() + "'.", ex);
            }
            throw new DataAccessException("No se pudo actualizar el mesonero: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean cambiarActivo(int id, boolean activo) {
        if (id <= 0) return false;
        String sql = "UPDATE mesoneros SET activo = ? WHERE id = ? AND eliminado = 0";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setBoolean(1, activo);
            st.setInt(2, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo cambiar el estado del mesonero: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean eliminarLogico(int id) {
        if (id <= 0) return false;
        String sql = "UPDATE mesoneros SET eliminado = 1 WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo eliminar el mesonero: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Mesonero buscarPorId(int id) {
        if (id <= 0) return null;
        String sql = "SELECT id, nombre_completo, cedula, telefono, activo, eliminado FROM mesoneros WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return mapearMesonero(rs);
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo buscar el mesonero por ID: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Mesonero> listarTodos() {
        List<Mesonero> lista = new ArrayList<>();
        String sql = "SELECT id, nombre_completo, cedula, telefono, activo, eliminado FROM mesoneros WHERE eliminado = 0 ORDER BY nombre_completo ASC";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearMesonero(rs));
            }
            return lista;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los mesoneros: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Mesonero> listarActivos() {
        List<Mesonero> lista = new ArrayList<>();
        String sql = "SELECT id, nombre_completo, cedula, telefono, activo, eliminado FROM mesoneros WHERE eliminado = 0 AND activo = 1 ORDER BY nombre_completo ASC";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearMesonero(rs));
            }
            return lista;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los mesoneros activos: " + ex.getMessage(), ex);
        }
    }

    private Mesonero mapearMesonero(ResultSet rs) throws SQLException {
        return new Mesonero(
                rs.getInt("id"),
                rs.getString("nombre_completo"),
                rs.getString("cedula"),
                rs.getString("telefono"),
                rs.getBoolean("activo"),
                rs.getBoolean("eliminado")
        );
    }

    private void validarMesonero(Mesonero m) {
        if (m == null) {
            throw ErrorAplicacionException.validacion("El mesonero no puede ser nulo.");
        }
        if (m.getNombreCompleto() == null || m.getNombreCompleto().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre completo del mesonero es obligatorio.");
        }
        if (m.getCedula() == null || m.getCedula().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La cédula del mesonero es obligatoria.");
        }
    }
}
