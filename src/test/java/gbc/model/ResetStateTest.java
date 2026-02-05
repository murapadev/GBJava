package gbc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gbc.model.cpu.CPU;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;

class ResetStateTest {

    @Test
    void resetAppliesHardwareProfiles() {
        for (HardwareType type : HardwareType.values()) {
            Memory memory = new Memory();
            memory.setHardwareType(type);
            CPU cpu = new CPU(memory);
            cpu.reset();

            Registers r = cpu.getRegisters();

            switch (type) {
                case DMG -> {
                    assertEquals(0x01, r.getRegister("A") & 0xFF, "DMG A");
                    assertEquals(0xB0, r.getRegister("F") & 0xFF, "DMG F");
                    assertEquals(0x00, r.getRegister("B") & 0xFF, "DMG B");
                    assertEquals(0x13, r.getRegister("C") & 0xFF, "DMG C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "DMG D");
                    assertEquals(0xD8, r.getRegister("E") & 0xFF, "DMG E");
                    assertEquals(0x01, r.getRegister("H") & 0xFF, "DMG H");
                    assertEquals(0x4D, r.getRegister("L") & 0xFF, "DMG L");
                }
                case DMG0 -> {
                    assertEquals(0x01, r.getRegister("A") & 0xFF, "DMG0 A");
                    assertEquals(0x00, r.getRegister("F") & 0xFF, "DMG0 F");
                    assertEquals(0xFF, r.getRegister("B") & 0xFF, "DMG0 B");
                    assertEquals(0x13, r.getRegister("C") & 0xFF, "DMG0 C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "DMG0 D");
                    assertEquals(0xC1, r.getRegister("E") & 0xFF, "DMG0 E");
                    assertEquals(0x84, r.getRegister("H") & 0xFF, "DMG0 H");
                    assertEquals(0x03, r.getRegister("L") & 0xFF, "DMG0 L");
                }
                case MGB -> {
                    assertEquals(0xFF, r.getRegister("A") & 0xFF, "MGB A");
                    assertEquals(0xB0, r.getRegister("F") & 0xFF, "MGB F");
                    assertEquals(0x00, r.getRegister("B") & 0xFF, "MGB B");
                    assertEquals(0x13, r.getRegister("C") & 0xFF, "MGB C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "MGB D");
                    assertEquals(0xD8, r.getRegister("E") & 0xFF, "MGB E");
                    assertEquals(0x01, r.getRegister("H") & 0xFF, "MGB H");
                    assertEquals(0x4D, r.getRegister("L") & 0xFF, "MGB L");
                }
                case SGB -> {
                    assertEquals(0x01, r.getRegister("A") & 0xFF, "SGB A");
                    assertEquals(0x00, r.getRegister("F") & 0xFF, "SGB F");
                    assertEquals(0x00, r.getRegister("B") & 0xFF, "SGB B");
                    assertEquals(0x14, r.getRegister("C") & 0xFF, "SGB C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "SGB D");
                    assertEquals(0x00, r.getRegister("E") & 0xFF, "SGB E");
                    assertEquals(0xC0, r.getRegister("H") & 0xFF, "SGB H");
                    assertEquals(0x60, r.getRegister("L") & 0xFF, "SGB L");
                }
                case SGB2 -> {
                    assertEquals(0xFF, r.getRegister("A") & 0xFF, "SGB2 A");
                    assertEquals(0x00, r.getRegister("F") & 0xFF, "SGB2 F");
                    assertEquals(0x00, r.getRegister("B") & 0xFF, "SGB2 B");
                    assertEquals(0x14, r.getRegister("C") & 0xFF, "SGB2 C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "SGB2 D");
                    assertEquals(0x00, r.getRegister("E") & 0xFF, "SGB2 E");
                    assertEquals(0xC0, r.getRegister("H") & 0xFF, "SGB2 H");
                    assertEquals(0x60, r.getRegister("L") & 0xFF, "SGB2 L");
                }
                case CGB -> {
                    assertEquals(0x11, r.getRegister("A") & 0xFF, "CGB A");
                    assertEquals(0x80, r.getRegister("F") & 0xFF, "CGB F");
                    assertEquals(0x00, r.getRegister("B") & 0xFF, "CGB B");
                    assertEquals(0x00, r.getRegister("C") & 0xFF, "CGB C");
                    assertEquals(0x00, r.getRegister("D") & 0xFF, "CGB D");
                    assertEquals(0x08, r.getRegister("E") & 0xFF, "CGB E");
                    assertEquals(0x00, r.getRegister("H") & 0xFF, "CGB H");
                    assertEquals(0x7C, r.getRegister("L") & 0xFF, "CGB L");
                }
            }

            assertEquals(0xFFFE, r.getSP() & 0xFFFF, type + " SP");
            assertEquals(0x0100, r.getPC() & 0xFFFF, type + " PC");

            int divider = memory.getTimer().getDividerRaw() & 0xFFFF;
            assertEquals(type.getInitialDivCounter() & 0xFFFF, divider, type + " DIV raw");
            assertEquals((type.getInitialDivCounter() >> 8) & 0xFF, memory.readByte(0xFF04) & 0xFF, type + " DIV reg");

            assertEquals(0xE1, memory.readByte(0xFF0F) & 0xFF, type + " IF default");
            assertEquals(type.getInitialJoypadRegister() & 0xFF, memory.readByte(0xFF00) & 0xFF, type + " JOYP default");
            assertEquals(0xFF, memory.readByte(0xFF46) & 0xFF, type + " DMA reg default");
        }
    }
}
