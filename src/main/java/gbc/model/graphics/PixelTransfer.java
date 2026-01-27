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
    private int windowLine; // Window internal line counter
    private int lastLy;
    private boolean windowActive;
    private boolean windowUsedLastLine;

    public PixelTransfer(PixelFifo fifo, Fetcher fetcher, Memory memory, SpritePosition[] sprites) {
        this.fifo = fifo;
        this.fetcher = fetcher;
        this.memory = memory;
        this.sprites = sprites;
    }

    public void start() {
        int ly = memory.getLy();
        if (ly == 0) {
            windowLine = 0;
            lastLy = 0;
            windowUsedLastLine = false;
        } else if (ly != lastLy) {
            if (windowUsedLastLine) {
                windowLine++;
            }
            windowUsedLastLine = false;
            lastLy = ly;
        }

        this.x = 0;
        this.droppedPixels = 0;
        this.windowActive = false;
        fetcher.init();

        // Start fetching background/window tiles
        startFetchingBackground();
    }

    public boolean tick() {
        // Switch to window when conditions are met
        if (!windowActive && shouldStartWindow()) {
            windowActive = true;
            windowUsedLastLine = true;
            droppedPixels = 0;
            fifo.clear();
            fetcher.init();
            startFetchingWindow();
        }

        // Run fetcher
        fetcher.tick();

        // Check for sprites to add
        if (!fetcher.spriteInProgress() && spritesEnabled()) {
            int bestIndex = -1;
            int bestX = Integer.MAX_VALUE;
            int bestOamIndex = Integer.MAX_VALUE;
            int bestOffset = 0;
            int fifoLen = fifo.getLength();
            boolean cgbMode = memory.isCgbMode();

            for (int i = 0; i < sprites.length; i++) {
                SpritePosition s = sprites[i];
                if (!s.isEnabled()) {
                    continue;
                }
                int offset;
                if (s.getX() <= x) {
                    // Sprite already started; drop left part
                    offset = x - s.getX();
                } else {
                    // Sprite starts ahead in FIFO; use negative offset to shift right
                    offset = -(s.getX() - x);
                }
                if (offset >= 8) {
                    // Already past sprite width; skip it.
                    sprites[i].disable();
                    continue;
                }
                int requiredFifo = 8 - offset;
                if (requiredFifo > fifoLen) {
                    continue;
                }
                int oamIndex = (s.getAddress() - 0xFE00) / 4;
                if (cgbMode) {
                    // CGB priority: lower OAM index wins
                    if (oamIndex < bestOamIndex) {
                        bestIndex = i;
                        bestX = s.getX();
                        bestOamIndex = oamIndex;
                        bestOffset = offset;
                    }
                } else {
                    // DMG priority: lower X wins; tie-breaker lower OAM index
                    if (s.getX() < bestX || (s.getX() == bestX && oamIndex < bestOamIndex)) {
                        bestIndex = i;
                        bestX = s.getX();
                        bestOamIndex = oamIndex;
                        bestOffset = offset;
                    }
                }
            }

            if (bestIndex != -1) {
                SpritePosition s = sprites[bestIndex];
                fetcher.addSprite(s, bestOffset, bestOamIndex);
                sprites[bestIndex].disable();
            }
        }

        // Stall pixel output while sprite fetch is in progress (approximate hardware behavior)
        if (fetcher.spriteInProgress()) {
            return x < 160;
        }

        // If we have pixels in FIFO
        if (fifo.getLength() > 0) {
            // Check for SCX fine scrolling (dropping pixels)
            if (!windowActive && droppedPixels < getScx() % 8) {
                fifo.dropPixel();
                droppedPixels++;
                // Do not increment x, do not put pixel to screen
            } else if (x < 160) {
                fifo.putPixelToScreen();
                x++;
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
        int lcdc = memory.getLcdc();
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

        int ly = memory.getLy();
        int scy = memory.getScy();
        int bgY = (ly + scy) & 0xFF;
        int xOffset = (getScx() / 8) & 0x1F;

        fetcher.startFetching(bgMapBase, bgTileDataBase, xOffset, tileIdSigned, bgY);
    }

    private void startFetchingWindow() {
        int lcdc = memory.getLcdc();
        int windowMapBase = (lcdc & 0x40) != 0 ? 0x9C00 : 0x9800;
        int windowTileDataBase = (lcdc & 0x10) != 0 ? 0x8000 : 0x9000;
        boolean tileIdSigned = (lcdc & 0x10) == 0;

        fetcher.startFetching(windowMapBase, windowTileDataBase, 0, tileIdSigned, windowLine);
    }

    private int getScx() {
        return memory.getScx();
    }

    private boolean spritesEnabled() {
        return (memory.getLcdc() & 0x02) != 0;
    }

    private boolean shouldStartWindow() {
        int lcdc = memory.getLcdc();
        boolean windowEnabled = (lcdc & 0x20) != 0;
        if (!windowEnabled) {
            return false;
        }
        int ly = memory.getLy();
        if (ly < memory.getWy()) {
            return false;
        }
        int windowStartX = memory.getWx() - 7;
        if (windowStartX < 0) {
            windowStartX = 0;
        }
        if (windowStartX >= 160) {
            return false;
        }
        return x >= windowStartX;
    }


    public void reset() {
        this.x = 0;
        this.droppedPixels = 0;
        this.windowActive = false;
        fifo.clear();
    }
}
