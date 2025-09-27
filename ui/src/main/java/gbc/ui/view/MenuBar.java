package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;
import gbc.ui.controller.EmulatorController;
import gbc.ui.view.EmulatorView.ColorFilter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Application menu bar driving emulator, view and debug controls.
 * Exposes integration hooks so the primary toolbar can reuse the same actions
 * and keeps visual state (pause, speed, display options) in sync.
 */
public class MenuBar extends JMenuBar {

    public static final float[] SPEED_VALUES = { 0.25f, 0.5f, 1.0f, 2.0f, 4.0f };
    public static final int[] SCALE_OPTIONS = { 1, 2, 3, 4, 5, 6 };

    private static final int MAX_RECENT_ROMS = 10;

    private final EmulatorWindow window;
    private final GameBoyColor gbc;
    private final EmulatorView emulatorView;

    private EmulatorController controller;

    private final List<File> recentROMs = new ArrayList<>();
    private JMenu recentMenu;
    private final GraphicsOptionsDialog graphicsDialog;

    private JMenuItem pauseItem;
    private JCheckBoxMenuItem alwaysOnTopItem;
    private JCheckBoxMenuItem scanlinesItem;
    private JCheckBoxMenuItem smoothScalingItem;
    private JCheckBoxMenuItem maintainAspectItem;

    private final Map<Integer, JRadioButtonMenuItem> scaleItems = new LinkedHashMap<>();
    private final Map<Float, JRadioButtonMenuItem> speedItems = new LinkedHashMap<>();
    private final Map<ColorFilter, JRadioButtonMenuItem> filterItems = new EnumMap<>(ColorFilter.class);

    private boolean suppressScaleEvents;
    private boolean suppressSpeedEvents;
    private boolean suppressDisplayEvents;
    private boolean suppressFilterEvents;

    public MenuBar(EmulatorWindow window, GameBoyColor gbc, EmulatorView emulatorView) {
        this.window = window;
        this.gbc = gbc;
        this.emulatorView = emulatorView;
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

        JMenuItem openItem = new JMenuItem("Open ROM...");
        openItem.setMnemonic(KeyEvent.VK_O);
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openRomDialog());
        fileMenu.add(openItem);

        recentMenu = new JMenu("Recent ROMs");
        recentMenu.setMnemonic(KeyEvent.VK_R);
        updateRecentROMsMenu();
        fileMenu.add(recentMenu);
        fileMenu.addSeparator();

        JMenuItem saveStateItem = new JMenuItem("Save State...");
        saveStateItem.setMnemonic(KeyEvent.VK_S);
        saveStateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        saveStateItem.addActionListener(e -> saveStateDialog());
        fileMenu.add(saveStateItem);

        JMenuItem loadStateItem = new JMenuItem("Load State...");
        loadStateItem.setMnemonic(KeyEvent.VK_L);
        loadStateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
        loadStateItem.addActionListener(e -> loadStateDialog());
        fileMenu.add(loadStateItem);

        fileMenu.addSeparator();

        JMenuItem exportScreenshotItem = new JMenuItem("Export Screenshot...");
        exportScreenshotItem.addActionListener(e -> exportScreenshot());
        fileMenu.add(exportScreenshotItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic(KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitItem);

        return fileMenu;
    }

