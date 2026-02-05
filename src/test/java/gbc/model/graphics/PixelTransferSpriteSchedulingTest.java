package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class PixelTransferSpriteSchedulingTest {

    @Test
    void cgbSchedulingUsesLeftmostSpriteBeforeLowerOamIndex() {
        TestMemory memory = new TestMemory(true);
        TestFifo fifo = new TestFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        SpritePosition[] sprites = createSprites();

        // Lower OAM index but further right.
        sprites[0].enable(8, 0, 0xFE00, 0, 0, 0);
        // Higher OAM index but leftmost on screen.
        sprites[1].enable(6, 0, 0xFE04, 1, 0, 0);

        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, sprites);
        transfer.start();
        transfer.tick();

        assertEquals(1, fetcher.addCalls, "One sprite should be scheduled");
        assertEquals(6, fetcher.lastAddedX, "Sprite scheduling should follow scanline X order");
        assertEquals(1, fetcher.lastAddedOamIndex, "Leftmost sprite should be fetched first in CGB mode");
    }

    @Test
    void schedulingBreaksEqualXWithLowerOamIndex() {
        TestMemory memory = new TestMemory(false);
        TestFifo fifo = new TestFifo();
        RecordingFetcher fetcher = new RecordingFetcher(fifo, memory);
        SpritePosition[] sprites = createSprites();

        sprites[0].enable(6, 0, 0xFE00, 3, 0, 0);
        sprites[1].enable(6, 0, 0xFE04, 1, 0, 0);

        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, sprites);
        transfer.start();
        transfer.tick();

        assertEquals(1, fetcher.addCalls, "One sprite should be scheduled");
        assertEquals(6, fetcher.lastAddedX, "Both sprites share the same X");
        assertEquals(1, fetcher.lastAddedOamIndex, "Lower OAM index should win X tie");
    }

    @Test
    void spriteFetchStallsPixelOutputUntilOverlayIsReady() {
        TestMemory memory = new TestMemory(true);
        CountingFifo fifo = new CountingFifo();
        StallingFetcher fetcher = new StallingFetcher(fifo, memory);

        PixelTransfer transfer = new PixelTransfer(fifo, fetcher, memory, new SpritePosition[0]);
        transfer.start();

        boolean continueTransfer = transfer.tick();

        assertTrue(continueTransfer, "Transfer should keep running while sprite fetch is in progress");
        assertEquals(0, transfer.getCurrentX(), "Visible X should not advance during sprite fetch stall");
        assertEquals(0, fifo.putCount, "No pixel should be pushed while sprite fetch is in progress");
    }

    private static SpritePosition[] createSprites() {
        SpritePosition[] sprites = new SpritePosition[2];
        sprites[0] = new SpritePosition();
        sprites[1] = new SpritePosition();
        return sprites;
    }

    private static final class TestMemory extends Memory {
        private final boolean cgbMode;

        TestMemory(boolean cgbMode) {
            this.cgbMode = cgbMode;
        }

        @Override
        public boolean isCgbMode() {
            return cgbMode;
        }

        @Override
        public int getLcdc() {
            return 0x83; // LCD on, BG on, sprites on.
        }

        @Override
        public int getLy() {
            return 0;
        }

        @Override
        public int getScx() {
            return 0;
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
        public void setOverlay(int[] pixelLine, int offset, TileAttributes spriteAttributes, int oamIndex) {
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
        int putCount;

        @Override
        public void putPixelToScreen() {
            putCount++;
        }
    }

    private static final class RecordingFetcher extends Fetcher {
        int addCalls;
        int lastAddedX = Integer.MIN_VALUE;
        int lastAddedOamIndex = Integer.MIN_VALUE;

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
            // no-op
        }

        @Override
        public void addSprite(SpritePosition sprite, int offset, int oamIndex) {
            addCalls++;
            lastAddedX = sprite.getX();
            lastAddedOamIndex = oamIndex;
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

    private static final class StallingFetcher extends Fetcher {
        StallingFetcher(PixelFifo fifo, Memory memory) {
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
            // no-op
        }

        @Override
        public boolean spriteInProgress() {
            return true;
        }

        @Override
        public boolean isFetchingDisabled() {
            return false;
        }
    }
}
