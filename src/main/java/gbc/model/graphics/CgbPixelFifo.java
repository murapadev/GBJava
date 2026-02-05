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
    private final FrameBuffer frameBuffer;
    private int x;

    public CgbPixelFifo(FrameBuffer frameBuffer, Memory memory) {
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
        int bgPrio = bgPriority.dequeue();
        int objPixel = objPixels.dequeue();
        int objPalette = objPalettes.dequeue();
        int objBehind = objBehindBg.dequeue();
        objPriority.dequeue();

        int y = memory.getLy();
        if (x < 160 && y < 144) {
            int color = resolveColor(bgPixel, bgPalette, bgPrio, objPixel, objPalette, objBehind);
            frameBuffer.setPixel(x, y, color);
        }
        x++;
    }

    private int resolveColor(int bgPixel, int bgPalette, int bgPrio, int objPixel, int objPalette, int objBehind) {
        boolean masterPriority = (memory.getLcdc() & 0x01) != 0;
        if (objPixel == 0) {
            return getCgbBgColor(bgPalette, bgPixel);
        }
        if (!masterPriority) {
            return getCgbObjColor(objPalette, objPixel);
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
        // TODO: Verify CGB priority rules (BG priority bit vs OBJ priority) against hardware tests.
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
        int rr = (r << 3) | (r >> 2);
        int gg = (g << 3) | (g >> 2);
        int bb = (b << 3) | (b >> 2);
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
        boolean xflip = spriteAttributes != null && spriteAttributes.isXflip();
        int palette = spriteAttributes != null ? spriteAttributes.getPalette() : 0;
        int priorityKey = oamIndex & 0xFF; // CGB OBJ priority: lower OAM index wins

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
                    continue;
                }
                objPixels.set(fifoIndex, p);
                objPalettes.set(fifoIndex, palette);
                objBehindBg.set(fifoIndex, priority ? 1 : 0);
                objPriority.set(fifoIndex, priorityKey);
                continue;
            }
            objPixels.set(fifoIndex, p);
            objPalettes.set(fifoIndex, palette);
            objBehindBg.set(fifoIndex, priority ? 1 : 0);
            objPriority.set(fifoIndex, priorityKey);
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
