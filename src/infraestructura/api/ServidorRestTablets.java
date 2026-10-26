package infraestructura.api;

import Modelo.*;
import Servicio.CalculoMonetarioPedido;
import Servicio.ConfiguracionServicio;
import Servicio.ServicioComandaCocina;
import com.sun.net.httpserver.*;
import infraestructura.ProveedorConexionJdbc;
import infraestructura.impresion.CircuitBreakerImpresora;
import infraestructura.impresion.SpoolerCocinaTcp;
import infraestructura.red.ServicioTunelSsh;
import infraestructura.seguridad.ValidadorLicencia;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor API REST Embebido en Java 21 para Comanderas Móviles y Tablets (Amazon Fire HD 8).
 * Opera mediante Virtual Threads (Project Loom) y está protegido contra saturación de base
 * de datos mediante mampara de contrapresión (Semaphore) que preserva conexiones prioritarias
 * para la estación de caja Swing.
 */
public final class ServidorRestTablets {

    private static final Logger LOGGER = Logger.getLogger(ServidorRestTablets.class.getName());
    private static final int PUERTO_DEFAULT = 8080;
    private static final int MAX_CONEXIONES_BD_API = 10;
    private static final Semaphore DB_THROTTLE = new Semaphore(MAX_CONEXIONES_BD_API, true);
    private static final Map<String, RespuestaIdempotente> CACHE_IDEMPOTENCIA = new ConcurrentHashMap<>();

    // Estado volátil de pantalla digital de cocina (KDS)
    private static final Set<Integer> KDS_PEDIDOS_DESPACHADOS = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> KDS_ITEMS_LISTOS = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Integer> KDS_CONTEO_ITEMS = new ConcurrentHashMap<>();

    private static HttpServer servidorHttp;
    private static boolean activo = false;
    private static int puertoEnUso = PUERTO_DEFAULT;

    private ServidorRestTablets() {
    }

    public static synchronized void iniciarSiEstaHabilitado() {
        if (activo) {
            return;
        }
        int puerto = PUERTO_DEFAULT;
        String puertoEnv = System.getenv("API_PORT");
        if (puertoEnv == null || puertoEnv.isBlank()) {
            puertoEnv = System.getProperty("restaurante.api.puerto");
        }
        if (puertoEnv != null && !puertoEnv.isBlank()) {
            try {
                puerto = Integer.parseInt(puertoEnv.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        iniciar(puerto);
    }

    public static synchronized void iniciar(int puerto) {
        if (activo && servidorHttp != null) {
            LOGGER.info("El servidor API REST ya se encuentra en ejecución en el puerto " + puertoEnUso);
            return;
        }
        try {
            puertoEnUso = puerto;
            servidorHttp = HttpServer.create(new InetSocketAddress(puerto), 0);
            servidorHttp.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            // Endpoints de API REST
            servidorHttp.createContext("/api/v1/health", new HealthHandler());
            servidorHttp.createContext("/api/v1/auth/login", new LoginHandler());
            servidorHttp.createContext("/api/v1/mesoneros", new MesonerosHandler());
            servidorHttp.createContext("/api/v1/salas", new SalasHandler());
            servidorHttp.createContext("/api/v1/mesas", new MesasHandler());
            servidorHttp.createContext("/api/v1/platos", new PlatosHandler());
            servidorHttp.createContext("/api/v1/comandas", new ComandasHandler());
            servidorHttp.createContext("/api/v1/telemetria/resumen", new TelemetriaHandler());
            servidorHttp.createContext("/api/v1/cocina/comandas", new CocinaComandasHandler());
            servidorHttp.createContext("/api/v1/cocina/despachar", new CocinaDespacharHandler());
            servidorHttp.createContext("/gerencia", new DashboardGerencialHandler());
            servidorHttp.createContext("/cocina", new CocinaKdsHandler());

            // Servir la aplicación web PWA para tablets en la raíz
            servidorHttp.createContext("/", new PwaWebHandler());

            servidorHttp.start();
            activo = true;

            String ipLocal = obtenerIpLocal();
            LOGGER.info("================================================================================");
            LOGGER.info(" [OK] SERVIDOR REST TABLETS ACTIVO (JAVA 21 VIRTUAL THREADS)");
            LOGGER.info(" URL para Tablets Fire HD 8: http://" + ipLocal + ":" + puerto + "/");
            LOGGER.info(" URL Pantalla Cocina (KDS):  http://" + ipLocal + ":" + puerto + "/cocina");
            LOGGER.info(" URL Gerencia Móvil:         http://" + ipLocal + ":" + puerto + "/gerencia");
            LOGGER.info("================================================================================");

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo iniciar el servidor API REST en el puerto " + puerto + ": " + ex.getMessage(), ex);
        }
    }

    public static synchronized void detener() {
        if (servidorHttp != null) {
            servidorHttp.stop(1);
            servidorHttp = null;
            activo = false;
            LOGGER.info("Servidor API REST para tablets detenido.");
        }
    }

    public static boolean isActivo() {
        return activo;
    }

    public static int getPuerto() {
        return puertoEnUso;
    }

    public static void limpiarEstadoKdsParaPruebas() {
        KDS_PEDIDOS_DESPACHADOS.clear();
        KDS_ITEMS_LISTOS.clear();
        KDS_CONTEO_ITEMS.clear();
    }

    private static String obtenerIpLocal() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "localhost";
    }

    // --------------------------------------------------------------------------
    // Controladores de Endpoints (Handlers)
    // --------------------------------------------------------------------------

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            ValidadorLicencia lic = ValidadorLicencia.getInstancia();
            String json = "{\"status\":\"UP\",\"version\":\"2026.enterprise\",\"licencia_activa\":"
                    + lic.isActiva() + ",\"cliente\":\"" + escaparJson(lic.getNombreEmpresa()) + "\"}";
            enviarJson(exchange, 200, json);
        }
    }

    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarError(exchange, 405, "Método no permitido. Use POST.");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parsearJsonSimple(body);
            String codigo = params.get("codigo");
            String idStr = params.get("id_mesonero");

