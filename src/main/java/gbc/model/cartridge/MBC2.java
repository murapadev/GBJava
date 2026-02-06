package gbc.model.cartridge;

public class MBC2 extends Cartridge {
    private static final int ROM_BANK_SIZE = 0x4000;

    private int romBankNumber = 1;
    private boolean ramEnabled = false;
    private final int romBankCount;

    public MBC2(byte[] data, boolean hasBattery) {
        super(data);
        this.hasBattery = hasBattery;
        // MBC2 has 512x4 bits built-in RAM
        this.ram = new byte[512];
        this.romBankCount = Math.max(1, data.length / ROM_BANK_SIZE);
    }

    @Override
    public byte read(int address) {
        if (address >= 0x0000 && address < 0x4000) {
            if (address < data.length) {
                return data[address];
            }
            return (byte) 0xFF;
        } else if (address >= 0x4000 && address < 0x8000) {
            int bank = romBankNumber % romBankCount;
            int index = bank * ROM_BANK_SIZE + (address - 0x4000);
            if (index < data.length) {
                return data[index];
            }
            return (byte) 0xFF;
        } else if (address >= 0xA000 && address < 0xC000) {
            if (!ramEnabled) {
                return (byte) 0xFF;
            }
            // MBC2 RAM: 512 bytes, mirrored across 0xA000-0xBFFF
            // Only lower 4 bits are valid
            int index = (address - 0xA000) & 0x01FF;
            return (byte) (ram[index] | 0xF0); // Upper 4 bits read as 1
        }
        return (byte) 0xFF;
    }

    @Override
    public void write(int address, byte value) {
        if (address >= 0x0000 && address < 0x4000) {
            if ((address & 0x0100) == 0) {
                // RAM enable/disable (bit 8 of address must be 0)
                ramEnabled = ((value & 0x0F) == 0x0A);
            } else {
                // ROM bank number (bit 8 of address must be 1)
                romBankNumber = (value & 0x0F);
                if (romBankNumber == 0)
                    romBankNumber = 1;
            }
        } else if (address >= 0xA000 && address < 0xC000) {
            if (!ramEnabled)
                return;
            int index = (address - 0xA000) & 0x01FF;
            ram[index] = (byte) (value & 0x0F);
        }
    }
}
