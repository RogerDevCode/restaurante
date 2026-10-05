package Modelo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SalasDao {
    private final Conexion cn = new Conexion();

    public boolean RegistrarSala(Salas sala) {
        if (sala == null) {
            throw ErrorAplicacionException.validacion("Los datos de la sala son obligatorios.");
        }
        String sql = "INSERT INTO salas(nombre, mesas) VALUES (?,?)";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, sala.getNombre());
            sentencia.setInt(2, sala.getMesas());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar sala");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar la sala.", ex);
        }
    }

    public List<Salas> Listar() {
        List<Salas> salas = new ArrayList<>();
        String sql = "SELECT * FROM salas";
        try (Connection conexion = cn.getConnection();
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

    public boolean Eliminar(int id) {
        String sql = "DELETE FROM salas WHERE id = ?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "eliminar sala " + id);
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1451) {
                throw new ErrorAplicacionException(
                        "No se puede eliminar la sala porque tiene pedidos asociados.", ex);
            }
            throw new DataAccessException("No se pudo eliminar la sala.", ex);
        }
    }

    public boolean Modificar(Salas sala) {
        if (sala == null) {
            throw ErrorAplicacionException.validacion("Los datos de la sala son obligatorios.");
        }
        String sql = "UPDATE salas SET nombre=?, mesas=? WHERE id=?";
        try (Connection conexion = cn.getConnection();
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
}
