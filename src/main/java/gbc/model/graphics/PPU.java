package gbc.model.graphics;

import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.memory.Memory;

/**
 * Pixel Processing Unit for Game Boy / Game Boy Color.
 *
 * <p>Architecture: Uses a FIFO-based pixel pipeline with separate
 * DMG ({@link DmgPixelFifo}) and CGB ({@link CgbPixelFifo}) renderers.
 * The pipeline is rebuilt when CGB mode changes via {@link #rebuildPipeline()}.
 *
 * <p>Mode 3 timing is dynamically calculated per-scanline based on SCX
 * fine-scroll penalty, window activation, and per-sprite fetch penalties.
 * The {@link Fetcher} handles sprite fetch timing constraints including
 * a 6-dot minimum penalty per sprite and proper BG fetch state save/restore.
 */
public class PPU {
    private static final Logger LOGGER = Logger.getLogger(PPU.class.getName());
    private static final boolean PPU_TRACE = Boolean.getBoolean("gbc.ppu.trace");
    private Memory memory;
    private FrameBuffer frameBuffer;
    private Screen screen;

    private int mode; // Current mode of the PPU
    private int modeClock; // Clock for timing mode changes

    // Pixel FIFO components
    private PixelFifo pixelFifo;
    private Fetcher fetcher;
    private PixelTransfer pixelTransfer;
    private OamSearch oamSearch;

    // Cached state for faster mode checking
    private boolean inMode3;

    public PPU(Memory memory, FrameBuffer frameBuffer, Screen screen) {
        this.memory = memory;
        this.frameBuffer = frameBuffer;
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
        this.inMode3 = false;
        rebuildPipeline();
        this.pixelFifo.reset();
        this.fetcher.reset();
        this.oamSearch.reset();
    }

    public void tick(int cycles) {
        if (PPU_TRACE) {
            LOGGER.log(Level.FINE, () -> "LY=" + memory.getLy() + ", mode=" + (memory.getStat() & 0x03)
                    + ", modeClock=" + modeClock);
        }
        if (!isCartridgeLoaded() || (memory.getLcdc() & 0x80) == 0) {
            return;
        }

        // Fast path using cached mode state
        if (inMode3) {
            int i = 0;
            int end4 = cycles - 3;
            for (; i < end4; i += 4) {
                pixelTransfer.tick();
                pixelTransfer.tick();
                pixelTransfer.tick();
                pixelTransfer.tick();
            }
            for (; i < cycles; i++) {
                pixelTransfer.tick();
            }
        }
    }

    public void enterMode(int newMode, int oldMode) {
        if (oldMode == newMode) {
            return;
        }
        inMode3 = (newMode == 3);
        switch (newMode) {
            case 0: // HBlank
                break;
            case 1: // VBlank
                frameBuffer.swapBuffers();
                screen.render();
                break;
            case 2: // OAM Search
                int currentLine = memory.getLy();

                oamSearch.searchSprites(currentLine);
                pixelFifo.clear();
                pixelTransfer.reset();
                pixelTransfer.setScxLatch(oamSearch.getLatchedScx());
                break;
            case 3: // Pixel Transfer
                pixelTransfer.start();
                break;
        }
    }

    private void rebuildPipeline() {
        if (memory.isCgbMode()) {
            this.pixelFifo = new CgbPixelFifo(frameBuffer, memory);
        } else {
            this.pixelFifo = new DmgPixelFifo(frameBuffer, memory);
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

    public FrameBuffer getFrameBuffer() {
        return this.frameBuffer;
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
        int baseDuration = getBaseMode3Duration();
        int lcdCycle = memory.getLcdCycleCounter();
        int elapsedMode3 = Math.max(0, lcdCycle - 80);
        int transferredPixels = Math.max(0, Math.min(160, getPixelTransferProgress()));
        int remainingPixels = 160 - transferredPixels;
        int dynamicMinimum = elapsedMode3 + remainingPixels;
        int duration = Math.max(baseDuration, dynamicMinimum);
        return Math.min(289, duration);
    }

    protected int getBaseMode3Duration() {
        return oamSearch.getMode3Duration();
    }

    protected int getPixelTransferProgress() {
        return pixelTransfer != null ? pixelTransfer.getCurrentX() : 0;
    }

    public boolean isPixelTransferComplete() {
        return pixelTransfer == null || pixelTransfer.getCurrentX() >= 160;
    }
}
