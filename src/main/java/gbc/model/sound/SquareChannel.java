package gbc.model.sound;

/**
 * Square wave channel for Game Boy APU (channels 1 and 2).
 * Generates square waves with duty cycle, frequency, and volume envelope
 * control.
 *
 * <p>
 * Implements hardware-accurate timing quirks:
 * <ul>
 * <li>Extra length clocking when enabling length counter on an odd frame
 * sequencer step</li>
 * <li>Trigger edge cases: length reload from 0, frequency timer reset, envelope
 * restart</li>
 * <li>Sweep negate-mode disable (handled in {@link Sweep})</li>
 * <li>DAC disable immediately silences channel</li>
 * </ul>
 */
public class SquareChannel {
    private final int lengthDutyRegisterAddress;
    private final int freqLowRegisterAddress;
    private final int freqHighRegisterAddress;

    private final VolumeEnvelope volumeEnvelope;
    private Sweep sweep;

    private int frequencyTimer;
    private int dutyPosition;
    private int lengthDutyRegister;
    private int freqLowRegister;
    private int freqHighRegister;
    private final float[] samples = new float[2];
    private boolean enabled;
    private int lengthTimer;
    // Cached current sample for fast access
    private float currentSample;
    // Frame sequencer step (0-7), updated by APU each tick
    private int frameSequencerStep;

    public SquareChannel(int lengthRegisterAddress, int volumeRegisterAddress, boolean useSweep,
            int frequencyLowDataRegisterAddress, int frequencyHighDataRegisterAddress) {
        this.lengthDutyRegisterAddress = lengthRegisterAddress;
        this.freqLowRegisterAddress = frequencyLowDataRegisterAddress;
        this.freqHighRegisterAddress = frequencyHighDataRegisterAddress;
        this.lengthDutyRegister = 0; // Will be set via writeByte
        this.volumeEnvelope = new VolumeEnvelope(volumeRegisterAddress);
        if (useSweep) {
            this.sweep = new Sweep(this::disableChannel);
        }
    }

    public float[] step(boolean stepLength, boolean stepEnvelope, boolean stepSweep) {
        if (enabled && isDacOn()) {
            // Step timing systems
            if (stepLength) {
                stepLength();
                if (!enabled) {
                    samples[0] = 0f;
                    samples[1] = 0f;
                    return samples;
                }
            }
            if (stepEnvelope) {
                volumeEnvelope.step();
            }
            if (stepSweep && sweep != null) {
                sweep.step();
            }

            // Generate wave
            float[] duty = Apu.WAVE_DUTY[lengthDutyRegister >>> 6];
            int frequency = getFrequency();
            if (sweep != null) {
                frequency = sweep.getFrequency(frequency);
            }

            frequencyTimer--;
            if (frequencyTimer <= 0) {
                frequencyTimer = (2048 - frequency) * 4;
                dutyPosition++;
                if (dutyPosition > 7) {
                    dutyPosition = 0;
                }
            }

            samples[0] = duty[dutyPosition];
            samples[1] = duty[dutyPosition];
            volumeEnvelope.modifySamples(samples);
        } else {
            samples[0] = 0f;
            samples[1] = 0f;
        }

        return samples;
    }

    /** Batch step frequency timer by multiple cycles */
    public void stepCycles(int cycles) {
        if (!enabled || !isDacOn()) {
            currentSample = 0f;
            return;
        }

        int frequency = getFrequency();
        if (sweep != null) {
            frequency = sweep.getFrequency(frequency);
        }
        int period = (2048 - frequency) * 4;
        if (period <= 0)
            period = 1;

        frequencyTimer -= cycles;
        while (frequencyTimer <= 0) {
            frequencyTimer += period;
            dutyPosition = (dutyPosition + 1) & 7;
        }

        // Update cached sample
        float[] duty = Apu.WAVE_DUTY[lengthDutyRegister >>> 6];
        currentSample = duty[dutyPosition] * volumeEnvelope.getVolume();
    }

    /** Get current sample value (precalculated) */
    public float getCurrentSample() {
        return currentSample;
    }

