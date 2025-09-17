package gbc.model.cpu;

import gbc.model.memory.Memory;
import java.util.HashMap;
import java.util.Map;

public class Instructions {

	private Registers r;
	private Memory m;


	Instructions(Registers r, Memory m) {
		this.r = r;
		this.m = m;
	}

	public void execute(byte opcode) {

	}





	// ... (other instruction methods)

	public void setRegisters(Registers r) {
		this.r = r;
	}

	public void setMemory(Memory m) {
		this.m = m;
	}
}
