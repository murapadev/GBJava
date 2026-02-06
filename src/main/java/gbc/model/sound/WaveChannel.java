package gbc.model.sound;

/**
 * Wave channel (channel 3) for Game Boy APU
 * Plays 4-bit samples from wave RAM with volume control
 */
public class WaveChannel {
    private final int[] wavePattern = new int[16]; // 32 4-bit samples (16 bytes)
    private final float[] samples = new float[2];
    private int frequencyTimer;
    private int frequency;
    private int dutyPosition;
    private boolean dacOn;
    private boolean lengthEnabled;
    private int lengthCounter;
    private boolean enabled;
    private int outputLevel;
    private int volumeShift;
    private boolean cgbMode;
    // Cached current sample
    private float currentSample;
    // Frame sequencer step (0-7), updated by APU
    private int frameSequencerStep;

    public void setCgbMode(boolean cgbMode) {
        this.cgbMode = cgbMode;
    }

    private int resolveWaveRamIndex(int address) {
        int index = address - 0xFF30;
        if (!cgbMode && enabled && dacOn) {
            return (dutyPosition / 2) & 0x0F;
        }
        return index & 0x0F;
    }

    public float[] step(boolean stepLength) {
        // Step timing systems
        if (stepLength) {
            stepLength();
        }

        if (frequencyTimer == 0) {
            frequencyTimer = (2048 - frequency) * 2;
            dutyPosition = (dutyPosition + 1) & 31;
        }
        frequencyTimer--;

        float sample = 0f;
        if (dacOn && enabled) {
            int sampleIndex = dutyPosition / 2;
            int nibble = wavePattern[sampleIndex];
            if ((dutyPosition & 1) != 0) {
                nibble &= 0x0F; // Low nibble
            } else {
                nibble >>>= 4; // High nibble
            }
            sample = (nibble >>> volumeShift) / 15f;
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

        int period = (2048 - frequency) * 2;
        if (period <= 0)
            period = 1;

        frequencyTimer -= cycles;
        while (frequencyTimer <= 0) {
            frequencyTimer += period;
            dutyPosition = (dutyPosition + 1) & 31;
        }

        // Update cached sample
        int sampleIndex = dutyPosition / 2;
        int nibble = wavePattern[sampleIndex];
        if ((dutyPosition & 1) != 0) {
            nibble &= 0x0F;
        } else {
            nibble >>>= 4;
        }
        currentSample = (nibble >>> volumeShift) / 15f;
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
     * Used for length counter extra-clocking quirk on NR34 writes.
     */
    public void setFrameSequencerStep(int step) {
        this.frameSequencerStep = step;
    }

    public void setDutyPosition(int value) {
        dutyPosition = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean acceptsAddress(int address) {
        return address == 0xFF1A || address == 0xFF1B || address == 0xFF1C ||
                address == 0xFF1D || address == 0xFF1E ||
                (address >= 0xFF30 && address < 0xFF40);
    }

    public int readByte(int address) {
        if (address == 0xFF1A) {
            return (dacOn ? 1 : 0) << 7 | 0x7F;
        }
        if (address == 0xFF1B) {
            return 0xFF;
        }
        if (address == 0xFF1C) {
            return outputLevel << 5 | 0x9F;
        }
        if (address == 0xFF1D) {
            return 0xFF;
        }
        if (address == 0xFF1E) {
            return (lengthEnabled ? 1 : 0) << 6 | 0b1011_1111;
        }
        if (address >= 0xFF30 && address < 0xFF40) {
            return wavePattern[resolveWaveRamIndex(address)];
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }

    public void writeByte(int address, int value) {
        if (address == 0xFF1A) {
            dacOn = (value >>> 7 & 0b1) != 0;
            if (!dacOn) {
                enabled = false;
            }
            return;
        }
        if (address == 0xFF1B) {
            lengthCounter = 256 - value;
            return;
        }
        if (address == 0xFF1C) {
            outputLevel = value >>> 5 & 0b11;
            switch (outputLevel) {
                case 0b00:
                    volumeShift = 4; // Mute
                    break;
                case 0b01:
                    volumeShift = 0; // 100%
                    break;
                case 0b10:
                    volumeShift = 1; // 50%
                    break;
                case 0b11:
                    volumeShift = 2; // 25%
                    break;
            }
            return;
        }
        if (address == 0xFF1D) {
            frequency = (frequency & 0x700) | value;
            return;
        }
        if (address == 0xFF1E) {
            frequency = (frequency & 0xFF) | ((value & 0x7) << 8);
            boolean wasLengthEnabled = lengthEnabled;
            lengthEnabled = (value >>> 6 & 0b1) != 0;
            boolean trigger = (value >>> 7) != 0;

            // Extra length clocking quirk
            boolean onNonLengthStep = (frameSequencerStep & 1) != 0;
            if (!wasLengthEnabled && lengthEnabled && onNonLengthStep && lengthCounter > 0) {
                lengthCounter--;
                if (lengthCounter == 0 && !trigger) {
                    enabled = false;
                }
            }

            if (trigger) {
                if (lengthCounter == 0) {
                    lengthCounter = 256;
                    if (lengthEnabled && onNonLengthStep) {
                        lengthCounter--;
                    }
                }
                if (dacOn) {
                    enabled = true;
                    dutyPosition = 0;
                    frequencyTimer = (2048 - frequency) * 2;
                }
            }
            return;
        }
        if (address >= 0xFF30 && address < 0xFF40) {
            wavePattern[resolveWaveRamIndex(address)] = value & 0xFF;
            return;
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }
}
