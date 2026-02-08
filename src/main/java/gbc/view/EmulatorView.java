package gbc.view;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
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
        maintainAspectRatio = settings.maintainAspectRatio();
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

            // Draw subtle border around screen
            g2d.setColor(new Color(0, 0, 0, 40));
            g2d.drawRect(x - 1, y - 1, displaySize.width + 1, displaySize.height + 1);
        } else {
            // Draw a test pattern when no image is available
            drawTestPattern(g2d);
        }
        g2d.dispose();
    }

    private void drawTestPattern(Graphics2D g2d) {
        int width = getWidth();
        int height = getHeight();

        // Dark solid background
        g2d.setColor(new Color(24, 24, 32));
        g2d.fillRect(0, 0, width, height);

        // Radial vignette gradient overlay
        float radius = Math.max(width, height) * 0.7f;
        Point center = new Point(width / 2, height / 2);
        RadialGradientPaint vignette = new RadialGradientPaint(
                center, radius,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(40, 40, 52), new Color(24, 24, 32), new Color(10, 10, 16)});
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, width, height);

        // Faint horizontal LCD grid lines
        g2d.setColor(new Color(255, 255, 255, 8));
        for (int y = 0; y < height; y += 3) {
            g2d.drawLine(0, y, width, y);
        }

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // "GBJava" title in Game Boy green
        g2d.setColor(new Color(139, 172, 15));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        FontMetrics fm = g2d.getFontMetrics();
        String title = "GBJava";
        int titleX = (width - fm.stringWidth(title)) / 2;
        int titleY = height / 2 - fm.getHeight() / 2;
        g2d.drawString(title, titleX, titleY);

        // Subtitle in muted gray
        g2d.setColor(new Color(130, 130, 140));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        fm = g2d.getFontMetrics();
        String subtitle = "Drop a ROM or use File \u203A Open ROM...";
        int subX = (width - fm.stringWidth(subtitle)) / 2;
        int subY = titleY + fm.getHeight() + 6;
        g2d.drawString(subtitle, subX, subY);
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

    // Pre-computed Game Boy green palette as packed ints
    private static final int GB_GREEN_DARKEST  = (15 << 16) | (56 << 8) | 15;
    private static final int GB_GREEN_DARK     = (48 << 16) | (98 << 8) | 48;
    private static final int GB_GREEN_LIGHT    = (139 << 16) | (172 << 8) | 15;
    private static final int GB_GREEN_LIGHTEST = (155 << 16) | (188 << 8) | 15;

    private BufferedImage applyColorFilter(BufferedImage original) {
        if (colorFilter == ColorFilter.NONE) {
            return original;
        }

        int w = original.getWidth();
        int h = original.getHeight();
        int[] pixels = original.getRGB(0, 0, w, h, null, 0, w);

        switch (colorFilter) {
            case GREEN_MONOCHROME -> applyGreenMonochromeFilter(pixels);
            case SEPIA -> applySepiaFilter(pixels);
            case HIGH_CONTRAST -> applyHighContrastFilter(pixels);
            default -> { /* no-op */ }
        }

        BufferedImage filtered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        filtered.setRGB(0, 0, w, h, pixels, 0, w);
        return filtered;
    }

    private void applyGreenMonochromeFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int gray = ((rgb >> 16 & 0xFF) * 77 + (rgb >> 8 & 0xFF) * 150 + (rgb & 0xFF) * 29) >> 8;
            if (gray < 64) {
                pixels[i] = GB_GREEN_DARKEST;
            } else if (gray < 128) {
                pixels[i] = GB_GREEN_DARK;
            } else if (gray < 192) {
                pixels[i] = GB_GREEN_LIGHT;
            } else {
                pixels[i] = GB_GREEN_LIGHTEST;
            }
        }
    }

    private void applySepiaFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int newR = Math.min(255, (r * 100 + g * 196 + b * 48) >> 8);
            int newG = Math.min(255, (r * 89 + g * 175 + b * 43) >> 8);
            int newB = Math.min(255, (r * 70 + g * 137 + b * 34) >> 8);
            pixels[i] = (newR << 16) | (newG << 8) | newB;
        }
    }

    private void applyHighContrastFilter(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int gray = ((rgb >> 16 & 0xFF) * 77 + (rgb >> 8 & 0xFF) * 150 + (rgb & 0xFF) * 29) >> 8;
            pixels[i] = gray < 128 ? 0x000000 : 0xFFFFFF;
        }
    }

    private void drawScanlines(Graphics2D g2d, int x, int y, int width, int height) {
        Composite original = g2d.getComposite();
        int pixelScale = height / GB_HEIGHT;

        if (pixelScale < 2) {
            // Low resolution: light overlay every other pixel
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 30f / 255f));
            g2d.setColor(Color.BLACK);
            for (int i = 1; i < height; i += 2) {
                g2d.drawLine(x, y + i, x + width, y + i);
            }
        } else {
            // Proportional scanlines matching GB scanline pitch
            g2d.setColor(Color.BLACK);
            for (int line = 0; line < GB_HEIGHT; line++) {
                int lineY = y + line * pixelScale;
                // Primary dark scanline
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
                g2d.drawLine(x, lineY, x + width, lineY);
                // Secondary lighter line if enough room
                if (pixelScale >= 3) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
                    g2d.drawLine(x, lineY + 1, x + width, lineY + 1);
                }
            }
        }

        g2d.setComposite(original);
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
