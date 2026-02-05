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
    private int windowPixelsToDrop;
    private int windowDroppedPixels;
    private int scxLatch;
    private boolean scxLatched;
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
        if (!scxLatched) {
            scxLatch = memory.getScx() & 0xFF;
        }
        scxLatched = false;
        // Reset window line when a new frame starts or LY jumps backwards
        // (LCD off/on or explicit LY reset behavior).
        if (ly == 0 || ly < lastLy) {
            windowLine = 0;
            windowUsedLastLine = false;
        }
        if (ly != lastLy) {
            if (windowUsedLastLine) {
                windowLine++;
            }
            windowUsedLastLine = false;
            lastLy = ly;
        }

        this.x = 0;
        this.droppedPixels = 0;
        this.windowPixelsToDrop = 0;
        this.windowDroppedPixels = 0;
        this.windowActive = false;
        fetcher.init();
        fetcher.setScxLatch(scxLatch);

        // Start fetching background/window tiles
        startFetchingBackground();
    }

    public boolean tick() {
        // Switch to window when conditions are met
        if (!windowActive && shouldStartWindow()) {
            windowActive = true;
            windowUsedLastLine = true;
            droppedPixels = 0;
            int windowStartX = memory.getWx() - 7;
            windowPixelsToDrop = Math.max(0, -windowStartX);
            windowDroppedPixels = 0;
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
            boolean stallForSprite = false;
            int stallX = Integer.MAX_VALUE;
            int stallOamIndex = Integer.MAX_VALUE;

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
                    // If the sprite has reached the current X but the FIFO is
                    // too short, stall output to let the fetcher fill the FIFO.
                    if (offset >= 0) {
                        int oamIndex = s.getOamIndex();
                        if (s.getX() < stallX || (s.getX() == stallX && oamIndex < stallOamIndex)) {
                            stallForSprite = true;
                            stallX = s.getX();
                            stallOamIndex = oamIndex;
                        }
                    }
                    continue;
                }
                int oamIndex = s.getOamIndex();
                // Sprite fetch scheduling is left-to-right on the scanline.
                // Final pixel priority differences are handled in the FIFO implementation.
                if (s.getX() < bestX || (s.getX() == bestX && oamIndex < bestOamIndex)) {
                    bestIndex = i;
                    bestX = s.getX();
                    bestOamIndex = oamIndex;
                    bestOffset = offset;
                }
            }

            if (bestIndex != -1) {
                SpritePosition s = sprites[bestIndex];
                fetcher.addSprite(s, bestOffset, bestOamIndex);
                sprites[bestIndex].disable();
            } else if (stallForSprite && !fetcher.isFetchingDisabled()) {
                return x < 160;
            }
        }

        // Keep pixel output stalled while sprite fetch is in progress.
        // This avoids dropping early sprite pixels when the overlay is not ready yet.
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
            } else if (windowActive && windowDroppedPixels < windowPixelsToDrop) {
                fifo.dropPixel();
                windowDroppedPixels++;
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
        // DMG: bit 0 disables BG. CGB: BG is always enabled; bit 0 only affects priority.
        boolean bgEnabled = memory.isCgbMode() || (lcdc & 0x01) != 0;

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
        // Start at tile 0; SCX upper bits are sampled per tile fetch.
        fetcher.startFetching(bgMapBase, bgTileDataBase, 0, tileIdSigned, bgY, true, true);
    }

    private void startFetchingWindow() {
        int lcdc = memory.getLcdc();
        int windowMapBase = (lcdc & 0x40) != 0 ? 0x9C00 : 0x9800;
        int windowTileDataBase = (lcdc & 0x10) != 0 ? 0x8000 : 0x9000;
        boolean tileIdSigned = (lcdc & 0x10) == 0;

        fetcher.startFetching(windowMapBase, windowTileDataBase, 0, tileIdSigned, windowLine, false, false);
    }

    private int getScx() {
        return scxLatch;
    }

    private boolean spritesEnabled() {
        return (memory.getLcdc() & 0x02) != 0;
    }

    private boolean shouldStartWindow() {
        int lcdc = memory.getLcdc();
        if (!memory.isCgbMode() && (lcdc & 0x01) == 0) {
            return false;
        }
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
        // TODO: Verify window trigger rules (WX/WY edge cases and mid-line behavior).
        return x >= windowStartX;
    }


    public void reset() {
        this.x = 0;
        this.droppedPixels = 0;
        this.windowPixelsToDrop = 0;
        this.windowDroppedPixels = 0;
        this.windowActive = false;
        this.scxLatched = false;
        fifo.clear();
    }

    public void setScxLatch(int scx) {
        this.scxLatch = scx & 0xFF;
        this.scxLatched = true;
    }

    public int getCurrentX() {
        return x;
    }
}
