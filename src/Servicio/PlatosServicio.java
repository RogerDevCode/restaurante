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

    public PlatosServicio(PlatosRepositorio repositorio) {
        this(repositorio, PoliticaAcceso.denegada());
    }

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
        List<Platos> lista = repositorio.listarPorFecha(nombre.trim(), fecha);
        if (lista == null || lista.isEmpty()) {
            return lista != null ? lista : java.util.Collections.emptyList();
        }
        List<Platos> ordenada = new java.util.ArrayList<>(lista);
        ordenada.sort(java.util.Comparator
                .comparing(Platos::isFavorito, java.util.Comparator.reverseOrder())
                .thenComparing(p -> p.getRankingTotal() != null ? p.getRankingTotal() : BigDecimal.ZERO, java.util.Comparator.reverseOrder())
                .thenComparing(Platos::getCategoriaNombre, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(p -> p.getNombre() != null ? p.getNombre() : "", String.CASE_INSENSITIVE_ORDER)
        );
        return ordenada;
    }

    public List<Platos> listarPlatosParaVenta(String nombre, String fecha) {
        exigir(PoliticaAcceso.Accion.CONSULTAR_PLATOS);
        if (nombre == null) {
            throw ErrorAplicacionException.validacion("El filtro del menú es obligatorio.");
        }
        validarFecha(fecha);
        List<Platos> lista = repositorio.listarPlatosParaVenta(nombre.trim(), fecha);
        if (lista == null || lista.isEmpty()) {
            return lista != null ? lista : java.util.Collections.emptyList();
        }
        List<Platos> ordenada = new java.util.ArrayList<>(lista);
        ordenada.sort(java.util.Comparator
                .comparing(Platos::isFavorito, java.util.Comparator.reverseOrder())
                .thenComparing(p -> p.getRankingTotal() != null ? p.getRankingTotal() : BigDecimal.ZERO, java.util.Comparator.reverseOrder())
                .thenComparing(Platos::getCategoriaNombre, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(p -> p.getNombre() != null ? p.getNombre() : "", String.CASE_INSENSITIVE_ORDER)
        );
        return ordenada;
    }

    public boolean registrar(Platos plato) {
        return registrar(plato, null);
    }

    public boolean registrar(Platos plato, BigDecimal tasa) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validarDatos(plato);
        String nombre = plato.getNombre().trim();
        if (repositorio.buscarActivoPorNombre(nombre).isPresent()) {
            throw ErrorAplicacionException.validacion(
                    "Ya existe el plato '" + nombre + "'. Selecciónelo en la tabla y use Editar para modificarlo.");
        }
        return repositorio.registrar(normalizar(plato, tasa));
    }

    public boolean modificar(Platos plato) {
        return modificar(plato, null);
    }

    public boolean modificar(Platos plato, BigDecimal tasa) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validarDatos(plato);
        if (plato.getId() <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona un plato válido para modificar.");
        }
        return repositorio.modificar(normalizar(plato, tasa));
    }

    public Modelo.ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, String usuario) {
        return modificarProductoYRecalcularPendientes(plato, null, usuario);
    }

    public Modelo.ResultadoActualizacionPrecios modificarProductoYRecalcularPendientes(Platos plato, BigDecimal tasa, String usuario) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        validarDatos(plato);
        if (plato.getId() <= 0) {
            throw ErrorAplicacionException.validacion("Selecciona un plato válido para modificar.");
        }
        return repositorio.modificarProductoYRecalcularPendientes(normalizar(plato, tasa), usuario);
    }

    public int contarPedidosAbiertosConPlato(int idPlato) {
        exigir(PoliticaAcceso.Accion.GESTIONAR_PLATOS);
        return repositorio.contarPedidosAbiertosConPlato(idPlato);
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
        if (plato.getMonedaBase() == null || plato.getMonedaBase().trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("Debe indicar la moneda base del plato (USD o VES).");
        }
        String m = plato.getMonedaBase().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"USD".equals(m) && !"VES".equals(m)) {
            throw ErrorAplicacionException.validacion("La moneda base del plato debe ser USD o VES.");
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

    private Platos normalizar(Platos plato, BigDecimal tasa) {
        String moneda = plato.getMonedaBase().trim().toUpperCase(java.util.Locale.ROOT);

        Platos normalizado = new Platos(
                plato.getId(), plato.getNombre().trim(), plato.getPrecioDecimal(), plato.getFecha());
        normalizado.setAplicaIva(plato.isAplicaIva());
        normalizado.setIdCategoria(plato.getIdCategoria());
        normalizado.setCategoriaNombre(plato.getCategoriaNombre());
        normalizado.setCategoriaColor(plato.getCategoriaColor());
        normalizado.setFavorito(plato.isFavorito());
        normalizado.setRankingTotal(plato.getRankingTotal());
        normalizado.setMonedaBase(moneda);

        if (!normalizado.isAplicaIva()) {
            normalizado.setPrecioIncluyeIva(Boolean.FALSE);
        } else {
            if (plato.getPrecioIncluyeIva() == null) {
                throw ErrorAplicacionException.validacion("Debe indicar si el precio del plato incluye IVA.");
            }
            normalizado.setPrecioIncluyeIva(plato.getPrecioIncluyeIva());
        }

        BigDecimal precioPrimario = plato.getPrecioDecimal().setScale(2, java.math.RoundingMode.HALF_UP);
        if ("VES".equals(moneda)) {
            normalizado.setPrecioVes(precioPrimario);
            if (plato.getPrecioUsd() != null && plato.getPrecioUsd().signum() > 0) {
                normalizado.setPrecioUsd(plato.getPrecioUsd().setScale(2, java.math.RoundingMode.HALF_UP));
            } else if (tasa != null && tasa.compareTo(BigDecimal.ZERO) > 0) {
                normalizado.setPrecioUsd(precioPrimario.divide(tasa, 2, java.math.RoundingMode.HALF_UP));
            }
        } else {
            normalizado.setPrecioUsd(precioPrimario);
            if (plato.getPrecioVes() != null && plato.getPrecioVes().signum() > 0) {
                normalizado.setPrecioVes(plato.getPrecioVes().setScale(2, java.math.RoundingMode.HALF_UP));
            } else if (tasa != null && tasa.compareTo(BigDecimal.ZERO) > 0) {
                normalizado.setPrecioVes(precioPrimario.multiply(tasa).setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }

        boolean clasificado = plato.isPrecioClasificado()
                || (normalizado.getMonedaBase() != null && normalizado.getPrecioUsd() != null
                    && normalizado.getPrecioVes() != null && normalizado.getPrecioIncluyeIva() != null);
        normalizado.setPrecioClasificado(clasificado);

        return normalizado;
    }

    public boolean esAptoParaVenta(Platos plato) {
        if (plato == null || !plato.isPrecioClasificado()) {
            return false;
        }
        String moneda = plato.getMonedaBase();
        if (!"USD".equalsIgnoreCase(moneda) && !"VES".equalsIgnoreCase(moneda)) {
            return false;
        }
        BigDecimal precioPrimario = "VES".equalsIgnoreCase(moneda) ? plato.getPrecioVes() : plato.getPrecioUsd();
        if (precioPrimario == null || precioPrimario.signum() <= 0 || precioPrimario.scale() > 2) {
            return false;
        }
        if (plato.getPrecioIncluyeIva() == null) {
            return false;
        }
        if (!plato.isAplicaIva() && Boolean.TRUE.equals(plato.getPrecioIncluyeIva())) {
            return false;
        }
        return true;
    }
}
