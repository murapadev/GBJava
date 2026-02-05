package gbc.model.graphics;

/**
 * Double-buffered pixel storage for the Game Boy screen.
 * Thread-safe: the emulation thread writes to the back buffer,
 * and the EDT reads from the front buffer via {@link #getPixels()}.
 */
public class FrameBuffer {
    // TODO: Consider atomic swaps for strict thread-safety guarantees.
    public static final int WIDTH = 160;
    public static final int HEIGHT = 144;
    private static final int SIZE = WIDTH * HEIGHT;

    private int[] backBuffer = new int[SIZE];
    private volatile int[] frontBuffer = new int[SIZE];
    private volatile long frameId;

    /**
     * Set pixel at coordinates. Uses unchecked access for performance.
     * Caller must ensure x is in [0,160) and y is in [0,144).
     */
    public void setPixel(int x, int y, int color) {
        backBuffer[y * WIDTH + x] = color;
    }

    /**
     * Set pixel with bounds checking. Use when coordinates may be out of range.
     */
    public void setPixelSafe(int x, int y, int color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            backBuffer[y * WIDTH + x] = color;
        }
    }

    public int getPixel(int x, int y) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            return backBuffer[y * WIDTH + x];
        }
        return 0;
    }

    /**
     * Swaps back and front buffers. Called once per VBlank.
     */
    public void swapBuffers() {
        int[] temp = frontBuffer;
        frontBuffer = backBuffer;
        backBuffer = temp;
        frameId++;
    }

    /**
     * Returns the front buffer pixels for display.
     * Safe to call from the EDT while the emulation thread writes to the back
     * buffer.
     */
    public int[] getPixels() {
        return frontBuffer;
    }

    public long getFrameId() {
        return frameId;
    }

    public void clear() {
        java.util.Arrays.fill(backBuffer, 0);
        java.util.Arrays.fill(frontBuffer, 0);
        frameId++;
    }

    public void reset() {
        clear();
    }
}
