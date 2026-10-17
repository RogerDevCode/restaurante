package integracion;

import Modelo.Cliente;
import Modelo.ClienteDao;
import Modelo.Config;
import Modelo.CierreCaja;
import Modelo.CierreCajaDao;
import Modelo.DataAccessException;
import Modelo.DetallePedido;
import Modelo.ErrorAplicacionException;
import Modelo.EstadisticasDashboard;
import Modelo.LoginDao;
import Modelo.Mesonero;
import Modelo.MesoneroDao;
import Modelo.PedidoPendienteExistenteException;
import Modelo.Pedidos;
import Modelo.PedidosDao;
import Modelo.PagoPedido;
import Modelo.Platos;
import Modelo.PlatosDao;
import Modelo.Salas;
import Modelo.SalasDao;
import Modelo.Usuario;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.ServicioRespaldoBaseDatos;
import infraestructura.ConfiguracionLogs;
import infraestructura.DiarioRecuperacionBD;
import infraestructura.ProveedorConexionJdbc;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
import static org.junit.Assert.assertThrows;
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
        assertNotNull("El usuario de BD debe estar configurado", System.getProperty("DB_USER"));
        assertNotNull("La clave de BD debe estar configurada", System.getProperty("DB_PASSWORD"));
        assertFalse("La clave de BD no debe estar vacía", System.getProperty("DB_PASSWORD").isBlank());

        directorioLogsAnterior = System.getProperty("restaurante.logs.dir");
        directorioLogs = Files.createTempDirectory("restaurante-mysql-integration-logs-");
        System.setProperty("restaurante.logs.dir", directorioLogs.toString());
        ConfiguracionLogs.configurar();

        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement()) {
            assertEquals("restaurante_test", conexion.getCatalog());
            assertTrue(conexion.getMetaData().getDatabaseProductVersion().startsWith("8.4"));
            String tasaLegacy = valorEscalar(conexion, "SELECT CAST(tasa_dolar AS CHAR) FROM config LIMIT 1");
            String tasaKv = valorEscalar(conexion, "SELECT valor FROM configuracion_sistema WHERE clave='tasa_dolar'");
            sentencia.executeUpdate("UPDATE config SET tasa_dolar=41.1234");
            sentencia.executeUpdate("UPDATE configuracion_sistema SET valor='36.5000' WHERE clave='tasa_dolar'");
            sentencia.executeUpdate("DELETE FROM schema_migrations");
            // Primera migración y respaldo ocurren antes de instalar el trigger de fallo de estas pruebas.
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(new ProveedorConexionJdbc());
            assertEquals("El valor personalizado de config prevalece sobre un default KV anterior",
                    "41.1234", valorEscalar(conexion, "SELECT valor FROM configuracion_sistema WHERE clave='tasa_dolar'"));
            sentencia.executeUpdate("UPDATE config SET tasa_dolar=" + tasaLegacy);
            sentencia.executeUpdate("UPDATE configuracion_sistema SET valor='" + tasaKv.replace("'", "''") + "' WHERE clave='tasa_dolar'");
            sentencia.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
            sentencia.execute("CREATE TRIGGER trg_restaurante_it_error_detalle "
                    + "BEFORE INSERT ON detalle_pedidos FOR EACH ROW "
                    + "BEGIN IF NEW.comentario = '__FALLAR_DETALLE_IT__' THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo provocado por integración'; "
                    + "END IF; END");
        }
    }

    private static String valorEscalar(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(sql)) {
            assertTrue("La consulta centinela debe devolver una fila", resultado.next());
            return resultado.getString(1);
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
    public void preciosDualCurrencyEnforcedAndClosedPendingOrdersReleaseTable() throws SQLException {
        int platoId;
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "INSERT INTO platos (nombre, precio, fecha, activo, aplica_iva) VALUES ('IT-PLATO-MONEDA-BASE', NULL, CURRENT_DATE(), 1, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) { assertTrue(keys.next()); platoId = keys.getInt(1); }
        }

        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE platos SET precio_clasificado=1 WHERE id=?")) {
            ps.setInt(1, platoId);
            assertThrows(SQLException.class, ps::executeUpdate);
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE platos SET moneda_base='VES', precio_ves=60.00, precio_usd=1.50, "
                + "precio_clasificado=1, precio_incluye_iva=1 WHERE id=?")) {
            ps.setInt(1, platoId);
            assertEquals(1, ps.executeUpdate());
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE platos SET aplica_iva=0 WHERE id=?")) {
            ps.setInt(1, platoId);
            assertThrows(SQLException.class, ps::executeUpdate);
        }

        int pedidoCerrado;
        int pedidoAbierto;
        try (Connection con = conexion()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO pedidos "
                    + "(id_sala,num_mesa,tipo_pedido,total,subtotal,iva_monto,estado,usuario,cerrada_en) "
                    + "VALUES (1,987654,'MESA',60.00,60.00,0.00,'PENDIENTE',?,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, USUARIO_PRUEBA); assertEquals(1, ps.executeUpdate());
                try (ResultSet keys = ps.getGeneratedKeys()) { assertTrue(keys.next()); pedidoCerrado = keys.getInt(1); }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO pedidos "
                    + "(id_sala,num_mesa,tipo_pedido,total,subtotal,iva_monto,estado,usuario) "
                    + "VALUES (1,987654,'MESA',60.00,60.00,0.00,'PENDIENTE',?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, USUARIO_PRUEBA); assertEquals(1, ps.executeUpdate());
                try (ResultSet keys = ps.getGeneratedKeys()) { assertTrue(keys.next()); pedidoAbierto = keys.getInt(1); }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO detalle_pedidos "
                    + "(nombre,precio,cantidad,id_pedido,aplica_iva,id_plato,moneda_base,precio_incluye_iva,iva_porcentaje) "
                    + "VALUES ('IT-PLATO-MONEDA-BASE',60.00,1,?,1,?,'VES',1,16.00)")) {
                ps.setInt(1, pedidoAbierto); ps.setInt(2, platoId); assertEquals(1, ps.executeUpdate());
            }
            con.commit();
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("DELETE FROM platos WHERE id=?")) {
            ps.setInt(1, platoId);
            assertEquals("Borrar el producto debe conservar el snapshot y dejar la referencia a NULL", 1, ps.executeUpdate());
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("SELECT id_plato FROM detalle_pedidos WHERE id_pedido=?")) {
            ps.setInt(1, pedidoAbierto);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0, rs.getInt(1)); assertTrue(rs.wasNull()); }
        }
        assertTrue("La mesa cerrada pendiente debe liberar el número para otro pedido abierto", pedidoCerrado > 0 && pedidoAbierto > 0);
    }

    @Test
    public void asegurarEsquemaPreservaTotalesYEventosDePagoExistentes() throws SQLException {
        int idPedido;
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("INSERT INTO pedidos "
                + "(tipo_pedido,total,subtotal,iva_monto,subtotal_bs,iva_bs,total_bs,estado,usuario) "
                + "VALUES ('RAPIDO',77.13,66.49,10.64,2426.89,388.03,2814.92,'FINALIZADO',?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, USUARIO_PRUEBA);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) { assertTrue(keys.next()); idPedido = keys.getInt(1); }
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("INSERT INTO pedido_pagos "
                + "(id_pedido,metodo_pago,moneda,monto,tasa_cambio,monto_bs,monto_usd) VALUES (?,'EFECTIVO','VES',77.13,36.5000,2814.92,77.13)")) {
            ps.setInt(1, idPedido); assertEquals(1, ps.executeUpdate());
        }
        String antes;
        try (Connection con = conexion()) {
            antes = valorEscalar(con, "SELECT CONCAT(total,'|',subtotal,'|',iva_monto,'|',total_bs,'|', "
                    + "(SELECT monto_bs FROM pedido_pagos WHERE id_pedido=" + idPedido + " LIMIT 1)) FROM pedidos WHERE id=" + idPedido);
            infraestructura.MigradorEsquemaJdbc.asegurarEsquema(con);
        }
        try (Connection con = conexion()) {
            String despues = valorEscalar(con, "SELECT CONCAT(total,'|',subtotal,'|',iva_monto,'|',total_bs,'|', "
                    + "(SELECT monto_bs FROM pedido_pagos WHERE id_pedido=" + idPedido + " LIMIT 1)) FROM pedidos WHERE id=" + idPedido);
            assertEquals("La migración de esquema solo amplía columnas y no revaloriza ventas/pagos", antes, despues);
        }
    }

    @Test
    public void barreraEsperaEscrituraActivaYRechazaEscriturasDuranteMantenimiento() throws Exception {
        try (Connection preparar = new ProveedorConexionJdbc().getConnection()) {
            infraestructura.ControlMantenimientoBD.asegurarTablaControl(preparar);
        }
        java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        String idOperacion = UUID.randomUUID().toString();
        try (Connection escritor = new ProveedorConexionJdbc().getConnection()) {
            escritor.setAutoCommit(false);
            try (Statement st = escritor.createStatement()) {
                st.executeUpdate("UPDATE config SET tasa_dolar = tasa_dolar LIMIT 1");
            }
            Future<String> mantenimiento = executor.submit(() -> {
                try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                    return infraestructura.ControlMantenimientoBD.iniciarMantenimiento(con, "IT", idOperacion);
                }
            });
            Thread.sleep(250);
            assertFalse("El inicio de mantenimiento debe esperar el commit de la escritura en curso", mantenimiento.isDone());
            escritor.commit();
            String token = mantenimiento.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("IT:" + idOperacion, token);
            try (Connection pruebaEscritura = new ProveedorConexionJdbc().getConnection();
                 Statement st = pruebaEscritura.createStatement()) {
                boolean rechazada = false;
                try {
                    st.executeUpdate("UPDATE config SET tasa_dolar = tasa_dolar LIMIT 1");
                } catch (SQLException esperado) {
                    rechazada = true;
                }
                assertTrue("La escritura nueva debe rechazarse durante mantenimiento", rechazada);
            }
            try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                infraestructura.ControlMantenimientoBD.terminarMantenimiento(con, token);
            }
        } finally {
            executor.shutdownNow();
            try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                String estado = infraestructura.ControlMantenimientoBD.estado(con);
                if ("MANTENIMIENTO".equalsIgnoreCase(estado)) {
                    String actual = infraestructura.ControlMantenimientoBD.operacionActual(con);
                    if (actual != null && actual.startsWith("IT:")) {
                        infraestructura.ControlMantenimientoBD.terminarMantenimiento(con, actual);
                    }
                }
            }
        }
    }

    @Test
    public void respaldoRestauradoConservaControlYRecuperaDatosExactos() throws Exception {
        Path directorio = Files.createTempDirectory("restaurante-restore-it-");
        Path diarioPath = directorio.resolve("recovery.properties");
        ServicioRespaldoBaseDatos servicio = new ServicioRespaldoBaseDatos(new ProveedorConexionJdbc(),
                directorio, new DiarioRecuperacionBD(diarioPath));
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
        }
        try {
            Path respaldo = servicio.crearRespaldo(directorio);
            Properties manifiesto = new Properties();
            try (var reader = Files.newBufferedReader(respaldo.resolveSibling(respaldo.getFileName() + ".manifest"))) {
                manifiesto.load(reader);
            }
            int cantidad = Integer.parseInt(manifiesto.getProperty("tablas.cantidad"));
            for (int i = 0; i < cantidad; i++) {
                assertFalse("El control de mantenimiento nunca debe incluirse en el respaldo",
                        "restaurante_control".equalsIgnoreCase(manifiesto.getProperty("tabla." + i + ".nombre")));
            }
            String nombreAntes;
            try (Connection con = conexion()) { nombreAntes = valorEscalar(con, "SELECT nombre FROM config WHERE id=1"); }
            try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("UPDATE config SET nombre='CAMBIO_POST_RESPALDO' WHERE id=1")) {
                assertEquals(1, ps.executeUpdate());
            }
            servicio.restaurarRespaldo(respaldo);
            try (Connection con = conexion()) {
                assertEquals(nombreAntes, valorEscalar(con, "SELECT nombre FROM config WHERE id=1"));
            }
            assertFalse("El diario debe eliminarse al terminar la restauración", Files.exists(diarioPath));
            try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                assertEquals("OPERATIVO", infraestructura.ControlMantenimientoBD.estado(con));
            }
        } finally {
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
                st.execute("CREATE TRIGGER trg_restaurante_it_error_detalle "
                        + "BEFORE INSERT ON detalle_pedidos FOR EACH ROW "
                        + "BEGIN IF NEW.comentario = '__FALLAR_DETALLE_IT__' THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo provocado por integración'; "
                        + "END IF; END");
            }
            try (var paths = Files.walk(directorio)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }

    @Test
    public void inicioRecuperaUnaRestauracionInterrumpidaEnFaseAplicando() throws Exception {
        Path directorio = Files.createTempDirectory("restaurante-recovery-it-");
        DiarioRecuperacionBD diario = new DiarioRecuperacionBD(directorio.resolve("recovery.properties"));
        ServicioRespaldoBaseDatos servicio = new ServicioRespaldoBaseDatos(new ProveedorConexionJdbc(), directorio, diario);
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
        }
        try {
            Path respaldo = servicio.crearRespaldo(directorio);
            String nombreOriginal;
            try (Connection con = conexion()) { nombreOriginal = valorEscalar(con, "SELECT nombre FROM config WHERE id=1"); }
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.executeUpdate("UPDATE config SET nombre='CAMBIO_SIN_CONFIRMAR' WHERE id=1");
            }
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(respaldo)));
            DiarioRecuperacionBD.Operacion nueva = diario.nueva(respaldo.toString(), hash);
            DiarioRecuperacionBD.Operacion interrumpida = new DiarioRecuperacionBD.Operacion(
                    nueva.id(), "APLICANDO", respaldo.toString(), hash, respaldo.toString(), hash, nueva.creadaEn());
            diario.guardar(interrumpida);
            try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                infraestructura.ControlMantenimientoBD.asegurarTablaControl(con);
                infraestructura.ControlMantenimientoBD.iniciarMantenimiento(con, "RESTAURACION", nueva.id());
            }

            servicio.recuperarOperacionInterrumpida();
            try (Connection con = conexion()) {
                assertEquals(nombreOriginal, valorEscalar(con, "SELECT nombre FROM config WHERE id=1"));
            }
            assertFalse(Files.exists(diario.getArchivo()));
            try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                assertEquals("OPERATIVO", infraestructura.ControlMantenimientoBD.estado(con));
            }
        } finally {
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS trg_restaurante_it_error_detalle");
                st.execute("CREATE TRIGGER trg_restaurante_it_error_detalle "
                        + "BEFORE INSERT ON detalle_pedidos FOR EACH ROW "
                        + "BEGIN IF NEW.comentario = '__FALLAR_DETALLE_IT__' THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo provocado por integración'; "
                        + "END IF; END");
            }
            try (var paths = Files.walk(directorio)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }

    @Test
    public void autenticaUsuarioSemillaRechazaCredencialesYConsultaMenu() throws SQLException {
        establecerClaveLegacySemilla();
        String hoy = LocalDate.now().toString();
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("UPDATE platos SET fecha = ? WHERE id IN (1, 2, 3)")) {
            ps.setString(1, hoy);
            ps.executeUpdate();
        }
        Optional<Usuario> autenticado = new LoginDao().autenticar("info@angelsifuentes.com", "admin");
        assertTrue(autenticado.isPresent());
        assertEquals("Administrador", autenticado.get().getRol());
        assertTrue("La clave legacy debe migrarse tras autenticar", claveSemillaMigrada());
        assertTrue(new LoginDao().autenticar("no-existe@restaurante.test", "incorrecta").isEmpty());
        assertTrue(new PlatosDao().Listar("", hoy).size() >= 3);
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
        PedidoPdfServicio servicio = servicioPdfReal(directorio, archivo -> true);

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
    public void esquemaSinMetodoPagoNoDebeFinalizarPedidoParcialmente() throws Exception {
        int idSala = crearSalaPrueba();
        int idPedido = new PedidosDao().registrarPedidoCompleto(
                pedido(idSala, mesaPrueba()), detallesValidos());
        boolean columnaEliminada = false;
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("ALTER TABLE pedidos DROP COLUMN metodo_pago");
            columnaEliminada = true;
        }
        try {
            org.junit.Assert.assertThrows(DataAccessException.class,
                    () -> new PedidosDao().actualizarEstadoConCliente(
                            idPedido, "Cliente IT", "V-100", "TRANSFERENCIA"));
            assertEquals("Un error de esquema debe conservar el estado pendiente", "PENDIENTE",
                    estadoPedido(idPedido));
        } finally {
            if (columnaEliminada) {
                try (Connection con = conexion(); Statement st = con.createStatement()) {
                    st.execute("ALTER TABLE pedidos ADD COLUMN metodo_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'");
                }
            }
        }
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
        pedido.setSubtotal(new BigDecimal("20.00"));
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
    public void errorDeAuditoriaRevierteAnulacionDelPedido() throws Exception {
        int idSala = crearSalaPrueba();
        int idPedido = new PedidosDao().registrarPedidoCompleto(
                pedido(idSala, mesaPrueba()), detallesValidos());
        String trigger = "trg_it_error_anulacion_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON auditoria_pedidos FOR EACH ROW "
                    + "BEGIN IF NEW.accion = 'ANULACION' THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo auditoría inducido'; END IF; END");
        }
        try {
            org.junit.Assert.assertThrows(DataAccessException.class,
                    () -> new PedidosDao().anularPedidoConAuditoria(idPedido, "Prueba rollback", USUARIO_PRUEBA));
            assertEquals("PENDIENTE", estadoPedido(idPedido));
            assertEquals(0, contarFilas("SELECT COUNT(*) FROM auditoria_pedidos WHERE id_pedido=" + idPedido
                    + " AND accion='ANULACION'"));
        } finally {
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    @Test
    public void falloAlEliminarDetallesRevierteLaPurgaCompleta() throws Exception {
        int idSala = crearSalaPrueba();
        int idPedido = new PedidosDao().registrarPedidoCompleto(
                pedido(idSala, mesaPrueba()), detallesValidos());
        PedidosDao dao = new PedidosDao();
        assertTrue(dao.actualizarEstado(idPedido));
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "UPDATE pedidos SET fecha = '2000-01-01 00:00:00' WHERE id = ?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }
        String trigger = "trg_it_error_purga_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("CREATE TRIGGER " + trigger + " BEFORE DELETE ON detalle_pedidos FOR EACH ROW "
                    + "BEGIN IF OLD.id_pedido = " + idPedido + " THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo purga inducido'; END IF; END");
        }
        try {
            org.junit.Assert.assertThrows(DataAccessException.class, () -> dao.purgarPedidosFinalizados(1));
            assertEquals("FINALIZADO", estadoPedido(idPedido));
            assertEquals(2, contarFilas("SELECT COUNT(*) FROM detalle_pedidos WHERE id_pedido=" + idPedido));
        } finally {
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    @Test
    public void falloEnClaveValorRevierteActualizacionDeConfiguracionEmpresarial() throws Exception {
        LoginDao login = new LoginDao();
        Config anterior = login.datosEmpresa();
        assertNotNull(anterior);
        Config cambio = login.datosEmpresa();
        String nombreAnterior;
        BigDecimal tasaAnterior;
        String tasaClaveAnterior;
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT nombre, tasa_dolar FROM config WHERE id = ?")) {
            ps.setInt(1, anterior.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                nombreAnterior = rs.getString("nombre");
                tasaAnterior = rs.getBigDecimal("tasa_dolar");
            }
        }
        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT valor FROM configuracion_sistema WHERE clave = 'tasa_dolar'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            tasaClaveAnterior = rs.getString("valor");
        }
        cambio.setNombre("CONFIG_ROLLBACK_" + UUID.randomUUID());
        cambio.setTasaDolar(new BigDecimal("99.1234"));
        String trigger = "trg_it_error_config_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection con = conexion(); Statement st = con.createStatement()) {
            st.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON configuracion_sistema FOR EACH ROW "
                    + "BEGIN IF NEW.clave = 'tasa_dolar' THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fallo config KV inducido'; END IF; END");
        }
        try {
            org.junit.Assert.assertThrows(DataAccessException.class, () -> login.ModificarDatos(cambio));
            try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                         "SELECT nombre, tasa_dolar FROM config WHERE id = ?")) {
                ps.setInt(1, anterior.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(nombreAnterior, rs.getString("nombre"));
                    assertEquals(0, tasaAnterior.compareTo(rs.getBigDecimal("tasa_dolar")));
                }
            }
            try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                    "SELECT valor FROM configuracion_sistema WHERE clave = 'tasa_dolar'");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(tasaClaveAnterior, rs.getString("valor"));
            }
        } finally {
            try (Connection con = conexion(); Statement st = con.createStatement()) {
                st.execute("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
        try {
            assertTrue("La actualización debe confirmar ambas tablas después de retirar el fallo",
                    login.ModificarDatos(cambio));
            try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                    "SELECT tasa_dolar FROM config WHERE id = ?")) {
                ps.setInt(1, anterior.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, new BigDecimal("99.1234").compareTo(rs.getBigDecimal("tasa_dolar")));
                }
            }
            try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement(
                    "SELECT valor FROM configuracion_sistema WHERE clave = 'tasa_dolar'");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("99.1234", rs.getString("valor"));
            }
        } finally {
            assertTrue("La configuración previa de pruebas debe restaurarse", login.ModificarDatos(anterior));
        }
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

    @Test
    public void metodosDeEfectivoSeGuardanSeVuelvenALeerYConciliacionSumaPagoMixto() throws Exception {
        int idSala = crearSalaPrueba(3);
        PedidosDao pedidosDao = new PedidosDao();
        List<String> metodos = List.of("EFECTIVO_BS", "EFECTIVO_USD", "MIXTO");
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < metodos.size(); i++) {
            int id = pedidosDao.registrarPedidoCompleto(pedido(idSala, i + 1), detallesValidos());
            assertTrue(id > 0);
            ids.add(id);
        }

        assertTrue(pedidosDao.actualizarEstadoConCliente(ids.get(0), "Cliente", "V-101", "EFECTIVO_BS"));
        assertTrue(pedidosDao.actualizarEstadoConCliente(ids.get(1), "Cliente", "V-102", "EFECTIVO_USD"));
        assertTrue(pedidosDao.actualizarEstadoConCliente(ids.get(2), "Cliente", "V-103", "MIXTO",
                new BigDecimal("1295.38"), new BigDecimal("0.01")));

        Pedidos efectivoBs = pedidosDao.verPedido(ids.get(0));
        Pedidos efectivoUsd = pedidosDao.verPedido(ids.get(1));
        Pedidos mixto = pedidosDao.verPedido(ids.get(2));
        assertEquals("EFECTIVO_BS", efectivoBs.getMetodoPago());
        assertEquals(0, efectivoBs.getEfectivoBs().compareTo(efectivoBs.getTotalBs()));
        assertEquals("EFECTIVO_USD", efectivoUsd.getMetodoPago());
        assertEquals(0, efectivoUsd.getEfectivoUsd().compareTo(efectivoUsd.getTotalDecimal()));
        assertEquals("MIXTO", mixto.getMetodoPago());
        assertEquals(new BigDecimal("1295.38"), mixto.getEfectivoBs());
        assertEquals(new BigDecimal("0.01"), mixto.getEfectivoUsd());

        Config config = new Config();
        config.setTasaDolar(new BigDecimal("36.5000"));
        String fechaCierre = efectivoBs.getFecha().substring(0, 10);
        CierreCaja cierre = new CierreCajaDao().consultarCierre(
                fechaCierre, CierreCaja.TipoCierre.TOTAL, "Admin", config);
        assertEquals(new BigDecimal("2591.13"), cierre.getTotalEfectivoBs());
        assertEquals(new BigDecimal("35.51"), cierre.getTotalEfectivoUsd());
        assertEquals(0, cierre.getPagosMixtosSinDesglose());
    }

    @Test
    public void pagoInlineRegistraBsUsdYVariosMediosConLaTasaDelPedido() throws Exception {
        int idSala = crearSalaPrueba(1);
        PedidosDao pedidosDao = new PedidosDao();
        int idPedido = pedidosDao.registrarPedidoCompleto(pedido(idSala, 1), detallesValidos());
        Pedidos pendiente = pedidosDao.verPedido(idPedido);
        BigDecimal tasa = pendiente.getTasaCambio();
        BigDecimal totalBs = pendiente.getTotalBs();
        BigDecimal efectivoUsd = new BigDecimal("0.50");
        BigDecimal transferenciaBs = new BigDecimal("10.00");
        BigDecimal efectivoBs = totalBs.subtract(efectivoUsd.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP))
                .subtract(transferenciaBs).setScale(2, java.math.RoundingMode.HALF_UP);
        List<PagoPedido> pagos = List.of(
                new PagoPedido("TRANSFERENCIA", "VES", transferenciaBs),
                new PagoPedido("EFECTIVO_USD", "USD", efectivoUsd),
                new PagoPedido("EFECTIVO_BS", "VES", efectivoBs));

        assertTrue(pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente IT", "V-810", "MIXTO", pagos));
        Pedidos finalizado = pedidosDao.verPedido(idPedido);
        assertEquals("MIXTO", finalizado.getMetodoPago());
        assertEquals(3, finalizado.getPagos().size());
        assertEquals(0, finalizado.getTasaCambio().compareTo(tasa));
        assertEquals("TRANSFERENCIA", finalizado.getPagos().get(0).metodo());
        assertEquals("VES", finalizado.getPagos().get(0).moneda());
        assertEquals(0, finalizado.getPagos().get(0).monto().compareTo(transferenciaBs));
        assertEquals(0, finalizado.getPagos().get(0).equivalenteBs(finalizado.getTasaCambio()).compareTo(transferenciaBs));

        assertEquals("EFECTIVO_USD", finalizado.getPagos().get(1).metodo());
        assertEquals("USD", finalizado.getPagos().get(1).moneda());
        assertEquals(0, finalizado.getPagos().get(1).monto().compareTo(efectivoUsd));
        BigDecimal esperadoUsdEnBs = efectivoUsd.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, finalizado.getPagos().get(1).equivalenteBs(finalizado.getTasaCambio()).compareTo(esperadoUsdEnBs));

        assertEquals("EFECTIVO_BS", finalizado.getPagos().get(2).metodo());
        assertEquals("VES", finalizado.getPagos().get(2).moneda());
        assertEquals(0, finalizado.getPagos().get(2).monto().compareTo(efectivoBs));
        assertEquals(0, finalizado.getPagos().get(2).equivalenteBs(finalizado.getTasaCambio()).compareTo(efectivoBs));

        BigDecimal sumaEquivalentesBs = finalizado.getPagos().stream()
                .map(p -> p.equivalenteBs(finalizado.getTasaCambio()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sumaEquivalentesBs.compareTo(totalBs));

        try (Connection con = conexion();
             PreparedStatement ps = con.prepareStatement("SELECT tasa_cambio FROM pedido_pagos WHERE id_pedido = ?")) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertEquals(0, rs.getBigDecimal(1).compareTo(tasa));
                }
                assertEquals(3, count);
            }
        }
        assertEquals(0, finalizado.getEfectivoBs().compareTo(efectivoBs));
        assertEquals(0, finalizado.getEfectivoUsd().compareTo(efectivoUsd));

        Config config = new Config();
        config.setTasaDolar(new BigDecimal("36.5000"));
        String fechaCierre = finalizado.getFecha().substring(0, 10);
        CierreCaja cierre = new CierreCajaDao().consultarCierre(
                fechaCierre, CierreCaja.TipoCierre.TOTAL, "Admin", config);
        assertEquals(0, cierre.getTotalEfectivoBs().compareTo(efectivoBs));
        assertEquals(0, cierre.getTotalEfectivoUsd().compareTo(efectivoUsd));
    }

    @Test
    public void pagoIncompletoOConTasaAusenteNoFinalizaNiGuardaPagos() throws Exception {
        int idSala = crearSalaPrueba(2);
        PedidosDao pedidosDao = new PedidosDao();
        int idPedido = pedidosDao.registrarPedidoCompleto(pedido(idSala, 1), detallesValidos());
        Pedidos pendiente = pedidosDao.verPedido(idPedido);
        BigDecimal totalBs = pendiente.getTotalBs();
        List<PagoPedido> incompleto = List.of(new PagoPedido("EFECTIVO_BS", "VES", totalBs.subtract(new BigDecimal("0.01"))));
        assertThrows(ErrorAplicacionException.class,
                () -> pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente IT", "V-811", "EFECTIVO_BS", incompleto));
        assertEquals("PENDIENTE", estadoPedido(idPedido));
        assertEquals(0, pedidosDao.verPedido(idPedido).getPagos().size());

        try (Connection con = conexion(); PreparedStatement ps = con.prepareStatement("UPDATE pedidos SET tasa_cambio=0 WHERE id=?")) {
            ps.setInt(1, idPedido);
            assertEquals(1, ps.executeUpdate());
        }
        List<PagoPedido> exactoEnBs = List.of(new PagoPedido("EFECTIVO_BS", "VES", totalBs));
        assertThrows(ErrorAplicacionException.class,
                () -> pedidosDao.actualizarEstadoConCliente(idPedido, "Cliente IT", "V-811", "EFECTIVO_BS", exactoEnBs));
        assertEquals("PENDIENTE", estadoPedido(idPedido));
        assertEquals(0, pedidosDao.verPedido(idPedido).getPagos().size());
    }

    @Test
    public void testIntegracionClientesYDashboard() throws Exception {
        ClienteDao clienteDao = new ClienteDao();
        LoginDao loginDao = new LoginDao();
        PedidosDao pedidosDao = new PedidosDao();
        PlatosDao platosDao = new PlatosDao();

        // 1. Probar configuración de cliente por defecto
        Config config = loginDao.datosEmpresa();
        assertNotNull(config);
        config.setClienteDefaultNombre("Consumidor Empresa IT");
        config.setClienteDefaultDoc("J-99988877-1");
        assertTrue(loginDao.ModificarDatos(config));

        Config recargada = loginDao.datosEmpresa();
        assertEquals("Consumidor Empresa IT", recargada.getClienteDefaultNombre());
        assertEquals("J-99988877-1", recargada.getClienteDefaultDoc());

        // 2. Registrar/actualizar cliente explícito
        String docCliente = "V-" + (10000000 + (int)(Math.random() * 80000000));
        Cliente cliente = new Cliente(0, docCliente, "Cliente Integración VIP", "0412-1112233", "Caracas");
        assertTrue(clienteDao.guardarOActualizar(cliente));

        List<Cliente> encontrados = clienteDao.buscarClientes("Integración");
        assertFalse(encontrados.isEmpty());
        Cliente encontrado = encontrados.stream()
                .filter(c -> docCliente.equals(c.getDocumento()))
                .findFirst()
                .orElse(null);
        assertNotNull(encontrado);
        assertEquals("Cliente Integración VIP", encontrado.getNombre());

        // 3. Crear pedido y finalizar con asociación de cliente
        int idSala = crearSalaPrueba();
        int mesa = mesaPrueba();
        Platos plato = new Platos();
        plato.setNombre("IT-PLATO-DASH-" + UUID.randomUUID());
        plato.setPrecioDecimal(new BigDecimal("25.00"));
        plato.setPrecioUsd(new BigDecimal("25.00"));
        plato.setMonedaBase("USD");
        plato.setAplicaIva(true);
        plato.setPrecioIncluyeIva(false);
        plato.setPrecioClasificado(true);
        plato.setFecha(LocalDate.now().toString());
        assertTrue(platosDao.Registrar(plato));
        int idPlato = idPlatoPorNombre(plato.getNombre());

        DetallePedido detalle = new DetallePedido(0, plato.getNombre(), new BigDecimal("25.00"), 2, "", 0);
        detalle.setIdPlato(idPlato);
        detalle.setMonedaBase("USD");
        detalle.setAplicaIva(true);
        detalle.setPrecioIncluyeIva(false);
        detalle.setIvaPorcentaje(new BigDecimal("16.00"));

        Pedidos ped = new Pedidos();
        ped.setId_sala(idSala);
        ped.setNum_mesa(mesa);
        ped.setSubtotal(new BigDecimal("50.00"));
        ped.setIvaPorcentaje(new BigDecimal("16.00"));
        ped.setIvaMonto(new BigDecimal("8.00"));
        ped.setTotalDecimal(new BigDecimal("58.00"));
        ped.setSubtotalBs(new BigDecimal("1825.00"));
        ped.setIvaBs(new BigDecimal("292.00"));
        ped.setTotalBs(new BigDecimal("2117.00"));
        ped.setTasaCambio(new BigDecimal("36.5000"));
        ped.setUsuario(USUARIO_PRUEBA);

        int idPedido = pedidosDao.registrarPedidoCompleto(ped, List.of(detalle));
        assertTrue(idPedido > 0);

        // Finalizar con datos del cliente
        boolean finalizado = pedidosDao.actualizarEstadoConCliente(idPedido, cliente.getNombre(), cliente.getDocumento());
        assertTrue(finalizado);

        // Verificar lectura del pedido con datos del cliente
        Pedidos pedConsultado = pedidosDao.verPedido(idPedido);
        assertNotNull(pedConsultado);
        assertEquals("FINALIZADO", pedConsultado.getEstado());
        assertEquals(cliente.getNombre(), pedConsultado.getClienteNombre());
        assertEquals(cliente.getDocumento(), pedConsultado.getClienteDocumento());

        // 4. Listar facturas del cliente (debe estar ordenada descendente)
        List<Pedidos> facturas = clienteDao.listarFacturasCliente(encontrado.getDocumento());
        assertFalse("Debe listar facturas para el cliente", facturas.isEmpty());
        assertEquals(idPedido, facturas.get(0).getId());

        // 5. Estadísticas del dashboard
        EstadisticasDashboard stats = clienteDao.obtenerEstadisticasDashboard();
        assertNotNull(stats);
        assertTrue(stats.getTotalClientes() > 0);
        assertTrue(stats.getVentasHistoricasDolares().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void cierreCuadraDesgloseConCabeceraYUsaTasaDeBdParaPedidoLegado() throws Exception {
        int idSala = crearSalaPrueba(4);
        PedidosDao pedidosDao = new PedidosDao();
        List<Integer> ids = new ArrayList<>();
        String[] metodos = {"EFECTIVO_BS", "EFECTIVO_USD", "EFECTIVO_BS", "EFECTIVO_USD"};
        for (int i = 0; i < metodos.length; i++) {
            int id = pedidosDao.registrarPedidoCompleto(pedido(idSala, i + 1), detallesValidos());
            assertTrue(id > 0);
            assertTrue(pedidosDao.actualizarEstadoConCliente(id, "Cliente IT", "V-95" + i, metodos[i]));
            ids.add(id);
        }

        // Pedido legado: sin total_bs y tasa_cambio=0 -> el cierre debe usar la tasa de la BD (config) redondeada por pedido
        try (Connection con = conexion();
             PreparedStatement ps = con.prepareStatement("UPDATE pedidos SET total_bs=NULL, tasa_cambio=0 WHERE id=?")) {
            ps.setInt(1, ids.get(0));
            assertEquals(1, ps.executeUpdate());
        }

        Config config = new Config();
        config.setTasaDolar(new BigDecimal("36.5000"));
        String fechaCierre = pedidosDao.verPedido(ids.get(0)).getFecha().substring(0, 10);
        CierreCaja cierre = new CierreCajaDao().consultarCierre(fechaCierre, CierreCaja.TipoCierre.TOTAL, "Admin", config);

        BigDecimal metUsd = BigDecimal.ZERO;
        BigDecimal metBs = BigDecimal.ZERO;
        for (CierreCaja.ResumenMetodoPago r : cierre.getDesgloseMetodos()) {
            metUsd = metUsd.add(r.getMontoUsd());
            metBs = metBs.add(r.getMontoBs());
        }
        BigDecimal salUsd = BigDecimal.ZERO;
        BigDecimal salBs = BigDecimal.ZERO;
        for (CierreCaja.ResumenSala r : cierre.getDesgloseSalas()) {
            salUsd = salUsd.add(r.getMontoUsd());
            salBs = salBs.add(r.getMontoBs());
        }

        assertEquals("Suma de metodos USD debe igualar la cabecera", 0, cierre.getTotalVentasUsd().compareTo(metUsd));
        assertEquals("Suma de metodos Bs debe igualar la cabecera", 0, cierre.getTotalVentasBs().compareTo(metBs));
        assertEquals("Suma de salas USD debe igualar la cabecera", 0, cierre.getTotalVentasUsd().compareTo(salUsd));
        assertEquals("Suma de salas Bs debe igualar la cabecera", 0, cierre.getTotalVentasBs().compareTo(salBs));
        assertTrue("El cierre debe incluir ventas en Bs positivas", cierre.getTotalVentasBs().compareTo(BigDecimal.ZERO) > 0);
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
            sentencia.executeUpdate("DELETE m FROM pedido_saldos_movimientos m INNER JOIN pedidos p ON p.id=m.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE pg FROM pedido_pagos pg INNER JOIN pedidos p ON p.id=pg.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE l FROM pedido_liquidaciones l INNER JOIN pedidos p ON p.id=l.id_pedido WHERE p.usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE FROM auditoria_cambios_precio WHERE actor='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE FROM pedidos WHERE usuario='" + USUARIO_PRUEBA + "'");
            sentencia.executeUpdate("DELETE FROM salas WHERE nombre LIKE 'IT-SALA-%'");
            sentencia.executeUpdate("DELETE FROM platos WHERE nombre LIKE 'IT-PLATO-%'");
            sentencia.executeUpdate("DELETE FROM clientes WHERE documento LIKE 'V-%' OR documento LIKE 'J-%'");
        }
    }

    private int crearSalaPrueba() throws SQLException {
        return crearSalaPrueba(2);
    }

    private int crearSalaPrueba(int mesas) throws SQLException {
        Salas sala = new Salas();
        sala.setNombre("IT-SALA-" + UUID.randomUUID());
        sala.setMesas(mesas);
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

    @Test
    public void mesoneroCrudYVacacionesYSoftDelete() {
        MesoneroDao dao = new MesoneroDao();
        String cedulaUnica = "V-IT-" + System.currentTimeMillis();
        Mesonero m = new Mesonero("Mesonero IT Test", cedulaUnica, "04141112233", true);

        int id = dao.registrar(m);
        assertTrue("El ID generado debe ser mayor a 0", id > 0);

        Mesonero recuperado = dao.buscarPorId(id);
        assertNotNull(recuperado);
        assertEquals("Mesonero IT Test", recuperado.getNombreCompleto());
        assertTrue(recuperado.isActivo());
        assertFalse(recuperado.isEliminado());

        // Modificar
        recuperado.setNombreCompleto("Mesonero IT Modificado");
        recuperado.setTelefono("04149998877");
        assertTrue(dao.modificar(recuperado));
        assertEquals("Mesonero IT Modificado", dao.buscarPorId(id).getNombreCompleto());

        // Alternar a vacaciones / inactivo
        assertTrue(dao.cambiarActivo(id, false));
        assertFalse(dao.buscarPorId(id).isActivo());
        assertFalse("No debe aparecer en listarActivos() mientras esté de vacaciones",
                dao.listarActivos().stream().anyMatch(mes -> mes.getId() == id));
        assertTrue("Debe aparecer en listarTodos()",
                dao.listarTodos().stream().anyMatch(mes -> mes.getId() == id));

        // Reactivar
        assertTrue(dao.cambiarActivo(id, true));
        assertTrue(dao.listarActivos().stream().anyMatch(mes -> mes.getId() == id));

        // Soft-delete
        assertTrue(dao.eliminarLogico(id));
        assertFalse("No debe aparecer en listarActivos()",
                dao.listarActivos().stream().anyMatch(mes -> mes.getId() == id));
        assertFalse("No debe aparecer en listarTodos() tras eliminación lógica",
                dao.listarTodos().stream().anyMatch(mes -> mes.getId() == id));
    }

    @Test
    public void pedidoConMesoneroAsignadoSeRegistraYConsultaMesasPendientes() throws Exception {
        MesoneroDao mDao = new MesoneroDao();
        PedidosDao pDao = new PedidosDao();
        int idSala = crearSalaPrueba();

        String cedula = "V-PED-" + System.currentTimeMillis();
        Mesonero m = new Mesonero("Mesonero Pedido IT", cedula, "04120000000", true);
        int idMesonero = mDao.registrar(m);

        int mesa = mesaPrueba();
        Pedidos p = pedido(idSala, mesa);
        p.setIdMesonero(idMesonero);
        p.setMesoneroNombre("Mesonero Pedido IT");

        int idPedido = pDao.registrarPedidoCompleto(p, detallesValidos());
        assertTrue(idPedido > 0);

        // Consultar mesas pendientes con mesoneros
        java.util.Map<Integer, String> mesonerosPendientes = pDao.consultarMesonerosMesasPendientes(idSala);
        assertEquals("Mesonero Pedido IT", mesonerosPendientes.get(mesa));

        // Finalizar y verificar estadísticas dashboard
        pDao.actualizarEstadoConCliente(idPedido, "Cliente Mesonero", "V-12345");
        ClienteDao cDao = new ClienteDao();
        EstadisticasDashboard stats = cDao.obtenerEstadisticasDashboard();
        assertNotNull(stats.getEstadisticasMesoneros());
        assertTrue("Las estadísticas deben incluir al mesonero",
                stats.getEstadisticasMesoneros().stream()
                        .anyMatch(item -> item.getNombre().contains("Mesonero Pedido IT")));

        // Limpiar mesonero
        mDao.eliminarLogico(idMesonero);
    }

    private int mesaPrueba() {
        // Mesas validas: las salas de prueba se crean con 2 mesas (regla 1..mesas).
        return 1 + (int) (Math.random() * 2);
    }

    private Pedidos pedido(int idSala, int mesa) {
        Pedidos pedido = new Pedidos();
        pedido.setId_sala(idSala);
        pedido.setNum_mesa(mesa);
        pedido.setSubtotal(new BigDecimal("35.50"));
        pedido.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));
        pedido.setIvaMonto(BigDecimal.ZERO.setScale(2));
        pedido.setTotalDecimal(new BigDecimal("35.50"));
        pedido.setTasaCambio(new BigDecimal("36.5000"));
        pedido.setUsuario(USUARIO_PRUEBA);
        return pedido;
    }

    private List<DetallePedido> detallesValidos() {
        DetallePedido primero = new DetallePedido(0, "IT-PLATO-A", new BigDecimal("20.00"), 1, "", 0);
        primero.setMonedaBase("USD");
        primero.setAplicaIva(false);
        primero.setPrecioIncluyeIva(false);
        primero.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));

        DetallePedido segundo = new DetallePedido(0, "IT-PLATO-B", new BigDecimal("15.50"), 1, "", 0);
        segundo.setMonedaBase("USD");
        segundo.setAplicaIva(false);
        segundo.setPrecioIncluyeIva(false);
        segundo.setIvaPorcentaje(BigDecimal.ZERO.setScale(2));
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
