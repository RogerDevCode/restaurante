
package Modelo;

public class Salas {
    private int id;
    private String nombre;
    private int mesas;
    private String tipo = "SALON";

    public Salas(){

    }

    public Salas(int id, String nombre, int mesas) {
        this(id, nombre, mesas, "SALON");
    }

    public Salas(int id, String nombre, int mesas, String tipo) {
        this.id = id;
        this.nombre = nombre;
        this.mesas = mesas;
        setTipo(tipo);
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

    public int getMesas() {
        return mesas;
    }

    public void setMesas(int mesas) {
        this.mesas = mesas;
    }

    public String getTipo() {
        return tipo != null ? tipo : "SALON";
    }

    public void setTipo(String tipo) {
        this.tipo = (tipo != null && !tipo.trim().isEmpty())
                ? tipo.trim().toUpperCase(java.util.Locale.ROOT)
                : "SALON";
    }

    public boolean esBarra() {
        return "BARRA".equalsIgnoreCase(this.tipo);
    }
}
