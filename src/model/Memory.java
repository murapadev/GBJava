package model;

import java.util.HashMap;

public class Memory {
    private HashMap<Short, MemoryAddress> memoryBank = new HashMap<>();

    public Memory() {
        for (int i = 0x0000;(i <= (0xFFFF)); i+=0x1) memoryBank.put((short)i, new MemoryAddress((byte) 0x00));
    }

    public MemoryAddress getMemoryAddress(Short address) {
        return memoryBank.get(address);
    }

    public byte readByte(short address) {
        return memoryBank.get(address).getValue();
    }

    public short readShort(short address) {
        return (short) (readByte(address) | (readByte((short) (address + 1)) << 8));
    }

    public short readShortFromStack(Registers r) {
        short stack = r.getSP();
        short value = readShort(stack);
        stack += 0x2;
        r.setSP(stack);
        return value;
    }

    public void writeByte(short address, byte value) {
        memoryBank.get(address).setValue(value);
    }

    public void writeShort(short address, short value) {
        writeByte(address, (byte) (value & 0x00FF));
        writeByte((short) (address + 1), (byte) ((value & 0xFF00) >> 8));
    }

    public void writeShortToStack(Registers r, short value) {
        short address = (short) (r.getSP() - 0x2);
        r.setSP(address);
        writeShort(address, value);
    }

    HashMap<Short,MemoryAddress> getMemoryRange(short _b,short _e){
        HashMap<Short,MemoryAddress> temp = new HashMap<>();
        if(_b<=_e)
            for(int i = _b;i<=_e;i++)
                temp.put((short)i,memoryBank.get(i));

        return temp;

    }


}






