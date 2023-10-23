package me.emulador.gbc.model.cartridge;

public abstract class Cartridge {
    protected byte[] data;

    public Cartridge(byte[] data) {
        this.data = data;
    }

    public abstract byte read(int address);
    public abstract void write(int address, byte value);
    public byte[] getData(){
        return data;
    };
}
