package main.java.model.cartridge;

public class MBC3 extends Cartridge {
	private int romBankNumber;
	private int ramBankNumber;
	private boolean ramEnabled;
	private boolean romBankingMode;

	public MBC3(byte[] data) {
		super(data);
		System.out.println("MBC3");
		romBankNumber = 1;
		ramBankNumber = 0;
		ramEnabled = false;
		romBankingMode = true;
	}

	@Override
	public byte read(int address) {
		if (address < 0x4000) {
			// ROM bank 0
			return data[address];
		} else if (address < 0x8000) {
			// Switchable ROM bank
			int bankOffset = (romBankingMode ? (romBankNumber << 14) : 0);
			int addressOffset = address - 0x4000;
			int index = bankOffset + addressOffset;
			return data[index];
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			// Switchable RAM bank
			int bankOffset = ramBankNumber << 13;
			int addressOffset = address - 0xA000;
			int index = bankOffset + addressOffset;
			return data[index];
		} else {
			// Invalid address
			return 0;
		}
	}

	@Override
	public void write(int address, byte value) {
		if (address < 0x2000) {
			// RAM enable/disable
			ramEnabled = ((value & 0x0F) == 0x0A);
		} else if (address < 0x4000) {
			// ROM bank number
			romBankNumber = (romBankNumber & 0x60) | (value & 0x1F);
		} else if (address < 0x6000) {
			// RAM bank number or ROM bank number
			if (romBankingMode) {
				romBankNumber = (romBankNumber & 0x1F) | ((value & 0x03) << 5);
			} else {
				ramBankNumber = value & 0x03;
			}
		} else if (address < 0x8000) {
			// ROM/RAM mode select
			romBankingMode = ((value & 0x01) == 0x00);
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			// Switchable RAM bank
			int bankOffset = ramBankNumber << 13;
			int addressOffset = address - 0xA000;
			int index = bankOffset + addressOffset;
			data[index] = value;
		}
	}
	
}
