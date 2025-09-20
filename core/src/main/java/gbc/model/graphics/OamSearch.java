package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * OAM search phase - finds sprites that should be rendered on the current
 * scanline.
 */
public class OamSearch {
    private final Memory memory;
    private final SpritePosition[] sprites;

    public OamSearch(Memory memory) {
        this.memory = memory;
        this.sprites = new SpritePosition[10]; // Up to 10 sprites per scanline
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = new SpritePosition();
        }
    }

    public void searchSprites(int ly) {
        // Clear previous sprites
        for (SpritePosition sprite : sprites) {
            sprite.disable();
        }

        int spriteCount = 0;
        int spriteHeight = 8; // TODO: Get from LCDC register

        // Search through all 40 sprites in OAM
        for (int i = 0; i < 40 && spriteCount < 10; i++) {
            int baseAddr = 0xFE00 + i * 4;
            int yPos = (memory.readByte(baseAddr) & 0xFF) - 16;

            // Check if sprite intersects current scanline
            if (ly >= yPos && ly < yPos + spriteHeight) {
                int xPos = (memory.readByte(baseAddr + 1) & 0xFF) - 8;
                sprites[spriteCount].enable(xPos, yPos, baseAddr);
                spriteCount++;
            }
        }
    }

    public SpritePosition[] getSprites() {
        return sprites;
    }
}