package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gbc.model.HardwareType;
import gbc.model.cartridge.Cartridge;
import gbc.model.cartridge.CartridgeFactory;
import gbc.model.memory.Memory;

class DmgCgbPaletteDefaultsTest {

    @Test
    void defaultDmgOnCgbPaletteUsesCommonReferenceColors() {
        Memory memory = new Memory();
        memory.setHardwareType(HardwareType.CGB);
        memory.loadCartridge(createDmgCartridge());
        memory.reset();

        assertTrue(memory.isDmgOnCgb(), "Expected DMG compatibility mode on CGB hardware");

        // BG shades: #FFFFFF, #7BFF31, #0063C6, #000000 (in DMG shade order 0..3).
        assertEquals(0xFFFFFFFF, memory.resolveDmgCgbColor(false, 0, 0));
        assertEquals(0xFF7BFF31, memory.resolveDmgCgbColor(false, 0, 1));
        assertEquals(0xFF0063C6, memory.resolveDmgCgbColor(false, 0, 2));
        assertEquals(0xFF000000, memory.resolveDmgCgbColor(false, 0, 3));

        // OBJ shades: #FFFFFF, #FF8484, #943939, #000000 (in DMG shade order 0..3).
        assertEquals(0xFFFFFFFF, memory.resolveDmgCgbColor(true, 0, 0));
        assertEquals(0xFFFF8484, memory.resolveDmgCgbColor(true, 0, 1));
        assertEquals(0xFF943939, memory.resolveDmgCgbColor(true, 0, 2));
        assertEquals(0xFF000000, memory.resolveDmgCgbColor(true, 0, 3));
    }

    private static Cartridge createDmgCartridge() {
        byte[] rom = new byte[0x8000];
        rom[0x0143] = 0x00; // DMG only
        rom[0x0147] = 0x00;
        rom[0x0148] = 0x00;
        rom[0x0149] = 0x00;
        rom[0x0104] = (byte) 0xCE; // Header signature seed for lightweight validity
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
