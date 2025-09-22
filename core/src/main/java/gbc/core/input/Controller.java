package gbc.core.input;

import gbc.model.cpu.Interruptions;

public class Controller {
	private Interruptions interruptions; // For interrupt handling

	// Game Boy button states
	private boolean up, down, left, right;
	private boolean a, b, start, select;

	// Joypad register select bits (P14 and P15)
	private boolean selectDirection; // P14 (bit 4) - 0 = select direction buttons
	private boolean selectAction; // P15 (bit 5) - 0 = select action buttons

	// Previous button states for interrupt detection
	private boolean prevUp, prevDown, prevLeft, prevRight;
	private boolean prevA, prevB, prevStart, prevSelect;

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
		prevUp = prevDown = prevLeft = prevRight = false;
		prevA = prevB = prevStart = prevSelect = false;
		selectDirection = true; // Default: not selected
		selectAction = true; // Default: not selected
	}

	// Get the current joypad register value
	public int getJoypadState() {
		int value = 0xCF; // Default: bits 7-6=1, 5-4=1, 3-0=1 (all buttons released)

		// Set select bits
		if (!selectDirection)
			value &= ~(1 << 4); // P14 = 0
		if (!selectAction)
			value &= ~(1 << 5); // P15 = 0

		// Set button bits (active low)
		if (selectDirection) {
			// Direction buttons
			if (!down)
				value &= ~(1 << 3); // Down
			if (!up)
				value &= ~(1 << 2); // Up
			if (!left)
				value &= ~(1 << 1); // Left
			if (!right)
				value &= ~(1 << 0); // Right
		}

		if (selectAction) {
			// Action buttons
			if (!start)
				value &= ~(1 << 3); // Start
			if (!select)
				value &= ~(1 << 2); // Select
			if (!b)
				value &= ~(1 << 1); // B
			if (!a)
				value &= ~(1 << 0); // A
		}

		return value;
	}

	// Handle writing to joypad register (setting select bits)
	public void writeJoypadSelect(int value) {
		boolean oldSelectDirection = selectDirection;
		boolean oldSelectAction = selectAction;

		selectDirection = (value & (1 << 4)) != 0; // P14
		selectAction = (value & (1 << 5)) != 0; // P15

		// Check for button presses when select bits change to active (0)
		if (interruptions != null) {
			if (!oldSelectDirection && selectDirection) {
				// Direction buttons now selected - check for presses
				if (down && !prevDown)
					interruptions.requestInterrupt((byte) 0x10);
				if (up && !prevUp)
					interruptions.requestInterrupt((byte) 0x10);
				if (left && !prevLeft)
					interruptions.requestInterrupt((byte) 0x10);
				if (right && !prevRight)
					interruptions.requestInterrupt((byte) 0x10);
			}
			if (!oldSelectAction && selectAction) {
				// Action buttons now selected - check for presses
				if (start && !prevStart)
					interruptions.requestInterrupt((byte) 0x10);
				if (select && !prevSelect)
					interruptions.requestInterrupt((byte) 0x10);
				if (b && !prevB)
					interruptions.requestInterrupt((byte) 0x10);
				if (a && !prevA)
					interruptions.requestInterrupt((byte) 0x10);
			}
		}
	}

	// Convenience methods for setting button states
	public void setUp(boolean pressed) {
		prevUp = up;
		up = pressed;
		if (pressed && !prevUp && !selectDirection && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setDown(boolean pressed) {
		prevDown = down;
		down = pressed;
		if (pressed && !prevDown && !selectDirection && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setLeft(boolean pressed) {
		prevLeft = left;
		left = pressed;
		if (pressed && !prevLeft && !selectDirection && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setRight(boolean pressed) {
		prevRight = right;
		right = pressed;
		if (pressed && !prevRight && !selectDirection && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setA(boolean pressed) {
		prevA = a;
		a = pressed;
		if (pressed && !prevA && !selectAction && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setB(boolean pressed) {
		prevB = b;
		b = pressed;
		if (pressed && !prevB && !selectAction && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setStart(boolean pressed) {
		prevStart = start;
		start = pressed;
		if (pressed && !prevStart && !selectAction && interruptions != null) {
			interruptions.requestInterrupt((byte) 0x10);
		}
	}

	public void setSelect(boolean pressed) {
		prevSelect = select;
		select = pressed;
		if (pressed && !prevSelect && !selectAction && interruptions != null) {
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
