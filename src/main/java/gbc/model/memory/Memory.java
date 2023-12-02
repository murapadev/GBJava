package gbc.model.memory;

import java.util.HashMap;

import gbc.model.cartridge.Cartridge;

public class Memory {
    private final HashMap<Integer, Byte> memoryBank = new HashMap<>();
    private Cartridge cartridge;

    public Memory() {
        for (int i = 0x0000; i <= 0xFFFF; i++) {
            memoryBank.put(i, (byte) 0x00);
        }
    }

    public byte readByte(int address) {
        if (address >= 0x0000 && address < 0x8000) {
            return cartridge.read(address);
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            return memoryBank.get(address);
        }
        throw new IllegalArgumentException("Invalid address: " + address);
    }

    public char readChar(int address) {
        int high = readByte(address + 1) & 0xFF;
        int low = readByte(address) & 0xFF;
        return (char) ((high << 8) | low);
    }

    public void writeByte(int address, byte value) {
        if (address >= 0x0000 && address < 0x8000) {
            cartridge.write(address, value);
        } else if (address >= 0x8000 && address <= 0xFFFF) {
            memoryBank.put(address, value);
        } else {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    public void writeChar(int address, char value) {
        writeByte(address, (byte) (value & 0xFF));
        writeByte(address + 1, (byte) ((value >> 8) & 0xFF));
    }

    public HashMap<Integer, Byte> getMemoryRange(int startAddress, int endAddress) {
        if (startAddress > endAddress) {
            throw new IllegalArgumentException("Start address must be less than or equal to end address");
        }

        HashMap<Integer, Byte> temp = new HashMap<>();
        for (int i = startAddress; i <= endAddress; i++) {
            temp.put(i, memoryBank.get(i));
        }
        return temp;
    }

    public void loadCartridge(Cartridge cartridge) {
        this.cartridge = cartridge;
    }

    public void reset() {
        for (int i = 0x8000; i <= 0xFFFF; i++) {
            memoryBank.put(i, (byte) 0x00);
        }
        this.writeByte((char) 0xFF05, (byte) 0x00);
        this.writeByte((char) 0xFF06, (byte) 0x00);
        this.writeByte((char) 0xFF07, (byte) 0x00);
        this.writeByte((char) 0xFF10, (byte) 0x80);
        this.writeByte((char) 0xFF11, (byte) 0xBF);
        this.writeByte((char) 0xFF12, (byte) 0xF3);
        this.writeByte((char) 0xFF14, (byte) 0xBF);
        this.writeByte((char) 0xFF16, (byte) 0x3F);
        this.writeByte((char) 0xFF17, (byte) 0x00);
        this.writeByte((char) 0xFF19, (byte) 0xBF);
        this.writeByte((char) 0xFF1A, (byte) 0x7F);
        this.writeByte((char) 0xFF1B, (byte) 0xFF);
        this.writeByte((char) 0xFF1C, (byte) 0x9F);
        this.writeByte((char) 0xFF1E, (byte) 0xBF);
        this.writeByte((char) 0xFF20, (byte) 0xFF);
        this.writeByte((char) 0xFF21, (byte) 0x00);
        this.writeByte((char) 0xFF22, (byte) 0x00);
        this.writeByte((char) 0xFF23, (byte) 0xBF);
        this.writeByte((char) 0xFF24, (byte) 0x77);
        this.writeByte((char) 0xFF25, (byte) 0xF3);
        this.writeByte((char) 0xFF26, (byte) 0xF1);
        this.writeByte((char) 0xFF40, (byte) 0x91);
        this.writeByte((char) 0xFF42, (byte) 0x00);
        this.writeByte((char) 0xFF43, (byte) 0x00);
        this.writeByte((char) 0xFF45, (byte) 0x00);
        this.writeByte((char) 0xFF47, (byte) 0xFC);
        this.writeByte((char) 0xFF48, (byte) 0xFF);
        this.writeByte((char) 0xFF49, (byte) 0xFF);
        this.writeByte((char) 0xFF4A, (byte) 0x00);
        this.writeByte((char) 0xFF4B, (byte) 0x00);
        this.writeByte((char) 0xFFFF, (byte) 0x00);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(String.format("%-6s | %-4s | %-6s | %-4s | %-6s | %-4s%n",
                "Address", "Value", "Address", "Value", "Address", "Value"));

        // Separator
        sb.append("------------------------------------------------------\n");

        for (int i = 0x0000; i <= 0xFFFF; i += 3) {
            for (int j = 0; j < 3; j++) {
                int address = i + j;
                if (address <= 0xFFFF) {
                    // Address Columnx
                    sb.append(String.format("%04X", address));

                    // Value Column
                    sb.append(String.format(" | %02X ", readByte(address)));

                    // Separator for next column set or end of line
                    if (j < 2) {
                        sb.append("| ");
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }


}
