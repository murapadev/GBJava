package gbc.model.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerBehaviorTest {
    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory();
    }

    private void enableTimer(int tacValue) {
        memory.writeByte(0xFF07, tacValue & 0xFF);
    }

    @Test
    void timaReloadsFromTmaAfterFourCycles() {
        // Arrange
        memory.writeByte(0xFF06, 0xAB); // TMA
        memory.writeByte(0xFF05, 0xFF); // TIMA set to overflow on next increment
        enableTimer(0b101); // Timer enabled, clock select 01 (bit 3)

        // Act: run enough cycles to trigger overflow (16 T-cycles)
        memory.stepPeripherals(16);
        assertEquals(0x00, memory.readByte(0xFF05)); // TIMA locked at 0 during delay
        assertEquals(0x00, memory.readByte(0xFF0F) & 0x04); // Interrupt not yet requested

        // Advance three cycles – still inside the reload delay window
        memory.stepPeripherals(3);
        assertEquals(0x00, memory.readByte(0xFF05));
        assertEquals(0x00, memory.readByte(0xFF0F) & 0x04);

        // One more cycle completes the reload delay
        memory.stepPeripherals(1);

        // Assert
        assertEquals(0xAB, memory.readByte(0xFF05));
        assertEquals(0x04, memory.readByte(0xFF0F) & 0x04);
    }

    @Test
    void writingTimaDuringReloadCancelsReloadAndInterrupt() {
        memory.writeByte(0xFF06, 0xAB);
        memory.writeByte(0xFF05, 0xFF);
        enableTimer(0b101);

        memory.stepPeripherals(16); // Trigger overflow
        memory.writeByte(0xFF05, 0x12); // Write during reload delay

        memory.stepPeripherals(4); // Finish delay window

        assertEquals(0x12, memory.readByte(0xFF05));
        assertEquals(0x00, memory.readByte(0xFF0F) & 0x04);
    }

    @Test
    void writingTimaAtFinalDelayCycleStillCancelsReloadAndInterrupt() {
        memory.writeByte(0xFF06, 0xAB);
        memory.writeByte(0xFF05, 0xFF);
        enableTimer(0b101);

        memory.stepPeripherals(16); // Trigger overflow
        memory.stepPeripherals(3); // Advance to the final cycle inside reload window

        memory.writeByte(0xFF05, 0x12); // Write at the last possible cycle

        memory.stepPeripherals(2); // Let the delay complete and interrupt fire

        assertEquals(0x12, memory.readByte(0xFF05));
        assertEquals(0x00, memory.readByte(0xFF0F) & 0x04);
    }

    @Test
    void writingTmaDuringReloadUpdatesReloadValue() {
        memory.writeByte(0xFF06, 0xAA);
        memory.writeByte(0xFF05, 0xFF);
        enableTimer(0b101);

        memory.stepPeripherals(16); // Trigger overflow
        memory.writeByte(0xFF06, 0x55); // Update TMA during delay

        memory.stepPeripherals(4); // Finish delay

        assertEquals(0x55, memory.readByte(0xFF05));
        assertEquals(0x04, memory.readByte(0xFF0F) & 0x04);
    }

    @Test
    void divWriteCreatesFallingEdgeIncrement() {
        memory.writeByte(0xFF05, 0x00);
        enableTimer(0b101); // Enable timer, clock select 01 (bit 3)

        // Advance 8 cycles so selected bit is 1 but no falling edge yet
        memory.stepPeripherals(8);
        assertEquals(0x00, memory.readByte(0xFF05));

        // Writing to DIV resets divider and should cause immediate falling edge
        memory.writeByte(0xFF04, 0x00);

        assertEquals(0x01, memory.readByte(0xFF05));
    }

    @Test
    void timer4096HzIncrementsEvery1024CyclesAfterDivReset() {
        // Arrange
        memory.writeByte(0xFF04, 0x00); // Reset DIV
        memory.writeByte(0xFF05, 0x04); // TIMA
        memory.writeByte(0xFF06, 0x04); // TMA
        enableTimer(0b100); // Start timer at 4096 Hz

        // Act
        memory.stepPeripherals(1024);

        // Assert
        assertEquals(0x05, memory.readByte(0xFF05));
    }

    @Test
    void timer4096HzDivResetDuringRunDelaysNextIncrementBy1024Cycles() {
        memory.writeByte(0xFF04, 0x00); // Initial DIV reset
        memory.writeByte(0xFF05, 0x04);
        memory.writeByte(0xFF06, 0x04);
        enableTimer(0b100);

        // Let the timer increment once to ensure it's running
        memory.stepPeripherals(1024);
        assertEquals(0x05, memory.readByte(0xFF05));

        // Restore TIMA to match the ROM setup and trigger another DIV reset while
        // running
        memory.writeByte(0xFF05, 0x04);
        memory.writeByte(0xFF04, 0x00); // DIV reset during active timer

        memory.stepPeripherals(1023);
        assertEquals(0x04, memory.readByte(0xFF05));

        memory.stepPeripherals(1);
        assertEquals(0x05, memory.readByte(0xFF05));
    }
}
