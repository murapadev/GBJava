package gbc.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import gbc.controller.EmulatorActions;
import gbc.view.EmulatorView.ColorFilter;
import net.miginfocom.swing.MigLayout;

public class SettingsDialog extends JDialog {

    private static final String CONFIG_FILE = "emulator.properties";

    private final EmulatorWindow window;
    private final EmulatorView emulatorView;
    private EmulatorActions controller;

    private final List<SettingEntry> entries = new ArrayList<>();
    private final Map<String, BindingEntry> keyBindings = new LinkedHashMap<>();
    private final Map<String, String> defaultJoystickBindings = new LinkedHashMap<>();
    private final Properties defaultProperties = new Properties();

    private final JLabel restartHint = new JLabel("Some changes require restart to take effect.");
    private final Path profilesDir = Path.of("profiles");

    private JComboBox<String> profilesCombo;
    private JComboBox<String> mixerCombo;
    private JComboBox<String> joystickCombo;
    private final Map<String, JTextField> joystickBindings = new LinkedHashMap<>();
    private JLabel rebindHint;
    private String rebindAction;
    private KeyEventDispatcher rebindDispatcher;

    public SettingsDialog(EmulatorWindow window, EmulatorView emulatorView) {
        super(window, "Settings", false);
        this.window = Objects.requireNonNull(window, "window");
        this.emulatorView = Objects.requireNonNull(emulatorView, "emulatorView");
        buildUi();
        captureDefaultProperties();
        loadFromSystemProperties();
        pack();
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(window);
    }

    public void setController(EmulatorActions controller) {
        this.controller = controller;
    }

    public void showDialog() {
        loadFromSystemProperties();
        setVisible(true);
        toFront();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible) {
            rebindAction = null;
            uninstallRebindDispatcher();
        }
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Core", buildCoreTab());
        tabs.addTab("Video", buildVideoTab());
        tabs.addTab("Audio", buildAudioTab());
        tabs.addTab("Input", buildInputTab());
        tabs.addTab("Logging", buildLoggingTab());
        tabs.addTab("Debug", buildDebugTab());
        tabs.addTab("Profiles", buildProfilesTab());

        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        restartHint.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        footer.add(restartHint, BorderLayout.WEST);

        JPanel buttons = new JPanel(new MigLayout("insets 0", "[]8[]8[]8[]", "[]"));
        JButton applyButton = new JButton("Apply", IconFactory.apply());
        JButton saveButton = new JButton("Save", IconFactory.save());
        JButton closeButton = new JButton("Close", IconFactory.close());
        JButton defaultsButton = new JButton("Restore Defaults", IconFactory.restore());

        applyButton.addActionListener(e -> applySettings(false));
        saveButton.addActionListener(e -> applySettings(true));
        closeButton.addActionListener(e -> setVisible(false));
        defaultsButton.addActionListener(e -> restoreDefaults());

        buttons.add(defaultsButton);
        buttons.add(applyButton);
        buttons.add(saveButton);
        buttons.add(closeButton);

        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildCoreTab() {
        JPanel panel = createFormPanel();
        addText(panel, "ROM Directory", "emulator.romDir");
        addText(panel, "Save Directory", "emulator.saveDir");
        addInteger(panel, "Frame Rate", "emulator.frameRate", 30, 240, 1);
        addInteger(panel, "Fast Forward Speed", "emulator.fastForwardSpeed", 1, 8, 1);
        addCheckbox(panel, "Throttle to Frame Rate", "emulator.throttle");
        addCombo(panel, "Sync Mode", "emulator.syncMode", new String[] { "hybrid", "auto", "video", "audio", "none" });
        addCombo(panel, "Hardware Mode", "emulator.hardware",
                new String[] { "auto", "dmg", "dmg0", "mgb", "sgb", "sgb2", "cgb" });
        addText(panel, "Boot ROM Path", "emulator.bootRom");
        addCombo(panel, "Theme", "ui.theme", new String[] { "light", "dark" });
        return wrapWithPadding(panel);
    }

    private JPanel buildVideoTab() {
        JPanel panel = createFormPanel();
        addInteger(panel, "Scale", "video.scale", 1, 8, 1);
        addCheckbox(panel, "Integer Scale", "video.integerScale");
        addCheckbox(panel, "Scanlines", "video.scanlines");
        addCheckbox(panel, "VSync", "video.vsync");
        addCombo(panel, "Scaling Filter", "video.filter", new String[] { "none", "nearest", "linear" });
        addCombo(panel, "Palette", "video.palette", new String[] { "dmg_default", "dmg_green", "custom" });
        addInteger(panel, "Frame Skip", "video.frameskip", 0, 5, 1);
        addCheckbox(panel, "Render Stats", "video.renderStats");
        return wrapWithPadding(panel);
    }

