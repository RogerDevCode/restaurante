package Modelo;

import infraestructura.MigradorEsquemaJdbc;
import infraestructura.ProveedorConexionJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO para la tabla `auditoria_pedidos`.
 * Permite registrar y consultar eventos de auditoría (anulaciones, reimpresiones de comprobantes, etc.).
 */
public class AuditoriaPedidosDao {

    private static final Logger LOGGER = Logger.getLogger(AuditoriaPedidosDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public AuditoriaPedidosDao() {
        this(new ProveedorConexionJdbc());
    }

    public AuditoriaPedidosDao(ProveedorConexionJdbc conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones no puede ser nulo.");
    }

    public boolean registrar(AuditoriaPedido auditoria) {
        if (auditoria == null) {
            throw ErrorAplicacionException.validacion("El registro de auditoría no puede ser nulo.");
        }
        if (auditoria.getIdPedido() <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para auditar.");
        }
        String sql = """
            INSERT INTO auditoria_pedidos (id_pedido, accion, motivo, usuario, fecha_hora)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection con = conexiones.getConnection()) {
            MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, auditoria.getIdPedido());
                ps.setString(2, auditoria.getAccion() != null ? auditoria.getAccion().trim().toUpperCase() : "ACCION");
                ps.setString(3, auditoria.getMotivo() != null ? auditoria.getMotivo().trim() : "");
                ps.setString(4, auditoria.getUsuario() != null ? auditoria.getUsuario().trim() : "Sistema");
                ps.setTimestamp(5, auditoria.getFechaHora() != null ? auditoria.getFechaHora() : new Timestamp(System.currentTimeMillis()));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al registrar evento de auditoría para pedido #" + auditoria.getIdPedido() + ": " + ex.getMessage());
            return false;
        }
    }

    public List<AuditoriaPedido> listarPorPedido(int idPedido) {
        if (idPedido <= 0) {
            return List.of();
        }
        List<AuditoriaPedido> lista = new ArrayList<>();
        String sql = "SELECT id, id_pedido, accion, motivo, usuario, fecha_hora FROM auditoria_pedidos WHERE id_pedido = ? ORDER BY id DESC";
        try (Connection con = conexiones.getConnection()) {
            MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, idPedido);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new AuditoriaPedido(
                                rs.getInt("id"),
                                rs.getInt("id_pedido"),
                                rs.getString("accion"),
                                rs.getString("motivo"),
                                rs.getString("usuario"),
                                rs.getTimestamp("fecha_hora")
                        ));
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al listar auditoría para pedido #" + idPedido + ": " + ex.getMessage());
        }
        return lista;
    }

    public List<AuditoriaPedido> listarRecientes(int limite) {
        int max = limite > 0 ? limite : 50;
        List<AuditoriaPedido> lista = new ArrayList<>();
        String sql = "SELECT id, id_pedido, accion, motivo, usuario, fecha_hora FROM auditoria_pedidos ORDER BY id DESC LIMIT ?";
        try (Connection con = conexiones.getConnection()) {
            MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, max);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new AuditoriaPedido(
                                rs.getInt("id"),
                                rs.getInt("id_pedido"),
                                rs.getString("accion"),
                                rs.getString("motivo"),
                                rs.getString("usuario"),
                                rs.getTimestamp("fecha_hora")
                        ));
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al listar eventos recientes de auditoría: " + ex.getMessage());
        }
        return lista;
    }
}
