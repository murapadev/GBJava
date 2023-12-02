package gbc.view;

import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MenuBar extends JMenuBar {
	// Assume that CPU and Memory are already instantiated and passed to the constructor
	private CPU cpu;
	private Memory memory;

	public MenuBar(CPU cpu, Memory memory) {
		this.cpu = cpu;
		this.memory = memory;

		JMenu fileMenu = new JMenu("File");
		JMenuItem openItem = new JMenuItem("Open");
		JMenuItem debugItem = new JMenuItem("Debug View");
		JMenuItem exitItem = new JMenuItem("Exit");

		fileMenu.add(openItem);
		fileMenu.add(debugItem);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		openItem.addActionListener(e -> openFile());
		debugItem.addActionListener(e -> openDebugView());
		exitItem.addActionListener(e -> System.exit(0));

		this.add(fileMenu);
	}

	private void openFile() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Open ROM File");
		fileChooser.setAcceptAllFileFilterUsed(false);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("ROM Files", "gb", "gbc");
		fileChooser.addChoosableFileFilter(filter);

		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			loadROM(selectedFile);
		}
	}

	private void loadROM(File file) {
		// Implement the logic to load and start the ROM
		System.out.println("Loading ROM: " + file.getAbsolutePath());
		// You might need to call a method from your emulator core here
	}

	private void openDebugView() {
		// Assuming DebugView is a class that takes CPU and Memory in its constructor
		DebugView debugView = new DebugView(cpu, memory);
		debugView.setVisible(true);
	}
}
