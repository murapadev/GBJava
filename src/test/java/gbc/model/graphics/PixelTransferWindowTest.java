package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class PixelTransferWindowTest {

    @Test
    void windowStartsAtWxMinusSeven() {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xB1; // LCD on + BG on + window on, window map 0x9800
        memory.ly = 0;
        memory.wy = 0;
        memory.wx = 12; // window start at x=5

        TestFifo fifo = new TestFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, new SpritePosition[0]);

        transfer.start();
        assertEquals(1, fetcher.startFetchingCalls, "Background fetch should start on line begin");

        runTicks(transfer, 5);
        assertEquals(1, fetcher.startFetchingCalls, "Window should not start before x reaches WX-7");

        runTicks(transfer, 1);
        assertEquals(2, fetcher.startFetchingCalls, "Window fetch should start when x reaches WX-7");
        assertEquals(0x9800, fetcher.lastMapAddress, "Window map should match LCDC bit 6");
        assertEquals(0, fetcher.lastXOffset, "Window fetch starts at tile x offset 0");
    }

    @Test
    void windowStartClampsWhenWxIsLessThanSeven() {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xB1;
        memory.ly = 0;
        memory.wy = 0;
        memory.wx = 0; // window start clamps to x=0

        TestFifo fifo = new TestFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, new SpritePosition[0]);

        transfer.start();
        runTicks(transfer, 1);

        assertEquals(2, fetcher.startFetchingCalls, "Window fetch should start on first tick when WX<7");
    }

    @Test
    void wxLessThanSevenDropsInitialWindowPixels() {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xB1;
        memory.ly = 0;
        memory.wy = 0;
        memory.wx = 0; // Window starts 7 pixels off-screen to the left

        CountingFifo fifo = new CountingFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, new SpritePosition[0]);

        transfer.start();
        runTicks(transfer, 7);
        assertEquals(0, transfer.getCurrentX(), "Visible X should not advance while hidden window pixels are dropped");
        assertEquals(7, fifo.dropCount, "WX=0 should drop 7 hidden window pixels");

        runTicks(transfer, 1);
        assertEquals(1, transfer.getCurrentX(), "After dropping hidden pixels, the first visible window pixel should render");
        assertEquals(1, fifo.putCount, "One visible pixel should be pushed after initial drops");
    }

    @Test
    void windowLineResetsWhenLyMovesBackwards() throws Exception {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xB1;
        memory.wy = 0;
        memory.wx = 7; // window start at x=0

        TestFifo fifo = new TestFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, new SpritePosition[0]);

        memory.ly = 10;
        transfer.start();
        runTicks(transfer, 1); // Use window on this line

        memory.ly = 11;
        transfer.start();
        assertEquals(1, getWindowLine(transfer), "Window line should advance after previous line used window");

        memory.ly = 5; // Backwards jump (LCD reset/disable->enable style behavior)
        transfer.start();
        assertEquals(0, getWindowLine(transfer), "Window line should reset when LY jumps backwards");
    }

    private static void runTicks(PixelTransfer transfer, int count) {
        for (int i = 0; i < count; i++) {
            transfer.tick();
        }
    }

    private static int getWindowLine(PixelTransfer transfer) throws Exception {
        Field field = PixelTransfer.class.getDeclaredField("windowLine");
        field.setAccessible(true);
        return field.getInt(transfer);
    }

    private static final class TestMemory extends Memory {
        int lcdc = 0x91;
        int ly;
        int wy;
        int wx;
        int scx;

        @Override
        public int getLcdc() {
            return lcdc & 0xFF;
        }

        @Override
        public int getLy() {
            return ly & 0xFF;
        }

        @Override
        public int getWy() {
            return wy & 0xFF;
        }

        @Override
        public int getWx() {
            return wx & 0xFF;
        }

        @Override
        public int getScx() {
            return scx & 0xFF;
        }
    }

    private static class TestFifo implements PixelFifo {
        @Override
        public int getLength() {
            return 16;
        }

        @Override
        public void putPixelToScreen() {
            // no-op
        }

        @Override
        public void dropPixel() {
            // no-op
        }

        @Override
        public void enqueue8Pixels(int[] pixelLine, TileAttributes tileAttributes) {
            // no-op
        }

        @Override
        public void setOverlay(int[] pixelLine, int offset, TileAttributes tileAttributes, int oamIndex) {
            // no-op
        }

        @Override
        public void clear() {
            // no-op
        }

        @Override
        public void reset() {
            // no-op
        }
    }

    private static final class CountingFifo extends TestFifo {
        int dropCount;
        int putCount;

        @Override
        public void putPixelToScreen() {
            putCount++;
        }

        @Override
        public void dropPixel() {
            dropCount++;
        }
    }

    private static final class RecordingFetcher extends Fetcher {
        int startFetchingCalls;
        int lastMapAddress;
        int lastXOffset;

        RecordingFetcher(PixelFifo fifo, Memory memory) {
            super(fifo, memory);
        }

        @Override
        public void init() {
            // no-op
        }

        @Override
        public void tick() {
            // no-op
        }

        @Override
        public void startFetching(int mapAddress, int tileDataAddress, int xOffset, boolean tileIdSigned, int tileLine) {
            startFetchingCalls++;
            lastMapAddress = mapAddress;
            lastXOffset = xOffset;
        }

        @Override
        public void startFetching(int mapAddress, int tileDataAddress, int xOffset,
                boolean tileIdSigned, int tileLine, boolean useScx, boolean useScy) {
            startFetchingCalls++;
            lastMapAddress = mapAddress;
            lastXOffset = xOffset;
        }

        @Override
        public boolean spriteInProgress() {
            return false;
        }

        @Override
        public boolean isFetchingDisabled() {
            return false;
        }
    }
}
