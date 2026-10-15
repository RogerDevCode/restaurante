package Modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DetallePedido {
    public static final int CANTIDAD_MAXIMA_POR_PLATO = 999;

    private int id;
    private String nombre;
    private BigDecimal precio = BigDecimal.ZERO.setScale(2);
    private int cantidad;
    private String comentario;
    private int id_pedido;
    // Nullable para líneas históricas sin enlace recuperable al catálogo.
    private Integer idPlato;
    private String monedaBase;
    private Boolean precioIncluyeIva;
    private BigDecimal ivaPorcentaje;
    // NULL identifica detalles históricos cuyo tratamiento fiscal no quedó registrado.
    private Boolean aplicaIva = Boolean.TRUE;

    public DetallePedido() {
    }

    public DetallePedido(int id, String nombre, BigDecimal precio, int cantidad, String comentario, int id_pedido) {
        this.id = id;
        this.nombre = nombre;
        setPrecioDecimal(precio);
        this.cantidad = cantidad;
        this.comentario = comentario;
        this.id_pedido = id_pedido;
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
            throw ErrorAplicacionException.validacion("El precio del detalle es obligatorio.");
        }
        this.precio = precio.setScale(2, RoundingMode.HALF_UP);
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public int getId_pedido() {
        return id_pedido;
    }

    public void setId_pedido(int id_pedido) {
        this.id_pedido = id_pedido;
    }

    public Integer getIdPlato() {
        return idPlato;
    }

    public void setIdPlato(Integer idPlato) {
        this.idPlato = idPlato;
    }

    public String getMonedaBase() {
        return monedaBase;
    }

    public void setMonedaBase(String monedaBase) {
        this.monedaBase = monedaBase;
    }

    public Boolean getPrecioIncluyeIva() {
        return precioIncluyeIva;
    }

    public void setPrecioIncluyeIva(Boolean precioIncluyeIva) {
        this.precioIncluyeIva = precioIncluyeIva;
    }

    public BigDecimal getIvaPorcentaje() {
        return ivaPorcentaje;
    }

    public void setIvaPorcentaje(BigDecimal ivaPorcentaje) {
        this.ivaPorcentaje = ivaPorcentaje;
    }

    public Boolean getAplicaIva() {
        return aplicaIva;
    }

    public void setAplicaIva(Boolean aplicaIva) {
        this.aplicaIva = aplicaIva;
    }
    
}
