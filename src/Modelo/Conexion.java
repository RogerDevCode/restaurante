package Modelo;

import infraestructura.ProveedorConexionJdbc;
import java.sql.Connection;
import java.sql.SQLException;

public class Conexion {
    private final ProveedorConexionJdbc proveedor = new ProveedorConexionJdbc();

    public Connection getConnection() throws SQLException {
        return proveedor.getConnection();
    }
}
