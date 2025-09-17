package gbc.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

import javax.swing.*;
import java.awt.*;

public class DebugView extends JFrame {

    private final GameBoyColor gbc;
    private final CPU cpu;
    private final Memory memory;
    private final JTextArea opcodeArea;
    private final JTextArea memoryArea;
    private final JTextArea registerArea;
    private final JButton memoryDumpButton;
    private final JButton stackDumpButton;

    public DebugView(GameBoyColor gbc) {
        this.gbc = gbc;
        this.cpu = gbc.getCpu();
        this.memory = gbc.getMemory();


        setTitle("Emulator Debug View");
        setPreferredSize(new Dimension(800, 600)); // Preferred size is used for better layout management
        setLayout(new BorderLayout());

        opcodeArea = createTextArea();
        memoryArea = createTextArea();
        registerArea = createTextArea();
        memoryDumpButton = new JButton("Dump Memory (0xC000-0xC0FF)");
        stackDumpButton = new JButton("Dump Stack (32 bytes)");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(memoryDumpButton);
        buttonPanel.add(stackDumpButton);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Opcodes", new JScrollPane(opcodeArea));
        tabbedPane.addTab("Memory", new JScrollPane(memoryArea));
        tabbedPane.addTab("Registers", new JScrollPane(registerArea));
        tabbedPane.addTab("Debug Tools", buttonPanel);

        add(tabbedPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Change to DISPOSE to avoid shutting down the whole app
        pack(); // Use pack instead of setSize to fit components
        setLocationRelativeTo(null); // Center on screen
        setVisible(false);

        memoryDumpButton.addActionListener(e -> {
            String dump = cpu.dumpMemory(0xC000, 0xC0FF);
            JOptionPane.showMessageDialog(this, dump, "Memory Dump", JOptionPane.INFORMATION_MESSAGE);
        });
        stackDumpButton.addActionListener(e -> {
            String dump = cpu.dumpStack(32);
            JOptionPane.showMessageDialog(this, dump, "Stack Dump", JOptionPane.INFORMATION_MESSAGE);
        });

    }

    private JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Set a monospaced font for alignment
        textArea.setEditable(false); // Set text area to be read-only
        return textArea;
    }

    public void update() {

        if (super.isVisible()) {
            updateOpcodes();
            updateMemory();
            updateRegisters();
        }

    }

    private void updateOpcodes() {
        // Display current opcode and next few opcodes
        opcodeArea.setText(cpu.getOpcodeLog()); // Requires toString method in Opcode class
    }

    private void updateMemory() {
        // Display a portion of memory or specific memory locations
        memoryArea.setText(memory.toString()); // Requires toString method in Memory class
    }

    private void updateRegisters() {
        // Display current register values
        registerArea.setText(cpu.getRegisters().toString()); // Requires toString method in Registers class
    }
}
