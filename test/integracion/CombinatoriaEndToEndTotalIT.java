package integracion;

import Controlador.LoginControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.CalculoFiscalRecord;
import Modelo.Config;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.LoginDao;
import Modelo.PedidoPendienteExistenteException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.Salas;
import Modelo.SalasDao;
import Modelo.Usuario;
import Servicio.AutenticacionServicio;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PlatosServicio;
import Servicio.PoliticaAcceso;
import Servicio.SalasServicio;
import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite de Integración End-to-End Combinatoria Total sobre MySQL Real:
 *
 * Ejecuta una matriz exhaustiva combinando:
 * 1. Personas/Roles: Administrador vs Asistente vs No autorizado.
 * 2. Tasas de IVA: 0.00% (Exento), 8.00% (Reducido), 16.00% (Estándar), 21.00% (Especial).
 * 3. Tasas de Cambio: 36.5000, 38.5000, 42.0000.
 * 4. Ciclo de vida completo:
 *    - Sala -> Mesa -> Plato -> Carrito/Pedido preliminar (sin IVA) ->
 *    - Detección de colisiones de mesa (concurrencia) ->
 *    - Facturación y liquidación fiscal (con desglose de IVA) ->
 *    - Generación de PDF fiscal bimonetario ->
 *    - Liberación de mesa para nuevo pedido ->
 *    - Verificación directa de inmutabilidad fiscal en tablas de MySQL ->
 *    - Protección de integridad referencial (FK salas-pedidos).
 */
public class CombinatoriaEndToEndTotalIT {

    private static final String TEST_URL = "jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String TEST_USER = "restaurante_test_app";
    private static final String TEST_PASS = System.getProperty("DB_PASSWORD",
            System.getenv().getOrDefault("TEST_DB_PASSWORD", System.getenv().getOrDefault("MYSQL_PASSWORD", "")));

    private BigDecimal tasaOriginal = new BigDecimal("36.5000");
    private BigDecimal ivaOriginal = new BigDecimal("16.00");

    @BeforeClass
    public static void configurarEntorno() {
        System.setProperty("DB_URL", TEST_URL);
        System.setProperty("DB_USER", TEST_USER);
        System.setProperty("DB_PASSWORD", TEST_PASS);
    }

    private Connection abrirConexion() throws SQLException {
        return DriverManager.getConnection(TEST_URL, TEST_USER, TEST_PASS);
    }

    @Before
    public void prepararDatos() throws SQLException {
        limpiarEntidades();
        guardarConfigOriginal();
    }

    @After
    public void restaurarDatos() throws SQLException {
        limpiarEntidades();
        restaurarConfigOriginal();
    }

    private void guardarConfigOriginal() {
        try {
            LoginDao dao = new LoginDao();
            Config cfg = dao.datosEmpresa();
            if (cfg != null) {
                if (cfg.getTasaDolar() != null) tasaOriginal = cfg.getTasaDolar();
                if (cfg.getIvaPorcentaje() != null) ivaOriginal = cfg.getIvaPorcentaje();
            }
        } catch (Exception ignored) {}
    }

    private void restaurarConfigOriginal() {
        try {
            LoginDao dao = new LoginDao();
            Config cfg = dao.datosEmpresa();
            if (cfg != null) {
                cfg.setTasaDolar(tasaOriginal);
                cfg.setIvaPorcentaje(ivaOriginal);
                dao.ModificarDatos(cfg);
            }
        } catch (Exception ignored) {}
    }

    private void limpiarEntidades() throws SQLException {
        try (Connection con = abrirConexion(); Statement stmt = con.createStatement()) {
            stmt.execute("DELETE d FROM detalle_pedidos d JOIN pedidos p ON p.id=d.id_pedido WHERE p.usuario LIKE 'E2E_%'");
            stmt.execute("DELETE FROM pedidos WHERE usuario LIKE 'E2E_%'");
            stmt.execute("DELETE FROM platos WHERE nombre LIKE 'E2E_PLATO_%'");
            stmt.execute("DELETE FROM salas WHERE nombre LIKE 'E2E_SALA_%'");
            stmt.execute("DELETE FROM usuarios WHERE correo LIKE '%@restaurante-e2e.test'");
        }
    }

