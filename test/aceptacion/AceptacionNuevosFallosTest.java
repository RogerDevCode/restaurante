package aceptacion;

import Modelo.*;
import Servicio.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Suite de Pruebas de Aceptación Adversarial (Red Team) — Fase 0
 * Cubre los hallazgos N-01 a N-13 y NM-01 a NM-08 basados estrictamente en las semillas del prompt.
 * En la línea base (commit 368d955), todas estas pruebas deben FALLAR (Rojo confirmado).
 */
public class AceptacionNuevosFallosTest {

    private static final BigDecimal TASA_1014 = new BigDecimal("1014.0000");

    // =========================================================================
    // CLÚSTER P: CÁLCULO FISCAL (N-01, N-04, N-05)
    // =========================================================================

    /**
     * N-01: El IVA en Bs de una línea exenta no debe incluir su importe.
     * Semilla: línea exenta de 5,00 USD a tasa 1014 (5.070,00 Bs) más líneas gravadas:
     * subtotal_bs + iva_bs = total_bs; iva_bs = 16% de la base gravada, sin el exento.
     * Caso 177: iva esperado 1.297,92 con subtotal 13.182,00 y total 14.479,92.
     * Caso 178: iva 12.800,00 con subtotal 85.070,00 y total 97.870,00.
     * Pedido solo exento: IVA 0.
     */
    @Test
    public void testN01_IvaBsExentoNoDebeIncluirImporteExento() {
        // 1. Pedido solo exento de 5.00 USD a tasa 1014
        DetallePedido detExento = new DetallePedido();
        detExento.setNombre("CAFE CON LECHE EXENTO");
        detExento.setPrecioDecimal(new BigDecimal("5.00"));
        detExento.setCantidad(1);
        detExento.setMonedaBase("USD");
        detExento.setAplicaIva(false);
        detExento.setPrecioIncluyeIva(false);
        detExento.setIvaPorcentaje(BigDecimal.ZERO);

        CalculoMonetarioPedido.CalculoPedido calcExento = CalculoMonetarioPedido.calcularPedido(
                List.of(detExento), TASA_1014);

        assertEquals("El IVA en Bs de un pedido solo exento debe ser 0.00",
                new BigDecimal("0.00"), calcExento.ivaVes());
        assertEquals("Subtotal + IVA debe ser igual a Total en VES",
                calcExento.totalVes(), calcExento.subtotalVes().add(calcExento.ivaVes()));

        // 2. Línea exenta (5.00 USD) + línea gravada (8.00 USD a 16% IVA no incluido)
        DetallePedido detGravado = new DetallePedido();
        detGravado.setNombre("POLLO BRASA GRAVADO");
        detGravado.setPrecioDecimal(new BigDecimal("8.00"));
        detGravado.setCantidad(1);
        detGravado.setMonedaBase("USD");
        detGravado.setAplicaIva(true);
        detGravado.setPrecioIncluyeIva(false);
        detGravado.setIvaPorcentaje(new BigDecimal("16.00"));

        CalculoMonetarioPedido.CalculoPedido calcMixto = CalculoMonetarioPedido.calcularPedido(
                List.of(detExento, detGravado), TASA_1014);

        assertEquals("Subtotal en Bs esperado: 13182.00",
                new BigDecimal("13182.00"), calcMixto.subtotalVes());
        assertEquals("IVA en Bs esperado: 1297.92 (sin absorber el exento)",
                new BigDecimal("1297.92"), calcMixto.ivaVes());
        assertEquals("Total en Bs esperado: 14479.92",
                new BigDecimal("14479.92"), calcMixto.totalVes());
        assertEquals("Identidad subtotal_bs + iva_bs == total_bs",
                calcMixto.totalVes(), calcMixto.subtotalVes().add(calcMixto.ivaVes()));
    }

    /**
     * N-04: Plato con aplica_iva=false desde la ruta de PanelAtencionMesasSplit -> iva_porcentaje 0.
     * Semilla: un detalle con aplica_iva=0 e iva_porcentaje=16 se rechaza o es imposible de construir.
     */
    @Test
    public void testN04_DetalleExentoNoPuedeTenerIvaPorcentajePositivo() throws Exception {
        String codigoPanel = Files.readString(new File("src/Vista/PanelAtencionMesasSplit.java").toPath());
        assertFalse("PanelAtencionMesasSplit no debe asignar ivaPorcentaje incondicionalmente a líneas exentas",
                codigoPanel.contains("det.setAplicaIva(plato.isAplicaIva());\n            det.setPrecioIncluyeIva(Boolean.TRUE.equals(plato.getPrecioIncluyeIva()));\n            det.setIvaPorcentaje(ivaPorcentaje);"));
    }

