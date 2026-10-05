package Controlador;

import Modelo.ErrorAplicacionException;
import Modelo.Platos;
import Servicio.PlatosServicio;
import java.util.List;

/** Coordina las solicitudes de la vista con los casos de uso de platos. */
public final class PlatosControlador {
    private final PlatosServicio servicio;

    public PlatosControlador(PlatosServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de platos es obligatorio.");
        }
        this.servicio = servicio;
    }

    public List<Platos> listarPorFecha(String nombre, String fecha) {
        return servicio.listarPorFecha(nombre, fecha);
    }

    public boolean registrar(Platos plato) {
        return servicio.registrar(plato);
    }

    public boolean modificar(Platos plato) {
        return servicio.modificar(plato);
    }

    public boolean eliminar(int id) {
        return servicio.eliminar(id);
    }
}