    private JMenu createEmulationMenu() {
        JMenu emulationMenu = new JMenu("Emulation");
        emulationMenu.setMnemonic(KeyEvent.VK_E);

        pauseItem = new JMenuItem("Pause");
        pauseItem.setMnemonic(KeyEvent.VK_P);
        pauseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
        pauseItem.addActionListener(e -> togglePause());
        emulationMenu.add(pauseItem);

        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.setMnemonic(KeyEvent.VK_R);
        resetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK));
        resetItem.addActionListener(e -> resetEmulator());
        emulationMenu.add(resetItem);

        emulationMenu.addSeparator();

        JMenu speedMenu = new JMenu("Speed");
        speedMenu.setMnemonic(KeyEvent.VK_S);

        ButtonGroup speedGroup = new ButtonGroup();
        for (float speed : SPEED_VALUES) {
            String label = speed == 1.0f ? "100% (Normal)" : String.format("%.0f%%", speed * 100f);
            JRadioButtonMenuItem speedItem = new JRadioButtonMenuItem(label);
            if (speed == 1.0f) {
                speedItem.setSelected(true);
            }
            speedItem.addActionListener(e -> {
                if (suppressSpeedEvents) {
                    return;
                }
                setEmulationSpeed(speed, true);
            });
            speedItems.put(speed, speedItem);
            speedGroup.add(speedItem);
            speedMenu.add(speedItem);
        }

        emulationMenu.add(speedMenu);
        return emulationMenu;
    }

    private JMenu createViewMenu() {
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JMenu scaleMenu = new JMenu("Scale");
        scaleMenu.setMnemonic(KeyEvent.VK_S);

        ButtonGroup scaleGroup = new ButtonGroup();
        for (int scale : SCALE_OPTIONS) {
            JRadioButtonMenuItem scaleItem = new JRadioButtonMenuItem(scale + "x");
            if (scale == emulatorView.getScaleFactor()) {
                scaleItem.setSelected(true);
            }
            scaleItem.addActionListener(e -> {
                if (suppressScaleEvents) {
                    return;
                }
                setScale(scale, true);
            });
            scaleItems.put(scale, scaleItem);
            scaleGroup.add(scaleItem);
            scaleMenu.add(scaleItem);
        }

        viewMenu.add(scaleMenu);
        viewMenu.addSeparator();

        JMenuItem fullscreenItem = new JMenuItem("Toggle Fullscreen");
        fullscreenItem.setMnemonic(KeyEvent.VK_F);
        fullscreenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0));
        fullscreenItem.addActionListener(e -> toggleFullscreen());
        viewMenu.add(fullscreenItem);

        alwaysOnTopItem = new JCheckBoxMenuItem("Always on Top");
        alwaysOnTopItem.addActionListener(e -> setAlwaysOnTop(alwaysOnTopItem.isSelected()));
        viewMenu.add(alwaysOnTopItem);

        return viewMenu;
    }

    private JMenu createGraphicsMenu() {
        JMenu graphicsMenu = new JMenu("Graphics");
        graphicsMenu.setMnemonic(KeyEvent.VK_G);

        JMenu filterMenu = new JMenu("Color Filter");
        ButtonGroup filterGroup = new ButtonGroup();
        for (ColorFilter filter : ColorFilter.values()) {
            JRadioButtonMenuItem filterItem = new JRadioButtonMenuItem(filter.toString());
            if (filter == emulatorView.getColorFilter()) {
                filterItem.setSelected(true);
            }
            filterItem.addActionListener(e -> {
                if (suppressFilterEvents) {
                    return;
                }
                setColorFilter(filter, true);
            });
            filterItems.put(filter, filterItem);
            filterGroup.add(filterItem);
            filterMenu.add(filterItem);
        }

        graphicsMenu.add(filterMenu);
        graphicsMenu.addSeparator();

        scanlinesItem = new JCheckBoxMenuItem("Show Scanlines", emulatorView.isShowScanlines());
        scanlinesItem.addActionListener(e -> {
            if (suppressDisplayEvents) {
                return;
            }
            setScanlines(scanlinesItem.isSelected(), true);
        });
        graphicsMenu.add(scanlinesItem);

        smoothScalingItem = new JCheckBoxMenuItem("Smooth Scaling", emulatorView.isSmoothScaling());
        smoothScalingItem.addActionListener(e -> {
            if (suppressDisplayEvents) {
                return;
            }
            setSmoothScaling(smoothScalingItem.isSelected(), true);
        });
        graphicsMenu.add(smoothScalingItem);

        maintainAspectItem = new JCheckBoxMenuItem("Maintain Aspect Ratio", emulatorView.isMaintainAspectRatio());
        maintainAspectItem.addActionListener(e -> {
            if (suppressDisplayEvents) {
                return;
            }
            setMaintainAspect(maintainAspectItem.isSelected(), true);
        });
        graphicsMenu.add(maintainAspectItem);

        graphicsMenu.addSeparator();

        JMenuItem optionsItem = new JMenuItem("Graphics Options...");
        optionsItem.addActionListener(e -> showGraphicsOptions());
        graphicsMenu.add(optionsItem);

        return graphicsMenu;
    }

    private JMenu createDebugMenu() {
        JMenu debugMenu = new JMenu("Debug");
        debugMenu.setMnemonic(KeyEvent.VK_D);

        JMenuItem debugViewItem = new JMenuItem("Debug View");
        debugViewItem.setMnemonic(KeyEvent.VK_D);
        debugViewItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0));
        debugViewItem.setEnabled(window.hasDebugView());
        debugViewItem.addActionListener(e -> window.openDebugView());
        debugMenu.add(debugViewItem);

        JMenuItem vramViewerItem = new JMenuItem("VRAM Viewer");
        vramViewerItem.setMnemonic(KeyEvent.VK_V);
        vramViewerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        vramViewerItem.setEnabled(window.hasVRAMViewer());
        vramViewerItem.addActionListener(e -> window.openVRAMViewer());
        debugMenu.add(vramViewerItem);

        debugMenu.addSeparator();

        JMenuItem dumpMemoryItem = new JMenuItem("Dump Memory...");
        dumpMemoryItem.addActionListener(e -> dumpMemory());
        debugMenu.add(dumpMemoryItem);

        JMenuItem dumpRegistersItem = new JMenuItem("Dump Registers");
        dumpRegistersItem.addActionListener(e -> dumpRegisters());
        debugMenu.add(dumpRegistersItem);

        return debugMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem controlsItem = new JMenuItem("Controls");
        controlsItem.addActionListener(e -> showControlsDialog());
        helpMenu.add(controlsItem);

        helpMenu.addSeparator();

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    private void openRomDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open ROM File");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("ROM Files", "gb", "gbc", "zip"));

        int result = chooser.showOpenDialog(window);
        if (result == JFileChooser.APPROVE_OPTION) {
            performRomLoad(chooser.getSelectedFile(), true);
        }
    }

    private void performRomLoad(File file, boolean updateRecents) {
        if (file == null) {
            return;
        }
        setBusyCursor(true);
        submitRomLoad(file).whenComplete((unused, throwable) -> SwingUtilities.invokeLater(() -> {
            setBusyCursor(false);
            if (throwable == null) {
                if (updateRecents) {
                    addRecentROM(file);
                }
                window.setStatusText("ROM loaded: " + file.getName());
                if (controller == null) {
                    window.setTitle("Game Boy Color Emulator - " + file.getName());
                    emulatorView.repaint();
                }
                window.refreshUiState(true);
            } else {
                JOptionPane.showMessageDialog(window,
                        "Error loading ROM:\n" + describeError(throwable),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }));
    }

    private void saveStateDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save State");
        chooser.setSelectedFile(new File("game_state.sav"));

        if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = chooser.getSelectedFile();
                StringBuilder saveData = new StringBuilder();
                saveData.append("# Game Boy Color Emulator Save State\n");
                saveData.append("# Version: 1.0\n");
                saveData.append("# Timestamp: ").append(System.currentTimeMillis()).append("\n\n");

                saveCpuRegisters(saveData);
                saveMemoryRegions(saveData);

                Files.write(selectedFile.toPath(), saveData.toString().getBytes());
                window.setStatusText("State saved: " + selectedFile.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(window,
                        "Error saving state: " + ex.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadStateDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load State");

        if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = chooser.getSelectedFile();
                String saveData = Files.readString(selectedFile.toPath());
                restoreStateFromString(saveData);
                window.setStatusText("State loaded: " + selectedFile.getName());
                window.refreshUiState(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(window,
                        "Error loading state: " + ex.getMessage(),
                        "Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportScreenshot() {
        emulatorView.takeScreenshot();
        window.setStatusText("Screenshot captured");
    }

    private void exitApplication() {
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor instanceof JFrame frame) {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        } else {
            System.exit(0);
        }
    }

    private void togglePause() {
        if (controller != null) {
            controller.togglePause();
            window.setStatusText(controller.isPaused() ? "State: Paused" : "State: Running");
        } else {
            if (gbc.isPaused()) {
                gbc.resume();
            } else {
                gbc.pause();
            }
            window.onPauseStateChanged(gbc.isPaused());
            window.setStatusText(gbc.isPaused() ? "State: Paused" : "State: Running");
        }
        window.refreshUiState(true);
    }

    private void resetEmulator() {
        if (controller != null) {
            controller.reset();
        } else {
            gbc.reset();
            emulatorView.repaint();
        }
        window.setStatusText("Emulator reset");
        window.refreshUiState(true);
    }

    private void setEmulationSpeed(float speed, boolean fromMenu) {
        if (controller != null) {
            controller.getGameBoyColor().setSpeedMultiplier(speed);
        } else {
            gbc.setSpeedMultiplier(speed);
        }
        window.onSpeedChanged(speed);
        window.setStatusText(String.format("Speed: %.0f%%", speed * 100f));
        if (!fromMenu) {
            synchronizeSpeedFromWindow(speed);
        }
    }

    private void toggleFullscreen() {
        window.toggleFullscreen();
    }

    private void setAlwaysOnTop(boolean selected) {
        window.setAlwaysOnTop(selected);
        window.setStatusText(selected ? "Always on top enabled" : "Always on top disabled");
    }

    private void setScale(int scale, boolean fromMenu) {
        emulatorView.setScaleFactor(scale);
        window.setStatusText("Scale: " + scale + "x");
        window.refreshUiState(!fromMenu);
    }

    private void setScanlines(boolean enabled, boolean fromMenu) {
        emulatorView.setShowScanlines(enabled);
        window.setStatusText(enabled ? "Scanlines enabled" : "Scanlines disabled");
        window.onDisplaySettingsChanged(!fromMenu);
    }

    private void setSmoothScaling(boolean enabled, boolean fromMenu) {
        emulatorView.setSmoothScaling(enabled);
        window.setStatusText(enabled ? "Smooth scaling enabled" : "Smooth scaling disabled");
        window.onDisplaySettingsChanged(!fromMenu);
    }

    private void setMaintainAspect(boolean maintain, boolean fromMenu) {
        emulatorView.setMaintainAspectRatio(maintain);
        window.setStatusText(maintain ? "Aspect ratio locked" : "Aspect ratio free");
        window.onDisplaySettingsChanged(!fromMenu);
    }

    private void setColorFilter(ColorFilter filter, boolean fromMenu) {
        emulatorView.setColorFilter(filter);
        window.setStatusText("Filter: " + filter);
        window.onColorFilterChanged(filter, !fromMenu);
    }

    private void showGraphicsOptions() {
        graphicsDialog.showDialog();
    }

    private void dumpMemory() {
        String address = JOptionPane.showInputDialog(window, "Enter start address (hex):", "0000");
        if (address != null && !address.trim().isEmpty()) {
            try {
                int startAddr = Integer.parseInt(address, 16);
                String dump = gbc.getCpu().dumpMemory(startAddr, startAddr + 0xFF);
                JTextArea textArea = new JTextArea(dump);
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(600, 400));
                JOptionPane.showMessageDialog(window, scrollPane, "Memory Dump", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(window, "Invalid address format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void dumpRegisters() {
        String registers = gbc.getCpu().getRegisters().toString();
        JTextArea textArea = new JTextArea(registers);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(300, 250));
        JOptionPane.showMessageDialog(window, scrollPane, "Register Dump", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showControlsDialog() {
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
        scrollPane.setPreferredSize(new Dimension(420, 320));
        JOptionPane.showMessageDialog(window, scrollPane, "Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAboutDialog() {
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

        JOptionPane.showMessageDialog(window, aboutText, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateRecentROMsMenu() {
        recentMenu.removeAll();

        if (recentROMs.isEmpty()) {
            JMenuItem noRecentItem = new JMenuItem("No recent ROMs");
            noRecentItem.setEnabled(false);
            recentMenu.add(noRecentItem);
            return;
        }

        int index = 1;
        for (File romFile : recentROMs) {
            JMenuItem romItem = new JMenuItem(index + ". " + romFile.getName());
            romItem.setToolTipText(romFile.getAbsolutePath());
            romItem.addActionListener(e -> loadRecentROM(romFile));
            recentMenu.add(romItem);
            index++;
        }

        recentMenu.addSeparator();
        JMenuItem clearItem = new JMenuItem("Clear Recent");
        clearItem.addActionListener(e -> clearRecentROMs());
        recentMenu.add(clearItem);
    }

    private void addRecentROM(File romFile) {
        recentROMs.removeIf(file -> file.getAbsolutePath().equals(romFile.getAbsolutePath()));
        recentROMs.add(0, romFile);
        while (recentROMs.size() > MAX_RECENT_ROMS) {
            recentROMs.remove(recentROMs.size() - 1);
        }
        updateRecentROMsMenu();
    }

    private void loadRecentROM(File romFile) {
        if (!romFile.exists()) {
            JOptionPane.showMessageDialog(window,
                    "ROM file not found:\n" + romFile.getAbsolutePath(),
                    "File Not Found",
                    JOptionPane.WARNING_MESSAGE);
            recentROMs.remove(romFile);
            updateRecentROMsMenu();
            return;
        }

        performRomLoad(romFile, true);
    }

    private void clearRecentROMs() {
        recentROMs.clear();
        updateRecentROMsMenu();
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

    private void saveCpuRegisters(StringBuilder saveData) {
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
    }

    private void saveMemoryRegions(StringBuilder saveData) {
        saveData.append("[MEMORY]\n");
        Memory memory = gbc.getMemory();

        saveData.append("VRAM=");
        for (int addr = 0x8000; addr <= 0x9FFF; addr++) {
            saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
        }
        saveData.append("\n");

        saveData.append("WRAM=");
        for (int addr = 0xC000; addr <= 0xDFFF; addr++) {
            saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
        }
        saveData.append("\n");

        saveData.append("HRAM=");
        for (int addr = 0xFF80; addr <= 0xFFFE; addr++) {
            saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
        }
        saveData.append("\n");

        saveData.append("IO=");
        for (int addr = 0xFF00; addr <= 0xFF7F; addr++) {
            if (addr < 0xFF10 || addr > 0xFF3F) {
                saveData.append(String.format("%02X", memory.readByte(addr) & 0xFF));
            } else {
                saveData.append("00");
            }
        }
        saveData.append("\n\n");
    }

    private void restoreStateFromString(String saveData) throws Exception {
        String[] lines = saveData.split("\n");
        Registers regs = gbc.getCpu().getRegisters();
        Memory memory = gbc.getMemory();

        boolean inCpuSection = false;
        boolean inMemorySection = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.equals("[CPU_REGISTERS]")) {
                inCpuSection = true;
                inMemorySection = false;
                continue;
            }
            if (line.equals("[MEMORY]")) {
                inCpuSection = false;
                inMemorySection = true;
                continue;
            }

            if (inCpuSection) {
                applyCpuRegisterLine(regs, line);
            } else if (inMemorySection) {
                applyMemoryLine(memory, line);
            }
        }
    }

    private void applyCpuRegisterLine(Registers regs, String line) {
        if (line.startsWith("PC=")) {
            regs.setPC(Integer.parseInt(line.substring(3), 16));
        } else if (line.startsWith("SP=")) {
            regs.setSP(Integer.parseInt(line.substring(3), 16));
        } else if (line.length() >= 3 && line.charAt(1) == '=') {
            String register = line.substring(0, 1);
            int value = Integer.parseInt(line.substring(2), 16);
            regs.setRegister(register, (byte) value);
        }
    }

    private void applyMemoryLine(Memory memory, String line) {
        if (line.startsWith("VRAM=")) {
            writeMemoryRange(memory, line.substring(5), 0x8000);
        } else if (line.startsWith("WRAM=")) {
            writeMemoryRange(memory, line.substring(5), 0xC000);
        } else if (line.startsWith("HRAM=")) {
            writeMemoryRange(memory, line.substring(5), 0xFF80);
        } else if (line.startsWith("IO=")) {
            String data = line.substring(3);
            for (int i = 0; i < data.length() / 2 && (0xFF00 + i) <= 0xFF7F; i++) {
                if (0xFF00 + i < 0xFF10 || 0xFF00 + i > 0xFF3F) {
                    int value = Integer.parseInt(data.substring(i * 2, i * 2 + 2), 16);
                    memory.writeByte(0xFF00 + i, (byte) value);
                }
            }
        }
    }

    private void writeMemoryRange(Memory memory, String hexData, int baseAddress) {
        for (int i = 0; i < hexData.length() / 2; i++) {
            int value = Integer.parseInt(hexData.substring(i * 2, i * 2 + 2), 16);
            memory.writeByte(baseAddress + i, (byte) value);
        }
    }

    private void setBusyCursor(boolean busy) {
        Cursor cursor = Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR);
        Window ancestor = SwingUtilities.getWindowAncestor(this);
        if (ancestor != null) {
            ancestor.setCursor(cursor);
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

    public void promptOpenRomDialog() {
        openRomDialog();
    }

    public void togglePauseFromToolbar() {
        togglePause();
    }

    public void resetFromToolbar() {
        resetEmulator();
    }

    public void saveStateFromToolbar() {
        saveStateDialog();
    }

    public void loadStateFromToolbar() {
        loadStateDialog();
    }

    public void takeScreenshotFromToolbar() {
        exportScreenshot();
    }

    public void toggleFullscreenFromToolbar() {
        toggleFullscreen();
    }

    public void setEmulationSpeedFromToolbar(float speed) {
        setEmulationSpeed(speed, false);
    }

    public void setScaleFromToolbar(int scale) {
        setScale(scale, false);
    }

    public void setScanlinesFromToolbar(boolean enabled) {
        setScanlines(enabled, false);
    }

    public void setSmoothScalingFromToolbar(boolean enabled) {
        setSmoothScaling(enabled, false);
    }

    public void setMaintainAspectFromToolbar(boolean enabled) {
        setMaintainAspect(enabled, false);
    }

    public void setColorFilterFromToolbar(ColorFilter filter) {
        setColorFilter(filter, false);
    }

    public void showGraphicsOptionsFromToolbar() {
        showGraphicsOptions();
    }

    public void synchronizePauseState(boolean paused) {
        if (pauseItem != null) {
            pauseItem.setText(paused ? "Resume" : "Pause");
        }
    }

    public void synchronizeSpeedFromWindow(float speed) {
        suppressSpeedEvents = true;
        try {
            JRadioButtonMenuItem item = speedItems.get(speed);
            if (item != null) {
                item.setSelected(true);
            }
        } finally {
            suppressSpeedEvents = false;
        }
    }

    public void synchronizeScaleFromWindow(int scale) {
        suppressScaleEvents = true;
        try {
            JRadioButtonMenuItem item = scaleItems.get(scale);
            if (item != null) {
                item.setSelected(true);
            }
        } finally {
            suppressScaleEvents = false;
        }
    }

    public void synchronizeDisplayOptionsFromWindow(boolean scanlines, boolean smooth, boolean maintain) {
        suppressDisplayEvents = true;
        try {
            if (scanlinesItem != null) {
                scanlinesItem.setSelected(scanlines);
            }
            if (smoothScalingItem != null) {
                smoothScalingItem.setSelected(smooth);
            }
            if (maintainAspectItem != null) {
                maintainAspectItem.setSelected(maintain);
            }
        } finally {
            suppressDisplayEvents = false;
        }
    }

    public void synchronizeFilterFromWindow(ColorFilter filter) {
        suppressFilterEvents = true;
        try {
            JRadioButtonMenuItem item = filterItems.get(filter);
            if (item != null) {
                item.setSelected(true);
            }
        } finally {
            suppressFilterEvents = false;
        }
    }

    public void synchronizeAlwaysOnTop(boolean alwaysOnTop) {
        if (alwaysOnTopItem != null) {
            alwaysOnTopItem.setSelected(alwaysOnTop);
        }
    }

    public void resetDisplaySettings() {
        emulatorView.resetView();
        window.setStatusText("Display settings reset");
        window.refreshUiState(true);
    }

    private class GraphicsOptionsDialog extends JDialog {
        private boolean adjusting;

        private final JComboBox<ColorFilter> filterCombo;
        private final JSpinner scaleSpinner;
        private final JCheckBox scanlinesCheck;
        private final JCheckBox smoothCheck;
        private final JCheckBox aspectCheck;

        GraphicsOptionsDialog() {
            super(window, "Graphics Options", true);
            setSize(420, 320);
            setLocationRelativeTo(window);
            setLayout(new BorderLayout());

            JPanel mainPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            gbc.gridx = 0;
            gbc.gridy = 0;
            mainPanel.add(new JLabel("Color Filter:"), gbc);

            gbc.gridx = 1;
            filterCombo = new JComboBox<>(ColorFilter.values());
            filterCombo.addActionListener(e -> {
                if (adjusting) {
                    return;
                }
                setColorFilterFromToolbar((ColorFilter) filterCombo.getSelectedItem());
            });
            mainPanel.add(filterCombo, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            mainPanel.add(new JLabel("Scale Factor:"), gbc);

            gbc.gridx = 1;
            scaleSpinner = new JSpinner(new SpinnerNumberModel(emulatorView.getScaleFactor(), 1, 8, 1));
            scaleSpinner.addChangeListener(e -> {
                if (adjusting) {
                    return;
                }
                setScaleFromToolbar((Integer) scaleSpinner.getValue());
            });
            mainPanel.add(scaleSpinner, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            scanlinesCheck = new JCheckBox("Show Scanlines");
            scanlinesCheck.addActionListener(e -> {
                if (adjusting) {
                    return;
                }
                setScanlinesFromToolbar(scanlinesCheck.isSelected());
            });
            mainPanel.add(scanlinesCheck, gbc);

            gbc.gridy = 3;
            smoothCheck = new JCheckBox("Smooth Scaling");
            smoothCheck.addActionListener(e -> {
                if (adjusting) {
                    return;
                }
                setSmoothScalingFromToolbar(smoothCheck.isSelected());
            });
            mainPanel.add(smoothCheck, gbc);

            gbc.gridy = 4;
            aspectCheck = new JCheckBox("Maintain Aspect Ratio");
            aspectCheck.addActionListener(e -> {
                if (adjusting) {
                    return;
                }
                setMaintainAspectFromToolbar(aspectCheck.isSelected());
            });
            mainPanel.add(aspectCheck, gbc);

            add(mainPanel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton resetButton = new JButton("Reset to Defaults");
            resetButton.addActionListener(e -> {
                resetDisplaySettings();
                refreshFromView();
            });
            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> dispose());
            buttonPanel.add(resetButton);
            buttonPanel.add(closeButton);
            add(buttonPanel, BorderLayout.SOUTH);
        }

        void showDialog() {
            refreshFromView();
            setVisible(true);
        }

        private void refreshFromView() {
            adjusting = true;
            try {
                filterCombo.setSelectedItem(emulatorView.getColorFilter());
                scaleSpinner.setValue(emulatorView.getScaleFactor());
                scanlinesCheck.setSelected(emulatorView.isShowScanlines());
                smoothCheck.setSelected(emulatorView.isSmoothScaling());
                aspectCheck.setSelected(emulatorView.isMaintainAspectRatio());
            } finally {
                adjusting = false;
            }
        }
    }
}
