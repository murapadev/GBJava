package gbc.ui.audio;

import gbc.model.GameBoyColor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams APU samples to the host audio device on a dedicated executor.
 * Falls back to a no-op mode when audio output is disabled or unavailable.
 */
public final class AudioEngine implements AutoCloseable {
    private static final int SAMPLE_RATE = 44_100;
    private static final int BUFFER_SIZE = 4_096;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final boolean disabled = Boolean.getBoolean("gbc.audio.nullOutput");

    private ExecutorService executor;
    private Future<?> worker;
    private SourceDataLine line;

    public synchronized void start(GameBoyColor gbc) {
        if (disabled) {
            return; // Audio explicitly disabled (e.g., headless test/CI runs)
        }
        if (running.get()) {
            return;
        }

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(new AudioThreadFactory());
        }

        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 2, false, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, BUFFER_SIZE);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            System.err.println("AudioEngine disabled: " + ex.getMessage());
            shutdownExecutor();
            line = null;
            return;
        }

        running.set(true);
        worker = executor.submit(() -> pumpAudio(gbc));
    }

    public void setPaused(boolean value) {
        paused.set(value);
        if (value && line != null) {
            line.flush();
        }
    }

    @Override
    public synchronized void close() {
        stop();
    }

    public synchronized void stop() {
        running.set(false);

        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        if (line != null) {
            try {
                line.stop();
                line.flush();
            } finally {
                line.close();
                line = null;
            }
        }

        shutdownExecutor();
    }

    private void pumpAudio(GameBoyColor gbc) {
        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                if (paused.get()) {
                    TimeUnit.MILLISECONDS.sleep(5);
                    continue;
                }

                if (line == null) {
                    TimeUnit.MILLISECONDS.sleep(50);
                    continue;
                }

                if (gbc.isAudioBufferFull()) {
                    byte[] samples = gbc.fetchAudioSamples();
                    if (samples != null && samples.length > 0) {
                        line.write(samples, 0, samples.length);
                    }
                } else {
                    TimeUnit.MILLISECONDS.sleep(1);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("AudioEngine error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static final class AudioThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Emulator-Audio");
            thread.setDaemon(true);
            return thread;
        }
    }
}
