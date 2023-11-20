package main.java.model.graphics;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import main.java.model.memory.Memory;

public class Screen extends JPanel {

    Memory memory;
    BufferedImage image;
    Graphics graphics;

    public Screen(Memory memory) {
        this.memory = memory;
        this.image = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
        this.graphics = this.image.getGraphics();
        this.setPreferredSize(new Dimension(160, 144));
        this.setBackground(Color.WHITE);
    }

    public void render(){
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 160; x++) {
                int color = this.memory.readByte((short) (0x8000 + (y * 160) + x));
                this.graphics.setColor(new Color(color, color, color));
                this.graphics.fillRect(x, y, 1, 1);
            }
        }
        this.repaint();
    }

    public void clear(){
        this.graphics.setColor(Color.WHITE);
        this.graphics.fillRect(0, 0, 160, 144);
        this.repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(this.image, 0, 0, null);
    }

    public void setPixel(int x, int y, Color color) {
        this.graphics.setColor(color);
        this.graphics.fillRect(x, y, 1, 1);
    }

    public Color getPixel(int x, int y) {
        return new Color(this.image.getRGB(x, y));
    }

	public BufferedImage getImage() {
		return this.image;
	}
}
