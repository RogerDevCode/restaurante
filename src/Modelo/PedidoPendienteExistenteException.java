package Modelo;

import java.sql.SQLException;
import java.util.logging.Level;

public class PedidoPendienteExistenteException extends DataAccessException {
    public PedidoPendienteExistenteException(int numeroMesa, SQLException causa) {
        super("La mesa " + numeroMesa + " ya tiene un pedido pendiente.", causa, Level.WARNING);
    }
}
