package gbc.model.cpu;

import gbc.model.memory.Memory;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CPU {
    private static final Logger LOGGER = Logger.getLogger(CPU.class.getName());
    private static final boolean DEBUG_LOG = Boolean.getBoolean("gbc.cpu.debug");

    private Registers registers;
    private Memory memory;
    private Interruptions interruptions;
    private OperationsLoader operationsLoader;

    private static final boolean TRACE_ENABLED = Boolean.getBoolean("gbc.cpu.trace");
    private static final int TRACE_START = Integer.getInteger("gbc.cpu.trace.start", 0);
    private static final int TRACE_END = Integer.getInteger("gbc.cpu.trace.end", 0xFFFF);
    private static final long TRACE_LIMIT = Long.getLong("gbc.cpu.trace.limit", Long.MAX_VALUE);
    private long traceCount = 0;

    private int cycles;
    private StringBuilder opcodeLog;

    // CGB Dual-Speed Mode
    private boolean doubleSpeedMode; // true = 8.388608 MHz, false = 4.194304 MHz
    private boolean prepareSpeedSwitch; // KEY1 prepare bit

    // Interrupt handling
    private boolean ime; // Interrupt Master Enable flag
    private int imeEnableDelay; // EI has delayed effect - enables IME after next instruction

    // Track whether last conditional control-flow instruction took its branch
    private boolean lastConditionTaken;

    // HALT state
    private boolean halted = false;
    private boolean haltBugTriggered = false;
    private boolean haltBugPendingFetch = false;
    private boolean haltBugDeferredIncrement = false;

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
        this.imeEnableDelay = 0;

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
        imeEnableDelay = 0;
        halted = false;
        haltBugTriggered = false;
        haltBugPendingFetch = false;
        haltBugDeferredIncrement = false;
        lastConditionTaken = false;
    }

    public int executeCycle() {
        // Don't execute if no cartridge is loaded
        if (!isCartridgeLoaded()) {
            return 0;
        }

        int interruptCycles = handleInterrupts();
        if (interruptCycles > 0) {
            memory.stepPeripherals(interruptCycles);
            cycles += interruptCycles;
            return interruptCycles;
        }
        int cyclesExecuted = 0;
        boolean executedInstruction = false;

        if (!halted) {
            int instructionPc = registers.getPC();
            int opcode = fetchByte();

            if (DEBUG_LOG && cycles % 1000 == 0) { // Every 1000 cycles
                LOGGER.log(Level.FINE, () -> String.format("DEBUG: PC=0x%04X, Opcode=0x%02X, SP=0x%04X, Cycles=%d",
                        registers.getPC() & 0xFFFF, opcode, registers.getSP() & 0xFFFF, cycles));
            }

            boolean cbPrefixed = opcode == 0xCB;
            int cbOpcode = -1;
            Operation operation;

            if (cbPrefixed) {
                cbOpcode = fetchByte();
                operation = operationsLoader.getCbOperation(cbOpcode);
            } else {
                operation = operationsLoader.getOperation(opcode);
            }

            int baseCycles;
            if (operation != null && !operation.getCycles().isEmpty()) {
                baseCycles = operation.getCycles().get(0);
            } else {
                baseCycles = cbPrefixed ? 8 : 4;
            }

            cyclesExecuted = 0;

            int preCycles = Math.max(0, baseCycles - 4);
            if (preCycles > 0) {
                memory.stepPeripherals(preCycles);
                cycles += preCycles;
                cyclesExecuted += preCycles;
            }

            setLastConditionTaken(false);

            if (operation != null) {
                operation.perform(registers, memory);
            } else {
                LOGGER.log(Level.SEVERE, () -> String.format("No %s operation found for opcode 0x%02X at PC=0x%04X",
                        cbPrefixed ? "CB" : "", cbPrefixed ? cbOpcode : opcode, registers.getPC() & 0xFFFF));
            }

            int postCycles = baseCycles - preCycles;
            if (postCycles > 0) {
                memory.stepPeripherals(postCycles);
                cycles += postCycles;
                cyclesExecuted += postCycles;
            }

            int totalCycles = cbPrefixed ? updateCyclesCB(cbOpcode) : updateCycles(opcode);
            int extraCycles = Math.max(0, totalCycles - baseCycles);
            if (extraCycles > 0) {
                memory.stepPeripherals(extraCycles);
                cycles += extraCycles;
                cyclesExecuted += extraCycles;
            }

            if (TRACE_ENABLED && traceCount < TRACE_LIMIT) {
                int pcToReport = instructionPc & 0xFFFF;
                if (pcToReport >= TRACE_START && pcToReport <= TRACE_END) {
                    traceCount++;
                    StringBuilder sb = new StringBuilder();
                    sb.append("[CPU ")
                            .append(cycles)
                            .append("T] PC=")
                            .append(String.format("%04X", pcToReport))
                            .append(" OP=")
                            .append(String.format("%02X", opcode));
                    if (cbPrefixed) {
                        sb.append(" CB=")
                                .append(String.format("%02X", cbOpcode));
                    }
                    sb.append(" base=")
                            .append(baseCycles)
                            .append(" extra=")
                            .append(extraCycles)
                            .append(" total=")
                            .append(baseCycles + extraCycles)
                            .append(" IME=")
                            .append(ime ? 1 : 0)
                            .append(" HALT=")
                            .append(halted ? 1 : 0);
                    LOGGER.log(Level.FINE, sb::toString);
                }
            }

            executedInstruction = true;
        } else {
            cyclesExecuted = 4; // HALT consumes 4 cycles
            memory.stepPeripherals(cyclesExecuted);
            cycles += cyclesExecuted;
        }

        if (executedInstruction) {
            finalizeInstructionExecution();
        }

        return cyclesExecuted;
    }

    private boolean isCartridgeLoaded() {
        // Check if memory has a cartridge loaded
        return memory.isCartridgeLoaded();
    }

    private int handleInterrupts() {
        // Handle EI delayed effect
        boolean pendingInterrupt = interruptions.hasPendingInterrupt();

        // If interrupts are disabled, we still need to wake from HALT when a request
        // arrives
        if (!ime) {
            if (halted && pendingInterrupt) {
                halted = false;
            }
            return 0;
        }

        if (pendingInterrupt && interruptions.handleInterrupts()) {
            halted = false;
            return 20; // 5 machine cycles
        }
        return 0;
    }

    private int updateCycles(int opcode) {
        if (opcode == 0xCB) {
            int cbOpcode = memory.readByte(registers.getPC() - 1); // Reread CB opcode
            return updateCyclesCB(cbOpcode);
        }
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
        if (haltBugDeferredIncrement) {
            registers.incrementPC();
            haltBugDeferredIncrement = false;
        }

        int pc = registers.getPC();
        int value = memory.readByte(pc) & 0xFF;

        registers.incrementPC();

        if (haltBugPendingFetch) {
            registers.setPC((registers.getPC() - 1) & 0xFFFF);
            haltBugPendingFetch = false;
            haltBugDeferredIncrement = true;
            haltBugTriggered = false;
        }

        return value;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public void addCycles(int cycles) {
        this.cycles += cycles;
        memory.stepPeripherals(cycles);
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
        return imeEnableDelay > 0;
    }

    public void setImePending(boolean imePending) {
        if (imePending) {
            imeEnableDelay = 2;
        } else {
            imeEnableDelay = 0;
        }
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
        this.haltBugPendingFetch = haltBugTriggered;
        if (!haltBugTriggered) {
            haltBugDeferredIncrement = false;
        }
    }

    public void step(int tCycles) {
        memory.stepPeripherals(tCycles);
        this.cycles += tCycles;
    }

    private void finalizeInstructionExecution() {
        if (imeEnableDelay > 0) {
            imeEnableDelay--;
            if (imeEnableDelay == 0) {
                ime = true;
            }
        }
    }

    // Other methods...
}
