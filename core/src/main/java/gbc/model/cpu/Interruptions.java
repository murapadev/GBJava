package gbc.model.cpu;
import gbc.model.memory.Memory;

public class Interruptions {
    private boolean masterEnabled;
    private Memory memory;
    private Registers registers;

    private static final int ADDR_INTERRUPT_FLAG = 0xFF0F;
    private static final int ADDR_INTERRUPT_ENABLE = 0xFFFF;

    private static final byte INTERRUPT_VBLANK = 0x01;
    private static final byte INTERRUPT_LCDSTAT = 0x02;
    private static final byte INTERRUPT_TIMER = 0x04;
    private static final byte INTERRUPT_SERIAL = 0x08;
    private static final byte INTERRUPT_JOYPAD = 0x10;

    public Interruptions(Memory memory, Registers registers) {
        this.memory = memory;
        this.registers = registers;
        this.masterEnabled = true;
    }

    public void setMasterEnabled(boolean enabled) {
        this.masterEnabled = enabled;
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void requestInterrupt(byte interrupt) {
        byte currentFlags = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        currentFlags |= interrupt;
        memory.writeByte(ADDR_INTERRUPT_FLAG, currentFlags);
    }

    public void handleInterrupts() {
        if (!masterEnabled) {
            return;
        }

        byte interruptFlag = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        byte interruptEnable = (byte) memory.readByte(ADDR_INTERRUPT_ENABLE);

        byte interruptFired = (byte) (interruptFlag & interruptEnable);
        if (interruptFired == 0) {
            return;
        }

        // Service the first interrupt that's fired
        if ((interruptFired & INTERRUPT_VBLANK) != 0) {
            serviceInterrupt(INTERRUPT_VBLANK, 0x0040);
        } else if ((interruptFired & INTERRUPT_LCDSTAT) != 0) {
            serviceInterrupt(INTERRUPT_LCDSTAT, 0x0048);
        } else if ((interruptFired & INTERRUPT_TIMER) != 0) {
            serviceInterrupt(INTERRUPT_TIMER, 0x0050);
        } else if ((interruptFired & INTERRUPT_SERIAL) != 0) {
            serviceInterrupt(INTERRUPT_SERIAL, 0x0058);
        } else if ((interruptFired & INTERRUPT_JOYPAD) != 0) {
            serviceInterrupt(INTERRUPT_JOYPAD, 0x0060);
        }
    }

    private void serviceInterrupt(byte interrupt, int handlerAddress) {
        // Push current PC on stack
        pushPC();

        // Jump to interrupt handler
        registers.setPC((char) handlerAddress);

        // Disable further interrupts
        setMasterEnabled(false);

        // Reset the interrupt flag
        byte currentFlags = (byte) memory.readByte(ADDR_INTERRUPT_FLAG);
        currentFlags &= ~interrupt;
        memory.writeByte(ADDR_INTERRUPT_FLAG, currentFlags);
    }

    private void pushPC() {
        char sp = registers.getSP();
        memory.writeChar((char) (sp - 2), registers.getPC());
        registers.setSP((char) (sp - 2));
    }

    public void reset() {
        masterEnabled = true;
    }
}
