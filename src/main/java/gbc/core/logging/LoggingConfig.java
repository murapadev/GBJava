package gbc.core.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Centralizes java.util.logging setup so modules can share consistent output
 * formatting and per-package verbosity. The configuration respects standard
 * JUL system properties; if {@code java.util.logging.config.file} is provided,
 * this helper will not override user settings.
 */
public final class LoggingConfig {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private LoggingConfig() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        if (System.getProperty("java.util.logging.config.file") != null) {
            return; // honor external configuration
        }

        System.setProperty("java.util.logging.SimpleFormatter.format",
                System.getProperty("java.util.logging.SimpleFormatter.format",
                        "%1$tF %1$tT.%1$tL [%4$s] %3$s - %5$s%6$s%n"));

        LogManager manager = LogManager.getLogManager();
        Logger root = manager.getLogger("");

        // Replace default handlers with a single console handler tuned for tracing
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }

        boolean logToConsole = Boolean.parseBoolean(System.getProperty("gbc.logging.console", "true"));
        if (logToConsole) {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());
            consoleHandler.setLevel(Level.ALL);
            root.addHandler(consoleHandler);
        }

        String logFile = System.getProperty("gbc.logging.file");
        if (logFile != null && !logFile.isBlank()) {
            try {
                FileHandler fileHandler = new FileHandler(logFile, true);
                fileHandler.setFormatter(new SimpleFormatter());
                fileHandler.setLevel(Level.ALL);
                root.addHandler(fileHandler);
            } catch (Exception ex) {
                Logger.getLogger(LoggingConfig.class.getName()).log(Level.WARNING,
                        "Failed to create log file handler", ex);
            }
        }

        Level rootLevel = levelFromProperty("gbc.logging.level.root", Level.ALL);
        root.setLevel(rootLevel);

        // Module/package defaults – can be overridden via system properties
        configureLogger("gbc.model.cpu", "gbc.logging.level.cpu", Level.FINE);
        configureLogger("gbc.model.memory", "gbc.logging.level.memory", Level.FINE);
        configureLogger("gbc.model.graphics", "gbc.logging.level.graphics", Level.FINE);
        configureLogger("gbc.model", "gbc.logging.level.core", Level.INFO);
        configureLogger("gbc.ui", "gbc.logging.level.ui", Level.INFO);
        configureLogger("gbc.cli", "gbc.logging.level.cli", Level.INFO);
    }

    private static void configureLogger(String loggerName, String propertyKey, Level defaultLevel) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(levelFromProperty(propertyKey, defaultLevel));
    }

    private static Level levelFromProperty(String key, Level defaultLevel) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultLevel;
        }
        try {
            return Level.parse(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(LoggingConfig.class.getName()).log(Level.WARNING,
                    () -> String.format("Invalid log level '%s' for %s; using %s", value, key, defaultLevel));
            return defaultLevel;
        }
    }
}
