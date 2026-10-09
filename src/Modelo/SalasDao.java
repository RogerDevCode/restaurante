package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class SalasDao implements SalasRepositorio {
    private final ProveedorConexionJdbc conexiones;

    public SalasDao() {
        this(new ProveedorConexionJdbc());
    }

    public SalasDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }

    @Override
    public boolean registrar(Salas sala) {
        if (sala == null) {
            throw ErrorAplicacionException.validacion("Los datos de la sala son obligatorios.");
        }
        String sql = """
            INSERT INTO salas (nombre, mesas, tipo)
            VALUES (?, ?, ?)
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, sala.getNombre());
            sentencia.setInt(2, sala.getMesas());
            sentencia.setString(3, sala.getTipo());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar sala");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return registrarLegacy(sala);
            }
            throw new DataAccessException("No se pudo registrar la sala.", ex);
        }
    }

    private boolean registrarLegacy(Salas sala) {
        String sql = """
            INSERT INTO salas (nombre, mesas)
            VALUES (?, ?)
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, sala.getNombre());
            sentencia.setInt(2, sala.getMesas());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar sala");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar la sala.", ex);
        }
    }

    @Override
    public List<Salas> listar() {
        List<Salas> salas = new ArrayList<>();
        String sql = """
            SELECT id, nombre, mesas, tipo
            FROM salas
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Salas sala = new Salas();
                sala.setId(resultados.getInt("id"));
                sala.setNombre(resultados.getString("nombre"));
                sala.setMesas(resultados.getInt("mesas"));
                sala.setTipo(resultados.getString("tipo"));
                salas.add(sala);
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return listarLegacy();
            }
            throw new DataAccessException("No se pudieron listar las salas.", ex);
        }
        return salas;
    }

    private List<Salas> listarLegacy() {
        List<Salas> salas = new ArrayList<>();
        String sql = """
            SELECT id, nombre, mesas
            FROM salas
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Salas sala = new Salas();
                sala.setId(resultados.getInt("id"));
                sala.setNombre(resultados.getString("nombre"));
                sala.setMesas(resultados.getInt("mesas"));
                salas.add(sala);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar las salas.", ex);
        }
        return salas;
    }

    @Override
    public boolean eliminar(int id) {
        String sql = """
            DELETE FROM salas
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "eliminar sala " + id);
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1451) {
                throw new ErrorAplicacionException(
                        "No se puede eliminar la sala porque tiene pedidos asociados.", ex, Level.WARNING);
            }
            throw new DataAccessException("No se pudo eliminar la sala.", ex);
        }
    }

    @Override
    public boolean modificar(Salas sala) {
        if (sala == null) {
            throw ErrorAplicacionException.validacion("Los datos de la sala son obligatorios.");
        }
        String sql = """
            UPDATE salas
            SET nombre = ?, mesas = ?, tipo = ?
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, sala.getNombre());
            sentencia.setInt(2, sala.getMesas());
            sentencia.setString(3, sala.getTipo());
            sentencia.setInt(4, sala.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "modificar sala " + sala.getId());
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return modificarLegacy(sala);
            }
            throw new DataAccessException("No se pudo modificar la sala.", ex);
        }
    }

    private boolean modificarLegacy(Salas sala) {
        String sql = """
            UPDATE salas
            SET nombre = ?, mesas = ?
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, sala.getNombre());
            sentencia.setInt(2, sala.getMesas());
            sentencia.setInt(3, sala.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "modificar sala " + sala.getId());
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo modificar la sala.", ex);
        }
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean RegistrarSala(Salas sala) {
        return registrar(sala);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public List<Salas> Listar() {
        return listar();
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Eliminar(int id) {
        return eliminar(id);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Modificar(Salas sala) {
        return modificar(sala);
    }
}
