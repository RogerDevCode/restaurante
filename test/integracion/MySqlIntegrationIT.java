package integracion;

import Modelo.DataAccessException;
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
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import infraestructura.ConfiguracionLogs;
import infraestructura.ProveedorConexionJdbc;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Pruebas de integración: requieren MySQL de docker-compose.integration.yml. */
public class MySqlIntegrationIT {
    private static final String USUARIO_PRUEBA = "__INTEGRACION_MYSQL__";
    private static Path directorioLogs;
    private static String directorioLogsAnterior;

    @BeforeClass
    public static void comprobarBaseAisladaYPrepararLogs() throws Exception {
        String url = System.getProperty("DB_URL", "");
        assertTrue("La URL debe apuntar exclusivamente a restaurante_test en 127.0.0.1:3307",
                url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/restaurante_test(?:\\?.*)?"));
        assertEquals("restaurante_test_app", System.getProperty("DB_USER"));
        assertEquals("test_only_app_password", System.getProperty("DB_PASSWORD"));

        directorioLogsAnterior = System.getProperty("restaurante.logs.dir");
        directorioLogs = Files.createTempDirectory("restaurante-mysql-integration-logs-");
        System.setProperty("restaurante.logs.dir", directorioLogs.toString());
        ConfiguracionLogs.configurar();

        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement()) {
            assertEquals("restaurante_test", conexion.getCatalog());
            assertTrue(conexion.getMetaData().getDatabaseProductVersion().startsWith("8.4"));
            sentencia.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
            sentencia.execute("CREATE TRIGGER trg_restaurante_it_error_detalle "
                    + "BEFORE INSERT ON detalle_pedidos FOR EACH ROW "
                    + "BEGIN IF NEW.comentario = '__FALLAR_DETALLE_IT__' THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo provocado por integración'; "
                    + "END IF; END");
        }
    }

