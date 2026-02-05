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
    private boolean useScx;
    private boolean useScy;
    private int scxLatch;
    private boolean scxLatched;

    private int tileData1;
    private int tileData2;

    private State savedBgState;
    private int savedTileId;
    private TileAttributes savedTileAttributes;
    private int savedTileData1;
    private int savedTileData2;
    private boolean hasSavedBgState;

    // Sprite-related fields
    private int spriteTileLine;
    private int spriteHeight;
    private SpritePosition sprite;
    private TileAttributes spriteAttributes;
    private int spriteOffset;
    private int spriteOamIndex;

    private final int[] pixelLine = new int[8];
    private int tickCounter;

    public Fetcher(PixelFifo fifo, Memory memory) {
        this.fifo = fifo;
        this.memory = memory;
        this.state = State.READ_TILE_ID;
    }

    public void init() {
        this.state = State.READ_TILE_ID;
        this.fetchingDisabled = false;
        this.hasSavedBgState = false;
        this.tickCounter = 0;
        this.scxLatched = false;
    }

    public void reset() {
        init();
    }

    public void start(int currentLine) {
        int lcdc = memory.getLcdc();
        int bgMap = (lcdc & 0x08) == 0 ? 0x9800 : 0x9C00;
        int tileData = (lcdc & 0x10) == 0 ? 0x9000 : 0x8000;
        boolean signed = (lcdc & 0x10) == 0;
        int scy = memory.getScy();
        int y = (currentLine + scy) & 0xFF;

        startFetching(bgMap, tileData, 0, signed, y, true, true);
    }

    public void startFetching(int mapAddress, int tileDataAddress, int xOffset,
            boolean tileIdSigned, int tileLine) {
        startFetching(mapAddress, tileDataAddress, xOffset, tileIdSigned, tileLine, true, true);
    }

    public void startFetching(int mapAddress, int tileDataAddress, int xOffset,
            boolean tileIdSigned, int tileLine, boolean useScx, boolean useScy) {
        this.mapAddress = mapAddress;
        this.tileDataAddress = tileDataAddress;
        this.xOffset = xOffset;
        this.tileIdSigned = tileIdSigned;
        this.tileLine = tileLine;
        this.useScx = useScx;
        this.useScy = useScy;
        this.state = State.READ_TILE_ID;
        this.fetchingDisabled = false;
        if (useScx && !scxLatched) {
            scxLatch = memory.getScx() & 0xFF;
            scxLatched = true;
        }
    }

    public void setScxLatch(int scx) {
        this.scxLatch = scx & 0xFF;
        this.scxLatched = true;
    }

    public void tick() {
        if (fetchingDisabled) {
            return;
        }

        // Hardware fetcher advances 1 step every 2 T-cycles
        // TODO: Confirm fetcher phase timing when sprites are injected mid-tile.
        tickCounter++;
        if (tickCounter < 2) {
            return;
        }
        tickCounter = 0;

        switch (state) {
            case READ_TILE_ID -> {
                int effectiveTileLine = useScy ? ((memory.getLy() + memory.getScy()) & 0xFF) : tileLine;
                int baseX = useScx ? ((scxLatch & 0xF8) >> 3) : 0;
                int mapIndex = ((effectiveTileLine / 8) * 32) + ((baseX + xOffset) & 0x1F);
                int mapAddr = mapAddress + mapIndex;
                if (memory.isCgbMode()) {
                    tileId = memory.getVramByteBanked(mapAddr, 0) & 0xFF;
                    int attr = memory.getVramByteBanked(mapAddr, 1) & 0xFF;
                    tileAttributes = new TileAttributes(attr);
                } else {
                    tileId = memory.getVramByte(mapAddr) & 0xFF;
                    tileAttributes = new TileAttributes(0); // Default for DMG
                }
                state = State.READ_TILE_DATA_1;
            }

            case READ_TILE_DATA_1 -> {
                int effectiveTileLine = useScy ? ((memory.getLy() + memory.getScy()) & 0xFF) : tileLine;
                int tileAddr = tileDataAddress + (tileIdSigned ? (byte) tileId * 16 : tileId * 16);
                int line = effectiveTileLine % 8;
                if (tileAttributes != null && tileAttributes.isYflip()) {
                    line = 7 - line;
                }
                int bank = tileAttributes != null ? tileAttributes.getVramBank() : 0;
                tileData1 = memory.getVramByteBanked(tileAddr + line * 2, bank) & 0xFF;
                state = State.READ_TILE_DATA_2;
            }

            case READ_TILE_DATA_2 -> {
                int effectiveTileLine = useScy ? ((memory.getLy() + memory.getScy()) & 0xFF) : tileLine;
                int tileAddr = tileDataAddress + (tileIdSigned ? (byte) tileId * 16 : tileId * 16);
                int line = effectiveTileLine % 8;
                if (tileAttributes != null && tileAttributes.isYflip()) {
                    line = 7 - line;
                }
                int bank = tileAttributes != null ? tileAttributes.getVramBank() : 0;
                tileData2 = memory.getVramByteBanked(tileAddr + line * 2 + 1, bank) & 0xFF;
                state = State.PUSH;
            }

            case PUSH -> {
                if (fifo.getLength() <= 8) {
                    // Convert tile data to pixel line
                    boolean xflip = tileAttributes != null && tileAttributes.isXflip();
                    zip(tileData1, tileData2, xflip, pixelLine);
                    fifo.enqueue8Pixels(pixelLine, tileAttributes);
                    xOffset = (xOffset + 1) % 32; // Wrap around tile map
                    state = State.READ_TILE_ID;
                }
                // If FIFO is full, stay in PUSH state
            }

            case READ_SPRITE_TILE_ID -> {
                // Sprite tile ID latched during OAM search
                int tile = sprite.getTileId() & 0xFF;
                // For 8x16 sprites, tile index is forced even
                int lcdc = memory.getLcdc();
                boolean is8x16Sprites = (lcdc & 0x04) != 0; // Bit 2: sprite height
                if (is8x16Sprites) {
                    tile &= 0xFE;
                }
                tileId = tile;
                state = State.READ_SPRITE_FLAGS;
            }

            case READ_SPRITE_FLAGS -> {
                // Sprite attributes latched during OAM search
                int attr = sprite.getAttributes();
                if (!memory.isCgbMode()) {
                    attr &= ~0x08; // DMG: bit 3 is unused, not VRAM bank
                }
                spriteAttributes = new TileAttributes(attr);
                if (spriteAttributes.isYflip()) {
                    spriteTileLine = (spriteHeight - 1) - spriteTileLine;
                }
                state = State.READ_SPRITE_DATA_1;
            }

            case READ_SPRITE_DATA_1 -> {
                // Read first byte of sprite tile data
                int tileLine = spriteTileLine;
                int effectiveTileId = tileId;
                if (spriteHeight == 16 && tileLine >= 8) {
                    effectiveTileId = (tileId + 1) & 0xFF;
                    tileLine -= 8;
                }
                int tileAddr = 0x8000 + effectiveTileId * 16 + tileLine * 2;
                int bank = spriteAttributes != null ? spriteAttributes.getVramBank() : 0;
                tileData1 = memory.getVramByteBanked(tileAddr, bank) & 0xFF;
                state = State.READ_SPRITE_DATA_2;
            }

            case READ_SPRITE_DATA_2 -> {
                // Read second byte of sprite tile data
                int tileLine = spriteTileLine;
                int effectiveTileId = tileId;
                if (spriteHeight == 16 && tileLine >= 8) {
                    effectiveTileId = (tileId + 1) & 0xFF;
                    tileLine -= 8;
                }
                int tileAddr = 0x8000 + effectiveTileId * 16 + tileLine * 2 + 1;
                int bank = spriteAttributes != null ? spriteAttributes.getVramBank() : 0;
                tileData2 = memory.getVramByteBanked(tileAddr, bank) & 0xFF;
                state = State.PUSH_SPRITE;
            }

            case PUSH_SPRITE -> {
                // Convert sprite tile data to pixels and overlay on FIFO
                // Keep sprite pixel data in tile order; FIFO handles X-flip when applying
                // offset.
                zip(tileData1, tileData2, false, pixelLine);
                fifo.setOverlay(pixelLine, spriteOffset, spriteAttributes, spriteOamIndex, sprite.getX());
                // TODO: Account for sprite fetch timing penalties before resuming BG fetch.
                if (hasSavedBgState) {
                    state = savedBgState;
                    tileId = savedTileId;
                    tileAttributes = savedTileAttributes;
                    tileData1 = savedTileData1;
                    tileData2 = savedTileData2;
                    hasSavedBgState = false;
                } else {
                    state = State.READ_TILE_ID;
                }
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

        // TODO: Sprite fetch should respect hardware timing constraints (one sprite per
        // 8 dots; OAM order).
        if (!hasSavedBgState && isBgState(state)) {
            savedBgState = state;
            savedTileId = tileId;
            savedTileAttributes = tileAttributes;
            savedTileData1 = tileData1;
            savedTileData2 = tileData2;
            hasSavedBgState = true;
        }

        // Calculate which line of the sprite to fetch based on LY and sprite Y position
        int ly = memory.getLy(); // Current scanline (LY register)
        int spriteY = sprite.getY(); // Sprite Y position from OAM (already adjusted)

        // Read LCDC to determine sprite height
        int lcdc = memory.getLcdc();
        spriteHeight = (lcdc & 0x04) != 0 ? 16 : 8; // Bit 2: sprite height

        // Calculate which line of the sprite tile to fetch
        int relativeY = ly - spriteY;
        this.spriteTileLine = relativeY % spriteHeight;

        this.state = State.READ_SPRITE_TILE_ID;
    }

    public boolean spriteInProgress() {
        return state == State.READ_SPRITE_TILE_ID
                || state == State.READ_SPRITE_FLAGS
                || state == State.READ_SPRITE_DATA_1
                || state == State.READ_SPRITE_DATA_2
                || state == State.PUSH_SPRITE;
    }

    private boolean isBgState(State s) {
        return s == State.READ_TILE_ID
                || s == State.READ_TILE_DATA_1
                || s == State.READ_TILE_DATA_2
                || s == State.PUSH;
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
        // Optimized bit extraction using shifts instead of masks
        if (reverse) {
            pixelLine[0] = ((data2 & 0x01) << 1) | (data1 & 0x01);
            pixelLine[1] = ((data2 & 0x02) >> 0) | ((data1 & 0x02) >> 1);
            pixelLine[2] = ((data2 & 0x04) >> 1) | ((data1 & 0x04) >> 2);
            pixelLine[3] = ((data2 & 0x08) >> 2) | ((data1 & 0x08) >> 3);
            pixelLine[4] = ((data2 & 0x10) >> 3) | ((data1 & 0x10) >> 4);
            pixelLine[5] = ((data2 & 0x20) >> 4) | ((data1 & 0x20) >> 5);
            pixelLine[6] = ((data2 & 0x40) >> 5) | ((data1 & 0x40) >> 6);
            pixelLine[7] = ((data2 & 0x80) >> 6) | ((data1 & 0x80) >> 7);
        } else {
            pixelLine[0] = ((data2 & 0x80) >> 6) | ((data1 & 0x80) >> 7);
            pixelLine[1] = ((data2 & 0x40) >> 5) | ((data1 & 0x40) >> 6);
            pixelLine[2] = ((data2 & 0x20) >> 4) | ((data1 & 0x20) >> 5);
            pixelLine[3] = ((data2 & 0x10) >> 3) | ((data1 & 0x10) >> 4);
            pixelLine[4] = ((data2 & 0x08) >> 2) | ((data1 & 0x08) >> 3);
            pixelLine[5] = ((data2 & 0x04) >> 1) | ((data1 & 0x04) >> 2);
            pixelLine[6] = ((data2 & 0x02) >> 0) | ((data1 & 0x02) >> 1);
            pixelLine[7] = ((data2 & 0x01) << 1) | (data1 & 0x01);
        }
    }
}
