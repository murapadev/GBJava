package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.ui.controller.EmulatorController;
import gbc.ui.view.EmulatorView.ColorFilter;

import javax.swing.*;
import javax.swing.Box;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * EmulatorWindow with improved graphics, debugging, and UI features
 */
public class EmulatorWindow extends JFrame implements KeyListener {

    private final GameBoyColor gbc;
    private EmulatorController controller;

    // UI Components
    private EmulatorView emulatorView;
    private DebugView debugView;
    private VRAMViewer vramViewer;
    private MenuBar menuBar;
    private PrimaryToolbar primaryToolbar;

    // Status and info
    private JPanel statusPanel;
    private JLabel romStatusLabel;
    private JLabel stateStatusLabel;
    private JLabel speedStatusLabel;
    private JLabel fpsStatusLabel;

    // Window state
    private boolean isFullscreen = false;
    private Dimension windowedSize;
    private Point windowedLocation;

    public EmulatorWindow(GameBoyColor gbc) {
        this.gbc = gbc;

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
                debugView = new DebugView(gbc);
                debugView.setVisible(false);
            } catch (Exception e) {
                System.out.println("Debug view not available: " + e.getMessage());
                debugView = null;
            }

            try {
                vramViewer = new VRAMViewer(gbc);
                vramViewer.setVisible(false);
            } catch (Exception e) {
                System.out.println("VRAM viewer not available: " + e.getMessage());
                vramViewer = null;
            }

