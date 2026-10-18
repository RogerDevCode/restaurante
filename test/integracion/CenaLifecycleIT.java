package integracion;

import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ciclo de vida de una cena en BD scratch (MySQL dedicado 127.0.0.1:3308,
 * variables DB_URL/DB_USER/DB_PASSWORD). No toca producción.
 *
 * Fases: seed 6 platos fiscales (exento/gravado/incluido x USD/VES) -&gt; ~50
 * pedidos con tasa/IVA cambiantes -&gt; cobro directo y diferido (mixto, vuelto,
 * residuo FX) -&gt; verificación de snapshots congelados y conciliación.
 */
public final class CenaLifecycleIT {

    private static int fallos = 0;
    private static int creados = 0;
    private static int pagados = 0;
    private static final Map<Integer, String> snapPagados = new HashMap<>();
    private static final Random AZAR = new Random(42);

    private static void check(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FALLO ") + msg);
        if (!cond) {
            fallos++;
        }
    }

    private static void eq(String que, BigDecimal esperado, BigDecimal real) {
        boolean ok = esperado != null && real != null && esperado.compareTo(real) == 0;
        check(ok, que + " esperado=" + esperado + " real=" + real);
    }

    private record PlatoSemilla(String nombre, String moneda, BigDecimal primario,
            boolean aplicaIva, Boolean incluyeIva) {}

    private static final List<PlatoSemilla> CARTA = List.of(
            new PlatoSemilla("E2E-CENA-AGUA", "USD", new BigDecimal("2.00"), false, false),
            new PlatoSemilla("E2E-CENA-PAN", "VES", new BigDecimal("60.00"), false, false),
            new PlatoSemilla("E2E-CENA-LOMO", "USD", new BigDecimal("10.00"), true, false),
            new PlatoSemilla("E2E-CENA-PASTA", "VES", new BigDecimal("400.00"), true, false),
            new PlatoSemilla("E2E-CENA-VINO", "USD", new BigDecimal("23.20"), true, true),
            new PlatoSemilla("E2E-CENA-CERVEZA", "VES", new BigDecimal("60.00"), true, true));

    private static BigDecimal ref(BigDecimal primarioVes, BigDecimal tasa) {
        return primarioVes.divide(tasa, 2, RoundingMode.HALF_UP);
    }

    private static int sembrar(PlatosDao platosDao, BigDecimal tasa) throws Exception {
        String hoy = LocalDate.now().toString();
        for (PlatoSemilla s : CARTA) {
            Platos p = new Platos();
            p.setNombre(s.nombre());
            p.setFecha(hoy);
            p.setActivo(true);
            p.setMonedaBase(s.moneda());
            p.setAplicaIva(s.aplicaIva());
            p.setPrecioIncluyeIva(s.incluyeIva());
            p.setPrecioClasificado(true);
            if ("USD".equals(s.moneda())) {
                p.setPrecioUsd(s.primario());
                p.setPrecioVes(s.primario().multiply(tasa).setScale(2, RoundingMode.HALF_UP));
                p.setPrecioDecimal(s.primario());
            } else {
                p.setPrecioVes(s.primario());
                p.setPrecioUsd(ref(s.primario(), tasa));
                p.setPrecioDecimal(s.primario());
            }
            if (!platosDao.registrar(p)) {
                throw new IllegalStateException("No se pudo sembrar " + s.nombre());
            }
        }
        Map<String, Platos> mapa = new HashMap<>();
        for (Platos p : platosDao.listarPorFecha("E2E-CENA-", hoy)) {
            mapa.put(p.getNombre(), p);
        }
        check(mapa.size() == CARTA.size(), "carta sembrada con 6 platos (" + mapa.size() + ")");
        return mapa.size();
    }

    private static Map<String, Platos> catalogo(PlatosDao dao) throws Exception {
        Map<String, Platos> mapa = new HashMap<>();
        for (Platos p : dao.listarPorFecha("E2E-CENA-", LocalDate.now().toString())) {
            mapa.put(p.getNombre(), p);
        }
        return mapa;
    }

