package aceptacion;

import Modelo.*;
import Servicio.*;
import infraestructura.ProveedorConexionJdbc;
import infraestructura.MigradorEsquemaJdbc;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * SUITE DE ACEPTACIÓN ADVERSARIAL — RED TEAM (FASE 0)
 *
 * Contratos de prueba para los fallos F-01 a F-12 reportados en fallos.md.
 * Criterio estricto: Todas las pruebas deben FALLAR sobre el código actual
 * sin corregir, y pasarán ÚNICAMENTE cuando la causa raíz de cada fallo
 * sea resuelta según las especificaciones del plan.
 */
public class AceptacionFallosRedTeamTest {

    // =========================================================================
    // F-01: Migrador no aborta en arranque si detalle_pedidos.aplica_iva tiene NULL
    // =========================================================================
    @Test
    public void testF01_MigracionNoAbortaConDetalleAplicaIvaNull() throws Exception {
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        try (Connection con = proveedor.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS test_f01_detalles ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "nombre VARCHAR(100), "
                        + "aplica_iva TINYINT(1) NULL)");
                st.executeUpdate("DELETE FROM test_f01_detalles");
                st.executeUpdate("INSERT INTO test_f01_detalles (nombre, aplica_iva) VALUES ('Plato Historico', NULL)");
            }

            Method m = MigradorEsquemaJdbc.class.getDeclaredMethod("asegurarColumnaNoNula",
                    Connection.class, String.class, String.class, String.class);
            m.setAccessible(true);

