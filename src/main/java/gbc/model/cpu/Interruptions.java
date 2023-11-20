package main.java.model.cpu;
import main.java.model.memory.Memory;

public class Interruptions {
    private byte master;
    private byte enable;
    private byte flags;

    private Registers r;
    private Memory m;

    private final static byte Ivblank = (1);
    private final static byte Ilcdstat = (1 << 1);
    private final static byte Itimer = (1 << 2);
    private final static byte Iserial = (1 << 3);
    private final static byte Ijoypad = (1 << 4);

    public Interruptions(Memory memory, Registers registers) {
        m = memory;
        r = registers;
        enable = m.readByte((short) 0xFFFF);
        flags = m.readByte((short) 0xFF0F);
        master = (byte) 1;

    }

    public void setMaster(byte master) {
        this.master = master;
    }

    public void setEnable(byte enable) {
        this.enable = enable;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public byte getMaster() {
        return master;
    }

    public byte getEnable() {
        return enable;
    }

    public byte getFlags() {
        return flags;
    }

    public void setRegisters(Registers r) {
        this.r = r;
    }

    public void requestInterruption(byte interruption) {
        flags |= interruption;
    }

    public void vblank() {
        if ((flags & Ivblank) != 0) {
            master = 0;
            r.setPC((short) 0x40);
            flags &= ~Ivblank;
        }
    }

    public void lcdStat() {
        if ((flags & Ilcdstat) != 0) {
            master = 0;
            r.setPC((short) 0x48);
            flags &= ~Ilcdstat;
        }
    }

    public void timer() {
        if ((flags & Itimer) != 0) {
            master = 0;
            r.setPC((short) 0x50);
            flags &= ~Itimer;
        }
    }

    public void serial() {
        if ((flags & Iserial) != 0) {
            master = 0;
            r.setPC((short) 0x58);
            flags &= ~Iserial;
        }
    }

    public void joypad() {
        if ((flags & Ijoypad) != 0) {
            master = 0;
            r.setPC((short) 0x60);
            flags &= ~Ijoypad;
        }
    }

    public void checkInterruptions() {
        if (master == 1) {
            if ((enable & flags & Ivblank) != 0) {
                vblank();
            } else if ((enable & flags & Ilcdstat) != 0) {
                lcdStat();
            } else if ((enable & flags & Itimer) != 0) {
                timer();
            } else if ((enable & flags & Iserial) != 0) {
                serial();
            } else if ((enable & flags & Ijoypad) != 0) {
                joypad();
            }
        }
    }

}
