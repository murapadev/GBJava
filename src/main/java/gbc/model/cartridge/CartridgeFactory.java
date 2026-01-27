package gbc.model.cartridge;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CartridgeFactory {

    private static final Logger LOGGER = Logger.getLogger(CartridgeFactory.class.getName());

    /**
     * Creates a cartridge from ROM data and sets the battery flag based on cartridge type.
     * 
     * Cartridge types with battery (from https://gbdev.io/pandocs/The_Cartridge_Header.html):
     * - 0x03: MBC1+RAM+BATTERY
     * - 0x06: MBC2+BATTERY
     * - 0x09: ROM+RAM+BATTERY
     * - 0x0D: MMM01+RAM+BATTERY
     * - 0x0F: MBC3+TIMER+BATTERY
     * - 0x10: MBC3+TIMER+RAM+BATTERY
     * - 0x13: MBC3+RAM+BATTERY
     * - 0x1B: MBC5+RAM+BATTERY
     * - 0x1E: MBC5+RUMBLE+RAM+BATTERY
     * - 0x22: MBC7+SENSOR+RUMBLE+RAM+BATTERY
     * - 0xFF: HuC1+RAM+BATTERY
     */
    public static Cartridge create(byte[] data) {
        byte value = data[0x147];
        LOGGER.log(Level.INFO, () -> String.format("Detected cartridge type 0x%02X", value));
        
        Cartridge cartridge = switch (value) {
            case 0x00 -> new ROM(data);
            case 0x01, 0x02 -> new MBC1(data, false);  // MBC1, MBC1+RAM (no battery)
            case 0x03 -> new MBC1(data, true);          // MBC1+RAM+BATTERY
            case 0x05 -> new MBC2(data, false);         // MBC2 (no battery)
            case 0x06 -> new MBC2(data, true);          // MBC2+BATTERY
            case 0x0F, 0x10 -> new MBC3(data, true);    // MBC3+TIMER+BATTERY, MBC3+TIMER+RAM+BATTERY
            case 0x11, 0x12 -> new MBC3(data, false);   // MBC3, MBC3+RAM (no battery)
            case 0x13 -> new MBC3(data, true);          // MBC3+RAM+BATTERY
            case 0x19, 0x1A -> new MBC5(data, false);   // MBC5, MBC5+RAM (no battery)
            case 0x1B -> new MBC5(data, true);          // MBC5+RAM+BATTERY
            case 0x1C, 0x1D -> new MBC5(data, false);   // MBC5+RUMBLE, MBC5+RUMBLE+RAM (no battery)
            case 0x1E -> new MBC5(data, true);          // MBC5+RUMBLE+RAM+BATTERY
            default -> new ROM(data); // Fallback to ROM for unsupported types
        };
        
        if (cartridge.hasBattery()) {
            LOGGER.log(Level.INFO, "Cartridge has battery-backed RAM");
        }
        
        return cartridge;
    }
}
