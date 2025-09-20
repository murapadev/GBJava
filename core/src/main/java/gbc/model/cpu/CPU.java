package gbc.model.cpu;

import gbc.model.memory.Memory;

public class CPU {
    private Registers registers;
    private Memory memory;
    private Interruptions interruptions;
    private OperationsLoader operationsLoader;

    private int cycles;
    private StringBuilder opcodeLog;

    // CGB Dual-Speed Mode
    private boolean doubleSpeedMode; // true = 8.388608 MHz, false = 4.194304 MHz
    private boolean prepareSpeedSwitch; // KEY1 prepare bit

    // Interrupt handling
    private boolean ime; // Interrupt Master Enable flag
    private boolean imePending; // EI has delayed effect - enables IME after next instruction

    public CPU(Memory memory) {
        this.memory = memory;
        this.registers = new Registers();
        this.interruptions = new Interruptions(memory, registers, this);
        this.opcodeLog = new StringBuilder();
        this.operationsLoader = new OperationsLoader(interruptions, this);

        // Initialize CGB dual-speed mode (starts in normal speed)
        this.doubleSpeedMode = false;
        this.prepareSpeedSwitch = false;

        // Initialize interrupt flags
        this.ime = false;
        this.imePending = false;

        // Set CPU reference in memory for I/O register access
        memory.setCPU(this);

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
        int sp = registers.getSP();
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
        interruptions.reset();
        cycles = 0;

        // Reset interrupt flags
        ime = false;
        imePending = false;
    }

    public int executeCycle() {
        handleInterrupts();
        int opcode = fetchByte();
        int cyclesExecuted = 0;

        // Special handling for STOP instruction (CGB speed switching)
        if (opcode == 0x10) { // STOP opcode
            handleStopInstruction();
            cyclesExecuted = 4; // STOP takes 4 cycles
            cycles += cyclesExecuted;
            return cyclesExecuted;
        }

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

        // In double speed mode, CPU runs at 2x speed, so cycles are doubled for timing
        if (doubleSpeedMode) {
            cyclesExecuted *= 2;
        }

        return cyclesExecuted;
    }

    private void handleInterrupts() {
        // Handle EI delayed effect
        if (imePending) {
            ime = true;
            imePending = false;
        }

        // Check for interrupts if IME is enabled
        if (ime) {
            interruptions.handleInterrupts();
        }
    }

    private void executeInstruction(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            operation.perform(registers, memory);
            // opcodeLog.append(String.format("PC: 0x%04X, Opcode: 0x%02X\n", (int)
            // registers.getPC(), opcode));
        }
    }

    private void executeCBInstruction(int cbOpcode) {
        Operation operation = operationsLoader.getCbOperation(cbOpcode);
        if (operation != null) {
            operation.perform(registers, memory);
            // opcodeLog.append(String.format("PC: 0x%04X, CB Opcode: 0x%02X\n", (int)
            // registers.getPC(), cbOpcode));
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

    // CGB Dual-Speed Mode methods
    public boolean isDoubleSpeedMode() {
        return doubleSpeedMode;
    }

    public void setDoubleSpeedMode(boolean doubleSpeedMode) {
        this.doubleSpeedMode = doubleSpeedMode;
    }

    public boolean isPrepareSpeedSwitch() {
        return prepareSpeedSwitch;
    }

    public void setPrepareSpeedSwitch(boolean prepareSpeedSwitch) {
        this.prepareSpeedSwitch = prepareSpeedSwitch;
    }

    // KEY1 register (0xFF4D) - Speed control
    public int readKey1() {
        int key1 = 0;
        if (doubleSpeedMode)
            key1 |= 0x80; // Current speed mode (1 = double speed)
        if (prepareSpeedSwitch)
            key1 |= 0x01; // Prepare bit
        return key1;
    }

    public void writeKey1(int value) {
        // Only the prepare bit (bit 0) is writable
        prepareSpeedSwitch = (value & 0x01) != 0;
    }

    // Handle STOP instruction for speed switching
    public void handleStopInstruction() {
        if (prepareSpeedSwitch) {
            // Switch speed mode
            doubleSpeedMode = !doubleSpeedMode;
            prepareSpeedSwitch = false; // Clear prepare bit

            // Reset DIV register when switching speeds
            memory.writeByte(0xFF04, (byte) 0x00);

            // TODO: Adjust timers and other components for new speed
        }
    }

    public String getOpcodeLog() {
        return opcodeLog.toString();
    }

    public Registers getRegisters() {
        return registers;
    }

    public Interruptions getInterruptions() {
        return interruptions;
    }

    // Interrupt methods
    public boolean isIme() {
        return ime;
    }

    public void setIme(boolean ime) {
        this.ime = ime;
    }

    public boolean isImePending() {
        return imePending;
    }

    public void setImePending(boolean imePending) {
        this.imePending = imePending;
    }

    // Other methods...
}