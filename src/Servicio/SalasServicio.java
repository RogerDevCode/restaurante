package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Salas;
import Modelo.SalasRepositorio;
import java.util.List;

/** Casos de uso de salas con autorización y validación antes de tocar persistencia. */
public final class SalasServicio {
    private final SalasRepositorio repositorio;
    private final PoliticaAcceso politica;

    public SalasServicio(SalasRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de salas son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public List<Salas> listar() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_SALAS);
        return repositorio.listar();
    }

    public boolean registrar(Salas sala) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_SALAS);
        validarDatos(sala);
        return repositorio.registrar(normalizar(sala));
    }

    public boolean modificar(Salas sala) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_SALAS);
        validarDatos(sala);
        if (sala.getId() <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una sala válida para modificar.");
        }
        return repositorio.modificar(normalizar(sala));
    }

    public boolean eliminar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_SALAS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una sala válida para eliminar.");
        }
        return repositorio.eliminar(id);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }

    private void validarDatos(Salas sala) {
        if (sala == null || sala.getNombre() == null || sala.getNombre().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre de la sala es obligatorio.");
        }
        if (sala.getMesas() <= 0) {
            throw ErrorAplicacionException.validacion("La sala debe tener al menos una mesa.");
        }
    }

    private Salas normalizar(Salas sala) {
        return new Salas(sala.getId(), sala.getNombre().trim(), sala.getMesas());
    }
}
