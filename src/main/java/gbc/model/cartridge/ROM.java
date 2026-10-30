package gbc.model.cartridge;

public class ROM extends Cartridge {
	// Keep optional ROM write emulation only for dedicated tests that explicitly
	// need it.
	private final boolean allowRomWrites;
	private byte[] writeBuffer; // For testing purposes
	private boolean[] written; // Track which addresses have been written

	public ROM(byte[] data) {
		this(data, false);
	}

	public ROM(byte[] data, boolean hasBattery) {
		super(data);
		this.hasBattery = hasBattery;
		this.allowRomWrites = Boolean.getBoolean("gbc.rom.allowWrites");
		if (allowRomWrites) {
			this.writeBuffer = new byte[data.length];
			this.written = new boolean[data.length];
		}
		// Allocate RAM if header indicates RAM
		int ramSize = headerRamSize(data);
		if (ramSize > 0) {
			this.ram = new byte[ramSize];
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
		// Optional write-buffer for dedicated tests.
		if (allowRomWrites && written != null && address >= 0 && address < written.length && written[address]) {
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
		// Real hardware ignores ROM writes (except mapper control on MBC carts).
		// For plain ROM carts, writes are ignored unless a test enables write
		// buffering.
		if (allowRomWrites && writeBuffer != null && address >= 0 && address < writeBuffer.length) {
			writeBuffer[address] = value;
			if (written != null && address < written.length) {
				written[address] = true;
			}
		}
	}
}
