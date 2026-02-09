package gbc.model.memory;

import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.CPU;
import gbc.model.cpu.Registers;
import gbc.model.event.EmulatorEvent;
import gbc.model.event.EventBus;
import gbc.model.graphics.LcdController;
import gbc.model.input.Controller;
import gbc.model.sound.Apu;
import gbc.model.timer.Timer;

public class Memory implements MemoryBus {
    // DMG/CGB IO register differences are handled via cgbMode checks in
    // readIORegister/writeIORegister. Per-dot OAM/VRAM access conflicts
    // are enforced by LcdController.isOamBlocked()/isVramBlocked() which
    // check the current LCD mode before allowing reads/writes.

    // Memory arrays
    private final byte[] videoRam0 = new byte[0x2000];
    private final byte[] videoRam1 = new byte[0x2000];
    // Unified Work RAM: bank 0 at index 0, banks 1-7 at indices 1-7
    private final byte[][] workRam = new byte[8][0x1000];
    private final byte[] highRam = new byte[0x7F];
    private final byte[] oam = new byte[0xA0];
    private byte interruptEnable;
    private byte interruptFlag = 0x00;

    // Serial port registers
    private byte serialData;
    private byte serialControl;
    private boolean serialTransferActive;
    private boolean serialInternalClock;
    private int serialBitsRemaining;
    private int serialClockBit = 8;
    private int joypadRegister = 0xCF;

    // Serial output callback for test harnesses
    private SerialOutputCallback serialOutputCallback;

    // Subcomponents
    private final Timer timer;
    private final LcdController lcd;
    private final DmaController dma;

    // Peripherals
    private Cartridge cartridge;
    private CPU cpu;
    private Apu apu;
    private Controller controller;
    private gbc.model.graphics.PPU ppu;
    private EventBus eventBus;

    private int vramBank;
    private int wramBank = 1;
    private gbc.model.HardwareType hardwareType = gbc.model.HardwareType.DMG;
    private boolean hardwareOverride;
    private boolean cgbMode;
    private byte[] bootRom;
    private boolean bootRomEnabled;

    private int apuCycleCounter;

    // M-cycle callback: called by readByte/writeByte during CPU instruction
    // execution
    // to step peripherals for each memory access M-cycle
    private Runnable mcycleCallback;

    public Memory() {
        this.eventBus = new EventBus();
        this.timer = new Timer(eventBus);
        this.lcd = new LcdController();
        this.dma = new DmaController();
        this.apu = new Apu();

        // Wire DMA
        this.dma.setOam(oam);
        this.dma.setOamReader(this::readByteOamDma);
        this.dma.setHdmaReader(this::readByteInternal);
        this.dma.setVramWriter(this::writeVideoRam);

        // Wire LCD interrupts
        this.lcd.setInterruptSink(bit -> interruptFlag |= (byte) bit);

        // Wire timer interrupt via EventBus
        this.eventBus.subscribe(event -> {
            if (event instanceof EmulatorEvent.InterruptRequest ir) {
                interruptFlag |= (byte) ir.bit();
            }
        });

        loadBootRomFromProperty();
        reset();
    }

    private void loadBootRomFromProperty() {
        // Boot ROM usage disabled by policy: always start from post-boot state
        // with exact DIV preset values (no boot ROM execution).
        bootRom = null;
        bootRomEnabled = false;
    }

    // --- Component accessors ---

    public void setCPU(CPU cpu) {
        this.cpu = cpu;
        this.timer.setPcSupplier(() -> cpu.getRegisters().getPC() & 0xFFFF);
    }

