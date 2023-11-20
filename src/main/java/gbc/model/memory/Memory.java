package main.java.model.memory;

import java.util.HashMap;

import main.java.model.cartridge.Cartridge;

public class Memory {
    private HashMap<Short, Byte> memoryBank = new HashMap<>();
    private Cartridge cartridge;

    public Memory() {
        for (int i = 0x0000;(i <= (0xFFFF)); i+=0x1) memoryBank.put((short)i, ((byte) 0x00));
    }

    public byte readByte(short address) {
		if(address<0x8000)
			return cartridge.read(address);
		return memoryBank.get(address);
	}
    public short readShort(short address) {
        if(address<0x8000)
            return (short) (cartridge.read(address) | (cartridge.read((short) (address + 1)) << 8));
        return (short) (readByte(address) | (readByte((short) (address + 1)) << 8));
    }

    public void writeByte(short address, byte value) {
        if(address<0x8000)
            cartridge.write(address,value);
        else
            memoryBank.put(address, value);
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

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
    }

	public void reset() {
		for (int i = 0x0000;(i <= (0xFFFF)); i+=0x1) memoryBank.put((short)i, ((byte) 0x00));
	}
    
}






