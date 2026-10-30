package gbc.model.cartridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        byte[] data = new byte[0x8000];
        data[0x0147] = (byte) type;
        data[0x0149] = 0x02; // 8 KiB
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
