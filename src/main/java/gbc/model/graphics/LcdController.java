package gbc.model.graphics;

import gbc.model.HardwareType;

/**
 * LCD timing controller extracted from Memory.
 * Manages LCD mode transitions, LY counter, LYC compare, and STAT interrupts.
 */
public class LcdController {

    private static final int LCD_TOTAL_LINES = 154;
    private static final int LCD_VBLANK_START_LINE = 144;
    private static final int LCD_CYCLES_PER_LINE = 456;
    private static final int LCD_ENABLE_DELAY = 244;
    static final int LCD_MODE_HBLANK = 0;
    static final int LCD_MODE_VBLANK = 1;
    static final int LCD_MODE_OAM = 2;
    static final int LCD_MODE_TRANSFER = 3;

    private ModeChangeListener modeChangeListener;

    // Optional LCD trace (disabled by default)
    private final boolean traceEnabled = Boolean.getBoolean("gbc.lcd.trace");
    private final String[] traceBuffer = traceEnabled
            ? new String[Math.max(64, Integer.getInteger("gbc.lcd.trace.size", 2048))]
            : null;
    private int traceIndex;
    private int traceCount;

    // Registers
    private int lcdc = 0x91;
    private int stat = 0x85;
    private int scy;
    private int scx;
    private int ly;
    private int lyc;
    private int wy;
    private int wx;
    private int bgp = 0xFC;
    private int obp0 = 0xFF;
    private int obp1 = 0xFF;

    // CGB palettes
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

    // LCD timing state
    private int lcdCycleCounter;
    private int lcdMode = LCD_MODE_VBLANK;
    private boolean lycMatch = true;
    private int cachedMode3MinEnd = 80 + 172;
    // LCD enable delay counter (in dots). When >0, LCD is effectively off but bit 7
    // is set.
    private final int lcdEnableDelayCycles = Integer.getInteger("gbc.lcd.enableDelay", LCD_ENABLE_DELAY);
    private int lcdEnableDelay = 0;
    private boolean statWriteQuirkEnabled = false;

    // STAT interrupt rising-edge detection for the OR of all enabled sources.
    private boolean statInterruptLine = false;
    private int statIrqDelayCycles = 0;
    private int statIrqDelay = 0;

    // Interrupt flag reference (written by this controller, read by Memory)
    private InterruptSink interruptSink;

    // Mode3 duration supplier
    private Mode3DurationSupplier mode3Supplier;
    private Mode3CompletionSupplier mode3CompletionSupplier;

    // HDMA trigger callback
    private HdmaCallback hdmaCallback;

    @FunctionalInterface
    public interface InterruptSink {
        void requestInterrupt(int bit);
    }

    @FunctionalInterface
    public interface Mode3DurationSupplier {
        int getMode3Duration();
    }

    @FunctionalInterface
    public interface Mode3CompletionSupplier {
        boolean isMode3TransferComplete();
    }

    @FunctionalInterface
    public interface HdmaCallback {
        void onHblankDma(int ly);
    }

    @FunctionalInterface
    public interface ModeChangeListener {
        void onModeChange(int oldMode, int newMode, int ly);
    }

    public LcdController() {
    }

    public void setModeChangeListener(ModeChangeListener listener) {
        this.modeChangeListener = listener;
    }

    public void setInterruptSink(InterruptSink sink) {
        this.interruptSink = sink;
    }

    public void setMode3DurationSupplier(Mode3DurationSupplier supplier) {
        this.mode3Supplier = supplier;
    }

    public void setMode3CompletionSupplier(Mode3CompletionSupplier supplier) {
        this.mode3CompletionSupplier = supplier;
    }

    public void setHdmaCallback(HdmaCallback callback) {
        this.hdmaCallback = callback;
    }

    public void setStatWriteQuirkEnabled(boolean enabled) {
        this.statWriteQuirkEnabled = enabled;
    }

    public void setStatIrqDelayCycles(int cycles) {
        this.statIrqDelayCycles = Math.max(0, cycles);
    }

    // --- Step (called once per T-cycle) ---

