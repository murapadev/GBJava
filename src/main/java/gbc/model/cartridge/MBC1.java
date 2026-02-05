package gbc.model.cartridge;

import java.util.logging.Logger;

public class MBC1 extends Cartridge {
    // TODO: Verify bank-0 remap behavior (0x20/0x40/0x60) and RAM size mapping nuances.
    private static final Logger LOGGER = Logger.getLogger(MBC1.class.getName());
    private static final int ROM_BANK_SIZE = 0x4000;
    private static final int RAM_BANK_SIZE = 0x2000;

    private int romBankLow = 1;   // 5-bit ROM bank register (0x2000-0x3FFF)
    private int bankUpper = 0;    // 2-bit upper bank register (0x4000-0x5FFF)
    private boolean ramEnabled = false;
    private boolean bankingMode = false; // false: mode 0 (ROM), true: mode 1 (RAM/advanced)

    private final int romBankCount;
    private final int ramBankCount;
    private final boolean multicart; // MBC1M: upper bits shift by 4 instead of 5

    public MBC1(byte[] data, boolean hasBattery) {
        super(data);
        this.hasBattery = hasBattery;
        this.romBankCount = Math.max(1, data.length / ROM_BANK_SIZE);
        this.multicart = detectMulticart(data);
        if (multicart) {
            LOGGER.info("MBC1M multicart detected");
        }
        int ramSize = getRamSize(data[0x0149]);
        if (ramSize > 0) {
            this.ram = new byte[ramSize];
        }
        this.ramBankCount = ram == null ? 0 : Math.max(1, ram.length / RAM_BANK_SIZE);
    }

    private static boolean detectMulticart(byte[] data) {
        // MBC1M multicart: 1MB ROM with valid Nintendo logos at 256KB boundaries
        if (data.length < 0x100000) return false; // Must be at least 1MB
        // Check for Nintendo logo at each 256KB (0x40000) boundary
        byte[] logo = new byte[0x30];
        System.arraycopy(data, 0x0104, logo, 0, Math.min(0x30, data.length - 0x0104));
        int matches = 0;
        for (int slot = 1; slot <= 3; slot++) {
            int base = slot * 0x40000;
            if (base + 0x134 > data.length) break;
            boolean match = true;
            for (int i = 0; i < 0x30 && match; i++) {
                if (data[base + 0x0104 + i] != logo[i]) match = false;
            }
            if (match) matches++;
        }
        return matches >= 1;
    }

    private int getRamSize(byte ramSizeType) {
        return switch (ramSizeType) {
            case 0x01 -> 2 * 1024;   // 2KB
            case 0x02 -> 8 * 1024;   // 8KB
            case 0x03 -> 32 * 1024;  // 32KB
            default -> 0;
        };
    }

    private int upperShift() {
        return multicart ? 4 : 5;
    }

    private int lowerMask() {
        return multicart ? 0x0F : 0x1F;
    }

    @Override
    public byte read(int address) {
        if (address >= 0x0000 && address < 0x4000) {
            // ROM bank 0 area
            // In mode 1: upper bank bits affect which bank appears in 0x0000-0x3FFF
            int bank = bankingMode ? (bankUpper << upperShift()) : 0;
            int index = (bank * ROM_BANK_SIZE + address) % data.length;
            return data[index];
        } else if (address >= 0x4000 && address < 0x8000) {
            // Switchable ROM bank area
            // lowerMask selects which bits of the 5-bit register connect to ROM lines
            // (4 bits for MBC1M wiring, 5 bits for standard MBC1)
            int low = romBankLow & lowerMask();
            int bank = low | (bankUpper << upperShift());
            // Zero fix: the MBC1 chip checks the full 5-bit register value,
            // not just the bits used for addressing. If the register is 0,
            // it maps to bank 1 instead of bank 0.
            if (romBankLow == 0) {
                bank = (bankUpper << upperShift()) | 1;
            }
            int index = (bank * ROM_BANK_SIZE + (address - 0x4000)) % data.length;
            return data[index];
        } else if (address >= 0xA000 && address < 0xC000) {
            if (!ramEnabled || ram == null || ramBankCount == 0) {
                return (byte) 0xFF;
            }
            // In mode 1: upper bank bits select RAM bank
            int bank = bankingMode ? (bankUpper % ramBankCount) : 0;
            int index = bank * RAM_BANK_SIZE + (address - 0xA000);
            if (index >= ram.length) return (byte) 0xFF;
            return ram[index];
        }
        return (byte) 0xFF;
    }

    @Override
    public void write(int address, byte value) {
        if (address >= 0x0000 && address < 0x2000) {
            // RAM enable/disable
            ramEnabled = ((value & 0x0F) == 0x0A);
        } else if (address >= 0x2000 && address < 0x4000) {
            // ROM bank number (lower 5 bits)
            // The MBC1 chip always stores 5 bits regardless of wiring (MBC1M)
            romBankLow = (value & 0x1F);
        } else if (address >= 0x4000 && address < 0x6000) {
            // Upper bank register (2 bits) — always written regardless of mode
            bankUpper = (value & 0x03);
        } else if (address >= 0x6000 && address < 0x8000) {
            // Banking mode select
            bankingMode = ((value & 0x01) == 0x01);
        } else if (address >= 0xA000 && address < 0xC000) {
            if (!ramEnabled || ram == null || ramBankCount == 0) return;
            int bank = bankingMode ? (bankUpper % ramBankCount) : 0;
            int index = bank * RAM_BANK_SIZE + (address - 0xA000);
            if (index >= ram.length) return;
            ram[index] = value;
        }
    }
}
