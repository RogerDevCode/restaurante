package Servicio;

import Modelo.Categoria;
import Modelo.CategoriaRepositorio;
import Modelo.ErrorAplicacionException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Casos de uso de categorías del menú con autorización, nombres únicos y colores irrepetibles. */
public final class CategoriaServicio {

    private static final Pattern PATRON_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final CategoriaRepositorio repositorio;
    private final PoliticaAcceso politica;

    public CategoriaServicio(CategoriaRepositorio repositorio, PoliticaAcceso politica) {
        if (repositorio == null || politica == null) {
            throw ErrorAplicacionException.validacion("El repositorio y la política de categorías son obligatorios.");
        }
        this.repositorio = repositorio;
        this.politica = politica;
    }

    public List<Categoria> listar() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.listar();
    }

    public int registrar(Categoria categoria) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validar(categoria);
        verificarColorUnico(categoria, true);
        return repositorio.registrar(categoria);
    }

    public boolean modificar(Categoria categoria) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (categoria == null || categoria.getId() <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una categoría válida para modificar.");
        }
        validar(categoria);
        verificarColorUnico(categoria, false);
        return repositorio.modificar(categoria);
    }

    public boolean eliminar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una categoría válida para eliminar.");
        }
        if (repositorio.categoriaEnUso(id)) {
            throw ErrorAplicacionException.validacion("No se puede eliminar una categoría que ya tiene platos asignados.");
        }
        return repositorio.eliminar(id);
    }

    public Categoria buscar(int id) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.buscar(id);
    }

    /** Asigna o quita la categoría de un plato (identidad por nombre normalizado). */
    public boolean asignarPlato(String nombrePlato, int idCategoria) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (nombrePlato == null || nombrePlato.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El plato es obligatorio para asignar una categoría.");
        }
        if (idCategoria <= 0) {
            return repositorio.quitarAsignacionPlato(nombrePlato.trim());
        }
        return repositorio.asignarPlato(nombrePlato.trim(), idCategoria);
    }

    private void exigir(PoliticaAcceso.Accion accion) {
        if (!politica.permite(accion)) {
            throw ErrorAplicacionException.validacion("Tu rol no permite realizar esta acción.");
        }
    }

    private void validar(Categoria categoria) {
        if (categoria == null) {
            throw ErrorAplicacionException.validacion("La categoría es obligatoria.");
        }
        String nombre = categoria.getNombre();
        if (nombre == null || nombre.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre de la categoría es obligatorio.");
        }
        nombre = nombre.trim();
        if (nombre.length() > 60) {
            throw ErrorAplicacionException.validacion("El nombre de la categoría debe tener como máximo 60 caracteres.");
        }
        categoria.setNombre(nombre);
        String color = categoria.getColor();
        if (color == null || !PATRON_COLOR.matcher(color).matches()) {
            throw ErrorAplicacionException.validacion("El color debe tener formato #RRGGBB (por ejemplo #E45A4E).");
        }
        categoria.setColor(color.toUpperCase(Locale.ROOT));
    }

    private void verificarColorUnico(Categoria categoria, boolean alCrear) {
        String color = categoria.getColor();
        for (Categoria existente : repositorio.listar()) {
            boolean esLaMisma = !alCrear && existente.getId() == categoria.getId();
            if (!esLaMisma && color.equalsIgnoreCase(existente.getColor())) {
                throw ErrorAplicacionException.validacion(
                        "El color " + color + " ya está usado por la categoría '" + existente.getNombre() + "'.");
            }
        }
    }
}