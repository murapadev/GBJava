package gbc.model.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class CpuDebugReadSideEffectTest {

    @Test
    void dumpMemoryUsesPeekReads() {
        Memory memory = new Memory();
        CPU cpu = new CPU(memory);

        AtomicInteger callbackCount = new AtomicInteger();
        memory.setMcycleCallback(callbackCount::incrementAndGet);

        cpu.dumpMemory(0xC000, 0xC00F);

        assertEquals(0, callbackCount.get(), "dumpMemory should not trigger memory access callbacks");
    }

    @Test
    void dumpStackUsesPeekReads() {
        Memory memory = new Memory();
        CPU cpu = new CPU(memory);

        AtomicInteger callbackCount = new AtomicInteger();
        memory.setMcycleCallback(callbackCount::incrementAndGet);

        cpu.dumpStack(8);

        assertEquals(0, callbackCount.get(), "dumpStack should not trigger memory access callbacks");
    }
}
