package Vista;

import java.awt.event.KeyEvent;
import javax.swing.JTextField;

/** Filtra la entrada mientras se escribe en controles Swing. */
public class Eventos {

    public void textKeyPress(KeyEvent evt) {
        char car = evt.getKeyChar();
        if ((car < 'a' || car > 'z') && (car < 'A' || car > 'Z')
                && car != KeyEvent.VK_BACK_SPACE && car != KeyEvent.VK_SPACE) {
            evt.consume();
        }
    }

    public void numberKeyPress(KeyEvent evt) {
        char car = evt.getKeyChar();
        if ((car < '0' || car > '9') && car != KeyEvent.VK_BACK_SPACE) {
            evt.consume();
        }
    }

    public void numberDecimalKeyPress(KeyEvent evt, JTextField textField) {
        char car = evt.getKeyChar();
        // Aceptar coma y convertirla en punto decimal automáticamente
        if (car == ',') {
            evt.setKeyChar('.');
            car = '.';
        }

        if ((car < '0' || car > '9') && car != '.' && car != KeyEvent.VK_BACK_SPACE) {
            evt.consume();
            return;
        }

        String texto = (textField != null && textField.getText() != null) ? textField.getText() : "";
        String textoSeleccionado = textField != null ? textField.getSelectedText() : null;
        boolean haySeleccion = textoSeleccionado != null && !textoSeleccionado.isEmpty();

        // Evitar múltiples puntos decimales
        if (car == '.') {
            if (texto.contains(".") && (!haySeleccion || !textoSeleccionado.contains("."))) {
                evt.consume();
            }
            return;
        }

        // Restringir a máximo 2 decimales si el cursor está después del punto
        if (car >= '0' && car <= '9' && textField != null) {
            if (!haySeleccion && texto.contains(".")) {
                int puntoIdx = texto.indexOf('.');
                int caretPos = textField.getCaretPosition();
                if (caretPos > puntoIdx) {
                    String decimales = texto.substring(puntoIdx + 1);
                    if (decimales.length() >= 2) {
                        evt.consume();
                    }
                }
            }
        }
    }
}
