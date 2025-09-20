package gbc.view;

import gbc.model.GameBoyColor;
import gbc.view.EmulatorView.ColorFilter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * MenuBar with comprehensive emulation controls and graphics options
 */
public class MenuBar extends JMenuBar {

    private final GameBoyColor gbc;
    private final DebugView debugView;
    private final VRAMViewer vramViewer;
    private final EmulatorView emulatorView;

    // Graphics options dialog
    private GraphicsOptionsDialog graphicsDialog;

    public MenuBar(GameBoyColor gbc, DebugView debugView, VRAMViewer vramViewer,
            EmulatorView emulatorView) {
        this.gbc = gbc;
        this.debugView = debugView;
        this.vramViewer = vramViewer;
        this.emulatorView = emulatorView;
        this.graphicsDialog = new GraphicsOptionsDialog();

        initializeMenus();
    }

    private void initializeMenus() {
        add(createFileMenu());
        add(createEmulationMenu());
        add(createViewMenu());
        add(createGraphicsMenu());
        add(createDebugMenu());
        add(createHelpMenu());
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        // Open ROM
        JMenuItem openItem = new JMenuItem("Open ROM...");
        openItem.setMnemonic(KeyEvent.VK_O);
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(this::openFile);

        // Recent ROMs submenu
        JMenu recentMenu = new JMenu("Recent ROMs");
        recentMenu.setMnemonic(KeyEvent.VK_R);
        // TODO: Implement recent ROMs functionality

        // Save/Load States
        JMenuItem saveStateItem = new JMenuItem("Save State...");
        saveStateItem.setMnemonic(KeyEvent.VK_S);
        saveStateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        saveStateItem.addActionListener(this::saveState);

        JMenuItem loadStateItem = new JMenuItem("Load State...");
        loadStateItem.setMnemonic(KeyEvent.VK_L);
        loadStateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
        loadStateItem.addActionListener(this::loadState);

        // Export
        JMenuItem exportScreenshotItem = new JMenuItem("Export Screenshot...");
        exportScreenshotItem.addActionListener(this::exportScreenshot);

        // Exit
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic(KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(this::exit);

        fileMenu.add(openItem);
        fileMenu.add(recentMenu);
        fileMenu.addSeparator();
        fileMenu.add(saveStateItem);
        fileMenu.add(loadStateItem);
        fileMenu.addSeparator();
        fileMenu.add(exportScreenshotItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        return fileMenu;
    }

    private JMenu createEmulationMenu() {
        JMenu emulationMenu = new JMenu("Emulation");
        emulationMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem pauseItem = new JMenuItem("Pause/Resume");
        pauseItem.setMnemonic(KeyEvent.VK_P);
        pauseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
        pauseItem.addActionListener(this::togglePause);

        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.setMnemonic(KeyEvent.VK_R);
        resetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
        resetItem.addActionListener(this::resetEmulator);

        // Speed control
        JMenu speedMenu = new JMenu("Speed");
        speedMenu.setMnemonic(KeyEvent.VK_S);

        JMenuItem speed25Item = new JMenuItem("25%");
        JMenuItem speed50Item = new JMenuItem("50%");
        JMenuItem speed100Item = new JMenuItem("100% (Normal)");
        JMenuItem speed200Item = new JMenuItem("200%");
        JMenuItem speed400Item = new JMenuItem("400%");

        speed25Item.addActionListener(e -> setEmulationSpeed(0.25f));
        speed50Item.addActionListener(e -> setEmulationSpeed(0.5f));
        speed100Item.addActionListener(e -> setEmulationSpeed(1.0f));
        speed200Item.addActionListener(e -> setEmulationSpeed(2.0f));
        speed400Item.addActionListener(e -> setEmulationSpeed(4.0f));

        speedMenu.add(speed25Item);
        speedMenu.add(speed50Item);
        speedMenu.add(speed100Item);
        speedMenu.add(speed200Item);
        speedMenu.add(speed400Item);

        emulationMenu.add(pauseItem);
        emulationMenu.add(resetItem);
        emulationMenu.addSeparator();
        emulationMenu.add(speedMenu);

        return emulationMenu;
    }

    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        // Scale options
        JMenu scaleMenu = new JMenu("Scale");
        scaleMenu.setMnemonic(KeyEvent.VK_S);

        ButtonGroup scaleGroup = new ButtonGroup();
        for (int i = 1; i <= 4; i++) {
            JRadioButtonMenuItem scaleItem = new JRadioButtonMenuItem(i + "x");
            final int scale = i;
            scaleItem.addActionListener(e -> emulatorView.setScaleFactor(scale));
            if (i == 2)
                scaleItem.setSelected(true); // Default 2x
            scaleGroup.add(scaleItem);
            scaleMenu.add(scaleItem);
        }

        // Fullscreen
        JMenuItem fullscreenItem = new JMenuItem("Toggle Fullscreen");
        fullscreenItem.setMnemonic(KeyEvent.VK_F);
        fullscreenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        fullscreenItem.addActionListener(this::toggleFullscreen);

        // Always on top
        JCheckBoxMenuItem alwaysOnTopItem = new JCheckBoxMenuItem("Always on Top");
        alwaysOnTopItem.addActionListener(this::toggleAlwaysOnTop);

        viewMenu.add(scaleMenu);
        viewMenu.addSeparator();
        viewMenu.add(fullscreenItem);
        viewMenu.add(alwaysOnTopItem);

        return viewMenu;
    }

    private JMenu createGraphicsMenu() {
        JMenu graphicsMenu = new JMenu("Graphics");
        graphicsMenu.setMnemonic(KeyEvent.VK_G);

        // Color filter options
        JMenu filterMenu = new JMenu("Color Filter");
        ButtonGroup filterGroup = new ButtonGroup();

        for (ColorFilter filter : ColorFilter.values()) {
            JRadioButtonMenuItem filterItem = new JRadioButtonMenuItem(filter.toString());
            filterItem.addActionListener(e -> emulatorView.setColorFilter(filter));
            if (filter == ColorFilter.NONE)
                filterItem.setSelected(true);
            filterGroup.add(filterItem);
            filterMenu.add(filterItem);
        }

        // Display options
        JCheckBoxMenuItem scanlinesItem = new JCheckBoxMenuItem("Show Scanlines");
        scanlinesItem.addActionListener(e -> emulatorView.setShowScanlines(scanlinesItem.isSelected()));

        JCheckBoxMenuItem smoothScalingItem = new JCheckBoxMenuItem("Smooth Scaling");
        smoothScalingItem.addActionListener(e -> emulatorView.setSmoothScaling(smoothScalingItem.isSelected()));

        JCheckBoxMenuItem maintainAspectItem = new JCheckBoxMenuItem("Maintain Aspect Ratio");
        maintainAspectItem.setSelected(true);
        maintainAspectItem.addActionListener(e -> emulatorView.setMaintainAspectRatio(maintainAspectItem.isSelected()));

        // Graphics options dialog
        JMenuItem optionsItem = new JMenuItem("Graphics Options...");
        optionsItem.addActionListener(e -> graphicsDialog.setVisible(true));

        graphicsMenu.add(filterMenu);
        graphicsMenu.addSeparator();
        graphicsMenu.add(scanlinesItem);
        graphicsMenu.add(smoothScalingItem);
        graphicsMenu.add(maintainAspectItem);
        graphicsMenu.addSeparator();
        graphicsMenu.add(optionsItem);

        return graphicsMenu;
    }

    private JMenu createDebugMenu() {
        JMenu debugMenu = new JMenu("Debug");
        debugMenu.setMnemonic(KeyEvent.VK_D);

        JMenuItem debugViewItem = new JMenuItem("Debug View");
        debugViewItem.setMnemonic(KeyEvent.VK_D);
        debugViewItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
        debugViewItem.addActionListener(e -> debugView.setVisible(true));

        JMenuItem vramViewerItem = new JMenuItem("VRAM Viewer");
        vramViewerItem.setMnemonic(KeyEvent.VK_V);
        vramViewerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        vramViewerItem.addActionListener(e -> vramViewer.setVisible(true));

        JMenuItem dumpMemoryItem = new JMenuItem("Dump Memory...");
        dumpMemoryItem.addActionListener(this::dumpMemory);

        JMenuItem dumpRegistersItem = new JMenuItem("Dump Registers");
        dumpRegistersItem.addActionListener(this::dumpRegisters);

        debugMenu.add(debugViewItem);
        debugMenu.add(vramViewerItem);
        debugMenu.addSeparator();
        debugMenu.add(dumpMemoryItem);
        debugMenu.add(dumpRegistersItem);

        return debugMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem controlsItem = new JMenuItem("Controls");
        controlsItem.addActionListener(this::showControls);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(this::showAbout);

        helpMenu.add(controlsItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    // Action handlers
    private void openFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open ROM File");
        fileChooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("ROM Files", "gb", "gbc", "zip");
        fileChooser.addChoosableFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                loadROM(selectedFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error loading ROM:\n" + ex.getMessage(),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadROM(File file) {
        try {
            // Get the parent window
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window instanceof JFrame) {
                JFrame frame = (JFrame) window;
                if (frame.getTitle().contains("EmulatorWindow")) {
                    // Use reflection or direct access to call EmulatorWindow.loadROM
                    // For now, just use the GBC directly
                    gbc.insertCartridge(file.getAbsolutePath());
                }
            } else {
                gbc.insertCartridge(file.getAbsolutePath());
            }

            JOptionPane.showMessageDialog(this,
                    "ROM loaded successfully:\n" + file.getName(),
                    "ROM Loaded",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading ROM:\n" + ex.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveState(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save State");
        fileChooser.setSelectedFile(new File("savestate.sav"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement save state functionality
            JOptionPane.showMessageDialog(this, "Save state functionality not yet implemented", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadState(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load State");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement load state functionality
            JOptionPane.showMessageDialog(this, "Load state functionality not yet implemented", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void exportScreenshot(ActionEvent e) {
        emulatorView.takeScreenshot();
    }

    private void exit(ActionEvent e) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispose();
        }
        System.exit(0);
    }

    private void togglePause(ActionEvent e) {
        // TODO: Implement pause/resume functionality
        JOptionPane.showMessageDialog(this, "Pause/Resume functionality not yet fully implemented", "Info",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetEmulator(ActionEvent e) {
        gbc.reset();
        JOptionPane.showMessageDialog(this, "Emulator reset successfully!", "Reset", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setEmulationSpeed(float speed) {
        // TODO: Implement speed control
        JOptionPane.showMessageDialog(this, String.format("Speed set to %.0f%% (not yet implemented)", speed * 100),
                "Speed", JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleFullscreen(ActionEvent e) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            JFrame frame = (JFrame) window;
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

            if (device.getFullScreenWindow() == null) {
                frame.dispose();
                frame.setUndecorated(true);
                device.setFullScreenWindow(frame);
            } else {
                device.setFullScreenWindow(null);
                frame.dispose();
                frame.setUndecorated(false);
                frame.setVisible(true);
            }
        }
    }

    private void toggleAlwaysOnTop(ActionEvent e) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            JFrame frame = (JFrame) window;
            boolean alwaysOnTop = ((JCheckBoxMenuItem) e.getSource()).isSelected();
            frame.setAlwaysOnTop(alwaysOnTop);
        }
    }

    private void dumpMemory(ActionEvent e) {
        String address = JOptionPane.showInputDialog(this, "Enter start address (hex):", "0000");
        if (address != null && !address.trim().isEmpty()) {
            try {
                int startAddr = Integer.parseInt(address, 16);
                String dump = gbc.getCpu().dumpMemory(startAddr, startAddr + 0xFF);
                JTextArea textArea = new JTextArea(dump);
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(600, 400));
                JOptionPane.showMessageDialog(this, scrollPane, "Memory Dump", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid address format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void dumpRegisters(ActionEvent e) {
        String registers = gbc.getCpu().getRegisters().toString();
        JTextArea textArea = new JTextArea(registers);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(300, 250));
        JOptionPane.showMessageDialog(this, scrollPane, "Register Dump", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showControls(ActionEvent e) {
        String controlsText = """
                Game Boy Controls:

                D-Pad:        Arrow Keys
                A Button:     Z Key
                B Button:     X Key
                Start:        Enter Key
                Select:       Space Key

                Emulator Controls:

                Pause:        P Key
                Reset:        Ctrl+R
                Debug View:   F12
                VRAM Viewer:  F5
                Fullscreen:   F11
                Open ROM:     Ctrl+O
                Save State:   Ctrl+S
                Load State:   Ctrl+L
                """;

        JTextArea textArea = new JTextArea(controlsText);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        JOptionPane.showMessageDialog(this, scrollPane, "Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout(ActionEvent e) {
        String aboutText = """
                Game Boy Color Emulator
                Version 2.0 Enhanced

                A Java-based Game Boy Color emulator with
                enhanced graphics, debugging, and UI features.

                Features:
                • Accurate CPU emulation
                • Enhanced graphics with filters
                • Comprehensive debug tools
                • VRAM and memory viewers
                • Save/Load states
                • Multiple scaling options

                Built with Java 19+ and Swing
                """;

        JOptionPane.showMessageDialog(this, aboutText, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    // Graphics Options Dialog
    private class GraphicsOptionsDialog extends JDialog {
        public GraphicsOptionsDialog() {
            super((Frame) SwingUtilities.getWindowAncestor(MenuBar.this), "Graphics Options", true);
            initializeDialog();
        }

        private void initializeDialog() {
            setSize(400, 300);
            setLocationRelativeTo(getParent());
            setLayout(new BorderLayout());

            JPanel mainPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);

            // Color filter selection
            gbc.gridx = 0;
            gbc.gridy = 0;
            mainPanel.add(new JLabel("Color Filter:"), gbc);

            gbc.gridx = 1;
            JComboBox<ColorFilter> filterCombo = new JComboBox<>(ColorFilter.values());
            filterCombo.setSelectedItem(emulatorView.getColorFilter());
            filterCombo
                    .addActionListener(e -> emulatorView.setColorFilter((ColorFilter) filterCombo.getSelectedItem()));
            mainPanel.add(filterCombo, gbc);

            // Scale factor
            gbc.gridx = 0;
            gbc.gridy = 1;
            mainPanel.add(new JLabel("Scale Factor:"), gbc);

            gbc.gridx = 1;
            JSpinner scaleSpinner = new JSpinner(new SpinnerNumberModel(emulatorView.getScaleFactor(), 1, 8, 1));
            scaleSpinner.addChangeListener(e -> emulatorView.setScaleFactor((Integer) scaleSpinner.getValue()));
            mainPanel.add(scaleSpinner, gbc);

            // Checkboxes
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            JCheckBox scanlinesCheck = new JCheckBox("Show Scanlines", emulatorView.isShowScanlines());
            scanlinesCheck.addActionListener(e -> emulatorView.setShowScanlines(scanlinesCheck.isSelected()));
            mainPanel.add(scanlinesCheck, gbc);

            gbc.gridy = 3;
            JCheckBox smoothCheck = new JCheckBox("Smooth Scaling", emulatorView.isSmoothScaling());
            smoothCheck.addActionListener(e -> emulatorView.setSmoothScaling(smoothCheck.isSelected()));
            mainPanel.add(smoothCheck, gbc);

            gbc.gridy = 4;
            JCheckBox aspectCheck = new JCheckBox("Maintain Aspect Ratio", emulatorView.isMaintainAspectRatio());
            aspectCheck.addActionListener(e -> emulatorView.setMaintainAspectRatio(aspectCheck.isSelected()));
            mainPanel.add(aspectCheck, gbc);

            add(mainPanel, BorderLayout.CENTER);

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton resetButton = new JButton("Reset to Defaults");
            resetButton.addActionListener(e -> {
                emulatorView.resetView();
                dispose();
            });

            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> dispose());

            buttonPanel.add(resetButton);
            buttonPanel.add(closeButton);
            add(buttonPanel, BorderLayout.SOUTH);
        }
    }
}