package gbc.model.sound;

import java.util.function.IntConsumer;

/**
 * Frequency sweep for channel 1.
 * <p>
 * Works on a shadow copy of the frequency taken at trigger time. Updated
 * values are written back to the channel's NR13/NR14 frequency, so later
 * writes to those registers change the pitch but not the sweep source.
 */
public class Sweep {
    private int shadowFrequency;
    private int timer;
    private int period;
    private boolean enabled;
    private int shift;
    private boolean decrementing;
    private boolean negateUsed;
    private final Runnable disableChannel;
    private final IntConsumer writeFrequency;

    public Sweep(Runnable disableChannel) {
        this(disableChannel, frequency -> {
        });
    }

    public Sweep(Runnable disableChannel, IntConsumer writeFrequency) {
        this.disableChannel = disableChannel;
        this.writeFrequency = writeFrequency;
    }

    public void step() {
        if (timer > 0) {
            timer--;
        }
        if (timer != 0) {
            return;
        }
        timer = period > 0 ? period : 8;
        if (!enabled || period == 0) {
            return;
        }
        int newFrequency = calculateFrequency();
        if (newFrequency <= 2047 && shift > 0) {
            shadowFrequency = newFrequency;
            writeFrequency.accept(newFrequency);
            calculateFrequency(); // second calculation only checks for overflow
        }
    }

    private int calculateFrequency() {
        int delta = shadowFrequency >>> shift;
        int newFrequency;
        if (decrementing) {
            newFrequency = shadowFrequency - delta;
            negateUsed = true;
        } else {
            newFrequency = shadowFrequency + delta;
        }
        if (newFrequency > 2047) {
            disableChannel.run();
        }
        return newFrequency;
    }

    public void triggerEvent(int frequency) {
        shadowFrequency = frequency;
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
        decrementing = (value & 0b1000) != 0;
        period = (value & 0b111_0000) >>> 4;
        shift = value & 0b111;
        // Leaving negate mode after a negate calculation since the last
        // trigger disables the channel.
        if (wasDecrementing && !decrementing && negateUsed) {
            disableChannel.run();
        }
    }
}
