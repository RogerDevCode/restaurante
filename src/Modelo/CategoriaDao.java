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
 * Acceso a datos de categorías del menú y su asignación a platos.
 * La identidad de un plato es su nombre normalizado (LOWER(TRIM(nombre))),
 * calculado por la base de datos para coincidir con la columna generada.
 */
public class CategoriaDao implements CategoriaRepositorio {

    private static final Logger LOGGER = Logger.getLogger(CategoriaDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public CategoriaDao() {
        this(new ProveedorConexionJdbc());
    }

    public CategoriaDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        try {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo sincronizar el esquema en CategoriaDao: " + ex.getMessage());
        }
    }

    @Override
    public int registrar(Categoria categoria) {
        validarCategoria(categoria);
        String sql = "INSERT INTO categorias (nombre, color, orden) VALUES (?, ?, ?)";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, categoria.getNombre());
            st.setString(2, categoria.getColor());
            st.setInt(3, categoria.getOrden());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    categoria.setId(id);
                    return id;
                }
            }
            return 0;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                throw new ErrorAplicacionException("Ya existe una categoría con el nombre '" + categoria.getNombre() + "'.", ex);
            }
            throw new DataAccessException("No se pudo registrar la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean modificar(Categoria categoria) {
        if (categoria == null || categoria.getId() <= 0) {
            throw ErrorAplicacionException.validacion("ID de categoría no válido para modificar.");
        }
        validarCategoria(categoria);
        String sql = "UPDATE categorias SET nombre = ?, color = ?, orden = ? WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, categoria.getNombre());
            st.setString(2, categoria.getColor());
            st.setInt(3, categoria.getOrden());
            st.setInt(4, categoria.getId());
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                throw new ErrorAplicacionException("Ya existe otra categoría con el nombre '" + categoria.getNombre() + "'.", ex);
            }
            throw new DataAccessException("No se pudo actualizar la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean eliminar(int id) {
        if (id <= 0) {
            return false;
        }
        String sql = "DELETE FROM categorias WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo eliminar la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Categoria buscar(int id) {
        if (id <= 0) {
            return null;
        }
        String sql = "SELECT id, nombre, color, orden FROM categorias WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return mapearCategoria(rs);
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo buscar la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Categoria> listar() {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, color, orden FROM categorias ORDER BY orden ASC, nombre ASC";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCategoria(rs));
            }
            return lista;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar las categorías: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean asignarPlato(String nombrePlato, int idCategoria) {
        if (nombrePlato == null || nombrePlato.trim().isEmpty() || idCategoria <= 0) {
            return false;
        }
        String sql = "INSERT INTO plato_categoria (nombre_clave, id_categoria) VALUES (LOWER(TRIM(?)), ?) "
                + "ON DUPLICATE KEY UPDATE id_categoria = VALUES(id_categoria)";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, nombrePlato);
            st.setInt(2, idCategoria);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo asignar la categoría al plato: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean quitarAsignacionPlato(String nombrePlato) {
        if (nombrePlato == null || nombrePlato.trim().isEmpty()) {
            return false;
        }
        String sql = "DELETE FROM plato_categoria WHERE nombre_clave = LOWER(TRIM(?))";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, nombrePlato);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo quitar la asignación de categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean categoriaEnUso(int idCategoria) {
        if (idCategoria <= 0) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM plato_categoria WHERE id_categoria = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, idCategoria);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo comprobar el uso de la categoría: " + ex.getMessage(), ex);
        }
    }

    private Categoria mapearCategoria(ResultSet rs) throws SQLException {
        return new Categoria(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("color"),
                rs.getInt("orden"));
    }

    private void validarCategoria(Categoria categoria) {
        if (categoria == null) {
            throw ErrorAplicacionException.validacion("La categoría no puede ser nula.");
        }
        if (categoria.getNombre() == null || categoria.getNombre().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre de la categoría es obligatorio.");
        }
        if (categoria.getColor() == null || categoria.getColor().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El color de la categoría es obligatorio.");
        }
    }
}