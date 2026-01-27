package gbc.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.core.input.Controller;
import gbc.core.logging.LoggingConfig;
import gbc.model.cartridge.Cartridge;
import gbc.model.cartridge.CartridgeFactory;
import gbc.model.cpu.CPU;
import gbc.model.graphics.PPU;
import gbc.model.graphics.Screen;
import gbc.model.memory.Memory;
import gbc.util.TraceGenerator;

public class GameBoyColor {
	private static final Logger LOGGER = Logger.getLogger(GameBoyColor.class.getName());
	private final CPU cpu;
	private final Memory memory;
	private PPU ppu;
	private TraceGenerator trace;

	private Screen screen;

	private Controller input;

	private boolean paused = false;
	private float speedMultiplier = 1.0f;
	private String currentRomPath;
	private int peripheralCycleRemainder;

	public GameBoyColor() {
		LoggingConfig.initialize();
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.screen = new Screen();
		this.ppu = new PPU(this.memory, this.screen);
		this.memory.setPPU(this.ppu);
		this.input = new Controller();

		// Set up controller in memory and interruptions
		this.memory.setController(this.input);
		this.input.setInterruptions(this.cpu.getInterruptions());
        applyHardwareSetting(null);
	}

	public int executeCycle() {
		if (paused) {
			return 0; // Return 0 cycles when paused
		}
		if (trace != null) {
			trace.logInstruction();
		}

		int cycles = cpu.executeCycle();
		if (cycles > 0) {
			int stepCycles = cycles;
			if (cpu.isDoubleSpeedMode()) {
				peripheralCycleRemainder += cycles;
				stepCycles = peripheralCycleRemainder / 2;
				peripheralCycleRemainder %= 2;
			}
			if (stepCycles > 0) {
				memory.stepPeripherals(stepCycles);
			}
		}
		return cycles;
	}

	public void insertCartridge(String path) {
		byte[] data;
		try {
			data = Files.readAllBytes(Paths.get(path));
			Cartridge cartridge = CartridgeFactory.create(data);
			cartridge.setRomPath(path);
			memory.loadCartridge(cartridge);
			currentRomPath = path;
            applyHardwareSetting(path);
			
			// Try to load existing SRAM save
			cartridge.loadSRAM();
			
			if (System.getProperty("gbc.trace") != null) {
				String romName = Paths.get(path).getFileName().toString();
				this.trace = new TraceGenerator(romName, cpu, memory);
			}
			LOGGER.log(Level.INFO, () -> String.format("Loaded cartridge from %s", path));
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to load cartridge from " + path, e);
		}

	}

	public void reset() {
		cpu.reset();
		memory.reset();
		screen.reset();
		ppu.reset();
		peripheralCycleRemainder = 0;
	}

    private void applyHardwareSetting(String romPath) {
        String mode = System.getProperty("emulator.hardware", "auto").toLowerCase();
        if ("auto".equals(mode) && memory.isHardwareOverride()) {
            return;
        }
        HardwareType type;
        switch (mode) {
            case "dmg":
                type = HardwareType.DMG;
                break;
            case "dmg0":
                type = HardwareType.DMG0;
                break;
            case "mgb":
                type = HardwareType.MGB;
                break;
            case "sgb":
                type = HardwareType.SGB;
                break;
            case "sgb2":
                type = HardwareType.SGB2;
                break;
            case "cgb":
            case "gbc":
                type = HardwareType.CGB;
                break;
            case "auto":
            default:
                if (memory.getCartridge() != null && memory.getCartridge().isCgbCompatible()) {
                    type = HardwareType.CGB;
                } else {
                    type = HardwareType.DMG;
                }
                break;
        }
        if ("auto".equals(mode)) {
            memory.setHardwareTypeAuto(type);
        } else {
            memory.setHardwareType(type);
        }
        cpu.reset();
        ppu.reset();
        screen.reset();
    }
	
	/**
	 * Saves the battery-backed SRAM to disk.
	 * Should be called on emulator shutdown or when saving is requested.
	 */
	public void saveSRAM() {
		Cartridge cartridge = memory.getCartridge();
		if (cartridge != null && cartridge.hasBattery()) {
			cartridge.saveSRAM();
		}
	}
	
	/**
	 * Returns the path of the currently loaded ROM, or null if none loaded.
	 */
	public String getCurrentRomPath() {
		return currentRomPath;
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

	public float getEffectiveSpeedMultiplier() {
		float hardwareMultiplier = cpu.isDoubleSpeedMode() ? 2.0f : 1.0f;
		return this.speedMultiplier * hardwareMultiplier;
	}

	public TraceGenerator getTrace() {
		return this.trace;
	}

	public void closeTrace() {
		if (this.trace != null) {
			this.trace.close();
			this.trace = null;
		}
	}
	
	/**
	 * Shuts down the emulator, saving any battery-backed RAM.
	 */
	public void shutdown() {
		saveSRAM();
		closeTrace();
	}
}
