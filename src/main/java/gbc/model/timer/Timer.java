package gbc.model.timer;

import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.event.EmulatorEvent;
import gbc.model.event.EventBus;

/**
 * Game Boy timer subsystem.
 * Manages the 16-bit divider, TIMA, TMA, TAC and overflow/reload state machine.
 */
public class Timer {
    // TODO: Verify DIV/TIMA behavior on TAC writes, overflow reload timing, and
    // double-speed edge cases.
    private static final Logger LOGGER = Logger.getLogger(Timer.class.getName());

    private final EventBus eventBus;

    // Timer registers
    private int divider; // full 16-bit divider incremented every T-cycle
    private int tima; // TIMA (0xFF05)
    private int tma; // TMA (0xFF06)
    private int tac; // TAC (0xFF07)

    private enum TimerState {
        NORMAL,
        OVERFLOW_DELAY,
        RELOAD_ACTIVE
    }

    private static final boolean TIMER_TRACE = Boolean.getBoolean("gbc.timer.trace");
    private static final long TIMER_TRACE_THRESHOLD = Long.getLong("gbc.timer.trace.start", 0L);
    private static final boolean TIMER_TRACE_INCREMENTS = Boolean.getBoolean("gbc.timer.trace.increments");
    private static final long TIMER_TRACE_LIMIT = Long.getLong("gbc.timer.trace.limit", Long.MAX_VALUE);
    private static final boolean TIMER_TRACE_POST_DIV = Boolean.getBoolean("gbc.timer.trace.postDiv");
    private static final int TIMER_TRACE_POST_DIV_LENGTH = Integer.getInteger("gbc.timer.trace.postDiv.length", 2048);
    private static final int TIMER_TRACE_POST_DIV_WINDOWS = Integer.getInteger("gbc.timer.trace.postDiv.windows", 1);

    private TimerState timerState = TimerState.NORMAL;
    private int stateCounterT;
    private boolean reloadPending;
    private boolean overflowDelayActive;
    private long totalTCycles;
    private long timerTraceCount;
    private long lastDividerIncrementCycle = -1;
    private int lastDividerIncrementTac;
    private int postDivTraceRemaining;
    private int postDivTraceBitIndex = -1;
    private long postDivTraceTriggerCycle = -1;
    private int postDivWindowsRemaining = TIMER_TRACE_POST_DIV_WINDOWS;

    // For trace context
    private PcSupplier pcSupplier;

    @FunctionalInterface
    public interface PcSupplier {
        int getPC();
    }

