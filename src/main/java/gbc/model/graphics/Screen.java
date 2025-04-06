package gbc.model.graphics;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class Screen extends JPanel {
    private static final int WIDTH = 160; // Game Boy screen width
    private static final int HEIGHT = 144; // Game Boy screen height

    private BufferedImage image;

    public Screen() {
        this.image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
    }

    public void render(int[] frameBuffer) {
        image.setRGB(0, 0, WIDTH, HEIGHT, frameBuffer, 0, WIDTH);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(this.image, 0, 0, this);
    }

    public BufferedImage getImage() {
        return this.image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public void setPixel(int x, int y, int color) {
        this.image.setRGB(x, y, color);
    }

    public int getPixel(int x, int y) {
        return this.image.getRGB(x, y);
    }

    public void clear() {
        this.image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    public void clear(int color) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                this.image.setRGB(x, y, color);
            }
        }
    }

public void drawLine(int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = (x1 < x2) ? 1 : -1;
        int sy = (y1 < y2) ? 1 : -1;

        int err = dx - dy;

        while (true) {
            this.setPixel(x1, y1, color);

            if ((x1 == x2) && (y1 == y2)) {
                break;
            }

            int e2 = 2 * err;

            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }

            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }
}
