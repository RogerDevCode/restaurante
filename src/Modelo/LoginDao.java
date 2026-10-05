package Modelo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoginDao implements AutenticacionRepositorio {
    private final Conexion cn = new Conexion();

    @Override
    public Optional<login> autenticar(String correo, String clave) {
        login usuario = log(correo, clave);
        if (usuario.getCorreo() == null) {
            return Optional.empty();
        }
        return Optional.of(usuario);
    }

    public login log(String correo, String pass) {
        if (correo == null || correo.trim().isEmpty() || pass == null || pass.isEmpty()) {
            throw ErrorAplicacionException.validacion("El correo y la contraseña son obligatorios.");
        }
        login usuario = new login();
        String sql = "SELECT * FROM usuarios WHERE correo = ? AND pass = ?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, correo);
            sentencia.setString(2, pass);
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    usuario.setId(resultados.getInt("id"));
                    usuario.setNombre(resultados.getString("nombre"));
                    usuario.setCorreo(resultados.getString("correo"));
                    usuario.setPass(resultados.getString("pass"));
                    usuario.setRol(resultados.getString("rol"));
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo validar el usuario.", ex);
        }
        if (usuario.getCorreo() == null) {
            ErrorAplicacionException.registrarAdvertencia("Intento de inicio de sesión rechazado.");
        }
        return usuario;
    }

    public boolean Registrar(login reg) {
        if (reg == null) {
            throw ErrorAplicacionException.validacion("Los datos del usuario son obligatorios.");
        }
        String sql = "INSERT INTO usuarios (nombre, correo, pass, rol) VALUES (?,?,?,?)";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, reg.getNombre());
            sentencia.setString(2, reg.getCorreo());
            sentencia.setString(3, reg.getPass());
            sentencia.setString(4, reg.getRol());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "registrar usuario");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo registrar el usuario.", ex);
        }
    }

    public List<login> ListarUsuarios() {
        List<login> usuarios = new ArrayList<>();
        String sql = "SELECT * FROM usuarios";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql);
                ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                login usuario = new login();
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
        String sql = "UPDATE config SET ruc=?, nombre=?, telefono=?, direccion=?, mensaje=? WHERE id=?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, conf.getRuc());
            sentencia.setString(2, conf.getNombre());
            sentencia.setString(3, conf.getTelefono());
            sentencia.setString(4, conf.getDireccion());
            sentencia.setString(5, conf.getMensaje());
            sentencia.setInt(6, conf.getId());
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "actualizar configuración de empresa");
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo actualizar la configuración.", ex);
        }
    }

    public Config datosEmpresa() {
        Config configuracion = null;
        String sql = "SELECT * FROM config";
        try (Connection conexion = cn.getConnection();
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
