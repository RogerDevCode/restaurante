package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para registrar y consultar movimientos de saldos (CREDITO, DEVOLUCION, ANULACION)
 * en la tabla pedido_saldos_movimientos (Task 7).
 */
public class MovimientoSaldoPedidoDao {
    private final ProveedorConexionJdbc conexiones;

    public MovimientoSaldoPedidoDao() {
        this(new ProveedorConexionJdbc());
    }

    public MovimientoSaldoPedidoDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }

    public boolean registrarMovimiento(MovimientoSaldoPedido mov) {
        try (Connection con = conexiones.getConnection()) {
            return registrarMovimiento(mov, con);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar movimiento de saldo.", ex);
        }
    }

    public boolean registrarMovimiento(MovimientoSaldoPedido mov, Connection con) throws SQLException {
        if (mov == null) {
            throw ErrorAplicacionException.validacion("El movimiento no puede ser nulo.");
        }
        String sql = """
            INSERT INTO pedido_saldos_movimientos
                (id_pedido, id_liquidacion, tipo, moneda, monto_origen, equivalente_ves, tasa_cambio, actor, motivo, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id=id
            """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, mov.getIdPedido());
            if (mov.getIdLiquidacion() != null) {
                ps.setLong(2, mov.getIdLiquidacion());
            } else {
                ps.setNull(2, Types.BIGINT);
            }
            ps.setString(3, mov.getTipo());
            ps.setString(4, mov.getMoneda());
            ps.setBigDecimal(5, mov.getMontoOrigen());
            ps.setBigDecimal(6, mov.getEquivalenteVes());
            ps.setBigDecimal(7, mov.getTasaCambio());
            ps.setString(8, mov.getActor());
            ps.setString(9, mov.getMotivo());
            ps.setString(10, mov.getIdempotencyKey());
            return ps.executeUpdate() > 0;
        }
    }

    public List<MovimientoSaldoPedido> listarPorPedido(int idPedido) {
        try (Connection con = conexiones.getConnection()) {
            return listarPorPedido(idPedido, con);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los movimientos de saldo del pedido #" + idPedido, ex);
        }
    }

    public List<MovimientoSaldoPedido> listarPorPedido(int idPedido, Connection con) throws SQLException {
        String sql = """
            SELECT id, id_pedido, id_liquidacion, tipo, moneda, monto_origen, equivalente_ves, tasa_cambio,
                   actor, motivo, idempotency_key, creado_en
            FROM pedido_saldos_movimientos
            WHERE id_pedido = ?
            ORDER BY id ASC
            """;
        List<MovimientoSaldoPedido> resultado = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MovimientoSaldoPedido m = new MovimientoSaldoPedido();
                    m.setId(rs.getLong("id"));
                    m.setIdPedido(rs.getInt("id_pedido"));
                    long liqId = rs.getLong("id_liquidacion");
                    if (!rs.wasNull()) {
                        m.setIdLiquidacion(liqId);
                    }
                    m.setTipo(rs.getString("tipo"));
                    m.setMoneda(rs.getString("moneda"));
                    m.setMontoOrigen(rs.getBigDecimal("monto_origen"));
                    m.setEquivalenteVes(rs.getBigDecimal("equivalente_ves"));
                    m.setTasaCambio(rs.getBigDecimal("tasa_cambio"));
                    m.setActor(rs.getString("actor"));
                    m.setMotivo(rs.getString("motivo"));
                    m.setIdempotencyKey(rs.getString("idempotency_key"));
                    m.setCreadoEn(rs.getTimestamp("creado_en"));
                    resultado.add(m);
                }
            }
        }
        return resultado;
    }
}
