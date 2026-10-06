package Modelo;

import java.math.BigDecimal;

public class Config {
    private int id;
    private String ruc;
    private String nombre;
    private String telefono;
    private String direccion;
    private String mensaje;
    private BigDecimal tasaDolar = new BigDecimal("36.5000");
    private BigDecimal ivaPorcentaje = new BigDecimal("16.00");

    public Config() {
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje) {
        this(id, ruc, nombre, telefono, direccion, mensaje, new BigDecimal("36.5000"), new BigDecimal("16.00"));
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje, BigDecimal tasaDolar) {
        this(id, ruc, nombre, telefono, direccion, mensaje, tasaDolar, new BigDecimal("16.00"));
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje, BigDecimal tasaDolar, BigDecimal ivaPorcentaje) {
        this.id = id;
        this.ruc = ruc;
        this.nombre = nombre;
        this.telefono = telefono;
        this.direccion = direccion;
        this.mensaje = mensaje;
        setTasaDolar(tasaDolar);
        setIvaPorcentaje(ivaPorcentaje);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public BigDecimal getTasaDolar() {
        return tasaDolar;
    }

    public void setTasaDolar(BigDecimal tasaDolar) {
        if (tasaDolar != null && tasaDolar.compareTo(BigDecimal.ZERO) > 0) {
            this.tasaDolar = tasaDolar;
        } else {
            this.tasaDolar = new BigDecimal("36.5000");
        }
    }

    public BigDecimal getIvaPorcentaje() {
        return ivaPorcentaje;
    }

    public void setIvaPorcentaje(BigDecimal ivaPorcentaje) {
        if (ivaPorcentaje == null) {
            this.ivaPorcentaje = new BigDecimal("16.00");
            return;
        }
        if (ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0 || ivaPorcentaje.scale() > 2) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0.00 y 100.00% con hasta dos decimales.");
        }
        this.ivaPorcentaje = ivaPorcentaje.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
