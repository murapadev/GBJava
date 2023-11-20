
package main.java.model.cpu;

import main.java.model.memory.Memory;

public class Instructions {

    private Registers r;
    private Memory m;

    Instructions(Registers r, Memory m) {
        // Constructor
        this.r = r;
        this.m = m;

    }

    public void setRegisters(Registers r) {
        this.r = r;
    }

    public void setMemory(Memory m) {
        this.m = m;
    }

    public void execute(byte opcode) {
        //TODO: Implementar la lógica para ejecutar las instrucciones
    }

	public void nop() {
		// NOP
		r.setPC((short) (r.getPC() + 1));
	}

	public void ld(byte opcode) {
		// LD r, n
		byte n = m.readByte((short) (r.getPC() + 1));
		switch (opcode) {
			case (byte) 0x06:
				r.setB(n);
				break;
			case (byte) 0x0E:
				r.setC(n);
				break;
			case (byte) 0x16:
				r.setD(n);
				break;
			case (byte) 0x1E:
				r.setE(n);
				break;
			case (byte) 0x26:
				r.setH(n);
				break;
			case (byte) 0x2E:
				r.setL(n);
				break;
			case (byte) 0x3E:
				r.setA(n);
				break;
		}
		r.setPC((short) (r.getPC() + 2));
	}

	
}