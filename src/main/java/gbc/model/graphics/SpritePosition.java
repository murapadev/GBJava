package gbc.model.graphics;

/**
 * Represents a sprite position and attributes from OAM.
 */
public class SpritePosition {
    private int x;
    private int y;
    private int address;
    private int tileId;
    private int attributes;
    private boolean enabled;

    public SpritePosition() {
        this.enabled = false;
    }

    public void enable(int x, int y, int address, int tileId, int attributes) {
        this.x = x;
        this.y = y;
        this.address = address;
        this.tileId = tileId;
        this.attributes = attributes;
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getAddress() {
        return address;
    }

    public int getTileId() {
        return tileId;
    }

    public int getAttributes() {
        return attributes;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
