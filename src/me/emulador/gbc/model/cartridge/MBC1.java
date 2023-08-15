package me.emulador.gbc.model.cartridge;

public class MBC1 extends Cartridge {
    // Variables específicas para MBC1

    public MBC1(byte[] data) {
        super(data);
    }

    @Override
    public byte read(int address) {
        // Implementación específica para MBC1
        return 0; // placeholder
    }

    @Override
    public void write(int address, byte value) {
        // Implementación específica para MBC1
    }
}
