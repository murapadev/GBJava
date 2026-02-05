package gbc;

import javax.swing.SwingUtilities;

import gbc.controller.logging.LoggingConfig;
import gbc.model.GameBoyColor;
import gbc.controller.EmulatorController;
import gbc.view.EmulatorWindow;
import gbc.view.ThemeManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        LoggingConfig.initialize();
        ThemeManager.apply();

        SwingUtilities.invokeLater(() -> {
            GameBoyColor gbc = new GameBoyColor();
            EmulatorWindow window = new EmulatorWindow(gbc);
            EmulatorController controller = new EmulatorController(gbc, window);
            controller.start();
        });
    }
}
