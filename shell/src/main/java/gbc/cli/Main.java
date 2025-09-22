package gbc.cli;

import gbc.ui.controller.EmulatorController;

import javax.swing.SwingUtilities;

public class Main {

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
						System.out.println("Loading default ROM: " + romPath);
						controller.loadRom(romPath);
					} else {
						System.out.println("Default ROM not found: " + romPath);
						System.out.println(
								"Running without ROM - showing test pattern");
					}
				}

			} catch (Exception e) {
				System.err.println("Error starting emulator: " + e.getMessage());
				e.printStackTrace();
				System.exit(1);
			}
		});
	}
}
