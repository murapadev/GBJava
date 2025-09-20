package gbc.model.graphics;

import gbc.model.memory.Memory;

/**
 * Fetcher for Game Boy PPU pixel pipeline.
 * Manages fetching tile data and pushing pixels to the FIFO.
 */
public class Fetcher {
    public enum State {
        READ_TILE_ID,
        READ_TILE_DATA_1,
        READ_TILE_DATA_2,
        PUSH,
        READ_SPRITE_TILE_ID,
        READ_SPRITE_FLAGS,
        READ_SPRITE_DATA_1,
        READ_SPRITE_DATA_2,
        PUSH_SPRITE
    }

    private final PixelFifo fifo;
    private final Memory memory;

    private State state;
    private boolean fetchingDisabled;

    private int mapAddress;
    private int xOffset;
    private int tileDataAddress;
    private boolean tileIdSigned;
    private int tileLine;
    private int tileId;
    private TileAttributes tileAttributes;

    private int tileData1;
    private int tileData2;

    // Sprite-related fields
    private int spriteTileLine;
    private SpritePosition sprite;
    private TileAttributes spriteAttributes;
    private int spriteOffset;
    private int spriteOamIndex;

    private final int[] pixelLine = new int[8];

    public Fetcher(PixelFifo fifo, Memory memory) {
        this.fifo = fifo;
        this.memory = memory;
        this.state = State.READ_TILE_ID;
    }

    public void init() {
        this.state = State.READ_TILE_ID;
        this.fetchingDisabled = false;
    }

    public void startFetching(int mapAddress, int tileDataAddress, int xOffset,
            boolean tileIdSigned, int tileLine) {
        this.mapAddress = mapAddress;
        this.tileDataAddress = tileDataAddress;
        this.xOffset = xOffset;
        this.tileIdSigned = tileIdSigned;
        this.tileLine = tileLine;
        this.state = State.READ_TILE_ID;
        this.fetchingDisabled = false;
    }

    public void tick() {
        if (fetchingDisabled) {
            return;
        }

        switch (state) {
            case READ_TILE_ID -> {
                int mapIndex = ((tileLine / 8) * 32) + xOffset;
                int mapAddr = mapAddress + mapIndex;
                tileId = memory.readByte(mapAddr) & 0xFF;
                // For GBC, tile attributes would be read here from VRAM bank 1
                tileAttributes = new TileAttributes(0); // Default for DMG
                state = State.READ_TILE_DATA_1;
            }

            case READ_TILE_DATA_1 -> {
                int tileAddr = tileDataAddress + (tileIdSigned ? (byte) tileId * 16 : tileId * 16);
                tileData1 = memory.readByte(tileAddr + (tileLine % 8) * 2) & 0xFF;
                state = State.READ_TILE_DATA_2;
            }

            case READ_TILE_DATA_2 -> {
                int tileAddr = tileDataAddress + (tileIdSigned ? (byte) tileId * 16 : tileId * 16);
                tileData2 = memory.readByte(tileAddr + (tileLine % 8) * 2 + 1) & 0xFF;
                state = State.PUSH;
            }

            case PUSH -> {
                if (fifo.getLength() <= 8) {
                    // Convert tile data to pixel line
                    zip(tileData1, tileData2, false, pixelLine);
                    fifo.enqueue8Pixels(pixelLine, tileAttributes);
                    xOffset = (xOffset + 1) % 32; // Wrap around tile map
                    state = State.READ_TILE_ID;
                }
                // If FIFO is full, stay in PUSH state
            }

            case READ_SPRITE_TILE_ID -> {
                // Read sprite tile ID from OAM
                int oamAddr = sprite.getAddress();
                int tile = memory.readByte(oamAddr + 2) & 0xFF;
                // For 8x16 sprites, tile index is forced even
                if (true) { // TODO: Check sprite height from LCDC
                    tile &= 0xFE;
                }
                tileId = tile;
                state = State.READ_SPRITE_FLAGS;
            }

            case READ_SPRITE_FLAGS -> {
                // Read sprite attributes from OAM
                int oamAddr = sprite.getAddress();
                int attr = memory.readByte(oamAddr + 3) & 0xFF;
                spriteAttributes = new TileAttributes(attr);
                state = State.READ_SPRITE_DATA_1;
            }

            case READ_SPRITE_DATA_1 -> {
                // Read first byte of sprite tile data
                int tileAddr = 0x8000 + tileId * 16 + spriteTileLine * 2;
                tileData1 = memory.readByte(tileAddr) & 0xFF;
                state = State.READ_SPRITE_DATA_2;
            }

            case READ_SPRITE_DATA_2 -> {
                // Read second byte of sprite tile data
                int tileAddr = 0x8000 + tileId * 16 + spriteTileLine * 2 + 1;
                tileData2 = memory.readByte(tileAddr) & 0xFF;
                state = State.PUSH_SPRITE;
            }

            case PUSH_SPRITE -> {
                // Convert sprite tile data to pixels and overlay on FIFO
                zip(tileData1, tileData2, spriteAttributes.isXflip(), pixelLine);
                fifo.setOverlay(pixelLine, spriteOffset, spriteAttributes, spriteOamIndex);
                state = State.READ_TILE_ID;
            }
        }
    }

    public void fetchingDisabled() {
        this.fetchingDisabled = true;
    }

    public boolean isFetchingDisabled() {
        return fetchingDisabled;
    }

    public State getState() {
        return state;
    }

    public void addSprite(SpritePosition sprite, int offset, int oamIndex) {
        this.sprite = sprite;
        this.spriteOffset = offset;
        this.spriteOamIndex = oamIndex;
        // Calculate which line of the sprite to fetch
        this.spriteTileLine = 0; // TODO: Calculate based on LY and sprite Y position
        this.state = State.READ_SPRITE_TILE_ID;
    }

    public boolean spriteInProgress() {
        return state == State.READ_SPRITE_TILE_ID
                || state == State.READ_SPRITE_FLAGS
                || state == State.READ_SPRITE_DATA_1
                || state == State.READ_SPRITE_DATA_2
                || state == State.PUSH_SPRITE;
    }

    public void setSprite(SpritePosition sprite) {
        this.sprite = sprite;
    }

    public int getSpriteOamIndex() {
        return spriteOamIndex;
    }

    /**
     * Convert two bytes of tile data into 8 pixels.
     * 
     * @param data1     low byte
     * @param data2     high byte
     * @param reverse   whether to reverse pixel order (for X-flip)
     * @param pixelLine output array for 8 pixels
     */
    public static void zip(int data1, int data2, boolean reverse, int[] pixelLine) {
        for (int i = 0; i < 8; i++) {
            int bit = reverse ? i : (7 - i);
            int mask = (1 << bit);
            int lo = (data1 & mask) == 0 ? 0 : 1;
            int hi = (data2 & mask) == 0 ? 0 : 1;
            pixelLine[reverse ? (7 - i) : i] = (hi << 1) | lo;
        }
    }
}