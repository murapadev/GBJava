package gbc.model.cpu;

import gbc.model.cartridge.ROM;
import gbc.model.memory.Memory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CPUInstructionTest {

    private static final int PROGRAM_START = 0x0100;
    private static final int FLAG_Z = 0x80;
    private static final int FLAG_N = 0x40;
    private static final int FLAG_H = 0x20;
    private static final int FLAG_C = 0x10;

    private CPU cpu;
    private Memory memory;
    private Registers registers;
    private byte[] romData;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        romData = new byte[0x8000];
        memory.loadCartridge(new ROM(romData));
        cpu = new CPU(memory);
        registers = cpu.getRegisters();
    }

    @Test
    void addImmediateSetsZeroHalfCarryAndCarry() {
        loadProgram(0xC6, 0x01); // ADD A, d8 (0x01)
        registers.setRegister("A", (byte) 0xFF);

        cpu.executeCycle();

        assertEquals(0x00, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertTrue((flags & FLAG_Z) != 0, "Zero flag must be set");
        assertFalse((flags & FLAG_N) != 0, "Subtract flag must be cleared");
        assertTrue((flags & FLAG_H) != 0, "Half-carry flag must be set");
        assertTrue((flags & FLAG_C) != 0, "Carry flag must be set");
    }

    @Test
    void addRegisterClearsFlagsWhenNoCarry() {
        loadProgram(0x80); // ADD A, B
        registers.setRegister("A", (byte) 0x10);
        registers.setRegister("B", (byte) 0x0F);

        cpu.executeCycle();

        assertEquals(0x1F, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertEquals(0, flags & (FLAG_Z | FLAG_N | FLAG_H | FLAG_C));
    }

    @Test
    void sbcImmediateAccountsForCarryIn() {
        loadProgram(0xDE, 0x01); // SBC A, d8
        registers.setRegister("A", (byte) 0x10);
        registers.setRegister("F", (byte) FLAG_C); // preset carry

        cpu.executeCycle();

        assertEquals(0x0E, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertFalse((flags & FLAG_Z) != 0, "Zero flag must be cleared");
        assertTrue((flags & FLAG_N) != 0, "Subtract flag must be set");
        assertTrue((flags & FLAG_H) != 0, "Half-carry flag must be set due to borrow");
        assertFalse((flags & FLAG_C) != 0, "Carry flag must be cleared");
    }

    @Test
    void incRegisterPreservesCarryFlag() {
        loadProgram(0x04); // INC B
        registers.setRegister("B", (byte) 0x0F);
        registers.setRegister("F", (byte) FLAG_C); // set carry beforehand

        cpu.executeCycle();

        assertEquals(0x10, registers.getRegister("B") & 0xFF);
        int flags = flags();
        assertFalse((flags & FLAG_Z) != 0, "Zero flag must be cleared");
        assertFalse((flags & FLAG_N) != 0, "Subtract flag must be cleared");
        assertTrue((flags & FLAG_H) != 0, "Half-carry flag must be set");
        assertTrue((flags & FLAG_C) != 0, "Carry flag must be preserved");
    }

    @Test
    void decMemorySetsBorrowFlags() {
        loadProgram(0x21, 0x00, 0xC0, // LD HL, 0xC000
                0x35); // DEC (HL)
        registers.setRegister("F", (byte) 0);
        memory.writeByte(0xC000, 0x00);

        cpu.executeCycle(); // LD HL, d16
        cpu.executeCycle(); // DEC (HL)

        assertEquals(0xFF, memory.readByte(0xC000) & 0xFF);
        int flags = flags();
        assertFalse((flags & FLAG_Z) != 0, "Zero flag must be cleared");
        assertTrue((flags & FLAG_N) != 0, "Subtract flag must be set");
        assertTrue((flags & FLAG_H) != 0, "Half-carry flag must be set");
    }

    @Test
    void andMemoryAppliesPaletteFlags() {
        loadProgram(0x21, 0x00, 0xC0, // LD HL, 0xC000
                0xA6); // AND (HL)
        registers.setRegister("A", (byte) 0xF0);
        memory.writeByte(0xC000, 0x0F);

        cpu.executeCycle(); // LD HL, d16
        cpu.executeCycle(); // AND (HL)

        assertEquals(0x00, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertTrue((flags & FLAG_Z) != 0, "Zero flag must be set");
        assertFalse((flags & FLAG_N) != 0, "Subtract flag must be cleared");
        assertTrue((flags & FLAG_H) != 0, "Half-carry flag must be set for AND");
        assertFalse((flags & FLAG_C) != 0, "Carry flag must be cleared");
    }

    @Test
    void xorImmediateClearsAppropriateFlags() {
        loadProgram(0xEE, 0xFF); // XOR d8
        registers.setRegister("A", (byte) 0xF0);

        cpu.executeCycle();

        assertEquals(0x0F, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertFalse((flags & FLAG_Z) != 0, "Zero flag must be cleared");
        assertEquals(0, flags & (FLAG_N | FLAG_H | FLAG_C));
    }

    @Test
    void cpImmediateLeavesAccumulatorUntouched() {
        loadProgram(0xFE, 0x10); // CP d8
        registers.setRegister("A", (byte) 0x10);

        cpu.executeCycle();

        assertEquals(0x10, registers.getRegister("A") & 0xFF);
        int flags = flags();
        assertTrue((flags & FLAG_Z) != 0, "Zero flag must be set");
        assertTrue((flags & FLAG_N) != 0, "Subtract flag must be set");
        assertEquals(0, flags & FLAG_C);
    }

    @Test
    void ldTransfersBetweenRegisters() {
        loadProgram(0x06, 0x12, // LD B, d8
                0x78); // LD A, B
        registers.setRegister("A", (byte) 0x00);

        cpu.executeCycle();
        cpu.executeCycle();

        assertEquals(0x12, registers.getRegister("A") & 0xFF);
    }

    @Test
    void ldImmediateToMemoryStoresValue() {
        loadProgram(0x21, 0x00, 0xC0, // LD HL, 0xC000
                0x36, 0x42); // LD (HL), d8

        cpu.executeCycle();
        cpu.executeCycle();

        assertEquals(0x42, memory.readByte(0xC000) & 0xFF);
    }

    @Test
    void haltSetsHaltedFlag() {
        loadProgram(0x76); // HALT

        cpu.executeCycle();

        assertTrue(cpu.isHalted(), "CPU should be halted after HALT instruction");
    }

    @Test
    void haltBugAllowsImmediateFetchWithoutCrashing() {
        loadProgram(0x76, 0x3E, 0x12, 0x00); // HALT, LD A, d8, padding
        registers.setRegister("A", (byte) 0x00);

        memory.writeByte(0xFFFF, 0x01); // Enable VBlank interrupt
        memory.writeByte(0xFF0F, 0x01); // Request VBlank interrupt

        cpu.executeCycle();

        assertFalse(cpu.isHalted(), "Pending interrupt should wake HALT immediately");
        assertTrue(cpu.isHaltBugTriggered(), "HALT bug flag should be raised after HALT");

        int cycles = cpu.executeCycle();

        assertEquals(8, cycles, "LD A, d8 should consume 8 cycles");
        assertEquals(0x3E, registers.getRegister("A") & 0xFF,
                "HALT bug should feed the opcode byte into the accumulator");
        assertEquals(0x0102, registers.getPC(), "PC should advance by the instruction length");
        assertFalse(cpu.isHaltBugTriggered(), "HALT bug flag should clear after the next fetch completes");
    }

    @Test
    void stopWithPrepareSwitchesSpeedAndReloadsTimer() {
        loadProgram(0x10, 0x00); // STOP, padding byte

        cpu.setPrepareSpeedSwitch(true);
        cpu.setDoubleSpeedMode(false);

        memory.writeByte(0xFF06, 0xAB); // TMA
        memory.writeByte(0xFF05, 0x01); // TIMA before reload
        memory.stepPeripherals(32);

        cpu.executeCycle();

        assertTrue(cpu.isDoubleSpeedMode(), "Speed switch should enable double-speed mode");
        assertFalse(cpu.isPrepareSpeedSwitch(), "Prepare bit must clear after switching speed");
        assertEquals(0xAB, memory.readByte(0xFF05) & 0xFF, "TIMA should reload from TMA on speed switch");
        assertEquals(0, memory.readByte(0xFF04) & 0xFF, "DIV register should reset when switching speed");
    }

    @Test
    void stopWithoutPrepareLeavesSpeedUnchanged() {
        loadProgram(0x10, 0x00); // STOP, padding byte

        cpu.setDoubleSpeedMode(true);
        cpu.setPrepareSpeedSwitch(false);

        cpu.executeCycle();

        assertTrue(cpu.isDoubleSpeedMode(), "Without prepare bit, STOP must not toggle speed");
    }

    @Test
    void cycleCountsRemainStableInDoubleSpeedMode() {
        loadProgram(0x00); // NOP

        int normalCycles = cpu.executeCycle();
        assertEquals(4, normalCycles, "NOP should take 4 cycles in normal speed");

        loadProgram(0x00); // reset and run again in double-speed mode
        cpu.setDoubleSpeedMode(true);

        int doubleSpeedCycles = cpu.executeCycle();

        assertEquals(normalCycles, doubleSpeedCycles,
                "Cycle counts returned by executeCycle must be consistent in double-speed mode");
    }

    @Test
    void jrNzTakenUsesCorrectCycles() {
        loadProgram(0x20, 0x05); // JR NZ, +5
        registers.setRegister("F", (byte) 0); // Z=0, so taken

        int cycles = cpu.executeCycle();

        assertEquals(12, cycles, "JR NZ taken should use 12 cycles");
    }

    @Test
    void jrNzNotTakenUsesCorrectCycles() {
        loadProgram(0x20, 0x05); // JR NZ, +5
        registers.setRegister("F", (byte) FLAG_Z); // Z=1, so not taken

        int cycles = cpu.executeCycle();

        assertEquals(8, cycles, "JR NZ not taken should use 8 cycles");
    }

    @Test
    void popAfMasksFlagRegister() {
        loadProgram(0x31, 0x00, 0xC0, // LD SP, 0xC000
                0xF1); // POP AF
        memory.writeChar(0xC000, 0x1234); // value to pop

        cpu.executeCycle(); // LD SP
        cpu.executeCycle(); // POP AF

        assertEquals(0x12, registers.getRegister("A") & 0xFF);
        assertEquals(0x30, registers.getRegister("F") & 0xFF, "F register should be masked to 0xF0");
    }

    @Test
    void eiSetsImePending() {
        loadProgram(0xFB, // EI
                0x00); // NOP

        cpu.executeCycle(); // EI

        assertTrue(cpu.isImePending(), "EI should set imePending");
        assertFalse(cpu.isIme(), "IME should not be set immediately");

        cpu.executeCycle(); // NOP

        assertTrue(cpu.isIme(), "IME should be set after next instruction");
        assertFalse(cpu.isImePending(), "imePending should be cleared");
    }

    private void loadProgram(int... bytes) {
        Arrays.fill(romData, (byte) 0);
        for (int i = 0; i < bytes.length; i++) {
            romData[PROGRAM_START + i] = (byte) bytes[i];
        }
        cpu.reset();
        registers = cpu.getRegisters();
        registers.setRegister("F", (byte) 0);
    }

    private int flags() {
        return registers.getRegister("F") & 0xFF;
    }
}
