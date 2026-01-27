package gbc.model.cartridge;

public class ROM extends Cartridge {
	private byte[] writeBuffer; // For testing purposes
	private boolean[] written; // Track which addresses have been written

	public ROM(byte[] data) {
		super(data);
		this.writeBuffer = new byte[data.length];
		this.written = new boolean[data.length];
		// Allocate RAM if header indicates RAM
		int ramSize = getRamSize(data[0x0149]);
		if (ramSize > 0) {
			this.ram = new byte[ramSize];
		}
	}

	private int getRamSize(byte ramSizeType) {
		switch (ramSizeType) {
			case 0x01:
				return 2 * 1024; // 2KB
			case 0x02:
				return 8 * 1024; // 8KB
			case 0x03:
				return 32 * 1024; // 32KB
			default:
				return 0;
		}
	}

	@Override
	public byte read(int address) {
		if (address >= 0xA000 && address < 0xC000) {
			// Cartridge RAM
			if (ram != null) {
				return ram[address - 0xA000];
			}
			return 0;
		}
		// Check if we have written data for this address
		if (written != null && address >= 0 && address < written.length && written[address]) {
			return writeBuffer[address];
		}
		// Otherwise read from original ROM data
		if (address >= 0 && address < data.length) {
			return data[address];
		}
		return 0;
	}

	@Override
	public void write(int address, byte value) {
		if (address >= 0xA000 && address < 0xC000) {
			// Cartridge RAM
			if (ram != null) {
				ram[address - 0xA000] = value;
			}
			return;
		}
		// Store writes in buffer for testing purposes
		if (writeBuffer != null && address >= 0 && address < writeBuffer.length) {
			writeBuffer[address] = value;
			if (written != null && address < written.length) {
				written[address] = true;
			}
		}
	}
}
