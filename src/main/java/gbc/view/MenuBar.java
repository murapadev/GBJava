package gbc.view;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.memory.Memory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.Window;
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

		// File menu
		JMenu fileMenu = new JMenu("File");
		JMenuItem openItem = new JMenuItem("Open ROM...");
		JMenuItem resetItem = new JMenuItem("Reset");
		JMenuItem exitItem = new JMenuItem("Exit");

		fileMenu.add(openItem);
		fileMenu.add(resetItem);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		openItem.addActionListener(this::openFile);
		resetItem.addActionListener(this::resetEmulator);
		exitItem.addActionListener(this::closeApplication);

		// Emulation menu
		JMenu emulationMenu = new JMenu("Emulation");
		JMenuItem pauseItem = new JMenuItem("Pause/Resume");
		JMenuItem debugItem = new JMenuItem("Debug View");

		emulationMenu.add(pauseItem);
		emulationMenu.add(debugItem);

		pauseItem.addActionListener(this::togglePause);
		debugItem.addActionListener(this::openDebugView);

		// Help menu
		JMenu helpMenu = new JMenu("Help");
		JMenuItem aboutItem = new JMenuItem("About");

		helpMenu.add(aboutItem);
		aboutItem.addActionListener(this::showAbout);

		this.add(fileMenu);
		this.add(emulationMenu);
		this.add(helpMenu);
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
		try {
			// Prefer using the window's controller if available
			Window w = SwingUtilities.getWindowAncestor(this);
			if (w instanceof EmulatorWindow) {
				((EmulatorWindow) w).loadROM(file);
			} else {
				this.gbc.insertCartridge(file.getAbsolutePath());
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
			ex.printStackTrace();
		}
	}

	private void openDebugView(ActionEvent e) {
		Window w = SwingUtilities.getWindowAncestor(this);
		if (w instanceof EmulatorWindow) {
			((EmulatorWindow) w).openDebugView();
		} else {
			debugView.setVisible(true);
		}

	}

	private void closeApplication(ActionEvent e) {
		// Assuming that this class is an inner class of a JFrame subclass
		SwingUtilities.getWindowAncestor(this).dispose();
	}

	private void resetEmulator(ActionEvent e) {
		Window w = SwingUtilities.getWindowAncestor(this);
		if (w instanceof EmulatorWindow) {
			((EmulatorWindow) w).resetEmulator();
		} else {
			this.gbc.reset();
		}
		JOptionPane.showMessageDialog(this, "Emulator reset successfully!",
				"Reset", JOptionPane.INFORMATION_MESSAGE);
	}

	private void togglePause(ActionEvent e) {
		Window w = SwingUtilities.getWindowAncestor(this);
		if (w instanceof EmulatorWindow) {
			((EmulatorWindow) w).togglePause();
		} else {
			JOptionPane.showMessageDialog(this, "Pause/Resume functionality not yet implemented",
				"Info", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void showAbout(ActionEvent e) {
		String aboutText = "Game Boy Color Emulator\n" +
				"Version 1.0\n" +
				"A Java-based Game Boy Color emulator\n\n" +
				"Controls:\n" +
				"Arrow Keys: D-Pad\n" +
				"Z: A Button\n" +
				"X: B Button\n" +
				"Enter: Start\n" +
				"Space: Select\n" +
				"P: Pause\n" +
				"R: Reset\n" +
				"F12: Debug View";

		JOptionPane.showMessageDialog(this, aboutText, "About", JOptionPane.INFORMATION_MESSAGE);
	}
}