    /** Step length counter (called by frame sequencer) */
    public void stepLength() {
        if (isLengthTimerEnabled()) {
            lengthTimer--;
            if (lengthTimer <= 0) {
                disableChannel();
            }
        }
    }

    /** Step sweep (called by frame sequencer) */
    public void stepSweep() {
        if (sweep != null) {
            sweep.step();
        }
    }

    /** Step envelope (called by frame sequencer) */
    public void stepEnvelope() {
        volumeEnvelope.step();
    }

    private int getFrequency() {
        return freqLowRegister | ((freqHighRegister & 0b111) << 8);
    }

    private void triggerEvent() {
        enabled = true;
        dutyPosition = 0;
        frequencyTimer = (2048 - getFrequency()) * 4;
        volumeEnvelope.triggerEvent();
        if (sweep != null) {
            sweep.triggerEvent(getFrequency());
        }
    }

    private void disableChannel() {
        enabled = false;
    }

    public void setDutyPosition(int value) {
        dutyPosition = value;
    }

    /**
     * Update the frame sequencer step, called by APU each frame sequencer tick.
     * Used for length counter extra-clocking quirk on NRx4 writes.
     */
    public void setFrameSequencerStep(int step) {
        this.frameSequencerStep = step;
    }

    private boolean isDacOn() {
        return volumeEnvelope.isDacEnabled();
    }

    private boolean isLengthTimerEnabled() {
        return (freqHighRegister & 0b0100_0000) != 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean acceptsAddress(int address) {
        return address == lengthDutyRegisterAddress || address == freqLowRegisterAddress ||
                address == freqHighRegisterAddress ||
                volumeEnvelope.acceptsAddress(address) ||
                (sweep != null && sweep.acceptsAddress(address));
    }

    public int readByte(int address) {
        if (address == lengthDutyRegisterAddress) {
            return lengthDutyRegister | 0b0011_1111;
        }
        if (address == freqLowRegisterAddress) {
            // NR13/NR23 are write-only.
            return 0xFF;
        }
        if (address == freqHighRegisterAddress) {
            return freqHighRegister | 0b1011_1111;
        }
        if (volumeEnvelope.acceptsAddress(address)) {
            return volumeEnvelope.readByte(address);
        }
        if (sweep != null && sweep.acceptsAddress(address)) {
            return sweep.readByte(address);
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }

    public void writeByte(int address, int value) {
        if (address == lengthDutyRegisterAddress) {
            // Update length timer when writing to duty/length register
            int newLength = 64 - (value & 0b0011_1111);
            lengthTimer = newLength;
            lengthDutyRegister = value & 0b1100_0000; // Duty bits only
            return;
        }
        if (address == freqLowRegisterAddress) {
            freqLowRegister = value;
            return;
        }
        if (address == freqHighRegisterAddress) {
            boolean wasLengthEnabled = isLengthTimerEnabled();
            freqHighRegister = value;
            boolean nowLengthEnabled = isLengthTimerEnabled();
            boolean trigger = (value & 0b1000_0000) != 0;

            // Extra length clocking quirk: when length enable transitions 0→1
            // on an odd frame sequencer step (1,3,5,7 — steps that don't clock length),
            // the length counter gets an extra decrement.
            boolean onNonLengthStep = (frameSequencerStep & 1) != 0;
            if (!wasLengthEnabled && nowLengthEnabled && onNonLengthStep && lengthTimer > 0) {
                lengthTimer--;
                if (lengthTimer == 0 && !trigger) {
                    disableChannel();
                }
            }

            if (trigger) {
                // Reload length from 0 to max on trigger
                if (lengthTimer == 0) {
                    lengthTimer = 64;
                    // If length enabled and on a non-length step, extra clock
                    if (nowLengthEnabled && onNonLengthStep) {
                        lengthTimer--;
                    }
                }
                if (isDacOn()) {
                    triggerEvent();
                }
            }
            return;
        }
        if (volumeEnvelope.acceptsAddress(address)) {
            volumeEnvelope.writeByte(address, value);
            if (!isDacOn()) {
                enabled = false;
            }
            return;
        }
        if (sweep != null && sweep.acceptsAddress(address)) {
            sweep.writeByte(address, value);
            return;
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }
}
