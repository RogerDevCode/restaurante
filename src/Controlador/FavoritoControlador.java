package Controlador;

import Modelo.ErrorAplicacionException;
import Servicio.FavoritoServicio;

/** Coordina las solicitudes de la vista con el caso de uso de favoritos. */
public final class FavoritoControlador {

    private final FavoritoServicio servicio;

    public FavoritoControlador(FavoritoServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de favoritos es obligatorio.");
        }
        this.servicio = servicio;
    }

    public boolean marcar(String nombrePlato, boolean favorito) {
        return servicio.marcar(nombrePlato, favorito);
    }
}