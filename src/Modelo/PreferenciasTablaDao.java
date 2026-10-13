package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persiste los anchos de columna de cada JTable por usuario ({@code preferencias_tabla}).
 * Se guardan pesos normalizados (fraccion del ancho total), nunca pixeles, para que el
 * layout sobreviva a distintas resoluciones. Sin bloqueo ni versiones: un solo puesto.
 */
public class PreferenciasTablaDao {

    private static final Logger LOGGER = Logger.getLogger(PreferenciasTablaDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public PreferenciasTablaDao() {
        this(new ProveedorConexionJdbc());
    }

    public PreferenciasTablaDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
        try {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo sincronizar el esquema en PreferenciasTablaDao: " + ex.getMessage());
        }
    }

    /** Carga los pesos guardados o vacio si no hay, estan corruptos o falla la BD. */
    public Optional<double[]> cargar(int usuarioId, String tablaClave) {
        if (tablaClave == null || tablaClave.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT layout_json FROM preferencias_tabla WHERE usuario_id = ? AND tabla_clave = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, usuarioId);
            st.setString(2, tablaClave);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return deJson(rs.getString(1));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Sin preferencias para " + tablaClave + ": " + ex.getMessage());
        }
        return Optional.empty();
    }

    /** Guarda (upsert) los pesos de la tabla. */
    public void guardar(int usuarioId, String tablaClave, double[] pesos) {
        if (tablaClave == null || tablaClave.isBlank() || pesos == null || pesos.length == 0) {
            throw ErrorAplicacionException.validacion("Clave y pesos son obligatorios para guardar preferencias.");
        }
        String sql = "INSERT INTO preferencias_tabla (usuario_id, tabla_clave, layout_json)"
                + " VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE layout_json = VALUES(layout_json),"
                + " actualizado_en = CURRENT_TIMESTAMP";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, usuarioId);
            st.setString(2, tablaClave);
            st.setString(3, aJson(pesos));
            st.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo guardar el layout de " + tablaClave + ": " + ex.getMessage(), ex);
        }
    }

    /** Serializa pesos a JSON compacto: {"v":1,"w":[0.25,0.75]}. Sin dependencias externas. */
    static String aJson(double[] pesos) {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"w\":[");
        for (int i = 0; i < pesos.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Double.toString(pesos[i]));
        }
        return sb.append("]}").toString();
    }

    /** Lee el JSON compacto; vacio ante cualquier formato invalido o numero no finito. */
    static Optional<double[]> deJson(String json) {
        if (json == null) {
            return Optional.empty();
        }
        int ini = json.indexOf('[');
        int fin = json.lastIndexOf(']');
        if (ini < 0 || fin <= ini) {
            return Optional.empty();
        }
        String cuerpo = json.substring(ini + 1, fin).trim();
        if (cuerpo.isEmpty()) {
            return Optional.empty();
        }
        try {
            String[] partes = cuerpo.split(",");
            List<Double> lista = new ArrayList<>(partes.length);
            for (String p : partes) {
                double v = Double.parseDouble(p.trim());
                if (!Double.isFinite(v)) {
                    return Optional.empty();
                }
                lista.add(v);
            }
            if (lista.isEmpty()) {
                return Optional.empty();
            }
            double[] r = new double[lista.size()];
            for (int i = 0; i < r.length; i++) {
                r[i] = lista.get(i);
            }
            return Optional.of(r);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
