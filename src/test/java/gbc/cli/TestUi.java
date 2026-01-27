package gbc.cli;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import gbc.ui.controller.EmulatorController;

public class TestUi {
    private static final Logger LOGGER = Logger.getLogger(TestUi.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Starting UI test...");

        SwingUtilities.invokeLater(() -> {
            try {
                EmulatorController controller = new EmulatorController();
                LOGGER.info("Controller created");

                controller.start();
                LOGGER.info("Controller started");

                // Keep main thread alive
                Thread.sleep(5000); // Run for 5 seconds

                controller.stop();
                LOGGER.info("Test completed");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "UI test failed", e);
            }
        });
    }
}
