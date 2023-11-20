package main.java.view;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import main.java.model.graphics.Screen;

public class EmulatorView extends JFrame {
	
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private Screen screen;

	public EmulatorView(Screen screen) {
		this.screen = screen;
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setBounds(0, 0, 160, 144);
		this.contentPane = new JPanel() {
			private static final long serialVersionUID = 1L;

			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.drawImage(screen.getImage(), 0, 0, null);
			}
		};
		this.contentPane.setBounds(0, 0, 160, 144);
		this.contentPane.setLayout(null);
		this.contentPane.setOpaque(true);
		this.setContentPane(this.contentPane);
		this.setVisible(true);
	}

	public void init() {
		this.contentPane.repaint();
	}

	public void update() {
		this.contentPane.repaint();
	}

	public void setScreen(Screen screen) {
		this.screen = screen;
	}

	public Screen getScreen() {
		return this.screen;
	}

	public BufferedImage getImage() {
		return this.screen.getImage();
	}

	public byte[] getPixels() {
		return ((DataBufferByte) this.getImage().getRaster().getDataBuffer()).getData();
	}

}