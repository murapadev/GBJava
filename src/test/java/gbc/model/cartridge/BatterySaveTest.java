package gbc.model.cartridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gbc.model.GameBoyColor;

class BatterySaveTest {

    @TempDir
    Path tempDir;

    private Path writeRom(String name, int type) throws Exception {
        return writeRom(name, type, 0x02); // 8 KiB
    }

    private Path writeRom(String name, int type, int ramSizeCode) throws Exception {
        byte[] data = new byte[0x8000];
        data[0x0147] = (byte) type;
        data[0x0149] = (byte) ramSizeCode;
        Path rom = tempDir.resolve(name);
        Files.write(rom, data);
        return rom;
    }

    private static Cartridge load(Path rom) throws Exception {
        Cartridge cart = CartridgeFactory.create(Files.readAllBytes(rom));
        cart.setRomPath(rom.toString());
        return cart;
    }

    @Test
    void sramRoundTripsThroughSavFile() throws Exception {
        Path rom = writeRom("game.gb", 0x1B); // MBC5+RAM+BATTERY
        Cartridge cart = load(rom);
        cart.write(0x0000, (byte) 0x0A);
        cart.write(0xA123, (byte) 0x5A);
        cart.saveSRAM();

        Path sav = tempDir.resolve("game.sav");
        assertTrue(Files.exists(sav));

        Cartridge reloaded = load(rom);
        reloaded.loadSRAM();
        reloaded.write(0x0000, (byte) 0x0A);
        assertEquals(0x5A, reloaded.read(0xA123) & 0xFF);
    }

    @Test
    void fallbackRamIsUsableButNeverPersisted() throws Exception {
        Path rom = writeRom("test.gb", 0x03, 0x00); // MBC1+RAM+BATTERY, header RAM size 0
        Cartridge cart = load(rom);
        cart.write(0x0000, (byte) 0x0A);
        cart.write(0xA000, (byte) 0x42);
        assertEquals(0x42, cart.read(0xA000) & 0xFF);

        cart.saveSRAM();
        assertFalse(Files.exists(tempDir.resolve("test.sav")));
    }

    @Test
    void swappingCartridgeSavesThePreviousGame() throws Exception {
        Path first = writeRom("first.gb", 0x03); // MBC1+RAM+BATTERY
        Path second = writeRom("second.gb", 0x03);

        GameBoyColor gbc = new GameBoyColor();
        Cartridge cart = load(first);
        gbc.insertCartridge(cart);
        cart.write(0x0000, (byte) 0x0A);
        cart.write(0xA000, (byte) 0x77);

        gbc.insertCartridge(load(second));

        byte[] saved = Files.readAllBytes(tempDir.resolve("first.sav"));
        assertEquals(0x77, saved[0] & 0xFF);
        assertArrayEquals(cart.getRam(), saved);
    }
}