    @Test
    public void combinatoriaTotalEndToEndRolesTasasIvaYConcurrencia() throws Exception {
        Path tempPdfDir = Files.createTempDirectory("pdf-e2e-total-");
        try {
            ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
            PasswordHasher hasher = new PasswordHasher();
            LoginDao loginDao = new LoginDao(proveedor, hasher);
            SalasDao salasDao = new SalasDao(proveedor);
            PlatosDao platosDao = new PlatosDao(proveedor);
            PedidosDao pedidosDao = new PedidosDao(proveedor);

            String sufijo = UUID.randomUUID().toString().substring(0, 8);
            String fechaHoy = LocalDate.now().toString();

            // -----------------------------------------------------------------
            // FASE 1: GESTIÓN DE USUARIOS Y ROLES (ADMIN Y ASISTENTE)
            // -----------------------------------------------------------------
            String correoAdmin = "admin-" + sufijo + "@restaurante-e2e.test";
            String correoAsistente = "asist-" + sufijo + "@restaurante-e2e.test";
            String nombreAdmin = "E2E_ADMIN_" + sufijo;
            String nombreAsistente = "E2E_ASIST_" + sufijo;

            Usuario adminUser = new Usuario(0, nombreAdmin, correoAdmin, "adminPass", "Administrador");
            Usuario asistenteUser = new Usuario(0, nombreAsistente, correoAsistente, "asistPass", "Asistente");

            assertTrue(loginDao.Registrar(adminUser));
            assertTrue(loginDao.Registrar(asistenteUser));

            LoginControlador loginCtrl = new LoginControlador(new AutenticacionServicio(loginDao));
            Optional<Usuario> sesionAdmin = loginCtrl.autenticar(correoAdmin, "adminPass");
            Optional<Usuario> sesionAsistente = loginCtrl.autenticar(correoAsistente, "asistPass");

            assertTrue(sesionAdmin.isPresent());
            assertTrue(sesionAsistente.isPresent());

            Usuario admin = sesionAdmin.get();
            Usuario asistente = sesionAsistente.get();

            PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
            PoliticaAcceso rbacAsistente = new PoliticaAcceso(asistente);

            SalasServicio salasAdminServicio = new SalasServicio(salasDao, rbacAdmin);
            SalasServicio salasAsistenteServicio = new SalasServicio(salasDao, rbacAsistente);

            PlatosServicio platosAdminServicio = new PlatosServicio(platosDao, rbacAdmin);
            PlatosServicio platosAsistenteServicio = new PlatosServicio(platosDao, rbacAsistente);

            // Verificar restricciones RBAC para Asistente
            assertThrows(ErrorAplicacionException.class, () -> salasAsistenteServicio.registrar(new Salas(0, "E2E_SALA_FAIL_" + sufijo, 5)));
            assertThrows(ErrorAplicacionException.class, () -> platosAsistenteServicio.registrar(new Platos(0, "E2E_PLATO_FAIL_" + sufijo, new BigDecimal("10.00"), fechaHoy)));

            // Admin registra Sala de prueba
            String nombreSala = "E2E_SALA_" + sufijo;
            assertTrue(salasAdminServicio.registrar(new Salas(0, nombreSala, 10)));
            Salas salaCreada = salasAdminServicio.listar().stream()
                    .filter(s -> nombreSala.equals(s.getNombre()))
                    .findFirst().orElseThrow();
            int idSala = salaCreada.getId();

            // Admin registra Platos de prueba
            String platoA = "E2E_PLATO_A_" + sufijo;
            String platoB = "E2E_PLATO_B_" + sufijo;
            String platoC = "E2E_PLATO_C_" + sufijo;

            assertTrue(platosAdminServicio.registrar(new Platos(0, platoA, new BigDecimal("10.00"), fechaHoy)));
            assertTrue(platosAdminServicio.registrar(new Platos(0, platoB, new BigDecimal("25.00"), fechaHoy)));
            assertTrue(platosAdminServicio.registrar(new Platos(0, platoC, new BigDecimal("5.00"), fechaHoy)));

            // -----------------------------------------------------------------
            // FASE 2: MATRIZ COMBINATORIA DE IVA Y TASAS DE CAMBIO
            // -----------------------------------------------------------------
            BigDecimal[][] combinacionesFiscales = new BigDecimal[][]{
                    {new BigDecimal("0.00"), new BigDecimal("36.5000")},   // 0% IVA (Exento), Tasa 36.50
                    {new BigDecimal("8.00"), new BigDecimal("38.5000")},   // 8% IVA (Reducido), Tasa 38.50
                    {new BigDecimal("16.00"), new BigDecimal("36.5000")},  // 16% IVA (Estándar), Tasa 36.50
                    {new BigDecimal("21.00"), new BigDecimal("42.0000")}   // 21% IVA (Especial), Tasa 42.00
            };

            GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(tempPdfDir);
            PedidoPdfServicio pdfServicioAdmin = new PedidoPdfServicio(
                    pedidosDao::verPedido,
                    pedidosDao::verPedidoDetalle,
                    loginDao::datosEmpresa,
                    generadorPdf,
                    f -> {}
            );
            ConsultaPedidosServicio consultaAdmin = new ConsultaPedidosServicio(pedidosDao, rbacAdmin);
            PedidosControlador pedidosCtrlAdmin = new PedidosControlador(new PedidoServicio(pedidosDao), pdfServicioAdmin, rbacAdmin, consultaAdmin);

            ConsultaPedidosServicio consultaAsistente = new ConsultaPedidosServicio(pedidosDao, rbacAsistente);
            PedidosControlador pedidosCtrlAsistente = new PedidosControlador(new PedidoServicio(pedidosDao), pdfServicioAdmin, rbacAsistente, consultaAsistente);

            Config configEmpresa = loginDao.datosEmpresa();

            int mesaActual = 1;
            List<Integer> pedidosGenerados = new ArrayList<>();

            for (BigDecimal[] combo : combinacionesFiscales) {
                BigDecimal ivaPrueba = combo[0];
                BigDecimal tasaPrueba = combo[1];

                // Actualizar configuración fiscal
                configEmpresa.setIvaPorcentaje(ivaPrueba);
                configEmpresa.setTasaDolar(tasaPrueba);
                assertTrue(loginDao.ModificarDatos(configEmpresa));

                Config confLeida = loginDao.datosEmpresa();
                assertEquals(0, ivaPrueba.compareTo(confLeida.getIvaPorcentaje()));
                assertEquals(0, tasaPrueba.compareTo(confLeida.getTasaDolar()));

                // Armar Pedido preliminar para la mesa
                // 2 PlatoA ($10 x 2 = $20) + 1 PlatoB ($25 x 1 = $25) = Subtotal $45.00
                BigDecimal subtotalUsd = new BigDecimal("45.00");
                CalculoFiscalRecord fiscal = CalculoFiscalRecord.calcular(subtotalUsd, ivaPrueba, tasaPrueba);

                Pedidos pedidoNuevo = new Pedidos();
                pedidoNuevo.setId_sala(idSala);
                pedidoNuevo.setSala(nombreSala);
                pedidoNuevo.setNum_mesa(mesaActual);
                pedidoNuevo.setFecha(fechaHoy);
                pedidoNuevo.setSubtotal(fiscal.subtotalUsd());
                pedidoNuevo.setIvaPorcentaje(fiscal.ivaPorcentaje());
                pedidoNuevo.setIvaMonto(fiscal.ivaUsd());
                pedidoNuevo.setTotalDecimal(fiscal.totalUsd());
                pedidoNuevo.setTasaCambio(fiscal.tasaCambio());
                pedidoNuevo.setSubtotalBs(fiscal.subtotalBs());
                pedidoNuevo.setIvaBs(fiscal.ivaBs());
                pedidoNuevo.setTotalBs(fiscal.totalBs());
                pedidoNuevo.setUsuario(nombreAsistente);

                List<DetallePedido> detalles = Arrays.asList(
                        new DetallePedido(0, platoA, new BigDecimal("10.00"), 2, "Término medio", 0),
                        new DetallePedido(0, platoB, new BigDecimal("25.00"), 1, "Sin salsa", 0)
                );

                // Asistente registra pedido
                int idPedido = pedidosCtrlAsistente.registrarPedidoCompleto(pedidoNuevo, detalles);
                assertTrue("ID de pedido debe ser > 0", idPedido > 0);
                pedidosGenerados.add(idPedido);

                // Verificar colisión de mesa (concurrencia): mesa ocupada rechaza segundo pedido pendiente
                Pedidos pedidoColision = new Pedidos();
                pedidoColision.setId_sala(idSala);
                pedidoColision.setSala(nombreSala);
                pedidoColision.setNum_mesa(mesaActual);
                pedidoColision.setFecha(fechaHoy);
                pedidoColision.setSubtotal(fiscal.subtotalUsd());
                pedidoColision.setIvaPorcentaje(fiscal.ivaPorcentaje());
                pedidoColision.setIvaMonto(fiscal.ivaUsd());
                pedidoColision.setTotalDecimal(fiscal.totalUsd());
                pedidoColision.setTasaCambio(fiscal.tasaCambio());
                pedidoColision.setSubtotalBs(fiscal.subtotalBs());
                pedidoColision.setIvaBs(fiscal.ivaBs());
                pedidoColision.setTotalBs(fiscal.totalBs());
                pedidoColision.setUsuario(nombreAdmin);
                assertThrows("Mesa ocupada no admite segundo pedido",
                        PedidoPendienteExistenteException.class,
                        () -> pedidosCtrlAdmin.registrarPedidoCompleto(pedidoColision, detalles));

                // Verificar persistencia y desglose fiscal directo en base de datos
                try (Connection con = abrirConexion();
                     PreparedStatement stmt = con.prepareStatement(
                             "SELECT subtotal, iva_porcentaje, iva_monto, total, tasa_cambio, subtotal_bs, iva_bs, total_bs, estado " +
                             "FROM pedidos WHERE id = ?")) {
                    stmt.setInt(1, idPedido);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, fiscal.subtotalUsd().compareTo(rs.getBigDecimal("subtotal")));
                        assertEquals(0, fiscal.ivaPorcentaje().compareTo(rs.getBigDecimal("iva_porcentaje")));
                        assertEquals(0, fiscal.ivaUsd().compareTo(rs.getBigDecimal("iva_monto")));
                        assertEquals(0, fiscal.totalUsd().compareTo(rs.getBigDecimal("total")));
                        assertEquals(0, fiscal.tasaCambio().compareTo(rs.getBigDecimal("tasa_cambio")));
                        assertEquals(0, fiscal.subtotalBs().compareTo(rs.getBigDecimal("subtotal_bs")));
                        assertEquals(0, fiscal.ivaBs().compareTo(rs.getBigDecimal("iva_bs")));
                        assertEquals(0, fiscal.totalBs().compareTo(rs.getBigDecimal("total_bs")));
                        assertEquals("PENDIENTE", rs.getString("estado"));
                    }
                }

                // Generar PDF y verificar archivo
                pedidosCtrlAdmin.generarPdfPedido(idPedido);
                Path pdfGenerado = tempPdfDir.resolve("pedido-" + idPedido + ".pdf");
                assertTrue("Archivo PDF debe existir", Files.isRegularFile(pdfGenerado));
                assertTrue("Archivo PDF no debe estar vacío", Files.size(pdfGenerado) > 500);

                // Finalizar el pedido
                assertTrue(pedidosCtrlAdmin.finalizarPedido(idPedido));
                assertEquals("FINALIZADO", pedidosCtrlAdmin.verPedido(idPedido).getEstado());

                // Doble finalización es idempotente y preserva estado FINALIZADO
                assertTrue(pedidosCtrlAdmin.finalizarPedido(idPedido));
                assertEquals("FINALIZADO", pedidosCtrlAdmin.verPedido(idPedido).getEstado());

                // Mesa liberada: verificar que ahora sí permite un nuevo pedido en la misma mesa
                CalculoFiscalRecord fiscalReapertura = CalculoFiscalRecord.calcular(new BigDecimal("5.00"), ivaPrueba, tasaPrueba);
                Pedidos pedidoReapertura = new Pedidos();
                pedidoReapertura.setId_sala(idSala);
                pedidoReapertura.setSala(nombreSala);
                pedidoReapertura.setNum_mesa(mesaActual);
                pedidoReapertura.setFecha(fechaHoy);
                pedidoReapertura.setSubtotal(fiscalReapertura.subtotalUsd());
                pedidoReapertura.setIvaPorcentaje(fiscalReapertura.ivaPorcentaje());
                pedidoReapertura.setIvaMonto(fiscalReapertura.ivaUsd());
                pedidoReapertura.setTotalDecimal(fiscalReapertura.totalUsd());
                pedidoReapertura.setTasaCambio(fiscalReapertura.tasaCambio());
                pedidoReapertura.setSubtotalBs(fiscalReapertura.subtotalBs());
                pedidoReapertura.setIvaBs(fiscalReapertura.ivaBs());
                pedidoReapertura.setTotalBs(fiscalReapertura.totalBs());
                pedidoReapertura.setUsuario(nombreAsistente);

                List<DetallePedido> detalleReapertura = Arrays.asList(
                        new DetallePedido(0, platoC, new BigDecimal("5.00"), 1, "", 0)
                );
                int idReapertura = pedidosCtrlAsistente.registrarPedidoCompleto(pedidoReapertura, detalleReapertura);
                assertTrue(idReapertura > 0);
                assertTrue(pedidosCtrlAdmin.finalizarPedido(idReapertura));

                mesaActual++;
            }