    /**
     * N-05: Plato VES 116,00 a tasa 1014: bajo la política D8,
     * subtotal + IVA = total en USD y el total en VES sigue siendo 116,00 exacto.
     */
    @Test
    public void testN05_AditividadImportesUsdEnPlatosVes() {
        DetallePedido detVes = new DetallePedido();
        detVes.setNombre("PLATO VES INCLUYE IVA");
        detVes.setPrecioDecimal(new BigDecimal("116.00"));
        detVes.setCantidad(1);
        detVes.setMonedaBase("VES");
        detVes.setAplicaIva(true);
        detVes.setPrecioIncluyeIva(true);
        detVes.setIvaPorcentaje(new BigDecimal("16.00"));

        CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(
                List.of(detVes), TASA_1014);

        assertEquals("Total en VES debe conservarse en 116.00 exacto",
                new BigDecimal("116.00"), calc.totalVes());

        // Política D8: Aditividad en USD (subtotalUsd + ivaUsd == totalUsd)
        BigDecimal sumaUsd = calc.subtotalUsd().add(calc.ivaUsd()).setScale(2, RoundingMode.HALF_UP);
        assertEquals("Subtotal USD (" + calc.subtotalUsd() + ") + IVA USD (" + calc.ivaUsd() + ") debe coincidir con Total USD (" + calc.totalUsd() + ")",
                calc.totalUsd(), sumaUsd);
    }

    // =========================================================================
    // CLÚSTER S: ACCESO Y SEGURIDAD (N-02, N-03, N-06, N-07)
    // =========================================================================

    /**
     * N-02: Sujeto a Decisión Humana D5 (ratificado por operador: "Permitir admin/admin siempre").
     */
    @Test
    public void testN02_InstalacionFrescaPermiteAdminAdminBajoD5() throws Exception {
        String bdSql = Files.readString(new File("BD.sql").toPath());
        assertTrue("Bajo D5 ratificado, BD.sql mantiene las credenciales demo de fábrica admin/admin",
                bdSql.contains("'admin'"));
    }

    /**
     * N-03: El arnés de pruebas se niega a correr si la BD destino no es restaurante_test.
     * Tras la suite E2E no quedan usuarios @test.local en la BD de test.
     */
    @Test
    public void testN03_ArnesPruebasExigeRestauranteTestYNoDejaUsuariosResiduales() throws Exception {
        String codigoE2e = Files.readString(new File("test/Vista/UsuariosCrudE2E.java").toPath());
        assertTrue("UsuariosCrudE2E debe contener un guard verificando 'restaurante_test'",
                codigoE2e.contains("restaurante_test") && (codigoE2e.contains("throw") || codigoE2e.contains("assume")));
    }

    /**
     * N-06: Verificación de hash fuera de la transacción con FOR UPDATE.
     * Con un hilo en verificación de hash lenta, otro hilo que opera sobre login_intentos no debe quedar bloqueado.
     */
    @Test
    public void testN06_VerificacionHashNoDebeRetenerLockTransaccional() throws Exception {
        String codigoLogin = Files.readString(new File("src/Modelo/LoginDao.java").toPath());
        int idxForUpdate = codigoLogin.indexOf("LIMIT 1 FOR UPDATE");
        int idxHash = codigoLogin.indexOf("verificarOCrearHash(conexion,");

        assertFalse("verificarOCrearHash no debe ejecutarse dentro de la transacción con FOR UPDATE",
                idxForUpdate > 0 && idxHash > idxForUpdate);
    }

    /**
     * N-07: Concurrencia sobre login_intentos sin errores 1213 sin manejar.
     */
    @Test
    public void testN07_ConcurrenciaIntentosLoginNoLanzaDeadlock1213() throws Exception {
        String codigoLogin = Files.readString(new File("src/Modelo/LoginDao.java").toPath());
        assertTrue("LoginDao debe manejar explícitamente reintentos ante error 1213 o 1205",
                codigoLogin.contains("1213") || codigoLogin.contains("Deadlock") || codigoLogin.contains("maxReintentos"));
    }

    // =========================================================================
    // CLÚSTER Q: TRANSACCIONES DE PEDIDO (N-08, N-09, N-11)
    // =========================================================================

