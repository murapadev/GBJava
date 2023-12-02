package gbc.model.graphics;

import java.awt.Color;

import gbc.model.cpu.CPU;
import gbc.model.cpu.Interruptions;
import gbc.model.memory.Memory;



import java.awt.Color;
import gbc.model.cpu.CPU;
import gbc.model.cpu.Interruptions;
import gbc.model.memory.Memory;

public class GPU {
    private static final int MODE_HBLANK = 0;
    private static final int MODE_VBLANK = 1;
    private static final int MODE_OAM = 2;
    private static final int MODE_TRANSFER = 3;

    private Memory memory;
    private Screen screen;
    private Interruptions interrupts;
    private CPU cpu;
    private int modeClock;
    private int mode;

    public GPU(Memory memory, CPU cpu) {
        this.memory = memory;
        this.screen = new Screen(memory);
        this.cpu = cpu;
        this.interrupts = cpu.getInterruptions();
        this.modeClock = 0;
        this.mode = MODE_HBLANK;
    }

    public void step(int cycles) {
        this.modeClock += cycles;
        switch (this.mode) {
            case MODE_HBLANK:
                if (this.modeClock >= 204) {
                    enterModeOAM();
                }
                break;
            case MODE_OAM:
                if (this.modeClock >= 80) {
                    enterModeTransfer();
                }
                break;
            case MODE_TRANSFER:
                if (this.modeClock >= 172) {
                    enterModeHBlank();
                }
                break;
        }
    }

    private void enterModeOAM() {
        this.modeClock = 0;
        this.mode = MODE_OAM;
        this.interrupts.lcdStat();
    }

    private void enterModeTransfer() {
        this.modeClock = 0;
        this.mode = MODE_TRANSFER;
    }

    private void enterModeHBlank() {
        this.modeClock = 0;
        this.mode = MODE_HBLANK;
        this.interrupts.lcdStat();
        this.screen.render();
    }

    public void updateGraphics() {
        if (this.memory.readByte((char) 0xFF40) == 1) {
            this.step(456);
        } else {
            this.mode = 0;
            this.modeClock = 0;
            this.screen.clear();
        }
    }


    public void reset() {
        this.modeClock = 0;
        this.mode = MODE_HBLANK;
    }

    public void setLCDStatus() {
        byte status = this.memory.readByte((char) 0xFF41);
        if ((status & 0x03) == this.mode) {
            this.memory.writeByte((char) 0xFF41, (byte) (status | 0x04));
            this.interrupts.lcdStat();
        } else {
            this.memory.writeByte((char) 0xFF41, (byte) (status & 0xFC));
        }
    }

    public void setLCDControl(byte value) {
        this.memory.writeByte((char) 0xFF40, value);
    }

    public void setScrollY(byte value) {
        this.memory.writeByte((char) 0xFF42, value);
    }

    public void setScrollX(byte value) {
        this.memory.writeByte((char) 0xFF43, value);
    }

    public void setLCDCYCoordinate(byte value) {
        this.memory.writeByte((char) 0xFF44, value);
    }

    public void setLCDCompare(byte value) {
        this.memory.writeByte((char) 0xFF45, value);
    }

    public void setDMA(byte value) {
        char address = (char) ((value & 0xFF) << 8);
        for (int i = 0; i < 0xA0; i++) {
            this.memory.writeByte((char) (0xFE00 + i), this.memory.readByte((char) (address + i)));
        }
    }

    public void setBGPalette(byte value) {
        this.memory.writeByte((char) 0xFF47, value);
    }

    public void setSpritePalette0(byte value) {
        this.memory.writeByte((char) 0xFF48, value);
    }

    public void setSpritePalette1(byte value) {
        this.memory.writeByte((char) 0xFF49, value);
    }

    public void setWindowY(byte value) {
        this.memory.writeByte((char) 0xFF4A, value);
    }

    public void setWindowX(byte value) {
        this.memory.writeByte((char) 0xFF4B, value);
    }

    public byte getLCDStatus() {
        return this.memory.readByte((char) 0xFF41);
    }

    public byte getLCDControl() {
        return this.memory.readByte((char) 0xFF40);
    }

    public byte getScrollY() {
        return this.memory.readByte((char) 0xFF42);
    }

    public byte getScrollX() {
        return this.memory.readByte((char) 0xFF43);
    }

    public byte getLCDCYCoordinate() {
        return this.memory.readByte((char) 0xFF44);
    }

    public byte getLCDCompare() {
        return this.memory.readByte((char) 0xFF45);
    }

    public byte getDMA() {
        return this.memory.readByte((char) 0xFF46);
    }

    public byte getBGPalette() {
        return this.memory.readByte((char) 0xFF47);
    }

    public byte getSpritePalette0() {
        return this.memory.readByte((char) 0xFF48);
    }

    public byte getSpritePalette1() {
        return this.memory.readByte((char) 0xFF49);
    }

    public byte getWindowY() {
        return this.memory.readByte((char) 0xFF4A);
    }

    public byte getWindowX() {
        return this.memory.readByte((char) 0xFF4B);
    }

    public void setPixel(int x, int y, Color color) {
        this.screen.setPixel(x, y, color);
    }

    public Color getPixel(int x, int y) {
        return this.screen.getPixel(x, y);
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public void setInterrupts(Interruptions interrupts) {
        this.interrupts = interrupts;
    }

    public void setModeClock(int modeClock) {
        this.modeClock = modeClock;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public Memory getMemory() {
        return memory;
    }

    public Screen getScreen() {
        return screen;
    }

    public Interruptions getInterrupts() {
        return interrupts;
    }

    public int getModeClock() {
        return modeClock;
    }

    public int getMode() {
        return mode;
    }
}
