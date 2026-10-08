package Modelo;

import java.math.BigDecimal;

public class Cliente {
    private int id;
    private String documento;
    private String nombre;
    private String telefono;
    private String direccion;
    private String creadoEn;
    private int totalFacturas;
    private BigDecimal totalGastadoDolares = BigDecimal.ZERO;
    private BigDecimal totalGastadoBs = BigDecimal.ZERO;

    public Cliente() {
    }

    public Cliente(int id, String documento, String nombre, String telefono, String direccion) {
        this.id = id;
        this.documento = documento;
        this.nombre = nombre;
        this.telefono = telefono;
        this.direccion = direccion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
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

    public String getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(String creadoEn) {
        this.creadoEn = creadoEn;
    }

    public int getTotalFacturas() {
        return totalFacturas;
    }

    public void setTotalFacturas(int totalFacturas) {
        this.totalFacturas = totalFacturas;
    }

    public BigDecimal getTotalGastadoDolares() {
        return totalGastadoDolares;
    }

    public void setTotalGastadoDolares(BigDecimal totalGastadoDolares) {
        this.totalGastadoDolares = totalGastadoDolares != null ? totalGastadoDolares : BigDecimal.ZERO;
    }

    public BigDecimal getTotalGastadoBs() {
        return totalGastadoBs;
    }

    public void setTotalGastadoBs(BigDecimal totalGastadoBs) {
        this.totalGastadoBs = totalGastadoBs != null ? totalGastadoBs : BigDecimal.ZERO;
    }

    @Override
    public String toString() {
        return nombre + " (" + documento + ")";
    }
}
