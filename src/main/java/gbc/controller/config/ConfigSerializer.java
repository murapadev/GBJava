package gbc.controller.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles round-trip persistence of {@link EmulatorConfig} to/from
 * {@code emulator.properties} files. Every field in the config is
 * serialized — nothing is silently dropped.
 */
public final class ConfigSerializer {
    private static final Logger LOGGER = Logger.getLogger(ConfigSerializer.class.getName());

    private ConfigSerializer() {
    }

    /** Load an {@link EmulatorConfig} from a properties file. */
    public static EmulatorConfig load(Path file) {
        EmulatorConfig config = new EmulatorConfig();
        if (!Files.exists(file)) {
            return config;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load config from " + file, e);
            return config;
        }
        return fromProperties(props);
    }

    /** Map a {@link Properties} object into an {@link EmulatorConfig}. */
    public static EmulatorConfig fromProperties(Properties props) {
        EmulatorConfig c = new EmulatorConfig();

        // Core
        c.setRomDir(str(props, "emulator.romDir", ""));
        c.setSaveDir(str(props, "emulator.saveDir", ""));
        c.setFastForwardSpeed(intVal(props, "emulator.fastForwardSpeed", 1));
        c.setFrameRate(intVal(props, "emulator.frameRate", 60));
        c.setThrottle(bool(props, "emulator.throttle", true));
        c.setSyncMode(str(props, "emulator.syncMode", "hybrid"));
        c.setHardware(str(props, "emulator.hardware", "auto"));
        c.setBootRom(str(props, "emulator.bootRom", ""));

        // Video
        c.setScale(intVal(props, "video.scale", 1));
        c.setIntegerScale(bool(props, "video.integerScale", false));
        c.setScanlines(bool(props, "video.scanlines", false));
        c.setVsync(bool(props, "video.vsync", false));
        c.setFilter(str(props, "video.filter", "none"));
        c.setPalette(str(props, "video.palette", "dmg_default"));
        c.setFrameskip(intVal(props, "video.frameskip", 0));
        c.setRenderStats(bool(props, "video.renderStats", false));

        // Audio
        c.setAudioEnabled(bool(props, "audio.enabled", true));
        c.setSampleRate(intVal(props, "audio.sampleRate", 44100));
        c.setAudioBufferSize(intVal(props, "audio.bufferSize", 4096));
        c.setLatencyMs(intVal(props, "audio.latencyMs", 10));
        c.setNullOutput(bool(props, "gbc.audio.nullOutput", false));
        c.setMixer(str(props, "gbc.audio.mixer", ""));
        c.setAudioDebug(bool(props, "gbc.audio.debug", false));
        c.setTestTone(bool(props, "gbc.audio.testTone", false));

        // Input
        c.setRepeatDelayMs(intVal(props, "input.repeatDelayMs", 0));
        c.setRepeatRateMs(intVal(props, "input.repeatRateMs", 10));
        c.setDebounceMs(intVal(props, "input.debounceMs", 0));
        c.setMinPressMs(intVal(props, "input.minPressMs", 0));
        c.setDeadzone(dbl(props, "input.deadzone", 0.0));
        c.setJoystickEnabled(bool(props, "input.joystick.enabled", true));
        c.setJoystickDevice(str(props, "input.joystick.device", ""));
        c.setJoystickPollMs(intVal(props, "input.joystick.pollMs", 5));

        // Key bindings
        c.getKeyBindings().clear();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.key.")) {
                String keyName = key.substring("input.key.".length());
                c.getKeyBindings().put(keyName, props.getProperty(key));
            }
        }

        // Joystick mapping
        c.getJoystickButtons().clear();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.joystick.button.")) {
                c.getJoystickButtons().put(key, props.getProperty(key));
            }
        }
        c.setAxisX(str(props, "input.joystick.axis.x", "x"));
        c.setAxisY(str(props, "input.joystick.axis.y", "y"));
        c.setAxisXInverted(bool(props, "input.joystick.axis.x.inverted", false));
        c.setAxisYInverted(bool(props, "input.joystick.axis.y.inverted", false));
        c.setPovEnabled(bool(props, "input.joystick.pov", true));

        // Logging
        c.setLoggingConsole(bool(props, "gbc.logging.console", false));
        c.setLoggingFile(str(props, "gbc.logging.file", ""));
        c.setLevelRoot(str(props, "gbc.logging.level.root", "OFF"));
        c.setLevelModel(str(props, "gbc.logging.level.model", "OFF"));
        c.setLevelCpu(str(props, "gbc.logging.level.cpu", "OFF"));
        c.setLevelMemory(str(props, "gbc.logging.level.memory", "OFF"));
        c.setLevelGraphics(str(props, "gbc.logging.level.graphics", "OFF"));
        c.setLevelView(str(props, "gbc.logging.level.view", "OFF"));
        c.setLevelController(str(props, "gbc.logging.level.controller", "OFF"));

        // Debug
        c.setPpuTrace(bool(props, "gbc.ppu.trace", false));
        c.setTimerTrace(bool(props, "gbc.timer.trace", false));
        c.setCpuTrace(bool(props, "gbc.cpu.trace", false));

        // UI
        c.setTheme(str(props, "ui.theme", "light"));

        return c;
    }

    /** Save an {@link EmulatorConfig} to a properties file with section headers. */
    public static void save(Path file, EmulatorConfig config) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Emulator configuration (generated by UI)\n");

        // Core
        appendSection(out, "Core", List.of(
                kv("emulator.romDir", config.getRomDir()),
                kv("emulator.saveDir", config.getSaveDir()),
                kv("emulator.fastForwardSpeed", config.getFastForwardSpeed()),
                kv("emulator.frameRate", config.getFrameRate()),
                kv("emulator.throttle", config.isThrottle()),
                kv("emulator.syncMode", config.getSyncMode()),
                kv("emulator.hardware", config.getHardware()),
                kv("emulator.bootRom", config.getBootRom())));

        // Video
        appendSection(out, "Video", List.of(
                kv("video.scale", config.getScale()),
                kv("video.integerScale", config.isIntegerScale()),
                kv("video.scanlines", config.isScanlines()),
                kv("video.vsync", config.isVsync()),
                kv("video.filter", config.getFilter()),
                kv("video.palette", config.getPalette()),
                kv("video.frameskip", config.getFrameskip()),
                kv("video.renderStats", config.isRenderStats())));

        // Audio
        appendSection(out, "Audio", List.of(
                kv("audio.enabled", config.isAudioEnabled()),
                kv("audio.sampleRate", config.getSampleRate()),
                kv("audio.bufferSize", config.getAudioBufferSize()),
                kv("audio.latencyMs", config.getLatencyMs()),
                kv("gbc.audio.nullOutput", config.isNullOutput()),
                kv("gbc.audio.mixer", config.getMixer()),
                kv("gbc.audio.debug", config.isAudioDebug()),
                kv("gbc.audio.testTone", config.isTestTone())));

        // Input
        appendSection(out, "Input", List.of(
                kv("input.repeatDelayMs", config.getRepeatDelayMs()),
                kv("input.repeatRateMs", config.getRepeatRateMs()),
                kv("input.debounceMs", config.getDebounceMs()),
                kv("input.minPressMs", config.getMinPressMs()),
                kv("input.deadzone", config.getDeadzone()),
                kv("input.joystick.enabled", config.isJoystickEnabled()),
                kv("input.joystick.device", config.getJoystickDevice()),
                kv("input.joystick.pollMs", config.getJoystickPollMs())));

        // Input Bindings
        out.append("\n# --- Input Bindings ---\n");
        for (Map.Entry<String, String> entry : config.getKeyBindings().entrySet()) {
            out.append("input.key.").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        // Joystick Mapping
        out.append("\n# --- Joystick Mapping ---\n");
        for (Map.Entry<String, String> entry : config.getJoystickButtons().entrySet()) {
            out.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        out.append("input.joystick.axis.x=").append(config.getAxisX()).append("\n");
        out.append("input.joystick.axis.y=").append(config.getAxisY()).append("\n");
        out.append("input.joystick.axis.x.inverted=").append(config.isAxisXInverted()).append("\n");
        out.append("input.joystick.axis.y.inverted=").append(config.isAxisYInverted()).append("\n");
        out.append("input.joystick.pov=").append(config.isPovEnabled()).append("\n");

        // Logging
        appendSection(out, "Logging", List.of(
                kv("gbc.logging.console", config.isLoggingConsole()),
                kv("gbc.logging.file", config.getLoggingFile()),
                kv("gbc.logging.level.root", config.getLevelRoot()),
                kv("gbc.logging.level.model", config.getLevelModel()),
                kv("gbc.logging.level.cpu", config.getLevelCpu()),
                kv("gbc.logging.level.memory", config.getLevelMemory()),
                kv("gbc.logging.level.graphics", config.getLevelGraphics()),
                kv("gbc.logging.level.view", config.getLevelView()),
                kv("gbc.logging.level.controller", config.getLevelController())));

        // Debug
        appendSection(out, "Debug", List.of(
                kv("gbc.ppu.trace", config.isPpuTrace()),
                kv("gbc.timer.trace", config.isTimerTrace()),
                kv("gbc.cpu.trace", config.isCpuTrace())));

        // UI
        appendSection(out, "UI", List.<String[]>of(
                kv("ui.theme", config.getTheme())));

        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    }

    /** Push all config values into System properties for backward compatibility. */
    public static void applyToSystemProperties(EmulatorConfig config) {
        Properties props = config.toProperties();
        for (String key : props.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                System.setProperty(key, props.getProperty(key));
            }
        }
    }

    /** Push all config values into System properties, overwriting existing values. */
    public static void forceApplyToSystemProperties(EmulatorConfig config) {
        Properties props = config.toProperties();
        for (String key : props.stringPropertyNames()) {
            System.setProperty(key, props.getProperty(key));
        }
    }

    // ---- Helpers ----

    private static void appendSection(StringBuilder out, String title, List<String[]> entries) {
        out.append("\n# --- ").append(title).append(" ---\n");
        for (String[] kv : entries) {
            out.append(kv[0]).append("=").append(kv[1]).append("\n");
        }
    }

    private static String[] kv(String key, Object value) {
        return new String[] { key, String.valueOf(value) };
    }

    private static String str(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        return (value != null) ? value : defaultValue;
    }

    private static int intVal(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double dbl(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean bool(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
