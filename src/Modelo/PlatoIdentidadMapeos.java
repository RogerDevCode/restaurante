package Modelo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mantiene sincronizada la identidad por nombre de un plato con sus tablas satélite
 * {@code plato_categoria} y {@code plato_favorito}.
 *
 * <p>Ambas tablas referencian al plato por su nombre normalizado
 * ({@code LOWER(TRIM(nombre))}), no por su id. Por eso un renombrado debe trasladar las
 * filas existentes: de lo contrario quedan huérfanas (el plato pierde su categoría y su
 * marca de favorito) y, además, bloquean para siempre la eliminación de la categoría
 * implicada, ya que {@code categoriaEnUso} las sigue contando.</p>
 *
 * <p>Los métodos son tolerantes a esquemas heredados sin estas tablas o sin la columna
 * generada {@code nombre_clave}: ante {@code 1054} (columna desconocida) o {@code 1146}
 * (tabla inexistente) no hacen nada, preservando la compatibilidad.</p>
 */
final class PlatoIdentidadMapeos {

    private PlatoIdentidadMapeos() {
    }

    /** Nombre normalizado actual del plato, o {@code null} si no existe o el esquema no lo expone. */
    static String claveDePlato(Connection conexion, int idPlato) {
        try (PreparedStatement ps = conexion.prepareStatement("SELECT nombre_clave FROM platos WHERE id = ?")) {
            ps.setInt(1, idPlato);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException ex) {
            if (esEsquemaHeredado(ex)) {
                return null;
            }
            throw new DataAccessException("No se pudo leer la identidad del plato.", ex);
        }
    }

    /** Traslada categoría y favorito de la clave anterior a la nueva cuando el nombre cambió. */
    static void migrarClave(Connection conexion, String claveAnterior, String claveNueva) {
        if (claveAnterior == null || claveNueva == null || claveAnterior.equals(claveNueva)) {
            return;
        }
        try {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "INSERT INTO plato_categoria (nombre_clave, id_categoria) "
                    + "SELECT ?, id_categoria FROM plato_categoria WHERE nombre_clave = ? "
                    + "ON DUPLICATE KEY UPDATE id_categoria = VALUES(id_categoria)")) {
                ps.setString(1, claveNueva);
                ps.setString(2, claveAnterior);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexion.prepareStatement(
                    "DELETE FROM plato_categoria WHERE nombre_clave = ?")) {
                ps.setString(1, claveAnterior);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexion.prepareStatement(
                    "INSERT IGNORE INTO plato_favorito (nombre_clave) "
                    + "SELECT ? FROM plato_favorito WHERE nombre_clave = ?")) {
                ps.setString(1, claveNueva);
                ps.setString(2, claveAnterior);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexion.prepareStatement(
                    "DELETE FROM plato_favorito WHERE nombre_clave = ?")) {
                ps.setString(1, claveAnterior);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            if (esEsquemaHeredado(ex)) {
                return;
            }
            throw new DataAccessException("No se pudieron trasladar las asignaciones del plato renombrado.", ex);
        }
    }

    /** Limpia las asignaciones de un plato (al eliminarlo físicamente). */
    static void eliminarMapeos(Connection conexion, String clave) {
        if (clave == null) {
            return;
        }
        try {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "DELETE FROM plato_categoria WHERE nombre_clave = ?")) {
                ps.setString(1, clave);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conexion.prepareStatement(
                    "DELETE FROM plato_favorito WHERE nombre_clave = ?")) {
                ps.setString(1, clave);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            if (esEsquemaHeredado(ex)) {
                return;
            }
            throw new DataAccessException("No se pudieron limpiar las asignaciones del plato eliminado.", ex);
        }
    }

    private static boolean esEsquemaHeredado(SQLException ex) {
        int code = ex.getErrorCode();
        return code == 1054 || code == 1146;
    }
}
