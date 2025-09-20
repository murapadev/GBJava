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

    private final byte[] internalBuffer = new byte[1024];
    private final byte[] outputBuffer = new byte[1024];
    private int bufferPosition;
    private int cycleCounter;
    private boolean bufferFull;
    private int frameSequencer;
    private int frameSequencerCycleCounter;
    private boolean enabled;

    public Apu() {
        this.channel1 = new SquareChannel(0xFF11, 0xFF12, true, 0xFF13, 0xFF14);
        this.channel2 = new SquareChannel(0xFF16, 0xFF17, false, 0xFF18, 0xFF19);
        this.channel3 = new WaveChannel();
        this.channel4 = new NoiseChannel();
    }

    public void step() {
        if (enabled) {
            for (int i = 0; i < 4; i++) {
                stepInternal();
            }
        }
    }

    private void stepInternal() {
        cycleCounter++;
        frameSequencerCycleCounter++;

        boolean stepLength = false;
        boolean stepEnvelope = false;
        boolean stepSweep = false;

        // Frame sequencer runs at 512Hz (every 8192 cycles)
        if (frameSequencerCycleCounter >= 8192) {
            frameSequencerCycleCounter = 0;
            frameSequencer++;
            if (frameSequencer > 7) {
                frameSequencer = 0;
            }

            switch (frameSequencer) {
                case 0:
                case 4:
                    stepLength = true;
                    break;
                case 2:
                case 6:
                    stepLength = true;
                    stepSweep = true;
                    break;
                case 7:
                    stepEnvelope = true;
                    break;
                default:
                    break;
            }
        }

        // Step all channels
        final float[] channel1Samples = channel1.step(stepLength, stepEnvelope, stepSweep);
        final float[] channel2Samples = channel2.step(stepLength, stepEnvelope, stepSweep);
        final float[] channel3Samples = channel3.step(stepLength);
        final float[] channel4Samples = channel4.step(stepLength, stepEnvelope);

        // Generate sample every 87 cycles (approximately 44.1kHz)
        if (cycleCounter == 87) {
            cycleCounter = 0;

            // Apply channel panning (NR51)
            if ((channelSelectionRegister & 0b1000_0000) == 0)
                channel4Samples[0] = 0f; // Channel 4 left
            if ((channelSelectionRegister & 0b0100_0000) == 0)
                channel3Samples[0] = 0f; // Channel 3 left
            if ((channelSelectionRegister & 0b0010_0000) == 0)
                channel2Samples[0] = 0f; // Channel 2 left
            if ((channelSelectionRegister & 0b0001_0000) == 0)
                channel1Samples[0] = 0f; // Channel 1 left
            if ((channelSelectionRegister & 0b0000_1000) == 0)
                channel4Samples[1] = 0f; // Channel 4 right
            if ((channelSelectionRegister & 0b0000_0100) == 0)
                channel3Samples[1] = 0f; // Channel 3 right
            if ((channelSelectionRegister & 0b0000_0010) == 0)
                channel2Samples[1] = 0f; // Channel 2 right
            if ((channelSelectionRegister & 0b0000_0001) == 0)
                channel1Samples[1] = 0f; // Channel 1 right

            // Mix channels
            float leftChannelSample = (channel1Samples[0] + channel2Samples[0] + channel3Samples[0]
                    + channel4Samples[0]) / 4f;
            float rightChannelSample = (channel1Samples[1] + channel2Samples[1] + channel3Samples[1]
                    + channel4Samples[1]) / 4f;

            // Apply master volume and panning (NR50)
            final float leftPanFactor = ((channelControlRegister >>> 4) & 0b111) / 7f;
            final float rightPanFactor = (channelControlRegister & 0b111) / 7f;
            leftChannelSample *= leftPanFactor;
            rightChannelSample *= rightPanFactor;

            // Convert to 8-bit samples and store in buffer
            internalBuffer[bufferPosition] = (byte) (int) (128f + leftChannelSample * 127f);
            bufferPosition++;
            internalBuffer[bufferPosition] = (byte) (int) (128f + rightChannelSample * 127f);
            bufferPosition++;

            // Buffer is full, copy to output
            if (bufferPosition == 1024) {
                bufferPosition = 0;
                bufferFull = true;
                System.arraycopy(internalBuffer, 0, outputBuffer, 0, internalBuffer.length);
            }
        }
    }

    public boolean isBufferFull() {
        return bufferFull;
    }

    public byte[] fetchSamples() {
        bufferFull = false;
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
            for (int i = 0xFF30; i < 0xFF40; i++) {
                channel3.writeByte(i, 0);
            }
            enabled = false;
            channel1.setDutyPosition(0);
            channel2.setDutyPosition(0);
            channel3.setDutyPosition(0);
        } else if (!enabled && enable) {
            // Turn on
            enabled = true;
            frameSequencer = 0;
        }
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
        if (address == 0xFF15 || address == 0xFF1F || address == 0xFF20 ||
                (address >= 0xFF27 && address <= 0xFF2F)) {
            return 0xFF;
        }

        throw new RuntimeException("Invalid APU address: " + Integer.toHexString(address));
    }

    public void writeRegister(int address, int value) {
        if (!enabled && address != 0xFF26 && (address < 0xFF30 || address >= 0xFF40)) {
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
            return;
        }
        if (address == 0xFF25) { // NR51
            channelSelectionRegister = value;
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

    public String toString() {
        return "APU";
    }
}