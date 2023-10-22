package me.emulador.gbc.model.graphics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class Screen extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int SCALE = 2;
    public static final int WIDTH = 160;
    public static final int HEIGHT = 144;

    private BufferedImage image;

    public Screen() {
        setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    public void setPixel(int x, int y, int color) {
        image.setRGB(x, y, color);
    }

    public void clear() {
        Graphics g = image.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, WIDTH * SCALE, HEIGHT * SCALE, null);
    }

    public void drawTile(int x, int y, int tileNumber, int[] tileset) {
        int offset = tileNumber * 16;
        for (int i = 0; i < 8; i++) {
            int lsb = tileset[offset + i * 2];
            int msb = tileset[offset + i * 2 + 1];
            for (int j = 0; j < 8; j++) {
                int colorBit = (msb & (1 << (7 - j))) >> (7 - j);
                colorBit <<= 1;
                colorBit |= (lsb & (1 << (7 - j))) >> (7 - j);
                setPixel(x + j, y + i, getColor(colorBit));
            }
        }
    }

    public void drawBackground(int[] bgMap, int[] tileset) {
        for (int i = 0; i < 32 * 32; i++) {
            int tileNumber = bgMap[i];
            int x = (i % 32) * 8;
            int y = (i / 32) * 8;
            drawTile(x, y, tileNumber, tileset);
        }
    }

    public void drawSprites(int[] oam, int[] tileset) {
        for (int i = 0; i < 40; i++) {
            int offset = i * 4;
            int y = oam[offset] - 16;
            int x = oam[offset + 1] - 8;
            int tileNumber = oam[offset + 2];
            int attributes = oam[offset + 3];
            boolean yFlip = (attributes & 0x40) != 0;
            boolean xFlip = (attributes & 0x20) != 0;
            boolean priority = (attributes & 0x80) != 0;
            drawSprite(x, y, tileNumber, tileset, xFlip, yFlip, priority);
        }
    }

    public void drawSprite(int x, int y, int tileNumber, int[] tileset, boolean xFlip, boolean yFlip, boolean priority) {
        int offset = tileNumber * 16;
        for (int i = 0; i < 8; i++) {
            int lsb = tileset[offset + i * 2];
            int msb = tileset[offset + i * 2 + 1];
            for (int j = 0; j < 8; j++) {
                int colorBit = (msb & (1 << (7 - j))) >> (7 - j);
                colorBit <<= 1;
                colorBit |= (lsb & (1 << (7 - j))) >> (7 - j);
                if (colorBit != 0) {
                    int xPixel = x + (xFlip ? (7 - j) : j);
                    int yPixel = y + (yFlip ? (7 - i) : i);
                    if (xPixel >= 0 && xPixel < WIDTH && yPixel >= 0 && yPixel < HEIGHT) {
                        if (priority || getPixel(xPixel, yPixel) == 0) {
                            setPixel(xPixel, yPixel, getColor(colorBit));
                        }
                    }
                }
            }
        }
    }

    public int getPixel(int x, int y) {
        return image.getRGB(x, y);
    }

    public int getColor(int colorBit) {
        switch (colorBit) {
            case 0:
                return 0xFFFFFF;
            case 1:
                return 0xC0C0C0;
            case 2:
                return 0x808080;
            case 3:
                return 0x000000;
            default:
                return 0x000000;
        }
    }

    public void draw() {
        repaint();
    }

    public void drawScanline(int[] line, int y) {
        for (int i = 0; i < WIDTH; i++) {
            setPixel(i, y, line[i]);
        }
    }

    
}
