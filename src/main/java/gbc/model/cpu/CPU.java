package gbc.model.cpu;

import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.memory.Memory;
import gbc.model.trace.TraceGenerator;

public class CPU {
    // TODO: Audit STOP/HALT/IME timing and CGB speed-switch side effects for cycle
    // accuracy.
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
    private int peripheralCycleRemainder;
    private StringBuilder opcodeLog;

    // CGB Dual-Speed Mode
    private boolean doubleSpeedMode; // true = 8.388608 MHz, false = 4.194304 MHz
    private boolean prepareSpeedSwitch; // KEY1 prepare bit

    // Interrupt handling
    private boolean ime; // Interrupt Master Enable flag
    private int imeEnableDelay; // EI has delayed effect - enables IME after next instruction
    private int interruptDispatchState = 0; // 0 = Idle, 1-5 = Dispatching

    // Track whether last conditional control-flow instruction took its branch
    private boolean lastConditionTaken;

    // HALT state
    private boolean halted = false;
    private boolean haltBugTriggered = false;

    // M-cycle interleaving: tracks cycles consumed by memory access callbacks
    private int mcycleCallbackCycles;
    private int currentInstructionCycles; // total cycles executed for the current instruction
    private TraceGenerator trace;
    private boolean invalidTraceDumped;

    // Cached operation cycles to avoid re-lookup in updateCycles
    private Operation cachedOperation;

