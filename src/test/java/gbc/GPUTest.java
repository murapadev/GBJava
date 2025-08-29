package gbc;

import gbc.model.graphics.GPU;
import gbc.model.graphics.Screen;
import gbc.model.memory.Memory;
import gbc.model.cartridge.ROM;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GPUTest {

    @Test
    public void testBackgroundRenderSolidTile() {
        Memory memory = new Memory();

        // Load a small dummy ROM (empty) to satisfy cartridge reads
        byte[] romData = new byte[0x8000];
        ROM rom = new ROM(romData);
        memory.loadCartridge(rom);

        Screen screen = new Screen();
        GPU gpu = new GPU(memory, screen);

        // Enable LCD (0xFF40 bit7), enable BG (bit0), set BG map at 0x9800 (bit3=0), tile data unsigned (bit4=1)
        memory.writeByte(0xFF40, 0x80 | 0x01 | 0x10);
        // Reset scroll
        memory.writeByte(0xFF42, 0);
        memory.writeByte(0xFF43, 0);
        // Default palette BGP = 0b00_01_10_11 -> just use default mapping
        memory.writeByte(0xFF47, 0b11100100);

        // Create a tile at tile index 0 with black pixels: each tile line two bytes representing color 3 (11)
        // For each of 8 rows, low byte = 0xFF, high byte = 0xFF -> color 3 for all 8 pixels
        int tileAddr = 0x8000; // unsigned addressing
        for (int row = 0; row < 8; row++) {
            memory.writeByte(tileAddr + row * 2, 0xFF);
            memory.writeByte(tileAddr + row * 2 + 1, 0xFF);
        }

        // Set BG map entry 0 to tile 0
        memory.writeByte(0x9800, 0x00);

        // Reset LY
        memory.writeByte(0xFF44, 0);

        // Trigger a frame render
        gpu.updateGraphics();

        BufferedImage img = screen.getImage();
        int rgb = img.getRGB(0, 0);

        // Expect top-left pixel to match color index 3 (black) -> our grays[3] = 0x000000
        assertEquals(0xFF000000 | 0x000000, rgb);
    }
}
