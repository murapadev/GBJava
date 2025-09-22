package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.ui.controller.EmulatorController;

import javax.swing.*;
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

    // Status and info
    private JLabel statusLabel;
    private JLabel fpsLabel;
    private JPanel statusPanel;

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
                menuBar = new MenuBar(gbc, debugView, vramViewer, emulatorView);
            } catch (Exception e) {
                System.out.println("Custom menu bar not available: " + e.getMessage());
                // Create simple fallback - set to null and create simple JMenuBar
                menuBar = null;
            }

            // Status components
            statusLabel = new JLabel("Ready");
            fpsLabel = new JLabel("FPS: --");

        } catch (Exception e) {
            System.err.println("Error initializing components: " + e.getMessage());
            e.printStackTrace();

            // Fallback: create minimal components
            emulatorView = new EmulatorView(gbc);
            statusLabel = new JLabel("Error initializing - using minimal UI");
            fpsLabel = new JLabel("FPS: --");
            menuBar = null;
        }
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

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
        statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(fpsLabel, BorderLayout.EAST);
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
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
        if (menuBar != null) {
            menuBar.setController(controller);
        }
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
        // Check if a cartridge is loaded by checking if ROM area has been initialized
        try {
            int romByte = gbc.getMemory().readByte(0x0134); // Read from ROM title area
            if (romByte != 0) {
                // Try to read ROM title
                StringBuilder title = new StringBuilder();
                for (int i = 0x0134; i <= 0x0143; i++) {
                    int b = gbc.getMemory().readByte(i) & 0xFF;
                    if (b == 0)
                        break;
                    if (b >= 32 && b <= 126) { // Printable ASCII
                        title.append((char) b);
                    }
                }
                if (title.length() > 0) {
                    statusLabel.setText("ROM: " + title.toString().trim());
                } else {
                    statusLabel.setText("ROM Loaded");
                }
            } else {
                statusLabel.setText("No ROM loaded");
            }
        } catch (Exception e) {
            statusLabel.setText("No ROM loaded");
        }
    }

    public void updateFPS(double fps) {
        // Ensure FPS updates happen on EDT
        if (SwingUtilities.isEventDispatchThread()) {
            fpsLabel.setText(String.format("FPS: %.1f", fps));
        } else {
            SwingUtilities.invokeLater(() -> {
                fpsLabel.setText(String.format("FPS: %.1f", fps));
            });
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
            statusPanel.setVisible(false);

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
            statusPanel.setVisible(true);
        }

        // Request focus back to this window
        requestFocusInWindow();
    }

    // Debug windows management
    public void openDebugView() {
        debugView.setVisible(true);
        debugView.toFront();
        debugView.updateAllViews();
    }

    public void openVRAMViewer() {
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
            statusLabel.setText(text);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
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
