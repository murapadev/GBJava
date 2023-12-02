package gbc.view;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serial;

import gbc.model.GameBoyColor;
import gbc.model.graphics.Screen;
import gbc.model.graphics.GPU;

public class EmulatorView extends JFrame {

	@Serial
	private static final long serialVersionUID = 1L;
	private final JPanel contentPane;
	private final GameBoyColor gbc;

	private GPU gpu;
	private Screen screen;

	public EmulatorView(GameBoyColor gbc) {
		this.gpu = gbc.getGpu();
		this.gbc = gbc;
		this.screen = gpu.getScreen();
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setBounds(100, 100, 320, 288); // Scaled size for better visibility
		this.contentPane = new JPanel() {
			@Serial
			private static final long serialVersionUID = 1L;

			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				BufferedImage image = screen.getImage();
				if (image != null) {
					g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
				}
			}
		};
		this.contentPane.setPreferredSize(new Dimension(320, 288)); // Scaled size
		this.setContentPane(this.contentPane);
		this.pack();
		this.setVisible(true);
		this.setResizable(false);

		// Create an instance of MenuBar and set it as the menu bar for this JFrame
		MenuBar menuBar = new MenuBar(this.gbc.getCpu(), this.gbc.getMemory());
		this.setJMenuBar(menuBar);
	}

	public void update() {
		this.gpu.updateGraphics();
		this.contentPane.repaint();
	}

	public void setGPU(GPU gpu) {
		this.gpu = gpu;
		this.screen = gpu.getScreen();
	}

	public GPU getGPU() {
		return this.gpu;
	}
}