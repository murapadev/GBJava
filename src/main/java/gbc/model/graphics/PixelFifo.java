package gbc.model.graphics;

/**
 * Pixel FIFO interface for Game Boy PPU pixel rendering.
 *
 * <h3>Priority Semantics</h3>
 * <p><b>DMG:</b> Sprite pixels win over BG unless the sprite's OAM priority bit
 * (bit 7) is set AND the BG color is non-zero. Among overlapping sprites,
 * lower X position wins; ties are broken by lower OAM index.
 *
 * <p><b>CGB:</b> LCDC bit 0 acts as a master BG priority switch.
 * When clear, sprites always win over BG. When set, the BG tile attribute
 * priority bit (bit 7) can force BG over sprites. Among sprites,
 * lower OAM index always wins (X position is not used for CGB priority).
 *
 * <p>Both implementations share this interface. The overlay/priority logic
 * is handled in {@link #setOverlay} and {@link #putPixelToScreen}.
 */
public interface PixelFifo {

    /**
     * Get the current length of the FIFO queue.
     * 
     * @return number of pixels currently in the queue
     */
    int getLength();

    /**
     * Put the next pixel from the FIFO to the screen.
     */
    void putPixelToScreen();

    /**
     * Drop the next pixel from the FIFO without displaying it.
     */
    void dropPixel();

    /**
     * Enqueue 8 pixels from a tile line into the FIFO.
     * 
     * @param pixelLine      array of 8 pixel values (0-3 for DMG, palette indices
     *                       for GBC)
     * @param tileAttributes tile attributes (for GBC background tiles)
     */
    void enqueue8Pixels(int[] pixelLine, TileAttributes tileAttributes);

    /**
     * Set sprite overlay pixels in the FIFO at the specified offset.
     * Handles sprite priority and merging with background pixels.
     * 
     * @param pixelLine        sprite pixel line (8 pixels)
     * @param offset           offset within the FIFO where to place the sprite
     *                         pixels
     * @param spriteAttributes sprite tile attributes
     * @param oamIndex         OAM index for sprite priority
     */
    void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex);

    /**
     * Variant used by the fetcher when sprite X has already been latched during
     * OAM search. Implementations that don't need X can ignore it.
     */
    default void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex, int spriteX) {
        setOverlay(pixelLine, offset, spriteAttributes, oamIndex);
    }

    /**
     * Clear the FIFO.
     */
    void clear();

    /**
     * Clear the FIFO queues for window activation without resetting the
     * screen X counter. On real hardware the pixel output position is not
     * affected when the window starts mid-scanline.
     */
    default void clearForWindow() {
        clear();
    }

    /**
     * Reset the FIFO to its initial state.
     */
    void reset();
}
