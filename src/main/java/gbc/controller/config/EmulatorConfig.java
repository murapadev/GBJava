package gbc.controller.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Centralized, typed configuration for the emulator.
 * Every user-visible setting lives here with sensible defaults.
 * The {@link ConfigSerializer} handles round-trip persistence to
 * {@code emulator.properties}.
 */
public final class EmulatorConfig {

    // --- Core ---
    private String romDir = "";
    private String saveDir = "";
    private int fastForwardSpeed = 1;
    private int frameRate = 60;
    private boolean throttle = true;
    private String syncMode = "hybrid";
    private String hardware = "auto";
    private String bootRom = "";

    // --- Video ---
    private int scale = 1;
    private boolean integerScale = false;
    private boolean scanlines = false;
    private boolean vsync = false;
    private String filter = "none";
    private String palette = "dmg_default";
    private int frameskip = 0;
    private boolean renderStats = false;

    // --- Audio ---
    private boolean audioEnabled = true;
    private int sampleRate = 44100;
    private int audioBufferSize = 4096;
    private int latencyMs = 10;
    private boolean nullOutput = false;
    private String mixer = "";
    private boolean audioDebug = false;
    private boolean testTone = false;

    // --- Input ---
    private int repeatDelayMs = 0;
    private int repeatRateMs = 10;
    private int debounceMs = 0;
    private int minPressMs = 0;
    private double deadzone = 0.0;
    private boolean joystickEnabled = true;
    private String joystickDevice = "";
    private int joystickPollMs = 5;

    // --- Input Bindings (key name -> action) ---
    private final Map<String, String> keyBindings = new LinkedHashMap<>();

    // --- Joystick Mapping ---
    private final Map<String, String> joystickButtons = new LinkedHashMap<>();
    private String axisX = "x";
    private String axisY = "y";
    private boolean axisXInverted = false;
    private boolean axisYInverted = false;
    private boolean povEnabled = true;

    // --- Logging ---
    private boolean loggingConsole = false;
    private String loggingFile = "";
    private String levelRoot = "OFF";
    private String levelModel = "OFF";
    private String levelCpu = "OFF";
    private String levelMemory = "OFF";
    private String levelGraphics = "OFF";
    private String levelView = "OFF";
    private String levelController = "OFF";

    // --- Debug ---
    private boolean ppuTrace = false;
    private boolean timerTrace = false;
    private boolean cpuTrace = false;

    // --- UI ---
    private String theme = "light";

    public EmulatorConfig() {
        // Default key bindings
        keyBindings.put("UP", "up");
        keyBindings.put("DOWN", "down");
        keyBindings.put("LEFT", "left");
        keyBindings.put("RIGHT", "right");
        keyBindings.put("Z", "a");
        keyBindings.put("X", "b");
        keyBindings.put("ENTER", "start");
        keyBindings.put("SPACE", "select");
        keyBindings.put("P", "pause");
        keyBindings.put("CTRL_R", "reset");
        keyBindings.put("F4", "vram");
        keyBindings.put("F5", "save0");
        keyBindings.put("F6", "save1");
        keyBindings.put("F7", "save2");
        keyBindings.put("F8", "load0");
        keyBindings.put("F9", "load1");
        keyBindings.put("F10", "load2");
        keyBindings.put("F11", "fullscreen");
        keyBindings.put("F12", "debug");

        // Default joystick button mappings
        joystickButtons.put("input.joystick.button.a", "A");
        joystickButtons.put("input.joystick.button.b", "B");
        joystickButtons.put("input.joystick.button.start", "Start");
        joystickButtons.put("input.joystick.button.select", "Select");
    }

