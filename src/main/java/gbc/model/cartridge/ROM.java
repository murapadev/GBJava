package main.java.model.cartridge;

public class ROM extends Cartridge {

	public ROM(byte[] data) {
		super(data);
		System.out.println("ROM");
	}

	@Override
	public byte read(int address) {
		System.out.println("ROM read " + address);
		if(address < 0x8000) {
			System.out.println("ROM read " + address + " " + data[address]);
			return data[address];
		} else {
			return 0;
		}

	}

	@Override
	public void write(int address, byte value) {
		// TODO Auto-generated method stub
	}

}
