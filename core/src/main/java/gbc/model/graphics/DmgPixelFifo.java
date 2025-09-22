package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * Pixel FIFO implementation for original Game Boy (DMG).
 * Uses separate queues for pixel data and palette information.
 */
public class DmgPixelFifo implements PixelFifo {
    private final IntQueue pixels = new IntQueue(16);
    private final IntQueue palettes = new IntQueue(16);
    private final IntQueue pixelType = new IntQueue(16); // 0 = background, 1 = sprite
    private final Memory memory;

    public DmgPixelFifo(Screen screen, Memory memory) {
        this.memory = memory;
    }

    @Override
    public int getLength() {
        return pixels.size();
    }

    @Override
    public void putPixelToScreen() {
        dequeuePixel();
        // For DMG, pixels are already RGB values
        // This would need to be updated when integrating with the main rendering loop
    }

    @Override
    public void dropPixel() {
        dequeuePixel();
    }

    private int dequeuePixel() {
        pixelType.dequeue();
        palettes.dequeue();
        return pixels.dequeue();
    }

    @Override
    public void enqueue8Pixels(int[] pixelLine, TileAttributes tileAttributes) {
        for (int pixel : pixelLine) {
            pixels.enqueue(pixel);
            palettes.enqueue(0); // DMG doesn't use tile attributes for palette
            pixelType.enqueue(0); // Background pixel
        }
    }

    /**
     * Set sprite overlay pixels in the FIFO.
     * For DMG, sprites can be behind background or always on top.
     */
    @Override
    public void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex) {
        boolean priority = spriteAttributes.isPriority();
        // Read sprite palette registers based on sprite attributes (DMG: bit 4 of OAM
        // attr byte)
        // For DMG, we need to check bit 4 of the sprite attributes byte
        int spriteAttrByte = memory.readByte(0xFE00 + oamIndex * 4 + 3) & 0xFF;
        int paletteRegister = (spriteAttrByte & 0x10) != 0 ? 0xFF49 : 0xFF48; // Bit 4: 0=OBP0, 1=OBP1
        int overlayPalette = memory.readByte(paletteRegister) & 0xFF;

        for (int j = offset; j < pixelLine.length; j++) {
            int p = pixelLine[j];
            int i = j - offset;
            if (pixelType.get(i) == 1) {
                continue; // Already has a sprite pixel
            }
            if ((priority && pixels.get(i) == 0) || !priority && p != 0) {
                pixels.set(i, p);
                palettes.set(i, overlayPalette);
                pixelType.set(i, 1); // Mark as sprite pixel
            }
        }
    }

    @Override
    public void clear() {
        pixels.clear();
        palettes.clear();
        pixelType.clear();
    }
}