            if ((codigo == null || codigo.isBlank()) && (idStr == null || idStr.isBlank())) {
                enviarError(exchange, 400, "Debe ingresar el código o ID de mesonero.");
                return;
            }

            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado. Intente en unos segundos.");
                    return;
                }
                try {
                    MesoneroDao dao = new MesoneroDao(new ProveedorConexionJdbc());
                    Mesonero encontrado = null;
                    List<Mesonero> activos = dao.listarActivos();
                    for (Mesonero m : activos) {
                        if (idStr != null && String.valueOf(m.getId()).equals(idStr.trim())) {
                            encontrado = m;
                            break;
                        }
                        if (codigo != null && (m.getCedula().equalsIgnoreCase(codigo.trim()) || String.valueOf(m.getId()).equals(codigo.trim()))) {
                            encontrado = m;
                            break;
                        }
                    }

                    if (encontrado == null) {
                        enviarError(exchange, 401, "Credenciales de mesonero no válidas o usuario inactivo.");
                        return;
                    }

                    String token = generarTokenMesonero(encontrado);
                    String resp = "{\"ok\":true,\"token\":\"" + token + "\",\"id_mesonero\":" + encontrado.getId()
                            + ",\"nombre\":\"" + escaparJson(encontrado.getNombreCompleto()) + "\",\"rol\":\"MESONERO\"}";
                    enviarJson(exchange, 200, resp);

                } finally {
                    DB_THROTTLE.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                enviarError(exchange, 503, "Operación interrumpida.");
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error interno: " + ex.getMessage());
            }
        }
    }

    private static class MesonerosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try {
                    MesoneroDao dao = new MesoneroDao(new ProveedorConexionJdbc());
                    List<Mesonero> lista = dao.listarActivos();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < lista.size(); i++) {
                        Mesonero m = lista.get(i);
                        if (i > 0) sb.append(",");
                        sb.append("{\"id\":").append(m.getId())
                          .append(",\"nombre\":\"").append(escaparJson(m.getNombreCompleto())).append("\"")
                          .append(",\"cedula\":\"").append(escaparJson(m.getCedula())).append("\"}");
                    }
                    sb.append("]");
                    enviarJson(exchange, 200, sb.toString());
                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error al listar mesoneros: " + ex.getMessage());
            }
        }
    }

    private static class SalasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try {
                    ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
                    SalasDao salasDao = new SalasDao(proveedor);
                    PedidosDao pedidosDao = new PedidosDao(proveedor);

                    List<Salas> salas = salasDao.Listar();
                    Map<Integer, Integer> ocupadasPorSala = pedidosDao.contarMesasOcupadasPorSala();

                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < salas.size(); i++) {
                        Salas s = salas.get(i);
                        if (i > 0) sb.append(",");
                        int ocupadas = ocupadasPorSala.getOrDefault(s.getId(), 0);
                        sb.append("{\"id\":").append(s.getId())
                          .append(",\"nombre\":\"").append(escaparJson(s.getNombre())).append("\"")
                          .append(",\"mesas\":").append(s.getMesas())
                          .append(",\"tipo\":\"").append(escaparJson(s.getTipo())).append("\"")
                          .append(",\"mesas_ocupadas\":").append(ocupadas)
                          .append("}");
                    }
                    sb.append("]");
                    enviarJson(exchange, 200, sb.toString());
                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error consultando salas: " + ex.getMessage());
            }
        }
    }

    private static class MesasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            int idSala = 0;
            if (query != null && query.contains("id_sala=")) {
                try {
                    idSala = Integer.parseInt(query.replaceAll(".*id_sala=([0-9]+).*", "$1"));
                } catch (Exception ignored) {
                }
            }

            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                    String sql = """
                        SELECT p.id, p.id_sala, p.num_mesa, p.total, p.total_bs, p.fecha, p.id_mesonero, p.mesonero_nombre
                        FROM pedidos p
                        WHERE p.estado = 'PENDIENTE' AND p.cerrada_en IS NULL
                        """ + (idSala > 0 ? " AND p.id_sala = ?" : "") + " ORDER BY p.num_mesa ASC";

                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        if (idSala > 0) ps.setInt(1, idSala);
                        try (ResultSet rs = ps.executeQuery()) {
                            StringBuilder sb = new StringBuilder("[");
                            boolean primero = true;
                            while (rs.next()) {
                                if (!primero) sb.append(",");
                                primero = false;
                                sb.append("{\"id_pedido\":").append(rs.getInt("id"))
                                  .append(",\"id_sala\":").append(rs.getInt("id_sala"))
                                  .append(",\"num_mesa\":").append(rs.getInt("num_mesa"))
                                  .append(",\"total\":").append(rs.getBigDecimal("total"))
                                  .append(",\"total_bs\":").append(rs.getBigDecimal("total_bs"))
                                  .append(",\"id_mesonero\":").append(rs.getInt("id_mesonero"))
                                  .append(",\"mesonero\":\"").append(escaparJson(rs.getString("mesonero_nombre"))).append("\"")
                                  .append(",\"fecha\":\"").append(rs.getString("fecha")).append("\"}");
                            }
                            sb.append("]");
                            enviarJson(exchange, 200, sb.toString());
                        }
                    }
                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error consultando mesas: " + ex.getMessage());
            }
        }
    }

    private static class PlatosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try (Connection con = new ProveedorConexionJdbc().getConnection()) {
                    String sql = """
                        SELECT p.id, p.nombre, p.precio_usd, p.precio_ves, p.aplica_iva,
                               COALESCE(c.nombre, 'General') AS categoria, COALESCE(c.id, 0) AS id_categoria
                        FROM platos p
                        LEFT JOIN plato_categoria pc ON pc.nombre_clave = p.nombre_clave
                        LEFT JOIN categorias c ON c.id = pc.id_categoria
                        WHERE p.activo = 1
                        ORDER BY categoria ASC, p.nombre ASC
                        """;
                    try (PreparedStatement ps = con.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        StringBuilder sb = new StringBuilder("[");
                        boolean primero = true;
                        while (rs.next()) {
                            if (!primero) sb.append(",");
                            primero = false;
                            sb.append("{\"id\":").append(rs.getInt("id"))
                              .append(",\"nombre\":\"").append(escaparJson(rs.getString("nombre"))).append("\"")
                              .append(",\"precio_usd\":").append(rs.getBigDecimal("precio_usd"))
                              .append(",\"precio_ves\":").append(rs.getBigDecimal("precio_ves"))
                              .append(",\"aplica_iva\":").append(rs.getInt("aplica_iva") != 0)
                              .append(",\"id_categoria\":").append(rs.getInt("id_categoria"))
                              .append(",\"categoria\":\"").append(escaparJson(rs.getString("categoria"))).append("\"}");
                        }
                        sb.append("]");
                        enviarJson(exchange, 200, sb.toString());
                    }
                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error consultando platos: " + ex.getMessage());
            }
        }
    }

    private static class ComandasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarError(exchange, 405, "Método no permitido. Use POST.");
                return;
            }

            // 1. Control de Idempotencia estricto (Anti-duplicación Wi-Fi)
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                RespuestaIdempotente cacheada = CACHE_IDEMPOTENCIA.get(idempotencyKey.trim());
                if (cacheada != null && !cacheada.haExpirado()) {
                    LOGGER.info("Petición de comanda absorbida por Idempotency-Key: " + idempotencyKey);
                    enviarJson(exchange, cacheada.codigoStatus(), cacheada.cuerpoJson());
                    return;
                }
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                enviarError(exchange, 400, "Payload vacío.");
                return;
            }

            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado. Intente en unos segundos.");
                    return;
                }
                try {
                    ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
                    PedidosDao pedidosDao = new PedidosDao(proveedor);

                    // Parsear comanda
                    ComandaPayload comanda = parsearComandaJson(body);
                    if (comanda.idSala <= 0 || comanda.numMesa <= 0) {
                        enviarError(exchange, 400, "Sala y número de mesa son obligatorios.");
                        return;
                    }
                    if (comanda.items.isEmpty()) {
                        enviarError(exchange, 400, "Debe agregar al menos un ítem a la comanda.");
                        return;
                    }

                    // Cargar parámetros de configuración del sistema (Tasa e IVA)
                    ConfiguracionServicio configServicio = new ConfiguracionServicio();
                    BigDecimal tasa = configServicio.getDecimal(ConfigClaves.TASA_DOLAR, BigDecimal.ONE);
                    BigDecimal ivaPorcentaje = configServicio.getDecimal(ConfigClaves.IVA_PORCENTAJE, BigDecimal.valueOf(16.00));
                    boolean imprimirComanda = configServicio.getBoolean(ConfigClaves.IMPRIMIR_COMANDA_COCINA, true);

                    // Cargar y validar cada ítem contra el catálogo oficial de la BD
                    List<DetallePedido> itemsNuevos = new ArrayList<>();
                    try (Connection con = proveedor.getConnection()) {
                        for (ItemComandaDto it : comanda.items) {
                            try (PreparedStatement ps = con.prepareStatement(
                                    "SELECT id, nombre, precio, moneda_base, precio_usd, precio_ves, precio_incluye_iva, aplica_iva FROM platos WHERE id = ? AND activo = 1")) {
                                ps.setInt(1, it.idPlato);
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (!rs.next()) {
                                        enviarError(exchange, 400, "El plato #" + it.idPlato + " no existe o está inactivo.");
                                        return;
                                    }
                                    DetallePedido det = new DetallePedido();
                                    det.setIdPlato(rs.getInt("id"));
                                    det.setNombre(rs.getString("nombre"));
                                    det.setCantidad(it.cantidad);
                                    BigDecimal precio = rs.getBigDecimal("precio");
                                    det.setPrecioDecimal(precio != null ? precio : BigDecimal.ZERO);
                                    String moneda = rs.getString("moneda_base");
                                    det.setMonedaBase(moneda != null && !moneda.isBlank() ? moneda : "USD");
                                    det.setAplicaIva(rs.getInt("aplica_iva") != 0);
                                    det.setPrecioIncluyeIva(rs.getInt("precio_incluye_iva") == 1);
                                    det.setIvaPorcentaje(det.getAplicaIva() ? ivaPorcentaje : BigDecimal.ZERO.setScale(2));
                                    det.setComentario(it.notas != null ? it.notas.trim() : "");
                                    itemsNuevos.add(det);
                                }
                            }
                        }
                    }

                    // Verificar si la mesa ya tiene pedido pendiente
                    int idPedidoExistente = 0;
                    try (Connection con = proveedor.getConnection();
                         PreparedStatement ps = con.prepareStatement(
                                 "SELECT id FROM pedidos WHERE id_sala = ? AND num_mesa = ? AND estado = 'PENDIENTE' AND cerrada_en IS NULL")) {
                        ps.setInt(1, comanda.idSala);
                        ps.setInt(2, comanda.numMesa);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                idPedidoExistente = rs.getInt("id");
                            }
                        }
                    }

                    List<DetallePedido> detallesParaGuardar;
                    if (idPedidoExistente > 0) {
                        List<DetallePedido> detallesActuales = pedidosDao.verPedidoDetalle(idPedidoExistente);
                        detallesParaGuardar = new ArrayList<>(detallesActuales);
                        detallesParaGuardar.addAll(itemsNuevos);
                    } else {
                        detallesParaGuardar = itemsNuevos;
                    }

                    CalculoMonetarioPedido.CalculoPedido calc = CalculoMonetarioPedido.calcularPedido(detallesParaGuardar, tasa);

                    Pedidos p = new Pedidos();
                    if (idPedidoExistente > 0) {
                        p.setId(idPedidoExistente);
                    }
                    p.setId_sala(comanda.idSala);
                    p.setNum_mesa(comanda.numMesa);
                    p.setTipoPedido("MESA");
                    p.setSubtotal(calc.subtotalUsd());
                    p.setIvaPorcentaje(ivaPorcentaje);
                    p.setIvaMonto(calc.ivaUsd());
                    p.setTotalDecimal(calc.totalUsd());
                    p.setTasaCambio(tasa);
                    p.setSubtotalBs(calc.subtotalVes());
                    p.setIvaBs(calc.ivaVes());
                    p.setTotalBs(calc.totalVes());
                    p.setUsuario(comanda.mesoneroNombre != null && !comanda.mesoneroNombre.isBlank() ? comanda.mesoneroNombre : "Tablet");
                    if (comanda.idMesonero > 0) {
                        p.setIdMesonero(comanda.idMesonero);
                        p.setMesoneroNombre(comanda.mesoneroNombre);
                    }

                    ResultadoPedidoComanda resultado;
                    if (idPedidoExistente > 0) {
                        resultado = pedidosDao.actualizarPedidoCompletoConMarcha(idPedidoExistente, p, detallesParaGuardar, imprimirComanda);
                    } else {
                        resultado = pedidosDao.registrarPedidoCompletoConMarcha(p, detallesParaGuardar, imprimirComanda);
                    }

                    int idPedidoFinal = resultado.idPedido();

                    // Impresión asíncrona de comanda a cocina para los ítems nuevos
                    if (imprimirComanda && resultado.marcha() != null && !resultado.marcha().movimientos().isEmpty()) {
                        String salaTexto = obtenerNombreSala(proveedor, comanda.idSala);
                        ServicioComandaCocina.imprimirAsync(resultado.marcha(), salaTexto, comanda.numMesa, p.getMesoneroNombre());
                    }

                    String jsonRespuesta = "{\"ok\":true,\"id_pedido\":" + idPedidoFinal
                            + ",\"id_sala\":" + comanda.idSala + ",\"num_mesa\":" + comanda.numMesa
                            + ",\"items_enviados\":" + itemsNuevos.size()
                            + ",\"mensaje\":\"Comanda enviada a cocina exitosamente\"}";

                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        CACHE_IDEMPOTENCIA.put(idempotencyKey.trim(), new RespuestaIdempotente(200, jsonRespuesta, Instant.now().plusSeconds(86400)));
                    }

                    enviarJson(exchange, 200, jsonRespuesta);

                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error procesando comanda desde tablet: " + ex.getMessage(), ex);
                enviarError(exchange, 500, "Error despachando comanda: " + ex.getMessage());
            }
        }
    }

    private static class TelemetriaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }

            // Validar token si está configurado
            String tokenConfigurado = ServicioTunelSsh.getTokenAdmin();
            if (!tokenConfigurado.isBlank()) {
                String tokenRecibido = "";
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
                    tokenRecibido = authHeader.substring(7).trim();
                }
                if (tokenRecibido.isBlank()) {
                    tokenRecibido = exchange.getRequestHeaders().getFirst("X-Admin-Token");
                }
                if (tokenRecibido == null || tokenRecibido.isBlank()) {
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null && query.contains("token=")) {
                        tokenRecibido = query.replaceAll(".*token=([^&]+).*", "$1").trim();
                    }
                }
                if (tokenRecibido == null || !tokenConfigurado.equals(tokenRecibido.trim())) {
                    enviarError(exchange, 401, "Acceso no autorizado. Token gerencial inválido o ausente.");
                    return;
                }
            }

            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try {
                    ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
                    EstadisticasDashboard stats;
                    boolean dbOk = true;
                    try {
                        ClienteDao clienteDao = new ClienteDao(proveedor);
                        stats = clienteDao.obtenerEstadisticasDashboard();
                    } catch (Exception exDb) {
                        LOGGER.log(Level.WARNING, "Error consultando estadísticas para telemetría: " + exDb.getMessage());
                        stats = new EstadisticasDashboard();
                        dbOk = false;
                    }

                    ValidadorLicencia lic = ValidadorLicencia.getInstancia();
                    CircuitBreakerImpresora cb = SpoolerCocinaTcp.getCircuitBreaker();

                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"ok\":true,");
                    sb.append("\"empresa\":\"").append(escaparJson(lic.getNombreEmpresa())).append("\",");
                    sb.append("\"rif\":\"").append(escaparJson(lic.getRif())).append("\",");
                    sb.append("\"licencia_activa\":").append(lic.isActiva()).append(",");
                    sb.append("\"ventas\":{");
                    sb.append("\"ventas_usd\":").append(stats.getVentasHoyDolares()).append(",");
                    sb.append("\"ventas_bs\":").append(stats.getVentasHoyBs()).append(",");
                    sb.append("\"pedidos_hoy\":").append(stats.getPedidosHoy()).append(",");
                    sb.append("\"pagos_metodos\":[");
                    List<EstadisticasDashboard.ItemEstadistica> pagos = stats.getPagosPorMetodoHoy();
                    for (int i = 0; i < pagos.size(); i++) {
                        EstadisticasDashboard.ItemEstadistica p = pagos.get(i);
                        if (i > 0) sb.append(",");
                        sb.append("{\"metodo\":\"").append(escaparJson(p.getNombre())).append("\"")
                          .append(",\"cantidad\":").append(p.getCantidad())
                          .append(",\"total_usd\":").append(p.getTotalDolares())
                          .append(",\"total_bs\":").append(p.getTotalBs()).append("}");
                    }
                    sb.append("]},");

                    sb.append("\"operaciones\":{");
                    sb.append("\"mesas_ocupadas\":").append(stats.getMesasOcupadasActuales()).append(",");
                    sb.append("\"top_platos\":[");
                    List<EstadisticasDashboard.ItemEstadistica> platos = stats.getTopPlatos();
                    for (int i = 0; i < Math.min(5, platos.size()); i++) {
                        EstadisticasDashboard.ItemEstadistica pl = platos.get(i);
                        if (i > 0) sb.append(",");
                        sb.append("{\"nombre\":\"").append(escaparJson(pl.getNombre())).append("\"")
                          .append(",\"cantidad\":").append(pl.getCantidad())
                          .append(",\"total_usd\":").append(pl.getTotalDolares()).append("}");
                    }
                    sb.append("]},");

                    sb.append("\"salud\":{");
                    sb.append("\"impresora_cocina\":\"").append(cb.getEstado().name()).append("\",");
                    sb.append("\"impresora_cocina_fallos\":").append(cb.getFallosConsecutivos()).append(",");
                    sb.append("\"tunel_ssh\":\"").append(ServicioTunelSsh.getEstado().name()).append("\",");
                    sb.append("\"tunel_ssh_mensaje\":\"").append(escaparJson(ServicioTunelSsh.getUltimoMensaje())).append("\"");
                    sb.append("},");
                    sb.append("\"timestamp\":\"").append(java.time.LocalDateTime.now().toString()).append("\"");
                    sb.append("}");

                    enviarJson(exchange, 200, sb.toString());

                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                enviarError(exchange, 500, "Error recopilando telemetría: " + ex.getMessage());
            }
        }
    }

    private static class DashboardGerencialHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generarDashboardGerencialHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String generarDashboardGerencialHtml() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <meta name="theme-color" content="#0b1120">
              <title>Gerencia Móvil — Restaurante 2026</title>
              <style>
                :root {
                  --bg: #0b1120;
                  --card: #1e293b;
                  --border: #334155;
                  --text: #f8fafc;
                  --muted: #94a3b8;
                  --emerald: #10b981;
                  --sky: #38bdf8;
                  --amber: #f59e0b;
                  --rose: #f43f5e;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                body { background: var(--bg); color: var(--text); padding: 16px; padding-bottom: 40px; }
                .header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 16px; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
                .brand { display: flex; align-items: center; gap: 8px; font-weight: 800; font-size: 1.2rem; color: var(--sky); }
                .live-badge { display: flex; align-items: center; gap: 6px; background: rgba(16, 185, 129, 0.15); border: 1px solid var(--emerald); color: var(--emerald); padding: 4px 10px; border-radius: 9999px; font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; }
                .pulse-dot { width: 8px; height: 8px; background: var(--emerald); border-radius: 50%; animation: pulse 1.5s infinite; }
                @keyframes pulse { 0% { opacity: 1; transform: scale(1); } 50% { opacity: 0.3; transform: scale(1.3); } 100% { opacity: 1; transform: scale(1); } }
                
                .grid-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 24px; }
                .card-kpi { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 14px; display: flex; flex-direction: column; }
                .kpi-title { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); font-weight: 600; margin-bottom: 6px; }
                .kpi-val { font-size: 1.35rem; font-weight: 800; }
                .val-emerald { color: var(--emerald); }
                .val-sky { color: var(--sky); }
                
                .section-card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 16px; margin-bottom: 20px; }
                .section-header { font-size: 0.95rem; font-weight: 700; margin-bottom: 14px; display: flex; justify-content: space-between; align-items: center; }
                
                .row-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(51, 65, 85, 0.5); font-size: 0.9rem; }
                .row-item:last-child { border-bottom: none; }
                
                .badge-status { padding: 4px 8px; border-radius: 6px; font-size: 0.75rem; font-weight: 700; }
                .status-ok { background: rgba(16, 185, 129, 0.2); color: var(--emerald); }
                .status-warn { background: rgba(245, 158, 11, 0.2); color: var(--amber); }
                .status-err { background: rgba(244, 63, 94, 0.2); color: var(--rose); }

                .btn-refresh { background: var(--card); border: 1px solid var(--border); color: white; padding: 8px 14px; border-radius: 8px; font-size: 0.85rem; font-weight: 600; cursor: pointer; display: flex; align-items: center; gap: 6px; }
                .btn-refresh:active { background: #334155; }
              </style>
            </head>
            <body>
              <div class="header">
                <div>
                  <div class="brand">📊 <span>Gerencia Móvil</span></div>
                  <div style="font-size:0.75rem; color:var(--muted); margin-top:2px;" id="txtEmpresa">Cargando datos...</div>
                </div>
                <div style="display:flex; gap:8px; align-items:center;">
                  <div class="live-badge"><div class="pulse-dot"></div> En Vivo</div>
                  <button class="btn-refresh" onclick="cargarTelemetria()">🔄</button>
                </div>
              </div>

              <!-- KPIs Principales -->
              <div class="grid-kpis">
                <div class="card-kpi">
                  <div class="kpi-title">Ventas Hoy (USD)</div>
                  <div class="kpi-val val-emerald" id="txtVentasUsd">$0.00</div>
                </div>
                <div class="card-kpi">
                  <div class="kpi-title">Ventas Hoy (VES)</div>
                  <div class="kpi-val val-sky" id="txtVentasBs">Bs. 0.00</div>
                </div>
                <div class="card-kpi">
                  <div class="kpi-title">Facturas Hoy</div>
                  <div class="kpi-val" id="txtPedidosHoy">0</div>
                </div>
                <div class="card-kpi">
                  <div class="kpi-title">Mesas Ocupadas</div>
                  <div class="kpi-val" id="txtMesasOcupadas">0</div>
                </div>
              </div>

              <!-- Salud y Estado del Sistema -->
              <div class="section-card">
                <div class="section-header">
                  <span>Monitor de Infraestructura</span>
                  <span style="font-size:0.75rem; color:var(--muted);" id="txtActualizado">--:--:--</span>
                </div>
                <div class="row-item">
                  <span>Impresora de Cocina (TCP 9100)</span>
                  <span id="badgeImpresora" class="badge-status status-ok">CONSULTANDO</span>
                </div>
                <div class="row-item">
                  <span>Túnel Seguro Zero-Trust</span>
                  <span id="badgeTunel" class="badge-status status-ok">ACTIVO</span>
                </div>
                <div class="row-item">
                  <span>Licencia Comercial</span>
                  <span id="badgeLicencia" class="badge-status status-ok">ACTIVA</span>
                </div>
              </div>

              <!-- Desglose por Método de Pago -->
              <div class="section-card">
                <div class="section-header">Cobros del Día por Método</div>
                <div id="listaPagos">
                  <div style="color:var(--muted); font-size:0.85rem; text-align:center; padding:12px;">Consultando pagos...</div>
                </div>
              </div>

              <!-- Top 5 Platos -->
              <div class="section-card">
                <div class="section-header">Top Platos Más Vendidos Hoy</div>
                <div id="listaPlatos">
                  <div style="color:var(--muted); font-size:0.85rem; text-align:center; padding:12px;">Consultando platos...</div>
                </div>
              </div>

              <script>
                async function cargarTelemetria() {
                  const token = localStorage.getItem('tunel_admin_token') || '';
                  try {
                    const headers = {};
                    if (token) headers['X-Admin-Token'] = token;

                    const res = await fetch('/api/v1/telemetria/resumen', { headers });
                    if (res.status === 401) {
                      const nuevoToken = prompt('Ingrese el Token de Acceso Gerencial:');
                      if (nuevoToken) {
                        localStorage.setItem('tunel_admin_token', nuevoToken.trim());
                        cargarTelemetria();
                      }
                      return;
                    }
                    const data = await res.json();
                    if (!data.ok) return;

                    // Actualizar UI
                    document.getElementById('txtEmpresa').textContent = (data.empresa || 'Restaurante') + ' — ' + (data.rif || '');
                    document.getElementById('txtVentasUsd').textContent = '$' + Number(data.ventas.ventas_usd).toFixed(2);
                    document.getElementById('txtVentasBs').textContent = 'Bs. ' + Number(data.ventas.ventas_bs).toFixed(2);
                    document.getElementById('txtPedidosHoy').textContent = data.ventas.pedidos_hoy + ' órdenes';
                    document.getElementById('txtMesasOcupadas').textContent = data.operaciones.mesas_ocupadas + ' activas';

                    const hora = new Date().toLocaleTimeString();
                    document.getElementById('txtActualizado').textContent = 'Act: ' + hora;

                    // Impresora
                    const bImp = document.getElementById('badgeImpresora');
                    if (data.salud.impresora_cocina === 'CLOSED') {
                      bImp.className = 'badge-status status-ok';
                      bImp.textContent = 'LISTA / EN LÍNEA';
                    } else if (data.salud.impresora_cocina === 'HALF_OPEN') {
                      bImp.className = 'badge-status status-warn';
                      bImp.textContent = 'PROBANDO ENLACE';
                    } else {
                      bImp.className = 'badge-status status-err';
                      bImp.textContent = 'OFFLINE (' + data.salud.impresora_cocina_fallos + ' fallos)';
                    }

                    // Túnel
                    const bTunel = document.getElementById('badgeTunel');
                    bTunel.textContent = data.salud.tunel_ssh;
                    bTunel.className = 'badge-status ' + (data.salud.tunel_ssh === 'ACTIVO' ? 'status-ok' : 'status-warn');

                    // Licencia
                    const bLic = document.getElementById('badgeLicencia');
                    bLic.textContent = data.licencia_activa ? 'VERIFICADA' : 'DEMO';

                    // Desglose de pagos
                    const divPagos = document.getElementById('listaPagos');
                    if (data.ventas.pagos_metodos.length === 0) {
                      divPagos.innerHTML = '<div style="color:var(--muted); font-size:0.85rem; text-align:center; padding:8px;">Sin cobros registrados aún hoy.</div>';
                    } else {
                      divPagos.innerHTML = data.ventas.pagos_metodos.map(p => `
                        <div class="row-item">
                          <div>
                            <div style="font-weight:600;">${p.metodo}</div>
                            <div style="font-size:0.75rem; color:var(--muted);">${p.cantidad} transacciones</div>
                          </div>
                          <div style="text-align:right;">
                            <div style="font-weight:700; color:var(--emerald);">$${Number(p.total_usd).toFixed(2)}</div>
                            <div style="font-size:0.75rem; color:var(--sky);">Bs. ${Number(p.total_bs).toFixed(2)}</div>
                          </div>
                        </div>
                      `).join('');
                    }

                    // Top Platos
                    const divPlatos = document.getElementById('listaPlatos');
                    if (data.operaciones.top_platos.length === 0) {
                      divPlatos.innerHTML = '<div style="color:var(--muted); font-size:0.85rem; text-align:center; padding:8px;">Sin platos facturados aún hoy.</div>';
                    } else {
                      divPlatos.innerHTML = data.operaciones.top_platos.map((pl, idx) => `
                        <div class="row-item">
                          <div>
                            <span style="font-weight:700; color:var(--sky); margin-right:6px;">#${idx+1}</span>
                            <span>${pl.nombre}</span>
                          </div>
                          <div style="font-weight:700;">${pl.cantidad} un.</div>
                        </div>
                      `).join('');
                    }

                  } catch (e) {
                    console.error('Error cargando telemetría', e);
                  }
                }

                window.onload = () => {
                  cargarTelemetria();
                  setInterval(cargarTelemetria, 20000); // Polling cada 20 segundos
                };
              </script>
            </body>
            </html>
            """;
    }

    private static String obtenerNombreSala(ProveedorConexionJdbc proveedor, int idSala) {
        try (Connection con = proveedor.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT nombre FROM salas WHERE id = ?")) {
            ps.setInt(1, idSala);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nombre");
            }
        } catch (Exception ignored) {
        }
        return "Sala " + idSala;
    }

    // --------------------------------------------------------------------------
    // KDS — Pantalla Digital de Cocina en Tablet (Kitchen Display System)
    // --------------------------------------------------------------------------

    private static class ItemKds {
        final int id;
        final String nombre;
        final int cantidad;
        final String comentario;
        final int idPlato;

        ItemKds(int id, String nombre, int cantidad, String comentario, int idPlato) {
            this.id = id;
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.comentario = comentario;
            this.idPlato = idPlato;
        }
    }

    private static class CocinaComandasHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarError(exchange, 405, "Método no permitido. Use GET.");
                return;
            }

            ConfiguracionServicio configServicio = new ConfiguracionServicio();
            boolean habilitada = configServicio.getBoolean(ConfigClaves.PANTALLA_COCINA_HABILITADA, true);
            if (!habilitada) {
                enviarJson(exchange, 200, "{\"ok\":true,\"habilitada\":false,\"total_activas\":0,\"comandas\":[]}");
                return;
            }

            try {
                if (!DB_THROTTLE.tryAcquire(5, TimeUnit.SECONDS)) {
                    enviarError(exchange, 503, "Servidor ocupado.");
                    return;
                }
                try {
                    ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();
                    String sqlPedidos = """
                        SELECT p.id, p.num_mesa, p.tipo_pedido, p.fecha, p.usuario, p.mesonero_nombre,
                               s.nombre AS sala_nombre,
                               TIMESTAMPDIFF(MINUTE, p.fecha, NOW()) AS minutos_transcurridos
                        FROM pedidos p
                        LEFT JOIN salas s ON p.id_sala = s.id
                        WHERE p.estado = 'PENDIENTE' AND p.cerrada_en IS NULL
                        ORDER BY p.id ASC
                    """;

                    String sqlItems = """
                        SELECT d.id, d.nombre, d.cantidad, d.comentario, d.id_plato
                        FROM detalle_pedidos d
                        WHERE d.id_pedido = ?
                        ORDER BY d.id ASC
                    """;

                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"ok\":true,");
                    sb.append("\"habilitada\":true,");
                    sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
                    sb.append("\"comandas\":[");

                    int totalActivas = 0;
                    try (Connection con = proveedor.getConnection();
                         PreparedStatement psPed = con.prepareStatement(sqlPedidos);
                         PreparedStatement psIt = con.prepareStatement(sqlItems);
                         ResultSet rsPed = psPed.executeQuery()) {

                        boolean firstPed = true;
                        while (rsPed.next()) {
                            int idPedido = rsPed.getInt("id");
                            int numMesa = rsPed.getInt("num_mesa");
                            String tipoPedido = rsPed.getString("tipo_pedido");
                            String usuario = rsPed.getString("usuario");
                            String mesonero = rsPed.getString("mesonero_nombre");
                            String sala = rsPed.getString("sala_nombre");
                            long minutos = rsPed.getLong("minutos_transcurridos");
                            if (minutos < 0) minutos = 0;

                            psIt.setInt(1, idPedido);
                            List<ItemKds> items = new ArrayList<>();
                            try (ResultSet rsIt = psIt.executeQuery()) {
                                while (rsIt.next()) {
                                    items.add(new ItemKds(
                                            rsIt.getInt("id"),
                                            rsIt.getString("nombre"),
                                            rsIt.getInt("cantidad"),
                                            rsIt.getString("comentario"),
                                            rsIt.getInt("id_plato")
                                    ));
                                }
                            }

                            if (items.isEmpty()) continue;

                            int cantActual = items.size();
                            Integer cantPrevia = KDS_CONTEO_ITEMS.get(idPedido);
                            if (cantPrevia != null && cantActual > cantPrevia) {
                                KDS_PEDIDOS_DESPACHADOS.remove(idPedido);
                            }
                            KDS_CONTEO_ITEMS.put(idPedido, cantActual);

                            boolean despachada = KDS_PEDIDOS_DESPACHADOS.contains(idPedido);
                            if (!despachada) {
                                totalActivas++;
                            }

                            String urgencia = minutos < 10 ? "NORMAL" : (minutos < 20 ? "ADVERTENCIA" : "URGENTE");

                            if (!firstPed) sb.append(",");
                            firstPed = false;

                            sb.append("{");
                            sb.append("\"id_pedido\":").append(idPedido).append(",");
                            sb.append("\"num_mesa\":").append(numMesa).append(",");
                            sb.append("\"tipo_pedido\":\"").append(escaparJson(tipoPedido != null ? tipoPedido : "MESA")).append("\",");
                            sb.append("\"sala\":\"").append(escaparJson(sala != null ? sala : "Principal")).append("\",");
                            sb.append("\"mesonero\":\"").append(escaparJson(mesonero != null && !mesonero.isBlank() ? mesonero : (usuario != null ? usuario : "Caja"))).append("\",");
                            sb.append("\"minutos\":").append(minutos).append(",");
                            sb.append("\"urgencia\":\"").append(urgencia).append("\",");
                            sb.append("\"despachada\":").append(despachada).append(",");
                            sb.append("\"items\":[");

                            for (int i = 0; i < items.size(); i++) {
                                ItemKds it = items.get(i);
                                if (i > 0) sb.append(",");
                                sb.append("{");
                                sb.append("\"id\":").append(it.id).append(",");
                                sb.append("\"nombre\":\"").append(escaparJson(it.nombre)).append("\",");
                                sb.append("\"cantidad\":").append(it.cantidad).append(",");
                                sb.append("\"comentario\":\"").append(escaparJson(it.comentario != null ? it.comentario : "")).append("\",");
                                sb.append("\"listo\":").append(KDS_ITEMS_LISTOS.contains(it.id));
                                sb.append("}");
                            }
                            sb.append("]}");
                        }
                    } catch (Exception exDb) {
                        LOGGER.log(Level.WARNING, "Error consultando comandas de cocina: " + exDb.getMessage());
                    }

                    sb.append("],");
                    sb.append("\"total_activas\":").append(totalActivas);
                    sb.append("}");

                    enviarJson(exchange, 200, sb.toString());
                } finally {
                    DB_THROTTLE.release();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error procesando petición de comandas cocina: " + ex.getMessage(), ex);
                enviarError(exchange, 500, "Error interno consultando comandas: " + ex.getMessage());
            }
        }
    }

    private static class CocinaDespacharHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarError(exchange, 405, "Método no permitido. Use POST.");
                return;
            }

            ConfiguracionServicio configServicio = new ConfiguracionServicio();
            boolean habilitada = configServicio.getBoolean(ConfigClaves.PANTALLA_COCINA_HABILITADA, true);
            if (!habilitada) {
                enviarError(exchange, 403, "Pantalla de cocina deshabilitada.");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                enviarError(exchange, 400, "Payload vacío.");
                return;
            }

            try {
                String accion = extraerString(body, "accion").toUpperCase();
                int idPedido = extraerInt(body, "id_pedido");
                int idDetalle = extraerInt(body, "id_detalle");

                if (accion.isBlank()) {
                    if (idDetalle > 0 && idPedido <= 0) {
                        accion = "ITEM";
                    } else {
                        accion = "COMANDA";
                    }
                }

                if ("ITEM".equals(accion)) {
                    if (idDetalle > 0) {
                        if (KDS_ITEMS_LISTOS.contains(idDetalle)) {
                            KDS_ITEMS_LISTOS.remove(idDetalle);
                        } else {
                            KDS_ITEMS_LISTOS.add(idDetalle);
                        }
                    }
                } else if ("REABRIR".equals(accion)) {
                    if (idPedido > 0) {
                        KDS_PEDIDOS_DESPACHADOS.remove(idPedido);
                    }
                } else { // COMANDA
                    if (idPedido > 0) {
                        KDS_PEDIDOS_DESPACHADOS.add(idPedido);
                    }
                }

                StringBuilder sb = new StringBuilder("{");
                sb.append("\"ok\":true,");
                sb.append("\"accion\":\"").append(escaparJson(accion)).append("\",");
                sb.append("\"id_pedido\":").append(idPedido).append(",");
                sb.append("\"id_detalle\":").append(idDetalle);
                sb.append("}");

                enviarJson(exchange, 200, sb.toString());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error despachando comanda en KDS: " + ex.getMessage(), ex);
                enviarError(exchange, 500, "Error procesando despacho: " + ex.getMessage());
            }
        }
    }

    private static class CocinaKdsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generarCocinaKdsHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String generarCocinaKdsHtml() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <meta name="theme-color" content="#090d16">
              <title>Pantalla de Cocina — KDS Restaurante 2026</title>
              <style>
                :root {
                  --bg: #090d16;
                  --card-bg: #131b2e;
                  --card-border: #24324f;
                  --text: #f8fafc;
                  --muted: #94a3b8;
                  --emerald: #10b981;
                  --amber: #f59e0b;
                  --rose: #f43f5e;
                  --sky: #0ea5e9;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; -webkit-tap-highlight-color: transparent; }
                body { background: var(--bg); color: var(--text); padding: 12px; height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
                
                .topbar { display: flex; justify-content: space-between; align-items: center; background: #0f172a; border: 1px solid var(--card-border); border-radius: 12px; padding: 10px 16px; margin-bottom: 12px; gap: 12px; flex-shrink: 0; }
                .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 1.25rem; color: #fff; }
                .pulse-dot { width: 10px; height: 10px; background: var(--emerald); border-radius: 50%; box-shadow: 0 0 10px var(--emerald); animation: pulse 1.5s infinite; }
                @keyframes pulse { 0% { opacity: 1; transform: scale(1); } 50% { opacity: 0.4; transform: scale(1.2); } 100% { opacity: 1; transform: scale(1); } }
                
                .tabs-nav { display: flex; gap: 8px; }
                .tab-btn { background: #1e293b; border: 1px solid var(--card-border); color: var(--muted); padding: 8px 16px; border-radius: 8px; font-weight: 700; font-size: 0.9rem; cursor: pointer; transition: all 0.2s; }
                .tab-btn.active { background: var(--sky); color: #fff; border-color: var(--sky); box-shadow: 0 0 12px rgba(14, 165, 233, 0.4); }
                
                .actions-nav { display: flex; align-items: center; gap: 10px; }
                .clock-display { font-family: monospace; font-size: 1.15rem; font-weight: 800; color: #38bdf8; background: #020617; padding: 6px 12px; border-radius: 8px; border: 1px solid #1e293b; }
                .btn-tool { background: #1e293b; border: 1px solid var(--card-border); color: #fff; padding: 8px 12px; border-radius: 8px; font-size: 0.85rem; font-weight: 600; cursor: pointer; display: flex; align-items: center; gap: 6px; }
                .btn-tool:hover { background: #334155; }
                
                .kds-container { flex: 1; overflow-y: auto; overflow-x: hidden; padding-right: 4px; }
                .kds-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; align-items: start; }
                
                .kds-card { background: var(--card-bg); border: 2px solid var(--card-border); border-radius: 14px; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 8px 20px rgba(0,0,0,0.4); transition: transform 0.15s, opacity 0.2s; }
                .kds-card.urgente { border-color: var(--rose); box-shadow: 0 0 14px rgba(244, 63, 94, 0.35); }
                .kds-card.advertencia { border-color: var(--amber); }
                .kds-card.normal { border-color: var(--emerald); }
                
                .card-header { padding: 10px 14px; background: rgba(255,255,255,0.03); border-bottom: 1px solid var(--card-border); display: flex; justify-content: space-between; align-items: center; }
                .mesa-badge { font-size: 1.25rem; font-weight: 900; color: #fff; letter-spacing: 0.02em; }
                .meta-info { font-size: 0.8rem; color: var(--muted); margin-top: 2px; }
                
                .timer-badge { font-weight: 800; font-size: 0.9rem; padding: 4px 10px; border-radius: 9999px; display: flex; align-items: center; gap: 4px; }
                .timer-normal { background: rgba(16, 185, 129, 0.15); color: var(--emerald); border: 1px solid var(--emerald); }
                .timer-advertencia { background: rgba(245, 158, 11, 0.15); color: var(--amber); border: 1px solid var(--amber); }
                .timer-urgente { background: rgba(244, 63, 94, 0.15); color: var(--rose); border: 1px solid var(--rose); animation: pulse 1.2s infinite; }
                
                .card-body { padding: 12px 14px; display: flex; flex-direction: column; gap: 8px; flex: 1; max-height: 380px; overflow-y: auto; }
                .item-row { display: flex; align-items: flex-start; gap: 10px; padding: 8px 10px; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); border-radius: 8px; cursor: pointer; transition: all 0.15s; min-height: 44px; }
                .item-row:hover { background: rgba(255,255,255,0.05); }
                .item-row.item-listo { opacity: 0.35; text-decoration: line-through; background: rgba(0,0,0,0.2); }
                .item-qty { font-weight: 900; font-size: 1.05rem; background: #020617; border: 1px solid var(--card-border); color: #38bdf8; padding: 2px 8px; border-radius: 6px; min-width: 32px; text-align: center; }
                .item-content { flex: 1; }
                .item-title { font-weight: 700; font-size: 0.95rem; color: #f1f5f9; }
                .item-nota { font-size: 0.8rem; color: #fbbf24; font-style: italic; margin-top: 3px; font-weight: 500; }
                .check-icon { font-size: 1.1rem; color: var(--emerald); opacity: 0; }
                .item-row.item-listo .check-icon { opacity: 1; }
                
                .card-footer { padding: 10px 14px; background: rgba(255,255,255,0.02); border-top: 1px solid var(--card-border); }
                .btn-despachar { width: 100%; padding: 12px; background: #059669; border: none; border-radius: 8px; color: #fff; font-weight: 800; font-size: 0.95rem; cursor: pointer; transition: all 0.2s; display: flex; justify-content: center; align-items: center; gap: 6px; }
                .btn-despachar:hover { background: #10b981; box-shadow: 0 0 12px rgba(16,185,129,0.4); }
                .btn-reabrir { width: 100%; padding: 12px; background: #d97706; border: none; border-radius: 8px; color: #fff; font-weight: 800; font-size: 0.95rem; cursor: pointer; transition: all 0.2s; }
                .btn-reabrir:hover { background: #f59e0b; }
                
                .state-box { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 60vh; text-align: center; padding: 20px; }
                .state-icon { font-size: 4rem; margin-bottom: 16px; }
                .state-title { font-size: 1.5rem; font-weight: 800; margin-bottom: 8px; color: #f8fafc; }
                .state-desc { color: var(--muted); max-width: 450px; font-size: 0.95rem; line-height: 1.4; }
              </style>
            </head>
            <body>
              <div class="topbar">
                <div class="brand">
                  <div class="pulse-dot"></div>
                  <span>KDS COCINA</span>
                </div>
                
                <div class="tabs-nav">
                  <button id="tab-pendientes" class="tab-btn active" onclick="cambiarTab('pendientes')">
                    Pendientes (<span id="count-pendientes">0</span>)
                  </button>
                  <button id="tab-despachadas" class="tab-btn" onclick="cambiarTab('despachadas')">
                    Despachadas (<span id="count-despachadas">0</span>)
                  </button>
                </div>
                
                <div class="actions-nav">
                  <button id="btn-sonido" class="btn-tool" onclick="toggleSonido()">🔕 Activar Sonido</button>
                  <button class="btn-tool" onclick="toggleFullscreen()">⛶ Pantalla</button>
                  <div id="reloj" class="clock-display">--:--:--</div>
                </div>
              </div>

              <div class="kds-container">
                <div id="kds-grid" class="kds-grid">
                  <div class="state-box">
                    <div class="state-icon">⏳</div>
                    <div class="state-title">Cargando comandas...</div>
                  </div>
                </div>
              </div>

              <script>
                let audioCtx = null;
                let sonidoHabilitado = false;
                let pedidosConocidos = new Set();
                let primerCarga = true;
                let tabActual = 'pendientes';
                let comandasActuales = [];

                function actualizarReloj() {
                  const d = new Date();
                  document.getElementById('reloj').textContent = d.toTimeString().split(' ')[0];
                }
                setInterval(actualizarReloj, 1000);
                actualizarReloj();

                function toggleSonido() {
                  if (!audioCtx) {
                    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                  }
                  if (audioCtx.state === 'suspended') {
                    audioCtx.resume();
                  }
                  sonidoHabilitado = !sonidoHabilitado;
                  actualizarBotonSonido();
                  if (sonidoHabilitado) {
                    tocarCampana();
                  }
                }

                function actualizarBotonSonido() {
                  const btn = document.getElementById('btn-sonido');
                  if (sonidoHabilitado) {
                    btn.innerHTML = '🔔 Sonido ACTIVO';
                    btn.style.borderColor = '#10b981';
                    btn.style.color = '#10b981';
                  } else {
                    btn.innerHTML = '🔕 Activar Sonido';
                    btn.style.borderColor = '#334155';
                    btn.style.color = '#fff';
                  }
                }

                function tocarCampana() {
                  try {
                    if (!audioCtx) {
                      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                    }
                    const t = audioCtx.currentTime;
                    const osc1 = audioCtx.createOscillator();
                    const g1 = audioCtx.createGain();
                    osc1.type = 'sine';
                    osc1.frequency.setValueAtTime(880, t);
                    osc1.frequency.exponentialRampToValueAtTime(1760, t + 0.12);
                    g1.gain.setValueAtTime(0.4, t);
                    g1.gain.exponentialRampToValueAtTime(0.001, t + 0.7);
                    osc1.connect(g1);
                    g1.connect(audioCtx.destination);
                    osc1.start(t);
                    osc1.stop(t + 0.7);

                    const osc2 = audioCtx.createOscillator();
                    const g2 = audioCtx.createGain();
                    osc2.type = 'triangle';
                    osc2.frequency.setValueAtTime(1320, t + 0.15);
                    g2.gain.setValueAtTime(0.3, t + 0.15);
                    g2.gain.exponentialRampToValueAtTime(0.001, t + 1.1);
                    osc2.connect(g2);
                    g2.connect(audioCtx.destination);
                    osc2.start(t + 0.15);
                    osc2.stop(t + 1.1);
                  } catch(e) {
                    console.error('Audio error:', e);
                  }
                }

                function toggleFullscreen() {
                  if (!document.fullscreenElement) {
                    document.documentElement.requestFullscreen().catch(err => console.log(err));
                  } else {
                    if (document.exitFullscreen) {
                      document.exitFullscreen();
                    }
                  }
                }

                function cambiarTab(tab) {
                  tabActual = tab;
                  document.getElementById('tab-pendientes').className = 'tab-btn' + (tab === 'pendientes' ? ' active' : '');
                  document.getElementById('tab-despachadas').className = 'tab-btn' + (tab === 'despachadas' ? ' active' : '');
                  renderizarComandas();
                }

                function escaparHtml(str) {
                  if (!str) return '';
                  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
                }

                async function cargarComandas() {
                  try {
                    const res = await fetch('/api/v1/cocina/comandas');
                    if (!res.ok) throw new Error('HTTP ' + res.status);
                    const data = await res.json();

                    if (!data.habilitada) {
                      document.getElementById('kds-grid').innerHTML = `
                        <div class="state-box">
                          <div class="state-icon">⚠️</div>
                          <div class="state-title">Pantalla de Cocina Deshabilitada</div>
                          <div class="state-desc">La pantalla digital de cocina está desactivada en la configuración del sistema. Puede habilitarla en la estación de caja principal en <b>Configuración &rarr; Impresión y Cocina</b>.</div>
                        </div>
                      `;
                      return;
                    }

                    comandasActuales = data.comandas || [];

                    let hayNueva = false;
                    comandasActuales.forEach(c => {
                      if (!c.despachada) {
                        if (!primerCarga && !pedidosConocidos.has(c.id_pedido)) {
                          hayNueva = true;
                        }
                        pedidosConocidos.add(c.id_pedido);
                      }
                    });
                    primerCarga = false;

                    if (hayNueva && sonidoHabilitado) {
                      tocarCampana();
                    }

                    const cantPend = comandasActuales.filter(c => !c.despachada).length;
                    const cantDesp = comandasActuales.filter(c => c.despachada).length;
                    document.getElementById('count-pendientes').textContent = cantPend;
                    document.getElementById('count-despachadas').textContent = cantDesp;

                    renderizarComandas();
                  } catch(e) {
                    console.error('Error cargando comandas:', e);
                  }
                }

                function renderizarComandas() {
                  const grid = document.getElementById('kds-grid');
                  const filtradas = comandasActuales.filter(c => tabActual === 'pendientes' ? !c.despachada : c.despachada);

                  if (filtradas.length === 0) {
                    grid.innerHTML = `
                      <div class="state-box">
                        <div class="state-icon">👨‍🍳</div>
                        <div class="state-title">${tabActual === 'pendientes' ? '¡Cocina al Día!' : 'Sin comandas despachadas'}</div>
                        <div class="state-desc">${tabActual === 'pendientes' ? 'No hay pedidos pendientes por despachar en este momento.' : 'Las comandas completadas aparecerán aquí.'}</div>
                      </div>
                    `;
                    return;
                  }

                  grid.innerHTML = filtradas.map(c => {
                    const urgClass = (c.urgencia || 'NORMAL').toLowerCase();
                    const timerClass = 'timer-' + urgClass;
                    const mesaText = c.tipo_pedido === 'LLEVAR' ? '🥡 PARA LLEVAR' : 'MESA ' + c.num_mesa;
                    const subText = `${escaparHtml(c.sala)} • Garzón: ${escaparHtml(c.mesonero)} • #${c.id_pedido}`;

                    const itemsHtml = c.items.map(it => `
                      <div class="item-row ${it.listo ? 'item-listo' : ''}" onclick="tacharItem(${c.id_pedido}, ${it.id}, this)">
                        <div class="item-qty">${it.cantidad}x</div>
                        <div class="item-content">
                          <div class="item-title">${escaparHtml(it.nombre)}</div>
                          ${it.comentario ? `<div class="item-nota">⚠️ ${escaparHtml(it.comentario)}</div>` : ''}
                        </div>
                        <div class="check-icon">✓</div>
                      </div>
                    `).join('');

                    const btnFooter = tabActual === 'pendientes'
                      ? `<button class="btn-despachar" onclick="despacharComanda(${c.id_pedido})">✓ DESPACHAR COMANDA</button>`
                      : `<button class="btn-reabrir" onclick="reabrirComanda(${c.id_pedido})">↩ REABRIR A PENDIENTES</button>`;

                    return `
                      <div id="card-${c.id_pedido}" class="kds-card ${urgClass}">
                        <div class="card-header">
                          <div>
                            <div class="mesa-badge">${mesaText}</div>
                            <div class="meta-info">${subText}</div>
                          </div>
                          <div class="timer-badge ${timerClass}">⏱️ ${c.minutos}m</div>
                        </div>
                        <div class="card-body">
                          ${itemsHtml}
                        </div>
                        <div class="card-footer">
                          ${btnFooter}
                        </div>
                      </div>
                    `;
                  }).join('');
                }

                async function tacharItem(idPedido, idDetalle, el) {
                  el.classList.toggle('item-listo');
                  try {
                    await fetch('/api/v1/cocina/despachar', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ accion: 'ITEM', id_pedido: idPedido, id_detalle: idDetalle })
                    });
                  } catch(e) { console.error(e); }
                }

                async function despacharComanda(idPedido) {
                  const card = document.getElementById('card-' + idPedido);
                  if (card) {
                    card.style.opacity = '0';
                    card.style.transform = 'scale(0.95)';
                  }
                  try {
                    await fetch('/api/v1/cocina/despachar', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ accion: 'COMANDA', id_pedido: idPedido })
                    });
                    setTimeout(cargarComandas, 200);
                  } catch(e) {
                    console.error(e);
                    cargarComandas();
                  }
                }

                async function reabrirComanda(idPedido) {
                  try {
                    await fetch('/api/v1/cocina/despachar', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ accion: 'REABRIR', id_pedido: idPedido })
                    });
                    cargarComandas();
                  } catch(e) { console.error(e); }
                }

                cargarComandas();
                setInterval(cargarComandas, 5000);
              </script>
            </body>
            </html>
            """;
    }

    // --------------------------------------------------------------------------
    // Servidor de PWA / Web App para Mesoneros (Modo Kiosco Amazon Fire HD 8)
    // --------------------------------------------------------------------------

    private static class PwaWebHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/manifest.json")) {
                String manifest = """
                    {
                      "name": "Restaurante 2026 — Comandera",
                      "short_name": "Comandera",
                      "start_url": "/",
                      "display": "standalone",
                      "background_color": "#121824",
                      "theme_color": "#0ea5e9",
                      "icons": [
                        {
                          "src": "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🍽️</text></svg>",
                          "sizes": "192x192 512x512",
                          "type": "image/svg+xml"
                        }
                      ]
                    }
                    """;
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            // HTML5 PWA Kiosk
            String html = generarPwaHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String generarPwaHtml() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <meta name="theme-color" content="#0ea5e9">
              <title>Comandera Móvil — Restaurante 2026</title>
              <link rel="manifest" href="/manifest.json">
              <style>
                :root {
                  --bg: #0f172a;
                  --card: #1e293b;
                  --text: #f8fafc;
                  --muted: #94a3b8;
                  --primary: #0284c7;
                  --primary-hover: #0369a1;
                  --accent: #10b981;
                  --danger: #ef4444;
                  --border: #334155;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                body { background: var(--bg); color: var(--text); padding-bottom: 90px; -webkit-tap-highlight-color: transparent; }
                header { background: #0b1120; border-bottom: 1px solid var(--border); padding: 12px 16px; position: sticky; top: 0; z-index: 50; display: flex; justify-content: space-between; align-items: center; }
                .brand { font-size: 1.15rem; font-weight: 700; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
                .badge { background: #0369a1; color: white; padding: 4px 10px; border-radius: 9999px; font-size: 0.8rem; font-weight: 600; }
                .container { max-width: 900px; margin: 0 auto; padding: 16px; }
                
                /* Grid de Salas y Mesas */
                .section-title { font-size: 1rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 12px; font-weight: 600; }
                .salas-nav { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 8px; margin-bottom: 14px; }
                .tab-btn { background: var(--card); border: 1px solid var(--border); color: var(--text); padding: 10px 16px; border-radius: 8px; font-weight: 600; font-size: 0.95rem; white-space: nowrap; cursor: pointer; }
                .tab-btn.active { background: var(--primary); border-color: var(--primary); color: white; }
                
                .mesas-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(105px, 1fr)); gap: 12px; margin-bottom: 24px; }
                .mesa-btn { background: var(--card); border: 2px solid var(--border); border-radius: 12px; padding: 16px 8px; text-align: center; cursor: pointer; transition: all 0.15s ease; min-height: 85px; display: flex; flex-direction: column; justify-content: center; }
                .mesa-btn:active { transform: scale(0.96); }
                .mesa-btn.ocupada { border-color: #f59e0b; background: rgba(245, 158, 11, 0.15); }
                .mesa-btn.seleccionada { border-color: var(--accent); background: rgba(16, 185, 129, 0.2); }
                .mesa-num { font-size: 1.35rem; font-weight: 800; }
                .mesa-sub { font-size: 0.75rem; color: var(--muted); margin-top: 4px; }
                
                /* Buscador y Catálogo */
                .search-box { width: 100%; background: var(--card); border: 1px solid var(--border); color: white; padding: 12px 16px; border-radius: 10px; font-size: 1rem; margin-bottom: 16px; outline: none; }
                .search-box:focus { border-color: var(--primary); }
                
                .platos-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
                .plato-card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 14px; display: flex; justify-content: space-between; align-items: center; }
                .plato-info { flex: 1; padding-right: 12px; }
                .plato-nombre { font-weight: 600; font-size: 0.95rem; line-height: 1.3; }
                .plato-precio { color: #38bdf8; font-weight: 700; margin-top: 4px; font-size: 0.9rem; }
                .btn-add { background: var(--primary); color: white; border: none; width: 44px; height: 44px; border-radius: 10px; font-size: 1.5rem; display: flex; align-items: center; justify-content: center; cursor: pointer; }
                .btn-add:active { background: var(--primary-hover); transform: scale(0.94); }
                
                /* Barra de Comanda Flotante */
                .cart-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #0b1120; border-top: 1px solid var(--border); padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; z-index: 100; }
                .btn-enviar { background: var(--accent); color: #022c22; font-weight: 800; font-size: 1.05rem; border: none; padding: 14px 24px; border-radius: 10px; cursor: pointer; display: flex; align-items: center; gap: 8px; }
                .btn-enviar:disabled { background: var(--border); color: var(--muted); cursor: not-allowed; }
                
                /* Modal */
                .modal-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 200; align-items: center; justify-content: center; padding: 16px; }
                .modal-overlay.open { display: flex; }
                .modal-content { background: var(--card); border: 1px solid var(--border); border-radius: 16px; width: 100%; max-width: 500px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; }
                .modal-header { padding: 16px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; font-weight: 700; }
                .modal-body { padding: 16px; overflow-y: auto; flex: 1; }
                .cart-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--border); }
                .qty-control { display: flex; align-items: center; gap: 10px; }
                .btn-qty { background: var(--border); color: white; border: none; width: 34px; height: 34px; border-radius: 8px; font-size: 1.2rem; cursor: pointer; }
              </style>
            </head>
            <body>
              <header>
                <div class="brand">🍽️ <span>Comandera</span></div>
                <div style="display: flex; gap: 8px; align-items: center;">
                  <span id="txtMesonero" class="badge">Mesonero #1</span>
                  <button onclick="cambiarMesonero()" style="background:none; border:none; color:var(--muted); font-size:1.1rem; cursor:pointer;">👤</button>
                </div>
              </header>

              <div class="container">
                <div class="section-title">1. Seleccionar Mesa</div>
                <div id="salasNav" class="salas-nav"></div>
                <div id="mesasGrid" class="mesas-grid"></div>

                <div class="section-title">2. Platos y Bebidas</div>
                <input type="text" id="txtBuscar" class="search-box" placeholder="Buscar plato o bebida..." oninput="filtrarPlatos()">
                <div id="platosGrid" class="platos-grid"></div>
              </div>

              <div class="cart-bar">
                <div>
                  <div style="font-size:0.8rem; color:var(--muted);" id="lblMesaSel">Mesa no seleccionada</div>
                  <div style="font-weight:700; font-size:1.1rem;" id="lblItemsCant">0 ítems agregados</div>
                </div>
                <div style="display:flex; gap:10px;">
                  <button class="tab-btn" onclick="abrirModal()">Ver Pedido</button>
                  <button class="btn-enviar" id="btnEnviar" disabled onclick="enviarComanda()">🚀 Enviar</button>
                </div>
              </div>

              <!-- Modal Resumen de Comanda -->
              <div class="modal-overlay" id="modalCarrito">
                <div class="modal-content">
                  <div class="modal-header">
                    <span id="modalTitulo">Comanda para Mesa</span>
                    <button onclick="cerrarModal()" style="background:none; border:none; color:white; font-size:1.4rem; cursor:pointer;">✕</button>
                  </div>
                  <div class="modal-body" id="modalCartItems"></div>
                  <div style="padding:16px; border-top:1px solid var(--border);">
                    <button class="btn-enviar" style="width:100%; justify-content:center;" onclick="enviarComanda()">Confirmar y Despachar a Cocina</button>
                  </div>
                </div>
              </div>

              <script>
                let state = {
                  salas: [],
                  salaActual: null,
                  mesaSeleccionada: null,
                  platos: [],
                  platosFiltrados: [],
                  carrito: [],
                  idMesonero: 1,
                  nombreMesonero: 'Mesonero 1'
                };

                async function init() {
                  const savedMes = localStorage.getItem('mesonero_id');
                  if (savedMes) {
                    state.idMesonero = parseInt(savedMes);
                    state.nombreMesonero = localStorage.getItem('mesonero_nombre') || 'Mesonero ' + savedMes;
                  }
                  document.getElementById('txtMesonero').textContent = state.nombreMesonero;

                  await cargarSalas();
                  await cargarPlatos();
                }

                async function cargarSalas() {
                  try {
                    const res = await fetch('/api/v1/salas');
                    state.salas = await res.json();
                    if (state.salas.length > 0) {
                      state.salaActual = state.salas[0];
                      renderSalas();
                      renderMesas();
                    }
                  } catch (e) {
                    console.error('Error cargando salas', e);
                  }
                }

                function renderSalas() {
                  const nav = document.getElementById('salasNav');
                  nav.innerHTML = state.salas.map(s => `
                    <button class="tab-btn ${s.id === state.salaActual.id ? 'active' : ''}" onclick="seleccionarSala(${s.id})">
                      ${s.nombre} (${s.mesas_ocupadas}/${s.mesas})
                    </button>
                  `).join('');
                }

                function seleccionarSala(id) {
                  state.salaActual = state.salas.find(s => s.id === id);
                  renderSalas();
                  renderMesas();
                }

                async function renderMesas() {
                  const grid = document.getElementById('mesasGrid');
                  grid.innerHTML = '<div style="color:var(--muted)">Cargando mesas...</div>';

                  let mesasOcupadas = [];
                  try {
                    const res = await fetch('/api/v1/mesas?id_sala=' + state.salaActual.id);
                    mesasOcupadas = await res.json();
                  } catch (e) {}

                  const ocupadasMap = new Map();
                  mesasOcupadas.forEach(m => ocupadasMap.set(m.num_mesa, m));

                  grid.innerHTML = '';
                  for (let i = 1; i <= state.salaActual.mesas; i++) {
                    const ocupada = ocupadasMap.get(i);
                    const btn = document.createElement('div');
                    const esSel = state.mesaSeleccionada === i;
                    btn.className = `mesa-btn ${ocupada ? 'ocupada' : ''} ${esSel ? 'seleccionada' : ''}`;
                    btn.onclick = () => seleccionarMesa(i);
                    btn.innerHTML = `
                      <div class="mesa-num">#${i}</div>
                      <div class="mesa-sub">${ocupada ? 'Ocupada (' + ocupada.mesonero + ')' : 'Libre'}</div>
                    `;
                    grid.appendChild(btn);
                  }
                }

                function seleccionarMesa(num) {
                  state.mesaSeleccionada = num;
                  document.getElementById('lblMesaSel').textContent = state.salaActual.nombre + ' — Mesa #' + num;
                  actualizarBotonEnviar();
                  renderMesas();
                }

                async function cargarPlatos() {
                  try {
                    const res = await fetch('/api/v1/platos');
                    state.platos = await res.json();
                    state.platosFiltrados = [...state.platos];
                    renderPlatos();
                  } catch (e) {
                    console.error('Error cargando platos', e);
                  }
                }

                function filtrarPlatos() {
                  const txt = document.getElementById('txtBuscar').value.toLowerCase();
                  state.platosFiltrados = state.platos.filter(p => p.nombre.toLowerCase().includes(txt) || p.categoria.toLowerCase().includes(txt));
                  renderPlatos();
                }

                function renderPlatos() {
                  const grid = document.getElementById('platosGrid');
                  grid.innerHTML = state.platosFiltrados.slice(0, 50).map(p => `
                    <div class="plato-card">
                      <div class="plato-info">
                        <div class="plato-nombre">${p.nombre}</div>
                        <div class="plato-precio">$${p.precio_usd.toFixed(2)} / Bs. ${p.precio_ves.toFixed(2)}</div>
                      </div>
                      <button class="btn-add" onclick="agregarAlCarrito(${p.id})">+</button>
                    </div>
                  `).join('');
                }

                function agregarAlCarrito(idPlato) {
                  const plato = state.platos.find(p => p.id === idPlato);
                  if (!plato) return;
                  const item = state.carrito.find(it => it.id === idPlato);
                  if (item) {
                    item.cantidad++;
                  } else {
                    state.carrito.push({ id: plato.id, nombre: plato.nombre, precio: plato.precio_usd, cantidad: 1, notas: '' });
                  }
                  actualizarCarritoUI();
                }

                function cambiarCantidad(idPlato, delta) {
                  const idx = state.carrito.findIndex(it => it.id === idPlato);
                  if (idx < 0) return;
                  state.carrito[idx].cantidad += delta;
                  if (state.carrito[idx].cantidad <= 0) {
                    state.carrito.splice(idx, 1);
                  }
                  actualizarCarritoUI();
                  renderModalItems();
                }

                function actualizarCarritoUI() {
                  const totalItems = state.carrito.reduce((acc, it) => acc + it.cantidad, 0);
                  document.getElementById('lblItemsCant').textContent = totalItems + ' ítems en comanda';
                  actualizarBotonEnviar();
                }

                function actualizarBotonEnviar() {
                  const btn = document.getElementById('btnEnviar');
                  btn.disabled = !(state.mesaSeleccionada && state.carrito.length > 0);
                }

                function abrirModal() {
                  if (!state.mesaSeleccionada) {
                    alert('Seleccione primero una mesa.');
                    return;
                  }
                  document.getElementById('modalTitulo').textContent = 'Comanda: ' + state.salaActual.nombre + ' — Mesa #' + state.mesaSeleccionada;
                  renderModalItems();
                  document.getElementById('modalCarrito').classList.add('open');
                }

                function cerrarModal() {
                  document.getElementById('modalCarrito').classList.remove('open');
                }

                function renderModalItems() {
                  const container = document.getElementById('modalCartItems');
                  if (state.carrito.length === 0) {
                    container.innerHTML = '<div style="color:var(--muted); text-align:center; padding:20px;">No hay ítems en la comanda.</div>';
                    return;
                  }
                  container.innerHTML = state.carrito.map(it => `
                    <div class="cart-item">
                      <div style="flex:1;">
                        <div style="font-weight:600;">${it.nombre}</div>
                        <input type="text" placeholder="Instrucción de cocina..." value="${it.notas || ''}"
                               onchange="actualizarNotas(${it.id}, this.value)"
                               style="background:none; border:none; border-bottom:1px dashed var(--muted); color:#38bdf8; font-size:0.8rem; width:90%; outline:none; margin-top:4px;">
                      </div>
                      <div class="qty-control">
                        <button class="btn-qty" onclick="cambiarCantidad(${it.id}, -1)">-</button>
                        <span style="font-weight:700; min-width:20px; text-align:center;">${it.cantidad}</span>
                        <button class="btn-qty" onclick="cambiarCantidad(${it.id}, 1)">+</button>
                      </div>
                    </div>
                  `).join('');
                }

                function actualizarNotas(id, notas) {
                  const it = state.carrito.find(x => x.id === id);
                  if (it) it.notas = notas;
                }

                async function enviarComanda() {
                  if (!state.mesaSeleccionada || state.carrito.length === 0) return;
                  const btn = document.getElementById('btnEnviar');
                  btn.disabled = true;
                  btn.textContent = 'Enviando...';

                  const payload = {
                    id_sala: state.salaActual.id,
                    num_mesa: state.mesaSeleccionada,
                    id_mesonero: state.idMesonero,
                    mesonero_nombre: state.nombreMesonero,
                    items: state.carrito.map(it => ({
                      id_plato: it.id,
                      cantidad: it.cantidad,
                      notas: it.notas || ''
                    }))
                  };

                  try {
                    const res = await fetch('/api/v1/comandas', {
                      method: 'POST',
                      headers: {
                        'Content-Type': 'application/json',
                        'Idempotency-Key': crypto.randomUUID()
                      },
                      body: JSON.stringify(payload)
                    });

                    const json = await res.json();
                    if (res.ok && json.ok) {
                      alert('¡Comanda #' + json.id_pedido + ' enviada a cocina exitosamente!');
                      state.carrito = [];
                      actualizarCarritoUI();
                      cerrarModal();
                      renderMesas();
                    } else {
                      alert('Error: ' + (json.error || 'No se pudo enviar'));
                    }
                  } catch (e) {
                    alert('Fallo de red: Verifique la conexión Wi-Fi con el servidor.');
                  } finally {
                    btn.textContent = '🚀 Enviar';
                    actualizarBotonEnviar();
                  }
                }

                async function cambiarMesonero() {
                  const codigo = prompt('Ingrese ID o Cédula del mesonero:');
                  if (!codigo) return;
                  try {
                    const res = await fetch('/api/v1/auth/login', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ codigo: codigo.trim() })
                    });
                    const json = await res.json();
                    if (res.ok && json.ok) {
                      state.idMesonero = json.id_mesonero;
                      state.nombreMesonero = json.nombre;
                      localStorage.setItem('mesonero_id', json.id_mesonero);
                      localStorage.setItem('mesonero_nombre', json.nombre);
                      document.getElementById('txtMesonero').textContent = json.nombre;
                      alert('Sesión iniciada: ' + json.nombre);
                    } else {
                      alert('Error: ' + (json.error || 'Mesonero no encontrado'));
                    }
                  } catch (e) {
                    alert('Error conectando con el servidor');
                  }
                }

                window.onload = init;
              </script>
            </body>
            </html>
            """;
    }

    // --------------------------------------------------------------------------
    // Utilidades Criptográficas y Auxiliares
    // --------------------------------------------------------------------------

    private static String generarTokenMesonero(Mesonero m) {
        String data = m.getId() + ":" + m.getCedula() + ":" + System.currentTimeMillis();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private static void enviarJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        agregarCabecerasCors(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void enviarError(HttpExchange exchange, int status, String mensaje) throws IOException {
        String json = "{\"ok\":false,\"error\":\"" + escaparJson(mensaje) + "\"}";
        enviarJson(exchange, status, json);
    }

    private static void enviarCorsOk(HttpExchange exchange) throws IOException {
        agregarCabecerasCors(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private static void agregarCabecerasCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key");
    }

    private static String escaparJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static Map<String, String> parsearJsonSimple(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;
        String clean = body.trim();
        if (clean.startsWith("{") && clean.endsWith("}")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        for (String pair : clean.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].replaceAll("[\"\\s]", "");
                String v = kv[1].replaceAll("[\"\\s]", "");
                map.put(k, v);
            }
        }
        return map;
    }

    private static ComandaPayload parsearComandaJson(String json) {
        ComandaPayload p = new ComandaPayload();
        try {
            p.idSala = extraerInt(json, "id_sala");
            p.numMesa = extraerInt(json, "num_mesa");
            p.idMesonero = extraerInt(json, "id_mesonero");
            p.mesoneroNombre = extraerString(json, "mesonero_nombre");

            int idxItems = json.indexOf("\"items\"");
            if (idxItems > 0) {
                int start = json.indexOf("[", idxItems);
                int end = json.indexOf("]", start);
                if (start > 0 && end > start) {
                    String itemsArray = json.substring(start + 1, end);
                    String[] objetos = itemsArray.split("\\},\\s*\\{");
                    for (String obj : objetos) {
                        int idPlato = extraerInt(obj, "id_plato");
                        int cantidad = extraerInt(obj, "cantidad");
                        String notas = extraerString(obj, "notas");

                        if (idPlato > 0 && cantidad > 0) {
                            ItemComandaDto it = new ItemComandaDto();
                            it.idPlato = idPlato;
                            it.cantidad = cantidad;
                            it.notas = notas;
                            p.items.add(it);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error parseando comanda JSON: " + ex.getMessage());
        }
        return p;
    }

    private static int extraerInt(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return 0;
            int colon = json.indexOf(":", idx);
            int end = json.indexOf(",", colon);
            if (end < 0) end = json.indexOf("}", colon);
            if (end < 0) end = json.length();
            return Integer.parseInt(json.substring(colon + 1, end).replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String extraerString(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return "";
            int colon = json.indexOf(":", idx);
            int quote1 = json.indexOf("\"", colon);
            if (quote1 < 0) return "";
            int quote2 = json.indexOf("\"", quote1 + 1);
            if (quote2 < 0) return "";
            return json.substring(quote1 + 1, quote2).trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static class ItemComandaDto {
        public int idPlato;
        public int cantidad;
        public String notas = "";
    }

    public static class ComandaPayload {
        public int idSala;
        public int numMesa;
        public int idMesonero;
        public String mesoneroNombre = "Mesonero";
        public List<ItemComandaDto> items = new ArrayList<>();
    }

    private record RespuestaIdempotente(int codigoStatus, String cuerpoJson, Instant expiraEn) {
        boolean haExpirado() {
            return Instant.now().isAfter(expiraEn);
        }
    }
}
