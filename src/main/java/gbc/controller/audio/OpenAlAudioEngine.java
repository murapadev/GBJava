package gbc.controller.audio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;

import gbc.controller.config.AppConfig;
import gbc.controller.config.EmulatorConfig;
import gbc.model.GameBoyColor;

/**
 * Streams APU samples to the host audio device via LWJGL OpenAL.
 * Uses a streaming buffer queue for low-latency audio output.
 * <p>
 * The APU produces large buffers (e.g. 2048 stereo frames) but this engine
 * uses smaller OpenAL buffers (512 frames) for lower latency. A residual
 * byte array bridges the size mismatch.
 */
public final class OpenAlAudioEngine implements AudioBackend {
    private static final Logger LOGGER = Logger.getLogger(OpenAlAudioEngine.class.getName());
    private static final int NUM_BUFFERS = 6;
    /** OpenAL buffer size in stereo frames – kept small for low latency. */
    private static final int AL_BUFFER_FRAMES = 512;
    /**
     * Noise gate threshold (signed 8-bit). Samples within ±GATE of silence
     * are zeroed to suppress DC-filter quantisation artifacts.
     */
    private static final int NOISE_GATE = 1;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private long alDevice;
    private long alContext;
    private int alSource;
    private int[] alBuffers;

    private ExecutorService executor;
    private Future<?> worker;

    private int sampleRate;
    private int bufferFrames;

    // Residual buffer: holds unconsumed APU data between pump iterations
    private byte[] residual;
    private int residualPos;
    private int residualLen;

    private volatile long lastWriteNs;
    private volatile int lastBufferedBytes;
    private volatile int bufferCapacityBytes;

