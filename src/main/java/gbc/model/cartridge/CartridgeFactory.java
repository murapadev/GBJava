package main.java.model.cartridge;

public class CartridgeFactory {

	public static Cartridge create(byte[] data) {
		
		if (data[0x147] == 0x00) {
			return new ROM(data);
		} else if (data[0x147] == 0x01 || data[0x147] == 0x02 || data[0x147] == 0x03) {
			return new MBC1(data);
		} else if (data[0x147] == 0x05 || data[0x147] == 0x06) {
			return new MBC2(data);
		} else if (data[0x147] == 0x0F || data[0x147] == 0x10 || data[0x147] == 0x11 || data[0x147] == 0x12
				|| data[0x147] == 0x13) {
			return new MBC3(data);
		} else if (data[0x147] == 0x19 || data[0x147] == 0x1A || data[0x147] == 0x1B || data[0x147] == 0x1C
				|| data[0x147] == 0x1D || data[0x147] == 0x1E) {
			return new MBC5(data);
		} else {
			return null;
		}
	}

}
