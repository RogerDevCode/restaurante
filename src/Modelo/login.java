package Modelo;

/**
 * Fachada temporal para mantener compatibilidad con consumidores del antiguo
 * tipo `login` mientras migran individualmente a {@link Usuario}.
 */
@Deprecated
public class login extends Usuario {

    public login() {
        super();
    }

    public login(int id, String nombre, String correo, String pass, String rol) {
        super(id, nombre, correo, pass, rol);
    }

    public String getPass() {
        return getPassword();
    }

    public void setPass(String pass) {
        setPassword(pass);
    }
}
