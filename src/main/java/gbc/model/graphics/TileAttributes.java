package gbc.model.graphics;

/**
 * Tile attributes for Game Boy Color background, window, and sprite tiles.
 *
 * <h3>Priority Bit Interactions (CGB)</h3>
 * <p>Bit 7 has different meanings depending on context:
 * <ul>
 *   <li><b>BG/Window tiles:</b> When set, this BG tile has priority over all sprites
 *       regardless of sprite OAM priority — but only if LCDC bit 0 is set (master
 *       BG priority enabled).</li>
 *   <li><b>Sprite (OBJ) tiles:</b> When set, the sprite renders behind BG colors 1-3
 *       (but still shows over BG color 0). This is the "OBJ-to-BG priority" flag.</li>
 * </ul>
 *
 * <p>Resolution order in CGB mode (see {@link CgbPixelFifo}):
 * <ol>
 *   <li>LCDC bit 0 off → sprites always visible</li>
 *   <li>BG color 0 → sprite always visible</li>
 *   <li>BG priority attribute set → BG wins</li>
 *   <li>OBJ priority attribute set → BG wins if non-zero</li>
 *   <li>Otherwise → sprite wins</li>
 * </ol>
 */
public class TileAttributes {
    private final int rawValue;
    private final int palette; // CGB palette number (0-7)
    private final int vramBank; // VRAM bank (0-1) for CGB tiles
    private final boolean xFlip; // Horizontal flip
    private final boolean yFlip; // Vertical flip
    private final boolean priority; // BG-to-OAM priority

    public TileAttributes(int value) {
        this.rawValue = value & 0xFF;
        this.palette = value & 0x07;
        this.vramBank = (value & 0x08) != 0 ? 1 : 0;
        this.xFlip = (value & 0x20) != 0;
        this.yFlip = (value & 0x40) != 0;
        this.priority = (value & 0x80) != 0;
    }

    public int getPalette() {
        return palette;
    }

    public int getVramBank() {
        return vramBank;
    }

    public boolean isXflip() {
        return xFlip;
    }

    public boolean isYflip() {
        return yFlip;
    }

    public boolean isPriority() {
        return priority;
    }

    /**
     * DMG OBJ palette select (OAM attribute bit 4).
     * 0 -> OBP0, 1 -> OBP1.
     */
    public int getDmgObjPaletteIndex() {
        return (rawValue & 0x10) != 0 ? 1 : 0;
    }
}
