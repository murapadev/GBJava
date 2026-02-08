package gbc.view;

import gbc.controller.config.AppConfig;
import gbc.controller.config.ConfigSerializer;
import gbc.controller.config.EmulatorConfig;
import gbc.model.GameBoyColor;
import gbc.controller.EmulatorActions;
import gbc.controller.EmulatorStatus;
import gbc.controller.EmulatorStatusPublisher;
import gbc.view.EmulatorView.ColorFilter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

/**
 * EmulatorWindow with improved graphics, debugging, and UI features
 */
public class EmulatorWindow extends JFrame implements EmulatorUi {

    private static final Logger LOGGER = Logger.getLogger(EmulatorWindow.class.getName());

    private final GameBoyColor gbc;
    private EmulatorActions controller;
    private EmulatorStatusPublisher statusPublisher;
    private java.util.function.Consumer<EmulatorStatus> statusListener;
    private volatile EmulatorStatus lastStatus;

    // UI Components
    private EmulatorView emulatorView;
    private DebugView debugView;
    private VRAMViewer vramViewer;
    private MenuBar menuBar;
    private SettingsDialog settingsDialog;

    // Status and info
    private JLabel romStatusLabel;
    private JLabel stateStatusLabel;
    private JLabel speedStatusLabel;
    private JLabel fpsStatusLabel;
    private JPanel statusPanel;

    // Window state
    private boolean isFullscreen = false;
    private Dimension windowedSize;
    private Point windowedLocation;

    public EmulatorWindow(GameBoyColor gbc) {
        this.gbc = gbc;

        applyTheme();
        // Initialize components first
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupWindow();
    }

    private void initializeComponents() {
        try {
            // Create views
            emulatorView = new EmulatorView(gbc);

            // Create simple debug views (may be null if classes don't exist)
            try {
                debugView = new DebugView();
                debugView.setVisible(false);
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Debug view not available", e);
                debugView = null;
            }

            try {
                vramViewer = new VRAMViewer(gbc);
                vramViewer.setVisible(false);
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "VRAM viewer not available", e);
                vramViewer = null;
            }

            // Create menu bar
            try {
                menuBar = new MenuBar(this, emulatorView);
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Custom menu bar not available", e);
                menuBar = null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing components", e);

            // Fallback: create minimal components
            emulatorView = new EmulatorView(gbc);
            menuBar = null;
        }

        settingsDialog = new SettingsDialog(this, emulatorView);
        romStatusLabel = new JLabel("ROM: none");
        stateStatusLabel = new JLabel("State: Ready");
        speedStatusLabel = new JLabel("Speed: 100%");
        fpsStatusLabel = new JLabel("FPS: --");
    }

    private void setupLayout() {
        setLayout(new MigLayout("insets 0, fill", "[grow]", "[grow][]"));

        Color panelBg = UIManager.getColor("Panel.background");
        Color cardBg = UIManager.getColor("TextField.background");
        Color separator = UIManager.getColor("Separator.foreground");

        JPanel contentPanel = new JPanel(new MigLayout("insets 12, fill", "[grow]", "[grow]"));
        contentPanel.setBackground(panelBg);

        Color borderColor = UIManager.getColor("Component.borderColor");
        JPanel screenFrame = new JPanel(new MigLayout("insets 8, fill", "[grow]", "[grow]"));
        screenFrame.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor != null ? borderColor : separator != null ? separator : new Color(210, 202, 188), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        screenFrame.setBackground(cardBg != null ? cardBg : new Color(250, 248, 243));
        screenFrame.add(emulatorView, "grow");

        contentPanel.add(screenFrame, "grow");
        add(contentPanel, "grow, wrap");

        // Menu bar (if available, otherwise create simple one)
        if (menuBar != null) {
            setJMenuBar(menuBar);
        } else {
            // Create simple fallback menu bar
            JMenuBar simpleMenuBar = new JMenuBar();
            JMenu fileMenu = new JMenu("File");
            JMenuItem exitItem = new JMenuItem("Exit");
            exitItem.addActionListener(e -> System.exit(0));
            fileMenu.add(exitItem);
            simpleMenuBar.add(fileMenu);

            JMenu helpMenu = new JMenu("Help");
            JMenuItem aboutItem = new JMenuItem("About");
            aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                    "GBJava\nGame Boy Color Emulator", "About", JOptionPane.INFORMATION_MESSAGE));
            helpMenu.add(aboutItem);
            simpleMenuBar.add(helpMenu);

