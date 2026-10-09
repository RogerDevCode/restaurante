package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.FavoritoRepositorio;

/** Caso de uso para marcar/desmarcar platos favoritos con autorización. */
public final class FavoritoServicio {

    private final FavoritoRepositorio repositorio;
    private final PoliticaAcceso politica;

    public FavoritoServicio(FavoritoRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de favoritos son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public boolean marcar(String nombrePlato, boolean favorito) {
        exigir();
        if (nombrePlato == null || nombrePlato.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El plato es obligatorio para marcarlo como favorito.");
        }
        return repositorio.marcar(nombrePlato.trim(), favorito);
    }

    private void exigir() {
        if (!politica.permite(PoliticaAcceso.Accion.GESTIONAR_PLATOS)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite gestionar los favoritos del menú.");
        }
    }
}