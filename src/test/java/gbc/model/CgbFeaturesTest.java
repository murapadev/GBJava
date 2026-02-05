package gbc.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CGB (Game Boy Color) specific features.
 * Tests VRAM banking, WRAM banking, CGB palette registers,
 * HDMA, double speed mode, and other CGB-only behavior.
 */
class CgbFeaturesTest {

    private Memory memory;
    private CPU cpu;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        memory.setHardwareType(HardwareType.CGB);
        cpu = new CPU(memory);
        // Need a CGB-compatible cartridge for CGB mode to activate
        memory.loadCartridge(createCgbCartridge());
        cpu.reset();
    }

    @Nested
    class VramBanking {
        @Test
        void defaultVramBankIsZero() {
            int bank = memory.readByte(0xFF4F) & 0x01;
            assertEquals(0, bank, "Default VRAM bank should be 0");
        }

        @Test
        void writingVramBankSelectsBankOne() {
            memory.writeByte(0xFF4F, 0x01);
            int bank = memory.readByte(0xFF4F) & 0x01;
            assertEquals(1, bank, "VRAM bank should be 1 after writing 1");
        }

        @Test
        void writingVramBankOnlyUsesLowestBit() {
            memory.writeByte(0xFF4F, 0xFE);
            int bank = memory.readByte(0xFF4F) & 0x01;
            assertEquals(0, bank, "VRAM bank should mask to bit 0 only");
        }

        @Test
        void vramBanksAreIndependent() {
            // Disable LCD to allow VRAM writes
            memory.writeByte(0xFF40, 0x00);

            // Write to VRAM bank 0
            memory.writeByte(0xFF4F, 0x00);
            memory.writeByte(0x8000, 0xAA);

            // Write to VRAM bank 1
            memory.writeByte(0xFF4F, 0x01);
            memory.writeByte(0x8000, 0x55);

            // Verify bank 0 value is preserved
            memory.writeByte(0xFF4F, 0x00);
            assertEquals(0xAA, memory.readByte(0x8000) & 0xFF,
                    "VRAM bank 0 should retain value 0xAA");

            // Verify bank 1 value is preserved
            memory.writeByte(0xFF4F, 0x01);
            assertEquals(0x55, memory.readByte(0x8000) & 0xFF,
                    "VRAM bank 1 should retain value 0x55");
        }
    }

    @Nested
    class WramBanking {
        @Test
        void defaultWramBankIsOne() {
            int bank = memory.readByte(0xFF70) & 0x07;
            assertEquals(1, bank, "Default WRAM bank should be 1");
        }

        @Test
        void wramBankZeroMapsToOne() {
            memory.writeByte(0xFF70, 0x00);
            int bank = memory.readByte(0xFF70) & 0x07;
            assertEquals(1, bank, "Writing WRAM bank 0 should map to bank 1");
        }

        @Test
        void wramBankOnlyUsesLow3Bits() {
            memory.writeByte(0xFF70, 0xFD); // 0xFD & 0x07 = 5
            int bank = memory.readByte(0xFF70) & 0x07;
            assertEquals(5, bank, "WRAM bank should mask to 3 bits");
        }

        @Test
        void wramBanksAreIndependent() {
            // Write to bank 1
            memory.writeByte(0xFF70, 0x01);
            memory.writeByte(0xD000, 0x11);

            // Write to bank 2
            memory.writeByte(0xFF70, 0x02);
            memory.writeByte(0xD000, 0x22);

            // Write to bank 7
            memory.writeByte(0xFF70, 0x07);
            memory.writeByte(0xD000, 0x77);

            // Verify bank 1
            memory.writeByte(0xFF70, 0x01);
            assertEquals(0x11, memory.readByte(0xD000) & 0xFF);

            // Verify bank 2
            memory.writeByte(0xFF70, 0x02);
            assertEquals(0x22, memory.readByte(0xD000) & 0xFF);

            // Verify bank 7
            memory.writeByte(0xFF70, 0x07);
            assertEquals(0x77, memory.readByte(0xD000) & 0xFF);
        }

        @Test
        void wramBank0IsFixedAndSharedAcrossBankSelections() {
            // Bank 0 (0xC000-0xCFFF) is always the same
            memory.writeByte(0xFF70, 0x01);
            memory.writeByte(0xC000, 0xAA);

            memory.writeByte(0xFF70, 0x05);
            assertEquals(0xAA, memory.readByte(0xC000) & 0xFF,
                    "WRAM bank 0 (0xC000-0xCFFF) should not change with bank selection");
        }
    }

    @Nested
    class CgbPalettes {
        @Test
        void bgPaletteIndexWriteAndRead() {
            memory.writeByte(0xFF68, 0x80 | 0x10); // Auto-increment, index 0x10
            int bgpi = memory.readByte(0xFF68) & 0xFF;
            assertEquals(0x90, bgpi, "BGPI should return index with auto-inc flag");
        }

        @Test
        void bgPaletteDataAutoIncrements() {
            memory.writeByte(0xFF68, 0x80); // Auto-increment, index 0
            memory.writeByte(0xFF69, 0xFF); // Write palette byte 0
            memory.writeByte(0xFF69, 0x7F); // Write palette byte 1

            // Index should have auto-incremented to 2
            int bgpi = memory.readByte(0xFF68) & 0x3F;
            assertEquals(2, bgpi, "BGPI index should auto-increment after write");
        }

        @Test
        void bgPaletteDataReadback() {
            // Disable LCD for palette access
            memory.writeByte(0xFF40, 0x00);

            memory.writeByte(0xFF68, 0x80); // Auto-increment, index 0
            memory.writeByte(0xFF69, 0xAB); // Write palette byte
            memory.writeByte(0xFF68, 0x00); // Reset index, no auto-inc
            int readBack = memory.readByte(0xFF69) & 0xFF;
            assertEquals(0xAB, readBack, "BG palette data should be readable");
        }

        @Test
        void objPaletteIndexWriteAndRead() {
            memory.writeByte(0xFF6A, 0x80 | 0x04);
            int obpi = memory.readByte(0xFF6A) & 0xFF;
            assertEquals(0x84, obpi, "OBPI should return index with auto-inc flag");
        }

        @Test
        void objPaletteDataAutoIncrements() {
            memory.writeByte(0xFF6A, 0x80); // Auto-increment, index 0
            memory.writeByte(0xFF6B, 0x12);
            memory.writeByte(0xFF6B, 0x34);

            int obpi = memory.readByte(0xFF6A) & 0x3F;
            assertEquals(2, obpi, "OBPI index should auto-increment after write");
        }
    }

    @Nested
    class DoubleSpeedMode {
        @Test
        void key1UnusedBitsReadAsOne() {
            int key1 = memory.readByte(0xFF4D) & 0xFF;
            assertEquals(0x7E, key1 & 0x7E, "KEY1 bits 6-1 should read as 1 on CGB");
        }

        @Test
        void key1ReportsNormalSpeedInitially() {
            int key1 = memory.readByte(0xFF4D) & 0xFF;
            assertFalse((key1 & 0x80) != 0, "Should start in normal speed");
        }

        @Test
        void key1PrepBitWritable() {
            // Write prepare bit
            memory.writeByte(0xFF4D, 0x01);
            int key1 = memory.readByte(0xFF4D) & 0xFF;
            assertTrue((key1 & 0x01) != 0, "Prepare speed switch bit should be writable");
        }
    }

    @Nested
    class HdmaTransfer {
        @Test
        void hdma5ReadsFFWhenInactive() {
            int hdma5 = memory.readByte(0xFF55) & 0xFF;
            assertEquals(0xFF, hdma5, "HDMA5 should read 0xFF when no transfer is active");
        }

        @Test
        void generalPurposeDmaTransfersData() {
            // Disable LCD to allow VRAM access
            memory.writeByte(0xFF40, 0x00);

            // Write test data to WRAM at 0xC000
            for (int i = 0; i < 0x10; i++) {
                memory.writeByte(0xC000 + i, 0xA0 + i);
            }

            // Set HDMA source: 0xC000
            memory.writeByte(0xFF51, 0xC0);
            memory.writeByte(0xFF52, 0x00);

            // Set HDMA dest: 0x8000
            memory.writeByte(0xFF53, 0x80);
            memory.writeByte(0xFF54, 0x00);

            // Start general purpose DMA: 1 block (0x10 bytes), bit 7 = 0
            memory.writeByte(0xFF55, 0x00);

            // Verify data was transferred to VRAM
            memory.writeByte(0xFF4F, 0x00); // Bank 0
            for (int i = 0; i < 0x10; i++) {
                assertEquals(0xA0 + i, memory.readByte(0x8000 + i) & 0xFF,
                        "VRAM byte " + i + " should match source data");
            }
        }
    }

    @Nested
    class CgbRegistersOnDmg {
        private Memory dmgMemory;

        @BeforeEach
        void setUp() {
            dmgMemory = new Memory();
            dmgMemory.setHardwareType(HardwareType.DMG);
            new CPU(dmgMemory);
            dmgMemory.loadCartridge(createDmgCartridge());
        }

        @Test
        void key1ReturnsFFOnDmg() {
            assertEquals(0xFF, dmgMemory.readByte(0xFF4D) & 0xFF,
                    "KEY1 should return 0xFF on DMG");
        }

        @Test
        void vramBankReturnsFFOnDmg() {
            assertEquals(0xFF, dmgMemory.readByte(0xFF4F) & 0xFF,
                    "VBK should return 0xFF on DMG");
        }

        @Test
        void wramBankReturnsFFOnDmg() {
            assertEquals(0xFF, dmgMemory.readByte(0xFF70) & 0xFF,
                    "SVBK should return 0xFF on DMG");
        }

        @Test
        void bgpiReturnsFFOnDmg() {
            assertEquals(0xFF, dmgMemory.readByte(0xFF68) & 0xFF,
                    "BGPI should return 0xFF on DMG");
        }
    }

    @Nested
    class TimerInitialState {
        @Test
        void cgbDivInitialValue() {
            int div = memory.readByte(0xFF04) & 0xFF;
            // CGB initial DIV counter is 0x1EA0, upper byte = 0x1E
            assertEquals(0x1E, div, "CGB initial DIV should be 0x1E");
        }

        @Test
        void dmgDivInitialValue() {
            Memory dmgMem = new Memory();
            dmgMem.setHardwareType(HardwareType.DMG);
            new CPU(dmgMem);
            dmgMem.loadCartridge(createDmgCartridge());
            // Trigger reset
            int div = dmgMem.readByte(0xFF04) & 0xFF;
            // DMG initial DIV counter is 0xABCC, upper byte = 0xAB
            assertEquals(0xAB, div, "DMG initial DIV should be 0xAB");
        }
    }

    @Nested
    class InterruptFlags {
        @Test
        void postBootIfHasVblankFlag() {
            int ifReg = memory.readByte(0xFF0F) & 0xFF;
            // Post-boot IF should have VBlank flag set (bit 0)
            // Upper 3 bits always read as 1 (0xE0)
            assertTrue((ifReg & 0x01) != 0,
                    "Post-boot IF should have VBlank flag set");
        }

        @Test
        void ieRegisterAllBitsReadWritable() {
            memory.writeByte(0xFFFF, 0xFF);
            assertEquals(0xFF, memory.readByte(0xFFFF) & 0xFF,
                    "IE register should be fully readable/writable");

            memory.writeByte(0xFFFF, 0xE0);
            assertEquals(0xE0, memory.readByte(0xFFFF) & 0xFF,
                    "IE upper bits should be preserved");

            memory.writeByte(0xFFFF, 0x00);
            assertEquals(0x00, memory.readByte(0xFFFF) & 0xFF,
                    "IE should be clearable to 0");
        }
    }

    @Nested
    class SerialPort {
        @Test
        void serialDataReadWritable() {
            memory.writeByte(0xFF01, 0xAB);
            assertEquals(0xAB, memory.readByte(0xFF01) & 0xFF);
        }

        @Test
        void serialControlUnusedBitsRead1() {
            memory.writeByte(0xFF02, 0x00);
            // On CGB, SC bits 2-6 read as 1; bit 1 is the speed flag.
            int sc = memory.readByte(0xFF02) & 0xFF;
            assertEquals(0x7C, sc, "SC unused bits should read as 1 (bit1 reflects speed)");
        }
    }

    @Nested
    class OamDma {
        @Test
        void oamDmaTransfersData() {
            // Write test pattern to WRAM
            for (int i = 0; i < 160; i++) {
                memory.writeByte(0xC000 + i, i & 0xFF);
            }

            // Start OAM DMA from 0xC000
            memory.writeByte(0xFF46, 0xC0);

            // Step peripherals enough to complete the transfer
            // DMA takes: 4 T-cycles startup + 160 bytes × 4 T-cycles = 644 T-cycles
            memory.stepPeripherals(650);

            // Verify OAM data was transferred
            // Note: OAM reads are blocked during DMA, so we verify after completion
            for (int i = 0; i < 160; i++) {
                assertEquals(i & 0xFF, memory.getOamByte(i),
                        "OAM byte " + i + " should match source data");
            }
        }
    }

    @Nested
    class LcdRegisters {
        @Test
        void statUpperBitAlwaysSet() {
            int stat = memory.readByte(0xFF41) & 0xFF;
            assertTrue((stat & 0x80) != 0, "STAT bit 7 should always read as 1");
        }

        @Test
        void tacUpperBitsRead1() {
            int tac = memory.readByte(0xFF07) & 0xFF;
            assertEquals(0xF8, tac & 0xF8, "TAC upper 5 bits should read as 1");
        }

        @Test
        void lcdcPostBootValue() {
            assertEquals(0x91, memory.readByte(0xFF40) & 0xFF,
                    "Post-boot LCDC should be 0x91");
        }
    }

    // --- Helper: create a minimal CGB-compatible cartridge ---
    private static gbc.model.cartridge.Cartridge createCgbCartridge() {
        byte[] rom = new byte[0x8000]; // 32KB minimum
        // Set cartridge header
        rom[0x0143] = (byte) 0x80; // CGB flag: supports CGB
        rom[0x0147] = 0x00; // ROM only
        rom[0x0148] = 0x00; // 32KB ROM
        rom[0x0149] = 0x00; // No RAM
        // Set Nintendo logo (enough to pass validation)
        rom[0x0104] = (byte) 0xCE;
        // Cartridge title
        rom[0x0134] = 'T'; rom[0x0135] = 'E'; rom[0x0136] = 'S'; rom[0x0137] = 'T';
        // Header checksum
        int checksum = 0;
        for (int i = 0x0134; i <= 0x014C; i++) {
            checksum = checksum - (rom[i] & 0xFF) - 1;
        }
        rom[0x014D] = (byte) (checksum & 0xFF);
        return gbc.model.cartridge.CartridgeFactory.create(rom);
    }

    private static gbc.model.cartridge.Cartridge createDmgCartridge() {
        byte[] rom = new byte[0x8000];
        rom[0x0143] = 0x00; // DMG only
        rom[0x0147] = 0x00;
        rom[0x0148] = 0x00;
        rom[0x0149] = 0x00;
        rom[0x0104] = (byte) 0xCE;
        rom[0x0134] = 'D'; rom[0x0135] = 'M'; rom[0x0136] = 'G';
        int checksum = 0;
        for (int i = 0x0134; i <= 0x014C; i++) {
            checksum = checksum - (rom[i] & 0xFF) - 1;
        }
        rom[0x014D] = (byte) (checksum & 0xFF);
        return gbc.model.cartridge.CartridgeFactory.create(rom);
    }
}
