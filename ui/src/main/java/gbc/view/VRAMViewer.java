package gbc.view;

import gbc.model.GameBoyColor;
import gbc.model.memory.Memory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * VRAM Viewer for debugging graphics data
 * Displays tiles, backgrounds, sprites, and palettes
 */
public class VRAMViewer extends JFrame {

    private final GameBoyColor gbc;
    private final Memory memory;

    // Components
    private JTabbedPane tabbedPane;
    private TileViewPanel tileViewPanel;
    private BackgroundViewPanel backgroundViewPanel;
    private SpriteViewPanel spriteViewPanel;
    private PaletteViewPanel paletteViewPanel;

    // Status info
    private JLabel statusLabel;
    private JLabel tileInfoLabel;

    public VRAMViewer(GameBoyColor gbc) {
        this.gbc = gbc;
        this.memory = gbc.getMemory();

        setTitle("VRAM Viewer");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initializeComponents() {
        tabbedPane = new JTabbedPane();

        tileViewPanel = new TileViewPanel();
        backgroundViewPanel = new BackgroundViewPanel();
        spriteViewPanel = new SpriteViewPanel();
        paletteViewPanel = new PaletteViewPanel();

        tabbedPane.addTab("Tiles", tileViewPanel);
        tabbedPane.addTab("Background", backgroundViewPanel);
        tabbedPane.addTab("Sprites", spriteViewPanel);
        tabbedPane.addTab("Palettes", paletteViewPanel);

        statusLabel = new JLabel("Ready");
        tileInfoLabel = new JLabel("Hover over tiles for info");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        add(tabbedPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(tileInfoLabel, BorderLayout.EAST);

        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Add mouse listeners for tile info
        tileViewPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateTileInfo(e.getX(), e.getY());
            }
        });
    }

    private void updateTileInfo(int x, int y) {
        // Calculate which tile is being hovered
        int tileX = x / (8 * 2); // 8 pixels per tile, 2x scale
        int tileY = y / (8 * 2);

        if (tileX >= 0 && tileX < 16 && tileY >= 0 && tileY < 24) {
            int tileIndex = tileY * 16 + tileX;
            int tileAddress = 0x8000 + (tileIndex * 16);
            tileInfoLabel.setText(String.format("Tile: $%02X, Address: $%04X", tileIndex, tileAddress));
        }
    }

    public void refresh() {
        if (isVisible()) {
            tileViewPanel.refresh();
            backgroundViewPanel.refresh();
            spriteViewPanel.refresh();
            paletteViewPanel.refresh();
        }
    }

    // Tile View Panel
    private class TileViewPanel extends JPanel {
        private BufferedImage tileImage;
        private final int TILE_SIZE = 8;
        private final int SCALE = 2;
        private final int TILES_PER_ROW = 16;
        private final int TILE_ROWS = 24; // 384 tiles total

        public TileViewPanel() {
            setPreferredSize(new Dimension(TILES_PER_ROW * TILE_SIZE * SCALE,
                    TILE_ROWS * TILE_SIZE * SCALE));
            setBackground(Color.BLACK);
            refresh();
        }

        public void refresh() {
            // Create image for all tiles
            tileImage = new BufferedImage(TILES_PER_ROW * TILE_SIZE,
                    TILE_ROWS * TILE_SIZE,
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = tileImage.createGraphics();

            // Draw all 384 tiles from VRAM
            for (int tileIndex = 0; tileIndex < 384; tileIndex++) {
                int tileX = (tileIndex % TILES_PER_ROW) * TILE_SIZE;
                int tileY = (tileIndex / TILES_PER_ROW) * TILE_SIZE;

                drawTile(g2d, tileIndex, tileX, tileY);
            }

            g2d.dispose();
            repaint();
        }

        private void drawTile(Graphics2D g2d, int tileIndex, int destX, int destY) {
            int tileAddress = 0x8000 + (tileIndex * 16);

            // Game Boy grayscale palette
            Color[] palette = {
                    Color.WHITE, // 0
                    Color.LIGHT_GRAY, // 1
                    Color.GRAY, // 2
                    Color.BLACK // 3
            };

            for (int row = 0; row < 8; row++) {
                int lowByte = memory.readByte(tileAddress + row * 2) & 0xFF;
                int highByte = memory.readByte(tileAddress + row * 2 + 1) & 0xFF;

                for (int col = 0; col < 8; col++) {
                    int bit = 7 - col;
                    int low = (lowByte >> bit) & 1;
                    int high = (highByte >> bit) & 1;
                    int colorIndex = (high << 1) | low;

                    g2d.setColor(palette[colorIndex]);
                    g2d.fillRect(destX + col, destY + row, 1, 1);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (tileImage != null) {
                // Scale up for better visibility
                g.drawImage(tileImage, 0, 0,
                        tileImage.getWidth() * SCALE,
                        tileImage.getHeight() * SCALE, this);

                // Draw grid
                g.setColor(Color.BLUE);
                for (int x = 0; x <= TILES_PER_ROW; x++) {
                    int xPos = x * TILE_SIZE * SCALE;
                    g.drawLine(xPos, 0, xPos, getHeight());
                }
                for (int y = 0; y <= TILE_ROWS; y++) {
                    int yPos = y * TILE_SIZE * SCALE;
                    g.drawLine(0, yPos, getWidth(), yPos);
                }
            }
        }
    }

    // Background View Panel
    private class BackgroundViewPanel extends JPanel {
        private BufferedImage backgroundImage;

        public BackgroundViewPanel() {
            setPreferredSize(new Dimension(256 * 2, 256 * 2)); // 32x32 tiles scaled 2x
            setBackground(Color.BLACK);
        }

        public void refresh() {
            // Draw background tilemap
            backgroundImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = backgroundImage.createGraphics();

            // Read LCDC to determine tilemap location
            int lcdc = memory.readByte(0xFF40) & 0xFF;
            int bgMapBase = (lcdc & 0x08) != 0 ? 0x9C00 : 0x9800;
            boolean unsignedTiles = (lcdc & 0x10) != 0;

            Color[] palette = { Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.BLACK };

            for (int mapY = 0; mapY < 32; mapY++) {
                for (int mapX = 0; mapX < 32; mapX++) {
                    int mapIndex = mapY * 32 + mapX;
                    int tileNumber = memory.readByte(bgMapBase + mapIndex) & 0xFF;

                    int tileAddress;
                    if (unsignedTiles) {
                        tileAddress = 0x8000 + tileNumber * 16;
                    } else {
                        tileAddress = 0x9000 + ((byte) tileNumber) * 16;
                    }

                    // Draw 8x8 tile
                    for (int row = 0; row < 8; row++) {
                        int lowByte = memory.readByte(tileAddress + row * 2) & 0xFF;
                        int highByte = memory.readByte(tileAddress + row * 2 + 1) & 0xFF;

                        for (int col = 0; col < 8; col++) {
                            int bit = 7 - col;
                            int low = (lowByte >> bit) & 1;
                            int high = (highByte >> bit) & 1;
                            int colorIndex = (high << 1) | low;

                            g2d.setColor(palette[colorIndex]);
                            g2d.fillRect(mapX * 8 + col, mapY * 8 + row, 1, 1);
                        }
                    }
                }
            }

            g2d.dispose();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null) {
                g.drawImage(backgroundImage, 0, 0,
                        backgroundImage.getWidth() * 2,
                        backgroundImage.getHeight() * 2, this);
            }
        }
    }

    // Sprite View Panel
    private class SpriteViewPanel extends JPanel {
        public SpriteViewPanel() {
            setBackground(Color.DARK_GRAY);
        }

        public void refresh() {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Monospaced", Font.PLAIN, 12));

            // Display OAM data
            int y = 20;
            g.drawString("OAM Sprites:", 10, y);
            y += 20;

            for (int i = 0; i < 40; i++) {
                int oamBase = 0xFE00 + i * 4;
                int yPos = memory.readByte(oamBase) & 0xFF;
                int xPos = memory.readByte(oamBase + 1) & 0xFF;
                int tileIndex = memory.readByte(oamBase + 2) & 0xFF;
                int attributes = memory.readByte(oamBase + 3) & 0xFF;

                if (yPos > 0 && yPos < 160 && xPos > 0 && xPos < 168) {
                    String spriteInfo = String.format("Sprite %02d: Y=%3d X=%3d Tile=%02X Attr=%02X",
                            i, yPos, xPos, tileIndex, attributes);
                    g.drawString(spriteInfo, 10, y);
                    y += 15;

                    if (y > getHeight() - 20)
                        break;
                }
            }
        }
    }

    // Palette View Panel
    private class PaletteViewPanel extends JPanel {
        public PaletteViewPanel() {
            setBackground(Color.WHITE);
        }

        public void refresh() {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Background Palette
            int bgp = memory.readByte(0xFF47) & 0xFF;
            Color[] bgPalette = extractPalette(bgp);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            g.drawString("Background Palette (BGP):", 10, 25);

            for (int i = 0; i < 4; i++) {
                g.setColor(bgPalette[i]);
                g.fillRect(10 + i * 50, 35, 40, 40);
                g.setColor(Color.BLACK);
                g.drawRect(10 + i * 50, 35, 40, 40);
                g.drawString(String.valueOf(i), 25 + i * 50, 95);
            }

            // Object Palettes
            int obp0 = memory.readByte(0xFF48) & 0xFF;
            int obp1 = memory.readByte(0xFF49) & 0xFF;

            Color[] obj0Palette = extractPalette(obp0);
            Color[] obj1Palette = extractPalette(obp1);

            g.drawString("Object Palette 0 (OBP0):", 10, 130);
            for (int i = 0; i < 4; i++) {
                g.setColor(obj0Palette[i]);
                g.fillRect(10 + i * 50, 140, 40, 40);
                g.setColor(Color.BLACK);
                g.drawRect(10 + i * 50, 140, 40, 40);
                g.drawString(String.valueOf(i), 25 + i * 50, 200);
            }

            g.drawString("Object Palette 1 (OBP1):", 10, 235);
            for (int i = 0; i < 4; i++) {
                g.setColor(obj1Palette[i]);
                g.fillRect(10 + i * 50, 245, 40, 40);
                g.setColor(Color.BLACK);
                g.drawRect(10 + i * 50, 245, 40, 40);
                g.drawString(String.valueOf(i), 25 + i * 50, 305);
            }
        }

        private Color[] extractPalette(int paletteData) {
            Color[] colors = new Color[4];
            Color[] shades = { Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.BLACK };

            for (int i = 0; i < 4; i++) {
                int colorIndex = (paletteData >> (i * 2)) & 0x3;
                colors[i] = shades[colorIndex];
            }

            return colors;
        }
    }
}