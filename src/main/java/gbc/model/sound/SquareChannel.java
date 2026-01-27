package gbc.model.sound;

/**
 * Square wave channel for Game Boy APU (channels 1 and 2)
 * Generates square waves with duty cycle, frequency, and volume envelope
 * control
 */
public class SquareChannel {
    private final int volumeRegisterAddress;
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

    public SquareChannel(int lengthRegisterAddress, int volumeRegisterAddress, boolean useSweep,
            int frequencyLowDataRegisterAddress, int frequencyHighDataRegisterAddress) {
        this.volumeRegisterAddress = volumeRegisterAddress;
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

    private void stepLength() {
        if (isLengthTimerEnabled()) {
            lengthTimer--;
            if (lengthTimer <= 0) {
                disableChannel();
            }
        }
    }

    private int getFrequency() {
        return freqLowRegister | ((freqHighRegister & 0b111) << 8);
    }

    private void triggerEvent() {
        enabled = true;
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

    private boolean isDacOn() {
        // DAC is on if bits 3-7 of volume register are not all zero
        return (volumeEnvelope.readByte(volumeRegisterAddress) & 0b1111_1000) > 0;
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
            return freqLowRegister;
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
            if (lengthTimer == 0) {
                lengthTimer = newLength;
            }
            lengthDutyRegister = value & 0b1100_0000; // Duty bits only
            return;
        }
        if (address == freqLowRegisterAddress) {
            freqLowRegister = value;
            return;
        }
        if (address == freqHighRegisterAddress) {
            freqHighRegister = value;
            boolean trigger = (value & 0b1000_0000) != 0;
            if (trigger && isDacOn()) {
                triggerEvent();
            }
            if (trigger) {
                lengthTimer = lengthTimer == 0 ? 64 : lengthTimer;
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