package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class PpuMode3DurationTest {

    @Test
    void extendsMode3WhenTransferProgressLagsBehindElapsedDots() {
        TestMemory memory = new TestMemory();
        TestPpu ppu = new TestPpu(memory, 172, 100);
        memory.lcdCycleCounter = 200; // elapsed mode3 = 120 dots

        int duration = ppu.getMode3Duration();

        // remaining pixels = 60, dynamic minimum = 180
        assertEquals(180, duration);
    }

    @Test
    void keepsBaseDurationWhenDynamicMinimumIsLower() {
        TestMemory memory = new TestMemory();
        TestPpu ppu = new TestPpu(memory, 204, 150);
        memory.lcdCycleCounter = 130; // elapsed mode3 = 50, dynamic minimum = 60

        int duration = ppu.getMode3Duration();

        assertEquals(204, duration);
    }

    @Test
    void mode3DurationIsCappedAtHardwareMaximum() {
        TestMemory memory = new TestMemory();
        TestPpu ppu = new TestPpu(memory, 260, 0);
        memory.lcdCycleCounter = 260; // elapsed mode3 = 180, dynamic minimum = 340

        int duration = ppu.getMode3Duration();

        assertEquals(289, duration);
    }

    private static final class TestMemory extends Memory {
        int lcdCycleCounter;

        @Override
        public int getLcdCycleCounter() {
            return lcdCycleCounter;
        }
    }

    private static final class TestPpu extends PPU {
        private final int baseMode3Duration;
        private final int pixelTransferProgress;

        TestPpu(Memory memory, int baseMode3Duration, int pixelTransferProgress) {
            super(memory, new FrameBuffer(), new Screen());
            this.baseMode3Duration = baseMode3Duration;
            this.pixelTransferProgress = pixelTransferProgress;
        }

        @Override
        protected int getBaseMode3Duration() {
            return baseMode3Duration;
        }

        @Override
        protected int getPixelTransferProgress() {
            return pixelTransferProgress;
        }
    }
}
