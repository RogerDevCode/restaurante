package Modelo;

/** Puerto de persistencia para marcar platos como favoritos (identidad por nombre normalizado). */
public interface FavoritoRepositorio {

    /** Marca o desmarca un plato como favorito según el flag. */
    boolean marcar(String nombrePlato, boolean favorito);
}