            try {
                m.invoke(null, con, "test_f01_detalles", "aplica_iva", "TINYINT(1) NOT NULL DEFAULT 1");
            } catch (java.lang.reflect.InvocationTargetException ite) {
                if (ite.getCause() instanceof SQLException) {
                    fail("F-01 FALLA: La migración abortó con SQLException al encontrar registros con NULL: "
                            + ite.getCause().getMessage());
                }
                throw ite;
            } finally {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate("DROP TABLE IF EXISTS test_f01_detalles");
                }
            }
        }
    }

    // =========================================================================
    // F-02: Auto-congelado no deja campos fiscales NULL en BD ni produce NPE
    // =========================================================================
    @Test
    public void testF02_AutoCongeladoNoDejaCamposFiscalesNullNiLanzaNpe() throws Exception {
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        PreciosPedidosDao dao = new PreciosPedidosDao(proveedor);

        int idPedido = -1;
        try (Connection con = proveedor.getConnection()) {
            // Crear pedido abierto
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                         metodo_pago, estado, fecha, tasa_cambio, total_bs)
                    VALUES (1, 99, 10.00, 16.00, 1.60, 11.60, 'ADMIN', 'EFECTIVO', 'PENDIENTE', CURRENT_TIMESTAMP, 36.5000, 423.40)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idPedido = rs.getInt(1);
                }
            }

            // Crear plato a modificar
            Platos plato = new Platos(0, "Plato Test Modificable", new BigDecimal("12.00"), LocalDate.now().toString());
            plato.setMonedaBase("USD");
            plato.setAplicaIva(true);
            plato.setPrecioIncluyeIva(false);
            PlatosDao pDao = new PlatosDao();
            pDao.registrar(plato);
            int idPlato = pDao.buscarActivoPorNombre("Plato Test Modificable").get().getId();
            plato.setId(idPlato);

            // Insertar detalle 1 enlazado al plato (hace que el pedido sea seleccionado para recálculo)
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO detalle_pedidos (id_pedido, id_plato, nombre, precio, cantidad, comentario, aplica_iva,
                                                 moneda_base, precio_incluye_iva, precio_congelado)
                    VALUES (?, ?, 'Plato Test Modificable', 10.00, 1, '', 1, 'USD', 0, 0)
                    """)) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idPlato);
                ps.executeUpdate();
            }

            // Insertar detalle 2 huérfano con nombre que ya no existe en el catálogo
            int idDetalle = -1;
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO detalle_pedidos (id_pedido, id_plato, nombre, precio, cantidad, comentario, aplica_iva,
                                                 moneda_base, precio_incluye_iva, precio_congelado)
                    VALUES (?, NULL, 'Plato Legado Desclasificado', 10.00, 1, '', 1, NULL, NULL, 0)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idDetalle = rs.getInt(1);
                }
            }

            try {
                // Invariante F-02: Recalcular pedido debe manejar ítems legados sin bloquear el catálogo ni dejar columnas fiscales huérfanas
                Modelo.ResultadoActualizacionPrecios res = dao.modificarProductoYRecalcularPendientes(plato, "RED_TEAM");
                assertNotNull("F-02: El resultado de actualización no debe ser nulo", res);
            } finally {
                pDao.eliminar(idPlato);
            }

            // Verificar si el detalle en BD quedó con moneda_base NULL a pesar de haber sido auto-congelado
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT precio_congelado, moneda_base, precio_incluye_iva FROM detalle_pedidos WHERE id = ?")) {
                ps.setInt(1, idDetalle);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int congelado = rs.getInt("precio_congelado");
                    String mb = rs.getString("moneda_base");
                    System.out.println("DEBUG F02: congelado=" + congelado + ", mb=" + mb);
                    if (congelado == 1 && mb == null) {
                        fail("F-02 FALLA: Auto-congelado fantasma; se congeló la línea pero se dejó moneda_base = NULL en BD");
                    }
                }
            }
        } finally {
            if (idPedido > 0) {
                try (Connection con = proveedor.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM detalle_pedidos WHERE id_pedido = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    // =========================================================================
    // F-03: Cierre Z desglosa métodos de pago mixto desde pedido_pagos
    // =========================================================================
    @Test
    public void testF03_CierreZDesglosaMetodosDePagoMixtoDesdePedidoPagos() throws Exception {
        CierreCajaDao dao = new CierreCajaDao();
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

        int idPedido = -1;
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                         metodo_pago, estado, fecha, tasa_cambio, total_bs)
                    VALUES (1, 99, 100.00, 0.00, 0.00, 100.00, 'ADMIN', 'MIXTO', 'FINALIZADO', CURRENT_TIMESTAMP, 36.5000, 3650.00)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idPedido = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, monto_recibido, monto_aplicado, monto_usd, monto_bs, tasa_cambio)
                    VALUES (?, 'EFECTIVO_USD', 'USD', 60.00, 60.00, 60.00, 60.00, 2190.00, 36.5000),
                           (?, 'TARJETA', 'USD', 40.00, 40.00, 40.00, 40.00, 1460.00, 36.5000)
                    """)) {
                ps.setInt(1, idPedido);
                ps.setInt(2, idPedido);
                ps.executeUpdate();
            }

            Config cfg = new Config();
            cfg.setTasaDolar(new BigDecimal("36.5000"));
            CierreCaja cierre = dao.consultarCierre(LocalDate.now().toString(), CierreCaja.TipoCierre.TOTAL, "ADMIN", cfg);

            List<CierreCaja.ResumenMetodoPago> desglose = cierre.getDesgloseMetodos();
            assertNotNull("Los métodos de pago no deben ser nulos", desglose);

            Map<String, BigDecimal> metodos = new HashMap<>();
            for (CierreCaja.ResumenMetodoPago rm : desglose) {
                metodos.put(rm.getMetodo(), rm.getMontoUsd());
            }

            assertFalse("F-03 FALLA: El reporte agrupa por pedidos.metodo_pago ('MIXTO') ignorando pedido_pagos",
                    metodos.containsKey("MIXTO"));
            assertTrue("F-03 FALLA: Falta desglose de EFECTIVO_USD en métodos de pago", metodos.containsKey("EFECTIVO_USD"));
            assertTrue("F-03 FALLA: Falta desglose de TARJETA en métodos de pago", metodos.containsKey("TARJETA"));
            assertEquals(new BigDecimal("60.00"), metodos.get("EFECTIVO_USD"));
            assertEquals(new BigDecimal("40.00"), metodos.get("TARJETA"));
        } finally {
            if (idPedido > 0) {
                try (Connection con = proveedor.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_pagos WHERE id_pedido = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    // =========================================================================
    // F-04: Cierre Z: Subtotal e IVA en Bs no deben ser 0 en pedidos legados
    // =========================================================================
    @Test
    public void testF04_CierreZSubtotalBsEivaBsNoSonCeroEnPedidosLegados() throws Exception {
        CierreCajaDao dao = new CierreCajaDao();
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

        int idPedido = -1;
        try (Connection con = proveedor.getConnection()) {
            // Pedido legado con subtotal_bs = 0 e iva_bs = 0 pero total_bs = 4234.00
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                         metodo_pago, estado, fecha, tasa_cambio, subtotal_bs, iva_bs, total_bs)
                    VALUES (1, 97, 100.00, 16.00, 16.00, 116.00, 'ADMIN', 'EFECTIVO', 'FINALIZADO', CURRENT_TIMESTAMP, 36.5000, 0.00, 0.00, 4234.00)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idPedido = rs.getInt(1);
                }
            }

            Config cfg = new Config();
            cfg.setTasaDolar(new BigDecimal("36.5000"));
            CierreCaja cierre = dao.consultarCierre(LocalDate.now().toString(), CierreCaja.TipoCierre.TOTAL, "ADMIN", cfg);

            assertNotNull(cierre.getSubtotalBs());
            assertNotNull(cierre.getIvaBs());
            assertTrue("F-04 FALLA: subtotalBs es 0 en pedido legado a pesar de tener subtotal USD y tasa",
                    cierre.getSubtotalBs().compareTo(BigDecimal.ZERO) > 0);
            assertTrue("F-04 FALLA: ivaBs es 0 en pedido legado a pesar de tener IVA USD y tasa",
                    cierre.getIvaBs().compareTo(BigDecimal.ZERO) > 0);

            BigDecimal sumaBs = cierre.getSubtotalBs().add(cierre.getIvaBs()).setScale(2, RoundingMode.HALF_UP);
            assertEquals("F-04 FALLA: subtotal_bs + iva_bs no cuadra con total_bs",
                    cierre.getTotalVentasBs().setScale(2, RoundingMode.HALF_UP), sumaBs);
        } finally {
            if (idPedido > 0) {
                try (Connection con = proveedor.getConnection();
                     PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                    ps.setInt(1, idPedido);
                    ps.executeUpdate();
                }
            }
        }
    }

    // =========================================================================
    // F-05: GeneradorPdf rechaza emisión sin tasa configurada y no usa '36.5'
    // =========================================================================
    @Test
    public void testF05_GeneradorPdfRechazaEmisionSinTasaConfiguradaYNoUsa36_5() throws IOException {
        Path srcDir = Path.of("src");
        List<String> coincidencias = new ArrayList<>();
        try (var stream = Files.walk(srcDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          String contenido = Files.readString(p);
                          if (contenido.contains("\"36.5") || contenido.contains("36.5000")) {
                              coincidencias.add(p.toString());
                          }
                      } catch (Exception e) {}
                  });
        }
        assertTrue("F-05 FALLA: Se encontró fallback hardcodeado '36.5' en src/: " + coincidencias,
                coincidencias.isEmpty());

        GeneradorPdfPedido generador = new GeneradorPdfPedido(Path.of("/tmp"));
        Pedidos pedido = new Pedidos();
        pedido.setId(1);
        pedido.setTotalDecimal(new BigDecimal("10.00"));
        pedido.setSubtotal(new BigDecimal("10.00"));
        pedido.setTasaCambio(null);

        Config configSinTasa = new Config();
        configSinTasa.setTasaDolar(null);
        configSinTasa.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido det = new DetallePedido();
        det.setNombre("Plato");
        det.setPrecioDecimal(new BigDecimal("10.00"));
        det.setCantidad(1);
        det.setMonedaBase("USD");
        det.setAplicaIva(false);
        det.setPrecioIncluyeIva(false);

        assertThrows("F-05 FALLA: Debe lanzar ErrorAplicacionException si no hay tasa configurada",
                ErrorAplicacionException.class,
                () -> generador.generar(pedido, configSinTasa, List.of(det)));
    }

    // =========================================================================
    // F-06: GeneradorPdf rechaza diferencia de 0.02 entre líneas y cabecera
    // =========================================================================
    @Test
    public void testF06_GeneradorPdfRechazaDiferenciaDeDosCentavos() {
        GeneradorPdfPedido generador = new GeneradorPdfPedido(Path.of("/tmp"));

        Pedidos pedido = new Pedidos();
        pedido.setId(2);
        pedido.setSubtotal(new BigDecimal("10.02"));
        pedido.setTotalDecimal(new BigDecimal("10.02"));
        pedido.setTasaCambio(new BigDecimal("50.0000"));

        Config config = new Config();
        config.setTasaDolar(new BigDecimal("50.0000"));
        config.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido d1 = new DetallePedido();
        d1.setNombre("Item 1");
        d1.setPrecioDecimal(new BigDecimal("5.00"));
        d1.setCantidad(1);
        d1.setMonedaBase("USD");
        d1.setAplicaIva(false);
        d1.setPrecioIncluyeIva(null); // Entra a la rama legacy con tolerancia laxa 0.05

        DetallePedido d2 = new DetallePedido();
        d2.setNombre("Item 2");
        d2.setPrecioDecimal(new BigDecimal("5.00"));
        d2.setCantidad(1);
        d2.setMonedaBase("USD");
        d2.setAplicaIva(false);
        d2.setPrecioIncluyeIva(false);

        assertThrows("F-06 FALLA: Discrepancia de 0.02 fue aceptada por tolerancia laxa (0.05)",
                ErrorAplicacionException.class,
                () -> generador.generar(pedido, config, List.of(d1, d2)));
    }

    // =========================================================================
    // F-07: Anulación con auditoría exige movimiento equivalente, no basta 0.01
    // =========================================================================
    @Test
    public void testF07_AnulacionConAuditoriaExigeMovimientoEquivalente() throws Exception {
        PedidosDao dao = new PedidosDao();
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

        int idPedido = -1;
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                         metodo_pago, estado, fecha, tasa_cambio, total_bs)
                    VALUES (1, 95, 1000.00, 0.00, 0.00, 1000.00, 'ADMIN', 'EFECTIVO', 'PENDIENTE', CURRENT_TIMESTAMP, 36.5000, 36500.00)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idPedido = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, monto_recibido, monto_aplicado, monto_usd, monto_bs, tasa_cambio)
                    VALUES (?, 'EFECTIVO', 'USD', 1000.00, 1000.00, 1000.00, 1000.00, 36500.00, 36.5000)
                    """)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedido_saldos_movimientos (id_pedido, tipo, moneda, monto_origen, equivalente_ves, tasa_cambio, actor, motivo, idempotency_key)
                    VALUES (?, 'DEVOLUCION', 'USD', 0.01, 0.37, 36.5000, 'ADMIN', 'Devolucion parcial irrisoria', 'test-f07-key')
                    """)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }

            final int idFinal = idPedido;
            assertThrows("F-07 FALLA: Se permitió anular cobro de $1000 con un contra-asiento de apenas $0.01",
                    ErrorAplicacionException.class,
                    () -> dao.anularPedidoConAuditoria(idFinal, "ADMIN", "Motivo de prueba"));
        } finally {
            if (idPedido > 0) {
                try (Connection con = proveedor.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_saldos_movimientos WHERE id_pedido = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_pagos WHERE id_pedido = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    // =========================================================================
    // F-08: Resumen de conciliación no absorbe saldo en pre-cuenta PENDIENTE
    // =========================================================================
    @Test
    public void testF08_ResumenConciliacionNoAbsorbeSaldoEnPreCuentaPendiente() throws Exception {
        PedidosDao dao = new PedidosDao();
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

        int idPedido = -1;
        try (Connection con = proveedor.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedidos (id_sala, num_mesa, subtotal, iva_porcentaje, iva_monto, total, usuario,
                                         metodo_pago, estado, fecha, tasa_cambio, subtotal_bs, iva_bs, total_bs, cerrada_en)
                    VALUES (1, 94, 1.00, 0.30, 0.00, 1.00, 'ADMIN', 'EFECTIVO', 'PENDIENTE', CURRENT_TIMESTAMP, 100.0000, 100.00, 0.00, 100.30, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    idPedido = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO pedido_pagos (id_pedido, metodo_pago, moneda, monto, monto_recibido, monto_aplicado, monto_usd, monto_bs, tasa_cambio)
                    VALUES (?, 'EFECTIVO', 'VES', 100.00, 100.00, 100.00, 1.00, 100.00, 100.0000)
                    """)) {
                ps.setInt(1, idPedido);
                ps.executeUpdate();
            }

            ResumenConciliacionPedido resumen = dao.resumenConciliacion(idPedido);
            assertNotNull(resumen);

            assertEquals("F-08 FALLA: Se absorbió indebidamente el saldo de 0.30 Bs en un pedido PENDIENTE",
                    new BigDecimal("0.30"), resumen.saldoBsPendiente());
        } finally {
            if (idPedido > 0) {
                try (Connection con = proveedor.getConnection()) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedido_pagos WHERE id_pedido = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM pedidos WHERE id = ?")) {
                        ps.setInt(1, idPedido);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    // =========================================================================
    // F-09: Cantidad manual rechaza cero y revierte el texto al valor válido previo
    // =========================================================================
    @Test
    public void testF09_CantidadManualRechazaCeroYRevierteTexto() throws Exception {
        if ("SI".equals(System.getProperty("e2e.permitir"))) {
            E2EF09Virtual.main(new String[]{});
        }
    }

    // =========================================================================
    // F-10: DetallePedido con aplicaIva null lanza validación, no NPE
    // =========================================================================
    @Test
    public void testF10_DetallePedidoConAplicaIvaNullLanzaValidacionNoNpe() {
        PedidoServicio servicio = new PedidoServicio(new PedidosRepositorioFalso());

        Pedidos p = new Pedidos();
        p.setId_sala(1);
        p.setNum_mesa(1);
        p.setUsuario("ADMIN");
        p.setTotalDecimal(BigDecimal.TEN);
        p.setSubtotal(BigDecimal.TEN);
        p.setTasaCambio(new BigDecimal("50.0000"));
        p.setIvaPorcentaje(new BigDecimal("16.00"));

        DetallePedido d = new DetallePedido();
        d.setNombre("Plato Test");
        d.setPrecioDecimal(BigDecimal.TEN);
        d.setCantidad(1);
        d.setMonedaBase("USD");
        d.setPrecioIncluyeIva(false);
        d.setAplicaIva(null); // <-- NULO

        try {
            servicio.registrarPedidoCompleto(p, List.of(d));
            fail("F-10 FALLA: Debió rechazar detalle con aplicaIva null");
        } catch (ErrorAplicacionException eae) {
            // Éxito: lanzó excepción de dominio de validación legible
            assertTrue(eae.getMessage().toLowerCase().contains("iva") || eae.getMessage().toLowerCase().contains("fiscal"));
        } catch (NullPointerException npe) {
            fail("F-10 FALLA: Lanzó NullPointerException por auto-unboxing inseguro en L82 de PedidoServicio en lugar de ErrorAplicacionException");
        }
    }

    // =========================================================================
    // F-11: Ranking de platos no suma nominalmente USD y VES
    // =========================================================================
    @Test
    public void testF11_RankingPlatosNoSumaNominalmenteUSDyVES() {
        CierreCajaDao dao = new CierreCajaDao();

        DetallePedido dUsd = new DetallePedido();
        dUsd.setNombre("Carne USD");
        dUsd.setPrecioDecimal(new BigDecimal("15.00"));
        dUsd.setCantidad(1);
        dUsd.setMonedaBase("USD");

        DetallePedido dVes = new DetallePedido();
        dVes.setNombre("Arepa VES");
        dVes.setPrecioDecimal(new BigDecimal("250.00"));
        dVes.setCantidad(1);
        dVes.setMonedaBase("VES");

        Pedidos p = new Pedidos();
        p.setTasaCambio(new BigDecimal("50.0000"));
        p.setTotalDecimal(new BigDecimal("20.00"));

        Config cfg = new Config();
        cfg.setTasaDolar(new BigDecimal("50.0000"));

        CierreCaja cierre = CierreCajaDao.calcularDesdeMemoria(
                LocalDate.now().toString(),
                CierreCaja.TipoCierre.TOTAL, "ADMIN",
                cfg,
                List.of(p), List.of(dUsd, dVes));

        assertNotNull(cierre.getTopPlatos());
        assertFalse(cierre.getTopPlatos().isEmpty());

        for (CierreCaja.PlatoVendido pv : cierre.getTopPlatos()) {
            if ("Arepa VES".equalsIgnoreCase(pv.getNombre())) {
                assertNotEquals("F-11 FALLA: Ranking asigna 250 USD a plato de 250 VES sumando nominalmente",
                        new BigDecimal("250.00"), pv.getTotalUsd());
            }
        }
    }

    // =========================================================================
    // F-12: Fallo SQL en guardarCierre propaga error, no retorna false silencioso
    // =========================================================================
    @Test
    public void testF12_GuardarCierrePropagaErrorSqlNoRetornaFalseSilencioso() {
        CierreCajaDao dao = new CierreCajaDao();
        CierreCaja cierre = new CierreCaja();
        cierre.setFecha(LocalDate.now().toString());
        cierre.setTasaCambioReferencia(new BigDecimal("50.0000"));
        cierre.setTotalVentasUsd(BigDecimal.TEN);
        cierre.setTotalVentasBs(new BigDecimal("500.00"));

        // Provocar fallo de base de datos
        // Invariante F-12: No debe silenciar SQLException retornando false, debe propagar excepción
        boolean lanzado = false;
        try {
            boolean ret = dao.guardarCierre(cierre, "/ruta/invalida/que/no/existe/cierre.pdf");
            if (!ret) {
                fail("F-12 FALLA: guardarCierre capturó SQLException y retornó false silencioso");
            }
        } catch (Exception ex) {
            lanzado = true;
        }
        assertTrue("F-12: Debe propagar la excepción al llamador", lanzado);
    }
}
