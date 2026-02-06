package gbc.model;

/**
 * Represents different Game Boy hardware variants.
 * Each variant has slightly different initial register and I/O states
 * after the boot ROM completes.
 *
 * <p>Post-boot register values are derived from per-model profiles.
 * CGB-in-DMG mode (i.e., CGB hardware running a DMG-only cartridge)
 * uses the CGB register profile but disables CGB-specific features
 * and applies DMG-compatible palette mapping via {@link gbc.model.graphics.DmgCgbPaletteTable}.
 *
 * <p>The initial A register value encodes the hardware model that the boot ROM
 * would set, allowing games to detect which hardware they're running on.
 */
public enum HardwareType {
    /**
     * Original Game Boy (DMG-01, revision ABC)
     * This is the most common DMG variant.
     */
    DMG("DMG-ABC", 0x01),
    
    /**
     * Original Game Boy (DMG-01, revision 0)
     * Early revision with slightly different boot ROM timing.
     */
    DMG0("DMG-0", 0x01),
    
    /**
     * Game Boy Pocket (MGB-001)
     * Similar to DMG but with different DIV initial value.
     */
    MGB("MGB", 0xFF),
    
    /**
     * Super Game Boy
     * Game Boy adapter for SNES.
     */
    SGB("SGB", 0x01),
    
    /**
     * Super Game Boy 2
     * Updated SGB with faster clock speed.
     */
    SGB2("SGB2", 0xFF),
    
    /**
     * Game Boy Color
     * Full color hardware with extended features.
     */
    CGB("CGB", 0x11);
    
    private final String name;
    private final int initialA;
    
    HardwareType(String name, int initialA) {
        this.name = name;
        this.initialA = initialA;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Returns the initial value of register A after boot ROM.
     * DMG/DMG0: 0x01
     * MGB/SGB/SGB2: 0xFF
     * CGB: 0x11
     */
    public int getInitialA() {
        return initialA;
    }

    public record CpuInitState(
            int a, int f, int b, int c, int d, int e, int h, int l,
            int sp, int pc) {
    }

    public record LcdInitState(
            int lcdc, int stat, int ly, int lyc, int scx, int scy, int wx, int wy,
            int bgp, int obp0, int obp1,
            boolean preserveInitialLycFlag) {
    }
    
    /**
     * Returns the initial DIV counter value (internal 16-bit counter).
     * The visible DIV register (0xFF04) shows the upper 8 bits.
     * Values correspond to post-boot state with no buttons held.
     */
    public int getInitialDivCounter() {
        return switch (this) {
            case DMG -> 0xABCC;
            case DMG0 -> 0x1830; // DMG0 preset tuned against boot_div/boot_hwio no-boot profile
            case MGB -> 0xABCC;  // MGB matches DMG divider high byte in post-boot tests
            // SGB/SGB2: The boot ROM sets DIV high byte to 0x00.
            // Exact startup-phase modeling for SGB boot_div/boot_div2 tests
            // requires simulating the boot ROM timing, which is not yet implemented.
            // Current values produce correct FF04 reads for post-boot state.
            case SGB -> 0x0000;
            case SGB2 -> 0x0000;
            case CGB -> 0x1EA0;  // Game Boy Color
        };
    }

    /**
     * Returns JOYP register default after boot ROM.
     * Bits 7-6 are always read as 1; bits 5-4 are select lines.
     */
    public int getInitialJoypadRegister() {
        return switch (this) {
            case SGB, SGB2 -> 0xFF;
            default -> 0xCF;
        };
    }

    /**
     * Returns the initial LCD dot counter phase used when boot ROM is skipped.
     * This aligns model-specific STAT mode reads observed by boot compatibility tests.
     */
    public int getInitialLcdCycleCounter() {
        return switch (this) {
            case DMG0 -> 172;
            default -> 0;
        };
    }

    /**
     * Returns post-boot CPU register state for the selected hardware profile.
     */
    public CpuInitState getCpuInitState() {
        return switch (this) {
            case DMG -> new CpuInitState(0x01, 0xB0, 0x00, 0x13, 0x00, 0xD8, 0x01, 0x4D, 0xFFFE, 0x0100);
            case DMG0 -> new CpuInitState(0x01, 0x00, 0xFF, 0x13, 0x00, 0xC1, 0x84, 0x03, 0xFFFE, 0x0100);
            case MGB -> new CpuInitState(0xFF, 0xB0, 0x00, 0x13, 0x00, 0xD8, 0x01, 0x4D, 0xFFFE, 0x0100);
            case SGB -> new CpuInitState(0x01, 0x00, 0x00, 0x14, 0x00, 0x00, 0xC0, 0x60, 0xFFFE, 0x0100);
            case SGB2 -> new CpuInitState(0xFF, 0x00, 0x00, 0x14, 0x00, 0x00, 0xC0, 0x60, 0xFFFE, 0x0100);
            case CGB -> new CpuInitState(0x11, 0x80, 0x00, 0x00, 0x00, 0x08, 0x00, 0x7C, 0xFFFE, 0x0100);
        };
    }

    /**
     * Returns post-boot LCD register state for the selected hardware profile.
     */
    public LcdInitState getLcdInitState() {
        return switch (this) {
            case DMG -> new LcdInitState(0x91, 0x85, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, false);
            case DMG0 -> new LcdInitState(0x91, 0x81, 0x91, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, false);
            case MGB -> new LcdInitState(0x91, 0x85, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, false);
            case SGB -> new LcdInitState(0x91, 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, true);
            case SGB2 -> new LcdInitState(0x91, 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, true);
            case CGB -> new LcdInitState(0x91, 0x85, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, false);
        };
    }
    
    /**
     * Returns whether this hardware supports CGB features.
     */
    public boolean isCgb() {
        return this == CGB;
    }
    
    /**
     * Returns whether this is a Super Game Boy variant.
     */
    public boolean isSgb() {
        return this == SGB || this == SGB2;
    }
    
    /**
     * Detects the appropriate hardware type from the ROM name.
     * Mooneye tests use specific naming conventions.
     */
    public static HardwareType fromRomName(String romName) {
        if (romName == null) {
            return DMG;
        }
        String lower = romName.toLowerCase();
        
        // Check for specific hardware variant markers
        // Order matters - more specific patterns first
        
        if (lower.contains("dmg0") || lower.contains("dmg-0") || lower.contains("-dmg0")) {
            return DMG0;
        }
        if (lower.contains("sgb2") || lower.matches(".*-s2\\.gb$")) {
            return SGB2;
        }
        if (lower.contains("sgb") || lower.matches(".*-s\\.gb$")) {
            return SGB;
        }
        if (lower.contains("cgb") || lower.contains("gbc")) {
            return CGB;
        }
        // "dmgABCmgb" means "works on DMG-ABC and MGB" - use DMG as default
        // Only return MGB if it's specifically for MGB only (like "-mgb.gb" or "_mgb.gb")
        if (lower.matches(".*[-_]mgb\\.gb$") || lower.contains("-mgb-")) {
            return MGB;
        }
        // Default: DMG (covers dmgABC, dmgABCmgb variants)
        return DMG;
    }
}
