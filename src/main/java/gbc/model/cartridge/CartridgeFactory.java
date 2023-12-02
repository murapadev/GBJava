package gbc.model.cartridge;

public class CartridgeFactory {

	public static Cartridge create(byte[] data) {
		byte value = data[0x147];
		System.out.println("CartridgeFactory: " + value);
        return switch (value) {
            case 0x00 -> new ROM(data);
            case 0x01, 0x02, 0x03 -> new MBC1(data);
            case 0x05, 0x06 -> new MBC2(data);
            case 0x0F, 0x10, 0x11, 0x12, 0x13 -> new MBC3(data);
            case 0x19, 0x1A, 0x1B, 0x1C, 0x1D -> new MBC5(data);
            default -> null;
        };
	}

}
