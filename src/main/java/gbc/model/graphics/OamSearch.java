package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * OAM search phase - finds sprites that should be rendered on the current
 * scanline.
 */
public class OamSearch {
    private final Memory memory;
    private final SpritePosition[] sprites;
    private int activeSpriteCount;
    private int mode3Duration;

    public OamSearch(Memory memory) {
        this.memory = memory;
        this.sprites = new SpritePosition[10]; // Up to 10 sprites per scanline
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = new SpritePosition();
        }
        this.activeSpriteCount = 0;
        this.mode3Duration = 172; // Base duration
    }

    public void searchSprites(int ly) {
        // Clear previous sprites
        for (SpritePosition sprite : sprites) {
            sprite.disable();
        }

        activeSpriteCount = 0;
        // Read LCDC register (0xFF40) to determine sprite enable/height
        int lcdc = memory.getLcdc() & 0xFF;
        boolean spritesEnabled = (lcdc & 0x02) != 0; // Bit 1: OBJ enable
        int spriteHeight = (lcdc & 0x04) != 0 ? 16 : 8; // Bit 2: 0=8x8, 1=8x16
        
        // Read SCX for mode 3 timing calculation
        int scx = memory.getScx() & 0xFF;

        if (spritesEnabled) {
            // Search through all 40 sprites in OAM
            for (int i = 0; i < 40 && activeSpriteCount < 10; i++) {
                int baseAddr = 0xFE00 + i * 4;
                int yPos = (memory.getOamByte(i * 4) & 0xFF) - 16;

                // Check if sprite intersects current scanline
                if (ly >= yPos && ly < yPos + spriteHeight) {
                    int xPos = (memory.getOamByte(i * 4 + 1) & 0xFF) - 8;
                    int tileId = memory.getOamByte(i * 4 + 2) & 0xFF;
                    int attributes = memory.getOamByte(i * 4 + 3) & 0xFF;
                    sprites[activeSpriteCount].enable(xPos, yPos, baseAddr, tileId, attributes);
                    activeSpriteCount++;
                }
            }
        }
        
        // Calculate Mode 3 duration based on:
        // - Base: 172 T-cycles
        // - SCX penalty: (SCX % 8) cycles for initial scroll discard
        // - Sprite penalty: approximately 6-11 cycles per sprite
        //   (simplified to 11 cycles per visible sprite on screen)
        int scxPenalty = scx % 8;
        int spritePenalty = 0;
        
        if (spritesEnabled) {
            for (int i = 0; i < activeSpriteCount; i++) {
                if (sprites[i].isEnabled()) {
                    int spriteX = sprites[i].getX();
                    // Sprites on screen (X position 0-159, accounting for -8 offset)
                    if (spriteX > -8 && spriteX < 160) {
                        // Each sprite adds 6-11 cycles depending on X position
                        // Simplified: add 11 cycles for worst case
                        spritePenalty += 11;
                    }
                }
            }
        }
        
        // Mode 3 duration: base + SCX penalty + sprite penalty
        // Maximum is 289 cycles (172 + 7 SCX + 110 for 10 sprites)
        mode3Duration = Math.min(289, 172 + scxPenalty + spritePenalty);
    }

    public void reset() {
        for (SpritePosition sprite : sprites) {
            sprite.disable();
        }
        activeSpriteCount = 0;
        mode3Duration = 172;
    }

    public SpritePosition[] getSprites() {
        return sprites;
    }
    
    /**
     * Returns the number of sprites found on the current scanline.
     */
    public int getActiveSpriteCount() {
        return activeSpriteCount;
    }
    
    /**
     * Returns the calculated Mode 3 duration for the current scanline.
     * This varies based on SCX value and number of sprites.
     */
    public int getMode3Duration() {
        return mode3Duration;
    }
}
