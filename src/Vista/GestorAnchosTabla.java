package Vista;

import Modelo.ErrorAplicacionException;
import Modelo.PreferenciasTablaDao;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;

/**
 * Persiste y restaura los anchos de columna de una JTable por usuario.
 *
 * <p>Guarda pesos normalizados (fraccion del ancho), nunca pixeles. Al mover un borde,
 * espera 600 ms sin cambios (debounce) y escribe en un worker de fondo. Al construir,
 * restaura los pesos como anchos preferidos; con {@code AUTO_RESIZE_ALL_COLUMNS} el
 * reparto final respeta esas proporciones en cualquier resolucion. Sin carga util,
 * sin versiones: un solo puesto.</p>
 */
public final class GestorAnchosTabla {

    private static final Logger LOGGER = Logger.getLogger(GestorAnchosTabla.class.getName());
    private static final int DEBOUNCE_MS = 600;

    private final JTable tabla;
    private final String clave;
    private final int usuarioId;
    private final PreferenciasTablaDao dao;
    private final javax.swing.Timer debounce;
    private boolean aplicando = false;

    public GestorAnchosTabla(JTable tabla, String clave, int usuarioId, PreferenciasTablaDao dao) {
        if (tabla == null || clave == null || clave.isBlank() || dao == null) {
            throw ErrorAplicacionException.validacion("Tabla, clave y DAO son obligatorios para persistir anchos.");
        }
        this.tabla = tabla;
        this.clave = clave;
        this.usuarioId = usuarioId;
        this.dao = dao;
        tabla.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                if (!aplicando) {
                    debounce.restart();
                }
            }
            @Override public void columnAdded(TableColumnModelEvent e) { recargar(); }
            @Override public void columnRemoved(TableColumnModelEvent e) { recargar(); }
            @Override public void columnMoved(TableColumnModelEvent e) { }
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        this.debounce = new javax.swing.Timer(DEBOUNCE_MS, e -> guardar());
        this.debounce.setRepeats(false);
        cargar();
    }

    private void recargar() {
        if (!aplicando) {
            cargar();
        }
    }

    private void cargar() {
        final Optional<double[]> crudo;
        try {
            crudo = dao.cargar(usuarioId, clave);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "Sin layout guardado para " + clave + ": " + ex.getMessage());
            return;
        }
        if (crudo.isEmpty()) {
            return;
        }
        Optional<double[]> pesos = normalizar(crudo.get(), tabla.getColumnModel().getColumnCount());
        if (pesos.isEmpty()) {
            return;
        }
        double[] w = pesos.get();
        int base = tabla.getWidth();
        if (base <= 0) {
            int suma = 0;
            TableColumnModel modelo = tabla.getColumnModel();
            for (int i = 0; i < w.length; i++) {
                suma += modelo.getColumn(i).getPreferredWidth();
            }
            base = Math.max(suma, 1);
        }
        aplicando = true;
        try {
            TableColumnModel modelo = tabla.getColumnModel();
            for (int i = 0; i < w.length; i++) {
                modelo.getColumn(i).setPreferredWidth(Math.max(1, (int) Math.round(w[i] * base)));
            }
        } finally {
            aplicando = false;
        }
    }

    private void guardar() {
        TableColumnModel modelo = tabla.getColumnModel();
        int n = modelo.getColumnCount();
        int total = tabla.getWidth();
        if (n == 0 || total <= 0) {
            return;
        }
        double[] pesos = new double[n];
        double suma = 0;
        for (int i = 0; i < n; i++) {
            pesos[i] = (double) modelo.getColumn(i).getWidth() / total;
            suma += pesos[i];
        }
        if (suma <= 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            pesos[i] /= suma;
        }
        final double[] copia = pesos.clone();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    dao.guardar(usuarioId, clave, copia);
                } catch (RuntimeException ex) {
                    LOGGER.log(Level.FINE, "No se pudo guardar layout de " + clave + ": " + ex.getMessage());
                }
                return null;
            }
        }.execute();
    }

    /** Valida y normaliza pesos crudos: igual cantidad de columnas, rango y suma sanas. */
    static Optional<double[]> normalizar(double[] crudo, int columnas) {
        if (crudo == null || columnas <= 0 || crudo.length != columnas) {
            return Optional.empty();
        }
        double suma = 0;
        for (double v : crudo) {
            if (!(v >= 0.01 && v <= 0.99)) {
                return Optional.empty();
            }
            suma += v;
        }
        if (!(suma >= 0.85 && suma <= 1.15)) {
            return Optional.empty();
        }
        double[] r = crudo.clone();
        for (int i = 0; i < r.length; i++) {
            r[i] /= suma;
        }
        return Optional.of(r);
    }
}
