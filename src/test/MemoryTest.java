package test;

import model.Memory;
import model.MemoryAddress;
import model.Registers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class usingMemoryAddress{
    MemoryAddress a;
    usingMemoryAddress(MemoryAddress _a){
        a = _a;
    }

    void usingValue(){
        byte value = a.getValue();
        value += 2;
        a.setValue(value);
    }
}

class MemoryTest {

    @Test
    void getMemoryAddress() {
        Memory memoria = new Memory();
        usingMemoryAddress a =new usingMemoryAddress( memoria.getMemoryAddress((short)0xFFFF));
        a.usingValue();
        assertEquals(memoria.readByte((short)0xFFFF),(short)2);
    }

    @Test
    void readByte() {
        Memory memoria = new Memory();
        short address = (short) 0xC000;
        byte value = (byte)0xFF;
        memoria.writeByte(address,value);
        assertEquals(memoria.readByte(address),value);
    }

    @Test
    void readShort() {
        Memory memoria = new Memory();
        short address = (short) 0xC000;
        short value = (short)0xFFFF;
        memoria.writeShort(address,value);
        assertEquals(memoria.readShort(address),value);

    }

    @Test
    void readShortFromStack() {
        Memory memoria = new Memory();
        Registers r = new Registers();
        r.setSP((short)0xC006);
        short value1 = (short) 0xC56F;
        short value2 = (short) 0xC57F;
        short value3 = (short) 0xC321;
        memoria.writeShortToStack(r,value3);
        memoria.writeShortToStack(r,value2);
        memoria.writeShortToStack(r,value1);
        short read1 = memoria.readShortFromStack(r);
        short read2 = memoria.readShortFromStack(r);
        short read3 = memoria.readShortFromStack(r);
        System.out.printf("%04x",read1);
        System.out.printf("%04x",read2);
        System.out.printf("%04x",read3);
        assertEquals(read3,value3);
        assertEquals(read2,value2);
        assertEquals(read1,value1);
    }

}