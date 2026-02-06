package gbc.model.sound;

/**
 * Frequency sweep for channel 1
 * Handles automatic frequency changes over time
 */
public class Sweep {
    private int shadowFrequency;
    private int timer;
    private int period;
    private boolean enabled;
    private int frequency;
    private int shift;
    private boolean decrementing;
    private boolean negateUsed;
    private final Runnable disableChannel;

    public Sweep(Runnable disableChannel) {
        this.disableChannel = disableChannel;
    }

    public void step() {
        if (timer > 0) {
            timer--;
        }

        if (timer == 0) {
            timer = period > 0 ? period : 8;

            if (enabled && shift > 0) {
                int newFrequency = calculateFrequency();

                if (newFrequency <= 2047 && shift > 0) {
                    frequency = newFrequency;
                    shadowFrequency = newFrequency;

                    calculateFrequency(); // Check for overflow again
                }
            }
        }
    }

    private int calculateFrequency() {
        int newFrequency = shadowFrequency >>> shift;

        if (decrementing) {
            newFrequency = shadowFrequency - newFrequency;
            if (shift > 0) {
                negateUsed = true;
            }
        } else {
            newFrequency = shadowFrequency + newFrequency;
        }

        // Check for overflow
        if (newFrequency > 2047) {
            disableChannel.run();
            enabled = false;
        }

        return newFrequency;
    }

    public int getFrequency(int unsweepedFrequency) {
        return enabled ? frequency : unsweepedFrequency;
    }

    public void triggerEvent(int frequency) {
        shadowFrequency = frequency;
        this.frequency = frequency;
        timer = period > 0 ? period : 8;
        enabled = period > 0 || shift > 0;
        negateUsed = false;
        if (shift > 0) {
            calculateFrequency();
        }
    }

    public boolean acceptsAddress(int address) {
        return address == 0xFF10; // NR10
    }

    public int readByte(int address) {
        if (address == 0xFF10) {
            return period << 4 | (decrementing ? 0b1000 : 0) | shift | 0b10000000;
        }

        throw new RuntimeException("Invalid address");
    }

    public void writeByte(int address, int value) {
        if (address != 0xFF10) {
            throw new RuntimeException("Invalid address");
        }
        boolean wasDecrementing = decrementing;
        decrementing = (value & 0b1000) > 0;
        period = (value & 0b1110000) >>> 4;
        shift = value & 0b111;
        enabled = period > 0 || shift > 0;
        if (wasDecrementing && !decrementing && negateUsed) {
            disableChannel.run();
            enabled = false;
        }
    }
}