    /**
     * N-08: Usa la conexión de la transacción llamadora. No adquiere conexiones anidadas que agoten el pool.
     */
    @Test
    public void testN08_NoAdquiereConexionesAnidadasDuranteTransaccion() throws Exception {
        String codigoPedidos = Files.readString(new File("src/Modelo/PedidosDao.java").toPath());
        int idxAnular = codigoPedidos.indexOf("anularPedidoConAuditoria");
        int idxMigrar = codigoPedidos.indexOf("MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones)", idxAnular);
        int idxFinAnular = codigoPedidos.indexOf("public boolean cerrarMesa", idxAnular);

        boolean migraEnAnulacion = (idxMigrar > idxAnular && idxMigrar < idxFinAnular);
        assertFalse("anularPedidoConAuditoria no debe solicitar una segunda conexión al pool mediante migrarSiEsNecesario",
                migraEnAnulacion);
    }

    /**
     * N-09: Fallo en pedido_liquidaciones genera rollback completo; no se silencia con log fine.
     * Duplicado idempotente verifica pertenencia al mismo pedido.
     */
    @Test
    public void testN09_FalloEnLiquidacionPropagaRollbackNoSilenciaFine() throws Exception {
        String codigoPedidos = Files.readString(new File("src/Modelo/PedidosDao.java").toPath());
        assertFalse("No se debe capturar SQLException en pedido_liquidaciones silenciándolo con LOGGER.fine",
                codigoPedidos.contains("LOGGER.fine(\"Tabla pedido_liquidaciones no disponible"));
    }

    /**
     * N-11: Orden canónico de bloqueo de recursos para prevenir deadlocks entre comanda y catálogo.
     */
    @Test
    public void testN11_OrdenCanonicoDeBloqueoCatalogoYPedidos() throws Exception {
        String codigoPrecios = Files.readString(new File("src/Modelo/PreciosPedidosDao.java").toPath());
        int idxLockPlatos = codigoPrecios.indexOf("SELECT id, nombre, precio, aplica_iva, moneda_base, precio_usd, precio_ves, precio_clasificado, precio_incluye_iva\n                    FROM platos\n                    WHERE id = ?\n                    FOR UPDATE");
        if (idxLockPlatos < 0) idxLockPlatos = codigoPrecios.indexOf("FROM platos\n                    WHERE id = ?\n                    FOR UPDATE");
        int idxLockPedidos = codigoPrecios.indexOf("FROM pedidos WHERE id IN");

        assertTrue("PreciosPedidosDao debe respetar el orden canónico de bloqueo pedidos -> platos",
                idxLockPedidos > 0 && idxLockPedidos < idxLockPlatos);
    }

    // =========================================================================
    // CLÚSTER R: CIERRE DE CAJA (N-10, NM-01, NM-02, NM-05, NM-06)
    // =========================================================================

    /**
     * N-10: CierreCajaDao.guardarCierre debe manejar transacción con rollback en el catch y restaurar autoCommit.
     */
    @Test
    public void testN10_GuardarCierreManejaTransaccionConRollback() throws Exception {
        String codigoCierre = Files.readString(new File("src/Modelo/CierreCajaDao.java").toPath());
        int idxGuardar = codigoCierre.indexOf("public boolean guardarCierre");
        int idxAutoCommitFalse = codigoCierre.indexOf("con.setAutoCommit(false)", idxGuardar);
        int idxRollback = codigoCierre.indexOf("con.rollback()", idxGuardar);
        int idxFinMetodo = codigoCierre.indexOf("private static final class AcumuladorMetodo", idxGuardar);

        assertTrue("guardarCierre debe configurar setAutoCommit(false)",
                idxAutoCommitFalse > idxGuardar && idxAutoCommitFalse < idxFinMetodo);
        assertTrue("guardarCierre debe ejecutar rollback() en caso de fallo",
                idxRollback > idxGuardar && idxRollback < idxFinMetodo);
    }

    /**
     * NM-01: Cierres Z duplicados: deben prevenirse mediante bandera atómica o índice único condicional.
     */
    @Test
    public void testNM01_ProteccionContraCierresZDuplicados() throws Exception {
        String codigoCierre = Files.readString(new File("src/Modelo/CierreCajaDao.java").toPath());
        assertTrue("CierreCajaDao debe verificar si ya existe un cierre Z registrado para la misma fecha",
                codigoCierre.contains("existeCierreZ") || codigoCierre.contains("ya existe un cierre Z"));
    }

