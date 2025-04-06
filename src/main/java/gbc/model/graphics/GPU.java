package gbc.model.graphics;

import gbc.model.memory.Memory;

public class GPU {
    private Memory memory;
    private Screen screen;
    private int[] frameBuffer = new int[160 * 144]; // Frame buffer array

    private int mode; // Current mode of the GPU
    private int modeClock; // Clock for timing mode changes

    public GPU(Memory memory, Screen screen) {
        this.memory = memory;
        this.screen = screen;
        this.mode = 2; // Start in OAM mode
        this.modeClock = 0;
    }

    public void step(int cycles) {
        modeClock += cycles;
        switch (mode) {
            case 2: // OAM mode
                if (modeClock >= 80) {
                    modeClock = 0;
                    mode = 3; // Switch to VRAM mode
                }
                break;
            case 3: // VRAM mode
                if (modeClock >= 172) {
                    modeClock = 0;
                    mode = 0; // Switch to HBlank
                    updateLine();
                }
                break;
            case 0: // HBlank
                if (modeClock >= 204) {
                    modeClock = 0;
                    memory.writeByte(0xFF44, (byte) (memory.readByte(0xFF44) + 1));
                    if (memory.readByte(0xFF44) == 143) {
                        mode = 1; // Switch to VBlank
                        // Render screen here
                    } else {
                        mode = 2; // Switch to OAM
                    }
                }
                break;

            case 1: // VBlank
                if (modeClock >= 456) {
                    modeClock = 0;
                    memory.writeByte(0xFF44, (byte) (memory.readByte(0xFF44) + 1));
                    if (memory.readByte(0xFF44) > 153) {
                        mode = 2; // Switch to OAM
                        memory.writeByte(0xFF44, (byte) 0);
                    }
                }
                break;

            default:
                break;


        }

    }

    private void updateLine() {
        int currentLine = memory.readByte(0xFF44) & 0xFF; // Ensure it's treated as an unsigned byte
        if (currentLine < 0 || currentLine >= 144) {
            // Invalid scanline number, handle error or return
            return;
        }
        int offset = 160 * currentLine;
        for (int i = 0; i < 160; i++) {
            int index = i + offset;
            if (index >= 0 && index < frameBuffer.length) {
                frameBuffer[index] = 0xFFFFFF; // Set color to white
            }
        }
        screen.render(frameBuffer);
    }

    // Other methods for rendering tiles, sprites, etc.

    public void updateGraphics() {
    	this.step(456);
    }

    public Screen getScreen() {
    	return this.screen;
    }

    public void setScreen(Screen screen) {
    	this.screen = screen;
    }

    public int[] getFrameBuffer() {
    	return this.frameBuffer;
    }

    public void setFrameBuffer(int[] frameBuffer) {
    	this.frameBuffer = frameBuffer;
    }

    public int getMode() {
    	return this.mode;
    }

    public void setMode(int mode) {
    	this.mode = mode;
    }

    public int getModeClock() {
    	return this.modeClock;
    }

    public void setModeClock(int modeClock) {
    	this.modeClock = modeClock;
    }

    public Memory getMemory() {
    	return this.memory;
    }

    public void setMemory(Memory memory) {
    	this.memory = memory;
    }


}
