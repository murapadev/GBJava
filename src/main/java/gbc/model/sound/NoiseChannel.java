package gbc.model.sound;

/**
 * Noise channel (channel 4) for Game Boy APU.
 * Generates pseudo-random noise using a 15-bit or 7-bit LFSR.
 *
 * <p>
 * LFSR behavior: On each clock, bits 0 and 1 are XORed. The result is
 * shifted into bit 14 (and also bit 6 in 7-bit width mode, set via NR43 bit 3).
 * The output is the complement of bit 0.
 *
 * <p>
 * Envelope: period 0 is treated as 8 internally (matches hardware).
 * Volume envelope ticks on frame sequencer step 7.
 */
public class NoiseChannel {
    private final float[] samples = new float[2];
    private int polynomialRegister;
    private int lengthCounter;
    private boolean incrementing;
    private int initialVolume;
    private int period;
    private boolean dacOn;
    private boolean enabled;
    private boolean lengthEnabled;
    private int lfsr = 0x7FFF;
    private int periodTimer;
    private int volume;
    private int frequencyTimer;
    // Cached current sample
    private float currentSample;
    // Frame sequencer step (0-7), updated by APU
    private int frameSequencerStep;

    private int getEffectiveEnvelopePeriod() {
        return period == 0 ? 8 : period;
    }

    public float[] step(boolean stepLength, boolean stepEnvelope) {
        // Step timing systems
        if (stepLength) {
            stepLength();
        }
        if (stepEnvelope) {
            stepEnvelope();
        }

        if (frequencyTimer == 0) {
            frequencyTimer = calculateFrequencyTimer();

            // Update LFSR
            int xorResult = (lfsr & 0b01) ^ ((lfsr & 0b10) >> 1);
            lfsr = (lfsr >> 1) | (xorResult << 14);

            // Apply width mode (7-bit or 15-bit)
            if ((polynomialRegister >>> 3 & 0b01) != 0) {
                lfsr &= ~(1 << 6);
                lfsr |= xorResult << 6;
            }
        }
        frequencyTimer--;

        float sample = 0f;
        if (dacOn && enabled) {
            float input = (~lfsr & 0b1) * volume;
            sample = input / 15f;
        }

        samples[0] = sample;
        samples[1] = sample;

        return samples;
    }

    /** Batch step frequency timer by multiple cycles */
    public void stepCycles(int cycles) {
        if (!dacOn || !enabled) {
            currentSample = 0f;
            return;
        }

        int freqPeriod = calculateFrequencyTimer();
        if (freqPeriod <= 0)
            freqPeriod = 1;

        frequencyTimer -= cycles;
        while (frequencyTimer <= 0) {
            frequencyTimer += freqPeriod;

            // Update LFSR
            int xorResult = (lfsr & 0b01) ^ ((lfsr & 0b10) >> 1);
            lfsr = (lfsr >> 1) | (xorResult << 14);
            if ((polynomialRegister >>> 3 & 0b01) != 0) {
                lfsr &= ~(1 << 6);
                lfsr |= xorResult << 6;
            }
        }

        // Update cached sample
        currentSample = ((~lfsr & 0b1) * volume) / 15f;
    }

    /** Get current sample value */
    public float getCurrentSample() {
        return currentSample;
    }

    /** Step length counter (called by frame sequencer) */
    public void stepLength() {
        if (lengthEnabled && lengthCounter > 0) {
            lengthCounter--;
            if (lengthCounter == 0) {
                enabled = false;
            }
        }
    }

    /**
     * Update the frame sequencer step, called by APU each frame sequencer tick.
     * Used for length counter extra-clocking quirk on NR44 writes.
     */
    public void setFrameSequencerStep(int step) {
        this.frameSequencerStep = step;
    }

    /** Step envelope (called by frame sequencer) */
    public void stepEnvelope() {
        if (periodTimer > 0) {
            periodTimer--;
        }
        if (periodTimer == 0) {
            periodTimer = getEffectiveEnvelopePeriod();
            if (volume < 0xF && incrementing || volume > 0 && !incrementing) {
                if (incrementing) {
                    volume++;
                } else {
                    volume--;
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean acceptsAddress(int address) {
        return address == 0xFF1F || address == 0xFF20 || address == 0xFF21 ||
                address == 0xFF22 || address == 0xFF23;
    }

    public int readByte(int address) {
        if (address == 0xFF1F) {
            return 0xFF;
        }
        if (address == 0xFF20) {
            // NR41 is write-only; all bits return 1 on read
            return 0xFF;
        }
        if (address == 0xFF21) {
            return initialVolume << 4 | (incrementing ? 0x08 : 0) | period;
        }
        if (address == 0xFF22) {
            return polynomialRegister;
        }
        if (address == 0xFF23) {
            return (lengthEnabled ? 1 : 0) << 6 | 0b1011_1111;
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }

    public void writeByte(int address, int value) {
        if (address == 0xFF1F) {
            return; // Unused
        }
        if (address == 0xFF20) {
            lengthCounter = 64 - (value & 0b11_1111);
            return;
        }
        if (address == 0xFF21) {
            // Update latch values used by trigger and readback.
            // Do NOT reset periodTimer here — that prevents the running
            // envelope from completing its fade-out (same issue as NRx2
            // in VolumeEnvelope).
            incrementing = (value & 0x08) != 0;
            initialVolume = value >>> 4;
            period = value & 0x07;
            dacOn = (value & 0b1111_1000) != 0;

            if (!dacOn) {
                enabled = false;
            }
            return;
        }
        if (address == 0xFF22) {
            polynomialRegister = value;
            return;
        }
        if (address == 0xFF23) {
            boolean wasLengthEnabled = lengthEnabled;
            lengthEnabled = (value >>> 6 & 0b1) != 0;
            boolean trigger = (value >>> 7) != 0;

            // Extra length clocking quirk: when length enable transitions 0→1
            // on an odd frame sequencer step, extra decrement occurs.
            boolean onNonLengthStep = (frameSequencerStep & 1) != 0;
            if (!wasLengthEnabled && lengthEnabled && onNonLengthStep && lengthCounter > 0) {
                lengthCounter--;
                if (lengthCounter == 0 && !trigger) {
                    enabled = false;
                }
            }

            if (trigger) {
                if (lengthCounter == 0) {
                    lengthCounter = 64;
                    if (lengthEnabled && onNonLengthStep) {
                        lengthCounter--;
                    }
                }
                if (dacOn) {
                    enabled = true;
                }
                lfsr = 0x7FFF;
                periodTimer = getEffectiveEnvelopePeriod();
                volume = initialVolume;
                frequencyTimer = calculateFrequencyTimer();
            }
            return;
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }

    private int calculateFrequencyTimer() {
        int divisorCode = polynomialRegister & 0x07;
        return (divisorCode == 0 ? 8 : divisorCode << 4) << (polynomialRegister >>> 4);
    }
}