    private JPanel buildAudioTab() {
        JPanel panel = createFormPanel();
        addCheckbox(panel, "Audio Enabled", "audio.enabled");
        addInteger(panel, "Sample Rate", "audio.sampleRate", 8000, 96000, 1000);
        addInteger(panel, "Buffer Size", "audio.bufferSize", 128, 8192, 128);
        addInteger(panel, "Latency (ms)", "audio.latencyMs", 10, 250, 5);
        addCheckbox(panel, "Null Output", "gbc.audio.nullOutput");
        mixerCombo = new JComboBox<>();
        mixerCombo.setPreferredSize(new Dimension(260, 26));
        addRow(panel, "Output Device", mixerCombo);
        JButton testButton = new JButton("Test Output");
        testButton.addActionListener(e -> playTestTone());
        addRow(panel, " ", testButton);
        addCheckbox(panel, "Audio Debug Logs", "gbc.audio.debug");
        addCheckbox(panel, "Test Tone", "gbc.audio.testTone");
        return wrapWithPadding(panel);
    }

    private JPanel buildInputTab() {
        JPanel panel = createFormPanel();
        addInteger(panel, "Repeat Delay (ms)", "input.repeatDelayMs", 0, 1000, 10);
        addInteger(panel, "Repeat Rate (ms)", "input.repeatRateMs", 10, 500, 5);
        addInteger(panel, "Debounce (ms)", "input.debounceMs", 0, 100, 1);
        addInteger(panel, "Min Press (ms)", "input.minPressMs", 0, 100, 1);
        addDecimal(panel, "Deadzone", "input.deadzone", 0.0, 0.95, 0.01);
        addCheckbox(panel, "Joystick Enabled", "input.joystick.enabled");
        joystickCombo = new JComboBox<>();
        joystickCombo.setPreferredSize(new Dimension(260, 26));
        addRow(panel, "Joystick Device", joystickCombo);
        JButton refreshJoy = new JButton("Refresh");
        refreshJoy.addActionListener(e -> loadJoysticks());
        addRow(panel, " ", refreshJoy);

        JPanel joyMap = createFormPanel();
        joyMap.setBorder(BorderFactory.createTitledBorder("Joystick Mapping"));
        addJoystickBinding(joyMap, "Button A", "input.joystick.button.a", "0");
        addJoystickBinding(joyMap, "Button B", "input.joystick.button.b", "1");
        addJoystickBinding(joyMap, "Start", "input.joystick.button.start", "7");
        addJoystickBinding(joyMap, "Select", "input.joystick.button.select", "6");
        addJoystickBinding(joyMap, "Axis X", "input.joystick.axis.x", "x");
        addJoystickBinding(joyMap, "Axis Y", "input.joystick.axis.y", "y");
        addCheckbox(joyMap, "Use POV/D-Pad", "input.joystick.pov");

        JPanel bindings = createFormPanel();
        bindings.setBorder(BorderFactory.createTitledBorder("Key Bindings"));
        addKeyBinding(bindings, "Up", "up", "UP");
        addKeyBinding(bindings, "Down", "down", "DOWN");
        addKeyBinding(bindings, "Left", "left", "LEFT");
        addKeyBinding(bindings, "Right", "right", "RIGHT");
        addKeyBinding(bindings, "A Button", "a", "Z");
        addKeyBinding(bindings, "B Button", "b", "X");
        addKeyBinding(bindings, "Start", "start", "ENTER");
        addKeyBinding(bindings, "Select", "select", "SPACE");
        addKeyBinding(bindings, "Pause", "pause", "P");
        addKeyBinding(bindings, "Reset", "reset", "CTRL_R");
        addKeyBinding(bindings, "Debug View", "debug", "F12");
        addKeyBinding(bindings, "VRAM Viewer", "vram", "F4");
        addKeyBinding(bindings, "Fullscreen", "fullscreen", "F11");
        addKeyBinding(bindings, "Save Slot 0", "save0", "F5");
        addKeyBinding(bindings, "Save Slot 1", "save1", "F6");
        addKeyBinding(bindings, "Save Slot 2", "save2", "F7");
        addKeyBinding(bindings, "Load Slot 0", "load0", "F8");
        addKeyBinding(bindings, "Load Slot 1", "load1", "F9");
        addKeyBinding(bindings, "Load Slot 2", "load2", "F10");

        rebindHint = new JLabel("Click Rebind, then press the desired key.");
        rebindHint.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(wrapWithPadding(panel), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.add(wrapWithPadding(joyMap), BorderLayout.NORTH);
        center.add(wrapWithPadding(bindings), BorderLayout.CENTER);
        wrapper.add(center, BorderLayout.CENTER);
        wrapper.add(rebindHint, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildLoggingTab() {
        JPanel panel = createFormPanel();
        addCheckbox(panel, "Console Logging", "gbc.logging.console");
        addText(panel, "Log File", "gbc.logging.file");
        String[] levels = { "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST" };
        addCombo(panel, "Root Level", "gbc.logging.level.root", levels);
        addCombo(panel, "Model Level", "gbc.logging.level.model", levels);
        addCombo(panel, "CPU Level", "gbc.logging.level.cpu", levels);
        addCombo(panel, "Memory Level", "gbc.logging.level.memory", levels);
        addCombo(panel, "Graphics Level", "gbc.logging.level.graphics", levels);
        addCombo(panel, "View Level", "gbc.logging.level.view", levels);
        addCombo(panel, "Controller Level", "gbc.logging.level.controller", levels);
        return wrapWithPadding(panel);
    }

    private JPanel buildDebugTab() {
        JPanel panel = createFormPanel();
        addCheckbox(panel, "PPU Trace", "gbc.ppu.trace");
        addCheckbox(panel, "Timer Trace", "gbc.timer.trace");
        addCheckbox(panel, "CPU Trace", "gbc.cpu.trace");
        addCheckbox(panel, "Audio Debug", "gbc.audio.debug");
        addCheckbox(panel, "Audio Test Tone", "gbc.audio.testTone");
        return wrapWithPadding(panel);
    }

    private JPanel buildProfilesTab() {
        JPanel panel = createFormPanel();
        profilesCombo = new JComboBox<>();
        profilesCombo.setPreferredSize(new Dimension(220, 26));

        JButton loadButton = new JButton("Load");
        JButton saveButton = new JButton("Save");
        JButton deleteButton = new JButton("Delete");
        JButton refreshButton = new JButton("Refresh");

        loadButton.addActionListener(e -> loadSelectedProfile());
        saveButton.addActionListener(e -> saveProfilePrompt());
        deleteButton.addActionListener(e -> deleteSelectedProfile());
        refreshButton.addActionListener(e -> reloadProfiles());

        addRow(panel, "Profile", profilesCombo);

        JPanel row = new JPanel(new MigLayout("insets 0", "[]8[]8[]8[]", "[]"));
        row.add(loadButton);
        row.add(saveButton);
        row.add(deleteButton);
        row.add(refreshButton);
        panel.add(new JLabel(" "), "alignx right");
        panel.add(row, "wrap");

        return wrapWithPadding(panel);
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, fillx", "[right]12[grow,fill]", "[]"));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return panel;
    }

    private JPanel wrapWithPadding(JPanel panel) {
        JPanel wrapper = new JPanel(new MigLayout("insets 0, fill", "[grow]", "[grow]"));
        wrapper.add(panel, "grow");
        return wrapper;
    }

    private void addText(JPanel panel, String label, String key) {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(260, 26));
        entries.add(new SettingEntry(key, field, SettingType.TEXT));
        addRow(panel, label, field);
    }

    private void addInteger(JPanel panel, String label, String key, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        spinner.setPreferredSize(new Dimension(120, 26));
        entries.add(new SettingEntry(key, spinner, SettingType.INTEGER));
        addRow(panel, label, spinner);
    }

    private void addDecimal(JPanel panel, String label, String key, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        spinner.setPreferredSize(new Dimension(120, 26));
        entries.add(new SettingEntry(key, spinner, SettingType.DECIMAL));
        addRow(panel, label, spinner);
    }

    private void addCheckbox(JPanel panel, String label, String key) {
        JCheckBox box = new JCheckBox();
        entries.add(new SettingEntry(key, box, SettingType.BOOLEAN));
        addRow(panel, label, box);
    }

    private void addCombo(JPanel panel, String label, String key, String[] options) {
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setPreferredSize(new Dimension(160, 26));
        entries.add(new SettingEntry(key, combo, SettingType.CHOICE));
        addRow(panel, label, combo);
    }

    private void addKeyBinding(JPanel panel, String label, String action, String defaultKey) {
        JTextField field = new JTextField(defaultKey);
        field.setPreferredSize(new Dimension(120, 26));
        JButton rebindButton = new JButton("Rebind");
        rebindButton.addActionListener(e -> beginRebind(action));

        JPanel row = new JPanel();
        row.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        row.add(field);
        row.add(rebindButton);

        keyBindings.put(action, new BindingEntry(action, field, rebindButton, defaultKey));
        addRow(panel, label, row);
    }

    private void addRow(JPanel panel, String label, java.awt.Component component) {
        panel.add(new JLabel(label), "alignx right");
        panel.add(component, "growx, wrap");
    }

    private void addJoystickBinding(JPanel panel, String label, String key, String defaultValue) {
        JTextField field = new JTextField(defaultValue);
        field.setPreferredSize(new Dimension(120, 26));
        JButton detect = new JButton("Detect");
        detect.addActionListener(e -> detectJoystickBinding(key, field));
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        row.add(field);
        row.add(detect);
        joystickBindings.put(key, field);
        defaultJoystickBindings.put(key, defaultValue);
        addRow(panel, label, row);
    }

    private void loadFromSystemProperties() {
        Properties props = new Properties();
        props.putAll(loadConfigProperties());
        props.putAll(System.getProperties());
        loadFromProperties(props);
    }

    private void loadFromProperties(Properties props) {
        for (SettingEntry entry : entries) {
            entry.loadFrom(props);
        }
        loadKeyBindings(props);
        loadMixers(props);
        loadJoysticks(props);
        loadJoystickBindings(props);
        reloadProfiles();
    }

    private Properties loadConfigProperties() {
        Properties props = new Properties();
        Path configPath = Path.of(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException ignored) {
        }
        return props;
    }

    private void captureDefaultProperties() {
        defaultProperties.clear();
        for (SettingEntry entry : entries) {
            entry.applyTo(defaultProperties);
        }
        for (BindingEntry binding : keyBindings.values()) {
            String normalized = binding.defaultKey.toUpperCase().replace(' ', '_');
            defaultProperties.setProperty("input.key." + normalized, binding.action);
        }
        for (Map.Entry<String, String> entry : defaultJoystickBindings.entrySet()) {
            defaultProperties.setProperty(entry.getKey(), entry.getValue());
        }
        defaultProperties.setProperty("input.joystick.device", "");
        defaultProperties.setProperty("gbc.audio.mixer", "");
    }

    private void loadJoysticks() {
        loadJoysticks(System.getProperties());
    }

    private void loadJoysticks(Properties props) {
        if (joystickCombo == null) {
            return;
        }
        try {
            gbc.controller.input.JInputNativeLoader.loadIfNeeded();
        } catch (Throwable ignored) {
            joystickCombo.removeAllItems();
            joystickCombo.addItem("auto");
            return;
        }
        joystickCombo.removeAllItems();
        joystickCombo.addItem("auto");
        try {
            net.java.games.input.Controller[] controllers = net.java.games.input.ControllerEnvironment
                    .getDefaultEnvironment().getControllers();
            for (net.java.games.input.Controller c : controllers) {
                if (c == null) {
                    continue;
                }
                if (c.getType() == net.java.games.input.Controller.Type.GAMEPAD
                        || c.getType() == net.java.games.input.Controller.Type.STICK
                        || c.getType() == net.java.games.input.Controller.Type.WHEEL) {
                    joystickCombo.addItem(c.getName());
                }
            }
        } catch (Throwable ignored) {
        }
        String current = props.getProperty("input.joystick.device", "");
        if (current == null || current.isBlank()) {
            joystickCombo.setSelectedItem("auto");
            return;
        }
        for (int i = 0; i < joystickCombo.getItemCount(); i++) {
            if (current.equalsIgnoreCase(joystickCombo.getItemAt(i))) {
                joystickCombo.setSelectedIndex(i);
                return;
            }
        }
        joystickCombo.setSelectedItem("auto");
    }

    private void loadMixers(Properties props) {
        if (mixerCombo == null) {
            return;
        }
        mixerCombo.removeAllItems();
        mixerCombo.addItem("Auto (recommended)");
        mixerCombo.addItem("System Default");
        try {
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            for (Mixer.Info info : mixers) {
                if (supportsSourceDataLine(AudioSystem.getMixer(info))) {
                    mixerCombo.addItem(info.getName());
                }
            }
        } catch (Exception ignored) {
        }
        String current = props.getProperty("gbc.audio.mixer", "");
        if (current == null || current.isBlank()) {
            mixerCombo.setSelectedItem("Auto (recommended)");
            return;
        }
        if ("system".equalsIgnoreCase(current)) {
            mixerCombo.setSelectedItem("System Default");
            return;
        }
        for (int i = 0; i < mixerCombo.getItemCount(); i++) {
            if (current.equalsIgnoreCase(mixerCombo.getItemAt(i))) {
                mixerCombo.setSelectedIndex(i);
                return;
            }
        }
        mixerCombo.setSelectedItem("Auto (recommended)");
    }

    private void loadJoystickBindings(Properties props) {
        for (Map.Entry<String, JTextField> entry : joystickBindings.entrySet()) {
            String key = entry.getKey();
            String fallback = defaultJoystickBindings.getOrDefault(key, "");
            String value = props.getProperty(key, fallback);
            entry.getValue().setText(value);
        }
    }

    private void loadKeyBindings(Properties props) {
        for (BindingEntry binding : keyBindings.values()) {
            String action = binding.action;
            JTextField field = binding.field;
            String keyName = findKeyForAction(props, action);
            if (keyName != null) {
                field.setText(keyName);
            } else {
                field.setText(binding.defaultKey);
            }
        }
    }

    private String findKeyForAction(Properties props, String action) {
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.key.")) {
                if (action.equalsIgnoreCase(props.getProperty(key))) {
                    return key.substring("input.key.".length());
                }
            }
        }
        return null;
    }

