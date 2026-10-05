package Vista;

import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventosTest {
    @Test
    public void filtraCaracteresQueNoCorrespondenAlCampo() {
        Eventos eventos = new Eventos();
        JTextField campo = new JTextField();

        KeyEvent letra = evento(campo, 'A');
        eventos.textKeyPress(letra);
        assertFalse(letra.isConsumed());

        KeyEvent numeroEnTexto = evento(campo, '3');
        eventos.textKeyPress(numeroEnTexto);
        assertTrue(numeroEnTexto.isConsumed());

        KeyEvent puntoDecimalRepetido = evento(campo, '.');
        campo.setText("1.5");
        eventos.numberDecimalKeyPress(puntoDecimalRepetido, campo);
        assertTrue(puntoDecimalRepetido.isConsumed());
    }

    private KeyEvent evento(JTextField campo, char caracter) {
        return new KeyEvent(campo, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                KeyEvent.VK_UNDEFINED, caracter);
    }
}
