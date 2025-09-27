package gbc.model.memory;

import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.Registers;
import gbc.model.cpu.CPU; // Import CPU class
import gbc.model.sound.Apu;
import gbc.core.input.Controller;

public class Memory {
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

    private enum TimerState {
        NORMAL,
        OVERFLOW
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
    private int lcdCycleCounter = 0;
    private int lcdMode = LCD_MODE_VBLANK;
    private boolean lycMatch = true;

    // Cartridge and peripherals
    private Cartridge cartridge;
    private CPU cpu;
    private Apu apu;
    private Controller controller;
    private int vramBank = 0;
    private int wramBank = 1;

    // Helper to get the DIV visible register value from the 16-bit divider
    private int getDivRegister() {
        return (divider >> 8) & 0xFF;
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

        if (timerState == TimerState.OVERFLOW && reloadPending) {
            // TIMA is locked while a reload is pending
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
            timerState = TimerState.OVERFLOW;
            reloadPending = true;
            overflowDelayActive = true;
            // Account for decrement in the same T-cycle that triggered the overflow so that
            // the reload completes exactly four T-cycles later (one machine cycle).
            stateCounterT = MACHINE_CYCLE_T_CYCLES + 1;
            traceTimer("overflow -> reload pending");
        } else {
            tima = (tima + 1) & 0xFF;
        }
    }

    private void updateTimerState() {
        if (overflowDelayActive) {
            if (stateCounterT > 0) {
                stateCounterT--;
                if (stateCounterT == 0) {
                    boolean reloaded = reloadPending;
                    if (reloaded) {
                        tima = tma & 0xFF;
                    }
                    timerState = TimerState.NORMAL;
                    overflowDelayActive = false;
                    reloadPending = false;
                    if (reloaded) {
                        requestTimerInterrupt();
                    }
                    traceTimer(reloaded ? "delay complete -> TIMA reloaded" : "delay complete -> reload cancelled");
                }
            }
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

        System.out.println(sb.toString());
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

        System.out.println(sb.toString());
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

        System.out.println(sb.toString());

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
        } else if (lcdCycleCounter < 80 + 172) {
            newMode = LCD_MODE_TRANSFER;
        } else {
            newMode = LCD_MODE_HBLANK;
        }
        setLcdMode(newMode);
        updateLycFlag();
    }

