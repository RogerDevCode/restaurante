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

    public List<Platos> listarPlatosParaVenta(String nombre, String fecha) {
        return servicio.listarPlatosParaVenta(nombre, fecha);
    }

    public boolean registrar(Platos plato) {
        return servicio.registrar(plato);
    }

    public boolean registrar(Platos plato, java.math.BigDecimal tasa) {
        return servicio.registrar(plato, tasa);
    }

    public boolean modificar(Platos plato) {
        return servicio.modificar(plato);
    }

    public boolean modificar(Platos plato, java.math.BigDecimal tasa) {
        return servicio.modificar(plato, tasa);
    }

    public boolean eliminar(int id) {
        return servicio.eliminar(id);
    }

    public boolean desactivar(int id) {
        return servicio.desactivar(id);
    }

    public boolean reactivar(int id) {
        return servicio.reactivar(id);
    }

    public java.util.List<Modelo.Platos> listarInactivos() {
        return servicio.listarInactivos();
    }

    public Modelo.ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, String usuario) {
        return servicio.modificarProductoYRecalcularPendientes(plato, usuario);
    }

    public Modelo.ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, java.math.BigDecimal tasa, String usuario) {
        return servicio.modificarProductoYRecalcularPendientes(plato, tasa, usuario);
    }

    public int contarPedidosAbiertosConPlato(int idPlato) {
        return servicio.contarPedidosAbiertosConPlato(idPlato);
    }
}

