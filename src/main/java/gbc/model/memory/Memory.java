package gbc.model.memory;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;

import gbc.core.input.Controller;
import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.CPU; // Import CPU class
import gbc.model.cpu.Registers;
import gbc.model.sound.Apu;

public class Memory {
    private static final Logger LOGGER = Logger.getLogger(Memory.class.getName());
    // APU (Audio Processing Unit) register constants
    public static final int NR10 = 0xFF10; // Channel 1 sweep
    public static final int NR11 = 0xFF11; // Channel 1 length/duty
    public static final int NR12 = 0xFF12; // Channel 1 volume envelope
    public static final int NR13 = 0xFF13; // Channel 1 frequency low
    public static final int NR14 = 0xFF14; // Channel 1 frequency high
    public static final int NR20 = 0xFF15; // Unused
    public static final int NR21 = 0xFF16; // Channel 2 length/duty
    public static final int NR22 = 0xFF17; // Channel 2 volume envelope
    public static final int NR23 = 0xFF18; // Channel 2 frequency low
    public static final int NR24 = 0xFF19; // Channel 2 frequency high
    public static final int NR30 = 0xFF1A; // Channel 3 DAC enable
    public static final int NR31 = 0xFF1B; // Channel 3 length
    public static final int NR32 = 0xFF1C; // Channel 3 volume
    public static final int NR33 = 0xFF1D; // Channel 3 frequency low
    public static final int NR34 = 0xFF1E; // Channel 3 frequency high
    public static final int NR40 = 0xFF1F; // Unused
    public static final int NR41 = 0xFF20; // Channel 4 length
    public static final int NR42 = 0xFF21; // Channel 4 volume envelope
    public static final int NR43 = 0xFF22; // Channel 4 frequency
    public static final int NR44 = 0xFF23; // Channel 4 control
    public static final int NR50 = 0xFF24; // Master volume/panning
    public static final int NR51 = 0xFF25; // Channel panning
    public static final int NR52 = 0xFF26; // APU control/status

    // CGB Memory Layout
    private final byte[] videoRam0 = new byte[0x2000]; // 8KB Video RAM Bank 0 (0x8000-0x9FFF)
    private final byte[] videoRam1 = new byte[0x2000]; // 8KB Video RAM Bank 1 (0x8000-0x9FFF) - CGB only
    private final byte[] workRam0 = new byte[0x1000]; // 4KB Work RAM Bank 0 (0xC000-0xCFFF) - fixed
    private final byte[] workRam1 = new byte[0x1000]; // 4KB Work RAM Bank 1 (0xD000-0xDFFF) - switchable
    private final byte[] workRam2 = new byte[0x1000]; // 4KB Work RAM Bank 2 (0xD000-0xDFFF) - CGB only
    private final byte[] workRam3 = new byte[0x1000]; // 4KB Work RAM Bank 3 (0xD000-0xDFFF) - CGB only
    private final byte[] workRam4 = new byte[0x1000]; // 4KB Work RAM Bank 4 (0xD000-0xDFFF) - CGB only
    private final byte[] workRam5 = new byte[0x1000]; // 4KB Work RAM Bank 5 (0xD000-0xDFFF) - CGB only
    private final byte[] workRam6 = new byte[0x1000]; // 4KB Work RAM Bank 6 (0xD000-0xDFFF) - CGB only
    private final byte[] workRam7 = new byte[0x1000]; // 4KB Work RAM Bank 7 (0xD000-0xDFFF) - CGB only
    private final byte[] highRam = new byte[0x7F]; // 127B High RAM (0xFF80-0xFFFE)
    private final byte[] oam = new byte[0xA0]; // 160B OAM (Object Attribute Memory) (0xFE00-0xFE9F)
    private byte interruptEnable; // Interrupt Enable Register (0xFFFF)
    private byte interruptFlag = 0x00; // Interrupt Flag (0xFF0F)

    // Timer registers
    private int divider = 0; // full 16-bit divider incremented every T-cycle

    private int tima = 0; // TIMA (0xFF05)
    private int tma = 0; // TMA (0xFF06)
    private int tac = 0; // TAC (0xFF07)

    // Serial port registers
    private byte serialData = 0; // SB (0xFF01) - Serial Data
    private byte serialControl = 0; // SC (0xFF02) - Serial Control

    private enum TimerState {
        NORMAL,
        OVERFLOW_DELAY,   // 4 T-cycles post-overflow (Cycle A)
        RELOAD_ACTIVE     // 4 T-cycles for reload (Cycle B) - reload happens at END
    }

    private static final boolean TIMER_TRACE = Boolean.getBoolean("gbc.timer.trace");
    private static final long TIMER_TRACE_THRESHOLD = Long.getLong("gbc.timer.trace.start", 0L);
    private static final boolean TIMER_TRACE_INCREMENTS = Boolean.getBoolean("gbc.timer.trace.increments");
    private static final long TIMER_TRACE_LIMIT = Long.getLong("gbc.timer.trace.limit", Long.MAX_VALUE);
    private static final boolean TIMER_TRACE_POST_DIV = Boolean.getBoolean("gbc.timer.trace.postDiv");
    private static final int TIMER_TRACE_POST_DIV_LENGTH = Integer.getInteger("gbc.timer.trace.postDiv.length", 2048);
    private static final int TIMER_TRACE_POST_DIV_WINDOWS = Integer.getInteger("gbc.timer.trace.postDiv.windows", 1);
    private static final int MACHINE_CYCLE_T_CYCLES = 4;
    private TimerState timerState = TimerState.NORMAL;
    private int stateCounterT = 0;
    private boolean reloadPending = false;
    private boolean overflowDelayActive = false;
    private long totalTCycles = 0;
    private long timerTraceCount = 0;
    private long lastDividerIncrementCycle = -1;
    private int lastDividerIncrementTac = 0;
    private int postDivTraceRemaining = 0;
    private int postDivTraceBitIndex = -1;
    private long postDivTraceTriggerCycle = -1;
    private int postDivWindowsRemaining = TIMER_TRACE_POST_DIV_WINDOWS;

    // OAM DMA state
    // DMA takes 160 M-cycles (640 T-cycles) to transfer 160 bytes
    // During DMA, CPU can only access HRAM (0xFF80-0xFFFE)
    private boolean dmaActive = false;
    private int dmaSourceBase = 0;        // Source address base (high byte << 8)
    private int dmaBytesTransferred = 0;  // Number of bytes transferred so far
    private int dmaStartupDelay = 0;      // Startup delay in T-cycles
    private int dmaCycleCounter = 0;      // Counter for 4 T-cycle transfer pacing

    // CGB HDMA (H-Blank / General DMA)
    private int hdmaSrcHigh;
    private int hdmaSrcLow;
    private int hdmaDstHigh;
    private int hdmaDstLow;
    private boolean hdmaActive;
    private boolean hdmaHblank;
    private int hdmaBlocksRemaining;
    private static final int DMA_TRANSFER_LENGTH = 160; // 0xA0 bytes

    private static final int LCD_TOTAL_LINES = 154;
    private static final int LCD_VBLANK_START_LINE = 144;
    private static final int LCD_CYCLES_PER_LINE = 456;
    private static final int LCD_MODE_HBLANK = 0;
    private static final int LCD_MODE_VBLANK = 1;
    private static final int LCD_MODE_OAM = 2;
    private static final int LCD_MODE_TRANSFER = 3;

