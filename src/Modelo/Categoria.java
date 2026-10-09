package Modelo;

/** Categoría visual del menú con color para resaltar platos. */
public class Categoria {
    private int id;
    private String nombre;
    private String color;
    private int orden;

    public Categoria() {
    }

    public Categoria(int id, String nombre, String color, int orden) {
        this.id = id;
        this.nombre = nombre;
        this.color = color;
        this.orden = orden;
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
}