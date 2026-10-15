package Modelo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** Persiste evidencia de cambios monetarios dentro de la transacción que los aplica. */
public final class AuditoriaPreciosDao {

    public record CambioPrecio(
            String accion,
            Integer productoId,
            Integer pedidoId,
            Integer detallePedidoId,
            String monedaBase,
            BigDecimal precioUsdAnterior,
            BigDecimal precioUsdNuevo,
            BigDecimal precioVesAnterior,
            BigDecimal precioVesNuevo,
            BigDecimal tasaAnterior,
            BigDecimal tasaNueva,
            BigDecimal totalAnteriorVes,
            BigDecimal totalNuevoVes,
            String actor,
            String motivo,
            String idempotencyKey) {
        public CambioPrecio {
            if (accion == null || !accion.matches("CAMBIO_TASA|CAMBIO_CATALOGO|CONGELAR_LINEA")) {
                throw new IllegalArgumentException("La acción de auditoría monetaria no es válida.");
            }
            if (actor == null || actor.isBlank() || actor.length() > 100) {
                throw new IllegalArgumentException("El actor de auditoría es obligatorio y debe tener hasta 100 caracteres.");
            }
            if (motivo != null && motivo.length() > 500) {
                throw new IllegalArgumentException("El motivo de auditoría supera 500 caracteres.");
            }
            if (idempotencyKey != null && idempotencyKey.length() > 100) {
                throw new IllegalArgumentException("La clave de idempotencia supera 100 caracteres.");
            }
            if (monedaBase != null && !monedaBase.matches("USD|VES")) {
                throw new IllegalArgumentException("La moneda base de auditoría debe ser USD o VES.");
            }
        }
    }

    /** No hace commit: el evento debe confirmarse o revertirse con el cambio auditado. */
    public long insertar(Connection conexion, CambioPrecio cambio) throws SQLException {
        Objects.requireNonNull(conexion, "La conexión es obligatoria.");
        Objects.requireNonNull(cambio, "El evento de auditoría es obligatorio.");
        String sql = "INSERT INTO auditoria_cambios_precio (accion, producto_id, pedido_id, detalle_pedido_id, moneda_base, "
                + "precio_usd_anterior, precio_usd_nuevo, precio_ves_anterior, precio_ves_nuevo, tasa_anterior, tasa_nueva, "
                + "total_anterior_ves, total_nuevo_ves, actor, motivo, idempotency_key) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexion.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cambio.accion());
            setInteger(ps, 2, cambio.productoId());
            setInteger(ps, 3, cambio.pedidoId());
            setInteger(ps, 4, cambio.detallePedidoId());
            ps.setString(5, cambio.monedaBase());
            ps.setBigDecimal(6, cambio.precioUsdAnterior());
            ps.setBigDecimal(7, cambio.precioUsdNuevo());
            ps.setBigDecimal(8, cambio.precioVesAnterior());
            ps.setBigDecimal(9, cambio.precioVesNuevo());
            ps.setBigDecimal(10, cambio.tasaAnterior());
            ps.setBigDecimal(11, cambio.tasaNueva());
            ps.setBigDecimal(12, cambio.totalAnteriorVes());
            ps.setBigDecimal(13, cambio.totalNuevoVes());
            ps.setString(14, cambio.actor());
            ps.setString(15, cambio.motivo());
            ps.setString(16, cambio.idempotencyKey());
            if (ps.executeUpdate() != 1) throw new SQLException("No se pudo guardar la auditoría del cambio de precios.");
            try (var keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("La auditoría se insertó sin devolver su identificador.");
                return keys.getLong(1);
            }
        }
    }

    private static void setInteger(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) ps.setNull(indice, java.sql.Types.INTEGER);
        else ps.setInt(indice, valor);
    }
}
