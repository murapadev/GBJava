package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gbc.model.HardwareType;
import gbc.model.cartridge.Cartridge;
import gbc.model.cartridge.CartridgeFactory;
import gbc.model.memory.Memory;

class CgbPixelFifoPriorityTest {

    private Memory memory;
    private FrameBuffer frameBuffer;
    private CgbPixelFifo fifo;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        memory.setHardwareType(HardwareType.CGB);
        memory.loadCartridge(createCgbCartridge());
        memory.reset();

        frameBuffer = new FrameBuffer();
        fifo = new CgbPixelFifo(frameBuffer, memory);

        // OBJ palette 0, color 1 -> red (0x001F).
        writeObjColor(0, 1, 0x001F);
        // OBJ palette 0, color 2 -> green (0x03E0).
        writeObjColor(0, 2, 0x03E0);
        // OBJ palette 1, color 1 -> green (0x03E0).
        writeObjColor(1, 1, 0x03E0);
        // BG palette 0, color 1 -> blue (0x7C00).
        writeBgColor(0, 1, 0x7C00);
    }

    @Test
    void overlappingSpritesUseLowerOamIndexInCgbMode() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));

        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 1); // palette 0, lower priority
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x01), 0); // palette 1, higher priority

        fifo.putPixelToScreen();

        assertEquals(0xFF00FF00, frameBuffer.getPixel(0, 0), "Lower OAM index sprite should win in CGB mode");
    }

    @Test
    void bgPriorityBitOverridesObjWhenBgPixelIsNonZero() {
        fifo.enqueue8Pixels(lineOf(1), new TileAttributes(0x80)); // BG priority bit set
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x00), 0);

        fifo.putPixelToScreen();

        assertEquals(0xFF0000FF, frameBuffer.getPixel(0, 0), "BG priority bit should hide OBJ");
    }

    @Test
    void objBehindFlagPlacesObjBehindNonZeroBg() {
        fifo.enqueue8Pixels(lineOf(1), new TileAttributes(0x00));
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x80), 0); // OBJ behind BG

        fifo.putPixelToScreen();

        assertEquals(0xFF0000FF, frameBuffer.getPixel(0, 0), "OBJ behind flag should let BG win");
    }

    @Test
    void bgColorZeroStillLetsObjShowEvenWithPriorityBits() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0x80)); // BG priority set but color zero
        fifo.setOverlay(lineOf(1), 0, new TileAttributes(0x80), 0); // OBJ behind flag set

        fifo.putPixelToScreen();

        assertEquals(0xFFFF0000, frameBuffer.getPixel(0, 0), "BG color 0 should remain transparent to OBJ");
    }

    @Test
    void lateFlippedSpriteClipsRightEdgeInsteadOfLeadingEdge() {
        fifo.enqueue8Pixels(lineOf(0), new TileAttributes(0));
        int[] spriteLine = new int[] { 1, 1, 1, 1, 1, 1, 1, 2 };

        // offset=1 means one pixel has already been consumed when the sprite is merged.
        fifo.setOverlay(spriteLine, 1, new TileAttributes(0x20), 0);

        for (int i = 0; i < 7; i++) {
            fifo.putPixelToScreen();
        }

        assertEquals(0xFFFF0000, frameBuffer.getPixel(6, 0),
                "X-flipped late sprites should keep their leading edge");
    }

    private void writeBgColor(int palette, int colorId, int bgr555) {
        int index = (palette * 8) + (colorId * 2);
        memory.writeByte(0xFF68, 0x80 | (index & 0x3F)); // BGPI auto-increment
        memory.writeByte(0xFF69, bgr555 & 0xFF);
        memory.writeByte(0xFF69, (bgr555 >> 8) & 0xFF);
    }

    private void writeObjColor(int palette, int colorId, int bgr555) {
        int index = (palette * 8) + (colorId * 2);
        memory.writeByte(0xFF6A, 0x80 | (index & 0x3F)); // OBPI auto-increment
        memory.writeByte(0xFF6B, bgr555 & 0xFF);
        memory.writeByte(0xFF6B, (bgr555 >> 8) & 0xFF);
    }

    private static int[] lineOf(int pixel) {
        int[] line = new int[8];
        for (int i = 0; i < line.length; i++) {
            line[i] = pixel;
        }
        return line;
    }

    private static Cartridge createCgbCartridge() {
        byte[] rom = new byte[0x8000];
        rom[0x0143] = (byte) 0x80; // CGB compatible
        rom[0x0147] = 0x00;
        rom[0x0148] = 0x00;
        rom[0x0149] = 0x00;
        rom[0x0104] = (byte) 0xCE;
        rom[0x0134] = 'T';
        rom[0x0135] = 'E';
        rom[0x0136] = 'S';
        rom[0x0137] = 'T';
        int checksum = 0;
        for (int i = 0x0134; i <= 0x014C; i++) {
            checksum = checksum - (rom[i] & 0xFF) - 1;
        }
        rom[0x014D] = (byte) (checksum & 0xFF);
        return CartridgeFactory.create(rom);
    }
}
