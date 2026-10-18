package Modelo;

/** Categoría visual del menú con color para resaltar platos. */
public class Categoria {
    private int id;
    private String nombre;
    private String color;
    private int orden;
    private boolean activo = true;

    public Categoria() {
    }

    public Categoria(int id, String nombre, String color, int orden) {
        this(id, nombre, color, orden, true);
    }

    public Categoria(int id, String nombre, String color, int orden, boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.color = color;
        this.orden = orden;
        this.activo = activo;
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Indica si esta categoría es la categoría canónica 'General' del sistema. */
    public boolean isGeneral() {
        return "General".equalsIgnoreCase(nombre != null ? nombre.trim() : "");
    }

    @Override
    public String toString() {
        return nombre != null ? nombre : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Categoria categoria = (Categoria) o;
        if (id > 0 && categoria.id > 0) {
            return id == categoria.id;
        }
        return nombre != null && nombre.equalsIgnoreCase(categoria.nombre);
    }

    @Override
    public int hashCode() {
        return id > 0 ? Integer.hashCode(id) : (nombre != null ? nombre.toLowerCase(java.util.Locale.ROOT).hashCode() : 0);
    }
}