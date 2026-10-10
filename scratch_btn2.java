import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class scratch_btn2 {
    public static void main(String[] args) throws Exception {
        JButton boton = new JButton("Mesa 1");
        boton.setBackground(new Color(255, 51, 51));
        // boton.setContentAreaFilled(false);
        boton.setSize(100, 100);
        
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        boton.paint(g2d);
        g2d.dispose();
        
        int centerPixel = img.getRGB(50, 10);
        System.out.println("Color: " + new Color(centerPixel, true));
    }
}
