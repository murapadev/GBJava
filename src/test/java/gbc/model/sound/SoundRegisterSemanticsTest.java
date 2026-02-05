package gbc.model.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundRegisterSemanticsTest {

    @Test
    void squareFrequencyLowRegisterIsWriteOnlyOnReadback() {
        SquareChannel channel = new SquareChannel(0xFF11, 0xFF12, false, 0xFF13, 0xFF14);
        channel.writeByte(0xFF13, 0x42);
        assertEquals(0xFF, channel.readByte(0xFF13), "NR13 should read as 0xFF");
    }

    @Test
    void envelopeReadReturnsLatchedRegisterValue() {
        VolumeEnvelope envelope = new VolumeEnvelope(0xFF12);
        envelope.writeByte(0xFF12, 0x19); // initial volume=1, increase, period=1
        envelope.triggerEvent();
        envelope.step();
        assertEquals(0x19, envelope.readByte(0xFF12),
                "NRx2 readback should remain the written register value");
    }

    @Test
    void sweepUsesTriggeredFrequencyWhenShiftIsZero() {
        final boolean[] disabled = { false };
        Sweep sweep = new Sweep(() -> disabled[0] = true);
        sweep.writeByte(0xFF10, 0x10); // period=1, decrease=0, shift=0
        sweep.triggerEvent(1024);

        assertEquals(1024, sweep.getFrequency(2047),
                "Sweep output should keep the triggered frequency when shift=0");
        assertFalse(disabled[0], "No overflow/disable is expected for this setup");
    }

    @Test
    void lengthRegistersRemainWritableWhileApuDisabled() {
        Apu apu = new Apu();
        apu.writeRegister(0xFF26, 0x00); // ensure APU off
        apu.writeRegister(0xFF11, 0xC0); // CH1 length/duty register

        assertEquals(0xFF, apu.readRegister(0xFF11),
                "NR11 writes should be accepted while APU is disabled");
    }

    @Test
    void nonLengthRegistersAreIgnoredWhileApuDisabled() {
        Apu apu = new Apu();
        apu.writeRegister(0xFF26, 0x00); // ensure APU off
        apu.writeRegister(0xFF12, 0xF3); // CH1 envelope register (should be ignored)

        assertEquals(0x00, apu.readRegister(0xFF12),
                "NR12 writes should be ignored while APU is disabled");
    }

    @Test
    void writingSquareLengthRegisterReloadsLengthCounter() {
        SquareChannel channel = new SquareChannel(0xFF11, 0xFF12, false, 0xFF13, 0xFF14);
        channel.writeByte(0xFF12, 0xF0); // DAC on
        channel.writeByte(0xFF14, 0xC0); // trigger + length enable

        channel.writeByte(0xFF11, 0x3F); // length counter = 1
        channel.step(true, false, false); // length step should disable immediately

        assertFalse(channel.isEnabled(), "Length reload via NRx1 should affect active channel immediately");
    }

    @Test
    void squareTriggerResetsDutyPhase() {
        SquareChannel channel = new SquareChannel(0xFF11, 0xFF12, false, 0xFF13, 0xFF14);
        channel.writeByte(0xFF11, 0x00); // duty 12.5% (only last step high)
        channel.writeByte(0xFF12, 0xF0); // DAC on, volume max
        channel.setDutyPosition(7); // force a high output phase

        channel.writeByte(0xFF14, 0x80); // trigger
        float[] sample = channel.step(false, false, false);

        assertEquals(0f, sample[0], "Trigger should restart square channel phase from duty position 0");
    }

    @Test
    void waveTriggerResetsSamplePosition() {
        WaveChannel channel = new WaveChannel();
        channel.writeByte(0xFF1A, 0x80); // DAC on
        channel.writeByte(0xFF1C, 0x20); // 100% output level
        channel.writeByte(0xFF30, 0xF0); // sample0=15, sample1=0
        channel.setDutyPosition(1); // force low nibble path

        channel.writeByte(0xFF1E, 0x80); // trigger
        float[] sample = channel.step(false);

        assertTrue(sample[0] > 0.9f, "Trigger should restart wave channel at sample position 0");
    }

    @Test
    void noiseTriggerReloadsLengthCounterWhenZero() {
        NoiseChannel channel = new NoiseChannel();
        channel.writeByte(0xFF21, 0xF0); // DAC on, initial volume
        channel.writeByte(0xFF20, 0x3F); // length=1
        channel.writeByte(0xFF23, 0xC0); // trigger + length enable
        channel.step(true, false); // consume length=1 -> disabled
        assertFalse(channel.isEnabled(), "Noise channel should disable after length expires");

        channel.writeByte(0xFF23, 0xC0); // retrigger without touching length register
        for (int i = 0; i < 63; i++) {
            channel.step(true, false);
        }
        assertTrue(channel.isEnabled(), "Retrigger should reload zero length to 64");
        channel.step(true, false);
        assertFalse(channel.isEnabled(), "Noise channel should disable after reloaded length expires");
    }
}
