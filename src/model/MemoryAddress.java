package model;

public class MemoryAddress {
    private byte value;
    MemoryAddress (byte _value){
        this.value = _value;
    }
    public byte getValue(){
        return this.value;
    }
    public void setValue(byte _value){
        this.value = _value;
    }
}
