package Modelo;

import java.util.List;

/** Puerto de persistencia para el menú diario de platos. */
public interface PlatosRepositorio {
    boolean registrar(Platos plato);

    List<Platos> listarPorFecha(String nombre, String fecha);

    boolean eliminar(int id);

    boolean modificar(Platos plato);
}
