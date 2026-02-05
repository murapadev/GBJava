package gbc.model.graphics;

/**
 * Pixel FIFO interface for Game Boy PPU pixel rendering.
 * Handles queuing pixels and outputting them to the screen.
 */
public interface PixelFifo {
    // TODO: Define precise overlay/priority semantics shared by DMG/CGB implementations.

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
     * Reset the FIFO to its initial state.
     */
    void reset();
}
