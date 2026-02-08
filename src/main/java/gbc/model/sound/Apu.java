package gbc.model.sound;

/**
 * Audio Processing Unit (APU) for Game Boy Color
 * Handles all audio generation and mixing for the four sound channels
 */
public class Apu {
    // Wave duty patterns for square channels
    static final float[][] WAVE_DUTY = {
            { 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f }, // 12.5%
            { 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f }, // 25%
            { 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f }, // 50%
            { 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f } // 75%
    };

    private final SquareChannel channel1;
    private final SquareChannel channel2;
    private final WaveChannel channel3;
    private final NoiseChannel channel4;

    private int channelControlRegister; // NR50
    private int channelSelectionRegister; // NR51

    // Precalculated panning masks (updated when NR51 changes)
    private boolean ch1Left, ch1Right, ch2Left, ch2Right;
    private boolean ch3Left, ch3Right, ch4Left, ch4Right;
    // Precalculated volume factors (updated when NR50 changes)
    private float leftVolumeFactor, rightVolumeFactor;

    private final byte[][] buffers;
    private final byte[] stagingBuffer;
    private final byte[] outputBuffer;
    private final int bufferSize;
    private int bufferPosition;
    private int writeBufferIndex;
    private int readBufferIndex;
    private volatile int queuedBuffers;
    private int cycleCounter;
    private int frameSequencer;
    private int frameSequencerCycleCounter;
    private boolean enabled;
    private final int cyclesPerSample;
    private boolean cgbMode;
    private float leftHpfPrevInput;
    private float rightHpfPrevInput;
    private float leftHpfPrevOutput;
    private float rightHpfPrevOutput;
    private final boolean useDcFilter;
    private final float hpfCoeff;
    private final float dmgMixGain;
    private final float cgbMixGain;

    // Sample cache - avoid recalculating when audio state hasn't changed
    private float cachedLeftSample;
    private float cachedRightSample;
    private boolean cachedIsSilent;
    private boolean sampleCacheDirty = true;
    private static final boolean AUDIO_DEBUG = Boolean.getBoolean("gbc.audio.debug");
    private boolean loggedEnable;

