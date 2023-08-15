package me.emulador.gbc.model;

/**
 * Represents the memory of the Game Boy Color.
 */
public class Memory {
    // Usamos un array de bytes en lugar de un HashMap para representar la memoria.
    private final byte[] memoryBank = new byte[0x10000]; // 0x0000 to 0xFFFF

    /**
     * Constructor de la clase Memory.
     * Inicializa la memoria con valores predeterminados.
     */
    public Memory() {
        for (int i = 0; i <= 0xFFFF; i++) {
            memoryBank[i] = 0x00;
        }
    }

    /**
     * Lee un byte de una dirección específica en la memoria.
     * 
     * @param address La dirección de memoria de la que se leerá el byte.
     * @return El byte leído de la dirección especificada.
     */
    public byte readByte(short address) {
        return memoryBank[address & 0xFFFF];
    }

    /**
     * Lee un valor short (2 bytes) de una dirección específica en la memoria.
     * 
     * @param address La dirección de memoria de la que se leerá el short.
     * @return El valor short leído de la dirección especificada.
     */
    public short readShort(short address) {
        return (short) (readByte(address) | (readByte((short) (address + 1)) << 8));
    }

    /**
     * Escribe un byte en una dirección específica en la memoria.
     * 
     * @param address La dirección de memoria en la que se escribirá el byte.
     * @param value El byte que se escribirá en la dirección especificada.
     */
    public void writeByte(short address, byte value) {
        memoryBank[address & 0xFFFF] = value;
    }

    /**
     * Escribe un valor short (2 bytes) en una dirección específica en la memoria.
     * 
     * @param address La dirección de memoria en la que se escribirá el short.
     * @param value El valor short que se escribirá en la dirección especificada.
     */
    public void writeShort(short address, short value) {
        writeByte(address, (byte) (value & 0x00FF));
        writeByte((short) (address + 1), (byte) ((value & 0xFF00) >> 8));
    }
}
