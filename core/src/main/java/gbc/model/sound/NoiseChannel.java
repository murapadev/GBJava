package gbc.model.sound;

/**
 * Noise channel (channel 4) for Game Boy APU
 * Generates pseudo-random noise using LFSR
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
    private int lfsr;
    private int periodTimer;
    private int volume;
    private int frequencyTimer;

    public float[] step(boolean stepLength, boolean stepEnvelope) {
        // Step timing systems
        if (stepLength) {
            stepLength();
        }
        if (stepEnvelope) {
            stepEnvelope();
        }

        if (frequencyTimer == 0) {
            // Calculate divisor
            int divisorCode = polynomialRegister & 0x07;
            frequencyTimer = (divisorCode == 0 ? 8 : divisorCode << 4) << (polynomialRegister >>> 4);

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

    private void stepLength() {
        if (lengthEnabled && lengthCounter > 0) {
            lengthCounter--;
            if (lengthCounter == 0) {
                enabled = false;
            }
        }
    }

    private void stepEnvelope() {
        if (period != 0) {
            if (periodTimer > 0) {
                periodTimer--;
            }
            if (periodTimer == 0) {
                periodTimer = period;
                if (volume < 0xF && incrementing || volume > 0 && !incrementing) {
                    if (incrementing) {
                        volume++;
                    } else {
                        volume--;
                    }
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
            lengthEnabled = (value >>> 6 & 0b1) != 0;
            boolean trigger = (value >>> 7) != 0;
            if (trigger && dacOn) {
                enabled = true;
            }
            if (trigger) {
                lfsr = 0x7FFF;
                periodTimer = period;
                volume = initialVolume;
            }
            return;
        }

        throw new RuntimeException("Invalid address: " + Integer.toHexString(address));
    }
}