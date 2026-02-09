package gbc.controller.audio;

import java.util.logging.Level;
import java.util.logging.Logger;

import gbc.model.GameBoyColor;

/**
 * Audio engine facade that delegates to a configurable backend
 * (OpenAL via LWJGL or javax.sound.sampled).
 */
public final class AudioEngine implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(AudioEngine.class.getName());
    private AudioBackend backend;

    @Override
    public synchronized void close() {
        stop();
    }

    public synchronized void start(GameBoyColor gbc) {
        if (backend != null)
            return;
        try {
            backend = new OpenAlAudioEngine();
            LOGGER.info("Using OpenAL audio backend");
            backend.start(gbc);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "OpenAL unavailable, audio disabled", e);
            backend = null;
        }
    }

    public void setPaused(boolean value) {
        AudioBackend b = backend;
        if (b != null)
            b.setPaused(value);
    }

    public synchronized void stop() {
        if (backend != null) {
            backend.stop();
            backend = null;
        }
    }

    public synchronized void restart(GameBoyColor gbc) {
        stop();
        start(gbc);
    }

    public long getLastWriteNs() {
        AudioBackend b = backend;
        return b != null ? b.getLastWriteNs() : 0;
    }

    public int getBufferedBytes() {
        AudioBackend b = backend;
        return b != null ? b.getBufferedBytes() : 0;
    }

    public int getBufferCapacityBytes() {
        AudioBackend b = backend;
        return b != null ? b.getBufferCapacityBytes() : 0;
    }
}
