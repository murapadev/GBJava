package gbc.model.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gbc.model.event.EmulatorEvent;
import gbc.model.event.EventBus;

class TimerOverflowTest {

    @Test
    void overflowReloadsAfterDelayAndRequestsInterrupt() {
        EventBus bus = new EventBus();
        Timer timer = new Timer(bus);
        final int[] interrupts = {0};
        bus.subscribe(event -> {
            if (event instanceof EmulatorEvent.InterruptRequest req && req.bit() == 0x04) {
                interrupts[0]++;
            }
        });

        timer.reset(0);
        timer.writeTMA(0xAB);
        timer.writeTIMA(0xFF);
        timer.writeTAC(0x04); // enable, select bit 9
        timer.setDivider(0x0200); // bit 9 high

        timer.writeDIV(); // falling edge -> overflow
        assertEquals(0x00, timer.getTima(), "TIMA resets on overflow");

        for (int i = 0; i < 4; i++) {
            timer.step();
        }

        assertEquals(0xAB, timer.getTima(), "TIMA reloads from TMA after overflow delay");
        assertEquals(1, interrupts[0], "Timer interrupt requested once");

        for (int i = 0; i < 4; i++) {
            timer.step();
        }

        assertEquals(0xAB, timer.getTima(), "TIMA stays latched after reload active window");
        assertEquals(1, interrupts[0], "No extra timer interrupt requests");
    }

    @Test
    void writeTimaDuringOverflowDelayCancelsReload() {
        EventBus bus = new EventBus();
        Timer timer = new Timer(bus);
        final int[] interrupts = {0};
        bus.subscribe(event -> {
            if (event instanceof EmulatorEvent.InterruptRequest req && req.bit() == 0x04) {
                interrupts[0]++;
            }
        });

        timer.reset(0);
        timer.writeTMA(0x55);
        timer.writeTIMA(0xFF);
        timer.writeTAC(0x04);
        timer.setDivider(0x0200);

        timer.writeDIV(); // overflow
        timer.writeTIMA(0x12); // cancel reload

        for (int i = 0; i < 4; i++) {
            timer.step();
        }

        assertEquals(0x12, timer.getTima(), "Write to TIMA cancels reload during overflow delay");
        assertEquals(0, interrupts[0], "No interrupt requested after cancelled reload");
    }

    @Test
    void writeTmaDuringReloadActiveUpdatesTima() {
        EventBus bus = new EventBus();
        Timer timer = new Timer(bus);

        timer.reset(0);
        timer.writeTMA(0x10);
        timer.writeTIMA(0xFF);
        timer.writeTAC(0x04);
        timer.setDivider(0x0200);

        timer.writeDIV(); // overflow -> enter OVERFLOW_DELAY

        for (int i = 0; i < 4; i++) {
            timer.step();
        }

        assertEquals(0x10, timer.getTima(), "TIMA reloaded at start of RELOAD_ACTIVE");

        timer.writeTMA(0x77);
        assertEquals(0x77, timer.getTima(), "TIMA mirrors TMA writes during RELOAD_ACTIVE");
    }
}
