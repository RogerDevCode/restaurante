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

    public CategoriaServicio(CategoriaRepositorio repositorio) {
        this(repositorio, new PoliticaAcceso(new Modelo.Usuario(0, "Sistema", "admin", "admin", RolesSistema.ADMINISTRADOR)));
    }

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

    public List<Categoria> listarTodas() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.listarTodas();
    }

    public Categoria obtenerGeneral() {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.obtenerGeneral();
    }

    public int contarPlatosEnCategoria(int id) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.contarPlatosEnCategoria(id);
    }

    public boolean categoriaEnUso(int id) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        return repositorio.categoriaEnUso(id);
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
        Categoria actual = buscar(categoria.getId());
        if (actual != null && actual.isGeneral()) {
            if (!categoria.isGeneral()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' es la categoría canónica del sistema y no se puede renombrar.");
            }
            if (!categoria.isActivo()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' es obligatoria y no se puede desactivar.");
            }
        }
        validar(categoria);
        verificarColorUnico(categoria, false);
        return repositorio.modificar(categoria);
    }

    /**
     * Soft-delete: Da de baja la categoría sin platos asociados.
     */
    public boolean eliminar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una categoría válida para eliminar.");
        }
        Categoria cat = buscar(id);
        if (cat != null && cat.isGeneral()) {
            throw ErrorAplicacionException.validacion("La categoría 'General' no se puede eliminar ni dar de baja.");
        }
        if (repositorio.categoriaEnUso(id)) {
            throw ErrorAplicacionException.validacion("No se puede eliminar una categoría que ya tiene platos asignados. Reasigna los platos primero.");
        }
        return repositorio.eliminar(id);
    }

    /**
     * Reasigna atómicamente todos los platos asociados a la categoría destino y da de baja la categoría origen.
     */
    public boolean desactivarConReasignacion(int idOrigen, int idDestino) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (idOrigen <= 0 || idDestino <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona categorías válidas para la reasignación.");
        }
        if (idOrigen == idDestino) {
            throw ErrorAplicacionException.validacion("La categoría destino debe ser diferente a la categoría que se desea dar de baja.");
        }
        Categoria origen = buscar(idOrigen);
        if (origen != null && origen.isGeneral()) {
            throw ErrorAplicacionException.validacion("La categoría 'General' no se puede eliminar ni dar de baja.");
        }
        Categoria destino = buscar(idDestino);
        if (destino == null || !destino.isActivo()) {
            throw ErrorAplicacionException.validacion("La categoría destino seleccionada debe estar activa.");
        }
        return repositorio.desactivarConReasignacion(idOrigen, idDestino);
    }

    /**
     * Reactiva una categoría previamente dada de baja (undelete).
     */
    public boolean reactivar(int id) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una categoría válida para reactivar.");
        }
        return repositorio.cambiarEstadoActivo(id, true);
    }

    /**
     * Cambia el estado activo/inactivo de una categoría.
     */
    public boolean cambiarEstadoActivo(int id, boolean activo) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        if (id <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona una categoría válida.");
        }
        if (!activo) {
            Categoria cat = buscar(id);
            if (cat != null && cat.isGeneral()) {
                throw ErrorAplicacionException.validacion("La categoría 'General' no se puede desactivar.");
            }
        }
        return repositorio.cambiarEstadoActivo(id, activo);
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
        for (Categoria existente : repositorio.listarTodas()) {
            boolean esLaMisma = !alCrear && existente.getId() == categoria.getId();
            if (!esLaMisma && color.equalsIgnoreCase(existente.getColor())) {
                throw ErrorAplicacionException.validacion(
                        "El color " + color + " ya está usado por la categoría '" + existente.getNombre() + "'.");
            }
        }
    }
}