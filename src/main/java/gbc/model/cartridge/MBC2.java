package main.java.model.cartridge;

public class MBC2 extends Cartridge {
    private byte[] ram;
    private boolean ramEnabled;
    private int romBank;

    public MBC2(byte[] data) {
        super(data);
		System.out.println("MBC2");
        ram = new byte[512];
        ramEnabled = false;
        romBank = 1;
    }

    @Override
    public byte read(int address) {
        if (address < 0x4000) {
            return data[address];
        } else if (address < 0x8000) {
            int offset = (romBank - 1) * 0x4000;
            return data[offset + address - 0x4000];
        } else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
            return ram[address - 0xA000];
        } else {
            return 0;
        }
    }

    @Override
    public void write(int address, byte value) {
        if (address < 0x2000) {
            ramEnabled = (value & 0x0F) == 0x0A;
        } else if (address >= 0x2000 && address < 0x4000) {
            romBank = value & 0x0F;
            if (romBank == 0) {
                romBank = 1;
            }
        } else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
            ram[address - 0xA000] = value;
        }
    }
}