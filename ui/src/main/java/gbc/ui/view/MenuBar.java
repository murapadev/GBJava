package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;
import gbc.ui.controller.EmulatorController;
import gbc.ui.view.EmulatorView.ColorFilter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * MenuBar with comprehensive emulation controls and graphics options
 */
public class MenuBar extends JMenuBar {

    private final GameBoyColor gbc;
    private final DebugView debugView;
    private final VRAMViewer vramViewer;
    private final EmulatorView emulatorView;
    private EmulatorController controller;

    // Recent ROMs functionality
    private static final int MAX_RECENT_ROMS = 10;
    private final List<File> recentROMs;
    private JMenu recentMenu;

    // Graphics options dialog
    private GraphicsOptionsDialog graphicsDialog;

    public MenuBar(GameBoyColor gbc, DebugView debugView, VRAMViewer vramViewer,
            EmulatorView emulatorView) {
        this.gbc = gbc;
        this.debugView = debugView;
        this.vramViewer = vramViewer;
        this.emulatorView = emulatorView;
        this.recentROMs = new ArrayList<>();
        this.graphicsDialog = new GraphicsOptionsDialog();

        initializeMenus();
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
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
        recentMenu = new JMenu("Recent ROMs");
        recentMenu.setMnemonic(KeyEvent.VK_R);
        updateRecentROMsMenu();

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
            loadROM(selectedFile).whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
                if (throwable == null) {
                    addRecentROM(selectedFile);
                    JOptionPane.showMessageDialog(this,
                            "ROM loaded successfully:\n" + selectedFile.getName(),
                            "ROM Loaded",
                            JOptionPane.INFORMATION_MESSAGE);
                    if (controller == null) {
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

    private CompletableFuture<Void> loadROM(File file) {
        setBusyCursor(true);
        return submitRomLoad(file).whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> setBusyCursor(false)));
    }

    private void saveState(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save State");
        fileChooser.setSelectedFile(new File("game_state.sav"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();

                // Create save state data
                StringBuilder saveData = new StringBuilder();
                saveData.append("# Game Boy Color Emulator Save State\n");
                saveData.append("# Version: 1.0\n");
                saveData.append("# Timestamp: ").append(System.currentTimeMillis()).append("\n\n");

                // Save CPU registers
                saveData.append("[CPU_REGISTERS]\n");
                Registers regs = gbc.getCpu().getRegisters();
                saveData.append("A=").append(regs.getRegister("A") & 0xFF).append("\n");
                saveData.append("B=").append(regs.getRegister("B") & 0xFF).append("\n");
                saveData.append("C=").append(regs.getRegister("C") & 0xFF).append("\n");
                saveData.append("D=").append(regs.getRegister("D") & 0xFF).append("\n");
                saveData.append("E=").append(regs.getRegister("E") & 0xFF).append("\n");
                saveData.append("F=").append(regs.getRegister("F") & 0xFF).append("\n");
                saveData.append("H=").append(regs.getRegister("H") & 0xFF).append("\n");
                saveData.append("L=").append(regs.getRegister("L") & 0xFF).append("\n");
                saveData.append("PC=").append(regs.getPC() & 0xFFFF).append("\n");
                saveData.append("SP=").append(regs.getSP() & 0xFFFF).append("\n\n");

                // Save key memory regions (VRAM, WRAM, HRAM)
                saveData.append("[MEMORY]\n");
                Memory memory = gbc.getMemory();

                // Save VRAM (0x8000-0x9FFF)
                saveData.append("VRAM=");
                for (int addr = 0x8000; addr <= 0x9FFF; addr++) {
                    saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
                }
                saveData.append("\n");

                // Save WRAM (0xC000-0xDFFF)
                saveData.append("WRAM=");
                for (int addr = 0xC000; addr <= 0xDFFF; addr++) {
                    saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
                }
                saveData.append("\n");

                // Save HRAM (0xFF80-0xFFFE)
                saveData.append("HRAM=");
                for (int addr = 0xFF80; addr <= 0xFFFE; addr++) {
                    saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
                }
                saveData.append("\n");

                // Save I/O registers (0xFF00-0xFF7F, excluding sound registers for simplicity)
                saveData.append("IO=");
                for (int addr = 0xFF00; addr <= 0xFF7F; addr++) {
                    if (addr < 0xFF10 || addr > 0xFF3F) { // Skip sound registers
                        saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
                    } else {
                        saveData.append("00"); // Placeholder for sound registers
                    }
                }
                saveData.append("\n\n");

                // Write to file
                Files.write(selectedFile.toPath(), saveData.toString().getBytes());

                JOptionPane.showMessageDialog(this, "State saved successfully to: " + selectedFile.getName(),
                        "Save State", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving state: " + ex.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadState(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load State");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();

                // Read save state data
                String saveData = Files.readString(selectedFile.toPath());
                String[] lines = saveData.split("\n");

                Registers regs = gbc.getCpu().getRegisters();
                Memory memory = gbc.getMemory();

                boolean inCpuSection = false;
                boolean inMemorySection = false;

                for (String line : lines) {
                    line = line.trim();

                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    if (line.equals("[CPU_REGISTERS]")) {
                        inCpuSection = true;
                        inMemorySection = false;
                        continue;
                    } else if (line.equals("[MEMORY]")) {
                        inCpuSection = false;
                        inMemorySection = true;
                        continue;
                    }

                    if (inCpuSection) {
                        // Parse CPU register data
                        if (line.startsWith("A=")) {
                            regs.setRegister("A", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("B=")) {
                            regs.setRegister("B", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("C=")) {
                            regs.setRegister("C", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("D=")) {
                            regs.setRegister("D", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("E=")) {
                            regs.setRegister("E", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("F=")) {
                            regs.setRegister("F", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("H=")) {
                            regs.setRegister("H", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("L=")) {
                            regs.setRegister("L", (byte) Integer.parseInt(line.substring(2), 16));
                        } else if (line.startsWith("PC=")) {
                            regs.setPC(Integer.parseInt(line.substring(3), 16));
                        } else if (line.startsWith("SP=")) {
                            regs.setSP(Integer.parseInt(line.substring(3), 16));
                        }
                    } else if (inMemorySection) {
                        // Parse memory data
                        if (line.startsWith("VRAM=")) {
                            String vramData = line.substring(5);
                            for (int i = 0; i < vramData.length() / 2 && (0x8000 + i) <= 0x9FFF; i++) {
                                int value = Integer.parseInt(vramData.substring(i * 2, i * 2 + 2), 16);
                                memory.writeByte(0x8000 + i, (byte) value);
                            }
                        } else if (line.startsWith("WRAM=")) {
                            String wramData = line.substring(5);
                            for (int i = 0; i < wramData.length() / 2 && (0xC000 + i) <= 0xDFFF; i++) {
                                int value = Integer.parseInt(wramData.substring(i * 2, i * 2 + 2), 16);
                                memory.writeByte(0xC000 + i, (byte) value);
                            }
                        } else if (line.startsWith("HRAM=")) {
                            String hramData = line.substring(5);
                            for (int i = 0; i < hramData.length() / 2 && (0xFF80 + i) <= 0xFFFE; i++) {
                                int value = Integer.parseInt(hramData.substring(i * 2, i * 2 + 2), 16);
                                memory.writeByte(0xFF80 + i, (byte) value);
                            }
                        } else if (line.startsWith("IO=")) {
                            String ioData = line.substring(3);
                            for (int i = 0; i < ioData.length() / 2 && (0xFF00 + i) <= 0xFF7F; i++) {
                                if (0xFF00 + i < 0xFF10 || 0xFF00 + i > 0xFF3F) { // Skip sound registers
                                    int value = Integer.parseInt(ioData.substring(i * 2, i * 2 + 2), 16);
                                    memory.writeByte(0xFF00 + i, (byte) value);
                                }
                            }
                        }
                    }
                }

                JOptionPane.showMessageDialog(this, "State loaded successfully from: " + selectedFile.getName(),
                        "Load State", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading state: " + ex.getMessage(),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
            }
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
        if (controller != null) {
            if (controller.isPaused()) {
                controller.resume();
                JOptionPane.showMessageDialog(this, "Emulator resumed!", "Resume", JOptionPane.INFORMATION_MESSAGE);
            } else {
                controller.pause();
                JOptionPane.showMessageDialog(this, "Emulator paused!", "Pause", JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            if (gbc.isPaused()) {
                gbc.resume();
                JOptionPane.showMessageDialog(this, "Emulator resumed!", "Resume", JOptionPane.INFORMATION_MESSAGE);
            } else {
                gbc.pause();
                JOptionPane.showMessageDialog(this, "Emulator paused!", "Pause", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void resetEmulator(ActionEvent e) {
        if (controller != null) {
            controller.reset();
        } else {
            gbc.reset();
        }
        JOptionPane.showMessageDialog(this, "Emulator reset successfully!", "Reset", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setEmulationSpeed(float speed) {
        if (controller != null) {
            controller.getGameBoyColor().setSpeedMultiplier(speed);
        } else {
            gbc.setSpeedMultiplier(speed);
        }
        JOptionPane.showMessageDialog(this, String.format("Emulation speed set to %.0f%%", speed * 100),
                "Speed Control", JOptionPane.INFORMATION_MESSAGE);
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

    // Recent ROMs functionality
    private void updateRecentROMsMenu() {
        recentMenu.removeAll();

        if (recentROMs.isEmpty()) {
            JMenuItem noRecentItem = new JMenuItem("No recent ROMs");
            noRecentItem.setEnabled(false);
            recentMenu.add(noRecentItem);
        } else {
            for (int i = 0; i < recentROMs.size(); i++) {
                File romFile = recentROMs.get(i);
                String displayName = (i + 1) + ". " + romFile.getName();
                JMenuItem romItem = new JMenuItem(displayName);
                romItem.setToolTipText(romFile.getAbsolutePath());
                romItem.addActionListener(e -> loadRecentROM(romFile));
                recentMenu.add(romItem);
            }

            recentMenu.addSeparator();
            JMenuItem clearItem = new JMenuItem("Clear Recent");
            clearItem.addActionListener(e -> clearRecentROMs());
            recentMenu.add(clearItem);
        }
    }

    private void addRecentROM(File romFile) {
        // Remove if already exists
        recentROMs.removeIf(file -> file.getAbsolutePath().equals(romFile.getAbsolutePath()));

        // Add to beginning of list
        recentROMs.add(0, romFile);

        // Keep only MAX_RECENT_ROMS items
        while (recentROMs.size() > MAX_RECENT_ROMS) {
            recentROMs.remove(recentROMs.size() - 1);
        }

        updateRecentROMsMenu();
    }

    private void loadRecentROM(File romFile) {
        if (!romFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "ROM file not found:\n" + romFile.getAbsolutePath(),
                    "File Not Found",
                    JOptionPane.WARNING_MESSAGE);
            recentROMs.remove(romFile);
            updateRecentROMsMenu();
            return;
        }

        loadROM(romFile).whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
            if (throwable == null) {
                addRecentROM(romFile);
                JOptionPane.showMessageDialog(this,
                        "ROM loaded successfully:\n" + romFile.getName(),
                        "ROM Loaded",
                        JOptionPane.INFORMATION_MESSAGE);
                if (controller == null) {
                    emulatorView.repaint();
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Error loading ROM:\n" + describeError(throwable),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
                recentROMs.remove(romFile);
                updateRecentROMsMenu();
            }
        }));
    }

    private CompletableFuture<Void> submitRomLoad(File file) {
        if (controller != null) {
            return controller.loadRomAsync(file.getAbsolutePath());
        }

        return CompletableFuture.runAsync(() -> {
            gbc.insertCartridge(file.getAbsolutePath());
            gbc.reset();
        });
    }

    private void setBusyCursor(boolean busy) {
        Cursor cursor = Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR);
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.setCursor(cursor);
        } else {
            setCursor(cursor);
        }
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

    private void clearRecentROMs() {
        recentROMs.clear();
        updateRecentROMsMenu();
    }
}
