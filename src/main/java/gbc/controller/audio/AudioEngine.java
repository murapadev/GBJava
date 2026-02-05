package gbc.controller.audio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import gbc.model.GameBoyColor;

/**
 * Streams APU samples to the host audio device on a dedicated executor.
 * Falls back to a no-op mode when audio output is disabled or unavailable.
 */
public final class AudioEngine implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(AudioEngine.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private static final boolean AUDIO_DEBUG = Boolean.getBoolean("gbc.audio.debug");
    private static final boolean AUDIO_TEST_TONE = Boolean.getBoolean("gbc.audio.testTone");
    private static final String AUDIO_MIXER = System.getProperty("gbc.audio.mixer");

    private ExecutorService executor;
    private Future<?> worker;
    private SourceDataLine line;
    private AudioFormat outputFormat;
    private boolean outputSigned;
    private boolean outputBigEndian;
    private int outputSampleSizeBits;
    private int outputChannels;
    private byte[] scratch16;
    private byte[] silenceBuffer;
    private long buffersWritten;
    private long lastBufferLogNs;
    private double testPhase;
    private static final double TEST_FREQ = 440.0;
    private final int sampleRate;
    private final int bufferSize;
    private volatile int lastAvailableBytes;
    private volatile long lastWriteNs;

    public AudioEngine() {
        this.sampleRate = Integer.getInteger("audio.sampleRate", 44_100);
        Integer configuredBuffer = Integer.getInteger("audio.bufferSize", 4096);
        Integer latencyMs = Integer.getInteger("audio.latencyMs", 0);
        if (configuredBuffer != null && configuredBuffer > 0) {
            this.bufferSize = configuredBuffer;
        } else if (latencyMs != null && latencyMs > 0) {
            int frames = Math.max(256, (int) ((sampleRate * latencyMs) / 1000.0));
            this.bufferSize = frames * 4; // 16-bit stereo bytes
        } else {
            this.bufferSize = 4_096;
        }
    }

    private void logDebug(String message, Throwable error) {
        if (!AUDIO_DEBUG) {
            return;
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("logs", "audio.log");
            String lineMsg = String.format("%s %s%n",
                    java.time.LocalDateTime.now().toString(), message);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, lineMsg,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            if (error != null) {
                String errMsg = String.format("%s %s%n", error.getClass().getName(),
                        error.getMessage());
                java.nio.file.Files.writeString(path, errMsg,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
            // Ignore logging failures
        }
    }

    public synchronized void start(GameBoyColor gbc) {
        if (isDisabled()) {
            return; // Audio explicitly disabled (e.g., headless test/CI runs)
        }
        if (running.get()) {
            return;
        }

        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(new AudioThreadFactory());
        }

        logAvailableMixers();
        try {
            AudioFormat selectedFormat = selectOutputFormat();
            line = openLineForFormat(selectedFormat);
            line.open(selectedFormat, bufferSize);
            line.start();
            configureOutputFormat(selectedFormat);
            logDebug("AudioEngine output format: " + selectedFormat, null);
        } catch (LineUnavailableException | IllegalArgumentException ex) {
            logDebug("AudioEngine disabled", ex);
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
                    if (AUDIO_TEST_TONE) {
                        writeTestTone();
                        continue;
                    }
                    if (samples != null && samples.length > 0) {
                        int written = writeConvertedSamples(samples);
                        if (written > 0) {
                            lastWriteNs = System.nanoTime();
                        }
                        lastAvailableBytes = line.available();
                        buffersWritten++;
                        long now = System.nanoTime();
                        if (AUDIO_DEBUG && now - lastBufferLogNs > TimeUnit.SECONDS.toNanos(2)) {
                            lastBufferLogNs = now;
                            logDebug("AudioEngine buffers written: " + buffersWritten, null);
                        }
                    }
                } else {
                    int available = line.available();
                    if (available >= bufferSize) {
                        writeSilence(bufferSize);
                        lastWriteNs = System.nanoTime();
                    } else {
                        TimeUnit.MILLISECONDS.sleep(1);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "AudioEngine error", e);
        }
    }

    public long getLastWriteNs() {
        return lastWriteNs;
    }

    public int getLastAvailableBytes() {
        return lastAvailableBytes;
    }

    private void writeTestTone() {
        int frames = 512;
        int needed = frames * outputChannels * Math.max(1, outputSampleSizeBits / 8);
        byte[] buf = new byte[needed];
        double phaseInc = (2.0 * Math.PI * TEST_FREQ) / sampleRate;
        for (int i = 0; i < frames; i++) {
            float value = (float) Math.sin(testPhase) * 0.6f;
            writeSampleFrame(buf, i, value, value);
            testPhase += phaseInc;
            if (testPhase > 2.0 * Math.PI) {
                testPhase -= 2.0 * Math.PI;
            }
        }
        line.write(buf, 0, needed);
    }

    private void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private boolean isDisabled() {
        boolean enabled = Boolean.parseBoolean(System.getProperty("audio.enabled", "true"));
        boolean nullOut = Boolean.getBoolean("gbc.audio.nullOutput");
        return !enabled || nullOut;
    }

    private AudioFormat selectOutputFormat() throws LineUnavailableException {
        javax.sound.sampled.Mixer mixer = resolveMixer();
        AudioFormat[] candidates = buildCandidateFormats();

        if (mixer != null) {
            AudioFormat fromMixer = selectFormatFromMixer(mixer);
            if (fromMixer != null) {
                return fromMixer;
            }
            for (AudioFormat format : candidates) {
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        SourceDataLine.class, format);
                if (mixer.isLineSupported(info)) {
                    return format;
                }
            }
        } else {
            for (AudioFormat format : candidates) {
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        SourceDataLine.class, format);
                if (AudioSystem.isLineSupported(info)) {
                    return format;
                }
            }
        }
        throw new LineUnavailableException("No compatible audio output format found");
    }

    private SourceDataLine openLineForFormat(AudioFormat format) throws LineUnavailableException {
        javax.sound.sampled.Mixer mixer = resolveMixer();
        if (mixer != null) {
            javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    SourceDataLine.class, format);
            return (SourceDataLine) mixer.getLine(info);
        }
        return AudioSystem.getSourceDataLine(format);
    }

    private javax.sound.sampled.Mixer resolveMixer() {
        if (AUDIO_MIXER == null || AUDIO_MIXER.isBlank()) {
            return null;
        }
        if ("system".equalsIgnoreCase(AUDIO_MIXER)) {
            return null;
        }
        for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equalsIgnoreCase(AUDIO_MIXER)
                    || info.getName().toLowerCase().contains(AUDIO_MIXER.toLowerCase())
                    || info.getDescription().toLowerCase().contains(AUDIO_MIXER.toLowerCase())) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    private AudioFormat[] buildCandidateFormats() {
        return new AudioFormat[] {
                new AudioFormat(sampleRate, 16, 2, true, false),
                new AudioFormat(sampleRate, 16, 2, true, true),
                new AudioFormat(sampleRate, 16, 1, true, false),
                new AudioFormat(sampleRate, 16, 1, true, true),
                new AudioFormat(sampleRate, 8, 2, false, false),
                new AudioFormat(sampleRate, 8, 2, true, false),
                new AudioFormat(sampleRate, 8, 1, false, false),
                new AudioFormat(sampleRate, 8, 1, true, false),
                new AudioFormat(44100, 16, 2, true, false),
                new AudioFormat(44100, 16, 2, true, true),
                new AudioFormat(44100, 8, 2, false, false),
                new AudioFormat(22050, 8, 1, false, false)
        };
    }

    private AudioFormat selectFormatFromMixer(javax.sound.sampled.Mixer mixer) {
        for (javax.sound.sampled.Line.Info info : mixer.getSourceLineInfo()) {
            if (info instanceof javax.sound.sampled.DataLine.Info dataInfo) {
                if (!SourceDataLine.class.isAssignableFrom(dataInfo.getLineClass())) {
                    continue;
                }
                AudioFormat[] formats = dataInfo.getFormats();
                if (formats == null) {
                    continue;
                }
                AudioFormat best = null;
                int bestScore = -1;
                for (AudioFormat format : formats) {
                    int score = scoreFormat(format);
                    if (score > bestScore) {
                        bestScore = score;
                        best = format;
                    }
                }
                if (best != null) {
                    return best;
                }
            }
        }
        return null;
    }

    private int scoreFormat(AudioFormat format) {
        int score = 0;
        String encoding = format.getEncoding().toString().toUpperCase();
        boolean signed = encoding.contains("SIGNED");
        int bits = format.getSampleSizeInBits();
        int channels = format.getChannels();
        float rate = format.getSampleRate();
        if (signed) {
            score += 4;
        }
        if (bits == 16) {
            score += 4;
        } else if (bits == 8) {
            score += 2;
        }
        if (channels == 2) {
            score += 3;
        } else if (channels == 1) {
            score += 2;
        }
        if (Math.abs(rate - sampleRate) < 1) {
            score += 3;
        }
        return score;
    }

    private void configureOutputFormat(AudioFormat format) {
        this.outputFormat = format;
        this.outputSigned = format.getEncoding().toString().toUpperCase().contains("SIGNED");
        this.outputBigEndian = format.isBigEndian();
        this.outputSampleSizeBits = format.getSampleSizeInBits();
        this.outputChannels = Math.max(1, format.getChannels());
    }

    private int writeConvertedSamples(byte[] samples) {
        if (line == null || outputFormat == null) {
            return 0;
        }
        int inputFrames = samples.length / 2;
        int bytesPerSample = Math.max(1, outputSampleSizeBits / 8);
        int frameSize = bytesPerSample * outputChannels;
        int needed = inputFrames * frameSize;
        if (scratch16 == null || scratch16.length < needed) {
            scratch16 = new byte[needed];
        }
        for (int i = 0; i < inputFrames; i++) {
            int left = (samples[i * 2] & 0xFF) - 128;
            int right = (samples[i * 2 + 1] & 0xFF) - 128;
            float lf = left / 128f;
            float rf = right / 128f;
            writeSampleFrame(scratch16, i, lf, rf);
        }
        line.write(scratch16, 0, needed);
        return needed;
    }

    private void writeSilence(int bytes) {
        if (line == null || outputFormat == null || bytes <= 0) {
            return;
        }
        int bytesPerSample = Math.max(1, outputSampleSizeBits / 8);
        int frameSize = bytesPerSample * outputChannels;
        int frames = bytes / frameSize;
        int needed = frames * frameSize;
        if (silenceBuffer == null || silenceBuffer.length < needed) {
            silenceBuffer = new byte[needed];
        }
        for (int i = 0; i < frames; i++) {
            writeSampleFrame(silenceBuffer, i, 0f, 0f);
        }
        line.write(silenceBuffer, 0, needed);
    }

    private void writeSampleFrame(byte[] out, int frameIndex, float left, float right) {
        float mono = (left + right) * 0.5f;
        int base = frameIndex * outputChannels * Math.max(1, outputSampleSizeBits / 8);
        for (int ch = 0; ch < outputChannels; ch++) {
            float sample = outputChannels == 1 ? mono : (ch == 0 ? left : right);
            if (outputSampleSizeBits == 16) {
                int value = Math.round(sample * 32767f);
                value = Math.max(-32768, Math.min(32767, value));
                if (!outputSigned) {
                    value += 32768;
                }
                byte lo = (byte) (value & 0xFF);
                byte hi = (byte) ((value >>> 8) & 0xFF);
                int idx = base + ch * 2;
                if (outputBigEndian) {
                    out[idx] = hi;
                    out[idx + 1] = lo;
                } else {
                    out[idx] = lo;
                    out[idx + 1] = hi;
                }
            } else {
                int value = Math.round(sample * 127f);
                value = Math.max(-128, Math.min(127, value));
                if (!outputSigned) {
                    value += 128;
                }
                int idx = base + ch;
                out[idx] = (byte) value;
            }
        }
    }

    private void logAvailableMixers() {
        if (!AUDIO_DEBUG) {
            return;
        }
        StringBuilder sb = new StringBuilder("Available mixers:\n");
        for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
            sb.append("- ").append(info.getName()).append(" | ").append(info.getDescription()).append("\n");
        }
        logDebug(sb.toString(), null);
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
