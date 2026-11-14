package Modelo;

import infraestructura.ProveedorConexionJdbc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceso a datos para la generación del Libro de Ventas Fiscal según normativa SENIAT.
 * Extrae todas las transacciones finalizadas y anuladas del período seleccionado,
 * calculando y normalizando bases imponibles, exentos y débitos fiscales en Bolívares.
 */
public class LibroVentasDao {

    private static final Logger LOGGER = Logger.getLogger(LibroVentasDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public LibroVentasDao() {
        this(new ProveedorConexionJdbc());
    }

    public LibroVentasDao(ProveedorConexionJdbc conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "El proveedor de conexiones no puede ser nulo.");
    }

    public List<RegistroLibroVenta> consultarLibroVentas(FiltroLibroVentas filtro) {
        if (filtro == null) {
            throw ErrorAplicacionException.validacion("El filtro de libro de ventas no puede ser nulo.");
        }

        LocalDate desde = filtro.getFechaDesde();
        LocalDate hasta = filtro.getFechaHasta();

        String sql = """
            SELECT 
                p.id,
                DATE(COALESCE(p.cerrada_en, p.fecha)) AS fecha_operacion,
                p.cliente_documento,
                p.cliente_nombre,
                p.numero_control,
                p.nc_numero_control,
                p.estado,
                p.total_bs,
                p.subtotal_bs,
                p.iva_bs,
                p.iva_porcentaje,
                p.tasa_cambio,
                p.total AS total_usd,
                p.subtotal AS subtotal_usd,
                p.iva_monto AS iva_usd,
                p.igtf_total_bs,
                COALESCE(ex.total_exento_usd, 0.00) AS total_exento_usd
            FROM pedidos p
            LEFT JOIN (
                SELECT id_pedido, SUM(precio * cantidad) AS total_exento_usd
                FROM detalle_pedidos
                WHERE aplica_iva = 0
                GROUP BY id_pedido
            ) ex ON ex.id_pedido = p.id
            WHERE p.estado IN ('FINALIZADO', 'ANULADO')
              AND DATE(COALESCE(p.cerrada_en, p.fecha)) BETWEEN ? AND ?
            ORDER BY p.id ASC
            """;

        List<RegistroLibroVenta> registros = new ArrayList<>();

        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));

            try (ResultSet rs = ps.executeQuery()) {
                int contadorOperacion = 1;

                while (rs.next()) {
                    int idPedido = rs.getInt("id");
                    Date fechaSql = rs.getDate("fecha_operacion");
                    LocalDate fechaOperacion = fechaSql != null ? fechaSql.toLocalDate() : desde;
                    String docCliente = rs.getString("cliente_documento");
                    String nombreCliente = rs.getString("cliente_nombre");
                    String numControl = rs.getString("numero_control");
                    String ncControl = rs.getString("nc_numero_control");
                    String estado = rs.getString("estado");

                    BigDecimal totalBs = rs.getBigDecimal("total_bs");
                    BigDecimal subtotalBs = rs.getBigDecimal("subtotal_bs");
                    BigDecimal ivaBs = rs.getBigDecimal("iva_bs");
                    BigDecimal ivaPct = rs.getBigDecimal("iva_porcentaje");
                    BigDecimal tasa = rs.getBigDecimal("tasa_cambio");
                    BigDecimal totalUsd = rs.getBigDecimal("total_usd");
                    BigDecimal subtotalUsd = rs.getBigDecimal("subtotal_usd");
                    BigDecimal ivaUsd = rs.getBigDecimal("iva_usd");
                    BigDecimal igtfBs = rs.getBigDecimal("igtf_total_bs");
                    BigDecimal exentoUsd = rs.getBigDecimal("total_exento_usd");

                    if (tasa == null || tasa.compareTo(BigDecimal.ZERO) <= 0) {
                        tasa = new BigDecimal("44.8000"); // tasa de seguridad por defecto
                    }
                    if (ivaPct == null || ivaPct.compareTo(BigDecimal.ZERO) < 0) {
                        ivaPct = new BigDecimal("16.00");
                    }

                    boolean anulado = "ANULADO".equalsIgnoreCase(estado);
                    String tipoTransaccion = anulado ? "03-ANU" : "01-REG";
                    String nroFactura = String.format("%08d", idPedido);
                    String facturaAfectada = anulado ? nroFactura : "";
                    String nroNotaCredito = (ncControl != null && !ncControl.isBlank()) ? ncControl.trim() : (anulado ? "NC-" + nroFactura : "");

                    BigDecimal totalFinalBs;
                    BigDecimal exentoFinalBs;
                    BigDecimal baseImponibleFinalBs;
                    BigDecimal ivaFinalBs;
                    BigDecimal igtfFinalBs;

                    if (anulado) {
                        // En factura anulada SENIAT, los montos fiscales se asientan en 0.00
                        totalFinalBs = BigDecimal.ZERO.setScale(2);
                        exentoFinalBs = BigDecimal.ZERO.setScale(2);
                        baseImponibleFinalBs = BigDecimal.ZERO.setScale(2);
                        ivaFinalBs = BigDecimal.ZERO.setScale(2);
                        igtfFinalBs = BigDecimal.ZERO.setScale(2);
                        nombreCliente = (nombreCliente != null && !nombreCliente.isBlank()) ? nombreCliente + " (ANULADA)" : "ANULADA";
                    } else {
                        // Cálculo y normalización de montos activos
                        if (totalBs != null && totalBs.compareTo(BigDecimal.ZERO) > 0) {
                            totalFinalBs = totalBs.setScale(2, RoundingMode.HALF_UP);
                        } else if (totalUsd != null) {
                            totalFinalBs = totalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            totalFinalBs = BigDecimal.ZERO.setScale(2);
                        }

                        if (exentoUsd != null && exentoUsd.compareTo(BigDecimal.ZERO) > 0) {
                            exentoFinalBs = exentoUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            exentoFinalBs = BigDecimal.ZERO.setScale(2);
                        }

                        if (ivaBs != null && ivaBs.compareTo(BigDecimal.ZERO) >= 0) {
                            ivaFinalBs = ivaBs.setScale(2, RoundingMode.HALF_UP);
                        } else if (ivaUsd != null) {
                            ivaFinalBs = ivaUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            ivaFinalBs = BigDecimal.ZERO.setScale(2);
                        }

                        if (subtotalBs != null && subtotalBs.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal subBruto = subtotalBs.setScale(2, RoundingMode.HALF_UP);
                            baseImponibleFinalBs = subBruto.subtract(exentoFinalBs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                        } else if (subtotalUsd != null) {
                            BigDecimal subBruto = subtotalUsd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                            baseImponibleFinalBs = subBruto.subtract(exentoFinalBs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            baseImponibleFinalBs = totalFinalBs.subtract(ivaFinalBs).subtract(exentoFinalBs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                        }

                        igtfFinalBs = igtfBs != null ? igtfBs.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
                    }

                    RegistroLibroVenta reg = new RegistroLibroVenta(
                            contadorOperacion++,
                            fechaOperacion,
                            docCliente,
                            nombreCliente,
                            nroFactura,
                            numControl != null ? numControl : "",
                            "",
                            nroNotaCredito,
                            facturaAfectada,
                            tipoTransaccion,
                            totalFinalBs,
                            exentoFinalBs,
                            baseImponibleFinalBs,
                            ivaPct,
                            ivaFinalBs,
                            BigDecimal.ZERO.setScale(2), // alícuota reducida base
                            BigDecimal.ZERO.setScale(2), // alícuota reducida pct
                            BigDecimal.ZERO.setScale(2), // alícuota reducida iva
                            BigDecimal.ZERO.setScale(2), // alícuota adicional base
                            BigDecimal.ZERO.setScale(2), // alícuota adicional pct
                            BigDecimal.ZERO.setScale(2), // alícuota adicional iva
                            BigDecimal.ZERO.setScale(2), // iva retenido
                            "",                         // nro comprobante retención
                            igtfFinalBs
                    );

                    registros.add(reg);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error al consultar libro de ventas fiscal en BD: " + ex.getMessage(), ex);
            throw new ErrorAplicacionException("No se pudo generar el libro de ventas fiscal: " + ex.getMessage(), ex);
        }

        return registros;
    }
}
