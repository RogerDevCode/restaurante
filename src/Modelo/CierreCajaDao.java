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
                    "AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id) AND (p.tasa_cambio IS NULL OR p.total_bs IS NULL)")) {
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
                           COALESCE(SUM(total_bs), 0) AS total_bs,
                           COALESCE(SUM(subtotal), 0) AS subtotal_usd,
                           COALESCE(SUM(subtotal_bs), 0) AS subtotal_bs,
                           COALESCE(SUM(iva_monto), 0) AS iva_usd,
                           COALESCE(SUM(iva_bs), 0) AS iva_bs
                    FROM pedidos
                    WHERE estado = 'FINALIZADO'
                      AND (fecha LIKE ? OR DATE(fecha) = ?)
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

                // 2. Pedidos pendientes (abiertos y cerrados pendientes de pago)
                String sqlPendientes = """
                    SELECT COUNT(*) AS cant,
                           COALESCE(SUM(CASE WHEN cerrada_en IS NOT NULL THEN 1 ELSE 0 END), 0) AS cerradas_pendientes
                    FROM pedidos
                    WHERE estado = 'PENDIENTE'
                      AND (fecha LIKE ? OR DATE(fecha) = ?)
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
                    SELECT metodo, COUNT(DISTINCT id_pedido) AS cant,
                           COALESCE(SUM(total_usd), 0) AS total_usd,
                           COALESCE(SUM(total_bs), 0) AS total_bs
                    FROM (
                        SELECT pp.id_pedido, pp.metodo_pago AS metodo, pp.monto_usd AS total_usd,
                               pp.monto_bs AS total_bs
                        FROM pedido_pagos pp
                        INNER JOIN pedidos p ON p.id = pp.id_pedido
                        WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
                        UNION ALL
                        SELECT p.id AS id_pedido,
                               COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo,
                               COALESCE(p.total, 0) AS total_usd,
                               COALESCE(p.total_bs, p.total * COALESCE(NULLIF(p.tasa_cambio, 0), 36.5000), 0) AS total_bs
                        FROM pedidos p
                        WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
                          AND NOT EXISTS (SELECT 1 FROM pedido_pagos pp WHERE pp.id_pedido = p.id)
                    ) pagos
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
                        WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
                        UNION ALL
                        SELECT p.id AS id_pedido, COALESCE(NULLIF(TRIM(p.metodo_pago), ''), 'EFECTIVO') AS metodo_pago,
                               CASE WHEN p.metodo_pago IN ('EFECTIVO', 'EFECTIVO_BS') THEN COALESCE(p.efectivo_bs, p.total_bs, 0)
                                    WHEN p.metodo_pago = 'MIXTO' THEN COALESCE(p.efectivo_bs, 0) ELSE 0 END AS monto_bs,
                               CASE WHEN p.metodo_pago = 'EFECTIVO_USD' THEN COALESCE(p.efectivo_usd, p.total, 0)
                                    WHEN p.metodo_pago = 'MIXTO' THEN COALESCE(p.efectivo_usd, 0) ELSE 0 END AS monto_usd,
                               CASE WHEN p.metodo_pago = 'MIXTO' AND (p.efectivo_bs IS NULL OR p.efectivo_usd IS NULL) THEN 1 ELSE 0 END AS mixto_sin_desglose
                        FROM pedidos p
                        WHERE p.estado = 'FINALIZADO' AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
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
                           COALESCE(SUM(p.total_bs), 0) AS total_bs
                    FROM pedidos p
                    LEFT JOIN salas s ON p.id_sala = s.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
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
                      AND (fecha LIKE ? OR DATE(fecha) = ?)
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
                      AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
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
                           COALESCE(SUM(d.precio * d.cantidad), 0) AS total_usd
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                      AND (p.fecha LIKE ? OR DATE(p.fecha) = ?)
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
                                    rs.getBigDecimal("total_usd")
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
            LOGGER.log(Level.WARNING, "CierreCajaDao.calcularDesdeMemoria: Configuración sin tasa válida. Aplicando tasa referencial por defecto 36.5000.");
            tasa = new BigDecimal("36.5000");
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
        BigDecimal efectivoEsperadoBs = BigDecimal.ZERO;
        BigDecimal efectivoEsperadoUsd = BigDecimal.ZERO;
        int mixtosSinDesglose = 0;

        Map<String, int[]> metodosMap = new LinkedHashMap<>(); // metodo -> [cant, usdCents, bsCents]
        Map<String, int[]> salasMap = new LinkedHashMap<>();
        Map<String, int[]> usuariosMap = new LinkedHashMap<>();

        for (Pedidos p : pedidos) {
            boolean esFin = "FINALIZADO".equalsIgnoreCase(p.getEstado());
            if (esFin) {
                finalizados++;
                BigDecimal u = p.getTotalDecimal() != null ? p.getTotalDecimal() : BigDecimal.ZERO;
                BigDecimal b = p.getTotalBs() != null ? p.getTotalBs() : u.multiply(tasa);
                BigDecimal su = p.getSubtotal() != null ? p.getSubtotal() : u;
                BigDecimal sb = p.getSubtotalBs() != null ? p.getSubtotalBs() : b;
                BigDecimal iu = p.getIvaMonto() != null ? p.getIvaMonto() : BigDecimal.ZERO;
                BigDecimal ib = p.getIvaBs() != null ? p.getIvaBs() : BigDecimal.ZERO;

                totUsd = totUsd.add(u);
                totBs = totBs.add(b);
                subUsd = subUsd.add(su);
                subBs = subBs.add(sb);
                ivUsd = ivUsd.add(iu);
                ivBs = ivBs.add(ib);

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
                metodosMap.computeIfAbsent(met, k -> new int[3]);
                metodosMap.get(met)[0]++;
                metodosMap.get(met)[1] += u.multiply(BigDecimal.valueOf(100)).intValue();
                metodosMap.get(met)[2] += b.multiply(BigDecimal.valueOf(100)).intValue();

                String sal = p.getSala() != null && !p.getSala().isBlank() ? p.getSala().trim() : "General";
                salasMap.computeIfAbsent(sal, k -> new int[3]);
                salasMap.get(sal)[0]++;
                salasMap.get(sal)[1] += u.multiply(BigDecimal.valueOf(100)).intValue();
                salasMap.get(sal)[2] += b.multiply(BigDecimal.valueOf(100)).intValue();

                String usr = p.getUsuario() != null && !p.getUsuario().isBlank() ? p.getUsuario().trim() : "Operador";
                usuariosMap.computeIfAbsent(usr, k -> new int[2]);
                usuariosMap.get(usr)[0]++;
                usuariosMap.get(usr)[1] += u.multiply(BigDecimal.valueOf(100)).intValue();
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
        cierre.setIvaUsd(ivUsd);
        cierre.setIvaBs(ivBs);
        cierre.setResumenEfectivo(efectivoEsperadoBs, efectivoEsperadoUsd, mixtosSinDesglose);

        if (finalizados > 0) {
            cierre.setTicketPromedioUsd(totUsd.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
            cierre.setTicketPromedioBs(totBs.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP));
        }

        List<CierreCaja.ResumenMetodoPago> listaMet = new ArrayList<>();
        metodosMap.forEach((k, v) -> listaMet.add(new CierreCaja.ResumenMetodoPago(
                k, v[0],
                BigDecimal.valueOf(v[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(v[2]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))));
        cierre.setDesgloseMetodos(listaMet);

        List<CierreCaja.ResumenSala> listaSal = new ArrayList<>();
        salasMap.forEach((k, v) -> listaSal.add(new CierreCaja.ResumenSala(
                k, v[0],
                BigDecimal.valueOf(v[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(v[2]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))));
        cierre.setDesgloseSalas(listaSal);

        List<CierreCaja.ResumenUsuario> listaUsr = new ArrayList<>();
        usuariosMap.forEach((k, v) -> listaUsr.add(new CierreCaja.ResumenUsuario(
                k, v[0],
                BigDecimal.valueOf(v[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))));
        cierre.setDesgloseUsuarios(listaUsr);

        if (detalles != null) {
            int arts = 0;
            Map<String, int[]> platosMap = new HashMap<>();
            for (DetallePedido d : detalles) {
                int cant = d.getCantidad();
                arts += cant;
                BigDecimal pr = d.getPrecioDecimal() != null ? d.getPrecioDecimal() : BigDecimal.ZERO;
                int subCents = pr.multiply(BigDecimal.valueOf(cant * 100L)).intValue();
                String nom = d.getNombre() != null ? d.getNombre().trim() : "Plato";
                platosMap.computeIfAbsent(nom, k -> new int[2]);
                platosMap.get(nom)[0] += cant;
                platosMap.get(nom)[1] += subCents;
            }
            cierre.setTotalArticulos(arts);

            List<CierreCaja.PlatoVendido> top = new ArrayList<>();
            platosMap.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue()[0], e1.getValue()[0]))
                    .limit(5)
                    .forEach(e -> top.add(new CierreCaja.PlatoVendido(
                            e.getKey(), e.getValue()[0],
                            BigDecimal.valueOf(e.getValue()[1]).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))));
            cierre.setTopPlatos(top);
        }

        return cierre;
    }

    /**
     * Persiste el cierre de caja (Corte X o Z) y su arqueo en la tabla cierres_caja para auditoría.
     */
    public boolean guardarCierre(CierreCaja cierre, String rutaPdf) {
        if (cierre == null) {
            return false;
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
        if (cierre == null) {
            throw ErrorAplicacionException.validacion("El objeto de cierre de caja no puede ser nulo.");
        }
        if (cierre.getTasaCambioReferencia() == null || cierre.getTasaCambioReferencia().compareTo(BigDecimal.ZERO) <= 0) {
            throw ErrorAplicacionException.validacion("No se puede registrar el cierre de caja: la tasa de cambio de referencia es obligatoria y debe ser mayor a cero.");
        }
        try (Connection con = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, cierre.getTipo() != null ? cierre.getTipo().name() : "TOTAL");
                ps.setString(2, cierre.getFecha());
                ps.setString(3, cierre.getFechaHoraEmision());
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
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error al registrar cierre de caja en base de datos: " + ex.getMessage());
            return false;
        }
    }
}
