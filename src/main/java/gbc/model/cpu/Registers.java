package gbc.model.cpu;

public class Registers {
	private byte A, B, C, D, E, F, H, L;
	private char PC, SP;

	public Registers() {
		A = B = C = D = E = F = H = L = 0;
		PC = SP = 0;
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
			case "AF" -> (byte) ((A << 8) | (F & 0xFF));
			case "BC" -> (byte) ((B << 8) | (C & 0xFF));
			case "DE" -> (byte) ((D << 8) | (E & 0xFF));
			case "HL" -> (byte) ((H << 8) | (L & 0xFF));

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
			case "AF" -> setAF((char) ((value << 8) | (F & 0xFF)));
			case "BC" -> setBC((char) ((B << 8) | (value & 0xFF)));
			case "DE" -> setDE((char) ((D << 8) | (value & 0xFF)));
			case "HL" -> setHL((char) ((H << 8) | (value & 0xFF)));
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
		A = B = C = D = E = F = H = L = 0;
		PC = SP = 0;
	}

	public void incrementPC() {
		PC++;
	}

	@Override
	public String toString() {
		return "A: " + A + "\n" + "B: " + B + "\n" + "C: " + C + "\n" + "D: " + D + "\n" + "E: " + E + "\n" + "F: " + F
				+ "\n" + "H: " + H + "\n" + "L: " + L + "\n" + "PC: " + PC + "\n" + "SP: " + SP + "\n";
	}
}