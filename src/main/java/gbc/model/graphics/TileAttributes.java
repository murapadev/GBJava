package gbc.model.graphics;

/**
 * Tile attributes for Game Boy Color background and window tiles.
 * Contains palette, flip flags, and priority information.
 */
public class TileAttributes {
    // TODO: Validate priority bit interactions (CGB BG priority vs OBJ priority rules).
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
