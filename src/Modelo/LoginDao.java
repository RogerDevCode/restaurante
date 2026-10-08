package Modelo;

import infraestructura.PasswordHasher;
import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginDao implements AutenticacionRepositorio {
    private static final Logger LOGGER = Logger.getLogger(LoginDao.class.getName());
    private final ProveedorConexionJdbc conexiones;
    private final PasswordHasher passwordHasher;
    private final ConfiguracionRepositorio configuracionRepo;

    public LoginDao() {
        this(new ProveedorConexionJdbc(), new PasswordHasher(), new ConfiguracionSistemaDao());
    }

    public LoginDao(ProveedorConexionJdbc conexiones, PasswordHasher passwordHasher) {
        this(conexiones, passwordHasher, new ConfiguracionSistemaDao(conexiones));
    }

    public LoginDao(ProveedorConexionJdbc conexiones, PasswordHasher passwordHasher, ConfiguracionRepositorio configuracionRepo) {
        if (conexiones == null || passwordHasher == null) {
            throw ErrorAplicacionException.validacion("Las dependencias de autenticación son obligatorias.");
        }
        this.conexiones = conexiones;
        this.passwordHasher = passwordHasher;
        this.configuracionRepo = configuracionRepo != null ? configuracionRepo : new ConfiguracionSistemaDao(conexiones);
    }

    public ConfiguracionRepositorio getConfiguracionRepo() {
        return configuracionRepo;
    }

    @Override
    public Optional<Usuario> autenticar(String correo, String clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
        Usuario usuario = null;
        String contrasenaGuardada = null;
        String sql = """
            SELECT id, nombre, correo, pass, rol
            FROM usuarios
            WHERE correo = ? OR nombre = ?
            ORDER BY id ASC
            LIMIT 1
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            String identificador = correo.trim();
            sentencia.setString(1, identificador);
            sentencia.setString(2, identificador);
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    usuario = new Usuario();
                    usuario.setId(resultados.getInt("id"));
                    usuario.setNombre(resultados.getString("nombre"));
                    usuario.setCorreo(resultados.getString("correo"));
                    usuario.setRol(resultados.getString("rol"));
                    contrasenaGuardada = resultados.getString("pass");
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo validar el usuario.", ex);
        }
        if (usuario == null || !rolReconocido(usuario.getRol())
                || !verificarOCrearHash(usuario.getId(), clave, contrasenaGuardada)) {
            ErrorAplicacionException.registrarAdvertencia("Intento de inicio de sesión rechazado.");
            return Optional.empty();
        }
        return Optional.of(usuario);
    }

    public Usuario log(String correo, String pass) {
        return autenticar(correo, pass).orElseGet(Usuario::new);
    }

    public boolean Registrar(Usuario reg) {
        validarRegistro(reg);
        String sql = """
            INSERT INTO usuarios (nombre, correo, pass, rol)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, reg.getNombre());
            sentencia.setString(2, reg.getCorreo());
            sentencia.setString(3, passwordHasher.hash(reg.getPassword().toCharArray()));
            sentencia.setString(4, reg.getRol());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar usuario");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                throw new ErrorAplicacionException("El correo electrónico ya está registrado.", ex, Level.WARNING);
            }
            throw new DataAccessException("No se pudo registrar el usuario.", ex);
        }
    }

    private boolean verificarOCrearHash(int id, String clave, String guardada) {
        if (guardada == null) {
            return false;
        }
        char[] contrasena = clave.toCharArray();
        if (passwordHasher.esHash(guardada)) {
            return passwordHasher.verificar(contrasena, guardada);
        }
        if (!compararEnTiempoConstante(clave, guardada)) {
            return false;
        }
        String hashNuevo = passwordHasher.hash(contrasena);
        String sql = """
            UPDATE usuarios
            SET pass = ?
            WHERE id = ? AND BINARY pass = BINARY ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, hashNuevo);
            sentencia.setInt(2, id);
            sentencia.setString(3, guardada);
            if (sentencia.executeUpdate() == 1) {
                return true;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar de forma segura la contraseña.", ex);
        }
        return verificarHashActual(id, contrasena);
    }

    private boolean rolReconocido(String rol) {
        return "Administrador".equals(rol) || "Asistente".equals(rol);
    }

    private boolean verificarHashActual(int id, char[] contrasena) {
        String sql = """
            SELECT pass
            FROM usuarios
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, id);
            try (ResultSet resultados = sentencia.executeQuery()) {
                return resultados.next() && passwordHasher.verificar(contrasena, resultados.getString("pass"));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo confirmar la migración segura de la contraseña.", ex);
        }
    }

    private boolean compararEnTiempoConstante(String primera, String segunda) {
        byte[] a = primera.getBytes(StandardCharsets.UTF_8);
        byte[] b = segunda.getBytes(StandardCharsets.UTF_8);
        try {
            return MessageDigest.isEqual(a, b);
        } finally {
            Arrays.fill(a, (byte) 0);
            Arrays.fill(b, (byte) 0);
        }
    }

    private void validarRegistro(Usuario reg) {
        if (reg == null || reg.getNombre() == null || reg.getNombre().trim().isEmpty()
                || reg.getCorreo() == null || reg.getCorreo().trim().isEmpty()
                || reg.getPassword() == null || reg.getPassword().isEmpty()
                || reg.getRol() == null || reg.getRol().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Todos los datos del usuario son obligatorios.");
        }
        if (!"Administrador".equals(reg.getRol()) && !"Asistente".equals(reg.getRol())) {
            throw ErrorAplicacionException.validacion("El rol del usuario no es válido.");
        }
    }

    public List<Usuario> ListarUsuarios() {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
            SELECT id, nombre, correo, pass, rol
            FROM usuarios
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Usuario usuario = new Usuario();
                usuario.setId(resultados.getInt("id"));
                usuario.setNombre(resultados.getString("nombre"));
                usuario.setCorreo(resultados.getString("correo"));
                usuario.setRol(resultados.getString("rol"));
                usuarios.add(usuario);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los usuarios.", ex);
        }
        return usuarios;
    }

    public boolean ModificarDatos(Config conf) {
        if (conf == null) {
            throw ErrorAplicacionException.validacion("Los datos de configuración son obligatorios.");
        }
        String sqlCompleto = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?, logo_path = ?, cliente_predeterminado_nombre = ?, cliente_predeterminado_documento = ?, meses_retencion_pedidos = ?, imprimir_logo_ticket = ?, impresora_tickets = ?, modo_salida_tickets = ?
            WHERE id = ?
            """;
        try (Connection conexion = conexiones.getConnection()) {
            int filas = 0;
            try (PreparedStatement sentencia = conexion.prepareStatement(sqlCompleto)) {
                sentencia.setString(1, conf.getRuc());
                sentencia.setString(2, conf.getNombre());
                sentencia.setString(3, conf.getTelefono());
                sentencia.setString(4, conf.getDireccion());
                sentencia.setString(5, conf.getMensaje());
                sentencia.setBigDecimal(6, conf.getTasaDolar());
                sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
                sentencia.setString(8, conf.getLogoPath());
                sentencia.setString(9, conf.getClienteDefaultNombre());
                sentencia.setString(10, conf.getClienteDefaultDocumento());
                sentencia.setInt(11, conf.getMesesRetencionPedidos() > 0 ? conf.getMesesRetencionPedidos() : 24);
                sentencia.setInt(12, conf.isImprimirLogoTicket() ? 1 : 0);
                sentencia.setString(13, conf.getImpresoraTickets());
                sentencia.setString(14, conf.getModoSalidaTickets() != null ? conf.getModoSalidaTickets().name() : "TERMICA_DIRECTA");
                sentencia.setInt(15, conf.getId());
                filas = sentencia.executeUpdate();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1054) {
                    filas = modificarDatosSinImpresora(conexion, conf);
                } else {
                    throw ex;
                }
            }

            // Si no se actualizó ninguna fila (por ejemplo ID no coincide o tabla vacía)
            if (filas == 0) {
                filas = autoRecuperarConfig(conexion, conf);
            }

            // Sincronizar parámetros operativos en la tabla clave-valor
            try {
                java.util.Map<String, String> pares = new java.util.HashMap<>();
                if (conf.getTasaDolar() != null) {
                    pares.put(ConfigClaves.TASA_DOLAR, conf.getTasaDolar().toPlainString());
                }
                if (conf.getIvaPorcentaje() != null) {
                    pares.put(ConfigClaves.IVA_PORCENTAJE, conf.getIvaPorcentaje().toPlainString());
                }
                if (conf.getImpresoraTickets() != null) {
                    pares.put(ConfigClaves.IMPRESORA_TICKETS, conf.getImpresoraTickets());
                }
                if (conf.getModoSalidaTickets() != null) {
                    pares.put(ConfigClaves.MODO_SALIDA_TICKETS, conf.getModoSalidaTickets().name());
                }
                pares.put(ConfigClaves.IMPRIMIR_LOGO_TICKET, String.valueOf(conf.isImprimirLogoTicket()));
                if (conf.getClienteDefaultNombre() != null) {
                    pares.put(ConfigClaves.CLIENTE_DEFAULT_NOMBRE, conf.getClienteDefaultNombre());
                }
                if (conf.getClienteDefaultDocumento() != null) {
                    pares.put(ConfigClaves.CLIENTE_DEFAULT_DOCUMENTO, conf.getClienteDefaultDocumento());
                }
                pares.put(ConfigClaves.MESES_RETENCION_PEDIDOS, String.valueOf(conf.getMesesRetencionPedidos() > 0 ? conf.getMesesRetencionPedidos() : 24));
                configuracionRepo.guardarVarios(pares);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Aviso sincronizando parámetros clave-valor: " + ex.getMessage());
            }

            return filas > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar la configuración.", ex);
        }
    }

    private int modificarDatosSinImpresora(Connection conexion, Config conf) throws SQLException {
        String sql = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?, logo_path = ?, cliente_predeterminado_nombre = ?, cliente_predeterminado_documento = ?, meses_retencion_pedidos = ?, imprimir_logo_ticket = ?
            WHERE id = ?
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            sentencia.setString(8, conf.getLogoPath());
            sentencia.setString(9, conf.getClienteDefaultNombre());
            sentencia.setString(10, conf.getClienteDefaultDocumento());
            sentencia.setInt(11, conf.getMesesRetencionPedidos() > 0 ? conf.getMesesRetencionPedidos() : 24);
            sentencia.setInt(12, conf.isImprimirLogoTicket() ? 1 : 0);
            sentencia.setInt(13, conf.getId());
            return sentencia.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return modificarDatosSinImprimirLogo(conexion, conf);
            }
            throw ex;
        }
    }

    private int modificarDatosSinImprimirLogo(Connection conexion, Config conf) throws SQLException {
        String sql = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?, logo_path = ?, cliente_predeterminado_nombre = ?, cliente_predeterminado_documento = ?, meses_retencion_pedidos = ?
            WHERE id = ?
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            sentencia.setString(8, conf.getLogoPath());
            sentencia.setString(9, conf.getClienteDefaultNombre());
            sentencia.setString(10, conf.getClienteDefaultDocumento());
            sentencia.setInt(11, conf.getMesesRetencionPedidos() > 0 ? conf.getMesesRetencionPedidos() : 24);
            sentencia.setInt(12, conf.getId());
            return sentencia.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return modificarDatosSinRetencion(conexion, conf);
            }
            throw ex;
        }
    }

    private int modificarDatosSinRetencion(Connection conexion, Config conf) throws SQLException {
        String sql = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?, logo_path = ?, cliente_predeterminado_nombre = ?, cliente_predeterminado_documento = ?
            WHERE id = ?
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            sentencia.setString(8, conf.getLogoPath());
            sentencia.setString(9, conf.getClienteDefaultNombre());
            sentencia.setString(10, conf.getClienteDefaultDocumento());
            sentencia.setInt(11, conf.getId());
            return sentencia.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return modificarDatosSinLogo(conexion, conf);
            }
            throw ex;
        }
    }

    private int modificarDatosSinLogo(Connection conexion, Config conf) throws SQLException {
        String sql = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?
            WHERE id = ?
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            sentencia.setInt(8, conf.getId());
            return sentencia.executeUpdate();
        }
    }

    private int autoRecuperarConfig(Connection conexion, Config conf) throws SQLException {
        // Intentar actualizar cualquier primera fila existente
        String sqlUpdatePrimero = """
            UPDATE config
            SET ruc = ?, nombre = ?, telefono = ?, direccion = ?, mensaje = ?, tasa_dolar = ?, iva_porcentaje = ?
            LIMIT 1
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sqlUpdatePrimero)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            int filas = sentencia.executeUpdate();
            if (filas > 0) {
                return filas;
            }
        }

        // Si la tabla estaba totalmente vacía, insertar fila inicial
        String sqlInsert = """
            INSERT INTO config (ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sqlInsert, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setBigDecimal(7, conf.getIvaPorcentaje() != null ? conf.getIvaPorcentaje() : new java.math.BigDecimal("16.00"));
            int filas = sentencia.executeUpdate();
            if (filas > 0) {
                try (ResultSet rs = sentencia.getGeneratedKeys()) {
                    if (rs.next()) {
                        conf.setId(rs.getInt(1));
                    }
                }
            }
            return filas;
        }
    }

    public Config datosEmpresa() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path,
                   cliente_predeterminado_nombre, cliente_predeterminado_documento, meses_retencion_pedidos, imprimir_logo_ticket,
                   impresora_tickets, modo_salida_tickets
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                configuracion.setLogoPath(resultados.getString("logo_path"));
                configuracion.setClienteDefaultNombre(resultados.getString("cliente_predeterminado_nombre"));
                configuracion.setClienteDefaultDocumento(resultados.getString("cliente_predeterminado_documento"));
                int meses = resultados.getInt("meses_retencion_pedidos");
                configuracion.setMesesRetencionPedidos(meses > 0 ? meses : 24);
                configuracion.setImprimirLogoTicket(resultados.getInt("imprimir_logo_ticket") != 0);
                String imp = resultados.getString("impresora_tickets");
                configuracion.setImpresoraTickets(imp != null ? imp : "DEFAULT");
                String modo = resultados.getString("modo_salida_tickets");
                configuracion.setModoSalidaTickets(ModoSalidaTicket.desdeCodigo(modo));
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacyConLogoTicket();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        aplicarConfiguracionClaveValor(configuracion);
        return configuracion;
    }

    private Config datosEmpresaLegacyConLogoTicket() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path,
                   cliente_predeterminado_nombre, cliente_predeterminado_documento, meses_retencion_pedidos, imprimir_logo_ticket
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                configuracion.setLogoPath(resultados.getString("logo_path"));
                configuracion.setClienteDefaultNombre(resultados.getString("cliente_predeterminado_nombre"));
                configuracion.setClienteDefaultDocumento(resultados.getString("cliente_predeterminado_documento"));
                int meses = resultados.getInt("meses_retencion_pedidos");
                configuracion.setMesesRetencionPedidos(meses > 0 ? meses : 24);
                configuracion.setImprimirLogoTicket(resultados.getInt("imprimir_logo_ticket") != 0);
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacyConRetencion();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }

    private Config datosEmpresaLegacyConRetencion() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path,
                   cliente_predeterminado_nombre, cliente_predeterminado_documento, meses_retencion_pedidos
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                configuracion.setLogoPath(resultados.getString("logo_path"));
                configuracion.setClienteDefaultNombre(resultados.getString("cliente_predeterminado_nombre"));
                configuracion.setClienteDefaultDocumento(resultados.getString("cliente_predeterminado_documento"));
                int meses = resultados.getInt("meses_retencion_pedidos");
                configuracion.setMesesRetencionPedidos(meses > 0 ? meses : 24);
                configuracion.setImprimirLogoTicket(true);
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacyConClientes();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }

    private Config datosEmpresaLegacyConClientes() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path,
                   cliente_predeterminado_nombre, cliente_predeterminado_documento
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                configuracion.setLogoPath(resultados.getString("logo_path"));
                configuracion.setClienteDefaultNombre(resultados.getString("cliente_predeterminado_nombre"));
                configuracion.setClienteDefaultDocumento(resultados.getString("cliente_predeterminado_documento"));
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacyConLogo();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }

    private Config datosEmpresaLegacyConLogo() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje, logo_path
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
                configuracion.setLogoPath(resultados.getString("logo_path"));
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacyConIva();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }

    private Config datosEmpresaLegacyConIva() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje, tasa_dolar, iva_porcentaje
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(resultados.getBigDecimal("tasa_dolar"));
                configuracion.setIvaPorcentaje(resultados.getBigDecimal("iva_porcentaje"));
            }
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {
                return datosEmpresaLegacy();
            }
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }

    private Config datosEmpresaLegacy() {
        Config configuracion = null;
        String sql = """
            SELECT id, ruc, nombre, telefono, direccion, mensaje
            FROM config
            """;
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
                configuracion = new Config();
                configuracion.setId(resultados.getInt("id"));
                configuracion.setRuc(resultados.getString("ruc"));
                configuracion.setNombre(resultados.getString("nombre"));
                configuracion.setTelefono(resultados.getString("telefono"));
                configuracion.setDireccion(resultados.getString("direccion"));
                configuracion.setMensaje(resultados.getString("mensaje"));
                configuracion.setTasaDolar(new java.math.BigDecimal("36.5000"));
                configuracion.setIvaPorcentaje(new java.math.BigDecimal("16.00"));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        aplicarConfiguracionClaveValor(configuracion);
        return configuracion;
    }

    private void aplicarConfiguracionClaveValor(Config configuracion) {
        if (configuracion == null || configuracionRepo == null) {
            return;
        }
        try {
            java.util.Map<String, String> ajustes = configuracionRepo.obtenerTodos();
            if (ajustes != null && !ajustes.isEmpty()) {
                if (ajustes.containsKey(ConfigClaves.TASA_DOLAR)) {
                    try {
                        configuracion.setTasaDolar(new java.math.BigDecimal(ajustes.get(ConfigClaves.TASA_DOLAR).trim()));
                    } catch (Exception ignored) {
                    }
                }
                if (ajustes.containsKey(ConfigClaves.IVA_PORCENTAJE)) {
                    try {
                        configuracion.setIvaPorcentaje(new java.math.BigDecimal(ajustes.get(ConfigClaves.IVA_PORCENTAJE).trim()));
                    } catch (Exception ignored) {
                    }
                }
                if (ajustes.containsKey(ConfigClaves.IMPRESORA_TICKETS)) {
                    configuracion.setImpresoraTickets(ajustes.get(ConfigClaves.IMPRESORA_TICKETS));
                }
                if (ajustes.containsKey(ConfigClaves.MODO_SALIDA_TICKETS)) {
                    configuracion.setModoSalidaTickets(ModoSalidaTicket.desdeCodigo(ajustes.get(ConfigClaves.MODO_SALIDA_TICKETS)));
                }
                if (ajustes.containsKey(ConfigClaves.IMPRIMIR_LOGO_TICKET)) {
                    String v = ajustes.get(ConfigClaves.IMPRIMIR_LOGO_TICKET);
                    configuracion.setImprimirLogoTicket(!"false".equalsIgnoreCase(v) && !"0".equals(v));
                }
                if (ajustes.containsKey(ConfigClaves.CLIENTE_DEFAULT_NOMBRE)) {
                    configuracion.setClienteDefaultNombre(ajustes.get(ConfigClaves.CLIENTE_DEFAULT_NOMBRE));
                }
                if (ajustes.containsKey(ConfigClaves.CLIENTE_DEFAULT_DOCUMENTO)) {
                    configuracion.setClienteDefaultDocumento(ajustes.get(ConfigClaves.CLIENTE_DEFAULT_DOCUMENTO));
                }
                if (ajustes.containsKey(ConfigClaves.MESES_RETENCION_PEDIDOS)) {
                    try {
                        configuracion.setMesesRetencionPedidos(Integer.parseInt(ajustes.get(ConfigClaves.MESES_RETENCION_PEDIDOS).trim()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso aplicando configuración clave-valor: " + ex.getMessage());
        }
    }
}
