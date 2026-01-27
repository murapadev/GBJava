package gbc.model;

/**
 * Represents different Game Boy hardware variants.
 * Each variant has slightly different initial register and I/O states
 * after the boot ROM completes.
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
    SGB("SGB", 0xFF),
    
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
    
    /**
     * Returns the initial DIV counter value (internal 16-bit counter).
     * The visible DIV register (0xFF04) shows the upper 8 bits.
     */
    public int getInitialDivCounter() {
        return switch (this) {
            case DMG -> 0xABCC;
            case DMG0 -> 0x267C; // DMG0 has different boot ROM timing
            case MGB -> 0x99D8;  // Game Boy Pocket
            case SGB -> 0xD3B1;  // Super Game Boy
            case SGB2 -> 0xD49A; // Super Game Boy 2
            case CGB -> 0x1EA0;  // Game Boy Color
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
        if (lower.contains("sgb2")) {
            return SGB2;
        }
        if (lower.contains("sgb")) {
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
