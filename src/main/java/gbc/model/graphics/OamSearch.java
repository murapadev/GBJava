package gbc.model.graphics;

import gbc.model.memory.Memory;
import java.util.Arrays;

/**
 * OAM search phase - finds sprites that should be rendered on the current
 * scanline.
 */
public class OamSearch {
    private final Memory memory;
    private final SpritePosition[] sprites;
    private int activeSpriteCount;
    private final int[] penaltySpriteX;
    private final int[] penaltySpriteOam;
    private int penaltySpriteCount;
    private int mode3Duration;
    private int latchedScx;
    private boolean scxLatched;
    private final boolean traceMode3 = Boolean.getBoolean("gbc.mode3.trace");
    private final int traceMode3Line = Integer.getInteger("gbc.mode3.traceLy", -1);

    public OamSearch(Memory memory) {
        this.memory = memory;
        this.sprites = new SpritePosition[10]; // Up to 10 sprites per scanline
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = new SpritePosition();
        }
        this.penaltySpriteX = new int[10];
        this.penaltySpriteOam = new int[10];
        this.activeSpriteCount = 0;
        this.mode3Duration = 172; // Base duration
    }

    public void searchSprites(int ly) {
        // Clear previous sprites
        for (SpritePosition sprite : sprites) {
            sprite.disable();
        }

        activeSpriteCount = 0;
        penaltySpriteCount = 0;
        // Read LCDC register (0xFF40) to determine sprite enable/height
        int lcdc = memory.getLcdc() & 0xFF;
        boolean spritesEnabled = (lcdc & 0x02) != 0; // Bit 1: OBJ enable
        int spriteHeight = (lcdc & 0x04) != 0 ? 16 : 8; // Bit 2: 0=8x8, 1=8x16

        if (spritesEnabled) {
            // Search through all 40 sprites in OAM
            // TODO: Emulate OAM scan timing and the hardware sprite overflow behavior.
            for (int i = 0; i < 40 && activeSpriteCount < 10; i++) {
                int baseAddr = 0xFE00 + i * 4;
                int yPos = (memory.getOamByte(i * 4) & 0xFF) - 16;

                // Check if sprite intersects current scanline
                if (ly >= yPos && ly < yPos + spriteHeight) {
                    int xPos = (memory.getOamByte(i * 4 + 1) & 0xFF) - 8;
                    int tileId = memory.getOamByte(i * 4 + 2) & 0xFF;
                    int attributes = memory.getOamByte(i * 4 + 3) & 0xFF;
                    sprites[activeSpriteCount].enable(xPos, yPos, baseAddr, i, tileId, attributes);
                    penaltySpriteX[penaltySpriteCount] = xPos;
                    penaltySpriteOam[penaltySpriteCount] = i;
                    penaltySpriteCount++;
                    activeSpriteCount++;
                }
            }
        }

        // Latch SCX at the start of the scanline (mode 2) for timing/penalty calculations.
        latchedScx = memory.getScx() & 0xFF;
        scxLatched = true;
    }

    public void reset() {
        for (SpritePosition sprite : sprites) {
            sprite.disable();
        }
        activeSpriteCount = 0;
        mode3Duration = 172;
    }

    public SpritePosition[] getSprites() {
        return sprites;
    }
    
    /**
     * Returns the number of sprites found on the current scanline.
     */
    public int getActiveSpriteCount() {
        return activeSpriteCount;
    }
    
    /**
     * Returns the calculated Mode 3 duration for the current scanline.
     * This varies based on SCX value and number of sprites.
     */
    public int getMode3Duration() {
        if (!scxLatched) {
            latchedScx = memory.getScx() & 0xFF;
            scxLatched = true;
        }
        int lcdc = memory.getLcdc() & 0xFF;
        boolean spritesEnabled = (lcdc & 0x02) != 0;
        int ly = memory.getLy() & 0xFF;
        mode3Duration = computeMode3Duration(ly, latchedScx, spritesEnabled);
        if (traceMode3 && ((traceMode3Line < 0 && activeSpriteCount > 0) || traceMode3Line == ly)) {
            int spritePenalty = spritesEnabled ? calculateSpritePenalty(latchedScx, ly) : 0;
            System.out.printf("MODE3 LY=%d SCX=%d sprites=%d penalty=%d duration=%d\n",
                    ly, latchedScx, activeSpriteCount, spritePenalty, mode3Duration);
        }
        return mode3Duration;
    }

    public int getLatchedScx() {
        if (!scxLatched) {
            latchedScx = memory.getScx() & 0xFF;
            scxLatched = true;
        }
        return latchedScx & 0xFF;
    }

    private int computeMode3Duration(int ly, int scx, boolean spritesEnabled) {
        int scxPenalty = scx & 0x07;
        if (!memory.isCgbMode()) {
            // DMG timing rounds SCX fine scroll penalty to the next M-cycle.
            scxPenalty = (scxPenalty + 3) & ~3;
        }
        int windowPenalty = calculateWindowPenalty(ly, scx);
        int spritePenalty = spritesEnabled ? calculateSpritePenalty(scx, ly) : 0;
        return Math.min(289, 172 + scxPenalty + windowPenalty + spritePenalty);
    }

    private int calculateWindowPenalty(int ly, int scx) {
        int lcdc = memory.getLcdc() & 0xFF;
        boolean windowEnabled = (lcdc & 0x20) != 0;
        if (!windowEnabled) {
            return 0;
        }
        int windowStartX = memory.getWx() - 7;
        if (windowStartX < 0) {
            windowStartX = 0;
        }
        if (windowStartX >= 160) {
            return 0;
        }
        if (ly < (memory.getWy() & 0xFF)) {
            return 0;
        }
        int penalty = 6;
        // Hardware quirk: WX=0 with SCX misaligned can shorten the window start penalty by 1 dot.
        if ((memory.getWx() & 0xFF) == 0 && (scx & 0x07) != 0) {
            penalty--;
        }
        return penalty;
    }

    private int calculateSpritePenalty(int scx, int ly) {
        if (penaltySpriteCount == 0) {
            return 0;
        }

        int lcdc = memory.getLcdc() & 0xFF;
        boolean windowEnabled = (lcdc & 0x20) != 0;
        int wy = memory.getWy() & 0xFF;
        int windowStartX = memory.getWx() - 7;
        if (windowStartX < 0) {
            windowStartX = 0;
        }
        boolean windowUsed = windowEnabled && windowStartX < 160 && ly >= wy;

        // Sort sprites by X position (left to right), ties by OAM index.
        int count = penaltySpriteCount;
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        for (int i = 1; i < count; i++) {
            int key = order[i];
            int keyX = penaltySpriteX[key];
            int keyOam = penaltySpriteOam[key];
            int j = i - 1;
            while (j >= 0) {
                int idx = order[j];
                int dx = penaltySpriteX[idx] - keyX;
                if (dx < 0 || (dx == 0 && penaltySpriteOam[idx] <= keyOam)) {
                    break;
                }
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }

        // Track which background/window tiles have already been considered.
        boolean[] seenBgTiles = new boolean[32];
        boolean[] seenWinTiles = new boolean[32];

        int penalty = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            int x = penaltySpriteX[idx];
            int oamX = x + 8;
            if (oamX >= 168) {
                continue; // fully off-screen to the right
            }
            if (oamX == 0) {
                // Special case: OAM X=0 always incurs 11-dot penalty.
                penalty += 11;
                continue;
            }

            boolean overWindow = windowUsed && x >= windowStartX;
            int tilePixel;
            int tileCol;
            boolean tileSeen;
            if (overWindow) {
                int winX = x - windowStartX;
                tilePixel = winX & 0x07;
                tileCol = (winX >> 3) & 0x1F;
                tileSeen = seenWinTiles[tileCol];
                seenWinTiles[tileCol] = true;
            } else {
                int bgX = (x + scx) & 0xFF;
                tilePixel = bgX & 0x07;
                tileCol = (bgX >> 3) & 0x1F;
                tileSeen = seenBgTiles[tileCol];
                seenBgTiles[tileCol] = true;
            }

            int extra = 0;
            if (!tileSeen) {
                int pixelsRight = 7 - tilePixel;
                extra = Math.max(0, pixelsRight - 2);
            }
            penalty += 6 + extra;
        }
        return penalty;
    }
}
