package Modelo;

import java.util.Objects;

/**
 * Representa a un mesonero / salonero del restaurante.
 * Soporta borrado lógico (soft-delete) y estado activo/vacaciones (hide).
 */
public class Mesonero {
    private int id;
    private String nombreCompleto;
    private String cedula;
    private String telefono;
    private boolean activo = true;       // false = de vacaciones / oculto para asignaciones
    private boolean eliminado = false;    // true = borrado lógico (soft-delete)

    public Mesonero() {
    }

    public Mesonero(int id, String nombreCompleto, String cedula, String telefono, boolean activo, boolean eliminado) {
        this.id = id;
        this.nombreCompleto = nombreCompleto != null ? nombreCompleto.trim() : "";
        this.cedula = cedula != null ? cedula.trim() : "";
        this.telefono = telefono != null ? telefono.trim() : "";
        this.activo = activo;
        this.eliminado = eliminado;
    }

    public Mesonero(String nombreCompleto, String cedula, String telefono, boolean activo) {
        this(0, nombreCompleto, cedula, telefono, activo, false);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombreCompleto() {
        return nombreCompleto != null ? nombreCompleto : "";
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto != null ? nombreCompleto.trim() : "";
    }

    public String getCedula() {
        return cedula != null ? cedula : "";
    }

    public void setCedula(String cedula) {
        this.cedula = cedula != null ? cedula.trim() : "";
    }

    public String getTelefono() {
        return telefono != null ? telefono : "";
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono != null ? telefono.trim() : "";
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isEliminado() {
        return eliminado;
    }

    public void setEliminado(boolean eliminado) {
        this.eliminado = eliminado;
    }

    @Override
    public String toString() {
        return getNombreCompleto().isEmpty() ? "Mesonero #" + id : getNombreCompleto();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mesonero mesonero = (Mesonero) o;
        return id == mesonero.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
