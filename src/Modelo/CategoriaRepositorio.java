package Modelo;

import java.util.List;

/** Puerto de persistencia para categorías del menú y su asignación a platos. */
public interface CategoriaRepositorio {

    int registrar(Categoria categoria);

    boolean modificar(Categoria categoria);

    /** Soft-delete: marca la categoría como inactiva (activo = 0). */
    boolean eliminar(int id);

    /** Lista únicamente las categorías activas (activo = 1), para menús y selección operativa. */
    List<Categoria> listar();

    /** Lista todas las categorías (activas e inactivas) para administración y reactivación. */
    default List<Categoria> listarTodas() {
        return listar();
    }

    Categoria buscar(int id);

    default Categoria buscarPorNombre(String nombre) {
        return null;
    }

    /** Obtiene o asegura la categoría canónica 'General' (imborrable y siempre activa). */
    default Categoria obtenerGeneral() {
        return null;
    }

    /** Activa o desactiva (undelete / soft-delete) una categoría. */
    default boolean cambiarEstadoActivo(int id, boolean activo) {
        return false;
    }

    /** Reasigna atómicamente todos los platos a la categoría destino y desactiva la categoría origen. */
    default boolean desactivarConReasignacion(int idCategoriaADesactivar, int idCategoriaDestino) {
        return false;
    }

    boolean asignarPlato(String nombrePlato, int idCategoria);

    boolean quitarAsignacionPlato(String nombrePlato);

    boolean categoriaEnUso(int idCategoria);

    default int contarPlatosEnCategoria(int idCategoria) {
        return categoriaEnUso(idCategoria) ? 1 : 0;
    }
}