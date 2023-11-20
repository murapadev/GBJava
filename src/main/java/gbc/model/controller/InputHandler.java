package main.java.model.controller;

public class InputHandler {
	private boolean[] keys;
	private boolean[] keysPressed;
	private boolean[] keysReleased;

	public InputHandler() {
		keys = new boolean[8];
		keysPressed = new boolean[8];
		keysReleased = new boolean[8];
	}

	public void setKey(int key, boolean pressed) {
		keys[key] = pressed;
		if (pressed) {
			keysPressed[key] = true;
		} else {
			keysReleased[key] = true;
		}
	}

	public boolean isKeyPressed(int key) {
		return keysPressed[key];
	}

	public boolean isKeyReleased(int key) {
		return keysReleased[key];
	}

	public void resetKeys() {
		keysPressed = new boolean[8];
		keysReleased = new boolean[8];
	}

	public boolean isKeyPressed() {
		for (int i = 0; i < keysPressed.length; i++) {
			if (keysPressed[i]) {
				return true;
			}
		}
		return false;
	}

	public boolean isKeyReleased() {
		for (int i = 0; i < keysReleased.length; i++) {
			if (keysReleased[i]) {
				return true;
			}
		}
		return false;
	}

	public boolean isKey(int key) {
		return keys[key];
	}
}