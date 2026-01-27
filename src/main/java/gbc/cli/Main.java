package gbc.cli;

import gbc.ui.controller.EmulatorController;

import javax.swing.SwingUtilities;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String DEFAULT_CONFIG = "emulator.properties";

    private static void loadConfigProperties() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(DEFAULT_CONFIG)) {
            props.load(in);
        } catch (IOException e) {
            LOGGER.log(Level.INFO, () -> "No emulator.properties found; using defaults");
            return;
        }
        for (String key : props.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                System.setProperty(key, props.getProperty(key));
            }
        }
    }

	public static void main(String[] args) {
        loadConfigProperties();
        try {
            gbc.core.input.JInputNativeLoader.loadIfNeeded();
        } catch (Throwable ignored) {
            // Keep startup resilient if joystick natives are unavailable.
        }
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
					// Try to load default ROM from configured ROM dir
					String romDir = System.getProperty("emulator.romDir", "samples/roms");
					String romPath = romDir + java.io.File.separator + "Tetris.gb";
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
