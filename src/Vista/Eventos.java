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
        if ((car < '0' || car > '9') && textField.getText().contains(".")
                && car != KeyEvent.VK_BACK_SPACE) {
            evt.consume();
        } else if ((car < '0' || car > '9') && car != '.' && car != KeyEvent.VK_BACK_SPACE) {
            evt.consume();
        }
    }
}
