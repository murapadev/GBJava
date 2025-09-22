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

    // Timer registers
    private int div = 0; // Divider register (0xFF04)
    private int divCounter = 0; // Counter for DIV increments

    // Memory banking
    private int vramBank; // VRAM bank select (0 or 1)
    private int wramBank; // WRAM bank select (1-7, 0 = bank 1)

    private Cartridge cartridge;
    private CPU cpu; // Reference to CPU for I/O register access
    private Apu apu; // Audio Processing Unit
    private Controller controller; // Joypad controller

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
        // Increment DIV every 256 cycles (16384 Hz)
        divCounter += cycles;
        while (divCounter >= 256) {
            divCounter -= 256;
            div = (div + 1) & 0xFF;
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
        byte byteValue = (byte) (value & 0xFF);
        int addr = address & 0xFFFF;

        // Handle I/O registers (0xFF00-0xFF7F)
        if (isIoRegisters(addr)) {
            writeIORegister(addr, byteValue);
            return;
        }

        // High RAM (0xFF80-0xFFFE)
        if (isHighRam(addr)) {
            highRam[addr - 0xFF80] = byteValue;
            return;
        }

        // Interrupt Enable Register (0xFFFF)
        if (isInterruptEnable(addr)) {
            interruptEnable = byteValue;
            return;
        }

        // I/O registers and other special areas (0xFEA0-0xFEFF) - unmapped, ignore
        // writes
        if (isUnusable(addr)) {
            return;
        }

        // OAM (Object Attribute Memory) (0xFE00-0xFE9F)
        if (isOam(addr)) {
            oam[addr - 0xFE00] = byteValue;
            return;
        }

        // Echo RAM (0xE000-0xFDFF) - mirrors WRAM
        if (isEchoRam(addr)) {
            writeByte(addr - 0x2000, byteValue);
            return;
        }

        // Work RAM (0xC000-0xDFFF)
        if (isWorkRam(addr)) {
            writeWorkRam(addr, byteValue);
            return;
        }

        // Video RAM (0x8000-0x9FFF)
        if (isVideoRam(addr)) {
            writeVideoRam(addr, byteValue);
            return;
        }

        // Cartridge RAM (0xA000-0xBFFF)
        if (isCartridgeRam(addr)) {
            cartridge.write(addr, byteValue);
            return;
        }

        // Cartridge ROM/RAM (0x0000-0x7FFF)
        if (isCartridgeRom(addr)) {
            cartridge.write(addr, byteValue);
            return;
        }

        // Should not reach here
        throw new IllegalArgumentException("Invalid address: " + String.format("0x%04X", addr));
    }

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
                    workRam1[address - 0xD000] = value;
                    break; // Default to bank 1
            }
        }
    }

    public void writeChar(int address, int value) {
        // Game Boy uses little-endian: low byte first, high byte second
        writeByte(address, value & 0xFF); // Low byte at address
        writeByte(address + 1, (value >> 8) & 0xFF); // High byte at address+1
    }

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
    }

    public boolean isCartridgeLoaded() {
        return this.cartridge != null;
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

        // Reset timer registers
        div = 0;
        divCounter = 0;

        // Initialize other registers as required
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

    // I/O Register handling for CGB features
    private int readIORegister(int address) {
        // FIXME: PPU I/O registers (LCDC=0xFF40, STAT=0xFF41, LY=0xFF44, etc.) are not
        // initialized
        // This causes renderFrame() to fail and show blank screen
        // Need to add proper default values for Game Boy hardware registers

        // Joypad register (0xFF00)
        if (address == 0xFF00) {
            if (controller != null) {
                return controller.getJoypadState();
            }
            return 0xCF; // Default: all buttons released
        }

        // PPU registers with Game Boy default values
        switch (address) {
            case 0xFF04: // DIV - Divider register
                return div;
            case 0xFF40: // LCDC - LCD Control
                return 0x91; // LCD enabled, BG enabled, window tilemap 0x9C00, BG tilemap 0x9800
            case 0xFF41: // STAT - LCD Status
                return 0x85; // LYC=LY interrupt enabled, mode 1 (VBlank)
            case 0xFF42: // SCY - Scroll Y
                return 0x00;
            case 0xFF43: // SCX - Scroll X
                return 0x00;
            case 0xFF44: // LY - LCD Y coordinate (current scanline)
                return 0x00;
            case 0xFF45: // LYC - LY compare
                return 0x00;
            case 0xFF46: // DMA - DMA transfer
                return 0x00;
            case 0xFF47: // BGP - Background palette
                return 0xFC; // Default Game Boy palette
            case 0xFF48: // OBP0 - Object palette 0
                return 0xFF;
            case 0xFF49: // OBP1 - Object palette 1
                return 0xFF;
            case 0xFF4A: // WY - Window Y position
                return 0x00;
            case 0xFF4B: // WX - Window X position
                return 0x00;
            // Add more PPU registers as needed...
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
            // Add other I/O registers here as needed
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
                div = 0;
                divCounter = 0;
                break;
            case 0xFF4D: // KEY1 - Speed control register
                if (cpu != null) {
                    cpu.writeKey1(value & 0xFF);
                }
                break;
            case 0xFF4F: // VBK - VRAM bank select
                vramBank = value & 0x01; // Only bit 0 is used
                break;
            case 0xFF70: // SVBK - WRAM bank select
                wramBank = (value & 0x07); // Bits 0-2
                if (wramBank == 0)
                    wramBank = 1; // Bank 0 maps to bank 1
                break;
            // Add other I/O registers here as needed
            default:
                // For unhandled I/O registers, ignore writes
                break;
        }
    }

    // Memory region detection helpers
    private boolean isCartridgeRom(int address) {
        return address >= 0x0000 && address <= 0x7FFF;
    }

    private boolean isVideoRam(int address) {
        return address >= 0x8000 && address <= 0x9FFF;
    }

    private boolean isCartridgeRam(int address) {
        return address >= 0xA000 && address <= 0xBFFF;
    }

    private boolean isWorkRam(int address) {
        return address >= 0xC000 && address <= 0xDFFF;
    }

    private boolean isEchoRam(int address) {
        return address >= 0xE000 && address <= 0xFDFF;
    }

    private boolean isOam(int address) {
        return address >= 0xFE00 && address <= 0xFE9F;
    }

    private boolean isUnusable(int address) {
        return (address >= 0xFEA0 && address <= 0xFEFF);
    }

    private boolean isIoRegisters(int address) {
        return address >= 0xFF00 && address <= 0xFF7F;
    }

    private boolean isHighRam(int address) {
        return address >= 0xFF80 && address <= 0xFFFE;
    }

    private boolean isInterruptEnable(int address) {
        return address == 0xFFFF;
    }
}
