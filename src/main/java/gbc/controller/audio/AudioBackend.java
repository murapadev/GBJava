package gbc.controller.audio;

import gbc.model.GameBoyColor;

/**
 * Common interface for audio output backends (OpenAL, javax.sound, etc.).
 */
public interface AudioBackend extends AutoCloseable {
    void start(GameBoyColor gbc);
    void stop();
    void restart(GameBoyColor gbc);
    void setPaused(boolean value);
    @Override void close();
    long getLastWriteNs();
    int getLastAvailableBytes();
}
