package gbc.controller;

import gbc.model.GameBoyColor;
import gbc.view.EmulatorWindow;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmulatorController {
	private GameBoyColor gbc;
	private EmulatorWindow view;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final AtomicBoolean paused = new AtomicBoolean(false);
	
	// Target frame rate (60 FPS for Game Boy)
	private static final int TARGET_FPS = 60;
	private static final long FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS;

	public EmulatorController(GameBoyColor gbc, EmulatorWindow view) {
		this.gbc = gbc;
		this.view = view;
		this.view.setController(this);
		setupKeyListener();
	}

	public EmulatorController() {
		this.gbc = new GameBoyColor();
		this.view = new EmulatorWindow(this.gbc);
		this.view.setController(this);
		setupKeyListener();
	}

	private void setupKeyListener() {
		// Add key listener to the view for input handling
		view.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				handleKeyPress(e);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				handleKeyRelease(e);
			}

			@Override
			public void keyTyped(KeyEvent e) {
				// Not used
			}
		});
		
		// Make sure the view can receive focus for key events
		view.setFocusable(true);
		view.requestFocusInWindow();
	}

	public void start() {
		// Init view
		view.update();
		view.setVisible(true);

		// Main emulation loop
		long lastFrameTime = System.nanoTime();
		
		while (running.get()) {
			long currentTime = System.nanoTime();
			long deltaTime = currentTime - lastFrameTime;
			
			if (deltaTime >= FRAME_TIME_NS && !paused.get()) {
				// Handle input
				handleInput();
				
				// Execute one frame worth of cycles
				executeFrame();
				
				// Update view
				view.update();
				
				lastFrameTime = currentTime;
			}
			
			// Small sleep to prevent busy waiting
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void executeFrame() {
		// Execute enough cycles for one frame (approximately 70224 cycles per frame)
		int cyclesPerFrame = 70224;
		int executedCycles = 0;
		
		while (executedCycles < cyclesPerFrame) {
			int cycles = gbc.executeCycle();
			executedCycles += cycles;
		}
	}

	public void handleInput() {
		// Input is handled through key listeners, but we can add additional logic here
		gbc.getController().handleInput();
	}

	private void handleKeyPress(KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_UP -> gbc.getController().setUp(true);
			case KeyEvent.VK_DOWN -> gbc.getController().setDown(true);
			case KeyEvent.VK_LEFT -> gbc.getController().setLeft(true);
			case KeyEvent.VK_RIGHT -> gbc.getController().setRight(true);
			case KeyEvent.VK_Z -> gbc.getController().setA(true);
			case KeyEvent.VK_X -> gbc.getController().setB(true);
			case KeyEvent.VK_ENTER -> gbc.getController().setStart(true);
			case KeyEvent.VK_SPACE -> gbc.getController().setSelect(true);
			case KeyEvent.VK_P -> togglePause();
			case KeyEvent.VK_R -> reset();
			case KeyEvent.VK_F12 -> view.openDebugView();
		}
	}

	private void handleKeyRelease(KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_UP -> gbc.getController().setUp(false);
			case KeyEvent.VK_DOWN -> gbc.getController().setDown(false);
			case KeyEvent.VK_LEFT -> gbc.getController().setLeft(false);
			case KeyEvent.VK_RIGHT -> gbc.getController().setRight(false);
			case KeyEvent.VK_Z -> gbc.getController().setA(false);
			case KeyEvent.VK_X -> gbc.getController().setB(false);
			case KeyEvent.VK_ENTER -> gbc.getController().setStart(false);
			case KeyEvent.VK_SPACE -> gbc.getController().setSelect(false);
		}
	}

	public void loadRom(String path) {
		try {
			gbc.insertCartridge(path);
			System.out.println("ROM loaded successfully: " + path);
		} catch (Exception e) {
			System.err.println("Error loading ROM: " + e.getMessage());
			JOptionPane.showMessageDialog(view, 
				"Error loading ROM: " + e.getMessage(), 
				"ROM Load Error", 
				JOptionPane.ERROR_MESSAGE);
		}
	}

	public void reset() {
		gbc.reset();
		System.out.println("Emulator reset");
	}

	public void togglePause() {
		boolean isPaused = paused.get();
		paused.set(!isPaused);
		System.out.println(isPaused ? "Emulator resumed" : "Emulator paused");
	}

	public void stop() {
		running.set(false);
	}

	public GameBoyColor getGameBoyColor() {
		return gbc;
	}

	public EmulatorWindow getView() {
		return view;
	}
}