    // Reusable M-cycle callback to avoid lambda allocation per instruction
    private final Runnable mcycleCallback = () -> {
        stepPeripheralsForCpuCycles(4);
        cycles += 4;
        currentInstructionCycles += 4;
        mcycleCallbackCycles += 4;
    };

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
                sb.append(String.format("%02X ", memory.peekByte(a)));
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
            sb.append(String.format("%04X: %04X\n", addr, memory.peekChar(addr)));
        }
        return sb.toString();
    }

    public String dumpRegisters() {
        return registers.toString();
    }

    public void reset() {
        // Reset with hardware-specific values from Memory
        gbc.model.HardwareType hwType = memory.getHardwareType();
        registers.reset(hwType);
        memory.reset();
        interruptions.reset();
        cycles = 0;
        peripheralCycleRemainder = 0;

        // Reset CGB dual-speed state
        doubleSpeedMode = false;
        prepareSpeedSwitch = false;

        // Reset interrupt flags
        ime = false;
        imeEnableDelay = 0;
        halted = false;
        haltBugTriggered = false;
        lastConditionTaken = false;
        interruptDispatchState = 0;
    }

    public int executeCycle() {
        // Don't execute if no cartridge is loaded
        if (!isCartridgeLoaded()) {
            return 0;
        }

        int interruptCycles = handleInterrupts();
        if (interruptCycles > 0) {
            stepPeripheralsForCpuCycles(interruptCycles);
            cycles += interruptCycles;
            return interruptCycles;
        }
        currentInstructionCycles = 0;
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

            int fetchCycles = cbPrefixed ? 8 : 4;

            // M-cycle granular stepping: step peripherals for opcode fetch M-cycle
            stepPeripheralsForCpuCycles(4);
            cycles += 4;
            currentInstructionCycles += 4;

            // For CB-prefixed: the CB opcode fetch is another M-cycle
            if (cbPrefixed) {
                stepPeripheralsForCpuCycles(4);
                cycles += 4;
                currentInstructionCycles += 4;
            }

            setLastConditionTaken(false);

            // Enable M-cycle interleaving: each memory access during the operation
            // will step peripherals for 4 T-cycles via the mcycleCallback
            mcycleCallbackCycles = 0;
            memory.setMcycleCallback(mcycleCallback);

            // Cache operation for cycle lookup
            cachedOperation = operation;

            if (operation != null) {
                operation.perform(registers, memory);
            } else {
                final int finalCbOpcode = cbOpcode;
                LOGGER.log(Level.SEVERE, () -> String.format("No %s operation found for opcode 0x%02X at PC=0x%04X",
                        cbPrefixed ? "CB" : "", cbPrefixed ? finalCbOpcode : opcode, registers.getPC() & 0xFFFF));
            }

            // Disable M-cycle callback
            memory.setMcycleCallback(null);

            // Determine total cycles (accounting for conditional branches)
            int totalCycles = cbPrefixed ? updateCyclesCB(cbOpcode) : updateCycles(opcode);

            // Step remaining "internal" M-cycles not covered by fetch or memory accesses
            int remainingCycles = Math.max(0, totalCycles - fetchCycles - mcycleCallbackCycles);
            for (int m = 0; m < remainingCycles; m += 4) {
                int chunk = Math.min(4, remainingCycles - m);
                stepPeripheralsForCpuCycles(chunk);
                cycles += chunk;
                currentInstructionCycles += chunk;
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
                            .append(" memAccess=")
                            .append(mcycleCallbackCycles)
                            .append(" total=")
                            .append(totalCycles)
                            .append(" IME=")
                            .append(ime ? 1 : 0)
                            .append(" HALT=")
                            .append(halted ? 1 : 0);
                    LOGGER.log(Level.FINE, sb::toString);
                }
            }

            executedInstruction = true;
        } else {
            currentInstructionCycles = 4; // HALT consumes 4 cycles
            stepPeripheralsForCpuCycles(currentInstructionCycles);
            cycles += currentInstructionCycles;
        }

        if (executedInstruction) {
            finalizeInstructionExecution();
        }

        return currentInstructionCycles;
    }

    private void stepPeripheralsForCpuCycles(int cpuCycles) {
        if (cpuCycles <= 0) {
            return;
        }
        int stepCycles = cpuCycles;
        if (doubleSpeedMode) {
            peripheralCycleRemainder += cpuCycles;
            stepCycles = peripheralCycleRemainder / 2;
            peripheralCycleRemainder %= 2;
        }
        if (stepCycles > 0) {
            memory.stepPeripherals(stepCycles);
        }
        if (doubleSpeedMode) {
            int fastCycles = cpuCycles - stepCycles;
            if (fastCycles > 0) {
                memory.stepFastPeripherals(fastCycles);
            }
        }
    }

    private boolean isCartridgeLoaded() {
        // Check if memory has a cartridge loaded
        return memory.isCartridgeLoaded();
    }

    private int handleInterrupts() {
        boolean pendingInterrupt = interruptions.hasPendingInterrupt();
        boolean dmaBlocksStack = memory.isDmaActive() && memory.isOamDmaBusLocked(registers.getSP());

        if (dmaBlocksStack) {
            // OAM DMA blocks stack access; defer interrupt dispatch until DMA completes.
            if (halted && pendingInterrupt) {
                halted = false;
            }
            // If we are mid-dispatch, stall in place (consume a dispatch M-cycle) until DMA
            // ends.
            if (interruptDispatchState != 0) {
                return 4;
            }
            return 0;
        }

        // Interrupt Dispatch State Machine
        if (interruptDispatchState == 0) {
            // If interrupts are disabled, we still need to wake from HALT when a request
            // arrives
            if (!ime) {
                if (halted && pendingInterrupt) {
                    halted = false;
                }
                return 0;
            }

            if (pendingInterrupt) {
                // Start dispatch sequence. Vector is latched after PC high is pushed (M3).
                latchedInterruptVector = null;
                interruptDispatchState = 1;
                halted = false;

                // M1: Internal delay (2 NOPs) - effectively 4 T-cycles
                return 4;
            }
        } else {
            switch (interruptDispatchState) {
                case 1: // M2: Internal delay (2 NOPs)
                    interruptDispatchState = 2;
                    return 4;
                case 2: // M3: Decrement SP, Write PC High
                    registers.setSP((char) ((registers.getSP() - 1) & 0xFFFF));
                    memory.writeByte(registers.getSP(), (registers.getPC() >> 8) & 0xFF);
                    // Latch interrupt vector after pushing PC high (end of M3)
                    latchedInterruptVector = interruptions.getInterruptVector();
                    interruptDispatchState = 3;
                    return 4;
                case 3: // M4: Decrement SP, Write PC Low
                    registers.setSP((char) ((registers.getSP() - 1) & 0xFFFF));
                    memory.writeByte(registers.getSP(), registers.getPC() & 0xFF);
                    interruptDispatchState = 4;
                    return 4;
                case 4: // M5: Use latched vector, Set PC, Disable IME, Clear IF bit
                    Integer vector = latchedInterruptVector;
                    if (vector != null) {
                        registers.setPC((char) (vector & 0xFFFF));
                        interruptions.clearInterruptFlag(vector);
                    } else {
                        // If interrupt was canceled mid-dispatch, PC is forced to 0x0000.
                        registers.setPC((char) 0x0000);
                    }
                    ime = false;
                    latchedInterruptVector = null;
                    interruptDispatchState = 0;
                    return 4;
            }
        }

        return 0;
    }

    private boolean debugTraceEnabled = false;
    private Integer latchedInterruptVector = null;

    private int updateCycles(int opcode) {
        if (debugTraceEnabled) {
            String msg = String.format("[TRACE] PC=%04X Op=%02X SP=%04X IE=%02X IF=%02X",
                    registers.getPC(), opcode, registers.getSP(),
                    memory.peekByte(0xFFFF), memory.peekByte(0xFF0F));
            LOGGER.fine(msg);
            // Limit trace to avoid massive log files
            if (registers.getPC() == 0x211) {
                debugTraceEnabled = false; // Stop if we get back to test loop
            }
        }
        if (opcode == 0xCB) {
            int cbOpcode = memory.peekByte(registers.getPC() - 1); // Reread CB opcode without side effects
            return updateCyclesCB(cbOpcode);
        }
        // Use cached operation to avoid re-lookup
        if (cachedOperation != null) {
            // If multiple cycle variants exist (conditional), select based on branch taken
            if (cachedOperation.getCycles().size() > 1) {
                return lastConditionTaken ? cachedOperation.getCycles().get(1) : cachedOperation.getCycles().get(0);
            }
            return cachedOperation.getCycles().get(0);
        }
        return 4; // Default cycle count for unknown operations
    }

    private int updateCyclesCB(int cbOpcode) {
        // Use cached operation to avoid re-lookup
        if (cachedOperation != null) {
            return cachedOperation.getCycles().get(0);
        }
        return 8; // Default cycle count for unknown CB operations
    }

    private int fetchByte() {
        if (debugTraceEnabled) {
            LOGGER.fine("[CPU] Fetching from PC=" + String.format("%04X", registers.getPC()));
        }
        int value = memory.readByte(registers.getPC());

        if (haltBugTriggered) {
            // Halt bug: PC is not incremented for this fetch
            haltBugTriggered = false;
        } else {
            registers.incrementPC();
        }

        return value & 0xFF;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public void addCycles(int cycles) {
        this.cycles += cycles;
        stepPeripheralsForCpuCycles(cycles);
    }

    /**
     * Tick one internal M-cycle (4 T-cycles) during instruction execution.
     * Used by operations that have internal cycles between memory accesses
     * (e.g., CALL has an internal cycle between reading the target address
     * and pushing the return address to the stack).
     */
    public void tickInternalMcycle() {
        stepPeripheralsForCpuCycles(4);
        cycles += 4;
        currentInstructionCycles += 4;
        mcycleCallbackCycles += 4; // Count toward total so remaining calculation is correct
    }

    // CGB Dual-Speed Mode methods
    public boolean isDoubleSpeedMode() {
        return doubleSpeedMode;
    }

    public void setDoubleSpeedMode(boolean doubleSpeedMode) {
        this.doubleSpeedMode = doubleSpeedMode;
        peripheralCycleRemainder = 0;
    }

    public boolean isPrepareSpeedSwitch() {
        return prepareSpeedSwitch;
    }

    public void setPrepareSpeedSwitch(boolean prepareSpeedSwitch) {
        this.prepareSpeedSwitch = prepareSpeedSwitch;
    }

    // KEY1 register (0xFF4D) - Speed control
    public int readKey1() {
        // Bits 6-1 read back as 1 on CGB hardware.
        int key1 = 0x7E;
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

    public void setTrace(TraceGenerator trace) {
        this.trace = trace;
        this.invalidTraceDumped = false;
    }

    public void onInvalidOpcode(int pc, int opcode) {
        if (trace != null && !invalidTraceDumped && Boolean.getBoolean("gbc.trace.ringbuffer")) {
            trace.dumpRingBuffer();
            invalidTraceDumped = true;
            LOGGER.log(Level.WARNING, () -> String.format(
                    "Trace ring buffer dumped on invalid opcode at PC=0x%04X (op=0x%02X)",
                    pc & 0xFFFF, opcode & 0xFF));
        }
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
            // Only set the delay if not already pending
            // EI;EI sequence should not reset the delay
            if (imeEnableDelay == 0) {
                // Set to 2 because finalizeInstructionExecution() will be called at the end
                // of the EI instruction itself, decrementing it to 1. Then after the NEXT
                // instruction completes, it will decrement to 0 and enable IME.
                imeEnableDelay = 2;
            }
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
    }

    public void step(int tCycles) {
        stepPeripheralsForCpuCycles(tCycles);
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
