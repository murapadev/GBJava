package gbc.controller.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogManager;

import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Bridges java.util.logging to SLF4J so all logging goes through logback.
 * Honors external JUL configuration when provided.
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
            return;
        }

        LogManager manager = LogManager.getLogManager();
        java.util.logging.Logger root = manager.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
}
