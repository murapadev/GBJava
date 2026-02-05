package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

public class PixelTransferMultiLineTest {

    private TestMemory memory;
    private TestFrameBuffer frameBuffer;
    private DmgPixelFifo fifo;
    private Fetcher fetcher;
    private PixelTransfer pixelTransfer;
    private SpritePosition[] sprites;

    @BeforeEach
    public void setup() {
        memory = new TestMemory();
        frameBuffer = new TestFrameBuffer();
        fifo = new DmgPixelFifo(frameBuffer, memory);
        fetcher = new Fetcher(fifo, memory);
        sprites = new SpritePosition[0];
        pixelTransfer = new PixelTransfer(fifo, fetcher, memory, sprites);
        memory.setBgp(0xE4); // Standard Palette
    }

    @Test
    public void testTwoLinesRendering() {
        // Setup for Line 0
        memory.setLy(0);
        memory.setScx(0);

        // Emulate Start of Line 0 (normally done by PPU)
        pixelTransfer.reset();          // THIS IS CRITICAL - PPU MUST CALL THIS
        fetcher.start(0);

        // Run Line 0
        runLine();

        System.out.println("Line 0 Drawn pixels: " + frameBuffer.drawnPixels);
        assertEquals(160, frameBuffer.drawnPixels, "Line 0 should draw 160 pixels");

        // Setup for Line 1
        memory.setLy(1);
        frameBuffer.drawnPixels = 0; // Reset counter for test

        // Emulate Start of Line 1
        pixelTransfer.reset();          // THIS IS CRITICAL
        fetcher.start(1);

        // Run Line 1
        runLine();

        assertEquals(160, frameBuffer.drawnPixels, "Line 1 should draw 160 pixels (requires reset of x)");
    }

    private void runLine() {
        int cycles = 0;
        int maxCycles = 2000;
        // pixelTransfer.tick() returns true while x < 160.
        // We simulate PPU calling tick() many times.
        while (cycles < maxCycles) {
            boolean active = pixelTransfer.tick();
            if (!active) break;
            cycles++;
        }
    }

    // --- Stubs ---
    static class TestMemory extends Memory {
        private int scx=0, bgp=0, ly=0;
        @Override public int getScx() { return scx; }
        public void setScx(int v) { scx = v; }
        @Override public int getBgp() { return bgp; }
        public void setBgp(int v) { bgp = v; }
        @Override public int getLy() { return ly; }
        public void setLy(int v) { ly = v; }

        @Override
        public int readByte(int address) {
            if (address >= 0x9800 && address < 0xA000) return 0; // Tile 0
            if (address >= 0x8000 && address < 0x9000) return 0xFF; // Solid color
            return 0;
        }
    }

    static class TestFrameBuffer extends FrameBuffer {
        public int drawnPixels = 0;
        @Override
        public void setPixel(int x, int y, int color) {
            if (x >= 0 && x < 160) drawnPixels++;
        }
    }
}
