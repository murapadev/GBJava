package gbc.ui.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.cpu.Disassembler;
import gbc.model.cpu.Disassembler.DecodedInstruction;
import gbc.model.memory.Memory;
import gbc.ui.controller.EmulatorController;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug View with hexadecimal memory viewer, disassembler, and debugging tools
 */
public class DebugView extends JFrame {

    private final GameBoyColor gbc;
    private final CPU cpu;
    private final Memory memory;
    private EmulatorController controller;

    // Components
    private JTabbedPane tabbedPane;
    private MemoryViewer memoryViewer;
    private DisassemblerPanel disassemblerPanel;
    private RegisterPanel registerPanel;
    private BreakpointPanel breakpointPanel;
    private MemorySearchPanel memorySearchPanel;

    // Control components
    private JToolBar controlToolbar;
    private JButton stepButton;
    private JButton runButton;
    private JButton pauseButton;
    private JButton resetButton;
    private JButton jumpToPcButton;
    private JToggleButton autoRefreshToggle;

    private Timer autoRefreshTimer;

    // Status
    private JLabel statusLabel;
    private JLabel pcStatusLabel;
    private JLabel spStatusLabel;
    private JLabel imeStatusLabel;
    private JLabel haltStatusLabel;
    private JLabel speedModeStatusLabel;

    public DebugView(GameBoyColor gbc) {
        this.gbc = gbc;
        this.cpu = gbc.getCpu();
        this.memory = gbc.getMemory();

        setTitle("Debug View");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        updateStatusIndicators();
    }

    private void initializeComponents() {
        tabbedPane = new JTabbedPane();

        memoryViewer = new MemoryViewer();
        disassemblerPanel = new DisassemblerPanel();
        registerPanel = new RegisterPanel();
        breakpointPanel = new BreakpointPanel();
        memorySearchPanel = new MemorySearchPanel();

        JPanel overview = createOverviewPanel();
        tabbedPane.addTab("Overview", overview);
        tabbedPane.addTab("Memory", memoryViewer);

        controlToolbar = new JToolBar();
        controlToolbar.setFloatable(false);
        controlToolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        stepButton = createToolbarButton("Step", "Execute one CPU instruction (F7)");
        runButton = createToolbarButton("Run", "Resume continuous execution (F5)");
        pauseButton = createToolbarButton("Pause", "Pause execution (F6)");
        resetButton = createToolbarButton("Reset", "Reset emulator state (Ctrl+R)");
        jumpToPcButton = createToolbarButton("Jump to PC", "Center views around the current program counter");

        autoRefreshToggle = new JToggleButton("Auto Refresh");
        configureToolbarToggle(autoRefreshToggle, "Continuously refresh while running");

        controlToolbar.add(stepButton);
        controlToolbar.add(runButton);
        controlToolbar.add(pauseButton);
        controlToolbar.add(resetButton);
        controlToolbar.addSeparator();
        controlToolbar.add(jumpToPcButton);
        controlToolbar.add(autoRefreshToggle);

        autoRefreshTimer = new Timer(250, e -> updateAllViews());
        autoRefreshTimer.setRepeats(true);

        statusLabel = new JLabel("Debug Mode Ready");
        pcStatusLabel = new JLabel("PC: ----");
        spStatusLabel = new JLabel("SP: ----");
        imeStatusLabel = new JLabel("IME: --");
        haltStatusLabel = new JLabel("HALT: --");
        speedModeStatusLabel = new JLabel("Speed: Normal");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        add(controlToolbar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        JPanel metricsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 2));
        metricsPanel.add(pcStatusLabel);
        metricsPanel.add(spStatusLabel);
        metricsPanel.add(imeStatusLabel);
        metricsPanel.add(haltStatusLabel);
        metricsPanel.add(speedModeStatusLabel);
        statusPanel.add(metricsPanel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        stepButton.addActionListener(e -> stepExecution());
        runButton.addActionListener(e -> startExecution());
        pauseButton.addActionListener(e -> pauseExecution());
        resetButton.addActionListener(e -> resetEmulator());
        jumpToPcButton.addActionListener(e -> jumpToProgramCounter());
        autoRefreshToggle.addActionListener(e -> toggleAutoRefresh());

        bindKeyboardShortcuts();
    }