    private void logDebug(String message) {
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
        } catch (Exception ignored) {
            // Ignore logging failures
        }
    }

    public Apu() {
        this.channel1 = new SquareChannel(0xFF11, 0xFF12, true, 0xFF13, 0xFF14);
        this.channel2 = new SquareChannel(0xFF16, 0xFF17, false, 0xFF18, 0xFF19);
        this.channel3 = new WaveChannel();
        this.channel4 = new NoiseChannel();
        int sampleRate = Integer.getInteger("audio.sampleRate", 44_100);
        this.cyclesPerSample = Math.max(1, (int) Math.round(4_194_304.0 / sampleRate));
        int configuredSize = Integer.getInteger("audio.bufferSize", 4096);
        int safeSize = Math.max(512, configuredSize);
        if ((safeSize & 1) != 0) {
            safeSize++;
        }
        this.bufferSize = safeSize;
        int bufferCount = Math.max(2, Integer.getInteger("audio.bufferCount", 4));
        this.buffers = new byte[bufferCount][bufferSize];
        this.stagingBuffer = new byte[bufferSize];
        this.outputBuffer = new byte[bufferSize];
        this.useDcFilter = Boolean.parseBoolean(System.getProperty("audio.dcFilter", "true"));
        this.hpfCoeff = clamp(Float.parseFloat(System.getProperty("audio.dcFilterCoeff", "0.995")), 0.90f, 0.9999f);
        this.dmgMixGain = clamp(Float.parseFloat(System.getProperty("audio.dmgMixGain", "1.0")), 0.1f, 2.0f);
        this.cgbMixGain = clamp(Float.parseFloat(System.getProperty("audio.cgbMixGain", "1.0")), 0.1f, 2.0f);
        updatePanningCache();
        updateVolumeCache();
        setCgbMode(false);
    }

    public void setCgbMode(boolean cgbMode) {
        this.cgbMode = cgbMode;
        channel3.setCgbMode(cgbMode);
    }

    private void updatePanningCache() {
        ch4Left = (channelSelectionRegister & 0b1000_0000) != 0;
        ch3Left = (channelSelectionRegister & 0b0100_0000) != 0;
        ch2Left = (channelSelectionRegister & 0b0010_0000) != 0;
        ch1Left = (channelSelectionRegister & 0b0001_0000) != 0;
        ch4Right = (channelSelectionRegister & 0b0000_1000) != 0;
        ch3Right = (channelSelectionRegister & 0b0000_0100) != 0;
        ch2Right = (channelSelectionRegister & 0b0000_0010) != 0;
        ch1Right = (channelSelectionRegister & 0b0000_0001) != 0;
        invalidateSampleCache();
    }

    private void updateVolumeCache() {
        leftVolumeFactor = ((channelControlRegister >>> 4) & 0b111) / 7f;
        rightVolumeFactor = (channelControlRegister & 0b111) / 7f;
        invalidateSampleCache();
    }

    public void step(int cycles) {
        if (!enabled || cycles <= 0)
            return;

        // Batch process cycles - only generate samples when needed
        int remainingCycles = cycles;
        while (remainingCycles > 0) {
            // Calculate how many cycles until next sample or frame sequencer tick
            int cyclesToNextSample = cyclesPerSample - cycleCounter;
            int cyclesToNextFrame = 8192 - frameSequencerCycleCounter;
            int cyclesToProcess = Math.min(remainingCycles, Math.min(cyclesToNextSample, cyclesToNextFrame));

            // Advance counters
            cycleCounter += cyclesToProcess;
            frameSequencerCycleCounter += cyclesToProcess;
            remainingCycles -= cyclesToProcess;

            // Step channels by cyclesToProcess (batch update frequency timers)
            channel1.stepCycles(cyclesToProcess);
            channel2.stepCycles(cyclesToProcess);
            channel3.stepCycles(cyclesToProcess);
            channel4.stepCycles(cyclesToProcess);
            sampleCacheDirty = true; // Channels stepped, samples may have changed

            // Handle frame sequencer tick
            if (frameSequencerCycleCounter >= 8192) {
                frameSequencerCycleCounter = 0;
                frameSequencer = (frameSequencer + 1) & 7;

                // Propagate frame sequencer step to channels for length-counter quirks
                channel1.setFrameSequencerStep(frameSequencer);
                channel2.setFrameSequencerStep(frameSequencer);
                channel3.setFrameSequencerStep(frameSequencer);
                channel4.setFrameSequencerStep(frameSequencer);

                boolean stepLength = (frameSequencer & 1) == 0; // 0,2,4,6
                boolean stepSweep = frameSequencer == 2 || frameSequencer == 6;
                boolean stepEnvelope = frameSequencer == 7;

                if (stepLength) {
                    channel1.stepLength();
                    channel2.stepLength();
                    channel3.stepLength();
                    channel4.stepLength();
                }
                if (stepSweep)
                    channel1.stepSweep();
                if (stepEnvelope) {
                    channel1.stepEnvelope();
                    channel2.stepEnvelope();
                    channel4.stepEnvelope();
                }
            }

            // Generate sample when counter reaches threshold
            if (cycleCounter >= cyclesPerSample) {
                cycleCounter = 0;
                generateSample();
            }
        }
    }

    private void generateSample() {
        // Only recalculate samples when cache is dirty
        if (sampleCacheDirty) {
            float ch1 = channel1.getCurrentSample();
            float ch2 = channel2.getCurrentSample();
            float ch3 = channel3.getCurrentSample();
            float ch4 = channel4.getCurrentSample();

            // Detect if all channels are producing zero output
            boolean allChannelsSilent = (ch1 == 0f && ch2 == 0f && ch3 == 0f && ch4 == 0f);

            float left = (ch1Left ? ch1 : 0f) + (ch2Left ? ch2 : 0f) +
                    (ch3Left ? ch3 : 0f) + (ch4Left ? ch4 : 0f);
            float right = (ch1Right ? ch1 : 0f) + (ch2Right ? ch2 : 0f) +
                    (ch3Right ? ch3 : 0f) + (ch4Right ? ch4 : 0f);

            cachedLeftSample = left * 0.25f * leftVolumeFactor;
            cachedRightSample = right * 0.25f * rightVolumeFactor;
            cachedIsSilent = allChannelsSilent || (cachedLeftSample == 0f && cachedRightSample == 0f);
            sampleCacheDirty = false;
        }

        // Output true silence when all channels are silent to avoid DC filter noise
        if (cachedIsSilent) {
            stagingBuffer[bufferPosition++] = (byte) 128;  // Silence for unsigned 8-bit audio
            stagingBuffer[bufferPosition++] = (byte) 128;
            // Reset DC filter state to prevent artifacts when audio resumes
            resetDcFilter();
        } else {
            float leftSample = cachedLeftSample * (cgbMode ? cgbMixGain : dmgMixGain);
            float rightSample = cachedRightSample * (cgbMode ? cgbMixGain : dmgMixGain);
            if (useDcFilter) {
                leftSample = applyHighPassFilter(leftSample, true);
                rightSample = applyHighPassFilter(rightSample, false);
            }
            leftSample = clamp(leftSample, -1f, 1f);
            rightSample = clamp(rightSample, -1f, 1f);

            stagingBuffer[bufferPosition++] = (byte) (128 + Math.round(leftSample * 127f));
            stagingBuffer[bufferPosition++] = (byte) (128 + Math.round(rightSample * 127f));
        }

        if (bufferPosition >= bufferSize) {
            enqueueFilledBuffer();
        }
    }

    private void enqueueFilledBuffer() {
        synchronized (this) {
            if (queuedBuffers >= buffers.length) {
                readBufferIndex = (readBufferIndex + 1) % buffers.length;
                queuedBuffers--;
            }
            System.arraycopy(stagingBuffer, 0, buffers[writeBufferIndex], 0, bufferSize);
            queuedBuffers++;
            writeBufferIndex = (writeBufferIndex + 1) % buffers.length;
        }
        bufferPosition = 0;
    }

    /** Mark sample cache as dirty - called when audio state changes */
    private void invalidateSampleCache() {
        sampleCacheDirty = true;
    }

    public boolean isBufferFull() {
        return queuedBuffers > 0;
    }

    public synchronized byte[] fetchSamples() {
        if (queuedBuffers <= 0) {
            return null;
        }
        System.arraycopy(buffers[readBufferIndex], 0, outputBuffer, 0, bufferSize);
        readBufferIndex = (readBufferIndex + 1) % buffers.length;
        queuedBuffers--;
        return outputBuffer;
    }

    private void enableAPU(boolean enable) {
        if (enabled && !enable) {
            // Turn off - reset all registers
            channel1.writeByte(0xFF10, 0);
            channel1.writeByte(0xFF11, 0);
            channel1.writeByte(0xFF12, 0);
            channel1.writeByte(0xFF13, 0);
            channel1.writeByte(0xFF14, 0);
            channel2.writeByte(0xFF16, 0);
            channel2.writeByte(0xFF17, 0);
            channel2.writeByte(0xFF18, 0);
            channel2.writeByte(0xFF19, 0);
            channel3.writeByte(0xFF1A, 0);
            channel3.writeByte(0xFF1B, 0);
            channel3.writeByte(0xFF1C, 0);
            channel3.writeByte(0xFF1D, 0);
            channel3.writeByte(0xFF1E, 0);
            channel4.writeByte(0xFF20, 0);
            channel4.writeByte(0xFF21, 0);
            channel4.writeByte(0xFF22, 0);
            channel4.writeByte(0xFF23, 0);
            channelControlRegister = 0;
            channelSelectionRegister = 0;
            updatePanningCache();
            updateVolumeCache();
            for (int i = 0xFF30; i < 0xFF40; i++) {
                channel3.writeByte(i, 0);
            }
            enabled = false;
            frameSequencer = 0;
            frameSequencerCycleCounter = 0;
            cycleCounter = 0;
            bufferPosition = 0;
            queuedBuffers = 0;
            writeBufferIndex = 0;
            readBufferIndex = 0;
            channel1.setDutyPosition(0);
            channel2.setDutyPosition(0);
            channel3.setDutyPosition(0);
            resetDcFilter();
            invalidateSampleCache();
        } else if (!enabled && enable) {
            // Turn on
            enabled = true;
            frameSequencer = 0;
            frameSequencerCycleCounter = 0;
            cycleCounter = 0;
            resetDcFilter();
            invalidateSampleCache();
            if (AUDIO_DEBUG && !loggedEnable) {
                loggedEnable = true;
                logDebug("[APU] Enabled");
            }
        }
    }

    private void resetDcFilter() {
        leftHpfPrevInput = 0f;
        rightHpfPrevInput = 0f;
        leftHpfPrevOutput = 0f;
        rightHpfPrevOutput = 0f;
    }

    private float applyHighPassFilter(float input, boolean left) {
        if (!useDcFilter) {
            return input;
        }
        if (left) {
            float output = input - leftHpfPrevInput + hpfCoeff * leftHpfPrevOutput;
            leftHpfPrevInput = input;
            leftHpfPrevOutput = output;
            return output;
        }
        float output = input - rightHpfPrevInput + hpfCoeff * rightHpfPrevOutput;
        rightHpfPrevInput = input;
        rightHpfPrevOutput = output;
        return output;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public int readRegister(int address) {
        if (channel1.acceptsAddress(address)) {
            return channel1.readByte(address);
        }
        if (channel2.acceptsAddress(address)) {
            return channel2.readByte(address);
        }
        if (channel3.acceptsAddress(address)) {
            return channel3.readByte(address);
        }
        if (channel4.acceptsAddress(address)) {
            return channel4.readByte(address);
        }
        if (address == 0xFF24) { // NR50
            return channelControlRegister;
        }
        if (address == 0xFF25) { // NR51
            return channelSelectionRegister;
        }
        if (address == 0xFF26) { // NR52
            int result = (enabled ? 1 : 0) << 7;
            result |= 0b0111_0000; // Bits 6-4 unused
            result |= (channel4.isEnabled() ? 1 : 0) << 3;
            result |= (channel3.isEnabled() ? 1 : 0) << 2;
            result |= (channel2.isEnabled() ? 1 : 0) << 1;
            result |= channel1.isEnabled() ? 1 : 0;
            return result;
        }
        // Unused registers return 0xFF
        // Note: 0xFF20 (NR41) is handled by channel4, not here
        if (address == 0xFF15 || address == 0xFF1F ||
                (address >= 0xFF27 && address <= 0xFF2F)) {
            return 0xFF;
        }

        throw new RuntimeException("Invalid APU address: " + Integer.toHexString(address));
    }

    public void writeRegister(int address, int value) {
        if (!enabled
                && address != 0xFF26
                && !isLengthRegister(address)
                && (address < 0xFF30 || address >= 0xFF40)) {
            return; // Ignore writes when APU is disabled
        }

        if (channel1.acceptsAddress(address)) {
            channel1.writeByte(address, value);
            return;
        }
        if (channel2.acceptsAddress(address)) {
            channel2.writeByte(address, value);
            return;
        }
        if (channel3.acceptsAddress(address)) {
            channel3.writeByte(address, value);
            return;
        }
        if (channel4.acceptsAddress(address)) {
            channel4.writeByte(address, value);
            return;
        }
        if (address == 0xFF24) { // NR50
            channelControlRegister = value;
            updateVolumeCache();
            return;
        }
        if (address == 0xFF25) { // NR51
            channelSelectionRegister = value;
            updatePanningCache();
            return;
        }
        if (address == 0xFF26) { // NR52
            enableAPU((value & 0b1000_0000) > 0);
            return;
        }
        // Unused registers - ignore writes
        if (address == 0xFF15 || address == 0xFF1F || address == 0xFF20 ||
                (address >= 0xFF27 && address <= 0xFF2F)) {
            return;
        }

        throw new RuntimeException("Invalid APU address: " + Integer.toHexString(address));
    }

    private boolean isLengthRegister(int address) {
        return address == 0xFF11
                || address == 0xFF16
                || address == 0xFF1B
                || address == 0xFF20;
    }

    public String toString() {
        return "APU";
    }
}
