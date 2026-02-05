package gbc.controller.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Locale;

public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig();
    private final Config config;

    private AppConfig() {
        this.config = ConfigFactory.load();
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    public int getInt(String path, int defaultValue) {
        if (config.hasPath(path)) {
            return config.getInt(path);
        }
        return Integer.getInteger(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        if (config.hasPath(path)) {
            return config.getLong(path);
        }
        return Long.getLong(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        if (config.hasPath(path)) {
            return config.getBoolean(path);
        }
        return Boolean.parseBoolean(System.getProperty(path, Boolean.toString(defaultValue)));
    }

    public String getString(String path, String defaultValue) {
        if (config.hasPath(path)) {
            return config.getString(path);
        }
        return System.getProperty(path, defaultValue);
    }

    public String getStringLower(String path, String defaultValue) {
        String value = getString(path, defaultValue);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
