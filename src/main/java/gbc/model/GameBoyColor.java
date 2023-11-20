package main.java.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import main.java.model.cartridge.*;
import main.java.model.cpu.*;
import main.java.model.memory.*;
import main.java.view.EmulatorView;
import main.java.model.graphics.*;

public class GameBoyColor {
	private CPU cpu;
	private Memory memory;
	private Cartridge cartridge;
	private GPU gpu;

	public GameBoyColor() {
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.gpu = new GPU(this.memory, this.cpu);

	}

	public void executeCycle() {
		cpu.executeCycle();
		gpu.step(cpu.getCycles());
		cpu.setCycles(0);



	}

	public void insertCartridge(String path) {
		byte[] data;
		try {
			data = Files.readAllBytes(Paths.get(path));
			cartridge = CartridgeFactory.create(data);
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

	public GPU getGpu() {
		return this.gpu;
	}
}
