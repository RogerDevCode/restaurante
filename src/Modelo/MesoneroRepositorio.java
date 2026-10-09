package Modelo;

import java.util.List;

public interface MesoneroRepositorio {
    int registrar(Mesonero mesonero);
    boolean modificar(Mesonero mesonero);
    boolean cambiarActivo(int id, boolean activo);
    boolean eliminarLogico(int id);
    Mesonero buscarPorId(int id);
    List<Mesonero> listarTodos();
    List<Mesonero> listarActivos();
}
