package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.model.graphics.Screen;
import gbc.model.graphics.PPU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EmulatorView with configurable scaling, filters, and display options
 */
public class EmulatorView extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(EmulatorView.class.getName());

    @Serial
    private static final long serialVersionUID = 1L;

    private PPU ppu;
    private Screen screen;

    // Display configuration
    private int scaleFactor = 2;
    private boolean maintainAspectRatio = true;
    private boolean showScanlines = false;
    private boolean smoothScaling = false;
    private Color backgroundColor = Color.BLACK;

    // Game Boy screen dimensions
    private static final int GB_WIDTH = 160;
    private static final int GB_HEIGHT = 144;

    // Color filters
    private ColorFilter colorFilter = ColorFilter.NONE;

    public enum ColorFilter {
        NONE("None"),
        GREEN_MONOCHROME("Green Monochrome"),
        SEPIA("Sepia"),
        HIGH_CONTRAST("High Contrast");

        private final String displayName;

        ColorFilter(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public EmulatorView(GameBoyColor gbc) {
        this.ppu = gbc.getPpu();
        this.screen = ppu.getScreen();

        configureFromProperties();
        setPreferredSize(new Dimension(GB_WIDTH * scaleFactor, GB_HEIGHT * scaleFactor));
        setBackground(backgroundColor);
        setFocusable(true);

        // Force initial rendering
        SwingUtilities.invokeLater(() -> {
            if (ppu != null) {
                try {
                    ppu.updateGraphics();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error in initial PPU update", e);
                }
            }
            repaint();
        });

        // Add resize listener to maintain aspect ratio
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (maintainAspectRatio) {
                    maintainAspectRatio();
                }
            }
        });
    }

    private void configureFromProperties() {
        int scale = Integer.getInteger("video.scale",
                Integer.getInteger("emulator.windowScale", 2));
        scaleFactor = Math.max(1, Math.min(8, scale));

        String filter = System.getProperty("video.filter", "none").toLowerCase();
        smoothScaling = filter.equals("linear");

        maintainAspectRatio = Boolean.parseBoolean(System.getProperty("video.integerScale", "true"));
        showScanlines = Boolean.parseBoolean(System.getProperty("video.scanlines", "false"));

        String palette = System.getProperty("video.palette", "none").toLowerCase();
        switch (palette) {
            case "green":
            case "dmg_green":
                colorFilter = ColorFilter.GREEN_MONOCHROME;
                break;
            case "custom":
            case "sepia":
                colorFilter = ColorFilter.SEPIA;
                break;
            case "high_contrast":
                colorFilter = ColorFilter.HIGH_CONTRAST;
                break;
            default:
                colorFilter = ColorFilter.NONE;
                break;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();

        // Set rendering hints
        if (smoothScaling) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        } else {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        }

        // Fill background
        g2d.setColor(backgroundColor);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Always try to get an image from screen
        BufferedImage image = null;
        if (screen != null) {
            image = screen.getImage();
        }

        if (image != null) {
            // Apply color filter
            BufferedImage filteredImage = applyColorFilter(image);

            // Calculate display dimensions
            Dimension displaySize = calculateDisplaySize();
            int x = (getWidth() - displaySize.width) / 2;
            int y = (getHeight() - displaySize.height) / 2;

            // Draw the Game Boy screen
            g2d.drawImage(filteredImage, x, y, displaySize.width, displaySize.height, this);

            // Add scanlines effect if enabled
            if (showScanlines) {
                drawScanlines(g2d, x, y, displaySize.width, displaySize.height);
            }

            // Draw border around screen
            g2d.setColor(Color.GRAY);
            g2d.drawRect(x - 1, y - 1, displaySize.width + 1, displaySize.height + 1);
        } else {
            // Draw a test pattern when no image is available
            drawTestPattern(g2d);
        }
        g2d.dispose();
    }

    private void drawTestPattern(Graphics2D g2d) {
        // Draw a simple test pattern to show the view is working
        int width = getWidth();
        int height = getHeight();

        // Draw checkerboard pattern
        g2d.setColor(Color.DARK_GRAY);
        for (int x = 0; x < width; x += 20) {
            for (int y = 0; y < height; y += 20) {
                if ((x / 20 + y / 20) % 2 == 0) {
                    g2d.fillRect(x, y, 20, 20);
                }
            }
        }

        // Draw centered message
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        String message = "Game Boy Color Emulator";
        int messageX = (width - fm.stringWidth(message)) / 2;
        int messageY = (height + fm.getAscent()) / 2;
        g2d.drawString(message, messageX, messageY);

        // Debug info
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        fm = g2d.getFontMetrics();
        String debugInfo = String.format("Screen: %s, PPU: %s",
                screen != null ? "OK" : "NULL",
                ppu != null ? "OK" : "NULL");
        g2d.drawString(debugInfo, 10, height - 20);
    }

    private BufferedImage applyColorFilter(BufferedImage original) {
        if (colorFilter == ColorFilter.NONE) {
            return original;
        }

        BufferedImage filtered = new BufferedImage(original.getWidth(), original.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = filtered.createGraphics();

        switch (colorFilter) {
            case GREEN_MONOCHROME:
                applyGreenMonochromeFilter(g2d, original);
                break;
            case SEPIA:
                applySepiaFilter(g2d, original);
                break;
            case HIGH_CONTRAST:
                applyHighContrastFilter(g2d, original);
                break;
            default:
                g2d.drawImage(original, 0, 0, null);
                break;
        }

        g2d.dispose();
        return filtered;
    }

    private void applyGreenMonochromeFilter(Graphics2D g2d, BufferedImage original) {
        // Convert to green monochrome (classic Game Boy look)
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int rgb = original.getRGB(x, y);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));

                // Map to Game Boy green palette
                Color gbColor;
                if (gray < 64) {
                    gbColor = new Color(15, 56, 15); // Darkest green
                } else if (gray < 128) {
                    gbColor = new Color(48, 98, 48); // Dark green
                } else if (gray < 192) {
                    gbColor = new Color(139, 172, 15); // Light green
                } else {
                    gbColor = new Color(155, 188, 15); // Lightest green
                }

                g2d.setColor(gbColor);
                g2d.fillRect(x, y, 1, 1);
            }
        }
    }

    private void applySepiaFilter(Graphics2D g2d, BufferedImage original) {
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int rgb = original.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Apply sepia transformation
                int newR = Math.min(255, (int) (r * 0.393 + gr * 0.769 + b * 0.189));
                int newG = Math.min(255, (int) (r * 0.349 + gr * 0.686 + b * 0.168));
                int newB = Math.min(255, (int) (r * 0.272 + gr * 0.534 + b * 0.131));

                Color sepiaColor = new Color(newR, newG, newB);
                g2d.setColor(sepiaColor);
                g2d.fillRect(x, y, 1, 1);
            }
        }
    }

    private void applyHighContrastFilter(Graphics2D g2d, BufferedImage original) {
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int rgb = original.getRGB(x, y);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));

                // High contrast: pure black or white
                Color contrastColor = gray < 128 ? Color.BLACK : Color.WHITE;
                g2d.setColor(contrastColor);
                g2d.fillRect(x, y, 1, 1);
            }
        }
    }

    private void drawScanlines(Graphics2D g2d, int x, int y, int width, int height) {
        g2d.setColor(new Color(0, 0, 0, 64)); // Semi-transparent black

        // Draw horizontal scanlines
        for (int i = 1; i < height; i += 2) {
            g2d.drawLine(x, y + i, x + width, y + i);
        }
    }

    private Dimension calculateDisplaySize() {
        if (maintainAspectRatio) {
            int componentWidth = getWidth();
            int componentHeight = getHeight();

            // Calculate scaling factor to fit while maintaining aspect ratio
            double scaleX = (double) componentWidth / GB_WIDTH;
            double scaleY = (double) componentHeight / GB_HEIGHT;
            double scale = Math.min(scaleX, scaleY);

            // Apply minimum scale to ensure readability
            scale = Math.max(scale, 1.0);

            return new Dimension((int) (GB_WIDTH * scale), (int) (GB_HEIGHT * scale));
        } else {
            return new Dimension(GB_WIDTH * scaleFactor, GB_HEIGHT * scaleFactor);
        }
    }

    private void maintainAspectRatio() {
        Dimension displaySize = calculateDisplaySize();
        int newWidth = displaySize.width + 20; // Add some padding
        int newHeight = displaySize.height + 20;

        if (getWidth() != newWidth || getHeight() != newHeight) {
            Container parent = getParent();
            if (parent instanceof JFrame) {
                JFrame frame = (JFrame) parent;
                frame.setSize(newWidth, newHeight);
            }
        }
    }

    public void update() {
        // Simplified update - just trigger PPU update and repaint
        try {
            if (ppu != null) {
                ppu.updateGraphics();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating PPU during view update", e);
        }

        // Simple repaint - no threading complexity
        repaint();
    }

    // Configuration methods
    public void setScaleFactor(int scaleFactor) {
        this.scaleFactor = Math.max(1, Math.min(8, scaleFactor));
        if (!maintainAspectRatio) {
            setPreferredSize(new Dimension(GB_WIDTH * this.scaleFactor, GB_HEIGHT * this.scaleFactor));
        }
        repaint();
    }

    public int getScaleFactor() {
        return scaleFactor;
    }

    public void setMaintainAspectRatio(boolean maintain) {
        this.maintainAspectRatio = maintain;
        if (maintain) {
            maintainAspectRatio();
        }
    }

    public boolean isMaintainAspectRatio() {
        return maintainAspectRatio;
    }

    public void setShowScanlines(boolean show) {
        this.showScanlines = show;
        repaint();
    }

    public boolean isShowScanlines() {
        return showScanlines;
    }

    public void setSmoothScaling(boolean smooth) {
        this.smoothScaling = smooth;
        repaint();
    }

    public boolean isSmoothScaling() {
        return smoothScaling;
    }

    public void setColorFilter(ColorFilter filter) {
        this.colorFilter = filter;
        repaint();
    }

    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        setBackground(color);
        repaint();
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    // PPU management
    public void setPPU(PPU ppu) {
        this.ppu = ppu;
        if (ppu != null) {
            this.screen = ppu.getScreen();
        }
    }

    public PPU getPPU() {
        return ppu;
    }

    // Utility methods for UI integration
    public void resetView() {
        scaleFactor = 2;
        maintainAspectRatio = true;
        showScanlines = false;
        smoothScaling = false;
        colorFilter = ColorFilter.NONE;
        backgroundColor = Color.BLACK;
        setBackground(backgroundColor);
        setPreferredSize(new Dimension(GB_WIDTH * scaleFactor, GB_HEIGHT * scaleFactor));
        repaint();
    }

    public void takeScreenshot() {
        BufferedImage screenshot = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = screenshot.createGraphics();
        paint(g2d);
        g2d.dispose();

        // Here you could save the screenshot to a file
        // For now, just store it in clipboard or show a dialog
        JOptionPane.showMessageDialog(this, "Screenshot captured!", "Screenshot", JOptionPane.INFORMATION_MESSAGE);
    }
}