    private void applySettings(boolean persist) {
        Properties props = System.getProperties();
        for (SettingEntry entry : entries) {
            entry.applyTo(props);
        }
        applyKeyBindings(props);
        applyMixerSelection(props);
        applyJoystickSelection(props);
        applyJoystickBindings(props);
        applyToRuntime();

        if (persist) {
            savePropertiesFile(props);
        }
    }

    private void applyJoystickSelection(Properties props) {
        if (joystickCombo == null) {
            return;
        }
        Object selected = joystickCombo.getSelectedItem();
        if (selected == null || "auto".equalsIgnoreCase(selected.toString())) {
            props.setProperty("input.joystick.device", "");
        } else {
            props.setProperty("input.joystick.device", selected.toString());
        }
    }

    private void applyJoystickBindings(Properties props) {
        for (Map.Entry<String, JTextField> entry : joystickBindings.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isEmpty()) {
                props.setProperty(entry.getKey(), value);
            } else {
                props.remove(entry.getKey());
            }
        }
    }

    private void applyMixerSelection(Properties props) {
        if (mixerCombo == null) {
            return;
        }
        Object selected = mixerCombo.getSelectedItem();
        if (selected == null) {
            props.setProperty("gbc.audio.mixer", "");
            return;
        }
        String text = selected.toString();
        if ("Auto (recommended)".equalsIgnoreCase(text)) {
            props.setProperty("gbc.audio.mixer", "");
            return;
        }
        if ("System Default".equalsIgnoreCase(text)) {
            props.setProperty("gbc.audio.mixer", "system");
        } else {
            props.setProperty("gbc.audio.mixer", text);
        }
    }

    private void playTestTone() {
        try {
            int bufferSize = Math.max(256, Integer.getInteger("audio.bufferSize", 1024));
            javax.sound.sampled.SourceDataLine line = openTestLine();
            javax.sound.sampled.AudioFormat format = line.getFormat();
            int sampleRate = (int) format.getSampleRate();

            line.start();

            int durationMs = 800;
            int frames = (int) ((durationMs / 1000.0) * sampleRate);
            byte[] data = new byte[frames * format.getFrameSize()];
            double freq = 880.0;

            if (format.getSampleSizeInBits() == 16) {
                for (int i = 0; i < frames; i++) {
                    double t = i / (double) sampleRate;
                    short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * 20000);
                    int frameOffset = i * format.getFrameSize();
                    for (int ch = 0; ch < format.getChannels(); ch++) {
                        int idx = frameOffset + ch * 2;
                        byte lo = (byte) (sample & 0xFF);
                        byte hi = (byte) ((sample >> 8) & 0xFF);
                        if (format.isBigEndian()) {
                            data[idx] = hi;
                            data[idx + 1] = lo;
                        } else {
                            data[idx] = lo;
                            data[idx + 1] = hi;
                        }
                    }
                }
            } else {
                boolean signed = format.getEncoding().toString().toUpperCase().contains("SIGNED");
                for (int i = 0; i < frames; i++) {
                    double t = i / (double) sampleRate;
                    int sample = (int) (Math.sin(2.0 * Math.PI * freq * t) * 100);
                    if (!signed) {
                        sample += 128;
                    }
                    int frameOffset = i * format.getFrameSize();
                    for (int ch = 0; ch < format.getChannels(); ch++) {
                        data[frameOffset + ch] = (byte) sample;
                    }
                }
            }

            line.write(data, 0, data.length);
            line.drain();
            line.stop();
            line.close();
        } catch (Exception e) {
            window.showMessage("Audio test failed: " + e.getMessage(), "Audio Test",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private javax.sound.sampled.SourceDataLine openTestLine() throws Exception {
        Object selected = mixerCombo != null ? mixerCombo.getSelectedItem() : null;
        javax.sound.sampled.Mixer mixer = null;
        if (selected != null
                && !"Auto (recommended)".equalsIgnoreCase(selected.toString())
                && !"System Default".equalsIgnoreCase(selected.toString())) {
            javax.sound.sampled.Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            for (javax.sound.sampled.Mixer.Info mix : mixers) {
                if (selected.toString().equalsIgnoreCase(mix.getName())) {
                    mixer = AudioSystem.getMixer(mix);
                    break;
                }
            }
        }

        if (mixer != null) {
            javax.sound.sampled.SourceDataLine line = openLineFromMixer(mixer);
            if (line != null) {
                return line;
            }
        }

        javax.sound.sampled.AudioFormat[] formats = new javax.sound.sampled.AudioFormat[] {
                new javax.sound.sampled.AudioFormat(44100, 16, 2, true, false),
                new javax.sound.sampled.AudioFormat(44100, 16, 2, true, true),
                new javax.sound.sampled.AudioFormat(44100, 16, 1, true, false),
                new javax.sound.sampled.AudioFormat(44100, 16, 1, true, true),
                new javax.sound.sampled.AudioFormat(48000, 16, 2, true, false),
                new javax.sound.sampled.AudioFormat(48000, 16, 2, true, true),
                new javax.sound.sampled.AudioFormat(48000, 16, 1, true, false),
                new javax.sound.sampled.AudioFormat(48000, 16, 1, true, true),
                new javax.sound.sampled.AudioFormat(44100, 8, 1, true, false),
                new javax.sound.sampled.AudioFormat(44100, 8, 1, false, false),
                new javax.sound.sampled.AudioFormat(22050, 8, 1, true, false)
        };

        Exception last = null;
        for (javax.sound.sampled.AudioFormat format : formats) {
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.SourceDataLine.class, format);
            try {
                javax.sound.sampled.SourceDataLine line;
                if (AudioSystem.isLineSupported(info)) {
                    line = (javax.sound.sampled.SourceDataLine) AudioSystem.getLine(info);
                } else {
                    continue;
                }
                int bufferSize = Math.max(256, Integer.getInteger("audio.bufferSize", 1024));
                line.open(format, bufferSize);
                return line;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("No supported audio output format found.");
    }

    private boolean supportsSourceDataLine(javax.sound.sampled.Mixer mixer) {
        if (mixer == null) {
            return false;
        }
        for (javax.sound.sampled.Line.Info info : mixer.getSourceLineInfo()) {
            if (info instanceof javax.sound.sampled.DataLine.Info dataInfo) {
                if (javax.sound.sampled.SourceDataLine.class.isAssignableFrom(dataInfo.getLineClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    private javax.sound.sampled.SourceDataLine openLineFromMixer(javax.sound.sampled.Mixer mixer) {
        for (javax.sound.sampled.Line.Info info : mixer.getSourceLineInfo()) {
            if (info instanceof javax.sound.sampled.DataLine.Info dataInfo) {
                if (!javax.sound.sampled.SourceDataLine.class.isAssignableFrom(dataInfo.getLineClass())) {
                    continue;
                }
                javax.sound.sampled.AudioFormat[] formats = dataInfo.getFormats();
                if (formats == null || formats.length == 0) {
                    continue;
                }
                for (javax.sound.sampled.AudioFormat format : formats) {
                    try {
                        javax.sound.sampled.SourceDataLine line = (javax.sound.sampled.SourceDataLine) mixer.getLine(
                                new javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine.class,
                                        format));
                        int bufferSize = Math.max(256, Integer.getInteger("audio.bufferSize", 1024));
                        line.open(format, bufferSize);
                        return line;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private void applyKeyBindings(Properties props) {
        clearExistingBindings(props, keyBindings.keySet());
        for (BindingEntry entry : keyBindings.values()) {
            String action = entry.action;
            String rawKey = entry.field.getText().trim().toUpperCase();
            if (rawKey.isEmpty()) {
                continue;
            }
            String normalized = rawKey.replace(' ', '_');
            props.setProperty("input.key." + normalized, action);
        }
    }

    private void clearExistingBindings(Properties props, Set<String> actions) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.key.")) {
                String value = props.getProperty(key, "");
                if (actions.contains(value)) {
                    keysToRemove.add(key);
                }
            }
        }
        for (String key : keysToRemove) {
            props.remove(key);
        }
    }

    private void applyToRuntime() {
        String scale = System.getProperty("video.scale");
        if (scale != null) {
            try {
                emulatorView.setScaleFactor(Integer.parseInt(scale));
            } catch (NumberFormatException ignored) {
            }
        }
        emulatorView.setMaintainAspectRatio(Boolean.parseBoolean(System.getProperty("video.integerScale", "true")));
        emulatorView.setShowScanlines(Boolean.parseBoolean(System.getProperty("video.scanlines", "false")));

        String filter = System.getProperty("video.filter", "none");
        emulatorView.setSmoothScaling("linear".equalsIgnoreCase(filter));

        String palette = System.getProperty("video.palette", "dmg_default");
        emulatorView.setColorFilter(mapPaletteToFilter(palette));

        SwingUtilities.invokeLater(() -> window.refreshUiState(true));
    }

    private ColorFilter mapPaletteToFilter(String palette) {
        return switch (palette.toLowerCase()) {
            case "dmg_green" -> ColorFilter.GREEN_MONOCHROME;
            case "custom" -> ColorFilter.SEPIA;
            default -> ColorFilter.NONE;
        };
    }

    private void savePropertiesFile(Properties props) {
        Path path = Path.of(CONFIG_FILE);
        StringBuilder out = new StringBuilder();
        out.append("# Emulator configuration (generated by UI)\n");
        writeSection(out, "Core", List.of(
                "emulator.romDir",
                "emulator.saveDir",
                "emulator.fastForwardSpeed",
                "emulator.frameRate",
                "emulator.throttle",
                "emulator.syncMode",
                "emulator.hardware",
                "emulator.bootRom"), props);
        writeSection(out, "Video", List.of(
                "video.scale",
                "video.integerScale",
                "video.scanlines",
                "video.vsync",
                "video.filter",
                "video.palette",
                "video.frameskip",
                "video.renderStats"), props);
        writeSection(out, "Audio", List.of(
                "audio.enabled",
                "audio.sampleRate",
                "audio.bufferSize",
                "audio.latencyMs",
                "gbc.audio.nullOutput",
                "gbc.audio.mixer",
                "gbc.audio.debug",
                "gbc.audio.testTone"), props);
        writeSection(out, "Input", List.of(
                "input.repeatDelayMs",
                "input.repeatRateMs",
                "input.debounceMs",
                "input.minPressMs",
                "input.deadzone",
                "input.joystick.enabled",
                "input.joystick.device",
                "input.joystick.pollMs"), props);
        writeBindings(out, props);
        writeJoystickMappings(out, props);
        writeSection(out, "Logging", List.of(
                "gbc.logging.console",
                "gbc.logging.file",
                "gbc.logging.level.root",
                "gbc.logging.level.model",
                "gbc.logging.level.cpu",
                "gbc.logging.level.memory",
                "gbc.logging.level.graphics",
                "gbc.logging.level.view",
                "gbc.logging.level.controller"), props);
        writeSection(out, "Debug", List.of(
                "gbc.ppu.trace",
                "gbc.timer.trace",
                "gbc.cpu.trace",
                "gbc.audio.debug",
                "gbc.audio.testTone"), props);
        writeSection(out, "UI", List.of(
                "ui.theme"), props);

        try {
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            window.showMessage("Failed to save settings: " + e.getMessage(), "Settings",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeSection(StringBuilder out, String title, List<String> keys, Properties props) {
        out.append("\n# --- ").append(title).append(" ---\n");
        for (String key : keys) {
            String value = props.getProperty(key);
            if (value == null) {
                continue;
            }
            out.append(key).append("=").append(value).append("\n");
        }
    }

    private void writeBindings(StringBuilder out, Properties props) {
        out.append("\n# --- Input Bindings ---\n");
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.key.")) {
                out.append(key).append("=").append(props.getProperty(key)).append("\n");
            }
        }
    }

    private void writeJoystickMappings(StringBuilder out, Properties props) {
        out.append("\n# --- Joystick Mapping ---\n");
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("input.joystick.button.") || key.startsWith("input.joystick.axis.")) {
                out.append(key).append("=").append(props.getProperty(key)).append("\n");
            }
        }
        if (props.getProperty("input.joystick.pov") != null) {
            out.append("input.joystick.pov=").append(props.getProperty("input.joystick.pov")).append("\n");
        }
    }

    private void restoreDefaults() {
        // Keep a deterministic UI baseline independent from the user's saved file.
        Properties defaults = new Properties();
        defaults.putAll(defaultProperties);
        for (SettingEntry entry : entries) {
            entry.loadFrom(defaults);
        }
        loadKeyBindings(defaults);
        loadMixers(defaults);
        loadJoysticks(defaults);
        loadJoystickBindings(defaults);
    }

    private void detectJoystickBinding(String key, JTextField field) {
        String selectedDevice = "";
        if (joystickCombo != null && joystickCombo.getSelectedItem() != null) {
            selectedDevice = joystickCombo.getSelectedItem().toString();
        }

        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        final String selectedHint = selectedDevice;
        javax.swing.SwingWorker<String, Void> worker = new javax.swing.SwingWorker<>() {
            @Override
            protected String doInBackground() {
                gbc.controller.input.JInputNativeLoader.loadIfNeeded();
                net.java.games.input.Controller controller = resolveSelectedJoystick(selectedHint);
                if (controller == null) {
                    return null;
                }
                return pollForJoystickInput(controller, key);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    String detected = get();
                    if (detected != null) {
                        field.setText(detected);
                    } else {
                        window.showMessage("No input detected. Try again and move/press the control.",
                                "Joystick", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    window.showMessage("Joystick detection failed: " + e.getMessage(),
                            "Joystick", javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private net.java.games.input.Controller resolveSelectedJoystick() {
        Object selected = joystickCombo != null ? joystickCombo.getSelectedItem() : null;
        String hint = selected != null ? selected.toString() : "";
        return resolveSelectedJoystick(hint);
    }

    private net.java.games.input.Controller resolveSelectedJoystick(String hint) {
        net.java.games.input.Controller[] controllers = net.java.games.input.ControllerEnvironment
                .getDefaultEnvironment().getControllers();
        for (net.java.games.input.Controller c : controllers) {
            if (c == null) {
                continue;
            }
            if (c.getType() == net.java.games.input.Controller.Type.GAMEPAD
                    || c.getType() == net.java.games.input.Controller.Type.STICK
                    || c.getType() == net.java.games.input.Controller.Type.WHEEL) {
                if (hint == null || hint.isBlank() || "auto".equalsIgnoreCase(hint)) {
                    return c;
                }
                if (c.getName().equalsIgnoreCase(hint)) {
                    return c;
                }
            }
        }
        return null;
    }

    private String pollForJoystickInput(net.java.games.input.Controller controller, String key) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!controller.poll()) {
                break;
            }
            for (net.java.games.input.Component comp : controller.getComponents()) {
                float value = comp.getPollData();
                if (isButtonKey(key)) {
                    if (comp.getIdentifier() instanceof net.java.games.input.Component.Identifier.Button
                            && value > 0.5f) {
                        return comp.getIdentifier().getName();
                    }
                } else if (key.contains("axis")) {
                    if (comp.getIdentifier() instanceof net.java.games.input.Component.Identifier.Axis) {
                        if (Math.abs(value) > 0.6f) {
                            return comp.getIdentifier().getName();
                        }
                    }
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private boolean isButtonKey(String key) {
        return key.contains("button");
    }

    private void beginRebind(String action) {
        rebindAction = action;
        if (rebindHint != null) {
            rebindHint.setText("Press a key to bind for: " + action);
        }
        installRebindDispatcher();
    }

    private void installRebindDispatcher() {
        if (rebindDispatcher != null) {
            return;
        }
        rebindDispatcher = event -> {
            if (rebindAction == null) {
                return false;
            }
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            String keyName = formatBindingKey(event);
            if (keyName == null) {
                return true;
            }
            BindingEntry entry = keyBindings.get(rebindAction);
            if (entry != null) {
                entry.field.setText(keyName);
            }
            rebindAction = null;
            if (rebindHint != null) {
                rebindHint.setText("Click Rebind, then press the desired key.");
            }
            uninstallRebindDispatcher();
            return true;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(rebindDispatcher);
    }

    private String formatBindingKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT
                || keyCode == KeyEvent.VK_CONTROL
                || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_META) {
            return null;
        }
        String keyName = KeyEvent.getKeyText(keyCode).toUpperCase().replace(' ', '_');
        StringBuilder sb = new StringBuilder();
        int modifiers = event.getModifiersEx();
        if ((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("CTRL_");
        }
        if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0) {
            sb.append("ALT_");
        }
        if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("SHIFT_");
        }
        if ((modifiers & KeyEvent.META_DOWN_MASK) != 0) {
            sb.append("META_");
        }
        sb.append(keyName);
        return sb.toString();
    }

    private void uninstallRebindDispatcher() {
        if (rebindDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(rebindDispatcher);
            rebindDispatcher = null;
        }
    }

    private void reloadProfiles() {
        if (profilesCombo == null) {
            return;
        }
        profilesCombo.removeAllItems();
        try {
            Files.createDirectories(profilesDir);
            Files.list(profilesDir)
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted()
                    .forEach(path -> profilesCombo.addItem(stripProfileExtension(path.getFileName().toString())));
        } catch (IOException ignored) {
        }
    }

    private void saveProfilePrompt() {
        String name = javax.swing.JOptionPane.showInputDialog(this, "Profile name:", "Save Profile",
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        saveProfile(name.trim());
    }

    private void saveProfile(String name) {
        Properties props = new Properties();
        for (SettingEntry entry : entries) {
            entry.applyTo(props);
        }
        applyKeyBindings(props);
        applyMixerSelection(props);
        applyJoystickSelection(props);
        applyJoystickBindings(props);
        Path file = profilesDir.resolve(name + ".properties");
        try {
            Files.createDirectories(profilesDir);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "GBJava profile: " + name);
            }
            reloadProfiles();
            profilesCombo.setSelectedItem(name);
        } catch (IOException e) {
            window.showMessage("Failed to save profile: " + e.getMessage(), "Profiles",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSelectedProfile() {
        if (profilesCombo == null) {
            return;
        }
        Object selected = profilesCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        loadProfile(selected.toString());
    }

    private void loadProfile(String name) {
        Path file = profilesDir.resolve(name + ".properties");
        if (!Files.exists(file)) {
            window.showMessage("Profile not found: " + name, "Profiles", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            window.showMessage("Failed to load profile: " + e.getMessage(), "Profiles",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (String key : props.stringPropertyNames()) {
            System.setProperty(key, props.getProperty(key));
        }
        loadFromSystemProperties();
        applyToRuntime();
    }

    private void deleteSelectedProfile() {
        if (profilesCombo == null) {
            return;
        }
        Object selected = profilesCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        Path file = profilesDir.resolve(selected.toString() + ".properties");
        try {
            Files.deleteIfExists(file);
            reloadProfiles();
        } catch (IOException e) {
            window.showMessage("Failed to delete profile: " + e.getMessage(), "Profiles",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private String stripProfileExtension(String fileName) {
        if (fileName.endsWith(".properties")) {
            return fileName.substring(0, fileName.length() - ".properties".length());
        }
        return fileName;
    }

    private enum SettingType {
        TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        CHOICE
    }

    private static final class SettingEntry {
        private final String key;
        private final java.awt.Component component;
        private final SettingType type;

        private SettingEntry(String key, java.awt.Component component, SettingType type) {
            this.key = key;
            this.component = component;
            this.type = type;
        }

        void loadFrom(Properties props) {
            String value = props.getProperty(key);
            switch (type) {
                case TEXT -> ((JTextField) component).setText(value != null ? value : "");
                case INTEGER -> {
                    int parsed = parseInt(value, ((Number) ((JSpinner) component).getValue()).intValue());
                    ((JSpinner) component).setValue(parsed);
                }
                case DECIMAL -> {
                    double parsed = parseDouble(value, ((Number) ((JSpinner) component).getValue()).doubleValue());
                    ((JSpinner) component).setValue(parsed);
                }
                case BOOLEAN -> ((JCheckBox) component).setSelected(Boolean.parseBoolean(value));
                case CHOICE -> {
                    if (value != null) {
                        ((JComboBox<?>) component).setSelectedItem(value);
                    }
                }
            }
        }

        void applyTo(Properties props) {
            switch (type) {
                case TEXT -> props.setProperty(key, ((JTextField) component).getText().trim());
                case INTEGER ->
                    props.setProperty(key, String.valueOf(((Number) ((JSpinner) component).getValue()).intValue()));
                case DECIMAL ->
                    props.setProperty(key, String.valueOf(((Number) ((JSpinner) component).getValue()).doubleValue()));
                case BOOLEAN -> props.setProperty(key, String.valueOf(((JCheckBox) component).isSelected()));
                case CHOICE ->
                    props.setProperty(key, Objects.toString(((JComboBox<?>) component).getSelectedItem(), ""));
            }
        }

        private int parseInt(String value, int fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private double parseDouble(String value, double fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    private static final class BindingEntry {
        private final String action;
        private final JTextField field;
        private final JButton button;
        private final String defaultKey;

        private BindingEntry(String action, JTextField field, JButton button, String defaultKey) {
            this.action = action;
            this.field = field;
            this.button = button;
            this.defaultKey = defaultKey;
        }
    }
}
