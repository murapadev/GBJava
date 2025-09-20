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

    private final Screen screen;
    private final Memory memory;

    public DmgPixelFifo(Screen screen, Memory memory) {
        this.screen = screen;
        this.memory = memory;
    }

    @Override
    public int getLength() {
        return pixels.size();
    }

    @Override
    public void putPixelToScreen() {
        int pixel = dequeuePixel();
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
        int overlayPalette = 0; // TODO: Get from sprite palette registers

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

    /**
     * Get the color value for a pixel using DMG palette mapping.
     * 
     * @param paletteIndex the palette register value
     * @param pixelValue   the pixel value (0-3)
     * @return RGB color value
     */
    private int getColor(int paletteIndex, int pixelValue) {
        // DMG palette mapping (each 2 bits select a shade)
        int shade = (paletteIndex >> (pixelValue * 2)) & 0x3;

        // Map to grayscale colors
        return switch (shade) {
            case 0 -> 0xFFFFFF; // White
            case 1 -> 0xC0C0C0; // Light gray
            case 2 -> 0x606060; // Dark gray
            case 3 -> 0x000000; // Black
            default -> 0xFFFFFF;
        };
    }
}