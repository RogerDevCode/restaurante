package Servicio;

import Modelo.ErrorAplicacionException;
import Modelo.Platos;
import Modelo.PlatosRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Casos de uso del menú diario de platos con autorización y reglas monetarias. */
public final class PlatosServicio {
    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("99999999.99");
    private final PlatosRepositorio repositorio;
    private final PoliticaAcceso politica;

    public PlatosServicio(PlatosRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de platos son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public List<Platos> listarPorFecha(String nombre, String fecha) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        if (nombre == null) {
            throw ErrorAplicacionException.validacion("El filtro del menú es obligatorio.");
        }
        validarFecha(fecha);
        return repositorio.listarPorFecha(nombre.trim(), fecha);
    }

    public boolean registrar(Platos plato) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validarDatos(plato);
        return repositorio.registrar(normalizar(plato));
    }

    public boolean modificar(Platos plato) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validarDatos(plato);
        if (plato.getId() <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona un plato válido para modificar.");
        }
        return repositorio.modificar(normalizar(plato));
    }

    public boolean desactivar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona un plato válido para desactivar.");
        }
        return repositorio.desactivar(id);
    }

    public boolean reactivar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona un plato válido para reactivar.");
        }
        return repositorio.reactivar(id);
    }

    public java.util.List<Platos> listarInactivos() {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        return repositorio.listarInactivos();
    }

    public boolean eliminar(int id) {
        return desactivar(id);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }

    private void validarDatos(Platos plato) {
        if (plato == null || plato.getNombre() == null || plato.getNombre().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre del plato es obligatorio.");
        }
        validarPrecio(plato.getPrecioDecimal());
        validarFecha(plato.getFecha());
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() <= 0 || precio.scale() > 2
                || precio.compareTo(PRECIO_MAXIMO) > 0) {
            throw ErrorAplicacionException.validacion(
                    "El precio debe ser positivo y caber en DECIMAL(10,2) con hasta dos decimales.");
        }
    }

    private void validarFecha(String fecha) {
        if (fecha == null || fecha.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("La fecha del menú es obligatoria.");
        }
        try {
            LocalDate.parse(fecha);
        } catch (DateTimeParseException error) {
            throw ErrorAplicacionException.validacion("La fecha del menú debe usar formato AAAA-MM-DD.");
        }
    }

    private Platos normalizar(Platos plato) {
        return new Platos(plato.getId(), plato.getNombre().trim(), plato.getPrecioDecimal(), plato.getFecha());
    }
}
