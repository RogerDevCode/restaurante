package Modelo;

import java.util.List;

/** Puerto de persistencia para categorías del menú y su asignación a platos. */
public interface CategoriaRepositorio {

    int registrar(Categoria categoria);

    boolean modificar(Categoria categoria);

    boolean eliminar(int id);

    List<Categoria> listar();

    Categoria buscar(int id);

    boolean asignarPlato(String nombrePlato, int idCategoria);

    boolean quitarAsignacionPlato(String nombrePlato);

    boolean categoriaEnUso(int idCategoria);
}