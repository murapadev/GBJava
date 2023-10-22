package me.emulador.gbc.model.memory;

import java.util.HashMap;

public class Memory {
    private HashMap<Short, Byte> memoryBank = new HashMap<>();

    public Memory() {
        for (int i = 0x0000;(i <= (0xFFFF)); i+=0x1) memoryBank.put((short)i, ((byte) 0x00));
    }

    public byte readByte(short address) {
        return memoryBank.get(address);
    }

    public short readShort(short address) {
        return (short) (readByte(address) | (readByte((short) (address + 1)) << 8));
    }

    public void writeByte(short address, byte value) {
        memoryBank.get(address);
    }

    public void writeShort(short address, short value) {
        writeByte(address, (byte) (value & 0x00FF));
        writeByte((short) (address + 1), (byte) ((value & 0xFF00) >> 8));
    }

    HashMap<Short,Byte> getMemoryRange(short _b,short _e){
        HashMap<Short,Byte> temp = new HashMap<>();
        if(_b<=_e)
            for(int i = _b;i<=_e;i++)
                temp.put((short)i,memoryBank.get(i));

        return temp;

    }


}






