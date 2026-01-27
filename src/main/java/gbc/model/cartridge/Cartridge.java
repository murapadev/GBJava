package gbc.model.cartridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Cartridge {
    private static final Logger LOGGER = Logger.getLogger(Cartridge.class.getName());
    
    protected byte[] data;
    protected byte[] ram;
    protected Path romPath;
    protected boolean hasBattery = false;

    public Cartridge(byte[] data) {
        this.data = data;
    }

    // Use 'int' for address to accommodate the full range of Game Boy addresses
    public abstract byte read(int address);

    public abstract void write(int address, byte value);

    public byte[] getData() {
        return data;
    }

    /**
     * Returns the title from the ROM header (0x0134-0x0143), trimmed and uppercased.
     */
    public String getTitle() {
        if (data == null || data.length < 0x144) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0x0134; i <= 0x0143; i++) {
            int value = data[i] & 0xFF;
            if (value == 0x00) {
                break;
            }
            if (value >= 0x20 && value <= 0x7E) {
                sb.append((char) value);
            }
        }
        return sb.toString().trim().toUpperCase();
    }

    /**
     * Returns the header checksum (0x014D).
     */
    public int getHeaderChecksum() {
        if (data == null || data.length <= 0x14D) {
            return 0x00;
        }
        return data[0x14D] & 0xFF;
    }

    public byte[] getRam() {
        return ram;
    }

    /**
     * Returns the CGB flag from ROM header (0x0143).
     * 0x80 = CGB compatible, 0xC0 = CGB only, 0x00 = DMG only.
     */
    public int getCgbFlag() {
        if (data == null || data.length <= 0x143) {
            return 0x00;
        }
        return data[0x143] & 0xFF;
    }

    public boolean isCgbCompatible() {
        int flag = getCgbFlag();
        return flag == 0x80 || flag == 0xC0;
    }

    public boolean isCgbOnly() {
        return getCgbFlag() == 0xC0;
    }
    
    /**
     * Sets the ROM file path. Used to determine where to save SRAM.
     */
    public void setRomPath(String path) {
        this.romPath = Paths.get(path);
    }
    
    /**
     * Returns true if this cartridge has a battery for SRAM persistence.
     */
    public boolean hasBattery() {
        return hasBattery;
    }
    
    /**
     * Gets the save file path for battery-backed RAM.
     * The save file is stored alongside the ROM with a .sav extension.
     */
    public Path getSaveFilePath() {
        if (romPath == null) {
            return null;
        }
        String romName = romPath.getFileName().toString();
        String saveName = romName.replaceFirst("\\.[^.]+$", ".sav");
        String saveDir = System.getProperty("emulator.saveDir");
        if (saveDir != null && !saveDir.isBlank()) {
            return Path.of(saveDir).resolve(saveName);
        }
        return romPath.getParent().resolve(saveName);
    }
    
    /**
     * Saves the battery-backed RAM to disk.
     * Only saves if the cartridge has a battery and RAM.
     */
    public void saveSRAM() {
        if (!hasBattery || ram == null || ram.length == 0) {
            return;
        }
        
        Path savePath = getSaveFilePath();
        if (savePath == null) {
            LOGGER.warning("Cannot save SRAM: ROM path not set");
            return;
        }
        
        try {
            Files.createDirectories(savePath.getParent());
            Files.write(savePath, ram);
            LOGGER.log(Level.INFO, () -> String.format("Saved SRAM to %s (%d bytes)", savePath, ram.length));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save SRAM to " + savePath, e);
        }
    }
    
    /**
     * Loads the battery-backed RAM from disk.
     * Only loads if the cartridge has a battery and a save file exists.
     */
    public void loadSRAM() {
        if (!hasBattery || ram == null) {
            return;
        }
        
        Path savePath = getSaveFilePath();
        if (savePath == null || !Files.exists(savePath)) {
            LOGGER.log(Level.FINE, () -> "No save file found: " + savePath);
            return;
        }
        
        try {
            byte[] savedData = Files.readAllBytes(savePath);
            int copyLength = Math.min(savedData.length, ram.length);
            System.arraycopy(savedData, 0, ram, 0, copyLength);
            LOGGER.log(Level.INFO, () -> String.format("Loaded SRAM from %s (%d bytes)", savePath, copyLength));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load SRAM from " + savePath, e);
        }
    }
}
