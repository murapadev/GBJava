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
        this.operationsLoader = new OperationsLoader();
        reset();
    }

    public void reset() {
        registers.reset();
        memory.reset();
        interrupts.reset();
        cycles = 0;
    }

    public void executeCycle() {
        handleInterrupts();
        int opcode = fetchByte();
        executeInstruction(opcode);
        updateCycles(opcode);
    }

    private void handleInterrupts() {
        interrupts.handleInterrupts();
    }

    private void executeInstruction(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            operation.perform(registers, memory);
            opcodeLog.append(String.format("PC: 0x%04X, Opcode: 0x%02X\n", (byte)registers.getPC(), opcode));
        }
    }

    private void updateCycles(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            cycles += operation.getCycles().get(0); // assuming the first cycle count is the base cycle count
        }
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