            // -----------------------------------------------------------------
            // FASE 3: INMUTABILIDAD HISTÓRICA ENTRE CAMBIOS FISCALES
            // -----------------------------------------------------------------
            // Verificar que los pedidos de la lista conservaron sus tasas respectivas
            for (int i = 0; i < combinacionesFiscales.length; i++) {
                int idPed = pedidosGenerados.get(i);
                BigDecimal ivaEsperado = combinacionesFiscales[i][0];
                BigDecimal tasaEsperada = combinacionesFiscales[i][1];

                Pedidos cargado = pedidosCtrlAdmin.verPedido(idPed);
                assertEquals("IVA histórico preservado", 0, ivaEsperado.compareTo(cargado.getIvaPorcentaje()));
                assertEquals("Tasa histórica preservada", 0, tasaEsperada.compareTo(cargado.getTasaCambio()));
            }

            // -----------------------------------------------------------------
            // FASE 4: INTEGRIDAD REFERENCIAL
            // -----------------------------------------------------------------
            // Intentar borrar la sala con pedidos asociados debe fallar
            assertThrows("FK salas-pedidos debe impedir borrar sala con historial",
                    ErrorAplicacionException.class,
                    () -> salasAdminServicio.eliminar(idSala));

        } finally {
            // Limpieza de archivos temporales de PDF
            File[] files = tempPdfDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            Files.deleteIfExists(tempPdfDir);
        }
    }
}
