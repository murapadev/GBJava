package gbc.model.graphics;

import gbc.model.cartridge.Cartridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a DMG-on-CGB palette table and selects a palette for DMG cartridges.
 * This emulates the CGB boot ROM palette selection when no boot ROM is present.
 *
 * <p>Selection rules (matching CGB boot ROM behavior):
 * <ol>
 *   <li>Match by exact ROM title (from cartridge header bytes $0134-$0143)</li>
 *   <li>Match by title + old licensee code ($014B) for titles with multiple
 *       publishers (not yet implemented — requires licensee field in Cartridge)</li>
 *   <li>Match by header checksum ($014D) as a fallback for common checksums</li>
 *   <li>Fall back to "default" palette (grayscale)</li>
 * </ol>
 *
 * <p>Palettes are loaded from {@code dmg_cgb_palettes.properties} (bundled resource)
 * or from an external file specified by {@code emulator.dmgCgbPaletteTable} system property.
 */
public final class DmgCgbPaletteTable {
    public record DmgCgbPalette(int[] bg, int[] obj0, int[] obj1) {
        public int[] bg() { return bg; }
        public int[] obj0() { return obj0; }
        public int[] obj1() { return obj1; }
    }

    private static final Logger LOGGER = Logger.getLogger(DmgCgbPaletteTable.class.getName());
    private static final String DEFAULT_RESOURCE = "/dmg_cgb_palettes.properties";
    private static final DmgCgbPaletteTable INSTANCE = new DmgCgbPaletteTable();

    private final Map<String, DmgCgbPalette> palettesByName = new HashMap<>();
    private final Map<String, String> titleMap = new HashMap<>();
    private final Map<Integer, String> checksumMap = new HashMap<>();

    private DmgCgbPaletteTable() {
        loadFromProperties();
    }

    public static DmgCgbPaletteTable getInstance() {
        return INSTANCE;
    }

    public DmgCgbPalette selectFor(Cartridge cartridge) {
        String title = cartridge != null ? cartridge.getTitle() : "";
        int checksum = cartridge != null ? cartridge.getHeaderChecksum() : 0;

        String paletteName = null;
        if (!title.isBlank()) {
            paletteName = titleMap.get(title);
        }
        if (paletteName == null) {
            paletteName = checksumMap.get(checksum);
        }
        if (paletteName == null) {
            paletteName = "default";
        }

        DmgCgbPalette palette = palettesByName.get(paletteName);
        if (palette == null) {
            palette = palettesByName.get("default");
        }
        return palette;
    }

    private void loadFromProperties() {
        Properties props = new Properties();
        String externalPath = System.getProperty("emulator.dmgCgbPaletteTable");
        if (externalPath != null && !externalPath.isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(externalPath))) {
                props.load(in);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load DMG/CGB palette table from " + externalPath, e);
            }
        }

        if (props.isEmpty()) {
            try (InputStream in = DmgCgbPaletteTable.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                if (in != null) {
                    props.load(in);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load default DMG/CGB palette table", e);
            }
        }

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key).trim();
            if (key.startsWith("palette.")) {
                String[] parts = key.split("\\.");
                if (parts.length != 3) {
                    continue;
                }
                String name = parts[1].toLowerCase(Locale.ROOT);
                String channel = parts[2].toLowerCase(Locale.ROOT);
                DmgCgbPalette palette = palettesByName.get(name);
                int[] bg = palette != null ? palette.bg() : null;
                int[] obj0 = palette != null ? palette.obj0() : null;
                int[] obj1 = palette != null ? palette.obj1() : null;

                int[] colors = parsePaletteColors(value);
                if (colors == null) {
                    continue;
                }
                switch (channel) {
                    case "bg" -> bg = colors;
                    case "obj0" -> obj0 = colors;
                    case "obj1" -> obj1 = colors;
                    default -> {
                        continue;
                    }
                }
                if (bg == null) {
                    bg = new int[] { 0x7FFF, 0x5AD6, 0x294A, 0x0000 };
                }
                if (obj0 == null) {
                    obj0 = bg.clone();
                }
                if (obj1 == null) {
                    obj1 = bg.clone();
                }
                palettesByName.put(name, new DmgCgbPalette(bg, obj0, obj1));
            } else if (key.startsWith("map.title.")) {
                String title = key.substring("map.title.".length()).trim().toUpperCase(Locale.ROOT);
                titleMap.put(title, value.toLowerCase(Locale.ROOT));
            } else if (key.startsWith("map.checksum.")) {
                String checksumText = key.substring("map.checksum.".length()).trim();
                try {
                    int checksum = Integer.parseInt(checksumText, 16) & 0xFF;
                    checksumMap.put(checksum, value.toLowerCase(Locale.ROOT));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid checksum entries
                }
            }
        }

        if (!palettesByName.containsKey("default")) {
            int[] dmg = new int[] { 0x7FFF, 0x5AD6, 0x294A, 0x0000 };
            palettesByName.put("default", new DmgCgbPalette(dmg, dmg.clone(), dmg.clone()));
        }
    }

    private int[] parsePaletteColors(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        int[] colors = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                String token = parts[i].trim().replace("0x", "").replace("0X", "");
                colors[i] = Integer.parseInt(token, 16) & 0x7FFF;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return colors;
    }
}