            // Create menu bar
            try {
                menuBar = new MenuBar(this, gbc, emulatorView);
            } catch (Exception e) {
                System.out.println("Custom menu bar not available: " + e.getMessage());
                menuBar = null;
            }

        } catch (Exception e) {
            System.err.println("Error initializing components: " + e.getMessage());
            e.printStackTrace();

            // Fallback: create minimal components
            emulatorView = new EmulatorView(gbc);
            menuBar = null;
        }

        primaryToolbar = new PrimaryToolbar(this, menuBar, emulatorView);
        romStatusLabel = new JLabel("ROM: none");
        stateStatusLabel = new JLabel("State: Ready");
        speedStatusLabel = new JLabel("Speed: 100%");
        fpsStatusLabel = new JLabel("FPS: --");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        if (primaryToolbar != null) {
            add(primaryToolbar, BorderLayout.NORTH);
        }

        // Main emulator view
        add(emulatorView, BorderLayout.CENTER);

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
                    "Game Boy Color Emulator\nMinimal UI Mode", "About", JOptionPane.INFORMATION_MESSAGE));
            helpMenu.add(aboutItem);
            simpleMenuBar.add(helpMenu);

            setJMenuBar(simpleMenuBar);
        }

        // Status panel
        statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        statusPanel.add(romStatusLabel);
        statusPanel.add(Box.createHorizontalStrut(16));
        statusPanel.add(stateStatusLabel);
        statusPanel.add(Box.createHorizontalStrut(16));
        statusPanel.add(speedStatusLabel);
        statusPanel.add(Box.createHorizontalStrut(16));
        statusPanel.add(fpsStatusLabel);
        statusPanel.add(Box.createHorizontalGlue());
        add(statusPanel, BorderLayout.SOUTH);
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
        addKeyListener(this);
        setFocusable(true);
        requestFocusInWindow();

        // Ensure emulator view can also receive focus
        emulatorView.setFocusable(true);
        emulatorView.addKeyListener(this);
    }

    private void setupWindow() {
        setTitle("Game Boy Color Emulator");
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

    public void setController(EmulatorController controller) {
        this.controller = controller;
        if (menuBar != null) {
            menuBar.setController(controller);
        }
        if (primaryToolbar != null) {
            primaryToolbar.setController(controller);
        }
        if (debugView != null) {
            debugView.setController(controller);
        }
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
            System.err.println("Error updating UI: " + e.getMessage());
        }
    }

    private void updateStatus() {
        updateRomStatus();
        updateSpeedStatus();
        boolean paused = controller != null ? controller.isPaused() : gbc.isPaused();
        stateStatusLabel.setText(paused ? "State: Paused" : "State: Running");
        refreshUiState(false);
    }

    private void updateRomStatus() {
        try {
            int romByte = gbc.getMemory().readByte(0x0134);
            if (romByte != 0) {
                StringBuilder title = new StringBuilder();
                for (int i = 0x0134; i <= 0x0143; i++) {
                    int value = gbc.getMemory().readByte(i) & 0xFF;
                    if (value == 0) {
                        break;
                    }
                    if (value >= 32 && value <= 126) {
                        title.append((char) value);
                    }
                }
                if (title.length() > 0) {
                    romStatusLabel.setText("ROM: " + title.toString().trim());
                } else {
                    romStatusLabel.setText("ROM: loaded");
                }
            } else {
                romStatusLabel.setText("ROM: none");
            }
        } catch (Exception e) {
            romStatusLabel.setText("ROM: none");
        }
    }

    private void updateSpeedStatus() {
        float speed = gbc.getSpeedMultiplier();
        speedStatusLabel.setText(String.format("Speed: %.0f%%", speed * 100f));
    }

    public void refreshUiState(boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refreshUiState(updateMenu));
            return;
        }

        boolean paused = controller != null ? controller.isPaused() : gbc.isPaused();
        float speed = gbc.getSpeedMultiplier();
        ColorFilter filter = emulatorView.getColorFilter();
        boolean scanlines = emulatorView.isShowScanlines();
        boolean smooth = emulatorView.isSmoothScaling();
        boolean maintainAspect = emulatorView.isMaintainAspectRatio();
        int scale = emulatorView.getScaleFactor();

        if (primaryToolbar != null) {
            primaryToolbar.syncState(paused, speed, filter, scanlines, smooth, maintainAspect, scale);
        }

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
        refreshUiState(true);
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
        refreshUiState(true);
    }

    public void onDisplaySettingsChanged(boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onDisplaySettingsChanged(updateMenu));
            return;
        }
        refreshUiState(updateMenu);
    }

    public void onColorFilterChanged(ColorFilter filter, boolean updateMenu) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onColorFilterChanged(filter, updateMenu));
            return;
        }
        refreshUiState(updateMenu);
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

            // Hide status panel in fullscreen
            if (statusPanel != null) {
                statusPanel.setVisible(false);
            }
            if (primaryToolbar != null) {
                primaryToolbar.setVisible(false);
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

            // Show status panel
            if (statusPanel != null) {
                statusPanel.setVisible(true);
            }
            if (primaryToolbar != null) {
                primaryToolbar.setVisible(true);
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
        if (controller != null) {
            controller.loadRomAsync(file.getAbsolutePath())
                    .whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
                        if (throwable != null) {
                            JOptionPane.showMessageDialog(this,
                                    "Error loading ROM:\n" + describeError(throwable),
                                    "Load Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }));
        } else {
            CompletableFuture.runAsync(() -> {
                gbc.insertCartridge(file.getAbsolutePath());
                gbc.reset();
            }).whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
                if (throwable == null) {
                    setTitle("Game Boy Color Emulator - " + file.getName());
                    if (emulatorView != null) {
                        emulatorView.repaint();
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Error loading ROM:\n" + describeError(throwable),
                            "Load Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }));
        }
    }

    // Emulation control
    public void togglePause() {
        if (controller != null) {
            controller.togglePause();
        }
    }

    public void stepInstruction() {
        if (controller != null) {
            controller.stepInstruction();
        } else {
            gbc.executeCycle();
            emulatorView.repaint();
            if (debugView != null) {
                debugView.updateAllViews();
            }
            setStatusText(String.format("Stepped - PC: $%04X", gbc.getCpu().getRegisters().getPC()));
        }
    }

    public void resetEmulator() {
        if (controller != null) {
            controller.reset();
        } else {
            gbc.reset();
        }

        // Reset window title
        setTitle("Game Boy Color Emulator");
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

    // Key handling for game controls
    @Override
    public void keyPressed(KeyEvent e) {
        if (gbc.getController() == null)
            return;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                gbc.getController().setUp(true);
                break;
            case KeyEvent.VK_DOWN:
                gbc.getController().setDown(true);
                break;
            case KeyEvent.VK_LEFT:
                gbc.getController().setLeft(true);
                break;
            case KeyEvent.VK_RIGHT:
                gbc.getController().setRight(true);
                break;
            case KeyEvent.VK_Z:
                gbc.getController().setA(true);
                break;
            case KeyEvent.VK_X:
                gbc.getController().setB(true);
                break;
            case KeyEvent.VK_ENTER:
                gbc.getController().setStart(true);
                break;
            case KeyEvent.VK_SPACE:
                gbc.getController().setSelect(true);
                break;

            // Emulator hotkeys
            case KeyEvent.VK_P:
                if (!e.isControlDown() && !e.isAltDown()) {
                    togglePause();
                }
                break;
            case KeyEvent.VK_R:
                if (e.isControlDown()) {
                    resetEmulator();
                }
                break;
            case KeyEvent.VK_F12:
                openDebugView();
                break;
            case KeyEvent.VK_F5:
                openVRAMViewer();
                break;
            case KeyEvent.VK_F11:
                toggleFullscreen();
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (gbc.getController() == null)
            return;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                gbc.getController().setUp(false);
                break;
            case KeyEvent.VK_DOWN:
                gbc.getController().setDown(false);
                break;
            case KeyEvent.VK_LEFT:
                gbc.getController().setLeft(false);
                break;
            case KeyEvent.VK_RIGHT:
                gbc.getController().setRight(false);
                break;
            case KeyEvent.VK_Z:
                gbc.getController().setA(false);
                break;
            case KeyEvent.VK_X:
                gbc.getController().setB(false);
                break;
            case KeyEvent.VK_ENTER:
                gbc.getController().setStart(false);
                break;
            case KeyEvent.VK_SPACE:
                gbc.getController().setSelect(false);
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
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

    public GameBoyColor getGameBoyColor() {
        return gbc;
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

    // Configuration
    public void applySettings() {
        // Apply any saved settings
        // This could load from a properties file or registry
    }

    public void saveSettings() {
        // Save current settings
        // This could save to a properties file or registry
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
