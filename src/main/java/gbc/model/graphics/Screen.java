package gbc.model.graphics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import gbc.model.memory.Memory;

public class Screen extends JPanel {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 144;

    private Memory memory;
    private BufferedImage image;

    public Screen(Memory memory) {
        this.memory = memory;
        this.image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setBackground(Color.WHITE);
    }

    public void render() {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int colorValue = this.memory.readByte(0x8000 + (y * WIDTH) + x) & 0xFF;
                Color color = getColorFromValue(colorValue);
                this.image.setRGB(x, y, color.getRGB());
            }
        }
        this.repaint();
    }

    private Color getColorFromValue(int colorValue) {
        // Implement color conversion logic here
        // Placeholder for demonstration:
        int rgb = colorValue | (colorValue << 8) | (colorValue << 16);
        return new Color(rgb);
    }

    public void clear() {
        Graphics g = this.image.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(this.image, 0, 0, this);
    }

    public void setPixel(int x, int y, Color color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            this.image.setRGB(x, y, color.getRGB());
        }
    }

    public Color getPixel(int x, int y) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            return new Color(this.image.getRGB(x, y));
        }
        return Color.WHITE;
    }

    public BufferedImage getImage() {
        return this.image;
    }
}
