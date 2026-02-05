package gbc.controller;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.cpu.Disassembler;
import gbc.model.cpu.Registers;

final class EmulatorDiagnostics implements DiagnosticsService {
    private final GameBoyColor gbc;
    private final ReentrantLock emulationLock;

    EmulatorDiagnostics(GameBoyColor gbc, ReentrantLock emulationLock) {
        this.gbc = Objects.requireNonNull(gbc, "gbc");
        this.emulationLock = Objects.requireNonNull(emulationLock, "emulationLock");
    }

    @Override
    public String dumpRegisters() {
        emulationLock.lock();
        try {
            return gbc.getCpu().getRegisters().toString();
        } finally {
            emulationLock.unlock();
        }
    }

    @Override
    public String dumpMemory(int startAddress, int endAddress) {
        emulationLock.lock();
        try {
            return gbc.getCpu().dumpMemory(startAddress, endAddress);
        } finally {
            emulationLock.unlock();
        }
    }

    @Override
    public boolean isCartridgeLoaded() {
        emulationLock.lock();
        try {
            return gbc.getMemory().isCartridgeLoaded();
        } finally {
            emulationLock.unlock();
        }
    }

    @Override
    public int readByte(int address) {
        emulationLock.lock();
        try {
            return gbc.getMemory().peekByte(address) & 0xFF;
        } finally {
            emulationLock.unlock();
        }
    }

    @Override
    public CpuSnapshot cpuSnapshot() {
        emulationLock.lock();
        try {
            CPU cpu = gbc.getCpu();
            Registers regs = cpu.getRegisters();
            return new CpuSnapshot(
                    regs.getPC() & 0xFFFF,
                    regs.getSP() & 0xFFFF,
                    regs.getAF() & 0xFFFF,
                    regs.getBC() & 0xFFFF,
                    regs.getDE() & 0xFFFF,
                    regs.getHL() & 0xFFFF,
                    regs.getRegister("A") & 0xFF,
                    regs.getRegister("B") & 0xFF,
                    regs.getRegister("C") & 0xFF,
                    regs.getRegister("D") & 0xFF,
                    regs.getRegister("E") & 0xFF,
                    regs.getRegister("F") & 0xFF,
                    regs.getRegister("H") & 0xFF,
                    regs.getRegister("L") & 0xFF,
                    cpu.isIme(),
                    cpu.isHalted(),
                    cpu.isDoubleSpeedMode()
            );
        } finally {
            emulationLock.unlock();
        }
    }

    @Override
    public Disassembler.DecodedInstruction decodeInstruction(int address) {
        emulationLock.lock();
        try {
            return Disassembler.decode(gbc.getMemory(), address);
        } finally {
            emulationLock.unlock();
        }
    }
}
