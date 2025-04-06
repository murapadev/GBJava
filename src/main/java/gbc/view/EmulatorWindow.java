package gbc.view;

import gbc.model.GameBoyColor;

import javax.swing.*;

public class EmulatorWindow extends JFrame {
    DebugView debugView;
    EmulatorView emulatorView;
    MenuBar menuBar;

    GameBoyColor gbc;

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

    public void update() {
        this.emulatorView.update();
        this.debugView.update();
    }

    public void openDebugView() {
        this.debugView.setVisible(true);
    }

}