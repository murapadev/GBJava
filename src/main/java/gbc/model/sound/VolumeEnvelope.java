package gbc.model.sound;

/**
 * Volume envelope for APU channels
 * Handles volume ramping up/down over time
 */
public class VolumeEnvelope {
    private int period;
    private int periodTimer;
    private int volume;
    private boolean upwards;
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

    public void triggerEvent() {
        // Reset volume to initial value and period timer
        int registerValue = readByte(registerAddress);
        volume = (registerValue >>> 4) & 0xF;
        period = registerValue & 0x7;
        periodTimer = period;
        upwards = (registerValue & 0x08) != 0;
    }

    public boolean acceptsAddress(int address) {
        return address == registerAddress;
    }

    public int readByte(int address) {
        if (address == registerAddress) {
            return (volume << 4) | (upwards ? 0x08 : 0) | period;
        }
        throw new RuntimeException("Invalid address");
    }

    public void writeByte(int address, int value) {
        if (address == registerAddress) {
            upwards = (value & 0x08) != 0;
            period = value & 0x07;
            volume = (value >>> 4) & 0x0F;
            periodTimer = period;
        } else {
            throw new RuntimeException("Invalid address");
        }
    }
}