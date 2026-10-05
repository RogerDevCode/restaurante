package restaurante;

import Vista.FrmLogin;
import Vista.ManejadorErroresSwing;
import javax.swing.SwingUtilities;

public class Restaurante {

    public static void main(String[] args) {
        ManejadorErroresSwing.instalar();
        SwingUtilities.invokeLater(() -> {
            FrmLogin iniciar = new FrmLogin();
            iniciar.setVisible(true);
        });
    }
    
}