    private int lcdc = 0x91;
    private int stat = 0x85;
    private int scy = 0;
    private int scx = 0;
    private int ly = 0;
    private int lyc = 0;
    private int wy = 0;
    private int wx = 0;
    private int bgp = 0xFC;
    private int obp0 = 0xFF;
    private int obp1 = 0xFF;
    private final byte[] cgbBgPalette = new byte[0x40];
    private final byte[] cgbObjPalette = new byte[0x40];
    private int[][] dmgCgbPalette = new int[][] {
            { 0x7FFF, 0x5AD6, 0x294A, 0x0000 },
            { 0x7FFF, 0x5AD6, 0x294A, 0x0000 },
            { 0x7FFF, 0x5AD6, 0x294A, 0x0000 }
    };
    private int bgpi;
    private boolean bgpiAutoInc;
    private int obpi;
    private boolean obpiAutoInc;
    private int lcdCycleCounter = 0;
    private int apuCycleCounter = 0;
    private int lcdMode = LCD_MODE_VBLANK;
    private boolean lycMatch = true;

    // Cartridge and peripherals
    private Cartridge cartridge;
    private CPU cpu;
    private Apu apu;
    private Controller controller;
    private gbc.model.graphics.PPU ppu;
    private int vramBank = 0;
    private int wramBank = 1;
    private gbc.model.HardwareType hardwareType = gbc.model.HardwareType.DMG;
    private boolean hardwareOverride;
    private boolean cgbMode;
    private byte[] bootRom;
    private boolean bootRomEnabled;

    // Helper to get the DIV visible register value from the 16-bit divider
    private int getDivRegister() {
        return (divider >> 8) & 0xFF;
    }

    // PPU Direct Accessors (Bypass DMA bus lock)
    public int getLcdc() { return lcdc; }
    public int getStat() { return stat; }
    public int getScy() { return scy; }
    public int getScx() { return scx; }
    public int getLy() { return ly; }
    public int getLyc() { return lyc; }
    public int getWy() { return wy; }
    public int getWx() { return wx; }
    public int getBgp() { return bgp; }
    public int getObp0() { return obp0; }
    public int getObp1() { return obp1; }
    public int readCgbBgPaletteByte(int index) { return cgbBgPalette[index & 0x3F] & 0xFF; }
    public int readCgbObjPaletteByte(int index) { return cgbObjPalette[index & 0x3F] & 0xFF; }
    public int getBgpi() { return (bgpi & 0x3F) | (bgpiAutoInc ? 0x80 : 0); }
    public int getObpi() { return (obpi & 0x3F) | (obpiAutoInc ? 0x80 : 0); }
    public int resolveDmgCgbColor(boolean obj, int paletteIndex, int shadeIndex) {
        int idx = obj ? (1 + (paletteIndex & 0x01)) : 0;
        int[] palette = dmgCgbPalette[idx];
        int color = palette[Math.max(0, Math.min(3, shadeIndex))] & 0x7FFF;
        int r = color & 0x1F;
        int g = (color >> 5) & 0x1F;
        int b = (color >> 10) & 0x1F;
        int rr = (r * 255) / 31;
        int gg = (g * 255) / 31;
        int bb = (b * 255) / 31;
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    // Direct OAM Accessor for PPU (Bypass DMA bus lock)
    public int getOamByte(int index) {
        if (index >= 0 && index < oam.length) {
            return oam[index] & 0xFF;
        }
        return 0xFF; // Out of bounds
    }

    // Direct VRAM Accessor for PPU (Bypass DMA bus lock)
    public int getVramByte(int address) {
        int addr = address & 0xFFFF;
        if (addr < 0x8000 || addr > 0x9FFF) {
            return 0xFF;
        }
        return readVideoRam(addr);
    }

    public int getVramByteBanked(int address, int bank) {
        int addr = address & 0xFFFF;
        if (addr < 0x8000 || addr > 0x9FFF) {
            return 0xFF;
        }
        if (bank == 0) {
            return videoRam0[addr - 0x8000] & 0xFF;
        }
        return videoRam1[addr - 0x8000] & 0xFF;
    }



    // Helper to map TAC clock select to divider bit index (PanDocs mapping)
    private int getTimerInputBit(int tacValue) {
        int select = tacValue & 0x03;
        switch (select) {
            case 0 -> { // 4096 Hz
                return 9;
            }
            case 1 -> { // 262144 Hz
                return 3;
            }
            case 2 -> { // 65536 Hz
                return 5;
            }
            case 3 -> { // 16384 Hz
                return 7;
            }
            default -> {
                return 9;
            }
        }
    }

    private boolean isTimerSignalActive(int dividerValue, int tacValue) {
        if ((tacValue & 0x04) == 0) {
            return false;
        }
        int bitIndex = getTimerInputBit(tacValue);
        return ((dividerValue >> bitIndex) & 0x1) != 0;
    }

    // Increment TIMA with overflow handling (called when falling edge detected)
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

        // During overflow delay or reload active phases, don't increment TIMA
        if ((timerState == TimerState.OVERFLOW_DELAY || timerState == TimerState.RELOAD_ACTIVE) && reloadPending) {
            // TIMA is locked while reload is pending
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
            // After 4 T-cycles, we enter RELOAD_ACTIVE for another 4 T-cycles
            // Total: 8 T-cycles from overflow to reload complete
            stateCounterT = 4;  // Counts down during OVERFLOW_DELAY
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
            // Transition from OVERFLOW_DELAY to RELOAD_ACTIVE (Start of Cycle B)
            // Reload happens here, and interrupt is requested (1 M-cycle after overflow)
            timerState = TimerState.RELOAD_ACTIVE;
            stateCounterT = 4;  // Another 4 T-cycles for reload M-cycle
            
            if (reloadPending) {
                tima = tma & 0xFF;
                requestTimerInterrupt();
                traceTimer(String.format("OVERFLOW_DELAY complete -> reloaded TIMA=%02X, req INT", tima));
            } else {
                traceTimer("OVERFLOW_DELAY complete -> reload cancelled");
            }
        } else if (timerState == TimerState.RELOAD_ACTIVE && stateCounterT == 0) {
            // End of reload M-cycle (End of Cycle B)
            // Just exit the state machine
            timerState = TimerState.NORMAL;
            overflowDelayActive = false;
            reloadPending = false;
            traceTimer("RELOAD_ACTIVE complete");
        }
    }

    private void requestTimerInterrupt() {
        interruptFlag |= 0x04; // Timer interrupt
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

        if (cpu != null) {
            sb.append(" PC=")
                    .append(String.format("%04X", cpu.getRegisters().getPC() & 0xFFFF));
        }

        LOGGER.log(Level.FINER, sb::toString);
        timerTraceCount++;
    }

