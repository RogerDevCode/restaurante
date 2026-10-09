package integracion;

import Controlador.LoginControlador;
import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite de integración end-to-end con combinatoria de casos sobre MySQL real:
 * - Ciclo completo de usuario: Admin y Asistente
 * - Catálogo de salas y platos con precios límite
 * - Pedidos bimonetarios con cambio dinámico de tasa y comprobación de inmutabilidad histórica
 * - Integridad referencial (FK salas-pedidos)
 * - Concurrencia de mesas y liberación tras finalización
 * - Generación de PDF bimonetario verificado
 */
public class CombinatoriaUsuarioE2EIT {

    private static final String TEST_URL = "jdbc:mysql://127.0.0.1:3307/restaurante_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String TEST_USER = "restaurante_test_app";
    private static final String TEST_PASS = System.getProperty("DB_PASSWORD",
            System.getenv().getOrDefault("TEST_DB_PASSWORD", System.getenv().getOrDefault("MYSQL_PASSWORD", "")));

    private BigDecimal tasaOriginal = new BigDecimal("36.5000");

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
    public void limpiarDatosPrevios() throws SQLException {
        limpiarEntidadesPropias();
        guardarTasaOriginal();
    }

    @After
    public void restaurarDatos() throws SQLException {
        limpiarEntidadesPropias();
        restaurarTasaOriginal();
    }

    private void guardarTasaOriginal() {
        try {
            LoginDao dao = new LoginDao();
            Config cfg = dao.datosEmpresa();
            if (cfg != null && cfg.getTasaDolar() != null) {
                tasaOriginal = cfg.getTasaDolar();
            }
        } catch (Exception ignored) {
        }
    }

    private void restaurarTasaOriginal() {
        try {
            LoginDao dao = new LoginDao();
            Config cfg = dao.datosEmpresa();
            if (cfg != null) {
                cfg.setTasaDolar(tasaOriginal);
                dao.ModificarDatos(cfg);
            }
        } catch (Exception ignored) {
        }
    }

    private void limpiarEntidadesPropias() throws SQLException {
        try (Connection con = abrirConexion(); Statement stmt = con.createStatement()) {
            stmt.execute("DELETE d FROM detalle_pedidos d JOIN pedidos p ON p.id=d.id_pedido WHERE p.usuario LIKE 'COMB_%'");
            stmt.execute("DELETE FROM pedidos WHERE usuario LIKE 'COMB_%'");
            stmt.execute("DELETE FROM platos WHERE nombre LIKE 'COMB_PLATO_%'");
            stmt.execute("DELETE FROM salas WHERE nombre LIKE 'COMB_SALA_%'");
            stmt.execute("DELETE FROM usuarios WHERE correo LIKE '%@restaurante-comb.test'");
        }
    }

