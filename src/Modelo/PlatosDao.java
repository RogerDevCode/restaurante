package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PlatosDao implements PlatosRepositorio {
    private final ProveedorConexionJdbc conexiones;

    public PlatosDao() {
        this(new ProveedorConexionJdbc());
    }

    public PlatosDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }

    @Override
    public boolean registrar(Platos pla) {
        validarPlato(pla);
        String sql = "INSERT INTO platos (nombre, precio, fecha) VALUES (?,?,?)";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setBigDecimal(2, precioPersistible(pla));
            sentencia.setString(3, pla.getFecha());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar plato");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar el plato.", ex);
        }
    }

    @Override
    public List<Platos> listarPorFecha(String valor, String fecha) {
        if (fecha == null || fecha.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La fecha del menú es obligatoria.");
        }
        List<Platos> platos = new ArrayList<>();
        boolean filtrarNombre = valor != null && !valor.trim().isEmpty();
        String sql = "SELECT * FROM platos WHERE fecha = ?";
        if (filtrarNombre) {
            sql += " AND nombre LIKE ?";
        }
        try (Connection conexion = conexiones.getConnection();
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
                    plato.setPrecioDecimal(resultados.getBigDecimal("precio"));
                    platos.add(plato);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los platos.", ex);
        }
        return platos;
    }

    @Override
    public boolean eliminar(int id) {
        String sql = "DELETE FROM platos WHERE id = ?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "eliminar plato " + id);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo eliminar el plato.", ex);
        }
    }

    @Override
    public boolean modificar(Platos pla) {
        validarPlato(pla);
        String sql = "UPDATE platos SET nombre=?, precio=? WHERE id=?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setBigDecimal(2, precioPersistible(pla));
            sentencia.setInt(3, pla.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "modificar plato " + pla.getId());
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo modificar el plato.", ex);
        }
    }

    private void validarPlato(Platos plato) {
        if (plato == null || plato.getNombre() == null || plato.getNombre().trim().isEmpty()
                || plato.getFecha() == null || plato.getFecha().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre, precio y fecha del plato son obligatorios.");
        }
        precioPersistible(plato);
    }

    private BigDecimal precioPersistible(Platos plato) {
        BigDecimal precio = plato.getPrecioDecimal();
        if (precio.signum() <= 0 || precio.scale() > 2 || precio.precision() - precio.scale() > 8) {
            throw ErrorAplicacionException.validacion(
                    "El precio debe ser positivo y tener como máximo dos decimales.");
        }
        try {
            return precio.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ErrorAplicacionException.validacion(
                    "El precio debe poder representarse con dos decimales.");
        }
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Registrar(Platos plato) {
        return registrar(plato);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public List<Platos> Listar(String nombre, String fecha) {
        return listarPorFecha(nombre, fecha);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Eliminar(int id) {
        return eliminar(id);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Modificar(Platos plato) {
        return modificar(plato);
    }
}
