package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClienteDao implements ClienteRepositorio {
    private final ProveedorConexionJdbc conexiones;

    public ClienteDao() {
        this(new ProveedorConexionJdbc());
    }

    public ClienteDao(ProveedorConexionJdbc conexiones) {
        if (conexiones == null) {
            throw ErrorAplicacionException.validacion("El proveedor de conexiones es obligatorio.");
        }
        this.conexiones = conexiones;
    }

    @Override
    public boolean guardarOActualizar(Cliente cliente) {
        if (cliente == null || cliente.getDocumento() == null || cliente.getDocumento().trim().isEmpty()
                || cliente.getNombre() == null || cliente.getNombre().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El documento y nombre del cliente son obligatorios.");
        }
        String sql = """
            INSERT INTO clientes (documento, nombre, telefono, direccion)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                nombre = VALUES(nombre),
                telefono = VALUES(telefono),
                direccion = VALUES(direccion)
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cliente.getDocumento().trim());
            sentencia.setString(2, cliente.getNombre().trim());
            sentencia.setString(3, cliente.getTelefono() != null ? cliente.getTelefono().trim() : null);
            sentencia.setString(4, cliente.getDireccion() != null ? cliente.getDireccion().trim() : null);
            return sentencia.executeUpdate() >= 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo guardar o actualizar el cliente.", ex);
        }
    }

    @Override
    public List<Cliente> buscarClientes(String criterio) {
        List<Cliente> clientes = new ArrayList<>();
        boolean tieneFiltro = criterio != null && !criterio.trim().isEmpty();
        String filtroLike = tieneFiltro ? "%" + criterio.trim() + "%" : "%";

        String sql = """
            SELECT c.id, c.documento, c.nombre, c.telefono, c.direccion, c.creado_en,
                   COUNT(p.id) AS total_facturas,
                   COALESCE(SUM(p.total), 0) AS total_gastado_usd,
                   COALESCE(SUM(p.total_bs), 0) AS total_gastado_bs
            FROM clientes c
            LEFT JOIN pedidos p ON (p.cliente_documento = c.documento AND p.estado = 'FINALIZADO')
            WHERE (? = '%' OR c.documento LIKE ? OR c.nombre LIKE ?)
            GROUP BY c.id, c.documento, c.nombre, c.telefono, c.direccion, c.creado_en
            ORDER BY c.nombre ASC
            """;

        try (Connection conexion = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setString(1, filtroLike);
                sentencia.setString(2, filtroLike);
                sentencia.setString(3, filtroLike);

                try (ResultSet rs = sentencia.executeQuery()) {
                    while (rs.next()) {
                        Cliente cli = new Cliente();
                        cli.setId(rs.getInt("id"));
                        cli.setDocumento(rs.getString("documento"));
                        cli.setNombre(rs.getString("nombre"));
                        cli.setTelefono(rs.getString("telefono"));
                        cli.setDireccion(rs.getString("direccion"));
                        cli.setCreadoEn(rs.getString("creado_en"));
                        cli.setTotalFacturas(rs.getInt("total_facturas"));
                        cli.setTotalGastadoDolares(rs.getBigDecimal("total_gastado_usd"));
                        cli.setTotalGastadoBs(rs.getBigDecimal("total_gastado_bs"));
                        clientes.add(cli);
                    }
                }
            } catch (SQLException exQuery) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Consulta de clientes con agregados falló, usando consulta base: " + exQuery.getMessage());
                String sqlBase = "SELECT id, documento, nombre, telefono, direccion, creado_en FROM clientes WHERE (? = '%' OR documento LIKE ? OR nombre LIKE ?) ORDER BY nombre ASC";
                try (PreparedStatement sb = conexion.prepareStatement(sqlBase)) {
                    sb.setString(1, filtroLike);
                    sb.setString(2, filtroLike);
                    sb.setString(3, filtroLike);
                    try (ResultSet rs = sb.executeQuery()) {
                        while (rs.next()) {
                            Cliente cli = new Cliente();
                            cli.setId(rs.getInt("id"));
                            cli.setDocumento(rs.getString("documento"));
                            cli.setNombre(rs.getString("nombre"));
                            cli.setTelefono(rs.getString("telefono"));
                            cli.setDireccion(rs.getString("direccion"));
                            cli.setCreadoEn(rs.getString("creado_en"));
                            clientes.add(cli);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                    .log(java.util.logging.Level.WARNING, "No se pudieron consultar los clientes: " + ex.getMessage(), ex);
            return clientes;
        }
        return clientes;
    }

    @Override
    public Cliente buscarPorDocumento(String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return null;
        }
        String sql = """
            SELECT id, documento, nombre, telefono, direccion, creado_en
            FROM clientes
            WHERE documento = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, documento.trim());
            try (ResultSet rs = sentencia.executeQuery()) {
                if (rs.next()) {
                    Cliente cli = new Cliente();
                    cli.setId(rs.getInt("id"));
                    cli.setDocumento(rs.getString("documento"));
                    cli.setNombre(rs.getString("nombre"));
                    cli.setTelefono(rs.getString("telefono"));
                    cli.setDireccion(rs.getString("direccion"));
                    cli.setCreadoEn(rs.getString("creado_en"));
                    return cli;
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error al buscar cliente por documento.", ex);
        }
        return null;
    }

    @Override
    public List<Pedidos> listarFacturasCliente(String documentoOCriterio) {
        List<Pedidos> facturas = new ArrayList<>();
        if (documentoOCriterio == null || documentoOCriterio.trim().isEmpty()) {
            return facturas;
        }
        String docOTexto = documentoOCriterio.trim();
        String likeTexto = "%" + docOTexto + "%";

        String sql = """
            SELECT p.id, p.id_sala, p.num_mesa, p.fecha, p.subtotal, p.iva_porcentaje, p.iva_monto,
                   p.total, p.subtotal_bs, p.iva_bs, p.total_bs, p.tasa_cambio, p.usuario, p.estado,
                   s.nombre AS nombre_sala, p.cliente_nombre, p.cliente_documento
            FROM pedidos p
            INNER JOIN salas s ON p.id_sala = s.id
            WHERE (p.cliente_documento = ? OR p.cliente_nombre LIKE ?)
              AND p.estado = 'FINALIZADO'
            ORDER BY p.fecha DESC
            """;

        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, docOTexto);
            sentencia.setString(2, likeTexto);

            try (ResultSet rs = sentencia.executeQuery()) {
                while (rs.next()) {
                    Pedidos ped = new Pedidos();
                    ped.setId(rs.getInt("id"));
                    ped.setId_sala(rs.getInt("id_sala"));
                    ped.setSala(rs.getString("nombre_sala"));
                    ped.setNum_mesa(rs.getInt("num_mesa"));
                    ped.setFecha(rs.getString("fecha"));
                    ped.setSubtotal(rs.getBigDecimal("subtotal"));
                    ped.setIvaPorcentaje(rs.getBigDecimal("iva_porcentaje"));
                    ped.setIvaMonto(rs.getBigDecimal("iva_monto"));
                    ped.setTotalDecimal(rs.getBigDecimal("total"));
                    ped.setSubtotalBs(rs.getBigDecimal("subtotal_bs"));
                    ped.setIvaBs(rs.getBigDecimal("iva_bs"));
                    ped.setTotalBs(rs.getBigDecimal("total_bs"));
                    ped.setTasaCambio(rs.getBigDecimal("tasa_cambio"));
                    ped.setUsuario(rs.getString("usuario"));
                    ped.setEstado(rs.getString("estado"));
                    ped.setClienteNombre(rs.getString("cliente_nombre"));
                    ped.setClienteDocumento(rs.getString("cliente_documento"));
                    facturas.add(ped);
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar las facturas del cliente.", ex);
        }
        return facturas;
    }

    @Override
    public EstadisticasDashboard obtenerEstadisticasDashboard() {
        EstadisticasDashboard stats = new EstadisticasDashboard();
        try (Connection conexion = conexiones.getConnection()) {
            infraestructura.MigradorEsquemaJdbc.migrarSiEsNecesario(conexiones);

            // 1. Ventas Hoy
            try {
                String sqlHoy = """
                    SELECT COALESCE(SUM(total), 0) AS total_usd,
                           COALESCE(SUM(total_bs), 0) AS total_bs,
                           COUNT(id) AS cant
                    FROM pedidos
                    WHERE estado = 'FINALIZADO'
                      AND fecha >= CURRENT_DATE()
                      AND fecha < DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY)
                    """;
                try (PreparedStatement s = conexion.prepareStatement(sqlHoy);
                        ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        stats.setVentasHoyDolares(rs.getBigDecimal("total_usd"));
                        stats.setVentasHoyBs(rs.getBigDecimal("total_bs"));
                        stats.setPedidosHoy(rs.getInt("cant"));
                    }
                }
            } catch (SQLException exHoy) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando ventas de hoy: " + exHoy.getMessage());
                try {
                    String sqlHoyFallback = "SELECT COALESCE(SUM(total), 0) AS total_usd, COUNT(id) AS cant FROM pedidos WHERE estado = 'FINALIZADO' AND DATE(fecha) = CURRENT_DATE()";
                    try (PreparedStatement s = conexion.prepareStatement(sqlHoyFallback);
                            ResultSet rs = s.executeQuery()) {
                        if (rs.next()) {
                            stats.setVentasHoyDolares(rs.getBigDecimal("total_usd"));
                            stats.setPedidosHoy(rs.getInt("cant"));
                        }
                    }
                } catch (SQLException ignored) {
                }
            }

            // 2. Ventas Históricas
            try {
                String sqlHist = """
                    SELECT COALESCE(SUM(total), 0) AS total_usd,
                           COALESCE(SUM(total_bs), 0) AS total_bs,
                           COUNT(id) AS cant
                    FROM pedidos
                    WHERE estado = 'FINALIZADO'
                    """;
                try (PreparedStatement s = conexion.prepareStatement(sqlHist);
                        ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        stats.setVentasHistoricasDolares(rs.getBigDecimal("total_usd"));
                        stats.setVentasHistoricasBs(rs.getBigDecimal("total_bs"));
                        stats.setPedidosHistoricos(rs.getInt("cant"));
                    }
                }
            } catch (SQLException exHist) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando ventas históricas: " + exHist.getMessage());
                try {
                    String sqlHistFallback = "SELECT COALESCE(SUM(total), 0) AS total_usd, COUNT(id) AS cant FROM pedidos WHERE estado = 'FINALIZADO'";
                    try (PreparedStatement s = conexion.prepareStatement(sqlHistFallback);
                            ResultSet rs = s.executeQuery()) {
                        if (rs.next()) {
                            stats.setVentasHistoricasDolares(rs.getBigDecimal("total_usd"));
                            stats.setPedidosHistoricos(rs.getInt("cant"));
                        }
                    }
                } catch (SQLException ignored) {
                }
            }

            // 3. Total Clientes
            try {
                String sqlClientes = "SELECT COUNT(*) AS cant FROM clientes";
                try (PreparedStatement s = conexion.prepareStatement(sqlClientes);
                        ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        stats.setTotalClientes(rs.getInt("cant"));
                    }
                }
            } catch (SQLException exClientes) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando total de clientes: " + exClientes.getMessage());
                stats.setTotalClientes(0);
            }

            // 4. Mesas Ocupadas
            try {
                String sqlMesas = "SELECT COUNT(*) AS cant FROM pedidos WHERE estado = 'PENDIENTE'";
                try (PreparedStatement s = conexion.prepareStatement(sqlMesas);
                        ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        stats.setMesasOcupadasActuales(rs.getInt("cant"));
                    }
                }
            } catch (SQLException exMesas) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando mesas ocupadas: " + exMesas.getMessage());
                stats.setMesasOcupadasActuales(0);
            }

            // 5. Top 5 Platos Más Vendidos
            try {
                String sqlTopPlatos = """
                    SELECT d.nombre, SUM(d.cantidad) AS cant, SUM(d.precio * d.cantidad) AS total_usd
                    FROM detalle_pedidos d
                    INNER JOIN pedidos p ON d.id_pedido = p.id
                    WHERE p.estado = 'FINALIZADO'
                    GROUP BY d.nombre
                    ORDER BY cant DESC
                    LIMIT 5
                    """;
                List<EstadisticasDashboard.ItemEstadistica> topPlatos = new ArrayList<>();
                try (PreparedStatement s = conexion.prepareStatement(sqlTopPlatos);
                        ResultSet rs = s.executeQuery()) {
                    while (rs.next()) {
                        topPlatos.add(new EstadisticasDashboard.ItemEstadistica(
                                rs.getString("nombre"),
                                rs.getInt("cant"),
                                rs.getBigDecimal("total_usd"),
                                null
                        ));
                    }
                }
                stats.setTopPlatos(topPlatos);
            } catch (SQLException exPlatos) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando top platos: " + exPlatos.getMessage());
                stats.setTopPlatos(new ArrayList<>());
            }

            // 6. Top Salas con mayor movimiento
            try {
                String sqlTopSalas = """
                    SELECT s.nombre, COUNT(p.id) AS cant, SUM(p.total) AS total_usd
                    FROM pedidos p
                    INNER JOIN salas s ON p.id_sala = s.id
                    WHERE p.estado = 'FINALIZADO'
                    GROUP BY s.nombre
                    ORDER BY total_usd DESC
                    LIMIT 5
                    """;
                List<EstadisticasDashboard.ItemEstadistica> topSalas = new ArrayList<>();
                try (PreparedStatement s = conexion.prepareStatement(sqlTopSalas);
                        ResultSet rs = s.executeQuery()) {
                    while (rs.next()) {
                        topSalas.add(new EstadisticasDashboard.ItemEstadistica(
                                rs.getString("nombre"),
                                rs.getInt("cant"),
                                rs.getBigDecimal("total_usd"),
                                null
                        ));
                    }
                }
                stats.setTopSalas(topSalas);
            } catch (SQLException exSalas) {
                java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                        .log(java.util.logging.Level.WARNING, "Aviso consultando top salas: " + exSalas.getMessage());
                stats.setTopSalas(new ArrayList<>());
            }

        } catch (SQLException ex) {
            java.util.logging.Logger.getLogger(ClienteDao.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Error crítico de conexión al cargar estadísticas del dashboard", ex);
            throw new DataAccessException("No se pudieron cargar las estadísticas del dashboard (" + ex.getMessage() + ")", ex);
        }
        return stats;
    }
}
