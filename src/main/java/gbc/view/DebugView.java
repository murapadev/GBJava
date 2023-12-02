package gbc.view;

import javax.swing.*;
import java.awt.*;
import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

public class DebugView extends JFrame {
    private final CPU cpu;
    private final Memory memory;
    private final JTextArea opcodeArea;
    private final JTextArea memoryArea;
    private final JTextArea registerArea;

    public DebugView(CPU cpu, Memory memory) {
        this.cpu = cpu;
        this.memory = memory;

        setTitle("Emulator Debug View");
        setPreferredSize(new Dimension(800, 600)); // Preferred size is used for better layout management
        setLayout(new BorderLayout());

        opcodeArea = createTextArea();
        memoryArea = createTextArea();
        registerArea = createTextArea();

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Opcodes", new JScrollPane(opcodeArea));
        tabbedPane.addTab("Memory", new JScrollPane(memoryArea));
        tabbedPane.addTab("Registers", new JScrollPane(registerArea));

        add(tabbedPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Change to DISPOSE to avoid shutting down the whole app
        pack(); // Use pack instead of setSize to fit components
        setLocationRelativeTo(null); // Center on screen
        setVisible(true);

        // Set a timer to refresh the debug information regularly
        new Timer(500, e -> update()).start(); // Update every 500ms, adjust as needed
    }

    private JTextArea createTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Set a monospaced font for alignment
        textArea.setEditable(false); // Set text area to be read-only
        return textArea;
    }

    public void update() {
        SwingUtilities.invokeLater(() -> { // Ensure updates are done on the EDT

            if(super.isVisible()){
                updateOpcodes();
                updateMemory();
                updateRegisters();
        }
        });

    }

    private void updateOpcodes() {
        // Display current opcode and next few opcodes
        opcodeArea.setText(cpu.getOpLog().toString()); // Requires toString method in Opcode class
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
