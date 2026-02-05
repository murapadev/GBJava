package gbc.controller;

/**
 * Diagnostic helpers for debugging the emulator state.
 */
public interface DiagnosticsService {
    String dumpRegisters();

    String dumpMemory(int startAddress, int endAddress);

    boolean isCartridgeLoaded();

    int readByte(int address);

    CpuSnapshot cpuSnapshot();

    gbc.model.cpu.Disassembler.DecodedInstruction decodeInstruction(int address);
}
