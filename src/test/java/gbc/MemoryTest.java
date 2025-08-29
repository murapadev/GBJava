package gbc;

import gbc.controller.FileLoader;
import gbc.model.cartridge.CartridgeFactory;
import gbc.model.memory.Memory;
import gbc.model.cartridge.Cartridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;

public class MemoryTest {
    private Memory memory;
    private Cartridge cartridge;

    @BeforeEach
    public void setUp() {
        memory = new Memory();
        cartridge = CartridgeFactory.create(FileLoader.loadFile("./Pokemon.gb"));
        memory.loadCartridge(cartridge);
    }

    @Test
    public void testReadAndWriteByte() {
        int address = 0x9871;
        byte value = (byte) 0xAB;

        memory.writeByte(address, value);
        int result = memory.readByte(address);

        assertEquals(value & 0xFF, result);
    }

    @Test
    public void testReadAndWriteChar() {
        int address = 0x9871;
        int value = 0xCDEF;

        memory.writeChar(address, value);
        int result = memory.readChar(address);

        assertEquals(value, result);
    }

    @Test
    public void testReadFromCartridge() {
        int address = 0x2000;
        byte value = (byte) 0x55;

        // Store original value
        int originalValue = memory.readByte(address);
        
        cartridge.write(address, value);
        int result = memory.readByte(address);

        // ROM is read-only, so write should have no effect
        assertEquals(originalValue, result);
    }

    @Test
    public void testWriteToCartridge() {
        int address = 0x3000;
        byte value = (byte) 0xAA;

        // Store original value
        int originalValue = cartridge.read(address);
        
        memory.writeByte(address, value);
        int result = cartridge.read(address);

        // ROM is read-only, so write should have no effect
        assertEquals(originalValue, result);
    }

    @Test
    public void testGetMemoryRange() {
        int startAddress = 0x8001;
        int endAddress = 0x8040;

        byte[] values = {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66};

        for (int i = 0; i < values.length; i++) {
            memory.writeByte(startAddress + i, values[i]);
        }

        HashMap<Integer, Byte> result = memory.getMemoryRange(startAddress, endAddress);

        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], result.get(startAddress + i));
        }
    }

    @Test
    public void testReset() {
        int address = 0x9132;
        byte value = (byte) 0xAB;

        memory.writeByte(address, value);
        memory.reset();
        int result = memory.readByte(address);

        assertEquals(0x00, result);
    }

    @Test
    public void testInvalidAddressRead() {
        int invalidAddress = 0x10000; // Beyond the memory range
        assertThrows(IllegalArgumentException.class, () -> {
            memory.readByte(invalidAddress);
        });
    }

    @Test
    public void testInvalidAddressWrite() {
        int invalidAddress = 0x10000; // Beyond the memory range
        assertThrows(IllegalArgumentException.class, () -> {
            memory.writeByte(invalidAddress, (byte) 0x00);
        });
    }
}
