package gbc.model.sound;

/**
 * Volume envelope for APU channels
 * Handles volume ramping up/down over time
 */
public class VolumeEnvelope {
    // TODO: Match envelope timing when period=0 and trigger behavior across
    // channels.
    private int period;
    private int periodTimer;
    private int volume;
    private boolean upwards;
    private int registerValue;
    private final int registerAddress;

    public VolumeEnvelope(int registerAddress) {
        this.registerAddress = registerAddress;
    }

    public void step() {
        if (period != 0) {
            if (periodTimer > 0) {
                periodTimer--;
            }

            if (periodTimer == 0) {
                periodTimer = period;
                if (volume < 0xF && upwards || volume > 0x0 && !upwards) {
                    if (upwards) {
                        volume++;
                    } else {
                        volume--;
                    }
                }
            }
        }
    }

    public void modifySamples(float[] samples) {
        float factor = (float) volume / (float) 0xF;
        samples[0] *= factor;
        samples[1] *= factor;
    }

    /** Get current volume as a float factor [0.0, 1.0] */
    public float getVolume() {
        return (float) volume / 15f;
    }

    public void triggerEvent() {
        // Reset volume to the value latched in NRx2.
        volume = (registerValue >>> 4) & 0xF;
        period = registerValue & 0x7;
        periodTimer = period;
        upwards = (registerValue & 0x08) != 0;
    }

    public boolean isDacEnabled() {
        return (registerValue & 0b1111_1000) != 0;
    }

    public boolean acceptsAddress(int address) {
        return address == registerAddress;
    }

    public int readByte(int address) {
        if (address == registerAddress) {
            return registerValue & 0xFF;
        }
        throw new RuntimeException("Invalid address");
    }

    public void writeByte(int address, int value) {
        if (address == registerAddress) {
            registerValue = value & 0xFF;
            upwards = (registerValue & 0x08) != 0;
            period = registerValue & 0x07;
            volume = (registerValue >>> 4) & 0x0F;
            periodTimer = period;
        } else {
            throw new RuntimeException("Invalid address");
        }
    }
}
