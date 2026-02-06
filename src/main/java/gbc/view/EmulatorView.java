package gbc.view;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.Serial;
import java.util.Objects;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import gbc.model.GameBoyColor;
import gbc.model.graphics.FrameBuffer;
import gbc.model.graphics.PPU;
import gbc.model.graphics.Screen;

/**
 * EmulatorView with configurable scaling, filters, and display options
 */
public class EmulatorView extends JPanel {

    @Serial
    private static final long serialVersionUID = 1L;

    private PPU ppu;
    private Screen screen;
    private FrameBuffer frameBuffer;
    private final ViewSettings settings = new ViewSettings();
    private final Cache<RenderKey, BufferedImage> renderCache = Caffeine.newBuilder()
            .maximumSize(6)
            .build();

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
        this.frameBuffer = gbc.getFrameBuffer();

        configureFromSettings();
        setPreferredSize(new Dimension(GB_WIDTH * scaleFactor, GB_HEIGHT * scaleFactor));
        setBackground(backgroundColor);
        setFocusable(true);

        // Force initial rendering
        SwingUtilities.invokeLater(this::repaint);

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

    private void configureFromSettings() {
        scaleFactor = settings.scale();
        smoothScaling = settings.smoothScaling();
        maintainAspectRatio = settings.integerScale();
        showScanlines = settings.scanlines();
        colorFilter = settings.palette();
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

        // Get frame data from FrameBuffer (model) if available, fall back to Screen
        BufferedImage image = null;
        long currentFrameId = 0L;
        if (frameBuffer != null) {
            int[] pixels = frameBuffer.getPixels();
            if (pixels != null && pixels.length == GB_WIDTH * GB_HEIGHT) {
                image = new BufferedImage(GB_WIDTH, GB_HEIGHT, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, GB_WIDTH, GB_HEIGHT, pixels, 0, GB_WIDTH);
                currentFrameId = frameBuffer.getFrameId();
            }
        }
        if (image == null && screen != null) {
            image = screen.getImage();
            currentFrameId = screen.getFrameId();
        }

        if (image != null) {
            Dimension displaySize = calculateDisplaySize();
            int x = (getWidth() - displaySize.width) / 2;
            int y = (getHeight() - displaySize.height) / 2;

            RenderKey key = new RenderKey(currentFrameId, displaySize.width, displaySize.height, colorFilter,
                    smoothScaling);
            BufferedImage sourceImage = image;
            BufferedImage rendered = renderCache.get(key, k -> buildRenderedImage(sourceImage, displaySize));

            g2d.drawImage(rendered, x, y, displaySize.width, displaySize.height, this);

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

    private BufferedImage buildRenderedImage(BufferedImage original, Dimension displaySize) {
        BufferedImage filtered = applyColorFilter(original);
        if (displaySize.width == original.getWidth() && displaySize.height == original.getHeight()) {
            return filtered;
        }
        BufferedImage scaled = new BufferedImage(displaySize.width, displaySize.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        if (smoothScaling) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        } else {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        }
        g2d.drawImage(filtered, 0, 0, displaySize.width, displaySize.height, null);
        g2d.dispose();
        return scaled;
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
        repaint();
    }

    // Configuration methods
    public void setScaleFactor(int scaleFactor) {
        this.scaleFactor = Math.max(1, Math.min(8, scaleFactor));
        if (!maintainAspectRatio) {
            setPreferredSize(new Dimension(GB_WIDTH * this.scaleFactor, GB_HEIGHT * this.scaleFactor));
        }
        renderCache.invalidateAll();
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
        renderCache.invalidateAll();
        repaint();
    }

    public boolean isSmoothScaling() {
        return smoothScaling;
    }

    public void setColorFilter(ColorFilter filter) {
        this.colorFilter = filter;
        renderCache.invalidateAll();
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
            this.frameBuffer = ppu.getFrameBuffer();
        }
    }

    public PPU getPPU() {
        return ppu;
    }

    // Utility methods for UI integration
    public void resetView() {
        configureFromSettings();
        backgroundColor = Color.BLACK;
        setBackground(backgroundColor);
        setPreferredSize(new Dimension(GB_WIDTH * scaleFactor, GB_HEIGHT * scaleFactor));
        renderCache.invalidateAll();
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

    private static final class RenderKey {
        private final long frameId;
        private final int width;
        private final int height;
        private final ColorFilter filter;
        private final boolean smooth;

        private RenderKey(long frameId, int width, int height, ColorFilter filter, boolean smooth) {
            this.frameId = frameId;
            this.width = width;
            this.height = height;
            this.filter = filter;
            this.smooth = smooth;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RenderKey)) {
                return false;
            }
            RenderKey key = (RenderKey) o;
            return frameId == key.frameId
                    && width == key.width
                    && height == key.height
                    && smooth == key.smooth
                    && filter == key.filter;
        }

        @Override
        public int hashCode() {
            return Objects.hash(frameId, width, height, filter, smooth);
        }
    }
}
