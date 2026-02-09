package gbc.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import gbc.controller.EmulatorActions;
import gbc.controller.config.AppConfig;
import gbc.controller.config.ConfigSerializer;
import gbc.controller.config.EmulatorConfig;
import net.miginfocom.swing.MigLayout;

public class SettingsDialog extends JDialog {

    private final EmulatorWindow window;
    private final EmulatorView emulatorView;
    private EmulatorActions controller;

    private final List<SettingEntry> entries = new ArrayList<>();
    private final Map<String, BindingEntry> keyBindings = new LinkedHashMap<>();
    private final Map<String, String> defaultJoystickBindings = new LinkedHashMap<>();
    private final Properties defaultProperties = new Properties();

    private final Path profilesDir = Path.of("profiles");

    private JComboBox<String> profilesCombo;
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
        tabs.addTab("General", wrapInScroll(buildGeneralTab()));
        tabs.addTab("Audio", wrapInScroll(buildAudioTab()));
        tabs.addTab("Controls", wrapInScroll(buildControlsTab()));
        tabs.addTab("Advanced", wrapInScroll(buildAdvancedTab()));

        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));

        JPanel buttons = new JPanel(new MigLayout("insets 0", "[]8[]8[]", "[]"));
        JButton defaultsButton = new JButton("Defaults", IconFactory.restore());
        JButton saveButton = new JButton("Save", IconFactory.save());
        JButton closeButton = new JButton("Close", IconFactory.close());

        defaultsButton.addActionListener(e -> restoreDefaults());
        saveButton.addActionListener(e -> applySettings(true));
        closeButton.addActionListener(e -> setVisible(false));

        buttons.add(defaultsButton);
        buttons.add(saveButton);
        buttons.add(closeButton);

        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private JScrollPane wrapInScroll(JPanel panel) {
        JScrollPane sp = new JScrollPane(panel);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(12);
        return sp;
    }

    /** General tab: Core settings + Video settings merged. */
    private JPanel buildGeneralTab() {
        JPanel panel = createFormPanel();

        // Core
        addText(panel, "ROM Directory", "emulator.romDir");
        addText(panel, "Save Directory", "emulator.saveDir");
        addCombo(panel, "Hardware", "emulator.hardware",
                new String[] { "auto", "dmg", "dmg0", "mgb", "sgb", "sgb2", "cgb" });
        addText(panel, "Boot ROM", "emulator.bootRom");
        addInteger(panel, "Frame Rate", "emulator.frameRate", 30, 240, 1);
        addInteger(panel, "Fast Forward", "emulator.fastForwardSpeed", 1, 8, 1);
        addCheckbox(panel, "Throttle", "emulator.throttle");
        addCombo(panel, "Sync Mode", "emulator.syncMode",
                new String[] { "hybrid", "auto", "video", "audio", "none" });

        // Separator
        panel.add(new JLabel(), "span 2, gaptop 6, wrap");

        // Video
        addInteger(panel, "Scale", "video.scale", 1, 8, 1);
        addCheckbox(panel, "Aspect Ratio", "video.maintainAspectRatio");
        addCheckbox(panel, "Scanlines", "video.scanlines");
        addCheckbox(panel, "VSync", "video.vsync");
        addCombo(panel, "Filter", "video.filter", new String[] { "none", "nearest", "linear" });
        addCombo(panel, "Palette", "video.palette", new String[] { "dmg_default", "dmg_green", "custom" });
        addInteger(panel, "Frame Skip", "video.frameskip", 0, 5, 1);
        addCheckbox(panel, "Stats Overlay", "video.renderStats");
        addCombo(panel, "Theme", "ui.theme", new String[] { "light", "dark" });

        return wrapWithPadding(panel);
    }

    /** Audio tab: common audio settings. */
    private JPanel buildAudioTab() {
        JPanel panel = createFormPanel();
        addCheckbox(panel, "Enabled", "audio.enabled");
        addInteger(panel, "Sample Rate", "audio.sampleRate", 8000, 96000, 1000);
        addInteger(panel, "Buffer Size", "audio.bufferSize", 128, 8192, 128);
        addInteger(panel, "Latency (ms)", "audio.latencyMs", 10, 250, 5);
        return wrapWithPadding(panel);
    }

    /** Controls tab: key bindings + joystick. */
    private JPanel buildControlsTab() {
        JPanel panel = createFormPanel();

        // Key bindings section
        panel.add(createSectionLabel("Key Bindings"), "span 2, gapbottom 4, wrap");
        addKeyBinding(panel, "Up", "up", "UP");
        addKeyBinding(panel, "Down", "down", "DOWN");
        addKeyBinding(panel, "Left", "left", "LEFT");
        addKeyBinding(panel, "Right", "right", "RIGHT");
        addKeyBinding(panel, "A", "a", "Z");
        addKeyBinding(panel, "B", "b", "X");
        addKeyBinding(panel, "Start", "start", "ENTER");
        addKeyBinding(panel, "Select", "select", "SPACE");
        addKeyBinding(panel, "Pause", "pause", "P");
        addKeyBinding(panel, "Reset", "reset", "CTRL_R");
        addKeyBinding(panel, "Fullscreen", "fullscreen", "F11");
        addKeyBinding(panel, "Save 0/1/2", "save0", "F5");
        addKeyBinding(panel, "Save 1", "save1", "F6");
        addKeyBinding(panel, "Save 2", "save2", "F7");
        addKeyBinding(panel, "Load 0/1/2", "load0", "F8");
        addKeyBinding(panel, "Load 1", "load1", "F9");
        addKeyBinding(panel, "Load 2", "load2", "F10");
        addKeyBinding(panel, "Debug", "debug", "F12");
        addKeyBinding(panel, "VRAM", "vram", "F4");

        rebindHint = new JLabel("Click Rebind, then press the desired key.");
        rebindHint.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        panel.add(rebindHint, "span 2, wrap");

        // Joystick section
        panel.add(createSectionLabel("Joystick"), "span 2, gaptop 8, gapbottom 4, wrap");
        addCheckbox(panel, "Enabled", "input.joystick.enabled");
        joystickCombo = new JComboBox<>();
        joystickCombo.setPreferredSize(new Dimension(200, 26));
        addRow(panel, "Device", joystickCombo);
        addDecimal(panel, "Deadzone", "input.deadzone", 0.0, 0.95, 0.01);
        addCheckbox(panel, "Use POV/D-Pad", "input.joystick.pov");

        // Joystick mapping
        addJoystickBinding(panel, "Btn A", "input.joystick.button.a", "0");
        addJoystickBinding(panel, "Btn B", "input.joystick.button.b", "1");
        addJoystickBinding(panel, "Btn Start", "input.joystick.button.start", "7");
        addJoystickBinding(panel, "Btn Select", "input.joystick.button.select", "6");
        addJoystickAxisBinding(panel, "Axis X", "input.joystick.axis.x", "x");
        addJoystickAxisBinding(panel, "Axis Y", "input.joystick.axis.y", "y");
        addCheckbox(panel, "Invert X", "input.joystick.axis.x.inverted");
        addCheckbox(panel, "Invert Y", "input.joystick.axis.y.inverted");

        return wrapWithPadding(panel);
    }

    /** Advanced tab: logging, debug, input timing, profiles. */
    private JPanel buildAdvancedTab() {
        JPanel panel = createFormPanel();

        // Input timing
        panel.add(createSectionLabel("Input Timing"), "span 2, gapbottom 4, wrap");
        addInteger(panel, "Repeat Delay (ms)", "input.repeatDelayMs", 0, 1000, 10);
        addInteger(panel, "Repeat Rate (ms)", "input.repeatRateMs", 10, 500, 5);
        addInteger(panel, "Debounce (ms)", "input.debounceMs", 0, 100, 1);
        addInteger(panel, "Min Press (ms)", "input.minPressMs", 0, 100, 1);

        // Audio advanced
        panel.add(createSectionLabel("Audio Advanced"), "span 2, gaptop 8, gapbottom 4, wrap");
        addCheckbox(panel, "Null Output", "gbc.audio.nullOutput");
        addCheckbox(panel, "Debug Logs", "gbc.audio.debug");

        // Debug traces
        panel.add(createSectionLabel("Debug Traces"), "span 2, gaptop 8, gapbottom 4, wrap");
        addCheckbox(panel, "PPU Trace", "gbc.ppu.trace");
        addCheckbox(panel, "Timer Trace", "gbc.timer.trace");
        addCheckbox(panel, "CPU Trace", "gbc.cpu.trace");

        // Logging
        panel.add(createSectionLabel("Logging"), "span 2, gaptop 8, gapbottom 4, wrap");
        addCheckbox(panel, "Console", "gbc.logging.console");
        addText(panel, "Log File", "gbc.logging.file");
        String[] levels = { "OFF", "SEVERE", "WARNING", "INFO", "FINE", "FINER", "FINEST" };
        addCombo(panel, "Root", "gbc.logging.level.root", levels);
        addCombo(panel, "Model", "gbc.logging.level.model", levels);
        addCombo(panel, "CPU", "gbc.logging.level.cpu", levels);
        addCombo(panel, "Memory", "gbc.logging.level.memory", levels);
        addCombo(panel, "Graphics", "gbc.logging.level.graphics", levels);
        addCombo(panel, "View", "gbc.logging.level.view", levels);
        addCombo(panel, "Controller", "gbc.logging.level.controller", levels);

        // Profiles
        panel.add(createSectionLabel("Profiles"), "span 2, gaptop 8, gapbottom 4, wrap");
        profilesCombo = new JComboBox<>();
        profilesCombo.setPreferredSize(new Dimension(180, 26));
        addRow(panel, "Profile", profilesCombo);

        JPanel profileButtons = new JPanel(new MigLayout("insets 0", "[]6[]6[]6[]", "[]"));
        JButton loadBtn = new JButton("Load");
        JButton saveBtn = new JButton("Save");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");
        loadBtn.addActionListener(e -> loadSelectedProfile());
        saveBtn.addActionListener(e -> saveProfilePrompt());
        deleteBtn.addActionListener(e -> deleteSelectedProfile());
        refreshBtn.addActionListener(e -> reloadProfiles());
        profileButtons.add(loadBtn);
        profileButtons.add(saveBtn);
        profileButtons.add(deleteBtn);
        profileButtons.add(refreshBtn);
        panel.add(new JLabel(), "alignx right");
        panel.add(profileButtons, "wrap");

        return wrapWithPadding(panel);
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 8, fillx", "[right]8[grow,fill]", "[]"));
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

        keyBindings.put(action, new BindingEntry(action, field, defaultKey));
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

    private void addJoystickAxisBinding(JPanel panel, String label, String key, String defaultValue) {
        JTextField field = new JTextField(defaultValue);
        field.setPreferredSize(new Dimension(120, 26));
        JButton detect = new JButton("Detect");
        detect.addActionListener(e -> detectJoystickBinding(key, field));
        JButton browse = new JButton("Browse");
        browse.addActionListener(e -> browseAxes(field));
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        row.add(field);
        row.add(detect);
        row.add(browse);
        joystickBindings.put(key, field);
        defaultJoystickBindings.put(key, defaultValue);
        addRow(panel, label, row);
    }

    private void loadFromSystemProperties() {
        loadFromConfig(AppConfig.get().getConfig());
    }

    private void loadFromConfig(EmulatorConfig config) {
        Properties props = config.toProperties();
        loadFromProperties(props);
    }

    private void loadFromProperties(Properties props) {
        for (SettingEntry entry : entries) {
            entry.loadFrom(props);
        }
        loadKeyBindings(props);
        loadJoysticks(props);
        loadJoystickBindings(props);
        reloadProfiles();
    }

    private Properties loadConfigProperties() {
        return AppConfig.get().getConfig().toProperties();
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
    }

    private void loadJoysticks() {
        loadJoysticks(AppConfig.get().getConfig().toProperties());
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
        // Build config from UI fields
        Properties props = new Properties();
        for (SettingEntry entry : entries) {
            entry.applyTo(props);
        }
        applyKeyBindings(props);
        applyJoystickSelection(props);
        applyJoystickBindings(props);

        EmulatorConfig config = ConfigSerializer.fromProperties(props);

        // Apply + optionally persist via controller (handles diffing + selective
        // restart)
        if (controller != null) {
            controller.applyConfig(config, persist);
        } else {
            AppConfig.get().setConfig(config);
            ConfigSerializer.forceApplyToSystemProperties(config);
            if (persist) {
                try {
                    ConfigSerializer.save(AppConfig.get().getConfigPath(), config);
                } catch (IOException e) {
                    window.showMessage("Failed to save settings: " + e.getMessage(), "Settings",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        // Refresh menu bar state
        SwingUtilities.invokeLater(() -> window.refreshUiState(true));
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

    /** Provide a list of axis names available on the resolved joystick. */
    public List<String> getAvailableAxes() {
        String selectedDevice = "";
        if (joystickCombo != null && joystickCombo.getSelectedItem() != null) {
            selectedDevice = joystickCombo.getSelectedItem().toString();
        }
        try {
            gbc.controller.input.JInputNativeLoader.loadIfNeeded();
        } catch (Throwable ex) {
            return List.of();
        }
        net.java.games.input.Controller controller = resolveSelectedJoystick(selectedDevice);
        if (controller == null) {
            return List.of();
        }
        List<String> axes = new ArrayList<>();
        for (net.java.games.input.Component comp : controller.getComponents()) {
            if (comp.getIdentifier() instanceof net.java.games.input.Component.Identifier.Axis
                    && !comp.getIdentifier().equals(net.java.games.input.Component.Identifier.Axis.POV)) {
                axes.add(comp.getIdentifier().getName());
            }
        }
        return axes;
    }

    private void restoreDefaults() {
        EmulatorConfig defaults = new EmulatorConfig();
        loadFromConfig(defaults);
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

    private void browseAxes(JTextField field) {
        String selectedDevice = "";
        if (joystickCombo != null && joystickCombo.getSelectedItem() != null) {
            selectedDevice = joystickCombo.getSelectedItem().toString();
        }
        try {
            gbc.controller.input.JInputNativeLoader.loadIfNeeded();
        } catch (Throwable ex) {
            window.showMessage("JInput not available: " + ex.getMessage(),
                    "Joystick", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        net.java.games.input.Controller controller = resolveSelectedJoystick(selectedDevice);
        if (controller == null) {
            window.showMessage("No joystick found. Connect a controller and try again.",
                    "Joystick", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> axisNames = new ArrayList<>();
        for (net.java.games.input.Component comp : controller.getComponents()) {
            if (comp.getIdentifier() instanceof net.java.games.input.Component.Identifier.Axis
                    && !comp.getIdentifier().equals(net.java.games.input.Component.Identifier.Axis.POV)) {
                axisNames.add(comp.getIdentifier().getName());
            }
        }
        if (axisNames.isEmpty()) {
            window.showMessage("No axes found on controller: " + controller.getName(),
                    "Joystick", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] options = axisNames.toArray(new String[0]);
        String chosen = (String) javax.swing.JOptionPane.showInputDialog(
                this, "Select an axis:", "Available Axes",
                javax.swing.JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (chosen != null) {
            field.setText(chosen);
        }
    }

    private void testDpadInput() {
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
                long deadline = System.nanoTime() + 3_000_000_000L;
                while (System.nanoTime() < deadline) {
                    if (!controller.poll()) {
                        break;
                    }
                    net.java.games.input.Component pov = controller.getComponent(
                            net.java.games.input.Component.Identifier.Axis.POV);
                    if (pov != null) {
                        float v = pov.getPollData();
                        if (v != net.java.games.input.Component.POV.CENTER) {
                            return "D-Pad detected! Value: " + v;
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

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    String result = get();
                    if (result != null) {
                        window.showMessage(result, "D-Pad Test",
                                javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        window.showMessage(
                                "No D-Pad input detected. Press a direction on the D-Pad and try again.",
                                "D-Pad Test", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    window.showMessage("D-Pad test failed: " + e.getMessage(),
                            "D-Pad Test", javax.swing.JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
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
                    if (comp.getIdentifier() instanceof net.java.games.input.Component.Identifier.Axis
                            && !comp.getIdentifier().equals(net.java.games.input.Component.Identifier.Axis.POV)) {
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
        applyJoystickSelection(props);
        applyJoystickBindings(props);
        EmulatorConfig profileConfig = ConfigSerializer.fromProperties(props);
        Path file = profilesDir.resolve(name + ".properties");
        try {
            Files.createDirectories(profilesDir);
            ConfigSerializer.save(file, profileConfig);
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
        EmulatorConfig profileConfig = ConfigSerializer.load(file);
        loadFromConfig(profileConfig);
        // Apply the loaded profile via the centralized apply path
        applySettings(false);
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
        private final String defaultKey;

        private BindingEntry(String action, JTextField field, String defaultKey) {
            this.action = action;
            this.field = field;
            this.defaultKey = defaultKey;
        }
    }
}
