package gbc.model.graphics;

/**
 * Represents a sprite position and attributes from OAM.
 */
public class SpritePosition {
    private int x;
    private int y;
    private int address;
    private boolean enabled;

    public SpritePosition() {
        this.enabled = false;
    }

    public void enable(int x, int y, int address) {
        this.x = x;
        this.y = y;
        this.address = address;
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

    public boolean isEnabled() {
        return enabled;
    }
}