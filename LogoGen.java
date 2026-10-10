import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class LogoGen {
    public static void main(String[] args) throws Exception {
        generateLogo(512, 512, "src/Img/logo.png");
        generateLogo(620, 467, "src/Img/portada.png");
    }

    private static void generateLogo(int w, int h, String path) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        
        // Anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        GradientPaint gp = new GradientPaint(0, 0, new Color(15, 32, 39), w, h, new Color(32, 58, 67));
        g2d.setPaint(gp);
        g2d.fillRoundRect(0, 0, w, h, w/10, h/10);

        // Outer Ring
        g2d.setColor(new Color(44, 83, 100));
        g2d.setStroke(new BasicStroke(w/20f));
        g2d.drawOval(w/8, h/8, w - w/4, h - h/4);

        // Text CAPIP
        g2d.setColor(Color.WHITE);
        Font font = new Font("SansSerif", Font.BOLD, w/6);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        String text1 = "CAPIP";
        int x1 = (w - fm.stringWidth(text1)) / 2;
        int y1 = (h / 2) + (fm.getAscent() / 4) - h/12;
        g2d.drawString(text1, x1, y1);

        // Text Sistemas
        g2d.setColor(new Color(200, 220, 240));
        Font font2 = new Font("SansSerif", Font.PLAIN, w/10);
        g2d.setFont(font2);
        FontMetrics fm2 = g2d.getFontMetrics();
        String text2 = "SISTEMAS";
        int x2 = (w - fm2.stringWidth(text2)) / 2;
        int y2 = y1 + h/8;
        g2d.drawString(text2, x2, y2);

        g2d.dispose();
        ImageIO.write(img, "png", new File(path));
    }
}
