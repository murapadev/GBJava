package gbc.model.cartridge;

public class ROM extends Cartridge {

	public ROM(byte[] data) {
		super(data);
	}

	@Override
	public byte read(int address) {
		// Simple direct mapping for ROM-only cartridges
		if (address >= 0 && address < data.length) {
			return data[address];
		}
		return 0;
	}

	@Override
	public void write(int address, byte value) {
		// ROM is read-only; writes have no effect
	}
}
