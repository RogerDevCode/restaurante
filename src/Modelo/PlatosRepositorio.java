package Modelo;

import java.util.List;

/** Puerto de persistencia para el menú diario de platos. */
public interface PlatosRepositorio {
    boolean registrar(Platos plato);

    List<Platos> listarPorFecha(String nombre, String fecha);

    /** Soft delete: marca el plato como inactivo sin eliminarlo físicamente. */
    default boolean desactivar(int id) {
        return eliminar(id);
    }

    /** Reactiva un plato previamente desactivado. */
    default boolean reactivar(int id) {
        return true;
    }

    /** Lista los platos actualmente desactivados. */
    default List<Platos> listarInactivos() {
        return java.util.Collections.emptyList();
    }

    /** @deprecated Delega a {@link #desactivar(int)}; mantenido por compatibilidad. */
    @Deprecated
    default boolean eliminar(int id) {
        return desactivar(id);
    }

    boolean modificar(Platos plato);
}
