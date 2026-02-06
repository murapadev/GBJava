package gbc.controller.config;

import java.util.Locale;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig();
    private final Config config;

    private AppConfig() {
        this.config = ConfigFactory.load();
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    /**
     * Returns an integer setting. System properties (set at runtime by the
     * Settings dialog) take priority over the static HOCON config so that
     * changes applied through the UI are visible immediately.
     */
    public int getInt(String path, int defaultValue) {
        String sysProp = System.getProperty(path);
        if (sysProp != null && !sysProp.isBlank()) {
            try {
                return Integer.parseInt(sysProp.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (config.hasPath(path)) {
            try {
                return config.getInt(path);
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    public long getLong(String path, long defaultValue) {
        String sysProp = System.getProperty(path);
        if (sysProp != null && !sysProp.isBlank()) {
            try {
                return Long.parseLong(sysProp.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (config.hasPath(path)) {
            try {
                return config.getLong(path);
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        String sysProp = System.getProperty(path);
        if (sysProp != null && !sysProp.isBlank()) {
            return Boolean.parseBoolean(sysProp.trim());
        }
        if (config.hasPath(path)) {
            try {
                return config.getBoolean(path);
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    public String getString(String path, String defaultValue) {
        String sysProp = System.getProperty(path);
        if (sysProp != null) {
            return sysProp;
        }
        if (config.hasPath(path)) {
            try {
                return config.getString(path);
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    public String getStringLower(String path, String defaultValue) {
        String value = getString(path, defaultValue);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
