package gbc.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import gbc.model.cartridge.*;
import gbc.model.controller.Controller;
import gbc.model.cpu.*;
import gbc.model.memory.*;
import gbc.model.graphics.*;

public class GameBoyColor {
	private final CPU cpu;
	private final Memory memory;
	private PPU ppu;

	private Screen screen;

	private Controller input;

	public GameBoyColor() {
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.screen = new Screen();
		this.ppu = new PPU(this.memory, this.screen);
		this.input = new Controller(this.memory);

		// Set up controller in memory and interruptions
		this.memory.setController(this.input);
		this.input.setInterruptions(this.cpu.getInterruptions());
	}

	public int executeCycle() {

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
		} catch (IOException e) {
			e.printStackTrace();
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
}
