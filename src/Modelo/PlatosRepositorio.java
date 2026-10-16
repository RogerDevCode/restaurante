package Modelo;

import java.util.List;

/** Puerto de persistencia para el menú diario de platos. */
public interface PlatosRepositorio {
    boolean registrar(Platos plato);

    List<Platos> listarPorFecha(String nombre, String fecha);

    /** Lista únicamente los platos clasificados y activos listos para la venta (POS/atención). */
    default List<Platos> listarPlatosParaVenta(String nombre, String fecha) {
        return listarPorFecha(nombre, fecha);
    }

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

    /**
     * Busca un plato activo con el mismo nombre (insensible a mayúsculas y espacios).
     * Vacío si no existe o el repositorio no lo soporta; usado para evitar altas duplicadas.
     */
    default java.util.Optional<Platos> buscarActivoPorNombre(String nombre) {
        return java.util.Optional.empty();
    }

    default ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, String usuario) {
        modificar(plato);
        return new ResultadoActualizacionPrecios(plato, 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.util.List.of(), 0);
    }

    default int contarPedidosAbiertosConPlato(int idPlato) {
        return 0;
    }
}
