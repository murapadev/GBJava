package gbc.model.graphics;

import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.memory.Memory;

public class PPU {
    private static final Logger LOGGER = Logger.getLogger(PPU.class.getName());
    private Memory memory;
    private Screen screen;

    private int mode; // Current mode of the PPU
    private int modeClock; // Clock for timing mode changes

    // Pixel FIFO components
    private PixelFifo pixelFifo;
    private Fetcher fetcher;
    private PixelTransfer pixelTransfer;
    private OamSearch oamSearch;

    public PPU(Memory memory, Screen screen) {
        this.memory = memory;
        this.screen = screen;
        this.mode = 2; // Start in OAM mode
        this.modeClock = 0;

        // Initialize pixel FIFO system
        this.oamSearch = new OamSearch(memory);
        rebuildPipeline();
    }

    public void reset() {
        this.mode = 2; // Start in OAM mode
        this.modeClock = 0;
        rebuildPipeline();
        this.pixelFifo.reset();
        this.fetcher.reset();
        this.oamSearch.reset();
    }

    public void tick(int cycles) {
        if (Boolean.getBoolean("gbc.ppu.trace")) {
            LOGGER.log(Level.FINE, () -> "LY=" + memory.getLy() + ", mode=" + (memory.getStat() & 0x03)
                    + ", modeClock=" + modeClock);
        }
        if (!isCartridgeLoaded() || (memory.getLcdc() & 0x80) == 0) {
            return;
        }

        // The mode is now managed by the Memory class. We just react to it.
        int currentMode = memory.getStat() & 0x03;

        if (currentMode == 3) { // Pixel Transfer mode
            for (int i = 0; i < cycles; i++) {
                pixelTransfer.tick();
            }
        }
    }

    public void enterMode(int newMode, int oldMode) {
        if (oldMode == newMode) {
            return;
        }
        switch (newMode) {
            case 0: // HBlank
                // HBlank actions can be added here if needed
                break;
            case 1: // VBlank
                // VBlank is the time to render the frame to the screen
                screen.render();
                break;
            case 2: // OAM Search
                int currentLine = memory.getLy();

                oamSearch.searchSprites(currentLine);
                pixelFifo.clear();
                pixelTransfer.reset();
                break;
            case 3: // Pixel Transfer
                pixelTransfer.start();
                break;
        }
    }

    private void rebuildPipeline() {
        if (memory.isCgbMode()) {
            this.pixelFifo = new CgbPixelFifo(screen, memory);
        } else {
            this.pixelFifo = new DmgPixelFifo(screen, memory);
        }
        this.fetcher = new Fetcher(pixelFifo, memory);
        this.pixelTransfer = new PixelTransfer(pixelFifo, fetcher, memory, oamSearch.getSprites());
    }

    private boolean isCartridgeLoaded() {
        return memory.isCartridgeLoaded();
    }

    public Screen getScreen() {
        return this.screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
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

    public void updateGraphics() {
        screen.render();
    }
    
    /**
     * Returns the calculated Mode 3 duration for the current scanline.
     * This varies based on SCX value and number of sprites found during OAM search.
     * Used by Memory for accurate LCD timing.
     */
    public int getMode3Duration() {
        return oamSearch.getMode3Duration();
    }
}
