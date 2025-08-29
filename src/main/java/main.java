import gbc.controller.EmulatorController;
import gbc.view.EmulatorWindow;

import javax.swing.SwingUtilities;


public class main {

	public static void main(String[] args) {
		// Ensure GUI is created on EDT
		SwingUtilities.invokeLater(() -> {
			try {
				EmulatorController controller = new EmulatorController();
				
				// Load ROM if provided as argument
				if (args.length > 0) {
					controller.loadRom(args[0]);
				} else {
					// Default ROM for testing
					controller.loadRom("./Tetris.gb");
				}
				
				// Start the emulator
				controller.start();
				
			} catch (Exception e) {
				System.err.println("Error starting emulator: " + e.getMessage());
				e.printStackTrace();
			}
		});
	}
}
