package gbc.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import gbc.model.cartridge.*;
import gbc.model.controller.Controller;
import gbc.model.cpu.*;
import gbc.model.memory.*;
import gbc.view.EmulatorView;
import gbc.model.graphics.*;

public class GameBoyColor {
	private final CPU cpu;
	private final Memory memory;
	private GPU gpu;

	private Controller input;

	public GameBoyColor() {
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.gpu = new GPU(this.memory, this.cpu);
		this.input = new Controller();

	}

	public void executeCycle() {

		input.handleInput();
		cpu.executeCycle();
		gpu.step(cpu.getCycles());
		cpu.setCycles(0);



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
		gpu = new GPU(this.memory, this.cpu);
		
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


	public GPU getGpu() {
		return this.gpu;
	}
}