    private void stepExecution() {
        if (controller != null) {
            controller.stepInstruction();
        } else {
            gbc.executeCycle();
        }
        updateAllViews();
        statusLabel.setText("Stepped - PC: $" + Integer.toHexString(cpu.getRegisters().getPC()).toUpperCase());
    }

    private void startExecution() {
        if (controller != null) {
            controller.resume();
        } else {
            gbc.resume();
        }
        reflectPauseState(false);
        statusLabel.setText("Running...");
    }

    private void pauseExecution() {
        if (controller != null) {
            controller.pause();
        } else {
            gbc.pause();
        }
        reflectPauseState(true);
        statusLabel.setText("Paused");
        updateAllViews();
    }

    private void resetEmulator() {
        if (controller != null) {
            controller.reset();
        } else {
            gbc.reset();
        }
        updateAllViews();
        statusLabel.setText("Reset - PC: $" + Integer.toHexString(cpu.getRegisters().getPC()).toUpperCase());
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
        reflectPauseState(controller != null ? controller.isPaused() : gbc.isPaused());
    }

    public void reflectPauseState(boolean paused) {
        SwingUtilities.invokeLater(() -> {
            stepButton.setEnabled(paused);
            runButton.setEnabled(paused);
            pauseButton.setEnabled(!paused);
            if (paused) {
                statusLabel.setText("Paused");
            }
            updateStatusIndicators();
        });
    }

    public void updateAllViews() {
        if (isVisible()) {
            memoryViewer.refresh();
            disassemblerPanel.refresh();
            registerPanel.refresh();
            updateStatusIndicators();
            repaint();
        }
    }

    private JPanel createOverviewPanel() {
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.6);
        verticalSplit.setBorder(null);
        verticalSplit.setTopComponent(disassemblerPanel);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        bottomSplit.setResizeWeight(0.5);
        bottomSplit.setBorder(null);
        bottomSplit.setLeftComponent(registerPanel);

        JTabbedPane detailTabs = new JTabbedPane();
        detailTabs.addTab("Breakpoints", breakpointPanel);
        detailTabs.addTab("Memory Search", memorySearchPanel);
        bottomSplit.setRightComponent(detailTabs);

        verticalSplit.setBottomComponent(bottomSplit);

