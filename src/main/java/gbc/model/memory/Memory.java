package gbc.model.memory;

import gbc.model.cartridge.Cartridge;

public class Memory {
    private final byte[] memoryBank = new byte[0x10000]; // 65,536 bytes for the entire addressable range
    private Cartridge cartridge;

    public Memory() {
        reset();
    }

    public int readByte(int address) {
        if (address >= 0x0000 && address < 0x8000) {
            return cartridge.read(address) & 0xFF; // Ensure unsigned byte
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            return memoryBank[address] & 0xFF; // Ensure unsigned byte
        } else {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    public int readChar(int address) {
        int high = readByte(address + 1);
        int low = readByte(address);
        return (high << 8) | low;
    }

    public void writeByte(int address, int value) {
        byte byteValue = (byte) (value & 0xFF); // Ensure we store as byte

        if (address >= 0x0000 && address < 0x8000) {
            cartridge.write(address, byteValue);
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            memoryBank[address] = byteValue;
        } else {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    public void writeChar(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
    }

    public void reset() {
        for (int i = 0; i < memoryBank.length; i++) {
            memoryBank[i] = 0x00;
        }
        // Initialize other registers as required
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(String.format("%-6s | %-4s | %-6s | %-4s | %-6s | %-4s%n",
                "Address", "Value", "Address", "Value", "Address", "Value"));

        // Separator
        sb.append("------------------------------------------------------\n");

        for (int i = 0; i < memoryBank.length; i += 16) {
            for (int j = 0; j < 16; j++) {
                int address = i + j;
                if (address < memoryBank.length) {
                    // Address Column
                    sb.append(String.format("%04X", address));

                    // Value Column
                    sb.append(String.format(" | %02X ", memoryBank[address]));

                    // Separator for next column set or end of line
                    if (j % 5 == 4) {
                        sb.append("| ");
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public char popFromStack() {
    	char value = (char) readChar(0xFFFD);
    	writeChar(0xFFFD, readChar(0xFFFD) + 1);
    	return value;
    }

    public void pushToStack(byte value) {
    	writeChar(0xFFFD, readChar(0xFFFD) - 1);
    	writeByte(readChar(0xFFFD), value);
    }



    // Additional methods if needed
}