    private void tracePostDivMessage(String event) {
        if (!TIMER_TRACE_POST_DIV) {
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
                .append(" TAC=")
                .append(String.format("%02X", tac & 0xFF))
                .append(")");

        if (cpu != null) {
            sb.append(" PC=")
                    .append(String.format("%04X", cpu.getRegisters().getPC() & 0xFFFF));
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
        sb.append("[TIMER ")
                .append(cycleNumber)
                .append("T] post-div cycle#")
                .append(cyclesSinceTrigger >= 0 ? cyclesSinceTrigger : -1)
                .append(" div=")
                .append(String.format("%04X->%04X", oldDivider & 0xFFFF, newDivider & 0xFFFF))
                .append(" bit")
                .append(postDivTraceBitIndex)
                .append(" ")
                .append(oldSignal ? '1' : '0')
                .append("->")
                .append(newSignal ? '1' : '0');

        if (fallingEdge) {
            sb.append(" FALL");
        }

        if (cpu != null) {
            sb.append(" PC=")
                    .append(String.format("%04X", cpu.getRegisters().getPC() & 0xFFFF));
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

    private void stepLcd() {
        if ((lcdc & 0x80) == 0) {
            ly = 0;
            lcdCycleCounter = 0;
            setLcdMode(LCD_MODE_HBLANK);
            updateLycFlag();
            return;
        }

        lcdCycleCounter++;
        if (lcdCycleCounter >= LCD_CYCLES_PER_LINE) {
            lcdCycleCounter -= LCD_CYCLES_PER_LINE;
            int previousLy = ly;
            ly = (ly + 1) % LCD_TOTAL_LINES;
            if (ly == LCD_VBLANK_START_LINE) {
                requestVBlankInterrupt();
            }
            if (previousLy != ly) {
                updateLycFlag();
            }
        }

        updateLcdModeForCurrentCycle();
    }

    private void updateLcdModeForCurrentCycle() {
        int newMode;
        if ((lcdc & 0x80) == 0) {
            newMode = LCD_MODE_HBLANK;
        } else if (ly >= LCD_VBLANK_START_LINE) {
            newMode = LCD_MODE_VBLANK;
        } else if (lcdCycleCounter < 80) {
            newMode = LCD_MODE_OAM;
        } else {
            // Mode 3 duration varies based on sprites and SCX
            // Get the calculated duration from PPU's OAM search
            int mode3Duration = (ppu != null) ? ppu.getMode3Duration() : 172;
            if (lcdCycleCounter < 80 + mode3Duration) {
                newMode = LCD_MODE_TRANSFER;
            } else {
                newMode = LCD_MODE_HBLANK;
            }
        }
        setLcdMode(newMode);
        updateLycFlag();
    }

    private void setLcdMode(int newMode) {
        if (lcdMode == newMode) {
            return;
        }

        int oldMode = lcdMode;
        lcdMode = newMode;
        stat = (stat & ~0x03) | (newMode & 0x03) | 0x80;

        if (ppu != null) {
            ppu.enterMode(newMode, oldMode);
        }

        switch (newMode) {
            case LCD_MODE_HBLANK -> {
                if ((stat & 0x08) != 0) {
                    requestLcdStatInterrupt();
                }
                if (hardwareType.isCgb() && hdmaActive && hdmaHblank && ly < LCD_VBLANK_START_LINE) {
                    stepHdmaBlock();
                }
            }
            case LCD_MODE_VBLANK -> {
                if ((stat & 0x10) != 0) {
                    requestLcdStatInterrupt();
                }
            }
            case LCD_MODE_OAM -> {
                if ((stat & 0x20) != 0) {
                    requestLcdStatInterrupt();
                }
            }
            default -> {
                // Mode 3 has no direct STAT interrupt source.
            }
        }
    }

    private void forceLcdMode(int newMode) {
        lcdMode = newMode & 0x03;
        stat = (stat & ~0x03) | lcdMode | 0x80;
    }

    private void handleHdmaStart(int value) {
        int length = value & 0x7F;
        boolean requestHblank = (value & 0x80) != 0;

        if (hdmaActive && hdmaHblank && !requestHblank) {
            // Cancel active HBlank DMA
            hdmaActive = false;
            hdmaHblank = false;
            hdmaBlocksRemaining = 0;
            return;
        }

        int totalBlocks = length + 1;
        hdmaBlocksRemaining = totalBlocks;
        hdmaActive = true;
        hdmaHblank = requestHblank;

        if (requestHblank && (lcdc & 0x80) == 0) {
            // If LCD is off, HBlank DMA behaves like General DMA
            requestHblank = false;
            hdmaHblank = false;
        }

        if (!requestHblank) {
            // General DMA: transfer immediately
            for (int i = 0; i < totalBlocks; i++) {
                stepHdmaBlock();
            }
            hdmaActive = false;
            hdmaHblank = false;
            hdmaBlocksRemaining = 0;
        }
    }

    private void stepHdmaBlock() {
        if (!hdmaActive || hdmaBlocksRemaining <= 0) {
            return;
        }
        int source = ((hdmaSrcHigh & 0xFF) << 8) | (hdmaSrcLow & 0xF0);
        int dest = 0x8000 | ((hdmaDstHigh & 0x1F) << 8) | (hdmaDstLow & 0xF0);

        for (int i = 0; i < 0x10; i++) {
            int value = readByteInternal((source + i) & 0xFFFF) & 0xFF;
            int addr = (dest + i) & 0xFFFF;
            if (addr >= 0x8000 && addr <= 0x9FFF) {
                writeVideoRam(addr, (byte) value);
            }
        }

        // Advance source/dest by 0x10
        source = (source + 0x10) & 0xFFFF;
        dest = (dest + 0x10) & 0x1FF0;
        hdmaSrcHigh = (source >>> 8) & 0xFF;
        hdmaSrcLow = source & 0xF0;
        hdmaDstHigh = (dest >>> 8) & 0x1F;
        hdmaDstLow = dest & 0xF0;

        hdmaBlocksRemaining--;
        if (hdmaBlocksRemaining <= 0) {
            hdmaActive = false;
            hdmaHblank = false;
        }
    }

    private void updateLycFlag() {
        boolean match = ly == lyc;
        if (match) {
            stat |= 0x04;
            if (!lycMatch && (stat & 0x40) != 0) {
                requestLcdStatInterrupt();
            }
        } else {
            stat &= ~0x04;
        }
        lycMatch = match;
    }

    private void setStatFromWrite(int value) {
        stat = 0x80 | (value & 0x78) | (stat & 0x07);
    }

    private void handleLcdcWrite(int value) {
        int newLcdc = value & 0xFF;
        boolean wasEnabled = (lcdc & 0x80) != 0;
        boolean isEnabled = (newLcdc & 0x80) != 0;
        lcdc = newLcdc;

        if (!isEnabled) {
            lcdCycleCounter = 0;
            ly = 0;
            forceLcdMode(LCD_MODE_HBLANK);
            updateLycFlag();
        } else if (!wasEnabled) {
            lcdCycleCounter = 0;
            ly = 0;
            updateLycFlag();
            setLcdMode(LCD_MODE_OAM);
        }
    }

    private void requestLcdStatInterrupt() {
        interruptFlag |= 0x02;
    }

    private void requestVBlankInterrupt() {
        interruptFlag |= 0x01;
    }

    public Memory() {
        // Initialize CGB memory banking
        this.vramBank = 0; // Start with bank 0
        this.wramBank = 1; // Start with bank 1
        this.apu = new Apu(); // Initialize APU
        loadBootRomFromProperty();
        reset();
    }

    private void loadBootRomFromProperty() {
        String path = System.getProperty("emulator.bootRom");
        if (path == null || path.isBlank()) {
            bootRom = null;
            bootRomEnabled = false;
            return;
        }
        try {
            byte[] data = Files.readAllBytes(Path.of(path));
            if (data.length != 0x100 && data.length != 0x900) {
                LOGGER.log(Level.WARNING, () -> String.format(
                        "Boot ROM size %d unexpected (expected 0x100 or 0x900). Using anyway.", data.length));
            }
            bootRom = data;
            bootRomEnabled = true;
            LOGGER.log(Level.INFO, () -> String.format("Boot ROM loaded (%d bytes)", data.length));
        } catch (Exception e) {
            bootRom = null;
            bootRomEnabled = false;
            LOGGER.log(Level.WARNING, "Failed to load boot ROM: " + path, e);
        }
    }

    public void setCPU(CPU cpu) {
        this.cpu = cpu;
    }

    public void setPPU(gbc.model.graphics.PPU ppu) {
        this.ppu = ppu;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }
    
    /**
     * Sets the hardware type for this emulator instance.
     * This affects initial register and I/O values after reset.
     */
    public void setHardwareType(gbc.model.HardwareType type) {
        this.hardwareType = type == null ? gbc.model.HardwareType.DMG : type;
        updateCgbMode();
        applyDmgCgbPalettesIfNeeded();
        hardwareOverride = true;
    }

    public void setHardwareTypeAuto(gbc.model.HardwareType type) {
        this.hardwareType = type == null ? gbc.model.HardwareType.DMG : type;
        updateCgbMode();
        applyDmgCgbPalettesIfNeeded();
        hardwareOverride = false;
    }

    public boolean isHardwareOverride() {
        return hardwareOverride;
    }
    
    /**
     * Gets the current hardware type.
     */
    public gbc.model.HardwareType getHardwareType() {
        return this.hardwareType;
    }

    public boolean isCgbMode() {
        return cgbMode;
    }

    public boolean isDmgOnCgb() {
        return hardwareType != null && hardwareType.isCgb() && !cgbMode;
    }

    private void initApuPostBoot() {
        if (apu == null) {
            return;
        }
        // Enable APU (NR52) and set post-boot defaults
        apu.writeRegister(0xFF26, 0x80); // APU on
        apu.writeRegister(0xFF24, 0x77); // NR50: max volume, enable vin
        apu.writeRegister(0xFF25, 0xF3); // NR51: enable all channels
        apu.writeRegister(0xFF10, 0x80);
        apu.writeRegister(0xFF11, 0xBF);
        apu.writeRegister(0xFF12, 0xF3);
        apu.writeRegister(0xFF13, 0xFF);
        apu.writeRegister(0xFF14, 0xBF);
        apu.writeRegister(0xFF16, 0x3F);
        apu.writeRegister(0xFF17, 0x00);
        apu.writeRegister(0xFF18, 0xFF);
        apu.writeRegister(0xFF19, 0xBF);
        apu.writeRegister(0xFF1A, 0x7F);
        apu.writeRegister(0xFF1B, 0xFF);
        apu.writeRegister(0xFF1C, 0x9F);
        apu.writeRegister(0xFF1D, 0xFF);
        apu.writeRegister(0xFF1E, 0xBF);
        apu.writeRegister(0xFF20, 0xFF);
        apu.writeRegister(0xFF21, 0x00);
        apu.writeRegister(0xFF22, 0x00);
        apu.writeRegister(0xFF23, 0xBF);
    }

    private void initApuPreBoot() {
        if (apu == null) {
            return;
        }
        apu.writeRegister(0xFF26, 0x00); // APU off
        apu.writeRegister(0xFF24, 0x00);
        apu.writeRegister(0xFF25, 0x00);
        for (int addr = 0xFF10; addr <= 0xFF3F; addr++) {
            apu.writeRegister(addr, 0x00);
        }
    }

    // APU access methods
    public void stepApu(int cycles) {
        apu.step(cycles);
    }

    public boolean isAudioBufferFull() {
        return apu.isBufferFull();
    }

    public byte[] fetchAudioSamples() {
        return apu.fetchSamples();
    }

    /**
     * Returns true if OAM DMA is currently in progress.
     * During DMA, CPU can only access HRAM (0xFF80-0xFFFE).
     */
    public boolean isDmaActive() {
        return dmaActive;
    }

    public void stepPeripherals(int cycles) {
        if (cycles <= 0)
            return;

        // Process T-cycles one by one to detect edges accurately
        for (int i = 0; i < cycles; i++) {
            int currentDivider = divider & 0xFFFF;
            boolean oldSignal = isTimerSignalActive(currentDivider, tac);

            // Advance divider by one T-cycle
            divider = (divider + 1) & 0xFFFF;

            int newDivider = divider & 0xFFFF;
            boolean newSignal = isTimerSignalActive(newDivider, tac);

            tracePostDivCycle(currentDivider, newDivider, oldSignal, newSignal);

            if (oldSignal && !newSignal) {
                incrementTIMA("divider");
            }

            updateTimerState();
            stepLcd();
            if (ppu != null) {
                ppu.tick(1);
            }
            apuCycleCounter++;
            if (apuCycleCounter >= 4) {
                if (apu != null) {
                    stepApu(4);
                }
                apuCycleCounter = 0;
            }
            
            // Step OAM DMA (transfers 1 byte per M-cycle = every 4 T-cycles)
            stepOamDma();
            
            totalTCycles++;
        }
    }

    private void stepOamDma() {
        if (!dmaActive) {
            return;
        }

        // Handle startup delay (1 M-cycle = 4 T-cycles)
        // We decrement every T-cycle.
        if (dmaStartupDelay > 0) {
            dmaStartupDelay--;
            if (dmaStartupDelay == 0) {
                // Delay finished, ready to start transfer
                // We align the transfer to M-cycles (multiples of 4 relative to start?)
                // Simplified: just start transferring now, regulated by a counter.
                // We use a counter to transfer 1 byte every 4 T-cycles.
                // Initialize counter to 0 so we process 1st byte immediately or after 4 cycles?
                // Real HW: 1 M-cycle delay, then 160 M-cycles of transfer.
                // Transfer happens at specific point in M-cycle.
                // Let's implement a 'dmaCycleCounter' to track when to copy.
                dmaCycleCounter = 0;
            }
            return;
        }

        // Transfer phase
        // We transfer 1 byte every 4 T-cycles.
        dmaCycleCounter++;
        if (dmaCycleCounter >= 4) {
            dmaCycleCounter = 0;
            
            if (dmaBytesTransferred < DMA_TRANSFER_LENGTH) {
                int srcAddr = (dmaSourceBase + dmaBytesTransferred) & 0xFFFF;
                int byteValue = readByteInternal(srcAddr) & 0xFF;
                oam[dmaBytesTransferred] = (byte) byteValue;
                dmaBytesTransferred++;
            }


            if (dmaBytesTransferred >= DMA_TRANSFER_LENGTH) {
                dmaActive = false;
            }
        }
    }

    /**
     * Internal read that bypasses DMA restrictions (used by DMA itself).
     * DMA has limited bus access capabilities compared to CPU.
     * It can generally access ROM, RAM (WRAM/SRAM), VRAM, HRAM.
     * 
     * On DMG/MGB, DMA from $FE00-$FFFF actually reads from Echo RAM ($DE00-$DFFF).
     * This is because the DMA unit maps high addresses back to Work RAM.
     */
    private int readByteInternal(int address) {
        int addr = address & 0xFFFF;

        // Cartridge ROM/RAM (0000-7FFF, A000-BFFF)
        if (cartridge != null && (isCartridgeRom(addr) || isCartridgeRam(addr))) {
            return cartridge.read(addr) & 0xFF;
        }

        // Video RAM (0x8000-0x9FFF)
        // DMA overrides PPU access, so always readable
        if (isVideoRam(addr)) {
            return readVideoRam(addr) & 0xFF;
        }

        // Work RAM (0xC000-0xDFFF)
        if (isWorkRam(addr)) {
            return readWorkRam(addr) & 0xFF;
        }

        // Echo RAM (0xE000-0xFDFF) - mirror of work RAM
        if (isEchoRam(addr)) {
            int echoAddr = addr - 0x2000;
            if (isWorkRam(echoAddr)) {
                return readWorkRam(echoAddr) & 0xFF;
            }
        }

        // High RAM (0xFF80-0xFFFE)
        // Accessible by DMA
        if (isHighRam(addr)) {
            return highRam[addr - 0xFF80] & 0xFF;
        }

        // OAM ($FE00-$FE9F), Unusable ($FEA0-$FEFF), IO ($FF00-$FF7F), IE ($FFFF)
        // On DMG/MGB, DMA from these addresses reads from Work RAM
        // The DMA unit masks bit 13, so $FE00 maps to $DE00, $FF00 maps to $DF00
        if (addr >= 0xFE00) {
            int wramAddr = addr & 0xDFFF;  // Mask bit 13: $FE00->$DE00, $FF00->$DF00
            if (isWorkRam(wramAddr)) {
                return readWorkRam(wramAddr) & 0xFF;
            }
            // Addresses outside WRAM range return 0xFF
            return 0xFF;
        }


        // Fallback for any unmapped addresses
        return 0xFF;
    }


    private boolean isBusLocked(int address) {
        // Bus is locked when DMA is active AND startup delay has passed.
        // During startup delay, CPU can still access memory.
        if (!dmaActive || dmaStartupDelay > 0) {
            return false;
        }

        // HRAM (0xFF80-0xFFFE) and IE (0xFFFF) are always accessible
        // Also DMA Register (0xFF46) is accessible
        if (address >= 0xFF80 || address == 0xFF46) {
            return false;
        }

        return true;
    }

    private boolean isOamBlockedForCpu() {
        return (lcdc & 0x80) != 0 && (lcdMode == LCD_MODE_OAM || lcdMode == LCD_MODE_TRANSFER);
    }

    private boolean isVramBlockedForCpu() {
        return (lcdc & 0x80) != 0 && lcdMode == LCD_MODE_TRANSFER;
    }

    public int readByte(int address) {
        int addr = address & 0xFFFF;

        // Check bus lock first
        if (isBusLocked(addr)) {
            // Allow I/O access partially or not?
            // Real hardware blocks almost everything except HRAM.
            // Tests often check that reads return 0xFF or garbage.
            return 0xFF;
        }

        if (bootRomEnabled && bootRom != null) {
            if (addr < 0x0100) {
                return bootRom[addr] & 0xFF;
            }
            if (bootRom.length >= 0x900 && addr >= 0x0200 && addr < 0x0900) {
                return bootRom[addr] & 0xFF;
            }
        }

        // Handle I/O registers (0xFF00-0xFF7F)
        if (isIoRegisters(addr)) {
            return readIORegister(addr);
        }

        // High RAM (0xFF80-0xFFFE)
        if (isHighRam(addr)) {
            return highRam[addr - 0xFF80] & 0xFF;
        }

        // Interrupt Enable Register (0xFFFF) - special case
        if (isInterruptEnable(addr)) {
            // IE: bits 7-5 are unused and always return 1
            return (interruptEnable & 0xFF) | 0xE0;
        }

        // I/O registers and other special areas (0xFEA0-0xFEFF) - unmapped
        if (isUnusable(addr)) {
            return 0xFF; // Return 0xFF for unmapped areas
        }

        // OAM (Object Attribute Memory) (0xFE00-0xFE9F)
        if (isOam(addr)) {
            if (isOamBlockedForCpu()) {
                return 0xFF;
            }
            return oam[addr - 0xFE00] & 0xFF;
        }

        // Echo RAM (0xE000-0xFDFF) - mirrors WRAM
        if (isEchoRam(addr)) {
            return readByte(addr - 0x2000);
        }

        // Work RAM (0xC000-0xDFFF)
        if (isWorkRam(addr)) {
            return readWorkRam(addr);
        }

        // Video RAM (0x8000-0x9FFF)
        if (isVideoRam(addr)) {
            if (isVramBlockedForCpu()) {
                return 0xFF;
            }
            return readVideoRam(addr);
        }

        // Cartridge RAM (0xA000-0xBFFF)
        if (isCartridgeRam(addr)) {
            if (cartridge == null) {
                return 0xFF;
            }
            return cartridge.read(addr) & 0xFF;
        }

        // Cartridge ROM/RAM (0x0000-0x7FFF)
        if (isCartridgeRom(addr)) {
            if (cartridge == null) {
                return 0xFF;
            }
            return cartridge.read(addr) & 0xFF;
        }

        // Should not reach here
        throw new IllegalArgumentException("Invalid address: " + String.format("0x%04X", addr));
    }

    private int readVideoRam(int address) {
        if (vramBank == 0) {
            return videoRam0[address - 0x8000] & 0xFF;
        } else {
            return videoRam1[address - 0x8000] & 0xFF;
        }
    }

    private int readWorkRam(int address) {
        if (address >= 0xC000 && address <= 0xCFFF) {
            // Bank 0 (fixed)
            return workRam0[address - 0xC000] & 0xFF;
        } else {
            // Switchable banks (1-7)
            switch (wramBank) {
                case 1:
                    return workRam1[address - 0xD000] & 0xFF;
                case 2:
                    return workRam2[address - 0xD000] & 0xFF;
                case 3:
                    return workRam3[address - 0xD000] & 0xFF;
                case 4:
                    return workRam4[address - 0xD000] & 0xFF;
                case 5:
                    return workRam5[address - 0xD000] & 0xFF;
                case 6:
                    return workRam6[address - 0xD000] & 0xFF;
                case 7:
                    return workRam7[address - 0xD000] & 0xFF;
                default:
                    return workRam1[address - 0xD000] & 0xFF; // Default to bank 1
            }
        }
    }

    public int readChar(int address) {
        // Game Boy uses little-endian: low byte first, high byte second
        int low = readByte(address) & 0xFF;
        int high = readByte(address + 1) & 0xFF;
        return (high << 8) | low;
    }

    public void writeByte(int address, int value) {
        address &= 0xFFFF;
        byte byteValue = (byte) (value & 0xFF);

        // Check bus lock first
        if (isBusLocked(address)) {
            // Bus is locked, writes are ignored
            // Exception: We must allow writing to 0xFF46 to RESTART DMA
            if (address != 0xFF46) {
                return;
            }
        }

        if (address < 0x8000) {
            // ROM area, handled by cartridge
            if (cartridge != null) {
                cartridge.write(address, byteValue);
            }
            return;
        }
        if (address >= 0x8000 && address < 0xA000) {
            if (isVramBlockedForCpu()) {
                return;
            }
            writeVideoRam(address, byteValue);
            return;
        }
        if (address >= 0xA000 && address < 0xC000) {
            // External RAM, handled by cartridge
            if (cartridge != null) {
                cartridge.write(address, byteValue);
            }
            return;
        }
        if (address >= 0xC000 && address < 0xE000) {
            writeWorkRam(address, byteValue);
            return;
        }
        if (address >= 0xE000 && address < 0xFE00) { // Echo RAM
            // Write to echo ram also writes to work ram
            writeWorkRam(address - 0x2000, byteValue);
            return;
        }
        if (address >= 0xFE00 && address < 0xFEA0) {
            if (isOamBlockedForCpu()) {
                return;
            }
            oam[address - 0xFE00] = byteValue;
            return;
        }
        if (address >= 0xFEA0 && address < 0xFF00) {
            // Unused memory, writes are ignored
            return;
        }
        if (address >= 0xFF00 && address < 0xFF80) { // I/O Registers
            writeIORegister(address, byteValue);
            return;
        }
        if (address >= 0xFF80 && address < 0xFFFF) {
            highRam[address - 0xFF80] = byteValue;
            return;
        }
        if (address == 0xFFFF) {
            interruptEnable = (byte) (byteValue & 0x1F);
            return;
        }
    }

    private int readIORegister(int address) {
        // Timer & PPU registers with Game Boy default values
        switch (address) {
            case 0xFF04: // DIV - Divider register
                return getDivRegister();
            case 0xFF05: // TIMA - Timer counter
                return tima & 0xFF;
            case 0xFF06: // TMA - Timer modulo
                return tma & 0xFF;
            case 0xFF07: // TAC - Timer control
                // TAC: bits 7-3 are unused and always return 1
                return (tac & 0xFF) | 0xF8;
            case 0xFF40: // LCDC - LCD Control
                return lcdc & 0xFF;
            case 0xFF41: // STAT - LCD Status
                // STAT: bit 7 is unused and always returns 1
                return (stat & 0xFF) | 0x80;
            case 0xFF42: // SCY - Scroll Y
                return scy & 0xFF;
            case 0xFF43: // SCX - Scroll X
                return scx & 0xFF;
            case 0xFF44: // LY - LCD Y coordinate (current scanline)
                return ly & 0xFF;
            case 0xFF45: // LYC - LY compare
                return lyc & 0xFF;
            case 0xFF46: // DMA - DMA transfer (returns last written value high byte)
                return (dmaSourceBase >> 8) & 0xFF;
            case 0xFF47: // BGP - Background palette
                return bgp & 0xFF;
            case 0xFF48: // OBP0 - Object palette 0
                return obp0 & 0xFF;
            case 0xFF49: // OBP1 - Object palette 1
                return obp1 & 0xFF;
            case 0xFF4A: // WY - Window Y position
                return wy & 0xFF;
            case 0xFF4B: // WX - Window X position
                return wx & 0xFF;
        }

        // APU registers (0xFF10-0xFF3F)
        if (address >= 0xFF10 && address <= 0xFF3F) {
            return apu.readRegister(address);
        }

        switch (address) {
            case 0xFF4D: // KEY1 - Speed control register
                if (!cgbMode || cpu == null) {
                    return 0xFF;
                }
                return cpu.readKey1();
            case 0xFF4F: // VBK - VRAM bank select
                return cgbMode ? vramBank : 0xFF;
            case 0xFF51: // HDMA1 - DMA source high (CGB)
                return cgbMode ? hdmaSrcHigh : 0xFF;
            case 0xFF52: // HDMA2 - DMA source low (CGB)
                return cgbMode ? (hdmaSrcLow & 0xF0) : 0xFF;
            case 0xFF53: // HDMA3 - DMA destination high (CGB)
                return cgbMode ? (hdmaDstHigh & 0x1F) : 0xFF;
            case 0xFF54: // HDMA4 - DMA destination low (CGB)
                return cgbMode ? (hdmaDstLow & 0xF0) : 0xFF;
            case 0xFF55: // HDMA5 - DMA length/mode (CGB)
                if (!cgbMode) {
                    return 0xFF;
                }
                if (hdmaActive && hdmaHblank) {
                    return (hdmaBlocksRemaining - 1) & 0x7F;
                }
                return 0xFF;
            case 0xFF68: // BGPI - BG palette index
                return cgbMode ? getBgpi() : 0xFF;
            case 0xFF69: // BGPD - BG palette data
                return cgbMode ? readCgbBgPaletteByte(bgpi) : 0xFF;
            case 0xFF6A: // OBPI - OBJ palette index
                return cgbMode ? getObpi() : 0xFF;
            case 0xFF6B: // OBPD - OBJ palette data
                return cgbMode ? readCgbObjPaletteByte(obpi) : 0xFF;
            case 0xFF70: // SVBK - WRAM bank select
                return cgbMode ? wramBank : 0xFF;
            case 0xFF0F: // IF - interrupt flags
                // IF: bits 7-5 are unused and always return 1
                return (interruptFlag & 0xFF) | 0xE0;
            case 0xFF00: // P1 - Joypad - bits 7-6 unused, always return 1
                if (controller != null) {
                    return controller.getJoypadState() | 0xC0;
                }
                return 0xCF; // Default with unused bits set
            case 0xFF01: // SB - Serial Data - all bits readable/writable
                return serialData;
            case 0xFF02: // SC - Serial Control - bits 6-1 unused, always return 1
                return (serialControl & 0xFF) | 0x7E;
            default:
                // For unused/unmapped I/O registers on DMG, return 0xFF
                // This includes: 0xFF03, 0xFF08-0xFF0E, 0xFF15, 0xFF1F, 0xFF27-0xFF2F,
                // 0xFF4C (non-CGB), 0xFF4E (non-CGB), 0xFF51-0xFF55 (non-CGB),
                // 0xFF56 (non-CGB), 0xFF57-0xFF67, 0xFF6D-0xFF6F, 0xFF71-0xFF7F
                return 0xFF;
        }
    }

    private void writeIORegister(int address, byte value) {
        // Joypad register (0xFF00) - only bits 5-4 are writable (select bits)
        if (address == 0xFF00) {
            if (controller != null) {
                controller.writeJoypadSelect(value & 0xFF);
            }
            return;
        }

        // Serial port registers
        if (address == 0xFF01) { // SB - Serial Data
            serialData = value;
            return;
        }
        if (address == 0xFF02) { // SC - Serial Control
            serialControl = value;
            return;
        }

        // APU registers (0xFF10-0xFF3F)
        if (address >= 0xFF10 && address <= 0xFF3F) {
            apu.writeRegister(address, value & 0xFF);
            return;
        }

        switch (address) {
            case 0xFF04: // DIV - Divider register (writing resets to 0)
                // Writing to DIV resets the 16-bit divider to 0 and may cause an
                // immediate timer input edge. We must detect a falling edge on the
                // selected timer bit.
                int oldDivider = divider & 0xFFFF;
                boolean oldSignal = isTimerSignalActive(oldDivider, tac);

                divider = 0;

                boolean newSignal = isTimerSignalActive(divider & 0xFFFF, tac);
                if (oldSignal && !newSignal) {
                    // A falling edge due to DIV write - increment TIMA
                    incrementTIMA("div");
                }
                activatePostDivTrace();
                traceTimer("write DIV (reset)");
                return;
            case 0xFF05: { // TIMA - Timer counter
                int newTima = value & 0xFF;

                if (reloadPending) {
                    if (timerState == TimerState.OVERFLOW_DELAY) {
                         // Write during overflow delay (Cycle A): Cancels reload and interrupt
                        tima = newTima;
                        reloadPending = false;
                        timerState = TimerState.NORMAL;
                        overflowDelayActive = false; // Stop the delay state machine
                        traceTimer(String.format("write TIMA=%02X (cancelled reload in OVERFLOW_DELAY)", newTima));
                        return;
                    } else if (timerState == TimerState.RELOAD_ACTIVE) {
                        // Write during reload active (Cycle B): Ignored, reload will overwrite it
                        traceTimer(String.format("write TIMA=%02X (ignored - in RELOAD_ACTIVE)", newTima));
                        return;
                    }
                }

                // Normal write
                tima = newTima;
                traceTimer(String.format("write TIMA=%02X", newTima));
                return;
            }
            case 0xFF06: // TMA - Timer modulo
                tma = value & 0xFF;
                // TMA write during reload window (Cycle B/RELOAD_ACTIVE):
                // The new value acts as if it was loaded into TIMA as well.
                if (reloadPending && timerState == TimerState.RELOAD_ACTIVE) {
                    tima = tma;
                    traceTimer(String.format("write TMA=%02X during RELOAD_ACTIVE - also copied to TIMA", tma & 0xFF));
                } else {
                    traceTimer(String.format("write TMA=%02X", tma & 0xFF));
                }
                return;
            case 0xFF07: // TAC - Timer control
                // Writing to TAC can change the selected input bit; this can also
                // cause an immediate increment if the selected bit undergoes a
                // falling edge as a result of the write.
                int oldTac = tac;
                boolean oldSignalTac = isTimerSignalActive(divider & 0xFFFF, oldTac);
                tac = value & 0xFF;
                boolean newSignalTac = isTimerSignalActive(divider & 0xFFFF, tac);
                if (oldSignalTac && !newSignalTac) {
                    incrementTIMA("tac");
                }
                traceTimer(String.format("write TAC=%02X", tac & 0xFF));
                return;
            case 0xFF40: // LCDC - LCD Control
                handleLcdcWrite(value & 0xFF);
                return;
            case 0xFF50: // BOOT - disable boot ROM
                if ((value & 0x01) != 0) {
                    bootRomEnabled = false;
                }
                return;
            case 0xFF41: { // STAT - LCD Status
                int previousStat = stat;
                setStatFromWrite(value & 0xFF);

                if ((stat & 0x40) != 0 && (previousStat & 0x40) == 0 && (ly == lyc)) {
                    requestLcdStatInterrupt();
                }
                if ((stat & 0x20) != 0 && (previousStat & 0x20) == 0 && lcdMode == LCD_MODE_OAM) {
                    requestLcdStatInterrupt();
                }
                if ((stat & 0x10) != 0 && (previousStat & 0x10) == 0 && lcdMode == LCD_MODE_VBLANK) {
                    requestLcdStatInterrupt();
                }
                if ((stat & 0x08) != 0 && (previousStat & 0x08) == 0 && lcdMode == LCD_MODE_HBLANK) {
                    requestLcdStatInterrupt();
                }
                updateLycFlag();
                return;
            }
            case 0xFF42: // SCY - Scroll Y
                scy = value & 0xFF;
                return;
            case 0xFF43: // SCX - Scroll X
                scx = value & 0xFF;
                return;
            case 0xFF44: // LY - Reset current scanline
                ly = 0;
                lcdCycleCounter = 0;
                if ((lcdc & 0x80) != 0) {
                    setLcdMode(LCD_MODE_OAM);
                } else {
                    forceLcdMode(LCD_MODE_HBLANK);
                }
                updateLycFlag();
                return;
            case 0xFF45: // LYC - LY compare
                lyc = value & 0xFF;
                updateLycFlag();
                return;
            case 0xFF0F: // IF - Interrupt Flag
                interruptFlag = (byte) (value & 0x1F);
                return;
            case 0xFF46: // DMA - OAM DMA transfer
                // Start cycle-accurate OAM DMA transfer
                // DMA takes 160 M-cycles (640 T-cycles) with 1 M-cycle startup delay
                dmaSourceBase = (value & 0xFF) << 8;
                dmaBytesTransferred = 0;
                dmaStartupDelay = 4; // 1 M-cycle startup delay (4 T-cycles)
                dmaActive = true;
                return;
            case 0xFF4D: // KEY1 - Speed control register
                if (cgbMode && cpu != null) {
                    cpu.writeKey1(value & 0xFF);
                }
                return;
            case 0xFF4F: // VBK - VRAM bank select
                if (cgbMode) {
                    vramBank = value & 0x01; // Only bit 0 is used
                }
                return;
            case 0xFF51: // HDMA1 - DMA source high (CGB)
                if (cgbMode) {
                    hdmaSrcHigh = value & 0xFF;
                }
                return;
            case 0xFF52: // HDMA2 - DMA source low (CGB) - lower 4 bits ignored
                if (cgbMode) {
                    hdmaSrcLow = value & 0xF0;
                }
                return;
            case 0xFF53: // HDMA3 - DMA destination high (CGB) - upper 3 bits ignored
                if (cgbMode) {
                    hdmaDstHigh = value & 0x1F;
                }
                return;
            case 0xFF54: // HDMA4 - DMA destination low (CGB) - lower 4 bits ignored
                if (cgbMode) {
                    hdmaDstLow = value & 0xF0;
                }
                return;
            case 0xFF55: // HDMA5 - DMA length/mode (CGB)
                if (cgbMode) {
                    handleHdmaStart(value & 0xFF);
                }
                return;
            case 0xFF68: // BGPI - BG palette index
                if (cgbMode) {
                    bgpi = value & 0x3F;
                    bgpiAutoInc = (value & 0x80) != 0;
                }
                return;
            case 0xFF69: // BGPD - BG palette data
                if (cgbMode) {
                    cgbBgPalette[bgpi & 0x3F] = (byte) value;
                    if (bgpiAutoInc) {
                        bgpi = (bgpi + 1) & 0x3F;
                    }
                }
                return;
            case 0xFF6A: // OBPI - OBJ palette index
                if (cgbMode) {
                    obpi = value & 0x3F;
                    obpiAutoInc = (value & 0x80) != 0;
                }
                return;
            case 0xFF6B: // OBPD - OBJ palette data
                if (cgbMode) {
                    cgbObjPalette[obpi & 0x3F] = (byte) value;
                    if (obpiAutoInc) {
                        obpi = (obpi + 1) & 0x3F;
                    }
                }
                return;
            case 0xFF47: // BGP - Background palette
                bgp = value & 0xFF;
                return;
            case 0xFF48: // OBP0 - Object palette 0
                obp0 = value & 0xFF;
                return;
            case 0xFF49: // OBP1 - Object palette 1
                obp1 = value & 0xFF;
                return;
            case 0xFF4A: // WY - Window Y position
                wy = value & 0xFF;
                return;
            case 0xFF4B: // WX - Window X position
                wx = value & 0xFF;
                return;
            case 0xFF70: // SVBK - WRAM bank select
                if (cgbMode) {
                    wramBank = (value & 0x07); // Bits 0-2
                    if (wramBank == 0) {
                        wramBank = 1; // Bank 0 maps to bank 1
                    }
                }
                return;
            default:
                // For unhandled I/O registers, ignore writes
                return;
        }
    }

    public void reset() {
        // Reset VRAM banks
        for (int i = 0; i < videoRam0.length; i++) {
            videoRam0[i] = 0x00;
        }
        for (int i = 0; i < videoRam1.length; i++) {
            videoRam1[i] = 0x00;
        }

        // Reset WRAM banks
        for (int i = 0; i < workRam0.length; i++) {
            workRam0[i] = 0x00;
        }
        for (int i = 0; i < workRam1.length; i++) {
            workRam1[i] = 0x00;
        }
        for (int i = 0; i < workRam2.length; i++) {
            workRam2[i] = 0x00;
        }
        for (int i = 0; i < workRam3.length; i++) {
            workRam3[i] = 0x00;
        }
        for (int i = 0; i < workRam4.length; i++) {
            workRam4[i] = 0x00;
        }
        for (int i = 0; i < workRam5.length; i++) {
            workRam5[i] = 0x00;
        }
        for (int i = 0; i < workRam6.length; i++) {
            workRam6[i] = 0x00;
        }
        for (int i = 0; i < workRam7.length; i++) {
            workRam7[i] = 0x00;
        }

        // Reset High RAM
        for (int i = 0; i < highRam.length; i++) {
            highRam[i] = 0x00;
        }

        // Reset OAM
        for (int i = 0; i < oam.length; i++) {
            oam[i] = 0x00;
        }

        // Reset Interrupt Enable Register
        interruptEnable = 0x00;

        // Reset memory banking
        vramBank = 0;
        wramBank = 1;

        // Reset CGB palettes
        for (int i = 0; i < cgbBgPalette.length; i++) {
            cgbBgPalette[i] = 0;
            cgbObjPalette[i] = 0;
        }
        bgpi = 0;
        obpi = 0;
        bgpiAutoInc = false;
        obpiAutoInc = false;
        if (hardwareType.isCgb() && bootRom == null) {
            initDefaultCgbPalettes();
        }

        // Reset timer registers/state
        // DIV internal counter value depends on hardware type
        // The visible DIV register (0xFF04) shows the upper byte
        divider = hardwareType.getInitialDivCounter();
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
        interruptFlag = 0x00;
        bootRomEnabled = bootRom != null;

        // Reset OAM DMA state
        dmaActive = false;
        dmaSourceBase = 0;
        dmaBytesTransferred = 0;
        dmaStartupDelay = 0;
        dmaCycleCounter = 0;

        // Reset HDMA state
        hdmaSrcHigh = 0;
        hdmaSrcLow = 0;
        hdmaDstHigh = 0;
        hdmaDstLow = 0;
        hdmaActive = false;
        hdmaHblank = false;
        hdmaBlocksRemaining = 0;

        // Initialize I/O registers to DMG post-boot state
        if (bootRomEnabled) {
            lcdc = 0x00;
            stat = 0x00;
            scy = 0x00;
            scx = 0x00;
            ly = 0x00;
            lyc = 0x00;
            wy = 0x00;
            wx = 0x00;
            bgp = 0x00;
            obp0 = 0x00;
            obp1 = 0x00;
            lcdCycleCounter = 0;
            apuCycleCounter = 0;
            lycMatch = ly == lyc;
            forceLcdMode(LCD_MODE_HBLANK);
            updateLycFlag();
            divider = 0;
            initApuPreBoot();
        } else {
            lcdc = 0x91; // LCD on, BG on, sprites on
            stat = 0x85; // Mode 1 (VBlank), LYC=LY flag set
            scy = 0x00;
            scx = 0x00;
            ly = 0x00; // Scanline 0
            lyc = 0x00;
            wy = 0x00;
            wx = 0x00;
            bgp = 0xFC; // Background palette
            obp0 = 0xFF; // Object palette 0
            obp1 = 0xFF; // Object palette 1
            lcdCycleCounter = 0;
            apuCycleCounter = 0;
            lycMatch = ly == lyc;
            forceLcdMode(LCD_MODE_VBLANK);
            updateLycFlag();
            initApuPostBoot();
        }

        applyDmgCgbPalettesIfNeeded();
    }

    private void initDefaultCgbPalettes() {
        int[] dmg = new int[] { 0x7FFF, 0x5AD6, 0x294A, 0x0000 };
        for (int p = 0; p < 8; p++) {
            for (int i = 0; i < 4; i++) {
                int color = dmg[i];
                int idx = (p * 8) + (i * 2);
                cgbBgPalette[idx] = (byte) (color & 0xFF);
                cgbBgPalette[idx + 1] = (byte) ((color >>> 8) & 0xFF);
                cgbObjPalette[idx] = (byte) (color & 0xFF);
                cgbObjPalette[idx + 1] = (byte) ((color >>> 8) & 0xFF);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("CGB Memory Layout:\n");
        sb.append(String.format("VRAM Bank: %d, WRAM Bank: %d\n", vramBank, wramBank));
        sb.append("Note: This is a simplified view. Full memory dump not implemented for CGB layout.\n");

        return sb.toString();
    }

    public char popFromStack(char sp) {
        char value = (char) readChar((int) sp);
        return value;
    }

    public char popFromStack(char sp, Registers registers) {
        // Game Boy: SP points to first free location, so data is at SP, SP+1
        char value = (char) readChar((int) sp);
        int newSp = sp + 2;
        registers.setSP((char) (newSp & 0xFFFF)); // Increment SP after pop
        return value;
    }

    public void pushToStack(char sp, char value, Registers registers) {
        // Game Boy: SP decreases, high byte at SP-1, low byte at SP-2
        int newSp = sp - 2;
        registers.setSP((char) (newSp & 0xFFFF)); // Update SP first
        // Write in little-endian format: low byte first, high byte second
        writeByte(newSp, value & 0xFF); // Low byte at SP-2
        writeByte(newSp + 1, (value >> 8) & 0xFF); // High byte at SP-1
    }

    public void pushToStack(char sp, byte value, Registers registers) {
        int newSp = sp - 1;
        writeByte(newSp, value);
        registers.setSP((char) (newSp & 0xFFFF));
    }

    // Add missing getMemoryRange method for testing
    public java.util.HashMap<Integer, Byte> getMemoryRange(int startAddress, int endAddress) {
        java.util.HashMap<Integer, Byte> range = new java.util.HashMap<>();
        for (int i = startAddress; i <= endAddress && i <= 0xFFFF; i++) {
            try {
                range.put(i, (byte) readByte(i));
            } catch (IllegalArgumentException e) {
                // Skip invalid addresses
            }
        }
        return range;
    }

    // Additional methods if needed

    // Address range checking methods
    private boolean isIoRegisters(int address) {
        return address >= 0xFF00 && address <= 0xFF7F;
    }

    private boolean isHighRam(int address) {
        return address >= 0xFF80 && address <= 0xFFFE;
    }

    private boolean isInterruptEnable(int address) {
        return address == 0xFFFF;
    }

    private boolean isUnusable(int address) {
        return address >= 0xFEA0 && address <= 0xFEFF;
    }

    private boolean isOam(int address) {
        return address >= 0xFE00 && address <= 0xFE9F;
    }

    private boolean isEchoRam(int address) {
        return address >= 0xE000 && address <= 0xFDFF;
    }

    private boolean isWorkRam(int address) {
        return address >= 0xC000 && address <= 0xDFFF;
    }

    private boolean isVideoRam(int address) {
        return address >= 0x8000 && address <= 0x9FFF;
    }

    private boolean isCartridgeRam(int address) {
        return address >= 0xA000 && address <= 0xBFFF;
    }

    private boolean isCartridgeRom(int address) {
        return address >= 0x0000 && address <= 0x7FFF;
    }

    // Write methods for memory regions
    private void writeVideoRam(int address, byte value) {
        if (vramBank == 0) {
            videoRam0[address - 0x8000] = value;
        } else {
            videoRam1[address - 0x8000] = value;
        }
    }

    private void writeWorkRam(int address, byte value) {
        if (address >= 0xC000 && address <= 0xCFFF) {
            // Bank 0 (fixed)
            workRam0[address - 0xC000] = value;
        } else {
            // Switchable banks (1-7)
            switch (wramBank) {
                case 1:
                    workRam1[address - 0xD000] = value;
                    break;
                case 2:
                    workRam2[address - 0xD000] = value;
                    break;
                case 3:
                    workRam3[address - 0xD000] = value;
                    break;
                case 4:
                    workRam4[address - 0xD000] = value;
                    break;
                case 5:
                    workRam5[address - 0xD000] = value;
                    break;
                case 6:
                    workRam6[address - 0xD000] = value;
                    break;
                case 7:
                    workRam7[address - 0xD000] = value;
                    break;
                default:
                    workRam1[address - 0xD000] = value; // Default to bank 1
                    break;
            }
        }
    }

    // Cartridge management
    public boolean isCartridgeLoaded() {
        return cartridge != null;
    }

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
        updateCgbMode();
        applyDmgCgbPalettesIfNeeded();
    }

    private void updateCgbMode() {
        if (hardwareType != null && hardwareType.isCgb()
                && cartridge != null && cartridge.isCgbCompatible()) {
            cgbMode = true;
        } else {
            cgbMode = false;
        }
        if (!cgbMode) {
            vramBank = 0;
            wramBank = 1;
        }
    }

    private void applyDmgCgbPalettesIfNeeded() {
        if (!isDmgOnCgb() || bootRomEnabled || cartridge == null) {
            return;
        }
        gbc.model.graphics.DmgCgbPaletteTable table = gbc.model.graphics.DmgCgbPaletteTable.getInstance();
        gbc.model.graphics.DmgCgbPaletteTable.DmgCgbPalette palette = table.selectFor(cartridge);
        dmgCgbPalette = new int[][] { palette.bg().clone(), palette.obj0().clone(), palette.obj1().clone() };
        writeDmgCgbPaletteToRam();
    }

    private void writeDmgCgbPaletteToRam() {
        for (int i = 0; i < 4; i++) {
            writeCgbPaletteEntry(cgbBgPalette, 0, i, dmgCgbPalette[0][i]);
        }
        for (int i = 0; i < 4; i++) {
            writeCgbPaletteEntry(cgbObjPalette, 0, i, dmgCgbPalette[1][i]);
            writeCgbPaletteEntry(cgbObjPalette, 1, i, dmgCgbPalette[2][i]);
        }
    }

    private void writeCgbPaletteEntry(byte[] palette, int paletteIndex, int colorIndex, int color) {
        int index = (paletteIndex & 0x07) * 8 + (colorIndex & 0x03) * 2;
        palette[index] = (byte) (color & 0xFF);
        palette[index + 1] = (byte) ((color >>> 8) & 0xFF);
    }
    
    /**
     * Returns the currently loaded cartridge, or null if no cartridge is loaded.
     */
    public Cartridge getCartridge() {
        return this.cartridge;
    }


    // 16-bit write method
    public void writeChar(int address, int value) {
        // Game Boy uses little-endian: low byte first, high byte second
        writeByte(address, value & 0xFF); // Low byte
        writeByte(address + 1, (value >> 8) & 0xFF); // High byte
    }

    // I/O Register handling for CGB features
}
