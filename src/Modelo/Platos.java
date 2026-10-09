package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class Platos {
    private int id;
    private String nombre;
    private BigDecimal precio = BigDecimal.ZERO.setScale(2);
    private String fecha;
    private boolean activo = true;
    private LocalDateTime desactivadoEn;
    private boolean aplicaIva = true;
    private int idCategoria;
    private String categoriaNombre;
    private String categoriaColor;
    private boolean favorito;
    private BigDecimal rankingTotal;

    public Platos() {
    }

    public Platos(int id, String nombre, BigDecimal precio, String fecha) {
        this(id, nombre, precio, fecha, true);
    }

    public Platos(int id, String nombre, BigDecimal precio, String fecha, boolean aplicaIva) {
        this.id = id;
        this.nombre = nombre;
        setPrecioDecimal(precio);
        this.fecha = fecha;
        this.aplicaIva = aplicaIva;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPrecioDecimal() {
        return precio;
    }

    public void setPrecioDecimal(BigDecimal precio) {
        if (precio == null) {
            throw ErrorAplicacionException.validacion("El precio del plato es obligatorio.");
        }
        this.precio = (precio.scale() <= 2) ? precio.setScale(2, RoundingMode.HALF_UP) : precio;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getDesactivadoEn() {
        return desactivadoEn;
    }

    public void setDesactivadoEn(LocalDateTime desactivadoEn) {
        this.desactivadoEn = desactivadoEn;
    }

    public boolean isAplicaIva() {
        return aplicaIva;
    }

    public void setAplicaIva(boolean aplicaIva) {
        this.aplicaIva = aplicaIva;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public void setCategoriaNombre(String categoriaNombre) {
        this.categoriaNombre = categoriaNombre;
    }

    public String getCategoriaColor() {
        return categoriaColor;
    }

    public void setCategoriaColor(String categoriaColor) {
        this.categoriaColor = categoriaColor;
    }

    public boolean isFavorito() {
        return favorito;
    }

    public void setFavorito(boolean favorito) {
        this.favorito = favorito;
    }

    /** Total de unidades pedidas en los últimos 7 días o null si no está en el top 10. */
    public BigDecimal getRankingTotal() {
        return rankingTotal;
    }

    public void setRankingTotal(BigDecimal rankingTotal) {
        this.rankingTotal = rankingTotal;
    }
}
