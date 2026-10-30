package gbc.model.cartridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CartridgeRamSizeTest {

    private static byte[] rom(int type, int ramSize) {
        byte[] data = new byte[0x8000];
        data[0x0147] = (byte) type;
        data[0x0149] = (byte) ramSize;
        return data;
    }

    @Test
    void declaredRamSizeIsUsed() {
        assertEquals(32 * 1024, CartridgeFactory.create(rom(0x03, 0x03)).getRam().length);
        assertEquals(128 * 1024, CartridgeFactory.create(rom(0x1B, 0x04)).getRam().length);
    }

    @Test
    void ramTypeWithZeroSizeGetsOneBank() {
        Cartridge cart = CartridgeFactory.create(rom(0x02, 0x00));
        assertEquals(8 * 1024, cart.getRam().length);

        cart.write(0x0000, (byte) 0x0A);
        cart.write(0xA000, (byte) 0x42);
        assertEquals(0x42, cart.read(0xA000) & 0xFF);
    }

    @Test
    void typeWithoutRamStaysWithoutRam() {
        assertNull(CartridgeFactory.create(rom(0x01, 0x00)).getRam());
        assertNull(CartridgeFactory.create(rom(0x11, 0x00)).getRam());
    }
}