    /** Deep-copy all fields from another config into this one. */
    public void copyFrom(EmulatorConfig other) {
        this.romDir = other.romDir;
        this.saveDir = other.saveDir;
        this.fastForwardSpeed = other.fastForwardSpeed;
        this.frameRate = other.frameRate;
        this.throttle = other.throttle;
        this.syncMode = other.syncMode;
        this.hardware = other.hardware;
        this.bootRom = other.bootRom;

        this.scale = other.scale;
        this.integerScale = other.integerScale;
        this.scanlines = other.scanlines;
        this.vsync = other.vsync;
        this.filter = other.filter;
        this.palette = other.palette;
        this.frameskip = other.frameskip;
        this.renderStats = other.renderStats;

        this.audioEnabled = other.audioEnabled;
        this.sampleRate = other.sampleRate;
        this.audioBufferSize = other.audioBufferSize;
        this.latencyMs = other.latencyMs;
        this.nullOutput = other.nullOutput;
        this.mixer = other.mixer;
        this.audioDebug = other.audioDebug;
        this.testTone = other.testTone;

        this.repeatDelayMs = other.repeatDelayMs;
        this.repeatRateMs = other.repeatRateMs;
        this.debounceMs = other.debounceMs;
        this.minPressMs = other.minPressMs;
        this.deadzone = other.deadzone;
        this.joystickEnabled = other.joystickEnabled;
        this.joystickDevice = other.joystickDevice;
        this.joystickPollMs = other.joystickPollMs;

        this.keyBindings.clear();
        this.keyBindings.putAll(other.keyBindings);

        this.joystickButtons.clear();
        this.joystickButtons.putAll(other.joystickButtons);
        this.axisX = other.axisX;
        this.axisY = other.axisY;
        this.axisXInverted = other.axisXInverted;
        this.axisYInverted = other.axisYInverted;
        this.povEnabled = other.povEnabled;

        this.loggingConsole = other.loggingConsole;
        this.loggingFile = other.loggingFile;
        this.levelRoot = other.levelRoot;
        this.levelModel = other.levelModel;
        this.levelCpu = other.levelCpu;
        this.levelMemory = other.levelMemory;
        this.levelGraphics = other.levelGraphics;
        this.levelView = other.levelView;
        this.levelController = other.levelController;

        this.ppuTrace = other.ppuTrace;
        this.timerTrace = other.timerTrace;
        this.cpuTrace = other.cpuTrace;

        this.theme = other.theme;
    }

    /** Export all settings to a Properties object (round-trip safe). */
    public Properties toProperties() {
        Properties p = new Properties();

        // Core
        p.setProperty("emulator.romDir", romDir);
        p.setProperty("emulator.saveDir", saveDir);
        p.setProperty("emulator.fastForwardSpeed", String.valueOf(fastForwardSpeed));
        p.setProperty("emulator.frameRate", String.valueOf(frameRate));
        p.setProperty("emulator.throttle", String.valueOf(throttle));
        p.setProperty("emulator.syncMode", syncMode);
        p.setProperty("emulator.hardware", hardware);
        p.setProperty("emulator.bootRom", bootRom);

        // Video
        p.setProperty("video.scale", String.valueOf(scale));
        p.setProperty("video.integerScale", String.valueOf(integerScale));
        p.setProperty("video.scanlines", String.valueOf(scanlines));
        p.setProperty("video.vsync", String.valueOf(vsync));
        p.setProperty("video.filter", filter);
        p.setProperty("video.palette", palette);
        p.setProperty("video.frameskip", String.valueOf(frameskip));
        p.setProperty("video.renderStats", String.valueOf(renderStats));

        // Audio
        p.setProperty("audio.enabled", String.valueOf(audioEnabled));
        p.setProperty("audio.sampleRate", String.valueOf(sampleRate));
        p.setProperty("audio.bufferSize", String.valueOf(audioBufferSize));
        p.setProperty("audio.latencyMs", String.valueOf(latencyMs));
        p.setProperty("gbc.audio.nullOutput", String.valueOf(nullOutput));
        p.setProperty("gbc.audio.mixer", mixer);
        p.setProperty("gbc.audio.debug", String.valueOf(audioDebug));
        p.setProperty("gbc.audio.testTone", String.valueOf(testTone));

        // Input
        p.setProperty("input.repeatDelayMs", String.valueOf(repeatDelayMs));
        p.setProperty("input.repeatRateMs", String.valueOf(repeatRateMs));
        p.setProperty("input.debounceMs", String.valueOf(debounceMs));
        p.setProperty("input.minPressMs", String.valueOf(minPressMs));
        p.setProperty("input.deadzone", String.valueOf(deadzone));
        p.setProperty("input.joystick.enabled", String.valueOf(joystickEnabled));
        p.setProperty("input.joystick.device", joystickDevice);
        p.setProperty("input.joystick.pollMs", String.valueOf(joystickPollMs));

        // Key bindings
        for (Map.Entry<String, String> entry : keyBindings.entrySet()) {
            p.setProperty("input.key." + entry.getKey(), entry.getValue());
        }

        // Joystick mapping
        for (Map.Entry<String, String> entry : joystickButtons.entrySet()) {
            p.setProperty(entry.getKey(), entry.getValue());
        }
        p.setProperty("input.joystick.axis.x", axisX);
        p.setProperty("input.joystick.axis.y", axisY);
        p.setProperty("input.joystick.axis.x.inverted", String.valueOf(axisXInverted));
        p.setProperty("input.joystick.axis.y.inverted", String.valueOf(axisYInverted));
        p.setProperty("input.joystick.pov", String.valueOf(povEnabled));

        // Logging
        p.setProperty("gbc.logging.console", String.valueOf(loggingConsole));
        p.setProperty("gbc.logging.file", loggingFile);
        p.setProperty("gbc.logging.level.root", levelRoot);
        p.setProperty("gbc.logging.level.model", levelModel);
        p.setProperty("gbc.logging.level.cpu", levelCpu);
        p.setProperty("gbc.logging.level.memory", levelMemory);
        p.setProperty("gbc.logging.level.graphics", levelGraphics);
        p.setProperty("gbc.logging.level.view", levelView);
        p.setProperty("gbc.logging.level.controller", levelController);

        // Debug
        p.setProperty("gbc.ppu.trace", String.valueOf(ppuTrace));
        p.setProperty("gbc.timer.trace", String.valueOf(timerTrace));
        p.setProperty("gbc.cpu.trace", String.valueOf(cpuTrace));

        // UI
        p.setProperty("ui.theme", theme);

        return p;
    }