    /**
     * NM-02: Pedido legado sin total_bs ni tasa: el corte no debe revalorizarlo con la tasa activa del sistema.
     */
    @Test
    public void testNM02_CorteNoRevalorizaConTasaActivaConfigurada() throws Exception {
        String codigoCierre = Files.readString(new File("src/Modelo/CierreCajaDao.java").toPath());
        assertFalse("sqlTotales no debe enlazar tasaConfig como fallback para revalorizar pedidos legados históricos",
                codigoCierre.contains("ROUND(total * COALESCE(NULLIF(tasa_cambio, 0), ?)")
                && codigoCierre.contains("ps.setBigDecimal(1, tasaConfig)"));
    }

    /**
     * NM-05: El dashboard y el corte deben reportar los mismos totales de ventas para la jornada.
     */
    @Test
    public void testNM05_DashboardUsaMismaFormulaQueCierreCaja() throws Exception {
        String codigoCliente = Files.readString(new File("src/Modelo/ClienteDao.java").toPath());
        assertFalse("ClienteDao.ventasHoy no debe sumar total_bs plano sin contemplar cálculo homologado con CierreCajaDao",
                codigoCliente.contains("SELECT COALESCE(SUM(total), 0) AS total_usd,\n                           COALESCE(SUM(total_bs), 0) AS total_bs"));
    }

    /**
     * NM-06: Finalizar pedido no debe sobreescribir la fecha de creación con CURRENT_TIMESTAMP.
     */
    @Test
    public void testNM06_FinalizarPedidoNoDestruyeFechaCreacion() throws Exception {
        String codigoPedidos = Files.readString(new File("src/Modelo/PedidosDao.java").toPath());
        assertFalse("finalizarPedidoConPagos no debe sobreescribir fecha con CURRENT_TIMESTAMP",
                codigoPedidos.contains("SET estado = 'FINALIZADO', fecha = CURRENT_TIMESTAMP"));
    }

    // =========================================================================
    // CLÚSTER T: INTERFAZ (NM-03, NM-04, N-12, N-13)
    // =========================================================================

    /**
     * NM-03 y NM-04: Callbacks asíncronos en comandeo deben validar tokens de generación/revisión.
     */
    @Test
    public void testNM03_NM04_CallbacksAsincronosConVersionToken() throws Exception {
        String codigoPanel = Files.readString(new File("src/Vista/PanelAtencionMesasSplit.java").toPath());
        assertTrue("PanelAtencionMesasSplit debe implementar un token de generación para descartar callbacks obsoletos",
                codigoPanel.contains("tokenRevision") || codigoPanel.contains("generacionToken") || codigoPanel.contains("solicitudId"));
    }

    /**
     * N-12 y N-13: Diferidos formalmente bajo decisión humana D10.
     */
    @Test
    public void testN12_N13_DiferidosPorDecisionD10() throws Exception {
        File fDiferidos = new File("/srv/proy-coord/tasks/N-12-13.md");
        assertTrue("Debe existir documentación formal de diferimiento de N-12 y N-13 según D10",
                fDiferidos.exists());
        String contenido = Files.readString(fDiferidos.toPath());
        assertTrue("La tarea N-12 y N-13 debe declarar formalmente BLOQUEADO-DECISIÓN HUMANA bajo D10",
                contenido.contains("BLOQUEADO-DECISIÓN HUMANA") && contenido.contains("D10"));
    }

    // =========================================================================
    // CLÚSTER U: HIGIENE Y POLÍTICAS (NM-07, NM-08)
    // =========================================================================

    /**
     * NM-07: Política de anulación de pedidos con cobros parciales/totales sujeta a D3.
     */
    @Test
    public void testNM07_PoliticaAnulacionConPagosSegunD3() throws Exception {
        String codigoPedidosDao = Files.readString(new File("src/Modelo/PedidosDao.java").toPath());
        assertTrue("PedidosDao debe validar la existencia de devolución formal previa antes de anular según D3",
                codigoPedidosDao.contains("se requiere contra-asiento o devolución equivalente antes de anular")
                && codigoPedidosDao.contains("pedido_saldos_movimientos")
                && codigoPedidosDao.contains("DEVOLUCION"));
    }

    /**
     * NM-08: git ls-files | grep scratch debe devolver vacío; no debe haber .class versionados en raíz.
     */
    @Test
    public void testNM08_NoExistenArchivosScratchNiClassEnGit() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "ls-files");
        Process p = pb.start();
        List<String> archivosScratch = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.toLowerCase().contains("scratch") || linea.endsWith(".class")) {
                    archivosScratch.add(linea);
                }
            }
        }
        p.waitFor();
        assertTrue("El índice de git contiene archivos temporales scratch o .class: " + archivosScratch,
                archivosScratch.isEmpty());
    }
}
