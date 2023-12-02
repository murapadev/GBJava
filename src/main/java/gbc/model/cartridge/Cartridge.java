package gbc.model.cartridge;

public abstract class Cartridge {
    protected byte[] data;

    public Cartridge(byte[] data) {
        this.data = data;
    }

    // Use 'int' for address to accommodate the full range of Game Boy addresses
    public abstract byte read(int address);
    public abstract void write(int address, byte value);

    public byte[] getData() {
        return data;
    }
}
