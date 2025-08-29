package gbc.model.cartridge;

public class ROM extends Cartridge {
    private byte[] writeBuffer; // For testing purposes
    private boolean[] written; // Track which addresses have been written

	public ROM(byte[] data) {
		super(data);
        this.writeBuffer = new byte[data.length];
        this.written = new boolean[data.length];
	}

	@Override
	public byte read(int address) {
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
		// Store writes in buffer for testing purposes
		if (writeBuffer != null && address >= 0 && address < writeBuffer.length) {
			writeBuffer[address] = value;
			if (written != null && address < written.length) {
				written[address] = true;
			}
		}
	}
}