    private static DetallePedido linea(Platos p, int cant) {
        DetallePedido d = new DetallePedido();
        d.setIdPlato(p.getId());
        d.setNombre(p.getNombre());
        d.setCantidad(cant);
        d.setComentario("");
        d.setPrecioDecimal("VES".equalsIgnoreCase(p.getMonedaBase()) ? p.getPrecioVes() : p.getPrecioUsd());
        d.setMonedaBase(p.getMonedaBase());
        d.setAplicaIva(p.isAplicaIva());
        d.setPrecioIncluyeIva(p.getPrecioIncluyeIva());
        d.setIvaPorcentaje(null);
        return d;
    }

    private static DetallePedido lineaEsperada(Platos p, int cant, BigDecimal ivaPct) {
        DetallePedido d = new DetallePedido();
        d.setIdPlato(p.getId());
        d.setNombre(p.getNombre());
        d.setCantidad(cant);
        d.setPrecioDecimal("VES".equalsIgnoreCase(p.getMonedaBase()) ? p.getPrecioVes() : p.getPrecioUsd());
        d.setMonedaBase(p.getMonedaBase());
        d.setAplicaIva(p.isAplicaIva());
        d.setPrecioIncluyeIva(p.getPrecioIncluyeIva());
        d.setIvaPorcentaje(p.isAplicaIva() ? ivaPct : BigDecimal.ZERO.setScale(2));
        return d;
    }

    private static int crearPedido(PedidosDao dao, int idSala, int mesa, List<DetallePedido> dets,
            BigDecimal tasa, BigDecimal iva) throws Exception {
        List<DetallePedido> norm = new ArrayList<>();
        for (DetallePedido d : dets) {
            DetallePedido c = new DetallePedido();
            c.setIdPlato(d.getIdPlato());
            c.setNombre(d.getNombre());
            c.setCantidad(d.getCantidad());
            c.setComentario(d.getComentario() != null ? d.getComentario() : "");
            c.setPrecioDecimal(d.getPrecioDecimal());
            c.setMonedaBase(d.getMonedaBase());
            c.setAplicaIva(d.getAplicaIva());
            c.setPrecioIncluyeIva(d.getPrecioIncluyeIva());
            c.setIvaPorcentaje(d.getIvaPorcentaje() != null ? d.getIvaPorcentaje()
                    : (Boolean.TRUE.equals(d.getAplicaIva()) ? iva : BigDecimal.ZERO.setScale(2)));
            norm.add(c);
        }
        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(norm, tasa);
        Pedidos p = new Pedidos();
        p.setId_sala(idSala);
        p.setNum_mesa(mesa);
        p.setTipoPedido("MESA");
        p.setUsuario("CAJA-E2E");
        p.setTasaCambio(tasa);
        p.setIvaPorcentaje(iva);
        p.setSubtotal(calc.subtotalUsd());
        p.setIvaMonto(calc.ivaUsd());
        p.setTotalDecimal(calc.totalUsd());
        p.setSubtotalBs(calc.subtotalVes());
        p.setIvaBs(calc.ivaVes());
        p.setTotalBs(calc.totalVes());
        int id = dao.registrarPedidoCompleto(p, dets);
        creados++;
        return id;
    }

