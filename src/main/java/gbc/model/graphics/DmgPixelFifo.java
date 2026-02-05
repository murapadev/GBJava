package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * Pixel FIFO implementation for original Game Boy (DMG).
 * Uses separate queues for pixel data and palette information.
 */
public class DmgPixelFifo implements PixelFifo {
    private final IntQueue bgPixels = new IntQueue(16);
    private final IntQueue bgPalettes = new IntQueue(16);
    private final IntQueue objPixels = new IntQueue(16);
    private final IntQueue objPalettes = new IntQueue(16);
    private final IntQueue objPaletteIndices = new IntQueue(16);
    private final IntQueue objBehindBg = new IntQueue(16); // 1 = OBJ behind BG, 0 = OBJ above BG
    private final IntQueue objPriority = new IntQueue(16); // lower value = higher priority
    private final Memory memory;
    private final FrameBuffer frameBuffer;
    private int x;

    public DmgPixelFifo(FrameBuffer frameBuffer, Memory memory) {
        this.frameBuffer = frameBuffer;
        this.memory = memory;
        this.x = 0;
    }

    @Override
    public int getLength() {
        return bgPixels.size();
    }

    @Override
    public void putPixelToScreen() {
        int bgPixel = bgPixels.dequeue();
        int bgPalette = bgPalettes.dequeue();
        int objPixel = objPixels.dequeue();
        int objPalette = objPalettes.dequeue();
        int objPaletteIndex = objPaletteIndices.dequeue();
        int objBehind = objBehindBg.dequeue();
        objPriority.dequeue();

        int y = memory.getLy(); // LY

        if (x < 160 && y < 144) {
            int color = resolveColor(bgPixel, bgPalette, objPixel, objPalette, objPaletteIndex, objBehind);
            frameBuffer.setPixel(x, y, color);
        }
        x++;
    }

    private int resolveColor(int bgPixel, int bgPalette, int objPixel, int objPalette, int objPaletteIndex, int objBehind) {
        if (objPixel == 0) {
            return getDmgColor(bgPixel, bgPalette, false, 0);
        }
        if (bgPixel == 0) {
            return getDmgColor(objPixel, objPalette, true, objPaletteIndex);
        }
        if (objBehind != 0) {
            return getDmgColor(bgPixel, bgPalette, false, 0);
        }
        // TODO: Verify DMG sprite priority rules for overlapping sprites and BG color 0 edge cases.
        return getDmgColor(objPixel, objPalette, true, objPaletteIndex);
    }

    private int getDmgColor(int colorId, int palette, boolean obj, int paletteIndex) {
        int shift = colorId * 2;
        int color = (palette >> shift) & 0x3;

        if (memory.isDmgOnCgb()) {
            return memory.resolveDmgCgbColor(obj, paletteIndex, color);
        }

        // Simplified DMG palette
        switch (color) {
            case 0:
                return 0xFFFFFFFF; // White
            case 1:
                return 0xFFC0C0C0; // Light Gray
            case 2:
                return 0xFF606060; // Dark Gray
            case 3:
                return 0xFF000000; // Black
            default:
                return 0xFFFFFFFF;
        }
    }

    @Override
    public void dropPixel() {
        dequeuePixel();
    }

    private int dequeuePixel() {
        bgPalettes.dequeue();
        objPixels.dequeue();
        objPalettes.dequeue();
        objPaletteIndices.dequeue();
        objBehindBg.dequeue();
        objPriority.dequeue();
        return bgPixels.dequeue();
    }

    @Override
    public void enqueue8Pixels(int[] pixelLine, TileAttributes tileAttributes) {
        for (int pixel : pixelLine) {
            bgPixels.enqueue(pixel);
            bgPalettes.enqueue(memory.getBgp()); // BGP
            objPixels.enqueue(0);
            objPalettes.enqueue(0);
            objPaletteIndices.enqueue(0);
            objBehindBg.enqueue(0);
            objPriority.enqueue(Integer.MAX_VALUE);
        }
    }

    /**
     * Set sprite overlay pixels in the FIFO.
     * For DMG, sprites can be behind background or always on top.
     */
    @Override
    public void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex) {
        int spriteX = (memory.getOamByte(oamIndex * 4 + 1) & 0xFF) - 8;
        setOverlay(pixelLine, offset, spriteAttributes, oamIndex, spriteX);
    }

    @Override
    public void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex, int spriteX) {
        boolean priority = spriteAttributes.isPriority();
        boolean xflip = spriteAttributes.isXflip();
        int paletteIndex = spriteAttributes.getDmgObjPaletteIndex();
        int overlayPalette = paletteIndex != 0 ? memory.getObp1() : memory.getObp0(); // Bit 4: 0=OBP0, 1=OBP1
        int priorityKey = (spriteX << 8) | (oamIndex & 0xFF);
        // TODO: Consider OAM search ordering vs X sorting for DMG sprite conflicts.

        int fifoSize = bgPixels.size();
        for (int screenIndex = 0; screenIndex < 8; screenIndex++) {
            int fifoIndex = screenIndex - offset;
            if (fifoIndex < 0 || fifoIndex >= fifoSize) {
                continue;
            }
            int srcIndex = xflip ? (7 - screenIndex) : screenIndex;
            if (srcIndex < 0 || srcIndex >= pixelLine.length) {
                continue;
            }
            int p = pixelLine[srcIndex];
            if (p == 0) {
                continue;
            }
            if (objPixels.get(fifoIndex) != 0) {
                int existingPriority = objPriority.get(fifoIndex);
                if (priorityKey >= existingPriority) {
                    continue; // Lower priority sprite loses
                }
                // Higher priority sprite wins; override
                objPixels.set(fifoIndex, p);
                objPalettes.set(fifoIndex, overlayPalette);
                objPaletteIndices.set(fifoIndex, paletteIndex);
                objBehindBg.set(fifoIndex, priority ? 1 : 0);
                objPriority.set(fifoIndex, priorityKey);
                continue;
            }
            objPixels.set(fifoIndex, p);
            objPalettes.set(fifoIndex, overlayPalette);
            objPaletteIndices.set(fifoIndex, paletteIndex);
            objBehindBg.set(fifoIndex, priority ? 1 : 0);
            objPriority.set(fifoIndex, priorityKey);
        }
    }

    @Override
    public void clear() {
        bgPixels.clear();
        bgPalettes.clear();
        objPixels.clear();
        objPalettes.clear();
        objPaletteIndices.clear();
        objBehindBg.clear();
        objPriority.clear();
        x = 0;
    }

    @Override
    public void reset() {
        clear();
    }
}
