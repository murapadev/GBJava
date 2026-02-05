package gbc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import gbc.model.GameBoyColor;

class EmulatorDiagnosticsTest {

    @Test
    void readByteUsesSideEffectFreePeek() {
        GameBoyColor gbc = new GameBoyColor();
        EmulatorDiagnostics diagnostics = new EmulatorDiagnostics(gbc, new ReentrantLock());

        AtomicInteger callbackCount = new AtomicInteger();
        gbc.getMemory().setMcycleCallback(callbackCount::incrementAndGet);

        diagnostics.readByte(0xC000);

        assertEquals(0, callbackCount.get(), "Diagnostics reads should not trigger memory access callbacks");
    }
}
