package Modelo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlatosDao {
    private final Conexion cn = new Conexion();

    public boolean Registrar(Platos pla) {
        if (pla == null) {
            throw ErrorAplicacionException.validacion("Los datos del plato son obligatorios.");
        }
        String sql = "INSERT INTO platos (nombre, precio, fecha) VALUES (?,?,?)";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setDouble(2, pla.getPrecio());
            sentencia.setString(3, pla.getFecha());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar plato");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar el plato.", ex);
        }
    }

    public List<Platos> Listar(String valor, String fecha) {
        if (fecha == null || fecha.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La fecha del menú es obligatoria.");
        }
        List<Platos> platos = new ArrayList<>();
        boolean filtrarNombre = valor != null && !valor.trim().isEmpty();
        String sql = "SELECT * FROM platos WHERE fecha = ?";
        if (filtrarNombre) {
            sql += " AND nombre LIKE ?";
        }
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, fecha);
            if (filtrarNombre) {
                sentencia.setString(2, "%" + valor.trim() + "%");
            }
            try (ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    Platos plato = new Platos();
                    plato.setId(resultados.getInt("id"));
                    plato.setNombre(resultados.getString("nombre"));
                    plato.setPrecio(resultados.getDouble("precio"));
                    platos.add(plato);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los platos.", ex);
        }
        return platos;
    }

    public boolean Eliminar(int id) {
        String sql = "DELETE FROM platos WHERE id = ?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "eliminar plato " + id);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo eliminar el plato.", ex);
        }
    }

    public boolean Modificar(Platos pla) {
        if (pla == null) {
            throw ErrorAplicacionException.validacion("Los datos del plato son obligatorios.");
        }
        String sql = "UPDATE platos SET nombre=?, precio=? WHERE id=?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setDouble(2, pla.getPrecio());
            sentencia.setInt(3, pla.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "modificar plato " + pla.getId());
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo modificar el plato.", ex);
        }
    }
}
