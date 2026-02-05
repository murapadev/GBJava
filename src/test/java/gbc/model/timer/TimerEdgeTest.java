package gbc.model.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gbc.model.event.EventBus;

class TimerEdgeTest {

    @Test
    void writeDivTriggersIncrementOnFallingEdge() {
        Timer timer = new Timer(new EventBus());
        timer.reset(0);
        timer.writeTAC(0x04); // enable, select bit 9
        timer.writeTIMA(0x00);

        timer.setDivider(0x0200); // bit 9 high
        timer.writeDIV();

        assertEquals(0x00, timer.getDividerRaw() & 0xFFFF, "DIV resets to 0");
        assertEquals(0x01, timer.getTima(), "TIMA increments on falling edge from DIV reset");
    }

    @Test
    void writeTacDisableTriggersIncrementOnFallingEdge() {
        Timer timer = new Timer(new EventBus());
        timer.reset(0);
        timer.writeTIMA(0x00);

        timer.setDivider(0x0008); // bit 3 high
        timer.writeTAC(0x05); // enable, select bit 3
        timer.writeTAC(0x00); // disable -> falling edge

        assertEquals(0x01, timer.getTima(), "TIMA increments on falling edge when disabling TAC");
    }

    @Test
    void stepIncrementsOnDividerFallingEdge() {
        Timer timer = new Timer(new EventBus());
        timer.reset(0);
        timer.writeTIMA(0x00);
        timer.writeTAC(0x05); // enable, select bit 3

        timer.setDivider(0x000F); // bit 3 high
        timer.step(); // divider -> 0x0010, bit 3 low

        assertEquals(0x01, timer.getTima(), "TIMA increments on falling edge during step");
    }
}
