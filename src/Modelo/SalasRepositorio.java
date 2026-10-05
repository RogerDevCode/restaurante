package Modelo;

import java.util.List;

/** Puerto de persistencia para salas y mesas. */
public interface SalasRepositorio {
    boolean registrar(Salas sala);

    List<Salas> listar();

    boolean eliminar(int id);

    boolean modificar(Salas sala);
}
