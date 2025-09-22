package gbc.cli;

import gbc.ui.controller.EmulatorController;
import javax.swing.SwingUtilities;

public class TestUi {
    public static void main(String[] args) {
        System.out.println("Starting UI test...");

        SwingUtilities.invokeLater(() -> {
            try {
                EmulatorController controller = new EmulatorController();
                System.out.println("Controller created");

                controller.start();
                System.out.println("Controller started");

                // Keep main thread alive
                Thread.sleep(5000); // Run for 5 seconds

                controller.stop();
                System.out.println("Test completed");
                System.exit(0);

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
}
