package gbc.cli;

import gbc.ui.controller.EmulatorController;
import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleTest {

    private static final Logger LOGGER = Logger.getLogger(SimpleTest.class.getName());
    public static void main(String[] args) {
        LOGGER.info("=== Simple Emulator Test ===");

        // Configurar Swing para debugging
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                LOGGER.info("Creating controller...");
                EmulatorController controller = new EmulatorController();

                LOGGER.info("Starting emulator...");
                controller.start();

                LOGGER.info("Emulator UI should be visible now");

                // Keep the program running
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    LOGGER.info("Shutting down...");
                    controller.stop();
                }));

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Simple emulator test failed", e);
                System.exit(1);
            }
        });

        LOGGER.info("Main thread finished, UI should be running...");
    }
}
