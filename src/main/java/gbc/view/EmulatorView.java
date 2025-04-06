package gbc.view;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serial;

import gbc.model.GameBoyColor;
import gbc.model.graphics.Screen;
import gbc.model.graphics.GPU;

public class EmulatorView extends JPanel {

	@Serial
	private static final long serialVersionUID = 1L;
	private final GameBoyColor gbc;

	private GPU gpu;
	private Screen screen;

	public EmulatorView(GameBoyColor gbc) {
		this.gpu = gbc.getGpu();
		this.gbc = gbc;
		this.screen = gpu.getScreen();
		this.setPreferredSize(new Dimension(320, 288)); // Scaled size
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		BufferedImage image = screen.getImage();
		if (image != null) {
			g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
		}
	}

	public void update() {
		this.gpu.updateGraphics();
		this.repaint();
	}

	public void setGPU(GPU gpu) {
		this.gpu = gpu;
		this.screen = gpu.getScreen();
	}

	public GPU getGPU() {
		return this.gpu;
	}
}