    private void setLcdMode(int newMode) {
        if (lcdMode == newMode) {
            return;
        }

        lcdMode = newMode;
        stat = (stat & ~0x03) | (newMode & 0x03) | 0x80;

        switch (newMode) {
            case LCD_MODE_HBLANK -> {
                if ((stat & 0x08) != 0) {
                    requestLcdStatInterrupt();
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
        reset();
    }

    public void setCPU(CPU cpu) {
        this.cpu = cpu;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    // APU access methods
    public void stepApu() {
        apu.step();
    }

    public boolean isAudioBufferFull() {
        return apu.isBufferFull();
    }

    public byte[] fetchAudioSamples() {
        return apu.fetchSamples();
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

            totalTCycles++;
        }
    }

    public int readByte(int address) {
        int addr = address & 0xFFFF;

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
            return interruptEnable & 0xFF;
        }

        // I/O registers and other special areas (0xFEA0-0xFEFF) - unmapped
        if (isUnusable(addr)) {
            return 0xFF; // Return 0xFF for unmapped areas
        }

        // OAM (Object Attribute Memory) (0xFE00-0xFE9F)
        if (isOam(addr)) {
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
            return readVideoRam(addr);
        }

        // Cartridge RAM (0xA000-0xBFFF)
        if (isCartridgeRam(addr)) {
            return cartridge.read(addr) & 0xFF;
        }

        // Cartridge ROM/RAM (0x0000-0x7FFF)
        if (isCartridgeRom(addr)) {
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

        if (address < 0x8000) {
            // ROM area, handled by cartridge
            if (cartridge != null) {
                cartridge.write(address, byteValue);
            }
            return;
        }
        if (address >= 0x8000 && address < 0xA000) {
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
            interruptEnable = byteValue;
            return;
        }
    }

    private int readIORegister(int address) {
        // Joypad register (0xFF00)
        if (address == 0xFF00) {
            if (controller != null) {
                return controller.getJoypadState();
            }
            return 0xCF; // Default: all buttons released
        }

        // Timer & PPU registers with Game Boy default values
        switch (address) {
            case 0xFF04: // DIV - Divider register
                return getDivRegister();
            case 0xFF05: // TIMA - Timer counter
                return tima & 0xFF;
            case 0xFF06: // TMA - Timer modulo
                return tma & 0xFF;
            case 0xFF07: // TAC - Timer control
                return tac & 0xFF;
            case 0xFF40: // LCDC - LCD Control
                return lcdc & 0xFF;
            case 0xFF41: // STAT - LCD Status
                return stat & 0xFF;
            case 0xFF42: // SCY - Scroll Y
                return scy & 0xFF;
            case 0xFF43: // SCX - Scroll X
                return scx & 0xFF;
            case 0xFF44: // LY - LCD Y coordinate (current scanline)
                return ly & 0xFF;
            case 0xFF45: // LYC - LY compare
                return lyc & 0xFF;
            case 0xFF46: // DMA - DMA transfer
                return 0x00;
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
                if (cpu != null) {
                    return cpu.readKey1();
                }
                return 0;
            case 0xFF4F: // VBK - VRAM bank select
                return vramBank;
            case 0xFF70: // SVBK - WRAM bank select
                return wramBank;
            case 0xFF0F: // IF - interrupt flags
                // IF is stored in a dedicated field
                return interruptFlag & 0xFF;
            default:
                // For unhandled I/O registers, return 0
                return 0;
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
                if (timerState == TimerState.OVERFLOW) {
                    // Cancel any pending reload while respecting the write override behaviour
                    reloadPending = false;
                    timerState = TimerState.NORMAL;
                    overflowDelayActive = false;
                    stateCounterT = 0;
                }
                tima = newTima;
                traceTimer(String.format("write TIMA=%02X", newTima));
                return;
            }
            case 0xFF06: // TMA - Timer modulo
                tma = value & 0xFF;
                traceTimer(String.format("write TMA=%02X", tma & 0xFF));
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
                interruptFlag = value;
                return;
            case 0xFF46: // DMA - OAM DMA transfer
                // TODO: implement proper cycle-accurate OAM DMA
                // For now, simple copy
                int source = (value & 0xFF) << 8;
                for (int i = 0; i < 0xA0; i++) {
                    int srcAddr = (source + i) & 0xFFFF;
                    int b = readByte(srcAddr) & 0xFF;
                    oam[i] = (byte) b;
                }
                return;
            case 0xFF4D: // KEY1 - Speed control register
                if (cpu != null) {
                    cpu.writeKey1(value & 0xFF);
                }
                return;
            case 0xFF4F: // VBK - VRAM bank select
                vramBank = value & 0x01; // Only bit 0 is used
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
                wramBank = (value & 0x07); // Bits 0-2
                if (wramBank == 0)
                    wramBank = 1; // Bank 0 maps to bank 1
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

        // Reset timer registers/state
        divider = 0;
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

        // Initialize other registers as required
        lcdc = 0x91;
        stat = 0x85;
        scy = 0;
        scx = 0;
        ly = 0;
        lyc = 0;
        wy = 0;
        wx = 0;
        bgp = 0xFC;
        obp0 = 0xFF;
        obp1 = 0xFF;
        lcdCycleCounter = 0;
        lycMatch = ly == lyc;
        forceLcdMode(LCD_MODE_VBLANK);
        updateLycFlag();
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
    }

    // 16-bit write method
    public void writeChar(int address, int value) {
        // Game Boy uses little-endian: low byte first, high byte second
        writeByte(address, value & 0xFF); // Low byte
        writeByte(address + 1, (value >> 8) & 0xFF); // High byte
    }

    // I/O Register handling for CGB features
}
