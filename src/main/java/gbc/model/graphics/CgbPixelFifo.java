package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * Pixel FIFO implementation for Game Boy Color (CGB).
 * Uses CGB palettes and tile attributes for color resolution.
 */
public class CgbPixelFifo implements PixelFifo {
    private final IntQueue bgPixels = new IntQueue(16);
    private final IntQueue bgPalettes = new IntQueue(16);
    private final IntQueue bgPriority = new IntQueue(16);
    private final IntQueue objPixels = new IntQueue(16);
    private final IntQueue objPalettes = new IntQueue(16);
    private final IntQueue objBehindBg = new IntQueue(16);
    private final IntQueue objPriority = new IntQueue(16);
    private final Memory memory;
    private final Screen screen;
    private int x;

    public CgbPixelFifo(Screen screen, Memory memory) {
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
        int bgPrio = bgPriority.dequeue();
        int objPixel = objPixels.dequeue();
        int objPalette = objPalettes.dequeue();
        int objBehind = objBehindBg.dequeue();
        objPriority.dequeue();

        int y = memory.getLy();
        if (x < 160 && y < 144) {
            int color = resolveColor(bgPixel, bgPalette, bgPrio, objPixel, objPalette, objBehind);
            screen.setPixel(x, y, color);
        }
        x++;
    }

    private int resolveColor(int bgPixel, int bgPalette, int bgPrio, int objPixel, int objPalette, int objBehind) {
        boolean bgEnabled = (memory.getLcdc() & 0x01) != 0;
        if (!bgEnabled) {
            if (objPixel == 0) {
                return getCgbBgColor(0, 0);
            }
            return getCgbObjColor(objPalette, objPixel);
        }

        if (objPixel == 0) {
            return getCgbBgColor(bgPalette, bgPixel);
        }
        if (bgPixel == 0) {
            return getCgbObjColor(objPalette, objPixel);
        }
        if (bgPrio != 0) {
            return getCgbBgColor(bgPalette, bgPixel);
        }
        if (objBehind != 0) {
            return getCgbBgColor(bgPalette, bgPixel);
        }
        return getCgbObjColor(objPalette, objPixel);
    }

    private int getCgbBgColor(int palette, int colorId) {
        int index = (palette & 0x07) * 8 + (colorId & 0x03) * 2;
        int lo = memory.readCgbBgPaletteByte(index);
        int hi = memory.readCgbBgPaletteByte(index + 1);
        return convertCgbColor(lo, hi);
    }

    private int getCgbObjColor(int palette, int colorId) {
        int index = (palette & 0x07) * 8 + (colorId & 0x03) * 2;
        int lo = memory.readCgbObjPaletteByte(index);
        int hi = memory.readCgbObjPaletteByte(index + 1);
        return convertCgbColor(lo, hi);
    }

    private int convertCgbColor(int lo, int hi) {
        int value = ((hi & 0xFF) << 8) | (lo & 0xFF);
        int r = value & 0x1F;
        int g = (value >> 5) & 0x1F;
        int b = (value >> 10) & 0x1F;
        int rr = (r * 255) / 31;
        int gg = (g * 255) / 31;
        int bb = (b * 255) / 31;
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    @Override
    public void dropPixel() {
        dequeuePixel();
    }

    private int dequeuePixel() {
        bgPalettes.dequeue();
        bgPriority.dequeue();
        objPixels.dequeue();
        objPalettes.dequeue();
        objBehindBg.dequeue();
        objPriority.dequeue();
        return bgPixels.dequeue();
    }

    @Override
    public void enqueue8Pixels(int[] pixelLine, TileAttributes tileAttributes) {
        int palette = tileAttributes != null ? tileAttributes.getPalette() : 0;
        int priority = tileAttributes != null && tileAttributes.isPriority() ? 1 : 0;
        for (int pixel : pixelLine) {
            bgPixels.enqueue(pixel);
            bgPalettes.enqueue(palette);
            bgPriority.enqueue(priority);
            objPixels.enqueue(0);
            objPalettes.enqueue(0);
            objBehindBg.enqueue(0);
            objPriority.enqueue(Integer.MAX_VALUE);
        }
    }

    @Override
    public void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex) {
        boolean priority = spriteAttributes != null && spriteAttributes.isPriority();
        int palette = spriteAttributes != null ? spriteAttributes.getPalette() : 0;
        int priorityKey = oamIndex & 0xFF; // CGB OBJ priority: lower OAM index wins

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
                    continue;
                }
                objPixels.set(i, p);
                objPalettes.set(i, palette);
                objBehindBg.set(i, priority ? 1 : 0);
                objPriority.set(i, priorityKey);
                continue;
            }
            objPixels.set(i, p);
            objPalettes.set(i, palette);
            objBehindBg.set(i, priority ? 1 : 0);
            objPriority.set(i, priorityKey);
        }
    }

    @Override
    public void clear() {
        bgPixels.clear();
        bgPalettes.clear();
        bgPriority.clear();
        objPixels.clear();
        objPalettes.clear();
        objBehindBg.clear();
        objPriority.clear();
        x = 0;
    }

    @Override
    public void reset() {
        clear();
    }
}
