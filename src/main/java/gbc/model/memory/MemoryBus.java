package gbc.model.memory;

/**
 * Abstraction over the memory bus used by CPU and peripherals.
 */
public interface MemoryBus {
    int readByte(int address);
    void writeByte(int address, int value);
    int readChar(int address);
    void writeChar(int address, int value);
    int peekByte(int address);
    int peekChar(int address);
}
