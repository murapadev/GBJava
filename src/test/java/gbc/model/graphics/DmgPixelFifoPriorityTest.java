package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class DmgPixelFifoPriorityTest {

    private Memory memory;
    private FrameBuffer frameBuffer;
    private DmgPixelFifo fifo;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        frameBuffer = new FrameBuffer();
        fifo = new DmgPixelFifo(frameBuffer, memory);

        // Identity DMG palettes: color-id 0..3 map to shades 0..3.
        memory.writeByte(0xFF47, 0xE4); // BGP
        memory.writeByte(0xFF48, 0xE4); // OBP0
        memory.writeByte(0xFF49, 0xE4); // OBP1

        // Default sprite attributes in OAM (kept for legacy path coverage).
        memory.writeByte(0xFE03, 0x00);
    }

    @Test
    void spriteInFrontOverridesNonZeroBackground() {
        fifo.enqueue8Pixels(lineOf(2), new TileAttributes(0));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 0);

        fifo.putPixelToScreen();

        assertEquals(0xFFC0C0C0, frameBuffer.getPixel(0, 0), "OBJ pixel should be visible in front of BG");
    }

    @Test
    void spriteBehindBackgroundLosesWhenBackgroundIsNonZero() {
        fifo.enqueue8Pixels(lineOf(2), new TileAttributes(0));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x80), 0); // OBJ behind BG

        fifo.putPixelToScreen();

        assertEquals(0xFF606060, frameBuffer.getPixel(0, 0), "BG pixel should win when OBJ is behind");
    }

    @Test
    void spriteBehindBackgroundStillShowsWhenBackgroundIsZero() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x80), 0); // OBJ behind BG

        fifo.putPixelToScreen();

        assertEquals(0xFFC0C0C0, frameBuffer.getPixel(0, 0), "OBJ should show when BG pixel is transparent");
    }

    @Test
    void overlappingSpritesWithSameXUseLowerOamIndex() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        fifo.setOverlay(lineOf(2), 0, new TileAttributes(0x00), 1, 20); // lower priority
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 0, 20); // higher priority

        fifo.putPixelToScreen();

        assertEquals(0xFFC0C0C0, frameBuffer.getPixel(0, 0), "Lower OAM index sprite should win at same X");
    }

    @Test
    void overlappingSpritesUseLowerXBeforeOamIndex() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 0, 24);
        fifo.setOverlay(lineOf(2), 0, new TileAttributes(0x00), 1, 16);

        fifo.putPixelToScreen();

        assertEquals(0xFF606060, frameBuffer.getPixel(0, 0), "Lower X sprite should win over higher X sprite");
    }

    @Test
    void spriteUsesObp1WhenAttributeBit4IsSetInLatchedAttributes() {
        memory.writeByte(0xFF48, 0xE4); // OBP0: color1 -> light gray
        memory.writeByte(0xFF49, 0x0C); // OBP1: color1 -> black
        memory.writeByte(0xFE03, 0x00); // Keep live OAM at OBP0 to verify latching

        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x10), 0, 8);

        fifo.putPixelToScreen();

        assertEquals(0xFF000000, frameBuffer.getPixel(0, 0), "Sprite should use OBP1 from latched attributes");
    }

    @Test
    void latchedSpriteXIsUsedForPriorityInsteadOfLiveOamX() {
        // Live OAM values disagree with latched positions on purpose.
        memory.writeByte(0xFE01, 32); // Sprite 0 live X=24
        memory.writeByte(0xFE05, 24); // Sprite 1 live X=16

        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        // Latched ordering says sprite 0 is leftmost and should keep priority.
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 0, 16);
        fifo.setOverlay(lineOf(2), 0, new TileAttributes(0x00), 1, 24);

        fifo.putPixelToScreen();

        assertEquals(0xFFC0C0C0, frameBuffer.getPixel(0, 0), "Priority must follow latched sprite X");
    }

    private static int[] lineOf(int pixel) {
        int[] line = new int[8];
        for (int i = 0; i < line.length; i++) {
            line[i] = pixel;
        }
        return line;
    }
}
