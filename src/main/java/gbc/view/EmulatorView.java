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
		
		// Fill background
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, getWidth(), getHeight());
		
		BufferedImage image = screen.getImage();
		if (image != null) {
			// Scale the image to fit the component while maintaining aspect ratio
			int componentWidth = getWidth();
			int componentHeight = getHeight();
			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();
			
			// Calculate scaling factor to fit while maintaining aspect ratio
			double scaleX = (double) componentWidth / imageWidth;
			double scaleY = (double) componentHeight / imageHeight;
			double scale = Math.min(scaleX, scaleY);
			
			int scaledWidth = (int) (imageWidth * scale);
			int scaledHeight = (int) (imageHeight * scale);
			
			int x = (componentWidth - scaledWidth) / 2;
			int y = (componentHeight - scaledHeight) / 2;
			
			g.drawImage(image, x, y, scaledWidth, scaledHeight, null);
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