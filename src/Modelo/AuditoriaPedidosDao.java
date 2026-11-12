package Modelo;

import infraestructura.MigradorEsquemaJdbc;
import infraestructura.ProveedorConexionJdbc;

import java.math.BigDecimal;
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

    /**
     * Consulta detallada y filtrada para el reporte ejecutivo de tickets y facturas anuladas.
     *
     * @param filtro criterios multicriterio (fechas, mesa, usuario, tipo, texto libre)
     * @return lista de comprobantes anulados con datos de auditoría
     */
    public List<TicketAnuladoReporte> consultarReporteTicketsAnulados(FiltroReporteAnulados filtro) {
        List<TicketAnuladoReporte> resultado = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT p.id,
                   COALESCE(s.nombre, CASE WHEN p.tipo_pedido = 'RAPIDO' THEN 'Pedido rápido' ELSE 'General' END) AS sala,
                   p.num_mesa,
                   p.tipo_pedido,
                   p.usuario AS atendido_por,
                   p.fecha AS fecha_emision,
                   COALESCE(a.fecha_hora, p.cerrada_en, p.fecha) AS fecha_anulacion,
                   p.total,
                   p.total_bs,
                   p.tasa_cambio,
                   p.cliente_nombre,
                   p.cliente_documento,
                   COALESCE(a.usuario, 'Sistema') AS anulado_por,
                   COALESCE(a.motivo, 'Sin justificación registrada') AS motivo,
                   COALESCE(a.accion, 'ANULACION') AS accion_auditoria,
                   (SELECT COUNT(*) FROM pedido_pagos pg WHERE pg.id_pedido = p.id) AS cant_pagos
            FROM pedidos p
            LEFT JOIN salas s ON s.id = p.id_sala
            LEFT JOIN (
                SELECT a1.id_pedido, a1.usuario, a1.motivo, a1.fecha_hora, a1.accion
                FROM auditoria_pedidos a1
                INNER JOIN (
                    SELECT id_pedido, MAX(id) AS max_id
                    FROM auditoria_pedidos
                    WHERE accion LIKE 'ANULACION%'
                    GROUP BY id_pedido
                ) a2 ON a1.id = a2.max_id
            ) a ON p.id = a.id_pedido
            WHERE p.estado = 'ANULADO'
            """);

        List<Object> parametros = new ArrayList<>();

        if (filtro != null) {
            if (filtro.getFechaDesde() != null) {
                sql.append(" AND DATE(COALESCE(a.fecha_hora, p.cerrada_en, p.fecha)) >= ?");
                parametros.add(java.sql.Date.valueOf(filtro.getFechaDesde()));
            }
            if (filtro.getFechaHasta() != null) {
                sql.append(" AND DATE(COALESCE(a.fecha_hora, p.cerrada_en, p.fecha)) <= ?");
                parametros.add(java.sql.Date.valueOf(filtro.getFechaHasta()));
            }
            if (filtro.getNumMesa() != null) {
                if (filtro.getNumMesa() <= 0) {
                    sql.append(" AND (p.num_mesa <= 0 OR p.tipo_pedido = 'RAPIDO')");
                } else {
                    sql.append(" AND p.num_mesa = ?");
                    parametros.add(filtro.getNumMesa());
                }
            }
            if (filtro.getUsuario() != null && !filtro.getUsuario().isBlank()) {
                sql.append(" AND (LOWER(COALESCE(a.usuario, '')) = LOWER(?) OR LOWER(COALESCE(p.usuario, '')) = LOWER(?))");
                parametros.add(filtro.getUsuario().trim());
                parametros.add(filtro.getUsuario().trim());
            }
            if (filtro.getTipo() == FiltroReporteAnulados.TipoFiltroAnulacion.SOLO_PENDIENTES_FUGAS) {
                sql.append(" AND (SELECT COUNT(*) FROM pedido_pagos pg WHERE pg.id_pedido = p.id) = 0");
            } else if (filtro.getTipo() == FiltroReporteAnulados.TipoFiltroAnulacion.SOLO_FACTURAS_COBRADAS) {
                sql.append(" AND (SELECT COUNT(*) FROM pedido_pagos pg WHERE pg.id_pedido = p.id) > 0");
            }
            if (filtro.getTextoBusqueda() != null && !filtro.getTextoBusqueda().isBlank()) {
                String pattern = "%" + filtro.getTextoBusqueda().trim().toLowerCase() + "%";
                sql.append(" AND (CAST(p.id AS CHAR) LIKE ? OR LOWER(COALESCE(a.motivo, '')) LIKE ? OR LOWER(COALESCE(p.cliente_nombre, '')) LIKE ? OR LOWER(COALESCE(p.cliente_documento, '')) LIKE ?)");
                parametros.add(pattern);
                parametros.add(pattern);
                parametros.add(pattern);
                parametros.add(pattern);
            }
        }

        sql.append(" ORDER BY fecha_anulacion DESC, p.id DESC");

        try (Connection con = conexiones.getConnection()) {
            MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                for (int i = 0; i < parametros.size(); i++) {
                    ps.setObject(i + 1, parametros.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String sala = rs.getString("sala");
                        int numMesa = rs.getInt("num_mesa");
                        String tipoPedido = rs.getString("tipo_pedido");
                        String atendidoPor = rs.getString("atendido_por");
                        String fechaEmision = rs.getString("fecha_emision");
                        Timestamp fechaAnulacion = rs.getTimestamp("fecha_anulacion");
                        BigDecimal totalUsd = rs.getBigDecimal("total");
                        BigDecimal totalBs = rs.getBigDecimal("total_bs");
                        BigDecimal tasaCambio = rs.getBigDecimal("tasa_cambio");
                        String clienteNombre = rs.getString("cliente_nombre");
                        String clienteDocumento = rs.getString("cliente_documento");
                        String anuladoPor = rs.getString("anulado_por");
                        String motivo = rs.getString("motivo");
                        String accion = rs.getString("accion_auditoria");
                        int cantPagos = rs.getInt("cant_pagos");

                        resultado.add(new TicketAnuladoReporte(
                                id, sala, numMesa, tipoPedido, atendidoPor, fechaEmision, fechaAnulacion,
                                totalUsd, totalBs, tasaCambio, clienteNombre, clienteDocumento,
                                anuladoPor, motivo, accion, cantPagos > 0
                        ));
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al consultar reporte de tickets anulados: " + ex.getMessage(), ex);
        }
        return resultado;
    }

    /**
     * Retorna la lista de nombres de usuarios que han atendido o anulado pedidos.
     */
    public List<String> listarUsuariosInvolucrados() {
        List<String> usuarios = new ArrayList<>();
        String sql = """
            SELECT DISTINCT usuario FROM (
                SELECT usuario FROM auditoria_pedidos WHERE usuario IS NOT NULL AND usuario != ''
                UNION
                SELECT usuario FROM pedidos WHERE estado = 'ANULADO' AND usuario IS NOT NULL AND usuario != ''
            ) u ORDER BY usuario ASC
            """;
        try (Connection con = conexiones.getConnection()) {
            MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String u = rs.getString("usuario");
                    if (u != null && !u.isBlank()) {
                        usuarios.add(u.trim());
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al listar usuarios involucrados en anulaciones: " + ex.getMessage());
        }
        return usuarios;
    }
}
