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
    private final Screen screen;
    private int x;

    public DmgPixelFifo(Screen screen, Memory memory) {
        this.screen = screen;
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
            screen.setPixel(x, y, color);
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
        boolean priority = spriteAttributes.isPriority();
        // Read sprite palette registers based on sprite attributes (DMG: bit 4 of OAM
        // attr byte)
        // For DMG, we need to check bit 4 of the sprite attributes byte
        int spriteAttrByte = memory.getOamByte(oamIndex * 4 + 3);
        int paletteIndex = (spriteAttrByte & 0x10) != 0 ? 1 : 0;
        int overlayPalette = paletteIndex != 0 ? memory.getObp1() : memory.getObp0(); // Bit 4: 0=OBP0, 1=OBP1
        int spriteX = (memory.getOamByte(oamIndex * 4 + 1) & 0xFF) - 8;
        int priorityKey = (spriteX << 8) | (oamIndex & 0xFF);

        int fifoSize = bgPixels.size();
        for (int j = Math.max(0, offset); j < pixelLine.length; j++) {
            int p = pixelLine[j];
            int i = j - offset;
            if (i < 0 || i >= fifoSize) {
                break;
            }
            if (p == 0) {
                continue;
            }
            if (objPixels.get(i) != 0) {
                int existingPriority = objPriority.get(i);
                if (priorityKey >= existingPriority) {
                    continue; // Lower priority sprite loses
                }
                // Higher priority sprite wins; override
                objPixels.set(i, p);
                objPalettes.set(i, overlayPalette);
                objPaletteIndices.set(i, paletteIndex);
                objBehindBg.set(i, priority ? 1 : 0);
                objPriority.set(i, priorityKey);
                continue;
            }
            objPixels.set(i, p);
            objPalettes.set(i, overlayPalette);
            objPaletteIndices.set(i, paletteIndex);
            objBehindBg.set(i, priority ? 1 : 0);
            objPriority.set(i, priorityKey);
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
