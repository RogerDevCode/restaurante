package Controlador;

import Modelo.Categoria;
import Modelo.ErrorAplicacionException;
import Servicio.CategoriaServicio;
import java.util.List;

/** Coordina las solicitudes de la vista con los casos de uso de categorías. */
public final class CategoriaControlador {

    private final CategoriaServicio servicio;

    public CategoriaControlador(CategoriaServicio servicio) {
        if (servicio == null) {
            throw ErrorAplicacionException.validacion("El servicio de categorías es obligatorio.");
        }
        this.servicio = servicio;
    }

    public List<Categoria> listar() {
        return servicio.listar();
    }

    public int registrar(Categoria categoria) {
        return servicio.registrar(categoria);
    }

    public boolean modificar(Categoria categoria) {
        return servicio.modificar(categoria);
    }

    public boolean eliminar(int id) {
        return servicio.eliminar(id);
    }

    public Categoria buscar(int id) {
        return servicio.buscar(id);
    }

    public boolean asignarPlato(String nombrePlato, int idCategoria) {
        return servicio.asignarPlato(nombrePlato, idCategoria);
    }
}