    @Override
    public synchronized void start(GameBoyColor gbc) {
        if (isDisabled())
            return;
        if (running.get())
            return;

        readSettings();

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Emulator-Audio-OpenAL");
                t.setDaemon(true);
                return t;
            });
        }

        running.set(true);
        worker = executor.submit(() -> runAudioThread(gbc));
    }

    @Override
    public void setPaused(boolean value) {
        paused.set(value);
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (worker != null) {
            worker.cancel(true);
            try {
                worker.get(500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            worker = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized void restart(GameBoyColor gbc) {
        stop();
        start(gbc);
    }

    @Override
    public synchronized void close() {
        stop();
    }

    @Override
    public long getLastWriteNs() {
        return lastWriteNs;
    }

    @Override
    public int getBufferedBytes() {
        return lastBufferedBytes;
    }

    @Override
    public int getBufferCapacityBytes() {
        return bufferCapacityBytes;
    }

    private void readSettings() {
        EmulatorConfig cfg = AppConfig.get().getConfig();
        this.sampleRate = Math.max(8000, cfg.getSampleRate());
        // bufferFrames is used only for the residual buffer sizing;
        // OpenAL buffers always use AL_BUFFER_FRAMES for low latency.
        int configuredBuffer = cfg.getAudioBufferSize();
        if (configuredBuffer > 0) {
            this.bufferFrames = Math.max(AL_BUFFER_FRAMES, configuredBuffer / 2);
        } else {
            this.bufferFrames = 2048;
        }
        this.bufferCapacityBytes = NUM_BUFFERS * AL_BUFFER_FRAMES * 4;
    }

    /**
     * Runs entirely on the audio thread so all OpenAL calls happen in a single
     * thread context. Initialisation, the pump loop, and cleanup are all here.
     */
    private void runAudioThread(GameBoyColor gbc) {
        ShortBuffer pcmBuffer = null;
        try {
            initOpenAL();
            pcmBuffer = MemoryUtil.memAllocShort(AL_BUFFER_FRAMES * 2);
            // Size residual to hold one full APU buffer
            residual = new byte[bufferFrames * 2];
            residualPos = 0;
            residualLen = 0;
            pumpAudio(gbc, pcmBuffer);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "OpenAL audio error", e);
        } finally {
            if (pcmBuffer != null) {
                MemoryUtil.memFree(pcmBuffer);
            }
            cleanupOpenAL();
        }
    }

    private void initOpenAL() {
        alDevice = ALC10.alcOpenDevice((ByteBuffer) null);
        if (alDevice == 0) {
            throw new IllegalStateException("Failed to open OpenAL device");
        }

        alContext = ALC10.alcCreateContext(alDevice, (IntBuffer) null);
        if (alContext == 0) {
            ALC10.alcCloseDevice(alDevice);
            alDevice = 0;
            throw new IllegalStateException("Failed to create OpenAL context");
        }
        ALC10.alcMakeContextCurrent(alContext);
        ALCCapabilities alcCaps = ALC.createCapabilities(alDevice);
        AL.createCapabilities(alcCaps);

        alSource = AL10.alGenSources();
        AL10.alSourcef(alSource, AL10.AL_GAIN, 1.0f);

        alBuffers = new int[NUM_BUFFERS];
        AL10.alGenBuffers(alBuffers);

        // Fill initial buffers with silence and queue them
        ShortBuffer silence = MemoryUtil.memAllocShort(AL_BUFFER_FRAMES * 2);
        for (int i = 0; i < AL_BUFFER_FRAMES * 2; i++) {
            silence.put((short) 0);
        }
        silence.flip();

        for (int buf : alBuffers) {
            AL10.alBufferData(buf, AL10.AL_FORMAT_STEREO16, silence, sampleRate);
            AL10.alSourceQueueBuffers(alSource, buf);
        }
        MemoryUtil.memFree(silence);

        AL10.alSourcePlay(alSource);
        LOGGER.info("OpenAL audio engine initialized: " + sampleRate + " Hz, "
                + AL_BUFFER_FRAMES + " frames/AL buffer, " + NUM_BUFFERS + " buffers");
    }

    private void cleanupOpenAL() {
        if (alSource != 0) {
            AL10.alSourceStop(alSource);
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                int[] removed = new int[queued];
                AL10.alSourceUnqueueBuffers(alSource, removed);
            }
            AL10.alDeleteSources(alSource);
            alSource = 0;
        }
        if (alBuffers != null) {
            AL10.alDeleteBuffers(alBuffers);
            alBuffers = null;
        }
        if (alContext != 0) {
            ALC10.alcMakeContextCurrent(0);
            ALC10.alcDestroyContext(alContext);
            alContext = 0;
        }
        if (alDevice != 0) {
            ALC10.alcCloseDevice(alDevice);
            alDevice = 0;
        }
    }

    private void pumpAudio(GameBoyColor gbc, ShortBuffer pcmBuffer) throws InterruptedException {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            if (paused.get()) {
                int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
                if (state == AL10.AL_PLAYING) {
                    AL10.alSourcePause(alSource);
                }
                // Discard residual so stale audio doesn't play on resume
                residualLen = 0;
                residualPos = 0;
                TimeUnit.MILLISECONDS.sleep(5);
                continue;
            }

            // Resume if we were paused
            int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PAUSED) {
                AL10.alSourcePlay(alSource);
            }

            int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
            if (processed <= 0) {
                updateBufferedBytes();
                LockSupport.parkNanos(100_000L);
                continue;
            }

            for (int i = 0; i < processed; i++) {
                int buf = AL10.alSourceUnqueueBuffers(alSource);

                // Fill pcmBuffer with AL_BUFFER_FRAMES frames from the residual
                pcmBuffer.clear();
                int framesWritten = 0;

                while (framesWritten < AL_BUFFER_FRAMES) {
                    // Refill residual from APU when exhausted
                    if (residualPos >= residualLen) {
                        byte[] samples = gbc.isAudioBufferFull()
                                ? gbc.fetchAudioSamples()
                                : null;
                        if (samples != null && samples.length > 0) {
                            if (samples.length > residual.length) {
                                residual = new byte[samples.length];
                            }
                            System.arraycopy(samples, 0, residual, 0, samples.length);
                            residualPos = 0;
                            residualLen = samples.length;
                        } else {
                            break; // no more APU data available
                        }
                    }

                    // Consume from residual into pcmBuffer
                    int residualFramesAvail = (residualLen - residualPos) / 2;
                    int framesToCopy = Math.min(residualFramesAvail,
                            AL_BUFFER_FRAMES - framesWritten);
                    for (int f = 0; f < framesToCopy; f++) {
                        int left = (residual[residualPos] & 0xFF) - 128;
                        int right = (residual[residualPos + 1] & 0xFF) - 128;
                        residualPos += 2;
                        // Noise gate: squelch DC-filter quantisation artifacts
                        short l = (Math.abs(left) <= NOISE_GATE) ? 0 : (short) (left << 8);
                        short r = (Math.abs(right) <= NOISE_GATE) ? 0 : (short) (right << 8);
                        pcmBuffer.put(l);
                        pcmBuffer.put(r);
                    }
                    framesWritten += framesToCopy;
                }

                // Pad remainder with silence if APU didn't have enough data
                for (int f = framesWritten; f < AL_BUFFER_FRAMES; f++) {
                    pcmBuffer.put((short) 0);
                    pcmBuffer.put((short) 0);
                }

                pcmBuffer.flip();
                AL10.alBufferData(buf, AL10.AL_FORMAT_STEREO16, pcmBuffer, sampleRate);
                AL10.alSourceQueueBuffers(alSource, buf);

                if (framesWritten > 0) {
                    lastWriteNs = System.nanoTime();
                }
            }

            updateBufferedBytes();

            // Recover from underrun
            state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && !paused.get()) {
                AL10.alSourcePlay(alSource);
            }
        }
    }

    private void updateBufferedBytes() {
        int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
        lastBufferedBytes = queued * AL_BUFFER_FRAMES * 4;
    }

    private boolean isDisabled() {
        EmulatorConfig cfg = AppConfig.get().getConfig();
        return !cfg.isAudioEnabled() || cfg.isNullOutput();
    }
}