    // ---- Getters / Setters ----

    public String getRomDir() { return romDir; }
    public void setRomDir(String romDir) { this.romDir = safe(romDir); }

    public String getSaveDir() { return saveDir; }
    public void setSaveDir(String saveDir) { this.saveDir = safe(saveDir); }

    public int getFastForwardSpeed() { return fastForwardSpeed; }
    public void setFastForwardSpeed(int fastForwardSpeed) { this.fastForwardSpeed = fastForwardSpeed; }

    public int getFrameRate() { return frameRate; }
    public void setFrameRate(int frameRate) { this.frameRate = frameRate; }

    public boolean isThrottle() { return throttle; }
    public void setThrottle(boolean throttle) { this.throttle = throttle; }

    public String getSyncMode() { return syncMode; }
    public void setSyncMode(String syncMode) { this.syncMode = safe(syncMode); }

    public String getHardware() { return hardware; }
    public void setHardware(String hardware) { this.hardware = safe(hardware); }

    public String getBootRom() { return bootRom; }
    public void setBootRom(String bootRom) { this.bootRom = safe(bootRom); }

    public int getScale() { return scale; }
    public void setScale(int scale) { this.scale = scale; }

    public boolean isIntegerScale() { return integerScale; }
    public void setIntegerScale(boolean integerScale) { this.integerScale = integerScale; }

    public boolean isScanlines() { return scanlines; }
    public void setScanlines(boolean scanlines) { this.scanlines = scanlines; }

    public boolean isVsync() { return vsync; }
    public void setVsync(boolean vsync) { this.vsync = vsync; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = safe(filter); }

    public String getPalette() { return palette; }
    public void setPalette(String palette) { this.palette = safe(palette); }

    public int getFrameskip() { return frameskip; }
    public void setFrameskip(int frameskip) { this.frameskip = frameskip; }

    public boolean isRenderStats() { return renderStats; }
    public void setRenderStats(boolean renderStats) { this.renderStats = renderStats; }

