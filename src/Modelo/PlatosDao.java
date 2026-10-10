package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
        String sql = """
            INSERT INTO platos (nombre, precio, fecha, aplica_iva)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setBigDecimal(2, precioPersistible(pla));
            sentencia.setString(3, pla.getFecha());
            sentencia.setInt(4, pla.isAplicaIva() ? 1 : 0);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar plato");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return registrarLegacy(pla);
            }
            throw new DataAccessException("No se pudo registrar el plato.", ex);
        }
    }

    private boolean registrarLegacy(Platos pla) {
        String sql = """
            INSERT INTO platos (nombre, precio, fecha)
            VALUES (?, ?, ?)
            """;
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
        String sql = filtrarNombre
            ? """
              SELECT p.id, p.nombre, p.precio, p.fecha, p.aplica_iva,
                     pc.id_categoria, c.nombre AS categoria_nombre, c.color AS categoria_color,
                     CASE WHEN pf.nombre_clave IS NULL THEN 0 ELSE 1 END AS favorito,
                     r.total AS rango_total
              FROM platos p
              LEFT JOIN plato_categoria pc ON pc.nombre_clave = p.nombre_clave
              LEFT JOIN categorias c ON c.id = pc.id_categoria
              LEFT JOIN plato_favorito pf ON pf.nombre_clave = p.nombre_clave
              LEFT JOIN (
                  SELECT d.nombre_clave, SUM(d.cantidad) AS total
                  FROM detalle_pedidos d
                  JOIN pedidos o ON d.id_pedido = o.id
                  WHERE o.estado = 'FINALIZADO'
                    AND o.fecha >= CURDATE() - INTERVAL 7 DAY
                    AND o.fecha < CURDATE()
                  GROUP BY d.nombre_clave
                  ORDER BY total DESC
                  LIMIT 10
              ) r ON r.nombre_clave = p.nombre_clave
              WHERE p.fecha = ? AND p.activo = 1 AND p.nombre LIKE ?
              ORDER BY c.nombre IS NULL ASC, c.nombre ASC, p.nombre ASC
              """
            : """
              SELECT p.id, p.nombre, p.precio, p.fecha, p.aplica_iva,
                     pc.id_categoria, c.nombre AS categoria_nombre, c.color AS categoria_color,
                     CASE WHEN pf.nombre_clave IS NULL THEN 0 ELSE 1 END AS favorito,
                     r.total AS rango_total
              FROM platos p
              LEFT JOIN plato_categoria pc ON pc.nombre_clave = p.nombre_clave
              LEFT JOIN categorias c ON c.id = pc.id_categoria
              LEFT JOIN plato_favorito pf ON pf.nombre_clave = p.nombre_clave
              LEFT JOIN (
                  SELECT d.nombre_clave, SUM(d.cantidad) AS total
                  FROM detalle_pedidos d
                  JOIN pedidos o ON d.id_pedido = o.id
                  WHERE o.estado = 'FINALIZADO'
                    AND o.fecha >= CURDATE() - INTERVAL 7 DAY
                    AND o.fecha < CURDATE()
                  GROUP BY d.nombre_clave
                  ORDER BY total DESC
                  LIMIT 10
              ) r ON r.nombre_clave = p.nombre_clave
              WHERE p.fecha = ? AND p.activo = 1
              ORDER BY c.nombre IS NULL ASC, c.nombre ASC, p.nombre ASC
              """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, fecha);
            if (filtrarNombre) {
                sentencia.setString(2, "%" + valor.trim() + "%");
            }
            try (ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    platos.add(mapearPlatoConEnriquecimiento(resultados));
                }
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054 || ex.getErrorCode() == 1146) {
                // Esquema previo a la v4 — consulta sin categorías ni ranking
                return listarPorFechaLegacy(valor, fecha);
            }
            throw new DataAccessException("No se pudieron listar los platos.", ex);
        }
        return platos;
    }

    private Platos mapearPlatoConEnriquecimiento(ResultSet resultados) throws SQLException {
        Platos plato = new Platos();
        plato.setId(resultados.getInt("id"));
        plato.setNombre(resultados.getString("nombre"));
        plato.setPrecioDecimal(resultados.getBigDecimal("precio"));
        plato.setAplicaIva(resultados.getInt("aplica_iva") != 0);
        int idCategoria = resultados.getInt("id_categoria");
        if (!resultados.wasNull()) {
            plato.setIdCategoria(idCategoria);
        }
        plato.setCategoriaNombre(resultados.getString("categoria_nombre"));
        plato.setCategoriaColor(resultados.getString("categoria_color"));
        plato.setFavorito(resultados.getInt("favorito") != 0);
        plato.setRankingTotal(resultados.getBigDecimal("rango_total"));
        return plato;
    }

    private List<Platos> listarPorFechaLegacy(String valor, String fecha) {
        List<Platos> platos = new ArrayList<>();
        boolean filtrarNombre = valor != null && !valor.trim().isEmpty();
        String sql = filtrarNombre
            ? """
              SELECT id, nombre, precio, fecha
              FROM platos
              WHERE fecha = ? AND nombre LIKE ?
              ORDER BY nombre ASC
              """
            : """
              SELECT id, nombre, precio, fecha
              FROM platos
              WHERE fecha = ?
              ORDER BY nombre ASC
              """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, fecha);
            if (filtrarNombre) {
                sentencia.setString(2, "%" + (valor != null ? valor.trim() : "") + "%");
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

    /** Soft-delete: marca el plato como inactivo en lugar de eliminarlo físicamente. */
    @Override
    public boolean desactivar(int id) {
        String sql = "UPDATE platos SET activo = 0, desactivado_en = NOW() WHERE id = ?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "desactivar plato " + id);
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return eliminarFisico(id);
            }
            throw new DataAccessException("No se pudo desactivar el plato.", ex);
        }
    }

    /** Reactiva un plato previamente desactivado. */
    @Override
    public boolean reactivar(int id) {
        String sql = "UPDATE platos SET activo = 1, desactivado_en = NULL WHERE id = ?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "reactivar plato " + id);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo reactivar el plato.", ex);
        }
    }

    /** Lista los platos desactivados para el panel de reactivación. */
    @Override
    public List<Platos> listarInactivos() {
        List<Platos> platos = new ArrayList<>();
        String sql = """
            SELECT id, nombre, precio, fecha, desactivado_en
            FROM platos
            WHERE activo = 0
            ORDER BY desactivado_en DESC
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Platos plato = new Platos();
                plato.setId(resultados.getInt("id"));
                plato.setNombre(resultados.getString("nombre"));
                plato.setPrecioDecimal(resultados.getBigDecimal("precio"));
                plato.setFecha(resultados.getString("fecha"));
                plato.setActivo(false);
                Timestamp ts = resultados.getTimestamp("desactivado_en");
                if (ts != null) {
                    plato.setDesactivadoEn(ts.toLocalDateTime());
                }
                platos.add(plato);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los platos inactivos.", ex);
        }
        return platos;
    }

    /** Compatibilidad hacia atrás: delega a desactivar() — no hace DELETE físico. */
    @Override
    public boolean eliminar(int id) {
        return desactivar(id);
    }

    private boolean eliminarFisico(int id) {
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
        String sql = """
            UPDATE platos
            SET nombre = ?, precio = ?, aplica_iva = ?
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, pla.getNombre());
            sentencia.setBigDecimal(2, precioPersistible(pla));
            sentencia.setInt(3, pla.isAplicaIva() ? 1 : 0);
            sentencia.setInt(4, pla.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "modificar plato " + pla.getId());
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return modificarLegacy(pla);
            }
            throw new DataAccessException("No se pudo modificar el plato.", ex);
        }
    }

    private boolean modificarLegacy(Platos pla) {
        String sql = """
            UPDATE platos
            SET nombre = ?, precio = ?
            WHERE id = ?
            """;
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

    /** Compatibilidad temporal con la vista Swing existente — ahora hace soft delete. */
    @Deprecated
    public boolean Eliminar(int id) {
        return desactivar(id);
    }

    /** Compatibilidad temporal con la vista Swing existente. */
    @Deprecated
    public boolean Modificar(Platos plato) {
        return modificar(plato);
    }
}
