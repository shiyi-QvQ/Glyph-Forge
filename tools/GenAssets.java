import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenAssets {
    static final String[] GLYPH = {
        ".......##.......",
        "......#..#......",
        "........#.......",
        ".##############.",
        ".#............#.",
        ".#............#.",
        "......####......",
        ".....#..##......",
        "....#....#......",
        "...#######......",
        ".......#........",
        ".......#........",
        "..#....#....#...",
        "...#######......",
        "................",
        "................"
    };

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File blockDir = new File(dir, "textures/block");
        blockDir.mkdirs();

        BufferedImage block = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = block.createGraphics();
        g.setColor(new Color(0x2E3138));
        g.fillRect(0, 0, 16, 16);
        g.setColor(new Color(0x14161A));
        g.drawRect(0, 0, 15, 15);
        g.setColor(new Color(0x4A4F58));
        g.drawRect(1, 1, 13, 13);
        g.setColor(new Color(0x3A3E46));
        g.drawLine(2, 2, 13, 2);
        g.drawLine(2, 13, 13, 13);
        g.drawLine(2, 2, 2, 13);
        g.drawLine(13, 2, 13, 13);
        Color gold = new Color(0xFFD166);
        Color goldDark = new Color(0xC79A3E);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (GLYPH[y].charAt(x) == '#') {
                    block.setRGB(x, y, ((x + y) % 5 == 0 ? goldDark : gold).getRGB());
                }
            }
        }
        g.dispose();
        ImageIO.write(block, "png", new File(blockDir, "font_block.png"));

        System.out.println("wrote block texture to " + blockDir.getAbsolutePath());
    }
}