    public void step() {
        if (statIrqDelay > 0) {
            statIrqDelay--;
            if (statIrqDelay == 0 && interruptSink != null) {
                trace("IRQ STAT");
                interruptSink.requestInterrupt(0x02);
            }
        }
        if ((lcdc & 0x80) == 0) {
            // LCD is off: LY stays at 0, mode stays HBlank.
            // Hardware keeps the LYC flag latched while LCD is off.
            // LY reads as 0, but the coincidence flag is not continuously updated.
            // When LCD is re-enabled (bit 7 set), a startup delay is applied
            // (see handleLcdcWrite) during which LY=0 and mode=HBlank are held.
            // After the delay, mode transitions to OAM (mode 2) for line 0.
            return;
        }
        if (lcdEnableDelay > 0) {
            lcdEnableDelay--;
            // Hold LY and mode 0 during the startup delay.
            ly = 0;
            lcdCycleCounter = 0;
            forceLcdMode(LCD_MODE_HBLANK);
            updateLycFlag();
            if (lcdEnableDelay == 0) {
                // Start first line immediately after the delay.
                refreshMode3MinEnd();
                setLcdMode(LCD_MODE_OAM);
            }
            return;
        }
        if (lcdCycleCounter == 0 && ly < LCD_VBLANK_START_LINE) {
            refreshMode3MinEnd();
        }
        // Update mode based on the current dot before advancing the counter.
        updateLcdModeForCurrentCycle();

        lcdCycleCounter++;
        if (lcdCycleCounter >= LCD_CYCLES_PER_LINE) {
            lcdCycleCounter = 0;
            int previousLy = ly;
            ly = (ly + 1) % LCD_TOTAL_LINES;
            if (ly == LCD_VBLANK_START_LINE) {
                requestVBlankInterrupt();
            }
            if (previousLy != ly) {
                trace(String.format("LY %03d->%03d", previousLy, ly));
                updateLycFlag();
            }
            if (ly < LCD_VBLANK_START_LINE) {
                refreshMode3MinEnd();
            }
            // Immediately update mode for the new line to avoid a 1-dot mismatch
            // between LY and STAT mode bits.
            updateLcdModeForCurrentCycle();
        }
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
            int mode3MaxEnd = 80 + 289;
            boolean transferComplete = mode3CompletionSupplier != null
                    && mode3CompletionSupplier.isMode3TransferComplete();
            if (lcdCycleCounter < cachedMode3MinEnd) {
                newMode = LCD_MODE_TRANSFER;
            } else if (!transferComplete && lcdCycleCounter < mode3MaxEnd) {
                newMode = LCD_MODE_TRANSFER;
            } else {
                newMode = LCD_MODE_HBLANK;
            }
        }
        setLcdMode(newMode);
    }

    private void refreshMode3MinEnd() {
        int mode3Min = (mode3Supplier != null) ? mode3Supplier.getMode3Duration() : 172;
        cachedMode3MinEnd = 80 + mode3Min;
    }

    private void setLcdMode(int newMode) {
        if (lcdMode == newMode) {
            return;
        }

        int oldMode = lcdMode;
        lcdMode = newMode;
        stat = (stat & ~0x03) | (newMode & 0x03) | 0x80;

        trace(String.format("MODE %d->%d STAT=%02X", oldMode, newMode, stat));
        if (modeChangeListener != null) {
            modeChangeListener.onModeChange(oldMode, newMode, ly);
        }

        if (newMode == LCD_MODE_HBLANK && hdmaCallback != null && ly < LCD_VBLANK_START_LINE) {
            hdmaCallback.onHblankDma(ly);
        }

        // Rising-edge STAT interrupt check is done in evaluateStatInterruptLine()
        evaluateStatInterruptLine();
    }

    private void forceLcdMode(int newMode) {
        lcdMode = newMode & 0x03;
        stat = (stat & ~0x03) | lcdMode | 0x80;
    }

    public void updateLycFlag() {
        boolean match = ly == lyc;
        if (match) {
            stat |= 0x04;
        } else {
            stat &= ~0x04;
        }
        if (match != lycMatch) {
            trace(String.format("LYC %s (LY=%03d LYC=%03d STAT=%02X)", match ? "ON" : "OFF", ly, lyc, stat));
        }
        lycMatch = match;
        // Rising-edge STAT interrupt check
        evaluateStatInterruptLine();
    }

    /**
     * Evaluate the combined STAT interrupt line.
     * The STAT interrupt fires only on a rising edge (false→true) of the
     * OR of all enabled STAT sources.
     */
    private void evaluateStatInterruptLine() {
        if ((lcdc & 0x80) == 0) {
            statInterruptLine = false;
            return;
        }

        boolean lyc = (stat & 0x40) != 0 && (stat & 0x04) != 0;
        boolean allowModes = lcdEnableDelay == 0;
        boolean mode2 = allowModes && (stat & 0x20) != 0 && lcdMode == LCD_MODE_OAM;
        boolean mode1 = allowModes && (stat & 0x10) != 0 && lcdMode == LCD_MODE_VBLANK;
        boolean mode0 = allowModes && (stat & 0x08) != 0 && lcdMode == LCD_MODE_HBLANK;
        boolean line = lyc || mode2 || mode1 || mode0;

        if (line && !statInterruptLine) {
            boolean allowDelay = mode2;
            requestLcdStatInterrupt(allowDelay);
        }
        statInterruptLine = line;
    }

    private boolean isStatLineHigh(int statValue) {
        boolean line = false;
        if ((statValue & 0x40) != 0 && (statValue & 0x04) != 0)
            line = true; // LYC=LY
        if ((statValue & 0x20) != 0 && lcdMode == LCD_MODE_OAM)
            line = true; // Mode 2
        if ((statValue & 0x10) != 0 && lcdMode == LCD_MODE_VBLANK)
            line = true; // Mode 1
        if ((statValue & 0x08) != 0 && lcdMode == LCD_MODE_HBLANK)
            line = true; // Mode 0
        return line;
    }

    // --- Register writes ---

    public void handleLcdcWrite(int value) {
        int newLcdc = value & 0xFF;
        boolean wasEnabled = (lcdc & 0x80) != 0;
        boolean isEnabled = (newLcdc & 0x80) != 0;
        lcdc = newLcdc;
        trace(String.format("LCDC=%02X (was %s)", lcdc, wasEnabled ? "on" : "off"));

        if (!isEnabled) {
            lcdCycleCounter = 0;
            ly = 0;
            lcdEnableDelay = 0;
            forceLcdMode(LCD_MODE_HBLANK);
            // Preserve the LYC flag state while LCD is disabled.
            // STAT line must be recalculated since mode changed.
            evaluateStatInterruptLine();
        } else if (!wasEnabled) {
            // LCD re-enabled: starts at LY=0, cycle 0.
            lcdCycleCounter = 0;
            ly = 0;
            lcdEnableDelay = lcdEnableDelayCycles;
            forceLcdMode(LCD_MODE_HBLANK);
            cachedMode3MinEnd = 80 + 172;
            updateLycFlag();
            evaluateStatInterruptLine();
        }
    }

    public void writeStatFromWrite(int value) {
        int newStat = 0x80 | (value & 0x78) | (stat & 0x07);
        if (statWriteQuirkEnabled && (lcdc & 0x80) != 0 && lcdEnableDelay == 0) {
            boolean line = isStatLineHigh(newStat);
            if (line) {
                trace("IRQ STAT (STAT write quirk)");
                requestLcdStatInterrupt(lcdMode == LCD_MODE_OAM);
            }
            stat = newStat;
            statInterruptLine = line;
            trace(String.format("STAT write=%02X -> STAT=%02X", value & 0xFF, stat));
            return;
        }
        stat = newStat;
        trace(String.format("STAT write=%02X -> STAT=%02X", value & 0xFF, stat));
        // Re-evaluate STAT line with new enable bits — rising edge will fire if needed
        evaluateStatInterruptLine();
    }

    public void writeLy() {
        ly = 0;
        lcdCycleCounter = 0;
        if ((lcdc & 0x80) != 0) {
            refreshMode3MinEnd();
        }
        if ((lcdc & 0x80) != 0) {
            setLcdMode(LCD_MODE_OAM);
        } else {
            forceLcdMode(LCD_MODE_HBLANK);
        }
        updateLycFlag();
    }

    public void writeLyc(int value) {
        lyc = value & 0xFF;
        trace(String.format("LYC write=%02X", lyc));
        if ((lcdc & 0x80) != 0 || lcdEnableDelay > 0) {
            updateLycFlag();
        }
    }

    // --- Register read accessors ---

    public int getLcdc() {
        return lcdc;
    }

    public int getStat() {
        return stat;
    }

    public int getScy() {
        return scy;
    }

    public int getScx() {
        return scx;
    }

    public int getLcdCycleCounter() {
        return lcdCycleCounter;
    }

    public int getLy() {
        return ly;
    }

    public int getLyc() {
        return lyc;
    }

    public int getWy() {
        return wy;
    }

    public int getWx() {
        return wx;
    }

    public int getBgp() {
        return bgp;
    }

    public int getObp0() {
        return obp0;
    }

    public int getObp1() {
        return obp1;
    }

    public int getLcdMode() {
        return lcdMode;
    }

    // --- Register write accessors ---

    public void setLcdc(int v) {
        lcdc = v;
    }

    public void setScy(int v) {
        scy = v & 0xFF;
    }

    public void setScx(int v) {
        scx = v & 0xFF;
    }

    public void setLyRaw(int v) {
        ly = v;
    }

    public void setWy(int v) {
        wy = v & 0xFF;
    }

    public void setWx(int v) {
        wx = v & 0xFF;
    }

    public void setBgp(int v) {
        bgp = v & 0xFF;
    }

    public void setObp0(int v) {
        obp0 = v & 0xFF;
    }

    public void setObp1(int v) {
        obp1 = v & 0xFF;
    }

    // --- CGB Palette ---

    public int readCgbBgPaletteByte(int index) {
        return cgbBgPalette[index & 0x3F] & 0xFF;
    }

    public int readCgbObjPaletteByte(int index) {
        return cgbObjPalette[index & 0x3F] & 0xFF;
    }

    public int getBgpi() {
        return (bgpi & 0x3F) | (bgpiAutoInc ? 0x80 : 0);
    }

    public int getObpi() {
        return (obpi & 0x3F) | (obpiAutoInc ? 0x80 : 0);
    }

    public void writeBgpi(int value) {
        bgpi = value & 0x3F;
        bgpiAutoInc = (value & 0x80) != 0;
    }

    public void writeBgpd(int value) {
        cgbBgPalette[bgpi & 0x3F] = (byte) value;
        if (bgpiAutoInc) {
            bgpi = (bgpi + 1) & 0x3F;
        }
    }

    public void writeObpi(int value) {
        obpi = value & 0x3F;
        obpiAutoInc = (value & 0x80) != 0;
    }

    public void writeObpd(int value) {
        cgbObjPalette[obpi & 0x3F] = (byte) value;
        if (obpiAutoInc) {
            obpi = (obpi + 1) & 0x3F;
        }
    }

    public int readBgpd() {
        return readCgbBgPaletteByte(bgpi);
    }

    public int readObpd() {
        return readCgbObjPaletteByte(obpi);
    }

    public int resolveDmgCgbColor(boolean obj, int paletteIndex, int shadeIndex) {
        int idx = obj ? (1 + (paletteIndex & 0x01)) : 0;
        int[] palette = dmgCgbPalette[idx];
        int color = palette[Math.max(0, Math.min(3, shadeIndex))] & 0x7FFF;
        int r = color & 0x1F;
        int g = (color >> 5) & 0x1F;
        int b = (color >> 10) & 0x1F;
        int rr = (r << 3) | (r >> 2);
        int gg = (g << 3) | (g >> 2);
        int bb = (b << 3) | (b >> 2);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    public void setDmgCgbPalette(int[][] palette) {
        this.dmgCgbPalette = palette;
    }

    public int[][] getDmgCgbPalette() {
        return dmgCgbPalette;
    }

    public byte[] getCgbBgPalette() {
        return cgbBgPalette;
    }

    public byte[] getCgbObjPalette() {
        return cgbObjPalette;
    }

    public void initDefaultCgbPalettes() {
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

    public void writeCgbPaletteEntry(byte[] palette, int paletteIndex, int colorIndex, int color) {
        int index = (paletteIndex & 0x07) * 8 + (colorIndex & 0x03) * 2;
        palette[index] = (byte) (color & 0xFF);
        palette[index + 1] = (byte) ((color >>> 8) & 0xFF);
    }

    // --- Reset ---

    public void reset(boolean bootRomEnabled, HardwareType hardwareType) {
        for (int i = 0; i < cgbBgPalette.length; i++) {
            cgbBgPalette[i] = 0;
            cgbObjPalette[i] = 0;
        }
        bgpi = 0;
        obpi = 0;
        bgpiAutoInc = false;
        obpiAutoInc = false;

        statInterruptLine = false;
        lcdEnableDelay = 0;
        statIrqDelay = 0;

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
            lycMatch = ly == lyc;
            forceLcdMode(LCD_MODE_HBLANK);
            updateLycFlag();
        } else {
            HardwareType hw = hardwareType == null ? HardwareType.DMG : hardwareType;
            HardwareType.LcdInitState state = hw.getLcdInitState();
            lcdc = state.lcdc() & 0xFF;
            stat = state.stat() & 0xFF;
            scy = state.scy() & 0xFF;
            scx = state.scx() & 0xFF;
            ly = state.ly() & 0xFF;
            lyc = state.lyc() & 0xFF;
            wy = state.wy() & 0xFF;
            wx = state.wx() & 0xFF;
            bgp = state.bgp() & 0xFF;
            obp0 = state.obp0() & 0xFF;
            obp1 = state.obp1() & 0xFF;
            lcdCycleCounter = hw.getInitialLcdCycleCounter() & 0x1FF;
            forceLcdMode(stat & 0x03);
            if (state.preserveInitialLycFlag()) {
                lycMatch = (stat & 0x04) != 0;
                evaluateStatInterruptLine();
            } else {
                lycMatch = ly == lyc;
                updateLycFlag();
            }
        }
    }

    // --- Interrupt helpers ---

    private void requestLcdStatInterrupt(boolean allowDelay) {
        if (interruptSink != null) {
            if (allowDelay && statIrqDelayCycles > 0) {
                if (statIrqDelay == 0) {
                    statIrqDelay = statIrqDelayCycles;
                }
            } else {
                trace("IRQ STAT");
                interruptSink.requestInterrupt(0x02);
            }
        }
    }

    private void requestVBlankInterrupt() {
        if (interruptSink != null) {
            trace("IRQ VBLANK");
            interruptSink.requestInterrupt(0x01);
        }
    }

    public boolean isOamBlocked() {
        return (lcdc & 0x80) != 0 && (lcdMode == LCD_MODE_OAM || lcdMode == LCD_MODE_TRANSFER);
    }

    public boolean isVramBlocked() {
        return (lcdc & 0x80) != 0 && lcdMode == LCD_MODE_TRANSFER;
    }

    public String dumpTrace() {
        if (!traceEnabled || traceBuffer == null || traceCount == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = Math.min(traceCount, traceBuffer.length);
        int start = traceIndex - count;
        if (start < 0) {
            start += traceBuffer.length;
        }
        for (int i = 0; i < count; i++) {
            int idx = (start + i) % traceBuffer.length;
            String line = traceBuffer[idx];
            if (line != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void trace(String message) {
        if (!traceEnabled || traceBuffer == null) {
            return;
        }
        String line = String.format("[LY=%03d DOT=%03d MODE=%d] %s", ly, lcdCycleCounter, lcdMode, message);
        traceBuffer[traceIndex] = line;
        traceIndex = (traceIndex + 1) % traceBuffer.length;
        if (traceCount < traceBuffer.length) {
            traceCount++;
        }
    }
}
