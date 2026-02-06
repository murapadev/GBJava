package gbc.model.input;

import gbc.model.cpu.Interruptions;

/**
 * Game Boy joypad controller.
 *
 * <h3>Select-line behavior</h3>
 * <p>The joypad register (FF00) uses active-low logic. P14 (bit 4) selects
 * the direction button group, P15 (bit 5) selects the action button group.
 * When both lines are selected (both bits low), the button states from both
 * groups are OR'd together, causing "ghosting" — pressing certain button
 * combinations can produce phantom readings.
 *
 * <h3>Interrupt edges</h3>
 * <p>A joypad interrupt (IF bit 4) is requested on any high-to-low transition
 * of the lower nibble (bits 0-3) of the joypad register. This can occur when:
 * <ul>
 *   <li>A button is pressed while its group is selected</li>
 *   <li>The select lines are changed to reveal a pressed button</li>
 * </ul>
 */
public class Controller {
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

		// When a select line is active (low), the corresponding buttons are readable.
		// When both lines are active, button states from both groups are OR'd,
		// causing ghosting artifacts on real hardware.
		boolean readDirections = !selectDirection;
		boolean readActions = !selectAction;

		if (readDirections) {
			if (down)
				value &= ~(1 << 3);
			if (up)
				value &= ~(1 << 2);
			if (left)
				value &= ~(1 << 1);
			if (right)
				value &= ~(1 << 0);
		}

		if (readActions) {
			if (start)
				value &= ~(1 << 3);
			if (select)
				value &= ~(1 << 2);
			if (b)
				value &= ~(1 << 1);
			if (a)
				value &= ~(1 << 0);
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
