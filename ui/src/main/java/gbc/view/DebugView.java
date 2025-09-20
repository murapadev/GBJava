package gbc.view;

import gbc.model.GameBoyColor;
import gbc.model.memory.Memory;
import gbc.model.cpu.CPU;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug View with hexadecimal memory viewer, disassembler, and debugging tools
 */
public class DebugView extends JFrame {

    private final GameBoyColor gbc;
    private final CPU cpu;
    private final Memory memory;

    // Components
    private JTabbedPane tabbedPane;
    private MemoryViewer memoryViewer;
    private DisassemblerPanel disassemblerPanel;
    private RegisterPanel registerPanel;
    private BreakpointPanel breakpointPanel;
    private MemorySearchPanel memorySearchPanel;

    // Control buttons
    private JButton stepButton;
    private JButton runButton;
    private JButton pauseButton;
    private JButton resetButton;

    // Status
    private JLabel statusLabel;

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
    }

    private void initializeComponents() {
        tabbedPane = new JTabbedPane();

        memoryViewer = new MemoryViewer();
        disassemblerPanel = new DisassemblerPanel();
        registerPanel = new RegisterPanel();
        breakpointPanel = new BreakpointPanel();
        memorySearchPanel = new MemorySearchPanel();

        tabbedPane.addTab("Memory", memoryViewer);
        tabbedPane.addTab("Disassembler", disassemblerPanel);
        tabbedPane.addTab("Registers", registerPanel);
        tabbedPane.addTab("Breakpoints", breakpointPanel);
        tabbedPane.addTab("Memory Search", memorySearchPanel);

        // Control buttons
        stepButton = new JButton("Step");
        runButton = new JButton("Run");
        pauseButton = new JButton("Pause");
        resetButton = new JButton("Reset");

        statusLabel = new JLabel("Debug Mode Ready");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(stepButton);
        controlPanel.add(runButton);
        controlPanel.add(pauseButton);
        controlPanel.add(resetButton);

        add(controlPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        stepButton.addActionListener(e -> stepExecution());
        runButton.addActionListener(e -> startExecution());
        pauseButton.addActionListener(e -> pauseExecution());
        resetButton.addActionListener(e -> resetEmulator());
    }

    private void stepExecution() {
        // Execute one instruction
        gbc.executeCycle();
        updateAllViews();
        statusLabel.setText("Stepped - PC: $" + Integer.toHexString(cpu.getRegisters().getPC()).toUpperCase());
    }

    private void startExecution() {
        statusLabel.setText("Running...");
        // This would need integration with the main emulator loop
    }

    private void pauseExecution() {
        statusLabel.setText("Paused");
        updateAllViews();
    }

    private void resetEmulator() {
        gbc.reset();
        updateAllViews();
        statusLabel.setText("Reset - PC: $" + Integer.toHexString(cpu.getRegisters().getPC()).toUpperCase());
    }

    public void updateAllViews() {
        if (isVisible()) {
            memoryViewer.refresh();
            disassemblerPanel.refresh();
            registerPanel.refresh();
            repaint();
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

        private void gotoAddress() {
            try {
                String addressText = addressField.getText().trim();
                if (addressText.startsWith("$")) {
                    addressText = addressText.substring(1);
                }
                currentAddress = Integer.parseInt(addressText, 16) & 0xFFFF;
                refresh();
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
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                if (column == 0) {
                    // Address column - light blue background
                    c.setBackground(new Color(230, 240, 255));
                } else if (column == 17) {
                    // ASCII column - light green background
                    c.setBackground(new Color(240, 255, 240));
                } else {
                    // Data columns - white background
                    c.setBackground(Color.WHITE);
                }
            }

            setHorizontalAlignment(SwingConstants.CENTER);
            return c;
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
            currentPC = cpu.getRegisters().getPC();
            StringBuilder sb = new StringBuilder();

            // Disassemble instructions around PC
            int startAddr = Math.max(0, currentPC - 0x20);
            int endAddr = Math.min(0xFFFF, currentPC + 0x40);

            for (int addr = startAddr; addr <= endAddr; addr++) {
                if (addr == currentPC) {
                    sb.append(">>> ");
                } else {
                    sb.append("    ");
                }

                int opcode = memory.readByte(addr) & 0xFF;
                String instruction = disassembleInstruction(addr, opcode);
                sb.append(String.format("%04X: %s%n", addr, instruction));
            }

            disassemblyArea.setText(sb.toString());

            // Scroll to current PC
            SwingUtilities.invokeLater(() -> {
                int pcLine = (currentPC - startAddr);
                try {
                    int lineStart = disassemblyArea.getLineStartOffset(pcLine);
                    disassemblyArea.setCaretPosition(lineStart);
                } catch (Exception e) {
                    // Ignore
                }
            });
        }

        private String disassembleInstruction(int address, int opcode) {
            // Simple disassembly - this should be expanded with full instruction set
            switch (opcode) {
                case 0x00:
                    return "NOP";
                case 0x01:
                    return String.format("LD BC, $%04X", read16(address + 1));
                case 0x02:
                    return "LD (BC), A";
                case 0x03:
                    return "INC BC";
                case 0x04:
                    return "INC B";
                case 0x05:
                    return "DEC B";
                case 0x06:
                    return String.format("LD B, $%02X", memory.readByte(address + 1) & 0xFF);
                case 0x07:
                    return "RLCA";
                case 0x08:
                    return String.format("LD ($%04X), SP", read16(address + 1));
                case 0x09:
                    return "ADD HL, BC";
                case 0x0A:
                    return "LD A, (BC)";
                case 0x0B:
                    return "DEC BC";
                case 0x0C:
                    return "INC C";
                case 0x0D:
                    return "DEC C";
                case 0x0E:
                    return String.format("LD C, $%02X", memory.readByte(address + 1) & 0xFF);
                case 0x0F:
                    return "RRCA";
                case 0x10:
                    return "STOP";
                case 0x11:
                    return String.format("LD DE, $%04X", read16(address + 1));
                case 0x18:
                    return String.format("JR $%02X", memory.readByte(address + 1) & 0xFF);
                case 0x20:
                    return String.format("JR NZ, $%02X", memory.readByte(address + 1) & 0xFF);
                case 0xC3:
                    return String.format("JP $%04X", read16(address + 1));
                case 0xCD:
                    return String.format("CALL $%04X", read16(address + 1));
                case 0xC9:
                    return "RET";
                default:
                    return String.format("??? ($%02X)", opcode);
            }
        }

        private int read16(int address) {
            int low = memory.readByte(address) & 0xFF;
            int high = memory.readByte(address + 1) & 0xFF;
            return (high << 8) | low;
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
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid address format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void removeBreakpoint() {
            int selectedRow = breakpointTable.getSelectedRow();
            if (selectedRow >= 0) {
                breakpointTableModel.removeBreakpoint(selectedRow);
            }
        }

        private void clearAllBreakpoints() {
            breakpointTableModel.clearAll();
        }
    }

    // Breakpoint Table Model
    private class BreakpointTableModel extends AbstractTableModel {
        private List<Integer> breakpoints = new ArrayList<>();
        private final String[] columnNames = { "Address", "Enabled" };

        public void addBreakpoint(int address) {
            if (!breakpoints.contains(address)) {
                breakpoints.add(address);
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
    }
}