    private static void verificarTotales(PedidosDao dao, int idPedido, List<DetallePedido> esperados, BigDecimal tasa) throws Exception {
        Pedidos guardado = dao.verPedido(idPedido);
        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(esperados, tasa);
        eq("pedido #" + idPedido + " subtotal USD", calc.subtotalUsd(), guardado.getSubtotal());
        eq("pedido #" + idPedido + " iva USD", calc.ivaUsd(), guardado.getIvaMonto());
        eq("pedido #" + idPedido + " total USD", calc.totalUsd(), guardado.getTotalDecimal());
        eq("pedido #" + idPedido + " total Bs", calc.totalVes(), guardado.getTotalBs());
        eq("pedido #" + idPedido + " tasa snapshot", tasa.setScale(4, RoundingMode.HALF_UP),
                guardado.getTasaCambio().setScale(4, RoundingMode.HALF_UP));
    }
    /** Pago exacto mixto VES+USD sin residuo (USD recortado para conversión exacta). */
    private static List<PagoPedido> pagoExactoMixto(BigDecimal totalBs, BigDecimal tasa) {
        List<PagoPedido> pagos = new ArrayList<>();
        BigDecimal resto = totalBs;
        BigDecimal tramoVes = totalBs.multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP);
        if (tramoVes.signum() > 0 && tramoVes.compareTo(resto) < 0) {
            pagos.add(new PagoPedido("EFECTIVO_BS", "VES", tramoVes, tramoVes, tramoVes, BigDecimal.ZERO));
            resto = resto.subtract(tramoVes);
        }
        BigDecimal usd = resto.divide(tasa, 2, RoundingMode.DOWN);
        BigDecimal aplicadoUsdBs = usd.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vesFinal = resto.subtract(aplicadoUsdBs);
        if (usd.signum() > 0) {
            pagos.add(new PagoPedido("EFECTIVO_USD", "USD", usd, usd, usd, BigDecimal.ZERO));
        }
        if (vesFinal.signum() > 0) {
            pagos.add(new PagoPedido("EFECTIVO_BS", "VES", vesFinal, vesFinal, vesFinal, BigDecimal.ZERO));
        }
        return pagos;
    }

    private static List<PagoPedido> pagoVuelto(BigDecimal totalBs) {
        BigDecimal extra = new BigDecimal("20.00");
        return List.of(new PagoPedido("EFECTIVO_BS", "VES", totalBs, totalBs.add(extra), totalBs, extra));
    }

    private static String snapshotCabecera(Connection con, int id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT total, total_bs, tasa_cambio, estado, cerrada_en, version_precio FROM pedidos WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1) + "|" + rs.getBigDecimal(2) + "|" + rs.getBigDecimal(3)
                        + "|" + rs.getString(4) + "|" + rs.getTimestamp(5) + "|" + rs.getInt(6);
            }
        }
    }

    private static String snapshotDetalles(Connection con, int id) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id_plato, nombre, precio, cantidad, moneda_base, aplica_iva, precio_incluye_iva, iva_porcentaje, precio_congelado FROM detalle_pedidos WHERE id_pedido = ? ORDER BY id")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append(",").append(rs.getString(2)).append(",")
                            .append(rs.getBigDecimal(3)).append(",").append(rs.getInt(4)).append(",")
                            .append(rs.getString(5)).append(",").append(rs.getString(6)).append(",")
                            .append(rs.getString(7)).append(",").append(rs.getBigDecimal(8)).append(",")
                            .append(rs.getString(9)).append(";");
                }
            }
        }
        return sb.toString();
    }

    private static BigDecimal[] conciliacion(Connection con, int id) throws Exception {
        BigDecimal aplicado;
        BigDecimal ajuste;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(COALESCE(monto_bs, monto_aplicado)), 0) FROM pedido_pagos WHERE id_pedido = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                aplicado = rs.getBigDecimal(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(ajuste_cambio_bs), 0) FROM pedido_liquidaciones WHERE id_pedido = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                ajuste = rs.getBigDecimal(1);
            }
        }
        return new BigDecimal[]{aplicado, ajuste};
    }
    private static List<DetallePedido> armarLineas(Map<String, Platos> carta, int nLineas) {
        List<PlatoSemilla> tipos = new ArrayList<>(CARTA);
        List<DetallePedido> dets = new ArrayList<>();
        Map<String, Platos> porNombre = new HashMap<>(carta);
        for (int i = 0; i < nLineas; i++) {
            PlatoSemilla s = tipos.get(AZAR.nextInt(tipos.size()));
            dets.add(linea(porNombre.get(s.nombre()), 1 + AZAR.nextInt(3)));
        }
        return dets;
    }

    private static List<DetallePedido> esperadosDe(Map<String, Platos> carta, List<DetallePedido> creados, BigDecimal iva) {
        List<DetallePedido> exp = new ArrayList<>();
        for (DetallePedido d : creados) {
            Platos p = null;
            for (Platos c : carta.values()) {
                if (c.getId() == d.getIdPlato()) {
                    p = c;
                    break;
                }
            }
            if (p == null) {
                throw new IllegalStateException("Plato fuera de carta: " + d.getIdPlato());
            }
            exp.add(lineaEsperada(p, d.getCantidad(), iva));
        }
        return exp;
    }

    private static void pagar(PedidosDao dao, int id, List<PagoPedido> pagos, BigDecimal tasa, boolean esVuelto) throws Exception {
        Pedidos p = dao.verPedido(id);
        // El pago liquida a TASA CONGELADA del pedido (no a la vigente): mesas cerradas
        // antes de un cambio de tasa pagan con su tasa de cierre.
        BigDecimal tasaPago = (p.getTasaCambio() != null && p.getTasaCambio().signum() > 0) ? p.getTasaCambio() : tasa;
        List<PagoPedido> tramos = pagos != null ? pagos : pagoExactoMixto(p.getTotalBs(), tasaPago);
        String metodo = tramos.size() == 1 ? tramos.get(0).metodo() : "MIXTO";
        check(dao.actualizarEstadoConCliente(id, "Consumidor Final", "V-00000000", metodo, tramos),
                "pedido #" + id + " finalizado (" + metodo + ")");
        pagados++;
        try (Connection con = new ProveedorConexionJdbc().getConnection()) {
            snapPagados.put(id, snapshotCabecera(con, id) + snapshotDetalles(con, id));
        }
        if (esVuelto) {
            ResumenConciliacionPedido r = dao.resumenConciliacion(id);
            check(r.excedenteBs().compareTo(BigDecimal.ZERO) == 0,
                    "pedido #" + id + " sin excedente tras vuelto registrado");
        }
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.contains("restaurante_cena")) {
            System.out.println("Seguridad: exige DB_URL a restaurante_cena (scratch). Nada que hacer.");
            System.exit(2);
        }
        ProveedorConexionJdbc prov = new ProveedorConexionJdbc();
        PedidosDao pedidosDao = new PedidosDao(prov);
        PlatosDao platosDao = new PlatosDao(prov);
        SalasDao salasDao = new SalasDao(prov);
        ActualizacionTasaDao tasaDao = new ActualizacionTasaDao(prov);
        Usuario admin = new Usuario(1, "E2E", "e2e", "x", "Administrador");
        PlatosServicio platosServicio = new PlatosServicio(platosDao, new PoliticaAcceso(admin));
        Path pdfDir = Files.createTempDirectory("cena-facturas");
        GeneradorPdfPedido pdfGen = new GeneradorPdfPedido(pdfDir);
        Config cfgPdf = new Config();
        cfgPdf.setRuc("J-00000000");
        cfgPdf.setNombre("E2E-CENA");

        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO config (tasa_dolar, iva_porcentaje, nombre) VALUES (40.0000, 16.00, 'E2E-CENA')")) {
            try {
                ps.executeUpdate();
            } catch (Exception dup) {
                System.out.println("config ya existía, se continúa");
            }
        }
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("40.0000"), "E2E");
        BigDecimal tasa = new BigDecimal("40.0000");
        BigDecimal iva = new BigDecimal("16.00");

        Salas sala = new Salas();
        sala.setNombre("E2E-CENA");
        sala.setMesas(6);
        sala.setTipo("SALON");
        check(salasDao.registrar(sala), "sala E2E-CENA creada");
        int idSala;
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id FROM salas WHERE nombre = 'E2E-CENA'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idSala = rs.getInt(1);
            }
        }

        sembrar(platosDao, tasa);
        Map<String, Platos> carta = catalogo(platosDao);

        Map<Integer, Integer> mesaAbierta = new HashMap<>();
        List<Integer> pagadosIds = new ArrayList<>();
        List<Integer> abiertosIds = new ArrayList<>();

        java.util.function.Function<Integer, Integer> mesaLibrePara = (seq) -> {
            for (int m = 1; m <= 6; m++) {
                int candidata = ((seq + m) % 6) + 1;
                if (!mesaAbierta.containsKey(candidata)) {
                    return candidata;
                }
            }
            return -1;
        };

        // ---------- FASE A: 16 pedidos a tasa 40 / IVA 16 ----------
        System.out.println("--- FASE A (tasa 40, IVA 16): 16 pedidos ---");
        for (int s = 0; s < 16; s++) {
            int mesa = mesaLibrePara.apply(s);
            if (mesa < 0) {
                int victimaMesa = mesaAbierta.keySet().iterator().next();
                int victima = mesaAbierta.remove(victimaMesa);
                abiertosIds.remove((Integer) victima);
                pagar(pedidosDao, victima, null, tasa, false);
                pagadosIds.add(victima);
                mesa = mesaLibrePara.apply(s);
            }
            List<DetallePedido> dets = armarLineas(carta, 1 + AZAR.nextInt(4));
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            verificarTotales(pedidosDao, id, esperadosDe(carta, dets, iva), tasa);
            if (s % 3 != 2) {
                pagar(pedidosDao, id, null, tasa, false);
                pagadosIds.add(id);
                mesaAbierta.remove(mesa);
            } else {
                mesaAbierta.put(mesa, id);
                abiertosIds.add(id);
            }
        }
        // Caso vuelto exacto + Bs. 20 de más con devolución.
        {
            List<DetallePedido> dets = armarLineas(carta, 2);
            int mesa = mesaLibrePara.apply(100);
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            verificarTotales(pedidosDao, id, esperadosDe(carta, dets, iva), tasa);
            Pedidos p = pedidosDao.verPedido(id);
            pagar(pedidosDao, id, pagoVuelto(p.getTotalBs()), tasa, true);
            pagadosIds.add(id);
        }
        // Caso residuo FX +0.03 VES (pago corto en VES + USD 1.50 exactos).
        {
            List<DetallePedido> dets = List.of(linea(carta.get("E2E-CENA-PASTA"), 2));
            int mesa = mesaLibrePara.apply(101);
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            Pedidos p = pedidosDao.verPedido(id);
            BigDecimal restoVes = p.getTotalBs().subtract(new BigDecimal("60.03"));
            List<PagoPedido> pagos = new ArrayList<>();
            pagos.add(new PagoPedido("EFECTIVO_BS", "VES", restoVes, restoVes, restoVes, BigDecimal.ZERO));
            pagos.add(new PagoPedido("EFECTIVO_USD", "USD", new BigDecimal("1.50"), new BigDecimal("1.50"),
                    new BigDecimal("1.50"), BigDecimal.ZERO));
            pagar(pedidosDao, id, pagos, tasa, false);
            pagadosIds.add(id);
            try (Connection con = prov.getConnection()) {
                BigDecimal[] conc = conciliacion(con, id);
                Pedidos cerrado = pedidosDao.verPedido(id);
                eq("residuo FX pedido #" + id, new BigDecimal("0.03"), conc[1].setScale(2, RoundingMode.HALF_UP));
                check(conc[0].add(conc[1]).compareTo(cerrado.getTotalBs()) == 0,
                        "aplicado+ajuste == total congelado pedido #" + id);
            }
        }
        System.out.println("FASE A: creados=" + creados + " pagados=" + pagadosIds.size());

        // ---------- TASA 40 -> 50 ----------
        System.out.println("--- TASA 40 -> 50 ---");
        Map<Integer, BigDecimal> versionAntes = new HashMap<>();
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id, version_precio FROM pedidos WHERE estado = 'PENDIENTE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versionAntes.put(rs.getInt(1), rs.getBigDecimal(2));
                }
            }
        }
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("50.0000"), "E2E");
        tasa = new BigDecimal("50.0000");
        carta = catalogo(platosDao);
        try (Connection con = prov.getConnection()) {
            for (int id : pagadosIds) {
                check(snapPagados.get(id).equals(snapshotCabecera(con, id) + snapshotDetalles(con, id)),
                        "cerrado #" + id + " intacto tras cambio de tasa");
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT id, version_precio FROM pedidos WHERE estado = 'PENDIENTE'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt(1);
                        check(rs.getBigDecimal(2).compareTo(versionAntes.get(id)) > 0,
                                "abierto #" + id + " incrementó versión");
                    }
                }
            }
        }
        for (int id : new ArrayList<>(abiertosIds)) {
            Pedidos p = pedidosDao.verPedido(id);
            List<DetallePedido> dets = pedidosDao.verPedidoDetalle(id);
            List<DetallePedido> exp = new ArrayList<>();
            for (DetallePedido d : dets) {
                Platos c = null;
                for (Platos x : carta.values()) {
                    if (Integer.valueOf(x.getId()).equals(d.getIdPlato())) {
                        c = x;
                        break;
                    }
                }
                exp.add(lineaEsperada(c, d.getCantidad(), iva));
            }
            verificarTotales(pedidosDao, id, exp, tasa);
        }

        // ---------- FASE B: 12 pedidos a tasa 50 ----------
        System.out.println("--- FASE B (tasa 50): 12 pedidos ---");
        for (int s = 0; s < 12; s++) {
            int mesa = mesaLibrePara.apply(50 + s);
            if (mesa < 0) {
                int victimaMesa = mesaAbierta.keySet().iterator().next();
                int victima = mesaAbierta.remove(victimaMesa);
                abiertosIds.remove((Integer) victima);
                pagar(pedidosDao, victima, null, tasa, false);
                pagadosIds.add(victima);
                mesa = mesaLibrePara.apply(50 + s);
            }
            List<DetallePedido> dets = armarLineas(carta, 1 + AZAR.nextInt(3));
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            verificarTotales(pedidosDao, id, esperadosDe(carta, dets, iva), tasa);
            if (s % 3 != 2) {
                pagar(pedidosDao, id, null, tasa, false);
                pagadosIds.add(id);
                mesaAbierta.remove(mesa);
            } else {
                mesaAbierta.put(mesa, id);
                abiertosIds.add(id);
            }
        }

        // ---------- Editar catálogo: LOMO 10 -> 12 USD ----------
        System.out.println("--- Editar LOMO 10 -> 12 USD ---");
        platosServicio.modificarProductoYRecalcularPendientes(
                modPlato(carta, "E2E-CENA-LOMO", new BigDecimal("12.00"), null, tasa), tasa, "E2E");
        carta = catalogo(platosDao);
        check(carta.get("E2E-CENA-LOMO").getPrecioUsd().compareTo(new BigDecimal("12.00")) == 0,
                "catálogo LOMO actualizado a 12.00");
        eq("referencia VES de LOMO", new BigDecimal("600.00"), carta.get("E2E-CENA-LOMO").getPrecioVes());
        try (Connection con = prov.getConnection()) {
            for (int id : pagadosIds) {
                Pedidos p = pedidosDao.verPedido(id);
                if (p.getTotalBs() != null) {
                    check(snapPagados.get(id).equals(snapshotCabecera(con, id) + snapshotDetalles(con, id)),
                            "cerrado #" + id + " intacto tras edición de catálogo");
                }
            }
        }

        // ---------- IVA 16 -> 10 ----------
        System.out.println("--- IVA 16 -> 10 ---");
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE config SET iva_porcentaje = 10.00 WHERE id = 1")) {
            check(ps.executeUpdate() == 1, "IVA global a 10.00");
        }
        iva = new BigDecimal("10.00");

        // ---------- FASE C: 12 pedidos a IVA 10 ----------
        System.out.println("--- FASE C (IVA 10): 12 pedidos ---");
        for (int s = 0; s < 12; s++) {
            int mesa = mesaLibrePara.apply(100 + s);
            if (mesa < 0) {
                int victimaMesa = mesaAbierta.keySet().iterator().next();
                int victima = mesaAbierta.remove(victimaMesa);
                abiertosIds.remove((Integer) victima);
                pagar(pedidosDao, victima, null, tasa, false);
                pagadosIds.add(victima);
                mesa = mesaLibrePara.apply(100 + s);
            }
            List<DetallePedido> dets = armarLineas(carta, 1 + AZAR.nextInt(3));
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            verificarTotales(pedidosDao, id, esperadosDe(carta, dets, iva), tasa);
            if (s % 4 != 3) {
                pagar(pedidosDao, id, null, tasa, false);
                pagadosIds.add(id);
                mesaAbierta.remove(mesa);
            } else {
                mesaAbierta.put(mesa, id);
                abiertosIds.add(id);
            }
        }
        // ---------- Cierre diferido de 8 mesas ----------
        System.out.println("--- Cierre de 8 mesas (pago posterior) ---");
        List<Integer> porCerrar = new ArrayList<>(abiertosIds.subList(0, Math.min(8, abiertosIds.size())));
        Map<Integer, String> snapCierre = new HashMap<>();
        try (Connection con = prov.getConnection()) {
            for (int id : porCerrar) {
                Pedidos p = pedidosDao.verPedido(id);
                check(pedidosDao.cerrarMesa(id, p.getVersionPrecio()), "mesa cerrada pedido #" + id);
                snapCierre.put(id, snapshotCabecera(con, id) + snapshotDetalles(con, id));
            }
        }
        abiertosIds.removeAll(porCerrar);
        for (int id : porCerrar) {
            mesaAbierta.values().removeIf(v -> v == id);
        }

        // ---------- TASA 50 -> 36.5 + editar PASTA ----------
        System.out.println("--- TASA 50 -> 36.5 + PASTA 400 -> 440 ---");
        tasaDao.actualizarTasaYRecalcularPendientes(new BigDecimal("36.5000"), "E2E");
        tasa = new BigDecimal("36.5000");
        // (la edición vía servicio propaga solo a abiertas; se valida abajo por invariancia)
        platosServicio.modificarProductoYRecalcularPendientes(
                modPlato(carta, "E2E-CENA-PASTA", null, new BigDecimal("440.00"), tasa), tasa, "E2E");
        carta = catalogo(platosDao);
        eq("referencia USD de PASTA", new BigDecimal("12.05"), carta.get("E2E-CENA-PASTA").getPrecioUsd());
        try (Connection con = prov.getConnection()) {
            for (Map.Entry<Integer, String> e : snapCierre.entrySet()) {
                check(e.getValue().equals(snapshotCabecera(con, e.getKey()) + snapshotDetalles(con, e.getKey())),
                        "cerrada #" + e.getKey() + " congelada ante tasa+catálogo");
            }
            for (int id : pagadosIds) {
                check(snapPagados.get(id).equals(snapshotCabecera(con, id) + snapshotDetalles(con, id)),
                        "finalizado #" + id + " intacto al final");
            }
        }

        // ---------- Pagar las 8 cerradas (mixto, vuelto, USD) ----------
        System.out.println("--- Pago diferido de cerradas ---");
        int k = 0;
        for (int id : porCerrar) {
            Pedidos p = pedidosDao.verPedido(id);
            if (k % 3 == 2) {
                pagar(pedidosDao, id, pagoVuelto(p.getTotalBs()), tasa, true);
            } else {
                pagar(pedidosDao, id, null, tasa, false);
            }
            k++;
            pagadosIds.add(id);
            try (Connection con = prov.getConnection()) {
                BigDecimal[] conc = conciliacion(con, id);
                Pedidos fin = pedidosDao.verPedido(id);
                check(conc[0].add(conc[1]).compareTo(fin.getTotalBs()) == 0,
                        "conciliación exacta pedido #" + id);
                ResumenConciliacionPedido r = pedidosDao.resumenConciliacion(id);
                check(r.cerrado(), "resumen marca cerrada pedido #" + id);
            }
        }

        // ---------- Cerrar+pagar las 4 abiertas restantes ----------
        for (int id : new ArrayList<>(abiertosIds)) {
            Pedidos p = pedidosDao.verPedido(id);
            pedidosDao.cerrarMesa(id, p.getVersionPrecio());
            pagar(pedidosDao, id, null, tasa, false);
            pagadosIds.add(id);
            mesaAbierta.values().removeIf(v -> v == id);
        }
        abiertosIds.clear();

        // ---------- FASE D: 10 pedidos directos ----------
        System.out.println("--- FASE D (tasa 36.5, IVA 10): 10 pedidos ---");
        for (int s = 0; s < 10; s++) {
            int mesa = mesaLibrePara.apply(200 + s);
            List<DetallePedido> dets = armarLineas(carta, 1 + AZAR.nextInt(3));
            int id = crearPedido(pedidosDao, idSala, mesa, dets, tasa, iva);
            verificarTotales(pedidosDao, id, esperadosDe(carta, dets, iva), tasa);
            pagar(pedidosDao, id, null, tasa, false);
            pagadosIds.add(id);
        }

        // ---------- Facturas PDF ----------
        System.out.println("--- Facturas PDF ---");
        cfgPdf.setTasaDolar(tasa);
        cfgPdf.setIvaPorcentaje(iva);
        int pdfs = 0;
        for (int id : List.of(pagadosIds.get(0), pagadosIds.get(pagadosIds.size() / 2),
                pagadosIds.get(pagadosIds.size() - 1))) {
            Path pdf = pdfGen.generar(pedidosDao.verPedido(id), cfgPdf, pedidosDao.verPedidoDetalle(id));
            check(Files.exists(pdf) && Files.size(pdf) > 0, "factura PDF pedido #" + id + " (" + pdf.getFileName() + ")");
            pdfs++;
        }

        // ---------- Reporte ----------
        BigDecimal ventaBs = BigDecimal.ZERO;
        BigDecimal ventaUsd = BigDecimal.ZERO;
        BigDecimal aplicadoBs = BigDecimal.ZERO;
        BigDecimal ajustesFx = BigDecimal.ZERO;
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(total_bs),0), COALESCE(SUM(total),0) FROM pedidos WHERE estado = 'FINALIZADO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            ventaBs = rs.getBigDecimal(1);
            ventaUsd = rs.getBigDecimal(2);
        }
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(COALESCE(monto_bs, monto_aplicado)),0) FROM pedido_pagos");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            aplicadoBs = rs.getBigDecimal(1);
        }
        try (Connection con = prov.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(ajuste_cambio_bs),0) FROM pedido_liquidaciones");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            ajustesFx = rs.getBigDecimal(1);
        }
        System.out.println("=================================================");
        System.out.println("CENA: pedidos=" + creados + " finalizados=" + pagadosIds.size()
                + " pdfs=" + pdfs);
        System.out.println("CENA: venta Bs=" + ventaBs + " USD=" + ventaUsd
                + " aplicado Bs=" + aplicadoBs + " ajustesFX Bs=" + ajustesFx);
        check(creados == 52, "52 pedidos creados (16+12+12+10+2 casos)");
        check(pagadosIds.size() == 52, "52 pedidos finalizados");
        check(ventaBs.compareTo(aplicadoBs.add(ajustesFx)) == 0, "caja cuadra: venta == aplicado + ajustes");
        check(pagados == 52, "contador de pagos == 52");
        System.out.println(fallos == 0 ? "CENA: TODO OK" : "CENA: " + fallos + " FALLOS");
        if (fallos > 0) {
            System.exit(1);
        }
    }

    private static Platos modPlato(Map<String, Platos> carta, String nombre, BigDecimal usd, BigDecimal ves, BigDecimal tasa) {
        Platos p = carta.get(nombre);
        Platos m = new Platos();
        m.setId(p.getId());
        m.setNombre(p.getNombre());
        m.setFecha(p.getFecha() != null ? p.getFecha() : LocalDate.now().toString());
        m.setActivo(true);
        m.setMonedaBase(p.getMonedaBase());
        m.setAplicaIva(p.isAplicaIva());
        m.setPrecioIncluyeIva(p.getPrecioIncluyeIva());
        m.setPrecioClasificado(true);
        if ("VES".equalsIgnoreCase(p.getMonedaBase())) {
            BigDecimal v = ves != null ? ves : p.getPrecioVes();
            m.setPrecioVes(v);
            m.setPrecioUsd(v.divide(tasa, 2, RoundingMode.HALF_UP));
            m.setPrecioDecimal(v);
        } else {
            BigDecimal u = usd != null ? usd : p.getPrecioUsd();
            m.setPrecioUsd(u);
            m.setPrecioVes(u.multiply(tasa).setScale(2, RoundingMode.HALF_UP));
            m.setPrecioDecimal(u);
        }
        return m;
    }
}