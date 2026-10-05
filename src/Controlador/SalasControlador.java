package Controlador;

import Modelo.ErrorAplicacionException;
import Modelo.Salas;
import Servicio.SalasServicio;
import java.util.List;

/** Coordina las solicitudes de la vista con los casos de uso de salas. */
public final class SalasControlador {
    private final SalasServicio servicio;

    public SalasControlador(SalasServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de salas es obligatorio.");
        }
        this.servicio = servicio;
    }

    public List<Salas> listar() {
        return servicio.listar();
    }

    public boolean registrar(Salas sala) {
        return servicio.registrar(sala);
    }

    public boolean modificar(Salas sala) {
        return servicio.modificar(sala);
    }

    public boolean eliminar(int id) {
        return servicio.eliminar(id);
    }
}