        JPanel container = new JPanel(new BorderLayout());
        container.add(verticalSplit, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            verticalSplit.setDividerLocation(0.6);
            bottomSplit.setDividerLocation(0.5);
        });

        return container;
    }

    private JButton createToolbarButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        return button;
    }

    private void configureToolbarToggle(JToggleButton toggle, String tooltip) {
        toggle.setFocusPainted(false);
        toggle.setToolTipText(tooltip);
    }

    private void toggleAutoRefresh() {
        if (autoRefreshToggle.isSelected()) {
            if (isVisible()) {
                autoRefreshTimer.start();
            }
            updateAllViews();
            statusLabel.setText("Auto refresh enabled");
        } else {
            autoRefreshTimer.stop();
            statusLabel.setText("Auto refresh disabled");
        }
    }

    private void jumpToProgramCounter() {
        if (!memory.isCartridgeLoaded()) {
            statusLabel.setText("No cartridge loaded");
            return;
        }

        int pc = cpu.getRegisters().getPC() & 0xFFFF;
        memoryViewer.showAddress(pc);
        disassemblerPanel.refresh();
        disassemblerPanel.scrollToAddress(pc);
        updateStatusIndicators();
        statusLabel.setText(String.format("Centered on PC $%04X", pc));
    }

    private void bindKeyboardShortcuts() {
        registerShortcut("debug.step", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), this::stepExecution);
        registerShortcut("debug.run", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::startExecution);
        registerShortcut("debug.pause", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), this::pauseExecution);
        registerShortcut("debug.reset", KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK),
                this::resetEmulator);
        registerShortcut("debug.jump", KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), this::jumpToProgramCounter);
    }

    private void registerShortcut(String name, KeyStroke keyStroke, Runnable action) {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(keyStroke, name);
        actionMap.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void updateStatusIndicators() {
        if (!memory.isCartridgeLoaded()) {
            pcStatusLabel.setText("PC: ----");
            spStatusLabel.setText("SP: ----");
            imeStatusLabel.setText("IME: --");
            haltStatusLabel.setText("HALT: --");
            haltStatusLabel.setForeground(Color.DARK_GRAY);
            speedModeStatusLabel.setText("Speed: Normal");
            speedModeStatusLabel.setForeground(Color.DARK_GRAY);
            return;
        }

        int pc = cpu.getRegisters().getPC() & 0xFFFF;
        int sp = cpu.getRegisters().getSP() & 0xFFFF;
        boolean ime = cpu.isIme();
        boolean halted = cpu.isHalted();
        boolean doubleSpeed = cpu.isDoubleSpeedMode();

        pcStatusLabel.setText(String.format("PC: $%04X", pc));
        spStatusLabel.setText(String.format("SP: $%04X", sp));
        imeStatusLabel.setText("IME: " + (ime ? "ON" : "OFF"));
        imeStatusLabel.setForeground(ime ? new Color(0, 128, 0) : Color.RED.darker());
        haltStatusLabel.setText(halted ? "HALT" : "RUN");
        haltStatusLabel.setForeground(halted ? Color.RED.darker() : new Color(0, 128, 0));
        speedModeStatusLabel.setText(doubleSpeed ? "Speed: Double" : "Speed: Normal");
        speedModeStatusLabel.setForeground(doubleSpeed ? new Color(0, 102, 204) : Color.DARK_GRAY);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            updateStatusIndicators();
            if (autoRefreshToggle != null && autoRefreshToggle.isSelected()) {
                autoRefreshTimer.start();
            }
        } else {
            if (autoRefreshTimer != null) {
                autoRefreshTimer.stop();
            }
            if (autoRefreshToggle != null) {
                autoRefreshToggle.setSelected(false);
            }
        }
    }

    // Memory Viewer with hexadecimal display
    private class MemoryViewer extends JPanel {
        private JTable memoryTable;
        private MemoryTableModel memoryTableModel;
        private JTextField addressField;
        private JButton gotoButton;
        private int currentAddress = 0x0000;

        public MemoryViewer() {
            setLayout(new BorderLayout());

            memoryTableModel = new MemoryTableModel();
            memoryTable = new JTable(memoryTableModel);
            memoryTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
            memoryTable.setDefaultRenderer(Object.class, new MemoryTableCellRenderer());
            memoryTable.setFillsViewportHeight(true);
            memoryTable.setRowSelectionAllowed(true);
            memoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            memoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            memoryTable.getTableHeader().setReorderingAllowed(false);

            // Setup column widths
            memoryTable.getColumnModel().getColumn(0).setPreferredWidth(80); // Address
            for (int i = 1; i <= 16; i++) {
                memoryTable.getColumnModel().getColumn(i).setPreferredWidth(30); // Hex values
            }
            memoryTable.getColumnModel().getColumn(17).setPreferredWidth(150); // ASCII

            JScrollPane scrollPane = new JScrollPane(memoryTable);
            add(scrollPane, BorderLayout.CENTER);

            // Address navigation panel
            JPanel navPanel = new JPanel(new FlowLayout());
            navPanel.add(new JLabel("Go to address:"));
            addressField = new JTextField("0000", 8);
            gotoButton = new JButton("Go");

            navPanel.add(addressField);
            navPanel.add(gotoButton);

            add(navPanel, BorderLayout.NORTH);

            // Event handlers
            gotoButton.addActionListener(e -> gotoAddress());
            addressField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        gotoAddress();
                    }
                }
            });

            refresh();
        }

        public void showAddress(int address) {
            currentAddress = address & 0xFFFF;
            refresh();

            SwingUtilities.invokeLater(() -> {
                int baseAddress = memoryTableModel.getBaseAddress();
                int colsPerRow = memoryTableModel.getColumnsPerRow();
                int offset = ((address & 0xFFFF) - baseAddress);
                if (offset < 0) {
                    offset += 0x10000;
                }
                int targetRow = offset / colsPerRow;
                targetRow = Math.max(0, Math.min(memoryTable.getRowCount() - 1, targetRow));
                memoryTable.getSelectionModel().setSelectionInterval(targetRow, targetRow);
                Rectangle cellRect = memoryTable.getCellRect(targetRow, 0, true);
                memoryTable.scrollRectToVisible(cellRect);
            });
        }

        private void gotoAddress() {
            try {
                String addressText = addressField.getText().trim();
                if (addressText.startsWith("$")) {
                    addressText = addressText.substring(1);
                }
                currentAddress = Integer.parseInt(addressText, 16) & 0xFFFF;
                showAddress(currentAddress);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid address format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        public void refresh() {
            memoryTableModel.setBaseAddress(currentAddress);
            memoryTableModel.fireTableDataChanged();
        }
    }

    // Memory Table Model
    private class MemoryTableModel extends AbstractTableModel {
        private int baseAddress = 0x0000;
        private final int ROWS = 16;
        private final int COLS_PER_ROW = 16;

        public void setBaseAddress(int address) {
            this.baseAddress = address & 0xFFF0; // Align to 16-byte boundary
        }

        public int getBaseAddress() {
            return baseAddress;
        }

        public int getColumnsPerRow() {
            return COLS_PER_ROW;
        }

        @Override
        public int getRowCount() {
            return ROWS;
        }

        @Override
        public int getColumnCount() {
            return 18; // Address + 16 hex values + ASCII
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0)
                return "Address";
            if (column == 17)
                return "ASCII";
            return String.format("%X", column - 1);
        }

        @Override
        public Object getValueAt(int row, int column) {
            int address = (baseAddress + row * COLS_PER_ROW) & 0xFFFF;

            if (column == 0) {
                return String.format("%04X", address);
            } else if (column == 17) {
                // ASCII representation
                StringBuilder ascii = new StringBuilder();
                for (int i = 0; i < COLS_PER_ROW; i++) {
                    int value = memory.readByte(address + i) & 0xFF;
                    char c = (value >= 32 && value <= 126) ? (char) value : '.';
                    ascii.append(c);
                }
                return ascii.toString();
            } else {
                // Hex value
                int byteAddress = address + (column - 1);
                int value = memory.readByte(byteAddress) & 0xFF;
                return String.format("%02X", value);
            }
        }
    }

    // Custom cell renderer for memory table
    private class MemoryTableCellRenderer extends DefaultTableCellRenderer {
        private final Color addressBackground = new Color(230, 240, 255);
        private final Color asciiBackground = new Color(240, 255, 240);
        private final Color dataBackground = Color.WHITE;
        private final Color pcHighlight = new Color(255, 250, 205);
        private final Color breakpointHighlight = new Color(255, 220, 220);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                int baseAddress = memoryViewer.memoryTableModel.getBaseAddress();
                int colsPerRow = memoryViewer.memoryTableModel.getColumnsPerRow();
                int rowStart = (baseAddress + row * colsPerRow) & 0xFFFF;
                int rowEnd = (rowStart + colsPerRow - 1) & 0xFFFF;
                int pc = cpu.getRegisters().getPC() & 0xFFFF;

                boolean rowContainsPc = containsAddress(rowStart, rowEnd, pc);
                boolean rowContainsBreakpoint = breakpointPanel != null
                        && breakpointPanel.hasBreakpointInRange(rowStart, rowEnd);

                if (column == 0) {
                    c.setBackground(rowContainsPc ? pcHighlight
                            : rowContainsBreakpoint ? breakpointHighlight.brighter() : addressBackground);
                } else if (column == 17) {
                    c.setBackground(rowContainsPc ? pcHighlight
                            : rowContainsBreakpoint ? breakpointHighlight.brighter() : asciiBackground);
                } else {
                    int byteAddress = (rowStart + (column - 1)) & 0xFFFF;
                    if (byteAddress == pc) {
                        c.setBackground(pcHighlight);
                    } else if (breakpointPanel != null && breakpointPanel.hasBreakpoint(byteAddress)) {
                        c.setBackground(breakpointHighlight);
                    } else {
                        c.setBackground(dataBackground);
                    }
                }
            }

            if (column == 0 || column == 17) {
                setHorizontalAlignment(SwingConstants.LEFT);
            } else {
                setHorizontalAlignment(SwingConstants.CENTER);
            }
            return c;
        }

        private boolean containsAddress(int start, int end, int value) {
            if (start <= end) {
                return value >= start && value <= end;
            }
            return value >= start || value <= end;
        }
    }

    // Disassembler Panel
    private class DisassemblerPanel extends JPanel {
        private JTextArea disassemblyArea;
        private JScrollPane scrollPane;
        private int currentPC;

        public DisassemblerPanel() {
            setLayout(new BorderLayout());

            disassemblyArea = new JTextArea();
            disassemblyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            disassemblyArea.setEditable(false);
            disassemblyArea.setBackground(Color.WHITE);

            scrollPane = new JScrollPane(disassemblyArea);
            add(scrollPane, BorderLayout.CENTER);

            refresh();
        }

        public void refresh() {
            if (!isCartridgeLoaded()) {
                disassemblyArea.setText("No cartridge loaded.\nPlease load a ROM file to view disassembly.");
                return;
            }

            currentPC = cpu.getRegisters().getPC();
            int startAddr = Math.max(0, currentPC - 32) & 0xFFFF;
            int maxBytes = 256;
            int addr = startAddr;
            int bytesDecoded = 0;
            int lineIndex = 0;
            StringBuilder sb = new StringBuilder();
            Map<Integer, Integer> lineByAddress = new HashMap<>();

            while (bytesDecoded < maxBytes) {
                DecodedInstruction decoded = Disassembler.decode(memory, addr);
                int length = Math.max(1, decoded.getLength());
                boolean isPc = addr == currentPC;
                boolean isBreakpoint = breakpointPanel != null && breakpointPanel.hasBreakpoint(addr);

                String pcMarker = isPc ? ">" : " ";
                String breakpointMarker = isBreakpoint ? "*" : " ";

                sb.append(String.format("%s %s %04X: %s%n", pcMarker, breakpointMarker, addr, decoded.getText()));
                lineByAddress.put(addr, lineIndex);

                bytesDecoded += length;
                addr = (addr + length) & 0xFFFF;
                lineIndex++;

                if (addr == startAddr) {
                    break; // Prevent infinite loop if we wrapped around memory
                }
            }

            disassemblyArea.setText(sb.toString());

            // Scroll to current PC
            SwingUtilities.invokeLater(() -> {
                Integer pcLine = lineByAddress.get(currentPC);
                if (pcLine == null) {
                    return;
                }
                try {
                    int lineStart = disassemblyArea.getLineStartOffset(pcLine);
                    int lineEnd = Math.min(disassemblyArea.getDocument().getLength(),
                            disassemblyArea.getLineEndOffset(pcLine));
                    disassemblyArea.setCaretPosition(lineStart);
                    disassemblyArea.select(lineStart, lineEnd);
                } catch (Exception e) {
                    // Ignore
                }
            });
        }

        private boolean isCartridgeLoaded() {
            return memory.isCartridgeLoaded();
        }

        public void scrollToAddress(int address) {
            if (!isCartridgeLoaded()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                String target = String.format("%04X:", address & 0xFFFF);
                String text = disassemblyArea.getText();
                int index = text.indexOf(target);
                if (index >= 0) {
                    int lineStart = text.lastIndexOf('\n', index);
                    lineStart = lineStart < 0 ? 0 : lineStart + 1;
                    int lineEnd = text.indexOf('\n', index);
                    lineEnd = lineEnd < 0 ? text.length() : lineEnd;
                    disassemblyArea.setCaretPosition(lineStart);
                    disassemblyArea.select(lineStart, lineEnd);
                }
            });
        }

    }

    // Register Panel
    private class RegisterPanel extends JPanel {
        private JTextArea registerArea;

        public RegisterPanel() {
            setLayout(new BorderLayout());

            registerArea = new JTextArea();
            registerArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
            registerArea.setEditable(false);
            registerArea.setBackground(Color.WHITE);

            add(new JScrollPane(registerArea), BorderLayout.CENTER);
            refresh();
        }

        public void refresh() {
            if (!isCartridgeLoaded()) {
                registerArea.setText("No cartridge loaded.\nPlease load a ROM file to view registers.");
                return;
            }

            StringBuilder sb = new StringBuilder();

            // CPU Registers
            sb.append("=== CPU Registers ===\n");
            sb.append(String.format("AF: $%04X  (A: $%02X  F: $%02X)\n",
                    cpu.getRegisters().getAF(),
                    cpu.getRegisters().getRegister("A") & 0xFF,
                    cpu.getRegisters().getRegister("F") & 0xFF));
            sb.append(String.format("BC: $%04X  (B: $%02X  C: $%02X)\n",
                    cpu.getRegisters().getBC(),
                    cpu.getRegisters().getRegister("B") & 0xFF,
                    cpu.getRegisters().getRegister("C") & 0xFF));
            sb.append(String.format("DE: $%04X  (D: $%02X  E: $%02X)\n",
                    cpu.getRegisters().getDE(),
                    cpu.getRegisters().getRegister("D") & 0xFF,
                    cpu.getRegisters().getRegister("E") & 0xFF));
            sb.append(String.format("HL: $%04X  (H: $%02X  L: $%02X)\n",
                    cpu.getRegisters().getHL(),
                    cpu.getRegisters().getRegister("H") & 0xFF,
                    cpu.getRegisters().getRegister("L") & 0xFF));
            sb.append(String.format("SP: $%04X\n", cpu.getRegisters().getSP()));
            sb.append(String.format("PC: $%04X\n", cpu.getRegisters().getPC()));

            // Flags
            int flags = cpu.getRegisters().getRegister("F") & 0xFF;
            sb.append("\n=== Flags ===\n");
            sb.append(String.format("Z: %d  N: %d  H: %d  C: %d\n",
                    (flags & 0x80) != 0 ? 1 : 0,
                    (flags & 0x40) != 0 ? 1 : 0,
                    (flags & 0x20) != 0 ? 1 : 0,
                    (flags & 0x10) != 0 ? 1 : 0));

            // I/O Registers
            sb.append("\n=== I/O Registers ===\n");
            sb.append(String.format("LCDC: $%02X  STAT: $%02X  LY: $%02X\n",
                    memory.readByte(0xFF40) & 0xFF,
                    memory.readByte(0xFF41) & 0xFF,
                    memory.readByte(0xFF44) & 0xFF));
            sb.append(String.format("SCY:  $%02X  SCX:  $%02X\n",
                    memory.readByte(0xFF42) & 0xFF,
                    memory.readByte(0xFF43) & 0xFF));
            sb.append(String.format("BGP:  $%02X  OBP0: $%02X  OBP1: $%02X\n",
                    memory.readByte(0xFF47) & 0xFF,
                    memory.readByte(0xFF48) & 0xFF,
                    memory.readByte(0xFF49) & 0xFF));

            registerArea.setText(sb.toString());
        }

        private boolean isCartridgeLoaded() {
            return memory.isCartridgeLoaded();
        }
    }

    // Breakpoint Panel
    private class BreakpointPanel extends JPanel {
        private JTable breakpointTable;
        private BreakpointTableModel breakpointTableModel;
        private JTextField addressField;
        private JButton addButton;
        private JButton removeButton;
        private JButton clearAllButton;

        public BreakpointPanel() {
            setLayout(new BorderLayout());

            breakpointTableModel = new BreakpointTableModel();
            breakpointTable = new JTable(breakpointTableModel);
            breakpointTable.setFont(new Font("Monospaced", Font.PLAIN, 12));

            add(new JScrollPane(breakpointTable), BorderLayout.CENTER);

            // Control panel
            JPanel controlPanel = new JPanel(new FlowLayout());
            controlPanel.add(new JLabel("Address:"));
            addressField = new JTextField("0000", 8);
            addButton = new JButton("Add");
            removeButton = new JButton("Remove");
            clearAllButton = new JButton("Clear All");

            controlPanel.add(addressField);
            controlPanel.add(addButton);
            controlPanel.add(removeButton);
            controlPanel.add(clearAllButton);

            add(controlPanel, BorderLayout.SOUTH);

            // Event handlers
            addButton.addActionListener(e -> addBreakpoint());
            removeButton.addActionListener(e -> removeBreakpoint());
            clearAllButton.addActionListener(e -> clearAllBreakpoints());
        }

        private void addBreakpoint() {
            try {
                String addressText = addressField.getText().trim();
                if (addressText.startsWith("$")) {
                    addressText = addressText.substring(1);
                }
                int address = Integer.parseInt(addressText, 16) & 0xFFFF;
                breakpointTableModel.addBreakpoint(address);
                addressField.setText("");
                memoryViewer.refresh();
                disassemblerPanel.refresh();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid address format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void removeBreakpoint() {
            int selectedRow = breakpointTable.getSelectedRow();
            if (selectedRow >= 0) {
                breakpointTableModel.removeBreakpoint(selectedRow);
                memoryViewer.refresh();
                disassemblerPanel.refresh();
            }
        }

        private void clearAllBreakpoints() {
            breakpointTableModel.clearAll();
            memoryViewer.refresh();
            disassemblerPanel.refresh();
        }

        public boolean hasBreakpoint(int address) {
            return breakpointTableModel.contains(address);
        }

        public boolean hasBreakpointInRange(int start, int end) {
            return breakpointTableModel.hasBreakpointInRange(start, end);
        }
    }

    // Breakpoint Table Model
    private class BreakpointTableModel extends AbstractTableModel {
        private List<Integer> breakpoints = new ArrayList<>();
        private final String[] columnNames = { "Address", "Enabled" };

        public void addBreakpoint(int address) {
            if (!breakpoints.contains(address)) {
                breakpoints.add(address & 0xFFFF);
                breakpoints.sort(Integer::compareUnsigned);
                fireTableDataChanged();
            }
        }

        public void removeBreakpoint(int index) {
            if (index >= 0 && index < breakpoints.size()) {
                breakpoints.remove(index);
                fireTableDataChanged();
            }
        }

        public void clearAll() {
            breakpoints.clear();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return breakpoints.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0) {
                return String.format("$%04X", breakpoints.get(row));
            } else {
                return true; // All breakpoints are enabled for now
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? Boolean.class : String.class;
        }

        public boolean contains(int address) {
            int normalized = address & 0xFFFF;
            return breakpoints.contains(normalized);
        }

        public boolean hasBreakpointInRange(int start, int end) {
            int normalizedStart = start & 0xFFFF;
            int normalizedEnd = end & 0xFFFF;

            for (int breakpoint : breakpoints) {
                int value = breakpoint & 0xFFFF;
                if (normalizedStart <= normalizedEnd) {
                    if (value >= normalizedStart && value <= normalizedEnd) {
                        return true;
                    }
                } else {
                    if (value >= normalizedStart || value <= normalizedEnd) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

    // Memory Search Panel
    private class MemorySearchPanel extends JPanel {
        private JTextField searchField;
        private JButton searchButton;
        private JTextArea resultsArea;
        private JComboBox<String> searchTypeCombo;

        public MemorySearchPanel() {
            setLayout(new BorderLayout());

            // Search controls
            JPanel searchPanel = new JPanel(new FlowLayout());
            searchPanel.add(new JLabel("Search for:"));
            searchField = new JTextField(10);
            searchTypeCombo = new JComboBox<>(new String[] { "Hex Value", "Text" });
            searchButton = new JButton("Search");

            searchPanel.add(searchField);
            searchPanel.add(searchTypeCombo);
            searchPanel.add(searchButton);

            add(searchPanel, BorderLayout.NORTH);

            // Results area
            resultsArea = new JTextArea();
            resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            resultsArea.setEditable(false);
            add(new JScrollPane(resultsArea), BorderLayout.CENTER);

            searchButton.addActionListener(e -> performSearch());
        }

        private void performSearch() {
            String searchText = searchField.getText().trim();
            if (searchText.isEmpty())
                return;

            StringBuilder results = new StringBuilder();
            String searchType = (String) searchTypeCombo.getSelectedItem();

            if ("Hex Value".equals(searchType)) {
                searchHexValue(searchText, results);
            } else {
                searchTextValue(searchText, results);
            }

            resultsArea.setText(results.toString());
        }

        private void searchHexValue(String hexText, StringBuilder results) {
            if (!isCartridgeLoaded()) {
                results.append("No cartridge loaded. Cannot search memory.\n");
                return;
            }

            try {
                int searchValue = Integer.parseInt(hexText, 16) & 0xFF;
                results.append(String.format("Searching for hex value: $%02X\n\n", searchValue));

                int foundCount = 0;
                for (int addr = 0x0000; addr <= 0xFFFF; addr++) {
                    int value = memory.readByte(addr) & 0xFF;
                    if (value == searchValue) {
                        results.append(String.format("$%04X: $%02X\n", addr, value));
                        foundCount++;
                        if (foundCount > 100) {
                            results.append("... (more than 100 results, search truncated)\n");
                            break;
                        }
                    }
                }

                if (foundCount == 0) {
                    results.append("No matches found.\n");
                } else {
                    results.append(String.format("\nTotal matches: %d\n", foundCount));
                }

            } catch (NumberFormatException ex) {
                results.append("Invalid hex value format.\n");
            }
        }

        private void searchTextValue(String text, StringBuilder results) {
            if (!isCartridgeLoaded()) {
                results.append("No cartridge loaded. Cannot search memory.\n");
                return;
            }

            results.append(String.format("Searching for text: \"%s\"\n\n", text));

            byte[] searchBytes = text.getBytes();
            int foundCount = 0;

            for (int addr = 0x0000; addr <= 0xFFFF - searchBytes.length; addr++) {
                boolean match = true;
                for (int i = 0; i < searchBytes.length; i++) {
                    if ((memory.readByte(addr + i) & 0xFF) != (searchBytes[i] & 0xFF)) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    results.append(String.format("$%04X: \"%s\"\n", addr, text));
                    foundCount++;
                    if (foundCount > 50) {
                        results.append("... (more than 50 results, search truncated)\n");
                        break;
                    }
                }
            }

            if (foundCount == 0) {
                results.append("No matches found.\n");
            } else {
                results.append(String.format("\nTotal matches: %d\n", foundCount));
            }
        }

        private boolean isCartridgeLoaded() {
            return memory.isCartridgeLoaded();
        }
    }
}