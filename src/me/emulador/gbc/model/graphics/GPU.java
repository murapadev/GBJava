package me.emulador.gbc.model.graphics;

import me.emulador.gbc.model.cpu.Interruptions;

public class GPU {
    // Variables relacionadas con la GPU
    private Screen screen;
    private Interruptions interrupts;
    private int[] tileset;
    private int[] bgMap;
    private int[] oam;

    private int scanlineCounter;
    private int mode;
    private int modeClock;

    public GPU(Interruptions interrupts) {
        screen = new Screen();
        tileset = new int[384 * 8 * 8];
        bgMap = new int[32 * 32];
        oam = new int[40 * 4];
        scanlineCounter = 0;
        mode = 0;
        modeClock = 0;
        this.interrupts = interrupts;
    }

    public void renderScanline() {
        if (scanlineCounter < 144) {
            // Draw a horizontal line on the screen
            int[] line = new int[160];
            for (int i = 0; i < 160; i++) {
                line[i] = (i % 2 == 0) ? 0xFFFFFF : 0x000000;
            }
            screen.drawScanline(line, scanlineCounter);
        }
        scanlineCounter++;
        if (scanlineCounter == 144) {
            // Vertical blanking period
            mode = 1;
            interrupts.vblank();

        } else if (scanlineCounter < 153) {
            // Non-visible scanlines
            mode = 2;
        } else {
            // Restart scanning modes
            mode = 0;
            scanlineCounter = 0;
        }
    }

    public void update(int cycles) {
        modeClock += cycles;
        switch (mode) {
            case 0:
                if (modeClock >= 204) {
                    // Enter scanline mode 2
                    modeClock = 0;
                    mode = 2;
                }
                break;
            case 1:
                if (modeClock >= 456) {
                    // Enter scanline mode 2
                    modeClock = 0;
                    mode = 2;
                }
                break;
            case 2:
                if (modeClock >= 80) {
                    // Enter scanline mode 3
                    modeClock = 0;
                    mode = 3;
                }
                break;
            case 3:
                if (modeClock >= 172) {
                    // Enter scanline mode 0
                    modeClock = 0;
                    renderScanline();
                }
                break;
        }
    }

    public void draw() {
        screen.draw();
    }

    public Screen getScreen() {
        return screen;
    }

    public int[] getTileset() {
        return tileset;
    }

    public int[] getBgMap() {
        return bgMap;
    }

    public int[] getOam() {
        return oam;
    }

    public int getScanlineCounter() {
        return scanlineCounter;
    }

    public int getMode() {
        return mode;
    }

    public int getModeClock() {
        return modeClock;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public void setTileset(int[] tileset) {
        this.tileset = tileset;
    }

    public void setBgMap(int[] bgMap) {
        this.bgMap = bgMap;
    }

    public void setOam(int[] oam) {
        this.oam = oam;
    }

    public void setScanlineCounter(int scanlineCounter) {
        this.scanlineCounter = scanlineCounter;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setModeClock(int modeClock) {
        this.modeClock = modeClock;
    }

    public Interruptions getInterrupts() {
        return interrupts;
    }

    public void setInterrupts(Interruptions interrupts) {
        this.interrupts = interrupts;
    }
    
}
