package gbc.model.cartridge;

public class MBC2 extends Cartridge {
    private int romBankNumber = 1; // Default ROM bank
    private boolean ramEnabled = false;

    public MBC2(byte[] data) {
        super(data);
    }

    @Override
    public byte read(int address) {
        if (address >= 0x0000 && address < 0x4000) {
            // ROM bank 0
            return data[address];
        } else if (address >= 0x4000 && address < 0x8000) {
            // Switchable ROM bank
            int bankOffset = romBankNumber * 0x4000;
            int index = bankOffset + (address - 0x4000);
            return data[index];
        } else if (address >= 0xA000 && address < 0xA200 && ramEnabled) {
            // MBC2 has 512x4 bits built-in RAM
            int index = address - 0xA000;
            return (byte) (data[index] & 0x0F); // Only lower 4 bits are used
        }
        return 0;
    }

    @Override
    public void write(int address, byte value) {
        if (address >= 0x0000 && address < 0x4000) {
            if ((address & 0x0100) == 0) {
                // RAM enable/disable
                ramEnabled = ((value & 0x0F) == 0x0A);
            } else {
                // ROM bank number
                romBankNumber = (value & 0x0F);
                if (romBankNumber == 0) romBankNumber = 1;
            }
        }
    }
}
