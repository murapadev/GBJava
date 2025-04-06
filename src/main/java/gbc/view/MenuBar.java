package gbc.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

public class MenuBar extends JMenuBar {

	private final GameBoyColor gbc;
	private CPU cpu;
	private Memory memory;

	private final DebugView debugView;

	public MenuBar(GameBoyColor gbc, DebugView debugView) {
		this.gbc = gbc;
		this.cpu = gbc.getCpu();
		this.memory = gbc.getMemory();
		this.debugView = debugView;


		JMenu fileMenu = new JMenu("File");
		JMenuItem openItem = new JMenuItem("Open");
		JMenuItem debugItem = new JMenuItem("Debug View");
		JMenuItem exitItem = new JMenuItem("Exit");

		fileMenu.add(openItem);
		fileMenu.add(debugItem);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		openItem.addActionListener(this::openFile);
		debugItem.addActionListener(this::openDebugView);
		exitItem.addActionListener(e -> closeApplication(
				new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null)
		));

		this.add(fileMenu);
	}

	private void openFile(ActionEvent e) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Open ROM File");
		fileChooser.setAcceptAllFileFilterUsed(false);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("ROM Files", "gb", "gbc");
		fileChooser.addChoosableFileFilter(filter);

		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			try {
				loadROM(selectedFile);
			} catch (IOException ex) {
				// Handle exception
				ex.printStackTrace();
			}
		}
	}

	private void loadROM(File file) throws IOException {
		// Implement the logic to load and start the ROM
		System.out.println("Loading ROM: " + file.getAbsolutePath());
		// You might need to call a method from your emulator core here
		this.gbc.insertCartridge(file.getAbsolutePath());
	}

	private void openDebugView(ActionEvent e) {
		debugView.setVisible(true);

	}

	private void closeApplication(ActionEvent e) {
		// Assuming that this class is an inner class of a JFrame subclass
		SwingUtilities.getWindowAncestor(this).dispose();
	}
}