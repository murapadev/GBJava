package model;

public class Interruptions {
    private MemoryAddress master;
    private MemoryAddress enable;
    private MemoryAddress flags;

    private Registers r;

    private final static byte Ivblank = (1);
    private final static byte Ilcdstat = (1 << 1);
    private final static byte Itimer = (1 << 2);
    private final static byte Iserial = (1 << 3);
    private final static byte Ijoypad = (1 << 4);

    public Interruptions(Memory memoria){
        enable = memoria.getMemoryAddress((short)0xFFFF);
        flags = memoria.getMemoryAddress((short)0xFF0F);
        master = new MemoryAddress((byte) 1);

    }

    //TODO Add implementation of some Interruption Methods
    //TODO: Vblank
    //TODO: Joypad
    //TODO: lcdStat
    //TODO: Timer
    //TODO: Serial

}
