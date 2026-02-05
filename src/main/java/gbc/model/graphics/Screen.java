package gbc.model.graphics;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JPanel;

public class Screen extends JPanel {
    // TODO: Avoid mutating input buffers in render and support scaling/color correction externally.
    private static final Logger LOGGER = Logger.getLogger(Screen.class.getName());
    private static final int WIDTH = 160; // Game Boy screen width
    private static final int HEIGHT = 144; // Game Boy screen height

    private BufferedImage backBuffer;
    private volatile BufferedImage frontBuffer;
    private volatile long frameId;

    public Screen() {
        this.backBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        this.frontBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
    }

    public void render(int[] frameBuffer) {
        if (frameBuffer.length != WIDTH * HEIGHT) {
            LOGGER.log(Level.WARNING, () -> String.format("Invalid frame buffer size: %d, expected: %d",
                    frameBuffer.length, WIDTH * HEIGHT));
            return;
        }

        // Convert Game Boy colors to RGB
        for (int i = 0; i < frameBuffer.length; i++) {
            frameBuffer[i] = convertGBColor(frameBuffer[i]);
        }

        backBuffer.setRGB(0, 0, WIDTH, HEIGHT, frameBuffer, 0, WIDTH);
        swapBuffers();
    }

    public void render() {
        swapBuffers();
    }

    private void swapBuffers() {
        BufferedImage temp = frontBuffer;
        frontBuffer = backBuffer;
        backBuffer = temp;
        frameId++;
        repaint();
    }

    private int convertGBColor(int gbColor) {
        // Game Boy colors are already RGB values in our implementation
        return gbColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(this.frontBuffer, 0, 0, this);
    }

    public BufferedImage getImage() {
        return this.frontBuffer;
    }

    public void setImage(BufferedImage image) {
        this.frontBuffer = image;
        frameId++;
    }

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public void setPixel(int x, int y, int color) {
        this.backBuffer.setRGB(x, y, color);
    }

    public int getPixel(int x, int y) {
        return this.backBuffer.getRGB(x, y);
    }

    public void clear() {
        this.backBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        this.frontBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        frameId++;
    }

    public void reset() {
        clear();
    }

    public long getFrameId() {
        return frameId;
    }

    public void clear(int color) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                this.backBuffer.setRGB(x, y, color);
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
