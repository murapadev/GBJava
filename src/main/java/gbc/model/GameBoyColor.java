package gbc.model;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.controller.logging.LoggingConfig;
import gbc.model.cartridge.Cartridge;
import gbc.model.cpu.CPU;
import gbc.model.graphics.FrameBuffer;
import gbc.model.graphics.PPU;
import gbc.model.graphics.Screen;
import gbc.model.input.Controller;
import gbc.model.memory.Memory;
import gbc.model.trace.TraceGenerator;

/**
 * Top-level Game Boy / Game Boy Color emulator.
 *
 * <p>Architecture: The emulator uses a single {@link Memory} instance that
 * handles DMG/CGB differences via mode flags. CGB-specific features (VRAM
 * banking, WRAM banking, HDMA, double speed) are gated by {@code cgbMode}
 * in Memory. DMG/CGB rendering differences are handled by separate
 * {@link gbc.model.graphics.PixelFifo} implementations selected at PPU reset.
 *
 * <p>Hardware profile is determined at cartridge load time based on the ROM's
 * CGB flag and the {@code emulator.hardware} system property. The profile
 * controls initial register state, LCD timing, and APU behavior.
 */
public class GameBoyColor {
	private static final Logger LOGGER = Logger.getLogger(GameBoyColor.class.getName());
	private final CPU cpu;
	private final Memory memory;
	private PPU ppu;
	private TraceGenerator trace;

	private final FrameBuffer frameBuffer;
	private Screen screen;

	private Controller input;

	private boolean paused = false;
	private float speedMultiplier = 1.0f;
	private String currentRomPath;

	public GameBoyColor() {
		LoggingConfig.initialize();
		this.memory = new Memory();
		this.cpu = new CPU(this.memory);
		this.frameBuffer = new FrameBuffer();
		this.screen = new Screen();
		this.ppu = new PPU(this.memory, this.frameBuffer, this.screen);
		this.memory.setPPU(this.ppu);

		this.input = new Controller();

		// Set up controller in memory and interruptions
		this.memory.setController(this.input);
		this.input.setInterruptions(this.cpu.getInterruptions());
		applyHardwareSetting(null);
	}

	public int executeCycle() {
		if (trace != null) {
			trace.logInstruction();
		}

		int cycles = cpu.executeCycle();
		return cycles;
	}

	public void insertCartridge(Cartridge cartridge) {
		if (cartridge == null) {
			LOGGER.warning("Cannot insert cartridge: null");
			return;
		}
		memory.loadCartridge(cartridge);
		Path romPath = cartridge.getRomPath();
		currentRomPath = romPath != null ? romPath.toString() : null;
		applyHardwareSetting(currentRomPath);
		LOGGER.log(Level.INFO, () -> String.format("Hardware=%s CGBMode=%s CGBFlag=0x%02X",
				memory.getHardwareType(), memory.isCgbMode(), cartridge.getCgbFlag() & 0xFF));

		// Try to load existing SRAM save
		cartridge.loadSRAM();

		if (System.getProperty("gbc.trace") != null) {
			String romName = romPath != null ? romPath.getFileName().toString() : cartridge.getTitle();
			if (romName == null || romName.isBlank()) {
				romName = "rom";
			}
			this.trace = new TraceGenerator(romName, cpu, memory);
			cpu.setTrace(this.trace);
		}
		if (currentRomPath != null) {
			LOGGER.log(Level.INFO, () -> String.format("Loaded cartridge from %s", currentRomPath));
		} else {
			LOGGER.info("Loaded cartridge");
		}
	}

	public void reset() {
		applyHardwareSetting(currentRomPath);
	}

	private void applyHardwareSetting(String romPath) {
		// If hardware type was explicitly set via setHardwareType(), don't override it
		if (!memory.isHardwareOverride()) {
			String mode = System.getProperty("emulator.hardware", "auto").toLowerCase();
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

	public FrameBuffer getFrameBuffer() {
		return this.frameBuffer;
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
			cpu.setTrace(null);
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
