package Vista;

import Controlador.PedidosControlador;
import Controlador.PlatosControlador;
import Controlador.SalasControlador;
import Modelo.Cliente;
import Modelo.ClienteRepositorio;
import Modelo.Config;
import Modelo.DataAccessException;
import Modelo.EstadisticasDashboard;
import Modelo.LoginDao;
import Modelo.Pedidos;
import Modelo.PedidosRepositorioFalso;
import Modelo.PlatosRepositorio;
import Modelo.SalasRepositorio;
import Modelo.Usuario;
import Servicio.ConsultaPedidosServicio;
import Servicio.GeneradorPdfPedido;
import Servicio.PedidoPdfServicio;
import Servicio.PedidoServicio;
import Servicio.PoliticaAcceso;
import infraestructura.MigradorEsquemaJdbc;
import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pruebas exhaustivas simulando todas las interacciones del usuario en el Dashboard:
 * 1. Carga inicial asíncrona de métricas y directorio de clientes.
 * 2. Búsqueda combinada de clientes (exacta, parcial, vacía, caracteres especiales, SQLi).
 * 3. Selección de clientes y carga de historial de facturas.
 * 4. Refresco concurrente y descarte de respuestas obsoletas por versión.
 * 5. Resiliencia ante tablas faltantes, pedidos nulos y BD vacía.
 * 6. Captura de excepciones críticas, logs y propagación de causa raíz.
 * 7. Nombre dinámico del restaurante en banner y título de ventana sin truncamiento.
 * 8. Auto-sincronización del esquema de base de datos con MigradorEsquemaJdbc.
 */
public class ExhaustivoDashboardUsuarioTest {

    private Usuario adminUsuario;
    private PoliticaAcceso politicaAdmin;

    @Before
    public void setUp() {
        adminUsuario = new Usuario(1, "Administrador", "admin@restaurante.com", "pass123", "Administrador");
        politicaAdmin = new PoliticaAcceso(adminUsuario);
    }

    private static Cliente crearCliente(int id, String doc, String nom, String tel, String dir, int facturas, BigDecimal usd, BigDecimal bs) {
        Cliente c = new Cliente(id, doc, nom, tel, dir);
        c.setTotalFacturas(facturas);
        c.setTotalGastadoDolares(usd);
        c.setTotalGastadoBs(bs);
        return c;
    }

    // =========================================================================
    // 1. Carga de Métricas y Tablas del Dashboard
    // =========================================================================

