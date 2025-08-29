package gbc.model.graphics;

import gbc.model.memory.Memory;

public class GPU {
    private Memory memory;
    private Screen screen;
    private int[] frameBuffer = new int[160 * 144]; // Frame buffer array

    private int mode; // Current mode of the GPU
    private int modeClock; // Clock for timing mode changes

    public GPU(Memory memory, Screen screen) {
        this.memory = memory;
        this.screen = screen;
        this.mode = 2; // Start in OAM mode
        this.modeClock = 0;
    }

    public void step(int cycles) {
        modeClock += cycles;
        switch (mode) {
            case 2 -> { // OAM mode
                if (modeClock >= 80) {
                    modeClock = 0;
                    mode = 3; // Switch to VRAM mode
                }
            }
            case 3 -> { // VRAM mode
                if (modeClock >= 172) {
                    modeClock = 0;
                    mode = 0; // Switch to HBlank
                    updateLine();
                }
            }
            case 0 -> { // HBlank
                if (modeClock >= 204) {
                    modeClock = 0;
                    int currentLine = memory.readByte(0xFF44) & 0xFF;
                    memory.writeByte(0xFF44, (byte) (currentLine + 1));
                    if (currentLine == 143) {
                        mode = 1; // Switch to VBlank
                        // Render screen here
                    } else {
                        mode = 2; // Switch to OAM
                    }
                }
            }

            case 1 -> { // VBlank
                if (modeClock >= 456) {
                    modeClock = 0;
                    int currentLine = memory.readByte(0xFF44) & 0xFF;
                    memory.writeByte(0xFF44, (byte) (currentLine + 1));
                    if (currentLine > 153) {
                        mode = 2; // Switch to OAM
                        memory.writeByte(0xFF44, (byte) 0);
                    }
                }
            }

            default -> {
                // no-op
            }
        }
    }