    public Timer(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void setPcSupplier(PcSupplier pcSupplier) {
        this.pcSupplier = pcSupplier;
    }

    // --- Register accessors ---

    public int getDivRegister() {
        return (divider >> 8) & 0xFF;
    }

    public int getDividerRaw() {
        return divider & 0xFFFF;
    }

    public int getTima() {
        return tima & 0xFF;
    }

    public int getTma() {
        return tma & 0xFF;
    }

    public int getTac() {
        return tac & 0xFF;
    }

    // --- Register writes ---

    public void writeDIV() {
        int oldDivider = divider & 0xFFFF;
        boolean oldSignal = isTimerSignalActive(oldDivider, tac);
        divider = 0;
        boolean newSignal = isTimerSignalActive(divider & 0xFFFF, tac);
        if (oldSignal && !newSignal) {
            incrementTIMA("div");
        }
        activatePostDivTrace();
        traceTimer("write DIV (reset)");
    }

    public void writeTIMA(int value) {
        int newTima = value & 0xFF;
        if (reloadPending) {
            if (timerState == TimerState.OVERFLOW_DELAY) {
                tima = newTima;
                reloadPending = false;
                timerState = TimerState.NORMAL;
                overflowDelayActive = false;
                traceTimer(String.format("write TIMA=%02X (cancelled reload in OVERFLOW_DELAY)", newTima));
                return;
            } else if (timerState == TimerState.RELOAD_ACTIVE) {
                traceTimer(String.format("write TIMA=%02X (ignored - in RELOAD_ACTIVE)", newTima));
                return;
            }
        }
        tima = newTima;
        traceTimer(String.format("write TIMA=%02X", newTima));
    }

    public void writeTMA(int value) {
        tma = value & 0xFF;
        if (reloadPending && timerState == TimerState.RELOAD_ACTIVE) {
            tima = tma;
            traceTimer(String.format("write TMA=%02X during RELOAD_ACTIVE - also copied to TIMA", tma & 0xFF));
        } else {
            traceTimer(String.format("write TMA=%02X", tma & 0xFF));
        }
    }

    public void writeTAC(int value) {
        int oldTac = tac;
        boolean oldSignal = isTimerSignalActive(divider & 0xFFFF, oldTac);
        tac = value & 0xFF;
        boolean newSignal = isTimerSignalActive(divider & 0xFFFF, tac);
        if (oldSignal && !newSignal) {
            incrementTIMA("tac");
        }
        traceTimer(String.format("write TAC=%02X", tac & 0xFF));
    }

    // --- Step (called once per T-cycle) ---

    /**
     * Advances the timer by one T-cycle.
     * 
     * @return interrupt flag bits to OR into IF, or 0 if none.
     */
    public void step() {
        int currentDivider = divider & 0xFFFF;
        boolean oldSignal = isTimerSignalActive(currentDivider, tac);

        divider = (divider + 1) & 0xFFFF;

        int newDivider = divider & 0xFFFF;
        boolean newSignal = isTimerSignalActive(newDivider, tac);

        tracePostDivCycle(currentDivider, newDivider, oldSignal, newSignal);

        if (oldSignal && !newSignal) {
            incrementTIMA("divider");
        }

        updateTimerState();
        totalTCycles++;
    }

    /**
     * Calculate cycles until next timer event (falling edge or state change).
     * Returns 0 if an event should happen immediately.
     */
    public int cyclesToNextEvent() {
        // If in overflow/reload state, we need per-cycle accuracy
        if (overflowDelayActive || timerState != TimerState.NORMAL) {
            return stateCounterT > 0 ? stateCounterT : 1;
        }

        // If timer is disabled, we only care about DIV overflow
        if ((tac & 0x04) == 0) {
            // DIV wraps every 65536 cycles
            return 0x10000 - (divider & 0xFFFF);
        }

        // Calculate cycles until next falling edge of selected timer bit
        int bitIndex = getTimerInputBit(tac);
        int mask = (1 << (bitIndex + 1)) - 1;
        int currentPos = divider & mask;
        int edgePos = 1 << bitIndex; // Position where bit goes from 1 to 0

        if (currentPos < edgePos) {
            // Bit is 0, need to wait for it to become 1, then 0
            return (edgePos << 1) - currentPos;
        } else {
            // Bit is 1, next falling edge when mask wraps
            return ((mask + 1) - currentPos) + edgePos;
        }
    }

    /**
     * Batch step the timer by multiple cycles.
     * More efficient than calling step() in a loop.
     */
    public void stepCycles(int cycles) {
        if (cycles <= 0)
            return;

        // If in special state, process cycle by cycle
        if (overflowDelayActive || timerState != TimerState.NORMAL) {
            for (int i = 0; i < cycles; i++) {
                step();
            }
            return;
        }

        // Fast path: timer disabled - just advance divider
        if ((tac & 0x04) == 0) {
            divider = (divider + cycles) & 0xFFFF;
            totalTCycles += cycles;
            return;
        }

        // Timer enabled - calculate falling edges
        int bitIndex = getTimerInputBit(tac);
        int period = 1 << (bitIndex + 1); // Full period of the selected bit

        int remaining = cycles;
        while (remaining > 0) {
            int currentDivider = divider & 0xFFFF;
            boolean currentSignal = ((currentDivider >> bitIndex) & 1) != 0;

            // Calculate cycles until next falling edge
            int cyclesToEdge;
            if (currentSignal) {
                // Bit is 1, calculate cycles until it becomes 0
                int mask = period - 1;
                cyclesToEdge = period - (currentDivider & mask);
            } else {
                // Bit is 0, need to wait for 1, then 0
                int mask = period - 1;
                int cyclesTo1 = (period >> 1) - (currentDivider & mask);
                if (cyclesTo1 <= 0)
                    cyclesTo1 += period;
                cyclesToEdge = cyclesTo1 + (period >> 1);
            }

            if (cyclesToEdge <= remaining) {
                // Advance to edge
                divider = (divider + cyclesToEdge) & 0xFFFF;
                totalTCycles += cyclesToEdge;
                remaining -= cyclesToEdge;
                incrementTIMA("divider");
                updateTimerState();

                // If we entered a special state, finish with per-cycle processing
                if (overflowDelayActive) {
                    for (int i = 0; i < remaining; i++) {
                        step();
                    }
                    return;
                }
            } else {
                // No edge in remaining cycles, just advance
                divider = (divider + remaining) & 0xFFFF;
                totalTCycles += remaining;
                remaining = 0;
            }
        }
    }

    // --- Reset ---

    public void reset(int initialDivider) {
        divider = initialDivider;
        tima = 0;
        tma = 0;
        tac = 0;
        timerState = TimerState.NORMAL;
        stateCounterT = 0;
        reloadPending = false;
        overflowDelayActive = false;
        totalTCycles = 0;
        timerTraceCount = 0;
        lastDividerIncrementCycle = -1;
        lastDividerIncrementTac = 0;
    }

    public void setDivider(int value) {
        this.divider = value;
    }

    public long getTotalTCycles() {
        return totalTCycles;
    }

    // --- Internal helpers ---

    private int getTimerInputBit(int tacValue) {
        int select = tacValue & 0x03;
        return switch (select) {
            case 0 -> 9; // 4096 Hz
            case 1 -> 3; // 262144 Hz
            case 2 -> 5; // 65536 Hz
            case 3 -> 7; // 16384 Hz
            default -> 9;
        };
    }

    private boolean isTimerSignalActive(int dividerValue, int tacValue) {
        if ((tacValue & 0x04) == 0) {
            return false;
        }
        int bitIndex = getTimerInputBit(tacValue);
        return ((dividerValue >> bitIndex) & 0x1) != 0;
    }

    private void incrementTIMA(String source) {
        if ("divider".equals(source)) {
            if (lastDividerIncrementCycle >= 0 && (lastDividerIncrementTac & 0x04) != 0) {
                int bitIndex = getTimerInputBit(lastDividerIncrementTac);
                long expectedDelta = 1L << (bitIndex + 1);
                long delta = totalTCycles - lastDividerIncrementCycle;
                if (delta != expectedDelta) {
                    traceTimer(String.format(
                            "unexpected TIMA interval: delta=%d expected=%d prevTAC=%02X", delta, expectedDelta,
                            lastDividerIncrementTac & 0xFF));
                }
            }
            lastDividerIncrementCycle = totalTCycles;
            lastDividerIncrementTac = tac & 0xFF;
        } else {
            lastDividerIncrementCycle = -1;
            lastDividerIncrementTac = tac & 0xFF;
        }

        if ((timerState == TimerState.OVERFLOW_DELAY || timerState == TimerState.RELOAD_ACTIVE) && reloadPending) {
            return;
        }

        boolean willOverflow = (tima & 0xFF) == 0xFF;
        if (TIMER_TRACE && TIMER_TRACE_INCREMENTS && totalTCycles >= TIMER_TRACE_THRESHOLD) {
            StringBuilder sb = new StringBuilder("increment TIMA");
            if (source != null) {
                sb.append(" [").append(source).append(']');
            }
            if (willOverflow) {
                sb.append(" -> overflow");
            }
            traceTimer(sb.toString());
        }

        if (willOverflow) {
            tima = 0;
            timerState = TimerState.OVERFLOW_DELAY;
            reloadPending = true;
            overflowDelayActive = true;
            stateCounterT = 4;
            traceTimer("overflow -> enter OVERFLOW_DELAY");
        } else {
            tima = (tima + 1) & 0xFF;
        }
    }

    private void updateTimerState() {
        if (!overflowDelayActive) {
            return;
        }

        if (stateCounterT > 0) {
            stateCounterT--;
        }

        if (timerState == TimerState.OVERFLOW_DELAY && stateCounterT == 0) {
            timerState = TimerState.RELOAD_ACTIVE;
            stateCounterT = 4;

            if (reloadPending) {
                tima = tma & 0xFF;
                eventBus.publish(new EmulatorEvent.InterruptRequest(0x04));
                traceTimer(String.format("OVERFLOW_DELAY complete -> reloaded TIMA=%02X, req INT", tima));
            } else {
                traceTimer("OVERFLOW_DELAY complete -> reload cancelled");
            }
        } else if (timerState == TimerState.RELOAD_ACTIVE && stateCounterT == 0) {
            timerState = TimerState.NORMAL;
            overflowDelayActive = false;
            reloadPending = false;
            traceTimer("RELOAD_ACTIVE complete");
        }
    }

    private void traceTimer(String event) {
        if (!TIMER_TRACE || totalTCycles < TIMER_TRACE_THRESHOLD || timerTraceCount >= TIMER_TRACE_LIMIT) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[TIMER ")
                .append(totalTCycles)
                .append("T] ")
                .append(event)
                .append(" (DIV=")
                .append(String.format("%02X", getDivRegister()))
                .append(" TIMA=")
                .append(String.format("%02X", tima & 0xFF))
                .append(" TMA=")
                .append(String.format("%02X", tma & 0xFF))
                .append(" TAC=")
                .append(String.format("%02X", tac & 0xFF))
                .append(")");

        if (pcSupplier != null) {
            sb.append(" PC=").append(String.format("%04X", pcSupplier.getPC() & 0xFFFF));
        }

        LOGGER.log(Level.FINER, sb::toString);
        timerTraceCount++;
    }

    private void tracePostDivMessage(String event) {
        if (!TIMER_TRACE_POST_DIV) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[TIMER ").append(totalTCycles).append("T] ").append(event)
                .append(" (DIV=").append(String.format("%02X", getDivRegister()))
                .append(" TIMA=").append(String.format("%02X", tima & 0xFF))
                .append(" TAC=").append(String.format("%02X", tac & 0xFF))
                .append(")");
        if (pcSupplier != null) {
            sb.append(" PC=").append(String.format("%04X", pcSupplier.getPC() & 0xFFFF));
        }
        LOGGER.log(Level.FINER, sb::toString);
    }

    private void tracePostDivCycle(int oldDivider, int newDivider, boolean oldSignal, boolean newSignal) {
        if (!TIMER_TRACE_POST_DIV || postDivTraceRemaining <= 0) {
            return;
        }

        long cycleNumber = totalTCycles + 1;
        long cyclesSinceTrigger = postDivTraceTriggerCycle >= 0 ? (cycleNumber - postDivTraceTriggerCycle) : -1;
        boolean fallingEdge = oldSignal && !newSignal;

        StringBuilder sb = new StringBuilder();
        sb.append("[TIMER ").append(cycleNumber).append("T] post-div cycle#")
                .append(cyclesSinceTrigger >= 0 ? cyclesSinceTrigger : -1)
                .append(" div=").append(String.format("%04X->%04X", oldDivider & 0xFFFF, newDivider & 0xFFFF))
                .append(" bit").append(postDivTraceBitIndex)
                .append(" ").append(oldSignal ? '1' : '0').append("->").append(newSignal ? '1' : '0');
        if (fallingEdge) {
            sb.append(" FALL");
        }
        if (pcSupplier != null) {
            sb.append(" PC=").append(String.format("%04X", pcSupplier.getPC() & 0xFFFF));
        }
        LOGGER.log(Level.FINEST, sb::toString);

        postDivTraceRemaining--;
        if (postDivTraceRemaining == 0) {
            tracePostDivMessage("post-div window complete");
        }
    }

    private void activatePostDivTrace() {
        if (!TIMER_TRACE_POST_DIV) {
            postDivTraceRemaining = 0;
            postDivTraceTriggerCycle = -1;
            postDivTraceBitIndex = -1;
            postDivWindowsRemaining = 0;
            return;
        }
        if (postDivWindowsRemaining <= 0) {
            return;
        }
        postDivTraceRemaining = TIMER_TRACE_POST_DIV_LENGTH;
        postDivTraceBitIndex = getTimerInputBit(tac);
        postDivTraceTriggerCycle = totalTCycles;
        postDivWindowsRemaining--;
        tracePostDivMessage(
                String.format("post-div window armed length=%d bit=%d", postDivTraceRemaining, postDivTraceBitIndex));
    }
}
