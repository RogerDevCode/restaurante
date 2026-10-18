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
        String sql = "INSERT INTO categorias (nombre, color, orden, activo) VALUES (?, ?, ?, ?)";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, categoria.getNombre());
            st.setString(2, categoria.getColor());
            st.setInt(3, categoria.getOrden());
            st.setInt(4, categoria.isActivo() ? 1 : 0);
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
        Categoria actual = buscar(categoria.getId());
        if (actual != null && actual.isGeneral()) {
            if (!categoria.isGeneral()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' es la categoría canónica del sistema y no se puede renombrar.");
            }
            if (!categoria.isActivo()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' es obligatoria y no se puede desactivar.");
            }
        }
        String sql = "UPDATE categorias SET nombre = ?, color = ?, orden = ?, activo = ? WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, categoria.getNombre());
            st.setString(2, categoria.getColor());
            st.setInt(3, categoria.getOrden());
            st.setInt(4, categoria.isActivo() ? 1 : 0);
            st.setInt(5, categoria.getId());
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
        Categoria c = buscar(id);
        if (c != null && c.isGeneral()) {
            throw ErrorAplicacionException.validacion("La categoría 'General' no se puede eliminar ni dar de baja.");
        }
        // Soft delete: activo = 0
        String sql = "UPDATE categorias SET activo = 0 WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo dar de baja la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean cambiarEstadoActivo(int id, boolean activo) {
        if (id <= 0) {
            return false;
        }
        if (!activo) {
            Categoria c = buscar(id);
            if (c != null && c.isGeneral()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' no se puede desactivar.");
            }
        }
        String sql = "UPDATE categorias SET activo = ? WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, activo ? 1 : 0);
            st.setInt(2, id);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar el estado de la categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean desactivarConReasignacion(int idCategoriaADesactivar, int idCategoriaDestino) {
        if (idCategoriaADesactivar <= 0 || idCategoriaDestino <= 0) {
            throw ErrorAplicacionException.validacion("Identificadores de categoría no válidos para reasignar.");
        }
        if (idCategoriaADesactivar == idCategoriaDestino) {
            throw ErrorAplicacionException.validacion("La categoría destino debe ser distinta a la categoría que se desea dar de baja.");
        }
        Categoria origen = buscar(idCategoriaADesactivar);
        if (origen != null && origen.isGeneral()) {
            throw ErrorAplicacionException.validacion("La categoría 'General' es canónica y no se puede dar de baja.");
        }
        Categoria destino = buscar(idCategoriaDestino);
        if (destino == null || !destino.isActivo()) {
            throw ErrorAplicacionException.validacion("La categoría destino debe existir y estar activa.");
        }

        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitOriginal = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                // 1. Reasignar platos en plato_categoria
                String sqlReasignar = "UPDATE plato_categoria SET id_categoria = ? WHERE id_categoria = ?";
                try (PreparedStatement st = con.prepareStatement(sqlReasignar)) {
                    st.setInt(1, idCategoriaDestino);
                    st.setInt(2, idCategoriaADesactivar);
                    st.executeUpdate();
                }
                // 2. Dar de baja categoría origen (soft-delete)
                String sqlBaja = "UPDATE categorias SET activo = 0 WHERE id = ?";
                try (PreparedStatement st = con.prepareStatement(sqlBaja)) {
                    st.setInt(1, idCategoriaADesactivar);
                    st.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException ex) {
                con.rollback();
                throw new DataAccessException("Error transaccional al reasignar platos y dar de baja categoría: " + ex.getMessage(), ex);
            } finally {
                con.setAutoCommit(autoCommitOriginal);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error de conexión al reasignar y dar de baja categoría: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Categoria buscar(int id) {
        if (id <= 0) {
            return null;
        }
        String sql = "SELECT id, nombre, color, orden, activo FROM categorias WHERE id = ?";
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
    public Categoria buscarPorNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT id, nombre, color, orden, activo FROM categorias WHERE LOWER(TRIM(nombre)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, nombre.trim());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return mapearCategoria(rs);
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo buscar la categoría por nombre: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Categoria obtenerGeneral() {
        Categoria general = buscarPorNombre("General");
        if (general != null) {
            if (!general.isActivo()) {
                cambiarEstadoActivo(general.getId(), true);
                general.setActivo(true);
            }
            return general;
        }
        // Si no existe, crearla canónicamente
        try (Connection con = conexiones.getConnection()) {
            String sqlInsert = "INSERT IGNORE INTO categorias (nombre, color, orden, activo) VALUES ('General', '#6B7280', 0, 1)";
            try (Statement st = con.createStatement()) {
                st.executeUpdate(sqlInsert);
            }
            return buscarPorNombre("General");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo asegurar la categoría canónica 'General': " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Categoria> listar() {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, color, orden, activo FROM categorias WHERE activo = 1 ORDER BY orden ASC, nombre ASC";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCategoria(rs));
            }
            if (lista.isEmpty()) {
                LOGGER.log(Level.INFO, "[CARGA_DATOS] Carga de categorías activas retornó 0 items.");
            }
            return lista;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar las categorías activas: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Categoria> listarTodas() {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT id, nombre, color, orden, activo FROM categorias ORDER BY orden ASC, nombre ASC";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearCategoria(rs));
            }
            if (lista.isEmpty()) {
                LOGGER.log(Level.INFO, "[CARGA_DATOS] Carga total de categorías retornó 0 items.");
            }
            return lista;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar todas las categorías: " + ex.getMessage(), ex);
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
        return contarPlatosEnCategoria(idCategoria) > 0;
    }

    @Override
    public int contarPlatosEnCategoria(int idCategoria) {
        if (idCategoria <= 0) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM plato_categoria WHERE id_categoria = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, idCategoria);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo contar el uso de la categoría: " + ex.getMessage(), ex);
        }
    }

    private Categoria mapearCategoria(ResultSet rs) throws SQLException {
        boolean activo = true;
        try {
            activo = rs.getInt("activo") == 1;
        } catch (SQLException ignored) {
            // si la consulta no incluye activo, asume activo por defecto
        }
        return new Categoria(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("color"),
                rs.getInt("orden"),
                activo);
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