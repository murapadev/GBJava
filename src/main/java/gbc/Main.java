package gbc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import gbc.controller.EmulatorController;
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
     * Loads {@code emulator.properties} into system properties so that saved
     * user settings are visible to {@link gbc.controller.config.AppConfig},
     * the audio engine, and every other component that reads system properties.
     * Existing system properties (e.g. from {@code -D} flags) are not
     * overwritten.
     */
    private static void loadEmulatorProperties() {
        Path path = Path.of("emulator.properties");
        if (!Files.exists(path)) {
            return;
        }
        Properties fileProps = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            fileProps.load(in);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load emulator.properties", e);
            return;
        }
        for (String key : fileProps.stringPropertyNames()) {
            // Don't override explicit -D flags
            if (System.getProperty(key) == null) {
                System.setProperty(key, fileProps.getProperty(key));
            }
        }
        LOGGER.info("Loaded " + fileProps.size() + " settings from emulator.properties");
    }
}
