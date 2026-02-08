package gbc;

import java.nio.file.Path;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import gbc.controller.EmulatorController;
import gbc.controller.config.AppConfig;
import gbc.controller.config.ConfigSerializer;
import gbc.controller.config.EmulatorConfig;
import gbc.controller.logging.LoggingConfig;
import gbc.model.GameBoyColor;
import gbc.view.EmulatorWindow;
import gbc.view.ThemeManager;

public final class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        loadEmulatorProperties();
        LoggingConfig.initialize();
        ThemeManager.apply();

        SwingUtilities.invokeLater(() -> {
            GameBoyColor gbc = new GameBoyColor();
            EmulatorWindow window = new EmulatorWindow(gbc);
            EmulatorController controller = new EmulatorController(gbc, window);
            controller.start();
        });
    }

    /**
     * Loads {@code emulator.properties} into the centralized {@link EmulatorConfig}
     * and pushes values to system properties for backward compatibility.
     */
    private static void loadEmulatorProperties() {
        Path path = Path.of("emulator.properties");
        EmulatorConfig config = ConfigSerializer.load(path);
        AppConfig.get().setConfig(config);
        ConfigSerializer.applyToSystemProperties(config);
        LOGGER.info("Loaded emulator configuration from " + path);
    }
}