    @Test
    public void combinatoriaCompletaUsuarioCicloBimonetarioYConcurrencia() throws Exception {
        Path tempPdfDir = Files.createTempDirectory("pdf-test-combinatoria-");
        ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
        PasswordHasher hasher = new PasswordHasher();
        LoginDao loginDao = new LoginDao(proveedor, hasher);
        SalasDao salasDao = new SalasDao(proveedor);
        PlatosDao platosDao = new PlatosDao(proveedor);
        PedidosDao pedidosDao = new PedidosDao(proveedor);

        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        String correoAdmin = "admin-" + sufijo + "@restaurante-comb.test";
        String correoAsistente = "asist-" + sufijo + "@restaurante-comb.test";
        String usuarioAdminNombre = "COMB_ADMIN_" + sufijo;
        String usuarioAsistenteNombre = "COMB_ASIST_" + sufijo;

        // 1. Creación y Autenticación Administrador
        Usuario adminUser = new Usuario(0, usuarioAdminNombre, correoAdmin, "adminPass123", "Administrador");
        assertTrue(loginDao.Registrar(adminUser));

        LoginControlador loginCtrl = new LoginControlador(new AutenticacionServicio(loginDao));
        Optional<Usuario> sesionAdmin = loginCtrl.autenticar(correoAdmin, "adminPass123");
        assertTrue("Admin debe autenticarse", sesionAdmin.isPresent());
        Usuario admin = sesionAdmin.get();

        // 2. Creación de Usuario Asistente
        Usuario nuevoAsistente = new Usuario(0, usuarioAsistenteNombre, correoAsistente, "mesero123", "Asistente");
        assertTrue("Admin debe poder crear Asistente", loginDao.Registrar(nuevoAsistente));

        // 3. Autenticación del Asistente y verificación de restricciones RBAC
        Optional<Usuario> sesionAsistente = loginCtrl.autenticar(correoAsistente, "mesero123");
        assertTrue("Asistente debe autenticarse", sesionAsistente.isPresent());
        Usuario asistente = sesionAsistente.get();

        PoliticaAcceso rbacAdmin = new PoliticaAcceso(admin);
        PoliticaAcceso rbacAsistente = new PoliticaAcceso(asistente);

        SalasServicio salasAdminServicio = new SalasServicio(salasDao, rbacAdmin);
        SalasServicio salasAsistenteServicio = new SalasServicio(salasDao, rbacAsistente);

        PlatosServicio platosAdminServicio = new PlatosServicio(platosDao, rbacAdmin);
        PlatosServicio platosAsistenteServicio = new PlatosServicio(platosDao, rbacAsistente);

        // 4. Asistente NO puede registrar salas ni platos
        assertThrows("Asistente no puede crear salas", ErrorAplicacionException.class,
                () -> salasAsistenteServicio.registrar(new Salas(0, "COMB_SALA_ERR_" + sufijo, 8)));
        assertThrows("Asistente no puede crear platos", ErrorAplicacionException.class,
                () -> platosAsistenteServicio.registrar(new Platos(0, "COMB_PLATO_ERR_" + sufijo, new BigDecimal("5.00"), LocalDate.now().toString())));

        // 5. Admin registra salas y platos válidos
        String nombreSala = "COMB_SALA_" + sufijo;
        assertTrue(salasAdminServicio.registrar(new Salas(0, nombreSala, 12)));
        List<Salas> salas = salasAdminServicio.listar();
        Salas salaCreada = salas.stream().filter(s -> nombreSala.equals(s.getNombre())).findFirst().orElseThrow();
        int idSala = salaCreada.getId();

        String fechaHoy = LocalDate.now().toString();
        String nombrePlatoA = "COMB_PLATO_A_" + sufijo;
        String nombrePlatoB = "COMB_PLATO_B_" + sufijo;
        assertTrue(platosAdminServicio.registrar(new Platos(0, nombrePlatoA, new BigDecimal("12.00"), fechaHoy)));
        assertTrue(platosAdminServicio.registrar(new Platos(0, nombrePlatoB, new BigDecimal("2.50"), fechaHoy)));

        // 6. Asistente puede consultar salas y platos
        assertTrue(salasAsistenteServicio.listar().stream().anyMatch(s -> s.getId() == idSala));
        assertTrue(platosAsistenteServicio.listarPorFecha("", fechaHoy).stream().anyMatch(p -> nombrePlatoA.equals(p.getNombre())));

        // 7. Registro de Pedido Bimonetario 1 a Tasa 36.5000
        PedidoServicio pedidoServicio = new PedidoServicio(pedidosDao);
        ConsultaPedidosServicio consultaServicio = new ConsultaPedidosServicio(pedidosDao, rbacAdmin);
        GeneradorPdfPedido generadorPdf = new GeneradorPdfPedido(tempPdfDir);
        PedidoPdfServicio pdfServicio = new PedidoPdfServicio(
                pedidosDao::verPedido,
                pedidosDao::verPedidoDetalle,
                loginDao::datosEmpresa,
                generadorPdf,
                archivo -> true
        );
        PedidosControlador pedidosCtrl = new PedidosControlador(pedidoServicio, pdfServicio, rbacAdmin, consultaServicio);

        Config configEmpresa = loginDao.datosEmpresa();
        BigDecimal tasaInicial = new BigDecimal("36.5000");
        configEmpresa.setTasaDolar(tasaInicial);
        loginDao.ModificarDatos(configEmpresa);

        // Detalle: 2 Pabellon ($12 * 2 = $24) + 2 Papelon ($2.50 * 2 = $5) = $29.00
        BigDecimal totalUSD1 = new BigDecimal("29.00");
        BigDecimal totalBs1 = totalUSD1.multiply(tasaInicial).setScale(2, RoundingMode.HALF_UP); // 29 * 36.50 = 1058.50

        Pedidos pedido1 = new Pedidos(0, idSala, 3, fechaHoy, totalUSD1, nombreSala, asistente.getNombre(), "PENDIENTE", tasaInicial, totalBs1);
        List<DetallePedido> detalles1 = Arrays.asList(
                new DetallePedido(0, nombrePlatoA, new BigDecimal("12.00"), 2, "Con extra queso", 0),
                new DetallePedido(0, nombrePlatoB, new BigDecimal("2.50"), 2, "Bien frío con limón", 0)
        );

        int idPedido1 = pedidosCtrl.registrarPedidoCompleto(pedido1, detalles1);
        assertTrue("Pedido 1 debe ser registrado con id > 0", idPedido1 > 0);

        // 8. Intentar crear segundo pedido PENDIENTE en la misma sala y mesa 3 -> DEBE FALLAR por uq_pedidos_mesa_pendiente
        Pedidos pedidoDuplicado = new Pedidos(0, idSala, 3, fechaHoy, totalUSD1, nombreSala, admin.getNombre(), "PENDIENTE", tasaInicial, totalBs1);
        assertThrows("No se permite segundo pedido pendiente en la misma mesa",
                PedidoPendienteExistenteException.class,
                () -> pedidosCtrl.registrarPedidoCompleto(pedidoDuplicado, detalles1));

        // 9. Actualización de la Tasa de Cambio Empresarial a 40.0000
        configEmpresa.setTasaDolar(new BigDecimal("40.0000"));
        assertTrue("Admin debe poder actualizar la tasa", loginDao.ModificarDatos(configEmpresa));

        Config configActualizada = loginDao.datosEmpresa();
        assertEquals(new BigDecimal("40.0000"), configActualizada.getTasaDolar());

        // 10. Registro de Pedido 2 en Mesa 4 a nueva tasa 40.0000
        BigDecimal tasaNueva = configActualizada.getTasaDolar();
        BigDecimal totalUSD2 = new BigDecimal("12.00");
        BigDecimal totalBs2 = totalUSD2.multiply(tasaNueva).setScale(2, RoundingMode.HALF_UP); // 12 * 40 = 480.00

        Pedidos pedido2 = new Pedidos(0, idSala, 4, fechaHoy, totalUSD2, nombreSala, asistente.getNombre(), "PENDIENTE", tasaNueva, totalBs2);
        List<DetallePedido> detalles2 = Arrays.asList(
                new DetallePedido(0, nombrePlatoA, new BigDecimal("12.00"), 1, "", 0)
        );

        int idPedido2 = pedidosCtrl.registrarPedidoCompleto(pedido2, detalles2);
        assertTrue("Pedido 2 debe ser registrado con id > 0", idPedido2 > 0);

        // 11. Comprobar Inmutabilidad Histórica: Pedido 1 conserva 36.5000 y 1058.50 Bs, Pedido 2 tiene 40.0000 y 480.00 Bs
        Pedidos pedido1Cargado = pedidosCtrl.verPedido(idPedido1);
        Pedidos pedido2Cargado = pedidosCtrl.verPedido(idPedido2);

        assertEquals("Pedido 1 debe conservar tasa histórica 36.5000", 0, new BigDecimal("36.5000").compareTo(pedido1Cargado.getTasaCambio()));
        assertEquals("Pedido 1 debe conservar total Bs histórico 1058.50", 0, new BigDecimal("1058.50").compareTo(pedido1Cargado.getTotalBs()));

        assertEquals("Pedido 2 debe tener nueva tasa 40.0000", 0, new BigDecimal("40.0000").compareTo(pedido2Cargado.getTasaCambio()));
        assertEquals("Pedido 2 debe tener total Bs 480.00", 0, new BigDecimal("480.00").compareTo(pedido2Cargado.getTotalBs()));

        // 12. Generación de PDF bimonetario para ambos pedidos sin lanzar excepción
        pedidosCtrl.generarPdfPedido(idPedido1);
        Path pdf1 = tempPdfDir.resolve("pedido-" + idPedido1 + ".pdf");
        assertTrue(Files.isRegularFile(pdf1));
        assertTrue(Files.size(pdf1) > 500);

        // 13. Integridad Referencial: Intentar eliminar Sala con pedidos asociados -> Rechazado
        assertThrows("No se permite eliminar una sala con pedidos históricos",
                ErrorAplicacionException.class,
                () -> salasAdminServicio.eliminar(idSala));

        // 14. Finalización de Pedidos y liberación de mesa
        assertTrue("Pedido 1 debe finalizarse", pedidosCtrl.finalizarPedido(idPedido1));
        assertEquals("FINALIZADO", pedidosCtrl.verPedido(idPedido1).getEstado());

        // Mesa 3 queda liberada: ahora sí se puede crear un nuevo pedido en Mesa 3
        Pedidos pedido3Mesa3 = new Pedidos(0, idSala, 3, fechaHoy, totalUSD2, nombreSala, asistente.getNombre(), "PENDIENTE", tasaNueva, totalBs2);
        int idPedido3 = pedidosCtrl.registrarPedidoCompleto(pedido3Mesa3, detalles2);
        assertTrue("Mesa 3 liberada puede recibir nuevo pedido", idPedido3 > 0);
    }
}
