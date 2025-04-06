package gbc.model.cpu;

public class Registers {
	private byte A, B, C, D, E, F, H, L;
	private char PC, SP;

	public Registers() {
		reset();
	}

	public byte getRegister(String register) {
		return switch (register) {
			case "A" -> A;
			case "B" -> B;
			case "C" -> C;
			case "D" -> D;
			case "E" -> E;
			case "F" -> F;
			case "H" -> H;
			case "L" -> L;
			default -> throw new IllegalArgumentException("Invalid register: " + register);
		};
	}

	public void setRegister(String register, byte value) {
		switch (register) {
			case "A" -> A = value;
			case "B" -> B = value;
			case "C" -> C = value;
			case "D" -> D = value;
			case "E" -> E = value;
			case "F" -> F = value;
			case "H" -> H = value;
			case "L" -> L = value;
			default -> throw new IllegalArgumentException("Invalid register: " + register);
		}
	}

	public char getPC() {
		return PC;
	}

	public void setPC(char value) {
		PC = value;
	}

	public char getSP() {
		return SP;
	}

	public void setSP(char value) {
		SP = value;
	}

	public char getAF() {
		return (char) ((A << 8) | (F & 0xFF));
	}

	public void setAF(char value) {
		A = (byte) ((value >> 8) & 0xFF);
		F = (byte) (value & 0xFF);
	}

	public char getBC() {
		return (char) ((B << 8) | (C & 0xFF));
	}

	public void setBC(char value) {
		B = (byte) ((value >> 8) & 0xFF);
		C = (byte) (value & 0xFF);
	}

	public char getDE() {
		return (char) ((D << 8) | (E & 0xFF));
	}

	public void setDE(char value) {
		D = (byte) ((value >> 8) & 0xFF);
		E = (byte) (value & 0xFF);
	}

	public char getHL() {
		return (char) ((H << 8) | (L & 0xFF));
	}

	public void setHL(char value) {
		H = (byte) ((value >> 8) & 0xFF);
		L = (byte) (value & 0xFF);
	}

	public void reset() {
		A = 0x01;
		B = 0x00;
		C = 0x13;
		D = 0x00;
		E = (byte) 0xD8;
		F = (byte) 0xB0;
		H = 0x01;
		L = 0x4d;
		SP = 0xfffe;
		PC = 0x0100;
	}

	public void incrementPC() {
		PC++;
	}

	@Override
	public String toString() {
		return "A: 0x" + String.format("%02X", A) + "\n" +
				"B: 0x" + String.format("%02X", B) + "\n" +
				"C: 0x" + String.format("%02X", C) + "\n" +
				"D: 0x" + String.format("%02X", D) + "\n" +
				"E: 0x" + String.format("%02X", E) + "\n" +
				"F: 0x" + String.format("%02X", F) + "\n" +
				"H: 0x" + String.format("%02X", H) + "\n" +
				"L: 0x" + String.format("%02X", L) + "\n" +
				"PC: 0x" + String.format("%04X", (int) PC) + "\n" +
				"SP: 0x" + String.format("%04X", (int) SP) + "\n";
	}
}