    public void setPPU(gbc.model.graphics.PPU ppu) {
        this.ppu = ppu;
        this.lcd.setModeChangeListener((oldMode, newMode, ly) -> {
            if (ppu != null) {
                ppu.enterMode(newMode, oldMode);
            }
        });
        this.lcd.setMode3DurationSupplier(() -> ppu != null ? ppu.getMode3Duration() : 172);
        this.lcd.setMode3CompletionSupplier(() -> ppu == null || ppu.isPixelTransferComplete());
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public EventBus getEventBus() {
        return this.eventBus;
    }

    public Timer getTimer() {
        return timer;
    }

    public LcdController getLcdController() {
        return lcd;
    }

    public DmaController getDmaController() {
        return dma;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    @FunctionalInterface
    public interface SerialOutputCallback {
        void onSerialByte(int value);
    }

    public void setSerialOutputCallback(SerialOutputCallback callback) {
        this.serialOutputCallback = callback;
    }

    public void setMcycleCallback(Runnable callback) {
        this.mcycleCallback = callback;
    }

    // --- Hardware type ---

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

    public gbc.model.HardwareType getHardwareType() {
        return this.hardwareType;
    }

    public boolean isCgbMode() {
        return cgbMode;
    }

    public boolean isDmgOnCgb() {
        return hardwareType != null && hardwareType.isCgb() && !cgbMode;
    }

    // --- PPU/LCD Direct Accessors (delegate to LcdController) ---

    public int getLcdc() {
        return lcd.getLcdc();
    }

    public int getStat() {
        return lcd.getStat();
    }

    public int getLcdCycleCounter() {
        return lcd.getLcdCycleCounter();
    }

    public int getScy() {
        return lcd.getScy();
    }

    public int getScx() {
        return lcd.getScx();
    }

    public int getLy() {
        return lcd.getLy();
    }

    public int getLyc() {
        return lcd.getLyc();
    }

    public int getWy() {
        return lcd.getWy();
    }

    public int getWx() {
        return lcd.getWx();
    }

    public int getBgp() {
        return lcd.getBgp();
    }

    public int getObp0() {
        return lcd.getObp0();
    }

    public int getObp1() {
        return lcd.getObp1();
    }

    public int readCgbBgPaletteByte(int index) {
        return lcd.readCgbBgPaletteByte(index);
    }

    public int readCgbObjPaletteByte(int index) {
        return lcd.readCgbObjPaletteByte(index);
    }

    public int getBgpi() {
        return lcd.getBgpi();
    }

    public int getObpi() {
        return lcd.getObpi();
    }

    public int resolveDmgCgbColor(boolean obj, int paletteIndex, int shadeIndex) {
        return lcd.resolveDmgCgbColor(obj, paletteIndex, shadeIndex);
    }

    // --- OAM/VRAM Direct Accessors ---

    public int getOamByte(int index) {
        if (index >= 0 && index < oam.length) {
            return oam[index] & 0xFF;
        }
        return 0xFF;
    }

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

    // --- DMA Bus lock ---

    public boolean isDmaActive() {
        return dma.isDmaActive();
    }

    public boolean isOamDmaBusLocked(int address) {
        return dma.isBusLocked(address & 0xFFFF);
    }

    // --- Step peripherals ---

    public void stepPeripherals(int cycles) {
        if (cycles <= 0)
            return;

        // Batch process APU (runs at 1/4 CPU speed)
        apuCycleCounter += cycles;
        if (apuCycleCounter >= 4 && apu != null) {
            int apuCycles = (apuCycleCounter / 4) * 4;
            apu.step(apuCycles);
            apuCycleCounter %= 4;
        }

        // Check if we can use batch Timer processing
        final boolean dmaActive = dma.isDmaActive();
        final boolean ppuActive = ppu != null;
        final boolean serialIdle = !serialTransferActive || !serialInternalClock;

        // Fast path: if no serial and no DMA, we can batch some operations
        if (serialIdle && !dmaActive) {
            // Use batch timer step
            timer.stepCycles(cycles);

            // LCD and PPU - unrolled for 4 iterations to reduce loop overhead
            if (ppuActive) {
                int i = 0;
                final int end4 = cycles - 3;
                for (; i < end4; i += 4) {
                    lcd.step();
                    ppu.tick(1);
                    lcd.step();
                    ppu.tick(1);
                    lcd.step();
                    ppu.tick(1);
                    lcd.step();
                    ppu.tick(1);
                }
                for (; i < cycles; i++) {
                    lcd.step();
                    ppu.tick(1);
                }
            } else {
                // No PPU - just LCD steps
                for (int i = 0; i < cycles; i++) {
                    lcd.step();
                }
            }
        } else {
            // Standard per-cycle processing when serial or DMA active
            for (int i = 0; i < cycles; i++) {
                int oldDivider = timer.getDividerRaw();
                timer.step();
                int newDivider = timer.getDividerRaw();
                lcd.step();
                if (ppuActive) {
                    ppu.tick(1);
                }
                if (dmaActive) {
                    dma.stepOamDma();
                }
                stepSerial(oldDivider, newDivider);
            }
        }
    }

    /**
     * Step peripherals that run at CPU speed in CGB double-speed mode.
     * These include Timer/Divider, Serial, and OAM DMA.
     */
    public void stepFastPeripherals(int cycles) {
        if (cycles <= 0)
            return;

        final boolean dmaActive = dma.isDmaActive();
        final boolean serialIdle = !serialTransferActive || !serialInternalClock;

        // Fast path when serial and DMA are idle
        if (serialIdle && !dmaActive) {
            timer.stepCycles(cycles);
        } else {
            for (int i = 0; i < cycles; i++) {
                int oldDivider = timer.getDividerRaw();
                timer.step();
                int newDivider = timer.getDividerRaw();
                dma.stepOamDma();
                stepSerial(oldDivider, newDivider);
            }
        }
    }

    // --- APU ---

    public void stepApu(int cycles) {
        apu.step(cycles);
    }

    public void reloadApuAudioSettings() {
        if (apu != null) {
            apu.reloadAudioSettings();
        }
    }

    public boolean isAudioBufferFull() {
        return apu.isBufferFull();
    }

    public byte[] fetchAudioSamples() {
        return apu.fetchSamples();
    }

    // --- Memory read/write (MemoryBus implementation) ---

    @Override
    public int readByte(int address) {
        // Capture and clear callback to prevent recursive triggers (e.g. echo RAM)
        Runnable cb = mcycleCallback;
        if (cb != null) {
            mcycleCallback = null;
        }

        int result = readByteCore(address);

        // Step peripherals AFTER the read (matching hardware: read happens at start of
        // M-cycle,
        // peripherals advance during the M-cycle)
        if (cb != null) {
            cb.run();
            mcycleCallback = cb;
        }
        return result;
    }

    @Override
    public int peekByte(int address) {
        // Side-effect-free read: no peripheral stepping, no m-cycle callback.
        return readByteCore(address);
    }

    private int readByteCore(int address) {
        int addr = address & 0xFFFF;

        if (dma.isBusLocked(addr)) {
            return 0xFF;
        }

        // Optimized dispatch: ordered by access frequency, using direct comparisons
        // Most accesses go to ROM, then Work RAM, then Video RAM

        // Cartridge ROM: 0x0000-0x7FFF (most frequent)
        if (addr < 0x8000) {
            if (bootRomEnabled && bootRom != null) {
                if (addr < 0x0100) {
                    return bootRom[addr] & 0xFF;
                }
                if (bootRom.length >= 0x900 && addr >= 0x0200 && addr < 0x0900) {
                    return bootRom[addr] & 0xFF;
                }
            }
            return cartridge != null ? (cartridge.read(addr) & 0xFF) : 0xFF;
        }

        // Video RAM: 0x8000-0x9FFF
        if (addr < 0xA000) {
            if (lcd.isVramBlocked()) {
                return 0xFF;
            }
            return readVideoRam(addr);
        }

        // Cartridge RAM: 0xA000-0xBFFF
        if (addr < 0xC000) {
            return cartridge != null ? (cartridge.read(addr) & 0xFF) : 0xFF;
        }

        // Work RAM: 0xC000-0xDFFF
        if (addr < 0xE000) {
            return readWorkRam(addr);
        }

        // Echo RAM: 0xE000-0xFDFF
        if (addr < 0xFE00) {
            return readWorkRam(addr - 0x2000);
        }

        // OAM: 0xFE00-0xFE9F
        if (addr < 0xFEA0) {
            // OAM is blocked during modes 2 (OAM search) and 3 (pixel transfer)
            // and during active OAM DMA transfers
            if (lcd.isOamBlocked() || dma.isDmaActive()) {
                return 0xFF;
            }
            return oam[addr - 0xFE00] & 0xFF;
        }

        // Unusable: 0xFEA0-0xFEFF
        if (addr < 0xFF00) {
            if (lcd.isOamBlocked()) {
                return 0xFF;
            }
            return (hardwareType != null && !hardwareType.isCgb()) ? 0x00 : 0xFF;
        }

        // I/O Registers: 0xFF00-0xFF7F
        if (addr < 0xFF80) {
            return readIORegister(addr);
        }

        // High RAM: 0xFF80-0xFFFE
        if (addr < 0xFFFF) {
            return highRam[addr - 0xFF80] & 0xFF;
        }

        // Interrupt Enable: 0xFFFF
        return interruptEnable & 0xFF;
    }

    @Override
    public void writeByte(int address, int value) {
        // Capture and clear callback to prevent recursive triggers
        Runnable cb = mcycleCallback;
        if (cb != null) {
            mcycleCallback = null;
        }

        writeByteCore(address, value);

        // Step peripherals AFTER the write (matching hardware: write happens at start
        // of M-cycle,
        // peripherals advance during the M-cycle and can observe the new value)
        if (cb != null) {
            cb.run();
            mcycleCallback = cb;
        }
    }

    private void writeByteCore(int address, int value) {
        address &= 0xFFFF;
        byte byteValue = (byte) (value & 0xFF);

        if (dma.isBusLocked(address)) {
            if (address != 0xFF46) {
                return;
            }
        }

        // Optimized dispatch: ordered by access frequency, using cascading comparisons
        if (address < 0x8000) {
            if (cartridge != null)
                cartridge.write(address, byteValue);
            return;
        }
        if (address < 0xA000) {
            if (lcd.isVramBlocked())
                return;
            writeVideoRam(address, byteValue);
            return;
        }
        if (address < 0xC000) {
            if (cartridge != null)
                cartridge.write(address, byteValue);
            return;
        }
        if (address < 0xE000) {
            writeWorkRam(address, byteValue);
            return;
        }
        if (address < 0xFE00) {
            writeWorkRam(address - 0x2000, byteValue);
            return;
        }
        if (address < 0xFEA0) {
            // OAM writes blocked during modes 2/3 and active OAM DMA
            if (lcd.isOamBlocked() || dma.isDmaActive())
                return;
            oam[address - 0xFE00] = byteValue;
            return;
        }
        if (address < 0xFF00) {
            return; // Unusable area
        }
        if (address < 0xFF80) {
            writeIORegister(address, byteValue);
            return;
        }
        if (address < 0xFFFF) {
            highRam[address - 0xFF80] = byteValue;
            return;
        }
        // address == 0xFFFF
        interruptEnable = byteValue;
    }

    @Override
    public int readChar(int address) {
        int low = readByte(address) & 0xFF;
        int high = readByte(address + 1) & 0xFF;
        return (high << 8) | low;
    }

    @Override
    public int peekChar(int address) {
        int low = peekByte(address) & 0xFF;
        int high = peekByte(address + 1) & 0xFF;
        return (high << 8) | low;
    }

    @Override
    public void writeChar(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    // --- I/O Register dispatch ---

    private int readIORegister(int address) {
        switch (address) {
            case 0xFF04:
                return timer.getDivRegister();
            case 0xFF05:
                return timer.getTima();
            case 0xFF06:
                return timer.getTma();
            case 0xFF07:
                return timer.getTac() | 0xF8;
            case 0xFF40:
                return lcd.getLcdc() & 0xFF;
            case 0xFF41:
                return lcd.getStat() | 0x80;
            case 0xFF42:
                return lcd.getScy() & 0xFF;
            case 0xFF43:
                return lcd.getScx() & 0xFF;
            case 0xFF44:
                return lcd.getLy() & 0xFF;
            case 0xFF45:
                return lcd.getLyc() & 0xFF;
            case 0xFF46:
                return dma.getDmaSourceHighByte();
            case 0xFF47:
                return lcd.getBgp() & 0xFF;
            case 0xFF48:
                return lcd.getObp0() & 0xFF;
            case 0xFF49:
                return lcd.getObp1() & 0xFF;
            case 0xFF4A:
                return lcd.getWy() & 0xFF;
            case 0xFF4B:
                return lcd.getWx() & 0xFF;
        }

        // APU registers
        if (address >= 0xFF10 && address <= 0xFF3F) {
            return apu.readRegister(address);
        }

        switch (address) {
            case 0xFF4D:
                if (!cgbMode || cpu == null)
                    return 0xFF;
                return cpu.readKey1();
            case 0xFF4F:
                return cgbMode ? (vramBank | 0xFE) : 0xFF;
            case 0xFF51:
                return cgbMode ? dma.getHdmaSrcHigh() : 0xFF;
            case 0xFF52:
                return cgbMode ? dma.getHdmaSrcLow() : 0xFF;
            case 0xFF53:
                return cgbMode ? dma.getHdmaDstHigh() : 0xFF;
            case 0xFF54:
                return cgbMode ? dma.getHdmaDstLow() : 0xFF;
            case 0xFF55:
                if (!cgbMode)
                    return 0xFF;
                return dma.readHdma5();
            case 0xFF68:
                return cgbMode ? lcd.getBgpi() : 0xFF;
            case 0xFF69:
                return cgbMode ? lcd.readBgpd() : 0xFF;
            case 0xFF6A:
                return cgbMode ? lcd.getObpi() : 0xFF;
            case 0xFF6B:
                return cgbMode ? lcd.readObpd() : 0xFF;
            case 0xFF70:
                return cgbMode ? wramBank : 0xFF;
            case 0xFF0F:
                return (interruptFlag & 0xFF) | 0xE0;
            case 0xFF00:
                if (controller != null) {
                    return (controller.getJoypadState() & 0xFF) | 0xC0;
                }
                return joypadRegister & 0xFF;
            case 0xFF01:
                return serialData;
            case 0xFF02:
                if (hardwareType != null && hardwareType.isCgb()) {
                    return (serialControl & 0x83) | 0x7C; // bit1 is meaningful on CGB
                }
                return (serialControl & 0x81) | 0x7E;
            default:
                return 0xFF;
        }
    }

    private void writeIORegister(int address, byte value) {
        if (address == 0xFF00) {
            joypadRegister = 0xC0 | (value & 0x30) | 0x0F;
            if (controller != null) {
                controller.writeJoypadSelect(value & 0xFF);
            }
            return;
        }
        if (address == 0xFF01) {
            serialData = value;
            return;
        }
        if (address == 0xFF02) {
            serialControl = value;
            // Start transfer if bit 7 (transfer start) and bit 0 (internal clock) are set
            serialInternalClock = (value & 0x01) != 0;
            if ((value & 0x81) == 0x81) {
                // Notify callback with the byte being sent (before transfer completes)
                if (serialOutputCallback != null) {
                    serialOutputCallback.onSerialByte(serialData & 0xFF);
                }
                boolean cgbFast = (hardwareType != null && hardwareType.isCgb()) && (value & 0x02) != 0;
                serialClockBit = cgbFast ? 3 : 8;
                serialTransferActive = true;
                serialBitsRemaining = 8;
            } else if ((value & 0x80) != 0) {
                // External clock requested: mark transfer active but no internal ticking
                serialTransferActive = true;
                serialBitsRemaining = 8;
            }
            return;
        }

        // APU registers
        if (address >= 0xFF10 && address <= 0xFF3F) {
            apu.writeRegister(address, value & 0xFF);
            return;
        }

        switch (address) {
            case 0xFF04:
                timer.writeDIV();
                return;
            case 0xFF05:
                timer.writeTIMA(value & 0xFF);
                return;
            case 0xFF06:
                timer.writeTMA(value & 0xFF);
                return;
            case 0xFF07:
                timer.writeTAC(value & 0xFF);
                return;
            case 0xFF40:
                lcd.handleLcdcWrite(value & 0xFF);
                if ((value & 0x80) == 0) {
                    dma.onLcdDisabled();
                }
                return;
            case 0xFF50:
                if ((value & 0x01) != 0)
                    bootRomEnabled = false;
                return;
            case 0xFF41:
                lcd.writeStatFromWrite(value & 0xFF);
                return;
            case 0xFF42:
                lcd.setScy(value & 0xFF);
                return;
            case 0xFF43:
                lcd.setScx(value & 0xFF);
                return;
            case 0xFF44:
                lcd.writeLy();
                return;
            case 0xFF45:
                lcd.writeLyc(value & 0xFF);
                return;
            case 0xFF0F:
                interruptFlag = (byte) (value & 0x1F);
                return;
            case 0xFF46:
                dma.startDma(value & 0xFF);
                return;
            case 0xFF4D:
                if (cgbMode && cpu != null)
                    cpu.writeKey1(value & 0xFF);
                return;
            case 0xFF4F:
                if (cgbMode)
                    vramBank = value & 0x01;
                return;
            case 0xFF51:
                if (cgbMode)
                    dma.setHdmaSrcHigh(value & 0xFF);
                return;
            case 0xFF52:
                if (cgbMode)
                    dma.setHdmaSrcLow(value & 0xFF);
                return;
            case 0xFF53:
                if (cgbMode)
                    dma.setHdmaDstHigh(value & 0xFF);
                return;
            case 0xFF54:
                if (cgbMode)
                    dma.setHdmaDstLow(value & 0xFF);
                return;
            case 0xFF55:
                if (cgbMode)
                    dma.handleHdmaStart(value & 0xFF, (lcd.getLcdc() & 0x80) != 0);
                return;
            case 0xFF68:
                if (cgbMode)
                    lcd.writeBgpi(value & 0xFF);
                return;
            case 0xFF69:
                if (cgbMode)
                    lcd.writeBgpd(value & 0xFF);
                return;
            case 0xFF6A:
                if (cgbMode)
                    lcd.writeObpi(value & 0xFF);
                return;
            case 0xFF6B:
                if (cgbMode)
                    lcd.writeObpd(value & 0xFF);
                return;
            case 0xFF47:
                lcd.setBgp(value & 0xFF);
                return;
            case 0xFF48:
                lcd.setObp0(value & 0xFF);
                return;
            case 0xFF49:
                lcd.setObp1(value & 0xFF);
                return;
            case 0xFF4A:
                lcd.setWy(value & 0xFF);
                return;
            case 0xFF4B:
                lcd.setWx(value & 0xFF);
                return;
            case 0xFF70:
                if (cgbMode) {
                    wramBank = (value & 0x07);
                    if (wramBank == 0)
                        wramBank = 1;
                }
                return;
            default:
                return;
        }
    }

    // --- Internal read (for DMA) ---

    private int readByteInternal(int address) {
        int addr = address & 0xFFFF;
        if (cartridge != null && (isCartridgeRom(addr) || isCartridgeRam(addr))) {
            return cartridge.read(addr) & 0xFF;
        }
        if (isVideoRam(addr)) {
            if (lcd.isVramBlocked()) {
                return 0xFF;
            }
            return readVideoRam(addr) & 0xFF;
        }
        if (isWorkRam(addr)) {
            return readWorkRam(addr) & 0xFF;
        }
        if (isEchoRam(addr)) {
            int echoAddr = addr - 0x2000;
            if (isWorkRam(echoAddr)) {
                return readWorkRam(echoAddr) & 0xFF;
            }
        }
        if (addr >= 0xFE00 && addr < 0xFEA0) {
            if (lcd.isOamBlocked()) {
                return 0xFF;
            }
            return oam[addr - 0xFE00] & 0xFF;
        }
        if (isHighRam(addr)) {
            return highRam[addr - 0xFF80] & 0xFF;
        }
        if (addr >= 0xFEA0 && addr < 0xFF00) {
            if (lcd.isOamBlocked()) {
                return 0xFF;
            }
            if (hardwareType != null && !hardwareType.isCgb()) {
                return 0x00;
            }
            return 0xFF;
        }
        if (addr >= 0xFF00 && addr < 0xFF80) {
            // I/O registers - DMA reads these directly
            return readByteCore(addr);
        }
        return 0xFF;
    }

    private int readByteOamDma(int address) {
        int addr = address & 0xFFFF;
        if (addr < 0x8000) {
            return cartridge != null ? (cartridge.read(addr) & 0xFF) : 0xFF;
        }
        if (addr < 0xA000) {
            return readVideoRam(addr) & 0xFF;
        }
        if (addr < 0xC000) {
            return cartridge != null ? (cartridge.read(addr) & 0xFF) : 0xFF;
        }
        if (addr < 0xE000) {
            return readWorkRam(addr) & 0xFF;
        }
        if (addr < 0xFE00) {
            return readWorkRam(addr - 0x2000) & 0xFF;
        }
        return 0xFF;
    }

    // --- Video RAM ---

    private int readVideoRam(int address) {
        if (vramBank == 0) {
            return videoRam0[address - 0x8000] & 0xFF;
        } else {
            return videoRam1[address - 0x8000] & 0xFF;
        }
    }

    private void writeVideoRam(int address, byte value) {
        if (vramBank == 0) {
            videoRam0[address - 0x8000] = value;
        } else {
            videoRam1[address - 0x8000] = value;
        }
    }

    // --- Work RAM ---

    private int readWorkRam(int address) {
        if (address >= 0xC000 && address <= 0xCFFF) {
            return workRam[0][address - 0xC000] & 0xFF;
        } else {
            return workRam[wramBank][address - 0xD000] & 0xFF;
        }
    }

    private void writeWorkRam(int address, byte value) {
        if (address >= 0xC000 && address <= 0xCFFF) {
            workRam[0][address - 0xC000] = value;
        } else {
            workRam[wramBank][address - 0xD000] = value;
        }
    }

    // --- Address range helpers (kept for potential external use) ---

    private boolean isHighRam(int a) {
        return a >= 0xFF80 && a <= 0xFFFE;
    }

    private boolean isEchoRam(int a) {
        return a >= 0xE000 && a <= 0xFDFF;
    }

    private boolean isWorkRam(int a) {
        return a >= 0xC000 && a <= 0xDFFF;
    }

    private boolean isVideoRam(int a) {
        return a >= 0x8000 && a <= 0x9FFF;
    }

    private boolean isCartridgeRam(int a) {
        return a >= 0xA000 && a <= 0xBFFF;
    }

    private boolean isCartridgeRom(int a) {
        return a >= 0x0000 && a <= 0x7FFF;
    }

    // --- Stack operations ---

    public char popFromStack(char sp) {
        return (char) readChar((int) sp);
    }

    public char popFromStack(char sp, Registers registers) {
        char value = (char) readChar((int) sp);
        int newSp = sp + 2;
        registers.setSP((char) (newSp & 0xFFFF));
        return value;
    }

    public void pushToStack(char sp, char value, Registers registers) {
        int newSp = sp - 2;
        registers.setSP((char) (newSp & 0xFFFF));
        writeByte(newSp, value & 0xFF);
        writeByte(newSp + 1, (value >> 8) & 0xFF);
    }

    public void pushToStack(char sp, byte value, Registers registers) {
        int newSp = sp - 1;
        writeByte(newSp, value);
        registers.setSP((char) (newSp & 0xFFFF));
    }

    // --- Cartridge management ---

    public boolean isCartridgeLoaded() {
        return cartridge != null;
    }

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
        updateCgbMode();
        applyDmgCgbPalettesIfNeeded();
    }

    public Cartridge getCartridge() {
        return this.cartridge;
    }

    private void updateCgbMode() {
        cgbMode = hardwareType != null && hardwareType.isCgb()
                && cartridge != null && cartridge.isCgbCompatible();
        lcd.setStatWriteQuirkEnabled(hardwareType != null && !hardwareType.isCgb());
        lcd.setStatIrqDelayCycles(hardwareType != null && !hardwareType.isCgb() ? 4 : 0);
        if (apu != null) {
            apu.setCgbMode(cgbMode);
        }
        if (!cgbMode) {
            vramBank = 0;
            wramBank = 1;
        }
    }

    private void applyDmgCgbPalettesIfNeeded() {
        if (!isDmgOnCgb() || bootRomEnabled || cartridge == null)
            return;
        gbc.model.graphics.DmgCgbPaletteTable table = gbc.model.graphics.DmgCgbPaletteTable.getInstance();
        gbc.model.graphics.DmgCgbPaletteTable.DmgCgbPalette palette = table.selectFor(cartridge);
        lcd.setDmgCgbPalette(new int[][] { palette.bg().clone(), palette.obj0().clone(), palette.obj1().clone() });
        writeDmgCgbPaletteToRam();
    }

    private void writeDmgCgbPaletteToRam() {
        int[][] dmgPal = lcd.getDmgCgbPalette();
        byte[] cgbBg = lcd.getCgbBgPalette();
        byte[] cgbObj = lcd.getCgbObjPalette();
        for (int i = 0; i < 4; i++) {
            lcd.writeCgbPaletteEntry(cgbBg, 0, i, dmgPal[0][i]);
        }
        for (int i = 0; i < 4; i++) {
            lcd.writeCgbPaletteEntry(cgbObj, 0, i, dmgPal[1][i]);
            lcd.writeCgbPaletteEntry(cgbObj, 1, i, dmgPal[2][i]);
        }
    }

    // --- APU init ---

    private void initApuPostBoot() {
        if (apu == null)
            return;
        // Start from a clean audio state before applying post-boot register values.
        apu.writeRegister(0xFF26, 0x00);
        apu.writeRegister(0xFF26, 0x80);
        apu.writeRegister(0xFF24, 0x77);
        apu.writeRegister(0xFF25, 0xF3);
        apu.writeRegister(0xFF10, 0x80);
        apu.writeRegister(0xFF11, 0xBF);
        apu.writeRegister(0xFF12, 0xF3);
        apu.writeRegister(0xFF13, 0xFF);
        // SGB/SGB2 power-up keeps channel 1 disabled (NR52 low nibble starts at 0x0).
        apu.writeRegister(0xFF14, hardwareType != null && hardwareType.isSgb() ? 0x3F : 0xBF);
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
        if (apu == null)
            return;
        apu.writeRegister(0xFF26, 0x00);
        apu.writeRegister(0xFF24, 0x00);
        apu.writeRegister(0xFF25, 0x00);
        for (int addr = 0xFF10; addr <= 0xFF3F; addr++) {
            apu.writeRegister(addr, 0x00);
        }
    }

    // --- Reset ---

    public void reset() {
        // Reset memory arrays
        java.util.Arrays.fill(videoRam0, (byte) 0);
        java.util.Arrays.fill(videoRam1, (byte) 0);
        for (int i = 0; i < 8; i++) {
            java.util.Arrays.fill(workRam[i], (byte) 0);
        }
        java.util.Arrays.fill(highRam, (byte) 0);
        java.util.Arrays.fill(oam, (byte) 0);

        interruptEnable = 0x00;
        vramBank = 0;
        wramBank = 1;

        // Reset subcomponents
        timer.reset(hardwareType.getInitialDivCounter());
        lcd.reset(bootRomEnabled, hardwareType);
        dma.reset();

        if (hardwareType.isCgb() && bootRom == null) {
            lcd.initDefaultCgbPalettes();
        }

        serialData = 0x00;
        serialControl = 0x00;
        serialTransferActive = false;
        serialInternalClock = false;
        serialBitsRemaining = 0;
        serialClockBit = 8;
        joypadRegister = hardwareType.getInitialJoypadRegister() & 0xFF;
        if (controller != null) {
            controller.reset();
            controller.writeJoypadSelect(joypadRegister);
        }
        bootRomEnabled = bootRom != null;
        apuCycleCounter = 0;

        if (bootRomEnabled) {
            interruptFlag = 0x00;
            timer.setDivider(0);
            initApuPreBoot();
        } else {
            // Post-boot ROM: VBlank IF flag is set (boot ROM leaves it set)
            interruptFlag = 0x01;
            initApuPostBoot();
        }

        applyDmgCgbPalettesIfNeeded();

        // Wire HDMA callback for CGB HBlank DMA
        if (hardwareType.isCgb()) {
            lcd.setHdmaCallback(ly -> {
                if (dma.isHdmaActive() && dma.isHdmaHblank()) {
                    dma.stepHdmaBlock();
                }
            });
        }
    }

    private void stepSerial(int oldDivider, int newDivider) {
        if (!serialTransferActive || !serialInternalClock) {
            return;
        }
        boolean oldClock = ((oldDivider >> serialClockBit) & 0x01) != 0;
        boolean newClock = ((newDivider >> serialClockBit) & 0x01) != 0;
        if (oldClock && !newClock) {
            serialData = (byte) (((serialData << 1) | 0x01) & 0xFF);
            serialBitsRemaining--;
            if (serialBitsRemaining <= 0) {
                serialTransferActive = false;
                serialControl &= ~0x80; // Clear transfer start flag
                interruptFlag |= 0x08; // Request serial interrupt (bit 3)
            }
        }
    }

    // --- Utility ---

    public java.util.HashMap<Integer, Byte> getMemoryRange(int startAddress, int endAddress) {
        java.util.HashMap<Integer, Byte> range = new java.util.HashMap<>();
        for (int i = startAddress; i <= endAddress && i <= 0xFFFF; i++) {
            try {
                range.put(i, (byte) peekByte(i));
            } catch (IllegalArgumentException e) {
                // Skip invalid addresses
            }
        }
        return range;
    }

    @Override
    public String toString() {
        return String.format("CGB Memory Layout:\nVRAM Bank: %d, WRAM Bank: %d\n", vramBank, wramBank);
    }
}
