package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceso a datos de platos favoritos. La identidad del plato es su nombre
 * normalizado (LOWER(TRIM(nombre))), calculado por la base de datos con la
 * misma normalización que la columna generada {@code platos.nombre_clave}.
 */
public class FavoritoDao implements FavoritoRepositorio {

    private static final Logger LOGGER = Logger.getLogger(FavoritoDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public FavoritoDao() {
        this(new ProveedorConexionJdbc());
    }

    public FavoritoDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        try {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo sincronizar el esquema en FavoritoDao: " + ex.getMessage());
        }
    }

    @Override
    public boolean marcar(String nombrePlato, boolean favorito) {
        if (nombrePlato == null || nombrePlato.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El plato es obligatorio para marcarlo como favorito.");
        }
        String sql = favorito
            ? "INSERT INTO plato_favorito (nombre_clave) VALUES (LOWER(TRIM(?))) "
                    + "ON DUPLICATE KEY UPDATE nombre_clave = nombre_clave"
            : "DELETE FROM plato_favorito WHERE nombre_clave = LOWER(TRIM(?))";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, nombrePlato.trim());
            return st.executeUpdate() >= 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo marcar el plato como favorito: " + ex.getMessage(), ex);
        }
    }
}