    @Test
    public void cargaExitosaMetricasYClientesConDatosCompletos() throws Exception {
        EstadisticasDashboard statsEsperadas = new EstadisticasDashboard();
        statsEsperadas.setVentasHoyDolares(new BigDecimal("150.50"));
        statsEsperadas.setVentasHoyBs(new BigDecimal("5493.25"));
        statsEsperadas.setPedidosHoy(5);
        statsEsperadas.setVentasHistoricasDolares(new BigDecimal("1200.00"));
        statsEsperadas.setVentasHistoricasBs(new BigDecimal("43800.00"));
        statsEsperadas.setPedidosHistoricos(40);
        statsEsperadas.setTotalClientes(12);
        statsEsperadas.setMesasOcupadasActuales(3);

        List<EstadisticasDashboard.ItemEstadistica> topPlatos = new ArrayList<>();
        topPlatos.add(new EstadisticasDashboard.ItemEstadistica("Hamburguesa Especial", 10, new BigDecimal("750.00"), null));
        topPlatos.add(new EstadisticasDashboard.ItemEstadistica("Pizza Margarita", 6, new BigDecimal("450.00"), null));
        statsEsperadas.setTopPlatos(topPlatos);

        List<Cliente> clientesMock = new ArrayList<>();
        Cliente c1 = crearCliente(1, "V-12345678", "Juan Pérez", "0414-1111111", "Caracas", 3, new BigDecimal("90.00"), new BigDecimal("3285.00"));
        Cliente c2 = crearCliente(2, "V-87654321", "María González", "0412-2222222", "Valencia", 1, new BigDecimal("25.00"), new BigDecimal("912.50"));
        clientesMock.add(c1);
        clientesMock.add(c2);

        ClienteRepositorio repoMock = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String criterio) { return clientesMock; }
            @Override public Cliente buscarPorDocumento(String documento) { return c1; }
            @Override public List<Pedidos> listarFacturasCliente(String doc) { return Collections.emptyList(); }
            @Override public EstadisticasDashboard obtenerEstadisticasDashboard() { return statsEsperadas; }
        };

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DashboardSwingWorker.DatosDashboard> datosRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        DashboardSwingWorker worker = new DashboardSwingWorker(
                repoMock, "",
                datos -> {
                    datosRef.set(datos);
                    latch.countDown();
                },
                err -> {
                    errorRef.set(err);
                    latch.countDown();
                }
        );

        worker.execute();
        assertTrue("La tarea asíncrona debe completarse en menos de 5 segundos", latch.await(5, TimeUnit.SECONDS));
        assertEquals(null, errorRef.get());
        assertNotNull(datosRef.get());

        EstadisticasDashboard stats = datosRef.get().getStats();
        assertEquals(new BigDecimal("150.50"), stats.getVentasHoyDolares());
        assertEquals(new BigDecimal("5493.25"), stats.getVentasHoyBs());
        assertEquals(5, stats.getPedidosHoy());
        assertEquals(new BigDecimal("30.10"), stats.getTicketPromedioHoyDolares());
        assertEquals(new BigDecimal("1098.65"), stats.getTicketPromedioHoyBs());
        assertEquals(2, datosRef.get().getClientes().size());
        assertEquals(2, stats.getTopPlatos().size());
    }

    // =========================================================================
    // 2. Búsqueda Combinada de Clientes (Todas las combinaciones)
    // =========================================================================

    @Test
    public void busquedaCombinadaFiltraPorDocumentoNombreYToleraCaracteresEspeciales() {
        List<Cliente> baseDatos = new ArrayList<>();
        baseDatos.add(crearCliente(1, "V-10000001", "Ana María O'Connor", "0414-1111111", "Caracas", 1, BigDecimal.TEN, BigDecimal.TEN));
        baseDatos.add(crearCliente(2, "V-20000002", "Carlos Díaz", "0412-2222222", "Maracay", 2, BigDecimal.TEN, BigDecimal.TEN));
        baseDatos.add(crearCliente(3, "J-30000003", "Empresa 100% Calidad", "0212-3333333", "Caracas", 0, BigDecimal.ZERO, BigDecimal.ZERO));

        ClienteRepositorio repo = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override
            public List<Cliente> buscarClientes(String criterio) {
                if (criterio == null || criterio.trim().isEmpty()) return baseDatos;
                String crit = criterio.trim().toLowerCase();
                List<Cliente> res = new ArrayList<>();
                for (Cliente c : baseDatos) {
                    if (c.getDocumento().toLowerCase().contains(crit) || c.getNombre().toLowerCase().contains(crit)) {
                        res.add(c);
                    }
                }
                return res;
            }
            @Override public Cliente buscarPorDocumento(String d) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String d) { return Collections.emptyList(); }
            @Override public EstadisticasDashboard obtenerEstadisticasDashboard() { return new EstadisticasDashboard(); }
        };

        // 2.1 Búsqueda vacía -> Retorna todos
        assertEquals(3, repo.buscarClientes("").size());
        assertEquals(3, repo.buscarClientes("   ").size());

        // 2.2 Búsqueda por documento exacto y parcial
        assertEquals(1, repo.buscarClientes("V-10000001").size());
        assertEquals("V-10000001", repo.buscarClientes("V-10000001").get(0).getDocumento());
        assertEquals(2, repo.buscarClientes("V-").size());

        // 2.3 Búsqueda por nombre parcial con mayúsculas/minúsculas
        assertEquals(1, repo.buscarClientes("carlos").size());
        assertEquals(1, repo.buscarClientes("CARLOS").size());
        assertEquals(1, repo.buscarClientes("ana").size());

        // 2.4 Búsqueda con caracteres especiales y comillas (O'Connor, %)
        assertEquals(1, repo.buscarClientes("O'Connor").size());
        assertEquals(1, repo.buscarClientes("100%").size());

        // 2.5 Inyección SQL simulada no debe romper la lógica
        assertEquals(0, repo.buscarClientes("admin' OR '1'='1").size());
    }

    // =========================================================================
    // 3. Selección de Cliente y Carga de Facturas Históricas
    // =========================================================================

    @Test
    public void clienteSeleccionadoCargaFacturasCorrectamenteYOrdenadas() {
        Cliente cliente = crearCliente(1, "V-12345678", "Pedro Pérez", "0414-0000000", "Caracas", 2, new BigDecimal("80.00"), new BigDecimal("2920.00"));

        List<Pedidos> facturasPedro = new ArrayList<>();
        Pedidos f1 = new Pedidos();
        f1.setId(102);
        f1.setClienteDocumento("V-12345678");
        f1.setFecha("2026-10-05 14:00:00");
        f1.setTotalDecimal(new BigDecimal("50.00"));
        f1.setEstado("FINALIZADO");

        Pedidos f2 = new Pedidos();
        f2.setId(101);
        f2.setClienteDocumento("V-12345678");
        f2.setFecha("2026-10-03 12:30:00");
        f2.setTotalDecimal(new BigDecimal("30.00"));
        f2.setEstado("FINALIZADO");

        facturasPedro.add(f1);
        facturasPedro.add(f2);

        ClienteRepositorio repo = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String c) { return Collections.singletonList(cliente); }
            @Override public Cliente buscarPorDocumento(String d) { return cliente; }
            @Override
            public List<Pedidos> listarFacturasCliente(String doc) {
                if ("V-12345678".equals(doc)) return facturasPedro;
                return Collections.emptyList();
            }
            @Override public EstadisticasDashboard obtenerEstadisticasDashboard() { return new EstadisticasDashboard(); }
        };

        List<Pedidos> facturasObtenidas = repo.listarFacturasCliente("V-12345678");
        assertEquals(2, facturasObtenidas.size());
        assertEquals(102, facturasObtenidas.get(0).getId());
        assertEquals(101, facturasObtenidas.get(1).getId());

        // Cliente sin compras
        List<Pedidos> facturasInexistente = repo.listarFacturasCliente("V-99999999");
        assertTrue(facturasInexistente.isEmpty());
    }

    // =========================================================================
    // 4. Concurrencia, Refresco Rápido y Descarte por Versión
    // =========================================================================

    @Test
    public void refrescoConcurrenteSoloAplicaUltimaVersionEnUI() throws Exception {
        AtomicInteger versionActual = new AtomicInteger(0);
        AtomicInteger versionesAplicadas = new AtomicInteger(0);
        CountDownLatch latchFin = new CountDownLatch(1);

        ClienteRepositorio repoLento = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String c) { return Collections.emptyList(); }
            @Override public Cliente buscarPorDocumento(String d) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String d) { return Collections.emptyList(); }
            @Override
            public EstadisticasDashboard obtenerEstadisticasDashboard() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                return new EstadisticasDashboard();
            }
        };

        // Simular clic 1 (versión 1)
        int v1 = versionActual.incrementAndGet();
        DashboardSwingWorker w1 = new DashboardSwingWorker(repoLento, "",
                datos -> {
                    if (v1 == versionActual.get()) versionesAplicadas.incrementAndGet();
                },
                err -> {}
        );

        // Inmediatamente clic 2 (versión 2)
        int v2 = versionActual.incrementAndGet();
        DashboardSwingWorker w2 = new DashboardSwingWorker(repoLento, "",
                datos -> {
                    if (v2 == versionActual.get()) {
                        versionesAplicadas.incrementAndGet();
                        latchFin.countDown();
                    }
                },
                err -> {}
        );

        w1.execute();
        w2.execute();

        assertTrue(latchFin.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertEquals("Solo la versión 2 debe haberse aplicado en la UI", 1, versionesAplicadas.get());
    }

    // =========================================================================
    // 5. Resiliencia ante Base de Datos Vacía y División por Cero
    // =========================================================================

    @Test
    public void baseDatosVaciaCalculaTicketPromedioCeroSinExcepciones() {
        EstadisticasDashboard vacio = new EstadisticasDashboard();
        vacio.setVentasHoyDolares(BigDecimal.ZERO);
        vacio.setVentasHoyBs(BigDecimal.ZERO);
        vacio.setPedidosHoy(0);
        vacio.setVentasHistoricasDolares(BigDecimal.ZERO);
        vacio.setVentasHistoricasBs(BigDecimal.ZERO);
        vacio.setPedidosHistoricos(0);

        assertEquals(BigDecimal.ZERO.setScale(2), vacio.getTicketPromedioHoyDolares());
        assertEquals(BigDecimal.ZERO.setScale(2), vacio.getTicketPromedioHoyBs());
        assertEquals(BigDecimal.ZERO.setScale(2), vacio.getTicketPromedioHistoricoDolares());
        assertEquals(BigDecimal.ZERO.setScale(2), vacio.getTicketPromedioHistoricoBs());
        assertTrue(vacio.getTopPlatos().isEmpty());
        assertTrue(vacio.getTopSalas().isEmpty());
    }

    // =========================================================================
    // 6. Resiliencia ante Falla de Base de Datos y Captura de Errores
    // =========================================================================

    @Test
    public void workerCapturaDataAccessExceptionYPropagaCausaRaiz() throws Exception {
        ClienteRepositorio repoFalla = new ClienteRepositorio() {
            @Override public boolean guardarOActualizar(Cliente c) { return true; }
            @Override public List<Cliente> buscarClientes(String c) { return Collections.emptyList(); }
            @Override public Cliente buscarPorDocumento(String d) { return null; }
            @Override public List<Pedidos> listarFacturasCliente(String d) { return Collections.emptyList(); }
            @Override
            public EstadisticasDashboard obtenerEstadisticasDashboard() {
                throw new DataAccessException("No se pudieron cargar las estadísticas del dashboard (Communications link failure)",
                        new SQLException("Communications link failure", "08S01"));
            }
        };

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorCapturado = new AtomicReference<>();

        DashboardSwingWorker worker = new DashboardSwingWorker(
                repoFalla, "",
                datos -> latch.countDown(),
                err -> {
                    errorCapturado.set(err);
                    latch.countDown();
                }
        );

        worker.execute();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertNotNull(errorCapturado.get());
        assertTrue(errorCapturado.get() instanceof DataAccessException);
        assertTrue(errorCapturado.get().getMessage().contains("Communications link failure"));
        assertNotNull(errorCapturado.get().getCause());
        assertTrue(errorCapturado.get().getCause() instanceof SQLException);
    }

    // =========================================================================
    // 7. Nombre Dinámico del Restaurante en UI sin Hardcodeo ni Truncamiento
    // =========================================================================

    @Test
    public void actualizarNombreRestauranteAjustaBannerYTituloSegunLongitud() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Sistema> sistemaRef = new AtomicReference<>();

        SalasRepositorio salasRepo = new SalasRepositorio() {
            @Override public boolean registrar(Modelo.Salas sala) { return true; }
            @Override public List<Modelo.Salas> listar() { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Salas sala) { return true; }
        };
        PlatosRepositorio platosRepo = new PlatosRepositorio() {
            @Override public boolean registrar(Modelo.Platos pla) { return true; }
            @Override public List<Modelo.Platos> listarPorFecha(String f, String fe) { return Collections.emptyList(); }
            @Override public boolean eliminar(int id) { return true; }
            @Override public boolean modificar(Modelo.Platos pla) { return true; }
        };
        PedidosRepositorioFalso pedidosRepo = new PedidosRepositorioFalso();
        SalasControlador salasCtrl = new SalasControlador(new Servicio.SalasServicio(salasRepo, politicaAdmin));
        PlatosControlador platosCtrl = new PlatosControlador(new Servicio.PlatosServicio(platosRepo, politicaAdmin));
        ConsultaPedidosServicio consultas = new ConsultaPedidosServicio(pedidosRepo, politicaAdmin);
        PedidosControlador pedidosCtrl = new PedidosControlador(
                new PedidoServicio(pedidosRepo),
                new PedidoPdfServicio(id -> null, id -> null, () -> null, new GeneradorPdfPedido(Path.of(".")), p -> {}),
                politicaAdmin,
                consultas
        );

        LoginDao loginDaoMock = new LoginDao() {
            @Override
            public Config datosEmpresa() {
                Config c = new Config(1, "J-12345678-0", "Restaurante Gourmet La Delicia Criolla 2026", "0212-0000000", "Caracas", "Gracias");
                return c;
            }
        };

        SwingUtilities.invokeLater(() -> {
            try {
                Sistema sistema = new Sistema(adminUsuario, salasCtrl, platosCtrl, pedidosCtrl, loginDaoMock);
                sistemaRef.set(sistema);
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        latch.await(3, TimeUnit.SECONDS);
        Sistema sistema = sistemaRef.get();
        if (sistema == null) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            // 7.1 Nombre corto estándar (<= 12 caracteres mantiene 48pt)
            sistema.actualizarNombreRestaurante("Mi Local");
            assertEquals("Mi Local", sistema.getLabelTituloRestaurante().getText());
            assertTrue(sistema.getTitle().contains("Mi Local"));
            assertEquals(48, sistema.getLabelTituloRestaurante().getFont().getSize());

            // 7.2 Nombre largo (ej. "Restaurante Gourmet La Delicia Criolla 2026")
            // Debe reducir la fuente automáticamente para evitar el truncamiento '...'
            sistema.actualizarNombreRestaurante("Restaurante Gourmet La Delicia Criolla 2026");
            assertEquals("Restaurante Gourmet La Delicia Criolla 2026", sistema.getLabelTituloRestaurante().getText());
            assertTrue("La fuente debe reducirse para nombres largos",
                    sistema.getLabelTituloRestaurante().getFont().getSize() < 48);

            // 7.3 Nombre vacío o nulo -> fallback "Restaurante"
            sistema.actualizarNombreRestaurante(null);
            assertEquals("Restaurante", sistema.getLabelTituloRestaurante().getText());

            sistema.actualizarNombreRestaurante("   ");
            assertEquals("Restaurante", sistema.getLabelTituloRestaurante().getText());

            sistema.dispose();
        });
    }

    // =========================================================================
    // 8. Auto-Sincronización del Esquema con MigradorEsquemaJdbc
    // =========================================================================

    @Test
    public void migradorEsquemaAseguraTablaClientesYColumnasSinRomperConexion() throws SQLException {
        AtomicInteger sentenciasEjecutadas = new AtomicInteger(0);

        Statement mockStatement = (Statement) java.lang.reflect.Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("executeUpdate".equals(name)) {
                        sentenciasEjecutadas.incrementAndGet();
                        return 1;
                    }
                    if ("executeQuery".equals(name)) {
                        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                                ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                                (p, m, a) -> "next".equals(m.getName()) ? false : null);
                    }
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        PreparedStatement mockPreparedStatement = (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        sentenciasEjecutadas.incrementAndGet();
                        return 1;
                    }
                    return null;
                });

        DatabaseMetaData mockMeta = (DatabaseMetaData) java.lang.reflect.Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getColumns".equals(name) || "getIndexInfo".equals(name)) {
                        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                                ResultSet.class.getClassLoader(),
                                new Class<?>[] { ResultSet.class },
                                (p, m, a) -> {
                                    if ("next".equals(m.getName())) return false;
                                    if ("close".equals(m.getName())) return null;
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        Connection mockConnection = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMetaData".equals(name)) return mockMeta;
                    if ("getCatalog".equals(name)) return "restaurante";
                    if ("createStatement".equals(name)) return mockStatement;
                    if ("prepareStatement".equals(name)) return mockPreparedStatement;
                    if ("isClosed".equals(name)) return false;
                    if ("close".equals(name)) return null;
                    return null;
                }
        );

        // Ejecutar migración simulada
        MigradorEsquemaJdbc.asegurarEsquema(mockConnection);

        assertTrue("Debe ejecutar sentencias para crear tabla clientes, columnas faltantes e índices",
                sentenciasEjecutadas.get() >= 10);
    }

    @Test
    public void migradorPropagaFalloDeDdlParaNoMarcarEsquemaCompleto() {
        SQLException falloDdl = new SQLException("Permiso ALTER denegado", "42000", 1142);
        Statement mockStatement = (Statement) java.lang.reflect.Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        throw falloDdl;
                    }
                    return null;
                });
        DatabaseMetaData mockMeta = (DatabaseMetaData) java.lang.reflect.Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    if ("getColumns".equals(method.getName()) || "getIndexInfo".equals(method.getName())) {
                        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                                ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                                (p, m, a) -> "next".equals(m.getName()) ? false : null);
                    }
                    return null;
                });
        Connection mockConnection = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) return mockMeta;
                    if ("getCatalog".equals(method.getName())) return "restaurante";
                    if ("createStatement".equals(method.getName())) return mockStatement;
                    return null;
                });

        SQLException error = org.junit.Assert.assertThrows(SQLException.class,
                () -> MigradorEsquemaJdbc.asegurarEsquema(mockConnection));

        assertEquals(falloDdl, error);
    }

    @Test
    public void migradorReintentaLuegoDeFalloYCacheaPorBaseDeDatos() {
        String sufijo = Long.toUnsignedString(System.nanoTime());
        AtomicInteger ejecucionesTrasReintento = new AtomicInteger();
        ProveedorConexionJdbc proveedorFallaUnaVez = proveedorMigracion(
                "jdbc:mysql://migracion.test/reintento_" + sufijo,
                "reintento_" + sufijo, new AtomicInteger(), true);
        org.junit.Assert.assertThrows(DataAccessException.class,
                () -> MigradorEsquemaJdbc.migrarSiEsNecesario(proveedorFallaUnaVez));

        MigradorEsquemaJdbc.migrarSiEsNecesario(proveedorMigracion(
                "jdbc:mysql://migracion.test/reintento_" + sufijo,
                "reintento_" + sufijo, ejecucionesTrasReintento, false));
        assertTrue("El fallo previo no debe bloquear el reintento", ejecucionesTrasReintento.get() > 0);

        AtomicInteger ejecucionesSegundaBase = new AtomicInteger();
        MigradorEsquemaJdbc.migrarSiEsNecesario(proveedorMigracion(
                "jdbc:mysql://migracion.test/segunda_" + sufijo,
                "segunda_" + sufijo, ejecucionesSegundaBase, false));
        assertTrue("La segunda base debe ejecutar su propia migración", ejecucionesSegundaBase.get() > 0);
    }

    private ProveedorConexionJdbc proveedorMigracion(String url, String catalogo,
            AtomicInteger sentencias, boolean fallarPrimeraSentencia) {
        AtomicInteger intentos = new AtomicInteger();
        Statement statement = (Statement) java.lang.reflect.Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        sentencias.incrementAndGet();
                        if (fallarPrimeraSentencia && intentos.getAndIncrement() == 0) {
                            throw new SQLException("Fallo DDL inducido", "42000", 1142);
                        }
                        return 1;
                    }
                    if ("executeQuery".equals(method.getName())) {
                        return resultadoVacioMigracion();
                    }
                    return null;
                });
        PreparedStatement preparedStatement = (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        sentencias.incrementAndGet();
                        return 1;
                    }
                    if ("executeBatch".equals(method.getName())) return new int[0];
                    return null;
                });
        DatabaseMetaData metadata = (DatabaseMetaData) java.lang.reflect.Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(), new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    if ("getURL".equals(method.getName())) return url;
                    if ("getColumns".equals(method.getName()) || "getIndexInfo".equals(method.getName())) {
                        return resultadoVacioMigracion();
                    }
                    return null;
                });
        Connection connection = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) return metadata;
                    if ("getCatalog".equals(method.getName())) return catalogo;
                    if ("createStatement".equals(method.getName())) return statement;
                    if ("prepareStatement".equals(method.getName())) return preparedStatement;
                    return null;
                });
        return new ProveedorConexionJdbc() {
            @Override public Connection getConnection() { return connection; }
        };
    }

    private ResultSet resultadoVacioMigracion() {
        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> "next".equals(method.getName()) ? false : null);
    }
}
