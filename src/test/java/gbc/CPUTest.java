package gbc;

import gbc.model.cpu.CPU;
import gbc.model.cpu.OperationsLoader;
import gbc.model.cpu.Operation;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;
import gbc.model.cartridge.Cartridge;
import gbc.controller.FileLoader;
import gbc.model.cartridge.CartridgeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CPUTest {
    private CPU cpu;
    private Registers registers;
    private Memory memory;
    private OperationsLoader operationsLoader;
    private Cartridge cartridge;

    @BeforeEach
    public void setUp() {
        memory = new Memory();
        cartridge = CartridgeFactory.create(FileLoader.loadFile("./Pokemon.gb"));
        memory.loadCartridge(cartridge);
        cpu = new CPU(memory);
        registers = cpu.getRegisters();
        operationsLoader = new OperationsLoader();
    }

    @Test
    public void testADDOperation() {
        // Test ADD A, B
        registers.setRegister("A", (byte) 0x10);
        registers.setRegister("B", (byte) 0x20);
        
        Operation addOp = operationsLoader.getOperation(0x80); // ADD A, B
        assertNotNull(addOp, "ADD operation should be loaded");
        
        // Execute the operation
        addOp.getExecutor().execute(registers, memory, addOp.getOperands());
        
        // Verify result
        assertEquals(0x30, registers.getRegister("A") & 0xFF);
        
        // Verify flags (no carry, no half carry, not zero)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testADDCarryOperation() {
        // Test ADD A, B with carry
        registers.setRegister("A", (byte) 0xFF);
        registers.setRegister("B", (byte) 0x01);
        
        Operation addOp = operationsLoader.getOperation(0x80); // ADD A, B
        assertNotNull(addOp, "ADD operation should be loaded");
        
        // Execute the operation
        addOp.getExecutor().execute(registers, memory, addOp.getOperands());
        
        // Verify result
        assertEquals(0x00, registers.getRegister("A") & 0xFF);
        
        // Verify flags (carry set, zero set)
        byte flags = registers.getRegister("F");
        assertEquals(0x80, flags & 0x80); // Z flag should be 1
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x20, flags & 0x20); // H flag should be 1
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testSUBOperation() {
        // Test SUB B
        registers.setRegister("A", (byte) 0x20);
        registers.setRegister("B", (byte) 0x10);
        
        Operation subOp = operationsLoader.getOperation(0x90); // SUB B
        assertNotNull(subOp, "SUB operation should be loaded");
        
        // Execute the operation
        subOp.getExecutor().execute(registers, memory, subOp.getOperands());
        
        // Verify result
        assertEquals(0x10, registers.getRegister("A") & 0xFF);
        
        // Verify flags (no carry, no half carry, not zero)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x40, flags & 0x40); // N flag should be 1
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testANDOperation() {
        // Test AND B
        registers.setRegister("A", (byte) 0xF0);
        registers.setRegister("B", (byte) 0x0F);
        
        Operation andOp = operationsLoader.getOperation(0xA0); // AND B
        assertNotNull(andOp, "AND operation should be loaded");
        
        // Execute the operation
        andOp.getExecutor().execute(registers, memory, andOp.getOperands());
        
        // Verify result
        assertEquals(0x00, registers.getRegister("A") & 0xFF);
        
        // Verify flags (zero set, H flag set for AND)
        byte flags = registers.getRegister("F");
        assertEquals(0x80, flags & 0x80); // Z flag should be 1
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x20, flags & 0x20); // H flag should be 1
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testOROperation() {
        // Test OR B
        registers.setRegister("A", (byte) 0xF0);
        registers.setRegister("B", (byte) 0x0F);
        
        Operation orOp = operationsLoader.getOperation(0xB0); // OR B
        assertNotNull(orOp, "OR operation should be loaded");
        
        // Execute the operation
        orOp.getExecutor().execute(registers, memory, orOp.getOperands());
        
        // Verify result
        assertEquals(0xFF, registers.getRegister("A") & 0xFF);
        
        // Verify flags (not zero, all other flags 0 for OR)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testXOROperation() {
        // Test XOR B
        registers.setRegister("A", (byte) 0xFF);
        registers.setRegister("B", (byte) 0x0F);
        
        Operation xorOp = operationsLoader.getOperation(0xA8); // XOR B
        assertNotNull(xorOp, "XOR operation should be loaded");
        
        // Execute the operation
        xorOp.getExecutor().execute(registers, memory, xorOp.getOperands());
        
        // Verify result
        assertEquals(0xF0, registers.getRegister("A") & 0xFF);
        
        // Verify flags (not zero, all flags 0 for XOR)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testINCOperation() {
        // Test INC B
        registers.setRegister("B", (byte) 0x0F);
        
        Operation incOp = operationsLoader.getOperation(0x04); // INC B
        assertNotNull(incOp, "INC operation should be loaded");
        
        // Execute the operation
        incOp.getExecutor().execute(registers, memory, incOp.getOperands());
        
        // Verify result
        assertEquals(0x10, registers.getRegister("B") & 0xFF);
        
        // Verify flags (no zero, N=0, H=1 for half carry)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x20, flags & 0x20); // H flag should be 1 (0x0F + 1 = 0x10, half carry)
    }

    @Test
    public void testINCZeroOperation() {
        // Test INC B with zero result
        registers.setRegister("B", (byte) 0xFF);
        
        Operation incOp = operationsLoader.getOperation(0x04); // INC B
        assertNotNull(incOp, "INC operation should be loaded");
        
        // Execute the operation
        incOp.getExecutor().execute(registers, memory, incOp.getOperands());
        
        // Verify result
        assertEquals(0x00, registers.getRegister("B") & 0xFF);
        
        // Verify flags (zero set, N=0, H=1 for half carry)
        byte flags = registers.getRegister("F");
        assertEquals(0x80, flags & 0x80); // Z flag should be 1
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x20, flags & 0x20); // H flag should be 1 (0xFF + 1 = 0x100, half carry)
    }

    @Test
    public void testDECOperation() {
        // Test DEC B
        registers.setRegister("B", (byte) 0x10);
        
        Operation decOp = operationsLoader.getOperation(0x05); // DEC B
        assertNotNull(decOp, "DEC operation should be loaded");
        
        // Execute the operation
        decOp.getExecutor().execute(registers, memory, decOp.getOperands());
        
        // Verify result
        assertEquals(0x0F, registers.getRegister("B") & 0xFF);
        
        // Verify flags (no zero, N=1, H=1 for half borrow)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x40, flags & 0x40); // N flag should be 1
        assertEquals(0x20, flags & 0x20); // H flag should be 1 (0x10 - 1 = 0x0F, half borrow)
    }

    @Test
    public void testDECZeroOperation() {
        // Test DEC B with zero result
        registers.setRegister("B", (byte) 0x01);
        
        Operation decOp = operationsLoader.getOperation(0x05); // DEC B
        assertNotNull(decOp, "DEC operation should be loaded");
        
        // Execute the operation
        decOp.getExecutor().execute(registers, memory, decOp.getOperands());
        
        // Verify result
        assertEquals(0x00, registers.getRegister("B") & 0xFF);
        
        // Verify flags (zero set, N=1, H=0)
        byte flags = registers.getRegister("F");
        assertEquals(0x80, flags & 0x80); // Z flag should be 1
        assertEquals(0x40, flags & 0x40); // N flag should be 1
        assertEquals(0x00, flags & 0x20); // H flag should be 0
    }

    @Test
    public void testINCMemoryOperation() {
        // Test INC (HL)
        registers.setHL((char) 0xC000);
        memory.writeByte((char) 0xC000, (byte) 0x0F);
        
        Operation incOp = operationsLoader.getOperation(0x34); // INC (HL)
        assertNotNull(incOp, "INC (HL) operation should be loaded");
        
        // Execute the operation
        incOp.getExecutor().execute(registers, memory, incOp.getOperands());
        
        // Verify result
        assertEquals(0x10, memory.readByte((char) 0xC000) & 0xFF);
        
        // Verify flags (no zero, N=0, H=1 for half carry)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x20, flags & 0x20); // H flag should be 1
    }

    @Test
    public void testDECMemoryOperation() {
        // Test DEC (HL)
        registers.setHL((char) 0xC000);
        memory.writeByte((char) 0xC000, (byte) 0x10);
        
        Operation decOp = operationsLoader.getOperation(0x35); // DEC (HL)
        assertNotNull(decOp, "DEC (HL) operation should be loaded");
        
        // Execute the operation
        decOp.getExecutor().execute(registers, memory, decOp.getOperands());
        
        // Verify result
        assertEquals(0x0F, memory.readByte((char) 0xC000) & 0xFF);
        
        // Verify flags (no zero, N=1, H=1 for half borrow)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x40, flags & 0x40); // N flag should be 1
        assertEquals(0x20, flags & 0x20); // H flag should be 1
    }

    @Test
    public void testLDImmediateOperation() {
        // Test LD B, d8
        registers.setPC((char) 0xC000);
        memory.writeByte((char) 0xC000, (byte) 0x42);
        
        Operation ldOp = operationsLoader.getOperation(0x06); // LD B, d8
        assertNotNull(ldOp, "LD immediate operation should be loaded");
        
        // Execute the operation
        ldOp.getExecutor().execute(registers, memory, ldOp.getOperands());
        
        // Verify result
        assertEquals(0x42, registers.getRegister("B") & 0xFF);
        // Verify PC incremented
        assertEquals(0xC001, registers.getPC() & 0xFFFF);
    }

    @Test
    public void testLDRegisterToRegister() {
        // Test LD A, B
        registers.setRegister("B", (byte) 0x5A);
        
        Operation ldOp = operationsLoader.getOperation(0x78); // LD A, B
        assertNotNull(ldOp, "LD register to register operation should be loaded");
        
        // Execute the operation
        ldOp.getExecutor().execute(registers, memory, ldOp.getOperands());
        
        // Verify result
        assertEquals(0x5A, registers.getRegister("A") & 0xFF);
    }

    @Test
    public void testLDLoadFromMemory() {
        // Test LD A, (HL)
        registers.setHL((char) 0xC000);
        memory.writeByte((char) 0xC000, (byte) 0x7F);
        
        Operation ldOp = operationsLoader.getOperation(0x7E); // LD A, (HL)
        assertNotNull(ldOp, "LD load from memory operation should be loaded");
        
        // Execute the operation
        ldOp.getExecutor().execute(registers, memory, ldOp.getOperands());
        
        // Verify result
        assertEquals(0x7F, registers.getRegister("A") & 0xFF);
    }

    @Test
    public void testLDStoreToMemory() {
        // Test LD (HL), A
        registers.setHL((char) 0xC000);
        registers.setRegister("A", (byte) 0xAB);
        
        Operation ldOp = operationsLoader.getOperation(0x77); // LD (HL), A
        assertNotNull(ldOp, "LD store to memory operation should be loaded");
        
        // Execute the operation
        ldOp.getExecutor().execute(registers, memory, ldOp.getOperands());
        
        // Verify result
        assertEquals(0xAB, memory.readByte((char) 0xC000) & 0xFF);
    }

    @Test
    public void testRLCAOperation() {
        // Test RLCA - Rotate A left through carry
        registers.setRegister("A", (byte) 0x85); // 10000101
        
        Operation rlcaOp = operationsLoader.getOperation(0x07); // RLCA
        assertNotNull(rlcaOp, "RLCA operation should be loaded");
        
        // Execute the operation
        rlcaOp.getExecutor().execute(registers, memory, rlcaOp.getOperands());
        
        // Verify result: 00001011 (bit 7 moves to bit 0, carry gets bit 7)
        assertEquals(0x0B, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=1 because bit 7 was set)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testRLCANoCarryOperation() {
        // Test RLCA without setting carry
        registers.setRegister("A", (byte) 0x42); // 01000010
        
        Operation rlcaOp = operationsLoader.getOperation(0x07); // RLCA
        assertNotNull(rlcaOp, "RLCA operation should be loaded");
        
        // Execute the operation
        rlcaOp.getExecutor().execute(registers, memory, rlcaOp.getOperands());
        
        // Verify result: 10000100 (rotated left, bit 7=0 so no carry)
        assertEquals(0x84, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=0)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testRRCAOperation() {
        // Test RRCA - Rotate A right through carry
        registers.setRegister("A", (byte) 0x85); // 10000101
        
        Operation rrcaOp = operationsLoader.getOperation(0x0F); // RRCA
        assertNotNull(rrcaOp, "RRCA operation should be loaded");
        
        // Execute the operation
        rrcaOp.getExecutor().execute(registers, memory, rrcaOp.getOperands());
        
        // Verify result: 11000010 (bit 0 moves to bit 7, carry gets bit 0)
        assertEquals(0xC2, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=1 because bit 0 was set)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testRRCANoCarryOperation() {
        // Test RRCA without setting carry
        registers.setRegister("A", (byte) 0x42); // 01000010
        
        Operation rrcaOp = operationsLoader.getOperation(0x0F); // RRCA
        assertNotNull(rrcaOp, "RRCA operation should be loaded");
        
        // Execute the operation
        rrcaOp.getExecutor().execute(registers, memory, rrcaOp.getOperands());
        
        // Verify result: 00100001 (rotated right, bit 0=0 so no carry)
        assertEquals(0x21, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=0)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testRLAOperation() {
        // Test RLA - Rotate A left through carry
        registers.setRegister("A", (byte) 0x42); // 01000010
        registers.setRegister("F", (byte) 0x10); // Set carry flag
        
        Operation rlaOp = operationsLoader.getOperation(0x17); // RLA
        assertNotNull(rlaOp, "RLA operation should be loaded");
        
        // Execute the operation
        rlaOp.getExecutor().execute(registers, memory, rlaOp.getOperands());
        
        // Verify result: 10000101 (rotated left, carry goes to bit 0)
        assertEquals(0x85, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=0 because bit 7 was 0)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testRLAWithCarryOutOperation() {
        // Test RLA with carry out
        registers.setRegister("A", (byte) 0x85); // 10000101
        registers.setRegister("F", (byte) 0x00); // Clear carry flag
        
        Operation rlaOp = operationsLoader.getOperation(0x17); // RLA
        assertNotNull(rlaOp, "RLA operation should be loaded");
        
        // Execute the operation
        rlaOp.getExecutor().execute(registers, memory, rlaOp.getOperands());
        
        // Verify result: 00001010 (rotated left, carry=0 goes to bit 0)
        assertEquals(0x0A, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=1 because bit 7 was set)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testRRAOperation() {
        // Test RRA - Rotate A right through carry
        registers.setRegister("A", (byte) 0x42); // 01000010
        registers.setRegister("F", (byte) 0x10); // Set carry flag
        
        Operation rraOp = operationsLoader.getOperation(0x1F); // RRA
        assertNotNull(rraOp, "RRA operation should be loaded");
        
        // Execute the operation
        rraOp.getExecutor().execute(registers, memory, rraOp.getOperands());
        
        // Verify result: 10100001 (rotated right, carry goes to bit 7)
        assertEquals(0xA1, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=0 because bit 0 was 0)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testRRAWithCarryOutOperation() {
        // Test RRA with carry out
        registers.setRegister("A", (byte) 0x85); // 10000101
        registers.setRegister("F", (byte) 0x00); // Clear carry flag
        
        Operation rraOp = operationsLoader.getOperation(0x1F); // RRA
        assertNotNull(rraOp, "RRA operation should be loaded");
        
        // Execute the operation
        rraOp.getExecutor().execute(registers, memory, rraOp.getOperands());
        
        // Verify result: 01000010 (rotated right, carry=0 goes to bit 7)
        assertEquals(0x42, registers.getRegister("A") & 0xFF);
        
        // Verify flags (Z=0, N=0, H=0, C=1 because bit 0 was set)
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testJRUnconditionalOperation() {
        // Test JR r8 (unconditional relative jump)
        registers.setPC((char) 0xC000);
        
        Operation jrOp = operationsLoader.getOperation(0x18); // JR r8
        assertNotNull(jrOp, "JR unconditional operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump offset (+10) at the current PC position
        memory.writeByte(registers.getPC(), (byte) 10);
        
        // Execute the operation
        jrOp.getExecutor().execute(registers, memory, jrOp.getOperands());
        
        // Verify PC was updated correctly: 0xC001 + 1 (consumed byte) + 10 (offset) = 0xC00C
        assertEquals(0xC00C, registers.getPC());
    }

    @Test
    public void testJRNegativeOffsetOperation() {
        // Test JR r8 with negative offset
        registers.setPC((char) 0xC000);
        
        Operation jrOp = operationsLoader.getOperation(0x18); // JR r8
        assertNotNull(jrOp, "JR unconditional operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump offset (-5) at the current PC position
        memory.writeByte(registers.getPC(), (byte) -5);
        
        // Execute the operation
        jrOp.getExecutor().execute(registers, memory, jrOp.getOperands());
        
        // Verify PC was updated correctly: 0xC001 + 1 (consumed byte) + (-5) (offset) = 0xBFFD
        assertEquals(0xBFFD, registers.getPC());
    }

    @Test
    public void testJRZConditionMetOperation() {
        // Test JR Z, r8 when Z flag is set
        registers.setPC((char) 0xC000);
        registers.setRegister("F", (byte) 0x80); // Set Z flag
        
        Operation jrzOp = operationsLoader.getOperation(0x28); // JR Z, r8
        assertNotNull(jrzOp, "JR Z operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump offset (+5) at the current PC position
        memory.writeByte(registers.getPC(), (byte) 5);
        
        // Execute the operation
        jrzOp.getExecutor().execute(registers, memory, jrzOp.getOperands());
        
        // Verify PC was updated correctly: 0xC001 + 1 (consumed byte) + 5 (offset) = 0xC007
        assertEquals(0xC007, registers.getPC());
    }

    @Test
    public void testJRZConditionNotMetOperation() {
        // Test JR Z, r8 when Z flag is not set
        registers.setPC((char) 0xC000);
        registers.setRegister("F", (byte) 0x00); // Clear Z flag
        
        Operation jrzOp = operationsLoader.getOperation(0x28); // JR Z, r8
        assertNotNull(jrzOp, "JR Z operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump offset (+5) at the current PC position
        memory.writeByte(registers.getPC(), (byte) 5);
        
        // Execute the operation
        jrzOp.getExecutor().execute(registers, memory, jrzOp.getOperands());
        
        // Verify PC was NOT updated: should only consume the offset byte
        assertEquals(0xC002, registers.getPC());
    }

    @Test
    public void testJPUnconditionalOperation() {
        // Test JP a16 (unconditional absolute jump)
        registers.setPC((char) 0xC000);
        
        Operation jpOp = operationsLoader.getOperation(0xC3); // JP a16
        assertNotNull(jpOp, "JP unconditional operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump address (0x1234) at the current PC position
        memory.writeChar(registers.getPC(), 0x1234);
        
        // Execute the operation
        jpOp.getExecutor().execute(registers, memory, jpOp.getOperands());
        
        // Verify PC was updated to the jump address
        assertEquals(0x1234, registers.getPC());
    }

    @Test
    public void testJPHLOperation() {
        // Test JP HL (jump to address in HL register)
        registers.setPC((char) 0x0100);
        registers.setHL((char) 0x5678);
        
        Operation jpHlOp = operationsLoader.getOperation(0xE9); // JP HL
        assertNotNull(jpHlOp, "JP HL operation should be loaded");
        
        // Execute the operation
        jpHlOp.getExecutor().execute(registers, memory, jpHlOp.getOperands());
        
        // Verify PC was updated to HL value
        assertEquals(0x5678, registers.getPC());
    }

    @Test
    public void testJPNZConditionMetOperation() {
        // Test JP NZ, a16 when Z flag is not set
        registers.setPC((char) 0xC000);
        registers.setRegister("F", (byte) 0x00); // Clear Z flag
        
        Operation jpNzOp = operationsLoader.getOperation(0xC2); // JP NZ, a16
        assertNotNull(jpNzOp, "JP NZ operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump address (0xABCD) at the current PC position
        memory.writeChar(registers.getPC(), 0xABCD);
        
        // Execute the operation
        jpNzOp.getExecutor().execute(registers, memory, jpNzOp.getOperands());
        
        // Verify PC was updated to the jump address
        assertEquals(0xABCD, registers.getPC());
    }

    @Test
    public void testJPNZConditionNotMetOperation() {
        // Test JP NZ, a16 when Z flag is set
        registers.setPC((char) 0xC000);
        registers.setRegister("F", (byte) 0x80); // Set Z flag
        
        Operation jpNzOp = operationsLoader.getOperation(0xC2); // JP NZ, a16
        assertNotNull(jpNzOp, "JP NZ operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with jump address (0xABCD) at the current PC position
        memory.writeChar(registers.getPC(), 0xABCD);
        
        // Execute the operation
        jpNzOp.getExecutor().execute(registers, memory, jpNzOp.getOperands());
        
        // Verify PC was NOT updated: should consume the 2 address bytes
        assertEquals(0xC003, registers.getPC());
    }

    @Test
    public void testCALLUnconditionalOperation() {
        // Test CALL a16 (unconditional call)
        registers.setPC((char) 0xC000);
        registers.setSP((char) 0xFFFE);
        
        Operation callOp = operationsLoader.getOperation(0xCD); // CALL a16
        assertNotNull(callOp, "CALL unconditional operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with call address (0x2000) at the current PC position
        memory.writeChar(registers.getPC(), 0x2000);
        
        // Execute the operation
        callOp.getExecutor().execute(registers, memory, callOp.getOperands());
        
        // Verify PC was updated to the call address
        assertEquals(0x2000, registers.getPC());
        
        // Verify return address was pushed to stack
        char returnAddress = (char) memory.readChar((char) 0xFFFC); // SP was decremented by 2
        assertEquals(0xC003, returnAddress); // Original PC + 2 (consumed bytes)
    }

    @Test
    public void testCALLZConditionMetOperation() {
        // Test CALL Z, a16 when Z flag is set
        registers.setPC((char) 0xC000);
        registers.setSP((char) 0xFFFE);
        registers.setRegister("F", (byte) 0x80); // Set Z flag
        
        Operation callZOp = operationsLoader.getOperation(0xCC); // CALL Z, a16
        assertNotNull(callZOp, "CALL Z operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with call address (0x3000) at the current PC position
        memory.writeChar(registers.getPC(), 0x3000);
        
        // Execute the operation
        callZOp.getExecutor().execute(registers, memory, callZOp.getOperands());
        
        // Verify PC was updated to the call address
        assertEquals(0x3000, registers.getPC());
        
        // Verify return address was pushed to stack
        char returnAddress = (char) memory.readChar((char) 0xFFFC);
        assertEquals(0xC003, returnAddress);
    }

    @Test
    public void testCALLZConditionNotMetOperation() {
        // Test CALL Z, a16 when Z flag is not set
        registers.setPC((char) 0xC000);
        registers.setSP((char) 0xFFFE);
        registers.setRegister("F", (byte) 0x00); // Clear Z flag
        
        Operation callZOp = operationsLoader.getOperation(0xCC); // CALL Z, a16
        assertNotNull(callZOp, "CALL Z operation should be loaded");
        
        // Simulate opcode fetch by incrementing PC
        registers.incrementPC();
        
        // Set up memory with call address (0x3000) at the current PC position
        memory.writeChar(registers.getPC(), 0x3000);
        
        // Execute the operation
        callZOp.getExecutor().execute(registers, memory, callZOp.getOperands());
        
        // Verify PC was NOT updated: should consume the 2 address bytes
        assertEquals(0xC003, registers.getPC());
        
        // Verify stack was not modified
        assertEquals(0xFFFE, registers.getSP());
    }

    @Test
    public void testRETUnconditionalOperation() {
        // Test RET (unconditional return)
        registers.setPC((char) 0x2000);
        registers.setSP((char) 0xFFFC);
        
        // Push return address to stack
        memory.writeChar((char) 0xFFFC, 0x0105);
        
        Operation retOp = operationsLoader.getOperation(0xC9); // RET
        assertNotNull(retOp, "RET unconditional operation should be loaded");
        
        // Execute the operation
        retOp.getExecutor().execute(registers, memory, retOp.getOperands());
        
        // Verify PC was updated to the return address
        assertEquals(0x0105, registers.getPC());
        
        // Verify SP was incremented (stack pop)
        assertEquals(0xFFFE, registers.getSP());
    }

    @Test
    public void testRETNZConditionMetOperation() {
        // Test RET NZ when Z flag is not set
        registers.setPC((char) 0x2000);
        registers.setSP((char) 0xFFFC);
        registers.setRegister("F", (byte) 0x00); // Clear Z flag
        
        // Push return address to stack
        memory.writeChar((char) 0xFFFC, 0x0105);
        
        Operation retNzOp = operationsLoader.getOperation(0xC0); // RET NZ
        assertNotNull(retNzOp, "RET NZ operation should be loaded");
        
        // Execute the operation
        retNzOp.getExecutor().execute(registers, memory, retNzOp.getOperands());
        
        // Verify PC was updated to the return address
        assertEquals(0x0105, registers.getPC());
        
        // Verify SP was incremented
        assertEquals(0xFFFE, registers.getSP());
    }

    @Test
    public void testRETNZConditionNotMetOperation() {
        // Test RET NZ when Z flag is set
        registers.setPC((char) 0x2000);
        registers.setSP((char) 0xFFFC);
        registers.setRegister("F", (byte) 0x80); // Set Z flag
        
        // Push return address to stack
        memory.writeChar((char) 0xFFFC, 0x0105);
        
        Operation retNzOp = operationsLoader.getOperation(0xC0); // RET NZ
        assertNotNull(retNzOp, "RET NZ operation should be loaded");
        
        // Execute the operation
        retNzOp.getExecutor().execute(registers, memory, retNzOp.getOperands());
        
        // Verify PC was NOT updated
        assertEquals(0x2000, registers.getPC());
        
        // Verify SP was not modified
        assertEquals(0xFFFC, registers.getSP());
    }

    @Test
    public void testNOPOperation() {
        // Test NOP - No operation
        registers.setPC((char) 0x0100);
        registers.setRegister("A", (byte) 0x42);
        registers.setRegister("F", (byte) 0x80); // Set Z flag
        
        Operation nopOp = operationsLoader.getOperation(0x00); // NOP
        assertNotNull(nopOp, "NOP operation should be loaded");
        
        // Execute the operation
        nopOp.getExecutor().execute(registers, memory, nopOp.getOperands());
        
        // Verify nothing changed
        assertEquals(0x0100, registers.getPC());
        assertEquals(0x42, registers.getRegister("A") & 0xFF);
        assertEquals(0x80, registers.getRegister("F") & 0xFF);
    }

    @Test
    public void testSTOPOperation() {
        // Test STOP - Stop CPU
        registers.setPC((char) 0x0100);
        
        Operation stopOp = operationsLoader.getOperation(0x10); // STOP
        assertNotNull(stopOp, "STOP operation should be loaded");
        
        // Execute the operation
        stopOp.getExecutor().execute(registers, memory, stopOp.getOperands());
        
        // Verify PC unchanged (STOP doesn't increment PC)
        assertEquals(0x0100, registers.getPC());
    }

    @Test
    public void testDAAOperation() {
        // Test DAA - Decimal Adjust Accumulator
        registers.setRegister("A", (byte) 0x3C); // 0011 1100
        registers.setRegister("F", (byte) 0x00); // Clear all flags
        
        Operation daaOp = operationsLoader.getOperation(0x27); // DAA
        assertNotNull(daaOp, "DAA operation should be loaded");
        
        // Execute the operation
        daaOp.getExecutor().execute(registers, memory, daaOp.getOperands());
        
        // Verify result: 0x3C + 0x06 = 0x42 (decimal adjust)
        assertEquals(0x42, registers.getRegister("A") & 0xFF);
        
        // Verify flags: Z=0, N=0, H=0, C=0
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag should be 0
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }

    @Test
    public void testDAACarryOperation() {
        // Test DAA with carry
        registers.setRegister("A", (byte) 0x9A); // 1001 1010
        registers.setRegister("F", (byte) 0x00); // Clear all flags
        
        Operation daaOp = operationsLoader.getOperation(0x27); // DAA
        assertNotNull(daaOp, "DAA operation should be loaded");
        
        // Execute the operation
        daaOp.getExecutor().execute(registers, memory, daaOp.getOperands());
        
        // Verify result: 0x9A + 0x66 = 0x00 (decimal adjust with carry)
        assertEquals(0x00, registers.getRegister("A") & 0xFF);
        
        // Verify flags: Z=1, N=0, H=0, C=1
        byte flags = registers.getRegister("F");
        assertEquals(0x80, flags & 0x80); // Z flag should be 1
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testCPLOperation() {
        // Test CPL - Complement A
        registers.setRegister("A", (byte) 0xF0); // 1111 0000
        registers.setRegister("F", (byte) 0x00); // Clear all flags
        
        Operation cplOp = operationsLoader.getOperation(0x2F); // CPL
        assertNotNull(cplOp, "CPL operation should be loaded");
        
        // Execute the operation
        cplOp.getExecutor().execute(registers, memory, cplOp.getOperands());
        
        // Verify result: ~0xF0 = 0x0F (complement)
        assertEquals(0x0F, registers.getRegister("A") & 0xFF);
        
        // Verify flags: N=1, H=1, Z and C unchanged
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag unchanged (0)
        assertEquals(0x40, flags & 0x40); // N flag should be 1
        assertEquals(0x20, flags & 0x20); // H flag should be 1
        assertEquals(0x00, flags & 0x10); // C flag unchanged (0)
    }

    @Test
    public void testSCFOperation() {
        // Test SCF - Set Carry Flag
        registers.setRegister("F", (byte) 0x00); // Clear all flags
        
        Operation scfOp = operationsLoader.getOperation(0x37); // SCF
        assertNotNull(scfOp, "SCF operation should be loaded");
        
        // Execute the operation
        scfOp.getExecutor().execute(registers, memory, scfOp.getOperands());
        
        // Verify flags: C=1, N=0, H=0, Z unchanged
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag unchanged (0)
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testCCFOperation() {
        // Test CCF - Complement Carry Flag (carry was clear)
        registers.setRegister("F", (byte) 0x00); // Clear all flags
        
        Operation ccfOp = operationsLoader.getOperation(0x3F); // CCF
        assertNotNull(ccfOp, "CCF operation should be loaded");
        
        // Execute the operation
        ccfOp.getExecutor().execute(registers, memory, ccfOp.getOperands());
        
        // Verify flags: C=1, N=0, H=0, Z unchanged
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag unchanged (0)
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x10, flags & 0x10); // C flag should be 1
    }

    @Test
    public void testCCFClearCarryOperation() {
        // Test CCF - Complement Carry Flag (carry was set)
        registers.setRegister("F", (byte) 0x10); // Set C flag
        
        Operation ccfOp = operationsLoader.getOperation(0x3F); // CCF
        assertNotNull(ccfOp, "CCF operation should be loaded");
        
        // Execute the operation
        ccfOp.getExecutor().execute(registers, memory, ccfOp.getOperands());
        
        // Verify flags: C=0, N=0, H=0, Z unchanged
        byte flags = registers.getRegister("F");
        assertEquals(0x00, flags & 0x80); // Z flag unchanged (0)
        assertEquals(0x00, flags & 0x40); // N flag should be 0
        assertEquals(0x00, flags & 0x20); // H flag should be 0
        assertEquals(0x00, flags & 0x10); // C flag should be 0
    }
}