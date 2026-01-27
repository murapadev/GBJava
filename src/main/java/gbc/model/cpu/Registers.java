package gbc.model.cpu;

public class Registers {
	private byte A, B, C, D, E, F, H, L;
	private int PC, SP;

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
			case "F" -> F = (byte) (value & 0xF0); // F register: lower 4 bits always 0
			case "H" -> H = value;
			case "L" -> L = value;
			default -> throw new IllegalArgumentException("Invalid register: " + register);
		}
	}

	public int getPC() {
		return PC;
	}

	public void setPC(int value) {
		PC = value;
	}

	public int getSP() {
		return SP;
	}

	public void setSP(int value) {
		SP = value;
	}

	public int getAF() {
		return ((A & 0xFF) << 8) | (F & 0xFF);
	}

	public void setAF(int value) {
		A = (byte) ((value >> 8) & 0xFF);
		F = (byte) (value & 0xF0); // F register: lower 4 bits always 0
	}

	public int getBC() {
		return ((B & 0xFF) << 8) | (C & 0xFF);
	}

	public void setBC(int value) {
		B = (byte) ((value >> 8) & 0xFF);
		C = (byte) (value & 0xFF);
	}

	public int getDE() {
		return ((D & 0xFF) << 8) | (E & 0xFF);
	}

	public void setDE(int value) {
		D = (byte) ((value >> 8) & 0xFF);
		E = (byte) (value & 0xFF);
	}

	public int getHL() {
		return ((H & 0xFF) << 8) | (L & 0xFF);
	}

	public void setHL(int value) {
		H = (byte) ((value >> 8) & 0xFF);
		L = (byte) (value & 0xFF);
	}

	public void reset() {
		// Default to DMG reset
		reset(gbc.model.HardwareType.DMG);
	}
	
	/**
	 * Reset registers to post-boot ROM state for the specified hardware type.
	 * Different hardware variants have different initial register values.
	 * Values from https://gbdev.io/pandocs/Power_Up_Sequence.html
	 */
	public void reset(gbc.model.HardwareType hardwareType) {
		// Common values for all hardware
		D = 0x00;
		SP = 0xfffe;
		PC = 0x0100;
		
		switch (hardwareType) {
			case DMG -> {
				// DMG-ABC (most common)
				A = 0x01;
				F = (byte) 0xB0; // Z=1, N=0, H=1, C=1
				B = 0x00;
				C = 0x13;
				E = (byte) 0xD8;
				H = 0x01;
				L = 0x4D;
			}
			case DMG0 -> {
				// DMG-0 (early revision)
				A = 0x01;
				F = (byte) 0x00; // Z=0, N=0, H=0, C=0 
				B = (byte) 0xFF;
				C = 0x13;
				E = (byte) 0xC1;
				H = (byte) 0x84;
				L = 0x03;
			}
			case MGB -> {
				// Game Boy Pocket - same as DMG except A
				A = (byte) 0xFF;
				F = (byte) 0xB0; // Z=1, N=0, H=1, C=1
				B = 0x00;
				C = 0x13;
				E = (byte) 0xD8;
				H = 0x01;
				L = 0x4D;
			}
			case SGB -> {
				// Super Game Boy
				A = 0x01;
				F = (byte) 0x00; // Z=0, N=0, H=0, C=0
				B = 0x00;
				C = 0x14;
				E = 0x00;
				H = (byte) 0xC0;
				L = 0x60;
			}
			case SGB2 -> {
				// Super Game Boy 2
				A = (byte) 0xFF;
				F = (byte) 0x00; // Z=0, N=0, H=0, C=0
				B = 0x00;
				C = 0x14;
				E = 0x00;
				H = (byte) 0xC0;
				L = 0x60;
			}
			case CGB -> {
				// Game Boy Color
				A = 0x11;
				F = (byte) 0x80; // Z=1, N=0, H=0, C=0
				B = 0x00;
				C = 0x00;
				E = 0x08;
				H = 0x00;
				L = 0x7C;
			}
		}
	}

	public void incrementPC() {
		PC++;
	}

	// Flag convenience methods for cleaner code
	public boolean isZ() {
		return (F & 0x80) != 0;
	}

	public boolean isN() {
		return (F & 0x40) != 0;
	}

	public boolean isH() {
		return (F & 0x20) != 0;
	}

	public boolean isC() {
		return (F & 0x10) != 0;
	}

	public void setZ(boolean value) {
		if (value) {
			F |= 0x80;
		} else {
			F &= 0x7F;
		}
	}

	public void setN(boolean value) {
		if (value) {
			F |= 0x40;
		} else {
			F &= 0xBF;
		}
	}

	public void setH(boolean value) {
		if (value) {
			F |= 0x20;
		} else {
			F &= 0xDF;
		}
	}

	public void setC(boolean value) {
		if (value) {
			F |= 0x10;
		} else {
			F &= 0xEF;
		}
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
				"PC: " + PC + "\n" +
				"SP: " + SP + "\n";
	}

	public Registers copy() {
		Registers copy = new Registers();
		copy.A = this.A;
		copy.B = this.B;
		copy.C = this.C;
		copy.D = this.D;
		copy.E = this.E;
		copy.F = this.F;
		copy.H = this.H;
		copy.L = this.L;
		copy.PC = this.PC;
		copy.SP = this.SP;
		return copy;
	}
}
