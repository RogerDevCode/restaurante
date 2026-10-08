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
}
