package gbc;

import java.net.URISyntaxException;
import java.nio.file.Files;
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
    private static final String CONFIG_FILENAME = "emulator.properties";

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
        Path path = resolveConfigPath();
        AppConfig.get().setConfigPath(path);
        EmulatorConfig config = ConfigSerializer.load(path);
        AppConfig.get().setConfig(config);
        ConfigSerializer.applyToSystemProperties(config);
        LOGGER.info("Loaded emulator configuration from " + path.toAbsolutePath());
    }

    /**
     * Resolves the config file path using a priority chain:
     * 1. Next to the JAR file (portable install)
     * 2. Current working directory (development / existing behavior)
     * 3. User home ~/.gbjava/ (fallback)
     * For new installs, defaults to next-to-JAR if available, else CWD.
     */
    private static Path resolveConfigPath() {
        // 1. Next to the JAR
        Path jarDir = resolveJarDirectory();
        if (jarDir != null) {
            Path jarConfig = jarDir.resolve(CONFIG_FILENAME);
            if (Files.exists(jarConfig)) {
                return jarConfig;
            }
        }

        // 2. Current working directory
        Path cwd = Path.of(CONFIG_FILENAME);
        if (Files.exists(cwd)) {
            return cwd;
        }

        // 3. User home
        Path home = Path.of(System.getProperty("user.home"), ".gbjava", CONFIG_FILENAME);
        if (Files.exists(home)) {
            return home;
        }

        // 4. Default: next to JAR if available, else CWD
        return jarDir != null ? jarDir.resolve(CONFIG_FILENAME) : cwd;
    }

    private static Path resolveJarDirectory() {
        try {
            Path jarPath = Path.of(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar")) {
                return jarPath.getParent();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
        }
        return null;
    }
}
