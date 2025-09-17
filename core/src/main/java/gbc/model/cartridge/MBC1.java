package gbc.model.cartridge;

public class MBC1 extends Cartridge {
    private int romBankNumber = 1; // Default ROM bank
    private int ramBankNumber = 0; // Default RAM bank
    private boolean ramEnabled = false;
    private boolean bankingMode = false; // false: ROM banking mode, true: RAM banking mode

    public MBC1(byte[] data) {
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
        } else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
            // Switchable RAM bank
            int bankOffset = ramBankNumber * 0x2000;
            int index = bankOffset + (address - 0xA000);
            return data[index];
        }
        return 0;
    }

    @Override
    public void write(int address, byte value) {
        if (address >= 0x0000 && address < 0x2000) {
            // RAM enable/disable
            ramEnabled = ((value & 0x0F) == 0x0A);
        } else if (address >= 0x2000 && address < 0x4000) {
            // ROM bank number
            romBankNumber = (value & 0x1F);
            if (romBankNumber == 0) romBankNumber = 1;
        } else if (address >= 0x4000 && address < 0x6000) {
            // RAM bank number or upper bits of ROM bank number
            if (bankingMode) {
                ramBankNumber = (value & 0x03);
            } else {
                romBankNumber = (romBankNumber & 0x1F) | ((value & 0x03) << 5);
            }
        } else if (address >= 0x6000 && address < 0x8000) {
            // Banking mode select
            bankingMode = ((value & 0x01) == 0x01);
        }
    }
}
