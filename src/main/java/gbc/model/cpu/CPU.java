package gbc.model.cpu;

import gbc.model.memory.Memory;

public class CPU {
    private Registers registers;
    private Memory memory;
    private Interruptions interrupts;
    private OperationsLoader operationsLoader;

    private int cycles;
    private StringBuilder opcodeLog;

    public CPU(Memory memory) {
        this.memory = memory;
        this.registers = new Registers();
        this.interrupts = new Interruptions(memory, registers);
        this.opcodeLog = new StringBuilder();
        this.operationsLoader = new OperationsLoader(interrupts);
        reset();
    }

    // --- Debug helpers ---
    public String dumpMemory(int start, int end) {
        int s = Math.max(0, start) & 0xFFFF;
        int e = Math.min(0xFFFF, end) & 0xFFFF;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Memory dump 0x%04X..0x%04X\n", s, e));
        for (int addr = s; addr <= e; addr += 16) {
            sb.append(String.format("%04X: ", addr));
            int lineEnd = Math.min(e, addr + 15);
            for (int a = addr; a <= lineEnd; a++) {
                sb.append(String.format("%02X ", memory.readByte(a)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String dumpStack(int bytes) {
        StringBuilder sb = new StringBuilder();
        char sp = registers.getSP();
        sb.append(String.format("Stack from SP=0x%04X (%d bytes)\n", (int) sp, bytes));
        int toRead = Math.max(0, bytes);
        for (int off = 0; off < toRead; off += 2) {
            int addr = ((sp & 0xFFFF) + off) & 0xFFFF;
            sb.append(String.format("%04X: %04X\n", addr, memory.readChar(addr)));
        }
        return sb.toString();
    }

    public String dumpRegisters() {
        return registers.toString();
    }

    public void reset() {
        registers.reset();
        memory.reset();
        interrupts.reset();
        cycles = 0;
    }

    public int executeCycle() {
        handleInterrupts();
        int opcode = fetchByte();
        int cyclesExecuted = 0;

        // Handle CB-prefixed operations
        if (opcode == 0xCB) {
            int cbOpcode = fetchByte();
            executeCBInstruction(cbOpcode);
            cyclesExecuted = updateCyclesCB(cbOpcode);
        } else {
            executeInstruction(opcode);
            cyclesExecuted = updateCycles(opcode);
        }
        
        cycles += cyclesExecuted;
        return cyclesExecuted;
    }

    private void handleInterrupts() {
        interrupts.handleInterrupts();
    }

    private void executeInstruction(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            operation.perform(registers, memory);
            opcodeLog.append(String.format("PC: 0x%04X, Opcode: 0x%02X\n", (int)registers.getPC(), opcode));
        }
    }

    private void executeCBInstruction(int cbOpcode) {
        Operation operation = operationsLoader.getCbOperation(cbOpcode);
        if (operation != null) {
            operation.perform(registers, memory);
            opcodeLog.append(String.format("PC: 0x%04X, CB Opcode: 0x%02X\n", (int)registers.getPC(), cbOpcode));
        }
    }

    private int updateCycles(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            return operation.getCycles().get(0); // assuming the first cycle count is the base cycle count
        }
        return 4; // Default cycle count for unknown operations
    }

    private int updateCyclesCB(int cbOpcode) {
        Operation operation = operationsLoader.getCbOperation(cbOpcode);
        if (operation != null) {
            return operation.getCycles().get(0); // assuming the first cycle count is the base cycle count
        }
        return 8; // Default cycle count for unknown CB operations
    }

    private int fetchByte() {
        int value = memory.readByte(registers.getPC()) & 0xFF;
        registers.incrementPC();
        return value;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public String getOpcodeLog() {
        return opcodeLog.toString();
    }

    public Registers getRegisters() {
        return registers;
    }


    // Other methods...
}