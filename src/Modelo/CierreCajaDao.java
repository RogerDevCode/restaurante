package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consulta y consolida los datos financieros y operativos para emitir el Cierre Parcial (Corte X)
 * y Cierre Total (Corte Z) de caja.
 */
public class CierreCajaDao {
    private static final Logger LOGGER = Logger.getLogger(CierreCajaDao.class.getName());
    private final ProveedorConexionJdbc conexiones;

    public CierreCajaDao() {
        this(new ProveedorConexionJdbc());
    }

    public CierreCajaDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }
    /**
     * Consulta la base de datos para compilar el reporte de cierre de caja en la fecha especificada.
     */
    public CierreCaja consultarCierre(String fechaConsulta, CierreCaja.TipoCierre tipo, String usuarioEmisor, Config config) {
        String fechaFiltro;
        if (fechaConsulta != null && !fechaConsulta.trim().isEmpty()) {
            try {
                LocalDate fechaParsed = LocalDate.parse(fechaConsulta.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate maxFechaPermitida = LocalDate.now().isAfter(LocalDate.now(java.time.ZoneOffset.UTC))
                        ? LocalDate.now()
                        : LocalDate.now(java.time.ZoneOffset.UTC);
                if (fechaParsed.isAfter(maxFechaPermitida)) {
                    throw ErrorAplicacionException.validacion("No se puede generar cierre de caja para una fecha futura: " + fechaConsulta.trim());
                }
                fechaFiltro = fechaParsed.toString();
            } catch (java.time.format.DateTimeParseException ex) {
                throw ErrorAplicacionException.validacion("El formato de fecha de cierre debe ser AAAA-MM-DD (ej: 2026-10-08).");
            }
        } else {
            fechaFiltro = LocalDate.now().toString();
        }

        BigDecimal tasaConfig = (config != null && config.getTasaDolar() != null && config.getTasaDolar().compareTo(BigDecimal.ZERO) > 0)
                ? config.getTasaDolar()
                : null;
        if (tasaConfig == null) {
            throw ErrorAplicacionException.validacion("No se puede consultar el cierre de caja: la tasa de cambio del sistema no ha sido configurada o es inválida.");
        }

        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(tipo);
        cierre.setFecha(fechaFiltro);
        cierre.setFechaHoraEmision(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        cierre.setUsuarioEmisor(usuarioEmisor != null && !usuarioEmisor.isBlank() ? usuarioEmisor.trim() : "Sistema");
        cierre.setTasaCambioReferencia(tasaConfig);

        String likeParam = fechaFiltro + "%";

        try (Connection con = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);

            // Registrar log si existen pedidos legados que requieran fallback referencial
            try (PreparedStatement psLeg = con.prepareStatement(
                    "SELECT COUNT(*) FROM pedidos p WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?) " +
                    "AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id) AND (NULLIF(p.tasa_cambio, 0) IS NULL OR p.total_bs IS NULL)")) {
                psLeg.setString(1, likeParam);
                psLeg.setString(2, fechaFiltro);
                try (ResultSet rsLeg = psLeg.executeQuery()) {
                    if (rsLeg.next() && rsLeg.getInt(1) > 0) {
                        LOGGER.log(Level.WARNING, "CierreCajaDao.consultarCierre: Se detectaron {0} pedidos legados sin tasa o totalBs; se aplicó fallback referencial en el reporte.", rsLeg.getInt(1));
                    }
                }
            } catch (SQLException exLeg) {
                LOGGER.log(Level.FINE, "No se pudo auditar pedidos legados para cierre: " + exLeg.getMessage());
            }

            boolean autoCommitPrevio = con.getAutoCommit();
            int aislamientoPrevio = con.getTransactionIsolation();

            try {
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.setAutoCommit(false);

                // 1. Totales de pedidos finalizados
                String sqlTotales = """
                    SELECT COUNT(*) AS cant,
                           COALESCE(SUM(total), 0) AS total_usd,
                           COALESCE(SUM(tot_bs), 0) AS total_bs,
                           COALESCE(SUM(subtotal), 0) AS subtotal_usd,
                           COALESCE(SUM(sub_bs), 0) AS subtotal_bs,
                           COALESCE(SUM(iva_monto), 0) AS iva_usd,
                           COALESCE(SUM(CASE WHEN tot_bs > sub_bs THEN (tot_bs - sub_bs) ELSE 0.00 END), 0) AS iva_bs
                    FROM (
                        SELECT total,
                               subtotal,
                               iva_monto,
                               COALESCE(NULLIF(total_bs, 0), CASE WHEN tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(total * tasa_cambio, 2) ELSE 0.00 END) AS tot_bs,
                               CASE
                                   WHEN NULLIF(subtotal_bs, 0) IS NOT NULL THEN subtotal_bs
                                   WHEN COALESCE(iva_monto, 0) > 0 AND tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(subtotal * tasa_cambio, 2)
                                   ELSE COALESCE(NULLIF(total_bs, 0), CASE WHEN tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(total * tasa_cambio, 2) ELSE 0.00 END)
                               END AS sub_bs
                        FROM pedidos
                        WHERE estado = 'FINALIZADO'
                          AND (COALESCE(cerrada_en, fecha) LIKE ? OR DATE(COALESCE(cerrada_en, fecha)) = ?)
                    ) calc_pedidos
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlTotales)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int finalizados = rs.getInt("cant");
                            BigDecimal totalUsd = rs.getBigDecimal("total_usd");
                            BigDecimal totalBs = rs.getBigDecimal("total_bs");
                            BigDecimal subtotalUsd = rs.getBigDecimal("subtotal_usd");
                            BigDecimal subtotalBs = rs.getBigDecimal("subtotal_bs");
                            BigDecimal ivaUsd = rs.getBigDecimal("iva_usd");
                            BigDecimal ivaBs = rs.getBigDecimal("iva_bs");

                            cierre.setPedidosFinalizados(finalizados);
                            cierre.setTotalVentasUsd(totalUsd);
                            cierre.setTotalVentasBs(totalBs);
                            cierre.setSubtotalUsd(subtotalUsd);
                            cierre.setSubtotalBs(subtotalBs);
                            cierre.setIvaUsd(ivaUsd);
                            cierre.setIvaBs(ivaBs);

                            if (finalizados > 0) {
                                cierre.setTicketPromedioUsd(totalUsd.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
                                cierre.setTicketPromedioBs(totalBs.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
                            }
                        }
                    }
                }

                // 1.1 Desglose fiscal analítico (Base Imponible y Exento de la jornada)
                String sqlFiscal = """
                    SELECT
                        COALESCE(SUM(CASE
                            WHEN d.aplica_iva = 1 THEN
                                CASE
                                    WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES' AND p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0
                                        THEN ROUND((CASE WHEN d.precio_incluye_iva = 1 THEN (d.precio * d.cantidad) / (1 + (COALESCE(d.iva_porcentaje, p.iva_porcentaje, (SELECT cfg.iva_porcentaje FROM config cfg WHERE cfg.id = 1), 16.00)/100.0)) ELSE (d.precio * d.cantidad) END) / p.tasa_cambio, 2)
                                    ELSE
                                        ROUND((CASE WHEN d.precio_incluye_iva = 1 THEN (d.precio * d.cantidad) / (1 + (COALESCE(d.iva_porcentaje, p.iva_porcentaje, (SELECT cfg.iva_porcentaje FROM config cfg WHERE cfg.id = 1), 16.00)/100.0)) ELSE (d.precio * d.cantidad) END), 2)
                                END
                            ELSE 0.00 END), 0.00) AS base_imp_usd,
                        COALESCE(SUM(CASE
                            WHEN d.aplica_iva = 1 THEN
                                CASE
                                    WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES'
                                        THEN ROUND((CASE WHEN d.precio_incluye_iva = 1 THEN (d.precio * d.cantidad) / (1 + (COALESCE(d.iva_porcentaje, p.iva_porcentaje, (SELECT cfg.iva_porcentaje FROM config cfg WHERE cfg.id = 1), 16.00)/100.0)) ELSE (d.precio * d.cantidad) END), 2)
                                    ELSE
                                        CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND((CASE WHEN d.precio_incluye_iva = 1 THEN (d.precio * d.cantidad) / (1 + (COALESCE(d.iva_porcentaje, p.iva_porcentaje, (SELECT cfg.iva_porcentaje FROM config cfg WHERE cfg.id = 1), 16.00)/100.0)) ELSE (d.precio * d.cantidad) END) * p.tasa_cambio, 2) ELSE 0.00 END
                                END
                            ELSE 0.00 END), 0.00) AS base_imp_bs,
                        COALESCE(SUM(CASE
                            WHEN d.aplica_iva = 0 OR d.aplica_iva IS NULL THEN
                                CASE
                                    WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES' AND p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0
                                        THEN ROUND((d.precio * d.cantidad) / p.tasa_cambio, 2)
                                    ELSE
                                        ROUND((d.precio * d.cantidad), 2)
                                END
                            ELSE 0.00 END), 0.00) AS exento_usd,
                        COALESCE(SUM(CASE
                            WHEN d.aplica_iva = 0 OR d.aplica_iva IS NULL THEN
                                CASE
                                    WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES'
                                        THEN ROUND((d.precio * d.cantidad), 2)
                                    ELSE
                                        CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND((d.precio * d.cantidad) * p.tasa_cambio, 2) ELSE 0.00 END
                                END
                            ELSE 0.00 END), 0.00) AS exento_bs
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlFiscal)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            BigDecimal baseUsd = rs.getBigDecimal("base_imp_usd");
                            BigDecimal baseBs = rs.getBigDecimal("base_imp_bs");
                            BigDecimal exUsd = rs.getBigDecimal("exento_usd");
                            BigDecimal exBs = rs.getBigDecimal("exento_bs");

                            if (baseUsd.signum() == 0 && exUsd.signum() == 0 && cierre.getSubtotalUsd().signum() > 0) {
                                if (cierre.getIvaUsd().signum() > 0) {
                                    BigDecimal pctIva = BigDecimal.ZERO;
                                    try (PreparedStatement psCfg = con.prepareStatement("SELECT iva_porcentaje FROM config WHERE id = 1")) {
                                        try (ResultSet rsCfg = psCfg.executeQuery()) {
                                            if (rsCfg.next()) {
                                                pctIva = rsCfg.getBigDecimal(1);
                                            }
                                        }
                                    }
                                    if (pctIva != null && pctIva.compareTo(BigDecimal.ZERO) > 0) {
                                        baseUsd = cierre.getIvaUsd().multiply(new BigDecimal("100")).divide(pctIva, 2, RoundingMode.HALF_UP).min(cierre.getSubtotalUsd());
                                        exUsd = cierre.getSubtotalUsd().subtract(baseUsd).max(BigDecimal.ZERO);
                                        baseBs = cierre.getIvaBs().multiply(new BigDecimal("100")).divide(pctIva, 2, RoundingMode.HALF_UP).min(cierre.getSubtotalBs());
                                        exBs = cierre.getSubtotalBs().subtract(baseBs).max(BigDecimal.ZERO);
                                    } else {
                                        baseUsd = cierre.getSubtotalUsd();
                                        exUsd = BigDecimal.ZERO;
                                        baseBs = cierre.getSubtotalBs();
                                        exBs = BigDecimal.ZERO;
                                    }
                                } else {
                                    exUsd = cierre.getSubtotalUsd();
                                    exBs = cierre.getSubtotalBs();
                                }
                            }
                            if (cierre.getSubtotalUsd() != null && cierre.getSubtotalUsd().signum() > 0) {
                                if (baseUsd.signum() > 0 && exUsd.signum() > 0) {
                                    baseUsd = cierre.getSubtotalUsd().subtract(exUsd).max(BigDecimal.ZERO);
                                } else if (exUsd.signum() > 0 && baseUsd.signum() == 0) {
                                    exUsd = cierre.getSubtotalUsd();
                                } else if (baseUsd.signum() > 0 && exUsd.signum() == 0) {
                                    baseUsd = cierre.getSubtotalUsd();
                                }
                            }
                            if (cierre.getSubtotalBs() != null && cierre.getSubtotalBs().signum() > 0) {
                                if (baseBs.signum() > 0 && exBs.signum() > 0) {
                                    baseBs = cierre.getSubtotalBs().subtract(exBs).max(BigDecimal.ZERO);
                                } else if (exBs.signum() > 0 && baseBs.signum() == 0) {
                                    exBs = cierre.getSubtotalBs();
                                } else if (baseBs.signum() > 0 && exBs.signum() == 0) {
                                    baseBs = cierre.getSubtotalBs();
                                }
                            }
                            cierre.setBaseImponibleUsd(baseUsd);
                            cierre.setBaseImponibleBs(baseBs);
                            cierre.setExentoUsd(exUsd);
                            cierre.setExentoBs(exBs);
                        }
                    }
                }

                // 2. Pedidos pendientes (abiertos y cerrados pendientes de pago)
                String sqlPendientes = """
                    SELECT COUNT(*) AS cant,
                           COALESCE(SUM(CASE WHEN cerrada_en IS NOT NULL THEN 1 ELSE 0 END), 0) AS cerradas_pendientes
                    FROM pedidos
                    WHERE estado = 'PENDIENTE'
                      AND (COALESCE(cerrada_en, fecha) LIKE ? OR DATE(COALESCE(cerrada_en, fecha)) = ?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlPendientes)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            cierre.setPedidosPendientes(rs.getInt("cant"));
                            cierre.setMesasCerradasPendientesPago(rs.getInt("cerradas_pendientes"));
                        }
                    }
                }

                // 3. Desglose por método de pago
                List<CierreCaja.ResumenMetodoPago> metodos = new ArrayList<>();
                String sqlMetodos = """
                    SELECT metodo,
                           COUNT(*) AS cant,
                           COALESCE(SUM(monto_usd), 0) AS total_usd,
                           COALESCE(SUM(monto_bs), 0) AS total_bs
                    FROM (
                        SELECT pp.id_pedido,
                               COALESCE(NULLIF(TRIM(pp.metodo_pago), ''), 'EFECTIVO') AS metodo,
                               COALESCE(pp.monto_usd, CASE WHEN pp.moneda = 'USD' THEN pp.monto_aplicado ELSE ROUND(pp.monto_bs / NULLIF(pp.tasa_cambio, 0), 2) END, 0) AS monto_usd,
                               COALESCE(pp.monto_bs, CASE WHEN pp.moneda = 'VES' THEN pp.monto_aplicado WHEN pp.tasa_cambio IS NOT NULL AND pp.tasa_cambio > 0 THEN ROUND(pp.monto_usd * pp.tasa_cambio, 2) ELSE 0.00 END) AS monto_bs
                        FROM pedido_pagos pp
                        INNER JOIN pedidos p ON p.id = pp.id_pedido
                        WHERE p.estado = 'FINALIZADO' AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                        UNION ALL
                        SELECT p.id AS id_pedido,
                               COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo,
                               COALESCE(p.total, 0) AS monto_usd,
                               COALESCE(NULLIF(p.total_bs, 0), CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END) AS monto_bs
                        FROM pedidos p
                        WHERE p.estado = 'FINALIZADO' AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                          AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id)
                    ) pagos_desglose
                    GROUP BY metodo
                    ORDER BY total_usd DESC
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlMetodos)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    ps.setString(3, likeParam);
                    ps.setString(4, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            metodos.add(new CierreCaja.ResumenMetodoPago(
                                    rs.getString("metodo"),
                                    rs.getInt("cant"),
                                    rs.getBigDecimal("total_usd"),
                                    rs.getBigDecimal("total_bs")
                            ));
                        }
                    }
                }
                cierre.setDesgloseMetodos(metodos);

                String sqlEfectivo = """
                    SELECT
                        COALESCE(SUM(CASE WHEN metodo_pago IN ('EFECTIVO', 'EFECTIVO_BS', 'MIXTO') THEN monto_bs ELSE 0 END), 0) AS efectivo_bs,
                        COALESCE(SUM(CASE WHEN metodo_pago IN ('EFECTIVO_USD', 'MIXTO') THEN monto_usd ELSE 0 END), 0) AS efectivo_usd,
                        COALESCE(SUM(mixto_sin_desglose), 0) AS mixtos_sin_desglose
                    FROM (
                        SELECT pp.id_pedido, pp.metodo_pago, pp.monto_bs, pp.monto_usd, 0 AS mixto_sin_desglose
                        FROM pedido_pagos pp
                        INNER JOIN pedidos p ON p.id = pp.id_pedido
                        WHERE p.estado = 'FINALIZADO' AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                        UNION ALL
                        SELECT p.id AS id_pedido, COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo_pago,
                               CASE WHEN p.metodo_pago IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(NULLIF(p.efectivo_bs, 0), NULLIF(p.total_bs, 0), CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END)
                                    WHEN p.metodo_pago = 'MIXTO' THEN COALESCE(p.efectivo_bs, 0) ELSE 0 END AS monto_bs,
                               CASE WHEN p.metodo_pago = 'EFECTIVO_USD' THEN COALESCE(p.efectivo_usd, p.total, 0)
                                    WHEN p.metodo_pago = 'MIXTO' THEN COALESCE(p.efectivo_usd, 0) ELSE 0 END AS monto_usd,
                               CASE WHEN p.metodo_pago = 'MIXTO' AND (p.efectivo_bs IS NULL OR p.efectivo_usd IS NULL) THEN 1 ELSE 0 END AS mixto_sin_desglose
                        FROM pedidos p
                        WHERE p.estado = 'FINALIZADO' AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                          AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id)
                    ) pagos
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlEfectivo)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    ps.setString(3, likeParam);
                    ps.setString(4, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            cierre.setResumenEfectivo(
                                     rs.getBigDecimal("efectivo_bs"),
                                     rs.getBigDecimal("efectivo_usd"),
                                     rs.getInt("mixtos_sin_desglose"));
                        }
                    }
                }

                // 4. Desglose por sala
                List<CierreCaja.ResumenSala> salas = new ArrayList<>();
                String sqlSalas = """
                    SELECT COALESCE(s.nombre, 'Sin Sala') AS sala_nombre,
                           COUNT(p.id) AS cant,
                           COALESCE(SUM(p.total), 0) AS total_usd,
                           COALESCE(SUM(COALESCE(NULLIF(p.total_bs, 0), CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END)), 0) AS total_bs
                    FROM pedidos p
                    LEFT JOIN salas s ON p.id_sala = s.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                    GROUP BY s.nombre
                    ORDER BY total_usd DESC
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlSalas)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            salas.add(new CierreCaja.ResumenSala(
                                    rs.getString("sala_nombre"),
                                    rs.getInt("cant"),
                                    rs.getBigDecimal("total_usd"),
                                    rs.getBigDecimal("total_bs")
                            ));
                        }
                    }
                }
                cierre.setDesgloseSalas(salas);

                // 5. Desglose por usuario / mesonero
                List<CierreCaja.ResumenUsuario> usuarios = new ArrayList<>();
                String sqlUsuarios = """
                    SELECT COALESCE(NULLIF(TRIM(usuario), ''), 'Sistema') AS usuario_nom,
                           COUNT(id) AS cant,
                           COALESCE(SUM(total), 0) AS total_usd
                    FROM pedidos
                    WHERE estado = 'FINALIZADO'
                      AND (COALESCE(cerrada_en, fecha) LIKE ? OR DATE(COALESCE(cerrada_en, fecha)) = ?)
                    GROUP BY usuario_nom
                    ORDER BY total_usd DESC
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlUsuarios)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            usuarios.add(new CierreCaja.ResumenUsuario(
                                    rs.getString("usuario_nom"),
                                    rs.getInt("cant"),
                                    rs.getBigDecimal("total_usd")
                            ));
                        }
                    }
                }
                cierre.setDesgloseUsuarios(usuarios);

                // 6. Total de artículos y Top 5 platos más vendidos
                String sqlTotalArticulos = """
                    SELECT COALESCE(SUM(d.cantidad), 0) AS cant
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlTotalArticulos)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            cierre.setTotalArticulos(rs.getInt("cant"));
                        }
                    }
                }

                List<CierreCaja.PlatoVendido> platos = new ArrayList<>();
                String sqlTopPlatos = """
                    SELECT d.nombre,
                           SUM(d.cantidad) AS cant,
                           COALESCE(SUM(CASE
                               WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES' AND p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0
                                   THEN ROUND((d.precio * d.cantidad) / p.tasa_cambio, 2)
                               ELSE ROUND((d.precio * d.cantidad), 2)
                           END), 0) AS total_usd,
                           COALESCE(SUM(CASE
                               WHEN UPPER(TRIM(COALESCE(d.moneda_base, 'USD'))) = 'VES'
                                   THEN ROUND((d.precio * d.cantidad), 2)
                               ELSE CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND((d.precio * d.cantidad) * p.tasa_cambio, 2) ELSE 0.00 END
                           END), 0) AS total_bs
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (COALESCE(p.cerrada_en, p.fecha) LIKE ? OR DATE(COALESCE(p.cerrada_en, p.fecha)) = ?)
                    GROUP BY d.nombre
                    ORDER BY cant DESC
                    LIMIT 5
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlTopPlatos)) {
                    ps.setString(1, likeParam);
                    ps.setString(2, fechaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            platos.add(new CierreCaja.PlatoVendido(
                                    rs.getString("nombre"),
                                    rs.getInt("cant"),
                                    rs.getBigDecimal("total_usd"),
                                    rs.getBigDecimal("total_bs")
                            ));
                        }
                    }
                }
                cierre.setTopPlatos(platos);

                con.commit();
            } finally {
                try {
                    con.setTransactionIsolation(aislamientoPrevio);
                    con.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
            }

        } catch (SQLException exCon) {
            throw new DataAccessException("No se pudo compilar el cierre de caja debido a un error de base de datos: " + exCon.getMessage(), exCon);
        }

        return cierre;
    }

    /**
     * Consulta de SOLO LECTURA: resumen de ventas de un rango de fechas (inclusive), con totales
     * USD/Bs y desglose por método de pago. No persiste en cierres_caja y no avanza correlativos.
     * Reutiliza las fórmulas monetarias de {@link #consultarCierre} con filtro de rango.
     */
    public CierreCaja consultarResumenVentas(String desde, String hasta, String usuarioEmisor, Config config) {
        final LocalDate dInicio;
        final LocalDate dFin;
        try {
            dInicio = LocalDate.parse(desde, DateTimeFormatter.ISO_LOCAL_DATE);
            dFin = LocalDate.parse(hasta, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception exParse) {
            throw ErrorAplicacionException.validacion("El formato de fecha del resumen de ventas debe ser AAAA-MM-DD (ej: 2026-10-20).");
        }
        if (dInicio.isAfter(dFin)) {
            throw ErrorAplicacionException.validacion("La fecha inicial del resumen de ventas no puede ser posterior a la fecha final.");
        }

        BigDecimal tasaConfig = (config != null && config.getTasaDolar() != null && config.getTasaDolar().compareTo(BigDecimal.ZERO) > 0)
                ? config.getTasaDolar()
                : null;
        if (tasaConfig == null) {
            throw ErrorAplicacionException.validacion("No se puede consultar el resumen de ventas: la tasa de cambio del sistema no ha sido configurada o es inválida.");
        }

        String desdeFiltro = dInicio.toString();
        String hastaFiltro = dFin.toString();

        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(CierreCaja.TipoCierre.RESUMEN);
        cierre.setFecha(dInicio.equals(dFin) ? desdeFiltro : desdeFiltro + " a " + hastaFiltro);
        cierre.setFechaHoraEmision(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        cierre.setUsuarioEmisor(usuarioEmisor != null && !usuarioEmisor.isBlank() ? usuarioEmisor.trim() : "Sistema");
        cierre.setTasaCambioReferencia(tasaConfig);

        try (Connection con = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);

            boolean autoCommitPrevio = con.getAutoCommit();
            int aislamientoPrevio = con.getTransactionIsolation();

            try {
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.setAutoCommit(false);

                // 1. Totales de pedidos finalizados del rango (fórmulas §1 de consultarCierre)
                String sqlTotales = """
                    SELECT COUNT(*) AS cant,
                           COALESCE(SUM(total), 0) AS total_usd,
                           COALESCE(SUM(tot_bs), 0) AS total_bs
                    FROM (
                        SELECT total,
                               COALESCE(NULLIF(total_bs, 0), CASE WHEN tasa_cambio IS NOT NULL AND tasa_cambio > 0 THEN ROUND(total * tasa_cambio, 2) ELSE 0.00 END) AS tot_bs
                        FROM pedidos
                        WHERE estado = 'FINALIZADO'
                          AND DATE(COALESCE(cerrada_en, fecha)) BETWEEN ? AND ?
                    ) calc_pedidos
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlTotales)) {
                    ps.setString(1, desdeFiltro);
                    ps.setString(2, hastaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int finalizados = rs.getInt("cant");
                            BigDecimal totalUsd = rs.getBigDecimal("total_usd");
                            BigDecimal totalBs = rs.getBigDecimal("total_bs");

                            cierre.setPedidosFinalizados(finalizados);
                            cierre.setTotalVentasUsd(totalUsd);
                            cierre.setTotalVentasBs(totalBs);

                            if (finalizados > 0) {
                                cierre.setTicketPromedioUsd(totalUsd.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
                                cierre.setTicketPromedioBs(totalBs.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
                            }
                        }
                    }
                }

                // 1.1 Total de artículos del rango (para la tarjeta de actividad)
                String sqlTotalArticulos = """
                    SELECT COALESCE(SUM(d.cantidad), 0) AS cant
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                      AND DATE(COALESCE(p.cerrada_en, p.fecha)) BETWEEN ? AND ?
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlTotalArticulos)) {
                    ps.setString(1, desdeFiltro);
                    ps.setString(2, hastaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            cierre.setTotalArticulos(rs.getInt("cant"));
                        }
                    }
                }

                // 2. Desglose por método de pago del rango (fórmulas §3 de consultarCierre)
                List<CierreCaja.ResumenMetodoPago> metodos = new ArrayList<>();
                String sqlMetodos = """
                    SELECT metodo,
                           COUNT(*) AS cant,
                           COALESCE(SUM(monto_usd), 0) AS total_usd,
                           COALESCE(SUM(monto_bs), 0) AS total_bs
                    FROM (
                        SELECT pp.id_pedido,
                               COALESCE(NULLIF(TRIM(pp.metodo_pago), ''), 'EFECTIVO') AS metodo,
                               COALESCE(pp.monto_usd, CASE WHEN pp.moneda = 'USD' THEN pp.monto_aplicado ELSE ROUND(pp.monto_bs / NULLIF(pp.tasa_cambio, 0), 2) END, 0) AS monto_usd,
                               COALESCE(pp.monto_bs, CASE WHEN pp.moneda = 'VES' THEN pp.monto_aplicado WHEN pp.tasa_cambio IS NOT NULL AND pp.tasa_cambio > 0 THEN ROUND(pp.monto_usd * pp.tasa_cambio, 2) ELSE 0.00 END) AS monto_bs
                        FROM pedido_pagos pp
                        INNER JOIN pedidos p ON p.id = pp.id_pedido
                        WHERE p.estado = 'FINALIZADO' AND DATE(COALESCE(p.cerrada_en, p.fecha)) BETWEEN ? AND ?
                        UNION ALL
                        SELECT p.id AS id_pedido,
                               COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo,
                               COALESCE(p.total, 0) AS monto_usd,
                               COALESCE(NULLIF(p.total_bs, 0), CASE WHEN p.tasa_cambio IS NOT NULL AND p.tasa_cambio > 0 THEN ROUND(p.total * p.tasa_cambio, 2) ELSE 0.00 END) AS monto_bs
                        FROM pedidos p
                        WHERE p.estado = 'FINALIZADO' AND DATE(COALESCE(p.cerrada_en, p.fecha)) BETWEEN ? AND ?
                          AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id)
                    ) pagos_desglose
                    GROUP BY metodo
                    ORDER BY total_usd DESC
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlMetodos)) {
                    ps.setString(1, desdeFiltro);
                    ps.setString(2, hastaFiltro);
                    ps.setString(3, desdeFiltro);
                    ps.setString(4, hastaFiltro);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            metodos.add(new CierreCaja.ResumenMetodoPago(
                                    rs.getString("metodo"),
                                    rs.getInt("cant"),
                                    rs.getBigDecimal("total_usd"),
                                    rs.getBigDecimal("total_bs")
                            ));
                        }
                    }
                }
                cierre.setDesgloseMetodos(metodos);

                BigDecimal totalUsdResumen = cierre.getTotalVentasUsd();
                BigDecimal totalBsResumen = cierre.getTotalVentasBs();
                if (totalUsdResumen != null && totalUsdResumen.compareTo(BigDecimal.ZERO) > 0
                        && totalBsResumen != null && totalBsResumen.compareTo(BigDecimal.ZERO) > 0) {
                    cierre.setTasaCambioReferencia(totalBsResumen.divide(totalUsdResumen, 4, RoundingMode.HALF_UP));
                }

                con.commit();
            } finally {
                try {
                    con.setTransactionIsolation(aislamientoPrevio);
                    con.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
            }

        } catch (SQLException exCon) {
            throw new DataAccessException("No se pudo compilar el resumen de ventas debido a un error de base de datos: " + exCon.getMessage(), exCon);
        }

        return cierre;
    }

    /**
     * Construye un CierreCaja puro a partir de objetos en memoria (ideal para pruebas unitarias sin BD).
     */
    public static CierreCaja calcularDesdeMemoria(
            String fecha,
            CierreCaja.TipoCierre tipo,
            String usuarioEmisor,
            Config config,
            List<Pedidos> pedidos,
            List<DetallePedido> detalles) {

        CierreCaja cierre = new CierreCaja();
        cierre.setTipo(tipo);
        cierre.setFecha(fecha != null ? fecha : LocalDate.now().toString());
        cierre.setFechaHoraEmision(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        cierre.setUsuarioEmisor(usuarioEmisor != null ? usuarioEmisor : "Sistema");

        BigDecimal tasa = (config != null && config.getTasaDolar() != null && config.getTasaDolar().compareTo(BigDecimal.ZERO) > 0)
                ? config.getTasaDolar() : null;
        if (tasa == null) {
            throw ErrorAplicacionException.validacion(
                    "No se puede calcular el cierre de caja: la tasa de cambio del sistema no ha sido configurada o es inválida.");
        }
        cierre.setTasaCambioReferencia(tasa);

        if (pedidos == null) {
            return cierre;
        }

        int finalizados = 0;
        int pendientes = 0;
        BigDecimal totUsd = BigDecimal.ZERO;
        BigDecimal totBs = BigDecimal.ZERO;
        BigDecimal subUsd = BigDecimal.ZERO;
        BigDecimal subBs = BigDecimal.ZERO;
        BigDecimal ivUsd = BigDecimal.ZERO;
        BigDecimal ivBs = BigDecimal.ZERO;
        BigDecimal baseImpUsdAcum = BigDecimal.ZERO;
        BigDecimal baseImpBsAcum = BigDecimal.ZERO;
        BigDecimal exentoUsdAcum = BigDecimal.ZERO;
        BigDecimal exentoBsAcum = BigDecimal.ZERO;
        BigDecimal efectivoEsperadoBs = BigDecimal.ZERO;
        BigDecimal efectivoEsperadoUsd = BigDecimal.ZERO;
        int mixtosSinDesglose = 0;

        Map<String, AcumuladorMetodo> metodosMap = new LinkedHashMap<>();
        Map<String, AcumuladorSala> salasMap = new LinkedHashMap<>();
        Map<String, AcumuladorUsuario> usuariosMap = new LinkedHashMap<>();

        for (Pedidos p : pedidos) {
            boolean esFin = "FINALIZADO".equalsIgnoreCase(p.getEstado());
            if (esFin) {
                finalizados++;
                BigDecimal u = p.getTotalDecimal() != null ? p.getTotalDecimal() : BigDecimal.ZERO;
                BigDecimal b = (p.getTotalBs() != null && p.getTotalBs().signum() > 0)
                        ? p.getTotalBs()
                        : u.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                BigDecimal su = p.getSubtotal() != null ? p.getSubtotal() : u;
                BigDecimal iu = p.getIvaMonto() != null ? p.getIvaMonto() : BigDecimal.ZERO;

                BigDecimal sb;
                BigDecimal ib;
                if (p.getSubtotalBs() != null && p.getSubtotalBs().signum() > 0) {
                    sb = p.getSubtotalBs();
                    BigDecimal diffBs = b.subtract(sb).max(BigDecimal.ZERO);
                    ib = (p.getIvaBs() != null && p.getIvaBs().compareTo(diffBs) <= 0)
                            ? p.getIvaBs()
                            : diffBs;
                } else if (iu.signum() > 0) {
                    sb = su.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                    ib = b.subtract(sb).max(BigDecimal.ZERO);
                } else {
                    sb = b;
                    ib = BigDecimal.ZERO;
                }

                totUsd = totUsd.add(u);
                totBs = totBs.add(b);
                subUsd = subUsd.add(su);
                subBs = subBs.add(sb);
                ivUsd = ivUsd.add(iu);
                ivBs = ivBs.add(ib);

                BigDecimal baseUsdPed;
                BigDecimal exUsdPed;
                BigDecimal baseBsPed;
                BigDecimal exBsPed;
                if (iu.signum() > 0 && p.getIvaPorcentaje() != null && p.getIvaPorcentaje().compareTo(BigDecimal.ZERO) > 0) {
                    baseUsdPed = iu.multiply(new BigDecimal("100")).divide(p.getIvaPorcentaje(), 2, RoundingMode.HALF_UP).min(su);
                    exUsdPed = su.subtract(baseUsdPed).max(BigDecimal.ZERO);
                    baseBsPed = baseUsdPed.multiply(tasa).setScale(2, RoundingMode.HALF_UP).min(sb);
                    exBsPed = sb.subtract(baseBsPed).max(BigDecimal.ZERO);
                } else if (iu.signum() > 0) {
                    baseUsdPed = su;
                    exUsdPed = BigDecimal.ZERO;
                    baseBsPed = sb;
                    exBsPed = BigDecimal.ZERO;
                } else {
                    baseUsdPed = BigDecimal.ZERO;
                    exUsdPed = su;
                    baseBsPed = BigDecimal.ZERO;
                    exBsPed = sb;
                }
                baseImpUsdAcum = baseImpUsdAcum.add(baseUsdPed);
                baseImpBsAcum = baseImpBsAcum.add(baseBsPed);
                exentoUsdAcum = exentoUsdAcum.add(exUsdPed);
                exentoBsAcum = exentoBsAcum.add(exBsPed);

                if (p.getPagos() != null && !p.getPagos().isEmpty()) {
                    for (PagoPedido pp : p.getPagos()) {
                        String m = pp.metodo() != null && !pp.metodo().isBlank() ? pp.metodo().trim().toUpperCase() : "EFECTIVO";
                        BigDecimal pu = "USD".equalsIgnoreCase(pp.moneda()) ? pp.montoAplicado() : (tasa.signum() > 0 ? pp.montoAplicado().divide(tasa, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                        BigDecimal pb = "VES".equalsIgnoreCase(pp.moneda()) ? pp.montoAplicado() : pp.montoAplicado().multiply(tasa).setScale(2, RoundingMode.HALF_UP);
                        AcumuladorMetodo am = metodosMap.computeIfAbsent(m, k -> new AcumuladorMetodo());
                        am.cant++;
                        am.usd = am.usd.add(pu);
                        am.bs = am.bs.add(pb);

                        if ("EFECTIVO".equals(m) || "EFECTIVO_BS".equals(m) || "EFECTIVO_USD".equals(m)) {
                            BigDecimal apl = pp.montoAplicado() != null ? pp.montoAplicado() : BigDecimal.ZERO;
                            if ("VES".equalsIgnoreCase(pp.moneda()) || "EFECTIVO_BS".equals(m)) {
                                efectivoEsperadoBs = efectivoEsperadoBs.add(apl);
                            } else {
                                efectivoEsperadoUsd = efectivoEsperadoUsd.add(apl);
                            }
                        }
                    }
                } else {
                    String met = p.getMetodoPago() != null && !p.getMetodoPago().isBlank() ? p.getMetodoPago().trim().toUpperCase() : "EFECTIVO";
                    switch (met) {
                        case "EFECTIVO", "EFECTIVO_BS" -> efectivoEsperadoBs = efectivoEsperadoBs.add(
                                p.getEfectivoBs() != null ? p.getEfectivoBs() : b);
                        case "EFECTIVO_USD" -> efectivoEsperadoUsd = efectivoEsperadoUsd.add(
                                p.getEfectivoUsd() != null ? p.getEfectivoUsd() : u);
                        case "MIXTO" -> {
                            if (p.getEfectivoBs() == null || p.getEfectivoUsd() == null) {
                                mixtosSinDesglose++;
                            } else {
                                efectivoEsperadoBs = efectivoEsperadoBs.add(p.getEfectivoBs());
                                efectivoEsperadoUsd = efectivoEsperadoUsd.add(p.getEfectivoUsd());
                            }
                        }
                        default -> { }
                    }
                    AcumuladorMetodo am = metodosMap.computeIfAbsent(met, k -> new AcumuladorMetodo());
                    am.cant++;
                    am.usd = am.usd.add(u);
                    am.bs = am.bs.add(b);
                }

                String sal = p.getSala() != null && !p.getSala().isBlank() ? p.getSala().trim() : "General";
                AcumuladorSala as = salasMap.computeIfAbsent(sal, k -> new AcumuladorSala());
                as.cant++;
                as.usd = as.usd.add(u);
                as.bs = as.bs.add(b);

                String usr = p.getUsuario() != null && !p.getUsuario().isBlank() ? p.getUsuario().trim() : "Operador";
                AcumuladorUsuario au = usuariosMap.computeIfAbsent(usr, k -> new AcumuladorUsuario());
                au.cant++;
                au.usd = au.usd.add(u);
            } else {
                pendientes++;
            }
        }

        cierre.setPedidosFinalizados(finalizados);
        cierre.setPedidosPendientes(pendientes);
        cierre.setTotalVentasUsd(totUsd);
        cierre.setTotalVentasBs(totBs);
        cierre.setSubtotalUsd(subUsd);
        cierre.setSubtotalBs(subBs);
        cierre.setBaseImponibleUsd(baseImpUsdAcum);
        cierre.setBaseImponibleBs(baseImpBsAcum);
        cierre.setExentoUsd(exentoUsdAcum);
        cierre.setExentoBs(exentoBsAcum);
        cierre.setIvaUsd(ivUsd);
        cierre.setIvaBs(ivBs);
        cierre.setResumenEfectivo(efectivoEsperadoBs, efectivoEsperadoUsd, mixtosSinDesglose);

        if (finalizados > 0) {
            cierre.setTicketPromedioUsd(totUsd.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
            cierre.setTicketPromedioBs(totBs.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
        }

        List<CierreCaja.ResumenMetodoPago> listaMet = new ArrayList<>();
        metodosMap.forEach((k, v) -> listaMet.add(new CierreCaja.ResumenMetodoPago(
                k, v.cant,
                v.usd.setScale(2, RoundingMode.HALF_UP),
                v.bs.setScale(2, RoundingMode.HALF_UP))));
        cierre.setDesgloseMetodos(listaMet);

        List<CierreCaja.ResumenSala> listaSal = new ArrayList<>();
        salasMap.forEach((k, v) -> listaSal.add(new CierreCaja.ResumenSala(
                k, v.cant,
                v.usd.setScale(2, RoundingMode.HALF_UP),
                v.bs.setScale(2, RoundingMode.HALF_UP))));
        cierre.setDesgloseSalas(listaSal);

        List<CierreCaja.ResumenUsuario> listaUsr = new ArrayList<>();
        usuariosMap.forEach((k, v) -> listaUsr.add(new CierreCaja.ResumenUsuario(
                k, v.cant,
                v.usd.setScale(2, RoundingMode.HALF_UP))));
        cierre.setDesgloseUsuarios(listaUsr);

        if (detalles != null) {
            int arts = 0;
            Map<String, AcumuladorPlato> platosMap = new HashMap<>();
            for (DetallePedido d : detalles) {
                int cant = d.getCantidad();
                arts += cant;
                BigDecimal pr = d.getPrecioDecimal() != null ? d.getPrecioDecimal() : BigDecimal.ZERO;
                BigDecimal sub = pr.multiply(BigDecimal.valueOf(cant));
                String nom = d.getNombre() != null ? d.getNombre().trim() : "Plato";
                AcumuladorPlato ap = platosMap.computeIfAbsent(nom, k -> new AcumuladorPlato());
                ap.cant += cant;

                BigDecimal itemUsd;
                BigDecimal itemBs;
                if ("VES".equalsIgnoreCase(d.getMonedaBase())) {
                    itemBs = sub.setScale(2, RoundingMode.HALF_UP);
                    itemUsd = (tasa != null && tasa.signum() > 0)
                            ? sub.divide(tasa, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2);
                } else {
                    itemUsd = sub.setScale(2, RoundingMode.HALF_UP);
                    itemBs = (tasa != null && tasa.signum() > 0)
                            ? sub.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2);
                }
                ap.total = ap.total.add(itemUsd);
                ap.totalBs = ap.totalBs.add(itemBs);
            }
            cierre.setTotalArticulos(arts);

            List<CierreCaja.PlatoVendido> top = new ArrayList<>();
            platosMap.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().cant, e1.getValue().cant))
                    .limit(5)
                    .forEach(e -> top.add(new CierreCaja.PlatoVendido(
                            e.getKey(), e.getValue().cant,
                            e.getValue().total.setScale(2, RoundingMode.HALF_UP),
                            e.getValue().totalBs.setScale(2, RoundingMode.HALF_UP))));
            cierre.setTopPlatos(top);
        }

        return cierre;
    }

    /**
     * Persiste el cierre de caja (Corte X o Z) y su arqueo en la tabla cierres_caja para auditoría.
     */
    public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
        if (cierre == null) {
            throw ErrorAplicacionException.validacion("El objeto de cierre de caja no puede ser nulo.");
        }
        if (cierre.getTasaCambioReferencia() == null || cierre.getTasaCambioReferencia().compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("No se puede registrar el cierre de caja: la tasa de cambio de referencia es obligatoria y debe ser mayor a cero.");
        }
        if (rutaPdf != null && !rutaPdf.isBlank() && !java.nio.file.Files.exists(java.nio.file.Path.of(rutaPdf))) {
            throw new DataAccessException("El archivo de cierre PDF no existe o es inaccesible: " + rutaPdf);
        }

        String tipoCierre = cierre.getTipo() != null ? cierre.getTipo().name() : "TOTAL";
        String fechaJornada = (cierre.getFecha() != null && !cierre.getFecha().isBlank())
                ? cierre.getFecha()
                : java.time.LocalDate.now().toString();
        if ("TOTAL".equalsIgnoreCase(tipoCierre) && existeCierreZ(fechaJornada)) {
            throw ErrorAplicacionException.validacion("ya existe un cierre Z registrado para la fecha " + fechaJornada + ".");
        }

        String sql = """
            INSERT INTO cierres_caja (
                tipo, fecha_jornada, fecha_hora_emision, usuario_emisor,
                total_ventas_usd, total_ventas_bs,
                efectivo_esperado_bs, efectivo_declarado_bs, diferencia_bs, estado_conciliacion_bs,
                efectivo_esperado_usd, efectivo_declarado_usd, diferencia_usd, estado_conciliacion_usd,
                tasa_cambio, ruta_pdf
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection con = conexiones.getConnection()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                if ("TOTAL".equalsIgnoreCase(tipoCierre)) {
                    String sqlCheck = "SELECT COUNT(*) FROM cierres_caja WHERE tipo = 'TOTAL' AND fecha_jornada = ? FOR UPDATE";
                    try (PreparedStatement psCheck = con.prepareStatement(sqlCheck)) {
                        psCheck.setString(1, fechaJornada);
                        try (ResultSet rsCheck = psCheck.executeQuery()) {
                            if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                                con.rollback();
                                throw ErrorAplicacionException.validacion("ya existe un cierre Z registrado para la fecha " + fechaJornada + ".");
                            }
                        }
                    }
                }

                boolean exito;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, tipoCierre);
                    ps.setString(2, fechaJornada);
                    String fechaEmision = (cierre.getFechaHoraEmision() != null && !cierre.getFechaHoraEmision().isBlank())
                            ? cierre.getFechaHoraEmision()
                            : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    ps.setString(3, fechaEmision);
                    ps.setString(4, cierre.getUsuarioEmisor());
                    ps.setBigDecimal(5, cierre.getTotalVentasUsd());
                    ps.setBigDecimal(6, cierre.getTotalVentasBs());
                    ps.setBigDecimal(7, cierre.getTotalEfectivoBs());
                    ps.setBigDecimal(8, cierre.getEfectivoDeclaradoBs());
                    ps.setBigDecimal(9, cierre.tieneConciliacionBs() ? cierre.getDiferenciaEfectivoBs() : null);
                    ps.setString(10, cierre.tieneConciliacionBs() ? cierre.getEstadoConciliacionBs() : null);
                    ps.setBigDecimal(11, cierre.getTotalEfectivoUsd());
                    ps.setBigDecimal(12, cierre.getEfectivoDeclaradoUsd());
                    ps.setBigDecimal(13, cierre.tieneConciliacionUsd() ? cierre.getDiferenciaEfectivoUsd() : null);
                    ps.setString(14, cierre.tieneConciliacionUsd() ? cierre.getEstadoConciliacionUsd() : null);
                    ps.setBigDecimal(15, cierre.getTasaCambioReferencia());
                    ps.setString(16, rutaPdf);
                    exito = ps.executeUpdate() > 0;
                }
                con.commit();
                return exito;
            } catch (SQLException ex) {
                con.rollback();
                if (ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState())) {
                    throw ErrorAplicacionException.validacion("ya existe un cierre Z registrado para la fecha " + fechaJornada + ".");
                }
                throw ex;
            } finally {
                try {
                    con.setAutoCommit(autoCommitPrevio);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al registrar cierre de caja en base de datos: " + ex.getMessage());
            throw new DataAccessException("No se pudo registrar el cierre de caja en base de datos: " + ex.getMessage(), ex);
        }
    }

    public boolean existeCierreZ(String fechaJornada) {
        if (fechaJornada == null || fechaJornada.isBlank()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM cierres_caja WHERE tipo = 'TOTAL' AND fecha_jornada = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fechaJornada.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al consultar existencia de cierre Z: " + ex.getMessage());
            return false;
        }
    }

    private static final class AcumuladorMetodo {
        int cant = 0;
        BigDecimal usd = BigDecimal.ZERO;
        BigDecimal bs = BigDecimal.ZERO;
    }

    private static final class AcumuladorSala {
        int cant = 0;
        BigDecimal usd = BigDecimal.ZERO;
        BigDecimal bs = BigDecimal.ZERO;
    }

    private static final class AcumuladorUsuario {
        int cant = 0;
        BigDecimal usd = BigDecimal.ZERO;
    }

    private static final class AcumuladorPlato {
        int cant = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBs = BigDecimal.ZERO;
    }
}