            setJMenuBar(simpleMenuBar);
        }

        Font statusFont = romStatusLabel.getFont().deriveFont(11f);
        Color mutedFg = UIManager.getColor("Label.disabledForeground");
        if (mutedFg == null) {
            mutedFg = new Color(140, 140, 140);
        }
        for (JLabel label : List.of(romStatusLabel, stateStatusLabel, speedStatusLabel, fpsStatusLabel)) {
            label.setFont(statusFont);
            label.setForeground(mutedFg);
        }

        statusPanel = new JPanel(new MigLayout("insets 4 12 4 12, fillx", "[]4[][]4[][]4[][]4[]push[]", "[]"));
        statusPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                separator != null ? separator : new Color(200, 200, 200)));
        statusPanel.setBackground(panelBg);
        statusPanel.add(romStatusLabel);
        statusPanel.add(createStatusSeparator());
        statusPanel.add(stateStatusLabel);
        statusPanel.add(createStatusSeparator());
        statusPanel.add(speedStatusLabel);
        statusPanel.add(createStatusSeparator());
        statusPanel.add(fpsStatusLabel);
        add(statusPanel, "growx");
    }

    private static JSeparator createStatusSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 14));
        return sep;
    }

    private void setupEventHandlers() {
        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });

        // Key listeners for game controls
        // Key handling is managed by EmulatorController.
        setFocusable(true);
        emulatorView.setFocusable(true);
    }

    private void setupWindow() {
        setTitle("GBJava");
        setIconImages(IconFactory.appIcons());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Set initial size based on emulator view
        pack();

        // Store windowed size for fullscreen toggle
        windowedSize = getSize();
        windowedLocation = getLocation();

        // Center on screen
        setLocationRelativeTo(null);

        // Set minimum size
        setMinimumSize(new Dimension(320, 288));

        // Force initial update
        SwingUtilities.invokeLater(() -> {
            updateUI();
            repaint();
            revalidate();
        });

        setVisible(true);

        refreshUiState(true);
    }

    private void applyTheme() {
        // Base theme now comes from FlatLaf; keep only minimal overrides here if needed.
    }

    public void setController(EmulatorActions controller) {
        if (statusPublisher != null && statusListener != null) {
            statusPublisher.removeListener(statusListener);
        }
        this.controller = controller;
        this.statusPublisher = controller != null ? controller.statusPublisher() : null;
        if (statusListener == null) {
            statusListener = this::onStatusChanged;
        }
        if (statusPublisher != null) {
            statusPublisher.addListener(statusListener);
        }
        if (menuBar != null) {
            menuBar.setController(controller);
        }
        if (settingsDialog != null) {
            settingsDialog.setController(controller);
        }
        if (debugView != null) {
            debugView.setController(controller);
        }
        lastStatus = controller != null ? controller.getStatus() : null;
        refreshUiState(true);
    }

    public void update() {
        // Simple, thread-safe update
        if (SwingUtilities.isEventDispatchThread()) {
            updateUI();
        } else {
            SwingUtilities.invokeLater(this::updateUI);
        }
    }

    private void updateUI() {
        try {
            // Only update what's essential
            if (emulatorView != null) {
                emulatorView.repaint(); // Just repaint, don't call update
            }

            // Simple status update
            updateStatus();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating UI", e);
        }
    }

    private void updateStatus() {
        EmulatorStatus status = lastStatus;
        if (status == null) {
            romStatusLabel.setText("ROM: controller unavailable");
            speedStatusLabel.setText("Speed: --");
            stateStatusLabel.setText("State: Controller unavailable");
            return;
        }
        updateRomStatus(status);
        updateSpeedStatus(status);
        stateStatusLabel.setText(status.paused() ? "State: Paused" : "State: Running");
        refreshUiState(status, false);
    }

    private void updateRomStatus(EmulatorStatus status) {
        if (!status.romLoaded()) {
            romStatusLabel.setText("ROM: none");
            setTitle("GBJava");
            return;
        }
        String title = status.romTitle();
        if (title != null && !title.isBlank()) {
            romStatusLabel.setText("ROM: " + title);
            setTitle("GBJava - " + title);
        } else {
            romStatusLabel.setText("ROM: loaded");
            setTitle("GBJava");
        }
    }

    private void updateSpeedStatus(EmulatorStatus status) {
        speedStatusLabel.setText(String.format("Speed: %.0f%%", status.speedMultiplier() * 100f));
    }

    public void refreshUiState(boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshUiState(updateMenu));
            return;
        }
        EmulatorStatus status = lastStatus;
        if (status == null) {
            return;
        }
        refreshUiState(status, updateMenu);
    }

    private void onStatusChanged(EmulatorStatus status) {
        lastStatus = status;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onStatusChanged(status));
            return;
        }
        updateRomStatus(status);
        updateSpeedStatus(status);
        stateStatusLabel.setText(status.paused() ? "State: Paused" : "State: Running");
        refreshUiState(status, true);
    }

    private void refreshUiState(EmulatorStatus status, boolean updateMenu) {
        boolean paused = status.paused();
        float speed = status.speedMultiplier();
        ColorFilter filter = emulatorView.getColorFilter();
        boolean scanlines = emulatorView.isShowScanlines();
        boolean smooth = emulatorView.isSmoothScaling();
        boolean maintainAspect = emulatorView.isMaintainAspectRatio();
        int scale = emulatorView.getScaleFactor();


        if (updateMenu && menuBar != null) {
            menuBar.synchronizePauseState(paused);
            menuBar.synchronizeSpeedFromWindow(speed);
            menuBar.synchronizeScaleFromWindow(scale);
            menuBar.synchronizeDisplayOptionsFromWindow(scanlines, smooth, maintainAspect);
            menuBar.synchronizeFilterFromWindow(filter);
            menuBar.synchronizeAlwaysOnTop(isAlwaysOnTop());
        }
    }

    public void onPauseStateChanged(boolean paused) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onPauseStateChanged(paused));
            return;
        }
        stateStatusLabel.setText(paused ? "State: Paused" : "State: Running");
        if (lastStatus != null) {
            lastStatus = new EmulatorStatus(lastStatus.romLoaded(), lastStatus.romTitle(),
                    lastStatus.speedMultiplier(), paused);
            refreshUiState(lastStatus, true);
        }
        if (debugView != null) {
            debugView.reflectPauseState(paused);
        }
    }

    public void onSpeedChanged(float speed) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onSpeedChanged(speed));
            return;
        }
        speedStatusLabel.setText(String.format("Speed: %.0f%%", speed * 100f));
        if (lastStatus != null) {
            lastStatus = new EmulatorStatus(lastStatus.romLoaded(), lastStatus.romTitle(), speed,
                    lastStatus.paused());
            refreshUiState(lastStatus, true);
        }
    }

    public void onDisplaySettingsChanged(boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onDisplaySettingsChanged(updateMenu));
            return;
        }
        persistDisplaySettings();
        refreshUiState(updateMenu);
    }

    public void onColorFilterChanged(ColorFilter filter, boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onColorFilterChanged(filter, updateMenu));
            return;
        }
        persistDisplaySettings();
        refreshUiState(updateMenu);
    }

    private void persistDisplaySettings() {
        EmulatorConfig cfg = AppConfig.get().getConfig();
        cfg.setMaintainAspectRatio(emulatorView.isMaintainAspectRatio());
        cfg.setScanlines(emulatorView.isShowScanlines());
        cfg.setFilter(emulatorView.isSmoothScaling() ? "linear" : "none");
        cfg.setPalette(mapFilterToPalette(emulatorView.getColorFilter()));
        if (controller != null) {
            controller.applyConfig(cfg, true);
        } else {
            // Fallback: save directly if controller unavailable
            try {
                ConfigSerializer.save(AppConfig.get().getConfigPath(), cfg);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to persist display settings", e);
            }
            ConfigSerializer.forceApplyToSystemProperties(cfg);
        }
    }

    private String mapFilterToPalette(ColorFilter filter) {
        return switch (filter) {
            case GREEN_MONOCHROME -> "dmg_green";
            case SEPIA -> "custom";
            case HIGH_CONTRAST -> "high_contrast";
            default -> "dmg_default";
        };
    }

    public boolean hasDebugView() {
        return debugView != null;
    }

    public boolean hasVRAMViewer() {
        return vramViewer != null;
    }

    public void updateFPS(double fps) {
        // Ensure FPS updates happen on EDT
        if (SwingUtilities.isEventDispatchThread()) {
            fpsStatusLabel.setText(String.format("FPS: %.1f", fps));
        } else {
            SwingUtilities.invokeLater(() -> fpsStatusLabel.setText(String.format("FPS: %.1f", fps)));
        }
    }

    public void openSettings() {
        if (settingsDialog != null) {
            settingsDialog.showDialog();
        }
    }

    // Window management
    public void toggleFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!isFullscreen) {
            // Enter fullscreen
            windowedSize = getSize();
            windowedLocation = getLocation();

            dispose();
            setUndecorated(true);
            device.setFullScreenWindow(this);
            isFullscreen = true;

            if (statusPanel != null) {
                statusPanel.setVisible(false);
            }

        } else {
            // Exit fullscreen
            device.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setSize(windowedSize);
            setLocation(windowedLocation);
            setVisible(true);
            isFullscreen = false;

            if (statusPanel != null) {
                statusPanel.setVisible(true);
            }
        }

        // Request focus back to this window
        requestFocusInWindow();
    }

    // Debug windows management
    public void openDebugView() {
        if (debugView == null) {
            showMessage("Debug view is not available in this build.", "Debug View", JOptionPane.WARNING_MESSAGE);
            return;
        }
        debugView.setVisible(true);
        debugView.toFront();
        debugView.updateAllViews();
    }

    public void openVRAMViewer() {
        if (vramViewer == null) {
            showMessage("VRAM viewer is not available in this build.", "VRAM Viewer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        vramViewer.setVisible(true);
        vramViewer.toFront();
        vramViewer.refresh();
    }

    // ROM management
    public void loadROM(java.io.File file) {
        if (controller == null) {
            showMessage("Controller unavailable.", "Load Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        controller.loadRomAsync(file.getAbsolutePath())
                .whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (throwable != null) {
                        JOptionPane.showMessageDialog(this,
                                "Error loading ROM:\n" + describeError(throwable),
                                "Load Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }));
    }

    // Emulation control
    public void togglePause() {
        if (controller == null) {
            setStatusText("Controller unavailable");
            return;
        }
        controller.togglePause();
    }

    public void stepInstruction() {
        if (controller == null) {
            setStatusText("Controller unavailable");
            return;
        }
        controller.stepInstruction();
    }

    public void resetEmulator() {
        if (controller == null) {
            setStatusText("Controller unavailable");
            return;
        }
        controller.reset();

        // Reset window title
        setTitle("GBJava");
    }

    // Cleanup
    private void handleWindowClosing() {
        // Save any settings if needed

        // Close debug windows
        if (debugView != null) {
            debugView.dispose();
        }
        if (vramViewer != null) {
            vramViewer.dispose();
        }

        // Stop emulation
        if (controller != null) {
            controller.stop();
        }

        // Exit application
        System.exit(0);
    }

    // Getters for components
    public EmulatorView getEmulatorView() {
        return emulatorView;
    }

    public DebugView getDebugView() {
        return debugView;
    }

    public VRAMViewer getVRAMViewer() {
        return vramViewer;
    }

    // Utility methods
    public void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    public void setStatusText(String text) {
        // Ensure status updates happen on EDT
        if (SwingUtilities.isEventDispatchThread()) {
            stateStatusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> stateStatusLabel.setText(text));
        }
    }

    @Override
    public void applyDisplayConfig(gbc.controller.config.EmulatorConfig cfg) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> applyDisplayConfig(cfg));
            return;
        }
        emulatorView.setScaleFactor(cfg.getScale());
        emulatorView.setMaintainAspectRatio(cfg.isMaintainAspectRatio());
        emulatorView.setShowScanlines(cfg.isScanlines());
        emulatorView.setSmoothScaling("linear".equalsIgnoreCase(cfg.getFilter()));
        emulatorView.setColorFilter(mapPaletteToFilter(cfg.getPalette()));
        refreshUiState(true);
    }

    private ColorFilter mapPaletteToFilter(String palette) {
        return switch (palette.toLowerCase()) {
            case "dmg_green" -> ColorFilter.GREEN_MONOCHROME;
            case "custom" -> ColorFilter.SEPIA;
            case "high_contrast" -> ColorFilter.HIGH_CONTRAST;
            default -> ColorFilter.NONE;
        };
    }

    private String describeError(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
        if (cause == null) {
            return "Unknown error";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
