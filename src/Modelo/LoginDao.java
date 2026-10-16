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
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginDao implements AutenticacionRepositorio {
    private static final Logger LOGGER = Logger.getLogger(LoginDao.class.getName());
    private static final Pattern PATRON_CORREO = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE);
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
        String sql = """
            SELECT id, nombre, correo, pass, rol
            FROM usuarios
            WHERE (correo = ? OR nombre = ?)
              AND (activo = 1 OR activo IS NULL)
            ORDER BY id ASC
            LIMIT 1 FOR UPDATE
            """;
        try (Connection conexion = conexiones.getConnection()) {
            boolean autoCommitPrevio = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try {
                Usuario usuario = null;
                String contrasenaGuardada = null;
                try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
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
                }
                if (usuario == null || !rolReconocido(usuario.getRol())) {
                    conexion.commit();
                    ErrorAplicacionException.registrarAdvertencia("Intento de inicio de sesión rechazado.");
                    return Optional.empty();
                }

                long ahora = obtenerAhoraServidorMs(conexion);
                EstadoIntentosLogin estado = leerEstadoIntentos(conexion, usuario.getId(), true);
                if (estado.bloqueadoHastaMs() > ahora) {
                    conexion.commit();
                    return Optional.empty();
                }
                if (estado.bloqueadoHastaMs() > 0) {
                    borrarEstadoIntentos(conexion, usuario.getId());
                    estado = new EstadoIntentosLogin(0, 0);
                }

                boolean claveValida = verificarOCrearHash(conexion, usuario.getId(), clave, contrasenaGuardada);
                if (!claveValida) {
                    int fallos = estado.intentos() + 1;
                    long bloqueoHasta = fallos >= MAX_INTENTOS_LOGIN
                            ? ahora + DURACION_BLOQUEO_LOGIN_MS : 0;
                    guardarEstadoIntentos(conexion, usuario.getId(), fallos, bloqueoHasta);
                    conexion.commit();
                    ErrorAplicacionException.registrarAdvertencia("Intento de inicio de sesión rechazado.");
                    return Optional.empty();
                }
                borrarEstadoIntentos(conexion, usuario.getId());
                conexion.commit();
                return Optional.of(usuario);
            } catch (SQLException | RuntimeException ex) {
                try { conexion.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            } finally {
                try { conexion.setAutoCommit(autoCommitPrevio); } catch (SQLException ignored) { }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo validar el usuario.", ex);
        }
    }

    private static final int MAX_INTENTOS_LOGIN = 5;
    private static final long DURACION_BLOQUEO_LOGIN_MS = 30_000L;
    private record EstadoIntentosLogin(int intentos, long bloqueadoHastaMs) { }

    private long obtenerAhoraServidorMs(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT CAST(FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000) AS UNSIGNED)");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("La BD no devolvió su hora actual.");
            return rs.getLong(1);
        }
    }

    private EstadoIntentosLogin leerEstadoIntentos(Connection conexion, int usuarioId, boolean bloquearFila) throws SQLException {
        String sql = "SELECT intentos, bloqueado_hasta_ms FROM login_intentos WHERE usuario_id = ?"
                + (bloquearFila ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new EstadoIntentosLogin(rs.getInt(1), rs.getLong(2))
                        : new EstadoIntentosLogin(0, 0);
            }
        }
    }

    private void guardarEstadoIntentos(Connection conexion, int usuarioId, int intentos, long bloqueoHasta) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("""
                INSERT INTO login_intentos (usuario_id, intentos, bloqueado_hasta_ms)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE intentos = VALUES(intentos), bloqueado_hasta_ms = VALUES(bloqueado_hasta_ms)
                """)) {
            ps.setInt(1, usuarioId);
            ps.setInt(2, intentos);
            ps.setLong(3, bloqueoHasta);
            ps.executeUpdate();
        }
    }

    private void borrarEstadoIntentos(Connection conexion, int usuarioId) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement("DELETE FROM login_intentos WHERE usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            ps.executeUpdate();
        }
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
            sentencia.setString(2, normalizarCorreoRegistro(reg.getCorreo()));
            sentencia.setString(3, passwordHasher.hash(reg.getPassword().toCharArray()));
            sentencia.setString(4, reg.getRol());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar usuario");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062) {
                if (ex.getMessage() != null && ex.getMessage().contains("uq_usuarios_nombre")) {
                    throw new ErrorAplicacionException("Ese nombre de usuario ya está registrado.", ex, Level.WARNING);
                }
                throw new ErrorAplicacionException("El correo electrónico ya está registrado.", ex, Level.WARNING);
            }
            throw new DataAccessException("No se pudo registrar el usuario.", ex);
        }
    }

    private boolean verificarOCrearHash(Connection conexion, int id, String clave, String guardada) throws SQLException {
        if (guardada == null) {
            return false;
        }
        char[] contrasena = clave.toCharArray();
        try {
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
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setString(1, hashNuevo);
                sentencia.setInt(2, id);
                sentencia.setString(3, guardada);
                return sentencia.executeUpdate() == 1;
            }
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }

    private boolean rolReconocido(String rol) {
        return "Administrador".equals(rol) || "Asistente".equals(rol);
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

    public static final int LONGITUD_MINIMA_CONTRASENA = 4;

    public static int contarLongitudSinEspacios(char[] chars) {
        if (chars == null) return 0;
        int count = 0;
        for (char c : chars) {
            if (!Character.isWhitespace(c)) {
                count++;
            }
        }
        return count;
    }

    public static boolean esBlanco(char[] chars) {
        if (chars == null || chars.length == 0) return true;
        for (char c : chars) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    public static void validarLongitudContrasena(char[] password) {
        if (password == null || password.length == 0 || esBlanco(password)) {
            throw ErrorAplicacionException.validacion("La contraseña es obligatoria.");
        }
        if (contarLongitudSinEspacios(password) < LONGITUD_MINIMA_CONTRASENA) {
            throw ErrorAplicacionException.validacion(
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres.");
        }
    }

    public static void validarLongitudContrasena(String password) {
        if (password == null) {
            validarLongitudContrasena((char[]) null);
            return;
        }
        char[] chars = password.toCharArray();
        try {
            validarLongitudContrasena(chars);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private void validarRegistro(Usuario reg) {
        if (reg == null || reg.getNombre() == null || reg.getNombre().trim().isEmpty()
                || reg.getCorreo() == null || reg.getCorreo().trim().isEmpty()
                || reg.getRol() == null || reg.getRol().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Todos los datos del usuario son obligatorios.");
        }
        validarLongitudContrasena(reg.getPassword());
        if (!"Administrador".equals(reg.getRol()) && !"Asistente".equals(reg.getRol())) {
            throw ErrorAplicacionException.validacion("El rol del usuario no es válido.");
        }
        reg.setNombre(NombreUsuario.normalizar(reg.getNombre()));
        normalizarCorreoRegistro(reg.getCorreo());
    }

    public static String normalizarCorreoRegistro(String correo) {
        if (correo == null) {
            throw ErrorAplicacionException.validacion("El correo electrónico es obligatorio.");
        }
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        int arroba = normalizado.lastIndexOf('@');
        if (normalizado.length() > 254 || arroba < 1 || arroba > 64
                || !PATRON_CORREO.matcher(normalizado).matches()) {
            throw ErrorAplicacionException.validacion("Ingresa un correo electrónico válido.");
        }
        return normalizado;
    }

    public List<Usuario> ListarUsuarios() {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
            SELECT id, nombre, correo, pass, rol, activo
            FROM usuarios
            ORDER BY id ASC
            """;
        try (Connection conexion = conexiones.getConnection()) {
            try (PreparedStatement sentencia = conexion.prepareStatement(sql);
                 ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    Usuario usuario = new Usuario();
                    usuario.setId(resultados.getInt("id"));
                    usuario.setNombre(resultados.getString("nombre"));
                    usuario.setCorreo(resultados.getString("correo"));
                    usuario.setRol(resultados.getString("rol"));
                    try {
                        Object act = resultados.getObject("activo");
                        usuario.setActivo(act == null || ((Number) act).intValue() == 1);
                    } catch (SQLException ignored) {
                        usuario.setActivo(true);
                    }
                    usuarios.add(usuario);
                }
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1054) {
                    return listarUsuariosSinColumnaActivo(conexion);
                }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron listar los usuarios.", ex);
        }
        return usuarios;
    }

    private List<Usuario> listarUsuariosSinColumnaActivo(Connection conexion) throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
            SELECT id, nombre, correo, pass, rol
            FROM usuarios
            ORDER BY id ASC
            """;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                Usuario usuario = new Usuario();
                usuario.setId(resultados.getInt("id"));
                usuario.setNombre(resultados.getString("nombre"));
                usuario.setCorreo(resultados.getString("correo"));
                usuario.setRol(resultados.getString("rol"));
                usuario.setActivo(true);
                usuarios.add(usuario);
            }
        }
        return usuarios;
    }

    public boolean cambiarContrasena(int idUsuario, char[] nuevaClave) {
        if (idUsuario <= 0) {
            throw ErrorAplicacionException.validacion("El identificador de usuario no es válido.");
        }
        validarLongitudContrasena(nuevaClave);
        String nuevoHash = passwordHasher.hash(nuevaClave);
        String sql = "UPDATE usuarios SET pass = ? WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setString(1, nuevoHash);
            st.setInt(2, idUsuario);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo cambiar la contraseña del usuario.", ex);
        }
    }

    public boolean cambiarContrasena(int idUsuario, String nuevaClave) {
        if (nuevaClave == null) {
            return cambiarContrasena(idUsuario, (char[]) null);
        }
        char[] chars = nuevaClave.toCharArray();
        try {
            return cambiarContrasena(idUsuario, chars);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private static final Object LOCK_MUTACION_ADMIN = new Object();

    public static boolean esAdministradorPrimario(String correo, String nombre) {
        return (correo != null && "admin".equalsIgnoreCase(correo.trim()))
                || (nombre != null && "admin".equalsIgnoreCase(nombre.trim()));
    }

    public boolean desactivarUsuario(int idUsuario) {
        if (idUsuario <= 0) {
            throw ErrorAplicacionException.validacion("El identificador de usuario no es válido.");
        }
        return ejecutarMutacionUsuarioProtegida(idUsuario, "desactivar");
    }

    public boolean reactivarUsuario(int idUsuario) {
        if (idUsuario <= 0) {
            throw ErrorAplicacionException.validacion("El identificador de usuario no es válido.");
        }
        String sql = "UPDATE usuarios SET activo = 1 WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, idUsuario);
            return st.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo reactivar el usuario.", ex);
        }
    }

    public boolean eliminarUsuario(int idUsuario) {
        if (idUsuario <= 0) {
            throw ErrorAplicacionException.validacion("El identificador de usuario no es válido.");
        }
        return ejecutarMutacionUsuarioProtegida(idUsuario, "eliminar");
    }

    public boolean esUsuarioActivo(int idUsuario) {
        if (idUsuario <= 0) return false;
        String sql = "SELECT activo FROM usuarios WHERE id = ?";
        try (Connection con = conexiones.getConnection();
             PreparedStatement st = con.prepareStatement(sql)) {
            st.setInt(1, idUsuario);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    Object act = rs.getObject("activo");
                    if (act == null) return true;
                    if (act instanceof Boolean b) return b;
                    if (act instanceof Number n) return n.intValue() == 1;
                    return "1".equals(act.toString()) || "true".equalsIgnoreCase(act.toString());
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo verificar el estado del usuario.", ex);
        }
        return false;
    }

    private boolean ejecutarMutacionUsuarioProtegida(int idUsuario, String accion) {
        synchronized (LOCK_MUTACION_ADMIN) {
            String sqlUsuario = "SELECT rol, correo, nombre FROM usuarios WHERE id = ? FOR UPDATE";
            String sqlOtros = "SELECT COUNT(*) FROM usuarios WHERE LOWER(rol) = 'administrador' AND id <> ? AND (activo = 1 OR activo IS NULL) FOR UPDATE";
            String sqlMutacion = "desactivar".equalsIgnoreCase(accion)
                    ? "UPDATE usuarios SET activo = 0 WHERE id = ?"
                    : "DELETE FROM usuarios WHERE id = ?";

            try (Connection con = conexiones.getConnection()) {
                boolean autoCommitPrevio = con.getAutoCommit();
                con.setAutoCommit(false);
                try {
                    String rol = null;
                    String correo = null;
                    String nombre = null;
                    try (PreparedStatement stUsuario = con.prepareStatement(sqlUsuario)) {
                        stUsuario.setInt(1, idUsuario);
                        try (ResultSet rs = stUsuario.executeQuery()) {
                            if (!rs.next()) {
                                con.rollback();
                                return false;
                            }
                            rol = rs.getString("rol");
                            correo = rs.getString("correo");
                            nombre = rs.getString("nombre");
                        }
                    }

                    if (esAdministradorPrimario(correo, nombre)) {
                        con.rollback();
                        throw ErrorAplicacionException.validacion(
                                "El usuario 'admin' es el administrador primario y no puede ser "
                                        + ("eliminar".equalsIgnoreCase(accion) ? "eliminado." : "desactivado."));
                    }

                    if ("administrador".equalsIgnoreCase(rol)) {
                        try (PreparedStatement stOtros = con.prepareStatement(sqlOtros)) {
                            stOtros.setInt(1, idUsuario);
                            try (ResultSet rsOtros = stOtros.executeQuery()) {
                                if (rsOtros.next() && rsOtros.getInt(1) == 0) {
                                    con.rollback();
                                    throw ErrorAplicacionException.validacion(
                                            "No se puede " + accion + " al único administrador activo del sistema.");
                                }
                            }
                        }
                    }

                    int filas;
                    try (PreparedStatement stMutacion = con.prepareStatement(sqlMutacion)) {
                        stMutacion.setInt(1, idUsuario);
                        filas = stMutacion.executeUpdate();
                    }

                    con.commit();
                    return filas > 0;
                } catch (SQLException | RuntimeException ex) {
                    try {
                        con.rollback();
                    } catch (SQLException rb) {
                        ex.addSuppressed(rb);
                    }
                    throw ex;
                } finally {
                    try {
                        con.setAutoCommit(autoCommitPrevio);
                    } catch (SQLException ignored) {
                    }
                }
            } catch (SQLException ex) {
                throw new DataAccessException("No se pudo " + accion + " el usuario.", ex);
            }
        }
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
            boolean autoCommitPrevio = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            try {
                int filas;
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

                if (filas == 0) {
                    filas = autoRecuperarConfig(conexion, conf);
                }
                if (filas <= 0) {
                    throw new SQLException("No se actualizó ni se pudo crear la fila de configuración empresarial.");
                }

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
                if (conf.getModoAtencion() != null) {
                    pares.put(ConfigClaves.MODO_ATENCION, conf.getModoAtencion());
                }
                configuracionRepo.guardarVarios(conexion, pares);
                conexion.commit();
                return true;
            } catch (SQLException | RuntimeException error) {
                try {
                    conexion.rollback();
                } catch (SQLException errorRollback) {
                    error.addSuppressed(errorRollback);
                }
                if (error instanceof SQLException errorSql) {
                    throw new DataAccessException("No se pudo actualizar atómicamente la configuración.", errorSql);
                }
                throw error;
            } finally {
                try {
                    conexion.setAutoCommit(autoCommitPrevio);
                } catch (SQLException errorRestauracion) {
                    LOGGER.log(Level.WARNING, "No se pudo restaurar el autocommit de la conexión de configuración.", errorRestauracion);
                }
            }
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
                LOGGER.log(Level.WARNING, "datosEmpresaLegacy: Columnas tasa_dolar/iva_porcentaje no existen en tabla config. Aplicando fallback referencial (36.5000 / 16.00%).");
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
                if (ajustes.containsKey(ConfigClaves.MODO_ATENCION)) {
                    configuracion.setModoAtencion(ajustes.get(ConfigClaves.MODO_ATENCION));
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Aviso aplicando configuración clave-valor: " + ex.getMessage());
        }
    }

    public boolean actualizarModoAtencion(String modo) {
        if (modo == null || modo.isBlank()) {
            modo = "OPCION_B";
        }
        String modoNormalizado = modo.trim().toUpperCase();
        try {
            if (configuracionRepo != null) {
                configuracionRepo.guardar(ConfigClaves.MODO_ATENCION, modoNormalizado);
                return true;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Aviso al guardar modo de atención en clave-valor: " + ex.getMessage());
        }
        return false;
    }

}
