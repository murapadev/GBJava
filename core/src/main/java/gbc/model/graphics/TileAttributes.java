package gbc.model.graphics;

/**
 * Tile attributes for Game Boy Color background and window tiles.
 * Contains palette, flip flags, and priority information.
 */
public class TileAttributes {
    private final int palette; // CGB palette number (0-7)
    private final boolean xFlip; // Horizontal flip
    private final boolean yFlip; // Vertical flip
    private final boolean priority; // BG-to-OAM priority

    public TileAttributes(int value) {
        this.palette = value & 0x07;
        this.xFlip = (value & 0x20) != 0;
        this.yFlip = (value & 0x40) != 0;
        this.priority = (value & 0x80) != 0;
    }

    public int getPalette() {
        return palette;
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
}