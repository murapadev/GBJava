package gbc.model.cpu;

import gbc.model.memory.Memory;

public class Interruptions {
    private Memory memory;
    private Registers registers;
    private CPU cpu;

    private static final int ADDR_INTERRUPT_FLAG = 0xFF0F;
    private static final int ADDR_INTERRUPT_ENABLE = 0xFFFF;

    private static final byte INTERRUPT_VBLANK = 0x01;
    private static final byte INTERRUPT_LCDSTAT = 0x02;
    private static final byte INTERRUPT_TIMER = 0x04;
    private static final byte INTERRUPT_SERIAL = 0x08;
    private static final byte INTERRUPT_JOYPAD = 0x10;

    public Interruptions(Memory memory, Registers registers, CPU cpu) {
        this.memory = memory;
        this.registers = registers;
        this.cpu = cpu;
    }

    public void requestInterrupt(byte interrupt) {
        byte currentFlags = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        currentFlags |= interrupt;
        memory.writeByte(ADDR_INTERRUPT_FLAG, currentFlags);
    }

    public boolean handleInterrupts() {
        // This method is now only used to check if an interrupt should be serviced
        // The actual servicing logic is moved to the CPU state machine
        return getInterruptVector() != null;
    }

    public Integer getInterruptVector() {
        byte interruptFlag = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        byte interruptEnable = (byte) memory.readByte(ADDR_INTERRUPT_ENABLE);
        byte interruptFired = (byte) (interruptFlag & interruptEnable & 0x1F);

        if ((interruptFired & INTERRUPT_VBLANK) != 0) {
            return 0x0040;
        } else if ((interruptFired & INTERRUPT_LCDSTAT) != 0) {
            return 0x0048;
        } else if ((interruptFired & INTERRUPT_TIMER) != 0) {
            return 0x0050;
        } else if ((interruptFired & INTERRUPT_SERIAL) != 0) {
            return 0x0058;
        } else if ((interruptFired & INTERRUPT_JOYPAD) != 0) {
            return 0x0060;
        }
        return null;
    }

    public void clearInterruptFlag(int vector) {
        byte interrupt = 0;
        switch (vector) {
            case 0x0040:
                interrupt = INTERRUPT_VBLANK;
                break;
            case 0x0048:
                interrupt = INTERRUPT_LCDSTAT;
                break;
            case 0x0050:
                interrupt = INTERRUPT_TIMER;
                break;
            case 0x0058:
                interrupt = INTERRUPT_SERIAL;
                break;
            case 0x0060:
                interrupt = INTERRUPT_JOYPAD;
                break;
        }
        byte currentFlags = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        currentFlags &= ~interrupt;
        memory.writeByte(ADDR_INTERRUPT_FLAG, currentFlags);
    }

    public void reset() {
    }

    public boolean hasPendingInterrupt() {
        byte interruptFlag = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        byte interruptEnable = (byte) memory.readByte(ADDR_INTERRUPT_ENABLE);
        return (interruptFlag & interruptEnable & 0x1F) != 0;
    }
}
