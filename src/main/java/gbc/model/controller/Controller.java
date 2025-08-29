package gbc.model.controller;

import gbc.model.memory.Memory;

public class Controller {
	private Memory memory;
	
	// Game Boy button states
	private boolean up, down, left, right;
	private boolean a, b, start, select;
	
	// Joypad register bits (0xFF00)
	private static final int P14 = 4; // P14 pin (bit 4) - Direction keys
	private static final int P15 = 5; // P15 pin (bit 5) - Action buttons

	public Controller() {
		this.memory = null; // Will be set later
		reset();
	}
	
	public Controller(Memory memory) {
		this.memory = memory;
		reset();
	}

	public void setMemory(Memory memory) {
		this.memory = memory;
	}

	public void reset() {
		up = down = left = right = false;
		a = b = start = select = false;
	}

	public void handleInput() {
		if (memory == null) return;
		
		// Read the joypad register to see which button group is being requested
		int joypad = memory.readByte(0xFF00);
		
		// Update the joypad register based on button states
		int newJoypad = 0xCF; // Default state (all buttons released)
		
		// Check if direction keys are being read (P14 = 0)
		if ((joypad & (1 << P14)) == 0) {
			if (!down) newJoypad |= (1 << 3); // Down
			if (!up) newJoypad |= (1 << 2);   // Up
			if (!left) newJoypad |= (1 << 1); // Left
			if (!right) newJoypad |= (1 << 0); // Right
		}
		
		// Check if action buttons are being read (P15 = 0)
		if ((joypad & (1 << P15)) == 0) {
			if (!start) newJoypad |= (1 << 3); // Start
			if (!select) newJoypad |= (1 << 2); // Select
			if (!b) newJoypad |= (1 << 1);     // B
			if (!a) newJoypad |= (1 << 0);     // A
		}
		
		// Keep the P14 and P15 bits as they were
		newJoypad |= (joypad & 0x30);
		
		memory.writeByte(0xFF00, newJoypad & 0xFF);
	}

	// Convenience methods for setting button states
	public void setUp(boolean pressed) { this.up = pressed; }
	public void setDown(boolean pressed) { this.down = pressed; }
	public void setLeft(boolean pressed) { this.left = pressed; }
	public void setRight(boolean pressed) { this.right = pressed; }
	public void setA(boolean pressed) { this.a = pressed; }
	public void setB(boolean pressed) { this.b = pressed; }
	public void setStart(boolean pressed) { this.start = pressed; }
	public void setSelect(boolean pressed) { this.select = pressed; }

	// Getters for button states
	public boolean isUp() { return up; }
	public boolean isDown() { return down; }
	public boolean isLeft() { return left; }
	public boolean isRight() { return right; }
	public boolean isA() { return a; }
	public boolean isB() { return b; }
	public boolean isStart() { return start; }
	public boolean isSelect() { return select; }
}