    /**
     * Render a full frame using background tile map (0x9800/0x9C00) and tile data (0x8000/0x8800).
     * This implementation respects LCDC control bits for tile data/map selection and uses SCX/SCY
     * and BGP (0xFF47) palette mapping. It still ignores sprites and window for simplicity.
     */
    private void renderFrame() {
        final int WIDTH = 160;
        final int HEIGHT = 144;
        final int TILE_WIDTH = 8;
        final int TILE_HEIGHT = 8;

        // Read LCDC and related registers
        int lcdc = memory.readByte(0xFF40) & 0xFF;
        boolean lcdEnabled = (lcdc & 0x80) != 0;
        int scy = memory.readByte(0xFF42) & 0xFF;
        int scx = memory.readByte(0xFF43) & 0xFF;
        int bgPalette = memory.readByte(0xFF47) & 0xFF; // BGP register

        // If LCD is disabled, show a blank (white) screen
        if (!lcdEnabled) {
            int white = 0xFFFFFF;
            for (int i = 0; i < WIDTH * HEIGHT; i++) frameBuffer[i] = white;
            screen.render(frameBuffer);
            return;
        }

        // Determine background map base address (bit 3)
        int bgMapBase = (lcdc & 0x08) != 0 ? 0x9C00 : 0x9800;
        // Determine tile data base (bit 4). If 0 -> signed indexing with base 0x9000; if 1 -> unsigned at 0x8000
        boolean tileDataUnsigned = (lcdc & 0x10) != 0;
        int tileDataBaseUnsigned = 0x8000;
        int tileDataBaseSigned = 0x9000;

        // Greyscale palette for rendering (actual Game Boy uses 4 shades)
        int[] grays = new int[] {0xFFFFFF, 0xC0C0C0, 0x606060, 0x000000};

        for (int y = 0; y < HEIGHT; y++) {
            int bgY = (y + scy) & 0xFF; // wrap-around
            int tileRow = bgY / TILE_HEIGHT;
            int rowInTile = bgY % TILE_HEIGHT;

            for (int x = 0; x < WIDTH; x++) {
                int bgX = (x + scx) & 0xFF;
                int tileCol = bgX / TILE_WIDTH;
                int colInTile = bgX % TILE_WIDTH;

                int mapIndex = tileRow * 32 + tileCol; // 32 tiles per row in BG map
                int mapAddr = bgMapBase + mapIndex;
                int tileNumber = memory.readByte(mapAddr) & 0xFF;

                int tileAddr;
                if (tileDataUnsigned) {
                    tileAddr = tileDataBaseUnsigned + tileNumber * 16;
                } else {
                    // Signed indexing mode: tileNumber is signed - use 0x9000 as base with signed index
                    byte signed = (byte) tileNumber;
                    tileAddr = tileDataBaseSigned + (signed * 16);
                }

                int lowByte = memory.readByte(tileAddr + rowInTile * 2) & 0xFF;
                int highByte = memory.readByte(tileAddr + rowInTile * 2 + 1) & 0xFF;

                int bitIndex = 7 - colInTile; // bit 7 is leftmost pixel
                int lo = (lowByte >> bitIndex) & 0x1;
                int hi = (highByte >> bitIndex) & 0x1;
                int colorIndex = (hi << 1) | lo; // 0..3

                // Map colorIndex through BGP palette (each pair of bits selects color shade)
                int paletteIndex = (bgPalette >> (colorIndex * 2)) & 0x3;
                frameBuffer[y * WIDTH + x] = grays[paletteIndex];
            }
        }

        // Window rendering (if enabled)
        boolean windowEnable = (lcdc & 0x20) != 0; // bit 5
        if (windowEnable) {
            int windowMapBase = (lcdc & 0x40) != 0 ? 0x9C00 : 0x9800; // bit 6
            int wx = (memory.readByte(0xFF4B) & 0xFF) - 7; // WX register at 0xFF4B
            int wy = memory.readByte(0xFF4A) & 0xFF; // WY register at 0xFF4A

            for (int wyPos = 0; wyPos < HEIGHT; wyPos++) {
                int screenY = wyPos;
                if (screenY < wy) continue;
                int windowRow = (wyPos - wy) & 0xFF;
                int rowInTileW = windowRow % TILE_HEIGHT;

                for (int wxPos = 0; wxPos < WIDTH; wxPos++) {
                    int screenX = wxPos;
                    if (screenX < wx) continue;
                    int windowCol = (wxPos - wx) / TILE_WIDTH;
                    int mapIndex = (windowRow / TILE_HEIGHT) * 32 + windowCol;
                    int mapAddr = windowMapBase + mapIndex;
                    int tileNumber = memory.readByte(mapAddr) & 0xFF;

                    int tileAddr;
                    if (tileDataUnsigned) tileAddr = tileDataBaseUnsigned + tileNumber * 16;
                    else tileAddr = tileDataBaseSigned + ((byte) tileNumber) * 16;

                    int lowByte = memory.readByte(tileAddr + rowInTileW * 2) & 0xFF;
                    int highByte = memory.readByte(tileAddr + rowInTileW * 2 + 1) & 0xFF;
                    int colInTile = (wxPos - wx) % TILE_WIDTH;
                    int bitIndex = 7 - colInTile;
                    int lo = (lowByte >> bitIndex) & 0x1;
                    int hi = (highByte >> bitIndex) & 0x1;
                    int colorIndex = (hi << 1) | lo;
                    int paletteIndex = (bgPalette >> (colorIndex * 2)) & 0x3;
                    frameBuffer[screenY * WIDTH + screenX] = grays[paletteIndex];
                }
            }
        }

        // Sprite rendering (simple implementation)
        boolean objEnable = (lcdc & 0x02) != 0; // bit 1
        if (objEnable) {
            int spriteHeight = ((lcdc & 0x04) != 0) ? 16 : 8; // bit 2
            int obp0 = memory.readByte(0xFF48) & 0xFF;
            int obp1 = memory.readByte(0xFF49) & 0xFF;

            // Iterate through 40 sprites in OAM
            for (int i = 0; i < 40; i++) {
                int base = 0xFE00 + i * 4;
                int yPos = (memory.readByte(base) & 0xFF) - 16;
                int xPos = (memory.readByte(base + 1) & 0xFF) - 8;
                int tile = memory.readByte(base + 2) & 0xFF;
                int attr = memory.readByte(base + 3) & 0xFF;

                boolean yFlip = (attr & 0x40) != 0;
                boolean xFlip = (attr & 0x20) != 0;
                boolean priority = (attr & 0x80) != 0; // 1 = behind BG
                int palette = (attr & 0x10) != 0 ? obp1 : obp0;

                int tileIndex = tile;
                if (spriteHeight == 16) tileIndex &= 0xFE;

                for (int sy = 0; sy < spriteHeight; sy++) {
                    int row = yFlip ? (spriteHeight - 1 - sy) : sy;
                    int tileRow = row % 8;
                    int tileAddr = 0x8000 + tileIndex * 16 + tileRow * 2;
                    int low = memory.readByte(tileAddr) & 0xFF;
                    int high = memory.readByte(tileAddr + 1) & 0xFF;

                    int py = yPos + sy;
                    if (py < 0 || py >= HEIGHT) continue;

                    for (int sx = 0; sx < 8; sx++) {
                        int bit = xFlip ? sx : (7 - sx);
                        int lo = (low >> bit) & 0x1;
                        int hi = (high >> bit) & 0x1;
                        int colorIndex = (hi << 1) | lo;
                        if (colorIndex == 0) continue; // transparent

                        int paletteIndex = (palette >> (colorIndex * 2)) & 0x3;
                        int color = grays[paletteIndex];

                        int px = xPos + sx;
                        if (px < 0 || px >= WIDTH) continue;

                        // If priority set and background is non-zero (not white), skip drawing
                        if (priority && frameBuffer[py * WIDTH + px] != grays[0]) continue;

                        frameBuffer[py * WIDTH + px] = color;
                    }
                }
            }
        }

        screen.render(frameBuffer);
    }

