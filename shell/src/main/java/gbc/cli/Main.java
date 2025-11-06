package gbc.cli;

import gbc.ui.controller.EmulatorController;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

	public static void main(String[] args) {
		// Ensure GUI is created on EDT
		SwingUtilities.invokeLater(() -> {
			try {
				EmulatorController controller = new EmulatorController();

				// Always start the emulator UI first
				controller.start();

				// Load ROM if provided as argument
				if (args.length > 0) {
					controller.loadRom(args[0]);
				} else {
					// Try to load default ROM
					String romPath = "samples/roms/Tetris.gb";
					java.io.File romFile = new java.io.File(romPath);
					if (romFile.exists()) {
						LOGGER.log(Level.INFO, () -> "Loading default ROM: " + romPath);
						controller.loadRom(romPath);
					} else {
						LOGGER.log(Level.INFO, () -> "Default ROM not found: " + romPath);
						LOGGER.info("Running without ROM - showing test pattern");
					}
				}

			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error starting emulator", e);
				System.exit(1);
			}
		});
	}
}
