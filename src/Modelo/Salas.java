
package Modelo;

public class Salas {
    public static final int MESAS_MAXIMAS_POR_SALA = 500;

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

    /** Valida datos operativos antes de guardar; no se usa al hidratar registros históricos. */
    public void validarParaGuardar() {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw ErrorAplicacionException.validacion("El nombre de la sala es obligatorio.");
        }
        if (nombre.trim().length() > 100) {
            throw ErrorAplicacionException.validacion("El nombre de la sala no puede superar 100 caracteres.");
        }
        if (mesas < 1 || mesas > MESAS_MAXIMAS_POR_SALA) {
            throw ErrorAplicacionException.validacion("La cantidad de mesas o puestos debe estar entre 1 y "
                    + MESAS_MAXIMAS_POR_SALA + ".");
        }
    }
}
