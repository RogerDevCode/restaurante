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

public class LoginDao implements AutenticacionRepositorio {
    private final ProveedorConexionJdbc conexiones;
    private final PasswordHasher passwordHasher;

    public LoginDao() {
        this(new ProveedorConexionJdbc(), new PasswordHasher());
    }

    public LoginDao(ProveedorConexionJdbc conexiones, PasswordHasher passwordHasher) {
        if (conexiones == null || passwordHasher == null) {
            throw ErrorAplicacionException.validacion("Las dependencias de autenticación son obligatorias.");
        }
        this.conexiones = conexiones;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Optional<Usuario> autenticar(String correo, String clave) {
        if (correo == null || correo.trim().isEmpty() || clave == null || clave.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
        Usuario usuario = null;
        String contrasenaGuardada = null;
        String sql = "SELECT id, nombre, correo, pass, rol FROM usuarios WHERE correo = ?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, correo.trim());
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
        String sql = "INSERT INTO usuarios (nombre, correo, pass, rol) VALUES (?,?,?,?)";
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
        String sql = "UPDATE usuarios SET pass = ? WHERE id = ? AND BINARY pass = BINARY ?";
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
        String sql = "SELECT pass FROM usuarios WHERE id = ?";
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
        String sql = "SELECT * FROM usuarios";
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
        String sql = "UPDATE config SET ruc=?, nombre=?, telefono=?, direccion=?, mensaje=?, tasa_dolar=? WHERE id=?";
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setBigDecimal(6, conf.getTasaDolar());
            sentencia.setInt(7, conf.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "actualizar configuración de empresa");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar la configuración.", ex);
        }
    }

    public Config datosEmpresa() {
        Config configuracion = null;
        String sql = "SELECT * FROM config";
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
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar la configuración.", ex);
        }
        if (configuracion == null) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        return configuracion;
    }
}
