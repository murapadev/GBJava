package gbc.model.cartridge;

public class MBC5 extends Cartridge {
	private static final int ROM_BANK_SIZE = 0x4000; // Size of a ROM bank
	private static final int RAM_BANK_SIZE = 0x2000; // Size of a RAM bank

	private int romBankNumber;
	private int ramBankNumber;
	private boolean ramEnabled;

	public MBC5(byte[] data) {
		super(data);
		this.romBankNumber = 1; // Default to bank 1, as bank 0 is always mapped to 0x0000-0x3FFF
		this.ramBankNumber = 0;
		this.ramEnabled = false;
	}

	@Override
	public byte read(int address) {
		if (address >= 0x0000 && address < 0x4000) {
			// Always read from ROM bank 0
			return data[address];
		} else if (address >= 0x4000 && address < 0x8000) {
			// Read from the selected ROM bank
			int bankOffset = romBankNumber * ROM_BANK_SIZE;
			int index = bankOffset + (address - 0x4000);
			return data[index];
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			// Read from the selected RAM bank, if enabled
			int bankOffset = ramBankNumber * RAM_BANK_SIZE;
			int index = bankOffset + (address - 0xA000);
			return data[index];
		}
		return 0; // Return 0 for invalid or disabled addresses
	}

	@Override
	public void write(int address, byte value) {
		if (address >= 0x0000 && address < 0x2000) {
			// Enable or disable RAM
			ramEnabled = ((value & 0x0A) != 0);
		} else if (address >= 0x2000 && address < 0x3000) {
			// Write lower 8 bits of ROM bank number
			romBankNumber = (romBankNumber & 0x100) | (value & 0xFF);
		} else if (address >= 0x3000 && address < 0x4000) {
			// Write 9th bit of ROM bank number
			romBankNumber = (romBankNumber & 0xFF) | ((value & 0x01) << 8);
		} else if (address >= 0x4000 && address < 0x6000) {
			// Select RAM bank
			ramBankNumber = value & 0x0F;
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			// Write to the selected RAM bank, if enabled
			int bankOffset = ramBankNumber * RAM_BANK_SIZE;
			int index = bankOffset + (address - 0xA000);
			data[index] = value;
		}
	}
}
