package gbc.model.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import gbc.model.HardwareType;
import gbc.model.cartridge.Cartridge;
import gbc.model.cartridge.CartridgeFactory;
import gbc.model.memory.Memory;

class CpuInterruptTimingTest {

    @Test
    void eiEnablesImeAfterNextInstruction() {
        byte[] program = {
                (byte) 0xFB, // EI
                (byte) 0x00, // NOP
                (byte) 0x00  // NOP
        };

        Memory memory = new Memory();
        memory.setHardwareType(HardwareType.DMG);
        memory.loadCartridge(createDmgCartridge(program));
        CPU cpu = new CPU(memory);
        cpu.reset();

        memory.writeByte(0xFFFF, 0x01); // IE: VBlank
        memory.writeByte(0xFF0F, 0x01); // IF: VBlank pending

        cpu.executeCycle(); // EI
        assertFalse(cpu.isIme(), "IME should still be disabled immediately after EI");
        assertTrue(cpu.isImePending(), "IME should be pending after EI");

        cpu.executeCycle(); // NOP
        assertTrue(cpu.isIme(), "IME should be enabled after the instruction following EI");
        assertEquals(0x0102, cpu.getRegisters().getPC() & 0xFFFF, "PC should advance after EI + NOP");

        // Interrupt dispatch runs over 5 M-cycles; step until vector is loaded.
        for (int i = 0; i < 8; i++) {
            cpu.executeCycle();
            if ((cpu.getRegisters().getPC() & 0xFFFF) == 0x0040) {
                break;
            }
        }
        assertEquals(0x0040, cpu.getRegisters().getPC() & 0xFFFF, "PC should jump to VBlank vector");
        assertFalse(cpu.isIme(), "IME should be cleared after interrupt dispatch");
    }

    @Test
    void haltBugSkipsPcIncrementOnNextFetch() {
        byte[] program = {
                (byte) 0x76, // HALT
                (byte) 0x04, // INC B
                (byte) 0x00  // NOP
        };

        Memory memory = new Memory();
        memory.setHardwareType(HardwareType.DMG);
        memory.loadCartridge(createDmgCartridge(program));
        CPU cpu = new CPU(memory);
        cpu.reset();

        cpu.getRegisters().setRegister("B", (byte) 0x00);
        cpu.setIme(false);
        memory.writeByte(0xFFFF, 0x01); // IE: VBlank
        memory.writeByte(0xFF0F, 0x01); // IF: VBlank pending

        cpu.executeCycle(); // HALT (halt bug triggered)
        assertEquals(0x0101, cpu.getRegisters().getPC() & 0xFFFF, "PC should advance after HALT");

        cpu.executeCycle(); // INC B, but PC doesn't increment due to halt bug
        assertEquals(1, cpu.getRegisters().getRegister("B") & 0xFF, "INC B should execute");
        assertEquals(0x0101, cpu.getRegisters().getPC() & 0xFFFF, "PC should not increment due to halt bug");

        cpu.executeCycle(); // INC B again
        assertEquals(2, cpu.getRegisters().getRegister("B") & 0xFF, "INC B should execute again");
        assertEquals(0x0102, cpu.getRegisters().getPC() & 0xFFFF, "PC should resume incrementing");
    }

    private static Cartridge createDmgCartridge(byte[] program) {
        byte[] rom = new byte[0x8000];
        int start = 0x0100;
        System.arraycopy(program, 0, rom, start, program.length);

        rom[0x0143] = 0x00; // DMG only
        rom[0x0147] = 0x00; // ROM only
        rom[0x0148] = 0x00; // 32KB ROM
        rom[0x0149] = 0x00; // No RAM
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
