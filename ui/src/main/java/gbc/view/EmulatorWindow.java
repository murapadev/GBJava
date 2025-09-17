package gbc.view;

import gbc.model.GameBoyColor;
import gbc.controller.EmulatorController;

import javax.swing.*;

public class EmulatorWindow extends JFrame {
    DebugView debugView;
    EmulatorView emulatorView;
    MenuBar menuBar;

    GameBoyColor gbc;
    private EmulatorController controller;

    public EmulatorWindow(GameBoyColor gbc) {
        this.gbc = gbc;
        this.debugView = new DebugView(gbc);
        this.emulatorView = new EmulatorView(gbc);
        this.menuBar = new MenuBar(gbc, debugView);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setBounds(100, 100, 320, 288); // Scaled size for better visibility
        this.setContentPane(this.emulatorView);
        this.pack();
        this.setVisible(true);
        this.setResizable(false);
        this.setJMenuBar(menuBar);
    }

    public void setController(EmulatorController controller) {
        this.controller = controller;
    }

    public void update() {
        if (this.emulatorView != null) this.emulatorView.update();
        if (this.debugView != null) this.debugView.update();
    }

    public void openDebugView() {
        if (this.debugView != null) this.debugView.setVisible(true);
    }

    // Called by MenuBar when pause is requested
    public void togglePause() {
        if (this.controller != null) {
            this.controller.togglePause();
        }
    }

    // Called by MenuBar when reset is requested
    public void resetEmulator() {
        if (this.controller != null) {
            this.controller.reset();
        } else if (this.gbc != null) {
            this.gbc.reset();
        }
    }

    // Called by MenuBar to load a ROM via controller if available
    public void loadROM(java.io.File file) {
        if (this.controller != null) {
            this.controller.loadRom(file.getAbsolutePath());
        } else if (this.gbc != null) {
            this.gbc.insertCartridge(file.getAbsolutePath());
        }
    }

}