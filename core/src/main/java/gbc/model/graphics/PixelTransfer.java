package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * Pixel Transfer phase for Game Boy PPU.
 * Manages the fetcher and FIFO during mode 3 (pixel transfer).
 */
public class PixelTransfer {
    private final PixelFifo fifo;
    private final Fetcher fetcher;
    private final Memory memory;
    private final SpritePosition[] sprites;

    private int x; // Current X position on scanline
    private int droppedPixels; // Pixels dropped for window/scx alignment

    public PixelTransfer(PixelFifo fifo, Fetcher fetcher, Memory memory, SpritePosition[] sprites) {
        this.fifo = fifo;
        this.fetcher = fetcher;
        this.memory = memory;
        this.sprites = sprites;
    }

    public void start() {
        this.x = 0;
        this.droppedPixels = 0;
        fetcher.init();

        // Start fetching background/window tiles
        startFetchingBackground();
    }

    public boolean tick() {
        // Run fetcher
        fetcher.tick();

        // Check for sprites to add
        if (!fetcher.spriteInProgress()) {
            boolean spriteAdded = false;
            for (int i = 0; i < sprites.length && !spriteAdded; i++) {
                SpritePosition s = sprites[i];
                if (!s.isEnabled()) {
                    continue;
                }
                // Check if sprite should be rendered at current X position
                if (s.getX() - 8 == x) {
                    fetcher.addSprite(s, 0, i);
                    sprites[i].disable();
                    spriteAdded = true;
                } else if (x == 0 && s.getX() < 8) {
                    fetcher.addSprite(s, 8 - s.getX(), i);
                    sprites[i].disable();
                    spriteAdded = true;
                }
            }
        }

        // If we have pixels in FIFO and haven't reached end of line
        if (fifo.getLength() > 0 && x < 160) {
            fifo.putPixelToScreen();
            x++;

            // Handle SCX scrolling
            if (droppedPixels < getScx() % 8) {
                fifo.dropPixel();
                droppedPixels++;
                x--; // Don't advance x for dropped pixels
            }
        } else if (fetcher.isFetchingDisabled() && fifo.getLength() == 0 && x < 160) {
            // Background disabled - enqueue a line of background color pixels
            int[] emptyLine = new int[8];
            TileAttributes defaultAttrs = new TileAttributes(0);
            fifo.enqueue8Pixels(emptyLine, defaultAttrs);
            // Now we have pixels, so the next iteration will handle them
        }

        // Continue until we've processed 160 pixels
        return x < 160;
    }

    private void startFetchingBackground() {
        int lcdc = memory.readByte(0xFF40) & 0xFF;
        boolean bgEnabled = (lcdc & 0x01) != 0;

        if (!bgEnabled) {
            // If background is disabled, disable fetching - pixels will be handled in
            // tick()
            fetcher.fetchingDisabled();
            return;
        }

        // Determine background map and tile data addresses
        int bgMapBase = (lcdc & 0x08) != 0 ? 0x9C00 : 0x9800;
        int bgTileDataBase = (lcdc & 0x10) != 0 ? 0x8000 : 0x9000;
        boolean tileIdSigned = (lcdc & 0x10) == 0;

        int ly = memory.readByte(0xFF44) & 0xFF;
        int scy = memory.readByte(0xFF42) & 0xFF;
        int bgY = (ly + scy) & 0xFF;
        int tileLine = bgY % 8;

        fetcher.startFetching(bgMapBase, bgTileDataBase, 0, tileIdSigned, tileLine);
    }

    private int getScx() {
        return memory.readByte(0xFF43) & 0xFF;
    }

    public void reset() {
        this.x = 0;
        this.droppedPixels = 0;
    }
}