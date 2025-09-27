package gbc.test;

import gbc.model.memory.Memory;
import gbc.model.cpu.CPU;
import gbc.core.input.Controller;

public class TimerTest {
    public static void main(String[] args) {
        System.out.println("Testing Timer Collision Implementation...");

        // Create components
        Memory memory = new Memory();
        Controller controller = new Controller();
        CPU cpu = new CPU(memory);

        // Set up CPU and controller
        memory.setCPU(cpu);
        memory.setController(controller);

        // Test TMA write during reload delay
        // Set TAC to enable timer with 4096 Hz (bit 9)
        memory.writeByte(0xFF07, 0x04); // TAC = 0x04 (enable timer, 4096 Hz)

        // Set TMA to 0x10
        memory.writeByte(0xFF06, 0x10); // TMA = 0x10

        // Set TIMA to 0xFF (should overflow after 1 increment)
        memory.writeByte(0xFF05, 0xFF); // TIMA = 0xFF

        System.out.println("Initial state:");
        System.out.println("DIV: " + String.format("0x%02X", memory.readByte(0xFF04)));
        System.out.println("TIMA: " + String.format("0x%02X", memory.readByte(0xFF05)));
        System.out.println("TMA: " + String.format("0x%02X", memory.readByte(0xFF06)));
        System.out.println("TAC: " + String.format("0x%02X", memory.readByte(0xFF07)));

        // Step until TIMA overflows (1024 T-cycles)
        for (int i = 0; i < 1024; i++) {
            memory.stepPeripherals(1);
        }

        System.out.println("After overflow (TIMA should be 0):");
        System.out.println("DIV: " + String.format("0x%02X", memory.readByte(0xFF04)));
        System.out.println("TIMA: " + String.format("0x%02X", memory.readByte(0xFF05)));
        System.out.println("TMA: " + String.format("0x%02X", memory.readByte(0xFF06)));
        System.out.println("TAC: " + String.format("0x%02X", memory.readByte(0xFF07)));

        // Now write to TMA during the 4 T-cycle delay
        // Write new TMA value 0x20
        memory.writeByte(0xFF06, 0x20); // TMA = 0x20

        System.out.println("After writing TMA during delay:");
        System.out.println("DIV: " + String.format("0x%02X", memory.readByte(0xFF04)));
        System.out.println("TIMA: " + String.format("0x%02X", memory.readByte(0xFF05)));
        System.out.println("TMA: " + String.format("0x%02X", memory.readByte(0xFF06)));
        System.out.println("TAC: " + String.format("0x%02X", memory.readByte(0xFF07)));

        // Step the remaining 4 T-cycles for reload
        for (int i = 0; i < 4; i++) {
            memory.stepPeripherals(1);
        }

        System.out.println("After reload delay (should have reloaded to old TMA 0x10):");
        System.out.println("DIV: " + String.format("0x%02X", memory.readByte(0xFF04)));
        System.out.println("TIMA: " + String.format("0x%02X", memory.readByte(0xFF05)));
        System.out.println("TMA: " + String.format("0x%02X", memory.readByte(0xFF06)));
        System.out.println("TAC: " + String.format("0x%02X", memory.readByte(0xFF07)));

        // Now write to TMA after reload
        memory.writeByte(0xFF06, 0x30); // TMA = 0x30

        // Check if TIMA is still 0x10 (old value)
        int tima = memory.readByte(0xFF05);
        if (tima == 0x10) {
            System.out.println("SUCCESS: TIMA kept old TMA value when written after delay!");
        } else {
            System.out.println(
                    "FAILURE: TIMA changed unexpectedly. Expected 0x10, got " + String.format("0x%02X", tima));
        }
    }
}