    public boolean isAudioEnabled() { return audioEnabled; }
    public void setAudioEnabled(boolean audioEnabled) { this.audioEnabled = audioEnabled; }

    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }

    public int getAudioBufferSize() { return audioBufferSize; }
    public void setAudioBufferSize(int audioBufferSize) { this.audioBufferSize = audioBufferSize; }

    public int getLatencyMs() { return latencyMs; }
    public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }

    public boolean isNullOutput() { return nullOutput; }
    public void setNullOutput(boolean nullOutput) { this.nullOutput = nullOutput; }

    public String getMixer() { return mixer; }
    public void setMixer(String mixer) { this.mixer = safe(mixer); }

    public boolean isAudioDebug() { return audioDebug; }
    public void setAudioDebug(boolean audioDebug) { this.audioDebug = audioDebug; }

    public boolean isTestTone() { return testTone; }
    public void setTestTone(boolean testTone) { this.testTone = testTone; }

    public int getRepeatDelayMs() { return repeatDelayMs; }
    public void setRepeatDelayMs(int repeatDelayMs) { this.repeatDelayMs = repeatDelayMs; }

    public int getRepeatRateMs() { return repeatRateMs; }
    public void setRepeatRateMs(int repeatRateMs) { this.repeatRateMs = repeatRateMs; }

    public int getDebounceMs() { return debounceMs; }
    public void setDebounceMs(int debounceMs) { this.debounceMs = debounceMs; }

    public int getMinPressMs() { return minPressMs; }
    public void setMinPressMs(int minPressMs) { this.minPressMs = minPressMs; }

    public double getDeadzone() { return deadzone; }
    public void setDeadzone(double deadzone) { this.deadzone = deadzone; }

    public boolean isJoystickEnabled() { return joystickEnabled; }
    public void setJoystickEnabled(boolean joystickEnabled) { this.joystickEnabled = joystickEnabled; }

    public String getJoystickDevice() { return joystickDevice; }
    public void setJoystickDevice(String joystickDevice) { this.joystickDevice = safe(joystickDevice); }

    public int getJoystickPollMs() { return joystickPollMs; }
    public void setJoystickPollMs(int joystickPollMs) { this.joystickPollMs = joystickPollMs; }

    public Map<String, String> getKeyBindings() { return keyBindings; }

    public Map<String, String> getJoystickButtons() { return joystickButtons; }

    public String getAxisX() { return axisX; }
    public void setAxisX(String axisX) { this.axisX = safe(axisX); }

    public String getAxisY() { return axisY; }
    public void setAxisY(String axisY) { this.axisY = safe(axisY); }

    public boolean isAxisXInverted() { return axisXInverted; }
    public void setAxisXInverted(boolean axisXInverted) { this.axisXInverted = axisXInverted; }

    public boolean isAxisYInverted() { return axisYInverted; }
    public void setAxisYInverted(boolean axisYInverted) { this.axisYInverted = axisYInverted; }

    public boolean isPovEnabled() { return povEnabled; }
    public void setPovEnabled(boolean povEnabled) { this.povEnabled = povEnabled; }

    public boolean isLoggingConsole() { return loggingConsole; }
    public void setLoggingConsole(boolean loggingConsole) { this.loggingConsole = loggingConsole; }

    public String getLoggingFile() { return loggingFile; }
    public void setLoggingFile(String loggingFile) { this.loggingFile = safe(loggingFile); }

    public String getLevelRoot() { return levelRoot; }
    public void setLevelRoot(String levelRoot) { this.levelRoot = safe(levelRoot); }

    public String getLevelModel() { return levelModel; }
    public void setLevelModel(String levelModel) { this.levelModel = safe(levelModel); }

    public String getLevelCpu() { return levelCpu; }
    public void setLevelCpu(String levelCpu) { this.levelCpu = safe(levelCpu); }

    public String getLevelMemory() { return levelMemory; }
    public void setLevelMemory(String levelMemory) { this.levelMemory = safe(levelMemory); }

    public String getLevelGraphics() { return levelGraphics; }
    public void setLevelGraphics(String levelGraphics) { this.levelGraphics = safe(levelGraphics); }

    public String getLevelView() { return levelView; }
    public void setLevelView(String levelView) { this.levelView = safe(levelView); }

    public String getLevelController() { return levelController; }
    public void setLevelController(String levelController) { this.levelController = safe(levelController); }

    public boolean isPpuTrace() { return ppuTrace; }
    public void setPpuTrace(boolean ppuTrace) { this.ppuTrace = ppuTrace; }

    public boolean isTimerTrace() { return timerTrace; }
    public void setTimerTrace(boolean timerTrace) { this.timerTrace = timerTrace; }

    public boolean isCpuTrace() { return cpuTrace; }
    public void setCpuTrace(boolean cpuTrace) { this.cpuTrace = cpuTrace; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = safe(theme); }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
