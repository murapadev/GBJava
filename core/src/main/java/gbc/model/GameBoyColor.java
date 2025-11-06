package gbc.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.cartridge.*;
import gbc.core.input.Controller;
import gbc.model.cpu.*;
import gbc.model.memory.*;
import gbc.model.graphics.*;
import gbc.core.logging.LoggingConfig;

public class GameBoyColor {
	private static final Logger LOGGER = Logger.getLogger(GameBoyColor.class.getName());
	private final CPU cpu;
	private final Memory memory;
	private PPU ppu;

	private Screen screen;

	private Controller input;

	private boolean paused = false;
	private float speedMultiplier = 1.0f;

	public GameBoyColor() {
		LoggingConfig.initialize();
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.screen = new Screen();
		this.ppu = new PPU(this.memory, this.screen);
		this.input = new Controller();

		// Set up controller in memory and interruptions
		this.memory.setController(this.input);
		this.input.setInterruptions(this.cpu.getInterruptions());
	}

	public int executeCycle() {
		if (paused) {
			return 0; // Return 0 cycles when paused
		}

		int cycles = cpu.executeCycle();
		ppu.step(cycles);
		memory.stepApu();
		return cycles;
	}

	public void insertCartridge(String path) {
		byte[] data;
		try {
			data = Files.readAllBytes(Paths.get(path));
			Cartridge cartridge = CartridgeFactory.create(data);
			memory.loadCartridge(cartridge);
			LOGGER.log(Level.INFO, () -> String.format("Loaded cartridge from %s", path));
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, () -> "Failed to load cartridge from " + path, e);
		}

	}

	public void reset() {
		cpu.reset();
		memory.reset();
		this.screen = new Screen();
		ppu = new PPU(this.memory, this.screen);

	}

	public Controller getController() {
		return this.input;
	}

	public CPU getCpu() {
		return this.cpu;
	}

	public Memory getMemory() {
		return this.memory;
	}

	public PPU getPpu() {
		return this.ppu;
	}

	public boolean isAudioBufferFull() {
		return memory.isAudioBufferFull();
	}

	public byte[] fetchAudioSamples() {
		return memory.fetchAudioSamples();
	}

	public void pause() {
		this.paused = true;
	}

	public void resume() {
		this.paused = false;
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void setSpeedMultiplier(float multiplier) {
		this.speedMultiplier = Math.max(0.25f, Math.min(4.0f, multiplier)); // Clamp between 0.25x and 4.0x
	}

	public float getSpeedMultiplier() {
		return this.speedMultiplier;
	}
}
