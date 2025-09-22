package gbc.cli;

import gbc.ui.controller.EmulatorController;
import javax.swing.SwingUtilities;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("=== Simple Emulator Test ===");

        // Configurar Swing para debugging
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Creating controller...");
                EmulatorController controller = new EmulatorController();

                System.out.println("Starting emulator...");
                controller.start();

                System.out.println("Emulator UI should be visible now");

                // Keep the program running
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("Shutting down...");
                    controller.stop();
                }));

            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        });

        System.out.println("Main thread finished, UI should be running...");
    }
}
