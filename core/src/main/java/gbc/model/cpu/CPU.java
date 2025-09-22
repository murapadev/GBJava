package gbc.model.cpu;

import gbc.model.memory.Memory;

public class CPU {
    private static final boolean DEBUG_LOG = Boolean.getBoolean("gbc.cpu.debug");

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

    // Track whether last conditional control-flow instruction took its branch
    private boolean lastConditionTaken;

    // HALT state
    private boolean halted = false;
    private boolean haltBugTriggered = false;
    private boolean skipNextInterrupt = false;

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
        halted = false;
        haltBugTriggered = false;
        skipNextInterrupt = false;
        lastConditionTaken = false;
    }

    public int executeCycle() {
        // Don't execute if no cartridge is loaded
        if (!isCartridgeLoaded()) {
            return 0;
        }

        handleInterrupts();
        int cyclesExecuted = 0;

        if (!halted) {
            int opcode = fetchByte();

            // FIXME: Add instruction debugging to detect problematic opcodes
            // Track the last few opcodes executed to identify freeze causes
            // Temporarily disabled for performance
            // if (cycles % 10000 == 0) { // Every 10000 cycles (reduced frequency)
            // System.out.println(String.format("PC: 0x%04X, Opcode: 0x%02X, Cycles: %d",
            // (int) registers.getPC(), opcode, cycles));
            // }

            // FIXME: Add better debugging - enable this temporarily to debug freeze
            if (DEBUG_LOG && cycles % 1000 == 0) { // Every 1000 cycles
                System.out.println(String.format("DEBUG: PC=0x%04X, Opcode=0x%02X, SP=0x%04X, Cycles=%d",
                        (int) registers.getPC(), opcode, (int) registers.getSP(), cycles));
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
        } else {
            cyclesExecuted = 4; // HALT consumes 4 cycles
        }

        // Step peripherals with base cycles (before double-speed multiplication)
        memory.stepPeripherals(cyclesExecuted);

        cycles += cyclesExecuted;

        // In double speed mode, CPU runs at 2x speed, so cycles are doubled for timing
        if (doubleSpeedMode) {
            cyclesExecuted *= 2;
        }

        return cyclesExecuted;
    }

    private boolean isCartridgeLoaded() {
        // Check if memory has a cartridge loaded
        return memory.isCartridgeLoaded();
    }

    private void handleInterrupts() {
        // Handle EI delayed effect
        if (imePending) {
            ime = true;
            imePending = false;
            if (haltBugTriggered) {
                skipNextInterrupt = true;
                haltBugTriggered = false;
            }
        }

        // Check for interrupts if IME is enabled
        if (ime) {
            if (skipNextInterrupt) {
                skipNextInterrupt = false;
                return;
            }
            if (interruptions.handleInterrupts()) {
                halted = false;
            }
        }
    }

    private void executeInstruction(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            operation.perform(registers, memory);
            // opcodeLog.append(String.format("PC: 0x%04X, Opcode: 0x%02X\n", (int)
            // registers.getPC(), opcode));
        } else {
            // FIXME: Handle missing operations to prevent freeze
            System.err.println(String.format("ERROR: No operation found for opcode 0x%02X at PC=0x%04X",
                    opcode, (int) registers.getPC()));
            // At least advance PC to prevent infinite loop
            registers.incrementPC();
        }
    }

    private void executeCBInstruction(int cbOpcode) {
        Operation operation = operationsLoader.getCbOperation(cbOpcode);
        if (operation != null) {
            operation.perform(registers, memory);
            // opcodeLog.append(String.format("PC: 0x%04X, CB Opcode: 0x%02X\n", (int)
            // registers.getPC(), cbOpcode));
        } else {
            // FIXME: Handle missing CB operations to prevent freeze
            System.err.println(String.format("ERROR: No CB operation found for opcode 0x%02X at PC=0x%04X",
                    cbOpcode, (int) registers.getPC()));
            // At least advance PC to prevent infinite loop
            registers.incrementPC();
        }
    }

    private int updateCycles(int opcode) {
        Operation operation = operationsLoader.getOperation(opcode);
        if (operation != null) {
            // If multiple cycle variants exist (conditional), select based on branch taken
            if (operation.getCycles().size() > 1) {
                return lastConditionTaken ? operation.getCycles().get(1) : operation.getCycles().get(0);
            }
            return operation.getCycles().get(0);
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

    public boolean wasLastConditionTaken() {
        return lastConditionTaken;
    }

    public void setLastConditionTaken(boolean taken) {
        this.lastConditionTaken = taken;
    }

    public boolean isHalted() {
        return halted;
    }

    public void setHalted(boolean halted) {
        this.halted = halted;
    }

    public boolean isHaltBugTriggered() {
        return haltBugTriggered;
    }

    public void setHaltBugTriggered(boolean haltBugTriggered) {
        this.haltBugTriggered = haltBugTriggered;
    }

    // Other methods...
}
