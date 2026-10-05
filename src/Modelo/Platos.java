package Modelo;

import java.math.BigDecimal;

public class Platos {
    private int id;
    private String nombre;
    private BigDecimal precio = BigDecimal.ZERO;
    private String fecha;

    public Platos() {
    }

    public Platos(int id, String nombre, BigDecimal precio, String fecha) {
        this.id = id;
        this.nombre = nombre;
        setPrecioDecimal(precio);
        this.fecha = fecha;
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
        this.precio = precio;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    
    
    
}