    private void updateLine() {
        int currentLine = memory.readByte(0xFF44) & 0xFF;
        if (currentLine < 0 || currentLine >= 144) {
            return;
        }

        // Keep a lightweight scanline fallback: draw a simple checkerboard for now
        int offset = 160 * currentLine;
        for (int i = 0; i < 160; i++) {
            int color = ((currentLine / 8) + (i / 8)) % 2 == 0 ? 0xFFFFFF : 0xAAAAAA;
            frameBuffer[offset + i] = color;
        }

        // We avoid calling screen.render on every scanline; full frame rendering is done elsewhere
    }

    /**
     * Render a full frame using background tile map (0x9800) and tile data (0x8000).
     * This is a simplified renderer: it ignores scrolling, window, sprites, and palettes
     * and assumes unsigned tile indexing and a simple 4-color grayscale mapping.
     */
    private void renderFrameOld() {
        final int WIDTH = 160;
        final int HEIGHT = 144;
        final int TILE_WIDTH = 8;
        final int TILE_HEIGHT = 8;
        final int BG_MAP_ADDR = 0x9800;
        final int TILE_DATA_ADDR = 0x8000; // using unsigned indexing

        // Palette: 0=white, 1=light gray, 2=dark gray, 3=black
        int[] palette = new int[] {0xFFFFFF, 0xC0C0C0, 0x606060, 0x000000};

        // For each pixel in screen, determine tile and pixel within tile
        for (int y = 0; y < HEIGHT; y++) {
            int tileRow = y / TILE_HEIGHT;
            int rowInTile = y % TILE_HEIGHT;
            for (int x = 0; x < WIDTH; x++) {
                int tileCol = x / TILE_WIDTH;
                int colInTile = x % TILE_WIDTH;

                int mapIndex = tileRow * 32 + tileCol; // 32 tiles per row in BG map
                int mapAddr = BG_MAP_ADDR + mapIndex;
                int tileNumber = memory.readByte(mapAddr) & 0xFF;

                int tileAddr = TILE_DATA_ADDR + tileNumber * 16; // 16 bytes per tile
                int lowByte = memory.readByte(tileAddr + rowInTile * 2) & 0xFF;
                int highByte = memory.readByte(tileAddr + rowInTile * 2 + 1) & 0xFF;

                int bitIndex = 7 - colInTile; // bit 7 is leftmost pixel
                int lo = (lowByte >> bitIndex) & 0x1;
                int hi = (highByte >> bitIndex) & 0x1;
                int colorIndex = (hi << 1) | lo; // 0..3

                frameBuffer[y * WIDTH + x] = palette[colorIndex];
            }
        }

        screen.render(frameBuffer);
    }

    public void updateGraphics() {
        // Perform a step for a line (456 cycles worth), then render full frame for now
        this.step(456);
        // Render full frame from VRAM background map to provide a visual output
        try {
            renderFrame();
        } catch (Exception e) {
            // Rendering must not crash emulator; fallback to simple updateLine
            updateLine();
        }
    }

    public Screen getScreen() {
        return this.screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public int[] getFrameBuffer() {
        return this.frameBuffer;
    }

    public void setFrameBuffer(int[] frameBuffer) {
        this.frameBuffer = frameBuffer;
    }

    public int getMode() {
        return this.mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getModeClock() {
        return this.modeClock;
    }

    public void setModeClock(int modeClock) {
        this.modeClock = modeClock;
    }

    public Memory getMemory() {
        return this.memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }
}