    @AfterClass
    public static void cerrarLogsYRestaurarConfiguracion() throws Exception {
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
            limpiar(conexion);
        } finally {
            for (Handler handler : Logger.getLogger("").getHandlers()) {
                handler.flush();
                handler.close();
            }
            if (directorioLogsAnterior == null) {
                System.clearProperty("restaurante.logs.dir");
            } else {
                System.setProperty("restaurante.logs.dir", directorioLogsAnterior);
            }
        }
    }

    @Before
    public void limpiarEstadoDeEjecucionesAnteriores() throws SQLException {
        try (Connection conexion = conexion()) {
            limpiar(conexion);
        }
    }

    @After
    public void limpiarDatosDeLaPrueba() throws SQLException {
        try (Connection conexion = conexion()) {
            limpiar(conexion);
        }
    }

    @Test
    public void verificaVersionCatalogoYEsquemaMysqlReal() throws SQLException {
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery("SELECT COUNT(*) FROM usuarios")) {
            assertEquals("restaurante_test", conexion.getCatalog());
            assertTrue(conexion.getMetaData().getDatabaseProductVersion().startsWith("8.4"));
            assertTrue(resultado.next());
            assertTrue(resultado.getInt(1) > 0);
        }
    }

    @Test
    public void autenticaUsuarioSemillaRechazaCredencialesYConsultaMenu() {
        establecerClaveLegacySemilla();
        Optional<Usuario> autenticado = new LoginDao().autenticar("info@angelsifuentes.com", "admin");
        assertTrue(autenticado.isPresent());
        assertEquals("Administrador", autenticado.get().getRol());
        assertTrue("La clave legacy debe migrarse tras autenticar", claveSemillaMigrada());
        assertTrue(new LoginDao().autenticar("no-existe@restaurante.test", "incorrecta").isEmpty());
        assertTrue(new PlatosDao().Listar("", LocalDate.now().toString()).size() >= 3);
    }

    @Test
    public void rechazaUnRolNoReconocidoAntesDeCrearLaSesion() {
        asignarRolSemilla("RolNoValido");
        try {
            assertTrue(new LoginDao().autenticar("info@angelsifuentes.com", "admin").isEmpty());
        } finally {
            asignarRolSemilla("Administrador");
        }
    }

    private void asignarRolSemilla(String rol) {
        try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                conexion.prepareStatement("UPDATE usuarios SET rol = ? WHERE correo = ?")) {
            sentencia.setString(1, rol);
            sentencia.setString(2, "info@angelsifuentes.com");
            assertEquals(1, sentencia.executeUpdate());
        } catch (SQLException ex) {
            throw new AssertionError("No se pudo preparar el rol de integración", ex);
        }
    }

    @Test
    public void altaGuardaHashYAutenticacionVerificaLaClaveSinTextoPlano() throws SQLException {
        String correo = "it-" + UUID.randomUUID() + "@restaurante.test";
        Usuario usuario = new Usuario();
        usuario.setNombre("Cuenta de prueba de hash");
        usuario.setCorreo(correo);
        usuario.setPassword("clave-segura-it");
        usuario.setRol("Asistente");
        try {
            assertTrue(new LoginDao().Registrar(usuario));
            String almacenada = clavePorCorreo(correo);
            assertTrue(almacenada.startsWith("pbkdf2-sha256$"));
            assertFalse(almacenada.equals("clave-segura-it"));
            assertTrue(new LoginDao().autenticar(correo, "clave-segura-it").isPresent());
            assertTrue(new LoginDao().autenticar(correo, "clave-incorrecta").isEmpty());
        } finally {
            try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                    conexion.prepareStatement("DELETE FROM usuarios WHERE correo = ?")) {
                sentencia.setString(1, correo);
                sentencia.executeUpdate();
            }
        }
    }

    private void establecerClaveLegacySemilla() {
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("UPDATE usuarios SET pass='admin' WHERE correo='info@angelsifuentes.com'");
        } catch (SQLException ex) {
            throw new AssertionError("No se pudo preparar la cuenta de integración para probar migración", ex);
        }
    }

    private boolean claveSemillaMigrada() {
        return clavePorCorreo("info@angelsifuentes.com").startsWith("pbkdf2-sha256$");
    }

    private String clavePorCorreo(String correo) {
        try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                conexion.prepareStatement("SELECT pass FROM usuarios WHERE correo = ?")) {
            sentencia.setString(1, correo);
            try (ResultSet resultado = sentencia.executeQuery()) {
                assertTrue(resultado.next());
                return resultado.getString(1);
            }
        } catch (SQLException ex) {
            throw new AssertionError("No se pudo consultar la cuenta de integración", ex);
        }
    }

    @Test
    public void correoDuplicadoEsConflictoWarningConCausaYLogUnico() throws IOException {
        int entradasAntes = contar(textoLog(), "El correo electrónico ya está registrado.");
        Usuario duplicado = new Usuario();
        duplicado.setNombre("Usuario de integración duplicado");
        duplicado.setCorreo("info@angelsifuentes.com");
        duplicado.setPassword("no-se-registra");
        duplicado.setRol("Asistente");

        ErrorAplicacionException error = org.junit.Assert.assertThrows(ErrorAplicacionException.class,
                () -> new LoginDao().Registrar(duplicado));

        assertTrue(error.getCause() instanceof SQLException);
        assertEquals(1062, ((SQLException) error.getCause()).getErrorCode());
        assertEquals(entradasAntes + 1, contar(textoLog(), "El correo electrónico ya está registrado."));
        assertTrue(textoLog().contains("WARNING: El correo electrónico ya está registrado."));
        assertFalse(textoLog().contains("SEVERE: El correo electrónico ya está registrado."));
        assertFalse(textoLog().contains("no-se-registra"));
    }

    @Test
    public void generaPdfDesdeDatosMySqlSinAbrirAplicacionExterna() throws Exception {
        int idPedido;
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(
                        "SELECT p.id FROM pedidos p INNER JOIN detalle_pedidos d ON d.id_pedido = p.id LIMIT 1")) {
            assertTrue("La semilla debe incluir un pedido con detalle", resultado.next());
            idPedido = resultado.getInt(1);
        }
        Path directorio = Files.createTempDirectory("restaurante-pdf-mysql-it-");
        Path pdf = directorio.resolve("pedido-" + idPedido + ".pdf");
        PedidoPdfServicio servicio = servicioPdfReal(directorio, archivo -> { });

        servicio.generar(idPedido);

        assertTrue(Files.isRegularFile(pdf));
        byte[] bytes = Files.readAllBytes(pdf);
        assertTrue(bytes.length > 100);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        Files.delete(pdf);
        Files.delete(directorio);
    }

    @Test
    public void falloAlAbrirPdfPreservaCausaYQuedaRegistradoUnaSolaVez() throws Exception {
        int idPedido;
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(
                        "SELECT p.id FROM pedidos p INNER JOIN detalle_pedidos d ON d.id_pedido = p.id LIMIT 1")) {
            assertTrue("La semilla debe incluir un pedido con detalle", resultado.next());
            idPedido = resultado.getInt(1);
        }
        Path directorio = Files.createTempDirectory("restaurante-pdf-apertura-it-");
        int registrosAntes = contar(textoLog(), "El PDF se generó, pero no se pudo abrir automáticamente.");
        IOException causa = new IOException("fallo de apertura inducido");
        PedidoPdfServicio servicio = servicioPdfReal(directorio, archivo -> { throw causa; });

        ErrorAplicacionException error = org.junit.Assert.assertThrows(
                ErrorAplicacionException.class, () -> servicio.generar(idPedido));

        assertSame(causa, error.getCause());
        assertEquals(registrosAntes + 1,
                contar(textoLog(), "El PDF se generó, pero no se pudo abrir automáticamente."));
        assertTrue(textoLog().contains("SEVERE: El PDF se generó, pero no se pudo abrir automáticamente."));
        assertTrue(Files.isRegularFile(directorio.resolve("pedido-" + idPedido + ".pdf")));
        Files.delete(directorio.resolve("pedido-" + idPedido + ".pdf"));
        Files.delete(directorio);
    }

    private PedidoPdfServicio servicioPdfReal(Path directorio, PedidoPdfServicio.AbridorPdf abridor) {
        PedidosDao pedidos = new PedidosDao(new ProveedorConexionJdbc());
        LoginDao configuracion = new LoginDao();
        return new PedidoPdfServicio(
                pedidos::verPedido,
                pedidos::verPedidoDetalle,
                configuracion::datosEmpresa,
                new GeneradorPdfPedido(directorio),
                abridor);
    }

    @Test
    public void ejecutaCrudRealDeSalasYPlatos() throws SQLException {
        String sufijo = UUID.randomUUID().toString();
        SalasDao salasDao = new SalasDao();
        Salas sala = new Salas();
        sala.setNombre("IT-SALA-" + sufijo);
        sala.setMesas(3);
        assertTrue(salasDao.RegistrarSala(sala));
        int idSala = idSalaPorNombre(sala.getNombre());
        sala.setId(idSala);
        sala.setNombre("IT-SALA-EDITADA-" + sufijo);
        sala.setMesas(4);
        assertTrue(salasDao.Modificar(sala));
        assertTrue(salasDao.Eliminar(idSala));

        PlatosDao platosDao = new PlatosDao();
        Platos plato = new Platos();
        plato.setNombre("IT-PLATO-" + sufijo);
        plato.setPrecioDecimal(new BigDecimal("5.25"));
        plato.setFecha(LocalDate.now().toString());
        assertTrue(platosDao.Registrar(plato));
        int idPlato = idPlatoPorNombre(plato.getNombre());
        plato.setId(idPlato);
        plato.setNombre("IT-PLATO-EDITADO-" + sufijo);
        plato.setPrecioDecimal(new BigDecimal("6.50"));
        assertTrue(platosDao.Modificar(plato));
        assertTrue(platosDao.Listar("IT-PLATO-EDITADO-" + sufijo, LocalDate.now().toString()).size() == 1);
        assertTrue(platosDao.Eliminar(idPlato));
    }

    @Test
    public void noPermiteBorrarSalaConHistorialYRegistraConflictoComoWarning() throws Exception {
        int idSala = crearSalaPrueba();
        int mesa = mesaPrueba();
        new PedidosDao().registrarPedidoCompleto(pedido(idSala, mesa), detallesValidos());
        int logsAntes = contar(textoLog(), "No se puede eliminar la sala porque tiene pedidos asociados.");

        ErrorAplicacionException error = org.junit.Assert.assertThrows(
                ErrorAplicacionException.class, () -> new SalasDao().eliminar(idSala));

        assertTrue(error.getCause() instanceof SQLException);
        assertEquals(1451, ((SQLException) error.getCause()).getErrorCode());
        assertEquals(logsAntes + 1,
                contar(textoLog(), "No se puede eliminar la sala porque tiene pedidos asociados."));
        assertTrue(textoLog().contains("WARNING: No se puede eliminar la sala porque tiene pedidos asociados."));
        assertFalse(textoLog().contains("SEVERE: No se puede eliminar la sala porque tiene pedidos asociados."));
        assertEquals(1, contarFilas("SELECT COUNT(*) FROM salas WHERE id=" + idSala));
    }

    @Test
    public void transaccionRegistraPedidoDetallesYFinalizacion() throws SQLException {
        int idSala = crearSalaPrueba();
        int mesa = mesaPrueba();
        PedidosDao dao = new PedidosDao();
        int idPedido = dao.registrarPedidoCompleto(pedido(idSala, mesa), detallesValidos());

        assertTrue(idPedido > 0);
        assertEquals(idPedido, dao.verificarStado(mesa, idSala));
        assertEquals(2, dao.verPedidoDetalle(idPedido).size());
        assertEquals(idPedido, dao.verPedido(idPedido).getId());
        assertTrue(dao.actualizarEstado(idPedido));
        assertEquals(0, dao.verificarStado(mesa, idSala));
        assertEquals("FINALIZADO", estadoPedido(idPedido));
    }

    @Test
    public void falloForzadoEnDetalleHaceRollbackYDejaUnaEntradaDeLog() throws SQLException, IOException {
        int idSala = crearSalaPrueba();
        int mesa = mesaPrueba();
        int pedidosAntes = pedidosDePrueba();
        int detallesAntes = detallesDePedidosDePrueba();
        int logsAntes = contar(textoLog(), "No se pudo guardar el pedido completo.");
        DetallePedido detalle = detallesValidos().get(0);
        detalle.setComentario("__FALLAR_DETALLE_IT__");
        Pedidos pedido = pedido(idSala, mesa);
        pedido.setTotalDecimal(new BigDecimal("20.00"));

        DataAccessException error = org.junit.Assert.assertThrows(DataAccessException.class,
                () -> new PedidosDao().registrarPedidoCompleto(pedido, Arrays.asList(detalle)));

        assertTrue(error.getCause() instanceof SQLException);
        assertEquals("45000", ((SQLException) error.getCause()).getSQLState());
        assertEquals(pedidosAntes, pedidosDePrueba());
        assertEquals(detallesAntes, detallesDePedidosDePrueba());
        assertEquals(logsAntes + 1, contar(textoLog(), "No se pudo guardar el pedido completo."));
    }

    @Test
    public void dosSesionesConcurrentesSoloDejanUnPedidoPendiente() throws Exception {
        int idSala = crearSalaPrueba();
        int mesa = mesaPrueba();
        CountDownLatch comenzar = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> intentos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                intentos.add(ejecutor.submit(() -> {
                    comenzar.await();
                    try {
                        new PedidosDao().registrarPedidoCompleto(pedido(idSala, mesa), detallesValidos());
                        return true;
                    } catch (PedidoPendienteExistenteException conflictoEsperado) {
                        return false;
                    }
                }));
            }
            comenzar.countDown();
            int exitos = 0;
            int conflictos = 0;
            for (Future<Boolean> intento : intentos) {
                if (intento.get()) {
                    exitos++;
                } else {
                    conflictos++;
                }
            }
            assertEquals(1, exitos);
            assertEquals(1, conflictos);
            assertEquals(1, pendientes(idSala, mesa));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    @Test
    public void erroresDeConexionYCredencialesSePropaganYQuedanEnLog() throws IOException {
        int logsAntes = contar(textoLog(), "No se pudo validar el usuario.");
        String urlOriginal = System.getProperty("DB_URL");
        try {
            System.setProperty("DB_URL", "jdbc:mysql://127.0.0.1:3308/restaurante_test");
            DataAccessException sinBase = org.junit.Assert.assertThrows(DataAccessException.class,
                    () -> new LoginDao().log("info@angelsifuentes.com", "admin"));
            assertTrue(sinBase.getCause() instanceof SQLException);
            assertEquals(logsAntes + 1, contar(textoLog(), "No se pudo validar el usuario."));
        } finally {
            restaurarPropiedad("DB_URL", urlOriginal);
        }

        int logsAntesClave = contar(textoLog(), "No se pudo validar el usuario.");
        String claveOriginal = System.getProperty("DB_PASSWORD");
        try {
            System.setProperty("DB_PASSWORD", "clave-incorrecta-de-integracion");
            DataAccessException credencialInvalida = org.junit.Assert.assertThrows(DataAccessException.class,
                    () -> new LoginDao().log("info@angelsifuentes.com", "admin"));
            assertTrue(credencialInvalida.getCause() instanceof SQLException);
            assertEquals(logsAntesClave + 1, contar(textoLog(), "No se pudo validar el usuario."));
        } finally {
            restaurarPropiedad("DB_PASSWORD", claveOriginal);
        }
    }

    private static Connection conexion() throws SQLException {
        return DriverManager.getConnection(System.getProperty("DB_URL"),
                System.getProperty("DB_USER"), System.getProperty("DB_PASSWORD"));
    }

    private static void restaurarPropiedad(String nombre, String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(nombre);
        } else {
            System.setProperty(nombre, valorAnterior);
        }
    }

    private static void limpiar(Connection conexion) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("DELETE d FROM detalle_pedidos d INNER JOIN pedidos p ON p.id=d.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE FROM salas WHERE nombre LIKE 'IT-SALA-%'");
        }
    }

    private int crearSalaPrueba() throws SQLException {
        Salas sala = new Salas();
        sala.setNombre("IT-SALA-" + UUID.randomUUID());
        sala.setMesas(2);
        assertTrue(new SalasDao().RegistrarSala(sala));
        return idSalaPorNombre(sala.getNombre());
    }

    private int idSalaPorNombre(String nombre) throws SQLException {
        try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                conexion.prepareStatement("SELECT id FROM salas WHERE nombre=?")) {
            sentencia.setString(1, nombre);
            try (ResultSet resultado = sentencia.executeQuery()) {
                assertTrue(resultado.next());
                return resultado.getInt(1);
            }
        }
    }

    private int idPlatoPorNombre(String nombre) throws SQLException {
        try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                conexion.prepareStatement("SELECT id FROM platos WHERE nombre=?")) {
            sentencia.setString(1, nombre);
            try (ResultSet resultado = sentencia.executeQuery()) {
                assertTrue(resultado.next());
                return resultado.getInt(1);
            }
        }
    }

    private int mesaPrueba() {
        return 10000 + (int) (Math.random() * 1000000);
    }

    private Pedidos pedido(int idSala, int mesa) {
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(idSala);
        pedido.setNum_mesa(mesa);
        pedido.setTotalDecimal(new BigDecimal("35.50"));
        pedido.setUsuario(USUARIO_PRUEBA);
        return pedido;
    }

    private List<DetallePedido> detallesValidos() {
        DetallePedido primero = new DetallePedido(0, "IT-PLATO-A", new BigDecimal("20.00"), 1, "", 0);
        DetallePedido segundo = new DetallePedido(0, "IT-PLATO-B", new BigDecimal("15.50"), 1, "", 0);
        return Arrays.asList(primero, segundo);
    }

    private String estadoPedido(int idPedido) throws SQLException {
        try (Connection conexion = conexion(); java.sql.PreparedStatement sentencia =
                conexion.prepareStatement("SELECT estado FROM pedidos WHERE id=?")) {
            sentencia.setInt(1, idPedido);
            try (ResultSet resultado = sentencia.executeQuery()) {
                assertTrue(resultado.next());
                return resultado.getString(1);
            }
        }
    }

    private int pedidosDePrueba() throws SQLException {
        return contarFilas("SELECT COUNT(*) FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
    }

    private int detallesDePedidosDePrueba() throws SQLException {
        return contarFilas("SELECT COUNT(*) FROM detalle_pedidos d JOIN pedidos p ON p.id=d.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
    }

    private int pendientes(int idSala, int mesa) throws SQLException {
        return contarFilas("SELECT COUNT(*) FROM pedidos WHERE id_sala=" + idSala
                + " AND num_mesa=" + mesa + " AND estado='PENDIENTE'");
    }

    private int contarFilas(String sql) throws SQLException {
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            assertTrue(resultado.next());
            return resultado.getInt(1);
        }
    }

    private String textoLog() throws IOException {
        Path archivo = directorioLogs.resolve("restaurante-" + LocalDate.now() + ".log");
        if (!Files.exists(archivo)) {
            return "";
        }
        return new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
    }

    private int contar(String texto, String patron) {
        int cantidad = 0;
        int indice = 0;
        while ((indice = texto.indexOf(patron, indice)) >= 0) {
            cantidad++;
            indice += patron.length();
        }
        return cantidad;
    }
}
