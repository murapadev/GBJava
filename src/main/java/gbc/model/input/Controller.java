package gbc.model.input;

import gbc.model.cpu.Interruptions;

public class Controller {
	// TODO: Emulate joypad select-line behavior/interrupt edges (P14/P15 combos,
	// ghosting).
	private Interruptions interruptions; // For interrupt handling

	// Game Boy button states
	private boolean up, down, left, right;
	private boolean a, b, start, select;

	// Joypad register select bits (P14 and P15)
	private boolean selectDirection; // P14 (bit 4) - 0 = select direction buttons
	private boolean selectAction; // P15 (bit 5) - 0 = select action buttons

	public Controller() {
		this.interruptions = null;
		reset();
	}

	public void setInterruptions(Interruptions interruptions) {
		this.interruptions = interruptions;
	}

	public void reset() {
		up = down = left = right = false;
		a = b = start = select = false;
		selectDirection = true; // Default: not selected
		selectAction = true; // Default: not selected
	}

	// Get the current joypad register value
	public int getJoypadState() {
		int value = 0xFF; // Default: bits 7-0 = 1 (all buttons released, no group selected)

		// Set select bits
		if (!selectDirection)
			value &= ~(1 << 4); // P14 = 0
		if (!selectAction)
			value &= ~(1 << 5); // P15 = 0

		// Set button bits (active low)
		if (!selectDirection) {
			// Direction buttons
			if (down)
				value &= ~(1 << 3); // Down
			if (up)
				value &= ~(1 << 2); // Up
			if (left)
				value &= ~(1 << 1); // Left
			if (right)
				value &= ~(1 << 0); // Right
		}

		if (!selectAction) {
			// Action buttons
			if (start)
				value &= ~(1 << 3); // Start
			if (select)
				value &= ~(1 << 2); // Select
			if (b)
				value &= ~(1 << 1); // B
			if (a)
				value &= ~(1 << 0); // A
		}

		return value;
	}

	// Handle writing to joypad register (setting select bits)
	public void writeJoypadSelect(int value) {
		int oldState = getJoypadState();
		selectDirection = (value & (1 << 4)) != 0; // P14
		selectAction = (value & (1 << 5)) != 0; // P15
		int newState = getJoypadState();
		requestJoypadInterruptIfNeeded(oldState, newState);
	}

	// Convenience methods for setting button states
	public void setUp(boolean pressed) {
		int oldState = getJoypadState();
		up = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setDown(boolean pressed) {
		int oldState = getJoypadState();
		down = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setLeft(boolean pressed) {
		int oldState = getJoypadState();
		left = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setRight(boolean pressed) {
		int oldState = getJoypadState();
		right = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setA(boolean pressed) {
		int oldState = getJoypadState();
		a = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setB(boolean pressed) {
		int oldState = getJoypadState();
		b = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setStart(boolean pressed) {
		int oldState = getJoypadState();
		start = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	public void setSelect(boolean pressed) {
		int oldState = getJoypadState();
		select = pressed;
		requestJoypadInterruptIfNeeded(oldState, getJoypadState());
	}

	private void requestJoypadInterruptIfNeeded(int oldState, int newState) {
		if (interruptions == null) {
			return;
		}
		int selectBits = newState & 0x30;
		if (selectBits == 0x30) {
			return; // No group selected
		}
		int oldButtons = oldState & 0x0F;
		int newButtons = newState & 0x0F;
		int falling = (~newButtons) & oldButtons & 0x0F; // 1->0 transitions
		if (falling != 0) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	// Getters for button states
	public boolean isUp() {
		return up;
	}

	public boolean isDown() {
		return down;
	}

	public boolean isLeft() {
		return left;
	}

	public boolean isRight() {
		return right;
	}

	public boolean isA() {
		return a;
	}

	public boolean isB() {
		return b;
	}

	public boolean isStart() {
		return start;
	}

	public boolean isSelect() {
		return select;
	}
}
