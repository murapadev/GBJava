package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class FrameBufferTest {

    @Test
    void clearResetsBothBuffersWithoutReallocationSideEffects() {
        FrameBuffer frameBuffer = new FrameBuffer();
        frameBuffer.setPixel(0, 0, 0xFF123456);
        frameBuffer.swapBuffers();

        assertEquals(0xFF123456, frameBuffer.getPixels()[0], "Front buffer should contain drawn pixel");

        frameBuffer.clear();

        assertEquals(0, frameBuffer.getPixels()[0], "Front buffer should be cleared");
        frameBuffer.setPixel(0, 0, 0xFFABCDEF);
        assertNotEquals(0xFFABCDEF, frameBuffer.getPixels()[0], "Writing back buffer must not affect front buffer");
    }
}
